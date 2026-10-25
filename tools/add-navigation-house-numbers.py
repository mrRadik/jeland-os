"""Add source-backed OSM house labels to an existing local PMTiles region.

No downloads, graph rebuild or route changes. Requires pyosmium, shapely,
mapbox-vector-tile and pmtiles. Existing base MVT layers are preserved verbatim.
Run: python add-navigation-house-numbers.py INPUT_REGION OUTPUT_REGION
"""
import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import zipfile

import mapbox_vector_tile
import osmium
from pmtiles.reader import MmapSource, Reader, all_tiles
from pmtiles.tile import tileid_to_zxy, zxy_to_tileid
from pmtiles.writer import write
from shapely.geometry import Point, Polygon, LineString
from shapely import make_valid
from shapely.strtree import STRtree


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source, output = args.source.resolve(), args.output.resolve()
    if source == output or output.exists():
        raise ValueError("Use a new output directory; never overwrite the source region")
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    for item in manifest["files"]:
        assert digest(source / item["name"]) == item["sha256"], item["name"]
    west, south, east, north = manifest["bounds"]
    zoom, radius = 15, 6378137.0
    world = math.pi * radius
    size = world * 2 / 2**zoom
    addresses, seen = [], set()
    counts = {"nodes": 0, "ways": 0, "invalidGeometry": 0}
    duplicate_nodes = set()

    def add(tags, lon, lat, osm_ref):
        number = " ".join(tags.get("addr:housenumber", "").split())
        if not number or len(number) > 40 or not (west <= lon <= east and south <= lat <= north):
            return
        # Only remove identical source locations, not neighbouring houses with the same number.
        key = (number, round(lon, 7), round(lat, 7))
        if key in seen:
            return
        seen.add(key)
        address={"number": number, "lon": lon, "lat": lat, "osm_ref": osm_ref,
                 "street": tags.get("addr:street", "")}
        addresses.append(address)
        return address

    class Addresses(osmium.SimpleHandler):
        def __init__(self):
            super().__init__()
            self.node_addresses, self.node_points = [], []
            self.node_tree = None

        def node(self, node):
            assert self.node_tree is None, "Use a node/way-sorted OSM extract"
            if node.tags.get("addr:housenumber") and node.location.valid():
                counts["nodes"] += 1
                address=add(dict(node.tags), node.location.lon, node.location.lat, f"n{node.id}")
                if address is not None:
                    self.node_addresses.append(address)
                    self.node_points.append(Point(node.location.lon,node.location.lat))

        def way(self, way):
            if not way.tags.get("addr:housenumber"):
                return
            try:
                coordinates = [(node.lon, node.lat) for node in way.nodes]
            except osmium.InvalidLocationError:
                counts["invalidGeometry"] += 1
                return
            if len(coordinates) < 2:
                return
            if len(coordinates) >= 4 and coordinates[0] == coordinates[-1]:
                shape = make_valid(Polygon(coordinates))
                if shape.is_empty or shape.area <= 0:
                    counts["invalidGeometry"] += 1
                    return
                # Unlike a centroid, this is inside even an L-shaped/concave footprint.
                point = shape.representative_point()
                assert shape.covers(point)
                if way.tags.get("building"):
                    if self.node_tree is None:
                        self.node_tree=STRtree(self.node_points)
                    number=" ".join(way.tags["addr:housenumber"].split()).casefold()
                    street=way.tags.get("addr:street", "").strip().casefold()
                    for index in self.node_tree.query(shape):
                        node_address=self.node_addresses[int(index)]
                        node_street=node_address["street"].strip().casefold()
                        if (node_address["number"].casefold()==number
                                and (not street or not node_street or street==node_street)
                                and shape.covers(self.node_points[int(index)])):
                            duplicate_nodes.add(node_address["osm_ref"])
            else:
                point = LineString(coordinates).interpolate(0.5, normalized=True)
            counts["ways"] += 1
            add(dict(way.tags), point.x, point.y, f"w{way.id}")

    Addresses().apply_file(str(source / "region.osm.pbf"), locations=True, idx="flex_mem")
    addresses=[address for address in addresses if address["osm_ref"] not in duplicate_nodes]
    assert len(addresses) > 1000, "Unexpectedly small source address set"
    tiles = {}
    for address in addresses:
        lon, lat = address["lon"], address["lat"]
        x = math.radians(lon) * radius
        y = math.log(math.tan(math.pi / 4 + math.radians(lat) / 2)) * radius
        tx, ty = int((x + world) / size), int((world - y) / size)
        tile_id = zxy_to_tileid(zoom, tx, ty)
        tiles.setdefault(tile_id, []).append({"geometry": Point(x, y), "properties": {
            "house_number": address["number"], "osm_ref": address["osm_ref"], "street": address["street"]}})
    output.mkdir(parents=True)
    archive_path = output / "map.pmtiles"
    changed = 0
    with (source / "map.pmtiles").open("rb") as input_file:
        get_bytes = MmapSource(input_file)
        reader = Reader(get_bytes)
        header, metadata = reader.header(), reader.metadata()
        assert not any(layer["id"] == "addresses" for layer in metadata.get("vector_layers", []))
        base = {zxy_to_tileid(*zxy): data for zxy, data in all_tiles(get_bytes)}
        with write(str(archive_path) + ".partial") as writer:
            for tile_id in sorted(base.keys() | tiles.keys()):
                original = base.get(tile_id)
                if tile_id in tiles:
                    z, x, y = tileid_to_zxy(tile_id)
                    assert z == zoom
                    min_x, max_y = x * size - world, world - y * size
                    bounds = (min_x, max_y - size, min_x + size, max_y)
                    encoded = mapbox_vector_tile.encode({"name": "addresses", "features": tiles[tile_id]},
                        default_options={"quantize_bounds": bounds, "extents": 4096})
                    # Tile is a protobuf message with a repeated Layer field. Appending the
                    # new Tile message merges its layer without re-encoding any base feature.
                    raw = (gzip.decompress(original) if original else b"") + encoded
                    assert len(mapbox_vector_tile.decode(raw)["addresses"]["features"]) == len(tiles[tile_id])
                    writer.write_tile(tile_id, gzip.compress(raw, mtime=0))
                    changed += 1
                else:
                    writer.write_tile(tile_id, original)
            metadata["vector_layers"].append({"id": "addresses", "fields": {
                "house_number": "String", "osm_ref": "String", "street": "String"}, "minzoom": zoom, "maxzoom": zoom})
            writer.finalize(header, metadata)
    Path(str(archive_path) + ".partial").replace(archive_path)
    for name in ("search.json", "valhalla_tiles.tar"):
        shutil.copy2(source / name, output / name)
        assert digest(output / name) == digest(source / name)
    manifest.update({"id": "chelyabinsk-west-20261021-houses", "prepared": "2026-10-21",
                     "houseNumberCount": len(addresses), "houseNumberLayer": "addresses",
                     "houseNumberMinZoom": 16, "houseNumberSource": "addr:housenumber in the original region.osm.pbf"})
    manifest["files"] = [{"name": name, "bytes": (output / name).stat().st_size,
                          "sha256": digest(output / name)} for name in ("map.pmtiles", "search.json", "valhalla_tiles.tar")]
    dump(output / "manifest.json", manifest)
    with zipfile.ZipFile(output / "region.jelandnav.zip", "w", zipfile.ZIP_DEFLATED, compresslevel=6) as region:
        for name in ("manifest.json", "map.pmtiles", "search.json", "valhalla_tiles.tar"):
            region.write(output / name, name)
    report = {"addresses": len(addresses), "sourceCounts": counts, "duplicateAddressNodesRemoved":len(duplicate_nodes),"changedTiles": changed,
              "graphUnchanged": True, "searchUnchanged": True,
              "nearCenter": sorted(addresses, key=lambda a: (a["lat"]-55.16)**2 + (a["lon"]-61.402)**2)[:20]}
    dump(output / "house-numbers-report.json", report)
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
