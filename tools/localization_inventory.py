"""List Russian UI phrases and Kotlin interpolation slots for translation review."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
def strings(source):
    result=[]
    def quoted(i):
        out='';slot=0;i+=1
        while i<len(source):
            c=source[i]
            if c=='"':return out,i+1
            if c=='\\' and i+1<len(source):
                out+=dict(n='\n',t='\t',r='\r').get(source[i+1],source[i+1]);i+=2;continue
            if source.startswith('${',i):
                out+='{'+str(slot)+'}';slot+=1;i+=2;depth=1
                while i<len(source) and depth:
                    if source[i]=='"':
                        inner,i=quoted(i)
                        if re.search('[А-Яа-яЁё]',inner):result.append(inner)
                    else:
                        depth+=int(source[i]=='{')-int(source[i]=='}');i+=1
                continue
            if c=='$' and i+1<len(source) and (source[i+1].isalpha() or source[i+1]=='_'):
                m=re.match(r'\$[\w]+',source[i:]);out+='{'+str(slot)+'}';slot+=1;i+=len(m[0]);continue
            out+=c;i+=1
        return out,i
    i=0
    while i<len(source):
        if source.startswith('//',i):i=source.find('\n',i) if '\n' in source[i:] else len(source)
        elif source.startswith('/*',i):
            end=source.find('*/',i+2);i=end+2 if end>=0 else len(source)
        elif source[i]=='"':
            value,i=quoted(i)
            if re.search('[А-Яа-яЁё]',value):result.append(value)
        else:i+=1
    return result
def inventory():
    base=ROOT/'app/src/main/java/ru/jeland/os'
    files=list((base/'ui').rglob('*.kt'))+list((base/'core').glob('*.kt'))+list((base/'core/automation').glob('*.kt'))
    files+=[p for p in (base/'navigation').glob('*.kt') if any(x in p.name for x in ['Panel','Screen','Places','Catalog','Voice.kt','Gps'])]
    values=set()
    for file in files:
        if any(x in file.name for x in ['Debug','Connection','TyreTemperature']):continue
        values.update(strings(file.read_text('utf-8')))
    return sorted(values)
if __name__=='__main__':
    values=inventory();target=ROOT/'artifacts/localization-inventory.json';target.write_text(json.dumps(values,ensure_ascii=False,indent=2),'utf-8');print(len(values),target)
