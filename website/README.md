# BAY65 website

The public landing page for BAY65. It is a static site (HTML, CSS and a small script) with no build step and no runtime dependencies, so it deploys to Vercel as-is.

## Files

| Path | Purpose |
|---|---|
| `index.html` | The landing page |
| `styles.css` | All styles and design tokens (colours, radii, type) |
| `main.js` | Progressive enhancements: route rail, sample arrival board, remote-control demo, photo preview, copy button |
| `404.html` | Not-found page, served automatically by Vercel |
| `assets/fonts/` | Self-hosted Bricolage Grotesque variable font (SIL Open Font License, see `OFL.txt`) |
| `assets/img/` | WebP home-screen screenshots and the 1200×630 social share image (`og.png`) |
| `assets/logo.svg` | BAY65 mark, also used as the favicon |

The page works without JavaScript. Icons are from [Phosphor Icons](https://phosphoricons.com) (MIT), inlined as an SVG sprite.

## Preview locally

From the repository root:

```bash
python3 -m http.server 8080 -d website
```

Then open http://127.0.0.1:8080.

## Deploy to Vercel

The root `vercel.json` tells Vercel to skip install and build steps and serve the `website/` folder. It also sets security headers (including a strict Content Security Policy) and caching for `/assets`.

**From the dashboard**

1. In Vercel, choose **Add New → Project** and import `cjgl23/bay65`.
2. Leave **Framework Preset** as **Other** and **Root Directory** as the repository root. `vercel.json` supplies the rest.
3. Choose **Deploy**. Every push to `main` then deploys to production, and pull requests get preview URLs.

**From the CLI**

```bash
npm i -g vercel
```

```bash
vercel        # preview deployment
```

```bash
vercel --prod # production deployment
```

## Design notes

- **Brand:** night `#0C1825`, bay teal `#53DECF`, mist `#A1B8C7`, taken from the existing logo and TV app. Arrival colours (green, orange, red) are used only for bus load, matching the app.
- **Type:** Bricolage Grotesque, using its width axis: condensed for headings (an arrival-board feel), normal width for reading.
- **Shapes:** actions are pills, surfaces use a 20px radius, app tiles 12px.
- **Theme:** dark only, matching the TV launcher and where it is used (a living room in the evening).
- **Motion:** the TV "switches on" once on load, the sample board refreshes every 20 seconds like the app, and the orb breathes. All motion stops with `prefers-reduced-motion`.
- **Honesty:** the arrival board on the page uses labelled sample data, never live LTA data. The uploaded-photo demo uses a local object URL and never leaves the browser.

## Updating content

Copy lives in `index.html`. If the app's features, privacy behaviour or build requirements change, update the matching section here as well as the main [README](../README.md) and [PRIVACY.md](../PRIVACY.md).

To refresh the screenshot, replace `portfolio/bay65-redacted.png` and regenerate the WebP files (Python with Pillow):

```bash
python3 -c "from PIL import Image; im=Image.open('portfolio/bay65-redacted.png').convert('RGB'); im.save('website/assets/img/home-screen.webp',quality=82,method=6); im.resize((1000,round(im.height*1000/im.width))).save('website/assets/img/home-screen-1000.webp',quality=80,method=6)"
```

Only use screenshots with stop names, stop codes and service numbers redacted.
