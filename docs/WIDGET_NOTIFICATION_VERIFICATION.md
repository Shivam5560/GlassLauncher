# Widget and notification refinement verification

Date: 29 October 2026. Branch: `codex/widgets-island-polish`, integrated with main through `657d5ff` before testing.

## Delivered behavior

- Notification Center uses a softly blurred, dimmed wallpaper copy and the shared Panel glass role. Cards show app identity, time, title, message and actions in a consistent hierarchy. App stacks, All / Alerting / Quiet filters, a live media card and an empty state share the same material.
- The scroll viewport ends above a separate flashlight / Manage / camera toolbar. Navigation and keyboard insets are applied to the whole layout. Long messages wrap; controls have accessibility labels and usable hit targets. Clear and swipe actions preserve ongoing notifications.
- A readable wallpaper is blurred once and reused by the cards. The covered app does not also receive window blur in that case. Unreadable/live wallpaper retains the existing window-blur/tinted-glass fallback. Both notification lists are lazy.
- Island notification text reserves the physical camera area. Compact text starts beside the camera; expanded app/title/message rows sit below it. Expanded height follows font scale, with an extra message line at larger sizes. Reading time is configurable.
- Automatic island mode yields to the observed native HyperOS island capability. System mode always hides Glass; Glass mode is an explicit override. Disabled state is read before overlay activation. No OEM island flags are changed.
- Lock cards are opt-in, omit secret notifications/media, and redact message text/actions. They only appear when the native lock notification list is already off. Android retains control of the keyguard.
- Quotes provides an editable Hindi default, optional attribution and Medium/Large layouts. Long quotes scroll rather than shrinking text. The user’s sentiment is written as “जो ग़म है, उसे लेकर क्यों रोते हो? जो होता है, अच्छे के लिए होता है।”
- Third-party widgets have per-instance Glass blend and background opacity. It copies and softens plain full-size container backgrounds, preserving provider text, images, charts, actions and smaller inner cards. Original backgrounds are restored before provider updates and when disabled. Standard mode retains the existing global opacity behavior. No image capture, background polling or provider-specific package patches are used.
- Checklist stores local tasks. Focus opens Clock timer requests. Quick Actions opens supported Android settings/Clock destinations. These widgets add no timer loop, polling, or background service.

## Automated checks

`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain --max-workers=2`

180 JVM tests passed, with zero failures/errors/skips. Includes checklist serialization, island coexistence, physical-camera text geometry, font-size geometry, notification filtering/privacy and the glass-only policy. Debug APK and lint passed.

## Physical-device checks

Xiaomi Android 16; 1268 × 2756; density override 416. APK installed as an update, preserving app data.

- Notification panel: normal and 150% font sizes; long sample title/message; app stack expansion; dismissal of the sample only; Quiet filter; media display; separated bottom toolbar and panel dismissal. Existing user notifications were retained. Manage opened the HyperOS notification app list successfully.
- Island: expanded sample below the cutout and compact text beside it; Automatic/native coexistence; Off persisted across a force-stop and relaunch. Normal font size restored and Automatic mode selected after testing.
- Widget picker: Checklist, Focus and Quick Actions previews inspected on the device. The requested Hindi quote was added to Today view, edited/saved and verified in the store after a force-stop/relaunch. Medium/Large previews render Devanagari correctly. No test-only widgets were left on the home grid.
- LinkedIn and AI Usage: enabled Glass blend through Edit Widget, inspected the resulting cards, refreshed AI Usage, and verified both choices survived a process restart. Both are left enabled at the default 25% background opacity. Image/custom-drawn provider backgrounds are deliberately unchanged.
- Accessibility service bound after the update/restart checks; no crashed services or AndroidRuntime fatal errors observed during the checks.

Screenshots and device backup remain local under `/tmp/glass-widget-review/device`; notification screenshots are not committed because they contain device content.

## Remaining verification limits

A full phone reboot was not performed. Private lock-card rendering, end-to-end inline reply, and end-to-end Checklist/Focus/Quick Actions interactions were not exercised on the physical phone. Their implementation is compiled and relevant data/geometry/privacy policies have JVM coverage. No measured frame-time or battery improvement is claimed. Live-wallpaper/no-wallpaper fallback, other OEMs, landscape and font sizes above 150% still need device coverage.

## Platform references

Android’s [AppWidgetHostView API](https://developer.android.com/reference/android/appwidget/AppWidgetHostView) supplies the hosted RemoteViews lifecycle; [widget guidance](https://developer.android.com/develop/ui/views/appwidgets) describes provider-owned layouts. Glass blend is a conservative opt-in host treatment, not a universal provider theme API. The Manage shortcut uses [ACTION_ALL_APPS_NOTIFICATION_SETTINGS](https://developer.android.com/reference/android/provider/Settings#ACTION_ALL_APPS_NOTIFICATION_SETTINGS) on API 33+, with system Settings as fallback.
