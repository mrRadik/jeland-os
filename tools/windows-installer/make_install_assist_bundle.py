"""Build the USB installer and explicit, fixed-service accessibility setup for Jeland OS."""
import argparse
import json
from pathlib import Path
import shutil
import zipfile
from make_bundle import identity, sha, ROOT, SOURCE

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk',type=Path,required=True)
    p.add_argument('--aapt',type=Path,required=True)
    p.add_argument('--adb-dir',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();info=identity(a.aapt,a.apk)
    if info['package']!='ru.jeland.os' or not all(c in '0123456789.' for c in info['versionName']):raise ValueError('Unexpected APK')
    name='JelandOS-InstallAssist-'+info['versionName']
    folder=a.output/name;archive=a.output/(name+'.zip')
    if folder.exists() or archive.exists():raise ValueError('Choose a fresh output directory')
    folder.mkdir(parents=True)
    def copy(src,dst):
        target=folder/dst;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,target)
    copy(SOURCE/'setup-install-assist.ps1','setup.ps1')
    for f in ('engine/AdbTools.ps1','engine/InstallAssist.ps1','engine/Install-APK.ps1','install-helper/helper.dex','install-helper/InstallVariants.java'):
        copy(SOURCE/f,f)
    info['file']='JelandOS-'+info['versionName']+'.apk';copy(a.apk,info['file'])
    for f in ('adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll'):copy(a.adb_dir/f,'tools/'+f)
    copy(a.adb_dir/'NOTICE.txt','licenses/ADB-NOTICE.txt')
    copy(ROOT/'LICENSE','licenses/LICENSE.txt')
    copy(ROOT/'THIRD_PARTY_NOTICES.md','licenses/THIRD_PARTY_NOTICES.md')
    (folder/'licenses/ADB-SOURCE.txt').write_text('Android SDK Platform Tools\nhttps://android.googlesource.com/platform/packages/modules/adb/\n','utf-8')
    for filename,mode in (('install.cmd','install'),('enable.cmd','enable'),('disable.cmd','disable'),('status.cmd','status')):
        (folder/filename).write_text('@echo off\nsetlocal EnableExtensions\nchcp 65001 >nul\ncd /d "%~dp0"\n'
            'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" -Mode '+mode+' -Serial "%~1"\n'
            'set "RESULT=%ERRORLEVEL%"\npause\nexit /b %RESULT%\n','utf-8')
    (folder/'INSTALL.txt').write_text('Jeland OS '+info['versionName']+' — помощь при установке\n\n'
        '1. Распакуйте архив целиком.\n2. Подключите компьютер к ГУ по USB с включённым ADB.\n'
        '3. На стоянке запустите install.cmd и подтвердите перечисленные действия.\n'
        'Будет установлена OS и подключена только её служба помощи через ADB. Меню Android не требуется.\n'
        'После этого в Jeland OS можно проверить онлайн-обновление на 0.9.40.\n'
        'Если галочка помощи была вручную выключена в Jeland OS, включите её в настройках самой OS.\n\n'
        'enable.cmd — подключить службу без повторной установки APK.\n'
        'status.cmd — проверить состояние. disable.cmd — отключить только службу Jeland OS.\n'
        'Изменения сохраняются после перезагрузки. Служба читает только системный установщик и нажимает '
        'Установить для обновления Jeland OS после вашего запуска. Другие службы сохраняются.\n'
        'Root, администратор устройства и выдача WRITE_SECURE_SETTINGS приложению не используются.\n','utf-8')
    for f in folder.rglob('*'):
        if f.suffix in ('.ps1','.cmd'):
            text=f.read_text('utf-8-sig').replace('\r\n','\n')
            f.write_bytes((b'\xef\xbb\xbf' if f.suffix=='.ps1' else b'')+text.replace('\n','\r\n').encode())
    entries=[{'path':f.relative_to(folder).as_posix(),'bytes':f.stat().st_size,'sha256':sha(f)} for f in sorted(folder.rglob('*')) if f.is_file()]
    (folder/'assist-bundle.json').write_text(json.dumps({'schema':1,'os':info,'files':entries},ensure_ascii=False,indent=2),'utf-8')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as z:
        for f in sorted(folder.rglob('*')):
            if f.is_file():z.write(f,f.relative_to(a.output).as_posix())
    with zipfile.ZipFile(archive) as z:
        if z.testzip():raise ValueError('ZIP integrity check failed')
    print(json.dumps({'zip':str(archive),'bytes':archive.stat().st_size,'sha256':sha(archive),'os':info}))

if __name__=='__main__':main()
