package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Экран заданий: серия, три ежедневные цели, полоса недели. */
public class QuestsView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private GameState game;
    private float d;

    public QuestsView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
    }

    public void setGame(GameState g) { game = g; invalidate(); }

    private void round(Canvas c, float x, float y, float w, float h, float rad, int fill, int border) {
        r.set(x, y, x + w, y + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawRoundRect(r, rad, rad, p);
        if (border != 0) {
            stroke.setColor(border);
            stroke.setStrokeWidth(1 * d);
            c.drawRoundRect(r, rad, rad, stroke);
        }
    }

    private void t(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold) {
        text.setTextSize(size * d);
        text.setColor(color);
        text.setTextAlign(align);
        text.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                              : Typeface.create("sans-serif", Typeface.NORMAL));
        c.drawText(s, x, y, text);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (game == null) return;

        float w = getWidth();
        float pad = 18 * d;

        // серия
        float y = pad;
        round(c, pad, y, w - pad * 2, 84 * d, 20 * d, Theme.CARD, Theme.CARD_STROKE);
        t(c, "СЕРИЯ", pad + 18 * d, y + 24 * d, 10, Theme.FAINT, Paint.Align.LEFT, false);
        t(c, game.streak + " дн.", pad + 18 * d, y + 60 * d, 26, Theme.GOLD, Paint.Align.LEFT, true);
        t(c, game.streak > 0 ? "не разрывай цепочку" : "польёшь — начнётся",
                w - pad - 18 * d, y + 60 * d, 10, Theme.MUTED, Paint.Align.RIGHT, false);

        // задания
        y += 84 * d + 12 * d;
        quest(c, pad, y, w - pad * 2, "Пройти " + game.q1Target + " шагов",
                game.q1Done, game.q1Target, game.q1Claimed == 1, "+60 очков", Theme.ACCENT);
        y += 78 * d;
        quest(c, pad, y, w - pad * 2, "Вырастить цветок сегодня",
                Math.min(1, game.flowersToday), 1, game.flowersToday >= 1, "+40 очков", Theme.ACCENT);
        y += 78 * d;
        quest(c, pad, y, w - pad * 2, "Неделя: 7 цветов",
                Math.min(7, game.weekFlowers), 7, game.weekFlowers >= 7, "+300 очков", Theme.GOLD);

        // неделя: тот же шаг, что и между заданиями (было +14dp сверху,
        // из-за чего блок «ЭТА НЕДЕЛЯ» висел заметно ниже остальных)
        y += 78 * d;
        round(c, pad, y, w - pad * 2, 96 * d, 20 * d, Theme.CARD, Theme.CARD_STROKE);
        t(c, "ЭТА НЕДЕЛЯ", pad + 18 * d, y + 24 * d, 10, Theme.FAINT, Paint.Align.LEFT, false);

        String[] names = {"П", "В", "С", "Ч", "П", "С", "В"};
        // понедельник = 0: Calendar.DAY_OF_WEEK даёт 1 для воскресенья
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int dow = cal.get(java.util.Calendar.DAY_OF_WEEK);
        int todayIdx = (dow + 5) % 7;

        float cw = (w - pad * 2 - 36 * d) / 7f;
        for (int i = 0; i < 7; i++) {
            float cx = pad + 18 * d + cw * i + cw / 2f;
            boolean today = i == todayIdx;

            // горит только сегодняшний день; серия видна цифрой в карточке воды,
            // а не подсветкой прошедших дней
            p.setStyle(Paint.Style.FILL);
            p.setColor(today ? Theme.ACCENT : Theme.SURFACE_HI);
            c.drawCircle(cx, y + 58 * d, 12 * d, p);

            stroke.setColor(today ? Theme.ACCENT : Theme.CARD_STROKE);
            stroke.setStrokeWidth(today ? 2 * d : 1 * d);
            c.drawCircle(cx, y + 58 * d, 12 * d, stroke);

            // буква дня центрируется по метрикам шрифта, а не подбором базовой линии
            text.setTextSize(10 * d);
            text.setColor(today ? Theme.ON_ACCENT : Theme.FAINT);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create(today ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            c.drawText(names[i], cx, Ui.baseline(text, y + 58 * d), text);
        }
    }

    private void quest(Canvas c, float x, float y, float w, String title,
                       int done, int target, boolean claimed, String reward, int color) {
        round(c, x, y, w, 68 * d, 18 * d, Theme.CARD, Theme.CARD_STROKE);
        t(c, title, x + 16 * d, y + 28 * d, 13, Theme.TEXT, Paint.Align.LEFT, false);
        t(c, claimed ? "получено" : reward, x + w - 16 * d, y + 28 * d, 10.5f,
                claimed ? Theme.FAINT : Theme.ACCENT_SOFT, Paint.Align.RIGHT, true);

        float frac = target <= 0 ? 1 : Math.min(1f, done / (float) target);
        round(c, x + 16 * d, y + 40 * d, w - 32 * d, 6 * d, 3 * d, Theme.STROKE, 0);
        if (frac > 0) {
            round(c, x + 16 * d, y + 40 * d, (w - 32 * d) * frac, 6 * d, 3 * d,
                    claimed ? Theme.GOLD : color, 0);
        }
        t(c, done + " / " + target, x + 16 * d, y + 60 * d, 10, Theme.FAINT, Paint.Align.LEFT, false);
    }
}
