"""Boot unmodified distribution JARs twice against a disposable MySQL service."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
import zipfile

# Pinned PaperMC downloads, identified by the digest published by the Fill API.
SERVERS = {
    "paper-1.21.11": ("paper-1.21.11-132.jar", "5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba"),
    "paper-26.1.2": ("paper-26.1.2-74.jar", "1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7"),
    "velocity-3.3": ("velocity-3.3.0-SNAPSHOT-436.jar", "06bb2a6ac16228a2f33a0336952624e6918a97535d36a3660725b18e2a6efdfb"),
}
ROWS = [
    ("00000000-0000-4000-8000-000000000001", 1, "lobby", "world", 5, 64, -9, 0, 90),
    ("00000000-0000-4000-8000-000000000001", 2, "mines", "nether", 7, 43, 8, 15, -10),
    ("00000000-0000-4000-8000-000000000002", 1, "mines", "world", -3, 70, 2, -22, 1),
]


def mysql(sql):
    # The runner grants this test user access only to the ephemeral test schema.
    return subprocess.check_output(["mysql", "-h", "127.0.0.1", "-P", os.environ["MYSQL_PORT"],
                                    "-u", "telehop", "--batch", "--skip-column-names", "telehop_ci"],
                                   input=sql, text=True).splitlines()


def seed_legacy_homes():
    mysql("""CREATE TABLE homes (
        uuid VARCHAR(36) NOT NULL, slot INT NOT NULL, server VARCHAR(64) NOT NULL,
        world VARCHAR(64) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
        yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, PRIMARY KEY (uuid, slot))""")
    for row in ROWS:
        mysql("INSERT INTO homes VALUES " + repr(row))


def check_migration():
    actual = mysql("SELECT uuid,name,server,world,x,y,z,yaw,pitch FROM homes ORDER BY uuid,name")
    expected = ["\t".join(map(str, [uuid, f"Home {slot}", *fields])) for uuid, slot, *fields in ROWS]
    if actual != expected:
        raise AssertionError(f"Legacy home data differs: {actual!r}")
    columns = mysql("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() "
                    "AND table_name='homes' AND index_name='PRIMARY' ORDER BY seq_in_index")
    if columns != ["uuid", "name"] or mysql("SHOW COLUMNS FROM homes LIKE 'slot'"):
        raise AssertionError("Home schema migration did not finish")


def configure(directory, platform, bundle, version):
    plugins = directory / "plugins"
    plugins.mkdir()
    shutil.copy(bundle / f"telehop-{platform}-{version}.jar", plugins)
    port = int(os.environ["MYSQL_PORT"])
    if platform == "paper":
        shutil.copy(bundle / "command-probe.jar", plugins)
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n")
        config = plugins / "TeleHop/config"
        config.mkdir(parents=True)
        (config / "database.yml").write_text(
            f"mysql:\n  host: 127.0.0.1\n  port: {port}\n  database: telehop_ci\n"
            "  username: telehop\n  password: ci-password\n  pool-size: 2\n")
    else:
        with zipfile.ZipFile(directory / "server.jar") as jar:
            toml = jar.read("default-velocity.toml").decode()
        (directory / "velocity.toml").write_text(toml.replace('bind = "0.0.0.0:25565"', 'bind = "127.0.0.1:25575"'))
        (directory / "forwarding.secret").write_text(secrets.token_hex(32))
        config = plugins / "telehop-velocity"
        config.mkdir()
        (config / "config.properties").write_text(
            f"mysql.host=127.0.0.1\nmysql.port={port}\nmysql.database=telehop_ci\n"
            "mysql.username=telehop\nmysql.password=ci-password\n")


ANSI = re.compile(r"\x1b\[[0-9;]*m")


def boot(directory, platform, second):
    paper = platform == "paper"
    args = ["java", "-Xms256m", "-Xmx1400m", "-XX:ActiveProcessorCount=2", "-Dterminal.jline=false",
            "-Dterminal.ansi=false", f"-Dtelehop.test.custom={str(second).lower()}", "-jar", "server.jar"]
    if paper:
        args.append("--nogui")
    log = directory / f"boot-{2 if second else 1}.log"
    ready = ["Startup complete!", "Done ("]
    if paper:
        ready.append(f"TeleHop command checks passed (custom={str(second).lower()})")
    with log.open("w") as out, subprocess.Popen(args, cwd=directory, stdin=subprocess.PIPE,
                                                stdout=out, stderr=subprocess.STDOUT, text=True) as proc:
        try:
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                content = ANSI.sub("", log.read_text(errors="replace"))
                if all(token in content for token in ready):
                    break
                if proc.poll() is not None or any(error in content for error in
                        ("Error occurred while enabling TeleHop", "Failed to initialize", "AssertionError",
                         "NoClassDefFoundError", "UnsupportedClassVersionError")):
                    raise RuntimeError(content[-6000:])
                time.sleep(0.5)
            else:
                raise TimeoutError(f"Missing startup evidence {ready}: {content[-6000:]}")
            proc.communicate("stop\n" if paper else "shutdown\n", timeout=60)
            content = ANSI.sub("", log.read_text(errors="replace"))
            if proc.returncode or "Goodbye!" not in content or "Error occurred while disabling TeleHop" in content:
                raise RuntimeError("Unclean shutdown: " + content[-4000:])
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("server", choices=SERVERS)
    parser.add_argument("version")
    parser.add_argument("bundle", type=Path)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    directory = args.directory
    directory.mkdir(parents=True, exist_ok=False)
    name, digest = SERVERS[args.server]
    jar = directory / "server.jar"
    subprocess.run(["curl", "-fsSL", "--retry", "3", "-A", "TeleHop-CI/1.0 (https://github.com/gitEpildev/TeleHop)",
                    "-o", str(jar), f"https://fill-data.papermc.io/v1/objects/{digest}/{name}"], check=True)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != digest:
        raise ValueError("Server download checksum mismatch")
    platform = args.server.split("-")[0]
    configure(directory, platform, args.bundle, args.version)
    seed_legacy_homes()
    for second in (False, True):
        if second and platform == "paper":
            (directory / "plugins/TeleHop/config/commands.yml").write_text("tp: [networktp, ntp]\n")
        boot(directory, platform, second)
        check_migration()
    print(f"{args.server}: both startups, shutdowns and legacy home migration checks passed")


if __name__ == "__main__":
    main()
