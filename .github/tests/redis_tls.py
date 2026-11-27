"""Run the packaged Redis bridge against a private CA and a password-protected TLS listener."""
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import tempfile


def run(jar):
    with tempfile.TemporaryDirectory(prefix="telehop-redis-tls-") as work:
        directory = Path(work)
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        def openssl(*args):
            subprocess.run(["openssl", *args], cwd=directory, check=True, capture_output=True)
        openssl("req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", "ca.key", "-out", "ca.pem",
                "-days", "1", "-subj", "/CN=TeleHop Test CA")
        openssl("req", "-newkey", "rsa:2048", "-nodes", "-keyout", "server.key", "-out", "server.csr", "-subj", "/CN=localhost")
        (directory / "extensions.cnf").write_text("subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth\n")
        openssl("x509", "-req", "-in", "server.csr", "-CA", "ca.pem", "-CAkey", "ca.key", "-CAcreateserial", "-days", "1",
                "-out", "server.pem", "-extfile", "extensions.cnf")
        password = secrets.token_hex(24)
        (directory / "redis.conf").write_text(
            f'bind 127.0.0.1\nport 0\ntls-port {port}\ntls-cert-file "{directory}/server.pem"\n'
            f'tls-key-file "{directory}/server.key"\ntls-ca-cert-file "{directory}/ca.pem"\n'
            f'tls-auth-clients no\nrequirepass {password}\nsave ""\nappendonly no\n')
        env = dict(os.environ, TELEHOP_TEST_REDIS_DIR=work, TELEHOP_TEST_REDIS_PORT=str(port), REDISCLI_AUTH=password)
        subprocess.run(["java", "-Djava.net.preferIPv4Stack=true", "-cp", jar,
                        Path(__file__).with_name("RedisBridgeCheck.java").resolve()], check=True, env=env)


if __name__ == "__main__":
    run(Path(sys.argv[1]).resolve())
