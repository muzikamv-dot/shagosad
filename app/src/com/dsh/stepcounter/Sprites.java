package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

/** Загрузка спрайтов plant_<вид>_<стадия> с кэшем в памяти. */
public final class Sprites {

    private static LruCache<String, Bitmap> cache;

    private Sprites() { }

    private static LruCache<String, Bitmap> cache() {
        if (cache == null) {
            int max = (int) (Runtime.getRuntime().maxMemory() / 10);
            cache = new LruCache<String, Bitmap>(Math.min(max, 12 * 1024 * 1024)) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
        }
        return cache;
    }

    /** Общий спрайт семени (стадия 0). */
    public static Bitmap seed(Context ctx) {
        Bitmap b = cache().get("seed");
        if (b != null) return b;
        try {
            int rid = ctx.getResources().getIdentifier("plant_seed", "drawable", ctx.getPackageName());
            if (rid != 0) {
                b = BitmapFactory.decodeResource(ctx.getResources(), rid);
                if (b != null) cache().put("seed", b);
            }
        } catch (Exception ignored) { }
        return b;
    }

    public static Bitmap get(Context ctx, int id, int stage) {
        String key = id + "_" + stage;
        Bitmap b = cache().get(key);
        if (b != null) return b;
        try {
            int rid = ctx.getResources().getIdentifier("plant_" + id + "_" + stage,
                    "drawable", ctx.getPackageName());
            if (rid != 0) {
                b = BitmapFactory.decodeResource(ctx.getResources(), rid);
                if (b != null) cache().put(key, b);
            }
        } catch (Exception ignored) { }
        return b;
    }
}
