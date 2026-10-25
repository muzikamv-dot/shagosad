package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Рисованные иконки нижней навигации.
 *
 * Все пять значков строятся в одном оптическом боксе S = 0.66u и симметричны
 * относительно центра — иначе ряд иконок выглядит рваным: одни выше, другие мельче.
 */
public class TabIcon extends View {

    public static final int STEPS = 0, GARDEN = 1, QUESTS = 2, PROFILE = 3, STAR = 4;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    private int type = STEPS;
    private boolean active = false;

    public TabIcon(Context ctx) {
        super(ctx);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setType(int t) { type = t; }
    public void setActive(boolean a) { active = a; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float u = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        float S = u * 0.66f;                    // единый оптический бокс
        float top = cy - S / 2f, bottom = cy + S / 2f;
        int color = active ? Theme.ACCENT : Theme.FAINT;

        p.setColor(color);
        s.setColor(color);
        s.setStrokeWidth(Math.max(1.4f, u * 0.085f));

        if (type == STEPS) {
            // три столбика: нижняя кромка общая, высоты внутри бокса
            float bw = S * 0.22f;
            float[] hs = {0.44f, 0.70f, 1f};
            for (int i = 0; i < 3; i++) {
                float x = cx + (i - 1) * S * 0.33f;
                float bh = S * hs[i];
                r.set(x - bw / 2f, bottom - bh, x + bw / 2f, bottom);
                c.drawRoundRect(r, bw / 2f, bw / 2f, p);
            }
        } else if (type == GARDEN) {
            // цветок: пять лепестков контуром, серединка точкой, стебель и лист.
            // Горшок с ростком на 20dp читался как ключ, поэтому — цветок.
            float hy = top + S * 0.26f;
            float pr = S * 0.13f;
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + Math.PI * 2 * i / 5;
                c.drawCircle(cx + (float) Math.cos(a) * pr * 2.05f,
                        hy + (float) Math.sin(a) * pr * 2.05f, pr, s);
            }
            c.drawLine(cx, hy + pr * 1.7f, cx, bottom - S * 0.04f, s);
            c.save();
            c.translate(cx, bottom - S * 0.26f);
            c.rotate(-38f);
            p.setColor(color);
            c.drawOval(new RectF(0f, -S * 0.075f, S * 0.28f, S * 0.075f), p);
            c.restore();
            p.setColor(color);
            c.drawCircle(cx, hy, pr * 0.62f, p);
        } else if (type == QUESTS) {
            // кружок с галочкой — по размеру как звезда и профиль
            c.drawCircle(cx, cy, S * 0.48f, s);
            path.reset();
            path.moveTo(cx - S * 0.20f, cy + S * 0.01f);
            path.lineTo(cx - S * 0.05f, cy + S * 0.16f);
            path.lineTo(cx + S * 0.21f, cy - S * 0.16f);
            c.drawPath(path, s);
        } else if (type == PROFILE) {
            c.drawCircle(cx, top + S * 0.24f, S * 0.24f, s);
            r.set(cx - S * 0.38f, cy + S * 0.04f, cx + S * 0.38f, cy + S * 0.80f);
            c.drawArc(r, 180f, 180f, false, s);
        } else {
            // звезда
            float outer = S * 0.52f, inner = S * 0.23f;
            path.reset();
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + Math.PI * i / 5;
                float rad = (i % 2 == 0) ? outer : inner;
                float px = cx + (float) Math.cos(ang) * rad;
                float py = cy + (float) Math.sin(ang) * rad;
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            path.close();
            c.drawPath(path, p);
        }
    }
}
