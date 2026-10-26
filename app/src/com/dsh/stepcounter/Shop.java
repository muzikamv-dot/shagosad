package com.dsh.stepcounter;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * Магазин визуала: рамки аватара.
 *
 * Все размеры мотивов считаются от радиуса r, а не от плотности экрана, поэтому
 * рамка одинаково читается и в списке магазина (64dp), и в профиле (92dp).
 * Мотивы живут в кольце r*0.84…r*1.06 и не залезают на лицо.
 */
public final class Shop {

    public static final String[] FRAME_NAMES = {
            "Без рамки", "Золотая", "Лиственная", "Цветочная",
            "Кристальная", "Звёздная", "Лавровая", "Неоновая"
    };

    /**
     * Цены растут быстрее дохода: первая рамка за день-два, последняя — за месяц.
     * При доходе ≈343 очка в день весь магазин (24 500) закрывается примерно за 71 день.
     */
    public static final int[] FRAME_PRICES = {0, 400, 900, 1600, 2600, 4000, 6000, 9000};

    /** Минимальный уровень для покупки: рамка — ещё и награда за путь. 0 — без ограничения. */
    public static final int[] FRAME_LEVEL = {0, 0, 0, 5, 8, 12, 18, 25};

    /** Цена с пробелами между тысячами: 9 000 вместо 9000. */
    public static String money(int v) {
        String s = String.valueOf(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    private Shop() { }

    public static boolean owned(GameState g, int id) {
        return id == 0 || g.ownedFrames.contains(id);
    }

    public static void drawFrame(Canvas c, float cx, float cy, float r, int id, float d) {
        if (id <= 0) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        float ring = r * 0.78f;

        switch (id) {
            case 1: golden(c, p, cx, cy, ring, r, d); break;
            case 2: leafy(c, p, cx, cy, ring, r, d); break;
            case 3: floral(c, p, cx, cy, ring, r, d); break;
            case 4: crystal(c, p, cx, cy, ring, r, d); break;
            case 5: starry(c, p, cx, cy, ring, r, d); break;
            case 6: laurel(c, p, cx, cy, ring, r, d); break;
            default: neon(c, p, cx, cy, ring, r, d); break;
        }
    }

    // ---------------- 1. золотая ----------------

    private static void golden(Canvas c, Paint p, float cx, float cy, float ring, float r, float d) {
        p.setShader(new SweepGradient(cx, cy,
                new int[]{0xFF9C6B1E, 0xFFFFE9A8, 0xFFC99A38, 0xFFFFF3C4,
                          0xFFB8892B, 0xFFFFE9A8, 0xFF9C6B1E},
                new float[]{0f, 0.16f, 0.34f, 0.5f, 0.68f, 0.86f, 1f}));
        p.setStrokeWidth(r * 0.075f);
        p.setShadowLayer(r * 0.09f, 0, 0, 0x55FFD98E);
        c.drawCircle(cx, cy, ring, p);
        p.setShadowLayer(0, 0, 0, 0);
        p.setShader(null);

        // внутренняя волосяная линия
        p.setStrokeWidth(r * 0.025f);
        p.setColor(0xCCFFF3C4);
        c.drawCircle(cx, cy, ring - r * 0.11f, p);

        // четыре ромба по диагоналям
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Math.PI / 4;
            float x = cx + (float) Math.cos(a) * ring;
            float y = cy + (float) Math.sin(a) * ring;
            Path d4 = new Path();
            d4.moveTo(x, y - r * 0.09f);
            d4.lineTo(x + r * 0.06f, y);
            d4.lineTo(x, y + r * 0.09f);
            d4.lineTo(x - r * 0.06f, y);
            d4.close();
            p.setColor(0xFFFFF6D6);
            c.drawPath(d4, p);
        }
        p.setStyle(Paint.Style.STROKE);
    }

    // ---------------- 2. лиственная ----------------

    private static void leafy(Canvas c, Paint p, float cx, float cy, float ring, float r, float d) {
        float from = 128f, to = 412f;             // открыто снизу
        RectF arc = new RectF(cx - ring, cy - ring, cx + ring, cy + ring);

        p.setStrokeWidth(r * 0.045f);
        p.setColor(0xFF2E8B5C);
        c.drawArc(arc, from, to - from, false, p);

        Paint leaf = new Paint(Paint.ANTI_ALIAS_FLAG);
        int n = 18;
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float a = from + (to - from) * t;
            double rad = Math.toRadians(a);
            float x = cx + (float) Math.cos(rad) * ring;
            float y = cy + (float) Math.sin(rad) * ring;
            boolean right = i % 2 == 0;
            c.save();
            c.translate(x, y);
            c.rotate(a + 90f + (right ? 42f : -42f));
            leaf.setShader(new LinearGradient(0, -r * 0.09f, 0, r * 0.09f,
                    0xFFA6F0C6, 0xFF2E8B5C, Shader.TileMode.CLAMP));
            Path lf = new Path();
            lf.moveTo(0, 0);
            lf.quadTo(r * 0.075f, -r * 0.06f, r * 0.155f, 0);
            lf.quadTo(r * 0.075f, r * 0.06f, 0, 0);
            c.drawPath(lf, leaf);
            leaf.setShader(null);
            c.restore();
        }
    }

    // ---------------- 3. цветочная ----------------

    private static void floral(Canvas c, Paint p, float cx, float cy, float ring, float r, float d) {
        p.setStrokeWidth(r * 0.03f);
        p.setColor(0x66F0A6C4);
        c.drawCircle(cx, cy, ring, p);

        int[] petal = {0xFFF7C6DA, 0xFFC4B4F5, 0xFFFFE9A8, 0xFFA8CDF5};
        int n = 6;
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n - Math.PI / 2;
            float x = cx + (float) Math.cos(a) * ring;
            float y = cy + (float) Math.sin(a) * ring;

            f.setColor(petal[i % petal.length]);
            for (int k = 0; k < 5; k++) {
                double b = Math.PI * 2 * k / 5 - Math.PI / 2;
                c.drawCircle(x + (float) Math.cos(b) * r * 0.068f,
                        y + (float) Math.sin(b) * r * 0.068f, r * 0.055f, f);
            }
            f.setColor(0xFFFFF0B8);
            c.drawCircle(x, y, r * 0.042f, f);
        }
    }

    // ---------------- 4. кристальная ----------------

    private static void crystal(Canvas c, Paint p, float cx, float cy, float ring, float r, float d) {
        p.setStrokeWidth(r * 0.05f);
        p.setColor(0xFF7FD6F0);
        p.setShadowLayer(r * 0.18f, 0, 0, 0x667FD6F0);
        c.drawCircle(cx, cy, ring, p);
        p.setShadowLayer(0, 0, 0, 0);
        p.setStrokeWidth(r * 0.02f);
        p.setColor(0xCCE6FBFF);
        c.drawCircle(cx, cy, ring - r * 0.10f, p);

        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setStyle(Paint.Style.FILL);
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            float x = cx + (float) Math.cos(a) * ring;
            float y = cy + (float) Math.sin(a) * ring;
            c.save();
            c.rotate((float) Math.toDegrees(a), x, y);
            Path shard = new Path();
            shard.moveTo(x - r * 0.055f, y);
            shard.lineTo(x, y - r * 0.145f);
            shard.lineTo(x + r * 0.055f, y);
            shard.lineTo(x, y + r * 0.05f);
            shard.close();
            f.setShader(new LinearGradient(x - r * 0.06f, y, x + r * 0.06f, y,
                    0xFFE6FBFF, 0x889FD9F5, Shader.TileMode.CLAMP));
            c.drawPath(shard, f);
            f.setShader(null);
            c.restore();
        }
    }

    // ---------------- 5. звёздная ----------------

    private static void starry(Canvas c, Paint p, float cx, float cy, float ring, float r, float d) {
        p.setStrokeWidth(r * 0.028f);
        p.setColor(0x88FFE9A8);
        p.setPathEffect(new DashPathEffect(new float[]{r * 0.10f, r * 0.10f}, 0));
        c.drawCircle(cx, cy, ring, p);
        p.setPathEffect(null);

        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setStyle(Paint.Style.FILL);
        int n = 10;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n - Math.PI / 2;
            float x = cx + (float) Math.cos(a) * ring;
            float y = cy + (float) Math.sin(a) * ring;
            float big = (i % 2 == 0) ? r * 0.11f : r * 0.075f;
            f.setColor(i % 2 == 0 ? 0xFFFFF0B8 : 0xFFFFD35C);
            c.save();
            c.rotate((float) Math.toDegrees(a) + 90, x, y);
            Path star = new Path();
            star.moveTo(x, y - big);
            star.quadTo(x + big * 0.18f, y - big * 0.18f, x + big, y);
            star.quadTo(x + big * 0.18f, y + big * 0.18f, x, y + big);
            star.quadTo(x - big * 0.18f, y + big * 0.18f, x - big, y);
            star.quadTo(x - big * 0.18f, y - big * 0.18f, x, y - big);
            star.close();
            c.drawPath(star, f);
            c.restore();
        }
    }

    // ---------------- 6. лавровая ----------------

    private static void laurel(Canvas c, Paint p, float cx, float cy, float ring, float r, float d) {
        // два побега по бокам, открыто сверху и снизу
        RectF arc = new RectF(cx - ring, cy - ring, cx + ring, cy + ring);
        p.setStrokeWidth(r * 0.04f);
        p.setColor(0xFF7C9440);
        c.drawArc(arc, 150f, 60f, false, p);
        c.drawArc(arc, 330f, 60f, false, p);
        // соединяющие дуги по бокам
        p.setStrokeWidth(r * 0.02f);
        p.setColor(0x66C9D77A);
        c.drawArc(arc, 40f, 70f, false, p);
        c.drawArc(arc, 220f, 70f, false, p);

        Paint leaf = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 5; i++) {
                float a = side == 0 ? 202f + i * 17f : 22f + i * 17f;
                double rad = Math.toRadians(a);
                float x = cx + (float) Math.cos(rad) * ring;
                float y = cy + (float) Math.sin(rad) * ring;
                boolean outward = (i % 2 == 0);
                c.save();
                c.translate(x, y);
                c.rotate(a + 90f + (outward ? 55f : -55f));
                leaf.setShader(new LinearGradient(0, 0, r * 0.14f, 0, 0xFFC9D77A, 0xFF6F8A38,
                        Shader.TileMode.CLAMP));
                Path lf = new Path();
                lf.moveTo(0, 0);
                lf.quadTo(r * 0.07f, -r * 0.05f, r * 0.14f, 0);
                lf.quadTo(r * 0.07f, r * 0.05f, 0, 0);
                c.drawPath(lf, leaf);
                leaf.setShader(null);
                c.restore();
            }
        }
        // ягодка сверху
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(0xFFE8749F);
        c.drawCircle(cx, cy - ring, r * 0.06f, f);
        f.setColor(0xFFFFD1E0);
        c.drawCircle(cx - r * 0.02f, cy - ring - r * 0.02f, r * 0.022f, f);
    }

    // ---------------- 7. неоновая ----------------

    private static void neon(Canvas c, Paint p, float cx, float cy, float ring, float r, float d) {
        for (int i = 3; i >= 1; i--) {
            p.setStrokeWidth(r * (0.06f + i * 0.05f));
            p.setColor(Color.argb(26 + i * 8, 127, 227, 255));
            c.drawCircle(cx, cy, ring, p);
        }
        p.setStrokeWidth(r * 0.075f);
        p.setColor(0xFF7FE3FF);
        p.setShadowLayer(r * 0.10f, 0, 0, 0xAA7FE3FF);
        c.drawCircle(cx, cy, ring, p);
        p.setShadowLayer(0, 0, 0, 0);
        p.setStrokeWidth(r * 0.025f);
        p.setColor(0xFFE6FBFF);
        c.drawCircle(cx, cy, ring - r * 0.09f, p);
    }
}
