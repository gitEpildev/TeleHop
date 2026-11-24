"""Publish verified assets, resuming each platform independently on a retry."""
import hashlib
import json
import os
import re
import subprocess
import sys

from compatibility import game_versions
from prepare import DIST, PLATFORMS, git, release_version, verify_jars

CANONICAL = DIST / "github"


def gh_json(args, missing=None):
    result = subprocess.run(["gh", *args], capture_output=True, text=True)
    if result.returncode:
        if missing and missing in result.stderr:
            return None
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout)


def release(tag):
    return gh_json(["release", "view", tag, "--json", "name,body,assets,isDraft,isPrerelease,targetCommitish"],
                   missing="release not found")


def github(version, channel):
    tag = "v" + version
    sha = git("rev-parse", "HEAD")
    title = "TeleHop " + version
    repo = "repos/" + os.environ["GITHUB_REPOSITORY"]
    ref = gh_json(["api", f"{repo}/git/ref/tags/{tag}"], missing="HTTP 404")
    existing = release(tag)
    if ref:
        obj = ref["object"]
        while obj["type"] == "tag":
            obj = gh_json(["api", f"{repo}/git/tags/{obj['sha']}"])["object"]
        if obj["sha"] != sha:
            raise ValueError(f"{tag} points to a different commit. Retry the original workflow run.")
    elif existing:
        raise ValueError(f"Existing release for {tag} has no matching tag")
    else:
        gh_json(["api", "-X", "POST", repo + "/git/refs", "-f", "ref=refs/tags/" + tag, "-f", "sha=" + sha])

    if not existing:
        args = ["gh", "release", "create", tag, "--verify-tag", "--target", sha, "--draft",
                "--title", title, "--notes-file", str(DIST / "notes.md")]
        if channel != "release":
            args.append("--prerelease")
        subprocess.run(args, check=True)
        existing = release(tag)
    if existing["name"] != title or existing["isPrerelease"] != (channel != "release"):
        raise ValueError(f"Existing GitHub release conflicts with requested title or channel: {tag}")

    if existing["body"].replace("\r\n", "\n").rstrip("\n") != (DIST / "notes.md").read_text().rstrip("\n"):
        raise ValueError(f"GitHub notes for {tag} differ from the validated notes. Retry the original release run.")

    CANONICAL.mkdir(parents=True, exist_ok=True)
    assets = {asset["name"]: asset for asset in existing["assets"]}
    for platform in PLATFORMS:
        name = f"telehop-{platform}-{version}.jar"
        incomplete = name in assets and assets[name].get("state", "").lower() == "starter"
        if name not in assets or incomplete:
            if not existing["isDraft"]:
                raise ValueError(f"Published release {tag} is missing {name}")
            upload = ["gh", "release", "upload", tag, str(DIST / name)]
            if incomplete:
                upload.append("--clobber")
            subprocess.run(upload, check=True)
        subprocess.run(["gh", "release", "download", tag, "-p", name, "-D", str(CANONICAL), "--clobber"], check=True)
        if existing["isDraft"]:
            if (DIST / name).read_bytes() != (CANONICAL / name).read_bytes():
                raise ValueError(f"GitHub download differs from the verified build: {name}")
    verify_jars(version, CANONICAL)
    (CANONICAL / "notes.md").write_text(existing["body"])
    if existing["isDraft"]:
        subprocess.run(["gh", "release", "edit", tag, "--draft=false"], check=True)
    print(f"GitHub release {tag} is ready with both JARs")


def mod_api(route, *args):
    token = os.environ.get("MODRINTH_TOKEN", "")
    if not token:
        raise ValueError("Configure the MODRINTH_TOKEN Actions secret")
    result = subprocess.run(["curl", "--silent", "--show-error", "--fail-with-body", "--max-time", "180",
                             "--user-agent", "gitEpildev/TeleHop release workflow", "--header", "@-",
                             *args, "https://api.modrinth.com/v2/" + route],
                            input=f"Authorization: {token}\n", text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"Modrinth {route}: {result.stderr.strip()} {result.stdout[:450]}")
    return json.loads(result.stdout)


def project():
    project_id = os.getenv("MODRINTH_PROJECT_ID", "")
    if not re.fullmatch("[A-Za-z0-9]+", project_id):
        raise ValueError("Set MODRINTH_PROJECT_ID to the project's ID from Modrinth")
    return project_id


def check_modrinth_versions(tags):
    available = {tag["version"] for tag in tags if tag["version_type"] == "release"}
    missing = set(game_versions("velocity")) - available
    if missing:
        raise ValueError("Missing Minecraft releases in Modrinth's catalogue: " + ", ".join(sorted(missing)))


def validate_mod(metadata, record, jar_hash, name):
    for key in ("project_id", "version_number", "version_type", "name", "changelog", "loaders"):
        if record.get(key) != metadata[key]:
            raise ValueError(f"Conflicting Modrinth {key} for {name}")
    if set(record["game_versions"]) != set(metadata["game_versions"]):
        raise ValueError(f"Conflicting Modrinth game versions for {name}")
    if not any(f["primary"] and f["filename"] == name and f["hashes"]["sha512"] == jar_hash for f in record["files"]):
        raise ValueError(f"Modrinth primary JAR differs from GitHub: {name}")


def modrinth(version, channel):
    project_id = project()
    records = mod_api(f"project/{project_id}/version")
    changelog = (CANONICAL / "notes.md").read_text()
    for platform in PLATFORMS:
        name = f"telehop-{platform}-{version}.jar"
        jar = CANONICAL / name
        digest = hashlib.sha512(jar.read_bytes()).hexdigest()
        metadata = {"project_id": project_id, "name": f"TeleHop {version} ({platform.title()})",
                    "version_number": version, "version_type": channel, "changelog": changelog,
                    "loaders": [platform], "game_versions": game_versions(platform), "dependencies": [],
                    "file_parts": ["jar"], "primary_file": "jar", "featured": False}
        matches = [v for v in records if v["version_number"] == version and platform in v["loaders"]]
        if len(matches) > 1:
            raise ValueError(f"Multiple Modrinth entries already exist for {version} ({platform})")
        if matches:
            record = matches[0]
        else:
            record = mod_api("version", "--form-string", "data=" + json.dumps(metadata),
                             "--form", f"jar=@{jar};type=application/java-archive")
        validate_mod(metadata, record, digest, name)
        print(f"Verified Modrinth {platform} version {record['id']} ({name})")


if __name__ == "__main__":
    version, channel = release_version(os.environ["VERSION"], os.environ["CHANNEL"])
    if sys.argv[1] == "preflight":
        project_id = project()
        mod_api("user")
        check_modrinth_versions(mod_api("tag/game_version"))
        if mod_api("project/" + project_id)["id"] != project_id:
            raise ValueError("MODRINTH_PROJECT_ID must be a project ID")
        # Stop before creating a GitHub release for a historical Modrinth-only version.
        if not release("v" + version) and any(v["version_number"] == version for v in mod_api(f"project/{project_id}/version")):
            raise ValueError(f"{version} already exists on Modrinth. Choose an unused version.")
    elif sys.argv[1] == "github":
        github(version, channel)
    elif sys.argv[1] == "modrinth":
        modrinth(version, channel)
    else:
        sys.exit("Expected preflight, github or modrinth")
