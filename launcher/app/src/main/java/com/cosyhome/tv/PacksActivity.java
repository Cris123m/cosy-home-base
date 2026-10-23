package com.cosyhome.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Scene pack picker: installed packs with preview + name + author.
 * Click activates immediately (validate-then-persist; Home shows it on return).
 * Long-press deletes a non-builtin pack after confirmation; the builtin valley
 * cannot be deleted. Previews decode sampled to 320 px so the list stays light.
 */
public class PacksActivity extends Activity {

    private static final String TAG = "PacksActivity";
    private static final int PREVIEW_W = 160, PREVIEW_H = 90, PREVIEW_MAX = 320;

    private PackStore store;
    private LinearLayout list;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new PackStore(this);
        density = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        int m = dp(48);
        pp.setMargins(m, dp(27), m, dp(27));
        root.addView(page, pp);

        TextView title = new TextView(this);
        title.setText(R.string.packs_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(24f);
        title.setTypeface(getResources().getFont(R.font.outfit));
        page.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        sp.topMargin = dp(16);
        page.addView(scroll, sp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildList();
    }

    /** Rebuild the rows from the store; the builtin valley is always first. Never throws. */
    private void buildList() {
        list.removeAllViews();
        List<PackStore.PackInfo> packs = store.packs();
        String active;
        try {
            active = store.active();
        } catch (Exception e) {
            active = PackStore.BUILTIN_ID;
        }
        View first = null;
        for (PackStore.PackInfo info : packs) {
            View row = row(info, info.id.equals(active));
            list.addView(row);
            if (first == null) first = row;
        }
        if (first != null) {
            final View f = first;
            list.post(f::requestFocus);
        }
    }

    private View row(PackStore.PackInfo info, boolean isActive) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        int pad = dp(12);
        row.setPadding(pad, pad, pad, pad);
        GradientDrawable rest = rounded(0x14FFFFFF), focused = rounded(0x33FFFFFF);
        row.setBackground(rest);
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? focused : rest);
            v.setScaleX(has ? 1.02f : 1f);
            v.setScaleY(has ? 1.02f : 1f);
        });

        FrameLayout art = new FrameLayout(this);
        Bitmap bm = preview(info.id);
        if (bm != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.addView(iv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            View ph = new View(this);
            ph.setBackgroundColor(0xFF2A3340);
            art.addView(ph, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        row.addView(art, new LinearLayout.LayoutParams(dp(PREVIEW_W), dp(PREVIEW_H)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(dp(16));
        row.addView(text, tp);

        TextView name = new TextView(this);
        name.setText(info.name + (isActive ? " \u2713" : ""));
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(17f);
        name.setTypeface(getResources().getFont(R.font.outfit));
        text.addView(name);

        TextView author = new TextView(this);
        author.setText(info.author);
        author.setTextColor(0xB3FFFFFF);
        author.setTextSize(13f);
        text.addView(author);

        // Immediate activation: one validate-then-persist, one still rebuild on return to Home.
        row.setOnClickListener(v -> {
            try {
                store.activate(info.id);
                finish();
            } catch (Exception e) {
                Log.w(TAG, "activate failed: " + info.id, e);
                Toast.makeText(this, getString(R.string.pack_activate_failed, info.name),
                        Toast.LENGTH_SHORT).show();
            }
        });
        row.setOnLongClickListener(v -> {
            if (PackStore.BUILTIN_ID.equals(info.id)) {
                Toast.makeText(this, R.string.pack_cannot_delete, Toast.LENGTH_SHORT).show();
                return true;
            }
            new AlertDialog.Builder(this)
                    .setTitle(info.name)
                    .setMessage(getString(R.string.pack_delete_confirm, info.name))
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        try {
                            if (store.delete(info.id)) {
                                Toast.makeText(this, getString(R.string.pack_deleted, info.name),
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "delete failed: " + info.id, e);
                            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                        }
                        buildList();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
        return row;
    }

    /** Preview sampled down so a phone photo never costs more than ~320 px wide. Null when absent. */
    private Bitmap preview(String id) {
        File f;
        try {
            f = store.previewFile(id);
        } catch (Exception e) {
            return null;
        }
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int s = 1;
        while (o.outWidth / (s * 2) >= PREVIEW_MAX) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (OutOfMemoryError | Exception e) {
            Log.w(TAG, "preview decode failed: " + id, e);
            return null;
        }
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(12 * density);
        d.setColor(color);
        return d;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
