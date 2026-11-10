package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;

/**
 * График активности за день: 24 столбика по часам.
 *
 * Нужен в выезжающей панели статистики: человек тапает день в календаре и видит,
 * в какое время он ходил. Максимальный столбик подсвечен, подписи — каждые три часа.
 */
public class DayHoursView extends View {

    private final float d;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPeak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private int[] hours = new int[24];

    public DayHoursView(Context c) {
        super(c);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());

        bar.setColor(Theme.ACCENT_DEEP);
        bar.setAlpha(150);

        barPeak.setColor(Theme.GOLD);

        line.setColor(Theme.STROKE);
        line.setStrokeWidth(1f * d);

        label.setTextSize(8.5f * d);
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(Theme.FAINT);

        value.setTextSize(9f * d);
        value.setTextAlign(Paint.Align.CENTER);
        value.setColor(Theme.TEXT);
    }

    /** Число с пробелами: 5 942 (без слова «шагов», чтобы влезало над столбиком). */
    private static String num(int n) {
        StringBuilder sb = new StringBuilder();
        String raw = String.valueOf(Math.abs(n));
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (raw.length() - i) % 3 == 0) sb.append(' ');
            sb.append(raw.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    void setHours(int[] h) {
        hours = h == null || h.length < 24 ? new int[24] : h;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        float labelH = 14f * d;
        float chartH = h - labelH;
        float cw = w / 24f;

        // базовая линия
        c.drawLine(0, chartH, w, chartH, line);

        int max = 1;
        int peak = -1;
        for (int i = 0; i < 24; i++) {
            if (hours[i] > max) max = hours[i];
            if (hours[i] == max && max > 1) peak = i;
        }

        for (int i = 0; i < 24; i++) {
            int v = hours[i];
            if (v <= 0) continue;
            float bh = Math.max(2f * d, chartH * (v / (float) max));
            float cx = cw * i + cw / 2f;
            r.set(cx - cw * 0.32f, chartH - bh, cx + cw * 0.32f, chartH - 1f * d);
            c.drawRoundRect(r, cw * 0.28f, cw * 0.28f, i == peak ? barPeak : bar);
        }

        // подписи часов и сумма на пике
        for (int i = 0; i < 24; i += 3) {
            float cx = cw * i + cw / 2f;
            c.drawText(String.valueOf(i), cx, h - 3f * d, label);
        }
        // число над столбиком не пишем: на маленьком графике его всё равно не разобрать,
        // все цифры есть в строке-подсказке под графиком
    }
}
