"""Synthetic OSM fixtures only; no network or changes to real map data."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('poi_addresses', Path(__file__).with_name('enrich-navigation-poi-addresses.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PoiAddressTests(unittest.TestCase):
    def run_fixture(self, entries, objects):
        # Keep fixtures inside this project's writable local test area on Windows.
        fixture_root=Path(__file__).resolve().parent.parent/'.local'/'poi-test-fixtures'
        fixture_root.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='jeland-poi-',dir=fixture_root) as folder:
            source = Path(folder) / 'fixture.osm'
            source.write_text('<osm version="0.6">' + objects + '</osm>', encoding='utf-8')
            before = [(e['lat'], e['lon']) for e in entries]
            module.enrich(entries, source)
            self.assertEqual(before, [(e['lat'], e['lon']) for e in entries])
        return entries

    def test_explicit_node_address_is_preserved(self):
        entry = dict(name='Кафе', lat=55., lon=60., kind='place', locality='Челябинск')
        self.run_fixture([entry], '<node id="1" lat="55" lon="60"><tag k="name" v="Кафе"/><tag k="addr:street" v="улица Мира"/><tag k="addr:housenumber" v="12"/></node>')
        self.assertEqual('улица Мира 12', entry['address'])
        self.assertEqual('Челябинск', entry['locality'])

    def test_existing_suffix_moves_to_address_not_title(self):
        entry = dict(name='Аптека · улица Мира 1', lat=55., lon=60., kind='place')
        self.run_fixture([entry], '')
        self.assertEqual('Аптека', entry['name'])
        self.assertEqual('улица Мира 1', entry['address'])

    def test_same_point_other_business_does_not_inherit_tags(self):
        entry = dict(name='Кафе', lat=55., lon=60., kind='place')
        self.run_fixture([entry], '<node id="1" lat="55" lon="60"><tag k="name" v="Аптека"/><tag k="addr:street" v="Мира"/><tag k="addr:housenumber" v="1"/></node>')
        self.assertNotIn('address', entry)

    def test_inside_building_inherits_address_but_nearest_outside_does_not(self):
        inside = dict(name='Магазин', lat=55.001, lon=60.001, kind='place')
        outside = dict(name='Киоск', lat=55.003, lon=60.003, kind='place')
        objects = ''.join(f'<node id="{i}" lat="{lat}" lon="{lon}"/>' for i,lat,lon in [
            (1,55.,60.),(2,55.,60.002),(3,55.002,60.002),(4,55.002,60.)])
        objects += '<way id="10"><nd ref="1"/><nd ref="2"/><nd ref="3"/><nd ref="4"/><nd ref="1"/><tag k="building" v="yes"/><tag k="addr:street" v="улица Мира"/><tag k="addr:housenumber" v="1"/></way>'
        self.run_fixture([inside,outside], objects)
        self.assertEqual('улица Мира 1', inside['address'])
        self.assertNotIn('address', outside)


if __name__ == '__main__':
    unittest.main()
