# Review rules

1. **Glass only.** No plain Material `Surface`, `Card`, `AlertDialog`, `Scaffold`, `Snackbar`,
   `Button`, `Switch`, `Slider`, `TextField`, `Dialog`/`Popup` windows, `android.app.AlertDialog`
   or `Toast` outside `app/src/main/java/com/shivam/glasslauncher/ui/glass/`. Every visible
   surface goes through `GlassSurface` or a control in `ui/glass` (`GlassButton`, `GlassSwitch`,
   `GlassSlider`, `GlassSegmented`, `GlassTextField`, `GlassListGroup`/`GlassRow`, `GlassAlert`,
   `GlassToastHost`, ...). Regular variant for large panels, Clear for small controls.
   Enforced by `GlassOnlyPolicyTest` (runs in CI as part of `testDebugUnitTest`).
2. **Never refract yourself.** A `GlassSurface` must not use a `Backdrop` that records the
   surface itself; see the nesting notes in `LauncherScreen.kt`.
3. **No screen capture.** Never use MediaProjection or any screenshot API for glass.
4. **Constraints are binding.** Every feature follows `docs/CONSTRAINTS.md`; ⚠️/❌ rows must
   ship their fallback and tell the user in-app, and System check must list what they need.
