# Security Policy

## Supported versions

Security fixes target the latest version on `main`. BAY65 is a personal, best-effort project; there is no guaranteed support SLA.

## Reporting vulnerabilities

Use **Security → Report a vulnerability** if private reporting is available. Otherwise, open an issue without sensitive details to request private contact. Do not post LTA keys, bus-stop configurations, private photos, signing keys or exploit details.

## Security and privacy

Each user supplies their own LTA DataMall key and, for the optional assistant, their own AI provider key. Never commit credentials, local preferences or signing material. The Android app stores its keys using Android Keystore, excludes private settings from backups, and uses HTTPS. The browser preview is a loopback-only development service, not a public web service.

The optional remote shortcut accessibility service receives key events while enabled by the user. It requests key-filter capability without screen-content retrieval or an accessibility event stream, stores no key history and handles only the selected shortcut while Everywhere Mode is active. The selected button replaces its usual action in that mode. Disable it in Android Accessibility to revoke access; turning the shortcut Off stops handling but does not revoke the service permission.

The optional BAY65 Now Playing notification listener exists only to hold Android's Notification access grant, which `MediaSessionManager.getActiveSessions()` requires. It is exported only under `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, overrides no notification callbacks and asks Android to unbind as soon as it connects. BAY65 reads only media-session metadata (app, title, subtitle, artist, album and playback state), and only when **Share what I'm watching with the assistant** is on. Media text is untrusted, so it is flattened to one line, shortened to 120 characters and labelled as data in the prompt. Revoke access in Android Notification access settings.

CI validates source tests, Android lint/build and release artifacts. The shared security workflow scans for vulnerabilities, secrets and misconfiguration. These checks do not constitute a security certification.
