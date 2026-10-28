"""Publish an already validated Jeland OS release; credentials stay in process memory."""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import subprocess
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

REPO='mrRadik/jeland-os'
API='https://api.github.com/repos/'+REPO

def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,required=True)
    parser.add_argument('--commit',required=True)
    parser.add_argument('--branch',required=True)
    parser.add_argument('--publish',action='store_true')
    args=parser.parse_args();folder=args.directory.resolve()
    manifest=json.loads((folder/'jelandos-update.json').read_text('utf-8-sig'))
    version=manifest['versionName'];tag='v'+version
    if manifest['applicationId']!='ru.jeland.os' or len(args.commit)!=40:raise ValueError('Unexpected package or commit')
    apk=folder/manifest['apkName']
    if apk.name!=f'JelandOS-{version}.apk' or apk.stat().st_size!=manifest['size'] or digest(apk)!=manifest['sha256']:
        raise ValueError('APK does not match its update manifest')
    files=[apk,folder/'jelandos-update.json',folder/'JelandOS-Windows-Installer.zip',folder/'SHA256SUMS.txt',folder/'LICENSE',folder/'THIRD_PARTY_NOTICES.md']
    expected={path.name:(path.stat().st_size,digest(path)) for path in files}
    credential_reply=subprocess.run(['git','-c','credential.interactive=false','credential','fill'],
        input='protocol=https\nhost=github.com\n\n',capture_output=True,text=True,timeout=30,
        env=dict(os.environ,GCM_INTERACTIVE='Never',GIT_TERMINAL_PROMPT='0'))
    credential=dict(line.split('=',1) for line in credential_reply.stdout.splitlines() if '=' in line)
    token=credential.get('password')
    if credential_reply.returncode or not token:raise RuntimeError('GitHub credentials unavailable')
    headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'JelandOS-release'}
    def api(method,path,body=None):
        data=None if body is None else json.dumps(body,ensure_ascii=False).encode()
        with urlopen(Request(API+path,data=data,headers=dict(headers,**{'Content-Type':'application/json'}),method=method),timeout=60) as response:return json.load(response)
    repo=api('GET','')
    if repo['full_name'].lower()!=REPO.lower() or not repo.get('permissions',{}).get('push'):raise RuntimeError('Repository write access unavailable')
    if api('GET','/branches/'+quote(args.branch,safe=''))['commit']['sha']!=args.commit:raise ValueError('Remote release branch differs from the validated commit')
    if not args.publish:
        print(json.dumps({'ready':True,'tag':tag,'commit':args.commit,'assets':expected}));return
    try:release=api('GET','/releases/tags/'+tag)
    except HTTPError as error:
        if error.code!=404:raise
        release=api('POST','/releases',{'tag_name':tag,'target_commitish':args.commit,'name':'Jeland OS '+version,
            'body':(folder/'release-notes.md').read_text('utf-8'),'draft':True,'prerelease':False})
        print('Created draft',release['id'],flush=True)
    upload=urlsplit(release['upload_url'].split('{',1)[0])
    if upload.scheme!='https' or upload.hostname!='uploads.github.com' or not upload.path.startswith('/repos/'+REPO+'/releases/'):
        raise ValueError('Unexpected GitHub upload endpoint')
    assets={asset['name']:asset for asset in release['assets']}
    for path in files:
        size,sha=expected[path.name]
        if path.name in assets:
            asset=assets[path.name]
            if asset['size']!=size or asset.get('digest')!='sha256:'+sha:raise ValueError('Existing release asset differs: '+path.name)
            continue
        if not release['draft']:raise ValueError('Published release is incomplete; nothing was overwritten')
        connection=http.client.HTTPSConnection(upload.hostname,timeout=60,blocksize=1024*1024)
        try:
            connection.putrequest('POST',upload.path+'?name='+quote(path.name))
            for key,value in headers.items():connection.putheader(key,value)
            connection.putheader('Content-Type','application/octet-stream');connection.putheader('Content-Length',str(size));connection.endheaders()
            with path.open('rb') as stream:
                while block:=stream.read(1024*1024):connection.send(block)
            response=connection.getresponse();data=json.loads(response.read())
            if response.status!=201 or data.get('size')!=size or data.get('digest')!='sha256:'+sha:raise RuntimeError('Upload verification failed: '+path.name)
            print('Verified asset',path.name,size,flush=True)
        finally:connection.close()
    verified=api('GET','/releases/'+str(release['id']))
    actual={asset['name']:(asset['size'],asset.get('digest')) for asset in verified['assets']}
    if any(actual.get(name)!=(size,'sha256:'+sha) for name,(size,sha) in expected.items()):raise RuntimeError('Draft asset verification failed')
    if verified['draft']:verified=api('PATCH','/releases/'+str(release['id']),{'draft':False,'prerelease':False,'make_latest':'true'})
    latest=api('GET','/releases/latest')
    if latest['tag_name']!=tag or latest['draft']:raise RuntimeError('Latest release is not the prepared version')
    print(json.dumps({'url':verified['html_url'],'tag':tag,'commit':args.commit,'assets_verified':len(expected),'latest':True}),flush=True)

if __name__=='__main__':main()
