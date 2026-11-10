package com.dsh.stepcounter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.Locale;

/**
 * Виджет на рабочий стол: шаги видно, не открывая приложение.
 *
 * Устроен как кольцо на главном экране, только вместо круга — прямоугольник по краям:
 * полоса прогресса бежит по периметру с насечками, а в середине крупные шаги,
 * процент от нормы и путь с временем в движении.
 *
 * Сезонные украшения раскладываются так: сначала считаем, где встанет текст,
 * потом ставим элементы только в свободные места — они не задевают ни линию,
 * ни цифры, а по площади распределены сеткой.
 *
 * Обновляется будильником раз в 5 минут (WidgetTickReceiver) и сразу после
 * каждого замера шагов.
 */
public class StepWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int id, Bundle newOptions) {
        update(context, manager, id);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
    }

    /** Обновить все виджеты (служба при изменении шагов и будильник). */
    static void refresh(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, StepWidget.class));
            if (ids == null || ids.length == 0) return;
            for (int id : ids) update(ctx, m, id);
        } catch (Exception ignored) { }
    }

    private static void update(Context ctx, AppWidgetManager m, int id) {
        try {
            Bundle o = m.getAppWidgetOptions(id);
            int dpW = o == null ? 110 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
            int dpH = o == null ? 110 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);

            float dens = ctx.getResources().getDisplayMetrics().density;
            int w = Math.max(60, Math.round(dpW * dens));
            int h = Math.max(60, Math.round(dpH * dens));

            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_stepper);
            v.setImageViewBitmap(R.id.widget_image, render(ctx, w, h, dpH));

            // тап по виджету: одинарный — прочитать счётчик, двойной — открыть приложение
            Intent tap = new Intent(ctx, WidgetClickReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, tap,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_image, pi);
            m.updateAppWidget(id, v);
        } catch (Exception ignored) { }
    }

    /** Число с пробелами: 10 319. Ui.steps добавляет слово «шагов», для виджета это лишнее. */
    private static String num(int n) {
        StringBuilder sb = new StringBuilder();
        String raw = String.valueOf(Math.abs(n));
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (raw.length() - i) % 3 == 0) sb.append(' ');
            sb.append(raw.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    /** Украшение по центру точки: масштаб одинаковый по осям, поворот вокруг центра. */
    private static void drawDecoAt(Canvas c, Bitmap art, float cx, float cy,
                                   float sizePx, float angle, Paint paint) {
        float scale = sizePx / Math.max(art.getWidth(), art.getHeight());
        float dw = art.getWidth() * scale;
        float dh = art.getHeight() * scale;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postScale(scale, scale);
        m.postTranslate(-dw / 2f, -dh / 2f);
        m.postRotate(angle);
        m.postTranslate(cx, cy);
        c.save();
        c.concat(m);
        c.drawBitmap(art, 0, 0, paint);
        c.restore();
    }

    /** Пересекаются ли круг (украшение) и прямоугольник (строка текста). */
    private static boolean hitsText(float cx, float cy, float r, RectF box) {
        float nx = Math.max(box.left, Math.min(cx, box.right));
        float ny = Math.max(box.top, Math.min(cy, box.bottom));
        float dx = cx - nx;
        float dy = cy - ny;
        return dx * dx + dy * dy < r * r;
    }

    /** Рисуем весь виджет: украшения в свободных местах, рамка-прогресс и цифры. */
    private static Bitmap render(Context ctx, int w, int h, int dpH) {
        Theme.load(ctx);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        float u = Math.min(w, h) / 110f;                 // условная единица
        u = Math.max(0.75f, Math.min(2.4f, u));

        int season = SeasonDeco.season(ctx);
        int accent = SeasonDeco.accent(ctx, season);

        float radius = Math.min(w, h) * 0.17f;
        RectF card = new RectF(1.5f * u, 1.5f * u, w - 1.5f * u, h - 1.5f * u);

        // ---------------- сначала считаем текст ----------------
        int steps = Steps.prefs(ctx).getInt(Steps.KEY_STEPS, 0);
        int goal = Steps.prefs(ctx).getInt("goal", 10000);
        if (goal < 1000) goal = 10000;
        float fraction = Math.max(0f, Math.min(1f, steps / (float) goal));

        boolean small = dpH < 95;
        float cx = w / 2f;

        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        String big = num(steps);
        float bigSize = Math.min(h * (small ? 0.30f : 0.27f), w * 0.34f);
        t.setTextSize(bigSize);
        while (t.measureText(big) > w * 0.72f && bigSize > 9f) {
            bigSize -= 1f;
            t.setTextSize(bigSize);
        }
        float bigH = t.getFontMetrics().descent - t.getFontMetrics().ascent;
        float bigW = t.measureText(big);

        String under = steps >= goal
                ? "цель достигнута"
                : "из " + num(goal) + " · " + Math.min(100, steps * 100 / goal) + "%";
        float underSize = Math.max(9f, h * 0.095f);

        String detail = "";
        float detailSize = Math.max(9f, h * 0.086f);
        double km = steps * Steps.strideMeters(ctx) / 1000.0;
        boolean[] est = new boolean[1];
        int minutes = Steps.minutesShown(ctx, steps, est);
        if (!small) {
            detail = String.format(Locale.US, "%.1f", km).replace('.', ',') + " км"
                    + (minutes > 0 ? " · " + (est[0] ? "≈ " : "") + Ui.duration(minutes) : "");
        }

        String kcal = "";
        float kcalSize = Math.max(8f, h * 0.076f);
        if (!small && Steps.showCalories(ctx) && steps > 0) {
            kcal = "≈ " + num(Steps.calories(ctx, steps)) + " ккал";
        }

        t.setTextSize(underSize);
        float underH = t.getFontMetrics().descent - t.getFontMetrics().ascent;
        float underW = t.measureText(under);
        t.setTextSize(detailSize);
        float detailH = t.getFontMetrics().descent - t.getFontMetrics().ascent;
        float detailW = detail.isEmpty() ? 0 : t.measureText(detail);
        t.setTextSize(kcalSize);
        float kcalH = t.getFontMetrics().descent - t.getFontMetrics().ascent;
        float kcalW = kcal.isEmpty() ? 0 : t.measureText(kcal);

        float gapBig = bigH * 0.18f;
        float gapMid = underH * 0.55f;
        float gapSmall = detailH * 0.55f;

        float block = bigH + gapBig + underH;
        if (!detail.isEmpty()) block += gapMid + detailH;
        if (!kcal.isEmpty()) block += gapSmall + kcalH;

        float top = Math.max(h * 0.115f, (h - block) * 0.46f);

        // прямоугольники строк — по ним проверяем, куда можно ставить украшения
        RectF boxBig = new RectF(cx - bigW / 2f, top, cx + bigW / 2f, top + bigH);
        float yUnder = top + bigH + gapBig;
        RectF boxUnder = new RectF(cx - underW / 2f, yUnder, cx + underW / 2f, yUnder + underH);
        float yDetail = yUnder + underH + (detail.isEmpty() ? 0 : gapMid);
        RectF boxDetail = detail.isEmpty() ? null
                : new RectF(cx - detailW / 2f, yDetail, cx + detailW / 2f, yDetail + detailH);
        float yKcal = yDetail + (detail.isEmpty() ? 0 : detailH) + (kcal.isEmpty() ? 0 : gapSmall);
        RectF boxKcal = kcal.isEmpty() ? null
                : new RectF(cx - kcalW / 2f, yKcal, cx + kcalW / 2f, yKcal + kcalH);

        float pad = Math.min(w, h) * 0.030f;             // запас вокруг букв
        boxBig.inset(-pad, -pad);
        boxUnder.inset(-pad, -pad);
        if (boxDetail != null) boxDetail.inset(-pad, -pad);
        if (boxKcal != null) boxKcal.inset(-pad, -pad);

        RectF[] textBoxes = boxDetail == null
                ? (boxKcal == null ? new RectF[]{boxBig, boxUnder}
                                   : new RectF[]{boxBig, boxUnder, boxKcal})
                : (boxKcal == null ? new RectF[]{boxBig, boxUnder, boxDetail}
                                   : new RectF[]{boxBig, boxUnder, boxDetail, boxKcal});

        // ---------------- фон карточки ----------------
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, Theme.SURFACE_HI, Theme.BG_BOTTOM,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(card, radius, radius, p);
        p.setShader(null);

        // ---------------- фон-волны по сезону ----------------
        // Готовый лист художника лежит под всем содержимым: обрезаем по форме карточки,
        // заполняем целиком (без растяжения пропорций) и притемняем к середине,
        // чтобы белые цифры читались на любом сезоне.
        Bitmap wave = SeasonDeco.waveBitmap(ctx, season);
        if (wave != null) {
            Path cardPath = new Path();
            cardPath.addRoundRect(card, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(cardPath);

            float scale = Math.max(card.width() / (float) wave.getWidth(),
                    card.height() / (float) wave.getHeight());
            float dw = wave.getWidth() * scale;
            float dh = wave.getHeight() * scale;
            RectF dst = new RectF(card.centerX() - dw / 2f, card.centerY() - dh / 2f,
                    card.centerX() + dw / 2f, card.centerY() + dh / 2f);

            Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(wave, null, dst, wp);

            Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
            scrim.setShader(new LinearGradient(0, card.top, 0, card.bottom,
                    new int[]{0x2E000000, 0x9A000000, 0x2E000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(card, scrim);
            c.restore();
        }

        // ---------------- рамка и прогресс ----------------
        RectF line = new RectF(card.left + 3f * u, card.top + 3f * u,
                card.right - 3f * u, card.bottom - 3f * u);
        float lineR = Math.max(4f, radius - 3f * u);
        Path edge = new Path();
        edge.addRoundRect(line, lineR, lineR, Path.Direction.CW);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * u);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Theme.STROKE);
        c.drawPath(edge, p);

        PathMeasure pm = new PathMeasure(edge, false);
        float len = pm.getLength();

        if (fraction > 0.001f) {
            Path seg = new Path();
            pm.getSegment(0, len * fraction, seg, true);
            p.setStrokeWidth(4.2f * u);
            p.setColor(accent);
            p.setAlpha(60);
            c.drawPath(seg, p);
            p.setStrokeWidth(2.6f * u);
            p.setAlpha(255);
            c.drawPath(seg, p);
        }

        int ticks = 40;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * u);
        float[] pos = new float[2];
        float[] tan = new float[2];
        for (int i = 0; i < ticks; i++) {
            float at = len * (i + 0.5f) / ticks;
            pm.getPosTan(at, pos, tan);
            boolean done = at <= len * fraction;
            p.setColor(done ? accent : Theme.STROKE);
            p.setAlpha(done ? 200 : 110);
            float nx = -tan[1];
            float ny = tan[0];
            float half = (done ? 3.6f : 2.6f) * u;
            c.drawLine(pos[0] - nx * half, pos[1] - ny * half,
                    pos[0] + nx * half, pos[1] + ny * half, p);
        }
        p.setAlpha(255);

        // ---------------- текст ----------------
        float y = top;
        t.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        t.setTextSize(bigSize);
        t.setColor(0xFFFFFFFF);
        t.setShadowLayer(3.5f * u, 0f, 1.2f * u, 0x99000000);
        float baseline = y - t.getFontMetrics().ascent;
        c.drawText(big, cx, baseline, t);
        y += bigH + gapBig;

        t.setShadowLayer(0f, 0f, 0f, 0);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setTextSize(underSize);
        t.setColor(Theme.MUTED);
        baseline = y - t.getFontMetrics().ascent;
        c.drawText(under, cx, baseline, t);
        y += underH;

        if (!detail.isEmpty()) {
            y += gapMid;
            t.setTextSize(detailSize);
            t.setColor(Theme.DIM);
            baseline = y - t.getFontMetrics().ascent;
            c.drawText(detail, cx, baseline, t);
            y += detailH;
        }

        if (!kcal.isEmpty()) {
            y += gapSmall;
            t.setTextSize(kcalSize);
            t.setColor(Theme.FAINT);
            baseline = y - t.getFontMetrics().ascent;
            c.drawText(kcal, cx, baseline, t);
        }

        return bmp;
    }
}
