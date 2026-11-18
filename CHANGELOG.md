# Changelog

This records released source versions and significant changes. It does not imply public APK availability.

## Unreleased

- Add the optional BAY65 Assistant: a floating, state-coloured voice orb ported from Rupert AI, using the TV's free speech recogniser and text-to-speech, with Google Gemini, Groq, OpenRouter or an HTTPS OpenAI-compatible server (such as Ollama) for answers.
- Encrypt the assistant key with Android Keystore; share stops, live arrivals and favourite app names only while the opt-out setting is on.
- Request microphone permission only on first use; fall back to the system voice screen or typing.
- Show the provider's reason for rejected assistant requests, and report Gemini's HTTP 400 invalid-key response as a key problem rather than a model problem.

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
