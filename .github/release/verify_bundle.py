"""Check the exact runtime bundle downloaded from Actions before starting the server suites."""
from pathlib import Path
import sys

from artefacts import PLATFORMS, filename
from checksums import manifest
from prepare import verify_jars


def verify(directory, version):
    expected = {filename(platform, version) for platform in PLATFORMS} | {"SHA256SUMS", "command-probe.jar"}
    contents = {path.name for path in directory.iterdir()}
    if expected != contents:
        raise ValueError(f"Unexpected bundle contents. Missing {sorted(expected - contents)}, extra {sorted(contents - expected)}")
    if (directory / "SHA256SUMS").read_text(encoding="ascii") != manifest(directory, version):
        raise ValueError("The two downloaded distribution files differ from the build manifest")
    verify_jars(version, directory)


if __name__ == "__main__":
    verify(Path(sys.argv[1]), sys.argv[2])
