"""Add source-backed settlement names to an existing region without rebuilding map/graph.

Requires pyosmium + shapely. Use a NEW output directory. --areas may reuse a JSON
area extraction (areas[].tags/geometry); its digest is recorded for reproducibility.
No nearest-town fallback: out-of-settlement results remain explicitly unknown.
"""
import argparse
from collections import Counter,defaultdict
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile

import osmium
from shapely.geometry import Point,box,shape
from shapely.strtree import STRtree

SETTLEMENTS={'city','town','village','hamlet','isolated_dwelling'}

def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def dump(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':')),encoding='utf-8')

def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('source',type=Path)
    parser.add_argument('output',type=Path)
    parser.add_argument('--pbf',type=Path,required=True,help='Complete original source, not clipped ways-only extract')
    parser.add_argument('--regional-pbf',type=Path,required=True,help='Same regional extract used to build the original search')
    parser.add_argument('--areas',type=Path)
    args=parser.parse_args()
    source=args.source.resolve();output=args.output.resolve()
    if output.exists():raise ValueError('Output must be a new directory')
    manifest=json.loads((source/'manifest.json').read_text(encoding='utf-8'))
    for entry in manifest['files']:
        if digest(source/entry['name'])!=entry['sha256']:raise ValueError('Source region checksum mismatch')
    source_hash=digest(args.pbf)
    if manifest.get('sourceSha256') and source_hash!=manifest['sourceSha256']:
        raise ValueError('PBF does not match original region source')
    west,south,east,north=manifest['bounds'];region=box(west,south,east,north)
    names={};centers={}

    class Names(osmium.SimpleHandler):
        def node(self,node):
            if node.tags.get('place') in SETTLEMENTS and node.location.valid():
                x,y=node.location.lon,node.location.lat
                if west<=x<=east and south<=y<=north:
                    name=node.tags.get('name:ru',node.tags.get('name',''))
                    if name:
                        names[name.casefold()]=name
                        centers[(round(x,6),round(y,6))]=name
    Names().apply_file(str(args.regional_pbf))
    if args.areas:
        raw=json.loads(args.areas.read_text(encoding='utf-8'))['areas']
    else:
        raw=[];factory=osmium.geom.GeoJSONFactory()
        class Areas(osmium.SimpleHandler):
            def area(self,area):
                if not area.tags.get('name'):return
                if area.tags.get('place') not in SETTLEMENTS and area.tags.get('boundary') not in {'administrative','place'}:return
                try:
                    geometry=json.loads(factory.create_multipolygon(area))
                    if region.intersects(shape(geometry)):raw.append({'tags':dict(area.tags),'geometry':geometry})
                except (RuntimeError,ValueError):pass
        Areas().apply_file(str(args.pbf),locations=True)
    polygons=[]
    for record in raw:
        tags=record['tags'];name=tags.get('name:ru',tags.get('name',''))
        if not name:continue
        # A municipality containing a city is NOT the settlement boundary of that city.
        if tags.get('place') in SETTLEMENTS or (tags.get('boundary') in {'administrative','place'} and name.casefold() in names):
            geometry=shape(record['geometry'])
            if geometry.is_valid and not geometry.is_empty:
                canonical=names.get(name.casefold(),name)
                names[name.casefold()]=canonical
                polygons.append((geometry,canonical))
    tree=STRtree([p[0] for p in polygons])
    by_first=defaultdict(list)
    for name in names.values():by_first[name.casefold().split()[0]].append(name)
    for candidates in by_first.values():candidates.sort(key=len,reverse=True)
    entries=json.loads((source/'search.json').read_text(encoding='utf-8'))
    counts=Counter();unresolved=[];mira=[]
    for entry in entries:
        original=entry['name'];chunks=original.split(' · ');address=chunks[-1]
        city='';method='unknown'
        # Existing index names were formed from explicit addr:city + street + house tags.
        for candidate in by_first.get(address.casefold().split()[0] if address.split() else '',[]):
            if address.casefold().startswith(candidate.casefold()+' '):
                rest=address[len(candidate):].strip()
                if entry['kind']=='address' or re.search(r'\d|улица|проспект|переулок|проезд|шоссе|бульвар|площадь|набережная|тракт',rest,re.I):
                    city=candidate;chunks[-1]=rest;entry['name']=' · '.join(chunks);method='explicit-address-prefix';break
        location=Point(entry['lon'],entry['lat'])
        if not city:
            hits=sorted((polygons[int(i)] for i in tree.query(location,predicate='covered_by')),key=lambda item:item[0].area)
            if hits:city=hits[0][1];method='settlement-polygon'
        center=centers.get((round(entry['lon'],6),round(entry['lat'],6)))
        if center and original.casefold()==center.casefold():
            city=center;entry['kind']='settlement';method='settlement-node'
        if city:entry['locality']=city
        counts[method]+=1
        counts['total']+=1
        if not city and len(unresolved)<30:unresolved.append(entry.copy())
        if 'мира' in entry['name'].lower() and entry['name'].split()[-1:]==['1']:mira.append(entry.copy())
    # Preserve every existing destination's position and order. No nearest-city guess or dedup.
    original_entries=json.loads((source/'search.json').read_text(encoding='utf-8'))
    assert len(entries)==len(original_entries)
    assert all((a['lat'],a['lon'])==(b['lat'],b['lon']) for a,b in zip(entries,original_entries))
    assert all(e.get('locality') for e in mira),'Mira 1 regression: unresolved settlement'
    output.mkdir(parents=True)
    for name in ['map.pmtiles','valhalla_tiles.tar']:shutil.copyfile(source/name,output/name)
    dump(output/'search.json',entries)
    if (output/'search.json').stat().st_size>32*1024*1024:raise ValueError('Search exceeds importer limit')
    manifest.update(id='chelyabinsk-west-20261021-localities',searchLocalityVersion=1,
        searchLocalitySource='Explicit existing addr:city prefixes and containing OSM settlement polygons from complete source; never nearest city',
        limitations='Bounded test extract, not full oblast. No traffic/elevation. Settlement attribution may be missing outside mapped boundaries.')
    manifest['files']=[{'name':name,'bytes':(output/name).stat().st_size,'sha256':digest(output/name)} for name in ['map.pmtiles','search.json','valhalla_tiles.tar']]
    dump(output/'manifest.json',manifest)
    report={'counts':dict(counts),'settlementPolygons':len(polygons),'sourceSha256':source_hash,
        'areaCacheSha256':digest(args.areas) if args.areas else None,'mira1':mira,'unknownExamples':unresolved}
    dump(output/'search-localities-report.json',report)
    with zipfile.ZipFile(output/'region.jelandnav.zip','w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for name in ['manifest.json','map.pmtiles','search.json','valhalla_tiles.tar']:archive.write(output/name,name)
    print(json.dumps({'counts':dict(counts),'polygons':len(polygons),'mira1':mira,'searchBytes':(output/'search.json').stat().st_size},ensure_ascii=False),flush=True)

if __name__=='__main__':main()
