#!/usr/bin/env python3
"""Export a clean source snapshot and, optionally, a locally signed sharing APK."""
import argparse
import hashlib
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ".github", "assets", ".gitignore", "LICENSE", "README.md", "SECURITY.md",
    "CHANGELOG.md", "CONTRIBUTING.md",
    "PRIVACY.md", "DATA-ATTRIBUTION.md", "THIRD-PARTY-NOTICES.md",
    "PUBLIC-RELEASE.md", "SHARING.md", "licenses", "android",
    "index.html", "server.py", "test_server.py", "test_preview.cjs",
    "scripts", "releases/SHARING.md",
]
REQUIRED = {"LICENSE", "CHANGELOG.md", "CONTRIBUTING.md", "PRIVACY.md", "DATA-ATTRIBUTION.md",
            "THIRD-PARTY-NOTICES.md", "licenses/Apache-2.0.txt",
            "android/gradle/wrapper/gradle-wrapper.jar"}
PREFIX = "BAY65-source/"

def run(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True)

def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out", type=Path, default=ROOT / "dist")
    p.add_argument("--apk", type=Path, help="Signed standard release APK (app.bay65.tv)")
    p.add_argument("--cert-sha256", help="Expected release signer SHA-256 fingerprint")
    p.add_argument("--build-tools", type=Path, help="Android SDK build-tools directory")
    args = p.parse_args()
    if bool(args.apk) != bool(args.cert_sha256) or (args.apk and not args.build_tools):
        p.error("--apk requires --cert-sha256 and --build-tools")
    out = args.out.resolve()
    if out.exists() and any(out.iterdir()):
        p.error("output directory must be empty; avoid mixing old and new artifacts")
    out.mkdir(parents=True, exist_ok=True)
    config = run("git", "show", "HEAD:android/app/build.gradle")
    version = re.search(r"versionName\s+'([^']+)'", config).group(1)
    code = re.search(r"versionCode\s+(\d+)", config).group(1)
    source = out / f"BAY65-{version}-source.zip"
    subprocess.run(["git", "archive", "--format=zip", "--prefix=" + PREFIX,
                    "-o", str(source), "HEAD", "--", *SOURCES], cwd=ROOT, check=True)
    with zipfile.ZipFile(source) as z:
        names = {n.removeprefix(PREFIX) for n in z.namelist()}
        if not REQUIRED <= names or z.testzip():
            raise SystemExit("Source package failed completeness or integrity validation")
        for n in names:
            if (n.endswith((".apk", ".jks", ".keystore", ".pem", ".p12", ".env"))
                or Path(n).name in {"local.properties", ".env"}
                or any(part in {".git", ".gradle", "build", "__pycache__"} for part in Path(n).parts)):
                raise SystemExit("Unexpected private/build file in source package: " + n)
    files = [source]
    if args.apk:
        apk = args.apk.resolve()
        tools = args.build_tools.resolve()
        info = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
        found = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", info)
        expected = args.cert_sha256.replace(":", "").lower()
        if not re.fullmatch(r"[a-f0-9]{64}", expected) or len(found) != 1 or found[0].replace(":", "").lower() != expected:
            raise SystemExit("APK signer does not match the expected release certificate")
        badging = run(str(tools / "aapt"), "dump", "badging", str(apk))
        if ("package: name='app.bay65.tv'" not in badging or
            f"versionName='{version}'" not in badging or
            f"versionCode='{code}'" not in badging or
            "application-debuggable" in badging):
            raise SystemExit("Not the expected non-debuggable standard BAY65 release")
        run(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
        dest = out / f"BAY65-{version}.apk"
        shutil.copyfile(apk, dest)
        files.append(dest)
    (out / "SHA256SUMS").write_text("".join(f"{sha256(f)}  {f.name}\n" for f in files))
    print("Verified package(s):", ", ".join(f.name for f in files))
    print("Source revision:", run("git", "rev-parse", "HEAD").strip())
    if not args.apk:
        print("Source only; no distributable APK was produced.")

if __name__ == "__main__":
    main()
