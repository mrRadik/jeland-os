"""Refresh an explicitly identified existing release, keeping verified assets for rollback."""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import socket
import subprocess
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

REPO = 'mrRadik/jeland-os'
API = 'https://api.github.com/repos/' + REPO


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def asset_identity(asset):
    return asset['id'], asset['name'], asset['size'], asset.get('digest')


def refresh(api, upload, release, files, notes, commit, move_tag):
    """Upload before replacing; restore original names, body and tag on promotion failure."""
    release_id = release['id']
    original = {a['name']: a for a in release['assets']}
    staged = []
    renamed = []
    tag_moved = False
    body_changed = False
    try:
        for path in files:
            size, digest = path.stat().st_size, 'sha256:' + sha(path)
            old = original.get(path.name)
            if old and (old['size'], old.get('digest')) == (size, digest):
                continue
            temporary_name = f'pending-{release_id}-{path.name}'
            if temporary_name in original:
                raise ValueError('Staged asset already exists; inspect it before retrying')
            asset = upload(path, temporary_name)
            staged.append((path.name, asset))
            if (asset['size'], asset.get('digest')) != (size, digest):
                raise RuntimeError('Upload verification failed: ' + path.name)
        current = api('GET', f'/releases/{release_id}')
        actual = {a['name']: asset_identity(a) for a in current['assets']}
        if any(actual.get(name) != asset_identity(a) for name, a in original.items()):
            raise RuntimeError('Release assets changed concurrently')
        if current['body'] != release['body'] or current['tag_name'] != release['tag_name']:
            raise RuntimeError('Release metadata changed concurrently')
        move_tag(False)
        tag_moved = True
        for name, new in staged:
            old = original.get(name)
            if old:
                renamed.append(old)
                api('PATCH', f"/releases/assets/{old['id']}", {'name': f"previous-{old['id']}-{name}"})
            api('PATCH', f"/releases/assets/{new['id']}", {'name': name})
        # Set this first so an ambiguous network response also restores the old body.
        body_changed = True
        api('PATCH', f'/releases/{release_id}', {
            'body': notes, 'target_commitish': commit, 'make_latest': 'true',
        })
        verified = api('GET', f'/releases/{release_id}')
        assets = {a['name']: (a['size'], a.get('digest')) for a in verified['assets']}
        if verified['body'] != notes or verified['target_commitish'] != commit:
            raise RuntimeError('Release metadata verification failed')
        if any(assets.get(p.name) != (p.stat().st_size, 'sha256:' + sha(p)) for p in files):
            raise RuntimeError('Published asset verification failed')
    except Exception:
        errors = []
        # Delete uploaded assets first; their names may have changed despite a lost response.
        for _, asset in staged:
            try:
                api('DELETE', f"/releases/assets/{asset['id']}")
            except Exception as error:
                errors.append(str(error))
        for old in reversed(renamed):
            try:
                api('PATCH', f"/releases/assets/{old['id']}", {'name': old['name']})
            except Exception as error:
                errors.append(str(error))
        if body_changed:
            try:
                api('PATCH', f'/releases/{release_id}', {
                    'body': release['body'], 'target_commitish': release['target_commitish'],
                })
            except Exception as error:
                errors.append(str(error))
        if tag_moved:
            try:
                move_tag(True)
            except Exception as error:
                errors.append(str(error))
        if errors:
            raise RuntimeError('Rollback needs attention: ' + '; '.join(errors))
        raise
    # Publication is verified. Cleanup failures must not roll back a successful release.
    for old in renamed:
        api('DELETE', f"/releases/assets/{old['id']}")
    return verified


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--snapshot', type=Path, required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--branch', required=True)
    parser.add_argument('--expected-tag-commit', required=True)
    parser.add_argument('--ipv4', action='store_true')
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    if args.ipv4:
        getaddrinfo = socket.getaddrinfo
        socket.getaddrinfo = lambda host, port, family=0, *a, **kw: getaddrinfo(host, port, socket.AF_INET, *a, **kw)
    folder = args.directory.resolve()
    manifest = json.loads((folder / 'jelandos-update.json').read_text('utf-8-sig'))
    tag = 'v' + manifest['versionName']
    files = [folder / name for name in [manifest['apkName'], 'JelandOS-Windows-Installer.zip',
        'JelandOS-Strelka-setup.zip', 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'SHA256SUMS.txt',
        'jelandos-update.json']]  # Promote the update manifest last.
    if manifest['applicationId'] != 'ru.jeland.os' or files[0].name != f"JelandOS-{manifest['versionName']}.apk":
        raise ValueError('Unexpected application identity')
    if (files[0].stat().st_size, sha(files[0])) != (manifest['size'], manifest['sha256']):
        raise ValueError('APK differs from manifest')
    sums = dict(line.split('  ', 1)[::-1] for line in (folder / 'SHA256SUMS.txt').read_text().splitlines())
    if any(sums.get(p.name) != sha(p) for p in files if p.name != 'SHA256SUMS.txt'):
        raise ValueError('Release checksums differ')
    notes = (folder / 'release-notes.md').read_text('utf-8')
    bundled = Path(__file__).resolve().parents[1] / f"app/src/main/assets/updates/{manifest['versionCode']}.md"
    if notes != bundled.read_text('utf-8'):
        raise ValueError('Release and bundled notices differ')
    snapshot = json.loads(args.snapshot.read_text('utf-8-sig'))
    credential_reply = subprocess.run(['git', '-c', 'credential.interactive=false', 'credential', 'fill'],
        input='protocol=https\nhost=github.com\n\n', capture_output=True, text=True, timeout=30,
        env=dict(os.environ, GCM_INTERACTIVE='Never', GIT_TERMINAL_PROMPT='0'))
    credential = dict(line.split('=', 1) for line in credential_reply.stdout.splitlines() if '=' in line)
    token = credential.get('password')
    if credential_reply.returncode or not token:
        raise RuntimeError('GitHub credentials unavailable')
    headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'JelandOS-release'}

    def api(method, path, body=None):
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
        with urlopen(Request(API + path, data=data, headers=dict(headers, **{'Content-Type': 'application/json'}), method=method), timeout=90) as response:
            return None if response.status == 204 else json.load(response)

    release = api('GET', '/releases/tags/' + tag)
    if release['id'] != snapshot['id'] or release['tag_name'] != snapshot['tag_name'] or release['draft'] or release['prerelease']:
        raise ValueError('Unexpected release')
    if sorted(map(asset_identity, release['assets'])) != sorted(map(asset_identity, snapshot['assets'])) or release['body'] != snapshot['body']:
        raise ValueError('Release changed since the saved snapshot')
    if api('GET', '/branches/' + quote(args.branch, safe=''))['commit']['sha'] != args.commit:
        raise ValueError('Remote release branch differs from the validated commit')
    reference = api('GET', '/git/ref/tags/' + tag)
    if reference['object']['type'] != 'commit' or reference['object']['sha'] != args.expected_tag_commit:
        raise ValueError('Release tag changed or is not a lightweight tag')
    upload_url = urlsplit(release['upload_url'].split('{', 1)[0])
    if upload_url.scheme != 'https' or upload_url.hostname != 'uploads.github.com' or upload_url.path != f"/repos/{REPO}/releases/{release['id']}/assets":
        raise ValueError('Unexpected upload endpoint')

    def upload(path, name):
        connection = http.client.HTTPSConnection(upload_url.hostname, timeout=180, blocksize=1024*1024)
        try:
            connection.putrequest('POST', upload_url.path + '?name=' + quote(name))
            for key, value in headers.items():
                connection.putheader(key, value)
            connection.putheader('Content-Type', 'application/octet-stream')
            connection.putheader('Content-Length', str(path.stat().st_size))
            connection.endheaders()
            with path.open('rb') as stream:
                while block := stream.read(1024*1024):
                    connection.send(block)
            response = connection.getresponse()
            result = json.loads(response.read())
            if response.status != 201:
                raise RuntimeError('Asset upload failed: ' + str(response.status))
            print('Uploaded', path.name, flush=True)
            return result
        finally:
            connection.close()

    def move_tag(rollback):
        old, new = (args.commit, args.expected_tag_commit) if rollback else (args.expected_tag_commit, args.commit)
        subprocess.run(['git', 'push', f'--force-with-lease=refs/tags/{tag}:{old}',
            'origin', f'{new}:refs/tags/{tag}'], check=True, timeout=90)

    if not args.publish:
        print(json.dumps({'ready': True, 'release': release['id'], 'tag': tag, 'versionCode': manifest['versionCode']}))
        return
    result = refresh(api, upload, release, files, notes, args.commit, move_tag)
    latest = api('GET', '/releases/latest')
    if latest['id'] != result['id'] or api('GET', '/git/ref/tags/' + tag)['object']['sha'] != args.commit:
        raise RuntimeError('Latest release or tag verification failed')
    print(json.dumps({'url': result['html_url'], 'commit': args.commit, 'assets_verified': len(files)}), flush=True)


if __name__ == '__main__':
    main()
