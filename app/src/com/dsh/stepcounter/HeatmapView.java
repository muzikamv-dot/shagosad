package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

/**
 * Теплокарта шагов: по клетке на день, 13 недель.
 * Чем больше шагов относительно нормы — тем плотнее цвет. Пустой день — контур.
 */
public class HeatmapView extends View {

    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int[] data = new int[0];
    private int goal = 10000;
    private final float d;

    public HeatmapView(Context c) {
        super(c);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());
        cell.setStyle(Paint.Style.FILL);
        empty.setStyle(Paint.Style.STROKE);
        empty.setStrokeWidth(1f * d);
        empty.setColor(Theme.CARD_STROKE);
        mark.setStyle(Paint.Style.FILL);
        mark.setColor(Theme.GOLD);
    }

    void setData(int[] days, int goalSteps) {
        data = days == null ? new int[0] : days;
        goal = Math.max(1000, goalSteps);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = data.length;
        if (n == 0) return;

        int weeks = (n + 6) / 7;
        float pad = 2 * d;
        float gridW = getWidth();
        float gridH = getHeight();
        float cw = (gridW - pad * (weeks - 1)) / weeks;
        float ch = (gridH - pad * 6) / 7;
        float size = Math.min(cw, ch);
        float left = (gridW - (size * weeks + pad * (weeks - 1))) / 2f;

        for (int i = 0; i < n; i++) {
            int week = i / 7;
            int day = i % 7;
            float x = left + week * (size + pad);
            float y = day * (size + pad);
            r.set(x, y, x + size, y + size);
            float radius = size * 0.28f;

            int v = data[i];
            if (v <= 0) {
                c.drawRoundRect(r, radius, radius, empty);
            } else {
                float k = Math.min(1f, v / (float) goal);
                cell.setColor(blend(Theme.SURFACE_HI, Theme.ACCENT, 0.25f + 0.75f * k));
                c.drawRoundRect(r, radius, radius, cell);
            }
            // сегодня — золотая точка
            if (i == n - 1) {
                float dot = Math.max(1.6f * d, size * 0.13f);
                c.drawCircle(r.centerX(), r.centerY(), dot, mark);
            }
        }
    }

    private int blend(int from, int to, float k) {
        int a = (int) (((from >> 24) & 0xFF) * (1 - k) + ((to >> 24) & 0xFF) * k);
        int r1 = (int) (((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int g1 = (int) (((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b1 = (int) ((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return (a << 24) | (r1 << 16) | (g1 << 8) | b1;
    }
}
