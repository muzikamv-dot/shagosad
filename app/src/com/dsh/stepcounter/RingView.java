package com.dsh.stepcounter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Кольцо прогресса шагов с плавной анимацией. */
public class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickHot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float density;
    private float shown = 0f;     // текущая доля на экране
    private float target = 0f;    // к чему анимируемся
    private ValueAnimator animator;

    public RingView(Context ctx) {
        super(ctx);
        density = getResources().getDisplayMetrics().density;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(14f * density);
        track.setColor(Theme.STROKE);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(14f * density);
        arc.setStrokeCap(Paint.Cap.ROUND);

        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(26f * density);
        glow.setAlpha(60);
        glow.setStrokeCap(Paint.Cap.ROUND);

        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(2f * density);
        tick.setColor(Theme.STROKE);

        tickHot.setStyle(Paint.Style.STROKE);
        tickHot.setStrokeWidth(2.4f * density);
        tickHot.setColor(Theme.ACCENT);
    }

    public void setProgress(int steps, int goal) {
        float f = Math.max(0f, Math.min(1f, steps / (float) Math.max(1, goal)));
        if (Math.abs(f - target) < 0.0005f) return;
        target = f;

        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, target);
        animator.setDuration(700L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                shown = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        // Деления торчат за радиус ещё на len (до 9dp), поэтому начинаем их в 8dp
        // от дуги: иначе верхнее и нижнее деление срезались краем холста.
        // Запас 40dp (по 20dp на сторону) при этом сохраняет крупное кольцо.
        float size = Math.min(w, h) - 40f * density;
        float cx = w / 2f, cy = h / 2f;
        float r = size / 2f;

        // деления: подсвечиваем пройденную часть
        int lit = Math.round(shown * 72f);
        for (int i = 0; i < 72; i++) {
            c.save();
            c.rotate(i * 5f, cx, cy);
            float len = (i % 6 == 0) ? 7f * density : 4.5f * density;
            float y1 = cy - r - 11f * density;
            c.drawLine(cx, y1, cx, y1 - len, i < lit ? tickHot : tick);
            c.restore();
        }

        oval.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(oval, 0f, 360f, false, track);

        // при нуле дугу не рисуем: иначе закруглённый кончик висит точкой
        if (shown > 0.005f) {
            SweepGradient sg = new SweepGradient(cx, cy,
                    new int[]{Theme.ACCENT, Theme.CYAN,
                              Theme.ACCENT},
                    new float[]{0f, 0.6f, 1f});
            Matrix m = new Matrix();
            m.setRotate(90f, cx, cy);
            sg.setLocalMatrix(m);

            c.save();
            c.rotate(-90f, cx, cy);
            glow.setShader(sg);
            c.drawArc(oval, 0f, 360f * shown, false, glow);
            arc.setShader(sg);
            c.drawArc(oval, 0f, 360f * shown, false, arc);
            c.restore();
        }
    }
}
