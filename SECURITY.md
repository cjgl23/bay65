# Security Policy

## Supported versions

Security fixes target the latest version on `main`. BAY65 is a personal, best-effort project; there is no guaranteed support SLA.

## Reporting vulnerabilities

Use **Security → Report a vulnerability** if private reporting is available. Otherwise, open an issue without sensitive details to request private contact. Do not post LTA keys, bus-stop configurations, private photos, signing keys or exploit details.

## Security and privacy

Each user supplies their own LTA DataMall key. Never commit credentials, local preferences or signing material. The Android app stores its key using Android Keystore, excludes private settings from backups, and uses HTTPS. The browser preview is a loopback-only development service, not a public web service.

CI validates source tests, Android lint/build and release artifacts. The shared security workflow scans for vulnerabilities, secrets and misconfiguration. These checks do not constitute a security certification.
