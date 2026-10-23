package com.cosyhome.tv;

import android.graphics.Color;
import android.graphics.Path;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A declarative scene pack: palettes per phase plus a data-only foreground
 * (hill ridges, landmark ops, smoke anchor, lit windows). Packs never execute
 * code: everything is JSON plus SVG pathData, parsed once per activation with
 * all Paths precompiled, so still rebuilds allocate nothing per call.
 *
 * <p>Unknown JSON keys are ignored (forward compatible); {@code spec} is required.
 * Any malformed value throws IllegalArgumentException so the caller (PackStore)
 * can keep the previous pack; total failure falls back to {@link #builtinValle}.
 */
public final class ScenePack {

    /** Pack format version this engine renders. */
    public static final int SPEC = 2;

    /** One phase palette, same fields as the old SceneView struct. */
    public static final class PackPalette {
        public final int s1, s2, s3, h1, h2, h3, win;
        public final float glow;
        public final boolean darkText;

        PackPalette(int s1, int s2, int s3, int h1, int h2, int h3, int win, float glow, boolean darkText) {
            this.s1 = s1;
            this.s2 = s2;
            this.s3 = s3;
            this.h1 = h1;
            this.h2 = h2;
            this.h3 = h3;
            this.win = win;
            this.glow = glow;
            this.darkText = darkText;
        }
    }

    /** One landmark op: rect | circle | oval | path, in 1920x1080 design space. */
    public static final class Op {
        public static final int RECT = 0, CIRCLE = 1, OVAL = 2, PATH = 3;
        /** Palette reference, or -1 for {@link #literal}. */
        public static final int H1 = 0, H2 = 1, H3 = 2, WIN = 3;

        public final int type;
        /** rect/oval: [l,t,r,b]; circle: [cx,cy,r]; path: null (see {@link #path}). */
        public final float[] params;
        /** Precompiled for path ops, null otherwise. */
        public final Path path;
        public final int colorKind;
        public final int literal;

        Op(int type, float[] params, Path path, int colorKind, int literal) {
            this.type = type;
            this.params = params;
            this.path = path;
            this.colorKind = colorKind;
            this.literal = literal;
        }
    }

    public final String id;
    public final int spec;
    /** Phase name ("dawn", "day", "dusk", "night") to palette; a missing phase renders with the fallback pack. */
    public final Map<String, PackPalette> palettes;
    /** Three precompiled ridge Paths: far, mid, near. */
    public final Path[] hills;
    public final List<Op> ops;
    public final boolean hasSmoke;
    public final float smokeX, smokeY;
    /** Lit window rects [l,t,r,b] in design space. */
    public final List<float[]> windows;
    /** Window-glow centre: the windows' bounding-box centre (valle: 1564,746), radius 110. */
    public final float glowX, glowY;
    public static final float GLOW_R = 110f;

    private ScenePack(String id, int spec, Map<String, PackPalette> palettes, Path[] hills,
                      List<Op> ops, boolean hasSmoke, float smokeX, float smokeY, List<float[]> windows) {
        this.id = id;
        this.spec = spec;
        this.palettes = palettes;
        this.hills = hills;
        this.ops = ops;
        this.hasSmoke = hasSmoke;
        this.smokeX = smokeX;
        this.smokeY = smokeY;
        this.windows = windows;
        if (windows.isEmpty()) {
            glowX = 0;
            glowY = 0;
        } else {
            float l = windows.get(0)[0], t = windows.get(0)[1], r = windows.get(0)[2], b = windows.get(0)[3];
            for (int i = 1; i < windows.size(); i++) {
                float[] w = windows.get(i);
                if (w[0] < l) l = w[0];
                if (w[1] < t) t = w[1];
                if (w[2] > r) r = w[2];
                if (w[3] > b) b = w[3];
            }
            glowX = (l + r) / 2f;
            glowY = (t + b) / 2f;
        }
    }

    /** Palette for a phase, or {@code fallback} when the pack omits that phase. */
    public PackPalette paletteFor(String phase, PackPalette fallback) {
        PackPalette p = palettes.get(phase);
        return p != null ? p : fallback;
    }

    /**
     * Parse a pack.json string. Unknown keys are ignored; anything malformed
     * (missing spec, bad colors, bad pathData, unknown op) throws IllegalArgumentException.
     */
    public static ScenePack fromJson(String json) {
        try {
            return parse(new JSONObject(json));
        } catch (JSONException e) {
            throw new IllegalArgumentException("bad pack.json: " + e.getMessage(), e);
        }
    }

    private static ScenePack parse(JSONObject o) throws JSONException {
        if (!o.has("spec")) throw new IllegalArgumentException("pack.json missing required \"spec\"");
        int spec = o.getInt("spec");
        String id = o.optString("id", "unknown");

        Map<String, PackPalette> palettes = new HashMap<>();
        JSONObject p = o.optJSONObject("palettes");
        if (p != null) {
            for (String name : new String[]{"dawn", "day", "dusk", "night"}) {
                JSONObject q = p.optJSONObject(name);
                if (q == null) continue;
                palettes.put(name, new PackPalette(
                        color(q, "s1"), color(q, "s2"), color(q, "s3"),
                        color(q, "h1"), color(q, "h2"), color(q, "h3"), color(q, "win"),
                        (float) q.optDouble("glow", 0), q.optBoolean("darkText", false)));
            }
        }

        JSONArray h = o.optJSONArray("hills");
        if (h == null || h.length() != 3) throw new IllegalArgumentException("pack needs exactly 3 hills");
        Path[] hills = new Path[3];
        for (int i = 0; i < 3; i++) hills[i] = PathData.parse(h.getString(i));

        List<Op> ops = new ArrayList<>();
        JSONArray ja = o.optJSONArray("ops");
        if (ja != null) {
            for (int i = 0; i < ja.length(); i++) {
                JSONObject e = ja.getJSONObject(i);
                String op = e.getString("op");
                int colorKind;
                int literal = 0;
                String c = e.optString("color", "h3");
                if ("h1".equals(c)) colorKind = Op.H1;
                else if ("h2".equals(c)) colorKind = Op.H2;
                else if ("h3".equals(c)) colorKind = Op.H3;
                else if ("win".equals(c)) colorKind = Op.WIN;
                else {
                    colorKind = -1;
                    try {
                        literal = Color.parseColor(c);
                    } catch (IllegalArgumentException bad) {
                        throw new IllegalArgumentException("bad op color: " + c);
                    }
                }
                if ("rect".equals(op) || "oval".equals(op)) {
                    float[] r = rect(e.getJSONArray("params"));
                    ops.add(new Op("rect".equals(op) ? Op.RECT : Op.OVAL, r, null, colorKind, literal));
                } else if ("circle".equals(op)) {
                    JSONArray a = e.getJSONArray("params");
                    if (a.length() != 3) throw new IllegalArgumentException("circle needs 3 params");
                    ops.add(new Op(Op.CIRCLE,
                            new float[]{(float) a.getDouble(0), (float) a.getDouble(1), (float) a.getDouble(2)},
                            null, colorKind, literal));
                } else if ("path".equals(op)) {
                    ops.add(new Op(Op.PATH, null, PathData.parse(e.getString("params")), colorKind, literal));
                } else {
                    throw new IllegalArgumentException("unknown op: " + op);
                }
            }
        }

        boolean hasSmoke = false;
        float sx = 0, sy = 0;
        JSONObject s = o.optJSONObject("smoke");
        if (s != null) {
            hasSmoke = true;
            sx = (float) s.getDouble("x");
            sy = (float) s.getDouble("y");
        }

        List<float[]> windows = new ArrayList<>();
        JSONArray w = o.optJSONArray("windows");
        if (w != null) {
            for (int i = 0; i < w.length(); i++) windows.add(rect(w.getJSONArray(i)));
        }

        return new ScenePack(id, spec, palettes, hills, ops, hasSmoke, sx, sy, windows);
    }

    private static int color(JSONObject q, String key) throws JSONException {
        if (!q.has(key)) throw new IllegalArgumentException("palette missing \"" + key + "\"");
        try {
            return Color.parseColor(q.getString(key));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("bad color for \"" + key + "\": " + q.optString(key));
        }
    }

    private static float[] rect(JSONArray a) throws JSONException {
        if (a.length() != 4) throw new IllegalArgumentException("rect needs 4 params");
        return new float[]{(float) a.getDouble(0), (float) a.getDouble(1),
                (float) a.getDouble(2), (float) a.getDouble(3)};
    }

    // ---- builtin valle: the valley exactly as SceneView drew it before packs existed. ----

    private static final String HILL_FAR =
            "M0,720 C240,640 420,660 640,700 C860,740 1040,640 1280,650 C1500,660 1700,720 1920,690 L1920,1080 L0,1080 Z";
    private static final String HILL_MID =
            "M0,820 C260,760 520,790 760,820 C1000,850 1240,760 1500,770 C1700,778 1820,800 1920,790 L1920,1080 L0,1080 Z";
    private static final String HILL_NEAR =
            "M0,940 C300,880 640,900 900,930 C1160,960 1440,900 1700,905 C1800,907 1880,915 1920,920 L1920,1080 L0,1080 Z";

    /** Emergency fallback when even the assets copy fails to parse: never null, never throws. */
    public static ScenePack builtinValle() {
        Map<String, PackPalette> palettes = new HashMap<>();
        palettes.put("dawn", new PackPalette(0xFF2E3A6B, 0xFFC98A9A, 0xFFF6C99A,
                0xFF7C6A93, 0xFF54497A, 0xFF2E2A4E, 0xFFFFC27A, 0.5f, false));
        palettes.put("day", new PackPalette(0xFF4F9CCB, 0xFF93C6E4, 0xFFD8EEF6,
                0xFF8DB59A, 0xFF64957A, 0xFF3F6E55, 0xFF2B3A40, 0f, true));
        palettes.put("dusk", new PackPalette(0xFF2F2350, 0xFFB4506A, 0xFFF2A65A,
                0xFF7A4560, 0xFF4E2C4A, 0xFF24162E, 0xFFFFB35C, 0.75f, false));
        palettes.put("night", new PackPalette(0xFF04060E, 0xFF0B1230, 0xFF1A2550,
                0xFF161D36, 0xFF0E1326, 0xFF070A14, 0xFFFFB35C, 1f, false));
        Path[] hills = new Path[]{PathData.parse(HILL_FAR), PathData.parse(HILL_MID), PathData.parse(HILL_NEAR)};
        List<Op> ops = new ArrayList<>();
        ops.add(new Op(Op.RECT, new float[]{1520, 722, 1610, 778}, null, Op.H3, 0));
        ops.add(new Op(Op.PATH, null, PathData.parse("M1508,726 L1565,684 L1622,726 Z"), Op.H3, 0));
        ops.add(new Op(Op.RECT, new float[]{1588, 690, 1601, 716}, null, Op.H3, 0));
        ops.add(new Op(Op.PATH, null, PathData.parse("M1650,782 L1667,728 L1684,782 Z"), Op.H3, 0));
        ops.add(new Op(Op.PATH, null, PathData.parse("M1690,784 L1709,716 L1728,784 Z"), Op.H3, 0));
        ops.add(new Op(Op.PATH, null, PathData.parse("M1468,780 L1482,738 L1496,780 Z"), Op.H3, 0));
        List<float[]> windows = new ArrayList<>();
        windows.add(new float[]{1540, 738, 1558, 754});
        windows.add(new float[]{1570, 738, 1588, 754});
        return new ScenePack("valle", SPEC, palettes, hills, ops, true, 1595, 680, windows);
    }

    // ---- minimal SVG pathData parser (absolute + relative M L C Z, implicit repeats) ----

    /**
     * PathData: parses the subset of SVG pathData packs may use. Anything else
     * (H V Q S T A, arcs, errors) throws IllegalArgumentException so the pack is rejected.
     */
    static final class PathData {
        private final String s;
        private int i;

        private PathData(String s) {
            this.s = s;
        }

        static Path parse(String d) {
            PathData p = new PathData(d);
            Path path = new Path();
            boolean empty = true;
            try {
                p.skip();
                while (p.i < p.s.length()) {
                    char cmd = p.s.charAt(p.i);
                    if (!isCmd(cmd)) throw new IllegalArgumentException("bad pathData near: " + p.s.substring(p.i));
                    p.i++;
                    if (cmd == 'Z' || cmd == 'z') {
                        path.close();
                        empty = false;
                        continue;
                    }
                    boolean first = true;
                    while (true) {
                        p.skip();
                        if (p.i >= p.s.length() || isCmd(p.s.charAt(p.i))) break;
                        switch (cmd) {
                            case 'M':
                                if (first) {
                                    float x = p.num(), y = p.num();
                                    path.moveTo(x, y);
                                } else {
                                    float x = p.num(), y = p.num();
                                    path.lineTo(x, y);
                                }
                                break;
                            case 'm':
                                if (first) {
                                    float x = p.num(), y = p.num();
                                    path.rMoveTo(x, y);
                                } else {
                                    float x = p.num(), y = p.num();
                                    path.rLineTo(x, y);
                                }
                                break;
                            case 'L':
                                path.lineTo(p.num(), p.num());
                                break;
                            case 'l':
                                path.rLineTo(p.num(), p.num());
                                break;
                            case 'C':
                                path.cubicTo(p.num(), p.num(), p.num(), p.num(), p.num(), p.num());
                                break;
                            case 'c':
                                path.rCubicTo(p.num(), p.num(), p.num(), p.num(), p.num(), p.num());
                                break;
                            default:
                                throw new IllegalArgumentException("unsupported path op: " + cmd);
                        }
                        first = false;
                        empty = false;
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("truncated pathData: " + d);
            }
            if (empty) throw new IllegalArgumentException("empty pathData");
            return path;
        }

        private static boolean isCmd(char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
        }

        private void skip() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ',') i++;
                else break;
            }
        }

        private float num() {
            skip();
            int j = i;
            if (j < s.length() && (s.charAt(j) == '-' || s.charAt(j) == '+')) j++;
            boolean dot = false, dig = false;
            while (j < s.length()) {
                char c = s.charAt(j);
                if (c >= '0' && c <= '9') {
                    dig = true;
                    j++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    j++;
                } else break;
            }
            if (!dig) throw new IllegalArgumentException("number expected near: " + s.substring(i));
            try {
                float v = Float.parseFloat(s.substring(i, j));
                i = j;
                return v;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad number near: " + s.substring(i));
            }
        }
    }
}
