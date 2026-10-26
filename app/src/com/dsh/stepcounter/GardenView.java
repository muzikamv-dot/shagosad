package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Сад: один крупный цветок, стадии роста и букет собранных видов. */
public class GardenView extends View {

    public interface Listener { void onTap(); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final RectF card = new RectF();
    private final RectF buttonRect = new RectF();

    private GameState game;
    private Listener listener;
    private float d;
    private final Bitmap[] sprites = new Bitmap[120];

    public static final String[] SPECIES_NAMES = {"Ромашка", "Пион", "Лаванда", "Мак", "Ирис", "Роза",
            "Тюльпан", "Подсолнух", "Лотос", "Звёздный", "Лунная лилия", "Кристальный"};
    private static final String[] STAGE_NAMES = {"семя", "росток", "проросток", "листья", "бутон",
            "крупный бутон", "раскрывается", "распускается", "почти цветок", "вот-вот", "цветок"};
    private static final int[] PETAL = {0xFFF7C6DA, 0xFFF5A9BE, 0xFFC4B4F5, 0xFFF59480,
            0xFFA8CDF5, 0xFFF07C8E, 0xFFF5B44E, 0xFFF2B01E, 0xFFF6A8CC, 0xFF7FE3FF,
            0xFFC9BCFF, 0xFF8FD9F5};

    public GardenView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        st.setStyle(Paint.Style.STROKE);
    }

    public void setGame(GameState g) { game = g; invalidate(); }
    public void setListener(Listener l) { listener = l; }

    // ---- движение: пружинка растения и блёстки при сборе ----

    private float pulse = 1f;
    private android.animation.ValueAnimator pulseAnim;

    private static final class Spark {
        float x, y, vx, vy, size, rot, spin, a;
        int color;
    }
    private final java.util.ArrayList<Spark> sparks = new java.util.ArrayList<>();
    private android.animation.ValueAnimator sparkAnim;

    /** Растение пружинисто встряхивается в ответ на полив. */
    public void pulse() {
        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = android.animation.ValueAnimator.ofFloat(1f, 1.10f, 1f);
        pulseAnim.setDuration(460L);
        pulseAnim.setInterpolator(new android.view.animation.OvershootInterpolator(2.4f));
        pulseAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                pulse = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        pulseAnim.start();
    }

    /** Полив: лейка наклоняется и льёт, а цветок подрастает только после неё. */
    private static final long WATER_MS = 950L;
    private long canStart = 0;
    private boolean canActive = false;
    private int stageBefore = -1;

    public void water() {
        if (game == null) return;
        canStart = System.currentTimeMillis();
        canActive = true;
        // пока льётся вода, растение рисуется в прежней стадии
        stageBefore = Math.max(0, game.stage[0] - 1);
        removeCallbacks(growAfterCan);
        postDelayed(growAfterCan, WATER_MS);
        invalidate();
    }

    private final Runnable growAfterCan = new Runnable() {
        @Override public void run() {
            canActive = false;
            stageBefore = -1;
            pulse();
        }
    };

    /** Лейка: въезжает справа, наклоняется, льёт и уезжает. */
    private void drawCan(Canvas c, float w, float soilY, long now) {
        float t = (now - canStart) / (float) WATER_MS;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;

        float slideIn = Math.min(1f, t / 0.18f);
        float slideOut = t > 0.82f ? (t - 0.82f) / 0.18f : 0f;
        float alpha = slideIn * (1f - slideOut);
        if (alpha <= 0.01f) return;

        // наклон к середине полива; знак минус — носик уходит вниз, к цветку
        float tilt;
        if (t < 0.22f) tilt = -34f * (t / 0.22f);
        else if (t < 0.60f) tilt = -34f;
        else if (t < 0.80f) tilt = -34f * Math.max(0f, 1f - (t - 0.60f) / 0.20f);
        else tilt = 0f;

        float cx = w / 2f + 132f * d + 250f * d * (1f - slideIn) + 300f * d * slideOut;
        float cy = soilY - 168f * d;

        float bodyL = cx - 36f * d, bodyR = cx + 36f * d;
        float bodyT = cy - 26f * d, bodyB = cy + 26f * d;

        c.save();
        c.rotate(tilt, cx, cy);
        int baseAlpha = p.getAlpha();
        p.setAlpha((int) (255 * alpha));
        st.setAlpha((int) (255 * alpha));

        // корпус
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.ACCENT);
        r.set(bodyL, bodyT, bodyR, bodyB);
        c.drawRoundRect(r, 14 * d, 14 * d, p);
        st.setColor(Theme.ACCENT_DEEP);
        st.setStrokeWidth(2 * d);
        c.drawRoundRect(r, 14 * d, 14 * d, st);

        // ручка: дуга, примыкающая к корпусу справа
        st.setStrokeWidth(7 * d);
        r.set(cx + 16 * d, bodyT - 24 * d, cx + 62 * d, bodyT + 22 * d);
        c.drawArc(r, -95f, 190f, false, st);

        // крышка
        p.setColor(Theme.ACCENT_SOFT);
        r.set(cx - 20 * d, bodyT - 7 * d, cx + 20 * d, bodyT + 3 * d);
        c.drawRoundRect(r, 5 * d, 5 * d, p);

        // носик: клин от корпуса к носику-розетке
        android.graphics.Path spout = new android.graphics.Path();
        spout.moveTo(bodyL + 3 * d, cy + 20 * d);
        spout.lineTo(cx - 92 * d, cy - 22 * d);
        spout.lineTo(cx - 96 * d, cy - 34 * d);
        spout.lineTo(bodyL + 3 * d, cy - 4 * d);
        spout.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.ACCENT);
        c.drawPath(spout, p);
        st.setStrokeWidth(2 * d);
        c.drawPath(spout, st);

        // розетка на конце носика
        st.setStrokeWidth(5 * d);
        c.drawLine(cx - 99 * d, cy - 36 * d, cx - 90 * d, cy - 16 * d, st);

        // блик на корпусе
        p.setColor(Theme.ACCENT_SOFT);
        p.setAlpha((int) (90 * alpha));
        r.set(bodyL + 8 * d, bodyT + 8 * d, bodyL + 24 * d, bodyB - 10 * d);
        c.drawRoundRect(r, 7 * d, 7 * d, p);
        p.setAlpha((int) (255 * alpha));

        // струя: капли летят от носика к основанию растения
        double rad = Math.toRadians(tilt);
        float dx = -95f * d, dy = -26f * d;
        float tipX = cx + (float) (dx * Math.cos(rad) - dy * Math.sin(rad));
        float tipY = cy + (float) (dx * Math.sin(rad) + dy * Math.cos(rad));
        c.restore();

        if (t > 0.20f && t < 0.84f) {
            float span = (t - 0.20f) / 0.64f;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 13; i++) {
                float ph = (span * 3.2f + i / 13f) % 1f;
                float x = tipX + (w / 2f - tipX) * ph * 0.92f;
                float y = tipY + (soilY - 26 * d - tipY) * ph;
                float rr = 3.1f * d * (0.5f + 0.5f * (1f - ph));
                p.setColor(Theme.CYAN);
                p.setAlpha((int) (215 * alpha * (1f - ph * 0.35f)));
                c.drawCircle(x, y, rr, p);
            }
            p.setAlpha(255);
        }

        p.setAlpha(baseAlpha);
        st.setAlpha(255);
    }

    /** Блёстки-конфетти при сборе цветка. */
    public void celebrate() {
        sparks.clear();
        final int[] cols = {Theme.PINK, Theme.GOLD, Theme.ACCENT, Theme.LAVENDER};
        java.util.Random rnd = new java.util.Random();
        float cx = getWidth() / 2f, cy = getHeight() * 0.52f;
        for (int i = 0; i < 48; i++) {
            Spark s = new Spark();
            s.x = cx + (rnd.nextFloat() - 0.5f) * getWidth() * 0.45f;
            s.y = cy + (rnd.nextFloat() - 0.5f) * getHeight() * 0.22f;
            double ang = rnd.nextDouble() * Math.PI * 2;
            float sp = (0.5f + rnd.nextFloat()) * 26f * d;
            s.vx = (float) Math.cos(ang) * sp;
            s.vy = (float) Math.sin(ang) * sp - 10f * d;
            s.size = (2.4f + rnd.nextFloat() * 3.6f) * d;
            s.rot = rnd.nextFloat() * 360f;
            s.spin = (rnd.nextFloat() - 0.5f) * 14f;
            s.color = cols[rnd.nextInt(cols.length)];
            s.a = 1f;
            sparks.add(s);
        }
        if (sparkAnim != null) sparkAnim.cancel();
        sparkAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        sparkAnim.setDuration(1200L);
        sparkAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float k = 1f - (Float) a.getAnimatedValue();
                for (Spark s : sparks) {
                    s.x += s.vx * 0.016f;
                    s.y += s.vy * 0.016f;
                    s.vy += 34f * d * 0.016f;
                    s.rot += s.spin;
                    s.a = Math.max(0f, k);
                }
                invalidate();
            }
        });
        sparkAnim.start();
    }

    private Bitmap sprite(int species, int stage) {
        int idx = species * 10 + stage;
        if (sprites[idx] == null) {
            try {
                int id = getResources().getIdentifier("plant_" + species + "_" + stage,
                        "drawable", getContext().getPackageName());
                if (id != 0) sprites[idx] = BitmapFactory.decodeResource(getResources(), id);
            } catch (Exception ignored) { }
        }
        return sprites[idx];
    }

    private void label(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean medium) {
        tx.setTextSize(size * d);
        tx.setColor(color);
        tx.setTextAlign(align);
        tx.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        c.drawText(s, x, y, tx);
    }

    /** Текст, центрированный по вертикали: базовая линия считается по метрикам. */
    private void centered(Canvas c, String s, float cx, float cy, float size, int color, boolean medium) {
        tx.setTextSize(size * d);
        tx.setColor(color);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        c.drawText(s, cx, Ui.baseline(tx, cy), tx);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (game == null) return;

        float w = getWidth(), h = getHeight();
        float pad = 18 * d;

        // ---- вода ----
        float ch = 78 * d;
        LinearGradient wg = new LinearGradient(pad, pad, pad, pad + ch, Theme.PANEL_TOP, Theme.PANEL_BOTTOM, Shader.TileMode.CLAMP);
        r.set(pad, pad, w - pad, pad + ch);
        p.setStyle(Paint.Style.FILL);
        p.setShader(wg);
        c.drawRoundRect(r, 26 * d, 26 * d, p);
        p.setShader(null);
        st.setColor(Theme.CARD_STROKE); st.setStrokeWidth(1 * d);
        c.drawRoundRect(r, 26 * d, 26 * d, st);

        label(c, "ВОДА", pad + 20 * d, pad + 24 * d, 9, Theme.FAINT, Paint.Align.LEFT, false);
        String wv = String.valueOf(game.water);
        tx.setTextSize(28 * d); tx.setColor(Theme.TEXT);
        tx.setTextAlign(Paint.Align.LEFT);
        tx.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        c.drawText(wv, pad + 20 * d, pad + 60 * d, tx);
        label(c, Ui.plural(game.water, "лейка", "лейки", "леек"), pad + 26 * d + tx.measureText(wv), pad + 60 * d, 11, Theme.MUTED, Paint.Align.LEFT, false);

        int toNext = game.waterToNext();
        label(c, "до лейки", w - pad - 20 * d, pad + 28 * d, 9, Theme.FAINT, Paint.Align.RIGHT, false);
        label(c, Ui.steps(toNext), w - pad - 20 * d, pad + 54 * d, 12, Theme.ACCENT_SOFT, Paint.Align.RIGHT, true);

        // ---- большая грядка ----
        float cardTop = pad + ch + 14 * d;
        // плашек букета больше нет: карточка растения занимает всю оставшуюся высоту,
        // ниже неё отдельной кнопкой идёт действие, а под ним — тихий счётчик
        float cardH = Math.max(240 * d, Math.min(h - cardTop - 112 * d, 620 * d));
        card.set(pad, cardTop, w - pad, cardTop + cardH);

        LinearGradient bgc = new LinearGradient(pad, cardTop, pad, cardTop + cardH,
                Theme.PANEL_TOP, Theme.PANEL_BOTTOM, Shader.TileMode.CLAMP);
        r.set(card);
        p.setStyle(Paint.Style.FILL);
        p.setShader(bgc);
        c.drawRoundRect(r, 26 * d, 26 * d, p);
        p.setShader(null);
        st.setColor(Theme.PANEL_BORDER); st.setStrokeWidth(1 * d);
        c.drawRoundRect(r, 26 * d, 26 * d, st);

        // тёплый свет за цветком
        p.setShader(new RadialGradient(w / 2f, cardTop + cardH * 0.55f, cardH * 0.55f,
                Theme.GLOW, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(w / 2f, cardTop + cardH * 0.55f, cardH * 0.55f, p);
        p.setShader(null);

        // Кнопка вынесена из карточки: цветок больше не поджимается её полосой,
        // а действие читается как отдельная кнопка, а не как надпись на картинке.
        float labelY = cardTop + cardH - 22 * d;
        float plantBottom = labelY - 16 * d;
        float plantTop = cardTop + 34 * d;

        int stage = (canActive && stageBefore >= 0) ? stageBefore : game.stage[0];
        int species = game.species[0];
        float fsize = Math.min(Math.max(60 * d, plantBottom - plantTop),
                Math.min((w - pad * 2) * 0.86f, cardH * 0.78f));

        // Параметрический цветок: стебель-кривая, листья на узлах, лепестки кольцами
        // по золотому углу и ветер. Спрайты остаются только для гербария и открыток.
        float time = (System.currentTimeMillis() % 600000L) / 1000f;
        float wind = (float) (Math.sin(time * 0.85f) * 0.62f + Math.sin(time * 2.1f + 1.3f) * 0.38f);
        c.save();
        c.scale(pulse, pulse, w / 2f, plantBottom);
        KawaiiPlant.draw(c, species, stage, w / 2f, plantBottom, fsize, time, wind);
        c.restore();
        p.setAlpha(255);

        // стадии
        float dotsY = cardTop + 24 * d;
        for (int i = 0; i < 10; i++) {
            float cx = pad + 22 * d + i * 13 * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(i < stage ? Theme.ACCENT : Theme.STROKE);
            c.drawCircle(cx, dotsY, 3.4f * d, p);
        }
        centered(c, stage + " / 10", w - pad - 18 * d, dotsY, 9.5f, Theme.MUTED, false);

        // подпись
        centered(c, Species.FORM[Species.shape(species) % Species.FORM.length] + " · " + STAGE_NAMES[Math.min(10, stage)],
                w / 2f, labelY, 11.5f, Theme.MUTED, false);

        if (canActive) drawCan(c, w, plantBottom, System.currentTimeMillis());

        // кнопка-действие под карточкой
        String action = stage >= GameState.STAGES
                ? "Собрать букет"
                : (game.water > 0 ? "Полить" : "Нужна вода · пройди " + Ui.steps(game.stepCost()));
        boolean ready = stage >= GameState.STAGES || game.water > 0;
        float btnTop = cardTop + cardH + 16 * d;
        float btnBottom = btnTop + 52 * d;
        buttonRect.set(pad + 8 * d, btnTop, w - pad - 8 * d, btnBottom);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ready ? Theme.ACCENT : Theme.ACTION_OFF);
        c.drawRoundRect(buttonRect, 26 * d, 26 * d, p);
        centered(c, action, w / 2f, (btnTop + btnBottom) / 2f, 14,
                ready ? Theme.ON_ACCENT : Theme.MUTED, true);

        // ---- счётчик букета ----
        // Раньше здесь была сетка из 12 плашек с точками: они выглядели нажимаемыми,
        // показывали 12 из 365 и ничего не объясняли. Осталась одна тихая строка.
        centered(c, "в букете " + game.bloomed.size() + " "
                        + Ui.plural(game.bloomed.size(), "цветок", "цветка", "цветов")
                        + " из " + Species.COUNT,
                w / 2f, btnBottom + 26 * d, 11, Theme.FAINT, false);

        // блёстки поверх всего
        if (!sparks.isEmpty()) {
            p.setStyle(Paint.Style.FILL);
            for (Spark s : sparks) {
                if (s.a <= 0.01f) continue;
                p.setColor(s.color);
                p.setAlpha((int) (255 * s.a));
                c.save();
                c.rotate(s.rot, s.x, s.y);
                c.drawRoundRect(s.x - s.size, s.y - s.size * 0.45f,
                        s.x + s.size, s.y + s.size * 0.45f,
                        s.size * 0.45f, s.size * 0.45f, p);
                c.restore();
            }
            p.setAlpha(255);
        }

        // ветер: перерисовываем ~30 кадров/с, только пока сад реально на экране
        if (isShown() && hasWindowFocus()) postInvalidateDelayed(33);
    }

    // Полив срабатывает только по настоящему нажатию. Раньше действие
    // запускалось на ACTION_DOWN, поэтому любой свайп по саду считался
    // нажатием и выкидывал «Нужна вода» — теперь ждём ACTION_UP и проверяем,
    // что палец не уехал дальше порога.
    private float downX, downY;
    private boolean pressed;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float slop = 14 * d;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                pressed = card.contains(downX, downY) || buttonRect.contains(downX, downY);
                return pressed;
            case MotionEvent.ACTION_MOVE:
                if (pressed
                        && (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop)) {
                    pressed = false;   // это свайп или прокрутка, а не нажатие
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean tap = pressed
                        && Math.abs(e.getX() - downX) <= slop
                        && Math.abs(e.getY() - downY) <= slop;
                pressed = false;
                if (tap && listener != null) listener.onTap();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;       // родитель перехватил жест под свайп
                return true;
        }
        return super.onTouchEvent(e);
    }
}
