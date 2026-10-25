package com.dsh.stepcounter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Рисует любой из 365 видов на любой стадии — без картинок-ассетов. */
public final class FlowerPainter {

    private FlowerPainter() { }

    public static Bitmap render(int id, int stage, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        draw(c, id, stage, size / 2f, size - size * 0.07f, size * 0.9f);
        return bmp;
    }

    public static void draw(Canvas c, int id, int stage, float cx, float baseY, float size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();

        float grow = 0.25f + Math.min(9, stage) * 0.075f;
        float tall = size * grow;
        int stem = Species.stemColor(id);

        // тень
        p.setShader(new RadialGradient(cx, baseY + size * 0.01f, size * 0.14f,
                0x40000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawOval(cx - size * 0.14f, baseY - size * 0.03f,
                cx + size * 0.14f, baseY + size * 0.03f, p);
        p.setShader(null);

        // стебель
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.018f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(stem);
        path.reset();
        path.moveTo(cx, baseY);
        path.quadTo(cx + size * 0.01f, baseY - tall * 0.55f, cx, baseY - tall);
        c.drawPath(path, p);

        // листья
        p.setStyle(Paint.Style.FILL);
        int leafColor = Color.HSVToColor(new float[]{118f, 0.55f, 0.72f});
        if (stage >= 1) leaf(c, cx - size * 0.02f, baseY - tall * 0.42f, size * 0.10f, leafColor, -0.5f);
        if (stage >= 2) leaf(c, cx + size * 0.02f, baseY - tall * 0.68f, size * 0.09f, leafColor, 0.55f);
        if (stage >= 4) leaf(c, cx - size * 0.02f, baseY - tall * 0.26f, size * 0.075f, leafColor, -0.95f);

        float topY = baseY - tall;
        int petal = Species.petalColor(id);
        int dark = Species.petalDark(id);
        int core = Species.coreColor(id);

        if (stage <= 1) {
            p.setColor(dark);
            c.drawCircle(cx, topY, size * 0.018f + stage * size * 0.012f, p);
        } else if (stage <= 5) {
            float bh = size * (0.035f + (stage - 2) * 0.012f);
            p.setShader(new LinearGradient(cx - bh, topY - bh, cx + bh, topY + bh,
                    petal, dark, Shader.TileMode.CLAMP));
            c.drawOval(cx - bh * 0.75f, topY - bh * 1.5f, cx + bh * 0.75f, topY + bh * 0.5f, p);
            p.setShader(null);
            p.setColor(stem);
            for (float a : new float[]{-0.8f, 0f, 0.8f}) {
                c.save();
                c.translate(cx, topY + bh * 0.4f);
                c.rotate((float) Math.toDegrees(a));
                c.drawOval(-size * 0.012f, -size * 0.03f, size * 0.012f, 0, p);
                c.restore();
            }
        } else {
            float k = 0.32f + (stage - 6) * 0.17f;
            float r = size * 0.22f * k;
            drawBloom(c, p, Species.shape(id), cx, topY - size * 0.02f, r,
                    petal, dark, core, Species.petals(id), Species.layers(id));
        }
    }

    private static void leaf(Canvas c, float x, float y, float len, int color, float rot) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.save();
        c.translate(x, y);
        c.rotate((float) Math.toDegrees(rot));
        Path path = new Path();
        path.moveTo(0, 0);
        path.quadTo(len, -len * 0.45f, len * 1.7f, 0);
        path.quadTo(len, len * 0.45f, 0, 0);
        c.drawPath(path, p);
        c.restore();
    }

    private static void petals(Canvas c, Paint p, int count, float R, float pw, float ph,
                               int light, int dark, float offset, boolean pointed) {
        for (int i = 0; i < count; i++) {
            c.save();
            c.rotate((360f * i) / count + offset);
            p.setShader(new LinearGradient(0, 0, R, 0, light, dark, Shader.TileMode.CLAMP));
            Path path = new Path();
            if (pointed) {
                path.moveTo(0, 0);
                path.quadTo(R * 0.55f, -pw, R, 0);
                path.quadTo(R * 0.55f, pw, 0, 0);
            } else {
                RectF r = new RectF(R * 0.1f, -ph, R * 1.05f, ph);
                path.addOval(r, Path.Direction.CW);
            }
            c.drawPath(path, p);
            c.restore();
        }
        p.setShader(null);
    }

    private static void drawBloom(Canvas c, Paint p, int shape, float cx, float cy, float R,
                                  int light, int dark, int core, int count, int layers) {
        c.save();
        c.translate(cx, cy);

        if (shape == 1 || shape == 11) {
            // пион / георгин: лепестки в несколько слоёв
            for (int l = 0; l < layers; l++) {
                petals(c, p, Math.max(5, count - l * 2), R * (1 - l * 0.22f),
                        R * 0.32f, R * 0.24f, light, l == 0 ? dark : light, l * 26f, false);
            }
        } else if (shape == 2) {
            // мак: 4 крупных лепестка
            for (int i = 0; i < 4; i++) {
                c.save();
                c.rotate(90f * i + 45f);
                p.setShader(new LinearGradient(0, 0, R * 1.1f, 0, light, dark, Shader.TileMode.CLAMP));
                RectF r = new RectF(-R * 0.25f, -R * 0.55f, R * 0.95f, R * 0.55f);
                c.drawOval(r, p);
                c.restore();
            }
            p.setShader(null);
        } else if (shape == 3) {
            // лаванда: колос
            for (int i = 0; i < 7; i++) {
                float y = -i * R * 0.42f;
                float rr = R * (0.34f - i * 0.03f);
                p.setColor(i % 2 == 0 ? light : dark);
                c.drawCircle(-rr * 0.8f, y, rr, p);
                c.drawCircle(rr * 0.8f, y, rr, p);
            }
        } else if (shape == 4) {
            // ирис: три вверх, три вниз
            petals(c, p, 3, R, R * 0.42f, R * 0.32f, light, dark, -90f, true);
            petals(c, p, 3, R * 0.75f, R * 0.36f, R * 0.28f, dark, light, 30f, false);
        } else if (shape == 5) {
            // тюльпан: чаша
            for (int i = -1; i <= 1; i++) {
                c.save();
                c.rotate(i * 22f);
                p.setShader(new LinearGradient(0, -R, 0, R, light, dark, Shader.TileMode.CLAMP));
                Path path = new Path();
                path.moveTo(0, R * 0.85f);
                path.quadTo(-R * 0.75f, -R * 0.2f, 0, -R);
                path.quadTo(R * 0.75f, -R * 0.2f, 0, R * 0.85f);
                c.drawPath(path, p);
                c.restore();
            }
            p.setShader(null);
        } else if (shape == 6) {
            // лотос: заострённые лепестки
            petals(c, p, 8, R, R * 0.34f, R * 0.34f, light, dark, 0f, true);
            petals(c, p, 6, R * 0.58f, R * 0.26f, R * 0.26f, light, dark, 22f, true);
        } else if (shape == 7) {
            // звезда
            for (int i = 0; i < 6; i++) {
                c.save();
                c.rotate(60f * i);
                p.setShader(new LinearGradient(0, 0, R * 1.1f, 0, light, dark, Shader.TileMode.CLAMP));
                Path path = new Path();
                path.moveTo(0, -R * 0.16f);
                path.lineTo(R * 0.7f, -R * 0.3f);
                path.lineTo(R * 1.15f, 0);
                path.lineTo(R * 0.7f, R * 0.3f);
                path.lineTo(0, R * 0.16f);
                path.close();
                c.drawPath(path, p);
                c.restore();
            }
            p.setShader(null);
        } else if (shape == 8) {
            // кристалл
            for (int i = 0; i < 6; i++) {
                c.save();
                c.rotate(60f * i);
                p.setShader(new LinearGradient(0, 0, R, 0, light, dark, Shader.TileMode.CLAMP));
                Path path = new Path();
                path.moveTo(0, -R * 0.12f);
                path.lineTo(R * 0.55f, -R * 0.28f);
                path.lineTo(R, 0);
                path.lineTo(R * 0.55f, R * 0.28f);
                path.lineTo(0, R * 0.12f);
                path.close();
                c.drawPath(path, p);
                c.restore();
            }
            p.setShader(null);
        } else if (shape == 9) {
            // колокольчик
            for (int i = 0; i < 4; i++) {
                c.save();
                c.rotate(35f * i - 50f);
                p.setShader(new LinearGradient(0, 0, 0, R, light, dark, Shader.TileMode.CLAMP));
                Path path = new Path();
                path.moveTo(0, 0);
                path.quadTo(-R * 0.55f, R * 0.4f, -R * 0.42f, R * 1.1f);
                path.quadTo(0, R * 1.35f, R * 0.42f, R * 1.1f);
                path.quadTo(R * 0.55f, R * 0.4f, 0, 0);
                c.drawPath(path, p);
                c.restore();
            }
            p.setShader(null);
        } else if (shape == 10) {
            // зонтик: много мелких цветков
            for (int i = 0; i < 9; i++) {
                double a = Math.PI * 2 * i / 9;
                float x = (float) Math.cos(a) * R * 0.8f;
                float y = (float) Math.sin(a) * R * 0.8f;
                p.setColor(i % 2 == 0 ? light : dark);
                c.drawCircle(x, y, R * 0.26f, p);
                p.setColor(core);
                c.drawCircle(x, y, R * 0.09f, p);
            }
        } else {
            // ромашка: два ряда овальных лепестков
            petals(c, p, count, R, R * 0.30f, R * 0.19f, light, dark, 0f, false);
            petals(c, p, Math.max(5, count - 4), R * 0.66f, R * 0.24f, R * 0.16f, light, light, 18f, false);
        }

        // серединка
        p.setShader(new RadialGradient(0, 0, R * 0.42f, core, dark, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, R * 0.28f, p);
        p.setShader(null);
        c.restore();
    }
}
