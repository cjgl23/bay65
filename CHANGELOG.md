# Changelog

This records released source versions and significant changes. It does not imply public APK availability.

## Unreleased

- Publish the landing page with GitHub Pages instead of Vercel: add the Pages workflow, make all site paths relative for the `/bay65/` subpath, move security policies into `<meta>` tags and remove `vercel.json`.
- Add optional now-playing awareness for the assistant (off by default): with **Share what I'm watching with the assistant** on and BAY65 Now Playing allowed in Android Notification access, questions such as "What is this movie about?" include the playing app, title, episode or artist and play/pause state from Android's media controls. Uses MediaSession metadata only; no screen content, notification content or accessibility window content is read. New component: a notification listener that holds the grant and unbinds immediately. Without the grant the assistant works as before. See [Everywhere Mode](android/EVERYWHERE.md#now-playing).
- Add the BAY65 landing page in `website/` (static, no build step) with a root `vercel.json` for Vercel deployment, security headers and asset caching. See [website/README.md](website/README.md).
- Fix: pressing the orb could keep opening the TV's system voice screen ("Ask BAY65" bar at the top) instead of listening in BAY65. The voice fallback no longer remembers anything between presses, silence shows "I didn't hear anything" instead of opening the system screen, and the stuck setting from 0.3.0 is cleared on start.
- Fix duplicate orbs when returning from another app: track visible launcher and panel instances separately, so a stopped app-task instance cannot clear the Home task’s visibility.

- Make the overlay orb fully opaque by default with automatic dimming off; opacity and dimming remain adjustable.

- Add an optional configurable remote-button shortcut (Menu, Red, Blue or Search) for Ask BAY65 over other apps. Requires the BAY65 remote shortcut accessibility service, with no screen-reading capability or key history. Off by default.

- **Everywhere Mode** (optional, off by default): the assistant orb stays in a chosen corner over other apps, and "Ask BAY65" opens a translucent assistant panel over the current app via the optional remote shortcut, notification, or an external app list. Google Assistant launching the entry by name is device-dependent and did not work on the owner’s TV. The overlay is display-only and never takes remote focus. Settings for position, size, idle opacity, auto-dim and restore after restart; burn-in drift and a 5 fps idle redraw. The owner confirmed physical Menu activation over Prime Video on Android 10; Back returning to Prime Video was verified. Other playback and reboot scenarios remain unverified. See [Everywhere Mode](android/EVERYWHERE.md).
- New permissions, requested only when Everywhere Mode is turned on: display over other apps, foreground service (special use), notifications (Android 13+) and start at boot.
- Shared assistant state and settings: one conversation and one provider configuration for the launcher and the panel.

## 0.3.0 — 18 November 2026

**BAY65 Assistant** — an optional, floating voice assistant on the home screen. Off until you set it up in Settings → BAY65 Assistant. PRs #1–#4.

### New
- Floating, state-coloured orb (blue idle, teal listening, amber thinking or speaking), ported from Rupert AI. Select it and press OK to talk, hold OK to type; once set up, the remote's search key also talks to it.
- Free voice on the TV: Android's speech recogniser for listening and built-in text-to-speech for replies. No voice API key.
- Free AI providers: Google Gemini (default model `gemini-3.5-flash-lite`), Groq, OpenRouter free models, or your own HTTPS OpenAI-compatible server such as Ollama.
- Gemini web search (Grounding with Google Search, on by default) for current questions about your favourite apps, such as new releases, top 10s and trending songs, with up to three sources shown under the answer.
- Optional sharing of your stops, live arrivals, and favourite app and game names, so you can ask "When is my next bus?" (on by default; turn it off in Assistant settings).

### Security and privacy
- Provider key tested before saving, encrypted with Android Keystore, masked, excluded from autofill and screenshots, sent only in a header and never logged. The LTA key's storage is unchanged.
- HTTPS only, redirects disabled; a saved custom-server key is never sent to a different address.
- Microphone permission requested only on first use. Conversation history stays in memory and is cleared on settings changes and Turn off.

### Reliability (from a multi-agent code review)
- Clear errors: server overloads are temporary and never blame your model or key; daily limits are told apart from per-minute limits; Gemini's invalid-key reply is reported as a key problem.
- Searched questions retry once without web search on a rate limit or grounding error; Save and test uses the real web-search setting.
- Falls back to the system voice screen when in-app listening fails, rebuilds a faulted recogniser and retries text-to-speech.
- Assistant requests run on their own thread, stop at once when you press OK and time out after 60 seconds, so bus updates are never delayed.
- The idle orb redraws at a reduced rate.

### Packaging
- Version 0.3.0, version code 6. Same package IDs and maintenance signer; existing stops, apps, photos and the LTA key are preserved on upgrade.
- New permissions: `RECORD_AUDIO` (asked on first voice use). The microphone is declared optional.

## 0.2.2 — 15 November 2026

- Restore standard system audio for vertical Settings navigation while keeping scrollbars hidden.
- Preserve existing maintenance package, original signer and local storage. Version code 5.
- Owner reports installed maintenance version with no further issues. See [validation](android/VALIDATION.md).
- PR #12.

## 0.2.1 — 15 November 2026

- Grow native bus cards into available space above favourites; display colour-only arrival times with accessible capacity descriptions.
- Improve logo and LTA-control spacing, centre Back/Close, hide Settings scrollbars and restore horizontal shelf navigation sounds.
- Improve Bluetooth/accessibility/launcher shortcuts and browser About privacy information.
- Version code 4. PR #11 (includes superseded PR #10 privacy changes).

## 0.2.0 — 15 November 2026

- Rebrand Nook as BAY65; new logo, banner, wordmark and midnight/aqua palette.
- Display three upcoming buses with independent LTA Load colours.
- Public package becomes `app.bay65.tv`; development becomes `app.bay65.tv.debug`; maintenance stays `app.nook.tv.debug`. Version code 3.
- PR #9.

## 0.1.1 — 15 November 2026

- Add privacy, LTA attribution and third-party licensing notices; replace browser brand artwork with neutral placeholders.
- Add clean source packaging, historical secret scanning and explicit local original-key signing automation.
- Retire legacy release binaries from the current tree; preserve private history.
- Version code 2. PR #7 and PR #8.

## 0.1.0 — Historical Nook baseline

- Native Android TV launcher and offline/live browser preview with configurable bus arrivals, app organisation and photo backgrounds.
- See the historical [validation notes](android/VALIDATION.md#earlier-development-milestones).
