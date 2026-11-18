# BAY65 Assistant — Everywhere Mode

Everywhere Mode keeps the BAY65 Assistant orb in a corner of the screen while you use other Android TV apps, and lets you ask the assistant without returning to the BAY65 home screen. It is **off by default** and needs the assistant to be set up first (Settings → BAY65 Assistant → Everywhere Mode).

> Status: the remote shortcut build passed local assembly, JVM unit tests and Android TV lint. The owner confirmed physical Menu-button activation over Prime Video on Android 10; Back returning to Prime Video was also verified. This is limited device validation, not a complete acceptance pass or CI result for the pending changes. Netflix, YouTube, playback, reboot and other checklist scenarios remain unverified.

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
- **Visibility across tasks.** Visible launcher and panel instances are tracked individually from start to stop. Android TV can keep both a Home-task launcher and an ordinary app-task launcher; a late stop from either must not clear the other’s visibility.
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
| Optional remote shortcut accessibility service | An optional accessibility service filters the chosen remote button. It requests no window content or accessibility events and stores no key history. Enable it separately in Android Accessibility settings. |
| No Usage Access | Hiding the orb over chosen apps ("excluded apps") requires knowing the foreground app, which needs the sensitive Usage Access permission. Not included; needs your approval. |
| Reuse the existing AI providers | Everywhere Mode reuses the existing assistant unchanged; no telemetry or external service is added. |

## Activating the assistant from another app

Set up **Settings → BAY65 Assistant → Everywhere Mode → Remote shortcut**:

1. Choose **Menu**, Red, Blue, or Search (Off by default).
2. Open **Remote shortcut service** and enable **BAY65 remote shortcut** in Android Accessibility settings.
3. With Everywhere Mode on, press the chosen button in another app. Ask BAY65 opens; Back returns to the app.

The selected button replaces its usual action while Everywhere Mode is on; other buttons pass through. Off disables filtering. Some firmware reserves Search for Google Assistant; Menu or a colour button is preferable. Only one accessibility service may receive key-filtering events, so another button mapper may conflict. The service does not read screen content, collect accessibility events, or store key history.

The Ask BAY65 notification and external TV app lists can also open the panel. Google Assistant's ability to launch the sideloaded entry by name is device-dependent and did not work on the owner's TV. Home then the launcher orb remains available.

## Settings

| Setting | Default | Notes |
|---|---|---|
| Everywhere Mode | Off | Asks for consent, then for "Display over other apps" (and notifications on Android 13+). |
| Orb position | Bottom right | Any of the four corners. The panel opens in the same corner. |
| Orb size | Small (48 dp) | Medium 64 dp, Large 80 dp. |
| Idle opacity | 100% | 15, 30, 50 or 100%. |
| Auto-dim during inactivity | Off | Halves the idle opacity after 30 s without assistant activity. |
| Excluded applications | None | Not available without Usage Access (needs approval). |
| Remote shortcut button | Off | Menu, Red, Blue or Search; replaces the selected button while the mode is on. |
| Remote shortcut accessibility service | Disabled | Enable separately in Android Accessibility; receives keys without screen-reading access or key history. |
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

- **Activation:** the optional remote shortcut requires enabling BAY65 remote shortcut in Accessibility. Reserved buttons and competing key-filter services may prevent delivery.
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
- The overlay service, panel activity and restart receiver are not exported. The optional remote shortcut accessibility service is exported for Android to bind, protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`. The "Ask BAY65" entry is exported (required for app lists and Google Assistant), accepts no data, and is disabled until you turn the feature on.
- The microphone is used only after you ask, in the foreground panel. Other apps' screens are never read, recorded or captured.
- The provider key stays encrypted with Android Keystore and is never logged. No telemetry, analytics or new external services.
- Revoking "Display over other apps" turns Everywhere Mode off quietly; the settings page explains it.

## Install and upgrade

1. Install the new build as an in-place update (same package and signer). Existing stops, apps, photos, LTA key and assistant settings are kept. Everywhere Mode is off by default on first setup; an existing enabled setting is preserved.
2. Settings → BAY65 Assistant → **Everywhere Mode** → **Turn on Everywhere Mode** → Continue.
3. Allow **Display over other apps** for BAY65 when Android asks, then press Back to return. (If your TV has no such screen, follow the one-time adb instruction BAY65 shows.)
4. Configure and enable the optional remote shortcut using the steps below. Open another app and press the chosen button to ask; press Back to return.

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
| 10 | Ask by remote | Enable the shortcut, open another app and press the selected physical remote button; ask a question | Panel opens, listens and answers; record voice and app compatibility separately |
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
| 24 | Upgrade | Install over 0.3.0 with settings in place | Stops, apps, photos, keys and existing mode settings kept; new shortcut defaults to Off |
| 25 | Shortcut disabled | Set the shortcut Off, then test the button; repeat with Everywhere Mode off | Original button action is preserved |
| 26 | Key isolation | With shortcut on, use D-pad, volume, playback and Back in another app | Unrelated buttons pass through |
| 27 | Shortcut alternatives | Test supported Red, Blue or Search buttons and any competing key-filter service | Record firmware-reserved buttons and service conflicts without claiming universal support |

## Exact setup used on the owner’s TV

Device: PrismPlus 4K Android TV, Android 10. Package: `app.nook.tv.debug`, version `0.3.0-tv` (version code 6), signed with the existing maintenance signer. These steps were performed on 18 November 2026 with the owner’s authorization.

1. **Install the shortcut build as an update.** Build `:app:assembleTv :app:testDebugUnitTest :app:lintTv`, sign and verify the maintenance APK using the original key, then install with `adb install --no-incremental -r <signed-maintenance.apk>`. Do not uninstall first. This preserves existing settings and encrypted keys. Signing material stays outside the repository; see [signing guidance](../scripts/RELEASE.md). No new key or signing identity was created.
2. **Confirm the prerequisites.** BAY65 Assistant was already configured, Everywhere Mode was already on, and Display over other apps and microphone permissions were already granted. These permissions were checked rather than reset. On a fresh setup, configure Assistant and enable Everywhere Mode first.
3. **Choose the remote button.** On the BAY65 home screen, open the gear → **BAY65 Assistant → Everywhere Mode**. Scroll to **Remote shortcut**, select **Shortcut button · Off**, then choose **Menu**. This replaces Menu’s normal action while Everywhere Mode is on.
4. **Open accessibility settings.** Select **Remote shortcut service · Enable in Accessibility**. This TV does not provide a working direct accessibility-settings intent, so BAY65 shows a fallback page. Select **Open Android settings → Device Preferences**, scroll down to **Accessibility → BAY65 remote shortcut**.
5. **Enable the service.** Select **Enable**, then **OK** on Android’s “Use BAY65 remote shortcut?” confirmation. Android shows a general warning about observing typed input because key filtering can receive key events. BAY65’s service requests only key filtering: it does not request window content or an accessibility event stream and does not record keys. No TalkBack, Switch Access, or other accessibility service was enabled or disabled. No ADB accessibility grant or secure-settings edit was used.
6. **Check visibility.** In BAY65’s Everywhere Mode settings, confirm **Idle opacity · 100%** and leave **Dim further after 30 seconds without assistant activity** unchecked. The current build’s defaults produced these values on this TV; the existing **Bottom right** corner and **Small** size were retained. Explicit choices remain adjustable and are not overwritten on each launch.
7. **Test using the physical remote.** Open Prime Video and press **Menu**. The owner confirmed that the assistant panel appeared. Press **Back** to close it and return to Prime Video. No Google Assistant voice command is required.

The service remains enabled in Android Accessibility, but handles the chosen button only when Everywhere Mode is on, overlay permission is granted and Assistant is configured. Other keys pass through. Turning the shortcut **Off**, turning Everywhere Mode off, or disabling **BAY65 remote shortcut** restores the button’s normal handling. To remove the accessibility permission completely, use **Android settings → Device Preferences → Accessibility → BAY65 remote shortcut → Disable**.

### Verification evidence and limits

| Check | Result |
|---|---|
| TV APK assembly, JVM unit tests, Android TV lint | Passed locally for the shortcut/visibility build |
| Package identity, original signer, APK alignment and key-material checks | Passed before installation |
| In-place install and BAY65 launch | Passed; existing installation retained |
| Accessibility binding | `dumpsys accessibility` showed BAY65 remote shortcut bound, with key-filter capability, no subscribed event types, and no other enabled services |
| Physical Menu over Prime Video | Owner confirmed panel opens on the shortcut build |
| Return-to-home overlay regression | After the visibility fix, three app-launcher → Prime Video → Home round trips showed zero overlay windows on BAY65 and exactly one over Prime Video. Opening/dismissing the panel over BAY65 also left zero overlay windows; checked with Android window/activity dumps |
| Panel and Back navigation | ADB showed `AssistantActivity` foreground, then Prime Video foreground after Back |
| Visibility settings | 100% opacity, dimming unchecked; bottom right, Small |
| Google Assistant “Open Ask BAY65” | Did not work on this TV; not required by the shortcut |
| Synthetic `adb shell input keyevent 82` | Did not activate the panel on this TV; use the physical remote for acceptance |
| Playback continuity, Netflix/YouTube, reboot persistence, other buttons/remotes, competing key-filter services | Not yet verified; transition checks do not establish playback continuity |

Optional read-only diagnostics for a connected test TV (select the intended device first if more than one is connected):

```sh
adb shell appops get app.nook.tv.debug SYSTEM_ALERT_WINDOW
adb shell dumpsys accessibility
adb shell dumpsys activity activities
```

Expect overlay access to be allowed and `app.nook.tv.debug/app.nook.tv.RemoteShortcutService` to be enabled and bound. While the panel is open the resumed activity should be `app.nook.tv.AssistantActivity`; after Back it should be the previous app. These diagnostics do not replace a physical remote test. Do not commit full device dumps, credentials or private screenshots as evidence.
