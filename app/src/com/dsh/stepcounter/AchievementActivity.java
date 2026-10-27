package com.dsh.stepcounter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Полноэкранное награждение за достижение.
 *
 * Крупная медаль вращается в 3D (Camera.rotateY) — видно лицо, ребро и обратную
 * сторону, размер при этом не «прыгает». Вокруг идёт салют: несколько типов
 * залпов, следы частиц и искры. Ниже медали — «ДОСТИЖЕНИЕ ПОЛУЧЕНО», название
 * достижения и кнопка «Закрыть».
 *
 * Если достижений несколько, окна идут по очереди: следующее открывается после
 * нажатия «Закрыть».
 */
public class AchievementActivity extends Activity {

    private static final String EXTRA_ID = "id";

    /** Очередь на показ в пределах запуска: первое окно забирает голову списка. */
    private static final ArrayList<String> QUEUE = new ArrayList<>();

    static void showQueue(Context ctx, List<Achievements.Ach> list) {
        QUEUE.clear();
        for (Achievements.Ach a : list) QUEUE.add(a.id);
        if (QUEUE.isEmpty()) return;
        Intent i = new Intent(ctx, AchievementActivity.class);
        i.putStringArrayListExtra("queue", new ArrayList<>(QUEUE));
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.load(this);
        Theme.bars(this);

        String id = getIntent().getStringExtra(EXTRA_ID);
        ArrayList<String> queue = getIntent().getStringArrayListExtra("queue");
        if (id == null && queue != null && !queue.isEmpty()) {
            id = queue.remove(0);
            getIntent().putStringArrayListExtra("queue", queue);
        }
        Achievements.Ach ach = id == null ? null : Achievements.byId(id);
        if (ach == null) { finish(); return; }

        // отмечен сразу: окно показалось один раз и больше не вернётся
        Achievements.markShown(this, ach.id);

        int color = ach.color;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG_TOP);

        Fireworks fireworks = new Fireworks(this, color);
        root.addView(fireworks, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(28), dp(30), dp(28), dp(26));

        final MedalView medal = new MedalView(this, ach.id, color);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(medal, mlp);

        // «ДОСТИЖЕНИЕ ПОЛУЧЕНО» ниже медали и крупнее — так читается сразу
        TextView cap = new TextView(this);
        cap.setText("ДОСТИЖЕНИЕ ПОЛУЧЕНО");
        cap.setTextColor(Theme.ACCENT_SOFT);
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        cap.setLetterSpacing(0.12f);
        cap.setGravity(Gravity.CENTER);
        cap.setIncludeFontPadding(false);
        cap.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        column.addView(cap, clp);

        TextView title = new TextView(this);
        title.setText(ach.title);
        title.setTextColor(Theme.TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        column.addView(title, tlp);

        TextView desc = new TextView(this);
        desc.setText(ach.desc);
        desc.setTextColor(Theme.MUTED);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(6);
        column.addView(desc, dlp);

        Button close = new Button(this);
        close.setText("Закрыть");
        close.setAllCaps(false);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        close.setTextColor(Theme.ON_ACCENT);
        // скругление как у остальных кнопок приложения (26 dp), иначе угловатый вид выбивается
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.ACCENT);
        bg.setCornerRadius(dp(26));
        close.setBackground(bg);
        close.setPadding(0, 0, 0, 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startNextOrFinish();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        blp.topMargin = dp(20);
        column.addView(close, blp);

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    /** Следующее достижение в очереди — новым окном, иначе закрываемся. */
    private void startNextOrFinish() {
        ArrayList<String> queue = getIntent().getStringArrayListExtra("queue");
        if (queue != null && !queue.isEmpty()) {
            Intent i = new Intent(this, AchievementActivity.class);
            i.putStringArrayListExtra("queue", queue);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        startNextOrFinish();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    // ---------------- медаль ----------------

    /**
     * Медаль крутится вокруг вертикальной оси по часовой стрелке.
     *
     * Вращение настоящее, через Camera.rotateY: у медали есть перспектива, поэтому
     * кажется, что она поворачивается, а не «сжимается». Размер диска при этом
     * не меняется — раньше сжатие по косинусу читалось как «медаль то больше,
     * то меньше». Толщина (ребро) видна на повороте, а с обратной стороны рисуется
     * другой лик — золотой диск с кольцом и звездой.
     */
    private static final class MedalView extends View {

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF dst = new RectF();

        private final Bitmap source;
        private Bitmap scaled;
        private final int color;
        private final long born = SystemClock.uptimeMillis();

        private Path clip;
        private Shader glowShader;
        private float glowR;
        private float glowCx, glowCy;

        MedalView(Context ctx, String id, int color) {
            super(ctx);
            this.color = color;
            Bitmap full = null;
            try {
                int rid = ctx.getResources().getIdentifier("medal_" + id, "drawable",
                        ctx.getPackageName());
                if (rid != 0) full = BitmapFactory.decodeResource(ctx.getResources(), rid);
            } catch (Exception ignored) { }

            // Ленты под медалью не вращаем: иначе они сжимаются вместе с диском,
            // и кажется, что медаль меняет размер. Берём верхнюю часть картинки.
            // Ленты под медалью не нужны, и из-за них передняя сторона казалась
            // больше обратной. Берём только диск: в картинке 256x256 он занимает
            // квадрат 176x176 начиная с (40, 34) — замерено по самим PNG.
            Bitmap coin = null;
            if (full != null) {
                try {
                    int x = Math.round(full.getWidth() * 0.168f);
                    int y = Math.round(full.getHeight() * 0.133f);
                    int d = Math.round(full.getWidth() * 0.664f);
                    coin = Bitmap.createBitmap(full, x, y, d, d);
                } catch (Exception e) {
                    coin = full;
                }
            }
            source = coin;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float r = Math.min(w * 0.38f, h * 0.38f);
            glowCx = w / 2f;
            glowCy = h / 2f;
            glowR = r * 1.8f;
            clip = new Path();
            clip.addCircle(glowCx, glowCy, r, Path.Direction.CW);
            glowShader = new RadialGradient(glowCx, glowCy, glowR,
                    (color & 0x00FFFFFF) | 0x66000000, 0x00000000, Shader.TileMode.CLAMP);
            // масштабируем один раз и с сохранением пропорций: квадратом картинку
            // растягивало, и монета выглядела вытянутой
            if (source != null) {
                int sw = Math.max(1, Math.round(r * 2f));
                int sh = Math.max(1, Math.round(sw * source.getHeight()
                        / (float) source.getWidth()));
                scaled = Bitmap.createScaledBitmap(source, sw, sh, true);
            }
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w * 0.38f, h * 0.38f);

            // один оборот за 2,6 с, по часовой стрелке
            float deg = ((SystemClock.uptimeMillis() - born) % 2600L) / 2600f * 360f;
            float rad = (float) Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            if (glowShader != null) {
                p.setShader(glowShader);
                c.drawCircle(glowCx, glowCy, glowR, p);
                p.setShader(null);
            }

            // ребро позади диска: толщина растёт к боковому положению
            float bh = coinHeight(r);
            float flat = Math.abs(sin);
            if (flat > 0.18f) {
                float thick = r * 0.055f * flat;
                rim.setShader(new LinearGradient(cx - thick, cy, cx + thick, cy,
                        0xFF6A5120, 0xFFD8B25A, Shader.TileMode.CLAMP));
                // ребро рисуем овалом по размеру самой монеты — иначе сверху и снизу
                // торчат «нашлёпки» за краем диска
                c.drawOval(cx - thick, cy - r * 0.98f, cx + thick, cy + r * 0.98f, rim);
                rim.setShader(null);
            }

            // Диск вращается сжатием по горизонтали: высота не меняется, поэтому
            // размер не «прыгает», а читается именно поворот.
            float sx = Math.max(0.05f, Math.abs(cos));
            dst.set(cx - r, cy - bh / 2f, cx + r, cy + bh / 2f);

            int save = c.save();
            c.scale(sx, 1f, cx, cy);
            if (cos >= 0f && scaled != null) {
                p.setAlpha(255);
                // клип по кругу: даже если в картинке осталась лента, она не покажется
                if (clip != null) c.clipPath(clip);
                c.drawBitmap(scaled, null, dst, p);
            } else {
                // обратная сторона: золотой диск с кольцом и звездой
                p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.7f,
                        0xFFFFF3CE, 0xFFB98C34, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.06f);
                p.setColor(0x88FFF6D8);
                c.drawCircle(cx, cy, r * 0.78f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xE6FFF6D8);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(r * 0.76f);
                p.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                c.drawText("★", cx, cy + r * 0.27f, p);
            }
            c.restoreToCount(save);

            p.setAlpha(255);
            postInvalidateOnAnimation();
        }

        /** Высота диска с сохранением пропорций картинки (иначе монета вытягивается). */
        private float coinHeight(float r) {
            return r * 2f;
        }
    }

    // ---------------- салют ----------------

    /**
     * Праздничный салют: залпы трёх видов (кольцо, звезда, «ива»), следы частиц,
     * золотая пыль и белые искры. Яркость взята выше, чем в первой версии.
     */
    private static final class Fireworks extends View {

        private static final int[] PALETTE = {
                0xFFFFFFFF, 0xFFFFD166, 0xFFFF8FB1, 0xFF8FE3FF, 0xFFB6FF8F, 0xFFFFB86B
        };

        private static final class Spark {
            float x, y, px, py, vx, vy, life, max, size;
            int color;
            boolean trail;
        }

        private final ArrayList<Spark> sparks = new ArrayList<>();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private final int accent;
        private long nextBurst = 0;
        private long lastFrame = 0;

        Fireworks(Context ctx, int accent) {
            super(ctx);
            this.accent = accent;
            p.setStrokeCap(Paint.Cap.ROUND);
            glow.setColor(0xFFFFFFFF);
            glow.setAlpha(36);
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;

            if (now >= nextBurst) {
                burst();
                if (rnd.nextInt(3) != 0) burst();
                nextBurst = now + 260 + rnd.nextInt(340);
            }

            for (int i = sparks.size() - 1; i >= 0; i--) {
                Spark s = sparks.get(i);
                s.life -= dt;
                if (s.life <= 0f) { sparks.remove(i); continue; }
                s.px = s.x;
                s.py = s.y;
                s.vy += 300f * dt;
                s.vx *= (1f - 0.55f * dt);
                s.x += s.vx * dt;
                s.y += s.vy * dt;

                float k = Math.max(0f, s.life / s.max);
                int alpha = (int) (255 * Math.min(1f, k * 1.6f));
                p.setColor(s.color);
                p.setAlpha(alpha);
                if (s.trail) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(s.size * 0.9f);
                    c.drawLine(s.px, s.py, s.x, s.y, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(s.x, s.y, s.size * (0.5f + 0.5f * k), p);
            }
            p.setAlpha(255);

            // общее свечение салюта — «праздничнее» без лишних частиц
            glow.setColor(accent);
            glow.setAlpha(28);
            for (int i = sparks.size() - 1; i >= Math.max(0, sparks.size() - 40); i--) {
                Spark s = sparks.get(i);
                c.drawCircle(s.x, s.y, s.size * 3.4f, glow);
            }

            postInvalidateOnAnimation();
        }

        private void burst() {
            float cx = getWidth() * (0.12f + rnd.nextFloat() * 0.76f);
            float cy = getHeight() * (0.08f + rnd.nextFloat() * 0.30f);
            int kind = rnd.nextInt(3);
            int mainColor = rnd.nextBoolean() ? accent : PALETTE[1 + rnd.nextInt(PALETTE.length - 1)];

            int n = kind == 0 ? 46 : kind == 1 ? 54 : 34;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n + (rnd.nextDouble() - 0.5) * 0.12;
                float speed;
                if (kind == 0) {
                    speed = 210f;                              // кольцо
                } else if (kind == 1) {
                    speed = (i % 2 == 0) ? 260f : 120f;        // звезда
                } else {
                    speed = 90f + rnd.nextFloat() * 90f;       // ива
                }
                Spark s = new Spark();
                s.x = cx; s.y = cy; s.px = cx; s.py = cy;
                s.vx = (float) Math.cos(a) * speed;
                s.vy = (float) Math.sin(a) * speed;
                s.max = (kind == 2 ? 1.6f : 1.0f) + rnd.nextFloat() * 0.5f;
                s.life = s.max;
                s.size = (kind == 1 ? 2.0f : 2.8f) + rnd.nextFloat() * 2.4f;
                s.color = (i % 4 == 0) ? PALETTE[0] : mainColor;
                s.trail = kind != 2;
                sparks.add(s);
            }
            // золотая пыль вокруг залпа
            for (int i = 0; i < 14; i++) {
                Spark s = new Spark();
                double a = rnd.nextDouble() * Math.PI * 2;
                float d = rnd.nextFloat() * 60f;
                s.x = cx + (float) Math.cos(a) * d;
                s.y = cy + (float) Math.sin(a) * d;
                s.px = s.x; s.py = s.y;
                s.vx = (rnd.nextFloat() - 0.5f) * 60f;
                s.vy = (rnd.nextFloat() - 0.5f) * 60f;
                s.max = 0.9f + rnd.nextFloat() * 0.9f;
                s.life = s.max;
                s.size = 1.6f + rnd.nextFloat() * 1.8f;
                s.color = PALETTE[1];
                sparks.add(s);
            }
            while (sparks.size() > 900) sparks.remove(0);
        }
    }
}
