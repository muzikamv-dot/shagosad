package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Иконки инструментов: тяпка и лопата.
 * Рисуются кодом, чтобы не тащить картинки и одинаково смотреться в любой теме.
 *
 * Канон для тяпки: лезвие внизу слева, черенок уходит вверх вправо — тогда
 * в анимации достаточно повернуть её вокруг лезвия, и получится удар по земле.
 */
public final class ToolIcon {

    public static final int HOE = 0;
    public static final int SHOVEL = 1;

    private static final int WOOD = 0xFFA6743F;
    private static final int WOOD_DARK = 0xFF7A5231;
    private static final int METAL = 0xFFC7D2DA;
    private static final int METAL_DARK = 0xFF7E8C97;
    private static final int METAL_LIGHT = 0xFFEFF5F9;

    private ToolIcon() { }

    /** cx, cy — центр, size — сторона квадрата. */
    public static void draw(Canvas c, int kind, float cx, float cy, float size) {
        if (kind == SHOVEL) drawShovel(c, cx, cy, size);
        else drawHoe(c, cx, cy, size);
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

        public View(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ToolIcon.draw(canvas, kind, getWidth() / 2f, getHeight() / 2f,
                    Math.min(getWidth(), getHeight()) * 0.92f);
        }
    }
}
