# Handoff — premium glass and reliability pass (29 Oct 2026)

The current pass integrates three agents in the shared working tree before the combined build.
Baseline was `a02f6e2`; its Fade hit-testing and Smart Stack cancellation/snap fixes are preserved.
The owner authorized widget/menu polish, performance fixes, removal of the retired icon module,
and pushing both `main` and `claude/relaxed-goodall-yfi2bq` after validation.

## Implemented

- Glass shader: analytic rim normals replace four extra SDF evaluations; straight interior pixels
  skip corner powers and undisplaced pixels skip chromatic sampling. Highlights and press/morph
  springs are restrained. Blur effects are reused until radius changes.
- Menus: one 280dp panel, grouped actions, 48dp minimum rows, bounded scrolling with an indicator,
  TalkBack pane semantics, status/navigation-safe placement. Automatic white text retains its
  readability scrim even with custom accents or thicker dark glass. Contrast is sampled from the
  wallpaper average; this is not a per-pixel AA guarantee.
- Widgets: wallpaper-derived hue (manual theme/explicit role colours preserved), consistent 16dp
  padding, clearer weather/date/clock/battery hierarchy, simplified music transport controls and
  calendar rows sharing their parent glass. Third-party corners follow the configured radius.
  Photos now stay still between short crossfades rather than continuously animating.
- Rendering: cancellable CPU blur, reused scratch buffer, promptly recycled owned intermediates,
  stale blur results cleared, duplicate wallpaper preparation removed. Window blur and overlay
  animations no longer read every frame in root composition. Paused/background audio bars stop;
  identical View property assignments no longer trigger repeated invalidation.
- HOME: clears drawer/search/edit/drag/arrangement, keyboard, frozen home and app-launch depth.
  The gesture service directly signals a resumed launcher (no replay), closes its panel first,
  and otherwise delegates to system HOME. Frozen-blur visibility observes current overlay state.
- Calendar: provider deep links fall back to the standard calendar app selector; failures now
  produce feedback instead of silently doing nothing.
- Icons: removed `glassicons/`, `tools/icons/`, their Gradle/CI hooks and dead settings actions.
  Generic packs, OnePro artwork and per-app/photo overrides remain. Settings → Icon pack or Icon
  style → Individual app icons provides normalized app-name/package search. No guessed brand
  glyphs were assigned to SonyLIV, ixigo, Spencer's or DiskWala.

## Validation

- Integrated `:app:assembleRelease :app:testDebugUnitTest --offline`: successful; 157 tests,
  zero failures/errors/skips. Release lint-vital checks passed as part of assembly.
- Analytic normals compared with numerical derivatives at 46,738 rim points across four shapes;
  maximum cosine error approximately 1.7e-14 (host numerical check, not a GPU benchmark).
- Signed optimized APK installed on the owner's Redmi, keeping app data and permissions:
  `app/build/outputs/apk/release/glass-premium.apk` (local debug signing key; release/R8 build).
- ADB visual/input checks: menu below status/island with all actions visible; Instagram opens
  Instagram; bottom HOME dismisses the drawer; Music → Weather → Calendar stack settles on one
  card; Calendar opens Google Calendar; HOME dismisses Spotlight and reports keyboard hidden.
  Individual app icons opens and a SonyLIV search correctly finds Sony LIV.
- Final five-cycle menu sample (music playing, process includes overlay windows): 3,713 frames,
  median 11ms / p90 30ms, GPU median 7ms / p90 14ms, reported jank 1.02%. These mixed-window
  numbers do not establish sustained 120Hz or a controlled before/after improvement.
- No LiquidGlass shader compilation errors or AndroidRuntime crashes in the checked log window.
- All widget variants compile; Clock, Notes, Battery, Photos, every size/font setting and provider
  widget interaction have not each received a complete device test. No hand-feel or battery-life
  claim: tests used ADB, and music was playing during final device validation.

The lock-screen references inform styling only. Native HyperOS lock-screen customization remains
outside a third-party launcher's control. Existing phone layout and stack contents are preserved.
Untracked `.claude/` and `.playwright-mcp/` are local tooling and are not part of this change.

---

# Historical handoff — Phase 7 (29 Oct 2026)

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
4. **Optimisation (Phase 7b, measured on the Turbo 5, gfxinfo medians)**: wallpaper blur is now a bitmap blurred
   once (`ui/glass/BlurredCopy.kt`), and while an overlay is fully open the blurred home is a still
   (`LauncherScreen.kt` `showFrozenBlur`) instead of a live full-screen blur. Drawer GPU 12 -> 8 ms, Spotlight
   13 -> 9 ms (CPU 19 -> 16 / 23 -> 18). Idle home renders 0 frames. Remaining budget: `GlassSurface` lens+blur
   costs ~6 ms in drawer/Spotlight (drawer 8 -> 2 ms with it off); next lead is a smaller blur radius or
   downscaled backdrop for large panels, and a cheaper open animation (home blur still live while opening).
   **SL6 study (APK read-only, JADX unavailable so dex was inspected with dexdump)**: SL6 uses no window blur and no
   per-panel live blur. It blurs the wallpaper once (downscale to ~200 px tall, RenderScript radius <= 25, cached as
   `blurred.png`), and its liquid glass is an AGSL lens that refracts only a thin edge band (interior = one texture
   fetch) with a 1-sample variant and a 7-sample dispersion variant. Glass here now does the same for overlays:
   `Backdrop.preBlurred` + blurred stills (wallpaper, frozen home) so overlay panels skip the live blur
   (drawer GPU 8 -> 7 ms, Spotlight 9 -> 7 ms, CPU 13/14 ms). Experiments: lens shader off saves ~2 ms, smaller
   blur radius alone saves nothing. Remaining: live blur on home-page panels (dock/widgets), lens interior early-out
   like SL6 (`if -sd >= refractionHeight return content`), open-animation cost.
   **Tooling**: REA's JADX provider lives outside the repo: `~/tools/jadx/jadx-headless-mcp-0.7.1-all.jar` (sha256 verified
   against REA's pin). Use `JAVA_HOME=<openjdk17> REA_JADX_MCP_JAR=~/tools/jadx/jadx-headless-mcp-0.7.1-all.jar
   npx -y rea-agents@6.8.0 search-android-classes <apk> <query>` (the running MCP server needs a restart to see the env var).
   SL6 strings: `aapt2 dump strings <apk>` (English block is roughly string #2400-3750).
   **Direction (owner)**: SL6's polished feature flows are the base; Glass's island, notification panel, App Library, Today
   view and iOS status bar stay on top. Done: "Add Widget" in the widget long-press menu; picker apps collapse to a row with a
   count badge (`0b25af6`). SL6 options Glass still lacks: blur vibrancy (backdrop saturation), widget shadow blur/opacity,
   transparent widget background, darker extra pages, greyscale mode ("fine positioning" doesn't fit a cell grid).
   **Figma reference** (owner's copy of the iOS and iPadOS 27 kit, signed in, page Examples > "iPhone - System": Control
   Center, Home Screen, Quick Actions, Widgets, Lock Screen, Notifications): lock-screen notifications are near-pill frosted
   platters with a squircle app icon, semibold title, message and a right-aligned time, stacks with slivers peeking below, an
   expanded variant with content plus a glass action list. Glass's platters (24 dp, hairline) and stacks already match; the
   iOS status bar (time left, cutout, signal, wifi, white battery pill) also matches. Unchecked: Control Center, Home Screen
   Quick Actions and Widgets frames, and the Colors page.
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
