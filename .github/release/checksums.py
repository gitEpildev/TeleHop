"""Generate a portable SHA-256 manifest from the two installable JARs."""
import hashlib
from pathlib import Path
import sys

from artefacts import PLATFORMS, filename


def manifest(directory, version):
    lines = []
    for platform in PLATFORMS:
        name = filename(platform, version)
        with (directory / name).open("rb") as handle:
            lines.append(f"{hashlib.file_digest(handle, 'sha256').hexdigest()}  {name}\n")
    return "".join(lines)


if __name__ == "__main__":
    directory = Path(sys.argv[1])
    (directory / "SHA256SUMS").write_text(manifest(directory, sys.argv[2]), encoding="ascii")
