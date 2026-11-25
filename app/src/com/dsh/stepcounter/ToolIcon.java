package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Иконки инструментов: тяпка, лопата и садовник.
 * Пока картинок нет — рисуются кодом, чтобы одинаково смотреться в любой теме.
 * Как только в `res/drawable-nodpi` появятся `tool_hoe`, `tool_shovel`,
 * `tool_gardener` (WebP с прозрачностью), вьюха возьмёт их вместо кода.
 *
 * Канон для тяпки: лезвие внизу слева, черенок уходит вверх вправо — тогда
 * в анимации достаточно повернуть её вокруг лезвия, и получится удар по земле.
 */
public final class ToolIcon {

    public static final int HOE = 0;
    public static final int SHOVEL = 1;
    public static final int GARDENER = 2;

    private static final int WOOD = 0xFFA6743F;
    private static final int WOOD_DARK = 0xFF7A5231;
    private static final int METAL = 0xFFC7D2DA;
    private static final int METAL_DARK = 0xFF7E8C97;
    private static final int METAL_LIGHT = 0xFFEFF5F9;
    private static final int LEAF = 0xFF5FBF86;
    private static final int LEAF_DARK = 0xFF3E8E62;
    private static final int SKIN = 0xFFF3C9A2;
    private static final int HAT = 0xFFE7C57A;

    private ToolIcon() { }

    /** Имя ресурса с готовой картинкой инструмента. */
    private static String resourceName(int kind) {
        if (kind == SHOVEL) return "tool_shovel";
        if (kind == GARDENER) return "tool_gardener";
        return "tool_hoe";
    }

    /** Готовая картинка инструмента, если художник её дал. */
    public static android.graphics.Bitmap art(Context ctx, int kind) {
        if (ctx == null) return null;
        int res = ctx.getResources().getIdentifier(resourceName(kind),
                "drawable", ctx.getPackageName());
        if (res == 0) return null;
        try {
            return android.graphics.BitmapFactory.decodeResource(ctx.getResources(), res);
        } catch (Exception e) {
            return null;
        }
    }

    /** cx, cy — центр, size — сторона квадрата. */
    public static void draw(Canvas c, int kind, float cx, float cy, float size) {
        if (kind == SHOVEL) drawShovel(c, cx, cy, size);
        else if (kind == GARDENER) drawGardener(c, cx, cy, size);
        else drawHoe(c, cx, cy, size);
    }

    /**
     * Садовник: человечек в соломенной шляпе с лейкой. Стоит на земле, поэтому
     * в анимации его можно просто покачивать, а не поворачивать.
     */
    public static void drawGardener(Canvas c, float cx, float cy, float size) {
        float x = cx - size / 2f;
        float y = cy - size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);

        // ручка лейки (слева) — рисуем до тела, чтобы стебель перекрыл её край
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.05f);
        p.setColor(METAL_DARK);
        c.drawLine(x + size * 0.20f, y + size * 0.58f,
                x + size * 0.10f, y + size * 0.74f, p);

        // корпус лейки и носик
        p.setStyle(Paint.Style.FILL);
        p.setColor(METAL);
        RectF can = new RectF(x + size * 0.02f, y + size * 0.72f,
                x + size * 0.20f, y + size * 0.88f);
        c.drawRoundRect(can, size * 0.03f, size * 0.03f, p);
        Path spout = new Path();
        spout.moveTo(x + size * 0.18f, y + size * 0.74f);
        spout.lineTo(x + size * 0.28f, y + size * 0.70f);
        spout.lineTo(x + size * 0.28f, y + size * 0.75f);
        spout.lineTo(x + size * 0.18f, y + size * 0.78f);
        spout.close();
        c.drawPath(spout, p);

        // тело: зелёный фартук-кафтан
        p.setColor(LEAF);
        RectF body = new RectF(x + size * 0.34f, y + size * 0.52f,
                x + size * 0.68f, y + size * 0.92f);
        c.drawRoundRect(body, size * 0.14f, size * 0.14f, p);
        p.setColor(LEAF_DARK);
        RectF apron = new RectF(x + size * 0.42f, y + size * 0.66f,
                x + size * 0.60f, y + size * 0.92f);
        c.drawRoundRect(apron, size * 0.06f, size * 0.06f, p);

        // руки
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.055f);
        p.setColor(SKIN);
        c.drawLine(x + size * 0.36f, y + size * 0.60f,
                x + size * 0.22f, y + size * 0.66f, p);
        c.drawLine(x + size * 0.66f, y + size * 0.60f,
                x + size * 0.76f, y + size * 0.68f, p);

        // голова
        p.setStyle(Paint.Style.FILL);
        p.setColor(SKIN);
        c.drawCircle(x + size * 0.51f, y + size * 0.42f, size * 0.135f, p);

        // глаза и улыбка
        p.setColor(0xFF243B33);
        c.drawCircle(x + size * 0.465f, y + size * 0.41f, size * 0.017f, p);
        c.drawCircle(x + size * 0.555f, y + size * 0.41f, size * 0.017f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.018f);
        RectF smile = new RectF(x + size * 0.47f, y + size * 0.44f,
                x + size * 0.55f, y + size * 0.49f);
        c.drawArc(smile, 20, 140, false, p);

        // соломенная шляпа: поля и тулья
        p.setStyle(Paint.Style.FILL);
        p.setColor(HAT);
        RectF brim = new RectF(x + size * 0.30f, y + size * 0.27f,
                x + size * 0.72f, y + size * 0.36f);
        c.drawOval(brim, p);
        p.setColor(0xFFD9B063);
        RectF crown = new RectF(x + size * 0.40f, y + size * 0.16f,
                x + size * 0.62f, y + size * 0.31f);
        c.drawRoundRect(crown, size * 0.05f, size * 0.05f, p);

        // земля под ногами
        p.setColor(0xFF8A6A46);
        RectF ground = new RectF(x + size * 0.28f, y + size * 0.91f,
                x + size * 0.76f, y + size * 0.97f);
        c.drawRoundRect(ground, size * 0.03f, size * 0.03f, p);
    }

    /** Лопата: черенок с ручкой-скобой и штык с бликом. */
    public static void drawShovel(Canvas c, float cx, float cy, float size) {
        float x = cx - size / 2f;
        float y = cy - size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        // скоба-ручка
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.085f);
        p.setColor(WOOD_DARK);
        RectF grip = new RectF(x + size * 0.34f, y + size * 0.02f,
                x + size * 0.66f, y + size * 0.26f);
        c.drawArc(grip, 195, 150, false, p);

        // черенок
        p.setStrokeWidth(size * 0.085f);
        p.setColor(WOOD);
        c.drawLine(x + size * 0.50f, y + size * 0.20f,
                x + size * 0.50f, y + size * 0.58f, p);
        p.setStrokeWidth(size * 0.026f);
        p.setColor(0x55FFFFFF);
        c.drawLine(x + size * 0.47f, y + size * 0.26f,
                x + size * 0.47f, y + size * 0.54f, p);

        // штык: трапеция с заострённым низом
        Path blade = new Path();
        blade.moveTo(x + size * 0.33f, y + size * 0.58f);
        blade.quadTo(x + size * 0.50f, y + size * 0.55f, x + size * 0.67f, y + size * 0.58f);
        blade.lineTo(x + size * 0.63f, y + size * 0.86f);
        blade.quadTo(x + size * 0.50f, y + size * 1.00f, x + size * 0.37f, y + size * 0.86f);
        blade.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(METAL);
        c.drawPath(blade, p);

        // затемнение по правому краю и блик по левому
        Path shade = new Path();
        shade.moveTo(x + size * 0.55f, y + size * 0.58f);
        shade.lineTo(x + size * 0.67f, y + size * 0.58f);
        shade.lineTo(x + size * 0.63f, y + size * 0.86f);
        shade.quadTo(x + size * 0.56f, y + size * 0.95f, x + size * 0.55f, y + size * 0.86f);
        shade.close();
        p.setColor(METAL_DARK);
        c.drawPath(shade, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.03f);
        p.setColor(METAL_LIGHT);
        c.drawLine(x + size * 0.42f, y + size * 0.64f,
                x + size * 0.40f, y + size * 0.82f, p);
        p.setColor(METAL_DARK);
        p.setStrokeWidth(size * 0.035f);
        p.setStyle(Paint.Style.STROKE);
        c.drawPath(blade, p);
    }

    /** Тяпка: черенок по диагонали, лезвие-лопаточка внизу слева. */
    public static void drawHoe(Canvas c, float cx, float cy, float size) {
        float x = cx - size / 2f;
        float y = cy - size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        // черенок
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.085f);
        p.setColor(WOOD);
        c.drawLine(x + size * 0.22f, y + size * 0.78f,
                x + size * 0.82f, y + size * 0.16f, p);
        p.setStrokeWidth(size * 0.026f);
        p.setColor(0x55FFFFFF);
        c.drawLine(x + size * 0.26f, y + size * 0.74f,
                x + size * 0.78f, y + size * 0.20f, p);

        // металлическая обойма у основания лезвия
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.10f);
        p.setColor(METAL_DARK);
        c.drawLine(x + size * 0.26f, y + size * 0.74f,
                x + size * 0.33f, y + size * 0.66f, p);

        // лезвие: широкая лопатка, почти перпендикулярно черенку
        Path blade = new Path();
        blade.moveTo(x + size * 0.10f, y + size * 0.80f);
        blade.lineTo(x + size * 0.40f, y + size * 0.92f);
        blade.lineTo(x + size * 0.46f, y + size * 0.76f);
        blade.lineTo(x + size * 0.16f, y + size * 0.64f);
        blade.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(METAL);
        c.drawPath(blade, p);

        // тень по нижней кромке и блик
        Path shade = new Path();
        shade.moveTo(x + size * 0.16f, y + size * 0.72f);
        shade.lineTo(x + size * 0.40f, y + size * 0.92f);
        shade.lineTo(x + size * 0.44f, y + size * 0.83f);
        shade.lineTo(x + size * 0.20f, y + size * 0.66f);
        shade.close();
        p.setColor(METAL_DARK);
        c.drawPath(shade, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.03f);
        p.setColor(METAL_LIGHT);
        c.drawLine(x + size * 0.15f, y + size * 0.74f,
                x + size * 0.33f, y + size * 0.81f, p);
        p.setColor(METAL_DARK);
        p.setStrokeWidth(size * 0.035f);
        c.drawPath(blade, p);
    }

    /** Небольшая вьюха, чтобы вставлять иконку в обычные экраны. */
    public static final class View extends android.view.View {
        private final int kind;
        private android.graphics.Bitmap art;

        public View(Context c, int kind) {
            super(c);
            this.kind = kind;
            art = ToolIcon.art(c, kind);      // готовая картинка, если она есть
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight()) * 0.92f;
            if (art != null) {
                android.graphics.RectF dst = new android.graphics.RectF(
                        getWidth() / 2f - size / 2f, getHeight() / 2f - size / 2f,
                        getWidth() / 2f + size / 2f, getHeight() / 2f + size / 2f);
                canvas.drawBitmap(art, null, dst,
                        new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                return;
            }
            ToolIcon.draw(canvas, kind, getWidth() / 2f, getHeight() / 2f, size);
        }
    }
}
