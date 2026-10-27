package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Лента-триколор: российский флаг в виде мягко вьющейся ленты.
 *
 * Рисуется кодом, без картинок. Три полосы (белая, синяя, красная) идут по синусоиде,
 * концы скруглены — прямых углов нет, чтобы лента не выбивалась из мягкого вида игры.
 */
public class FlagRibbon extends View {

    private static final int WHITE = 0xFFF4F6F8;
    private static final int BLUE = 0xFF0039A6;
    private static final int RED = 0xFFD52B1E;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path round = new Path();
    private final Path strip = new Path();
    private final RectF rect = new RectF();

    public FlagRibbon(Context ctx) {
        super(ctx);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float amp = h * 0.09f;                 // мягкая волна
        float k = (float) (2 * Math.PI * 1.35f / w);
        float bandH = h / 3f;
        float topEdge = h / 2f - h / 2f;       // верх ленты в нулевой фазе

        // скруглённые концы: клип по «таблетке», острые углы срезаются
        round.reset();
        rect.set(0, 0, w, h);
        round.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW);

        int save = c.save();
        c.clipPath(round);

        int[] colors = {WHITE, BLUE, RED};
        for (int i = 0; i < 3; i++) {
            float y0 = topEdge + i * bandH;
            float y1 = y0 + bandH;

            strip.reset();
            boolean first = true;
            for (float x = 0; x <= w; x += 3f) {
                float wave = (float) Math.sin(k * x) * amp;
                if (first) { strip.moveTo(x, y0 + wave); first = false; }
                else strip.lineTo(x, y0 + wave);
            }
            for (float x = w; x >= 0; x -= 3f) {
                float wave = (float) Math.sin(k * x) * amp;
                strip.lineTo(x, y1 + wave);
            }
            strip.close();

            p.setShader(null);
            p.setColor(colors[i]);
            c.drawPath(strip, p);
        }

        // тени сверху и снизу — лента выглядит тканью, а не плоской заливкой
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x40000000, 0x00000000, 0x00000000, 0x33000000},
                new float[]{0f, 0.28f, 0.74f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        c.restoreToCount(save);
    }
}
