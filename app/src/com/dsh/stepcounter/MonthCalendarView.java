package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Настоящий календарь месяца: дни недели, числа, заливка по шагам.
 *
 * Пустые квадратики-теплокарта заменили на календарь — человеку проще:
 * видно число, день недели и где были пропуски.
 */
public class MonthCalendarView extends View {

    private final Paint cellBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numMuted = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint week = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint today = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final float d;
    private int year;
    private int month;
    private int[] daySteps = new int[0];
    private int goal = 10000;

    public MonthCalendarView(Context c) {
        super(c);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());

        num.setTextSize(13f * d);
        num.setTextAlign(Paint.Align.CENTER);
        num.setColor(Theme.TEXT);

        numMuted.setTextSize(13f * d);
        numMuted.setTextAlign(Paint.Align.CENTER);
        numMuted.setColor(Theme.FAINT);

        week.setTextSize(9.5f * d);
        week.setTextAlign(Paint.Align.CENTER);
        week.setColor(Theme.FAINT);
        week.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        cellRing.setStyle(Paint.Style.STROKE);
        cellRing.setStrokeWidth(1.6f * d);
        cellRing.setColor(Theme.GOLD);

        today.setStyle(Paint.Style.FILL);

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH);
    }

    /** Данные месяца: массив по дням (индекс 0 — первое число). */
    void setMonth(int year, int month, int[] steps, int goalSteps) {
        this.year = year;
        this.month = month;
        this.daySteps = steps == null ? new int[0] : steps;
        this.goal = Math.max(1000, goalSteps);
        invalidate();
    }

    int year() { return year; }
    int month() { return month; }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        // понедельник — первый столбец: Calendar.MONDAY = 2
        int firstCol = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;

        float w = getWidth();
        float h = getHeight();
        float headH = 20f * d;
        float cw = w / 7f;
        float ch = (h - headH) / 6f;
        float size = Math.min(cw, ch) * 0.82f;

        String[] names = {"пн", "вт", "ср", "чт", "пт", "сб", "вс"};
        for (int i = 0; i < 7; i++) {
            float cx = cw * i + cw / 2f;
            c.drawText(names[i], cx, headH - 6f * d, week);
        }

        Calendar now = Calendar.getInstance();
        boolean thisMonth = now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month;
        int todayDay = now.get(Calendar.DAY_OF_MONTH);

        for (int day = 1; day <= days; day++) {
            int idx = firstCol + day - 1;
            int col = idx % 7;
            int row = idx / 7;
            float cx = cw * col + cw / 2f;
            float cy = headH + ch * row + ch / 2f;
            r.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            float radius = size * 0.34f;

            int steps = day < daySteps.length ? daySteps[day - 1] : 0;
            boolean future = thisMonth && day > todayDay;
            boolean isToday = thisMonth && day == todayDay;

            if (steps > 0) {
                float k = Math.min(1f, steps / (float) goal);
                cellBg.setColor(tint(Theme.ACCENT, 0.16f + 0.55f * k));
                c.drawRoundRect(r, radius, radius, cellBg);
            }
            if (isToday) {
                c.drawRoundRect(r, radius, radius, cellRing);
            }

            Paint p = (steps > 0 || isToday) ? num : (future ? numMuted : num);
            float baseline = cy + (num.getTextSize() / 2f) - 2f * d;
            c.drawText(String.valueOf(day), cx, baseline, p);
        }
    }

    /** Акцентный цвет с прозрачностью: чем больше шагов, тем плотнее заливка. */
    private int tint(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Шаги за конкретный день месяца — helper для внешних экранов. */
    static int stepsOf(java.util.Map<String, Integer> days, int year, int month, int day) {
        String key = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
        Integer v = days.get(key);
        return v == null ? 0 : v;
    }
}
