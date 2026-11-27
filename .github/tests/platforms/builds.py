"""Resolve one BuildTools revision per exact game release and verify its upstream source."""
import argparse
import json
from pathlib import Path
import subprocess

INVENTORY = Path(__file__).resolve().parents[2] / "release/compatibility.json"


def matrix():
    pins = json.loads(INVENTORY.read_text())["buildtools"]
    return {"include": [{"distribution": "BuildTools", "game": game, **pin} for game, pin in pins.items()]
            + [{"distribution": "Purpur", "game": "1.21.11", "java": 21, "client_schema": "1.21.11"}]}


def verify_sources(root, game):
    pin = json.loads(INVENTORY.read_text())["buildtools"][game]
    build_data = root / "BuildData"
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=build_data, text=True).strip()
    actual = json.loads((build_data / "info.json").read_text())["minecraftVersion"].split("_")[0]
    if actual != game or revision != pin["build_data"]:
        raise ValueError(f"BuildTools resolved {actual} at {revision}, expected {game} at {pin['build_data']}")
    for distribution in ("craftbukkit", "spigot"):
        if not (root / f"{distribution}-{game}.jar").is_file():
            raise FileNotFoundError(f"BuildTools did not produce {distribution}-{game}.jar")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", nargs=2, metavar=("BUILD_DIRECTORY", "GAME"))
    args = parser.parse_args()
    if args.verify:
        verify_sources(Path(args.verify[0]), args.verify[1])
    else:
        print(json.dumps(matrix(), separators=(",", ":")))
