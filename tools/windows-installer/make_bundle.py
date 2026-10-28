"""Package the offline Windows installer. Does not install apps or change device permissions."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[2]
SOURCE=Path(__file__).resolve().parent
VOICE_SHA='3da68e8a386d0e6fbc2b8964d85a02158dc2d7a7c1840c836442ad66edca5acc'

def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def identity(aapt,apk):
    import re
    data=subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True,encoding='utf-8')
    match=re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",data)
    if not match:raise ValueError('Cannot read APK identity')
    return {'package':match[1],'versionCode':int(match[2]),'versionName':match[3]}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',required=True,type=Path)
    parser.add_argument('--voice',required=True,type=Path)
    parser.add_argument('--aapt',required=True,type=Path)
    parser.add_argument('--adb-dir',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    os_info=identity(args.aapt,args.apk);voice=identity(args.aapt,args.voice)
    if os_info['package']!='ru.jeland.os' or voice['package']!='ru.kost.ruvoice' or sha(args.voice)!=VOICE_SHA:
        raise ValueError('Unexpected application or non-original RuVoice APK')
    version=os_info['versionName']
    if any(x not in '0123456789.-' for x in version):raise ValueError('Unexpected version name')
    destination=args.output/f'JelandOS-Installer-{version}'
    archive=args.output/f'JelandOS-Windows-Installer-{version}.zip'
    if destination.exists() or archive.exists():raise ValueError('Choose a new output directory; an existing bundle is not overwritten')
    destination.mkdir(parents=True)
    def copy(source,name):
        target=destination/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
    for name in ['install.cmd','install.ps1','engine/AdbTools.ps1','engine/Install-APK.ps1',
                 'install-helper/helper.dex','install-helper/InstallVariants.java']:
        copy(SOURCE/name,name)
    for path in destination.rglob('*'):
        if path.suffix in ('.ps1','.cmd'):
            text=path.read_text('utf-8-sig').replace('\r\n','\n')
            path.write_bytes((b'\xef\xbb\xbf' if path.suffix=='.ps1' else b'')+text.replace('\n','\r\n').encode())
    os_info['file']=f'JelandOS-{version}.apk';voice['file']='ruvoice-tts-1.1.1.apk'
    copy(args.apk,'packages/'+os_info['file']);copy(args.voice,'packages/'+voice['file'])
    for name in ['adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll']:
        copy(args.adb_dir/name,'tools/'+name)
    copy(args.adb_dir/'NOTICE.txt','licenses/ADB-NOTICE.txt')
    copy(ROOT/'LICENSE','licenses/JelandOS-LICENSE.txt')
    copy(ROOT/'THIRD_PARTY_NOTICES.md','licenses/JelandOS-THIRD-PARTY.md')
    copy(ROOT/'app/src/main/assets/voice/ruvoice/LICENSE.txt','licenses/RuVoice-LICENSE.txt')
    copy(ROOT/'app/src/main/assets/voice/ruvoice/NOTICE.txt','licenses/RuVoice-NOTICE.txt')
    (destination/'licenses/ADB-SOURCE.txt').write_text('Android SDK Platform Tools for Windows\nhttps://dl.google.com/android/repository/platform-tools-latest-windows.zip\nhttps://android.googlesource.com/platform/packages/modules/adb/\n','utf-8')
    guide=f'''Jeland OS {version} — установщик для Windows 10/11

1. Распакуйте ZIP целиком в обычную папку. Не запускайте install.cmd из окна архива.
2. На стоянке включите ADB на ГУ: штатные настройки → Система → несколько раз
   нажать «Версия» → инженерное меню → ADB/USB2.0 → DEVICE (зависит от прошивки).
3. Подключите ГУ к компьютеру кабелем передачи данных USB. Подтвердите RSA-запрос
   на экране ГУ, если он появится. Если устройство не видно, проверьте кабель и драйвер.
4. Запустите install.cmd. Если подключено несколько устройств, выберите нужное.
5. Проверьте модель/устройство и подтвердите установку буквой Y.
6. Дождитесь «Готово». Будут установлены Jeland OS и оригинальный RuVoice 1.1.1.
7. В Jeland OS при необходимости включите «Запускать вместе с системой».
   В настройках навигации подключите RuVoice, подтвердите условия модели,
   выберите голос и нажмите проверку голоса.

Интернет при установке, Python, Android SDK и отдельный помощник не нужны.
Системный синтезатор по умолчанию не меняется. Приложения и настройки не удаляются.
Файлы комплекта проверяются до начала установки. Версии на ГУ проверяются повторно.

Разрешения Jeland OS: GPS, состояние связи, Bluetooth и уведомления (если доступны
в этой версии Android), SYSTEM_ALERT_WINDOW для автозапуска и
REQUEST_INSTALL_PACKAGES для обновлений. RuVoice получает только уведомления.
Это не выдача root или системной подписи. Опция автозапуска включается в приложении.

Если установка не подтверждена, следующие действия автоматически не выполняются.
Не запускайте повтор вслепую при неизвестном результате: проверьте версии на ГУ.
Сообщения и результаты сохраняются рядом с установщиком в папке logs.
Сначала устраните проблему, затем повторите установку.

RuVoice и его модель: CC BY-NC-SA 4.0, некоммерческое использование.
Авторы, исходники и тексты лицензий компонентов находятся в папке licenses.
Поддерживаемая модель — Jeland J7 / Desay, Android 11+. Другие ГУ требуют адаптации.
https://github.com/mrRadik/jeland-os
'''
    (destination/'INSTALL.txt').write_bytes(b'\xef\xbb\xbf'+guide.replace('\n','\r\n').encode())
    files=[]
    for path in sorted(destination.rglob('*')):
        if path.is_file():files.append({'path':path.relative_to(destination).as_posix(),'bytes':path.stat().st_size,'sha256':sha(path)})
    manifest={'schema':1,'os':os_info,'voice':voice,'files':files}
    (destination/'bundle.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n','utf-8')
    sums=''.join(f'{item["sha256"]}  {item["path"]}\n' for item in files)
    sums+=sha(destination/'bundle.json')+'  bundle.json\n'
    (destination/'SHA256SUMS.txt').write_text(sums,'utf-8')
    with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as output:
        for path in sorted(destination.rglob('*')):
            if path.is_file():output.write(path,destination.name+'/'+path.relative_to(destination).as_posix())
    with zipfile.ZipFile(archive) as output:
        if output.testzip() is not None:raise ValueError('ZIP failed integrity check')
    print(json.dumps({'zip':str(archive),'bytes':archive.stat().st_size,'sha256':sha(archive),
        'os':os_info,'voice':voice},ensure_ascii=False),flush=True)

if __name__=='__main__':main()
