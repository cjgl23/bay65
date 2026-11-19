# BAY65 — Home is your next stop.

![BAY65 logo](assets/bay65-logo.svg)

**Your home. Your city. Your screen.**

A remote-first Android TV launcher that brings live Singapore bus arrivals, favourite apps, personal photo backgrounds and an optional voice assistant together. Built as an independent personal engineering project, inspired by *bahay* (home), Singapore's Marina Bay, bus bays and +65.

**Version:** 0.3.0 · Android TV 8.0+ · MIT-licensed source · Source-only distribution

![BAY65 home screen — privacy-redacted TV view](https://raw.githubusercontent.com/cjgl23/bay65/main/portfolio/bay65-redacted.png)

*Redacted view of the native TV home screen, edited to conceal bus stop names, stop codes and service numbers. Arrival estimates and capture time remain visible. Installed app artwork identifies the respective apps; those assets are not bundled with BAY65 or licensed under its MIT licence. The portfolio image is deliberately excluded from code-only source archives.*

## Highlights

- **BAY65 Transit:** up to four configurable stops and eight services per stop, three independently colour-coded arrival estimates, 20-second foreground refresh, stale-data handling, adaptive card sizing and LTA stop-directory lookup.
- **BAY65 Memories:** private photo import, configurable display fit and slideshow intervals.
- **Remote-first home:** installed-app discovery, favourites and Playtime organisation, D-pad navigation, system navigation sounds and device-dependent settings shortcuts.
- **BAY65 Assistant (optional):** a floating voice orb that answers questions about your buses, your favourite apps (with Gemini web search for current releases and charts) and more, using free AI providers (Google Gemini, Groq, OpenRouter or your own Ollama server) and the TV's built-in speech. See [Android setup](android/README.md#bay65-assistant-optional).
- **Everywhere Mode (optional):** keep the orb visible over other apps and press a configurable remote button to open the Assistant panel. The Menu shortcut requires enabling **BAY65 remote shortcut** in Android Accessibility; press Back to return to the previous app. Opacity defaults to 100%, with auto-dimming off. See [setup and device-test results](android/EVERYWHERE.md#exact-setup-used-on-the-owners-tv).
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

The 0.3.0 maintenance build with the pending remote-shortcut changes is installed on the owner’s PrismPlus Android 10 TV. The owner confirmed physical Menu-button activation over Prime Video; Back returning to Prime Video was also verified. Other-app playback, reboot persistence and a full Everywhere Mode acceptance pass remain unverified. A separate public APK is not distributed here. See [validation](android/VALIDATION.md).

## Use the Assistant over other apps

The remote shortcut is included in the current unreleased source and is pending PR review. Assistant and Everywhere Mode are optional and off by default.

1. Open **BAY65 Settings → BAY65 Assistant**, select your provider, enter your own key, and choose **Save and test**. The key is saved only after a successful test; provider quotas apply.
2. Open **Everywhere Mode**, turn it on and allow **Display over other apps**.
3. Choose **Shortcut button → Menu**, then select **Remote shortcut service → Enable in Accessibility**. On the tested TV, the path is **Open Android settings → Device Preferences → Accessibility → BAY65 remote shortcut → Enable → OK**.
4. Open another app and press **Menu** to ask BAY65. Press **Back** to return. The floating orb is display-only; it cannot receive D-pad focus over another app.

The selected button replaces its normal action while Everywhere Mode is on. Set the shortcut to Off to restore it. The optional accessibility service receives button events without reading screen content or storing key history. Google Assistant launching “Ask BAY65” by name is device-dependent and did not work on the tested TV. See the [complete setup, visibility controls and verification notes](android/EVERYWHERE.md).

## Try the browser preview

Open `index.html` for labelled synthetic arrivals, or run:

```sh
python3 server.py
```

Open http://127.0.0.1:8765. In Settings → BAY65 preferences, add your own stops and services, then connect your own LTA DataMall API Access Key. The Python server requires no external runtime dependencies and binds to loopback only. The browser preview cannot control TV settings or discover installed TV apps.

## Website

The landing page lives in [`website/`](website/) as a static site with no build step. The root [`vercel.json`](vercel.json) serves it on Vercel: import this repository as a new Vercel project and deploy with the default settings. To preview locally:

```sh
python3 -m http.server 8080 -d website
```

See [website/README.md](website/README.md) for structure, design notes and deployment details.

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
- [Website and Vercel deployment](website/README.md)
- [Contributing and development checks](CONTRIBUTING.md)
- [Android build, testing and setup](android/README.md)
- [Validation evidence](android/VALIDATION.md)
- [Source packaging](SHARING.md)
- [Release and signing guidance](scripts/RELEASE.md)
- [Distribution policy](PUBLIC-RELEASE.md)

## Licence

Original BAY65 source is provided under the [MIT License](LICENSE). Third-party data, marks and software remain subject to their respective terms. An independent project; not affiliated with or endorsed by LTA, Google or any streaming/game provider.
