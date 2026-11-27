"""Verify the editable Paper files in the source, production JAR and disposable installations."""
import argparse
from collections import Counter
from pathlib import Path
import posixpath
import re
from urllib.parse import unquote, urlsplit
from zipfile import ZipFile

SOURCE = Path(__file__).resolve().parents[2] / "paper/src/main/resources"
BASE = {"general.yml", "spawns.yml", "features.yml", "teleport.yml", "rtp.yml", "tpa.yml", "home.yml", "respawn.yml"}
PROFILES = {"single": BASE, "multi": BASE | {"messaging.yml", "regions.yml"}, "custom": BASE | {"messaging.yml", "regions.yml"}}
GROUPS = ("admin", "back", "homes", "hub", "lastlocation", "player-warps", "playerinfo", "rtp", "spawn", "teleport", "tpa", "warps")
LOCALES = ("de", "en", "es", "nl", "pl", "zh")
GUIDE = "config/WIKI.md"
EDITABLE = {"config.yml", "config/database.yml", GUIDE}
EDITABLE.update(f"config/{profile}/{file}" for profile, files in PROFILES.items() for file in files)
EDITABLE.update(f"commands/{group}.yml" for group in GROUPS)
EDITABLE.update(f"languages/{language}.yml" for language in LOCALES)
RUNTIME = {"storage.yml", "migrations/state.yml", "migrations/history.log", "migrations/backups"}
LEGACY = "internal/legacy/commands.yml"
PACKAGED = EDITABLE | {LEGACY}


def check_names(names, expected, origin):
    actual = set(names)
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing or extra:
        raise ValueError(f"{origin}: incorrect Paper layout. Missing {missing}. Unexpected {extra}.")


def scoped(name):
    root_yaml = "/" not in name and name.endswith((".yml", ".yaml")) and name != "plugin.yml"
    return root_yaml or name.startswith(("config/", "commands/", "languages/", "internal/"))


def generated(name):
    return name in {"storage.yml", "config.yml.old", ".telehop-legacy-migration"} or name.startswith(("migrations/", ".telehop-legacy-migration/"))


def files(root):
    return {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()}


def verify_source(source=SOURCE):
    names = files(source)
    check_names((name for name in names if scoped(name) or generated(name)), PACKAGED, source)
    # An installed guide must still work when the operator has no repository checkout.
    guide = (source / GUIDE).read_text(encoding="utf-8")
    installed = EDITABLE | RUNTIME
    directories = {parent.as_posix() for path in installed for parent in Path(path).parents}
    for href in re.findall(r"\[[^\]]*\]\(([^)\s]+)\)", guide):
        target = urlsplit(href.strip("<>"))
        if target.scheme or target.netloc or not target.path:
            continue
        installed_path = posixpath.normpath(posixpath.join(posixpath.dirname(GUIDE), unquote(target.path)))
        if installed_path not in installed and installed_path not in directories:
            raise ValueError(f"{GUIDE}: link {href} does not resolve inside the installed layout")


def verify_jar(jar, source=SOURCE):
    entries = Counter(name for name in jar.namelist() if not name.endswith("/") and (scoped(name) or generated(name)))
    check_names(entries, PACKAGED, jar.filename)
    for name, count in entries.items():
        if count != 1:
            raise ValueError(f"{jar.filename}: duplicated Paper template {name}")
        if jar.read(name) != (source / name).read_bytes():
            raise ValueError(f"{jar.filename}: {name} differs from the checked-out template")


def verify_install(root, jar_path):
    # Ignore the contents of recovery archives, which intentionally retain obsolete file names.
    editable = {f"{directory}/{name}" for directory in ("config", "commands", "languages", "internal")
                for name in files(root / directory)}
    editable.update(path.name for path in root.iterdir() if path.is_file() and scoped(path.name) and not generated(path.name))
    check_names(editable, EDITABLE, root)
    for name in ("storage.yml", "migrations/state.yml", "migrations/history.log"):
        if not (root / name).is_file():
            raise ValueError(f"{root}: missing generated state {name}")
    if not (root / "migrations/backups").is_dir() or (root / "migrations/work").exists():
        raise ValueError(f"{root}: incomplete or unfinished migration")
    with ZipFile(jar_path) as jar:
        if (root / GUIDE).read_bytes() != jar.read(GUIDE):
            raise ValueError(f"{root}: the stock installed {GUIDE} differs from {jar_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--install", type=Path, help="Disposable plugin data folder using the stock guide")
    args = parser.parse_args()
    verify_source(args.source)
    if args.jar:
        with ZipFile(args.jar) as jar:
            verify_jar(jar, args.source)
    if args.install:
        if not args.jar:
            parser.error("--install requires --jar to compare the installed guide")
        verify_install(args.install, args.jar)
    print("Verified Paper layout with 8 SINGLE, 10 MULTI and 10 CUSTOM files, 12 command groups and 6 languages.")


if __name__ == "__main__":
    main()
