"""Native Windows OSM -> reference-complete extract, compatible MVT and search."""
from collections import Counter
import gzip
import json
import math
from pathlib import Path

import mapbox_vector_tile
import osmium
from pmtiles.tile import Compression,TileType,zxy_to_tileid
from pmtiles.writer import write
from shapely import make_valid
from shapely.geometry import LineString,Point,box,mapping,shape
from shapely.ops import transform
from shapely.strtree import STRtree

R=6378137.0
WORLD=math.pi*R
SETTLEMENTS={"city","town","village","hamlet","isolated_dwelling"}

def mercator(x,y,z=None):
    def xy(a,b):return R*math.radians(a),R*math.log(math.tan(math.pi/4+math.radians(max(-85,min(85,b)))/2))
    if hasattr(x,"__iter__"):
        result=[xy(a,b) for a,b in zip(x,y)]
        return [v[0] for v in result],[v[1] for v in result]
    return xy(x,y)

def name(tags):return tags.get("name:ru",tags.get("name","")).strip()[:160]
def address(tags):return " ".join(filter(None,(tags.get("addr:street",tags.get("addr:place","")),tags.get("addr:housenumber",""))))[:320]

def source_bounds(source):
    limits=[180.,90.,-180.,-90.]
    class Bounds(osmium.SimpleHandler):
        def node(self,node):
            if node.location.valid():
                x,y=node.location.lon,node.location.lat
                limits[0]=min(limits[0],x);limits[1]=min(limits[1],y)
                limits[2]=max(limits[2],x);limits[3]=max(limits[3],y)
    Bounds().apply_file(str(source))
    return limits

def tile_budget(bounds,min_zoom,max_zoom):
    w,s=mercator(bounds[0],bounds[1]);e,n=mercator(bounds[2],bounds[3]);total=0
    for z in range(min_zoom,max_zoom+1):
        size=2*WORLD/(1<<z)
        nx=min((1<<z)-1,int((e+WORLD)/size))-max(0,int((w+WORLD)/size))+1
        ny=min((1<<z)-1,int((WORLD-s)/size))-max(0,int((WORLD-n)/size))+1
        total+=nx*ny
        if total>100000:raise ValueError('Слишком большая область для детальной карты; уменьшите границы')
    return total

def extract(source,destination,bounds):
    region=box(*bounds);ways=set();count=Counter()
    # BackReferenceWriter fills in every referenced node, way and restriction member.
    # Keep their tags: stripping them can lose one-way/access restrictions.
    with osmium.BackReferenceWriter(str(destination),str(source),remove_tags=False) as writer:
        class Extract(osmium.SimpleHandler):
            scanned_nodes=0
            scanned_ways=0
            def node(self,node):
                self.scanned_nodes+=1
                if self.scanned_nodes%1000000==0:print(f'Вырезка: прочитано {self.scanned_nodes:,} узлов',flush=True)
                if node.location.valid():
                    x,y=node.location.lon,node.location.lat
                    if bounds[0]<=x<=bounds[2] and bounds[1]<=y<=bounds[3]:writer.add_node(node);count['nodes']+=1
            def way(self,way):
                self.scanned_ways+=1
                if self.scanned_ways%100000==0:print(f'Вырезка: проверено {self.scanned_ways:,} линий',flush=True)
                if len(way.nodes)<2:return
                if not all(n.location.valid() for n in way.nodes):
                    count['incomplete_ways']+=1;return
                coordinates=[(n.lon,n.lat) for n in way.nodes]
                if max(p[0] for p in coordinates)<bounds[0] or min(p[0] for p in coordinates)>bounds[2] or max(p[1] for p in coordinates)<bounds[1] or min(p[1] for p in coordinates)>bounds[3]:return
                geometry=LineString(coordinates)
                if region.intersects(geometry):
                    writer.add_way(way);ways.add(way.id);count['ways']+=1
            def relation(self,relation):
                if relation.tags.get('type')=='restriction' and any(m.type=='w' and m.ref in ways for m in relation.members):
                    writer.add_relation(relation);count['restrictions']+=1
        Extract().apply_file(str(source),locations=True,idx='flex_mem')
        print('Вырезка: восстановление ссылок и сохранение PBF…',flush=True)
    if count['incomplete_ways']:raise ValueError("Исходный OSM неполный: отсутствуют узлы дорог. Скачайте полный .osm.pbf с Geofabrik")
    if not count['ways']:raise ValueError("В выбранных границах нет объектов OSM")
    return dict(count)

def read_osm(source,bounds,log=print):
    region=box(*bounds);features=[];entries=[];addresses=[];settlements=[];buildings=[];candidates=[];counts=Counter();settlement_names=set()
    factory=osmium.geom.GeoJSONFactory()
    def feature(geometry,props):
        if geometry.is_empty:return
        clipped=geometry.intersection(region)
        if not clipped.is_empty and clipped.geom_type in {'LineString','MultiLineString','Polygon','MultiPolygon'}:
            features.append((transform(mercator,clipped),props))
    def destination(tags,point,ref):
        if not region.covers(point):return
        city=tags.get('addr:city',tags.get('addr:town',tags.get('addr:village','')))[:160]
        street=tags.get('addr:street',tags.get('addr:place','')).strip()
        number=tags.get('addr:housenumber','').strip()[:40]
        if number:
            title=(street+' '+number).strip()
            entries.append(dict(name=title,lat=point.y,lon=point.x,kind='address',locality=city,address=address(tags),_ref=ref))
            addresses.append((transform(mercator,point),dict(house_number=number,street=street,osm_ref=ref)))
        title=name(tags)
        if title and (tags.get('amenity') or tags.get('shop') or tags.get('tourism') or tags.get('office') or tags.get('place') in SETTLEMENTS):
            entries.append(dict(name=title,lat=point.y,lon=point.x,kind='place',locality=city,address=address(tags),_ref=ref))
    class Data(osmium.SimpleHandler):
        scanned_nodes=0
        scanned_ways=0
        def node(self,node):
            self.scanned_nodes+=1
            if self.scanned_nodes%1000000==0:print(f'Карта и поиск: прочитано {self.scanned_nodes:,} узлов',flush=True)
            if not node.location.valid():return
            x,y=node.location.lon,node.location.lat
            if node.tags and node.tags.get('place') in SETTLEMENTS and name(node.tags):settlement_names.add(name(node.tags).casefold())
            if not(bounds[0]<=x<=bounds[2] and bounds[1]<=y<=bounds[3]):return
            if node.tags:destination(dict(node.tags),Point(x,y),'n'+str(node.id))
        def way(self,way):
            self.scanned_ways+=1
            if self.scanned_ways%100000==0:print(f'Карта и поиск: проверено {self.scanned_ways:,} линий',flush=True)
            tags=dict(way.tags)
            if not tags.get('highway') and not tags.get('waterway'):return
            if len(way.nodes)<2:return
            if not all(n.location.valid() for n in way.nodes):
                counts['incomplete_ways']+=1;return
            line=LineString([(n.lon,n.lat) for n in way.nodes])
            if not region.intersects(line):return
            highway=tags.get('highway','')
            feature(line,dict(kind='road' if highway else'water',highway=highway,name=name(tags)))
            if highway and name(tags):
                point=line.intersection(region).representative_point()
                entries.append(dict(name=name(tags),lat=point.y,lon=point.x,kind='street',locality=tags.get('addr:city',''),address='',_ref='w'+str(way.id)))
            if highway in {'motorway','trunk','primary','secondary','tertiary','residential','unclassified','living_street'} and len(candidates)<200:
                clipped=line.intersection(region)
                if clipped.geom_type=='LineString' and clipped.length>.0005:
                    a=clipped.interpolate(.15,normalized=True);b=clipped.interpolate(.85,normalized=True)
                    candidates.append([[a.y,a.x],[b.y,b.x]])
        def area(self,area):
            tags=dict(area.tags)
            settlement=tags.get('place') in SETTLEMENTS or (tags.get('boundary')=='administrative' and tags.get('admin_level') in {'8','9','10'} and name(tags).casefold() in settlement_names)
            kind='building' if tags.get('building') else 'water' if tags.get('natural')=='water' or tags.get('water') else 'land'
            visible=tags.get('building') or tags.get('landuse') or tags.get('leisure') or tags.get('natural') in {'wood','scrub','grassland','water'}
            if not(visible or settlement or tags.get('addr:housenumber') or tags.get('amenity') or tags.get('shop')):return
            try:
                coordinates=[(n.lon,n.lat) for ring in area.outer_rings() for n in ring]
                if not coordinates or max(p[0] for p in coordinates)<bounds[0] or min(p[0] for p in coordinates)>bounds[2] or max(p[1] for p in coordinates)<bounds[1] or min(p[1] for p in coordinates)>bounds[3]:return
                polygon=make_valid(shape(json.loads(factory.create_multipolygon(area))))
                if polygon.is_empty or not region.intersects(polygon):return
                if settlement and name(tags):settlements.append((polygon,name(tags)))
                if visible:feature(polygon,dict(kind=kind,name=name(tags)))
                point=polygon.intersection(region).representative_point()
                ref=('w' if area.from_way() else'r')+str(area.orig_id())
                destination(tags,point,ref)
                if tags.get('building') and address(tags):buildings.append((polygon,address(tags)))
            except (RuntimeError,ValueError) as error:
                counts['invalid_areas']+=1
    Data().apply_file(str(source),locations=True,idx='flex_mem')
    if not features:raise ValueError('Нет дорог и зданий в выбранной области')
    local_tree=STRtree([p for p,_ in settlements]) if settlements else None
    building_tree=STRtree([p for p,_ in buildings]) if buildings else None
    clean=[];seen=set()
    for entry in entries:
        point=Point(entry['lon'],entry['lat'])
        if not entry['locality'] and local_tree is not None:
            hits=[int(i) for i in local_tree.query(point) if settlements[int(i)][0].covers(point)]
            if hits:entry['locality']=settlements[min(hits,key=lambda i:settlements[i][0].area)][1]
        if not entry['address'] and building_tree is not None:
            hits=[int(i) for i in building_tree.query(point) if buildings[int(i)][0].covers(point)]
            if hits:entry['address']=buildings[min(hits,key=lambda i:buildings[i][0].area)][1]
        # Keep distinct same-name addresses in different settlements. Never assign a nearest city.
        key=(entry['kind'],entry['name'].casefold(),entry['locality'].casefold(),
             None if entry['kind']=='street' and entry['locality'] else (round(point.x,6),round(point.y,6)))
        if key in seen:continue
        seen.add(key);entry.pop('_ref',None);clean.append(entry)
    if len(clean)>250000:raise ValueError('Больше 250000 адресов; уменьшите регион')
    counts.update(features=len(features),addresses=len(addresses),search=len(clean),settlement_polygons=len(settlements))
    log('OSM: '+json.dumps(dict(counts),ensure_ascii=False),flush=True)
    return features,addresses,clean,candidates,dict(counts)

def build_pmtiles(features,addresses,bounds,path,min_zoom,max_zoom,log=print):
    tile_budget(bounds,min_zoom,max_zoom)
    tree=STRtree([g for g,_ in features]);address_tree=STRtree([g for g,_ in addresses]) if addresses else None
    w,s=mercator(bounds[0],bounds[1]);e,n=mercator(bounds[2],bounds[3])
    jobs=[]
    for z in range(min_zoom,max_zoom+1):
        size=2*WORLD/(1<<z)
        for x in range(max(0,int((w+WORLD)/size)),min((1<<z)-1,int((e+WORLD)/size))+1):
            for y in range(max(0,int((WORLD-n)/size)),min((1<<z)-1,int((WORLD-s)/size))+1):
                jobs.append((zxy_to_tileid(z,x,y),z,x,y))
    if len(jobs)>100000:raise ValueError('Слишком большая область для детальной карты; уменьшите границы')
    jobs.sort();written=0
    with write(str(path)) as writer:
        for index,(ident,z,x,y) in enumerate(jobs):
            size=2*WORLD/(1<<z);tile_bounds=(-WORLD+x*size,WORLD-(y+1)*size,-WORLD+(x+1)*size,WORLD-y*size)
            tile_box=box(*tile_bounds);layers=[];items=[]
            for raw in tree.query(tile_box):
                i=int(raw);geometry,props=features[i]
                if props['kind']=='building' and z<14:continue
                if props['kind']=='road' and z<12 and props.get('highway') not in {'motorway','trunk','primary','secondary','tertiary'}:continue
                clip=geometry.intersection(tile_box)
                if clip.is_empty or clip.geom_type=='GeometryCollection':continue
                clip=clip.simplify(size/4096*.6,preserve_topology=True)
                if not clip.is_empty:items.append(dict(geometry=mapping(clip),properties=props,id=i+1))
            if items:layers.append(dict(name='region',features=items))
            if z==max_zoom and address_tree is not None:
                items=[dict(geometry=mapping(addresses[int(i)][0]),properties=addresses[int(i)][1]) for i in address_tree.query(tile_box) if tile_box.covers(addresses[int(i)][0])]
                if items:layers.append(dict(name='addresses',features=items))
            if layers:
                raw=mapbox_vector_tile.encode(layers,default_options=dict(quantize_bounds=tile_bounds,extents=4096))
                writer.write_tile(ident,gzip.compress(raw,mtime=0));written+=1
            if index%250==0:log(f'Тайлы: {index+1}/{len(jobs)}',flush=True)
        if not written:raise ValueError('Не удалось создать ни одного тайла')
        header=dict(tile_type=TileType.MVT,tile_compression=Compression.GZIP,min_zoom=min_zoom,max_zoom=max_zoom,
                    min_lon_e7=round(bounds[0]*1e7),min_lat_e7=round(bounds[1]*1e7),max_lon_e7=round(bounds[2]*1e7),max_lat_e7=round(bounds[3]*1e7))
        writer.finalize(header,dict(name='Jeland offline region',vector_layers=[dict(id='region',fields={'kind':'String','name':'String','highway':'String'}),dict(id='addresses',fields={'house_number':'String','street':'String'})]))
    return written
