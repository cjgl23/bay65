# BAY65 validation evidence

## v0.2.2

The original 0.2.2 source baseline passed Android compilation, lint, unit tests, browser DOM/Python tests, temporary test-signed release packaging, and repository/dependency and reachable-history secret scans. These checks are repeated in this repository's GitHub Actions workflows.

The configured maintenance build was installed on an Android 10 TV, and the owner reported no further issues. This is owner-confirmed acceptance, not a complete per-test device log. Original-key signing and packaging were reported successful. Native instrumentation was compiled; execution for this version has not been independently verified. Browser rendered visual acceptance and a separate fresh installation of the standard public APK remain unverified.

## Earlier development milestones

- 0.1.0: native launcher, Home navigation, isolated multi-stop layout, key encryption and private photo-import pipeline tested on Android 10.
- 0.1.1: in-place maintenance upgrade and configuration preservation accepted by the owner.
- 0.2.0: BAY65 rebrand; physical testing identified UI issues while preserving configuration.
- 0.2.1: visual and configuration checks passed; vertical navigation audio issue identified.
- 0.2.2: audio fix installed; no further issues reported by the owner.

## Future validation

Run [development checks](../CONTRIBUTING.md) and native instrumentation on a separate test device. Record automated results and physical observations separately. Before distributing any signed APK, verify identity, version, signer, checksum, source provenance, and fresh-install functionality. Never collect real credentials, private settings or identifiable screenshots as test evidence.
