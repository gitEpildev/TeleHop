"""Require the region-threaded distribution to reject TeleHop before startup."""
import argparse
from contextlib import ExitStack
from pathlib import Path
import shutil

from boot import Process
from run import download

DIGEST = "f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39"
URL = f"https://fill-data.papermc.io/v1/objects/{DIGEST}/folia-1.21.11-14.jar"


def run(plugin, root, cache):
    root.mkdir(parents=True, exist_ok=False)
    (root / "plugins").mkdir()
    shutil.copy(plugin, root / "plugins")
    download(root / "server.jar", URL, DIGEST)
    if cache:
        shutil.copytree(cache, root / "cache")
    (root / "eula.txt").write_text("eula=true\n")
    (root / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n")
    with ExitStack() as stack:
        server = Process(stack, root, ["java", "-Xmx1G", "-XX:ActiveProcessorCount=4", "-jar", "server.jar", "--nogui"], "stop")
        server.wait(r"Could not load plugin 'TeleHop [^']+' as it is not marked as supporting Folia", seconds=180)
        server.wait(r"Done \(", seconds=300)
        if "Startup complete! Server:" in server.log.read_text() or (root / "plugins/TeleHop").exists():
            raise AssertionError("TeleHop ran startup on an unsupported scheduler")
    print("Folia rejected the plugin before configuration, database or command initialisation.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("plugin", type=Path)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--cache", type=Path)
    args = parser.parse_args()
    run(args.plugin, args.directory, args.cache)
