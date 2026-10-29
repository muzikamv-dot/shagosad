package com.dsh.stepcounter;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Коллекционная открытка: рисуем и сохраняем в галерею. */
public final class Postcard {

    private static final int W = 1080, H = 1350;
    /** Карточка рисуется в 2× и сохраняется 2160×2700: текст и лепестки остаются резкими. */
    private static final int SCALE = 2;

    private Postcard() { }

    public static Bitmap render(Context ctx, GameState.Card card) {
        // праздничная открытка идёт по тому же шаблону, что коллекционные
        boolean ev = card != null && card.species < 0;
        Bitmap out = Bitmap.createBitmap(W * SCALE, H * SCALE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.scale(SCALE, SCALE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float hue = KawaiiPlant.hueFor(card.species);
        int gold = Color.HSVToColor(new float[]{45f, 0.55f, 1f});

        int top = Color.HSVToColor(new float[]{hue, 0.42f, 0.26f});
        int bottom = Color.HSVToColor(new float[]{hue, 0.55f, 0.07f});

        p.setShader(new LinearGradient(0, 0, W * 0.4f, H, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);

        // бумажная фактура: редкие светлые крупинки, почти невидимые по отдельности
        java.util.Random paper = new java.util.Random(card.species * 31L + card.seed);
        for (int i = 0; i < 2600; i++) {
            float x = paper.nextFloat() * W, y = paper.nextFloat() * H;
            float r = 0.6f + paper.nextFloat() * 1.6f;
            p.setColor(0x0EFFFFFF);
            c.drawCircle(x, y, r, p);
        }

        // свет за цветком
        p.setShader(new RadialGradient(W / 2f, 560, 470,
                Color.HSVToColor(new float[]{hue, 0.28f, 0.85f}), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        p.setAlpha(90);
        c.drawCircle(W / 2f, 560, 470, p);
        p.setAlpha(255);
        p.setShader(null);

        // рамка двойная
        Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2.5f);
        frame.setColor(Color.HSVToColor(new float[]{hue, 0.22f, 0.92f}));
        c.drawRoundRect(new RectF(40, 40, W - 40, H - 40), 30, 30, frame);
        frame.setStrokeWidth(1f);
        frame.setColor(Color.HSVToColor(new float[]{hue, 0.2f, 0.8f}));
        c.drawRoundRect(new RectF(54, 54, W - 54, H - 54), 24, 24, frame);

        // внутренняя тень по краю рамки: даёт «утопленность» карточки
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(16f);
        inner.setColor(0x55000000);
        inner.setMaskFilter(new android.graphics.BlurMaskFilter(12f,
                android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new RectF(58, 58, W - 58, H - 58), 24, 24, inner);

        // уголки
        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setStrokeWidth(4f);
        tick.setColor(Color.HSVToColor(new float[]{hue, 0.3f, 0.95f}));
        float m = 74, len = 34;
        float[][] corners = {{m, m, 1, 1}, {W - m, m, -1, 1}, {m, H - m, 1, -1}, {W - m, H - m, -1, -1}};
        for (float[] co : corners) {
            c.drawLine(co[0], co[1], co[0] + len * co[2], co[1], tick);
            c.drawLine(co[0], co[1], co[0], co[1] + len * co[3], tick);
        }

        // шапка
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.HSVToColor(new float[]{hue, 0.16f, 0.95f}));
        t.setTextSize(26);
        t.setLetterSpacing(0.35f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextAlign(Paint.Align.LEFT);
        c.drawText(ev ? "ПРАЗДНИЧНАЯ КОЛЛЕКЦИЯ" : "ГЕРБАРИЙ", 96, 108, t);

        // номер — золотом, как на коллекционной карточке
        t.setTextAlign(Paint.Align.RIGHT);
        t.setTextSize(26);
        t.setLetterSpacing(0.18f);
        t.setColor(gold);
        c.drawText(ev ? Event.WHEN.toUpperCase() : "№ " + card.seed, W - 96, 108, t);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStrokeWidth(1f);
        line.setColor(Color.HSVToColor(new float[]{hue, 0.18f, 0.7f}));
        c.drawLine(96, 128, W - 96, 128, line);

        // золотая линия под шапкой
        Paint goldLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldLine.setStrokeWidth(2f);
        goldLine.setColor((gold & 0x00FFFFFF) | 0x66000000);
        c.drawLine(W - 96 - 88, 120, W - 96, 120, goldLine);

        // цветок — та же параметрическая модель, что и в саду,
        // иначе сад и гербарий показывали бы разные растения
        if (ev) {
            // лента-триколор над цветком
            try {
                Bitmap ribbon = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.event_ribbon);
                if (ribbon != null) {
                    float rw = 600f;
                    float rh = rw * ribbon.getHeight() / ribbon.getWidth();
                    float lx = W / 2f - rw / 2f;
                    c.drawBitmap(ribbon, null, new RectF(lx, 236f, lx + rw, 236f + rh), p);
                }
            } catch (Exception ignored) { }
            // сам праздничный цветок
            try {
                Bitmap flower = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.event_stage_20);
                if (flower != null) {
                    float fh = 470f;
                    float fw = fh * flower.getWidth() / flower.getHeight();
                    float lx = W / 2f - fw / 2f;
                    c.drawBitmap(flower, null, new RectF(lx, 900f - fh, lx + fw, 900f), p);
                }
            } catch (Exception ignored) { }
        } else {
            KawaiiPlant.draw(c, card.species, 10, W / 2f, 900, 700, 0f, 0.35f);
        }

        // орнамент
        float oy = 940;
        line.setStrokeWidth(1.2f);
        line.setColor(Color.HSVToColor(new float[]{hue, 0.22f, 0.85f}));
        c.drawLine(W / 2f - 190, oy, W / 2f - 26, oy, line);
        c.drawLine(W / 2f + 26, oy, W / 2f + 190, oy, line);
        Path diamond = new Path();
        diamond.moveTo(W / 2f, oy - 9);
        diamond.lineTo(W / 2f + 9, oy);
        diamond.lineTo(W / 2f, oy + 9);
        diamond.lineTo(W / 2f - 9, oy);
        diamond.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.HSVToColor(new float[]{hue, 0.3f, 0.95f}));
        c.drawPath(diamond, fill);

        // имя: подбираем кегль так, чтобы влезло в рамку
        t.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        t.setTextAlign(Paint.Align.CENTER);
        t.setLetterSpacing(0f);
        t.setColor(Color.parseColor("#FBF6E8"));

        float nameSize = 52;
        t.setTextSize(nameSize);
        while (t.measureText(card.name) > 840 && nameSize > 30) {
            nameSize -= 2;
            t.setTextSize(nameSize);
        }
        List<String> nameLines = wrap(card.name, 840, nameSize, "serif", 0f);

        float y = 1016;
        for (String ln : nameLines) {
            c.drawText(ln, W / 2f, y, t);
            y += nameSize + 12;
        }

        // дата
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setTextSize(24);
        t.setLetterSpacing(0.2f);
        t.setColor(Color.HSVToColor(new float[]{hue, 0.14f, 0.88f}));
        c.drawText(humanDate(card.date).toUpperCase(), W / 2f, y + 16, t);

        // пожелание
        t.setLetterSpacing(0f);
        t.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        t.setTextSize(31);
        t.setColor(Color.parseColor("#DCE8E1"));
        float wy = y + 86;
        for (String ln : wrap(card.wish, 800, 31, "serif", 0f)) {
            c.drawText(ln, W / 2f, wy, t);
            wy += 42;
        }

        // подпись
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setTextSize(20);
        t.setLetterSpacing(0.15f);
        t.setColor(Color.HSVToColor(new float[]{hue, 0.12f, 0.68f}));
        c.drawText(ev ? "ШАГОСАД · ПРАЗДНИЧНЫЙ ИВЕНТ" : "ШАГОСАД · " + card.seed + " ИЗ 365", W / 2f, H - 88, t);

        return out;
    }

    private static List<String> wrap(String text, float maxWidth, float size, String family,
                                     float spacing) {
        List<String> out = new ArrayList<>();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setLetterSpacing(spacing);
        p.setTypeface(Typeface.create(family, Typeface.NORMAL));
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (p.measureText(test) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static String humanDate(String iso) {
        try {
            String[] p = iso.split("-");
            String[] m = {"января", "февраля", "марта", "апреля", "мая", "июня",
                    "июля", "августа", "сентября", "октября", "ноября", "декабря"};
            return Integer.parseInt(p[2]) + " " + m[Integer.parseInt(p[1]) - 1] + " " + p[0];
        } catch (Exception e) {
            return iso;
        }
    }

    public static Bitmap flowerTinted(Context ctx, int species, int seed) {
        // миниатюра гербария — тем же рендером, что и в саду
        int s = 256;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        KawaiiPlant.draw(c, species, 10, s / 2f, s * 0.94f, s * 0.86f, 0f, 0f);
        return b;
    }

    public static File save(Context ctx, Bitmap bmp, String name) {
        String safe = name.replaceAll("[^\\p{L}\\p{Nd} _-]", "").trim();
        if (safe.length() > 60) safe = safe.substring(0, 60);
        if (safe.isEmpty()) safe = "Цветок";

        // Android 10+: через MediaStore — без разрешений и сразу в галерее
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, safe + ".png");
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Сад");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = ctx.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return null;

                OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                bmp.compress(Bitmap.CompressFormat.PNG, 96, os);
                if (os != null) os.close();

                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, done, null, null);

                return new File(uri.toString());
            } catch (Exception e) {
                return null;
            }
        }

        // Android 8–9: обычный файл + сканер
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Сад");
        if (!dir.exists()) dir.mkdirs();

        File f = new File(dir, safe + ".png");
        int n = 1;
        while (f.exists()) {
            f = new File(dir, safe + " (" + (++n) + ").png");
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 96, out);
            out.close();
            MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()},
                    new String[]{"image/png"}, null);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** Сохранить готовую открытку в галерею телефона. true — получилось. */
    public static boolean saveToGallery(Context ctx, Bitmap bmp, String name) {
        if (bmp == null) return false;
        return save(ctx, bmp, name) != null;

    }
}
