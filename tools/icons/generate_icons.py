#!/usr/bin/env python3
"""Generates the Glass Icons pack: original iOS 26-style vector icons in three variants.

Every glyph below is drawn from geometric primitives in a 100x100 box. Nothing is traced from,
or derived from, any official artwork or logo file. Third-party apps get simplified, stylised
glyphs in our own style.

Output (all committed; re-run after editing):
  glassicons/src/main/res/drawable/<name>.xml         light: coloured glass tile, light glyph
  glassicons/src/main/res/drawable/<name>_dark.xml    dark: dark glass tile, coloured glyph
  glassicons/src/main/res/drawable/<name>_tinted.xml  tinted: white monochrome glyph only, no tile
  glassicons/src/main/res/drawable/iconback|iconmask|iconupon(.xml, _dark.xml)
  glassicons/src/main/res/xml/appfilter.xml, glassicons/src/main/assets/appfilter.xml
  glassicons/src/main/res/xml/drawable.xml
  glassicons/src/main/res/values/icon_labels.xml

Variant convention (documented in README and read by Glass Launcher's IconPack loader):
  `<name>_dark`   - used when the launcher's icon style is Dark (or Auto in system dark mode)
  `<name>_tinted` - a monochrome glyph on transparent; the launcher draws its own dark tile and
                    tints the glyph with the theme accent.
Launchers that don't know the convention just use `<name>` from appfilter.xml.

Usage: python3 tools/icons/generate_icons.py [--preview DIR]
"""
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "glassicons", "src", "main", "res")
ASSETS = os.path.join(ROOT, "glassicons", "src", "main", "assets")


def n(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---- primitives (SVG path syntax, which Android's PathParser also reads) -----------------

def circle(cx, cy, r):
    return f"M{n(cx - r)},{n(cy)}a{n(r)},{n(r)} 0 1,1 {n(2 * r)},0a{n(r)},{n(r)} 0 1,1 {n(-2 * r)},0Z"


def ellipse(cx, cy, rx, ry):
    return f"M{n(cx - rx)},{n(cy)}a{n(rx)},{n(ry)} 0 1,1 {n(2 * rx)},0a{n(rx)},{n(ry)} 0 1,1 {n(-2 * rx)},0Z"


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (f"M{n(x + r)},{n(y)}H{n(x + w - r)}Q{n(x + w)},{n(y)} {n(x + w)},{n(y + r)}"
            f"V{n(y + h - r)}Q{n(x + w)},{n(y + h)} {n(x + w - r)},{n(y + h)}"
            f"H{n(x + r)}Q{n(x)},{n(y + h)} {n(x)},{n(y + h - r)}V{n(y + r)}Q{n(x)},{n(y)} {n(x + r)},{n(y)}Z")


def poly(*pts):
    return "M" + "L".join(f"{n(x)},{n(y)}" for x, y in pts) + "Z"


def line(*pts):
    return "M" + "L".join(f"{n(x)},{n(y)}" for x, y in pts)


def arc(cx, cy, r, a0, a1):
    """Stroke arc from angle a0 to a1 (degrees, 0 = right, clockwise in screen space)."""
    if a1 - a0 > 180:
        mid = (a0 + a1) / 2
        return arc(cx, cy, r, a0, mid) + _arc_cont(cx, cy, r, mid, a1)
    x0, y0 = cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M{n(x0)},{n(y0)}A{n(r)},{n(r)} 0 {large},1 {n(x1)},{n(y1)}"


def _arc_cont(cx, cy, r, a0, a1):
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    return f"A{n(r)},{n(r)} 0 0,1 {n(x1)},{n(y1)}"


def superellipse(cx=50, cy=50, r=50, e=5.0, steps=96):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        c, s = math.cos(t), math.sin(t)
        x = cx + r * math.copysign(abs(c) ** (2 / e), c)
        y = cy + r * math.copysign(abs(s) ** (2 / e), s)
        pts.append((x, y))
    return poly(*pts)


def gear(cx, cy, ro, ri, teeth, hole):
    pts = []
    steps = teeth * 4
    for i in range(steps):
        a = 2 * math.pi * i / steps - math.pi / 2
        r = ro if (i % 4) in (0, 1) else ri
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return poly(*pts) + circle(cx, cy, hole)


def sparkle(cx, cy, s):
    return (f"M{n(cx)},{n(cy - s)}Q{n(cx)},{n(cy)} {n(cx + s)},{n(cy)}Q{n(cx)},{n(cy)} {n(cx)},{n(cy + s)}"
            f"Q{n(cx)},{n(cy)} {n(cx - s)},{n(cy)}Q{n(cx)},{n(cy)} {n(cx)},{n(cy - s)}Z")


def handset(ox=0, oy=0, s=1.0):
    def p(x, y):
        return f"{n(ox + x * s)},{n(oy + y * s)}"
    return (f"M{p(31, 22)}C{p(35, 20)} {p(39, 21)} {p(41, 25)}L{p(46, 34)}C{p(48, 37)} {p(47, 41)} {p(44, 43)}"
            f"L{p(41, 45)}C{p(44, 52)} {p(48, 56)} {p(55, 59)}L{p(57, 56)}C{p(59, 53)} {p(63, 52)} {p(66, 54)}"
            f"L{p(75, 59)}C{p(79, 61)} {p(80, 65)} {p(78, 69)}L{p(75, 74)}C{p(72, 78)} {p(67, 80)} {p(61, 78)}"
            f"C{p(42, 72)} {p(28, 58)} {p(22, 39)}C{p(20, 33)} {p(23, 27)} {p(31, 22)}Z")


def rupee(cx, cy, s):
    """Stroke path for a rupee sign centred on (cx, cy)."""
    x, y = cx - 9 * s, cy - 12 * s
    def p(a, b):
        return f"{n(x + a * s)},{n(y + b * s)}"
    return (f"M{p(0, 0)}H{n(x + 18 * s)}M{p(0, 7)}H{n(x + 18 * s)}"
            f"M{p(3, 0)}Q{p(13, 0)} {p(13, 7)}Q{p(13, 14)} {p(3, 14)}H{n(x + 1 * s)}L{p(15, 25)}")


# Elements: (kind, pathData, role, opts)
#   kind: "f" fill (opts may set evenodd) or "s" stroke (opts["w"] width, "cap", "join")
#   role: "fg" main glyph colour, "fg2" main colour at 55% alpha, or "#RRGGBB" fixed colour
#   opts["ta"]: alpha in the tinted variant (default 1, or 0.55 for fg2 / fixed colours)

def F(d, role="fg", **o):
    return ("f", d, role, o)


def S(d, w, role="fg", **o):
    o["w"] = w
    return ("s", d, role, o)


TILE = superellipse()
TILE_RIM = superellipse(r=49.4)


class Icon:
    def __init__(self, name, label, tile, accent, elements, components, fg="#FFFFFF", dark_tile=None):
        self.name = name
        self.label = label
        self.tile = tile            # (top, bottom) light tile gradient; a third value = mid stop
        self.accent = accent        # (hi, lo) glyph gradient on the dark tile
        self.fg = fg                # glyph colour on the light tile
        self.elements = elements
        self.components = components
        self.dark_tile = dark_tile


WHITE_TILE = ("#FFFFFF", "#E9EAEE")
DARK_GLASS = ("#2F3034", "#121214")

ICONS = [
    # ---------------------------------------------------------------- system apps
    Icon("phone", "Phone", ("#5EE07A", "#1FB54A"), ("#7CF29A", "#2FCB5B"), [F(handset())], [
        "com.android.contacts/com.android.contacts.activities.TwelveKeyDialer",
        "com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity",
        "com.android.dialer/com.android.dialer.main.impl.MainActivity",
        "com.android.dialer/com.android.dialer.DialtactsActivity",
    ]),
    Icon("messages", "Messages", ("#64E58A", "#22B957"), ("#80F0A2", "#2CC861"), [
        F(ellipse(50, 47, 29, 23.5)), F("M30,60Q28,71 20,77Q34,78 42,68Z"),
    ], [
        "com.android.mms/com.android.mms.ui.MmsTabActivity",
        "com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity",
        "com.android.messaging/com.android.messaging.ui.conversationlist.ConversationListActivity",
    ]),
    Icon("camera", "Camera", ("#C9CCD3", "#7D828C"), ("#E6E8EE", "#A3A8B2"), [
        F(rrect(18, 33, 64, 42, 10) + rrect(37, 25, 26, 12, 4) + circle(50, 54, 14.5), evenodd=True),
        F(circle(50, 54, 8.5)), F(circle(71, 42, 2.8), "fg2"),
    ], [
        "com.android.camera/com.android.camera.Camera",
        "com.google.android.GoogleCamera/com.android.camera.CameraLauncher",
        "org.codeaurora.snapcam/com.android.camera.CameraLauncher",
    ]),
    Icon("gallery", "Gallery", ("#FFB56B", "#FF5F7E"), ("#FFC78A", "#FF7A8F"), [
        F(circle(65, 35, 8), "#FFE36B", ta=0.6),
        F(poly((18, 76), (40, 44), (53, 61), (62, 51), (82, 76))),
    ], [
        "com.miui.gallery/com.miui.gallery.activity.HomePageActivity",
        "com.google.android.apps.photos/com.google.android.apps.photos.home.HomeActivity",
        "com.android.gallery3d/com.android.gallery3d.app.GalleryActivity",
    ]),
    Icon("settings", "Settings", ("#B4B8C1", "#6C717C"), ("#D6D9E0", "#9499A4"), [
        F(gear(50, 50, 30, 23.5, 9, 9), evenodd=True),
        S(circle(50, 50, 15.5), 3, "fg2"),
    ], [
        "com.android.settings/com.android.settings.Settings",
        "com.android.settings/com.android.settings.MainSettings",
        "com.android.settings/com.android.settings.HWSettings",
    ]),
    Icon("clock", "Clock", ("#3C3D42", "#141417"), ("#FFFFFF", "#C7C7CC"), [
        S(circle(50, 50, 28), 5),
        S(line((50, 50), (50, 32)), 5, cap="round"),
        S(line((50, 50), (63, 58)), 4, cap="round"),
        S(line((50, 50), (37, 66)), 2, "#FF9F2E", cap="round", ta=0.7),
        F(circle(50, 50, 4), "#FF9F2E", ta=1),
    ], [
        "com.android.deskclock/com.android.deskclock.DeskClockTabActivity",
        "com.android.deskclock/com.android.deskclock.DeskClock",
        "com.google.android.deskclock/com.android.deskclock.DeskClock",
    ]),
    Icon("calendar", "Calendar", WHITE_TILE, ("#FF6B60", "#E8352B"), [
        F(rrect(22, 22, 56, 14, 6), "#FF4B3E", ta=0.7),
        F(rrect(22, 40, 56, 38, 8) + "".join(rrect(29 + c * 15, 46 + r * 15, 9, 8, 2.5)
                                             for r in range(2) for c in range(3)), evenodd=True),
    ], [
        "com.xiaomi.calendar/com.android.calendar.homepage.AllInOneActivity",
        "com.android.calendar/com.android.calendar.homepage.AllInOneActivity",
        "com.android.calendar/com.android.calendar.AllInOneActivity",
        "com.google.android.calendar/com.android.calendar.AllInOneActivity",
    ], fg="#3A3A3E"),
    Icon("calculator", "Calculator", ("#4A4B50", "#1A1A1D"), ("#FFB340", "#FF8A00"), [
        F(rrect(24, 20, 52, 15, 5), "fg2"),
    ] + [F(circle(32 + c * 18, 50 + r * 15, 5.5)) for r in range(3) for c in range(3) if (r, c) != (2, 2)] + [
        F(circle(68, 80, 5.5), "#FF9F0A", ta=0.6),
    ], [
        "com.miui.calculator/com.miui.calculator.cal.CalculatorActivity",
        "com.google.android.calculator/com.android.calculator2.Calculator",
        "com.android.calculator2/com.android.calculator2.Calculator",
    ]),
    Icon("files", "Files", ("#5CB8FF", "#1A7BF0"), ("#7CC8FF", "#2E8CF5"), [
        F("M18,32Q18,26 24,26H39L45,32H76Q82,32 82,38V68Q82,74 76,74H24Q18,74 18,68Z"
          + rrect(18, 40, 64, 2.6, 1.3), evenodd=True),
    ], [
        "com.mi.android.globalFileexplorer/com.android.fileexplorer.FileExplorerTabActivity",
        "com.android.fileexplorer/com.android.fileexplorer.activity.FileActivity",
        "com.google.android.apps.nbu.files/com.google.android.apps.nbu.files.home.HomeActivity",
        "com.google.android.documentsui/com.android.documentsui.LauncherActivity",
    ]),
    Icon("notes", "Notes", ("#FFD95A", "#FFA81E"), ("#FFE27A", "#FFB22E"), [
        F(rrect(26, 22, 48, 56, 8) + rrect(33, 36, 34, 3.6, 1.8) + rrect(33, 47, 34, 3.6, 1.8)
          + rrect(33, 58, 22, 3.6, 1.8), evenodd=True),
    ], [
        "com.miui.notes/com.miui.notes.ui.NotesListActivity",
        "com.google.android.keep/com.google.android.keep.activities.BrowseActivity",
    ]),
    Icon("weather", "Weather", ("#5AB6FF", "#2366E6"), ("#8FD0FF", "#4A8CF0"), [
        F(circle(39, 40, 13), "#FFD84A", ta=0.55),
        F(circle(46, 60, 11) + circle(60, 53, 14) + circle(71, 62, 9) + rrect(35, 60, 45, 11, 5.5)),
    ], [
        "com.miui.weather2/com.miui.weather2.ActivityWeatherMain",
        "com.google.android.apps.weather/com.google.android.apps.weather.home.HomeActivity",
    ]),
    Icon("maps", "Maps", ("#7EE0A6", "#22A879"), ("#9BEFC0", "#35C08C"), [
        F(poly((18, 30), (38, 22), (38, 70), (18, 78))),
        F(poly((41, 22), (59, 30), (59, 78), (41, 70)), "fg2"),
        F(poly((62, 30), (82, 22), (82, 70), (62, 78))),
    ], [
        "app.organicmaps/app.organicmaps.SplashActivity",
        "com.mapswithme.maps.pro/com.mapswithme.maps.SplashActivity",
        "com.huawei.maps.app/com.huawei.maps.app.MainActivity",
    ]),
    Icon("mail", "Mail", ("#62B4FF", "#1F6FEA"), ("#86C6FF", "#3486F2"), [
        F(rrect(18, 28, 64, 44, 9) + poly((19, 33), (50, 55), (81, 33), (81, 39), (50, 61), (19, 39)), evenodd=True),
    ], [
        "com.android.email/com.android.email.activity.Welcome",
        "com.google.android.email/com.android.email.activity.Welcome",
    ]),
    Icon("music", "Music", ("#FF7A92", "#F0284F"), ("#FF94A6", "#FF4567"), [
        F(circle(36, 70, 8.5) + circle(66, 64, 8.5)),
        F(rrect(40.5, 30, 5, 41, 2) + rrect(70.5, 24, 5, 41, 2)),
        F(poly((40.5, 30), (75.5, 22), (75.5, 31), (40.5, 39))),
    ], [
        "com.miui.player/com.miui.player.ui.MusicBrowserActivity",
        "com.miui.player/com.tencent.qqmusiclite.activity.MainActivity",
        "com.android.music/com.android.music.MusicBrowserActivity",
    ]),
    Icon("browser", "Browser", ("#5FC8FF", "#2A6CF0"), ("#83D6FF", "#3F84F5"), [
        S(circle(50, 50, 27), 5), S(ellipse(50, 50, 11, 27), 4), S(line((23, 50), (77, 50)), 4),
        S("M27,37Q50,43 73,37", 3, "fg2"), S("M27,63Q50,57 73,63", 3, "fg2"),
    ], [
        "com.mi.globalbrowser/com.android.browser.BrowserActivity",
        "com.android.browser/com.android.browser.BrowserActivity",
    ]),
    Icon("contacts", "Contacts", ("#C3A88E", "#86664D"), ("#E3C9AE", "#B08F72"), [
        F(circle(50, 38, 12)), F("M26,76Q26,54 50,54Q74,54 74,76Z"),
    ], [
        "com.android.contacts/com.android.contacts.activities.PeopleActivity",
        "com.google.android.contacts/com.android.contacts.activities.PeopleActivity",
    ]),
    Icon("recorder", "Recorder", ("#FF6A5E", "#E2281E"), ("#FF8478", "#FF453A"), [
        S(line((26 + i * 8, 50 - h / 2), (26 + i * 8, 50 + h / 2)), 5, cap="round")
        for i, h in enumerate([10, 22, 36, 46, 30, 18, 26])
    ], [
        "com.android.soundrecorder/com.android.soundrecorder.SoundRecorder",
        "com.miui.soundrecorder/com.android.soundrecorder.SoundRecorder",
        "com.google.android.apps.recorder/com.google.android.apps.recorder.ui.MainActivity",
    ]),
    Icon("compass", "Compass", ("#4A4C52", "#18191C"), ("#F2F2F7", "#AEAEB2"), [
        S(circle(50, 50, 28), 4),
        F(poly((50, 25), (58, 50), (42, 50)), "#FF5147", ta=1),
        F(poly((42, 50), (58, 50), (50, 75)), "fg2"),
    ], [
        "com.miui.compass/com.miui.compass.CompassActivity",
    ]),
    Icon("security", "Security", ("#4FE0B0", "#16A57A"), ("#6EF0C4", "#25BE8D"), [
        F("M50,19L77,28V47C77,64 66,76 50,82C34,76 23,64 23,47V28Z"
          + poly((37, 50), (46, 59), (64, 41), (68, 45.5), (46, 67), (33, 54.5)), evenodd=True),
    ], [
        "com.miui.securitycenter/com.miui.securityscan.MainActivity",
        "com.miui.securitycenter/com.miui.securitycenter.Main",
    ]),
    Icon("themes", "Themes", ("#B98CFF", "#6A3DF0"), ("#C9A6FF", "#8657F5"), [
        F(circle(50, 50, 29) + circle(38, 37, 5) + circle(55, 31, 5) + circle(68, 44, 5) + circle(43, 63, 7),
          evenodd=True),
    ], [
        "com.android.thememanager/com.android.thememanager.ThemeResourceTabActivity",
        "com.android.thememanager/com.android.thememanager.HomeActivity",
    ]),
    Icon("store", "App Store", ("#FFA45A", "#FF6A1E"), ("#FFB676", "#FF8236"), [
        S("M38,40V34A12,12 0 0,1 62,34V40", 5, cap="round"),
        F(rrect(23, 38, 54, 40, 9) + poly((44, 50), (56, 50), (56, 58), (62, 58), (50, 69), (38, 58), (44, 58)),
          evenodd=True),
    ], [
        "com.xiaomi.mipicks/com.xiaomi.market.ui.MarketTabActivity",
        "com.xiaomi.market/com.xiaomi.market.ui.MarketTabActivity",
    ]),
    Icon("downloads", "Downloads", ("#4DD5E8", "#1D8DD6"), ("#70E4F2", "#33A6E4"), [
        F(poly((44, 20), (56, 20), (56, 44), (67, 44), (50, 61), (33, 44), (44, 44))),
        S("M24,60V68Q24,75 31,75H69Q76,75 76,68V60", 5.5, cap="round", join="round"),
    ], [
        "com.android.providers.downloads.ui/com.android.providers.downloads.ui.DownloadList",
        "com.android.documentsui/com.android.documentsui.files.FilesActivity",
    ]),
    Icon("scanner", "Scanner", ("#6CA2FF", "#3555E8"), ("#8DB8FF", "#4A6DF2"), [
        S(line((24, 36), (24, 24), (36, 24)), 5, cap="round", join="round"),
        S(line((64, 24), (76, 24), (76, 36)), 5, cap="round", join="round"),
        S(line((76, 64), (76, 76), (64, 76)), 5, cap="round", join="round"),
        S(line((36, 76), (24, 76), (24, 64)), 5, cap="round", join="round"),
        S(line((32, 50), (68, 50)), 4, "fg2", cap="round"),
    ], [
        "com.xiaomi.scanner/com.xiaomi.scanner.app.ScanActivity",
    ]),
    Icon("fitness", "Fitness", ("#FF7FA0", "#F23A68"), ("#FF97B1", "#FF5580"), [
        S("M50,76C30,63 21,52 21,40C21,31 28,24 36,24C42,24 47,27 50,32C53,27 58,24 64,24C72,24 79,31 79,40"
          "C79,52 70,63 50,76Z", 5, join="round"),
        S(line((32, 50), (41, 50), (46, 40), (53, 61), (58, 50), (68, 50)), 4, cap="round", join="round"),
    ], [
        "com.mi.health/com.mi.health.main.MainActivity",
        "com.xiaomi.wearable/com.xiaomi.wearable.app.splash.SplashActivity",
        "com.google.android.apps.fitness/com.google.android.apps.fitness.welcome.WelcomeActivity",
    ]),
    Icon("wallet", "Wallet", ("#3E4A5C", "#151B26"), ("#7FD6FF", "#3D9CF0"), [
        F(rrect(27, 22, 46, 14, 5), "#5CC8FF", ta=0.45),
        F(rrect(23, 29, 54, 14, 5), "#FFC23D", ta=0.65),
        F(rrect(19, 38, 62, 40, 9) + circle(68, 58, 4.5), evenodd=True),
    ], [
        "com.mipay.wallet.in/com.mipay.wallet.ui.MipayEntryActivity",
        "com.google.android.apps.walletnfcrel/com.google.commerce.tapandpay.android.wallet.WalletActivity",
    ]),
    Icon("video", "Video", ("#8A7CFF", "#4C3BE8"), ("#A497FF", "#6656F2"), [
        F(circle(50, 50, 28) + "M44,37L66,50L44,63Z", evenodd=True),
    ], [
        "com.miui.videoplayer/com.miui.video.global.app.LauncherActivity",
        "com.miui.videoplayer/com.miui.videoplayer.Main",
        "com.google.android.videos/com.google.android.videos.GoogleTvEntryPoint",
    ]),
    Icon("cleaner", "Cleaner", ("#5BE3D0", "#12A8A0"), ("#7DF0E0", "#25C0B5"), [
        F(sparkle(44, 56, 21)), F(sparkle(67, 30, 11), "fg2"), F(sparkle(70, 66, 6), "fg2"),
    ], [
        "com.miui.cleaner/com.miui.cleaner.MainActivity",
        "com.miui.cleanmaster/com.miui.optimizecenter.MainActivity",
    ]),
    Icon("screenrecorder", "Screen Recorder", ("#4A4B50", "#18181B"), ("#FF6B60", "#FF3B30"), [
        S(circle(50, 50, 26), 5), F(circle(50, 50, 13), "#FF453A", ta=0.8),
    ], [
        "com.miui.screenrecorder/com.miui.screenrecorder.activity.ScreenRecorderActivity",
    ]),
    Icon("smarthome", "Home", ("#FFC55A", "#FF8A1E"), ("#FFD27A", "#FF9C36"), [
        F("M50,21L81,46H73V77H27V46H19Z" + rrect(43, 55, 14, 22, 4), evenodd=True),
    ], [
        "com.xiaomi.smarthome/com.xiaomi.smarthome.SmartHomeMainActivity",
        "com.google.android.apps.chromecast.app/com.google.android.apps.chromecast.app.DiscoveryActivity",
    ]),
    Icon("remote", "Remote", ("#7C8BA6", "#3A465E"), ("#A9B7D0", "#6A7894"), [
        F(rrect(35, 17, 30, 66, 13) + circle(50, 34, 7.5) + circle(43.5, 53, 2.8) + circle(56.5, 53, 2.8)
          + circle(43.5, 63, 2.8) + circle(56.5, 63, 2.8), evenodd=True),
    ], [
        "com.duokan.phone.remotecontroller/com.xiaomi.mitv.phone.remotecontroller.HoriWidgetMainActivityV2",
    ]),
    Icon("radio", "Radio", ("#FF8E5A", "#E8452A"), ("#FFA678", "#FF6040"), [
        S(line((30, 36), (66, 21)), 4, cap="round"),
        F(rrect(19, 37, 62, 38, 9) + circle(38, 56, 10) + rrect(56, 47, 17, 4, 2) + rrect(56, 55, 17, 4, 2)
          + rrect(56, 63, 17, 4, 2), evenodd=True),
    ], [
        "com.miui.fm/com.miui.fm.FmMainActivity",
        "com.android.fmradio/com.android.fmradio.FmMainActivity",
    ]),

    # ---------------------------------------------------------------- third-party (stylised, our own)
    Icon("whatsapp", "WhatsApp", ("#55E68A", "#16AE52"), ("#6EF29C", "#22C462"), [
        S(arc(51, 48, 25, 135, 495), 5, cap="round"),
        F(poly((30, 62), (24, 79), (41, 71))),
        F(handset(29.5, 27, 0.42)),
    ], [
        "com.whatsapp/com.whatsapp.Main",
        "com.whatsapp.w4b/com.whatsapp.Main",
    ]),
    Icon("instagram", "Instagram", ("#FFB547", "#E2337E", "#8A3CF0"), ("#FF8FB8", "#C24DF0"), [
        S(rrect(23, 23, 54, 54, 16), 5.5), S(circle(50, 50, 12.5), 5.5), F(circle(66.5, 33.5, 3.6)),
    ], [
        "com.instagram.android/com.instagram.mainactivity.LauncherActivity",
        "com.instagram.android/com.instagram.android.activity.MainTabActivity",
    ]),
    Icon("youtube", "YouTube", WHITE_TILE, ("#FF5A4E", "#F01E1E"), [
        F(rrect(17, 29, 66, 42, 13) + "M44,39L61,50L44,61Z", evenodd=True),
    ], [
        "com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell$HomeActivity",
        "com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
    ], fg="#F72A2A"),
    Icon("spotify", "Spotify", ("#3DE27A", "#14A84A"), ("#5CF090", "#1ED760"), [
        S("M27,40Q50,30 73,42", 6.5, cap="round"),
        S("M30,53Q50,45 70,55", 5.5, cap="round"),
        S("M33,65Q50,59 66,67", 4.5, cap="round"),
    ], [
        "com.spotify.music/com.spotify.music.MainActivity",
    ]),
    Icon("gmail", "Gmail", WHITE_TILE, ("#FF6B5E", "#EA3B2E"), [
        S(line((25, 72), (25, 34)), 8, "#3B82F6", cap="round", ta=0.7),
        S(line((75, 72), (75, 34)), 8, "#22B357", cap="round", ta=0.7),
        S(line((25, 34), (50, 54), (75, 34)), 8, "#F0443A", cap="round", join="round"),
    ], [
        "com.google.android.gm/com.google.android.gm.ConversationListActivityGmail",
    ], fg="#F0443A"),
    Icon("chrome", "Chrome", WHITE_TILE, ("#5AA0FF", "#2C6FEA"), [
        S(arc(50, 50, 24, -150, -30), 11, "#F04A3E", ta=0.75),
        S(arc(50, 50, 24, -30, 90), 11, "#FFC530", ta=0.55),
        S(arc(50, 50, 24, 90, 210), 11, "#26B45A", ta=0.9),
        F(circle(50, 50, 9.5), "#2F7BF2", ta=1),
    ], [
        "com.android.chrome/com.google.android.apps.chrome.Main",
        "com.chrome.beta/com.google.android.apps.chrome.Main",
    ], fg="#2F7BF2"),
    Icon("googlemaps", "Maps", ("#E9F7EE", "#CFE6FF"), ("#FF6B5E", "#EA3B2E"), [
        F("M50,19C37.5,19 28,28.5 28,41C28,57 50,81 50,81C50,81 72,57 72,41C72,28.5 62.5,19 50,19Z"
          + circle(50, 41, 8.5), evenodd=True),
    ], [
        "com.google.android.apps.maps/com.google.android.maps.MapsActivity",
    ], fg="#F0473A"),
    Icon("telegram", "Telegram", ("#5CC6F5", "#1E8AD6"), ("#7FD4F8", "#2E9EE6"), [
        F(poly((20, 48), (78, 25), (67, 75), (50, 62), (43, 72), (41, 57), (67, 34), (36, 54))),
    ], [
        "org.telegram.messenger/org.telegram.ui.LaunchActivity",
        "org.telegram.messenger.web/org.telegram.ui.LaunchActivity",
    ]),
    Icon("x", "X", ("#36373B", "#0B0B0C"), ("#FFFFFF", "#C7C7CC"), [
        S(line((31, 27), (69, 73)), 8, cap="round"), S(line((69, 27), (31, 73)), 4.5, cap="round"),
    ], [
        "com.twitter.android/com.twitter.android.StartActivity",
    ]),
    Icon("facebook", "Facebook", ("#4E90FF", "#1559D6"), ("#72A6FF", "#2F72F0"), [
        S("M61,26H55Q45,26 45,36V77", 8.5, cap="round", join="round"),
        S(line((34, 50), (59, 50)), 7.5, cap="round"),
    ], [
        "com.facebook.katana/com.facebook.katana.LoginActivity",
        "com.facebook.lite/com.facebook.lite.MainActivity",
    ]),
    Icon("netflix", "Netflix", ("#2A2A2D", "#0A0A0B"), ("#FF4B52", "#E01E28"), [
        S(line((36, 25), (36, 75)), 9, cap="butt"),
        S(line((64, 25), (64, 75)), 9, cap="butt"),
        S(line((36, 25), (64, 75)), 9.5, "#B5121B", cap="butt", ta=0.8),
    ], [
        "com.netflix.mediaclient/com.netflix.mediaclient.ui.launch.UIWebViewActivity",
    ], fg="#F2333C"),
    Icon("amazon", "Amazon", ("#3B4D66", "#151E2C"), ("#FFFFFF", "#D1D5DB"), [
        F(rrect(30, 24, 40, 28, 5) + rrect(45, 24, 10, 10, 2), evenodd=True),
        S("M24,60Q50,77 74,60", 5.5, "#FFA41C", cap="round", ta=0.8),
        F(poly((68, 55), (78, 57), (73, 65)), "#FFA41C", ta=0.8),
    ], [
        "in.amazon.mShop.android.shopping/com.amazon.mShop.home.HomeActivity",
        "com.amazon.mShop.android.shopping/com.amazon.mShop.home.HomeActivity",
    ]),
    Icon("paytm", "Paytm", ("#36C3F5", "#123E8C"), ("#5CD3FA", "#2E86E6"), [
        S(circle(50, 50, 26), 5), S(rupee(50, 51, 1.15), 4, cap="round", join="round"),
    ], [
        "net.one97.paytm/net.one97.paytm.AJRJarvisSplash",
        "net.one97.paytm/net.one97.paytm.landingpage.activity.AJRMainActivity",
    ]),
    Icon("phonepe", "PhonePe", ("#8E5BF0", "#4B1FAE"), ("#B08AFF", "#7A4CF0"), [
        S(rrect(31, 18, 38, 64, 10), 5), S(rupee(50, 52, 0.95), 3.8, cap="round", join="round"),
    ], [
        "com.phonepe.app/com.phonepe.app.ui.activity.Navigator_MainActivity",
    ]),
    Icon("gpay", "Google Pay", WHITE_TILE, ("#5AA0FF", "#2C6FEA"), [
        F(rrect(22, 28, 56, 9, 4.5), "#2F7BF2", ta=1),
        F(rrect(22, 41, 56, 9, 4.5), "#F0443A", ta=0.8),
        F(rrect(22, 54, 56, 9, 4.5), "#FFC530", ta=0.6),
        F(rrect(22, 67, 34, 9, 4.5), "#26B45A", ta=0.45),
    ], [
        "com.google.android.apps.nbu.paisa.user/com.google.nbu.paisa.flutter.gpay.app.LauncherActivity",
        "com.google.android.apps.nbu.paisa.user/com.google.android.apps.nbu.paisa.user.main.MainActivity",
    ], fg="#2F7BF2"),
    Icon("zomato", "Zomato", ("#FF5A68", "#D61F36"), ("#FF7A86", "#F03A50"), [
        S(line((33, 23), (33, 37)), 3.5, cap="round"), S(line((40, 23), (40, 37)), 3.5, cap="round"),
        S(line((47, 23), (47, 37)), 3.5, cap="round"), S("M33,37Q40,46 47,37", 3.5, cap="round"),
        S(line((40, 43), (40, 77)), 5.5, cap="round"),
        F(ellipse(62, 34, 8, 12)), S(line((62, 44), (62, 77)), 5.5, cap="round"),
    ], [
        "com.application.zomato/com.application.zomato.activities.Splash",
    ]),
    Icon("swiggy", "Swiggy", ("#FFA74A", "#FF6A12"), ("#FFB866", "#FF822E"), [
        F("M50,18C37,18 27,28 27,41C27,58 50,82 50,82C50,82 73,58 73,41C73,28 63,18 50,18Z"
          + "M41,36Q41,29 50,29Q57,29 59,35L53,37Q52,34 50,34Q47,34 47,36Q47,38 52,40Q60,43 60,49"
            "Q60,57 50,57Q42,57 40,50L46,48Q47,52 50,52Q54,52 54,49Q54,47 48,45Q41,42 41,36Z", evenodd=True),
    ], [
        "in.swiggy.android/in.swiggy.android.activities.HomeActivity",
    ]),
    Icon("snapchat", "Snapchat", ("#FFF36B", "#FFCF0A"), ("#FFF56E", "#FFDD1F"), [
        F("M50,21C61,21 69,29 69,41V47L75,46Q78,46 77,49L70,53Q73,61 81,64Q80,67 74,68L72,73L66,72"
          "Q60,73 56,77Q53,79 50,79Q47,79 44,77Q40,73 34,72L28,73L26,68Q20,67 19,64Q27,61 30,53L23,49"
          "Q22,46 25,46L31,47V41C31,29 39,21 50,21Z"),
    ], [
        "com.snapchat.android/com.snapchat.android.LandingPageActivity",
    ], fg="#1C1C1E"),
    Icon("linkedin", "LinkedIn", ("#3A8EE0", "#0A5AAE"), ("#62A8F0", "#2378D6"), [
        F(circle(33, 30, 5.5)), S(line((33, 43), (33, 74)), 9, cap="butt"),
        S("M50,74V43M50,56Q50,43 61,43Q72,43 72,56V74", 9, cap="butt", join="round"),
    ], [
        "com.linkedin.android/com.linkedin.android.authenticator.LaunchActivity",
    ]),
    Icon("discord", "Discord", ("#7C83FF", "#4A50E0"), ("#9AA0FF", "#6067F2"), [
        F("M29,33Q39,26 50,28Q61,26 71,33Q80,47 78,66Q71,72 63,74L59,68Q50,71 41,68L37,74Q29,72 22,66"
          "Q20,47 29,33Z" + ellipse(40.5, 52, 5, 6) + ellipse(59.5, 52, 5, 6), evenodd=True),
    ], [
        "com.discord/com.discord.main.MainActivity",
        "com.discord/com.discord.app.AppActivity$Main",
    ]),
    Icon("playstore", "Play Store", WHITE_TILE, ("#4FD1A0", "#2C9AF0"), [
        F(poly((30, 22), (68, 48), (30, 48)), "#22B88A", ta=0.9),
        F(poly((30, 52), (68, 52), (30, 78)), "#2F7BF2", ta=0.7),
        F(poly((56, 40), (76, 50), (56, 60)), "#FFC530", ta=1),
    ], [
        "com.android.vending/com.android.vending.AssetBrowserActivity",
    ], fg="#2F7BF2"),
    Icon("google", "Google", WHITE_TILE, ("#5AA0FF", "#2C6FEA"), [
        S(arc(44, 44, 16, 180, 270), 7, "#F0443A", cap="round", ta=0.8),
        S(arc(44, 44, 16, 270, 360), 7, "#FFC530", cap="round", ta=0.6),
        S(arc(44, 44, 16, 0, 90), 7, "#26B45A", cap="round", ta=0.9),
        S(arc(44, 44, 16, 90, 180), 7, "#2F7BF2", cap="round", ta=0.7),
        S(line((57, 57), (73, 73)), 8, "#2F7BF2", cap="round", ta=1),
    ], [
        "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity",
    ], fg="#2F7BF2"),
    Icon("drive", "Drive", WHITE_TILE, ("#4FD1A0", "#2C9AF0"), [
        S(line((48, 24), (28, 60)), 10, "#22B357", cap="round", ta=0.9),
        S(line((52, 24), (72, 60)), 10, "#FFC530", cap="round", ta=0.6),
        S(line((32, 74), (68, 74)), 10, "#2F7BF2", cap="round", ta=1),
    ], [
        "com.google.android.apps.docs/com.google.android.apps.docs.app.NewMainProxyActivity",
    ], fg="#2F7BF2"),
    Icon("ytmusic", "YouTube Music", ("#FF5A4E", "#D9141E"), ("#FF7066", "#F03A3A"), [
        S(circle(50, 50, 24), 4.5), F("M44,39L61,50L44,61Z"),
    ], [
        "com.google.android.apps.youtube.music/com.google.android.apps.youtube.music.activities.MusicActivity",
    ]),
    Icon("pinterest", "Pinterest", ("#FF4A5C", "#C8102E"), ("#FF6B7A", "#F02A44"), [
        S(line((50, 44), (42, 78)), 6, cap="round"),
        S("M40,52Q32,47 33,38Q35,25 50,24Q66,24 67,38Q68,54 54,56Q47,56 48,48", 6, cap="round", join="round"),
    ], [
        "com.pinterest/com.pinterest.activity.PinterestActivity",
    ]),
    Icon("signal", "Signal", ("#5C8DFF", "#2A5CE6"), ("#7FA6FF", "#4472F0"), [
        S(arc(50, 49, 25, 125, 485), 5, cap="round"),
        F(poly((31, 64), (25, 79), (40, 72))),
        F(circle(50, 49, 13), "fg2"),
    ], [
        "org.thoughtcrime.securesms/org.thoughtcrime.securesms.RoutingActivity",
    ]),
    Icon("zoom", "Zoom", ("#5AA2FF", "#1E66E6"), ("#7FB8FF", "#3A80F2"), [
        F(rrect(19, 34, 42, 32, 9)), F("M64,45L80,36V64L64,55Z"),
    ], [
        "us.zoom.videomeetings/com.zipow.videobox.LauncherActivity",
    ]),
    Icon("truecaller", "Truecaller", ("#3FA0FF", "#1452D6"), ("#62B4FF", "#2E6EF0"), [
        S(circle(50, 50, 27), 5), F(handset(23, 23, 0.55)),
    ], [
        "com.truecaller/com.truecaller.ui.TruecallerInit",
    ]),
    Icon("uber", "Uber", ("#38393D", "#0A0A0B"), ("#FFFFFF", "#C7C7CC"), [
        S(circle(50, 50, 21), 8), F(rrect(43, 43, 14, 14, 2)),
    ], [
        "com.ubercab/com.ubercab.presidio.app.core.root.RootActivity",
    ]),
    Icon("flipkart", "Flipkart", ("#3D8BFF", "#1550C8"), ("#FFE066", "#FFC21F"), [
        S("M38,40V34A12,12 0 0,1 62,34V40", 5, cap="round"),
        F(rrect(23, 38, 54, 40, 9) + "M44,49H58V54H49V57H56V62H49V70H44Z", evenodd=True),
    ], [
        "com.flipkart.android/com.flipkart.android.activity.HomeFragmentHolderActivity",
    ], fg="#FFD84A"),
]


# ---- output --------------------------------------------------------------------------------

def hex_alpha(color, alpha):
    a = max(0, min(255, round(alpha * 255)))
    return f"#{a:02X}{color[1:]}"


def gradient_xml(attr, colors, x0=50, y0=0, x1=50, y1=100):
    if len(colors) == 2:
        return (f'<aapt:attr name="android:{attr}"><gradient android:type="linear" '
                f'android:startX="{n(x0)}" android:startY="{n(y0)}" android:endX="{n(x1)}" android:endY="{n(y1)}" '
                f'android:startColor="{colors[0]}" android:endColor="{colors[1]}"/></aapt:attr>')
    items = "".join(f'<item android:offset="{n(i / (len(colors) - 1))}" android:color="{c}"/>' for i, c in enumerate(colors))
    return (f'<aapt:attr name="android:{attr}"><gradient android:type="linear" '
            f'android:startX="{n(x0)}" android:startY="{n(y0)}" android:endX="{n(x1)}" android:endY="{n(y1)}">'
            f'{items}</gradient></aapt:attr>')


class Paint:
    """A resolved fill or stroke: either a solid colour (+alpha) or a vertical gradient."""

    def __init__(self, solid=None, alpha=1.0, grad=None):
        self.solid, self.alpha, self.grad = solid, alpha, grad


def resolve(icon, role, variant, opts):
    fixed = role.startswith("#")
    if variant == "tinted":
        ta = opts.get("ta", 0.55 if (fixed or role == "fg2") else 1.0)
        return Paint("#FFFFFF", ta)
    alpha = 0.55 if role == "fg2" else 1.0
    if fixed:
        return Paint(role, 1.0)
    if variant == "light":
        return Paint(icon.fg, alpha)
    return Paint(alpha=alpha, grad=icon.accent)


def tile_layers(icon, variant):
    """(kind, path, paint, opts) for the glass tile."""
    if variant == "tinted":
        return []
    colors = icon.tile if variant == "light" else (icon.dark_tile or DARK_GLASS)
    sheen = 0.34 if variant == "light" else 0.16
    rim = 0.42 if variant == "light" else 0.2
    return [
        ("f", TILE, Paint(grad=colors), {"diag": len(colors) == 3}),
        ("sheen", ellipse(50, 8, 74, 36), Paint(grad=(hex_alpha("#FFFFFF", sheen), "#00FFFFFF")), {}),
        ("s", TILE_RIM, Paint("#FFFFFF", rim), {"w": 1.2}),
    ]


def element_layers(icon, variant):
    out = []
    for kind, d, role, opts in icon.elements:
        out.append((kind, d, resolve(icon, role, variant, opts), opts))
    return out


def vector_xml(icon, variant):
    parts = ['<?xml version="1.0" encoding="utf-8"?>',
             f'<!-- Glass Icons: "{icon.label}" ({variant}). Original artwork, generated by tools/icons/generate_icons.py. -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
             'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" '
             'android:viewportWidth="100" android:viewportHeight="100">']
    for kind, d, paint, opts in tile_layers(icon, variant) + element_layers(icon, variant):
        parts.append(path_xml(kind, d, paint, opts))
    parts.append("</vector>")
    return "\n".join(parts) + "\n"


def path_xml(kind, d, paint, opts):
    if kind == "sheen":
        return (f'    <group><clip-path android:pathData="{TILE}"/>'
                f'<path android:pathData="{d}">{gradient_xml("fillColor", paint.grad, 50, -28, 50, 44)}</path></group>')
    attrs = [f'android:pathData="{d}"']
    attr = "fillColor" if kind == "f" else "strokeColor"
    inner = ""
    if paint.grad:
        if opts.get("diag"):
            inner = gradient_xml(attr, paint.grad, 0, 0, 100, 100)
        else:
            inner = gradient_xml(attr, paint.grad, 50, 15, 50, 85) if kind != "f" or d != TILE else gradient_xml(attr, paint.grad)
    else:
        attrs.append(f'android:{attr}="{paint.solid}"')
    if paint.alpha < 1:
        attrs.append(f'android:{"fillAlpha" if kind == "f" else "strokeAlpha"}="{n(paint.alpha)}"')
    if kind == "f" and opts.get("evenodd"):
        attrs.append('android:fillType="evenOdd"')
    if kind == "s":
        attrs.append(f'android:strokeWidth="{n(opts["w"])}"')
        attrs.append(f'android:strokeLineCap="{opts.get("cap", "round")}"')
        attrs.append(f'android:strokeLineJoin="{opts.get("join", "round")}"')
    if inner:
        return f'    <path {" ".join(attrs)}>{inner}</path>'
    return f'    <path {" ".join(attrs)}/>'


_GID = [0]


def svg(icon, variant, tinted_bg=None):
    """SVG twin of the vector, for previews only."""
    defs, body = [], []
    gid = _GID

    def grad(colors, x0, y0, x1, y1):
        gid[0] += 1
        stops = "".join(f'<stop offset="{i / (len(colors) - 1)}" stop-color="#{c[-6:]}" '
                        f'stop-opacity="{int(c[1:3], 16) / 255 if len(c) == 9 else 1}"/>' for i, c in enumerate(colors))
        defs.append(f'<linearGradient id="g{gid[0]}" gradientUnits="userSpaceOnUse" x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}">{stops}</linearGradient>')
        return f"url(#g{gid[0]})"

    defs.append(f'<clipPath id="tile{gid[0]}"><path d="{TILE}"/></clipPath>')
    clip = f"tile{gid[0]}"
    if tinted_bg:
        body.append(f'<path d="{TILE}" fill="{tinted_bg}"/>')
    for kind, d, paint, opts in tile_layers(icon, variant) + element_layers(icon, variant):
        if kind == "sheen":
            body.append(f'<path d="{d}" clip-path="url(#{clip})" fill="{grad(paint.grad, 50, -28, 50, 44)}"/>')
            continue
        if paint.grad:
            if opts.get("diag"):
                col = grad(paint.grad, 0, 0, 100, 100)
            elif kind == "f" and d == TILE:
                col = grad(paint.grad, 50, 0, 50, 100)
            else:
                col = grad(paint.grad, 50, 15, 50, 85)
        else:
            col = paint.solid
        if kind == "f":
            rule = "evenodd" if opts.get("evenodd") else "nonzero"
            body.append(f'<path d="{d}" fill="{col}" fill-opacity="{paint.alpha}" fill-rule="{rule}"/>')
        else:
            body.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-opacity="{paint.alpha}" '
                        f'stroke-width="{opts["w"]}" stroke-linecap="{opts.get("cap", "round")}" '
                        f'stroke-linejoin="{opts.get("join", "round")}"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="120" height="120">'
            f'<defs>{"".join(defs)}</defs>{"".join(body)}</svg>')


def frame_vectors():
    """iconback / iconmask / iconupon: theme unmapped apps like the rest of the pack."""
    head = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/icons/generate_icons.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
            'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" '
            'android:viewportWidth="100" android:viewportHeight="100">\n')
    def back(colors, rim):
        return (head + f'    <path android:pathData="{TILE}">{gradient_xml("fillColor", colors)}</path>\n'
                f'    <path android:pathData="{TILE_RIM}" android:strokeColor="#FFFFFF" android:strokeAlpha="{rim}" '
                f'android:strokeWidth="1.2"/>\n</vector>\n')
    mask = head + '    <path android:pathData="M0,0H100V100H0Z" android:fillColor="#000000"/>\n' + \
        f'    <path android:pathData="{superellipse(r=44)}" android:fillColor="#FFFFFF"/>\n</vector>\n'
    upon = (head + f'    <group><clip-path android:pathData="{TILE}"/><path android:pathData="{ellipse(50, 8, 74, 36)}">'
            f'{gradient_xml("fillColor", ("#55FFFFFF", "#00FFFFFF"), 50, -28, 50, 44)}</path></group>\n'
            f'    <path android:pathData="{TILE_RIM}" android:strokeColor="#FFFFFF" android:strokeAlpha="0.4" '
            f'android:strokeWidth="1.2"/>\n</vector>\n')
    return {
        "iconback": back(("#F4F5F8", "#D5D8DF"), 0.6),
        "iconback_dark": back(DARK_GLASS, 0.2),
        "iconmask": mask,
        "iconupon": upon,
    }


def esc(s):
    return s.replace("&", "&amp;").replace('"', "&quot;").replace("'", "\\'")


def main():
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
    names = [i.name for i in ICONS]
    assert len(names) == len(set(names)), "duplicate icon names"
    comps = [c for i in ICONS for c in i.components]
    assert len(comps) == len(set(comps)), "duplicate components"

    draw = os.path.join(RES, "drawable")
    os.makedirs(draw, exist_ok=True)
    os.makedirs(os.path.join(RES, "xml"), exist_ok=True)
    os.makedirs(os.path.join(RES, "values"), exist_ok=True)
    os.makedirs(ASSETS, exist_ok=True)
    for icon in ICONS:
        for variant, suffix in (("light", ""), ("dark", "_dark"), ("tinted", "_tinted")):
            with open(os.path.join(draw, icon.name + suffix + ".xml"), "w") as f:
                f.write(vector_xml(icon, variant))
    for name, text in frame_vectors().items():
        with open(os.path.join(draw, name + ".xml"), "w") as f:
            f.write(text)

    af = ['<?xml version="1.0" encoding="utf-8"?>',
          "<!-- Glass Icons appfilter. Generated by tools/icons/generate_icons.py; edit the script, not this file. -->",
          "<resources>",
          '    <iconback img1="iconback" />',
          '    <iconmask img1="iconmask" />',
          '    <iconupon img1="iconupon" />',
          '    <scale factor="0.72" />']
    for icon in ICONS:
        af.append(f"    <!-- {icon.label} -->")
        for c in icon.components:
            af.append(f'    <item component="ComponentInfo{{{c}}}" drawable="{icon.name}" />')
    af.append("</resources>")
    text = "\n".join(af) + "\n"
    for path in (os.path.join(RES, "xml", "appfilter.xml"), os.path.join(ASSETS, "appfilter.xml")):
        with open(path, "w") as f:
            f.write(text)

    dx = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated by tools/icons/generate_icons.py -->",
          "<resources>", "    <version>1</version>",
          '    <category title="System" />']
    third = False
    for idx, icon in enumerate(ICONS):
        if icon.name == "whatsapp" and not third:
            dx.append('    <category title="Apps" />')
            third = True
        dx.append(f'    <item drawable="{icon.name}" />')
    dx.append("</resources>")
    with open(os.path.join(RES, "xml", "drawable.xml"), "w") as f:
        f.write("\n".join(dx) + "\n")

    lab = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated by tools/icons/generate_icons.py -->", "<resources>"]
    lab.append('    <string-array name="icon_names">' + "".join(f"<item>{i.name}</item>" for i in ICONS) + "</string-array>")
    lab.append('    <string-array name="icon_labels">' + "".join(f"<item>{esc(i.label)}</item>" for i in ICONS) + "</string-array>")
    lab.append("</resources>")
    with open(os.path.join(RES, "values", "icon_labels.xml"), "w") as f:
        f.write("\n".join(lab) + "\n")

    if preview:
        os.makedirs(preview, exist_ok=True)
        rows = []
        for icon in ICONS:
            cells = [svg(icon, "light"), svg(icon, "dark"), svg(icon, "tinted", tinted_bg="#1C1C1E")]
            rows.append(f'<div class="r"><span>{icon.name}</span>{"".join(cells)}</div>')
        html = ("<html><body style='background:#6b7a99;font:12px sans-serif;color:#fff;margin:8px'>"
                "<style>.r{display:inline-flex;align-items:center;gap:4px;margin:4px 10px;width:470px}"
                ".r span{width:90px}</style>" + "".join(rows) + "</body></html>")
        with open(os.path.join(preview, "icons.html"), "w") as f:
            f.write(html)
    print(f"{len(ICONS)} icons x 3 variants = {len(ICONS) * 3} drawables, {len(comps)} components")


if __name__ == "__main__":
    main()
