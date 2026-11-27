"""Copy shaded Maven artefacts to the release bundle with reproducible ZIP metadata."""
from pathlib import Path
import shutil
import sys
import tempfile
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

from artefacts import PLATFORMS, filename
from checksums import manifest
from prepare import verify_jars


def package(source, destination):
    # A retry may rebuild this commit. ZIP creation times and filesystem order must not change its hash.
    with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as file:
        staged = Path(file.name)
    try:
        with ZipFile(source) as original, ZipFile(staged, "w") as result:
            names = original.namelist()
            if len(set(names)) != len(names):
                raise ValueError(f"Duplicate entries in {source}")
            def order(name):
                if name == "META-INF/":
                    return 0, name
                if name == "META-INF/MANIFEST.MF":
                    return 1, name
                return 2, name
            for name in sorted(names, key=order):
                info = ZipInfo(name)
                info.create_system = 3
                info.external_attr = original.getinfo(name).external_attr
                info.compress_type = ZIP_STORED if info.is_dir() else ZIP_DEFLATED
                with original.open(name) as entry, result.open(info, "w") as out:
                    shutil.copyfileobj(entry, out)
        staged.chmod(0o644)
        staged.replace(destination)
    finally:
        staged.unlink(missing_ok=True)


if __name__ == "__main__":
    version, destination = sys.argv[1], Path(sys.argv[2])
    destination.mkdir(parents=True, exist_ok=True)
    for platform in PLATFORMS:
        name = filename(platform, version)
        package(Path(platform, "target", name), destination / name)
    verify_jars(version, destination)
    (destination / "SHA256SUMS").write_text(manifest(destination, version), encoding="ascii")
