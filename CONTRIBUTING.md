# Contributing to BAY65

BAY65 is an independent personal open-source portfolio project. Contributions should be focused, reviewed and validated before merge.

## Prerequisites

- Git, Python 3.10+, Node.js/npm.
- JDK 17, Android SDK platform 36, build-tools 36.0.0 and platform-tools.
- A compatible Android TV emulator or separate test device for native instrumentation.
- Use the repository Gradle wrapper (9.9.1); do not require a separately installed Gradle.

Configure Android SDK via `ANDROID_HOME` or untracked `android/local.properties`. Never commit local machine paths, credentials, imported photos or personal bus stops.

## Local development

From the repository root:

```sh
python3 -m unittest -v test_server.py scripts.test_build_signed_release
npm install --ignore-scripts --no-save --no-package-lock linkedom@0.18.12
node test_preview.cjs
```

Open `index.html` for synthetic offline arrivals, or run `python3 server.py` and use the loopback address http://127.0.0.1:8765 for live preview with your own DataMall key. Do not use real credentials in fixtures or logs.

```sh
cd android
./gradlew --no-daemon :app:lintRelease :app:lintTv :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleTv
cd ..
python3 -m scripts.check_apk_metadata --build-tools "$ANDROID_HOME/build-tools/36.0.0"
```

The above assumes no `NOOK_STORE_FILE` / `NOOK_STORE_PASSWORD` environment signing configuration. Development builds use `app.bay65.tv.debug`, separate from both the release and private maintenance packages. No owner's signing key is needed for contributor builds. Use an isolated test device for [instrumentation](android/README.md#verification); never install the instrumentation or unsigned maintenance APK on the configured personal TV.

## Remote shortcut and overlay validation

For shortcut changes, run TV assembly, unit tests and TV lint, then use the [Everywhere Mode acceptance checklist](android/EVERYWHERE.md#physical-tv-acceptance-checklist) on an authorized test TV. Enable **BAY65 remote shortcut** through Android Accessibility after choosing a button; do not silently enable it or replace other enabled services. Verify the physical remote button opens the panel over another app and Back returns to it. Synthetic ADB Menu input did not trigger the shortcut on the owner’s TV and is not a substitute for a physical button test.

Record shortcut-Off and Everywhere Mode-Off behavior, unrelated button navigation, competing key filters, appearance choices and reboot behavior separately. Do not claim those cases passed based on compilation or a single activation check. Keep read-only device diagnostics and private signing material out of the PR; summarize results in [validation evidence](android/VALIDATION.md).

## Change workflow

1. Update from `main`; create a short-lived feature, fix or docs branch.
2. Keep changes focused and add synthetic regression tests where applicable.
3. Preserve package and storage compatibility; do not rename internal Nook identifiers casually.
4. Update the related documentation and changelog when behaviour or release versions change.
5. Open a PR; require all applicable CI and Security checks and maintainer review before merge.
6. Remove merged branches when no longer needed.

Do not weaken security scans or bypass failed checks. Do not upload owner-signed APKs, private screenshots or signing materials. For vulnerability reports follow [SECURITY.md](SECURITY.md). For maintainer-only distribution see [PUBLIC-RELEASE.md](PUBLIC-RELEASE.md).

Contributions must be original or appropriately licensed and comply with [LICENSE](LICENSE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
