package com.dsh.stepcounter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Новогоднее оформление приложения: гирлянда в шапке с живыми лампочками и еловые
 * ветки в верхних углах — всё это подложкой под интерфейс, на всех вкладках.
 *
 * Включается в сезон (1 декабря — 12 января) либо принудительно кнопкой
 * «Новогоднее оформление» в меню разработчика.
 */
public final class NewYearDecor {

    /** Режим оформления: true — включено всегда, false — только по датам. */
    public static final String PREF_DECOR = "nyDecorAlways";

    private NewYearDecor() { }

    public static boolean enabled(Context ctx) {
        if (Steps.prefs(ctx).getBoolean(PREF_DECOR, true)) return true;
        long now = System.currentTimeMillis();
        return now >= NewYear.start() && now <= NewYear.end();
    }

    public static void setAlways(Context ctx, boolean always) {
        Steps.prefs(ctx).edit().putBoolean(PREF_DECOR, always).apply();
    }

    /** Накладывает слой украшений на фон активити (под интерфейс). */
    public static void attach(Activity activity, ViewGroup root) {
        if (!enabled(activity)) return;
        DecorView view = new DecorView(activity);
        root.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Слой-подложка: ветки в верхних углах и мерцающая гирлянда. */
    static class DecorView extends View {

        /** Положения лампочек в картинке гирлянды (доли ширины и высоты). */
        private static final float[] BULB_X = {
                0.0421f, 0.1589f, 0.2738f, 0.3869f, 0.5004f,
                0.6144f, 0.7280f, 0.8434f, 0.9596f
        };
        private static final float[] BULB_Y = {
                0.4686f, 0.5895f, 0.6763f, 0.7301f, 0.7523f,
                0.7381f, 0.6909f, 0.6082f, 0.4846f
        };

        private final Bitmap garland;
        private final Bitmap corner;
        private final Bitmap glow;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        private static final float BRANCH_H = 0.165f;
        /** Сколько секунд держится один режим мерцания. */
        private static final float MODE_SECONDS = 10f;
        /** Отсчёт времени от запуска экрана: большие числа float округляет. */
        private final long startMs = System.currentTimeMillis();

        DecorView(Context ctx) {
            super(ctx);
            garland = decode(ctx, "ny_garland");
            corner = decode(ctx, "ny_corner");
            glow = makeGlow();
            setClickable(false);
            setFocusable(false);
            startTicker();
        }

        private static Bitmap decode(Context ctx, String name) {
            int id = NewYear.res(ctx, name);
            return id == 0 ? null : BitmapFactory.decodeResource(ctx.getResources(), id);
        }

        /** Плавная перерисовка: без неё мерцания не будет. */
        private void startTicker() {
            android.animation.ValueAnimator anim =
                    android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1000L);
            anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            anim.setInterpolator(new android.view.animation.LinearInterpolator());
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    invalidate();
                }
            });
            anim.start();
        }

        /** Мягкое пятно света для лампочки. */
        private static Bitmap makeGlow() {
            int size = 64;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f,
                    new int[]{0xFFFFF3C4, 0x99FFD772, 0x00FFD772},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(size / 2f, size / 2f, size / 2f, p);
            return b;
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

            if (corner != null) drawBranches(c, w, h);

            if (garland != null) {
                float t = (System.currentTimeMillis() - startMs) / 1000f;
                float gw = w * 0.54f;
                float y = h * 0.050f;
                float sag = h * 0.010f;
                drawGarlandHalf(c, w * 0.5f - gw / 2f, y, gw / 2f, sag, true, t);
                drawGarlandHalf(c, w * 0.5f, y + sag, gw / 2f, sag, false, t);

            }
        }

        private void drawGarlandHalf(Canvas c, float left, float yEdge, float halfW, float sag,
                                     boolean toCenter, float t) {
            float segH = garland.getHeight() * halfW / (float) garland.getWidth();
            float cx = left + halfW / 2f;
            float cy = yEdge + (toCenter ? -sag / 2f : sag / 2f);
            float angle = (float) Math.toDegrees(Math.atan2(sag, halfW)) * (toCenter ? 1f : -1f);

            c.save();
            c.rotate(angle, cx, cy);

            RectF r = new RectF(cx - halfW / 2f, cy - segH / 2f, cx + halfW / 2f, cy + segH / 2f);
            paint.setAlpha(215);
            c.drawBitmap(garland, null, r, paint);
            paint.setAlpha(255);

            // живые лампочки: у каждой свой уровень яркости по режиму
            int mode = (int) (t / MODE_SECONDS) % 4;
            float bulb = Math.max(8f, r.height() * 0.78f);
            for (int i = 0; i < BULB_X.length; i++) {
                float bx = r.left + BULB_X[i] * r.width();
                float by = r.top + BULB_Y[i] * r.height();
                float bright = brightness(mode, i, t);
                if (bright < 0.04f) continue;
                glowPaint.setAlpha((int) (bright * 255));
                float gs = bulb * (1.5f + bright * 1.4f);
                c.drawBitmap(glow, null, new RectF(bx - gs / 2f, by - gs / 2f,
                        bx + gs / 2f, by + gs / 2f), glowPaint);
                // яркое ядро лампочки
                glowPaint.setAlpha((int) (bright * 255));
                float core = bulb * 0.42f;
                c.drawBitmap(glow, null, new RectF(bx - core / 2f, by - core / 2f,
                        bx + core / 2f, by + core / 2f), glowPaint);
            }
            c.restore();
        }

        /** Яркость лампочки i в момент t: четыре режима, как у настоящей гирлянды. */
        private static float brightness(int mode, int i, float t) {
            float v;
            switch (mode) {
                case 0: {
                    // бегущий огонёк
                    float phase = (t * 1.5f - i * 0.55f) % 6.2832f;
                    v = 0.5f + 0.5f * (float) Math.sin(phase);
                    v = v * v;
                    break;
                }
                case 1: {
                    // мерцание: у каждой лампочки свой ритм
                    float speed = 2.1f + (i % 3) * 0.9f;
                    v = 0.5f + 0.5f * (float) Math.sin(t * speed + i * 1.7f);
                    v = v * v * v;
                    break;
                }
                case 2: {
                    // волна слева направо
                    float phase = t * 2.2f - i * 0.62f;
                    v = 0.5f + 0.5f * (float) Math.sin(phase);
                    v = 0.25f + 0.75f * v;
                    break;
                }
                default: {
                    // спокойное дыхание — все вместе
                    v = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(t * 1.3f));
                    v = 0.55f + 0.45f * v;
                    break;
                }
            }
            // лампочка никогда не гаснет совсем — иначе гирлянда кажется мёртвой
            return 0.38f + 0.62f * Math.max(0f, Math.min(1f, v));
        }

        /** Ветки в верхних углах, зеркально. */
        private void drawBranches(Canvas c, float w, float h) {
            float ch = h * BRANCH_H;
            float cw = ch * corner.getWidth() / (float) corner.getHeight();
            RectF top = new RectF(w - cw * 0.80f, -ch * 0.10f, w + cw * 0.20f, ch * 0.90f);

            c.drawBitmap(corner, null, top, paint);

            c.save();
            c.scale(-1f, 1f);
            c.translate(-w, 0f);
            c.drawBitmap(corner, null, top, paint);
            c.restore();
        }
    }
}
