import os
from contextlib import chdir
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
from zipfile import ZipFile

import changelog
import ci_status
import prepare


def entry(version, text="- Fixed the colour of home labels.", day="2026-11-23"):
    return f"## [{version}] - {day}\n\n### Fixed\n\n{text}\n"


class ChangelogTests(unittest.TestCase):
    def test_only_the_latest_real_section_can_match(self):
        for text in [entry("2.2.0"), entry("2.4.0") + entry("2.3.0"),
                     "> ## [2.3.0] - 2026-11-23\n> - Fixed the label.\n\n" + entry("2.2.0"),
                     entry("2.2.0", "- Fixed a home.\n\n```md\n" + entry("2.3.0") + "```")]:
            with self.subTest(text=text), self.assertRaisesRegex(ValueError, "requested 2.3.0, latest dated entry"):
                changelog.current_entry(text, "2.3.0")

    def test_duplicate_versions_order_dates_and_placeholders_fail(self):
        for text in [entry("2.3.0") * 2, entry("2.3.0") + entry("2.4.0"), entry("2.3.0", day="2026-02-30"),
                     entry("2.3.0", ""), entry("2.3.0", "- `config.yml`"), entry("2.3.0", "- To be confirmed."),
                     entry("2.3.0", "- Various bug fixes."), entry("2.3.0", "> - Fixed a home."),
                     "## 2.3.0\n- Fixed the issue."]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                changelog.current_entry(text, "2.3.0")

    def test_unreleased_and_upgrade_references_do_not_hide_the_current_entry(self):
        body = changelog.current_entry("## [Unreleased]\n\n" + entry("2.4.0", "- Upgrade from 2.2.0 safely.")
                                       + entry("2.2.0"), "2.4.0")
        self.assertIn("Upgrade from 2.2.0", body)
        self.assertEqual(body.count("### Fixed"), 1)

    def test_spelling_in_visible_prose_including_links_and_historical_changes(self):
        for bad in ["Changed the color near `Color.BLUE`.", "Updated [behavior](https://example.com/colour).",
                    "The catalogue is labeled.", "Use https://example.com/colour to initialize the server.",
                    "Corrected the favroite item.", "The license was canceled."]:
            with self.subTest(bad=bad), self.assertRaisesRegex(ValueError, "UK English spelling"):
                changelog.current_entry(entry("2.4.0") + entry("2.2.0", "- " + bad), "2.4.0")

    def test_code_names_and_url_destinations_keep_their_original_spelling(self):
        text = ("- Changed the colour of [the label](https://example.com/color) using `Color.BLUE`.\n\n"
                "```java\ninitialize();\n```")
        self.assertIn("Color.BLUE", changelog.current_entry(entry("2.4.0", text), "2.4.0"))

    def test_dashes_in_prose_code_and_html_entities_fail(self):
        for dash in ["\u2014", "\u2013", "&mdash;", "&#8212;", "&#x2014;"]:
            with self.subTest(dash=dash), self.assertRaisesRegex(ValueError, "long dash"):
                changelog.checked_notes("2.4.0", f"- Updated the label `a{dash}b`.")

    def test_generated_and_custom_release_bodies_receive_the_same_checks(self):
        for body in ["### Bug fixes\n- Changed the color.", "- Updated player behavior.",
                     "## TeleHop 2.2.0\n- Updated the label.", "## **Release notes for v2.2.0**\n- Updated the label.",
                     "No changes."]:
            with self.subTest(body=body), self.assertRaises(ValueError):
                changelog.checked_notes("2.3.0", body)
        self.assertTrue(changelog.checked_notes("2.3.0", "- Updated the colour.").startswith("## TeleHop 2.3.0\n"))

    def test_date_or_heading_only_edits_do_not_satisfy_product_changes(self):
        with self.assertRaisesRegex(ValueError, "product code"):
            changelog.check_update(entry("2.2.0"), "### Added\n\n- Fixed the colour of home labels.")
        changelog.check_update(entry("2.2.0"), "- Fixed home labels on reconnect.")

    def test_push_checks_generated_commit_notes_before_a_release(self):
        with tempfile.TemporaryDirectory() as tmp, chdir(tmp), patch.dict(os.environ, CHANGE_BASE="", GITHUB_OUTPUT=""):
            Path("pom.xml").write_text("<project><version>3.0.0</version></project>")
            Path("CHANGELOG.md").write_text(entry("3.0.0"))
            with patch.object(prepare, "source_versions"), patch.object(prepare, "notes") as generated:
                generated.return_value = "- List every TeleHop permission in the README."
                prepare.prepare(False)
                generated.assert_called_once_with("3.0.0")
                generated.return_value = "- Update the color in the README."
                with self.assertRaisesRegex(ValueError, "UK English spelling 'color'"):
                    prepare.prepare(False)
            self.assertFalse(Path("release-bundle").exists())


class BuildTests(unittest.TestCase):
    def test_source_checks_reject_child_or_descriptor_drift_without_rewriting_files(self):
        with tempfile.TemporaryDirectory() as tmp, chdir(tmp):
            root = ET.fromstring("<project><version>2.4.0</version><modules><module>common</module></modules></project>")
            files = {"common/pom.xml": "<project><parent><version>2.2.0</version></parent><version>2.3.0</version></project>",
                     "paper/src/main/resources/plugin.yml": "version: 2.2.0\n",
                     "velocity/src/main/java/com/telehop/velocity/NetworkVelocityPlugin.java": '@Plugin(version = "2.2.0")'}
            for name, value in files.items():
                Path(name).parent.mkdir(parents=True)
                Path(name).write_text(value)
            with self.assertRaises(ValueError) as failure:
                prepare.source_versions(root, "2.4.0")
            for name, original in files.items():
                self.assertIn(name, str(failure.exception))
                self.assertEqual(Path(name).read_text(), original)

    def test_missing_skipped_failing_and_empty_suites_fail(self):
        with tempfile.TemporaryDirectory() as tmp, chdir(tmp):
            xml = '<testsuite tests="1" errors="0" failures="0" skipped="0"/>'
            for module in ("common", "paper", "velocity"):
                report = Path(module, "target/surefire-reports/TEST-suite.xml")
                report.parent.mkdir(parents=True)
                with self.subTest(module=module), self.assertRaisesRegex(ValueError, module):
                    prepare.verify_test_reports()
                for key in ("errors", "failures", "skipped", "tests"):
                    with self.subTest(module=module, key=key), self.assertRaisesRegex(ValueError, module):
                        report.write_text(xml.replace('tests="1"', 'tests="0"') if key == "tests"
                                          else xml.replace(f'{key}="0"', f'{key}="1"'))
                        prepare.verify_test_reports()
                report.write_text(xml)
            prepare.verify_test_reports()

    def test_older_paper_can_scan_every_class_in_the_distribution(self):
        with tempfile.TemporaryDirectory() as tmp, ZipFile(Path(tmp, "paper.jar"), "w") as jar:
            jar.writestr("old/Adapter.class", bytes.fromhex("cafebabe00000041"))
            prepare.check_paper_bytecode(jar)
            jar.writestr("new/Adapter.class", bytes.fromhex("cafebabe00000045"))
            with self.assertRaisesRegex(ValueError, "Java 21 limit.*new/Adapter.class"):
                prepare.check_paper_bytecode(jar)

    def test_missing_module_output_and_wrong_embedded_version_fail(self):
        with tempfile.TemporaryDirectory() as tmp, chdir(tmp):
            Path("pom.xml").write_text("<project><modules/></project>")
            with self.assertRaisesRegex(ValueError, "missing from reactor"):
                prepare.verify_reactor("2.4.0")
            with ZipFile("telehop-paper-2.4.0.jar", "w") as jar:
                for name in ["com/telehop/paper/NetworkPaperPlugin.class", "com/telehop/paper/version/VersionAdapter.class",
                             "com/telehop/paper/v1_21/Paper121Adapter.class", "com/telehop/paper/v26_1/Paper261Adapter.class"]:
                    jar.writestr(name, "")
                jar.writestr("plugin.yml", "version: 2.2.0")
            with self.assertRaisesRegex(ValueError, "Incorrect embedded version"):
                prepare.verify_jars("2.4.0", Path("."))

    def test_wrong_release_request_stops_before_build_or_publication(self):
        with tempfile.TemporaryDirectory() as tmp, chdir(tmp):
            pom = Path("pom.xml")
            pom.write_text("<project><version>2.3.0</version></project>")
            Path("CHANGELOG.md").write_text(entry("2.2.0"))
            with patch.dict(os.environ, REQUEST_VERSION="2.3.0", REQUEST_CHANNEL="release", TAG_NAME="",
                            CUSTOM_NOTES="- Updated home labels."), self.assertRaisesRegex(ValueError, "latest dated entry is 2.2.0"):
                prepare.prepare(True)
            self.assertFalse(Path("release-bundle").exists())
            self.assertEqual(pom.read_text(), "<project><version>2.3.0</version></project>")

    def test_requested_version_cannot_override_a_selected_tag(self):
        with patch.dict(os.environ, TAG_NAME="v2.2.0", REQUEST_VERSION="2.3.0"), self.assertRaisesRegex(ValueError, "tag"):
            prepare.prepare(True)


class GithubGateTests(unittest.TestCase):
    def test_only_a_successful_push_and_readiness_job_on_the_exact_commit_pass(self):
        repo, sha = "owner/TeleHop", "a" * 40
        run = {"head_sha": sha, "event": "push", "head_repository": {"full_name": repo}, "id": 2,
               "status": "completed", "conclusion": "success", "html_url": "https://example.com/ci"}
        for changes in [{"conclusion": "failure"}, {"conclusion": "cancelled"}, {"head_sha": "b" * 40},
                        {"event": "pull_request"}, {"head_repository": {"full_name": "other/TeleHop"}}]:
            with self.subTest(changes=changes), patch.object(ci_status, "api", return_value={"workflow_runs": [run | changes]}):
                with self.assertRaises(ValueError):
                    ci_status.ready(repo, sha)
        for result in [None, "skipped", "failure", "success"]:
            jobs = [{"name": "Release readiness", "conclusion": result}] if result else []
            with self.subTest(result=result), patch.object(ci_status, "api", side_effect=[{"workflow_runs": [run]}, {"jobs": jobs}]):
                if result == "success":
                    self.assertTrue(ci_status.ready(repo, sha))
                else:
                    with self.assertRaises(ValueError):
                        ci_status.ready(repo, sha)
        with patch.object(ci_status, "api", return_value={"workflow_runs": [run, run | {"id": 3, "status": "queued"}]}):
            self.assertFalse(ci_status.ready(repo, sha))
