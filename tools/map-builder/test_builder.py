import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from package_tools import validate_identity,validate_archive,pack_directory
from osm_build import extract,read_osm,tile_budget
import osmium

class BuilderTests(unittest.TestCase):
    def test_tile_budget_rejects_country_sized_jobs_before_allocating_tiles(self):
        self.assertLess(tile_budget([61.38,55.14,61.43,55.18],10,15),100)
        with self.assertRaises(ValueError):tile_budget([30.,40.,140.,75.],10,15)
    def test_crossing_road_has_complete_references_and_turn_restriction(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'source.osm'
            source.write_text('''<osm version="0.6">
              <node id="1" lat="0.5" lon="-1"/><node id="2" lat="0.5" lon="2"/><node id="3" lat="0.7" lon="3"/>
              <way id="10"><nd ref="1"/><nd ref="2"/><tag k="highway" v="residential"/></way>
              <way id="11"><nd ref="2"/><nd ref="3"/><tag k="highway" v="residential"/><tag k="oneway" v="yes"/></way>
              <relation id="100"><member type="way" ref="10" role="from"/><member type="node" ref="2" role="via"/>
                <member type="way" ref="11" role="to"/><tag k="type" v="restriction"/><tag k="restriction" v="no_right_turn"/></relation>
            </osm>''',encoding='utf-8')
            before=hashlib.sha256(source.read_bytes()).digest()
            target=Path(temp)/'cut.osm.pbf';extract(source,target,[0.,0.,1.,1.])
            nodes=set();ways={};relations=[]
            class Read(osmium.SimpleHandler):
                def node(self,node):nodes.add(node.id)
                def way(self,way):ways[way.id]=([n.ref for n in way.nodes],dict(way.tags))
                def relation(self,relation):relations.append(dict(relation.tags))
            Read().apply_file(str(target))
            self.assertTrue({1,2,3}.issubset(nodes));self.assertEqual('yes',ways[11][1]['oneway'])
            self.assertEqual('no_right_turn',relations[0]['restriction'])
            self.assertEqual(before,hashlib.sha256(source.read_bytes()).digest())

    def test_settlement_and_poi_address_come_from_containing_polygons(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'source.osm'
            source.write_text('''<osm version="0.6">
              <node id="1" lat="0.5" lon="0.5"><tag k="name" v="Магазин"/><tag k="shop" v="convenience"/></node>
              <node id="2" lat="0.4" lon="0.4"/><node id="3" lat="0.4" lon="0.6"/><node id="4" lat="0.6" lon="0.6"/><node id="5" lat="0.6" lon="0.4"/>
              <node id="6" lat="0" lon="0"/><node id="7" lat="0" lon="1"/><node id="8" lat="1" lon="1"/><node id="9" lat="1" lon="0"/>
              <node id="10" lat="0.35" lon="0.3"/><node id="11" lat="0.35" lon="0.7"/>
              <way id="20"><nd ref="2"/><nd ref="3"/><nd ref="4"/><nd ref="5"/><nd ref="2"/>
                <tag k="building" v="yes"/><tag k="addr:street" v="Мира"/><tag k="addr:housenumber" v="1"/></way>
              <way id="21"><nd ref="6"/><nd ref="7"/><nd ref="8"/><nd ref="9"/><nd ref="6"/>
                <tag k="place" v="city"/><tag k="name" v="Тестоград"/></way>
              <way id="22"><nd ref="10"/><nd ref="11"/><tag k="highway" v="residential"/><tag k="name" v="Мира"/></way>
              <way id="23"><nd ref="2"/><nd ref="3"/><nd ref="4"/><nd ref="5"/><nd ref="2"/>
                <tag k="boundary" v="administrative"/><tag k="admin_level" v="9"/><tag k="name" v="Центральный район"/></way>
            </osm>''',encoding='utf-8')
            _,addresses,search,_,_=read_osm(source,[.3,.3,.7,.7],log=lambda *a,**kw:None)
            poi=next(e for e in search if e['name']=='Магазин')
            self.assertEqual('Тестоград',poi['locality']);self.assertEqual('Мира 1',poi['address'])
            self.assertEqual(1,len(addresses))
            self.assertEqual('Тестоград',next(e for e in search if e['name']=='Мира 1')['locality'])

    def test_invalid_identity_and_zip_paths_are_rejected_without_writing_outside(self):
        for ident in ('../escape','Карта','a/b','A-UPPER'):
            with self.assertRaises(ValueError):validate_identity(ident,'Карта',[0,0,1,1],[.5,.5])
        with tempfile.TemporaryDirectory() as temp:
            archive=Path(temp)/'bad.zip'
            with zipfile.ZipFile(archive,'w') as output:output.writestr('../escape.txt','wrong')
            with self.assertRaises(ValueError):validate_archive(archive)
            self.assertFalse((Path(temp).parent/'escape.txt').exists())

    def test_invalid_package_never_creates_output_zip(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp)
            (folder/'manifest.json').write_text(json.dumps(dict(format=2,id='test',title='Тест',bounds=[0,0,1,1],center=[.5,.5],files=[])))
            with self.assertRaises(ValueError):pack_directory(folder,folder/'test.jelandnav.zip')
            self.assertFalse((folder/'test.jelandnav.zip').exists())

if __name__=='__main__':unittest.main()
