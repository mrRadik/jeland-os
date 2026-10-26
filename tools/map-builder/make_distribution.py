"""Build a small standalone source distribution; no Python, data or caches embedded."""
import hashlib
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]
VERSION='1.0.0'
FILES=['MAP-BUILDER.cmd','LICENSE','docs/MAP-BUILDER.md',
       *['tools/map-builder/'+name for name in ('start.ps1','requirements.txt','example-region.json','map_builder.py',
            'osm_build.py','package_tools.py','builder_gui.py','test_builder.py')]]

def main():
    target=ROOT/'artifacts'/f'Jeland-Map-Builder-{VERSION}.zip'
    if target.exists():raise ValueError('Distribution already exists; do not overwrite a delivered archive')
    target.parent.mkdir(parents=True,exist_ok=True)
    prefix=f'Jeland-Map-Builder-{VERSION}/'
    with zipfile.ZipFile(target,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for name in FILES:archive.write(ROOT/name,prefix+name)
        archive.writestr(prefix+'START-HERE.txt',
            'Jeland Map Builder '+VERSION+'\n\n'
            '1. Install Python 3.12 x64 from python.org (enable Add Python to PATH).\n'
            '2. Extract this whole ZIP into a writable folder.\n'
            '3. Run MAP-BUILDER.cmd. First launch installs tools; internet is required once.\n'
            '4. Select an OSM .osm.pbf, name and a NEW output folder, then build.\n'
            'Russian instructions: docs/MAP-BUILDER.md (the Instruction button opens it).\n'
            'No Docker, API keys, Android SDK or administrator rights required.\n')
    with target.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    print(str(target));print('bytes='+str(target.stat().st_size));print('sha256='+digest)

if __name__=='__main__':main()
