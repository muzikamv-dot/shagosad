package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Мини-график шагов за последние дни. Масштаб — по фактическим данным, а не по цели. */
public class HistoryView extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barToday = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barEmpty = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[] values = new int[0];
    private String[] labels = new String[0];
    private int goal = 10000;
    private float density;

    public HistoryView(Context ctx) {
        super(ctx);
        density = getResources().getDisplayMetrics().density;

        barEmpty.setColor(Theme.SURFACE_HI);

        label.setColor(Theme.FAINT);
        label.setTextSize(10f * density);
        label.setTextAlign(Paint.Align.CENTER);

        value.setColor(Theme.ACCENT_SOFT);
        value.setTextSize(9.5f * density);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        goalLine.setColor(Theme.STROKE);
        goalLine.setStrokeWidth(1f * density);
    }

    public void setData(int[] values, String[] labels, int goal) {
        this.values = values == null ? new int[0] : values;
        this.labels = labels == null ? new String[0] : labels;
        this.goal = Math.max(1, goal);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (values.length == 0) return;

        float w = getWidth(), h = getHeight();
        float labelH = 15f * density;
        float chartH = h - labelH;
        float slot = w / values.length;
        float barW = Math.min(slot * 0.5f, 22f * density);

        // масштаб по максимуму, но не мельче 1000 шагов — иначе столбики не видны
        int maxValue = 0;
        for (int v : values) maxValue = Math.max(maxValue, v);
        float scaleMax = Math.max(1000f, maxValue * 1.15f);
        float scale = chartH / scaleMax;

        for (int i = 0; i < values.length; i++) {
            float cx = slot * i + slot / 2f;
            boolean today = i == values.length - 1;

            if (values[i] <= 0) {
                rect.set(cx - barW / 2f, chartH - 3f * density, cx + barW / 2f, chartH);
                c.drawRoundRect(rect, barW / 2f, barW / 2f, barEmpty);
            } else {
                float bh = Math.max(4f * density, values[i] * scale);
                float top = chartH - bh;
                rect.set(cx - barW / 2f, top, cx + barW / 2f, chartH);

                Paint p = bar;
                LinearGradient lg = new LinearGradient(0, top, 0, chartH,
                        today ? Theme.ACCENT : Theme.ACCENT_DEEP,
                        Theme.ACCENT_DEEP,
                        Shader.TileMode.CLAMP);
                p.setShader(lg);
                c.drawRoundRect(rect, barW / 2f, barW / 2f, p);

                float labelY = top - 5f * density;
                // если столбик высокий и подпись не помещается над ним, рисуем её
                // внутри столбика тёмным — светлая на зелёном не читалась
                boolean inside = false;
                if (labelY < 12f * density) { labelY = top + 14f * density; inside = true; }
                value.setColor(inside ? Theme.ON_ACCENT : Theme.ACCENT_SOFT);
                c.drawText(shortValue(values[i]), cx, labelY, value);
            }

            if (i < labels.length) {
                label.setColor(today ? Theme.ACCENT_SOFT : Theme.FAINT);
                c.drawText(labels[i], cx, Ui.baseline(label, h - labelH / 2f), label);
            }
        }

        // Линия цели — поверх столбиков и пунктиром. Сплошная линия, нарисованная
        // до столбиков, «врезалась» в сегодняшний столбик и читалась как стык.
        if (goal <= scaleMax) {
            float gy = chartH - goal * scale;
            if (gy > 4f * density) {
                goalLine.setPathEffect(new DashPathEffect(
                        new float[]{5f * density, 5f * density}, 0f));
                c.drawLine(0, gy, w, gy, goalLine);
                goalLine.setPathEffect(null);
            }
        }
    }

    private String shortValue(int v) {
        if (v >= 10000) return Math.round(v / 1000f) + "k";
        if (v >= 1000) return String.format(java.util.Locale.US, "%.1fk", v / 1000f);
        return String.valueOf(v);
    }
}
