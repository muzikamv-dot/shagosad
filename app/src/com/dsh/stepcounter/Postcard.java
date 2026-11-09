package com.dsh.stepcounter;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
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
        boolean ev = card != null && card.species == -1;
        boolean magic = card != null && Rare.isRare(card.species);
        Bitmap out = Bitmap.createBitmap(W * SCALE, H * SCALE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.scale(SCALE, SCALE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float hue = magic ? 42f : KawaiiPlant.hueFor(card.species);
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
        c.drawText(ev ? "ПРАЗДНИЧНАЯ КОЛЛЕКЦИЯ" : magic ? "НЕОБЫЧНЫЙ ЦВЕТОК" : "ГЕРБАРИЙ", 96, 108, t);

        // номер — золотом, как на коллекционной карточке
        t.setTextAlign(Paint.Align.RIGHT);
        t.setTextSize(26);
        t.setLetterSpacing(0.18f);
        t.setColor(gold);
        c.drawText(ev ? Event.WHEN.toUpperCase() : magic ? "ИЗ МАГАЗИНА" : "№ " + card.seed, W - 96, 108, t);

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
        } else if (magic) {
            // волшебная ромашка: готовая картинка раскрытого цветка
            Bitmap flower = Rare.art(ctx, card.species, Rare.STAGES);
            if (flower != null) {
                float fh = 520f;
                float fw = fh * flower.getWidth() / flower.getHeight();
                float lx = W / 2f - fw / 2f;
                c.drawBitmap(flower, null, new RectF(lx, 900f - fh, lx + fw, 900f), p);
            }
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
        c.drawText(ev ? "ШАГОСАД · ПРАЗДНИЧНЫЙ ИВЕНТ"
                        : magic ? "ШАГОСАД · ВОЛШЕБНЫЙ ЦВЕТОК"
                        : "ШАГОСАД · " + card.seed + " ЦВЕТОК",
                W / 2f, H - 88, t);

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

    // ================= новогодняя открытка «Ёлка-2026» =================

    /** Рисует поздравительную открытку: ёлка игрока, «С Новым годом» и пожелание. */
    public static Bitmap renderTree(Context ctx, String placed, int opened) {
        final int W = 1080, H = 1920;
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        Bitmap bg = load(ctx, "ny_bg");
        Bitmap drift = load(ctx, "ny_drift");
        Bitmap tree = load(ctx, "ny_tree");
        Bitmap garland = load(ctx, "ny_garland");

        if (bg != null) {
            float k = Math.max(W / (float) bg.getWidth(), H / (float) bg.getHeight());
            float dw = bg.getWidth() * k, dh = bg.getHeight() * k;
            c.drawBitmap(bg, null, new RectF((W - dw) / 2f, (H - dh) / 2f,
                    (W + dw) / 2f, (H + dh) / 2f), p);
        } else {
            c.drawColor(0xFF0B1020);
        }

        Paint top = new Paint();
        top.setShader(new LinearGradient(0, 0, 0, H * 0.25f,
                new int[]{0xB3080D1C, 0x00080D1C}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H * 0.25f, top);

        // сугроб прижат к самому низу
        RectF dr = null;
        if (drift != null) {
            float dw = W * 1.02f;
            float dh = dw * drift.getHeight() / (float) drift.getWidth();
            float bottom = H - H * 0.004f;
            dr = new RectF((W - dw) / 2f, bottom - dh, (W + dw) / 2f, bottom);
        }

        // ёлка высокая, ствол уходит в сугроб
        RectF tr = null;
        if (tree != null) {
            float th = H * 0.70f;
            float tw = th * tree.getWidth() / (float) tree.getHeight();
            float bottom = (dr != null ? dr.bottom - dr.height() * 0.45f : H - H * 0.08f);
            float treeTop = bottom - th;
            tr = new RectF((W - tw) / 2f, treeTop, (W + tw) / 2f, bottom);
        }

        if (dr != null) c.drawBitmap(drift, null, dr, p);

        if (tr != null) {
            Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setColor(0x66000000);
            float sw = tr.width() * 0.24f, sh = H * 0.008f;
            c.drawOval(new RectF(tr.centerX() - sw, tr.bottom - sh / 2f,
                    tr.centerX() + sw, tr.bottom + sh / 2f), shadow);
            c.drawBitmap(tree, null, tr, p);
        }

        if (garland != null && tr != null) {
            float[] fracs = {0.33f, 0.55f, 0.75f};
            float[] wfr = {0.52f, 0.70f, 0.88f};
            for (int i = 0; i < fracs.length; i++) {
                float y = tr.top + tr.height() * fracs[i];
                float segW = tr.width() * wfr[i];
                float segH = garland.getHeight() * segW / (float) garland.getWidth();
                Matrix m = new Matrix();
                m.postRotate(i % 2 == 0 ? -3.5f : 3.5f, tr.centerX(), y);
                c.save();
                c.concat(m);
                c.drawBitmap(garland, null, new RectF(tr.centerX() - segW / 2f, y - segH / 2f,
                        tr.centerX() + segW / 2f, y + segH / 2f), p);
                c.restore();
            }
        }

        // украшения — в тех же местах, что на экране: считаем их положение
        // относительно ёлки по геометрии игрового экрана и переносим на карту
        if (placed != null && tr != null && tree != null && drift != null) {
            float wS = ctx.getResources().getDisplayMetrics().widthPixels;
            float hS = ctx.getResources().getDisplayMetrics().heightPixels;
            float dwS = wS * 1.12f;
            float dhS = dwS * drift.getHeight() / (float) drift.getWidth();
            float bottomDriftS = hS - hS * 0.005f;
            float thS = hS * 0.66f;
            float twS = thS * tree.getWidth() / (float) tree.getHeight();
            float bottomTreeS = bottomDriftS - dhS * 0.55f;
            float topTreeS = bottomTreeS - thS;

            float base = W * 0.072f;
            for (String part : placed.split(";")) {
                if (part.trim().isEmpty()) continue;
                String[] kv = part.split(":");
                if (kv.length != 2) continue;
                String[] xy = kv[1].split(",");
                if (xy.length != 2) continue;
                int id;
                float nx, ny;
                try {
                    id = Integer.parseInt(kv[0]);
                    nx = Float.parseFloat(xy[0]);
                    ny = Float.parseFloat(xy[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                Bitmap b = load(ctx, String.format("ny_orn_%02d", id + 1));
                if (b == null) continue;
                boolean topper = id == NewYear.TOP_ORNAMENT;
                float size = topper ? base * 1.9f : base;
                float k = size / Math.max(b.getWidth(), b.getHeight());
                float bw = b.getWidth() * k;
                float bh = b.getHeight() * k;

                // положение внутри ёлки на экране (0 — верх, 1 — низ)
                float relY = (ny * hS - topTreeS) / Math.max(1f, thS);
                float relX = ((nx * wS) - (wS / 2f)) / Math.max(1f, twS) + 0.5f;
                float cx0 = tr.left + relX * tr.width();
                float cy0 = tr.top + relY * tr.height();
                c.drawBitmap(b, null, new RectF(cx0 - bw / 2f, cy0 - bh / 2f,
                        cx0 + bw / 2f, cy0 + bh / 2f), p);
            }
        }

        // ---- заголовок ----
        int nyYear = NewYear.seasonYear() + 1;
        Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        big.setColor(0xFFFFFFFF);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        big.setShadowLayer(18f, 0f, 6f, 0xCC000000);
        big.setTextSize(74);
        c.drawText("С НОВЫМ", W / 2f, 128f, big);

        Paint year = new Paint(Paint.ANTI_ALIAS_FLAG);
        year.setColor(0xFFFFD772);
        year.setTextAlign(Paint.Align.CENTER);
        year.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        year.setShadowLayer(20f, 0f, 6f, 0xCC000000);
        year.setTextSize(100);
        c.drawText(String.valueOf(nyYear), W / 2f, 228f, year);

        Paint yearTail = new Paint(Paint.ANTI_ALIAS_FLAG);
        yearTail.setColor(0xFFFFFFFF);
        yearTail.setTextAlign(Paint.Align.CENTER);
        yearTail.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        yearTail.setLetterSpacing(0.10f);
        yearTail.setShadowLayer(18f, 0f, 5f, 0xCC000000);
        yearTail.setTextSize(56);
        c.drawText("ГОДОМ!", W / 2f, 292f, yearTail);

        // ---- поздравление: лёгкая плашка, сквозь неё видно сугроб и ствол ----
        float plateTop = H - 250f;
        float plateBottom = H - 34f;
        Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        plate.setColor(0x5A0A1424);
        RectF plateR = new RectF(W * 0.05f, plateTop, W * 0.95f, plateBottom);
        c.drawRoundRect(plateR, 38f, 38f, plate);
        Paint plateStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        plateStroke.setStyle(Paint.Style.STROKE);
        plateStroke.setStrokeWidth(3f);
        plateStroke.setColor(0x88FFD772);
        c.drawRoundRect(plateR, 38f, 38f, plateStroke);

        Paint wish = new Paint(Paint.ANTI_ALIAS_FLAG);
        wish.setColor(0xFFFFFFFF);
        wish.setTextAlign(Paint.Align.CENTER);
        wish.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wish.setShadowLayer(14f, 0f, 3f, 0xFF000000);
        wish.setTextSize(40);
        float baseY = plateTop + 72f;
        c.drawText("Пусть год принесёт больше прогулок,", W / 2f, baseY, wish);
        c.drawText("тёплых дней и цветущих садов.", W / 2f, baseY + 52f, wish);

        Paint wish2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        wish2.setColor(0xFFFFD772);
        wish2.setTextAlign(Paint.Align.CENTER);
        wish2.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wish2.setShadowLayer(14f, 0f, 3f, 0xFF000000);
        wish2.setTextSize(38);
        c.drawText("Спасибо, что гуляли вместе с нами!", W / 2f, baseY + 112f, wish2);

        return out;
    }

    /** Маленькое превью наряженной ёлки для коллекции: фон, ёлка, гирлянда, игрушки, звезда. */
    public static Bitmap renderTreeThumb(Context ctx, String placed) {
        final int W = 240, H = 300;
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // ночное небо
        Paint sky = new Paint();
        sky.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0xFF16264A, 0xFF0A1428}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, sky);

        Bitmap tree = load(ctx, "ny_tree");
        Bitmap garland = load(ctx, "ny_garland");
        Bitmap drift = load(ctx, "ny_drift");

        // сугроб
        if (drift != null) {
            float dw = W * 1.04f;
            float dh = dw * drift.getHeight() / (float) drift.getWidth();
            c.drawBitmap(drift, null, new RectF((W - dw) / 2f, H - dh - 4f, (W + dw) / 2f, H - 4f), p);
        }

        RectF tr = null;
        if (tree != null) {
            float th = H * 0.80f;
            float tw = th * tree.getWidth() / (float) tree.getHeight();
            tr = new RectF((W - tw) / 2f, H * 0.86f - th, (W + tw) / 2f, H * 0.86f);
            c.drawBitmap(tree, null, tr, p);
        }

        // гирлянды
        if (garland != null && tr != null) {
            float[] fracs = {0.33f, 0.55f, 0.75f};
            float[] wfr = {0.52f, 0.70f, 0.88f};
            for (int i = 0; i < fracs.length; i++) {
                float y = tr.top + tr.height() * fracs[i];
                float segW = tr.width() * wfr[i];
                float segH = garland.getHeight() * segW / (float) garland.getWidth();
                Matrix m = new Matrix();
                m.postRotate(i % 2 == 0 ? -3.5f : 3.5f, tr.centerX(), y);
                c.save();
                c.concat(m);
                c.drawBitmap(garland, null, new RectF(tr.centerX() - segW / 2f, y - segH / 2f,
                        tr.centerX() + segW / 2f, y + segH / 2f), p);
                c.restore();
            }
        }

        // игрушки: макушка всегда на верхушке, остальные по своим местам
        if (placed != null && tr != null) {
            float base = W * 0.085f;
            for (String part : placed.split(";")) {
                if (part.trim().isEmpty()) continue;
                String[] kv = part.split(":");
                if (kv.length != 2) continue;
                String[] xy = kv[1].split(",");
                if (xy.length != 2) continue;
                int id;
                float nx, ny;
                try {
                    id = Integer.parseInt(kv[0]);
                    nx = Float.parseFloat(xy[0]);
                    ny = Float.parseFloat(xy[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                Bitmap b = load(ctx, String.format("ny_orn_%02d", id + 1));
                if (b == null) continue;
                boolean topper = id == NewYear.TOP_ORNAMENT;
                float size = topper ? base * 1.9f : base;
                float k = size / Math.max(b.getWidth(), b.getHeight());
                float bw = b.getWidth() * k;
                float bh = b.getHeight() * k;
                float cx0 = topper ? tr.centerX()
                        : lerp(tr.left + tr.width() * 0.16f, tr.right - tr.width() * 0.16f, nx);
                float cy0 = topper ? tr.top + bh * 0.2f
                        : lerp(tr.top + tr.height() * 0.16f, tr.bottom - tr.height() * 0.02f,
                                Math.max(0f, Math.min(1f, ny)));
                c.drawBitmap(b, null, new RectF(cx0 - bw / 2f, cy0 - bh / 2f,
                        cx0 + bw / 2f, cy0 + bh / 2f), p);
            }
        }
        return out;
    }
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }

    private static Bitmap load(Context ctx, String name) {
        int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(ctx.getResources(), id);
    }
}
