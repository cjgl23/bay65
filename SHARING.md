# Sharing BAY65

Current distribution is source-only (0.3.0, version code 6). From a committed checkout, package the source and checksums into a new or empty directory:

```sh
python3 scripts/package_release.py --out dist
```

The archive is built from committed HEAD using an explicit allowlist. Uncommitted shortcut, documentation or other changes are not included; commit and review the intended source before creating the archive. It excludes Git history, local configuration, personal files, signing material and the portfolio-only redacted screenshot. Review the archive before sharing it.

For an independently signed standard APK (not distributed by this project), the package script can verify the signed APK against the expected certificate fingerprint, package ID, version and alignment:

```sh
python3 scripts/package_release.py --out dist-signed \
  --apk /absolute/path/to/signed-release.apk \
  --cert-sha256 YOUR_RELEASE_CERT_SHA256 \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0"
```

This does not prove reproducibility or publish anything. A fresh public binary release requires separate physical acceptance and maintainer approval. CI signs only temporary validation APKs. Never distribute the private maintenance APK or any signing keys.

See [distribution policy](PUBLIC-RELEASE.md) and [release guidance](scripts/RELEASE.md).
