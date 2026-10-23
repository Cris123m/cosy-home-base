package com.cosyhome.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Draws a scene pack's still-layer foreground: hill ridges, fog banks, the
 * window glow, landmark ops and lit windows. The sky, sun, moon and all
 * per-frame weather stay in {@link SceneView}; this class owns no phase state,
 * holds one reused Paint, and allocates nothing per call.
 */
public final class ForegroundRenderer {

    /** For the still layers. Shaders draw with the paint's alpha, so it must never carry a frame's fade. */
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Still-layer foreground in 1920x1080 design space, in the exact order the
     * valley always drew: far hill, high fog, mid hill, window glow, landmark
     * ops, windows, near hill, low fog.
     */
    public void render(Canvas c, ScenePack pack, SceneView.Palette pal, SceneView.Sky sky, SceneView.Phase phase) {
        float g = greyness(sky);
        float hg = g * 0.35f;
        fill(c, pack.hills[0], hill(pal.h1, hg, 0.55f, sky, phase));
        if (sky == SceneView.Sky.FOG) {
            fogBank(c, phase, 300, 420, 0.45f);
            fogBank(c, phase, 560, 320, 0.9f);
        }
        fill(c, pack.hills[1], hill(pal.h2, hg, 0.6f, sky, phase));

        if (pal.glow > 0 && !pack.windows.isEmpty()) {
            int a = Math.round(pal.glow * 0x99);
            int core = (a << 24) | (pal.win & 0xFFFFFF);
            paint.setShader(new RadialGradient(pack.glowX, pack.glowY, ScenePack.GLOW_R,
                    core, pal.win & 0xFFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(pack.glowX, pack.glowY, ScenePack.GLOW_R, paint);
            paint.setShader(null);
        }

        for (int i = 0; i < pack.ops.size(); i++) {
            ScenePack.Op op = pack.ops.get(i);
            paint.setColor(opColor(op, pal));
            switch (op.type) {
                case ScenePack.Op.RECT:
                    c.drawRect(op.params[0], op.params[1], op.params[2], op.params[3], paint);
                    break;
                case ScenePack.Op.CIRCLE:
                    c.drawCircle(op.params[0], op.params[1], op.params[2], paint);
                    break;
                case ScenePack.Op.OVAL:
                    c.drawOval(op.params[0], op.params[1], op.params[2], op.params[3], paint);
                    break;
                default:
                    c.drawPath(op.path, paint);
                    break;
            }
        }

        paint.setColor(pal.win);
        for (int i = 0; i < pack.windows.size(); i++) {
            float[] w = pack.windows.get(i);
            c.drawRect(w[0], w[1], w[2], w[3], paint);
        }

        fill(c, pack.hills[2], hill(pal.h3, 0f, 0.65f, sky, phase));

        if (sky == SceneView.Sky.FOG) {
            fogBank(c, phase, 740, 260, 0.85f);
            fogBank(c, phase, 880, 200, 0.5f);
        }
    }

    /** Per-frame chimney smoke rising from the pack's anchor; nothing when the pack has none. */
    public void drawSmoke(Canvas c, ScenePack pack, float seconds, Paint fx) {
        if (!pack.hasSmoke) return;
        for (int i = 0; i < 3; i++) {
            float t = ((seconds + i * 2.33f) % 7f) / 7f;              // 0..1 over a 7 s rise
            float alpha = t < 0.2f ? t / 0.2f * 0.45f : 0.45f * (1 - (t - 0.2f) / 0.8f);
            float r = 10 * (0.6f + 1.1f * t);
            fx.setColor(Color.argb(Math.round(alpha * 255), 0xD8, 0xD2, 0xE2));
            c.drawCircle(pack.smokeX + 34 * t, pack.smokeY - 130 * t, r, fx);
        }
    }

    private int opColor(ScenePack.Op op, SceneView.Palette pal) {
        if (op.colorKind == ScenePack.Op.H1) return pal.h1;
        if (op.colorKind == ScenePack.Op.H2) return pal.h2;
        if (op.colorKind == ScenePack.Op.WIN) return pal.win;
        if (op.colorKind == ScenePack.Op.H3) return pal.h3;
        return op.literal;
    }

    private void fill(Canvas c, Path p, int color) {
        paint.setColor(color);
        c.drawPath(p, paint);
    }

    // Purpose: a horizontal band of fog, densest in the middle, fading up and down.
    private void fogBank(Canvas c, SceneView.Phase phase, float top, float height, float alpha) {
        int fog = phase == SceneView.Phase.NIGHT ? 0x8090A0
                : phase == SceneView.Phase.DAY ? 0xE6EAEE : 0xC8BCC4;
        int a = Math.round(alpha * 255) << 24;
        paint.setShader(new LinearGradient(0, top, 0, top + height, new int[]{fog, a | fog, fog},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, top, SceneView.W, top + height, paint);
        paint.setShader(null);
    }

    // Purpose: a hill colour, turned snowy under snowfall (the nearer the hill, the more snow shows).
    private int hill(int c, float hg, float snow, SceneView.Sky sky, SceneView.Phase phase) {
        int base = grey(c, hg, sky);
        if (sky != SceneView.Sky.SNOW) return base;
        int white = phase == SceneView.Phase.NIGHT ? 0xFF3A4258
                : phase == SceneView.Phase.DAY ? 0xFFE4EAF0 : 0xFFB8B0C4;
        return lerp(base, white, snow);
    }

    // Purpose: how far the sky colours are washed towards grey for this weather.
    static float greyness(SceneView.Sky sky) {
        switch (sky) {
            case CLOUDY: return 0.55f;
            case FOG: return 0.6f;
            case RAIN: return 0.65f;
            case SNOW: return 0.5f;
            case STORM: return 0.75f;
            default: return 0f;
        }
    }

    // Purpose: a colour washed towards its own grey, darker for rain and storm.
    static int grey(int c, float amount, SceneView.Sky sky) {
        if (amount <= 0) return c;
        int l = Math.round(0.3f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c));
        if (sky == SceneView.Sky.STORM || sky == SceneView.Sky.RAIN) l = Math.round(l * 0.82f);
        if (sky == SceneView.Sky.SNOW || sky == SceneView.Sky.FOG) l = Math.min(255, Math.round(l * 1.08f + 10));
        return lerp(c, Color.rgb(l, l, Math.min(255, l + 6)), amount);
    }

    static int lerp(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
