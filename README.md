# Glass Launcher

An original, iOS-inspired Android launcher built around **Liquid Glass**: translucent, refracting
panels that float over your wallpaper. Built for the Redmi Turbo 5 on HyperOS (Android 15/16),
and works on any Android 12+ phone (minSdk 31).

> Original code and artwork. Not affiliated with Apple. No Apple artwork, no code from other launchers.
> No analytics or ads. The only network use is the weather widget (Open-Meteo, no account). Glass never captures your screen.

## Features (phase 1)

- **Launcher core.** HOME activity on a transparent window over the system wallpaper. Horizontally
  paged 4×6 grid, a 4-slot glass dock and a glass page-dot pill (tap it for Spotlight). Long-press
  and drag to rearrange; drop an app on another to make a folder. Folders open as glass popups that
  grow out of the icon. Edit mode has jiggling icons, glass remove badges and a glass toolbar.
  Kept in sync through `LauncherApps` callbacks, with work-profile apps badged.
- **Liquid Glass**, the single design primitive (`ui/glass/GlassSurface`), used for every surface,
  from the dock to settings rows:
  - **Base layer:** cross-window blur of the wallpaper (`Window.setBackgroundBlurRadius`), with a
    runtime listener on `isCrossWindowBlurEnabled`.
  - **In-app backdrop:** a recorded GraphicsLayer of the wallpaper copy plus the app content
    behind the panel, blurred with RenderEffect.
  - **Lens (Android 13+):** an AGSL `RuntimeShader` with a superellipse SDF. The rim (about 14dp)
    bends the backdrop, the centre stays flat, and the rim has slight chromatic aberration.
  - **Lighting:** a specular rim light and a soft sheen. Their angle follows the device tilt
    (game rotation vector, low-pass filtered, paused when Glass is off screen).
  - **Adaptive tone:** light glass with dark text over dark areas, dark glass over light areas,
    biased dark in system dark mode.
  - **Variants:** Regular (frosted) and Clear (transparent, stronger lensing).
  - **Motion:** spring morphs out of the tapped icon. Pressed glass brightens and swells.
  - **Fallbacks:** frosted only (blur + tint + gradient + 1px rim) below Android 13 or if the shader fails.
- **Glass tuning** with a live refraction preview: variant, tone, blur, tint colour and opacity,
  refraction, rim width, chromatic aberration, edge highlight, tilt lighting. Persisted with DataStore.
- **App Library.** Swipe past the last page. Apps are auto-categorised by `ApplicationInfo.category`
  plus a package-keyword table, shown as glass tiles. There is a glass search field, and an A–Z
  list with a glass scrubber.
- **Spotlight.** Swipe down anywhere on a home page, or tap the page-dot pill. The keyboard comes up
  at once. Results:
  - apps: fuzzy, prefix-weighted, boosted by usage;
  - contacts, with one-tap Call and WhatsApp (shown only if WhatsApp is installed);
  - Settings pages;
  - an inline calculator (hand-written parser) and a length/weight/temperature converter;
  - "Search Google" and a Siri Suggestions-style row.

  Searches run off the main thread, debounced.
- **Icon menus and badges.** The long-press glass menu shows `LauncherApps` shortcuts plus Rename,
  Remove, Hide, Lock, App info and Uninstall. Notification badges come from a
  `NotificationListenerService`.
- **Hidden and locked apps.** Locked apps need BiometricPrompt (or your screen lock) before launching.
- **Icon packs.** ADW/Nova packs are detected and their `appfilter.xml` parsed. Unmapped icons are
  masked to a squircle using the pack's iconback, mask and upon.
- **Glass Gestures.** An AccessibilityService with thin overlay zones:
  - swipe up = Home, swipe up and hold = Recents, edge swipe = Back (with a glass stretch bubble);
  - a glass home-indicator pill;
  - per-app exclusions, and the zones auto-pause while the keyboard is up;
  - double-tap empty home space to lock the screen.
- **System check and setup guide.** Every permission and HyperOS setting Phase 1 needs, with live
  status and a fix button (see `docs/CONSTRAINTS.md`).

## Features (phase 4)

- **Installed icon packs:** standard ADW/Nova packs remain supported. The old standalone
  Glass Icons companion app and its generator have been retired. Unmapped apps retain their
  original logos, styled with the selected pack's background and mask when available.
- **Icon variants:** Settings → Customise → Icon style: Auto (follows dark mode), Light, Dark or
  Tinted (glyph in the accent or a chosen colour on a dark glass tile).
- **Per-app icons:** long-press an app → Edit Icon, or Settings → Icon pack → Individual app icons.
  Search for an app and choose artwork from an installed pack, a photo, or Default.
- **Pinned shortcuts:** "Add to Home screen" from other apps opens a glass confirmation sheet.
- **Wallpapers:** 12 original abstract wallpapers (mesh gradients, flowing ribbons, soft caustics),
  each a light and dark pair at 1440×3200, rendered by `tools/wallpapers/generate_wallpapers.py`.
  The glass picker has categories, previews with a live glass panel, auto light/dark switching, and
  sets them through Glass so refraction works (Path A).
- **Theme:** Default, From wallpaper (accent and glass tint from the wallpaper's colours) or Custom
  (theme editor).
- **Font picker:** bundled OFL fonts plus downloadable Google Fonts via Google Play services, with a
  bundled fallback.
- **Backup & restore:** one JSON file through the Storage Access Framework, with a restore preview.

### Icon pack variant convention

For every drawable `name` mapped in `appfilter.xml`, a pack may ship `name_dark` (dark glass tile,
coloured glyph) and `name_tinted` (white monochrome glyph on transparent; the launcher draws the
tile and tints the glyph). Glass Launcher picks them by icon style and falls back to `name`.

## Install

1. Open the latest green run of the **Build** workflow in the repo's Actions tab.
2. Download the `app-debug` artifact and unzip it to get `app-debug.apk`.
3. Copy it to the phone and open it. Allow "Install unknown apps" for your file manager when asked.
4. Open Glass and follow **Set up Glass** (it opens on first launch).

## Features (phase 5 — Smart Launcher parity, own code and design)

Behaviour observed from using Smart Launcher 6, reimplemented from scratch (no code, resources or
artwork taken from it). The RSS/news feed page is out of scope.

- **Readable glass:** dark-tinted glass by default; text and icons pick white or near-black from
  the measured backdrop luminance (WCAG AA, never white on light). Gentler lens defaults
  (refraction 0.3, chroma 0.15, specular 0.45). Settings sections are one glass card with divider
  rows. Installed icon packs are selected explicitly in Settings.
- **Clean default home:** a large centred clock / date / weather / next-event header, rounded photo
  and calendar widgets, a label-free 4-column grid, the floating glass dock and page pill.
- **App drawer (swipe up):** apps sorted automatically into Internet, Media, Social, Games,
  Utilities, Settings and Other; one page per category with a big title and Search / Play Store /
  ⋮ buttons; few apps sit at the bottom for thumb reach; a floating glass category bar. ⋮ adds
  shortcuts, folders and categories, edits the category bar, sorting and hidden apps. Long-press
  an app → Move to category. Settings → App drawer: launch view and transition.
- **Widgets:** At a glance, Compass (rotation-vector sensor), Icon group (2×2 / 4×4), Smart search,
  alongside the existing ones.
- **Ultra-immersive mode:** hides the status bar (and the nav bar with Glass Gestures).
- **Appearance:** one screen with a live preview: Solid / Frosted / Liquid Glass presets, per-role
  colours (Background, Cards & buttons, Widget, Panel), icon shape and size, labels, widget corner
  radius, font, folders and home page animation.
- **Kustom:** KWGT widgets via the normal widget host; KLWP scrolls with the pages.
- **Share your home screen:** edit mode → share button renders Glass's own home layers to a PNG
  (no screen capture) and opens the share sheet.

## HyperOS setup (the in-app guide deep-links each step)

1. **Default Home:** Settings → Apps → Default apps → Home app → Glass Launcher.
2. **Allow restricted settings** (required for sideloaded accessibility / notification access):
   Settings → Apps → Manage apps → Glass Launcher → ⋮ (top right) → *Allow restricted settings*.
3. **Accessibility:** Settings → Additional settings → Accessibility → Downloaded apps → Glass Gestures → On.
4. **Notification access** (badges): Settings → Notifications → Notification access → Glass Launcher.
5. **Autostart:** Security app → Permissions → Autostart → Glass Launcher → On.
6. **Battery:** App info → Battery saver → *No restrictions*.
7. *(Recommended)* **Wallpaper via Glass:** Settings → Glass → Wallpaper → Choose from Photos. Glass
   keeps a copy so the glass can refract the wallpaper (Path A). Alternative: All files access (Path B).

### Optional ADB alternative for native HyperOS gestures

HyperOS forces 3-button navigation whenever any third-party launcher (Smart Launcher too) is the
default Home app. Glass Gestures is the fix. Some users
force them back on with:

```bash
adb shell settings put global force_fsg_nav_bar 1
```

This is unreliable: some builds ignore it, it can break the recents animation, and it often
needs re-running after a reboot or OTA update. Glass Gestures is the supported way.

## Build

CI only (no local SDK needed): `.github/workflows/build.yml` sets up JDK 17 and the Android SDK, then
runs the unit tests (calculator, converter, fuzzy matcher, layout ops, the glass-only policy check,
wallpaper colour extraction and backup round-trip) and assembles the launcher. It uploads `app-debug.apk`.

Kotlin · Jetpack Compose · `:app` launcher module · DataStore for prefs · JSON file for the layout ·
no DI framework, no Room.

## Constraints

`docs/CONSTRAINTS.md` is binding: if the ideal path is blocked, the listed fallback ships and the
user is told in-app. `REVIEW.md` lists the code-review rules (glass only, no screen capture).

## Roadmap

- **Phase 2 — Widgets and Today view:** music, weather (Open-Meteo), calendar, battery and photos
  widgets; third-party widgets via `AppWidgetHost`; free-placement grid.
- **Phase 3 — Dynamic Island, Control Center and Notification Center:** an island overlay around
  the punch-hole camera, a glass Control Center (Settings panels for radios), and a Notification
  Center with inline reply.
- **Phase 4 — Icons and wallpapers:** installed icon packs and per-app overrides,
  original light/dark wallpapers (set through Glass for Path A refraction), font picker,
  backup/restore.

All phases reuse `ui/glass` (`GlassSurface`, `Backdrop`, `GlassMorph`, the glass controls).

## Licences

App code: all rights reserved by the author unless a licence file says otherwise. Inter typeface
© The Inter Project Authors, SIL Open Font License 1.1 (`FONT_LICENSE_Inter_OFL.txt`; the OFL allows
bundling fonts in software). Manrope, Nunito, Space Grotesk and Lora © their project authors, SIL OFL
1.1 (`FONT_LICENSE_*_OFL.txt`). Google Fonts provider certificates (`res/values/font_certs.xml`) are
from the Android Open Source Project samples, Apache 2.0. The bundled wallpapers are original
and generated by `tools/wallpapers/generate_wallpapers.py`.

## Widget and notification refinement

- **Checklist:** local tasks, one-tap completion and a glass editor; Medium / Large sizes.
- **Focus:** 25-minute focus, 50-minute deep work and 5-minute breaks, opened in the system Clock app.
- **Quick actions:** Internet, volume, Bluetooth settings and alarms without background polling.
- **Readable island alerts:** compact text stays beside the physical camera; expanded titles and messages sit below it. Font-aware sizing, adjustable 4–15-second reading time and a notification preview. The island ticker pauses while the screen is off or locked.
- **Island compatibility:** Automatic / System / Glass modes. Automatic yields to a detected HyperOS island. Saved Off stays off during startup and calibration.
- **Notifications:** adaptive glass, clear app/title/message hierarchy, All / Alerting / Quiet filters and dismissible-only clearing. Lock cards hide message content and never replace authentication.

Design references: [Apple iOS 27](https://www.apple.com/os/ios/) for glass readability and contrast;
[Samsung One UI 9](https://www.samsung.com/us/apps/one-ui/) for readable layouts and accessible controls.
These are original launcher implementations; OS-level AI, kernel optimizations and replacement of the
system keyguard are not launcher features. The Focus handoff uses Android's
[Clock intents](https://developer.android.com/reference/android/provider/AlarmClock), and quick actions use
[system panels](https://developer.android.com/reference/android/provider/Settings.Panel).
