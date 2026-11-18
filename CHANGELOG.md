# Changelog

This records released source versions and significant changes. It does not imply public APK availability.

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
- See the historical [validation notes](android/VALIDATION.md#historical-validation-nook-010).
