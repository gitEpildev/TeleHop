"""Run TeleHop's release tests locally with isolated, loopback-only Docker databases."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import contextmanager
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT / ".github/tests"
INVENTORY = json.loads((ROOT / ".github/release/compatibility.json").read_text())
VERSION = ET.parse(ROOT / "pom.xml").findtext("{*}version")


@contextmanager
def database(flavour, env):
    image, client = ("mysql:8.4", "mysql") if flavour == "mysql" else ("mariadb:11.4", "mariadb")
    container = subprocess.check_output(["docker", "run", "--rm", "--detach", "-p", "127.0.0.1::3306",
        "-e", "MYSQL_DATABASE=telehop_ci", "-e", "MYSQL_USER=telehop", "-e", "MYSQL_PASSWORD=ci-password",
        "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes", image], text=True).strip()
    try:
        port = subprocess.check_output(["docker", "port", container, "3306/tcp"], text=True).strip().split(":")[-1]
        env.update(MYSQL_PORT=port, MYSQL_PWD="ci-password", TELEHOP_TEST_DB_CONTAINER=container, TELEHOP_TEST_DB_CLIENT=client)
        for _ in range(90):
            check = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD", container, client, "-u", "telehop",
                                    "--protocol=tcp", "telehop_ci", "-e", "SELECT 1"], env=env, capture_output=True)
            if check.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError(f"{image} did not accept test connections")
        yield
    finally:
        subprocess.run(["docker", "stop", container], check=True, stdout=subprocess.DEVNULL)


def verify(case, args):
    directory = args.output / case
    directory.mkdir()
    env = dict(os.environ, TELEHOP_TEST_CACHE=str(args.cache), PYTHONUNBUFFERED="1")
    flavour = "mariadb" if case == "upgrade-mariadb" else "mysql"
    runtime = case if case.startswith(("paper-", "velocity-")) else "paper-1.21.11"
    if case.startswith("client-"):
        client_version = case.removeprefix("client-")
        runtime = "paper-" + INVENTORY["velocity_client_backends"][client_version]["backend"]
    platform, game = runtime.split("-", 1)
    java = args.java25 if INVENTORY[platform][game]["java"] == 25 else args.java21
    env["PATH"] = str(java.parent) + os.pathsep + env["PATH"]
    bundle = str(args.bundle)
    with (directory / "result.log").open("w", buffering=1) as log:
        def run(*cmd):
            subprocess.run(cmd, env=env, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
        with database(flavour, env):
            if case.startswith("upgrade-"):
                run(sys.executable, TESTS / "profile_upgrade.py", bundle, VERSION, directory / "upgrade")
            else:
                smoke = directory / "smoke"
                run(sys.executable, TESTS / "server_smoke.py", runtime, VERSION, bundle, smoke)
                if case.startswith("client-"):
                    run(sys.executable, TESTS / "network/client_compatibility.py", smoke, bundle, VERSION, directory / "client", client_version)
                elif case in ("network-v3", "network-v4", "network-mixed"):
                    run(java, "-cp", args.bundle / f"telehop-velocity-{VERSION}.jar", TESTS / "network/SpawnDirectoryCheck.java")
                    if case == "network-mixed":
                        run(sys.executable, TESTS / "network/mixed.py", smoke, bundle, VERSION, directory / "network", "--java25", args.java25)
                    else:
                        proxy, jvm = ("velocity-3.5", args.java21) if case == "network-v3" else ("velocity-4.3.1", args.java25)
                        run(sys.executable, TESTS / "network/run.py", smoke, bundle, VERSION, directory / "network", "--proxy", proxy, "--proxy-java", jvm)
    if not args.keep_worlds:
        # These directories belong to this invocation. Keep all server/client evidence but release downloaded worlds.
        for path in list(directory.rglob("*.log")):
            if path.name.startswith("boot-") or path.name == "acceptance.log":
                destination = directory / "logs" / path.relative_to(directory)
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, destination)
        for name in ("smoke", "network", "upgrade", "client"):
            if (directory / name).exists():
                shutil.rmtree(directory / name)
    return case


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("cases", nargs="+", help="matrix, network-v3, network-v4, network-mixed, client, upgrade-mysql, upgrade-mariadb, or a pinned server")
    parser.add_argument("--java21", required=True, type=Path)
    parser.add_argument("--java25", required=True, type=Path)
    parser.add_argument("--bundle", type=Path, default=ROOT / "release-bundle")
    parser.add_argument("--cache", type=Path, default=Path(tempfile.gettempdir()) / "telehop-runtime-downloads")
    parser.add_argument("--output", type=Path, required=True, help="A new directory for logs and failed fixtures")
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--keep-worlds", action="store_true")
    args = parser.parse_args()
    servers = [f"{platform}-{game}" for platform in ("paper", "velocity") for game in INVENTORY[platform]]
    clients = ["client-" + game for game in INVENTORY["velocity_client_backends"]]
    groups = {"matrix": servers, "client": clients}
    cases = list(dict.fromkeys(case for selection in args.cases for case in groups.get(selection, [selection])))
    permitted = set(servers + clients) | {"network-v3", "network-v4", "network-mixed", "upgrade-mysql", "upgrade-mariadb"}
    if set(cases) - permitted or args.jobs < 1: parser.error("Unknown test case or invalid job count")
    args.output = args.output.resolve()
    args.bundle = args.bundle.resolve()
    args.cache = args.cache.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    failures = []
    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        jobs = {pool.submit(verify, case, args): case for case in cases}
        for job in as_completed(jobs):
            case = jobs[job]
            try:
                job.result()
                print(case, "PASS", flush=True)
            except Exception as error:
                failures.append(case)
                print(case, "FAIL", error, args.output / case / "result.log", flush=True)
    sys.exit(bool(failures))
