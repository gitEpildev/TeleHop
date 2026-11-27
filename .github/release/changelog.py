"""Validate the dated release history and the Markdown sent to both platforms."""
from datetime import date
from html import unescape
import json
from pathlib import Path
import re
import subprocess

NUMBER = r"(?:0|[1-9][0-9]*)"
VERSION = re.compile(rf"{NUMBER}\.{NUMBER}\.{NUMBER}(?:-(alpha|beta)(?:\.{NUMBER})?)?")
HEADING = re.compile(rf"\[(?P<version>{VERSION.pattern})\] - (?P<date>\d{{4}}-\d{{2}}-\d{{2}})")
DRAFT = re.compile(r"\b(?:TBD|TODO|FIXME|lorem ipsum|coming soon|to be (?:confirmed|determined))\b", re.I)
CHECKER = Path(__file__).with_name("check-prose.mjs")


def version_key(version):
    core, _, prerelease = version.partition("-")
    channel, _, number = prerelease.partition(".")
    return (*map(int, core.split(".")), {"alpha": 0, "beta": 1, "": 2}[channel], int(number or -1))


def markdown(source, text, *, lint=True):
    parsed = json.loads(subprocess.check_output(["node", CHECKER], input=text, text=True))
    if lint:
        errors = [(e["line"], e["message"]) for e in parsed["issues"]]
        for line, content in enumerate(text.splitlines(), 1):
            if re.search(r"[\u2013\u2014]", unescape(content)):
                errors.append((line, "Use a regular hyphen, comma or full stop in place of the long dash."))
        if errors:
            raise ValueError("\n".join(f"{source}:{line}: {message}" for line, message in sorted(errors)))
    return parsed


def check_content(source, text, bullets):
    generic = {"no changes", "no new changes", "minor bug fixes", "various bug fixes", "bump the version",
               "update the version", "fixes and improvements"}
    if not any(len(b["text"].split()) >= 3 and b["text"].casefold() not in generic for b in bullets):
        raise ValueError(f"{source}: include a bullet describing a concrete change in words.")
    if DRAFT.search(text):
        raise ValueError(f"{source}: replace unfinished placeholder text with the actual change.")


def newest_entry(text, expected=None):
    source = "CHANGELOG.md"
    parsed = markdown(source, text)
    headings = [h for h in parsed["headings"] if h["root"] and h["level"] == 2]
    entries = []
    for index, heading in enumerate(headings):
        title = heading["title"]
        if title == "[Unreleased]" and index == 0:
            continue
        match = HEADING.fullmatch(title)
        where = f'{source}:{heading["line"]}'
        if not match:
            raise ValueError(f"{where}: expected '## [X.Y.Z] - YYYY-MM-DD'. Found '{title}'.")
        value, day = match["version"], match["date"]
        try:
            released = date.fromisoformat(day)
        except ValueError:
            raise ValueError(f"{where}: invalid calendar date '{day}'.") from None
        if entries and (version_key(value) >= version_key(entries[-1][0]) or released > entries[-1][1]):
            raise ValueError(f"{where}: dates and unique versions must be in descending release order.")
        entries.append((value, released, heading["line"]))
    if not entries:
        raise ValueError(f"{source}: no dated entry. Add a release heading and describe the changes.")
    version = entries[0][0]
    if expected is not None and version != expected:
        raise ValueError(f"{source}: requested {expected}, latest dated entry is {version}. Update the newest entry.")
    start = entries[0][2]
    end = entries[1][2] - 1 if len(entries) > 1 else len(text.splitlines())
    body = "\n".join(text.splitlines()[start:end]).strip()
    body = re.sub(r"\n---\s*$", "", body).rstrip()
    check_content(f"{source}:{start}", body, [b for b in parsed["bullets"] if start < b["line"] <= end])
    return version, body


def current_entry(text, version):
    return newest_entry(text, expected=version)[1]


def check_update(previous, current):
    # A release heading, date or whitespace edit alone cannot document a product change.
    parsed = markdown("previous CHANGELOG.md", previous, lint=False)
    headings = [h for h in parsed["headings"] if h["root"] and h["level"] == 2 and HEADING.fullmatch(h["title"])]
    if not headings:
        return
    start = headings[0]["line"]
    end = headings[1]["line"] - 1 if len(headings) > 1 else len(previous.splitlines())
    before = [p for p in parsed["paragraphs"] if start < p["line"] <= end]
    after = markdown("current release entry", current, lint=False)["paragraphs"]

    def words(paragraphs):
        return re.findall(r"\w+", " ".join(p["text"] for p in paragraphs).casefold())

    if words(before) == words(after):
        raise ValueError("CHANGELOG.md: product code, packaging or SQL changed. Describe it in the newest release entry.")


def checked_notes(version, body):
    source = "release notes"
    parsed = markdown(source, body)
    check_content(source, body, parsed["bullets"])
    # References to earlier versions in upgrade instructions are legitimate.
    title = re.compile(r"^(?:(?:TeleHop|Release(?: notes)?|Version|Changelog|Changes in|For|Of)[: ]+)*"
                       r"\[?v?(\d+\.\d+\.\d+(?:-[\w.]+)?)\b", re.I)
    labels = [h["visibleTitle"] for h in parsed["headings"]]
    if parsed["paragraphs"]:
        labels.append(parsed["paragraphs"][0]["text"])
    for label in labels:
        match = title.match(label.strip())
        if match and match[1] != version:
            raise ValueError(f"{source}: heading identifies {match[1]}, expected {version}.")
    if parsed["headings"] and parsed["headings"][0]["line"] == 1 and parsed["headings"][0]["visibleTitle"] == f"TeleHop {version}":
        return body.strip() + "\n"
    return f"## TeleHop {version}\n\n" + body.strip() + "\n"
