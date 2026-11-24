import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('refresh_release', Path(__file__).with_name('refresh-release.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RefreshReleaseTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / 'app.apk'
        self.path.write_bytes(b'new APK')
        self.original = {'id': 7, 'body': 'original notes', 'tag_name': 'v1.4.1',
            'target_commitish': 'old', 'assets': [{'id': 1, 'name': 'app.apk', 'size': 3, 'digest': 'sha256:old'}]}
        self.release = copy.deepcopy(self.original)
        self.moves = []
        self.fail_promotion = False
        self.bad_upload = False

    def api(self, method, path, body=None):
        if path.startswith('/releases/assets/'):
            asset_id = int(path.rsplit('/', 1)[1])
            asset = next(a for a in self.release['assets'] if a['id'] == asset_id)
            if method == 'DELETE':
                self.release['assets'].remove(asset)
            else:
                asset.update(body)
                # Simulate a mutation that succeeded but whose response was lost.
                if self.fail_promotion and asset_id == 2 and body['name'] == 'app.apk':
                    raise OSError('lost response')
            return copy.deepcopy(asset)
        if method == 'PATCH':
            self.release.update(body)
        return copy.deepcopy(self.release)

    def upload(self, path, name):
        asset = {'id': 2, 'name': name, 'size': path.stat().st_size,
            'digest': 'sha256:' + ('invalid' if self.bad_upload else module.sha(path))}
        self.release['assets'].append(asset)
        return copy.deepcopy(asset)

    def run_refresh(self):
        return module.refresh(self.api, self.upload, self.original, [self.path], 'all notes', 'new', self.moves.append)

    def test_verified_upload_replaces_asset_and_cleans_backup(self):
        self.run_refresh()
        self.assertEqual(self.moves, [False])
        self.assertEqual(self.release['body'], 'all notes')
        self.assertEqual(self.release['target_commitish'], 'new')
        self.assertEqual([(a['id'], a['name']) for a in self.release['assets']], [(2, 'app.apk')])

    def test_bad_upload_preserves_published_release_and_tag(self):
        self.bad_upload = True
        with self.assertRaisesRegex(RuntimeError, 'Upload verification'):
            self.run_refresh()
        self.assertEqual(self.release, self.original)
        self.assertEqual(self.moves, [])

    def test_ambiguous_promotion_restores_original_asset_and_tag(self):
        self.fail_promotion = True
        with self.assertRaisesRegex(OSError, 'lost response'):
            self.run_refresh()
        self.assertEqual(self.release, self.original)
        self.assertEqual(self.moves, [False, True])

    def test_concurrent_asset_change_blocks_promotion(self):
        upload = self.upload
        def changed_upload(path, name):
            result = upload(path, name)
            self.release['assets'][0]['size'] = 9
            return result
        with self.assertRaisesRegex(RuntimeError, 'concurrently'):
            module.refresh(self.api, changed_upload, self.original, [self.path], 'notes', 'new', self.moves.append)
        self.assertEqual(self.moves, [])
        self.assertEqual(len(self.release['assets']), 1)
        self.assertEqual(self.release['assets'][0]['size'], 9)


if __name__ == '__main__':
    unittest.main()
