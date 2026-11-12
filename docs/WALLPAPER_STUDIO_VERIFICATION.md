# Wallpaper Studio verification

Date: 9 November 2026. Branch: `codex/wallpaper-studio`; baseline `250b5af`.

## Scope

Twenty paired wallpapers, four new collections, favorites, home-layout preview, crop and color
adjustments, atomic apply settings, wallpaper ownership checks, bounded decoding/cache work, and
scroll gesture fixes. See `WALLPAPER_STUDIO.md` for implementation and reproducible commands.

## Build and automated checks

- 221 JVM tests passed, zero failures/errors/skips.
- Android lint passed with existing repository warnings.
- Android 15 / API 35: all 11 instrumentation tests passed (86.131 seconds) on the final source.
- Android 12 / API 31: all 11 instrumentation tests passed (100.744 seconds) on the final source.
  An initial run was interrupted by a Google Play services emulator ANR while compiling; the
  successful rerun was isolated from builds. The slider test also needed lazy-list setup corrected.
- Optimized release and performance variants built from the feature branch.

## Physical device

Owner-authorized wireless ADB test on model 2606FRT34I, Android 16 / API 36, 1268 × 2756.
Installed 1.2 (version code 3) as an update over 1.1; no uninstall or data clear. The optimized,
non-debuggable release was signed with the local key matching the installed app. The repository's
default signing certificate differs, so its default APK cannot update this particular installation.

- Exported and validated a JSON backup before updating; retained the original APK locally.
- Preview showed the existing clock, app icons and dock. Provider widgets and smart stacks used
  placement panels, leaving the real widget hosts untouched.
- Applied Silk Flow with approximately 1.29× zoom, 12% dim and +36 warmth. Returning Home immediately
  after Apply still completed the operation. System wallpaper id advanced from 106 to 107.
- Favorites filtering included Silk Flow and excluded Lagoon. Favorite and applied adjustments
  survived process restart.
- Restored Ember/dark with automatic switching and both home/lock screens enabled; system id 108.
  Restored default adjustments and removed the test favorite.
- Repeated drawer/Back/Home navigation and search for Settings; the expected result appeared.
- Post-test backup comparison: layout.json, widgets.json, icon_overrides.json, custom icons and
  general preferences unchanged. Weather cache refreshed normally. Only wallpaper ownership id
  and newly introduced default-valued wallpaper preferences differ.
- Accessibility and notification-listener settings and recorded permission grants unchanged.
  No Glass Launcher fatal crash in the device crash buffer during these checks.

Private backups, screenshots and raw device logs remain in `/tmp/glass-studio-review/device`;
they are not committed. Do not run the destructive emulator instrumentation suite on this phone.

## Performance interpretation

Preview decoding changed from 720 × 1600 to 360 × 800: 75% fewer decoded pixels/ARGB bytes per
preview. Thumbnail retention is capped at 4 MiB. Bundled wallpaper assets increased from 816,484
bytes to 1,125,388 bytes including catalog and thumbnails.

A short three-round physical drawer smoke check recorded 264 frames / 3 janky frames on 1.1 and
256 / 3 on the first 1.2 candidate. Both had a 12 ms median; p90 was 19 ms and 23 ms respectively.
These small, uncontrolled samples are diagnostics and do not establish a speed improvement.

All three optimized macrobenchmark scenarios completed (five iterations each, Full compilation) on
API 35. Raw JSON and Perfetto traces are in `/tmp/glass-studio-review/candidate-final`; baseline
captures are in `baseline-cold`, `baseline-frames`, and the later `baseline-repeat` directories.
The original earlier baseline and candidate ran on the same 8 GB host, but its emulator performance
varied considerably across time. A same-session baseline rerun is therefore included below.

| Emulator diagnostic | Earlier baseline | Candidate | Later baseline rerun |
| --- | ---: | ---: | ---: |
| Cold start median (ms) | 1084.8 | 975.1 | Not repeated |
| Drawer CPU frame p50 (ms) | 144.1 | 292.2 | 326.3 |
| Drawer CPU frame p90 (ms) | 320.5 | 563.4 | 565.0 |
| Gallery CPU frame p50 (ms) | 244.6 | 699.7 | 612.7 |
| Gallery CPU frame p90 (ms) | 369.3 | 864.8 | 720.7 |

These are slow emulator diagnostics, not phone FPS or a performance acceptance result. Earlier
benchmark fixture failures exposed and led to fixing both preview and slider gesture interception.
The same-session candidate gallery p50 is about 14% above baseline (p90 about 20%); this remains
a physical-device smoothness check before merge. The richer gallery adds category/favorite controls;
these measurements do not establish whether that overhead or remaining host variability dominates.

## Acceptance limits

The owner still needs normal-use acceptance before merging main. A full phone reboot, sustained
battery testing, and other manufacturers are outside these checks. Automatic theme switching,
external-wallpaper ownership handling, large text and rotation are exercised on disposable emulators;
the daily-use phone's global theme, font settings and other apps' wallpapers were not changed.

## Device APK

Final slider-fix candidate: `app/build/outputs/apk/release/glass-launcher-1.2-rc2-device.apk`.
SHA-256: `409ad3be62cfe60821812d212d40db6bbaa2bdc8a365fc08f3c0bc705f12d22c`.
Matching installed certificate SHA-256: `6c49097af6524ef0cfb5f99d137dd9114cef61320ed53f147e51f87650255940`.
This is the optimized release payload, re-signed with the existing local development key for device
compatibility. It is not a Play Store production-signing migration. Future updates must retain the
same signing identity.

Wireless ADB disconnected after the first candidate was tested and restored. At this point the
phone retains that tested 1.2 candidate; installing the final slider-fix APK requires reconnection.
