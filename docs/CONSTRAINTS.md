# Glass Launcher — Platform Constraints & Fallbacks

Target: Redmi Turbo 5, HyperOS (Android 15/16). minSdk 31. Every feature must follow this file: if the ideal path is blocked, ship the listed fallback and tell the user in-app, never fail silently.

Status legend: ✅ works as designed · ⚠️ works with a caveat · ❌ not possible, fallback used

## Cross-cutting

| Topic | Status | Rule |
|---|---|---|
| Sideloaded app permissions | ⚠️ | Accessibility and notification access need **Settings → Apps → Glass Launcher → ⋮ → Allow restricted settings** first. Onboarding must explain this before linking. |
| HyperOS background killing | ⚠️ | Overlays and listeners die unless **Autostart = on** and **Battery = No restrictions**. Onboarding deep-links both; a health check in Settings shows a red row if either is off. |
| Every permission | ✅ | Ask lazily, at first use, with a one-line reason. Feature degrades gracefully if denied. |
| Analytics, ads, billing | — | None. No network except weather and Google search intents. |

## Phase 1 — Launcher core

| Feature | Status | Ifs and buts → fallback |
|---|---|---|
| Liquid Glass refraction | ⚠️ | Needs the wallpaper as a bitmap. Android 14+ blocks apps from reading it. **Path A:** wallpaper set via our picker → we keep the bitmap (default). **Path B:** user grants "All files access" → read via `WallpaperManager` (verify on HyperOS). **Path C:** live wallpaper or no access → blur + tint + rim only, no refraction. **Never** use screen capture / MediaProjection. |
| Background blur | ⚠️ | `blurBehindRadius` needs `WindowManager.isCrossWindowBlurEnabled()`. It turns false in battery saver and on some HyperOS builds. Fallback: pre-blurred wallpaper bitmap (Path A/B), else a solid tinted glass. Listen for the change at runtime. |
| AGSL shaders | ⚠️ | API 33+. If compiling the shader fails → frosted-only. |
| Adaptive tint | ✅ | `WallpaperColors`, no permission needed. |
| Gesture navigation | ⚠️ | AccessibilityService + `performGlobalAction`. No native "app shrinks into icon" animation. Recents opens the HyperOS recents screen. Edge zones can clash with in-app back swipes, keyboards and games → per-app exclusion list + an auto-pause when the keyboard is up. |
| Double-tap to lock | ⚠️ | Needs the accessibility service on; otherwise the gesture is hidden in settings. |
| Contacts in Spotlight | ✅ | READ_CONTACTS, asked at first search. WhatsApp button only if WhatsApp is installed. |
| App shortcuts | ⚠️ | `LauncherApps.getShortcuts` works only while we are the default home app. |
| Badges | ⚠️ | Needs notification access. Counts are per notification, not each app's own unread number. |
| Locked apps | ⚠️ | Lock prompt only when opened **from our launcher**. Opening from notifications or recents bypasses it. State this in the UI. |
| Hidden apps | ⚠️ | Hidden from our grid, library and search only. Still visible in system settings. |
| Icon packs | ✅ | ADW/Nova `appfilter.xml`. Unmapped icons → squircle mask. |

## Phase 2 — Widgets & Today view

| Feature | Status | Ifs and buts → fallback |
|---|---|---|
| Music widget | ⚠️ | `MediaSessionManager.getActiveSessions` needs notification access (the existing listener's ComponentName is the token). Album art and seek only if the player app exposes them (`ACTION_SEEK_TO`); otherwise a plain progress line. **Fallback:** without access the widget shows a glass prompt that deep-links to notification access. |
| Weather | ✅ | Open-Meteo, no key. Needs location (coarse is enough); fallback = manually picked city (Open-Meteo geocoding search). The only network use besides search intents. Cached in `files/weather.json`; refreshed when older than 30 min on screen, every 45 min by WorkManager (network required), or on tap. Offline → last forecast plus an "Offline" note. |
| Calendar | ✅ | READ_CALENDAR, asked when the user taps the widget's prompt. Denied → the prompt stays and offers App info after a permanent denial. |
| Battery (earbuds) | ⚠️ | Phone battery always (time-left estimate from the observed drain; charge time from `BatteryManager`). Bluetooth earbud battery only via `BluetoothDevice` battery level (Android 13+ BLUETOOTH_CONNECT), and many earbuds don't report it → hide that row. Without the permission: phone only, with a button to allow it. |
| Photos widget | ✅ | Use the Photo Picker (no storage permission). The user picks photos; Glass keeps downscaled copies so no long-lived URI grant is needed. Photos stay still between changes; the short crossfade is disabled when system animations are off. |
| Clock / world clock, Notes, Search bar | ✅ | No permissions. World clock uses the system time-zone database. |
| Smart Stack | ⚠️ | Rotation is by time of day only (calendar mornings, weather daytime, music evenings, batteries at night); there is no on-device usage learning. A manual swipe stays until the next part of the day. |
| Third-party widgets | ⚠️ | `AppWidgetHost` works; some widgets demand a bind permission dialog the first time (shown through an invisible trampoline activity), and some open a configure activity first; cancelling either frees the id. Resizing follows the provider's resize mode and minimum size. A widget whose provider was uninstalled shows a "no longer available" glass card. |
| Fluid / free placement grid | ✅ | Pure layout work. Widgets own cells and never overlap; icons reflow around them (overflowing onto the next page). Widgets drag within their page in edit mode; moving one to another page means removing it and adding it there. |
| Today view, page manager | ✅ | Pure layout work. Removing a page keeps its apps in the App Library and removes its widgets (confirmed first). |

## Phase 3 — Dynamic Island, Control Center, Notification Center

| Feature | Status | Ifs and buts → fallback |
|---|---|---|
| Island overlay | ⚠️ | Accessibility overlay (preferred, no extra permission) or SYSTEM_ALERT_WINDOW. Cutout position from `DisplayCutout`; manual offset slider for calibration. Hidden on lock screen, in fullscreen video and games. |
| Island content (music, calls, timer, navigation, downloads) | ⚠️ | All come from notifications or media sessions. If an app doesn't post a progress or ongoing notification, nothing shows for it. |
| Charging animation | ✅ | Broadcast `ACTION_POWER_CONNECTED` + BatteryManager. Charging watts are estimated (current × voltage); some devices report 0. |
| Camera / mic privacy dots | ⚠️ | `CameraManager.AvailabilityCallback` works; mic in-use detection is limited, so best-effort only. HyperOS shows its own dots too. |
| Control Center: Wi-Fi, Bluetooth, mobile data, airplane mode | ❌ | Third-party apps can't toggle these since Android 10 → open `Settings.Panel` pop-ups. |
| Control Center: flashlight, brightness, volume, DND, rotation | ✅ | Torch API; WRITE_SETTINGS (user grants once); AudioManager; DND access permission. |
| Control Center: full system quick settings | ✅ | Long-press → `GLOBAL_ACTION_QUICK_SETTINGS`. |
| Notification Center | ⚠️ | Notification access: read, group, dismiss, run actions, inline reply via RemoteInput. HyperOS heads-up banners can't be suppressed. |
| Opening our panels from the top edge | ⚠️ | Corner swipe zones through the accessibility overlay. A pull from the status-bar centre may still open the HyperOS shade, and we can't block it. |
| Replacing the lock screen | ❌ | Not possible without root. Out of scope. |

## Phase 4 — Icons, wallpapers, polish

| Feature | Status | Ifs and buts → fallback |
|---|---|---|
| Icon packs | ✅ | Use installed ADW/Nova packs or the app's original icon. The standalone Glass Icons module and generator are retired. Never redraw brand logos; use existing pack artwork or a user-selected photo for per-app overrides. |
| Icon variants (light / dark / tinted) | ⚠️ | Our convention: `<name>_dark` and `<name>_tinted` next to `<name>`. Only packs that ship them change with the style; other packs fall back to their normal drawable. Tinted glyphs are drawn by Glass on its own dark tile in the accent (or chosen) colour. |
| Per-app icon picker | ✅ | Long-press → Edit Icon → any installed pack's drawable, a photo (Photo Picker, no storage permission) or Default. Stored in `files/icon_overrides.json` + `files/custom_icons/`. |
| Pinned shortcuts | ⚠️ | `ACTION_CONFIRM_PIN_SHORTCUT` only reaches the default Home app, and `LauncherApps.startShortcut` / `pinShortcuts` only work while Glass is default. Accepted shortcuts are listed (open / remove) in Settings → Customise → Pinned shortcuts and exposed as `PinnedShortcutStore.shortcuts` for the home grid (Phase 2 owns grid placement). System check shows a row. |
| Wallpapers | ✅ | 12 original designs × light/dark, rendered at 1440×3200 (`tools/wallpapers/generate_wallpapers.py`, WebP). Setting one through the picker keeps a copy → Path A refraction. |
| Auto light/dark wallpaper | ⚠️ | Glass swaps the light/dark version when the system theme changes, while the process is alive (HyperOS may kill it: Autostart + battery rows apply). Stops by itself if the user sets a different wallpaper outside Glass (wallpaper id check). |
| Font picker | ⚠️ | Downloadable Google Fonts need Google Play services (font provider `com.google.android.gms.fonts`). Fallback: bundled OFL fonts (Inter, Manrope, Nunito, Space Grotesk, Lora); a downloadable family renders in Inter while loading or if the download fails. The picker and System check say when Play services is missing. |
| Backup / restore | ✅ | Storage Access Framework, one JSON file: home layout and other `files/*.json` state, every setting in the `settings` and `polish` DataStores (hidden and locked lists included), per-app icons (base64 PNG), pinned shortcuts. Restore shows a preview first. Not encrypted (stated in the UI). Wallpaper bitmaps are not included. Other phases' DataStores are not included unless added to `BackupManager.stores`. |
| Theme from wallpaper | ⚠️ | Palette from the wallpaper bitmap when readable (Path A/B), else from `WallpaperColors` (Path C). Applies the accent everywhere and writes the glass tint. Manual theme editor (accent + tint) as the alternative. |

## Phase 5 — Smart Launcher parity (own code and design)

Behaviour observed from using Smart Launcher 6 on the device, reimplemented from scratch: no decompiled code, resources or artwork. The RSS/news feed page is out of scope.

| Feature | Status | Ifs and buts → fallback |
|---|---|---|
| UI contrast | ✅ | Glass is dark-tinted by default and every surface picks white or near-black text from the measured backdrop luminance mixed with its tint (WCAG AA, never white on light). Settings and the drawer get a Background-role dim so titles drawn on the wallpaper stay legible. When the wallpaper can't be read (Path C) the estimate uses `WallpaperColors`, so contrast is approximate there. |
| Toned-down lens | ✅ | Defaults refraction 0.3, chroma 0.15, specular 0.45; thin surfaces get a proportionally smaller lens so flat rows never streak. Users still on the old defaults are migrated once (`glass_style_version`). |
| Grouped settings | ✅ | One glass card per section, plain rows with hairline dividers. |
| Auto-categorised drawer | ✅ | Category from `ApplicationInfo.category`, then `FLAG_IS_GAME`, then a built-in package/keyword map, then Other. Apps that declare no category and match no keyword land in Other; users move them with long-press → Move to category. Categories, moves, folders, shortcuts and sorting live in `layout.json` (backed up). |
| Drawer shortcuts | ⚠️ | Deep shortcuts can be listed and started only while Glass is the default Home app (`LauncherApps`). Otherwise the Add shortcut sheet explains it, and tapping an existing one shows a glass toast. System check row. |
| Drawer "most used" sorting | ⚠️ | Learns only from launches made through Glass (no usage-stats permission). |
| Widgets: Note, Smart search, Icon group | ✅ | No permissions. Icon group launches through the launcher, so locked apps still ask first. |
| Widget: At a glance header | ⚠️ | Weather and next event reuse the weather (location/city) and calendar (READ_CALENDAR) rows above; without them the header shows the clock and date only. |
| Widget: Compass | ⚠️ | Needs a rotation-vector sensor (no permission). Without one the widget is not offered in the picker, an existing one shows a note, and System check says so. |
| Ultra-immersive mode | ⚠️ | `WindowInsetsControllerCompat` hides the status bar on Home and in the drawer with transient swipe-to-reveal. The navigation bar is hidden only while Glass Gestures is on: HyperOS forces 3-button navigation for every third-party default launcher, so without our gesture service the buttons are the only navigation. |
| Global appearance | ✅ | Presets Solid / Frosted / Liquid Glass and per-role overrides (Background, Cards & buttons, Widget, Panel: colour, opacity, text colour, blur, stroke, accent) flow through `GlassStyle`/`LocalGlassStyle`; icon shape, size, labels, folders and home animation through `Appearance`. Icon shape only applies to icons Glass masks itself; icon-pack artwork keeps its own shape. |
| Kustom (KWGT / KLWP) | ⚠️ | KWGT widgets are ordinary app widgets hosted by our `AppWidgetHost` (bind dialog and configure activity included). KLWP is a live wallpaper: Glass sends wallpaper offsets as pages scroll when a live wallpaper is set, but refraction falls back to Path C (no readable bitmap). Kustom's launcher integration beyond that is not publicly documented, so it is not implemented. |
| Share your home screen | ✅ | Renders Glass's own recorded home layer (pages, widgets, icons, dock) over Glass's wallpaper copy into a PNG in `cache/share/`, shared through a FileProvider with `ACTION_SEND`. No screen capture. Without a readable wallpaper (Path C, live wallpapers) a neutral gradient replaces it. |
| HyperOS navigation | ⚠️ | HyperOS forces 3-button navigation for ANY third-party default launcher (Smart Launcher too). Onboarding says so plainly; Glass Gestures is the fix (System check row). |

## Definition of done for every phase
1. CI green, APK artifact uploaded.
2. Each feature row in this file marked as verified on a real device, or as compiled only.
3. Each ⚠️/❌ fallback actually implemented and reachable, not just documented.
4. The in-app Settings → "System check" screen lists every permission or HyperOS setting the phase needs, with its status and a fix button.
