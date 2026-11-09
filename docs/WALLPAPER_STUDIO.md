# Wallpaper Studio

Scope: bundled wallpaper customization and everyday rendering reliability. Work stays on
`codex/wallpaper-studio` until the release candidate has been tested on the owner's device.

## User experience

- Twenty original designs, each in light and dark, across seven collections. The new Obsidian,
  Aurora Silk, Warm Stone and Quiet Landscape collections add eight designs / sixteen images.
- Favorites persist in the existing polish DataStore and participate in JSON backup/restore.
- The studio previews the saved default home page with real app icons, folders, dock and built-in
  widgets. Third-party widgets and smart stacks show placement panels: a wallpaper preview must
  never create, reparent, resize or interact with an active provider widget host.
- Zoom, horizontal/vertical position, dim and warmth share resolution-independent crop/color
  calculations between preview and final rendering. Auto mode previews the current system variant;
  selecting Light or Dark explicitly switches to manual mode. Draft controls survive recreation.
- Apply is serialized and commits selection, adjustments, auto mode and wallpaper id together after
  system application succeeds. Once the system operation starts it completes even if Settings closes.
- Automatic light/dark switching preserves adjustments. It yields to externally changed wallpaper
  ids and does not replace a wallpaper when ownership cannot be verified.

## Performance and failure handling

- Thumbnail cache is capped at 4 MiB, measured in decoded bytes, and released under memory pressure.
  Eviction does not recycle images still held by Compose.
- Preview decodes use a quarter-size image (360 × 800); dragging adjustment sliders changes a draw
  matrix/filter instead of decoding or allocating full-size bitmaps per frame.
- Full-size renders run on IO, are capped at 1440 × 3200, and release intermediates after applying.
- Wallpaper refresh requests are conflated and processed serially. A completed stale refresh cannot
  overwrite a newer request; repeated resume/color callbacks do not decode multiple wallpapers in parallel.
- Owned wallpaper copies use atomic file writes and verified system ids. Temporary bitmap owners
  release their copies, including the pre-existing photo-picker path.
- Preview taps are intercepted without blocking parent scrolling. Sliders recognize horizontal drags,
  leaving vertical gestures available to the settings list.
- No screen capture is used for preview or glass. No wallpaper network service or new permission.

## Reproducible assets

```sh
python3 tools/wallpapers/generate_wallpapers.py --studio-only
# Preview the complete collection without overwriting production assets:
python3 tools/wallpapers/generate_wallpapers.py --scale 0.25 --output /tmp/glass-wallpaper-preview
```

Requires NumPy and Pillow. Original procedural sources are in `tools/wallpapers/`; the catalog
contains dimensions, palette samples and normalized focal positions. Scaled output cannot overwrite
bundled production files. JVM tests validate catalog integrity and asset budgets; emulator tests
fully decode every full-size image and thumbnail and verify dimensions.

## Validation commands

Use a disposable emulator: instrumentation changes wallpaper, favorites, system theme, font size and
runtime permissions. Do not run this suite on the owner's daily-use phone.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest :app:assembleRelease
```

The `performance` module measures cold startup, drawer round trips and wallpaper browsing against an
R8-optimized, locally signed target. Its manifest enables shell profiling only in the performance variant. Benchmarks use the existing
Settings entry point (also used by the standalone launcher settings icon). Emulator suppression must be explicitly supplied for diagnostic runs:

```sh
./gradlew :performance:connectedPerformanceAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Use a physical device without this suppression for performance acceptance. Android's
[Compose testing guidance](https://developer.android.com/develop/ui/compose/testing) and
[Macrobenchmark documentation](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
are the setup references. CI runs JVM tests, lint and Android 12/15 emulator tests before packaging release.

## Device acceptance before merge

Install the signed release APK as an update, retaining existing data. Check the real home preview
(including third-party placements), crop alignment, favorites after restart, light/dark switching,
lock-screen opt-in, external wallpaper changes, drawer/search/Home, and HyperOS overlay coexistence.
The owner authorized testing on the connected Android 16 phone. Install as an update using the matching local signing key; keep the feature branch separate until device acceptance. See WALLPAPER_STUDIO_VERIFICATION.md for results.
