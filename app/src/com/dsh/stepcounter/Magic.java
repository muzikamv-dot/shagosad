package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;

/**
 * «Волшебная ромашка» — необычный цветок из магазина.
 *
 * Отличие от обычного вида: рисуется готовыми кадрами `magic_stage_01..10.webp`
 * (10 кадров = 10 поливов), а в данных игры живёт как вид с номером −2 —
 * так он не путается с 365 обычными видами и с праздничным цветком (−1).
 */
public final class Magic {

    /** Номер вида в сохранении: не пересекается с обычными (0…364) и ивентом (−1). */
    public static final int SPECIES = -2;

    /** Цена семечка в магазине. */
    public static final int PRICE = 1000;

    /** Сколько поливов до цветка — столько же кадров. */
    public static final int STAGES = 10;

    public static final String NAME = "Волшебная ромашка";

    public static final String WISH =
            "Пусть в твоём саду случаются чудеса — маленькие, но настоящие.";

    private static final SparseArray<Bitmap> CACHE = new SparseArray<>();

    private Magic() { }

    /** Кадр стадии: 0 — только семечко, 10 — раскрытый цветок. */
    public static Bitmap art(Context ctx, int stage) {
        if (ctx == null) return null;
        int index = Math.max(1, Math.min(STAGES, stage + 1));
        Bitmap b = CACHE.get(index);
        if (b != null) return b;
        int res = ctx.getResources().getIdentifier(
                String.format(java.util.Locale.US, "magic_stage_%02d", index),
                "drawable", ctx.getPackageName());
        if (res == 0) return null;
        b = BitmapFactory.decodeResource(ctx.getResources(), res);
        if (b != null) CACHE.put(index, b);
        return b;
    }

    /** Рисует стадию в прямоугольник: растение стоит на его нижней линии. */
    public static void draw(Canvas c, Context ctx, int stage, RectF dst) {
        draw(c, ctx, stage, dst, 255);
    }

    /** То же, но с прозрачностью: нужно для анимации перекопки и увядания. */
    public static void draw(Canvas c, Context ctx, int stage, RectF dst, int alpha) {
        Bitmap b = art(ctx, stage);
        if (b == null || dst.width() <= 1 || dst.height() <= 1) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(Math.max(0, Math.min(255, alpha)));
        c.drawBitmap(b, null, dst, p);
    }

    /** Пропорции кадра: нужны, чтобы вписать растение в окно без искажений. */
    public static float aspect() { return 512f / 640f; }
}
