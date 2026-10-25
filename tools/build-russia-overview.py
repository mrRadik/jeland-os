"""One-time local Protomaps extract (not a hotlinked in-app tile source).
Use local python-tiles dependencies. Keeps z0-3 context + Russian bounding areas z4-7.
An overview only: no street search or routing graph is claimed.
"""
import argparse, bisect, gzip, hashlib, json, math, subprocess, threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from pmtiles.reader import Reader
from pmtiles.tile import deserialize_directory, tileid_to_zxy
from pmtiles.writer import write


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("url")
    parser.add_argument("output",type=Path)
    parser.add_argument("--maxzoom",type=int,default=6)
    args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    cache={};received=0;etag=None;network_slots=threading.Semaphore(6)
    def fetch(offset,length):
        nonlocal received,etag
        key=(offset,length)
        if key in cache:return cache[key]
        assert 0<length<160*1024*1024
        if length>16384:
            chunks=[(offset+i,min(16384,length-i)) for i in range(0,length,16384)]
            with ThreadPoolExecutor(max_workers=6) as pool:data=b"".join(pool.map(lambda part:fetch(*part),chunks))
            cache[key]=data
            return data
        cache_dir=args.output/"download-cache";cache_dir.mkdir(exist_ok=True)
        headers_file=cache_dir/f"{offset}-{length}.headers"
        body_file=cache_dir/f"{offset}-{length}.bin"
        command=["curl.exe","--fail","--silent","--show-error","--max-time","40","--max-filesize",str(length),
                 "--range",f"{offset}-{offset+length-1}","--dump-header",str(headers_file),"--output",str(body_file)]
        if etag:command += ["-H",f"If-Match: {etag}"]
        if not(body_file.exists() and body_file.stat().st_size==length and headers_file.exists()):
            with network_slots:subprocess.run(command+[args.url],check=True)
        headers=headers_file.read_text().lower()
        assert "206 partial content" in headers
        assert f"content-range: bytes {offset}-{offset+length-1}/" in headers
        current=next(line.split(":",1)[1].strip() for line in headers.splitlines() if line.startswith("etag:"))
        if etag:assert current==etag,"Source changed while reading"
        else:etag=current
        data=body_file.read_bytes();assert len(data)==length
        received+=length
        assert received<180*1024*1024,"Unexpected overview download size"
        cache[key]=data
        if received<20000:print(f"Fetched {received} bytes",flush=True)
        return data
    reader=Reader(fetch);header=reader.header();metadata=reader.metadata()
    assert 3<=args.maxzoom<=7
    max_id=((1<<(2*(args.maxzoom+1)))-1)//3-1
    entries=[]
    def walk(offset,length):
        for item in deserialize_directory(fetch(offset,length)):
            if item.tile_id>max_id:break
            if item.run_length:entries.append(item)
            else:walk(header["leaf_directory_offset"]+item.offset,item.length)
    walk(header["root_offset"],header["root_length"])
    def wanted(z,x,y):
        if z<=3:return True
        lon=(x+.5)/(1<<z)*360-180
        lat=math.degrees(math.atan(math.sinh(math.pi*(1-2*(y+.5)/(1<<z)))))
        # Whole intersecting tiles, including the Russian Far East across the dateline.
        margin=360/(1<<z)
        return (41-margin<=lat<=83+margin) and (lon>=19-margin or lon<=-169+margin)
    selected=[]
    for entry in entries:
        tile_ids=[tile_id for tile_id in range(entry.tile_id,min(max_id+1,entry.tile_id+entry.run_length)) if wanted(*tileid_to_zxy(tile_id))]
        if tile_ids:selected.append((entry,tile_ids))
    print(f"Selected {len(selected)} entries, {sum(e.length for e,_ in selected)} source bytes",flush=True)
    with ThreadPoolExecutor(max_workers=6) as pool:
        contents=list(pool.map(lambda item:fetch(header["tile_data_offset"]+item[0].offset,item[0].length),selected))
    keep={"earth","water","roads","places","boundaries"}
    def varint(data,index):
        value=shift=0
        while True:
            byte=data[index];index+=1;value|=(byte&127)<<shift
            if byte<128:return value,index
            shift+=7;assert shift<64
    def layer_name(data):
        index=0
        while index<len(data):
            tag,index=varint(data,index);wire=tag&7
            if wire==0:_,index=varint(data,index)
            elif wire==1:index+=8
            elif wire==5:index+=4
            elif wire==2:
                size,index=varint(data,index)
                if tag>>3==1:return data[index:index+size].decode()
                index+=size
            else:raise ValueError("Unsupported MVT wire type")
        raise ValueError("Unnamed MVT layer")
    def strip_details(data):
        raw=gzip.decompress(data);out=bytearray();index=0
        while index<len(raw):
            start=index;tag,index=varint(raw,index);assert tag==26
            size,index=varint(raw,index);end=index+size
            if layer_name(raw[index:end]) in keep:out.extend(raw[start:end])
            index=end
        return gzip.compress(bytes(out),mtime=0)
    count=0
    with write(str(args.output/"russia-overview.pmtiles")) as writer:
        for (entry,tile_ids),data in zip(selected,contents):
            data=strip_details(data)
            for tile_id in tile_ids:
                writer.write_tile(tile_id,data);count+=1
        header.update(min_zoom=0,max_zoom=args.maxzoom,min_lon_e7=-1800000000,min_lat_e7=-850000000,max_lon_e7=1800000000,max_lat_e7=850000000,center_zoom=3,center_lon_e7=940000000,center_lat_e7=620000000)
        metadata.update(name="Jeland · Обзор дорог России",description=f"Overview only, z0-{args.maxzoom}, Russia and surrounding context. No routing graph.",attribution="© OpenStreetMap contributors · Natural Earth · Protomaps · ODbL")
        metadata["vector_layers"]=[layer for layer in metadata["vector_layers"] if layer["id"] in keep]
        writer.finalize(header,metadata)
    path=args.output/"russia-overview.pmtiles"
    manifest={"title":"Обзор дорог России","file":path.name,"schema":"protomaps-v4","maxZoom":args.maxzoom,"tiles":count,"bytes":path.stat().st_size,
        "sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"source":args.url,"etag":etag,"downloadedBytes":received,"attribution":metadata["attribution"],"routing":False}
    (args.output/"russia-overview.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps(manifest,ensure_ascii=False),flush=True)

if __name__=="__main__":main()
