package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Лента-триколор на вкладке «Ивенты».
 *
 * Картинка вырезана из референса владельца (`res/drawable-nodpi/event_ribbon.webp`):
 * синяя и красная части взяты как есть, белая полоса восстановлена по контуру —
 * в исходнике она белая на белом и пиксельно не отделяется. Фон удалён, поэтому
 * лента ложится на тёмный фон без плашки.
 */
public class FlagRibbon extends View {

    private final Bitmap art;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();

    public FlagRibbon(Context ctx) {
        super(ctx);
        art = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.event_ribbon);
    }

    @Override protected void onDraw(Canvas c) {
        if (art == null) return;
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // вписываем по ширине, сохраняя пропорции и центрируя по вертикали
        float scale = Math.min(w / art.getWidth(), h / art.getHeight());
        float dw = art.getWidth() * scale;
        float dh = art.getHeight() * scale;
        float left = (w - dw) / 2f;
        float top = (h - dh) / 2f;
        dst.set(left, top, left + dw, top + dh);
        c.drawBitmap(art, null, dst, paint);
    }

    /** Пропорции картинки — чтобы вызывающий код мог задать высоту под ширину. */
    public static float aspect() {
        return 1200f / 270f;
    }
}
