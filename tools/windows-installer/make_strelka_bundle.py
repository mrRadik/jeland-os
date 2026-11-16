#!/usr/bin/env python3
"""Build the portable Strelka installer and notification-access setup kit; no third-party APKs included."""
import argparse
import hashlib
from pathlib import Path
import zipfile

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb-dir',type=Path,required=True,help='Official Android platform-tools directory including NOTICE.txt')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[2]
    source=root/'tools/windows-installer'
    files={
        'START.cmd':source/'install-strelka.cmd',
        'install-strelka.cmd':source/'install-strelka.cmd',
        'install-strelka.ps1':source/'install-strelka.ps1',
        'engine/ApkMetadata.ps1':source/'engine/ApkMetadata.ps1',
        'engine/StrelkaAccess.ps1':source/'engine/StrelkaAccess.ps1',
        'engine/Install-APK.ps1':source/'engine/Install-APK.ps1',
        'install-helper/helper.dex':source/'install-helper/helper.dex',
        'setup-strelka.cmd':source/'setup-strelka.cmd',
        'setup-strelka.ps1':source/'setup-strelka.ps1',
        'engine/AdbTools.ps1':source/'engine/AdbTools.ps1',
        'README.md':root/'docs/ROAD-ALERTS.md',
        'licenses/JelandOS-LICENSE.md':root/'LICENSE',
        'licenses/ADB-NOTICE.txt':args.adb_dir/'NOTICE.txt',
    }
    files.update({f'tools/{name}':args.adb_dir/name for name in ('adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll')})
    for p in files.values():
        if not p.is_file():parser.error(f'Missing input: {p}')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    hashes=[]
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as z:
        for name,p in files.items():
            data=p.read_bytes()
            z.writestr('JelandOS-Strelka-setup/'+name,data)
            hashes.append(f'{hashlib.sha256(data).hexdigest()}  {name}')
        z.writestr('JelandOS-Strelka-setup/licenses/ADB-SOURCE.txt','Android SDK Platform Tools for Windows\nhttps://dl.google.com/android/repository/platform-tools-latest-windows.zip\nhttps://android.googlesource.com/platform/packages/modules/adb/\n')
        z.writestr('JelandOS-Strelka-setup/SHA256SUMS.txt','\n'.join(hashes)+'\n')
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        for name in files:
            if name.endswith('.ps1'):
                assert z.read('JelandOS-Strelka-setup/'+name).startswith(b'\xef\xbb\xbf'), name
    print(args.output)

if __name__=='__main__':main()
