package com.dsh.stepcounter;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Параметрический цветок: стебель-кривая с сужением, листья на узлах стебля,
 * лепестки кольцами по золотому углу и ветер, который качает листья и головку.
 *
 * Никаких ассетов: вид 0..364 полностью определяется своим номером, поэтому
 * 365 видов рисуются одним и тем же кодом и занимают ноль байт в APK.
 */
public final class KawaiiPlant {

    private KawaiiPlant() { }

    private static final float GOLDEN = 137.50776f;

    /**
     * Милая палитра: сырой оттенок вида (id·137.5 mod 360) часто попадает в мутные
     * коричнево-оливковые зоны. Здесь вид выбирает один из девяти милых оттенков
     * плюс небольшой сдвиг, поэтому все 365 цветков остаются разными, но не грязными.
     */
    private static final float[] CUTE_HUES = {338f, 355f, 302f, 272f, 222f, 190f, 160f, 46f, 20f};

    /** Оттенок вида в милой палитре: общий для сада, гербария и открыток. */
    public static float hueFor(int species) { return cuteHue(species); }

    private static float cuteHue(int species) {
        int k = Math.abs(species * 7 + species / 9) % CUTE_HUES.length;
        float jitter = (((species * 37) % 11) - 5) * 1.7f;
        return (CUTE_HUES[k] + jitter + 360f) % 360f;
    }

    // ---- геометрия кривой ----

    private static float bx(float t, float p0, float p1, float p2, float p3) {
        float u = 1f - t;
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3;
    }

    /** Рисует растение. time — секунды (для мелкого дрожания), wind — [-1..1]. */
    public static void draw(Canvas c, int species, int stage, float cx, float baseY,
                            float size, float time, float wind) {
        if (size <= 0) return;
        Random rnd = new Random(species * 977L + 13L);

        float grow = 0.26f + Math.min(10, Math.max(0, stage)) * 0.074f;   // 0.26 … 1.0
        // 0.70, а не 0.80: остаётся место над головкой, иначе цветок наезжает
        // на ряд точек стадий в верхней части карточки
        float h = size * 0.70f * Math.min(1f, grow);

        int stemCol = Species.stemColor(species);
        float[] hsv = new float[3];
        Color.colorToHSV(stemCol, hsv);
        int stemDark = Color.HSVToColor(new float[]{hsv[0],
                Math.min(1f, hsv[1] * 1.15f), hsv[2] * 0.72f});
        // листья светлее стебля, иначе сливаются с фоном и читаются как палочки
        int leafA = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 1.0f), 0.82f});
        int leafB = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 1.1f), 0.56f});

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();

        // ---- тень под растением ----
        float shadowR = size * (0.10f + 0.05f * grow);
        p.setShader(new RadialGradient(cx, baseY, shadowR,
                0x33000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawOval(cx - shadowR, baseY - shadowR * 0.22f, cx + shadowR, baseY + shadowR * 0.22f, p);
        p.setShader(null);

        // на нулевой стадии стебля ещё нет — только семя на земле
        if (stage <= 0) {
            seedHead(c, p, cx, baseY, size);
            return;
        }

        // ---- стебель: кривая Безье + ветер ----
        float lean = wind * size * 0.045f + (rnd.nextFloat() - 0.5f) * size * 0.04f;
        float s = (float) Math.sin(time * 1.25f + species * 0.7f) * size * 0.006f;
        float x0 = cx, y0 = baseY;
        float x1 = cx + lean * 0.25f, y1 = baseY - h * 0.38f;
        float x2 = cx + lean * 0.75f + s, y2 = baseY - h * 0.72f;
        float x3 = cx + lean + s * 1.6f, y3 = baseY - h;

        float wBase = size * 0.022f, wTop = size * 0.008f;
        int steps = 18;
        path.reset();
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = bx(t, x0, x1, x2, x3);
            float y = bx(t, y0, y1, y2, y3);
            float dx = x - cx, dy = y - baseY;
            float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
            float nx = -dy / len, ny = dx / len;
            float w = wBase + (wTop - wBase) * t;
            if (i == 0) path.moveTo(x + nx * w, y + ny * w);
            else path.lineTo(x + nx * w, y + ny * w);
        }
        for (int i = steps; i >= 0; i--) {
            float t = i / (float) steps;
            float x = bx(t, x0, x1, x2, x3);
            float y = bx(t, y0, y1, y2, y3);
            float dx = x - cx, dy = y - baseY;
            float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
            float nx = -dy / len, ny = dx / len;
            float w = wBase + (wTop - wBase) * t;
            path.lineTo(x - nx * w, y - ny * w);
        }
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - size * 0.1f, baseY, cx + size * 0.1f, baseY - h,
                stemDark, stemCol, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);

        // ---- листья на узлах стебля ----
        int leaves = stage <= 1 ? 0 : Math.min(5, 1 + (stage - 1) / 2);
        int leafForm = Math.abs(species * 7 + 3) % 4;
        for (int i = 0; i < leaves; i++) {
            float t = 0.16f + 0.62f * (i + 0.6f) / Math.max(1, leaves);
            float lx = bx(t, x0, x1, x2, x3);
            float ly = bx(t, y0, y1, y2, y3);
            boolean right = (i % 2 == 0);
            float len = size * (0.165f - t * 0.05f) * (0.85f + rnd.nextFloat() * 0.3f);
            float baseAngle = 34f + rnd.nextFloat() * 22f;
            // справа лист отходит вверх-вправо, слева — зеркально вверх-влево
            float ang = (right ? -baseAngle : -(180f - baseAngle));
            float sway = wind * (7f + 5f * (1f - t)) * (right ? 1f : -1f)
                    + (float) Math.sin(time * 1.9f + i * 1.3f) * 2.6f;
            leaf(c, q, lx, ly, len, ang + sway, leafForm, leafA, leafB, right);
        }

        // ---- головка ----
        if (stage <= 4) {
            bud(c, q, species, x3, y3, size, stage, rnd, wind, time);
        } else {
            float open = Math.min(1f, (stage - 4) / 6f);
            bloom(c, q, species, x3, y3, size, open, rnd, wind, time);
        }
    }

    // ---- лист ----

    private static void leaf(Canvas c, Paint p, float x, float y, float len, float angleDeg,
                             int form, int colLight, int colDark, boolean right) {
        c.save();
        c.translate(x, y);
        c.rotate(angleDeg);

        Path path = new Path();
        float w = len * (form == 1 ? 0.26f : 0.42f);
        if (form == 2) {
            // зубчатый: пила по верхней кромке, низ — плавная дуга назад.
            // Раньше возврат шёл из середины, контур самопересекался и выходил «бантик».
            path.moveTo(0, 0);
            int teeth = 5;
            for (int i = 1; i <= teeth; i++) {
                float tx = len * i / teeth;
                path.lineTo(tx - len * 0.05f, -w * 0.62f);
                path.lineTo(tx, -w * 0.88f);
            }
            path.lineTo(len, 0);
            path.quadTo(len * 0.55f, w * 0.62f, 0, 0);
        } else if (form == 3) {
            path.moveTo(0, 0);
            path.quadTo(len * 0.35f, -w * 1.35f, len, 0);
            path.quadTo(len * 0.35f, w * 1.35f, 0, 0);
        } else if (form == 1) {
            path.moveTo(0, 0);
            path.quadTo(len * 0.55f, -w, len * 1.06f, 0);
            path.quadTo(len * 0.55f, w, 0, 0);
        } else {
            path.moveTo(0, 0);
            path.quadTo(len * 0.45f, -w * 1.1f, len, 0);
            path.quadTo(len * 0.5f, w * 0.95f, 0, 0);
        }
        path.close();

        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, len, 0,
                right ? colDark : colLight, right ? colLight : colDark, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);

        // прожилка светлая: тёмная на тёмном фоне не видна
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, len * 0.035f));
        p.setColor(0x44FFFFFF);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(len * 0.08f, 0, len * 0.84f, 0, p);
        c.restore();
    }

    // ---- бутон ----

    private static void bud(Canvas c, Paint p, int species, float x, float y, float size,
                            int stage, Random rnd, float wind, float time) {
        float k = 0.35f + (stage - 1) * 0.14f;
        float len = size * 0.085f * k;
        float wid = len * 0.62f;
        float hue = cuteHue(species);
        int light = Color.HSVToColor(new float[]{hue, 0.80f, 1f});
        int dark = Color.HSVToColor(new float[]{(hue + 7f) % 360f, 0.88f, 0.93f});

        // чашелистики
        p.setStyle(Paint.Style.FILL);
        p.setColor(Species.stemColor(species));
        Path sep = new Path();
        sep.moveTo(x, y);
        sep.quadTo(x - wid * 0.9f, y - len * 0.35f, x, y - len * 0.55f);
        sep.quadTo(x + wid * 0.9f, y - len * 0.35f, x, y);
        c.drawPath(sep, p);

        // закрытые лепестки
        for (int i = -1; i <= 1; i++) {
            c.save();
            c.translate(x, y);
            c.rotate(i * 17f + wind * 4f);
            Path pet = new Path();
            pet.moveTo(0, 0);
            pet.quadTo(-wid * 0.85f, -len * 0.68f, 0, -len * (1f + Math.abs(i) * 0.05f));
            pet.quadTo(wid * 0.85f, -len * 0.68f, 0, 0);
            p.setShader(new LinearGradient(0, 0, 0, -len,
                    dark, i == 0 ? light : dark, Shader.TileMode.CLAMP));
            c.drawPath(pet, p);
            p.setShader(null);
            c.restore();
        }
    }

    // ---- цветение ----

    private static void bloom(Canvas c, Paint p, int species, float x, float y, float size,
                              float open, Random rnd, float wind, float time) {
        int shape = Species.shape(species);
        float hue = cuteHue(species);
        int light = Color.HSVToColor(new float[]{hue, 0.86f, 1f});      // кончик: насыщенный
        int dark = Color.HSVToColor(new float[]{hue, 0.28f, 1f});       // основание: светлое горло
        int core = Species.coreColor(species);
        float R = size * 0.265f * (0.55f + 0.45f * open);
        float tilt = wind * 5f + (float) Math.sin(time * 1.4f + species) * 1.4f;

        c.save();
        c.translate(x, y);
        c.rotate(tilt);

        // мягкое свечение за головкой: даёт глубину на тёмном фоне
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0, 0, R * 1.6f,
                (light & 0x00FFFFFF) | 0x3A000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, R * 1.6f, p);
        p.setShader(null);
        p.setAlpha(255);

        int count = Math.max(5, Species.petals(species));
        int layers = Math.max(1, Math.min(3, Species.layers(species)));

        if (shape == 3) {
            // лаванда: колос
            for (int i = 0; i < 8; i++) {
                float yy = -i * R * 0.36f;
                float rr = R * (0.34f - i * 0.022f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(i % 2 == 0 ? light : dark);
                p.setShader(new RadialGradient(-rr, yy, rr,
                        light, dark, Shader.TileMode.CLAMP));
                c.drawCircle(-rr * 0.75f + wind * i, yy, rr, p);
                c.drawCircle(rr * 0.75f + wind * i, yy, rr, p);
                p.setShader(null);
            }
        } else if (shape == 2) {
            // мак: четыре крупных лепестка с золотым смещением
            for (int i = 0; i < 4; i++) {
                float a = 90f * i + 45f + (rnd.nextFloat() - 0.5f) * 5f;
                petal(c, p, a, R * 1.16f, R * 0.62f, dark, light, 1.6f);
            }
        } else if (shape == 5) {
            // тюльпан: чаша из трёх лепестков
            for (int i = -1; i <= 1; i++) {
                c.save();
                c.rotate(i * 20f);
                Path cup = new Path();
                cup.moveTo(0, R * 0.75f);
                cup.quadTo(-R * 0.72f, -R * 0.15f, 0, -R * (1.05f + 0.05f * open));
                cup.quadTo(R * 0.72f, -R * 0.15f, 0, R * 0.75f);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, -R, 0, R, light, dark, Shader.TileMode.CLAMP));
                c.drawPath(cup, p);
                p.setShader(null);
                c.restore();
            }
        } else if (shape == 7 || shape == 8) {
            // звезда и кристалл: угловатые лепестки кольцами
            for (int l = 0; l < Math.max(1, layers); l++) {
                float rr = R * (1f - l * 0.24f);
                int n = Math.max(5, count - l * 2);
                for (int i = 0; i < n; i++) {
                    c.save();
                    c.rotate(360f * i / n + l * GOLDEN * 0.5f
                            + (rnd.nextFloat() - 0.5f) * 3f);
                    Path sp = new Path();
                    float wide = rr * (shape == 8 ? 0.22f : 0.30f);
                    sp.moveTo(0, -wide * 0.5f);
                    sp.lineTo(rr * 0.62f, -wide);
                    sp.lineTo(rr, 0);
                    sp.lineTo(rr * 0.62f, wide);
                    sp.lineTo(0, wide * 0.5f);
                    sp.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setShader(new LinearGradient(0, 0, rr, 0, light, dark, Shader.TileMode.CLAMP));
                    c.drawPath(sp, p);
                    p.setShader(null);
                    c.restore();
                }
            }
        } else if (shape == 9) {
            // колокольчик: свешивающиеся чашечки
            for (int i = 0; i < 4; i++) {
                c.save();
                c.rotate(30f * i - 45f);
                Path bell = new Path();
                bell.moveTo(0, 0);
                bell.quadTo(-R * 0.5f, R * 0.35f, -R * 0.38f, R * 1.0f * open);
                bell.quadTo(0, R * 1.24f * open, R * 0.38f, R * 1.0f * open);
                bell.quadTo(R * 0.5f, R * 0.35f, 0, 0);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, 0, 0, R, light, dark, Shader.TileMode.CLAMP));
                c.drawPath(bell, p);
                p.setShader(null);
                c.restore();
            }
        } else if (shape == 10) {
            // зонтик: шапка мелких цветков
            int n = 9;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                float xx = (float) Math.cos(a) * R * 0.78f;
                float yy = (float) Math.sin(a) * R * 0.78f - R * 0.1f;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(xx, yy, R * 0.28f, light, dark, Shader.TileMode.CLAMP));
                c.drawCircle(xx, yy, R * 0.27f * open, p);
                p.setShader(null);
                p.setColor(core);
                c.drawCircle(xx, yy, R * 0.08f * open, p);
            }
        } else {
            // ромашка / пион / георгин: кольца лепестков по золотому углу
            for (int l = 0; l < layers; l++) {
                float rr = R * (1f - l * 0.21f);
                int n = Math.max(4, count - l * 2);
                float off = l * GOLDEN * 0.5f;
                for (int i = 0; i < n; i++) {
                    float a = 360f * i / n + off + (rnd.nextFloat() - 0.5f) * 4f;
                    float L = rr * (0.92f + rnd.nextFloat() * 0.14f);
                    petal(c, p, a, L, rr * 0.27f, dark, l == layers - 1 ? light : dark, 0.9f + l * 0.2f);
                }
            }
        }

        // серединка с бликом и тычинками
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(-R * 0.08f, -R * 0.08f, R * 0.34f,
                Color.WHITE, core, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, R * 0.24f * (0.6f + 0.4f * open), p);
        p.setShader(null);
        p.setColor(0x66FFFFFF);
        c.drawCircle(-R * 0.06f, -R * 0.08f, R * 0.055f * open, p);
        p.setColor(0x55FFFFFF);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            float dx = (float) Math.cos(a) * R * 0.17f;
            float dy = (float) Math.sin(a) * R * 0.17f;
            c.drawCircle(dx, dy, R * 0.022f * open, p);
        }

        c.restore();
    }

    /** Один лепесток: от основания к закруглённому кончику, с градиентом и бликом. */
    private static void petal(Canvas c, Paint p, float angleDeg, float len, float halfW,
                              int baseCol, int tipCol, float curl) {
        c.save();
        c.rotate(angleDeg);

        Path path = new Path();
        path.moveTo(0, 0);
        // левая сторона
        path.cubicTo(len * 0.30f, -halfW * 1.25f,
                len * 0.78f, -halfW * (1.15f - 0.1f * curl),
                len, 0);
        // правая сторона
        path.cubicTo(len * 0.78f, halfW * (1.15f - 0.1f * curl),
                len * 0.30f, halfW * 1.25f,
                0, 0);
        path.close();

        // alpha сбрасываем: она тянется от предыдущих setColor с прозрачными цветами
        // и умножала цвет лепестков, из-за чего цветок выглядел полупрозрачным
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, len, 0, baseCol, tipCol, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);

        // мягкий блик у кончика
        p.setColor(Color.WHITE);
        p.setAlpha(0x1E);
        c.save();
        c.scale(1f, 0.62f);
        c.drawCircle(len * 0.70f, 0, len * 0.16f, p);
        c.restore();
        p.setAlpha(255);
        c.restore();
    }

    // ---- семя ----

    private static void seedHead(Canvas c, Paint p, float x, float y, float size) {
        float r = size * 0.045f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.4f, r * 1.4f,
                0xFFF3E3C4, 0xFF8A6A44, Shader.TileMode.CLAMP));
        c.drawOval(x - r, y - r * 1.25f, x + r, y + r * 0.55f, p);
        p.setShader(null);
        p.setColor(0x88FFFFFF);
        c.drawOval(x - r * 0.55f, y - r * 0.95f, x - r * 0.05f, y - r * 0.45f, p);
    }

    // ---- мелкие помощники ----
}
