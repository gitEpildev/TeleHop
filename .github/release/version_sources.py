"""Read and synchronise TeleHop's release fields without serialising unrelated source text."""
from dataclasses import dataclass
import os
from pathlib import Path
import re
import stat
import tempfile
import xml.etree.ElementTree as ET
from xml.parsers import expat

from changelog import VERSION

MODULES = {Path(name) for name in ("common", "paper", "velocity", "paper-versions/paper-version-api",
                                   "paper-versions/paper.1.21.x", "paper-versions/paper.26.1.x")}
BACKEND = Path("paper/src/main/resources/plugin.yml")
PROXY = Path("velocity/src/main/java/com/telehop/velocity/NetworkVelocityPlugin.java")


@dataclass
class Source:
    path: Path
    original: bytes
    spans: list[tuple[int, int]]

    def updated(self, version):
        result = self.original
        for start, end in sorted(self.spans, reverse=True):
            value = self.original[start:end].decode()
            if not VERSION.fullmatch(value):
                raise ValueError(f"{self.path}: expected a literal TeleHop version, found {value!r}")
            result = result[:start] + version.encode("ascii") + result[end:]
        return result


def read(path):
    if path.is_symlink() or not path.resolve().is_relative_to(Path.cwd().resolve()):
        raise ValueError(f"Expected a repository file, found {path}")
    return path.read_bytes()


def pom_versions(path, contents):
    # Expat supplies byte offsets, retaining comments, indentation, namespace syntax and line endings.
    parser = expat.ParserCreate(namespace_separator="}")
    stack, opened, fields = [], [], {}
    targets = {("project", "version"), ("project", "parent", "version")}

    def start(name, attributes):
        stack.append(name.rsplit("}", 1)[-1])
        opened.append(parser.CurrentByteIndex)

    def end(name):
        location = tuple(stack)
        opening = opened.pop()
        stack.pop()
        if location not in targets:
            return
        scalar = re.fullmatch(rb"<(?:[\w.-]+:)?version\s*>\s*([^\s<>]+)\s*", contents[opening:parser.CurrentByteIndex])
        if not scalar or location in fields:
            raise ValueError(f"{path}: ambiguous value for {'/'.join(location)}")
        fields[location] = (opening + scalar.start(1), opening + scalar.end(1))

    parser.StartElementHandler, parser.EndElementHandler = start, end
    parser.Parse(contents, True)
    required = ("project", "version") if path == Path("pom.xml") else ("project", "parent", "version")
    if required not in fields:
        raise ValueError(f"{path}: missing {'/'.join(required)}")
    return list(fields.values())


def backend_version(contents):
    # Only a top-level YAML scalar belongs to the plugin version. Preserve existing quoting and comments.
    declarations = list(re.finditer(rb"(?m)^(?:version|'version'|\"version\")[ \t]*:[^\r\n]*", contents))
    if len(declarations) != 1:
        raise ValueError(f"{BACKEND}: expected exactly one top-level version")
    line = declarations[0]
    scalar = re.fullmatch(rb"""[^:]+:[ \t]*(?P<q>['"]?)(?P<v>[^'"#\s]+)(?P=q)(?:[ \t]+\#.*)?[ \t]*""", line.group())
    if not scalar:
        raise ValueError(f"{BACKEND}: expected a scalar version")
    return [(line.start() + scalar.start("v"), line.start() + scalar.end("v"))]


JAVA_TOKENS = re.compile(rb'''//[^\r\n]*|/\*.*?\*/|"""(?:\\.|(?!""").)*"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[\w$]+|[^\s]''', re.S)


def proxy_version(contents):
    tokens = [match for match in JAVA_TOKENS.finditer(contents) if not match.group().startswith((b"//", b"/*"))]
    values = [token.group() for token in tokens]
    annotations = [i for i in range(len(tokens) - 2) if values[i:i+3] == [b"@", b"Plugin", b"("]]
    if len(annotations) != 1:
        raise ValueError(f"{PROXY}: expected exactly one @Plugin annotation")
    start = annotations[0] + 3
    end = next((i for i in range(start, len(tokens)) if values[i] == b")"), None)
    if end is None:
        raise ValueError(f"{PROXY}: incomplete @Plugin annotation")
    versions = [i for i in range(start, end) if values[i] == b"version" and values[i-1] in (b"(", b",")]
    if len(versions) != 1:
        raise ValueError(f"{PROXY}: expected one version in the @Plugin annotation")
    i = versions[0]
    if i+2 >= end or values[i+1] != b"=" or not re.fullmatch(rb'"[^"\\]*"', values[i+2]) or values[i+3] not in (b",", b")"):
        raise ValueError(f"{PROXY}: expected a literal @Plugin version")
    return [(tokens[i+2].start()+1, tokens[i+2].end()-1)]


def sources():
    pending, seen, artefacts, parents, result = [Path("pom.xml")], set(), set(), set(), []
    while pending:
        path = pending.pop()
        if path in seen:
            raise ValueError(f"Duplicate Maven module {path}")
        seen.add(path)
        data = read(path)
        root = ET.fromstring(data)
        for element in ("artifactId", "groupId", "parent", "modules", "parent/{*}artifactId", "parent/{*}groupId"):
            if len(root.findall("{*}" + element)) > 1:
                raise ValueError(f"{path}: ambiguous Maven {element}")
        group = root.findtext("{*}groupId", root.findtext("{*}parent/{*}groupId"))
        artifact = root.findtext("{*}artifactId")
        if group != "com.telehop" or not artifact or artifact in artefacts:
            raise ValueError(f"{path}: expected a unique TeleHop project coordinate")
        if path == Path("pom.xml"):
            if artifact != "telehop-parent" or root.find("{*}parent") is not None:
                raise ValueError("The reactor root must be the TeleHop parent project")
        else:
            if root.findtext("{*}parent/{*}groupId") != "com.telehop":
                raise ValueError(f"{path}: expected a TeleHop parent")
            parents.add(root.findtext("{*}parent/{*}artifactId", ""))
        artefacts.add(artifact)
        result.append(Source(path, data, pom_versions(path, data)))
        for module in root.findall("{*}modules/{*}module"):
            name = Path((module.text or "").strip())
            if name.is_absolute() or ".." in name.parts or name == Path("."):
                raise ValueError(f"{path}: invalid module path {name}")
            pending.append(path.parent / name / "pom.xml")
    if missing := MODULES - {path.parent for path in seen}:
        raise ValueError(f"Missing required Maven modules: {', '.join(map(str, sorted(missing)))}")
    if parents - artefacts:
        raise ValueError("A TeleHop module refers to a parent outside the reactor")
    for path, locate in ((BACKEND, backend_version), (PROXY, proxy_version)):
        data = read(path)
        result.append(Source(path, data, locate(data)))
    return result


def replacement(path, contents):
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".telehop-version-", delete=False) as handle:
        temp = Path(handle.name)
        try:
            handle.write(contents)
            handle.flush()
            os.chmod(temp, stat.S_IMODE(path.stat().st_mode))
        except BaseException:
            temp.unlink(missing_ok=True)
            raise
    return temp


def apply(version, *, write=False):
    fields = sources()
    edits = [(source, source.updated(version)) for source in fields]
    changes = [(source, updated) for source, updated in edits if source.original != updated]
    if not write:
        if changes:
            errors = [f"{s.path}: found {', '.join(s.original[a:b].decode() for a, b in s.spans)}, expected {version}." for s, _ in changes]
            raise ValueError("\n".join(errors) + "\nRun python3 .github/release/prepare.py sync-version and review the changes.")
        return []
    staged, applied = {}, []
    try:
        for source, updated in changes:
            staged[source.path] = replacement(source.path, updated)
        for source in fields:
            if read(source.path) != source.original:
                raise ValueError(f"{source.path} changed while preparing the version update")
        for source, updated in changes:
            if read(source.path) != source.original:
                raise ValueError(f"{source.path} changed during the version update")
            staged[source.path].replace(source.path)
            applied.append((source, updated))
    except BaseException:
        for source, updated in reversed(applied):
            if source.path.read_bytes() == updated:
                replacement(source.path, source.original).replace(source.path)
        raise
    finally:
        for temp in staged.values():
            temp.unlink(missing_ok=True)
    return [source.path for source, _ in changes]
