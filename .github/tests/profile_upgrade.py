"""Upgrade the published 3.0.1 backend and proxy on disk and check the V4 configuration."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil

import yaml

from runtime_download import download, prepare_server
from server_smoke import SERVERS, boot, check_migration, configure, mysql, seed_legacy_homes, verify_install, filename


PREVIOUS = "3.0.1"
PUBLISHED = {"paper": "bc263be3f232ff08c23590422ae711e98bd44040934ac9748d7d1eb76285439c",
             "velocity": "e6b8efc9d889422d9713aa6eb92b56556310d8f913045b094fb90f191307b2c8"}


def config(path):
    return yaml.safe_load(path.read_text())


def previous_builds(directory, bundle):
    directory.mkdir()
    for platform, checksum in PUBLISHED.items():
        name = filename(platform, PREVIOUS)
        url = f"https://github.com/gitEpildev/TeleHop/releases/download/v{PREVIOUS}/{name}"
        download(directory / name, url, checksum)
    shutil.copyfile(bundle / "command-probe.jar", directory / "command-probe.jar")


def check_backups(data, saved):
    backups = data / "migrations/backups"
    assert backups.is_dir() and (data / "migrations/state.yml").is_file()
    assert (data / "migrations/history.log").is_file()
    entry = backups / config(data / "migrations/state.yml")["migration"]
    manifest = dict(line.split("  ", 1)[::-1] for line in (entry / "SHA256SUMS").read_text().splitlines())
    for relative, expected in saved.items():
        archived = entry / "original" / relative
        assert archived.read_bytes() == expected, relative
        assert hashlib.sha256(expected).hexdigest() == manifest[relative], relative


def upgrade(root, bundle, version):
    previous = root / "released"
    previous_builds(previous, bundle)
    seed_legacy_homes()
    for platform, build in (("paper", "paper-1.21.11"), ("velocity", "velocity-3.5")):
        work = root / platform
        work.mkdir()
        prepare_server(work, platform, *SERVERS[build])
        configure(work, platform, previous, PREVIOUS)
        boot(work, platform, False)
        data = work / "plugins" / ("TeleHop" if platform == "paper" else "telehop-velocity")
        if platform == "paper":
            # These files belong to the running published 3.0.1 JAR. V4 must import them.
            (data / "config/commands.yml").write_text("tp: [networktp, ntp]\nhub: [lobby, hub]\n")
            (data / "config/features.yml").write_text("features: {hub: true, rtp: false}\n")
            (data / "config/general.yml").write_text("server-name: preservation\nhub-server: preservation\n")
            with (data / "languages/en.yml").open("a") as language:
                language.write("\nowner-welcome: Welcome aboard!\n")
            files = ["config/WIKI.md", "config/commands.yml", "config/features.yml", "config/database.yml", "languages/en.yml"]
        else:
            with (data / "config.properties").open("a") as properties:
                properties.write("\nredis.ssl=true\nredis.ssl-verify=false\nredis.ssl-ca-cert=pki/custom-chain.pem\nproxy.id=existing-proxy\n")
            files = ["config.properties"]
        boot(work, platform, platform == "paper")
        # The released plugin backfills absent feature keys and command labels on startup.
        # These are the actual input bytes V4 must archive.
        saved = {name: (data / name).read_bytes() for name in files}
        old = work / "plugins" / filename(platform, PREVIOUS)
        old.rename(root / old.name)
        shutil.copyfile(bundle / filename(platform, version), work / "plugins" / filename(platform, version))
        boot(work, platform, platform == "paper", game="1.21.11")
        assert config(data / "config.yml")["mode"] == "SINGLE"
        check_backups(data, saved)
        check_migration()
        if platform == "paper":
            verify_install(data, bundle / filename("paper", version))
            assert config(data / "config/single/general.yml")["messaging"]["network-enabled"]
            settings = config(data / "config/single/features.yml")["features"]
            assert settings["hub"] and not settings["rtp"]
            assert config(data / "languages/en.yml")["owner-welcome"] == "Welcome aboard!"
            assert config(data / "config/database.yml")["mysql"]["port"] == int(os.environ["MYSQL_PORT"])
            assert mysql("SELECT hub_command FROM spawn_backends WHERE server='preservation'") == ["1"]
        else:
            redis = config(data / "config/redis.yml")["redis"]
            assert redis["ssl"] and not redis["ssl-verify"] and redis["ssl-ca-cert"] == "pki/custom-chain.pem"
            assert config(data / "config/single/servers.yml")["proxy"]["id"] == "existing-proxy"
            assert {f.name for f in (data / "config/single").iterdir()} == {"servers.yml", "messaging.yml"}
        state = (data / "migrations/state.yml").read_bytes()
        history = (data / "migrations/history.log").read_bytes()
        if platform == "paper":
            guide = data / "config/WIKI.md"
            stock = guide.read_bytes()
            custom = stock + b"\nLocal setup notes from the server owner.\n"
            guide.write_bytes(custom)
        boot(work, platform, platform == "paper", game="1.21.11")
        assert state == (data / "migrations/state.yml").read_bytes()
        assert history == (data / "migrations/history.log").read_bytes()
        if platform == "paper":
            assert guide.read_bytes() == custom
            # A tracked V4 directory can still contain an earlier distributed copy of the guide.
            previous_guide = saved["config/WIKI.md"]
            assert previous_guide != stock
            guide.write_bytes(previous_guide)
            boot(work, platform, True, game="1.21.11")
            assert guide.read_bytes() == stock
            print("Verified stock guide refresh and administrator notes on an existing V4 installation.", flush=True)
        print(f"Verified {platform} {PREVIOUS} to {version}, backup integrity, preserved data and repeat startup.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("version")
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    args.directory.mkdir(parents=True, exist_ok=False)
    upgrade(args.directory, args.bundle.resolve(), args.version)
