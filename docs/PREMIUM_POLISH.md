# Premium launcher polish

Worktree: `../launcher-premium`

Branch: `codex/premium-launcher`

Base: `main` at `a37c848`

## Experience

- A searchable settings overview with a wallpaper-and-icon appearance preview, quick access to wallpapers and icon packs, and clearly separated everyday, appearance, navigation and privacy sections.
- Settings search includes the names of nested controls, so searches such as “badges”, “True Tone” and “parallax” lead to Home screen. Empty results explain what to try next. The keyboard search action opens the top result.
- Home preferences have their own screen. Existing options and persistence remain intact. Advanced surface editing is opt-in on Appearance, below common controls.
- Settings navigation and scroll position survive screen changes and activity recreation. Every page has an explicit back or close button and a compact title while scrolling.
- Calmer shared rows: toned icon wells, consistent spacing, finer dividers, readable secondary text, accessible switch/slider/segment state and larger interaction areas. Custom presets no longer place the selection indicator outside the segmented control.
- Home search has a larger target and an expanding active-page indicator. Spotlight uses shared glass controls, explicit close/clear actions and Glass-specific suggestions. Drawer controls are labelled and show item counts.
- Activity access uses Compose's activity provider. The shader creation path explicitly guards Android 13 APIs, preserving the Android 12 frosted fallback.

## Review

Build with JDK 17 and an Android SDK containing API 35:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Suggested interaction checks:

1. Open Settings; try the appearance card, Wallpapers and Icon packs.
2. Search for “badges”, open Home screen, change a switch and return. Check that the query and position survive.
3. Search for an unknown phrase and clear it. Scroll the overview, open a page and go back.
4. Open Appearance. Try each material, a Custom theme and the advanced surface editor.
5. Use a large system font; check search, rows, segmented controls and the appearance card.
6. Open Spotlight from Home; type, clear, close and use Back. Open the drawer and change categories.
7. Check light and dark wallpapers and the Android 12 frosted fallback on a device.

No settings migrations, network services or new dependencies are introduced. Device-specific HyperOS gestures and Android 12 shader fallback require device verification.

## Verification result

- Debug APK assembled successfully.
- All 163 JVM tests passed, including six settings-search tests.
- Android lint completed successfully (existing warnings remain).
- Physical-device smoke testing completed on 2026-10-29: Android 16, model 2606FRT34I, 1268 × 2756 display at the user's 416 dpi setting. The debug APK was installed as an update, preserving the existing launcher data.
- Verified the overview, nested settings search, clear and empty states, row/switch toggling, restored search and scroll position, wallpaper gallery, icon packs and advanced appearance controls.
- Verified appearance at 150% font scale and navigation after activity recreation. Restored font scale to 100% and app labels to their original off state.
- Verified Home → Spotlight, Calculator launch, clear/close actions, drawer category swipes, menu dismissal and drawer → Spotlight → Home. Returned the phone to Home and the drawer to its original Internet category.
- Device testing exposed a Fade-transition hit-testing bug: fully transparent neighbouring category pages were translated over the visible page, so tapping More could launch an unrelated app. Fully faded pages now remain off-screen. Repeated More/Search tests on Media and Internet confirmed the fix.
- No launcher fatal exceptions or ANRs were found in the captured app logs. Build, 163 JVM tests and lint passed again after the fix.
- This is a focused smoke test with the existing wallpaper, theme, icon pack and widgets. Android 12 fallback rendering and every permission/gesture combination were not re-tested.
