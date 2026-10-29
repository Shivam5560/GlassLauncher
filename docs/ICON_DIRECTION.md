# Glass icon direction: Tinted glass

Status: proposal for the owner, 29 Oct 2026. The preview is `docs/icon-direction/sheet.html`. Open it in any
browser; it makes no network requests. Add `?m=dark|light|tinted|clear` to the URL to pick a style.

Assumptions (stated so nobody has to stop and ask):
- The chosen "Tinted glass" concept becomes Glass's **Dark** icon style, which is the default. The existing
  "Tinted" option keeps the iOS meaning: one accent colour on a graphite tile. See open question 3.
- Every glyph is original. The metaphors are iOS-familiar (handset, bubble, gear, live date, live clock face), but
  no Apple artwork is traced, extracted or imitated. Photos is a stack of frames, not a petal flower. Settings
  is a plain 8-tooth gear, not the layered Apple gear. Weather is a sun behind a cloud on our own sky gradient.

---

## 1. Strategy: one finish, four sources

Rule: **every icon on Home gets the same glass finish from one renderer (rim, specular cap, lower glow, shadow).
Only the layer inside the tile changes.** The owner's "unfinished" complaints come from mixed finishes: full-bleed
white squares next to dark tiles. Unifying the finish fixes that more than any single piece of artwork.

| Case | Treatment | Why |
|---|---|---|
| **(a) Core system apps** (dialer, messages, camera, gallery, clock, calendar, settings, files, security…) | Glass pack glyph (white, 24-grid) on a **hue tile** by job. Calendar and Clock are live. | This is where iOS familiarity matters, and HyperOS's own system icons are the furthest from iOS. |
| **(b) Third-party apps with a brand logo** | **Never redrawn.** Take the app's adaptive *foreground* layer as shipped and composite it on a **graphite glass tile** (`#2C3038→#111318`). Drop the adaptive background layer when it is near-white or a flat brand colour. If the foreground can't stand alone (logo baked into the background), use the whole adaptive icon masked to the squircle with the glass overlay on top. | This is the OnePro Dark look the owner already likes, produced automatically for every app. Calm graphite lets the logo carry the colour, so a hue tile doesn't fight the logo. |
| **(c) Adaptive icons with a monochrome layer** (Android 13+) | In the **Tinted** style, use the monochrome layer in the accent colour on graphite. Light, Dark and Clear behave as in (b). | Android's own themed-icon data gives iOS-style tinted icons with zero artwork. |
| **(d) Unknown / legacy bitmap icons** | Sample the bitmap's corners. **Opaque full-bleed** icons get masked to the squircle with the overlay on top. **Transparent** icons are drawn at 70 % on graphite. A **near-white full-bleed** background (luma > 0.85) gets its background darkened to graphite with the same corner sampling, so no white square ever appears in the Dark style. | Covers the long tail without a single drawn asset. |
| **Installed packs (OnePro Dark, others)** | A user pack is a **source for (b)/(d)**, not a replacement for (a). Resolution order: per-app override > **Glass pack for system roles** > user pack > adaptive composite > legacy fallback. Add a toggle, "Glass finish on pack icons" (default on). It adds only the rim and cap when the pack art is already a full tile. | Owner gets OnePro Dark for brands plus Glass art for system apps, in one consistent finish. |

**Remove the 96 brand drawables from `glassicons/`** (instagram, whatsapp, spotify, netflix, gpay…: 125 of the
201 `appfilter.xml` items. appletv, applemusic and authenticator count as brands here). They are simplified redraws of trademarks. That breaks the ground rule in HANDOFF
("brand logos are never redrawn"), and treatment (b) makes them unnecessary. `docs/CONSTRAINTS.md` line 67 still says
"third-party apps get simplified glyphs in our own style". Update it when this lands.

**Ship first:** the renderer plus (b) and (d). Brand and unknown apps are about 80 % of what is on screen, and
they need no artwork. Then the first 12 glyphs in this sheet.

---

## 2. Tile spec (Compose Canvas / AGSL)

All numbers use a **100-unit tile** unless marked px. Scale by `size / 100`.

**Shape**
- Superellipse `|x|^5 + |y|^5 = 1`, filling the full 100 units. Build it once as a 128-point `Path`
  (`x = 50 + 50·sgn(cosθ)|cosθ|^0.4`) and cache it per size.
- Every layer below is clipped to this path except the drop shadow.

**Layers, bottom to top**

| # | Layer | Dark (default) | Light | Tinted | Clear |
|---|---|---|---|---|---|
| 0 | Drop shadow (outside) | y `0.025·size`, blur `0.05·size`, black **0.40** (×0.6 on light wallpapers) | black 0.18 | 0.40 | 0.22 |
| 1 | Body: linear gradient, start (15,0) to end (85,100) | hue light → hue dark, alpha **0.94** | light → `mix(dark, light, 0.35)`, alpha 1 | `#2C3038 → #111318`, alpha 0.90 | white **0.09** on dark wallpaper / 0.22 on light, over backdrop blur `0.14·size` with saturation 1.7 |
| 2 | Lower glow: radial, centre (50,100), radius 50, drawn in an ellipse (50,104) rx 46 ry 22 | hue light @ **0.45** → 0 | 0.35 | 0.22 | 0.18 |
| 3 | Specular cap: ellipse (40,−4) rx 72 ry 46, vertical gradient | white **0.22** → 0 | 0.30 | 0.16 | 0.30 |
| 4 | Rim: inner stroke (stroke 2w, clipped), linear gradient from (0,0) to (100,100) | white **0.55** → 0.10 @ 55 % → 0.04 | 0.70 | 0.45 | 0.60 |
| 5 | Glyph | white @ 0.96 | white | hue light (graphite roles use `#C9CDD4`) | white (dark wallpaper) / `#1B1E24` (light) |
| 6 | Glyph shadow | dy 1.0, blur σ 1.1, black 0.28 | 0.28 | 0.28 | 0.12 on light |

- **Rim width** w = `clamp(0.02·size, 1, 2)` px: 1.2 px at 60 px, 2 px from 100 px up.
- **Light comes from the top left.** The cap is offset left, the rim fades toward the bottom right, and the glow
  sits on the lower edge.
- **Glyph box**: 48 × 48 units, centred (origin 26,26). The source grid is 24 units, scaled ×2.
  **Stroke 1.75 grid units** (3.5 tile units, 2.1 px at 60 px) with round caps and joins. Filled glyphs keep the same
  optical weight. Secondary strokes use 0.5 opacity. Keep 2 grid units of padding inside the glyph box.
- **Live Calendar**: weekday in coral `#FF6B5E`, IBM Plex Sans SemiBold, 4.8 grid units, tracking +0.5, baseline
  7.6. Date in Plex Sans Medium, 13.5 grid units, tracking −0.6, baseline 20.6. Redraw on `ACTION_DATE_CHANGED` and
  `TIME_SET`.
- **Live Clock**: 12 ticks (quarter ticks longer). Hour hand 2.0 wide to r 5.2, minute hand 1.5 wide to r 8.4,
  second hand `#FF8A3D` 0.8 wide with a 2-unit tail. Tick the second hand only while Home is resumed and visible.
  Otherwise tick per minute.
- **AGSL (optional, API 33+)**: replace layers 3 and 4 with a shader that bends the rim highlight by the
  superellipse normal (`n = ∇(|x|^5+|y|^5)`). Use the Canvas version everywhere else. Either way, **bake each tile
  to a bitmap cache** keyed by `(component, size, style, accent)`. The second hand is the only per-frame layer.

**Hue table** (light stop → dark stop). One hue per *job*, identical for the iOS role and the Android role.

| Token | Light | Dark | Used for |
|---|---|---|---|
| comm | `#45D483` | `#0B5C33` | Phone/Dialer, Messages |
| blue | `#4A9DFF` | `#0F3C8F` | Mail, Browser, Files, Downloads, Contacts |
| sky | `#63C3FF` | `#1A4C9A` | Weather |
| teal | `#34CDB5` | `#0A5753` | Maps, Compass, Translate |
| media | `#FF5E86` | `#7E0F33` | Music |
| photo | `#FF9A6B` | `#8A2546` | Photos/Gallery |
| violet | `#A874FF` | `#3E1A8C` | Podcasts, TV/Video |
| rose | `#FF7A9A` | `#82163A` | Health/Fitness |
| graphite | `#737985` | `#1B1E24` | Camera, Settings, Clock, Calendar, Calculator, Scanner, Remote |
| amber | `#FFC34D` | `#8A5300` | Notes, Reminders, Torch (glyph shadow 0.35 for contrast) |
| safety | `#3FD6A0` | `#075442` | Security, Find device |
| cyan | `#4CD3E8` | `#0A4E66` | Cleaner, Updater |
| red | `#FF5D55` | `#7F1414` | Recorder/Voice Memos, Screen Recorder |
| indigo | `#7C7FFF` | `#25238A` | Themes, Shortcuts, Glass Settings |
| ink | `#4A505C` | `#0B0D11` | Wallet |
| graphite tile (brands, Tinted) | `#2C3038` | `#111318` | Every third-party app |

Accents: weekday coral `#FF6B5E`, clock second hand `#FF8A3D`, weather sun `#FFD66B`.

---

## 3. Critique of the 48-icon list

**Merge (one glyph, two labels).** Phone/Dialer, Photos/Gallery, Voice Memos/Recorder, and Files, Clock, Calculator,
Settings and Camera (which appear in both lists). 48 → **about 38 unique glyphs**. The label comes from the app, not
the pack.

**Drop or defer.**
- *Torch* and *Battery*: on HyperOS these are Control Center tiles and Settings pages, not launcher apps.
- *Shortcuts*: there is no Android target. Reuse the indigo glyph later for Glass's own settings.
- *Mail*, *Browser* and *Maps*: most of the owner's devices run Gmail, Chrome and Google Maps, which are brand logos
  under rule (b). Draw these roles only for Mi Browser or AOSP fallbacks, at low priority.

**Weak metaphors and the fix.**
- *Cleaner*: a broom reads as housework. Use a spark plus a small spark over a sweep arc (in the sheet).
- *Updater*: a circular arrow reads as sync or refresh. Use an up-arrow rising out of an open ring.
- *Remote*: a rectangle with buttons reads as a phone. Use a short pill emitting two arcs from its tip.
- *Security*: a bare shield collides with VPN and Authenticator. Use a shield plus a check (in the sheet).
- *Contacts* vs *Phone*: both get drawn as a person. Contacts = person inside a rounded card with a spine. Phone stays
  a handset.
- *Themes*: a palette is generic. Use a brush resting on a small squircle (it themes *icons*).
- *Screen Recorder* vs *Recorder*: Recorder = waveform bars. Screen Recorder = record dot inside a phone outline.
- *Downloads*: an arrow into a tray, not a cloud (cloud means Drive/OneDrive brands).

**Missing roles.** Store (GetApps/Play fallback), Video player, Find device, Smart Home (Mi Home), Notes,
**Glass Settings** (the launcher's own entry needs its own icon so it isn't confused with system Settings),
Wallpaper, Magnifier.

**Priority for the first 24** (dock and page 1 first, then daily use, then the long tail):
1. Phone · 2. Messages · 3. Camera · 4. Photos/Gallery · 5. Settings · 6. Clock (live) · 7. Calendar (live) ·
8. Weather · 9. Files · 10. Security · 11. Notes · 12. Contacts · 13. Calculator · 14. Recorder · 15. Music ·
16. Maps · 17. Cleaner · 18. Themes · 19. Scanner · 20. Compass · 21. Downloads · 22. Store · 23. Glass Settings ·
24. Screen Recorder.

The sheet covers 1–10, plus Maps and Cleaner (the hardest metaphor).

---

## 4. Preview

`docs/icon-direction/sheet.html` shows Phone, Messages, Camera, Photos, Clock, Calendar, Settings, Weather, Maps,
Files, Security and Cleaner, on dark and light wallpapers at 60/120/180 px, in all four styles. The JS in the page is
the same token table as section 2, so it doubles as a reference implementation.

---

## 5. Open questions for the owner (max 5)

1. **Pack layering**: Glass art for system apps and OnePro Dark for brands, at the same time. OK as the default when
   both are installed?
2. **Delete the brand redraws** from `glassicons/` (96 apps). Treatment (b) replaces them with the real logos on
   a dark tile. OK?
3. **Naming**: the chosen direction becomes the **Dark** style. "Tinted" stays the single-accent style. We add
   **Clear**. That gives four styles: Dark (default) / Light / Tinted / Clear. OK?
4. **Brand tiles**: graphite for every brand (calm, OnePro-like, proposed), or a tile darkened from each brand's
   colour (livelier, less uniform)?
5. **Clock second hand**: live seconds while Home is visible (iOS-like, small battery cost), or minutes only?

## 6. Implementation order for the Glass team

1. **Renderer**: a `GlassTile` draw function with the cached squircle path, the layer table above as one `TileStyle`
   data class per style, and a bitmap cache. Existing `<name>_tinted.xml` drawables are already white glyph-only on
   a 100-unit viewport, so the renderer can use them on day one.
2. **Resolution chain + brand compositing** (cases b–d and pack layering). This is the biggest visible win, with no
   new art.
3. **Pack cleanup**: drop the brand items. `generate_icons.py` emits glyph-only `<name>_glyph` vectors plus a
   `hue` map for Glass, and keeps baked composites for other launchers (ADW compatibility).
4. **Live Calendar and Clock.**
5. **Glyphs** in the priority order above. Review them against this sheet at 60 px on the Turbo 5 before drawing
   the next batch.
