"""Original procedural artwork for Wallpaper Studio. No external artwork or network assets."""
import numpy as np


def palette_gradient(c, colors):
    from generate_wallpapers import hexrgb
    stops = np.linspace(0, 1, len(colors))
    t = c.y / c.aspect
    return np.stack([np.interp(t, stops, [hexrgb(v)[channel] for v in colors]) for channel in range(3)], axis=-1).astype(np.float32)


def obsidian(c, mode, variant=0):
    from generate_wallpapers import hexrgb
    dark = mode == 'dark'
    base = palette_gradient(c, ['#090C12', '#161C25', '#080B10'] if dark else ['#E6E8EC', '#A7B0BC', '#D9DEE4'])
    y = c.y / c.aspect
    # Broad diagonal graphite planes with a thin, diffuse silver edge; quiet top quarter.
    for i in range(3):
        curve = 0.35 + i * 0.17 + (0.28 if variant == 0 else -0.3) * (c.x - 0.5) + 0.08 * np.sin(c.x * 4 + i)
        d = y - curve
        plane = np.exp(-((d - 0.055) / 0.13) ** 2) * (0.27 if dark else 0.3)
        edge = np.exp(-(d / 0.005) ** 2) * (0.12 if dark else 0.2)
        tone = hexrgb('#526477' if dark else '#F4F6F8')
        base += (tone - base) * (plane + edge)[..., None]
    return base


def silk(c, mode, variant=0):
    from generate_wallpapers import hexrgb
    dark = mode == 'dark'
    base = palette_gradient(c, ['#080E22', '#10283D', '#090E24'] if dark else ['#E6F2F7', '#C3DFEA', '#E9DFF5'])
    y = c.y / c.aspect
    colors = ['#39BDB4', '#5877CE', '#9978D9'] if variant == 0 else ['#7777C9', '#3BABC4', '#59CBB7']
    for i, color in enumerate(colors):
        curve = 0.43 + i * 0.12 + 0.12 * np.sin(c.x * 4 + i * 0.6 + variant)
        d = y - curve
        band = np.exp(-(d / 0.08) ** 2) * 0.65
        highlight = np.exp(-((d + 0.026) / 0.014) ** 2) * 0.3
        base += (hexrgb(color) - base) * band[..., None]
        base += (hexrgb('#C7F8F2') - base) * (highlight * band)[..., None]
    return base


def stone(c, mode, variant=0):
    from generate_wallpapers import hexrgb
    dark = mode == 'dark'
    colors = (['#211A19', '#503B32', '#251F1C'] if variant == 0 else ['#241C1C', '#594039', '#332322']) if dark else \
             (['#F7EFE0', '#D4BEA3', '#E9DAC5'] if variant == 0 else ['#F5E9E2', '#CBAA99', '#E8D5C7'])
    base = palette_gradient(c, colors)
    y = c.y / c.aspect
    for i in range(4):
        curve = 0.4 + i * 0.15 + 0.08 * np.cos(c.x * 4.2 + i * 0.7 + variant)
        d = y - curve
        band = 1 / (1 + np.exp(np.clip(-d * 180, -80, 80)))
        tone = hexrgb(('#7C5F49' if dark else '#C6A98C') if variant == 0 else ('#896459' if dark else '#C89583'))
        base += (tone - base) * (band * 0.18)[..., None]
        base += (hexrgb('#B8A18D' if dark else '#FFF5E7') - base) * (np.exp(-(d / 0.012) ** 2) * 0.12)[..., None]
    return base


def landscape(c, mode, variant=0):
    from generate_wallpapers import hexrgb
    dark = mode == 'dark'
    base = palette_gradient(c, (['#0A1725', '#263F50', '#10232E'] if dark else ['#DCEAF0', '#A5C2CB', '#668B9C']) if variant == 0 else
                           (['#1B1621', '#4A3439', '#251F2B'] if dark else ['#F6E6D6', '#CEAF9F', '#8E8897']))
    y = c.y / c.aspect
    for i in range(5):
        ridge = 0.46 + 0.105 * i + 0.04 * np.sin(c.x * (6 + i) + i * 1.6) + 0.025 * np.cos(c.x * 14 + i)
        if variant: ridge = 0.48 + 0.1 * i + 0.065 * np.sin(c.x * 3.5 + i * 1.5)
        mask = 1 / (1 + np.exp(np.clip(-(y - ridge) * 380, -80, 80)))
        tint = hexrgb(('#07151F' if dark else '#345D73') if variant == 0 else ('#201822' if dark else '#766476'))
        haze = 0.38 - i * 0.055
        tone = tint * (1 - haze) + hexrgb('#8FA4AD' if variant == 0 else '#D5B7AA') * haze
        base = base * (1 - mask[..., None] * 0.72) + tone * mask[..., None] * 0.72
    return base


STUDIO_DESIGNS = [
    ('graphite', 'Graphite', 'Obsidian', lambda c, m: obsidian(c, m, 0)),
    ('silverfold', 'Silver Fold', 'Obsidian', lambda c, m: obsidian(c, m, 1)),
    ('silkflow', 'Silk Flow', 'Aurora Silk', lambda c, m: silk(c, m, 0)),
    ('blueveil', 'Blue Veil', 'Aurora Silk', lambda c, m: silk(c, m, 1)),
    ('travertine', 'Travertine', 'Warm Stone', lambda c, m: stone(c, m, 0)),
    ('clay', 'Clay', 'Warm Stone', lambda c, m: stone(c, m, 1)),
    ('mist', 'Misty Ridges', 'Quiet Landscape', lambda c, m: landscape(c, m, 0)),
    ('stilldunes', 'Still Dunes', 'Quiet Landscape', lambda c, m: landscape(c, m, 1)),
]
