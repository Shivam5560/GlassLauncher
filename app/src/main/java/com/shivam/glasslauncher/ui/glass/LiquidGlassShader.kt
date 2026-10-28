package com.shivam.glasslauncher.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * AGSL source for the liquid-glass lens. One body is compiled twice:
 *  - as a RenderEffect (`content` is the blurred backdrop of the panel), used by [GlassSurface];
 *  - as a plain paint shader with a flat `baseColor` instead of a backdrop, used by View-based
 *    glass in overlay windows (gesture pill, back-swipe bubble) where no backdrop is readable.
 *
 * Geometry: a rounded rectangle with superellipse corners (exponent `expo`), matching
 * [GlassShape]. Inside the `rim` band the backdrop is displaced towards the centre with a cubic
 * profile (flat centre, strong bend at the edge), red and blue are displaced by slightly
 * different amounts for chromatic aberration, and a directional rim light plus a soft sheen are
 * added from `light` (unit vector pointing at the light source in screen space).
 */
object LiquidGlassShader {

    private fun source(withBackdrop: Boolean): String {
        val header = if (withBackdrop) "uniform shader content;" else "uniform half4 baseColor;"
        val sample = if (withBackdrop) "content.eval(c)" else "baseColor"
        return """
$header
uniform float2 size;
uniform float radius;
uniform float expo;
uniform float rim;
uniform float refraction;
uniform float chroma;
uniform float2 light;
uniform float specular;
uniform half4 tint;
uniform float tintAmount;
uniform float brighten;

half4 sampleAt(float2 c) { return $sample; }

float sdf(float2 p) {
    float2 h = size * 0.5;
    float r = min(radius, min(h.x, h.y));
    float2 q = abs(p - h) - h + r;
    float2 m = max(q, 0.0);
    float corner = pow(pow(m.x, expo) + pow(m.y, expo), 1.0 / expo);
    return corner + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    float d = sdf(p);
    if (d > 1.0) { return half4(0.0); }

    const float e = 0.75;
    float2 n = float2(sdf(p + float2(e, 0.0)) - sdf(p - float2(e, 0.0)),
                      sdf(p + float2(0.0, e)) - sdf(p - float2(0.0, e)));
    float nl = length(n);
    n = nl > 0.0001 ? n / nl : float2(0.0, 0.0);

    float inside = max(-d, 0.0);
    float x = clamp(1.0 - inside / max(rim, 1.0), 0.0, 1.0);   // 1 at the edge, 0 in the flat centre
    float bend = refraction * x * x * x;
    float2 offset = -n * bend;

    float4 col;
    if (chroma > 0.001) {
        float4 g = float4(sampleAt(p + offset));
        float r = float(sampleAt(p + offset * (1.0 + 0.6 * chroma)).r);
        float b = float(sampleAt(p + offset * max(1.0 - 0.6 * chroma, 0.0)).b);
        col = float4(r, g.g, b, g.a);
    } else {
        col = float4(sampleAt(p + offset));
    }

    // tint (premultiplied mix)
    float t = clamp(tintAmount, 0.0, 1.0);
    float3 rgb = col.rgb * (1.0 - t) + float3(tint.rgb) * t;
    float a = col.a * (1.0 - t) + t;
    rgb += brighten * 0.10 * a;

    // lighting
    float2 L = normalize(light);
    float facing = dot(n, L);
    float rimLine = exp(-inside / 1.2);
    float spec = rimLine * (0.22 + 0.78 * max(facing, 0.0) + 0.4 * max(-facing, 0.0));
    float glow = x * x * (0.06 + 0.22 * max(facing, 0.0));
    float2 uv = p / size - 0.5;
    float s = clamp(dot(uv, L) + 0.5, 0.0, 1.0);
    float sheen = s * s * s * 0.14;
    float hl = (spec + glow + sheen) * specular * (1.0 + brighten * 0.5);
    rgb += hl;
    a = a + hl * (1.0 - a);
    rgb = min(rgb, float3(a));

    float coverage = clamp(0.5 - d, 0.0, 1.0);
    return half4(rgb * coverage, a * coverage);
}
"""
    }

    @Volatile private var effectBroken = false
    @Volatile private var paintBroken = false

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Lens shader with a `content` input for RenderEffect use, or null if unsupported/failed. */
    fun createEffectShader(): RuntimeShader? {
        if (!isSupported || effectBroken) return null
        return create(true).also { if (it == null) effectBroken = true }
    }

    /** Self-contained shader for Paint use (no backdrop), or null if unsupported/failed. */
    fun createPaintShader(): RuntimeShader? {
        if (!isSupported || paintBroken) return null
        return create(false).also { if (it == null) paintBroken = true }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createApi33(withBackdrop: Boolean) = RuntimeShader(source(withBackdrop))

    private fun create(withBackdrop: Boolean): RuntimeShader? = try {
        createApi33(withBackdrop)
    } catch (t: Throwable) {
        Log.w("LiquidGlass", "AGSL compile failed, falling back to blur + tint", t)
        null
    }

    /** Sets every uniform; [tint] is straight (non-premultiplied) RGB in 0..1. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun RuntimeShader.setGlassUniforms(
        width: Float,
        height: Float,
        radius: Float,
        exponent: Float,
        rim: Float,
        refraction: Float,
        chroma: Float,
        lightX: Float,
        lightY: Float,
        specular: Float,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        tintAmount: Float,
        brighten: Float,
    ) {
        setFloatUniform("size", width, height)
        setFloatUniform("radius", radius)
        setFloatUniform("expo", exponent)
        setFloatUniform("rim", rim)
        setFloatUniform("refraction", refraction)
        setFloatUniform("chroma", chroma)
        setFloatUniform("light", lightX, lightY)
        setFloatUniform("specular", specular)
        setFloatUniform("tint", tintR, tintG, tintB, 1f)
        setFloatUniform("tintAmount", tintAmount)
        setFloatUniform("brighten", brighten)
    }
}
