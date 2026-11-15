# Release guidance

BAY65 is currently distributed as source only. Contributors do not need the maintainer's signing keys. Use [CONTRIBUTING.md](../CONTRIBUTING.md) and [SHARING.md](../SHARING.md) for builds, tests and source archives.

The repository includes a maintainer-specific signing and verification script retained for compatibility with the original installation. The script requires the original certificates and a clean committed checkout; it is not intended for ordinary contributor builds. It verifies the pinned signer, package ID, version, non-debuggability, alignment and checksums, keeps signing credentials out of command arguments, and writes separate public and private-maintenance output directories. No keys or private APKs are included here. Never regenerate the maintenance signer or uninstall a configured TV package to perform an update.

When sharing a future public APK, use your own securely protected signing material, verify the intended source and artifact provenance, run CI and security validation, and test the standard public package on an isolated TV. Source-only archives can be created with `python3 scripts/package_release.py --out dist`.

## Package identities

- Public release: `app.bay65.tv`, 0.2.2, version code 5.
- Development: `app.bay65.tv.debug`, 0.2.2-dev.
- Private legacy maintenance: `app.nook.tv.debug`, 0.2.2-tv; original signer and storage identity retained.

Do not substitute one package for another. No APK release is currently published. See [distribution policy](../PUBLIC-RELEASE.md).
