"""Boot unmodified distribution JARs twice against a disposable MySQL service."""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
import sys
import zipfile
import socket

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "release"))
from artefacts import filename
from paper_layout import verify_install
from runtime_download import prepare_server
from platform_detection import signature

COMPATIBILITY = json.loads((Path(__file__).resolve().parents[1] / "release/compatibility.json").read_text())
# The release job and the runtime checks use the same PaperMC download pins.
SERVERS = {
    f"{platform}-{version}": (build["file"], build["sha256"])
    for platform in ("paper", "velocity")
    for version, build in COMPATIBILITY[platform].items()
}
ROWS = [
    ("00000000-0000-4000-8000-000000000001", 1, "lobby", "world", 5, 64, -9, 0, 90),
    ("00000000-0000-4000-8000-000000000001", 2, "mines", "nether", 7, 43, 8, 15, -10),
    ("00000000-0000-4000-8000-000000000002", 1, "mines", "world", -3, 70, 2, -22, 1),
]


def mysql(sql):
    # The runner grants this test user access only to the ephemeral test schema.
    args = ["mysql", "-h", "127.0.0.1", "-P", os.environ["MYSQL_PORT"]]
    if container := os.environ.get("TELEHOP_TEST_DB_CONTAINER"):
        args = ["docker", "exec", "-i", "-e", "MYSQL_PWD", container, os.getenv("TELEHOP_TEST_DB_CLIENT", "mysql")]
    return subprocess.check_output(args + ["-u", "telehop", "--batch", "--skip-column-names", "telehop_ci"],
                                   input=sql, text=True).splitlines()


def seed_legacy_homes():
    mysql("""CREATE TABLE homes (
        uuid VARCHAR(36) NOT NULL, slot INT NOT NULL, server VARCHAR(64) NOT NULL,
        world VARCHAR(64) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
        yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, PRIMARY KEY (uuid, slot))""")
    mysql("""CREATE TABLE tpa_requests (
        sender_uuid VARCHAR(36) NOT NULL, target_uuid VARCHAR(36) NOT NULL, type VARCHAR(16) NOT NULL,
        expiry BIGINT NOT NULL, PRIMARY KEY (sender_uuid, target_uuid))""")
    mysql("INSERT INTO tpa_requests VALUES ('00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000004', 'TPA', 4102444800000)")
    for row in ROWS:
        mysql("INSERT INTO homes VALUES " + repr(row))


def check_conflicting_upgrade(bundle, version):
    # Reproduce a partially migrated 1.x schema with a conflicting name for one owner.
    mysql("ALTER TABLE homes ADD name VARCHAR(32) NOT NULL DEFAULT ''")
    mysql("UPDATE homes SET name='Home 2' WHERE slot=1")
    subprocess.run(["java", "-cp", str(bundle / f"telehop-velocity-{version}.jar"),
                    str(Path(__file__).with_name("LegacyMigrationCheck.java"))], check=True)
    retained = mysql("SELECT uuid,slot,server,world,x,y,z,yaw,pitch FROM homes ORDER BY uuid,slot")
    assert retained == ["\t".join(map(str, row)) for row in ROWS]
    key = mysql("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() "
                "AND table_name='homes' AND index_name='PRIMARY' ORDER BY seq_in_index")
    assert key == ["uuid", "slot"]
    mysql("UPDATE homes SET name='' WHERE slot=1")


def check_migration():
    timestamp = mysql("SELECT sent_at FROM tpa_requests WHERE sender_uuid='00000000-0000-4000-8000-000000000003'")
    assert timestamp == ["4102444800000"], timestamp
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
    shutil.copy(bundle / filename(platform, version), plugins)
    port = int(os.environ["MYSQL_PORT"])
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        game_port = reservation.getsockname()[1]
    if platform == "paper":
        shutil.copy(bundle / "command-probe.jar", plugins)
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={game_port}\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n")
        config = plugins / "TeleHop/config"
        config.mkdir(parents=True)
        (config / "database.yml").write_text(
            f"mysql:\n  host: 127.0.0.1\n  port: {port}\n  database: telehop_ci\n"
            "  username: telehop\n  password: ci-password\n  pool-size: 2\n")
    else:
        with zipfile.ZipFile(directory / "server.jar") as jar:
            toml = jar.read("default-velocity.toml").decode()
        (directory / "velocity.toml").write_text(toml.replace('bind = "0.0.0.0:25565"', f'bind = "127.0.0.1:{game_port}"' ))
        (directory / "forwarding.secret").write_text(secrets.token_hex(32))
        config = plugins / "telehop-velocity"
        config.mkdir()
        (config / "config.properties").write_text(
            f"mysql.host=127.0.0.1\nmysql.port={port}\nmysql.database=telehop_ci\n"
            "mysql.username=telehop\nmysql.password=ci-password\n")


def use_legacy_paper_config(directory):
    plugin = directory / "plugins/TeleHop"
    database = plugin / "config/database.yml"
    legacy = (database.read_text() + "server-name: legacy-review\nhub-server: legacy-review\n"
              "spawn: {location: {world: world, x: 14.5, y: 86, z: -9.5, yaw: 67, pitch: -12}}\n")
    (plugin / "config.yml").write_text(legacy)
    database.unlink()  # This fixture starts with the pre-split layout.
    return legacy


def check_legacy_paper_config(directory, original):
    plugin = directory / "plugins/TeleHop"
    assert (plugin / "config.yml.old").read_text() == original
    assert "mode: SINGLE" in (plugin / "config.yml").read_text()
    assert (plugin / "config/database.yml").is_file() and (plugin / "storage.yml").is_file()
    assert mysql("SELECT default_world,default_x,default_y,default_z,default_yaw,default_pitch "
                 "FROM spawn_backends WHERE server='legacy-review'") == ["world\t14.5\t86\t-9.5\t67\t-12"]


ANSI = re.compile(r"\x1b\[[0-9;]*m")


def boot(directory, platform, second, *, game=None):
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
            if paper and game is not None:
                expected = "Platform detection completed: " + signature("Paper", game)
                if not re.search(expected, content):
                    raise AssertionError(f"{log}: incorrect platform, version adapter, travel provider or chat bridge for Paper {game}")
            elif platform == "velocity" and game is not None:
                major = game.split(".", 1)[0]
                expected = rf"Using Velocity {major} shared messaging API \(runtime {re.escape(game)}(?=[.)\s-])"
                if not re.search(expected, content):
                    raise AssertionError(f"{log}: missing Velocity {game} runtime and API selection")
            proc.communicate("telehop help\nstop\n" if paper else "shutdown\n", timeout=60)
            content = ANSI.sub("", log.read_text(errors="replace"))
            if paper:
                if "/spawn [name] selects a destination." not in content:
                    raise AssertionError("The command help did not list named spawn travel")
                if ("/lobby takes you to the global hub." in content) != second or "/hub takes you to the global hub." in content:
                    raise AssertionError("The hub help does not match the registered command name")
            stopped = "Goodbye!" in content or (paper and "TeleHop has been disabled." in content and "Shutdown completed." in content)
            if proc.returncode or not stopped or "Error occurred while disabling TeleHop" in content:
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
    platform = args.server.split("-")[0]
    prepare_server(directory, platform, name, digest)
    configure(directory, platform, args.bundle, args.version)
    legacy = use_legacy_paper_config(directory) if args.server == "paper-1.21.11" else None
    seed_legacy_homes()
    if args.server == "velocity-3.3":
        check_conflicting_upgrade(args.bundle, args.version)
    for second in (False, True):
        if second and platform == "paper":
            data = directory / "plugins/TeleHop"
            (data / "config/single/features.yml").write_text("features: {hub: true}\n")
            root = data / "config.yml"
            root.write_text(re.sub(r"(custom-names:\s*\n\s*enabled:) false", r"\1 true", root.read_text()))
            (data / "commands/hub.yml").write_text("hub: {custom-name: {enabled: true, name: lobby, aliases: [hub]}}\n")
            import yaml
            tp_file = data / "commands/teleport.yml"
            teleport = yaml.safe_load(tp_file.read_text())
            teleport["tp"]["custom-name"] = {"enabled": True, "name": "networktp", "aliases": ["ntp"]}
            tp_file.write_text(yaml.safe_dump(teleport, sort_keys=False))
        boot(directory, platform, second, game=args.server.split("-", 1)[1])
        check_migration()
        if platform == "paper":
            verify_install(directory / "plugins/TeleHop", args.bundle / filename("paper", args.version))
        else:
            data = directory / "plugins/telehop-velocity"
            config = data / "config"
            assert {p.name for p in config.iterdir()} == {"database.yml", "redis.yml", "single", "multi", "custom"}
            for profile in ("single", "multi", "custom"):
                expected = {"servers.yml", "messaging.yml"}
                if profile != "single":
                    expected.add("proxy.yml")
                assert {p.name for p in (config / profile).iterdir()} == expected, profile
            assert all((data / file).is_file() for file in ("config.yml", "migrations/state.yml", "migrations/history.log"))
        if legacy:
            check_legacy_paper_config(directory, legacy)
    print(f"{args.server}: both startups, shutdowns and legacy home migration checks passed")


if __name__ == "__main__":
    main()
