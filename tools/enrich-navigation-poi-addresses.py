"""Add source-backed POI street addresses, preserving every destination coordinate.

Offline only. Requires pyosmium + shapely. Never assigns the nearest house: an
address comes from the same OSM node, an existing explicit suffix, or a containing
addressed building polygon. Use a new output directory; source data is not edited.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile

import osmium
from shapely.geometry import Point, Polygon
from shapely import make_valid
from shapely.strtree import STRtree


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')


def address_line(tags):
    street = tags.get('addr:street', tags.get('addr:place', '')).strip()
    number = tags.get('addr:housenumber', '').strip()
    if street:
        return ' '.join(filter(None, (street, number)))[:320]
    return tags.get('addr:full', '').strip()[:320]


def normalize(value):
    return ' '.join(value.casefold().replace('ё', 'е').split())


def enrich(entries, pbf):
    counts = Counter()
    by_point = defaultdict(list)
    for index, entry in enumerate(entries):
        if entry.get('kind') != 'place':
            continue
        counts['organizations'] += 1
        by_point[(round(entry['lon'], 6), round(entry['lat'], 6))].append(index)
        # The old builder sometimes embedded an explicit address in name.
        title, separator, suffix = entry['name'].partition(' · ')
        if separator and (re.search(r'\d', suffix) or re.search(
                r'улица|проспект|переулок|проезд|шоссе|бульвар|площадь|набережная|тракт', suffix, re.I)):
            entry['name'] = title
            entry['address'] = suffix[:320]
            entry['addressSource'] = 'existing-explicit-address'
            counts['existing_suffix'] += 1

    polygons, building_addresses = [], []

    class Source(osmium.SimpleHandler):
        def node(self, node):
            if not node.location.valid():
                return
            indices = by_point.get((round(node.location.lon, 6), round(node.location.lat, 6)))
            if not indices:
                return
            line = address_line(node.tags)
            names = {normalize(node.tags.get(key, '')) for key in ('name', 'name:ru', 'brand')}
            if not line:
                return
            for index in indices:
                entry = entries[index]
                if normalize(entry['name']) in names:
                    entry['address'] = line
                    entry['addressSource'] = f'osm-node:{node.id}'
                    counts['explicit_node'] += 1

        def way(self, way):
            # Inherit only a full house address from an actual containing building.
            if not way.tags.get('building') or not way.tags.get('addr:housenumber'):
                return
            line = address_line(way.tags)
            if not line or not (way.tags.get('addr:street') or way.tags.get('addr:place')):
                return
            try:
                coordinates = [(node.lon, node.lat) for node in way.nodes]
                if len(coordinates) < 4 or coordinates[0] != coordinates[-1]:
                    return
                geometry = make_valid(Polygon(coordinates))
                if geometry.is_empty or geometry.area <= 0:
                    return
                polygons.append(geometry)
                building_addresses.append((line, f'osm-building:{way.id}'))
            except (osmium.InvalidLocationError, ValueError):
                counts['invalid_building'] += 1

    Source().apply_file(str(pbf), locations=True, idx='flex_mem')
    tree = STRtree(polygons)
    for entry in entries:
        if entry.get('kind') != 'place' or entry.get('address'):
            continue
        point = Point(entry['lon'], entry['lat'])
        hits = [int(index) for index in tree.query(point, predicate='covered_by')]
        lines = {building_addresses[index][0] for index in hits}
        if len(lines) == 1:
            index = min(hits, key=lambda index: polygons[index].area)
            entry['address'], entry['addressSource'] = building_addresses[index]
            counts['containing_building'] += 1
        elif len(lines) > 1:
            counts['ambiguous_building'] += 1
    counts['with_address'] = sum(1 for e in entries if e.get('kind') == 'place' and e.get('address'))
    counts['without_address'] = counts['organizations'] - counts['with_address']
    return dict(counts)


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--pbf', type=Path, required=True)
    args = parser.parse_args()
    source, output = args.source.resolve(), args.output.resolve()
    if output.exists():
        raise ValueError('Output must be a new directory')
    manifest = json.loads((source / 'manifest.json').read_text(encoding='utf-8'))
    for item in manifest['files']:
        if digest(source / item['name']) != item['sha256']:
            raise ValueError(f"Source checksum mismatch: {item['name']}")
    entries = json.loads((source / 'search.json').read_text(encoding='utf-8'))
    original_positions = [(e['lat'], e['lon'], e['kind'], e.get('locality')) for e in entries]
    counts = enrich(entries, args.pbf)
    assert original_positions == [(e['lat'], e['lon'], e['kind'], e.get('locality')) for e in entries]
    output.mkdir(parents=True)
    for name in ('map.pmtiles', 'valhalla_tiles.tar'):
        shutil.copyfile(source / name, output / name)
    dump(output / 'search.json', entries)
    assert (output / 'search.json').stat().st_size <= 32 * 1048576
    manifest.update(id='chelyabinsk-west-20261022-poi-addresses', searchAddressVersion=1,
                    searchAddressSource='Explicit OSM tags or containing addressed building; never nearest house')
    manifest['files'] = [dict(name=name, bytes=(output/name).stat().st_size, sha256=digest(output/name))
                         for name in ('map.pmtiles', 'search.json', 'valhalla_tiles.tar')]
    dump(output / 'manifest.json', manifest)
    examples = [e for e in entries if e.get('kind') == 'place' and e.get('address')][:20]
    dump(output / 'search-addresses-report.json', dict(counts=counts, examples=examples,
                                                     pbfSha256=digest(args.pbf)))
    with zipfile.ZipFile(output/'region.jelandnav.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name in ('manifest.json', 'map.pmtiles', 'search.json', 'valhalla_tiles.tar'):
            archive.write(output/name, name)
    print(json.dumps(counts), flush=True)


if __name__ == '__main__':
    main()
