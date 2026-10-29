# Smart Launcher 6 parity checklist

Source: SL6 6.6 build 024, observed on the phone and read via REA/aapt2 strings (never copied code or art).
Rule: SL6's polished flows are the base; Glass's island, notification panel, App Library, Today and iOS status bar stay on top.
Status: [x] done, [~] partly, [ ] to do.

## Looks / appearance
- [x] Global appearance (roles, tint, opacity, blur, stroke, accent, corner radius), icon shape/size/labels, folders, home animation
- [x] Themes: Solid, Frosted, Liquid Glass, AMOLED black (Phase 7b)
- [x] Fonts: bundled + Google Fonts + System font (Phase 7b)
- [x] Liquid-glass optimisation: pre-blurred stills, frozen blurred home under overlays
- [ ] Blur vibrancy (backdrop saturation)
- [ ] Widget shadow blur/opacity, transparent widget background, apply theme/font to all widgets
- [x] Make extra pages darker
- [ ] Greyscale mode
- [ ] Letter spacing / line height / item spacing in Fonts

- [x] True Tone (Glass windows, ambient-light veil; off by default) and one notification finish for lock screen + panel

## Widgets
- [x] Picker (our widgets + app widgets), resize handles, edit sheet, widget stack, Today view
- [x] "Add Widget" in the long-press menu; picker apps collapsed with count badge
- [ ] Popup widgets (widget opens from an icon gesture)
- [ ] Assign a widget to an icon, shown on double tap
- [ ] Fine positioning (n/a on a cell grid: skip unless a nudge mode is wanted)

## Home / drawer
- [x] Pages, page manager, infinite scroll, grid properties, hidden apps, categories, auto sort, backups, Nova import
- [ ] Dock settings (icon count, size, background)
- [ ] App launch animation / window-to-icon animation choices
- [ ] Smart folders
- [ ] Private Space (Android 15+ profile) support
- [ ] Independent landscape layout

## Search
- [x] Spotlight with apps, contacts, calendar, files, calculator, unit conversion, search providers
- [ ] Blocked words, bookmarks / built-in browser, Google page

## Feeds
- [ ] RSS / news feed page (add source, manage sources, group similar articles)

## Not planned
- Missions/Academy/badges, Pro paywall, billing, analytics, crash reporting, Play Services upsell, ads.
