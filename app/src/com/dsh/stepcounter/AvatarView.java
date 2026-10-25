package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Аватар игрока: 4 причёски × 5 палитр, рисуется кодом.
 *
 * Персонаж живёт внутри круга радиуса rad*0.80, рамка занимает внешнее кольцо —
 * поэтому покупная рамка никогда не закрывает лицо. Все элементы считаются от
 * внутреннего радиуса, а не от dp, иначе на 64dp аватар разваливался.
 */
public class AvatarView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float d;

    private int style = 0;
    private int palette = 0;
    private String nick = "";
    private Bitmap photo;
    private int frame = 0;

    private static final int[] LEAF = {0xFF3FB57A, 0xFF57C98B, 0xFFE0A34A, 0xFFC77DD4, 0xFF5AA8D8};
    private static final int[] FACE = {0xFFF3C6A5, 0xFFF7D7BE, 0xFFEFC09B, 0xFFF2CBB4, 0xFFF5CFAF};
    private static final int[] HAIR = {0xFF3E8F63, 0xFF3E8F63, 0xFF8B6B3E, 0xFF8A5BB0, 0xFF3D6E9E};

    public AvatarView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
    }

    public void setStyle(int style, int palette) {
        this.style = Math.max(0, style % 4);
        this.palette = Math.max(0, palette % 5);
        invalidate();
    }

    public void setNick(String n) { nick = n == null ? "" : n; invalidate(); }

    public void setPhoto(Bitmap b) { photo = b; invalidate(); }

    public void setFrame(int id) { frame = id; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float rad = Math.min(w, h) / 2f;
        float in = rad * 0.74f;                      // рабочая зона персонажа

        int leaf = LEAF[palette];
        int face = FACE[palette];
        int hair = HAIR[palette];

        // фон значка — из темы, иначе в светлой теме под аватаром тёмно-зелёный круг
        p.setShader(new RadialGradient(cx - rad * 0.25f, cy - rad * 0.45f, rad * 1.5f,
                Theme.PANEL_TOP, Theme.PANEL_BOTTOM, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad * 0.84f, p);
        p.setShader(null);

        if (photo != null) {
            c.save();
            Path circle = new Path();
            circle.addCircle(cx, cy, in, Path.Direction.CW);
            c.clipPath(circle);
            float scale = Math.max((in * 2f) / photo.getWidth(), (in * 2f) / photo.getHeight());
            float dw = photo.getWidth() * scale, dh = photo.getHeight() * scale;
            r.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
            c.drawBitmap(photo, null, r, p);
            c.restore();
            Shop.drawFrame(c, cx, cy, rad, frame, d);
            return;
        }

        // всё содержимое обрезаем по кругу, чтобы не вылезало за значок
        c.save();
        Path clip = new Path();
        clip.addCircle(cx, cy, in * 1.01f, Path.Direction.CW);
        c.clipPath(clip);

        // тельце
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx - in * 0.2f, cy + in * 0.75f, in * 0.9f,
                lighten(leaf, 0.18f), leaf, Shader.TileMode.CLAMP));
        r.set(cx - in * 0.46f, cy + in * 0.30f, cx + in * 0.46f, cy + in * 1.20f);
        c.drawRoundRect(r, in * 0.34f, in * 0.34f, p);
        p.setShader(null);

        // ушки-листики
        p.setColor(leaf);
        c.save();
        c.translate(cx - in * 0.44f, cy - in * 0.10f);
        c.rotate(-32f);
        c.drawOval(new RectF(-in * 0.20f, -in * 0.10f, in * 0.10f, in * 0.10f), p);
        c.restore();
        c.save();
        c.translate(cx + in * 0.44f, cy - in * 0.10f);
        c.rotate(32f);
        c.drawOval(new RectF(-in * 0.10f, -in * 0.10f, in * 0.20f, in * 0.10f), p);
        c.restore();

        // голова
        float headR = in * 0.54f;
        float headY = cy - in * 0.12f;
        p.setShader(new RadialGradient(cx - headR * 0.35f, headY - headR * 0.45f, headR * 1.6f,
                lighten(face, 0.12f), face, Shader.TileMode.CLAMP));
        c.drawCircle(cx, headY, headR, p);
        p.setShader(null);

        // румянец
        p.setColor(0x55FF7FA8);
        c.drawCircle(cx - headR * 0.60f, headY + headR * 0.30f, headR * 0.20f, p);
        c.drawCircle(cx + headR * 0.60f, headY + headR * 0.30f, headR * 0.20f, p);

        // глаза: белок, зрачок, блик
        float eyeDx = headR * 0.42f, eyeY = headY - headR * 0.08f;
        p.setColor(0xFFFFFFFF);
        c.drawOval(new RectF(cx - eyeDx - headR * 0.20f, eyeY - headR * 0.24f,
                cx - eyeDx + headR * 0.20f, eyeY + headR * 0.24f), p);
        c.drawOval(new RectF(cx + eyeDx - headR * 0.20f, eyeY - headR * 0.24f,
                cx + eyeDx + headR * 0.20f, eyeY + headR * 0.24f), p);
        p.setColor(0xFF243B33);
        c.drawCircle(cx - eyeDx, eyeY, headR * 0.115f, p);
        c.drawCircle(cx + eyeDx, eyeY, headR * 0.115f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - eyeDx - headR * 0.045f, eyeY - headR * 0.05f, headR * 0.042f, p);
        c.drawCircle(cx + eyeDx - headR * 0.045f, eyeY - headR * 0.05f, headR * 0.042f, p);

        // улыбка
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(headR * 0.075f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF6B4A3A);
        r.set(cx - headR * 0.26f, headY + headR * 0.16f,
                cx + headR * 0.26f, headY + headR * 0.56f);
        c.drawArc(r, 18f, 144f, false, p);
        p.setStyle(Paint.Style.FILL);

        // причёска
        p.setColor(hair);
        if (style == 0) {
            // два листика-антенны над головой (голова кончается на cy-0.66*in)
            c.save();
            c.translate(cx - in * 0.12f, cy - in * 0.74f);
            c.rotate(-28f);
            c.drawOval(new RectF(-in * 0.24f, -in * 0.11f, in * 0.14f, in * 0.11f), p);
            c.restore();
            c.save();
            c.translate(cx + in * 0.14f, cy - in * 0.74f);
            c.rotate(30f);
            c.drawOval(new RectF(-in * 0.14f, -in * 0.11f, in * 0.24f, in * 0.11f), p);
            c.restore();
        } else if (style == 1) {
            // венок из трёх цветков — над головой
            int[] petals = {0xFFF7C6DA, 0xFFFFE9A8, 0xFFC4B4F5};
            for (int i = 0; i < 3; i++) {
                float fx = cx + (i - 1) * in * 0.36f;
                float fy = cy - in * 0.78f - (i == 1 ? in * 0.09f : 0);
                p.setColor(petals[i]);
                for (int k = 0; k < 5; k++) {
                    double a = Math.PI * 2 * k / 5 - Math.PI / 2;
                    c.drawCircle(fx + (float) Math.cos(a) * in * 0.10f,
                            fy + (float) Math.sin(a) * in * 0.10f, in * 0.075f, p);
                }
                p.setColor(0xFFFFF0B8);
                c.drawCircle(fx, fy, in * 0.05f, p);
                p.setColor(hair);
            }
        } else if (style == 2) {
            // очки
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(headR * 0.06f);
            p.setColor(0xFF3A4A38);
            c.drawCircle(cx - eyeDx, eyeY, headR * 0.30f, p);
            c.drawCircle(cx + eyeDx, eyeY, headR * 0.30f, p);
            c.drawLine(cx - eyeDx + headR * 0.30f, eyeY, cx + eyeDx - headR * 0.30f, eyeY, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            // берет
            p.setColor(hair);
            r.set(cx - in * 0.46f, cy - in * 0.84f, cx + in * 0.46f, cy - in * 0.40f);
            c.drawOval(r, p);
            p.setColor(0xFFFFE9A8);
            c.drawCircle(cx + in * 0.06f, cy - in * 0.88f, in * 0.07f, p);
        }

        c.restore();

        // рамка — снаружи от содержимого
        Shop.drawFrame(c, cx, cy, rad, frame, d);
    }

    private static int lighten(int color, float k) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] + k);
        hsv[1] = Math.max(0f, hsv[1] - k * 0.3f);
        return Color.HSVToColor(hsv);
    }
}
