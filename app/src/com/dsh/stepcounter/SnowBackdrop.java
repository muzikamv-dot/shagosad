package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.Random;

/**
 * Статичные снежинки на фоне — только для «Новогодней» темы.
 *
 * Рисунок один раз: позиции детерминированные (фиксированное зерно), поэтому
 * снежинки не «прыгают» при перерисовке и не тратят батарею на анимацию.
 * Касания слой не перехватывает.
 */
public final class SnowBackdrop {

    private SnowBackdrop() { }

    /** Добавляет слой снежинок под содержимое. */
    public static void attach(Context ctx, ViewGroup root) {
        if (Theme.mode() != Theme.T_NEWYEAR) return;
        root.addView(new FlakesView(ctx), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    static class FlakesView extends View {

        private static final int COUNT = 42;

        private final Bitmap[] flakes = new Bitmap[3];
        private final float[] nx = new float[COUNT];
        private final float[] ny = new float[COUNT];
        private final float[] size = new float[COUNT];
        private final float[] angle = new float[COUNT];
        private final int[] kind = new int[COUNT];
        private final int[] alpha = new int[COUNT];

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix m = new Matrix();

        FlakesView(Context ctx) {
            super(ctx);
            for (int i = 0; i < 3; i++) {
                int id = NewYear.res(ctx, "ny_flake_" + (i + 1));
                if (id != 0) flakes[i] = BitmapFactory.decodeResource(ctx.getResources(), id);
            }
            Random rnd = new Random(20261201L);
            // сетка 6×11: по одной снежинке в ячейке со случайным смещением —
            // так они распределены ровно, без «пробелов» в углах
            final int cols = 6, rows = 11;
            int i = 0;
            for (int r = 0; r < rows && i < COUNT; r++) {
                for (int c = 0; c < cols && i < COUNT; c++) {
                    boolean topLeft = c < 2 && r < 4;
                    // в левом верхнем углу заполняем все ячейки, дальше — примерно две трети
                    if (!topLeft && rnd.nextInt(3) == 0) continue;
                    nx[i] = (c + 0.15f + rnd.nextFloat() * 0.70f) / cols;
                    ny[i] = (r + 0.15f + rnd.nextFloat() * 0.70f) / rows;
                    size[i] = 0.018f + rnd.nextFloat() * 0.070f;
                    angle[i] = rnd.nextFloat() * 360f;
                    kind[i] = rnd.nextInt(3);
                    alpha[i] = 30 + rnd.nextInt(70);
                    i++;
                }
            }
            // остаток (если сетка не заполнилась) — добиваем случайными точками
            for (; i < COUNT; i++) {
                nx[i] = rnd.nextFloat();
                ny[i] = 0.04f + rnd.nextFloat() * 0.92f;
                size[i] = 0.018f + rnd.nextFloat() * 0.070f;
                angle[i] = rnd.nextFloat() * 360f;
                kind[i] = rnd.nextInt(3);
                alpha[i] = 30 + rnd.nextInt(70);
            }
            setClickable(false);
            setFocusable(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            return false;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            for (int i = 0; i < COUNT; i++) {
                Bitmap b = flakes[kind[i] % 3];
                if (b == null) continue;
                float s = w * size[i];
                float x = nx[i] * w;
                float y = ny[i] * h;
                paint.setAlpha(alpha[i]);
                m.reset();
                m.postRotate(angle[i], x, y);
                c.save();
                c.concat(m);
                c.drawBitmap(b, null, new RectF(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f), paint);
                c.restore();
            }
            paint.setAlpha(255);
        }
    }
}
