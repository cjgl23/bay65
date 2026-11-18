# BAY65 validation evidence

## Everywhere Mode (unreleased)

Existing Everywhere Mode coverage includes JVM tests for overlay visibility, dimming, drift and frame rate, plus the shared context builder. The development instrumentation runner contains assertions for the non-exported panel, overlay service and restart receiver, and for the Ask BAY65 alias being disabled by default; this is distinct from the new accessibility service, which is exported under Android’s `BIND_ACCESSIBILITY_SERVICE` permission.

For the pending shortcut/visibility changes, **local TV assembly, JVM unit tests and TV lint passed**. The signed maintenance APK passed package identity, original-signer, alignment and key-material checks before an in-place installation. On 18 November 2026, the owner confirmed physical Menu-button activation over Prime Video on the PrismPlus Android 10 TV. ADB inspection verified AssistantActivity foreground, then Prime Video foreground after Back. The service was enabled through the TV’s accessibility UI, without an ADB grant or secure-settings edit. Opacity was 100%, auto-dimming was unchecked, and Bottom right / Small were retained.

Google Assistant launching “Ask BAY65” by name and synthetic ADB Menu input did not activate the panel on this TV. Neither is required by the physical Menu shortcut. Voice answering was previously owner-confirmed, but that does not establish all panel voice, playback, reboot, shortcut-Off or competing-service scenarios for this build.

For the duplicate-orb fix, regression tests cover overlapping launcher/panel instances, repeated starts and a late stop from an older task. The fix passed local TV assembly, JVM unit tests and TV lint and was installed in place. Three TV round trips between the app-task launcher, Prime Video and the Home-task launcher showed exactly one overlay over Prime Video and none over either BAY65 launcher. Opening the panel over BAY65 and returning with Back also left no overlay window. These results were checked using Android window and activity dumps, not a playback or screenshot comparison.

The full acceptance checklist remains incomplete. Before PR submission, local release/TV lint, JVM tests, all three variant builds, variant metadata checks, 14 Python server/release-safety tests and browser DOM checks passed. No new CI or native-instrumentation execution result is claimed for the pending shortcut changes. See the [exact setup and scoped results](EVERYWHERE.md#exact-setup-used-on-the-owners-tv) and [acceptance checklist](EVERYWHERE.md#physical-tv-acceptance-checklist).

## v0.3.0

For the earlier Assistant PRs, GitHub Actions passed on each assistant PR (#1–#4): Android lint (release and tv), unit tests, assembly of all variants, release-artifact verification, and Trivy/dependency scans. JVM unit tests cover provider requests, error mapping, grounding parsing and voice-fallback rules. The owner confirmed the assistant answering spoken questions on the Android 10 TV during development (Gemini, after correcting the model name). A full physical acceptance pass of 0.3.0, including web search, search-key behaviour, voice fallback and upgrade from 0.2.2, has not yet been recorded.

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
