# Glass Launcher

An original, iOS-inspired Android launcher built around **Liquid Glass**: translucent, refracting
panels that float over your wallpaper. Built for the Redmi Turbo 5 on HyperOS (Android 15/16),
and works on any Android 12+ phone (minSdk 31).

> Original code and artwork. Not affiliated with Apple. No Apple artwork, no code from other launchers.
> No analytics, ads or network access. Glass never captures your screen.

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

- **Glass Icons** — a second app (`:glassicons`, `com.shivam.glassicons`): 61 original icons in the
  iOS 26 style, each in three variants (light, dark, tinted), as Android vector drawables. It is a
  standard ADW/Nova icon pack (`appfilter.xml` with AOSP, Google and Xiaomi/HyperOS components,
  `drawable.xml`, iconback/mask/upon), so Nova, Lawnchair and others can use it too. The app is a
  glass gallery over the bundled wallpapers with an "Apply to Glass Launcher" button and a request
  list of installed apps that have no icon yet. All artwork is drawn from geometric primitives in
  `tools/icons/generate_icons.py`; third-party apps get simplified glyphs in our own style.
- **Icon variants:** Settings → Customise → Icon style: Auto (follows dark mode), Light, Dark or
  Tinted (glyph in the accent or a chosen colour on a dark glass tile).
- **Per-app icons:** long-press an app → Edit Icon → any installed pack, a photo, or Default.
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

### Glass Icons variant convention

For every drawable `name` mapped in `appfilter.xml`, a pack may ship `name_dark` (dark glass tile,
coloured glyph) and `name_tinted` (white monochrome glyph on transparent; the launcher draws the
tile and tints the glyph). Glass Launcher picks them by icon style and falls back to `name`.
To change the pack, edit `tools/icons/generate_icons.py` and run it; CI checks the output is current.

## Install

1. Open the latest green run of the **Build** workflow in the repo's Actions tab.
2. Download the `app-debug` artifact and unzip it to get `app-debug.apk`. For the icon pack, also
   download `glassicons-debug` (`glassicons-debug.apk`).
3. Copy it to the phone and open it. Allow "Install unknown apps" for your file manager when asked.
4. Open Glass and follow **Set up Glass** (it opens on first launch).

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

HyperOS turns off its own full-screen gestures while a third-party launcher is default. Some users
force them back on with:

```bash
adb shell settings put global force_fsg_nav_bar 1
```

This is unreliable: some builds ignore it, it can break the recents animation, and it often
needs re-running after a reboot or OTA update. Glass Gestures is the supported way.

## Build

CI only (no local SDK needed): `.github/workflows/build.yml` sets up JDK 17 and the Android SDK, then
checks the generated icon pack is current, runs the unit tests (calculator, converter, fuzzy matcher,
layout ops, the glass-only policy check, wallpaper colour extraction, backup round-trip and the
appfilter validation) and assembles both APKs. It uploads `app-debug.apk` and `glassicons-debug.apk`.

Kotlin · Jetpack Compose · `:app` (launcher) and `:glassicons` (icon pack) modules · DataStore for prefs · JSON file for the layout ·
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
- **Phase 4 — Glass Icons and wallpapers:** an original icon-pack app in the standard format,
  original light/dark wallpapers (set through Glass for Path A refraction), font picker,
  backup/restore.

All phases reuse `ui/glass` (`GlassSurface`, `Backdrop`, `GlassMorph`, the glass controls).

## Licences

App code: all rights reserved by the author unless a licence file says otherwise. Inter typeface
© The Inter Project Authors, SIL Open Font License 1.1 (`FONT_LICENSE_Inter_OFL.txt`; the OFL allows
bundling fonts in software). Manrope, Nunito, Space Grotesk and Lora © their project authors, SIL OFL
1.1 (`FONT_LICENSE_*_OFL.txt`). Google Fonts provider certificates (`res/values/font_certs.xml`) are
from the Android Open Source Project samples, Apache 2.0. Glass Icons artwork and the wallpapers are
original and generated by the scripts in `tools/`.
