import shutil
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

import paper_layout as layout


class PaperLayoutTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.resources = self.root / "source"
        self.jar = self.root / "paper.jar"
        for name in layout.PACKAGED:
            path = self.resources / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"fixture {name}\n")
        self.package()

    def package(self):
        with ZipFile(self.jar, "w") as jar:
            for name in layout.files(self.resources):
                jar.write(self.resources / name, name)

    def test_exact_profiles_reject_a_flat_duplicate_and_generated_state(self):
        layout.verify_source(self.resources)
        for name in ("config/general.yml", "config/single/messaging.yml", "storage.yml", "migrations/state.yml"):
            with self.subTest(name=name):
                path = self.resources / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
                with self.assertRaisesRegex(ValueError, "Unexpected"):
                    layout.verify_source(self.resources)
                path.unlink()
        (self.resources / "languages/pl.yml").unlink()
        with self.assertRaisesRegex(ValueError, "Missing.*languages/pl.yml"):
            layout.verify_source(self.resources)

    def test_packaging_rejects_old_guide_bytes_and_unexpected_editable_groups(self):
        with ZipFile(self.jar) as jar:
            layout.verify_jar(jar, self.resources)
        (self.resources / layout.GUIDE).write_text("Updated setup guide")
        with ZipFile(self.jar) as jar, self.assertRaisesRegex(ValueError, "WIKI.md differs"):
            layout.verify_jar(jar, self.resources)
        (self.resources / "commands/unused.yml").touch()
        self.package()
        with ZipFile(self.jar) as jar, self.assertRaisesRegex(ValueError, "Unexpected.*unused.yml"):
            layout.verify_jar(jar, self.resources)

    def test_guide_links_resolve_from_the_installed_config_directory(self):
        guide = self.resources / layout.GUIDE
        guide.write_text("[Selector](../config.yml), [hub](../commands/hub.yml) and [backups](../migrations/backups/)")
        layout.verify_source(self.resources)
        for link in ("general.yml", "../../README.md", "../internal/legacy/commands.yml"):
            with self.subTest(link=link):
                guide.write_text(f"[Settings]({link})")
                with self.assertRaisesRegex(ValueError, "does not resolve inside the installed layout"):
                    layout.verify_source(self.resources)

    def test_installed_guide_matches_jar_and_legacy_archives_are_separate(self):
        data = self.root / "plugins/TeleHop"
        shutil.copytree(self.resources, data, ignore=shutil.ignore_patterns("internal"))
        archive = data / "migrations/backups/example/original/config"
        archive.mkdir(parents=True)
        (archive / "general.yml").write_text("original")
        for name in ("storage.yml", "migrations/state.yml", "migrations/history.log"):
            (data / name).touch()
        layout.verify_install(data, self.jar)
        (data / layout.GUIDE).write_text("Stale stock guide")
        with self.assertRaisesRegex(ValueError, "stock installed.*WIKI.md"):
            layout.verify_install(data, self.jar)
        (data / "storage.yml").rename(data / "config/single/storage.yml")
        with self.assertRaisesRegex(ValueError, "Unexpected.*storage.yml"):
            layout.verify_install(data, self.jar)
