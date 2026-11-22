# BAY65 website

The public landing page for BAY65. It is a static site (HTML, CSS and a small script) with no build step and no runtime dependencies, so it is published to GitHub Pages as-is.

## Files

| Path | Purpose |
|---|---|
| `index.html` | The landing page |
| `styles.css` | All styles and design tokens (colours, radii, type) |
| `main.js` | Progressive enhancements: route rail, sample arrival board, remote-control demo, photo preview, copy button |
| `404.html` | Not-found page, served automatically by GitHub Pages |
| `.nojekyll` | Tells GitHub Pages to publish files as they are |
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

## Deploy to GitHub Pages

The workflow in [`.github/workflows/pages.yml`](../.github/workflows/pages.yml) publishes this folder to GitHub Pages whenever `website/` changes on `main`. It can also be run by hand from the **Actions** tab (**Pages → Run workflow**).

**One-time setup**

1. In the repository, open **Settings → Pages**.
2. Under **Build and deployment → Source**, choose **GitHub Actions**.
3. Run the **Pages** workflow once (or push a change to `website/`).

The site is then live at https://cjgl23.github.io/bay65/.

**Things to keep in mind**

- The site is served from the `/bay65/` subpath, so every link to a local file is relative (`assets/...`, not `/assets/...`). Keep it that way when adding files.
- GitHub Pages cannot set custom HTTP headers. The Content Security Policy and referrer policy are set with `<meta>` tags in `index.html` instead.
- `.nojekyll` turns off Jekyll processing, so files are published exactly as they are.
- `404.html` is served automatically for missing pages.
- The social share image (`og:image`) and `og:url` use the full `https://cjgl23.github.io/bay65/` address. Update them if you add a custom domain.

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
