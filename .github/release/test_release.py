import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import prepare
import publish


class ReleaseTests(unittest.TestCase):
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
        version = "2.3.0-beta.1"
        records = []
        uploads = []
        with tempfile.TemporaryDirectory() as tmp:
            assets = Path(tmp)
            for platform in prepare.PLATFORMS:
                (assets / f"telehop-{platform}-{version}.jar").write_bytes(platform.encode())
            (assets / "notes.md").write_text("### Custom notes\n\nRelease description.")
            def api(route, *args):
                if route != "version":
                    return list(records)
                data = publish.json.loads(args[1].removeprefix("data="))
                platform, = data["loaders"]
                uploads.append(platform)
                if uploads == ["paper", "velocity"]:
                    raise RuntimeError("temporary Modrinth failure")
                jar = assets / f"telehop-{platform}-{version}.jar"
                self.assertIn(f"jar=@{jar};type=application/java-archive", args)
                self.assertEqual(data["changelog"], (assets / "notes.md").read_text())
                record = dict(data, id=platform, files=[{"filename": jar.name, "primary": True,
                              "hashes": {"sha512": hashlib.sha512(jar.read_bytes()).hexdigest()}}])
                records.append(record)
                return record
            with patch.dict(os.environ, MODRINTH_PROJECT_ID="proj"), patch.object(publish, "CANONICAL", assets), \
                    patch.object(publish, "mod_api", api):
                with self.assertRaisesRegex(RuntimeError, "temporary"):
                    publish.modrinth(version, "beta")
                publish.modrinth(version, "beta")
                publish.modrinth(version, "beta")
                self.assertEqual(uploads, ["paper", "velocity", "velocity"])
                records[0]["files"][0]["hashes"]["sha512"] = "different"
                with self.assertRaisesRegex(ValueError, "differs"):
                    publish.modrinth(version, "beta")
                self.assertEqual(len(records), 2)

    def test_partial_github_draft_resumes_before_publication(self):
        version = "2.3.0"
        existing = {"name": "TeleHop 2.3.0", "isDraft": True, "isPrerelease": False, "body": "Notes",
                    "assets": [{"name": "telehop-paper-2.3.0.jar", "state": "uploaded"}]}
        with tempfile.TemporaryDirectory() as tmp:
            dist = Path(tmp)
            canonical = dist / "github"
            (dist / "notes.md").write_text("Notes")
            (dist / "telehop-paper-2.3.0.jar").write_bytes(b"original")
            velocity = "telehop-velocity-2.3.0.jar"
            (dist / velocity).write_bytes(b"verified build")
            def run(args, check):
                if args[2] == "download":
                    name = args[args.index("-p") + 1]
                    (canonical / name).write_bytes(b"original" if "paper" in name else b"verified build")
            with patch.dict(os.environ, GITHUB_REPOSITORY="owner/TeleHop"), patch.object(publish, "DIST", dist), \
                    patch.object(publish, "CANONICAL", canonical), patch.object(publish, "git", return_value="sha"), \
                    patch.object(publish, "gh_json", return_value={"object": {"type": "commit", "sha": "sha"}}), \
                    patch.object(publish, "release", return_value=existing), patch.object(publish, "verify_jars"), \
                    patch.object(publish.subprocess, "run", side_effect=run) as calls:
                publish.github(version, "release")
                self.assertEqual([c.args[0][2] for c in calls.call_args_list], ["download", "upload", "download", "edit"])
                self.assertEqual(calls.call_args_list[1].args[0][-1], str(dist / velocity))

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
