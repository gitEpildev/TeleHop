"""Resolve one BuildTools revision per exact game release and verify its upstream source."""
import argparse
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

INVENTORY = Path(__file__).resolve().parents[2] / "release/compatibility.json"


def write_maven_settings(destination):
    # BuildTools starts its own Maven outside our reactor. Give it the same repositories and checksum rules.
    pom = Path(__file__).resolve().parents[3] / "pom.xml"
    profile = ET.parse(pom).find("./m:profiles/m:profile[m:id='github-actions']", {"m": "http://maven.apache.org/POM/4.0.0"})
    if profile is None:
        raise ValueError("Missing github-actions repository profile in the root POM")
    for node in profile.iter():
        node.tag = node.tag.split("}")[-1]
    settings = ET.Element("settings")
    ET.SubElement(settings, "profiles").append(profile)
    ET.ElementTree(settings).write(destination, encoding="utf-8", xml_declaration=True)


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
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--verify", nargs=2, metavar=("BUILD_DIRECTORY", "GAME"))
    mode.add_argument("--maven-settings", type=Path, metavar="FILE")
    args = parser.parse_args()
    if args.verify:
        verify_sources(Path(args.verify[0]), args.verify[1])
    elif args.maven_settings:
        write_maven_settings(args.maven_settings)
    else:
        print(json.dumps(matrix(), separators=(",", ":")))
