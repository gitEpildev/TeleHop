import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import artefacts
import compatibility
import prepare
import publish


class ReleaseTests(unittest.TestCase):
    def test_every_supported_release_has_matching_server_or_client_coverage(self):
        all_clients = {"1.21", *(f"1.21.{patch}" for patch in range(1, 12)), "26.1", "26.1.1", "26.1.2", "26.2", "26.3"}
        paper = set(compatibility.game_versions("paper"))
        self.assertEqual(paper, all_clients - {"1.21.2", "26.1"})
        self.assertEqual(set(compatibility.game_versions("velocity")), all_clients)
        rows = compatibility.runtime_matrix()["include"]
        self.assertEqual({r["server"][6:] for r in rows if r["server"].startswith("paper-")}, paper)
        self.assertIn({"server": "paper-1.21.3", "java": "21", "suite": "client-login", "client": "1.21.2"}, rows)
        self.assertIn({"server": "paper-26.1.2", "java": "25", "suite": "client-login", "client": "26.1"}, rows)
        tags = [{"version": v, "version_type": "release"} for v in all_clients]
        publish.check_modrinth_versions(tags)
        with self.assertRaisesRegex(ValueError, "26.2"):
            publish.check_modrinth_versions([t for t in tags if t["version"] != "26.2"])

    def test_channels_and_invalid_inputs(self):
        for channel in ("alpha", "beta"):
            self.assertEqual(prepare.release_version("2.3.0", channel), (f"2.3.0-{channel}", channel))
            self.assertEqual(prepare.release_version(f"2.3.0-{channel}.1", ""), (f"2.3.0-{channel}.1", channel))
        self.assertEqual(prepare.release_version("2.3.0", ""), ("2.3.0", "release"))
        for value, channel in [("2.03.0", ""), ("2.3.0-beta.01", ""), ("2.3.0-beta", "release"),
                               ("$(touch bad)", ""), ("2.3.0-beta.1", "alpha")]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                prepare.release_version(value, channel)

    def test_notes_since_previous_tag(self):
        with tempfile.TemporaryDirectory() as tmp:
            def git(*args):
                return subprocess.check_output(["git", "-C", tmp, *args], text=True).strip()
            git("init", "-q")
            git("config", "user.email", "tests@example.invalid")
            git("config", "user.name", "TeleHop tests")
            git("commit", "--allow-empty", "-m", "old entry")
            git("tag", "v2.2.0")
            for key in prepare.SECTIONS:
                git("commit", "--allow-empty", "-m", f"{key}(paper): {key} entry")
            git("commit", "--allow-empty", "-m", "feat!: new format", "-m", "BREAKING CHANGE: migrate data")
            git("tag", "v2.3.0")
            with patch.object(prepare, "git", git):
                notes = prepare.notes("2.3.0")
            for section in [*prepare.SECTIONS.values(), "Breaking changes"]:
                self.assertIn("### " + section, notes)
            self.assertIn("migrate data", notes)
            self.assertNotIn("old entry", notes)

    def test_retry_after_one_modrinth_upload(self):
        version = "4.0.0-beta.1"
        records = []
        uploads = []
        games = compatibility.game_versions("paper")
        coverage = {"bukkit": games, "spigot": games, "purpur": games[:1]}
        with tempfile.TemporaryDirectory() as tmp, patch.dict(compatibility.INVENTORY, additional_backend_tests=coverage):
            assets = Path(tmp)
            for platform in prepare.PLATFORMS:
                (assets / artefacts.filename(platform, version)).write_bytes(platform.encode())
            (assets / "notes.md").write_text("### Custom notes\n\nRelease description.")
            def api(route, *args):
                if route != "version":
                    return list(records)
                data = publish.json.loads(args[1].removeprefix("data="))
                platform = "velocity" if "velocity" in data["loaders"] else "paper"
                self.assertEqual(data["loaders"], compatibility.platform_loaders(platform))
                expected_title = ("TeleHop 4.0.0-beta.1 - Bukkit backend (Paper, CraftBukkit and Spigot)"
                                  if platform == "paper" else "TeleHop 4.0.0-beta.1 (Velocity)")
                self.assertEqual(expected_title, data["name"])
                uploads.append(platform)
                self.assertEqual(data["game_versions"], compatibility.game_versions(platform))
                if uploads == ["paper", "velocity"]:
                    raise RuntimeError("temporary Modrinth failure")
                jar = assets / artefacts.filename(platform, version)
                self.assertIn(f"jar=@{jar};type=application/java-archive", args)
                self.assertEqual(data["changelog"], (assets / "notes.md").read_text())
                record = dict(data, id=platform, files=[{"filename": jar.name, "primary": True,
                              "hashes": {"sha512": hashlib.sha512(jar.read_bytes()).hexdigest()}}])
                record["loaders"] = list(reversed(record["loaders"]))
                records.append(record)
                return record
            with patch.dict(os.environ, MODRINTH_PROJECT_ID="proj"), patch.object(publish, "CANONICAL", assets), \
                    patch.object(publish, "mod_api", api):
                with self.assertRaisesRegex(RuntimeError, "temporary"):
                    publish.modrinth(version, "beta")
                publish.modrinth(version, "beta")
                publish.modrinth(version, "beta")
                self.assertEqual(uploads, ["paper", "velocity", "velocity"])
                loaders = records[0]["loaders"]
                records[0]["loaders"] = ["bukkit"]
                with self.assertRaisesRegex(ValueError, "Conflicting Modrinth loaders"):
                    publish.modrinth(version, "beta")
                records[0]["loaders"] = loaders
                records[0]["files"].append({"filename": "extra.jar", "primary": False, "hashes": {}})
                with self.assertRaisesRegex(ValueError, "Unexpected Modrinth JARs"):
                    publish.modrinth(version, "beta")
                records[0]["files"].pop()
                records[0]["files"][0]["hashes"]["sha512"] = "different"
                with self.assertRaisesRegex(ValueError, "differs"):
                    publish.modrinth(version, "beta")
                self.assertEqual(len(records), 2)

    def test_bukkit_name_starts_with_v4_and_historical_names_are_stable(self):
        self.assertEqual(artefacts.filename("paper", "2.3.0"), "telehop-paper-2.3.0.jar")
        self.assertEqual(artefacts.filename("paper", "3.0.1"), "telehop-paper-3.0.1.jar")
        self.assertEqual(artefacts.filename("paper", "4.0.0"), "telehop-bukkit-4.0.0.jar")
        self.assertEqual(artefacts.filename("paper", "5.0.0-alpha.1"), "telehop-bukkit-5.0.0-alpha.1.jar")
        self.assertEqual(artefacts.filename("velocity", "4.0.0"), "telehop-velocity-4.0.0.jar")
        self.assertEqual(artefacts.modrinth_title("paper", "3.0.1", ["paper"]), "TeleHop 3.0.1 (Paper)")
        self.assertEqual(artefacts.modrinth_title("paper", "4.0.0", ["paper", "spigot"]),
                         "TeleHop 4.0.0 - Bukkit backend (Paper and Spigot)")

    def test_existing_v4_draft_with_a_third_jar_is_never_published(self):
        existing = {"name": "TeleHop 4.0.0", "isDraft": True, "isPrerelease": False, "body": "Notes",
                    "assets": [{"name": "telehop-paper-4.0.0.jar", "state": "uploaded"}]}
        with tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, GITHUB_REPOSITORY="owner/TeleHop"), \
                patch.object(publish, "DIST", Path(tmp)), patch.object(publish, "CANONICAL", Path(tmp, "github")), \
                patch.object(publish, "git", return_value="sha"), \
                patch.object(publish, "gh_json", return_value={"object": {"type": "commit", "sha": "sha"}}), \
                patch.object(publish, "release", return_value=existing), patch.object(publish.subprocess, "run") as run:
            Path(tmp, "notes.md").write_text("Notes")
            with self.assertRaisesRegex(ValueError, "Unexpected release JARs"):
                publish.github("4.0.0", "release")
            run.assert_not_called()

    def test_a_loader_needs_evidence_for_the_whole_published_game_range(self):
        with patch.dict(compatibility.INVENTORY, additional_backend_tests={"spigot": ["1.21.11"]}):
            self.assertEqual(["paper"], compatibility.platform_loaders("paper"))
        self.assertEqual(["velocity"], compatibility.platform_loaders("velocity"))
        with patch.dict(compatibility.INVENTORY, additional_backend_tests={"spigot": compatibility.game_versions("paper")}):
            self.assertEqual(["paper", "spigot"], compatibility.platform_loaders("paper"))

    def test_wrong_manifest_stops_a_draft_and_a_modified_download_stops_a_retry(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            canonical = root / "github"
            canonical.mkdir()
            for loader in prepare.PLATFORMS:
                (canonical / artefacts.filename(loader, "4.0.0")).write_bytes(loader.encode())
            (root / "SHA256SUMS").write_text("wrong")
            with patch.object(publish, "DIST", root), patch.object(publish, "CANONICAL", canonical), patch.object(publish.subprocess, "run") as api:
                with self.assertRaisesRegex(ValueError, "Build manifest"):
                    publish.github_checksums("4.0.0", "v4.0.0", True, {})
                api.assert_not_called()
                expected = publish.manifest(canonical, "4.0.0")
                self.assertEqual(expected.splitlines(), [
                    hashlib.sha256(b"paper").hexdigest() + "  telehop-bukkit-4.0.0.jar",
                    hashlib.sha256(b"velocity").hexdigest() + "  telehop-velocity-4.0.0.jar"])
                (canonical / "SHA256SUMS").write_text("wrong")
                with self.assertRaisesRegex(ValueError, "checksum manifest differs"):
                    publish.github_checksums("4.0.0", "v4.0.0", False, {"SHA256SUMS": {}})
                (canonical / "SHA256SUMS").write_text(expected)
                publish.github_checksums("4.0.0", "v4.0.0", False, {"SHA256SUMS": {}})

    def test_partial_github_draft_resumes_before_publication(self):
        for version in ("2.3.0", "4.0.0"):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as tmp:
                backend = artefacts.filename("paper", version)
                velocity = artefacts.filename("velocity", version)
                existing = {"name": f"TeleHop {version}", "isDraft": True, "isPrerelease": False, "body": "Notes",
                            "assets": [{"name": backend, "state": "uploaded"}]}
                dist = Path(tmp)
                canonical = dist / "github"
                (dist / "notes.md").write_text("Notes")
                (dist / backend).write_bytes(b"original")
                (dist / velocity).write_bytes(b"verified build")
                uploads = [velocity]
                if version == "4.0.0":
                    (dist / "SHA256SUMS").write_text(publish.manifest(dist, version))
                    uploads.append("SHA256SUMS")

                def run(args, check):
                    if args[2] == "download":
                        name = args[args.index("-p") + 1]
                        (canonical / name).write_bytes((dist / name).read_bytes())

                with patch.dict(os.environ, GITHUB_REPOSITORY="owner/TeleHop"), patch.object(publish, "DIST", dist), \
                        patch.object(publish, "CANONICAL", canonical), patch.object(publish, "git", return_value="sha"), \
                        patch.object(publish, "gh_json", return_value={"object": {"type": "commit", "sha": "sha"}}), \
                        patch.object(publish, "release", return_value=existing), patch.object(publish, "verify_jars"), \
                        patch.object(publish.subprocess, "run", side_effect=run) as calls:
                    publish.github(version, "release")
                    sent = [Path(c.args[0][-1]).name for c in calls.call_args_list if c.args[0][2] == "upload"]
                    self.assertEqual(sent, uploads)
                    self.assertEqual({p.name for p in canonical.glob("*.jar")}, {backend, velocity})
                    self.assertEqual(calls.call_args_list[-1].args[0][2], "edit")

    def test_existing_github_release_uses_original_assets_and_body(self):
        existing = {"name": "TeleHop 2.3.0", "isDraft": False, "isPrerelease": False, "body": "Original",
                    "assets": [{"name": f"telehop-{p}-2.3.0.jar"} for p in prepare.PLATFORMS]}
        with tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, GITHUB_REPOSITORY="owner/TeleHop"), \
                patch.object(publish, "DIST", Path(tmp)), patch.object(publish, "CANONICAL", Path(tmp, "github")), \
                patch.object(publish, "git", return_value="sha"), \
                patch.object(publish, "gh_json", return_value={"object": {"type": "commit", "sha": "sha"}}), \
                patch.object(publish, "release", return_value=existing), patch.object(publish, "verify_jars"), \
                patch.object(publish.subprocess, "run") as run:
            Path(tmp, "notes.md").write_text("Reviewed content")
            with self.assertRaisesRegex(ValueError, "differ from the validated notes"):
                publish.github("2.3.0", "release")
            run.assert_not_called()
            Path(tmp, "notes.md").write_text("Original")
            publish.github("2.3.0", "release")
            self.assertEqual([c.args[0][2] for c in run.call_args_list], ["download", "download"])
            self.assertEqual(Path(tmp, "github/notes.md").read_text(), "Original")
            with patch.object(publish, "git", return_value="wrong"), self.assertRaisesRegex(ValueError, "different commit"):
                publish.github("2.3.0", "release")
