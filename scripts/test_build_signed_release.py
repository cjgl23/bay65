"""Release safety regression tests using synthetic inputs only."""
import tempfile
from pathlib import Path
import unittest
import zipfile
from scripts.build_signed_release import check_badging, check_zip, ROOT
from scripts.check_apk_metadata import check_metadata, EXPECTED


class ReleaseSafetyTests(unittest.TestCase):
    def test_manifest_identity_and_debuggability(self):
        text = "package: name='app.nook.tv.debug' versionCode='6' versionName='0.3.0-tv'\n"
        check_badging(text, 'app.nook.tv.debug', '0.3.0-tv', '6')
        for changed in (text.replace("name='app.nook.tv.debug'", "name='app.bay65.tv'"),
                        text.replace("versionCode='6'", "versionCode='1'"),
                        text.replace('0.3.0-tv', '0.3.0-dev'), text + 'application-debuggable'):
            with self.assertRaises(RuntimeError):
                check_badging(changed, 'app.nook.tv.debug', '0.3.0-tv', '6')

    def test_all_variant_identities_and_branding(self):
        for variant, (package, version, _) in EXPECTED.items():
            text = (f"package: name='{package}' versionCode='6' versionName='{version}'\n"
                    "application-label:'BAY65'\nleanback-launchable-activity: name='app.nook.tv.MainActivity'\n")
            check_metadata(text, variant)
            for changed in (text.replace('BAY65', 'Nook'), text.replace(package, 'app.wrong.tv'),
                            text.replace("versionCode='6'", "versionCode='2'"),
                            text.replace("versionCode='6'", "versionCode='3'"),
                            text.replace("versionCode='6'", "versionCode='4'"),
                            text.replace("versionCode='6'", "versionCode='5'"),
                            text.replace(version, '0.1.1'), text.replace('app.nook.tv.MainActivity', 'app.bay65.tv.MainActivity')):
                with self.subTest(variant=variant), self.assertRaises(RuntimeError):
                    check_metadata(changed, variant)
            if variant != 'debug':
                with self.assertRaises(RuntimeError):
                    check_metadata(text + 'application-debuggable', variant)

    def test_maintenance_storage_identifiers_remain_compatible(self):
        java = ROOT / 'android/app/src/main/java/app/nook/tv'
        store = (java / 'NookStore.java').read_text()
        secret = (java / 'SecretStore.java').read_text()
        self.assertIn('getSharedPreferences("nook", Context.MODE_PRIVATE)', store)
        self.assertIn('new File(context.getFilesDir(), "photos")', store)
        self.assertIn('ALIAS="nook.lta.v1"', secret)
        self.assertIn('getSharedPreferences("nook_secret",Context.MODE_PRIVATE)', secret)
        self.assertIn("namespace 'app.nook.tv'", (ROOT / 'android/app/build.gradle').read_text())

    def test_archive_rejects_private_material(self):
        for name, data in [('key.jks', b'x'), ('local.properties', b'x'),
                           ('assets/photo.jpg', b'x'), ('config', b'synthetic-secret'),
                           ('config', b'/' + b'Users/example/sdk'), ('old.apk', b'x')]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temp:
                path = Path(temp) / 'test.zip'
                with zipfile.ZipFile(path, 'w') as z:
                    z.writestr(name, data)
                with self.assertRaises(RuntimeError):
                    check_zip(path, [b'synthetic-secret'])

    def test_apk_metadata_is_allowed_without_allowing_source_build_output(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'test.zip'
            with zipfile.ZipFile(path, 'w') as z:
                z.writestr('META-INF/com/android/build/gradle/app-metadata.properties', 'appMetadataVersion=1.0')
            check_zip(path, [], apk=True)
            with self.assertRaises(RuntimeError):
                check_zip(path, [])

    def test_archive_accepts_clean_source(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'test.zip'
            with zipfile.ZipFile(path, 'w') as z:
                z.writestr('BAY65-source/LICENSE', 'MIT License')
            check_zip(path, [b'synthetic-secret'])
