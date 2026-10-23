package com.cosyhome.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validated store for scene packs. Layout under internal storage:
 * {@code files/packs/<id>/pack.json} (+ optional {@code preview.png}).
 * The builtin {@code valle} ships in assets and needs no store entry.
 *
 * <p>Every entry point validates before touching state: a corrupt pack,
 * a dead URL or a half-pushed folder throws and the previously active
 * pack keeps rendering. Activation is validate-then-persist; imports are
 * validate-then-stage-to-temp-then-rename, so a crash mid-copy can only
 * leave temp dirs behind, never a half-written pack. Packs never execute
 * code: only JSON plus pathData, parsed once per activation via
 * {@link ScenePack#fromJson}. Unknown JSON keys are ignored.
 */
public final class PackStore {

    private static final String TAG = "PackStore";
    private static final String PREFS = "scene_packs";
    private static final String KEY_ACTIVE = "active";
    /** The builtin valley: always present, never deletable, the fallback. */
    public static final String BUILTIN_ID = "valle";
    private static final String PACK_JSON = "pack.json";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int TIMEOUT_MS = 30_000;

    /** Display info for the picker; read leniently, never executed. */
    public static final class PackInfo {
        public final String id, name, author;
        public final boolean builtin, hasPreview;
        PackInfo(String id, String name, String author, boolean builtin, boolean hasPreview) {
            this.id = id;
            this.name = name;
            this.author = author;
            this.builtin = builtin;
            this.hasPreview = hasPreview;
        }
    }

    private final Context app;
    private final File dir;

    public PackStore(Context context) {
        app = context.getApplicationContext();
        dir = new File(app.getFilesDir(), "packs");
    }

    // ---- queries --------------------------------------------------------------------------------

    /** Id of the active pack, defaulting to the builtin valley. Never throws. */
    public String active() {
        try {
            return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ACTIVE, BUILTIN_ID);
        } catch (Exception e) {
            return BUILTIN_ID;
        }
    }

    /** The parsed active pack, ready for {@code SceneView.setPack}. Never throws: {@code valle} on any failure. */
    public ScenePack activePack() {
        try {
            return load(active());
        } catch (Exception e) {
            Log.w(TAG, "active pack failed, falling back to valle", e);
            return ScenePack.builtinValle();
        }
    }

    /** Installed pack ids, builtin first. Never throws. */
    public List<String> list() {
        List<String> ids = new ArrayList<>();
        ids.add(BUILTIN_ID);
        File[] subs = dir.listFiles();
        if (subs == null) return ids;
        List<String> rest = new ArrayList<>();
        for (File f : subs) {
            if (f.isDirectory() && !f.getName().startsWith(".")
                    && new File(f, PACK_JSON).isFile()) rest.add(f.getName());
        }
        Collections.sort(rest);
        for (String id : rest) if (!BUILTIN_ID.equals(id)) ids.add(id);
        return ids;
    }

    /** Display info for the picker, builtin first. Never throws. */
    public List<PackInfo> packs() {
        List<PackInfo> out = new ArrayList<>();
        for (String id : list()) {
            boolean builtin = BUILTIN_ID.equals(id) && !new File(dir, id + "/" + PACK_JSON).isFile();
            out.add(new PackInfo(id, displayName(id), displayAuthor(id), builtin, previewFile(id).isFile()));
        }
        return out;
    }

    /** Preview image for the picker; may not exist. */
    public File previewFile(String id) {
        try {
            return new File(packDir(checkId(id)), "preview.png");
        } catch (IllegalArgumentException e) {
            return new File(dir, "preview.png");
        }
    }

    // ---- activation -------------------------------------------------------------------------------

    /**
     * Make {@code id} the active pack. Parses first: an unknown or corrupt
     * id throws IllegalArgumentException and the previous pack stays active.
     */
    public ScenePack activate(String id) {
        ScenePack pack = load(checkId(id));
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.edit().putString(KEY_ACTIVE, id).commit()) {
            throw new IllegalStateException("could not persist active pack " + id);
        }
        Log.i(TAG, "activated pack " + id);
        return pack;
    }

    /** Remove a non-builtin pack; resets to builtin when it was active. */
    public boolean delete(String id) {
        checkId(id);
        if (BUILTIN_ID.equals(id)) return false;
        deleteTree(new File(dir, id));
        if (id.equals(active())) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_ACTIVE, BUILTIN_ID).apply();
        }
        Log.i(TAG, "deleted pack " + id);
        return true;
    }

    // ---- imports ----------------------------------------------------------------------------------

    /**
     * Import a folder holding a {@code pack.json}. Validates before copying;
     * bad content throws (IllegalArgumentException) and changes nothing.
     * Does not activate; call {@link #activate} after.
     */
    public ScenePack importDir(File src) throws IOException {
        if (src == null) throw new IOException("null source dir");
        File json = new File(src, PACK_JSON);
        if (!json.isFile()) throw new IOException("no pack.json in " + src);
        ScenePack pack = ScenePack.fromJson(readAll(json));   // validates; throws IAE on corrupt
        if (!BUILTIN_ID.equals(pack.id) && !pack.id.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IOException("bad pack id: " + pack.id);
        }
        File staging = stageDir();
        try {
            copyPackFiles(src, staging);
            // Re-read from staging so what we activate is byte-identical to what we ship.
            ScenePack staged = ScenePack.fromJson(readAll(new File(staging, PACK_JSON)));
            swapIn(pack.id, staging);
            staging = null;   // owned by the store now
            Log.i(TAG, "imported pack " + pack.id);
            return staged;
        } finally {
            if (staging != null) deleteTree(staging);
        }
    }

    /**
     * Import from a URL: a {@code pack.json} directly, or a zip holding one at
     * its root. Caps at 5 MB / 30 s timeouts; failures throw and change nothing.
     * Call off the main thread. Does not activate.
     */
    public ScenePack importUrl(String url) throws IOException {
        byte[] body = download(url);
        File staging = stageDir();
        try {
            if (body.length >= 4 && body[0] == 'P' && body[1] == 'K') {
                unzipRootPack(body, staging);
            } else {
                writeAll(new File(staging, PACK_JSON), body);
            }
            return importDir(staging);
        } finally {
            deleteTree(staging);
        }
    }

    // ---- loading ----------------------------------------------------------------------------------

    private ScenePack load(String id) {
        File json = new File(packDir(id), PACK_JSON);
        if (json.isFile()) {
            try {
                return ScenePack.fromJson(readAll(json));
            } catch (IOException e) {
                throw new IllegalArgumentException("unreadable pack " + id, e);
            }
        }
        if (BUILTIN_ID.equals(id)) return loadBuiltinAsset();
        throw new IllegalArgumentException("unknown pack: " + id);
    }

    private ScenePack loadBuiltinAsset() {
        InputStream in = null;
        try {
            in = app.getAssets().open("packs/valle/pack.json");
            return ScenePack.fromJson(streamToString(in));
        } catch (Exception e) {
            Log.w(TAG, "builtin valle asset failed, using code fallback", e);
            return ScenePack.builtinValle();
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---- internals --------------------------------------------------------------------------------

    private File packDir(String id) {
        return new File(dir, checkId(id));
    }

    private static String checkId(String id) {
        if (id == null || (!BUILTIN_ID.equals(id) && !id.matches("[a-z0-9][a-z0-9_-]*"))) {
            throw new IllegalArgumentException("bad pack id: " + id);
        }
        return id;
    }

    private File stageDir() throws IOException {
        File tmp = new File(dir, ".tmp-" + UUID.randomUUID());
        if (!tmp.mkdirs()) throw new IOException("cannot stage in " + dir);
        return tmp;
    }

    /** Validate → stage already validated; atomically replace the live folder. */
    private void swapIn(String id, File staging) throws IOException {
        File target = new File(dir, id);
        File backup = new File(dir, ".bak-" + UUID.randomUUID());
        boolean hadTarget = target.exists();
        try {
            if (hadTarget && !target.renameTo(backup)) throw new IOException("cannot back up " + id);
            if (!staging.renameTo(target)) throw new IOException("cannot publish " + id);
            deleteTree(backup);
        } catch (IOException e) {
            // Best effort rollback: never leave a half-written pack behind.
            deleteTree(target);
            if (hadTarget && backup.exists()) backup.renameTo(target);
            throw e;
        }
    }

    private static void copyPackFiles(File src, File dst) throws IOException {
        File[] files = src.listFiles();
        if (files == null) throw new IOException("cannot list " + src);
        long total = 0;
        for (File f : files) {
            if (!f.isFile()) continue;   // flat pack layout: no subdirs, no symlinks followed
            String name = f.getName();
            if (!PACK_JSON.equals(name) && !"preview.png".equals(name)) continue;
            total += f.length();
            if (total > MAX_BYTES) throw new IOException("pack too large");
            copyFile(f, new File(dst, name));
        }
        if (!new File(dst, PACK_JSON).isFile()) throw new IOException("no pack.json in " + src);
    }

    private static void copyFile(File from, File to) throws IOException {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(from);
            out = new FileOutputStream(to);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException ignored) {
            }
            if (out != null) try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private byte[] download(String urlString) throws IOException {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlString);
            String scheme = url.getProtocol().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IOException("only http(s) pack URLs: " + urlString);
            }
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.connect();
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("pack download failed: HTTP " + c.getResponseCode());
            }
            InputStream in = c.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
                byte[] buf = new byte[8192];
                int total = 0, n;
                while ((n = in.read(buf)) >= 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("pack over 5 MB cap");
                    out.write(buf, 0, n);
                }
                if (total == 0) throw new IOException("empty pack download");
                return out.toByteArray();
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Unzip, keeping only a root-level pack.json (+preview.png); rejects zip-slip and oversize. */
    private static void unzipRootPack(byte[] zip, File dst) throws IOException {
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new java.io.ByteArrayInputStream(zip));
            ZipEntry e;
            long total = 0;
            boolean gotJson = false;
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                try {
                    String name = e.getName();
                    if (e.isDirectory() || name.contains("..") || name.contains("/")
                            || (!PACK_JSON.equals(name) && !"preview.png".equals(name))) continue;
                    ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                    int n;
                    while ((n = zin.read(buf)) >= 0) {
                        total += n;
                        if (total > MAX_BYTES) throw new IOException("zipped pack too large");
                        out.write(buf, 0, n);
                    }
                    writeAll(new File(dst, name), out.toByteArray());
                    if (PACK_JSON.equals(name)) gotJson = true;
                } finally {
                    zin.closeEntry();
                }
            }
            if (!gotJson) throw new IOException("zip has no pack.json at root");
        } finally {
            if (zin != null) try {
                zin.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String readAll(File f) throws IOException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            return streamToString(in);
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String streamToString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static void writeAll(File f, byte[] body) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(body);
        } finally {
            if (out != null) try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private String displayName(String id) {
        return displayField(id, "name", id);
    }

    private String displayAuthor(String id) {
        return displayField(id, "author", "");
    }

    private String displayField(String id, String key, String fallback) {
        File json = new File(packDir(id), PACK_JSON);
        if (json.isFile()) {
            try {
                String v = new JSONObject(readAll(json)).optString(key, fallback);
                if (!v.isEmpty()) return v;
            } catch (Exception ignored) {
            }
        }
        if (BUILTIN_ID.equals(id)) {
            if ("name".equals(key)) return "Valle";
            if ("author".equals(key)) return "Cosy Home";
        }
        return fallback;
    }
}
