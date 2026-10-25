package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Значок настроек в шапке — мягкая шестерёнка.
 *
 * Три части, которые не сливаются: толстое кольцо, восемь скруглённых зубцов,
 * примыкающих к кольцу изнутри, и отверстие. Толщины считаются от размера вью,
 * поэтому на 30dp знак остаётся контуром, а не залитым пятном.
 */
public class GearView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GearView(Context ctx) {
        super(ctx);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Theme.ACCENT_SOFT);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float u = Math.min(w, h);

        float ringR = u * 0.295f;
        float ringStroke = u * 0.095f;
        float toothW = u * 0.092f;
        float toothOut = u * 0.385f;
        float toothIn = u * 0.285f;

        // зубцы: начинаются под кольцом, поэтому выглядят приваренными к нему
        p.setStrokeWidth(toothW);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45f - 90f);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * toothIn, cy + sin * toothIn,
                    cx + cos * toothOut, cy + sin * toothOut, p);
        }

        // кольцо
        p.setStrokeWidth(ringStroke);
        c.drawCircle(cx, cy, ringR, p);

        // отверстие
        p.setStrokeWidth(Math.max(1.5f, u * 0.048f));
        c.drawCircle(cx, cy, u * 0.105f, p);
    }
}
