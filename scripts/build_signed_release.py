#!/usr/bin/env python3
"""Build, test, explicitly sign and validate local owner release artifacts."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_CERT = '4e0f012a139ff2e13c07b128c1b4c77d277d47d3995d202937bde75d8ab0c0ed'
TV_CERT = '2f52129dd5172e60707d26c3fcc14878ff2a7b72c09107fbf8b7eacb82fe616f'


def run(args, **kwargs):
    return subprocess.check_output([str(x) for x in args], cwd=ROOT, **kwargs)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def check_badging(text, package, version, code):
    line = text.splitlines()[0]
    require(f"name='{package}'" in line and f"versionName='{version}'" in line
            and f"versionCode='{code}'" in line and 'application-debuggable' not in text,
            'Unexpected APK package/version/debuggable state')


def check_zip(path, secrets, apk=False):
    with zipfile.ZipFile(path) as z:
        require(z.testzip() is None, 'ZIP integrity failure')
        for name in z.namelist():
            parts = Path(name).parts
            require(not (name.lower().endswith(('.jks', '.keystore', '.pem', '.p12', '.apk', '.jpg', '.jpeg', '.png', '.webp'))
                         or name.startswith('/') or '..' in parts
                         or any(p in {'.git', '.gradle', 'local.properties', '.env'} or p.startswith('.env.') for p in parts)
                         or (not apk and 'build' in parts)),
                    'Forbidden archive entry: ' + name)
            data = z.read(name)
            require(not any(secret and secret in data for secret in secrets),
                    'Private material detected in archive')
            require((b'/' + b'Users/') not in data and (b'-----BEGIN ' + b'PRIVATE KEY-----') not in data,
                    'Local configuration/key material detected')


def verify_apk(path, tools, cert, package, version, code, secrets):
    info = run([tools / 'apksigner', 'verify', '--verbose', '--print-certs', path], text=True)
    found = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', info)
    require([x.lower() for x in found] == [cert], 'Unexpected signing certificate')
    badging = run([tools / 'aapt', 'dump', 'badging', path], text=True)
    check_badging(badging, package, version, code)
    require("application-label:'BAY65'" in badging, 'Unexpected application label')
    run([tools / 'zipalign', '-c', '-P', '16', '4', path])
    check_zip(path, secrets, apk=True)
    return info


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--signing-dir', type=Path, default=Path(os.environ.get('NOOK_SIGNING_DIR', Path.home() / '.local/share/nook/signing')))
    p.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', Path.home() / 'Library/Android/sdk'))))
    p.add_argument('--out', type=Path, required=True, help='New output root outside the repository')
    args = p.parse_args()
    os.umask(0o077)
    out, signing, sdk = args.out.resolve(), args.signing_dir.resolve(), args.sdk.resolve()
    require(not out.is_relative_to(ROOT) and not out.exists(), 'Output must be new and outside repository')
    require(not run(['git', 'status', '--porcelain']).strip(), 'Commit changes before building (source archive uses HEAD)')
    revision = run(['git', 'rev-parse', 'HEAD'])
    tools = sdk / 'build-tools/36.0.0'
    for executable in ('java', 'keytool', 'node', 'python3'):
        require(shutil.which(executable), 'Missing prerequisite: ' + executable)
    for file in (tools / 'apksigner', tools / 'zipalign', tools / 'aapt', sdk / 'platforms/android-36/android.jar'):
        require(file.is_file(), 'Missing SDK prerequisite: ' + str(file))
    password_file = signing / 'password'
    tv_password_file = signing / 'tv-password'
    for file in (password_file, tv_password_file, signing / 'nook-release.jks', signing / 'nook-tv.jks'):
        require(file.is_file() and file.stat().st_mode & 0o077 == 0, 'Signing files must exist and be owner-only')
    password = password_file.read_text().rstrip('\r\n')
    tv_password = tv_password_file.read_text().rstrip('\r\n')
    require(bool(tv_password) and '\n' not in tv_password and '\r' not in tv_password,
            'TV password file must contain one nonempty line')
    require(bool(password) and '\n' not in password and '\r' not in password, 'Password file must contain one nonempty line')
    env = os.environ.copy()
    for key in list(env):
        if key.startswith('NOOK_STORE_'):
            del env[key]
    local = ROOT / 'android/local.properties'
    if local.exists():
        configured = re.search(r'^sdk\.dir=(.+)$', local.read_text(), re.MULTILINE)
        require(configured is None or Path(configured.group(1)).resolve() == sdk,
                'local.properties SDK differs from --sdk; update the untracked local file')
    env['ANDROID_HOME'] = str(sdk)
    env['ANDROID_SDK_ROOT'] = str(sdk)
    # Credentials go only to keytool's short-lived environment and apksigner stdin.
    variants = [('release', 'nook-release.jks', 'nook', password, PUBLIC_CERT, 'app.bay65.tv', ''),
                ('tv', 'nook-tv.jks', 'androiddebugkey', tv_password, TV_CERT, 'app.nook.tv.debug', '-tv')]
    key_hashes = {}
    for _, key, alias, secret, cert, _, _ in variants:
        key_hashes[key] = hashlib.sha256((signing / key).read_bytes()).hexdigest()
        key_env = dict(env, NOOK_KEY_PASSWORD=secret)
        result = subprocess.run(['keytool', '-exportcert', '-keystore', str(signing / key),
                                 '-alias', alias, '-storepass:env', 'NOOK_KEY_PASSWORD'],
                                env=key_env, capture_output=True)
        require(result.returncode == 0 and hashlib.sha256(result.stdout).hexdigest() == cert,
                'Original signing certificate verification failed: ' + key)
    subprocess.run(['python3', '-m', 'unittest', '-v', 'test_server.py', 'scripts.test_build_signed_release'], cwd=ROOT, env=env, check=True)
    subprocess.run(['node', 'test_preview.cjs'], cwd=ROOT, env=env, check=True)
    subprocess.run([str(ROOT / 'android/gradlew'), '--no-daemon', '-p', str(ROOT / 'android'),
                    'clean', ':app:testDebugUnitTest',
                    ':app:lintRelease', ':app:lintTv', ':app:assembleRelease', ':app:assembleTv'],
                   cwd=ROOT, env=env, check=True)
    require(run(['git', 'rev-parse', 'HEAD']) == revision and
            not run(['git', 'status', '--porcelain']).strip(),
            'Checkout changed during build; refusing to package')
    config = (ROOT / 'android/app/build.gradle').read_text()
    version = re.search(r"versionName\s+'([^']+)'", config).group(1)
    code = re.search(r'versionCode\s+(\d+)', config).group(1)
    require(version == '0.3.0' and code == '6', 'Unexpected release version; review before updating automation')
    secrets = [password.encode(), *( (signing / key).read_bytes() for key in key_hashes)]
    out.mkdir(parents=True)
    private = out / 'private-maintenance'
    private.mkdir()
    with tempfile.TemporaryDirectory(prefix='nook-sign-') as temp:
        temp = Path(temp)
        for variant, key, alias, secret, cert, package, suffix in variants:
            unsigned = ROOT / f'android/app/build/outputs/apk/{variant}/app-{variant}-unsigned.apk'
            aligned = temp / f'{variant}.apk'
            run([tools / 'zipalign', '-P', '16', '4', unsigned, aligned], env=env)
            signed = temp / f'{variant}-signed.apk'
            result = subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(signing / key),
                                     '--ks-key-alias', alias, '--ks-pass', 'stdin', '--key-pass', 'stdin',
                                     '--out', str(signed), str(aligned)],
                                    input=(secret + '\n' + secret + '\n').encode(), env=env, capture_output=True)
            require(result.returncode == 0, 'APK signing failed: ' + variant)
            verify_apk(signed, tools, cert, package, version + suffix, code, secrets)
            if variant == 'release':
                subprocess.run(['python3', 'scripts/package_release.py', '--out', str(out / 'public'),
                                '--apk', str(signed), '--cert-sha256', cert, '--build-tools', str(tools)],
                               cwd=ROOT, env=env, check=True)
            else:
                dest = private / f'BAY65-{version}-tv-update.apk'
                shutil.copyfile(signed, dest)
                (private / 'SHA256SUMS').write_text(f'{hashlib.sha256(dest.read_bytes()).hexdigest()}  {dest.name}\n')
    check_zip(out / f'public/BAY65-{version}-source.zip', secrets)
    for key, digest in key_hashes.items():
        require(hashlib.sha256((signing / key).read_bytes()).hexdigest() == digest, 'Signing key changed')
    for folder in (out / 'public', private):
        for line in (folder / 'SHA256SUMS').read_text().splitlines():
            digest, name = line.split('  ')
            file = folder / name
            require(hashlib.sha256(file.read_bytes()).hexdigest() == digest, 'Checksum mismatch')
            print(f'Validated {file} ({file.stat().st_size} bytes)')
    print('All release checks passed. No device installation or publication performed.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from None
