# BAY65 for Android TV

A native, remote-first home screen for Android TV 8.0 or newer. BAY65 runs independently of the browser preview and its local server.

## First use

- Open BAY65 from your current launcher's app list.
- Use the network icon beside the gear to open the TV's native network settings.
- Open Settings for All apps, Manage favourites, BAY65 preferences, BAY65 Assistant, Android settings, Bluetooth/remotes, accessibility, launcher selection, and About BAY65.
- In BAY65 preferences, connect your own DataMall API Access Key and add your own bus stops and services. You can search LTA's public stop directory or enter details manually.
- Left and Right controls reorder bus stops. Native bus cards share a row and grow into unused space above favourites without moving the app shelf. Service lists scroll when their content exceeds available height; narrow layouts may require horizontal scrolling.
- Manage favourites changes app placement and order. Home tiles show artwork from the installed apps without visible app-name labels. Artwork is clipped to rounded corners; the focus ring switches between dark green on light artwork and pale green on dark artwork. App names remain available to accessibility services and in Settings.

BAY65 suggests the supported streaming/music favourites and the recognised games when they are installed. Other launchable apps remain available in All apps. No streaming apps, games, or their artwork are bundled in the APK.

## Bus data and privacy

There are **no preconfigured stops, services, account keys, imported photos, or personal settings** in the APK. Settings from a developer's browser or TV are never copied into a build.

- Keys are encrypted with an AES-GCM key held by Android Keystore and stored in app-private preferences. Key entry is masked, cleared on submission, excluded from autofill, and protected against screenshots while the connection page is open.
- HTTPS requests go directly to LTA's fixed endpoints with certificate verification and redirects disabled. Credentials are never put in URLs or logged. The optional assistant uses the same rules for the AI provider you choose.
- Stops, preferences, and imported photo copies remain in private app storage. Cloud backup and device-transfer extraction are disabled. There are no analytics, advertising, account sign-in, or embedded HTTP services.
- Arrivals refresh every 20 seconds while the app is visible. Polling pauses when it leaves the foreground; the TV's normal screensaver remains available.
- `—` means no estimate. `~` indicates a scheduled estimate. Missing/stale data never becomes a sample arrival time, and missing estimates are not labelled as “not in operation.”
- Uninstalling BAY65 or clearing its data removes the saved stops, encrypted key, and imported photo copies. The original source images are unchanged.

Operating-hour validation is not included in this version. Service suggestions show services currently reporting arrivals; services can also be entered manually.

## BAY65 Assistant (optional)

A floating voice orb in the bottom-right corner of the home screen, ported from the Rupert AI presence orb. It is off until you set it up in Settings → BAY65 Assistant.

- **Talk:** select the orb and press OK, or press the remote's search button. **Type:** hold OK on the orb. Press OK again or Back to stop.
- **Colour shows state:** calm blue when idle, teal while listening, amber while thinking or speaking. The orb pulses with your voice and with each spoken word, and stays still when the TV's animations are turned off.
- **Voice is free and on the TV:** listening uses Android's speech recogniser and replies use the TV's built-in text-to-speech. No voice API key is needed. If in-app listening is unavailable, BAY65 falls back to the system voice screen, then to typing. Microphone permission is requested only the first time you talk.
- **Answers come from a free provider you choose**, all through the same OpenAI-compatible request:

| Provider | Default model (editable) | Key |
|---|---|---|
| Google Gemini free tier | `gemini-flash-latest` | Free from aistudio.google.com/apikey |
| Groq free tier | `llama-3.3-70b-versatile` | Free from console.groq.com/keys |
| OpenRouter free models | `meta-llama/llama-3.3-70b-instruct:free` | Free from openrouter.ai/keys |
| Ollama or another OpenAI-compatible server | `llama3.2` | Optional; the server must use `https://` |

- Keys are tested before saving, encrypted with Android Keystore, masked on entry and protected against screenshots, like the LTA key. Free tiers have rate limits and some providers may use free-tier prompts to improve their models.
- With **Include my bus stops, live arrivals and favourite app names** on (the default), each question includes your stop names and codes, the current arrival estimates and your favourite app names, so you can ask "When is my next 10?". Turn it off to send only your question and the time. The assistant is told never to invent arrival times.
- BAY65 does not send unencrypted traffic, so Ollama on your home network needs an HTTPS front end such as Tailscale Serve or a reverse proxy.
- Conversation history (last six exchanges) is kept in memory only. **Turn off and remove saved key** clears the provider settings and key.

## Photos

Import photos through an installed Android image/file picker, including files on a USB drive when supported by that picker. Some TVs only expose a placeholder picker. BAY65 detects that and offers a file-manager setup route rather than launching the placeholder. A compatible file manager is a separate installation chosen by the user.

BAY65 copies selected images into private storage so they survive restarting the TV. The import limits are 50 images, 20 MB per image, and 200 MB total. The slideshow supports 30 seconds, 1 minute, 5 minutes, or 10 minutes per image. “Show the whole photo” preserves portrait images; the default mode fills the screen. Google Photos integration is not included.

## Launcher behaviour

BAY65 declares Android's HOME and LEANBACK_LAUNCHER activities. Opening it does not disable or remove other launchers. Settings → Choose launcher explains the current Home-role status and offers the system chooser or confirmation where supported. A launcher that intercepts the Home button through an accessibility service may require changing its own configuration before Home opens BAY65.

BAY65 does not install its own accessibility service or silently change the default home app. Native settings shortcuts skip known placeholder activities. If Bluetooth or accessibility routes are unsupported, BAY65 offers pairing or settings guidance as appropriate; device firmware determines available actions. Launcher changes require explicit system confirmation.

### Advanced, device-specific troubleshooting (optional)

Do not use the following commands for routine installation or upgrades. They change system launcher settings and should only be used by the device owner after verifying that the named packages and commands apply to their TV and that recovery via ADB is available.

Some Android TVs give the built-in Google launcher a higher priority even after the Home-role setting is changed. A manually configured device can use the reversible per-user launcher disable below, then select BAY65 as Home. Substitute `app.nook.tv.debug` for the configured-TV build's package name.

```sh
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity --user 0 app.bay65.tv/app.nook.tv.MainActivity
```

To restore the built-in Google launcher, run `adb shell pm enable --user 0 com.google.android.tvlauncher`, then choose the desired home app. Other launchers remain installed. Turn off any previous launcher's Home-interception option/accessibility service when switching, while preserving unrelated accessibility services. These are device settings; they are not bundled into or automatically changed by the APK.

## Build

Current source: 0.2.2, version code 5. Use JDK 17 or newer, Android SDK platform 36 and build-tools 36.0.0. The pinned Gradle wrapper is 9.9.1. Configure `sdk.dir` in an untracked `local.properties` file, or use your normal Android SDK environment configuration.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The Gradle wrapper pins its distribution checksum. Runtime UI code uses Android's native views without a WebView or third-party runtime libraries.

- Development package: `app.bay65.tv.debug`, version suffix `-dev`.
- Configured-TV build: `app.nook.tv.debug`, version suffix `-tv`, non-debuggable. It must be signed with the original TV signer using the local release script so an in-place update preserves existing device settings. It excludes the development-only layout activity.
- Release package: `app.bay65.tv`.

The development package is separate so it can coexist with the signed release. Each has its own private settings and key storage. Switching packages requires setting up the new package; updating the same package with the same signer preserves its settings.

`./gradlew :app:assembleTv` builds an unsigned, non-debuggable TV APK. Use [local release automation](../scripts/RELEASE.md) to sign it with the original TV key; the default debug keystore is never used for this variant. Preserve that signer too if maintaining a configured TV installation.

For a signed release, provide `NOOK_STORE_FILE` and `NOOK_STORE_PASSWORD` securely to Gradle, with signing alias `nook`, then run `./gradlew :app:assembleRelease :app:lintRelease`. Without signing configuration, the release APK is unsigned. Do not put passwords in source files or shell history, and never distribute the keystore. Keep the signing key for future updates.

## Verification

The JVM tests cover service validation, arrival formatting, occupancy mapping, adaptive bus card sizing and settings placeholder detection. The native instrumentation runner also covers navigation, native network access, key encryption, backup exclusion, private image import/decoding, multi-stop layout, spacing, settings alignment and horizontal/vertical focus audio. Its layout activity exists only in development builds and uses isolated preferences instead of the user's configuration.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r app.bay65.tv.debug.test/app.nook.tv.NookChecks
```

Run instrumentation only on an isolated development emulator or test device, not the configured maintenance installation. Compilation is not proof of execution. Development tests use generated images and generic fixture data. No real credentials or personal bus-stop examples are included in the tests.

The maintainer's Android 10 TV (1920×1080) has been used for historical physical testing; the owner reports the 0.2.2 maintenance update is installed with no further issues. See [validation evidence](VALIDATION.md) for the scope and limitations. Other manufacturers' settings screens and file pickers may behave differently. The UI is currently in English.

## Public distribution

See [privacy notice](../PRIVACY.md), [LTA data attribution and terms](../DATA-ATTRIBUTION.md), [third-party notices](../THIRD-PARTY-NOTICES.md), and [release packaging](../SHARING.md). The old maintenance APK is not a public release. Validate the new release on physical TV before distributing it.

## Current release and compatibility

Version 0.2.2 uses version code 5. Public release `app.bay65.tv` is a separate installation from the previous Nook public package. The maintenance build `app.nook.tv.debug` / `0.2.2-tv` retains its original signer, internal Java namespace, preference identifiers, photo paths and Keystore aliases. Development uses `app.bay65.tv.debug` / `0.2.2-dev`. Do not replace the configured-TV package with the public or development APK.

BAY65 Transit shows Next, Then and Later using each bus's LTA Load: green for SEA (seats), orange for SDA (standing), red for LSD (limited standing). Unknown/missing Load uses slate. Missing/stale estimates have no occupancy indication. Arrival times are colour-only; accessible descriptions and the About legend explain capacity. Native cards grow into available space above favourites while preserving app shelf position. BAY65 Memories retains private local photo storage.

Horizontal app shelves and vertical Settings scrolling use standard system navigation audio for handled directional operations, respecting disabled sound effects. Settings scrollbars are hidden without disabling scrolling. Back/Close labels are centred.

The owner reports that the 0.2.2 maintenance update is installed with no further issues. Do not interpret this as separate public-APK physical acceptance. See [VALIDATION.md](VALIDATION.md) and [release gates](../PUBLIC-RELEASE.md).
