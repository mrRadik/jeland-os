"""The Android importer's package contract. No third-party imports needed."""
import hashlib
import json
import math
from pathlib import Path
import re
import tarfile
import tempfile
import zipfile

MIB=1024*1024
LIMITS={"manifest.json":65536,"map.pmtiles":128*MIB,"search.json":32*MIB,"valhalla_tiles.tar":400*MIB}
REQUIRED=set(LIMITS)
MAX_TOTAL=512*MIB

def digest(file):
    with Path(file).open("rb") as stream:
        return hashlib.file_digest(stream,"sha256").hexdigest()

def write_json(file,value):
    Path(file).write_text(json.dumps(value,ensure_ascii=False,separators=(",",":"),allow_nan=False),encoding="utf-8")

def validate_identity(ident,title,bounds,center):
    if not re.fullmatch(r"[a-z0-9-]{1,64}",ident):
        raise ValueError("ID: только латинские строчные буквы, цифры и дефис, до 64 символов")
    if not isinstance(title,str) or not title.strip() or len(title)>160:
        raise ValueError("Название должно содержать от 1 до 160 символов")
    if not isinstance(bounds,list) or len(bounds)!=4 or not all(isinstance(x,(int,float)) and math.isfinite(x) for x in bounds):
        raise ValueError("Границы: [запад, юг, восток, север]")
    w,s,e,n=bounds
    if not (-180<=w<e<=180 and -85<=s<n<=85):
        raise ValueError("Некорректные границы региона; пересечение 180-го меридиана не поддерживается")
    if len(center)!=2 or not(s<=center[0]<=n and w<=center[1]<=e):
        raise ValueError("Центр должен находиться внутри региона (широта, долгота)")

def validate_directory(folder):
    folder=Path(folder)
    manifest=json.loads((folder/"manifest.json").read_text("utf-8-sig"))
    if manifest.get("format")!=2:
        raise ValueError("Инструмент создаёт пакеты format=2 (PMTiles)")
    validate_identity(manifest.get("id",""),manifest.get("title",""),manifest.get("bounds"),manifest.get("center",[]))
    files=manifest.get("files",[])
    if len(files)!=3 or {item.get("name") for item in files}!=REQUIRED-{"manifest.json"}:
        raise ValueError("Манифест должен описывать карту, поиск и граф")
    total=0
    for name,limit in LIMITS.items():
        path=folder/name
        if not path.is_file() or path.is_symlink() or not 0<path.stat().st_size<=limit:
            raise ValueError(f"Отсутствует или превышает лимит файл {name}; уменьшите регион")
        total+=path.stat().st_size
    if total>MAX_TOTAL:
        raise ValueError("Распакованный регион больше 512 МиБ; уменьшите границы")
    for item in files:
        path=folder/item["name"]
        if path.stat().st_size!=item["bytes"] or digest(path)!=item["sha256"]:
            raise ValueError("Не совпадает контрольная сумма: "+item["name"])
    with (folder/"map.pmtiles").open("rb") as stream:
        header=stream.read(127)
    if len(header)!=127 or header[:8]!=b"PMTiles\x03" or header[99]!=1:
        raise ValueError("Ожидается векторная карта PMTiles v3")
    entries=json.loads((folder/"search.json").read_text("utf-8"))
    if not isinstance(entries,list) or len(entries)>250000:
        raise ValueError("Поисковый индекс больше 250000 записей")
    for entry in entries:
        if not isinstance(entry.get("name"),str) or not entry["name"].strip():
            raise ValueError("Пустой адрес в поиске")
        lat,lon=entry.get("lat"),entry.get("lon")
        if not all(isinstance(x,(int,float)) and math.isfinite(x) for x in (lat,lon)) or not(-90<=lat<=90 and -180<=lon<=180):
            raise ValueError("Некорректные координаты адреса")
    graph_files=0
    with tarfile.open(folder/"valhalla_tiles.tar","r:") as graph:
        for member in graph:
            if not member.isfile() or not re.fullmatch(r"(?:index\.bin|[0-2]/(?:[0-9]{3}/)*[0-9]{3}\.gph)",member.name):
                raise ValueError("Неожиданный элемент графа: "+member.name)
            if member.name.endswith(".gph"):graph_files+=1
    if not graph_files:raise ValueError("Граф не содержит дорожных тайлов")
    return {"id":manifest["id"],"title":manifest["title"],"unpacked_bytes":total,"search_entries":len(entries),"graph_tiles":graph_files}

def pack_directory(folder,destination):
    folder=Path(folder);destination=Path(destination)
    report=validate_directory(folder)
    if destination.exists():raise ValueError("Выходной пакет уже существует; выберите новую папку или имя")
    destination.parent.mkdir(parents=True,exist_ok=True)
    pending=destination.with_suffix(destination.suffix+".partial")
    if pending.exists():raise ValueError("Остался незавершённый пакет .partial; выберите новое имя")
    try:
        with zipfile.ZipFile(pending,"x",zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
            for name in sorted(REQUIRED):archive.write(folder/name,name)
        if pending.stat().st_size>MAX_TOTAL:raise ValueError("ZIP больше 512 МиБ; уменьшите регион")
        pending.replace(destination)
    except BaseException:
        pending.unlink(missing_ok=True)
        raise
    report.update(file=destination.name,bytes=destination.stat().st_size,sha256=digest(destination))
    return report

def validate_archive(path):
    path=Path(path)
    if not path.is_file() or path.stat().st_size>MAX_TOTAL:raise ValueError("ZIP отсутствует или больше 512 МиБ")
    with tempfile.TemporaryDirectory(prefix="jeland-map-check-") as temp:
        folder=Path(temp)
        with zipfile.ZipFile(path) as archive:
            items=archive.infolist()
            if len(items)!=4 or {item.filename for item in items}!=REQUIRED:
                raise ValueError("В ZIP должны быть ровно четыре файла без вложенных папок")
            if sum(item.file_size for item in items)>MAX_TOTAL:raise ValueError("ZIP распаковывается в объём больше 512 МиБ")
            for item in items:
                if not 0<item.file_size<=LIMITS[item.filename] or item.compress_type not in (zipfile.ZIP_STORED,zipfile.ZIP_DEFLATED):
                    raise ValueError("Неподдерживаемый размер или сжатие: "+item.filename)
                count=0
                with archive.open(item) as source,(folder/item.filename).open("xb") as out:
                    while block:=source.read(65536):
                        count+=len(block)
                        if count>LIMITS[item.filename]:raise ValueError("Превышен размер: "+item.filename)
                        out.write(block)
        result=validate_directory(folder)
    return dict(result,file=path.name,bytes=path.stat().st_size,sha256=digest(path))
