# Handoff — Glass Launcher, Phase 7 (29 Oct 2026)

Branch `claude/relaxed-goodall-yfi2bq` (not pushed). Goal set by the owner: make Glass **better than
Smart Launcher 6 (SL6)** and look like **iOS 26/27**: looks → features → optimisation. Test device:
the owner's **REDMI Turbo 5** (HyperOS 3.0 / Android 16, 1268×2756, density 416, 120 Hz). Glass is now
the phone's **default Home** (SL6 is still installed).

## Ground rules (owner decisions, keep them)

- **No root.** Everything goes through normal APIs, `adb shell settings`, `pm grant`, and
  `pm uninstall -k --user 0` (reversible with `cmd package install-existing <pkg>`).
- **Brand logos are never redrawn**; iOS-style art only for core system apps. **Never copy Apple
  artwork** (owner asked about extracting iOS icons: declined, copyright). Study designs from public
  screenshots/articles only.
- SL6 is studied by observation only. Default icon look: Dark.
- Owner wants **iOS 27** behaviour; check the web (Claude in Chrome works) before guessing UI.
- **Ship release builds** (`assembleRelease`, signed with this Mac's debug key). Never debug APKs.
- Commit after each verified round; don't push.

## What shipped this phase (oldest first)

| Commit | What |
|---|---|
| `ce4cd74` | Home = SL6's apps only (one-time `LayoutOps.arrange`, layout `version` 2; no auto-add of new apps); bloat filter (`AppRepository.BLOATWARE`); gesture nav (HyperOS `force_fsg_nav_bar`); NC/CC corner swipes fixed; island sized from punch-hole width; Clear hides sticky cards; App Library tile cap; status-bar space kept while immersive; concentric dock |
| `0a1d69e` | Lock-screen notifications drawn by Glass (`LockScreenNotifications`, `OverlayWallpaperBackdrop`); HyperOS heads-up banners off while island shows (`setSystemBanners`); music island at idle size, album-art wave colours; HyperOS disclaimer removed; Clear All includes group summaries; pages full height under dock |
| `98c6667` | iOS status bar on home (`ui/home/IosStatusBar.kt`); island music card (art/waves flank camera, title below); blurred wallpaper behind App Library/Today; Spotlight inner glass removed; calls on home open the dialer's full-screen intent |
| `f56424d` | Status bar: notification icons, speed, 5G/4G, battery %; **Blur wallpaper on Home** setting (default on); island collapses on outside tap / swipe up; smoother sine waves |
| `4fa4281` | Island stays visible on lock screen; slow swipe-up released mid-screen = Recents |
| `bbcf0e1` | Foreground app also read from the active window (Recents "clear all" sends no event) |
| `fe074d8` | ContentObserver re-applies `force_fsg_nav_bar` (HyperOS clears it at boot) |
| `2d93fc7` | **Glass Control Center retired** → HyperOS's (StatusBarManager `expandSettingsPanel`; right corner only on Glass home); panel blur set once per open/close (per-frame `updateViewLayout` froze HyperOS); 1.5 s forced close |
| `3a8a701` | iOS 27 Spotlight ("Search or Ask" black pill + mic, dark results) and Notification Center (round ⓧ, see-through clock, torch/camera buttons, 24 dp frosted platters, cascade entrance) |

## Phone state set over adb (survives reboot unless noted)

- Granted to Glass: `WRITE_SECURE_SETTINGS`, `READ_PHONE_STATE`.
- Glass Gestures accessibility service enabled; notification access granted.
- Glass writes at runtime: `force_fsg_nav_bar=1` + `hide_gesture_line=1` (gesture mode, no 3 buttons),
  `heads_up_notifications_enabled` (0 while the island shows), `lock_screen_show_notifications=0`
  (Glass draws the lock list). Restored on service destroy.
- Private DNS = `dns.adguard-dns.com` (ad blocking; owner chose it).
- Bloat removed for user 0: App vault, Services & feedback, Games, Mi Music, Indus Appstore, Downloads UI,
  Google One, Google TV, Personal Safety, Meet; Mi Store disabled. **Kept**: Mi Remote (owner),
  Themes and SIM Toolkit (only hidden in Glass; HyperOS/Airtel depend on them). MSA ads not touched.
- `support_dynamic_island` (HyperOS Super Island): HyperOS resets it to 1 at boot → **cannot be
  disabled without root**. Left at 1.
- **Force-stopping Glass disables its accessibility service** — re-enable with
  `settings put secure enabled_accessibility_services com.shivam.glasslauncher/com.shivam.glasslauncher.gestures.GlassGestureService`.

## Known limits (explained to the owner)

- HyperOS per-app **floating notifications** and its own island can't be suppressed by an app; owner
  should turn off Settings → Notifications & status bar → Floating notifications per app.
- iOS-style full-screen call UI: impossible without becoming the default dialer (and HyperOS call
  recording only exists in its dialer). Glass opens the dialer's full-screen intent on home.
- `adb shell cmd notification post` notifications never reach Glass's peek path; test with real apps.
- Lock-screen cards frost the **home** wallpaper copy (wrong if the lock wallpaper differs).

## Untested / open

1. **Incoming call while on Glass home** (should open HyperOS call screen) — needs a real call.
2. **Recents "clear all" → island stays** (fix in `bbcf0e1`; not pressed, it closes the owner's apps).
3. Gesture nav after the next reboot (observer fix `fe074d8` only simulated).
4. **Optimisation (owner's next priority)**: drawer 13 ms / Spotlight 14 ms median vs 8.3 ms budget;
   paging 10 ms with Home blur. Lead from Phase 6: a full-screen layer drawn offscreen per drawer frame,
   ~3 Vulkan allocations/frame — label glass layers in a Perfetto trace. Home blur and side-page blur
   are `Modifier.blur` on a full-screen `Image` (`LauncherScreen.kt`, `HomeBlur = 14.dp`) — measure.
5. Earlier backlog (SL6 parity): popup widgets, widget stack, grid presets, Private Space, Nova import…

## Device workflow

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home
export ANDROID_SERIAL=NVPVQOT8ZPAY6LGA
ADB=~/Library/Android/sdk/platform-tools/adb; BT=~/Library/Android/sdk/build-tools/35.0.0
./gradlew :app:assembleRelease :app:testDebugUnitTest -q
$BT/zipalign -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/a.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out /tmp/glass.apk /tmp/a.apk
$ADB install -r /tmp/glass.apk          # keeps data, grants and the a11y service
$ADB shell dumpsys gfxinfo com.shivam.glasslauncher reset   # then swipe, then | grep -E "50th|90th"
```

## Gotchas

- Release builds aren't debuggable: no `run-as`, no stack traces. Use temporary `Log.i("GlassDbg", …)`
  + `adb logcat -s GlassDbg`, and remove them before committing.
- HyperOS: accessibility overlays get **stale insets** (status-bar state comes from a11y windows:
  short `TYPE_SYSTEM` window at top); `GLOBAL_ACTION_QUICK_SETTINGS` is ignored; the unlock broadcast is
  unreliable (lock window polls the keyguard); the cutout is reported as an 80×156 px strip.
- Any leftover full-screen or mid-screen overlay window eats home swipes — check
  `dumpsys window windows | grep Glass` when paging "freezes".
- Two `OverlayController`s exist (accessibility + island-only fallback); only the accessibility host
  may write system settings.
- The owner often uses the phone while you test: avoid lock/unlock loops and never press Recents
  "clear all" or other destructive buttons.
- `adb input` is single-touch; the unlock swipe that works is `input swipe 634 2600 634 1000 400`.
