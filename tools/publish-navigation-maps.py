"""Prepare map assets; --publish uploads a separate maps release, never an APK.

Credentials are obtained from Git Credential Manager only for the requested repo
and remain in process memory. Existing public assets are never overwritten.
"""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import subprocess
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = "mrRadik/jeland-os"
TAG = "maps-2026-10-26"
API = "https://api.github.com/repos/" + REPO


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def prepare():
    source = ROOT / "navigation-data/chelyabinsk-west-poi-addresses/region.jelandnav.zip"
    bundled = json.loads((ROOT / "app/src/main/assets/navigation/bundled/manifest.json").read_text("utf-8-sig"))
    with zipfile.ZipFile(source) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        if manifest != bundled:
            raise RuntimeError("The region archive differs from the current bundled map")
        expected = {"manifest.json", *(item["name"] for item in manifest["files"])}
        if set(archive.namelist()) != expected or len(archive.namelist()) != len(expected):
            raise RuntimeError("Unexpected region files")
        for item in manifest["files"]:
            with archive.open(item["name"]) as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            if digest != item["sha256"] or archive.getinfo(item["name"]).file_size != item["bytes"]:
                raise RuntimeError("Region integrity check failed: " + item["name"])
    destination = ROOT / "artifacts/maps"
    destination.mkdir(parents=True, exist_ok=True)
    region = destination / (manifest["id"] + ".jelandnav.zip")
    shutil.copyfile(source, region)
    overview = destination / "russia-overview.pmtiles"
    shutil.copyfile(ROOT / "app/src/main/assets/navigation/overview/russia-overview.pmtiles", overview)
    overview_manifest = json.loads((ROOT / "app/src/main/assets/navigation/overview/russia-overview.json").read_text("utf-8-sig"))
    if sha(overview) != overview_manifest["sha256"]:
        raise RuntimeError("Overview integrity check failed")
    maps = []
    for file, ident, title, kind in [(region, manifest["id"], manifest["title"], "region"),
                                    (overview, "russia-overview", "Россия · обзорная карта дорог", "overview")]:
        maps.append({"id": ident, "title": title, "kind": kind,
                     "url": f"https://github.com/{REPO}/releases/download/{TAG}/{file.name}",
                     "bytes": file.stat().st_size, "sha256": sha(file)})
    catalog = destination / "maps.json"
    catalog.write_text(json.dumps({"format": 1, "maps": maps}, ensure_ascii=False, indent=2) + "\n", "utf-8")
    print(json.dumps({"prepared": maps}, ensure_ascii=False), flush=True)
    return [region, overview, catalog], {Path(urlsplit(row["url"]).path).name: row["title"] for row in maps}


def publish(files, labels):
    result = subprocess.run([r"C:/Program Files/Git/mingw64/bin/git-credential-manager.exe", "get"],
                            input="protocol=https\nhost=github.com\n\n", text=True, capture_output=True, timeout=30,
                            env=dict(os.environ, GCM_INTERACTIVE="Never", GIT_TERMINAL_PROMPT="0"))
    credentials = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
    token = credentials.get("password")
    if result.returncode or not token:
        raise RuntimeError("GitHub credentials unavailable; no release changed")
    headers = {"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
               "User-Agent": "JelandOS-Maps", "X-GitHub-Api-Version": "2022-11-28"}

    def api(method, path, payload=None):
        data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode()
        request = Request(API + path, data=data, headers=dict(headers, **{"Content-Type": "application/json"}), method=method)
        with urlopen(request, timeout=45) as response:
            return json.load(response)

    repo = api("GET", "")
    if repo["full_name"].lower() != REPO.lower() or not repo.get("permissions", {}).get("push"):
        raise RuntimeError("Unexpected repository or no write permission")
    baseline = api("GET", "/git/ref/tags/v0.9.35")["object"]
    if baseline["type"] == "tag":
        baseline = api("GET", "/git/tags/" + baseline["sha"])["object"]
    if baseline["type"] != "commit":
        raise RuntimeError("Expected an existing published application commit")
    body = ("Подготовленные офлайн-карты Jeland OS.\n\n"
            "* Челябинск · Миасс · Златоуст и окрестности — ограниченная тестовая зона, не вся область. "
            "Содержит карту, адресный поиск и граф автомобильных маршрутов.\n"
            "* Россия — обзорная карта крупных дорог и городов. Не содержит дорожный граф всей России.\n\n"
            "Список для собственного источника: maps.json. Карты основаны на данных OpenStreetMap "
            "и выгрузке Geofabrik. © OpenStreetMap contributors, ODbL 1.0. "
            "https://www.openstreetmap.org/copyright · https://download.geofabrik.de/russia/ural-fed-district.html\n\n"
            "Состав карт совпадает с подготовленными данными приложения. Этот релиз содержит только карты.")
    try:
        release = api("GET", "/releases/tags/" + TAG)
    except HTTPError as error:
        if error.code != 404:
            raise
        release = api("POST", "/releases", {"tag_name": TAG, "target_commitish": baseline["sha"],
                                           "name": "Офлайн-карты · 26 октября 2026", "body": body,
                                           "draft": True, "prerelease": False, "make_latest": "false"})
    upload = urlsplit(release["upload_url"].split("{", 1)[0])
    if upload.scheme != "https" or upload.hostname != "uploads.github.com" or not upload.path.startswith("/repos/" + REPO + "/releases/"):
        raise RuntimeError("Unexpected upload endpoint")
    existing = {item["name"]: item for item in release["assets"]}
    for file in files:
        size, digest = file.stat().st_size, "sha256:" + sha(file)
        if file.name in existing:
            asset = existing[file.name]
            if asset["size"] != size or asset.get("digest") != digest:
                raise RuntimeError("Existing asset differs; nothing overwritten: " + file.name)
            continue
        if not release["draft"]:
            raise RuntimeError("Existing public map release is incomplete; nothing overwritten")
        label = labels.get(file.name, "Каталог карт Jeland OS")
        connection = http.client.HTTPSConnection(upload.hostname, timeout=60, blocksize=1024 * 1024)
        try:
            connection.putrequest("POST", upload.path + "?name=" + quote(file.name) + "&label=" + quote(label))
            for key, value in headers.items():
                connection.putheader(key, value)
            connection.putheader("Content-Type", "application/octet-stream")
            connection.putheader("Content-Length", str(size))
            connection.endheaders()
            with file.open("rb") as stream:
                while chunk := stream.read(1024 * 1024):
                    connection.send(chunk)
            response = connection.getresponse()
            content = response.read()
            if response.status != 201:
                raise RuntimeError("Upload failed (HTTP " + str(response.status) + ")")
            asset = json.loads(content)
            if asset["size"] != size or asset.get("digest") != digest:
                raise RuntimeError("Uploaded map verification failed")
            print("Verified uploaded asset: " + file.name, flush=True)
        finally:
            connection.close()
    if release["draft"]:
        release = api("PATCH", "/releases/" + str(release["id"]), {"draft": False, "make_latest": "false"})
    print(json.dumps({"url": release["html_url"], "published": not release["draft"]}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()
    prepared, labels = prepare()
    if args.publish:
        publish(prepared, labels)
