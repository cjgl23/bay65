"""Validate built variant identities without installing or signing an APK."""
import argparse
from pathlib import Path
import subprocess
from scripts.build_signed_release import ROOT, check_badging, require

EXPECTED = {
    'debug': ('app.bay65.tv.debug', '0.2.2-dev', 'app-debug.apk'),
    'release': ('app.bay65.tv', '0.2.2', 'app-release-unsigned.apk'),
    'tv': ('app.nook.tv.debug', '0.2.2-tv', 'app-tv-unsigned.apk'),
}


def check_metadata(text, variant):
    package, version, _ = EXPECTED[variant]
    if variant == 'debug':
        # check_badging deliberately accepts only non-debuggable distributions.
        text = text.replace('application-debuggable', '')
    check_badging(text, package, version, '5')
    require("application-label:'BAY65'" in text, 'Unexpected application label')
    require("leanback-launchable-activity: name='app.nook.tv.MainActivity'" in text,
            'Internal activity namespace must remain compatible')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-tools', type=Path, required=True)
    args = parser.parse_args()
    for variant, (_, _, name) in EXPECTED.items():
        apk = ROOT / 'android/app/build/outputs/apk' / variant / name
        text = subprocess.check_output([str(args.build_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
        check_metadata(text, variant)
        print('Validated BAY65 metadata:', variant)


if __name__ == '__main__':
    main()
