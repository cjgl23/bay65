// BAY65 website: small progressive enhancements. The page works without this file.
(() => {
  "use strict";
  document.documentElement.classList.add("js");
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  /* Masthead hairline once the page has moved */
  const masthead = document.querySelector(".masthead");
  const sentinel = document.createElement("div");
  sentinel.setAttribute("aria-hidden", "true");
  sentinel.style.cssText = "position:absolute;top:0;height:1px;width:1px";
  document.body.prepend(sentinel);
  new IntersectionObserver(([entry]) => {
    masthead.classList.toggle("is-scrolled", !entry.isIntersecting);
  }).observe(sentinel);

  /* Route rail: mark the current stop and the stops already passed */
  const stops = [...document.querySelectorAll(".route a")];
  const sections = stops.map((a) => document.getElementById(a.dataset.stop)).filter(Boolean);
  const routeList = document.querySelector(".route ol");
  const setCurrent = (id) => {
    const index = stops.findIndex((a) => a.dataset.stop === id);
    if (index < 0) return;
    stops.forEach((a, i) => {
      a.classList.toggle("is-current", i === index);
      a.classList.toggle("is-passed", i < index);
      if (i === index) a.setAttribute("aria-current", "true");
      else a.removeAttribute("aria-current");
    });
    if (routeList) {
      const progress = stops.length > 1 ? (index / (stops.length - 1)) * 100 : 0;
      routeList.style.setProperty("--progress", progress + "%");
    }
  };
  const visible = new Map();
  const sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach((e) => visible.set(e.target.id, e.isIntersecting ? e.intersectionRatio : 0));
    let best = null, bestRatio = 0;
    sections.forEach((s) => {
      const r = visible.get(s.id) || 0;
      if (r > bestRatio) { best = s.id; bestRatio = r; }
    });
    if (best) setCurrent(best);
  }, { rootMargin: "-35% 0px -45% 0px", threshold: [0, 0.01, 0.25, 0.5, 0.75, 1] });
  sections.forEach((s) => sectionObserver.observe(s));
  setCurrent("top");

  /* Sample arrival board. Synthetic data, refreshed every 20 seconds like the TV app. */
  const tbody = document.getElementById("arrivals");
  const updated = document.getElementById("updated");
  if (tbody && updated) {
    const loads = ["seats", "seats", "standing", "seats", "limited", "standing"];
    const pick = () => loads[Math.floor(Math.random() * loads.length)];
    const now = () => Date.now();
    const minute = 60000;
    // Each service: list of { at: timestamp, load, scheduled }
    // Minutes away, load and whether the estimate is scheduled rather than tracked.
    const services = [
      { no: "36", headway: 11, buses: [[1.2, "seats"], [11, "seats"], [24, "standing"]] },
      { no: "14", headway: 12, buses: [[4.6, "standing"], [16, "seats", true], null] },
      { no: "190", headway: 10, buses: [[7.8, "limited"], [19, "seats"], [31, "seats"]] }
    ].map((s) => ({
      no: s.no,
      headway: s.headway,
      buses: s.buses.map((b) => b && { at: now() + b[0] * minute, load: b[1], scheduled: !!b[2] })
    }));

    const label = (bus) => {
      if (!bus) return { text: "—", cls: "none" };
      const mins = Math.round((bus.at - now()) / minute);
      const text = mins <= 0 ? "Arriving" : mins + " min";
      return { text: (bus.scheduled ? "~" : "") + text, cls: bus.load };
    };

    const render = (animate) => {
      const t = now();
      services.forEach((s) => {
        // Drop buses that have left and queue the next one
        s.buses = s.buses.filter((b) => !b || b.at > t - 30000);
        const known = s.buses.filter(Boolean);
        while (s.buses.length < 3) {
          const last = known.length ? known[known.length - 1].at : t;
          const next = { at: last + s.headway * minute * (0.85 + Math.random() * 0.3), load: pick(), scheduled: false };
          const gap = s.buses.indexOf(null);
          if (gap >= 0) s.buses.splice(gap, 1);
          s.buses.push(next);
          known.push(next);
        }
      });
      const rows = tbody.rows;
      services.forEach((s, r) => {
        const row = rows[r];
        if (!row) return;
        s.buses.slice(0, 3).forEach((bus, c) => {
          const cell = row.cells[c + 1];
          const { text, cls } = label(bus);
          if (cell.textContent !== text) {
            cell.textContent = text;
            if (animate && !reduceMotion.matches) {
              cell.classList.remove("changed");
              void cell.offsetWidth;
              cell.classList.add("changed");
            }
          }
          cell.className = cls + (cell.classList.contains("changed") ? " changed" : "");
        });
      });
      const d = new Date();
      updated.textContent = "Sample data, updated " + d.toLocaleTimeString("en-SG", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false });
    };
    render(false);
    let timer = setInterval(() => render(true), 20000);
    document.addEventListener("visibilitychange", () => {
      clearInterval(timer);
      if (!document.hidden) { render(true); timer = setInterval(() => render(true), 20000); }
    });
  }

  /* Remote-first launcher demo: spatial navigation between tiles */
  const launcher = document.getElementById("launcher");
  const status = document.getElementById("launcher-status");
  if (launcher && status) {
    const tiles = [...launcher.querySelectorAll(".tile")];
    let current = tiles[0];
    const focusTile = (tile, moveFocus) => {
      tiles.forEach((t) => {
        t.tabIndex = t === tile ? 0 : -1;
        t.classList.toggle("is-focused", t === tile && !moveFocus);
      });
      current = tile;
      if (moveFocus) tile.focus({ preventScroll: true });
      status.textContent = tile.textContent.trim() + " is focused";
    };
    const centre = (el) => {
      const r = el.getBoundingClientRect();
      return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    };
    const move = (dir, moveFocus) => {
      const from = centre(current);
      let best = null, bestScore = Infinity;
      tiles.forEach((t) => {
        if (t === current) return;
        const c = centre(t);
        const dx = c.x - from.x, dy = c.y - from.y;
        const ok = (dir === "left" && dx < -4) || (dir === "right" && dx > 4) ||
                   (dir === "up" && dy < -4) || (dir === "down" && dy > 4);
        if (!ok) return;
        const primary = dir === "left" || dir === "right" ? Math.abs(dx) : Math.abs(dy);
        const cross = dir === "left" || dir === "right" ? Math.abs(dy) : Math.abs(dx);
        const score = primary + cross * 2.2;
        if (score < bestScore) { best = t; bestScore = score; }
      });
      if (best) focusTile(best, moveFocus);
      else status.textContent = "Edge of the screen. " + current.textContent.trim() + " stays focused";
    };
    const open = (tile) => {
      tile.classList.add("is-pressed");
      setTimeout(() => tile.classList.remove("is-pressed"), 160);
      status.textContent = "Opening " + tile.textContent.trim() + ". On a TV, this launches the app.";
    };
    const keys = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right" };
    launcher.addEventListener("keydown", (e) => {
      if (keys[e.key]) { e.preventDefault(); move(keys[e.key], true); }
    });
    tiles.forEach((t) => {
      t.addEventListener("click", () => { focusTile(t, true); open(t); });
      t.addEventListener("focus", () => { if (t !== current) focusTile(t, false); });
    });
    document.querySelectorAll(".pad__btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        const dir = btn.dataset.dir;
        if (dir === "ok") open(current);
        else move(dir, false);
        current.classList.add("is-focused");
      });
    });

    /* Memories: preview a photo as the backdrop, locally only */
    const input = document.getElementById("photo-input");
    const photo = document.getElementById("launcher-photo");
    const help = document.getElementById("photo-help");
    let objectUrl = null;
    input.addEventListener("change", () => {
      const file = input.files && input.files[0];
      if (!file) return;
      if (!file.type.startsWith("image/")) {
        help.textContent = "That file is not an image. Choose a JPEG, PNG or WebP photo.";
        return;
      }
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = URL.createObjectURL(file);
      const probe = new Image();
      probe.onload = () => {
        photo.style.backgroundImage = `url("${objectUrl}")`;
        photo.classList.add("has-photo");
        help.textContent = "Your photo is the backdrop now. It stays in this browser tab.";
      };
      probe.onerror = () => {
        help.textContent = "This browser could not open that photo. Try a JPEG or PNG.";
      };
      probe.src = objectUrl;
    });
  }

  /* Copy build commands */
  document.querySelectorAll("[data-copy]").forEach((btn) => {
    const label = btn.querySelector("span");
    btn.addEventListener("click", async () => {
      const source = document.getElementById(btn.dataset.copy);
      const text = source.innerText.split("\n").filter((l) => !l.trim().startsWith("#")).join("\n").replace(/\n{2,}/g, "\n").trim();
      try {
        await navigator.clipboard.writeText(text);
        btn.classList.add("is-done");
        label.textContent = "Copied";
      } catch {
        label.textContent = "Select and copy";
      }
      setTimeout(() => { btn.classList.remove("is-done"); label.textContent = "Copy"; }, 2200);
    });
  });
})();
