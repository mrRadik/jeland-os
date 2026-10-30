"""Prepare the pinned CoMaps engine without installing its application.

Java/Kotlin and resources are compiled from the corresponding source submodule.
Native ARM libraries and generated map assets come from its signed public release.
Only the listed assets/libs are extracted; no upstream classes.dex/application code.
"""
import argparse
import base64
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

VERSION = "v2026.10.05-19"
SHA256 = "7eea2f49dd79e583e14cd9ebac3b98e754b3e351dad2522530e40cedf9b5987a"
URL = "https://codeberg.org/comaps/comaps/releases/download/" + VERSION + "/CoMaps-26100519-main-release.apk"
ROOT = Path(__file__).resolve().parent.parent


def complete_android_plurals(resources: ET.Element, qualifier: str):
    # Upstream omits some `many` categories, including fractional quantities
    # in Czech/Slovak/Lithuanian. Android Lint's locale rules require them.
    # Keep the current `other` fallback explicit in generated resources,
    # preserving both formatting and the pinned upstream checkout.
    language = qualifier.split("-")[1] if "-" in qualifier else ""
    if language not in {"iw", "he", "cs", "sk", "lt"}:
        return
    for plural in resources.findall("plurals"):
        if plural.find("item[@quantity='many']") is not None:
            continue
        other = plural.find("item[@quantity='other']")
        if other is not None:
            many = copy.deepcopy(other)
            many.set("quantity", "many")
            plural.append(many)


def prepare(output: Path):
    source = ROOT / "third_party/comaps"
    if not (source / "android/sdk/src/main/java").is_dir():
        raise SystemExit("Initialize CoMaps sources: git submodule update --init third_party/comaps")
    actual = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    expected = "7f3b5be834f7f73abca08339b4e56dcaf1b5ec3e"
    if actual != expected:
        raise SystemExit("CoMaps sources must match " + VERSION)
    cache = ROOT / ".local/comaps/CoMaps-26100519-main-release.apk"
    cache.parent.mkdir(parents=True, exist_ok=True)
    if not cache.is_file():
        temp = cache.with_suffix(".download")
        request = urllib.request.Request(URL, headers={"User-Agent": "JelandOS-CoMaps-build/1.0"})
        with urllib.request.urlopen(request, timeout=90) as response, temp.open("wb") as target:
            while chunk := response.read(1024 * 1024):
                target.write(chunk)
        with temp.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != SHA256:
                raise SystemExit("CoMaps download checksum mismatch")
        temp.replace(cache)
    with cache.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != SHA256:
            raise SystemExit("CoMaps release checksum mismatch; remove the cache and retry")
    output.mkdir(parents=True, exist_ok=True)
    # Windows Git may check out symlinks as tiny text files. Materialize only
    # tracked SDK resources, resolving links strictly inside the upstream tree.
    entries = subprocess.check_output(["git", "-C", str(source), "ls-files", "--stage"], text=True)
    links = {line.split("\t",1)[1] for line in entries.splitlines() if line.startswith("120000 ")}
    def resource_content(path):
        for _ in range(10):
            relative = path.relative_to(source).as_posix()
            if relative not in links:
                return path.read_bytes()
            link = os.readlink(path) if path.is_symlink() else path.read_text().strip()
            path = (path.parent / link).resolve()
            if not path.is_relative_to(source):
                raise ValueError("Resource symlink escapes CoMaps sources")
        raise ValueError("Resource symlink cycle")
    for path in (source / "android/sdk/src/main/res").rglob("*"):
        if path.is_file():
            target = output / "res" / path.relative_to(source / "android/sdk/src/main/res")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(resource_content(path))
    # Native search/POI code looks up additional labels dynamically. They live
    # in the upstream app, rather than SDK resources (e.g. hotel stars). Keep
    # those labels too; missing ones can crash upstream's debug Toast path on
    # a native worker thread. Main Jeland resources still override library names.
    ET.register_namespace("xliff", "urn:oasis:names:tc:xliff:document:1.2")
    for values in (source / "android/app/src/main/res").glob("values*"):
        if not values.is_dir():
            continue
        destination = output / "res" / values.name
        existing = set()
        for xml in destination.glob("*.xml"):
            if xml.name == "comaps-native-strings.xml":
                continue
            for entry in ET.fromstring(xml.read_bytes()):
                existing.add((entry.tag, entry.get("name")))
        supplemental = ET.Element("resources")
        for xml in values.glob("*.xml"):
            for entry in ET.fromstring(resource_content(xml)):
                key = (entry.tag, entry.get("name"))
                if entry.tag in ("string", "plurals", "string-array") and key not in existing:
                    supplemental.append(entry)
                    existing.add(key)
        if len(supplemental):
            complete_android_plurals(supplemental, values.name)
            destination.mkdir(parents=True, exist_ok=True)
            ET.ElementTree(supplemental).write(destination / "comaps-native-strings.xml", encoding="utf-8", xml_declaration=True)
    count = 0
    with zipfile.ZipFile(cache) as archive:
        for item in archive.infolist():
            name = PurePosixPath(item.filename)
            if item.is_dir() or ".." in name.parts or name.is_absolute():
                continue
            if name.parts[0] == "assets" and not str(name).startswith("assets/dexopt/"):
                destination = output.joinpath(*name.parts)
            elif str(name) in ("lib/arm64-v8a/liborganicmaps.so", "lib/armeabi-v7a/liborganicmaps.so"):
                destination = output / "jniLibs" / name.parts[1] / name.name
            else:
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(item))
            count += 1
    countries = json.loads((output / "assets/countries.txt").read_text(encoding="utf-8"))
    # Same overview maps as the upstream full distribution. Regions remain
    # user-downloadable; the initial world/Russia overview works without a network.
    for region in countries["g"]:
        if region["id"] not in ("World", "WorldCoasts"):
            continue
        name = region["id"] + ".mwm"
        map_cache = cache.parent / str(countries["v"]) / name
        expected_digest = base64.b64decode(region["sha1_base64"])
        def valid(path):
            if not path.is_file() or path.stat().st_size != region["s"]:
                return False
            with path.open("rb") as stream:
                return hashlib.file_digest(stream, "sha1").digest() == expected_digest
        if not valid(map_cache):
            map_cache.parent.mkdir(parents=True, exist_ok=True)
            temp = map_cache.with_suffix(".download")
            for server in ("https://comaps-cdn.s3.cloud.ru", "https://cdn-fi-1.comaps.app", "https://mapgen-fi-1.comaps.app"):
                map_url = f'{server}/maps/{countries["map_series"]}/{countries["v"]}/{name}'
                try:
                    with urllib.request.urlopen(map_url, timeout=25) as response, temp.open("wb") as target:
                        while chunk := response.read(1024 * 1024):
                            target.write(chunk)
                    if valid(temp):
                        break
                except (OSError, TimeoutError) as error:
                    print(f"CoMaps overview mirror unavailable: {error}")
            if not valid(temp):
                temp.unlink(missing_ok=True)
                raise SystemExit("CoMaps overview checksum mismatch")
            temp.replace(map_cache)
        (output / "assets" / name).write_bytes(map_cache.read_bytes())
    for name in ("LICENSE", "NOTICE"):
        target = output / "assets/licenses/comaps" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((source / name).read_bytes())
    # The publisher's APK is ARM-only. F-Droid builds the same source tag for
    # x86_64 as well, avoiding ARM/OpenGL translation inside desktop emulators.
    x86_url = "https://f-droid.org/repo/app.comaps.fdroid_26100519.apk"
    x86_hash = "90b091be84c5a22f306729ae710b40b1cc1acf3918da6629d15da8027866dc46"
    x86_cache = cache.parent / "app.comaps.fdroid_26100519.apk"
    if not x86_cache.is_file():
        temp = x86_cache.with_suffix(".download")
        for candidate in (x86_url, x86_url.replace("/repo/", "/archive/")):
            try:
                with urllib.request.urlopen(candidate, timeout=90) as response, temp.open("wb") as target:
                    while chunk := response.read(1024 * 1024):
                        target.write(chunk)
                break
            except (OSError, TimeoutError):
                temp.unlink(missing_ok=True)
        if not temp.is_file():
            raise SystemExit("CoMaps x86_64 release unavailable from F-Droid and its archive")
        with temp.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != x86_hash:
                temp.unlink()
                raise SystemExit("CoMaps x86_64 download checksum mismatch")
        temp.replace(x86_cache)
    with x86_cache.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != x86_hash:
            raise SystemExit("CoMaps x86_64 release checksum mismatch")
    with zipfile.ZipFile(x86_cache) as archive:
        target = output / "jniLibs/x86_64/liborganicmaps.so"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read("lib/x86_64/liborganicmaps.so"))
    provenance={"version": VERSION, "commit": actual, "sha256": SHA256, "url": URL,
                "x86_64": {"url": x86_url, "sha256": x86_hash}}
    (output / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    (output / "assets/licenses/comaps/source.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(f"Prepared CoMaps {VERSION}: {count} engine assets/libraries")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    prepare(parser.parse_args().output.resolve())
