"""Use the same pinned servers for release gating and Modrinth game versions."""
import argparse
import json
from pathlib import Path


INVENTORY = json.loads(Path(__file__).with_name("compatibility.json").read_text())


def game_versions(platform):
    versions = set(INVENTORY["paper"])
    if platform == "velocity":
        versions.update(INVENTORY["velocity_client_backends"])
    return sorted(versions, key=lambda version: tuple(map(int, version.split("."))))


def platform_loaders(platform):
    # One Modrinth version applies every listed loader to every listed game version.
    if platform == "velocity":
        return ["velocity"]
    releases = set(game_versions("paper"))
    return ["paper", *(loader for loader, tested in INVENTORY["additional_backend_tests"].items()
                       if releases and releases.issubset(tested))]


def runtime_matrix():
    rows = []
    for platform in ("paper", "velocity"):
        for version, build in INVENTORY[platform].items():
            server = f"{platform}-{version}"
            entry = {"server": server, "java": str(build["java"])}
            suites = []
            if server == "paper-1.21.11":
                suites.extend({"suite": name} for name in ("network-v3", "network-v4", "network-mixed"))
            if platform == "paper":
                suites.extend({"suite": "client-login", "client": client}
                              for client, test in INVENTORY["velocity_client_backends"].items()
                              if test["backend"] == version)
            rows.extend(entry | suite for suite in suites or [{}])
    return {"include": rows}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", choices=["matrix", "summary"])
    args = parser.parse_args()
    if args.output == "matrix":
        print(json.dumps(runtime_matrix(), separators=(",", ":")))
    else:
        print("| Modrinth loader | Minecraft game versions |")
        print("|---|---|")
        for platform in ("paper", "velocity"):
            print(f"| {', '.join(platform_loaders(platform))} | {', '.join(game_versions(platform))} |")
        print("\nVelocity proxy builds in the runtime checks: " + ", ".join(INVENTORY["velocity"]) + ".")
