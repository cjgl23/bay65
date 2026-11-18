# Privacy notice

BAY65 is an independent, local-first launcher. It has no BAY65 accounts, advertising, analytics or cloud storage. This describes the source in this repository, not third-party apps launched from it.

## Android TV
BAY65 stores your chosen bus stops and services, app layout and preferences in app-private storage. The DataMall key is encrypted using Android Keystore; the key is not bundled or logged. Imported photos (including any metadata in the original file) are copied to private storage and displayed locally. Android backup and device-transfer extraction are disabled by the app. Installed application names and artwork are read locally for launcher functionality.

Upon connecting to LTA, your own API key and requested bus stop codes are sent to LTA over HTTPS, along with ordinary network request information such as your IP address. Bus stop directory data may be cached locally. LTA may monitor API usage under its [API terms](https://datamall.lta.gov.sg/content/datamall/en/api-terms-of-service.html). No photos, app lists or photo metadata are intentionally sent to LTA. Independent third-party apps have their own privacy practices.

The optional BAY65 Assistant is off until you set it up. When on, your typed or recognised question, the current time and recent exchanges from the same session are sent over HTTPS to the AI provider you chose (Google Gemini, Groq, OpenRouter or your own server), together with your API key. If **Include my bus stops, live arrivals and favourite app names** is on (the default), your stop names and codes, current arrival estimates and favourite app names are also sent. That provider's own terms and privacy policy apply; some free tiers may use prompts to improve their models. The provider key is encrypted using Android Keystore. Speech recognition and spoken replies use the TV's own speech services, which may process audio according to the TV maker's or Google's settings. Conversation history is kept in memory only. **Turn off and remove saved key** removes the provider settings and key.

Disconnect in BAY65 preferences to remove the saved key and cached directory; remove bus stops and imported photo copies in settings. Clear BAY65's app data or uninstall it to remove its private settings and copies. Your original photos are unaffected.

## Browser preview
Preferences and bus stops are stored in browser local storage. Selected photo files remain in the browser session. The optional loopback Python server retains the LTA key and short-lived arrivals cache in memory only; disconnect or stop the server to clear them. Clear site data to remove saved preferences. No external network request is made by the offline preview unless you follow an external link.

Do not post screenshots containing your stops, photos or credentials in public issues. Report security concerns as described in [SECURITY.md](SECURITY.md).
