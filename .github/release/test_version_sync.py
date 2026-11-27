"""Release preparation and edits are exercised against a disposable copy of the actual reactor."""
from contextlib import chdir, ExitStack
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import prepare
import version_sources as metadata

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / ".github/release/prepare.py"
TARGET = "9.2.0-beta.2"


class SyncTests(unittest.TestCase):
    def setUp(self):
        stack = self.enterContext(ExitStack())
        work = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.paths = [Path("pom.xml"), *(module / "pom.xml" for module in sorted(metadata.MODULES)), metadata.BACKEND, metadata.PROXY]
        for path in self.paths:
            (work / path).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(REPO / path, work / path)
        stack.enter_context(chdir(work))
        metadata.apply("9.1.0", write=True)
        self.changelog = ("## [Unreleased]\n\n## [" + TARGET + "] - 2026-11-27\n\n"
                          "### Fixed\n\n- Corrected the colours shown for homes.\n\n"
                          "## [9.1.0] - 2026-11-26\n\n- Improved home travel.\n")
        Path("CHANGELOG.md").write_text(self.changelog)
        stack.enter_context(patch.dict(os.environ, REQUEST_VERSION="", REQUEST_CHANNEL="", TAG_NAME="",
                                      CHANGE_BASE="", CUSTOM_NOTES="", GITHUB_OUTPUT=""))

    def snapshot(self):
        return {p: p.read_bytes() for p in self.paths}

    def test_latest_version_is_read_only_and_sync_is_idempotent(self):
        # The tool must preserve a third-party dependency with the same old literal, even with CRLF.
        root = Path("pom.xml")
        content = root.read_bytes().replace(b"<version>3.13.0</version>", b"<version>9.1.0</version>")
        root.write_bytes(content.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))
        descriptor = metadata.BACKEND.read_text().replace("version: 9.1.0", 'version: "9.1.0"  # release')
        metadata.BACKEND.write_text(descriptor)
        proxy = metadata.PROXY.read_bytes()
        metadata.PROXY.write_bytes(b'// @Plugin(version = "leave-this-alone")\n' + proxy)
        module = Path("common/pom.xml")
        module.write_text(module.read_text().replace("<artifactId>telehop-common</artifactId>",
                                                     "<artifactId>telehop-common</artifactId><version>9.0.5</version>"))
        before = self.snapshot()
        self.assertEqual(subprocess.check_output([sys.executable, SCRIPT, "version"], text=True), TARGET + "\n")
        self.assertEqual(before, self.snapshot())
        with self.assertRaisesRegex(ValueError, "sync-version"):
            prepare.source_versions(TARGET)
        subprocess.run([sys.executable, SCRIPT, "sync-version"], capture_output=True, check=True)
        self.assertEqual(Path("CHANGELOG.md").read_text(), self.changelog)
        self.assertEqual(root.read_bytes(), before[root].replace(b"<version>9.1.0</version>", b"<version>9.2.0-beta.2</version>", 1))
        self.assertIn('version: "9.2.0-beta.2"  # release', metadata.BACKEND.read_text())
        self.assertIn(b'// @Plugin(version = "leave-this-alone")', metadata.PROXY.read_bytes())
        for path, old in before.items():
            if path == root:
                continue
            self.assertEqual(path.read_bytes(), old.replace(b"9.1.0", TARGET.encode()).replace(b"9.0.5", TARGET.encode()))
        prepare.source_versions(TARGET)
        times = {path: path.stat().st_mtime_ns for path in self.paths}
        self.assertEqual([], metadata.apply(TARGET, write=True))
        self.assertEqual(times, {path: path.stat().st_mtime_ns for path in self.paths})

    def test_invalid_changelog_or_ambiguous_or_incomplete_sources_are_never_partially_changed(self):
        before = self.snapshot()
        Path("CHANGELOG.md").write_text(self.changelog.replace("colours", "colors"))
        process = subprocess.run([sys.executable, SCRIPT, "sync-version"], capture_output=True, text=True)
        self.assertNotEqual(0, process.returncode)
        self.assertIn("UK English", process.stderr)
        self.assertEqual(before, self.snapshot())
        Path("CHANGELOG.md").write_text(self.changelog)
        for path, bad in [(metadata.PROXY, before[metadata.PROXY] + b'\n@Plugin(version="9.1.0")\n'),
                          (metadata.BACKEND, before[metadata.BACKEND] + b'\nversion: []\n'),
                          (Path("pom.xml"), before[Path("pom.xml")].replace(b"<module>common</module>", b"")),
                          (Path("paper/pom.xml"), before[Path("paper/pom.xml")].replace(b"</parent>", b"<version>9.0.0</version></parent>"))]:
            with self.subTest(path=path):
                path.write_bytes(bad)
                expected = self.snapshot()
                with self.assertRaises(ValueError):
                    metadata.apply(TARGET, write=True)
                self.assertEqual(expected, self.snapshot())
                path.write_bytes(before[path])

    def test_next_full_release_needs_only_the_dated_changelog_and_sync(self):
        Path("CHANGELOG.md").write_text(self.changelog.replace(TARGET, "9.1.1"))
        version, _ = prepare.changelog_release()
        self.assertEqual(version, "9.1.1")
        self.assertEqual(set(metadata.apply(version, write=True)), set(self.paths))
        with patch.object(prepare, "git", return_value="a"*40), patch.object(prepare, "previous_release", return_value="v9.1.0"), \
                patch.object(prepare, "check_product_changes"), patch.object(prepare, "notes", return_value=""), \
                patch.dict(os.environ, GITHUB_OUTPUT="outputs"):
            prepare.prepare(True)
            self.assertEqual(Path("outputs").read_text(), "version=9.1.1\nchannel=release\n")
            with patch.dict(os.environ, REQUEST_CHANNEL="beta"), self.assertRaisesRegex(ValueError, "selected channel"):
                prepare.prepare(True)

    def test_replace_failure_restores_prior_files(self):
        before = self.snapshot()
        replace = Path.replace
        fail_once = True

        def fail_on_backend(path, target):
            nonlocal fail_once
            if target == metadata.BACKEND and fail_once:
                fail_once = False
                raise OSError("simulated write failure")
            return replace(path, target)

        with patch.object(Path, "replace", fail_on_backend), self.assertRaisesRegex(OSError, "simulated"):
            metadata.apply(TARGET, write=True)
        self.assertEqual(before, self.snapshot())
        self.assertFalse(list(Path(".").rglob(".telehop-version-*")))

    def test_release_derives_version_and_channel_and_keeps_explicit_mismatch_guards(self):
        with patch.object(prepare, "git", return_value="a"*40), patch.object(prepare, "previous_release", return_value="v9.1.0"), \
                patch.object(prepare, "check_product_changes"), patch.object(prepare, "notes", return_value=""):
            with self.assertRaisesRegex(ValueError, "sync-version"):
                prepare.prepare(True)
            self.assertFalse(Path("release-bundle").exists())
            metadata.apply(TARGET, write=True)
            for tag in ("", "v" + TARGET):
                with self.subTest(tag=tag), patch.dict(os.environ, TAG_NAME=tag, GITHUB_OUTPUT="outputs"):
                    Path("outputs").unlink(missing_ok=True)
                    prepare.prepare(True)
                    self.assertEqual(Path("outputs").read_text(), f"version={TARGET}\nchannel=beta\n")
            for key, wrong in (("REQUEST_VERSION", "9.2.0"), ("REQUEST_CHANNEL", "release"), ("TAG_NAME", "v9.2.0-beta.1")):
                with self.subTest(key=key), patch.dict(os.environ, {key: wrong}), self.assertRaises(ValueError):
                    prepare.prepare(True)
            metadata.BACKEND.write_text(metadata.BACKEND.read_text().replace(TARGET, "9.1.0"))
            metadata.PROXY.write_text(metadata.PROXY.read_text().replace(TARGET, "9.1.0"))
            with self.assertRaises(ValueError) as drift:
                prepare.source_versions(TARGET)
            for path in (metadata.BACKEND, metadata.PROXY):
                self.assertIn(str(path), str(drift.exception))
