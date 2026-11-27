"""Checksum-addressed cache for public Minecraft server and client test artefacts."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def sha256(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def download(destination, url, digest):
    cache = Path(os.environ.get("TELEHOP_TEST_CACHE", Path(tempfile.gettempdir()) / "telehop-runtime-downloads"))
    cache.mkdir(parents=True, exist_ok=True)
    cached = cache / digest
    if not cached.exists() or sha256(cached) != digest:
        with tempfile.NamedTemporaryFile(dir=cache, delete=False) as handle:
            temporary = Path(handle.name)
        try:
            subprocess.run(["curl", "-fsSL", "--retry", "3", "--user-agent",
                            "TeleHop verification (https://github.com/gitEpildev/TeleHop)",
                            "-o", temporary, url], check=True)
            if sha256(temporary) != digest:
                raise ValueError(f"Checksum mismatch for {destination.name}")
            temporary.replace(cached)
        finally:
            temporary.unlink(missing_ok=True)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(cached, destination)


def prepare_server(directory, platform, name, digest):
    jar = directory / "server.jar"
    download(jar, f"https://fill-data.papermc.io/v1/objects/{digest}/{name}", digest)
    if platform == "paper":
        with zipfile.ZipFile(jar) as source:
            expected, url, vanilla = source.read("META-INF/download-context").decode().strip().split()
        if Path(vanilla).name != vanilla or not url.startswith("https://"):
            raise ValueError("Invalid Paper download context")
        download(directory / "cache" / vanilla, url, expected)
