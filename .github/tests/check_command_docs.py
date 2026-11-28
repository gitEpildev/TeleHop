"""Check the public command and permission references against the Paper configuration."""

import re
from pathlib import Path

import yaml


RESOURCES = Path("paper/src/main/resources")
PERMISSION_PATTERNS = {
    "telehop.spawn.destination.<name>",
    "telehop.warp.<name>",
    "telehop.warps.<number>",
    "telehop.homes.<number>",
    "telehop.command.<id>",
    "telehop.rtp.region.<id>",
    "telehop.rtp.server.<id>",
    "telehop.rtp.world.<id>",
    "telehop.rtp.dimension.<id>",
}


def main():
    permissions = yaml.safe_load((RESOURCES / "plugin.yml").read_text())["permissions"]
    commands = yaml.safe_load((RESOURCES / "internal/legacy/commands.yml").read_text())
    grouped = {}
    for category in sorted((RESOURCES / "commands").glob("*.yml")):
        for root, settings in yaml.safe_load(category.read_text()).items():
            if root in grouped:
                raise SystemExit(f"Duplicate command root {root} in {category}")
            primary = settings["custom-name"]
            grouped[root] = [primary["name"], *primary.get("aliases", [])]
    if grouped != commands:
        raise SystemExit("The packaged command groups disagree with the legacy default labels")
    labels = {label for names in commands.values() for label in names}
    missing = []

    # A release can document new labels and nodes once in its changelog.
    # Only the identifier that leads a reference bullet counts.
    sections = re.findall(r"^### Command and permission additions\n(.*?)(?=^#|\Z)",
                          Path("CHANGELOG.md").read_text(), re.MULTILINE | re.DOTALL)
    additions = set(re.findall(r"^- `([^`]+)` ", "\n".join(sections), re.MULTILINE))
    for name in ("README.md", "telehop.md", "docs/permissions.md", "docs/commands.md"):
        body = Path(name).read_text()
        if name != "docs/commands.md":
            # Require a reference row, so incidental mentions in examples cannot mask an omission.
            rows = set(re.findall(r"^\| `(telehop\.[^`]+)` \|", body, re.MULTILINE))
            for node in sorted((set(permissions) | PERMISSION_PATTERNS) - rows - additions):
                missing.append(f"{name} and changelog additions: missing permission row for {node}")
        if name != "docs/permissions.md":
            documented = set(re.findall(r"`/([\w-]+)(?=[ `])", body))
            for label in sorted(labels - documented):
                if "/" + label not in additions:
                    missing.append(f"{name} and changelog additions: missing command or alias /{label}")

    if missing:
        raise SystemExit("\n".join(missing))
    print(f"Verified {len(permissions)} declared permissions, {len(PERMISSION_PATTERNS)} "
          f"permission patterns, {len(commands)} command roots and {len(labels) - len(commands)} aliases.")


if __name__ == "__main__":
    main()
