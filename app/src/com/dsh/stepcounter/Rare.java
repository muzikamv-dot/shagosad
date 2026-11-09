package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;

import java.util.Locale;

/**
 * Редкие (необычные) цветы: их нельзя вырастить из обычного семени, только купить
 * в магазине за очки. Рисуются готовыми кадрами по стадиям полива.
 *
 * Раньше в коде была одна «Волшебная ромашка» с номером вида −2. Теперь редких
 * цветов может быть сколько угодно: у каждого свой отрицательный номер, своя цена,
 * своё имя, пожелание и свой набор кадров `prefix_stage_01..10`.
 *
 * Номера: обычные виды 0…364, праздничный −1, редкие −2, −3, −4 ….
 */
public final class Rare {

    /** Волшебная ромашка — первый редкий цветок. */
    public static final int MAGIC = -2;
    /** Цвятачёк розовой зари — второй редкий цветок (название владельца). */
    public static final int DAWN = -3;

    /** Все редкие виды: порядок — порядок полки «необычные» в коллекции. */
    static final int[] ALL = {MAGIC, DAWN};

    /**
     * Сколько кадров у редкого цветка. Стадии игры идут 0…10, поэтому кадров 11:
     * иначе последняя стадия рисовалась бы тем же кадром, что и предыдущая.
     */
    public static final int STAGES = 11;

    private static final SparseArray<Bitmap> CACHE = new SparseArray<>();
    /** Кэш доли высоты растения в кадре. */
    private static final SparseArray<Float> RATIO = new SparseArray<>();

    private Rare() { }

    public static boolean isRare(int species) {
        return species == MAGIC || species == DAWN;
    }

    public static int price(int species) {
        return 1000;
    }

    public static String name(int species) {
        if (species == DAWN) return "Цвятачёк розовой зари";
        return "Волшебная ромашка";
    }

    public static String wish(int species) {
        if (species == DAWN) {
            return "Пусть каждый рассвет приносит что-то нежное — как этот цветок.";
        }
        return "Пусть в твоём саду случаются чудеса — маленькие, но настоящие.";
    }

    /** Основа имён файлов: magic / dawn. */
    private static String base(int species) {
        return species == DAWN ? "dawn" : "magic";
    }

    /** Префикс кадров стадий: magic_stage_ / dawn_stage_. */
    private static String prefix(int species) {
        return base(species) + "_stage_";
    }

    /** Отдельный квадратный значок семечка, если нарисован (`<вид>_seed_icon`). */
    public static int seedIcon(Context ctx, int species) {
        if (ctx == null || !isRare(species)) return 0;
        return ctx.getResources().getIdentifier(
                base(species) + "_seed_icon", "drawable", ctx.getPackageName());
    }
    /** Кадр стадии: 0 — только семечко, 10 — раскрытый цветок. */
    public static Bitmap art(Context ctx, int species, int stage) {
        if (ctx == null || !isRare(species)) return null;
        int index = Math.max(1, Math.min(STAGES, stage + 1));   // 0 → кадр 1, 10 → кадр 11
        int key = species * 100 + index;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        int res = ctx.getResources().getIdentifier(
                String.format(Locale.US, prefix(species) + "%02d", index),
                "drawable", ctx.getPackageName());
        if (res == 0) return null;
        b = BitmapFactory.decodeResource(ctx.getResources(), res);
        if (b != null) CACHE.put(key, b);
        return b;
    }

    /** Второй вариант последнего кадра: лапки поросёнка в другой фазе (для болтания). */
    public static Bitmap artAlt(Context ctx, int species) {
        if (ctx == null || !isRare(species)) return null;
        int key = species * 1000 + 111;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        int res = ctx.getResources().getIdentifier(
                String.format(Locale.US, prefix(species) + "%02db", STAGES),
                "drawable", ctx.getPackageName());
        if (res == 0) return null;
        b = BitmapFactory.decodeResource(ctx.getResources(), res);
        if (b != null) CACHE.put(key, b);
        return b;
    }

    /**
     * Какая доля кадра занята самим растением по высоте (от верхушки до земли).
     * Нужна, чтобы растение росло РАВНОМЕРНО: кадры нарисованы с разной высотой,
     * и без этой поправки поздние стадии выглядят ниже предыдущих.
     */
    public static float contentRatio(Context ctx, int species, int stage) {
        int key = species * 100 + Math.max(0, Math.min(STAGES, stage));
        Float cached = RATIO.get(key);
        if (cached != null) return cached;
        Bitmap b = art(ctx, species, stage);
        if (b == null) return 1f;
        int w = b.getWidth(), h = b.getHeight();
        int[] row = new int[w];
        int top = 0;
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            boolean found = false;
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) > 20) { found = true; break; }
            }
            if (found) { top = y; break; }
        }
        float ratio = (h - top) / (float) h;
        if (ratio <= 0.05f) ratio = 1f;
        RATIO.put(key, ratio);
        return ratio;
    }
    /** Рисует стадию в прямоугольник: растение стоит на его нижней линии. */
    public static void draw(Canvas c, Context ctx, int species, int stage, RectF dst) {
        draw(c, ctx, species, stage, dst, 255);
    }

    /** То же, но с прозрачностью: нужно для анимации перекопки и увядания. */
    public static void draw(Canvas c, Context ctx, int species, int stage, RectF dst, int alpha) {
        Bitmap b = art(ctx, species, stage);
        if (b == null || dst.width() <= 1 || dst.height() <= 1) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(Math.max(0, Math.min(255, alpha)));
        c.drawBitmap(b, null, dst, p);
    }

    /** Пропорции кадра: нужны, чтобы вписать растение в окно без искажений. */
    public static float aspect() { return 512f / 640f; }
}
