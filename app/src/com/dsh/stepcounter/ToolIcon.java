package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Иконки инструментов: тяпка и лопата.
 * Рисуются кодом, чтобы не тащить картинки и одинаково смотреться в любой теме.
 */
public final class ToolIcon {

    public static final int HOE = 0;
    public static final int SHOVEL = 1;

    private static final int WOOD = 0xFF9A6B45;
    private static final int WOOD_DARK = 0xFF7A5231;
    private static final int METAL = 0xFFB9C6CF;
    private static final int METAL_DARK = 0xFF8A98A3;

    private ToolIcon() { }

    /** cx, cy — центр, size — сторона квадрата. */
    public static void draw(Canvas c, int kind, float cx, float cy, float size) {
        float x = cx - size / 2f;
        float y = cy - size / 2f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        if (kind == SHOVEL) {
            // черенок
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.075f);
            p.setColor(WOOD);
            c.drawLine(x + size * 0.5f, y + size * 0.14f,
                    x + size * 0.5f, y + size * 0.62f, p);
            p.setColor(WOOD_DARK);
            p.setStrokeWidth(size * 0.022f);
            c.drawLine(x + size * 0.53f, y + size * 0.20f,
                    x + size * 0.53f, y + size * 0.58f, p);

            // ручка-скоба
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.07f);
            p.setColor(WOOD);
            RectF grip = new RectF(x + size * 0.38f, y + size * 0.02f,
                    x + size * 0.62f, y + size * 0.20f);
            c.drawArc(grip, 200, 140, false, p);

            // штык
            Path blade = new Path();
            blade.moveTo(x + size * 0.33f, y + size * 0.62f);
            blade.lineTo(x + size * 0.67f, y + size * 0.62f);
            blade.lineTo(x + size * 0.62f, y + size * 0.90f);
            blade.lineTo(x + size * 0.50f, y + size * 0.98f);
            blade.lineTo(x + size * 0.38f, y + size * 0.90f);
            blade.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(METAL);
            c.drawPath(blade, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.035f);
            p.setColor(METAL_DARK);
            c.drawPath(blade, p);
            // блик
            p.setStrokeWidth(size * 0.03f);
            p.setColor(0x66FFFFFF);
            c.drawLine(x + size * 0.46f, y + size * 0.68f,
                    x + size * 0.44f, y + size * 0.88f, p);

        } else {
            // черенок тяпки — по диагонали
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.08f);
            p.setColor(WOOD);
            c.drawLine(x + size * 0.16f, y + size * 0.86f,
                    x + size * 0.74f, y + size * 0.24f, p);
            p.setColor(WOOD_DARK);
            p.setStrokeWidth(size * 0.022f);
            c.drawLine(x + size * 0.22f, y + size * 0.80f,
                    x + size * 0.70f, y + size * 0.28f, p);

            // лезвие — поперёк черенка, слегка загнутое
            Path blade = new Path();
            blade.moveTo(x + size * 0.74f, y + size * 0.26f);
            blade.quadTo(x + size * 0.92f, y + size * 0.40f,
                    x + size * 0.82f, y + size * 0.60f);
            blade.lineTo(x + size * 0.66f, y + size * 0.52f);
            blade.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(METAL);
            c.drawPath(blade, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.035f);
            p.setColor(METAL_DARK);
            c.drawPath(blade, p);
        }
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
