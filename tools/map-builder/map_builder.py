"""Jeland Map Builder: GUI, complete OSM build, packaging and validation."""
import argparse
from datetime import date
import importlib.metadata
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import traceback
from urllib.parse import urlsplit

from package_tools import digest,pack_directory,validate_archive,validate_identity,write_json,MAX_TOTAL

VERSION='1.0.0'

def doctor():
    if sys.version_info<(3,12):raise ValueError('Нужен Python 3.12 или новее (x64)')
    result={name:importlib.metadata.version(name) for name in ('pyvalhalla','osmium','shapely','pmtiles','mapbox-vector-tile')}
    if result['pyvalhalla']!='3.9.1':raise ValueError('Нужна pyvalhalla 3.9.1 для совместимости с текущим приложением')
    import valhalla,osmium,mapbox_vector_tile,pmtiles,shapely,tkinter
    print(json.dumps(dict(tool=VERSION,python=sys.version.split()[0],packages=result),ensure_ascii=False),flush=True)
    return result

def read_config(path):
    path=Path(path).resolve();config=json.loads(path.read_text('utf-8-sig'))
    for key in ('source','output'):
        if not config.get(key):raise ValueError('В конфигурации отсутствует '+key)
        value=Path(config[key]).expanduser()
        config[key]=(value if value.is_absolute() else path.parent/value).resolve()
    if not config['source'].is_file():raise ValueError('OSM-файл не найден: '+str(config['source']))
    if config['output'].exists():raise ValueError('Папка результата уже существует; укажите новую папку')
    if not config['source'].name.endswith('.osm.pbf'):raise ValueError('Нужен исходный файл .osm.pbf, не ZIP и не карта другого навигатора')
    bounds=config.get('bounds')
    validate_identity(config.get('id',''),config.get('title',''),bounds or [-1.,-1.,1.,1.],
                      [(bounds[1]+bounds[3])/2,(bounds[0]+bounds[2])/2] if bounds else [0,0])
    minimum=int(config.get('min_zoom',10));maximum=int(config.get('max_zoom',15))
    if not 8<=minimum<=maximum or maximum!=15:raise ValueError('min_zoom: 8–15; max_zoom должен быть 15 для номеров домов')
    config.update(min_zoom=minimum,max_zoom=maximum,threads=max(1,min(16,int(config.get('threads',2)))))
    existing=config['output'].parent
    while not existing.exists():existing=existing.parent
    if shutil.disk_usage(existing).free<2*MAX_TOTAL:
        raise ValueError('Нужно хотя бы 1 ГиБ свободного места; для большого региона потребуется больше')
    return config

def run(command,log):
    print('Этап: '+' '.join(str(arg) for arg in command[:4]),flush=True)
    with Path(log).open('a',encoding='utf-8') as file:
        process=subprocess.Popen([str(arg) for arg in command],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                                 text=True,encoding='utf-8',errors='replace',env=dict(os.environ,PYTHONIOENCODING='utf-8',
                                     PATH=str(Path(sys.executable).parent)+os.pathsep+os.environ.get('PATH','')))
        for line in process.stdout:
            file.write(line);file.flush();print(line.rstrip(),flush=True)
        if process.wait():raise ValueError('Этап Valhalla завершился с ошибкой. Журнал: '+str(log))

def build(config_path):
    versions=doctor();config=read_config(config_path)
    from osm_build import source_bounds,extract,read_osm,build_pmtiles,tile_budget
    from valhalla import get_config,Actor
    source=config['source'];out=config['output']
    print('1/6 Проверка исходного OSM и границ…',flush=True)
    source_hash=digest(source)
    bounds=config.get('bounds') or source_bounds(source)
    center=[(bounds[1]+bounds[3])/2,(bounds[0]+bounds[2])/2]
    validate_identity(config['id'],config['title'],bounds,center)
    tile_budget(bounds,config['min_zoom'],config['max_zoom'])
    out.mkdir(parents=True,exist_ok=False);work=out/'.build';work.mkdir()
    write_json(work/'request.json',dict(config,source=str(source),output=str(out)))
    graph_source=source;extract_report={}
    if config.get('bounds'):
        print('2/6 Вырезка региона с сохранением узлов и ограничений поворотов…',flush=True)
        graph_source=work/'region.osm.pbf';extract_report=extract(source,graph_source,bounds)
    print('3/6 Дороги, здания, номера домов и адресный поиск…',flush=True)
    features,addresses,entries,candidates,source_report=read_osm(source,bounds)
    if not candidates:raise ValueError('Нет автомобильных дорог для проверки маршрутов; проверьте границы')
    write_json(out/'search.json',entries)
    print('4/6 Векторная карта PMTiles…',flush=True)
    tiles=build_pmtiles(features,addresses,bounds,out/'map.pmtiles',config['min_zoom'],config['max_zoom'])
    del features,addresses,entries
    print('5/6 Граф Valhalla 3.9.1…',flush=True)
    graph_dir=work/'tiles';graph_dir.mkdir();graph_tar=out/'valhalla_tiles.tar'
    native=get_config(tile_dir=str(graph_dir),tile_extract='')
    native['mjolnir']['tile_extract']=str(graph_tar)
    native['mjolnir']['concurrency']=config['threads']
    native['mjolnir']['data_processing']['use_admin_db']=False
    native['mjolnir'].pop('tile_url',None)
    native['logging']={'type':'std_out','color':False}
    native_path=work/'valhalla.json';write_json(native_path,native)
    run([sys.executable,'-m','valhalla','valhalla_build_tiles','-c',native_path,graph_source],work/'graph.log')
    run([sys.executable,'-m','valhalla.valhalla_build_extract','-c',native_path],work/'graph.log')
    print('6/6 Проверка автомобильного маршрута и упаковка…',flush=True)
    actor=Actor(native);smoke=None;errors=[]
    for start,end in candidates[:50]:
        try:
            route=actor.route(dict(locations=[dict(lat=start[0],lon=start[1]),dict(lat=end[0],lon=end[1])],costing='auto',format='osrm',directions_options={'language':'ru-RU'}))
            meters=route.get('routes',[{}])[0].get('distance',0)
            if route.get('code')=='Ok' and meters>10:
                smoke=dict(from_point=start,to_point=end,meters=meters);break
        except RuntimeError as error:errors.append(str(error)[:200])
    del actor
    if smoke is None:raise ValueError('Граф не прошёл проверку автомобильного маршрута: '+str(errors[:2]))
    attribution='© OpenStreetMap contributors · ODbL 1.0'
    if (urlsplit(config.get('source_url','')).hostname or '').endswith('geofabrik.de'):attribution+=' · Geofabrik'
    manifest=dict(format=2,id=config['id'],title=config['title'],bounds=bounds,center=center,prepared=date.today().isoformat(),
                  builder='3.9.1',builderTool='Jeland Map Builder '+VERSION,source=config.get('source_url') or source.name,sourceSha256=source_hash,
                  attribution=attribution,mapFormat='pmtiles-v3-mvt-region',
                  houseNumberCount=source_report['addresses'],houseNumberLayer='addresses',houseNumberMinZoom=16,
                  searchLocalityVersion=1,searchAddressVersion=1,
                  limitations='Bounded offline OSM region; no traffic or elevation. Source coverage and mapped addresses vary.',
                  files=[dict(name=name,bytes=(out/name).stat().st_size,sha256=digest(out/name)) for name in ('map.pmtiles','search.json','valhalla_tiles.tar')])
    write_json(out/'manifest.json',manifest)
    package=out/(config['id']+'.jelandnav.zip')
    report=pack_directory(out,package)
    report.update(tool=VERSION,dependencies=versions,source=source_report,extract=extract_report,vector_tiles=tiles,route_test=smoke)
    write_json(out/'build-report.json',report)
    write_json(out/'maps.json',{'format':1,'maps':[dict(id=config['id'],title=config['title'],kind='region',url=package.name,bytes=report['bytes'],sha256=report['sha256'])]})
    (out/'SHA256SUMS.txt').write_text(report['sha256']+'  '+package.name+'\n',encoding='utf-8')
    (out/'GITHUB-RELEASE.txt').write_text('Загрузите '+package.name+' в новый обычный релиз GitHub.\nИмя файла не менять.\nLabel файла: '+config['title']+'\nНе назначайте релиз карт latest для приложения.\nДля отдельного сервера разместите maps.json рядом с пакетом.\n'+manifest['attribution']+'\n',encoding='utf-8')
    print('ГОТОВО: '+str(package),flush=True)
    print(json.dumps(report,ensure_ascii=False),flush=True)
    return report

def main():
    parser=argparse.ArgumentParser(description='Jeland Map Builder '+VERSION)
    sub=parser.add_subparsers(dest='command')
    sub.add_parser('gui');sub.add_parser('doctor')
    b=sub.add_parser('build');b.add_argument('--config',required=True)
    v=sub.add_parser('validate');v.add_argument('package')
    p=sub.add_parser('pack');p.add_argument('directory');p.add_argument('--output',required=True)
    args=parser.parse_args()
    try:
        if args.command in (None,'gui'):
            from builder_gui import launch
            launch()
        elif args.command=='doctor':doctor()
        elif args.command=='build':build(args.config)
        elif args.command=='validate':print(json.dumps(validate_archive(args.package),ensure_ascii=False))
        elif args.command=='pack':print(json.dumps(pack_directory(args.directory,args.output),ensure_ascii=False))
    except Exception as error:
        print('ОШИБКА: '+str(error),file=sys.stderr,flush=True)
        if args.command=='build':traceback.print_exc()
        return 1
    return 0

if __name__=='__main__':sys.exit(main())
