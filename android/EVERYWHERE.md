# BAY65 Assistant — Everywhere Mode

Everywhere Mode keeps the BAY65 Assistant orb in a corner of the screen while you use other Android TV apps, and lets you ask the assistant without returning to the BAY65 home screen. It is **off by default** and needs the assistant to be set up first (Settings → BAY65 Assistant → Everywhere Mode).

> Status: implemented and automatically validated (unit tests, compilation, CI). **Not yet validated on a physical TV.** Netflix, YouTube and other third-party apps are *not* claimed as compatible until the checklist below has been run on a device.

## How it works

```text
MainActivity (launcher)  ─┐                         ┌─ AssistantActivity ("Ask BAY65" panel)
   own orb + Assistant     │                         │    own orb + Assistant, translucent,
                           ├── shared, main thread ──┤    opens over the current app on request
                           │   AssistantPresence     │
                           │   (state, conversation, │
                           │    which surface shows) │
                           │                         │
   AgentSettings ──────────┴── provider, model, web search, sharing, Keystore-encrypted key
   AssistantContext ───────── stops + arrivals + favourite/game names (one builder for both)
   Agent / AgentClient ────── provider requests (unchanged), own thread, cancellable

OrbOverlayService (foreground, specialUse)
   └─ one OrbView in a TYPE_APPLICATION_OVERLAY window:
      FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE → display-only, never takes remote focus
```

- **One assistant implementation.** The launcher and the panel both use the existing `Assistant` controller, provider settings, encrypted key and conversation. Nothing is duplicated.
- **One orb on screen.** The overlay orb hides whenever the launcher or the panel is visible (they draw their own orb), and the launcher hides its orb while the panel is open over it.
- **Display-only overlay.** The overlay window cannot be focused or touched, so D-pad navigation, Back and playback keys always go to the app you are using.
- **Asking happens in a normal activity.** The "Ask BAY65" panel is a translucent activity, so it gets D-pad focus, Back and the microphone legitimately, only while it is open. Back closes it and returns focus to the app underneath. It closes itself 12 seconds after the conversation goes quiet.
- **Bus arrivals on demand.** When the panel opens (and sharing is on and LTA is connected), it fetches arrivals for your saved stops once. Nothing is polled while idle.

## Technical decisions

| Decision | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` + `TYPE_APPLICATION_OVERLAY` | The only supported way for an ordinary app to draw over other apps. The user grants it in Android settings. |
| Foreground service, type `specialUse` | An overlay must be owned by a long-lived component; background services are stopped within a minute on Android 8+. No standard type fits "show a user-enabled overlay"; `specialUse` with a subtype description is Google's documented choice. The service never records audio or plays media, so it does not need the `microphone` or `mediaPlayback` types. |
| Panel activity instead of a focusable overlay | A focusable overlay would steal D-pad focus from the app underneath. An activity gets focus only while open and hands it back on Back. Apps with the overlay permission may open their own activity from the background (documented exemption), so the panel can open from the notification at any time. |
| Microphone only in the panel | Android blocks background microphone access (Android 9+), and the spec forbids continuous listening. The panel is a foreground activity, so speech recognition works there exactly as on the home screen. |
| No Accessibility Service | Global remote-button capture needs an Accessibility Service with key-event filtering. That is a significant privacy and security decision (it can observe all input), so it is **not** included and needs your explicit approval. |
| No Usage Access | Hiding the orb over chosen apps ("excluded apps") requires knowing the foreground app, which needs the sensitive Usage Access permission. Not included; needs your approval. |
| No `thinkingConfig`, no new providers or services | Everywhere Mode reuses the existing assistant unchanged; no telemetry or external service is added. |

## Activating the assistant from another app

Ordinary Android apps cannot receive remote-control buttons while another app is in the foreground. BAY65 does **not** work around this. Supported ways to ask:

1. **"Hey Google, open Ask BAY65"** using the remote's Assistant button. "Ask BAY65" is an app entry that BAY65 enables only while Everywhere Mode is on.
2. **The "Ask BAY65" notification** or app entry, where your TV shows notifications or app lists (Android TV notification panel, Google TV dashboard).
3. **Home, then the orb**, as before.

Dedicated-button or long-press activation inside other apps is not possible without an Accessibility Service (see above).

## Settings

| Setting | Default | Notes |
|---|---|---|
| Everywhere Mode | Off | Asks for consent, then for "Display over other apps" (and notifications on Android 13+). |
| Orb position | Bottom right | Any of the four corners. The panel opens in the same corner. |
| Orb size | Small (48 dp) | Medium 64 dp, Large 80 dp. |
| Idle opacity | 30% | 15, 30, 50 or 100%. |
| Auto-dim during inactivity | On | Halves the idle opacity after 30 s without assistant activity. |
| Excluded applications | None | Not available without Usage Access (needs approval). |
| Restore after TV restart | On | Starts the orb after boot or an app update. When BAY65 is the home app it also starts with the home screen. |

**Burn-in care:** the orb drifts by up to 4 dp every 10 minutes, dims when idle, and redraws at about 5 frames per second while idle (every frame only while active).

## Android version compatibility

| Android | API | Overlay | Foreground service | Panel and microphone | Restore after restart | Notes |
|---|---|---|---|---|---|---|
| 8.0–8.1 | 26–27 | Yes (`canDrawOverlays`) | Yes | Yes | Yes | Panel has no fixed orientation (avoids an 8.0 crash for translucent activities). |
| 9 | 28 | Yes | Yes | Yes | Yes | Background microphone blocked by Android; BAY65 uses it only in the panel. |
| **10** (owner's TV) | 29 | Yes | Yes | Yes | Yes | Primary physical test target. Background activity starts allowed via the overlay permission. |
| 11–12L | 30–32 | Yes | Yes | Yes | Yes | |
| 13 | 33 | Yes | Yes | Yes | Yes | Notification permission requested when turning on. |
| 14 | 34 | Yes | `specialUse` type declared | Yes | Yes | |
| 15–16 | 35–36 | Yes | Yes | Yes | Yes | A foreground start from the background needs a visible overlay; BAY65 starts the service from the home screen or at boot. |
| Google TV | 10+ | Device-dependent | Yes | Yes | Yes | Some builds have no "Display over other apps" screen; see below. |

**When the TV has no permission screen:** some Android TV builds omit "Display over other apps". BAY65 then shows a one-time command to run from a computer with adb: `adb shell appops set <package> SYSTEM_ALERT_WINDOW allow`. No repeated adb, root or custom firmware is needed.

## Known limitations

- **Activation:** no global remote button (see above). Asking from another app takes the Google Assistant, a notification or Home.
- **Apps may pause:** while the panel is open, the app underneath loses focus; some players pause until you press Back.
- **Apps can hide overlays:** some apps hide or block overlays on Android 12+ (`setHideOverlayWindows`). BAY65 respects this; the orb simply does not appear there.
- **DRM and secure video:** BAY65 never captures or reads other apps' content. Whether the orb is drawn over protected video depends on the TV.
- **OEM restrictions:** some manufacturers stop foreground services or hide overlays aggressively; the orb returns when you next open BAY65.
- **Excluded apps:** not available without Usage Access.
- **The conversation** is shared between the home screen and the panel and is kept in memory only.

## Performance and resource use

- No API requests, polling or microphone use while idle. Bus arrivals are fetched once per panel opening.
- The overlay orb idles at about 5 fps, pauses completely while the screen is off, and is removed while BAY65 or the panel is on screen.
- No wake locks. One small foreground service with a low-importance notification.
- Assistant requests run on the panel's own thread and are aborted when you leave.

## Security and privacy

- Only the permissions above are added; each is requested when you turn the feature on, with an explanation first.
- The service, panel activity and restart receiver are not exported. The "Ask BAY65" entry is exported (required for app lists and Google Assistant), accepts no data, and is disabled until you turn the feature on.
- The microphone is used only after you ask, in the foreground panel. Other apps' screens are never read, recorded or captured.
- The provider key stays encrypted with Android Keystore and is never logged. No telemetry, analytics or new external services.
- Revoking "Display over other apps" turns Everywhere Mode off quietly; the settings page explains it.

## Install and upgrade

1. Install the new build as an in-place update (same package and signer). Existing stops, apps, photos, LTA key and assistant settings are kept. Everywhere Mode stays off.
2. Settings → BAY65 Assistant → **Everywhere Mode** → **Turn on Everywhere Mode** → Continue.
3. Allow **Display over other apps** for BAY65 when Android asks, then press Back to return. (If your TV has no such screen, follow the one-time adb instruction BAY65 shows.)
4. Open another app. The orb should appear in the chosen corner. Say **"Hey Google, open Ask BAY65"** to ask a question.

To turn it off: Settings → BAY65 Assistant → Everywhere Mode → **Turn off Everywhere Mode**. Turning the assistant off also turns Everywhere Mode off.

## Physical TV acceptance checklist

Record each result as Pass, Fail or Not applicable, with the TV model and Android version. Use a test device first; do not install on the configured TV without approval.

| # | Area | Steps | Expected |
|---|---|---|---|
| 1 | Launcher, off | Everywhere Mode off; use BAY65 as before | No overlay, no notification, no "Ask BAY65" app entry; launcher orb, voice, buses unchanged |
| 2 | Turn on | Settings → Everywhere Mode → Turn on → allow permission → Back | "Everywhere Mode is on"; notification present; "Ask BAY65" in app list |
| 3 | Permission denied | Turn on, deny the permission, Back | "needs permission" message; mode stays off |
| 4 | No duplicate orbs | With the mode on, look at the BAY65 home screen | Only the launcher's orb |
| 5 | Netflix browsing | Open Netflix, browse rows with the D-pad | Orb visible at chosen corner and opacity; all navigation goes to Netflix |
| 6 | Netflix playback | Play a title; use pause, seek and Back | Playback controls unaffected; orb visible or hidden by Netflix (record which) |
| 7 | YouTube browsing | As 5, in YouTube | As 5 |
| 8 | YouTube playback | As 6, in YouTube | As 6 |
| 9 | Other apps | Two other installed apps and a game | Navigation unaffected; note any app that hides the orb |
| 10 | Ask by voice | In Netflix, "Hey Google, open Ask BAY65"; ask a question | Panel opens in the orb's corner, listens, answers on screen and aloud |
| 11 | Ask via notification | Open the "Ask BAY65" notification or dashboard entry | Panel opens and listens |
| 12 | Dismiss | Press Back in the panel | Panel closes; focus returns to the app; the app resumes (note whether it paused) |
| 13 | Auto-close | Ask, then wait | Panel closes about 12 s after the answer ends |
| 14 | Bus query | In another app, ask "When is my next bus?" | Answer uses current arrivals (sharing on, LTA connected) |
| 15 | Typed question | Hold OK on the panel orb | Typing dialog; answer shown and spoken |
| 16 | Text-to-speech | Any answer | Spoken in the TV's voice; OK stops it |
| 17 | Settings | Change corner, size, opacity, auto-dim | Orb moves, resizes and dims accordingly |
| 18 | Revocation | Remove "Display over other apps" in Android settings | Orb disappears; settings page explains; no crash |
| 19 | Standby | Put the TV in standby and wake it | Orb returns; no crash; no duplicate notification |
| 20 | Restart | Restart the TV (restore on) | Orb returns after boot (with BAY65 as home, and with another home app) |
| 21 | Restore off | Turn "Restore after restart" off; restart | Orb returns only after BAY65 is opened |
| 22 | Turn off | Turn off Everywhere Mode | Orb, notification and "Ask BAY65" entry removed |
| 23 | Performance | Play 10 minutes of HD video with the orb visible | No visible stutter; TV stays responsive |
| 24 | Upgrade | Install over 0.3.0 with settings in place | Stops, apps, photos, LTA key and assistant settings kept; mode off |
