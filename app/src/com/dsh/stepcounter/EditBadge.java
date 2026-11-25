package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

/**
 * Значок «можно изменить»: открытый скруглённый квадрат и КОНТУРНЫЙ (без заливки)
 * карандаш, входящий справа сверху: острие внутри, тупой конец выходит за угол.
 *
 * Рисуется только линиями одной толщины — как на образце, без сплошных пятен.
 */
public class EditBadge extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public EditBadge(Context c) {
        super(c);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());
        line.setColor(Theme.ACCENT_SOFT);
        line.setAlpha(150);                     // приглушённый
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    void setScale(float k) {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float s = Math.min(w, h);
        float sw = Math.max(1.3f * d, s * 0.095f);   // толщина линии — как на образце
        line.setStrokeWidth(sw);

        float inset = sw * 0.9f;
        float radius = s * 0.20f;
        float topY = inset + sw / 2f;
        float botY = h - inset - sw / 2f;
        float leftX = inset + sw / 2f;
        float rightX = w - inset - sw / 2f;

        // квадрат с разрывом справа сверху — туда входит карандаш
        // разрыв шире карандаша: он проходит СКВОЗЬ него и не касается линии
        float gapTop = leftX + (rightX - leftX) * 0.44f;
        float gapRight = topY + (botY - topY) * 0.44f;

        Path square = new Path();
        square.moveTo(gapTop, topY);
        square.lineTo(leftX + radius, topY);
        square.quadTo(leftX, topY, leftX, topY + radius);
        square.lineTo(leftX, botY - radius);
        square.quadTo(leftX, botY, leftX + radius, botY);
        square.lineTo(rightX - radius, botY);
        square.quadTo(rightX, botY, rightX, botY - radius);
        square.lineTo(rightX, gapRight);
        c.drawPath(square, line);

        // карандаш: входит справа сверху, острие смотрит вниз-влево
        // ось карандаша идёт ровно через разрыв квадрата
        float gx = (gapTop + rightX) / 2f;
        float gy = (topY + gapRight) / 2f;
        c.save();
        c.rotate(-45f, gx, gy);
        float cy = gy;
        float len = s * 0.80f;          // соразмерно квадрату, а не сквозь весь значок
        float bh = s * 0.26f;
        float tip = s * 0.20f;
        float x0 = gx - len / 2f;
        float x1 = gx + len / 2f;

        Path pencil = new Path();
        pencil.moveTo(x0 + tip, cy - bh / 2f);
        pencil.lineTo(x1, cy - bh / 2f);
        pencil.lineTo(x1, cy + bh / 2f);
        pencil.lineTo(x0 + tip, cy + bh / 2f);
        pencil.lineTo(x0, cy);                 // острие
        pencil.close();
        c.drawPath(pencil, line);
        c.restore();
    }
}
