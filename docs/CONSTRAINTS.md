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
| Photos widget | ✅ | Use the Photo Picker (no storage permission). The user picks photos; Glass keeps downscaled copies so no long-lived URI grant is needed. Ken Burns motion stops when system animations are off. |
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
| Glass Icons pack | ✅ | Original artwork only. No Apple artwork, no third-party packs bundled. Standard icon-pack format so it works in other launchers. |
| Wallpapers | ✅ | Original, rendered at the device's native resolution. Light and dark pairs. Setting one through our picker enables Path A refraction. |
| Font picker | ⚠️ | Downloadable Google Fonts need Play Services; fallback = bundled OFL fonts. |
| Backup / restore | ✅ | Storage Access Framework file export (JSON + icons). |
| Theme from wallpaper | ✅ | Palette from WallpaperColors / the bitmap. |

## Definition of done for every phase
1. CI green, APK artifact uploaded.
2. Each feature row in this file marked as verified on a real device, or as compiled only.
3. Each ⚠️/❌ fallback actually implemented and reachable, not just documented.
4. The in-app Settings → "System check" screen lists every permission or HyperOS setting the phase needs, with its status and a fix button.
