# BAY65 — Home is your next stop.

![BAY65 logo](assets/bay65-logo.svg)

**Your home. Your city. Your screen.**

A remote-first Android TV launcher that brings live Singapore bus arrivals, favourite apps and personal photo backgrounds together. Built as an independent personal engineering project, inspired by *bahay* (home), Singapore's Marina Bay, bus bays and +65.

**Version:** 0.2.2 · Android TV 8.0+ · MIT-licensed source · Source-only distribution

![BAY65 home screen — privacy-redacted TV view](https://raw.githubusercontent.com/cjgl23/bay65/main/portfolio/bay65-redacted.png)

*Redacted view of the native TV home screen, edited to conceal bus stop names, stop codes and service numbers. Arrival estimates and capture time remain visible. Installed app artwork identifies the respective apps; those assets are not bundled with BAY65 or licensed under its MIT licence. The portfolio image is deliberately excluded from code-only source archives.*

## Highlights

- **BAY65 Transit:** up to four configurable stops and eight services per stop, three independently colour-coded arrival estimates, 20-second foreground refresh, stale-data handling, adaptive card sizing and LTA stop-directory lookup.
- **BAY65 Memories:** private photo import, configurable display fit and slideshow intervals.
- **Remote-first home:** installed-app discovery, favourites and Playtime organisation, D-pad navigation, system navigation sounds and device-dependent settings shortcuts.
- **BAY65 Assistant (optional):** a floating voice orb that answers questions about your buses and more, using free AI providers (Google Gemini, Groq, OpenRouter or your own Ollama server) and the TV's built-in speech. See [Android setup](android/README.md#bay65-assistant-optional).
- **Local-first privacy:** encrypted LTA key storage using Android Keystore, app-private settings and photos, no analytics or cloud accounts.

## Engineering approach

| Area | Design |
|---|---|
| Native Android | Java, Android views, Android TV Home/Leanback integration |
| API integration | LTA DataMall Bus Arrival v3 and Bus Stops, fixed HTTPS endpoints |
| Resilience | Stale-data expiry, missing-estimate handling, foreground polling |
| Security and privacy | Keystore-backed credential encryption, backup exclusion, no bundled keys or personal stops |
| Browser preview | HTML/CSS/JavaScript, optional loopback Python server |
| Delivery | Gradle, GitHub Actions, pinned reusable workflows, dependency/secret scanning and verified release packaging |

The project evolved through iterative physical-device testing. The maintenance build was installed on the owner's Android 10 TV, with no further issues reported for v0.2.2. A separate public APK has not been physically accepted and is not distributed here. See [validation](android/VALIDATION.md).

## Try the browser preview

Open `index.html` for labelled synthetic arrivals, or run:

```sh
python3 server.py
```

Open http://127.0.0.1:8765. In Settings → BAY65 preferences, add your own stops and services, then connect your own LTA DataMall API Access Key. The Python server requires no external runtime dependencies and binds to loopback only. The browser preview cannot control TV settings or discover installed TV apps.

## Build the Android app

Requires JDK 17+, Android SDK platform 36 and build-tools 36.0.0. From the repository:

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

See [Android setup](android/README.md) and [contributor instructions](CONTRIBUTING.md). The debug APK is a separate development package; do not install it over a configured maintenance installation.

## Transit data and privacy

Green means seats available (SEA), orange standing available (SDA), red limited standing (LSD), and slate unknown. Every arrival uses its own LTA Load code; vehicle Type is not used as a capacity indicator. Scheduled estimates use `~`; missing or stale estimates show `—`. Red does not necessarily mean full. The About legend explains the colours, and accessibility descriptions retain capacity information.

BAY65 uses Singapore LTA DataMall data. Obtain and protect your own API key. See [data attribution and terms](DATA-ATTRIBUTION.md), [privacy](PRIVACY.md), [third-party notices](THIRD-PARTY-NOTICES.md), and [security policy](SECURITY.md).

## Project documentation

- [Changelog](CHANGELOG.md)
- [Contributing and development checks](CONTRIBUTING.md)
- [Android build, testing and setup](android/README.md)
- [Validation evidence](android/VALIDATION.md)
- [Source packaging](SHARING.md)
- [Release and signing guidance](scripts/RELEASE.md)
- [Distribution policy](PUBLIC-RELEASE.md)

## Licence

Original BAY65 source is provided under the [MIT License](LICENSE). Third-party data, marks and software remain subject to their respective terms. An independent project; not affiliated with or endorsed by LTA, Google or any streaming/game provider.
