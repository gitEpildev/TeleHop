"""Prepare release inputs and inspect the two shaded Maven artifacts."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile

import paper_layout
import version_sources
from artefacts import PLATFORMS, filename
from checksums import manifest
from changelog import VERSION, check_update, checked_notes, newest_entry

DIST = Path("release-bundle")
SECTIONS = {"feat": "New features", "fix": "Bug fixes", "perf": "Performance improvements",
            "refactor": "Internal changes", "docs": "Documentation", "chore": "Maintenance"}
BASELINE = "046b39e650df749c8033eedcb4d5894fc1f46aa5"  # Last pre-automation release, 2.2.0


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def release_version(value, channel):
    match = VERSION.fullmatch(value)
    if not match or len(value) > 32:
        raise ValueError("Use X.Y.Z with an optional -alpha.N or -beta.N suffix (32 characters maximum)")
    suffix = match.group(1)
    if channel and (channel not in ("release", "alpha", "beta") or suffix and suffix != channel):
        raise ValueError("Version suffix and release channel disagree")
    if channel in ("alpha", "beta") and not suffix:
        value += "-" + channel
    if len(value) > 32:
        raise ValueError("Version exceeds Modrinth's 32 character limit")
    return value, channel or suffix or "release"


def previous_release(version):
    tag = "v" + version
    tags = git("tag", "--merged", "HEAD", "--sort=-version:refname").splitlines()
    candidates = [t for t in tags if t != tag and t.startswith("v") and VERSION.fullmatch(t[1:])]
    return git("describe", "--tags", "--abbrev=0", *["--match=" + t for t in candidates]) if candidates else BASELINE


def notes(version):
    base = previous_release(version)
    groups = {name: [] for name in ["Breaking changes", *SECTIONS.values(), "Other changes"]}
    for record in git("log", "--no-merges", "--reverse", "--format=%s%x00%b%x00%h%x1e", base + "..HEAD").split("\x1e"):
        if not record.strip():
            continue
        subject, body, sha = record.strip().split("\0")
        conventional = re.match(r"^(\w+)(?:\([^)]*\))?(!)?:\s*(.*)", subject)
        category, description = "Other changes", subject
        if conventional:
            category = SECTIONS.get(conventional[1], category)
            description = conventional[3]
        breaking = re.findall(r"^BREAKING[ -]CHANGE:\s*(.+)$", subject + "\n" + body, re.M)
        if breaking or conventional and conventional[2]:
            category = "Breaking changes"
            description = " / ".join(breaking) or description
        groups[category].append(f"- {description} (`{sha}`)")
    return "\n\n".join(f"### {k}\n" + "\n".join(v) for k, v in groups.items() if v) or "No new commits since the preceding release."


def changelog_release(expected=None):
    version, entry = newest_entry(Path("CHANGELOG.md").read_text(), expected)
    release_version(version, "")
    return version, entry


def source_versions(version):
    version_sources.apply(version)


def check_product_changes(base, entry):
    if not re.fullmatch(r"[a-f0-9]{40}", base):
        raise ValueError("Expected a full comparison commit SHA.")
    if git("diff", "--name-only", base, "HEAD", "--", "pom.xml", ":(glob)**/pom.xml",
           ":(glob)**/src/main/**", "sql/"):
        if "CHANGELOG.md" in git("ls-tree", "--name-only", base, "--", "CHANGELOG.md").splitlines():
            check_update(git("show", base + ":CHANGELOG.md"), entry)


def prepare(publishing):
    requested = os.getenv("REQUEST_VERSION", "") if publishing else ""
    if publishing and os.getenv("TAG_NAME"):
        tag = os.environ["TAG_NAME"]
        if not tag.startswith("v") or requested and requested != tag[1:]:
            raise ValueError(f"Selected tag {tag} disagrees with the release request {requested!r}.")
        requested = tag[1:]
        if not VERSION.fullmatch(requested):
            raise ValueError(f"Invalid release tag {tag}")
    version, entry = changelog_release(requested or None)
    release, channel = release_version(version, os.getenv("REQUEST_CHANNEL") if publishing else "")
    if release != version:
        raise ValueError(f"The selected channel requires {release}, but the newest changelog entry declares {version}.")
    source_versions(version)

    base = os.getenv("CHANGE_BASE")
    if base == "0" * 40:
        base = git("merge-base", "HEAD", "origin/" + os.environ["DEFAULT_BRANCH"])
    if base:
        check_product_changes(base, entry)

    if publishing:
        check_product_changes(git("rev-parse", previous_release(version) + "^{commit}"), entry)
    custom = os.getenv("CUSTOM_NOTES", "").strip() if publishing else ""
    final = checked_notes(version, custom or (entry + "\n\n" + notes(version)))
    if publishing:
        DIST.mkdir(parents=True, exist_ok=True)
        (DIST / "notes.md").write_text(final)
    if os.getenv("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            print(f"version={version}\nchannel={channel}", file=output)
    print(f"Validated TeleHop {version} ({channel}).")


def verify_reactor(version):
    root = ET.parse("pom.xml").getroot()
    modules = {Path(module.text) for module in root.findall("{*}modules/{*}module")}
    if missing := version_sources.MODULES - modules:
        raise ValueError(f"Required Maven modules missing from reactor: {', '.join(map(str, sorted(missing)))}.")
    for module in sorted(modules):
        pom = ET.parse(module / "pom.xml").getroot()
        if pom.findtext("{*}packaging", "jar") == "jar":
            name = filename(module.name, version) if module.name in PLATFORMS else f"{pom.findtext('{*}artifactId')}-{version}.jar"
            artefact = module / "target" / name
            if not artefact.is_file():
                raise ValueError(f"Missing reactor output {artefact}. Run the full clean verify build.")


def verify_test_reports():
    # A skipped module produces no Surefire reports after a clean build.
    for module in ("common", "paper", "velocity"):
        reports = list(Path(module, "target/surefire-reports").glob("TEST-*.xml"))
        total = 0
        for report in reports:
            suite = ET.parse(report).getroot()
            total += int(suite.attrib["tests"])
            if any(int(suite.attrib[key]) for key in ("errors", "failures", "skipped")):
                raise ValueError(f"{report}: all tests must run and pass before packaging.")
        if total == 0:
            raise ValueError(f"{module}: no executed tests in Surefire reports. Run the full clean verify build.")


def check_paper_bytecode(jar):
    # The original 1.21 remapper inspects even classes selected only on newer Paper versions.
    for entry in jar.infolist():
        if entry.filename.endswith(".class"):
            with jar.open(entry) as stream:
                header = stream.read(8)
            if len(header) != 8 or header[:4] != bytes.fromhex("cafebabe"):
                raise ValueError(f"Invalid class file in backend JAR: {entry.filename}")
            if int.from_bytes(header[6:8], "big") > 65:
                raise ValueError(f"Class exceeds the Java 21 limit in the shared backend JAR: {entry.filename}")


def verify_jars(version, directory=None):
    shared = {"com/telehop/common/db/DatabaseManager.class", "com/telehop/common/config/ConfigurationUpgrade.class",
              "com/telehop/common/messaging/TravelFields.class"}
    required = {
        "paper": shared | {"com/telehop/paper/NetworkPaperPlugin.class", "com/telehop/paper/version/VersionAdapter.class",
                           "com/telehop/paper/v1_21/Paper121Adapter.class", "com/telehop/paper/v26_1/Paper261Adapter.class",
                           "com/telehop/paper/platform/BackendPlatform.class", "com/telehop/paper/config/PaperProfiles.class",
                           "plugin.yml"} | paper_layout.PACKAGED,
        "velocity": shared | {"com/telehop/velocity/NetworkVelocityPlugin.class", "com/telehop/velocity/config/VelocityProfiles.class",
                              "com/telehop/velocity/lib/redis/jedis/Jedis.class", "com/telehop/velocity/lib/yaml/Yaml.class"}
    }
    for platform in PLATFORMS:
        path = (directory or Path(platform, "target")) / filename(platform, version)
        with ZipFile(path) as jar:
            if jar.testzip():
                raise ValueError(f"Invalid production JAR: {path}")
            if platform == "paper":
                descriptor = jar.read("plugin.yml")
                matches = [descriptor[start:end].decode() for start, end in version_sources.backend_version(descriptor)]
            else:
                matches = [json.loads(jar.read("velocity-plugin.json"))["version"]]
            if matches != [version]:
                raise ValueError(f"Incorrect embedded version in {path}: {matches}")
            entries = set(jar.namelist())
            if missing := sorted(required[platform] - entries):
                raise ValueError(f"{path} lacks required runtime files: {', '.join(missing)}")
            forbidden = ("org/bukkit/", "net/luckperms/api/", "com/velocitypowered/api/",
                         "io/papermc/paper/", "com/telehop/" + ("paper/" if platform == "velocity" else "velocity/"))
            if any(name.startswith(forbidden) and name.endswith(".class") for name in entries):
                raise ValueError(f"{path} bundles an external platform API or the other plugin")
            if platform == "paper":
                paper_layout.verify_jar(jar)
                check_paper_bytecode(jar)
        print(f"Verified {path}")


def verify_bundle(directory, version):
    """Check the complete downloaded Actions bundle before starting the live servers."""
    expected = {filename(platform, version) for platform in PLATFORMS} | {"SHA256SUMS", "command-probe.jar"}
    contents = {path.name for path in directory.iterdir()}
    if expected != contents:
        raise ValueError(f"Unexpected bundle contents. Missing {sorted(expected - contents)}, extra {sorted(contents - expected)}")
    if (directory / "SHA256SUMS").read_text(encoding="ascii") != manifest(directory, version):
        raise ValueError("The two downloaded distribution files differ from the build manifest")
    verify_jars(version, directory)


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["version"]:
            print(changelog_release()[0])
        elif sys.argv[1:] == ["sync-version"]:
            version, _ = changelog_release()
            changed = version_sources.apply(version, write=True)
            if changed:
                print(f"Synchronised TeleHop {version} in " + ", ".join(map(str, changed)))
            else:
                print(f"All TeleHop release fields already match {version}.")
        elif len(sys.argv) == 3 and sys.argv[1] == "reactor":
            verify_reactor(sys.argv[2])
            verify_test_reports()
        elif len(sys.argv) == 3 and sys.argv[1] == "verify":
            verify_jars(sys.argv[2])
        elif len(sys.argv) == 4 and sys.argv[1] == "verify-bundle":
            verify_bundle(Path(sys.argv[2]), sys.argv[3])
        elif sys.argv[1:] in (["check"], ["prepare"]):
            prepare(sys.argv[1] == "prepare")
        else:
            sys.exit("Use version, sync-version, check, prepare, reactor VERSION, verify VERSION or verify-bundle DIRECTORY VERSION.")
    except (ValueError, OSError, BadZipFile, ET.ParseError) as error:
        sys.exit(str(error))
