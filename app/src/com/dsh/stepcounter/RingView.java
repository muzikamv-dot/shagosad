package com.dsh.stepcounter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.graphics.BlurMaskFilter;

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
    private float depth = 0f;     // 0 — на месте, 1 — «ближе к глазам» (рисуем крупнее)
    private ValueAnimator animator;
    private int mode = RingMode.FLAT;
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Как рисовать: плоское, объёмное, текстура, 3D. */
    public void setMode(int m) {
        mode = RingMode.FLAT;
        invalidate();
    }

    /**
     * Насколько шайба «выдвинута» из экрана: 0 — покой, 1 — приближена.
     *
     * Кольцо не масштабируется картинкой (от этого оно мутнеет), а перерисовывается
     * заново в большем диаметре: все длины и толщины умножаются на 1 + 0.22 * depth.
     */
    public void setDepth(float d) {
        float v = Math.max(0f, Math.min(1f, d));
        if (Math.abs(v - depth) < 0.001f) return;
        depth = v;
        invalidate();
    }

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
        // Приближение: перерисовываем кольцо заново, а не растягиваем картинку.
        // Радиус растёт сильнее, чем толщина дуги, — внутри остаётся больше места
        // для цифр, поэтому шрифт можно увеличить заметнее без обрезки.
        float z = 1f + 0.45f * depth;      // диаметр кольца
        float zs = 1f + 0.16f * depth;     // толщина линий
        // Деления торчат за радиус ещё на len (до 9dp), поэтому начинаем их в 8dp
        // от дуги: иначе верхнее и нижнее деление срезались краем холста.
        // Запас 40dp (по 20dp на сторону) при этом сохраняет крупное кольцо.
        float size = (Math.min(w, h) - 40f * density) * z;
        float cx = w / 2f, cy = h / 2f;
        float r = size / 2f;

        tick.setStrokeWidth(2f * density * zs);
        tickHot.setStrokeWidth(2.4f * density * zs);
        track.setStrokeWidth(14f * density * zs);

        // деления: подсвечиваем пройденную часть
        int lit = Math.round(shown * 72f);
        for (int i = 0; i < 72; i++) {
            c.save();
            c.rotate(i * 5f, cx, cy);
            float len = (i % 6 == 0) ? 7f * density * zs : 4.5f * density * zs;
            float y1 = cy - r - 11f * density * zs;
            c.drawLine(cx, y1, cx, y1 - len, i < lit ? tickHot : tick);
            c.restore();
        }

        oval.set(cx - r, cy - r, cx + r, cy + r);

        // трек: ровное кольцо
        track.setStrokeWidth(14f * density * zs);
        track.setStrokeCap(Paint.Cap.BUTT);
        c.drawArc(oval, 0f, 360f, false, track);

        if (shown > 0.004f) {
            SweepGradient sg = sweep(cx, cy);
            c.save();
            c.rotate(-90f, cx, cy);
            // мягкое свечение вокруг пройденной части
            glow.setStrokeWidth(20f * density * zs);
            glow.setAlpha((int) (34 + 30 * depth));
            glow.setStrokeCap(Paint.Cap.ROUND);
            glow.setShader(sg);
            c.drawArc(oval, 0f, 360f * shown, false, glow);
            // сама дуга прогресса
            arc.setStrokeWidth(14f * density * zs);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setShader(sg);
            c.drawArc(oval, 0f, 360f * shown, false, arc);
            c.restore();
        }
    }

    private SweepGradient sweep(float cx, float cy) {
        SweepGradient sg = new SweepGradient(cx, cy,
                new int[]{Theme.ACCENT, Theme.CYAN, Theme.ACCENT},
                new float[]{0f, 0.6f, 1f});
        Matrix m = new Matrix();
        m.setRotate(90f, cx, cy);
        sg.setLocalMatrix(m);
        return sg;
    }

    /**
     * Объём на Canvas: тень под кольцом, трек, внутренняя тень, блик сверху-слева
     * и прогресс со своим бликом. Всё вектором — остаётся чётким.
     */

    /**
     * Рисует дугу как объёмную трубу: основа цветом/градиентом, затем тёмные кромки
     * по внешнему и внутреннему краю и узкий блик чуть выше середины.
     */


    private int darken(int color) {
        int rr = (int) (((color >> 16) & 0xFF) * 0.75f);
        int gg = (int) (((color >> 8) & 0xFF) * 0.80f);
        int bb = (int) ((color & 0xFF) * 0.85f);
        return (0xFF << 24) | (rr << 16) | (gg << 8) | bb;
    }

    /** Текстура тора из ресурсов, если она есть в сборке. */
}
