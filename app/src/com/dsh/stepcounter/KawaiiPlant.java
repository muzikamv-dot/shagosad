package com.dsh.stepcounter;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Параметрический цветок: стебель-кривая, листья на узлах и 24 семейства соцветий —
 * ромашка, пион, мак, лаванда, ирис, тюльпан, лотос, звезда, кристалл, колокольчик,
 * зонтик, георгин, маргаритка, камелия, гибискус, протея, ранункулюс, орхидея,
 * анемон, лютик, василёк, чертополох, подснежник, астра.
 *
 * Каждое семейство рисуется своим способом, а форма внутри семейства меняется
 * признаками вида (ширина и волнистость лепестка, наклон, кончик, размер головки,
 * схема цвета). Свет падает со стороны настоящего солнца: подсвеченные лепестки
 * получают блик, остальные — тень у основания.
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

    // ---- кончики лепестков ----
    private static final int TIP_ROUND = 0, TIP_POINT = 1, TIP_FRINGE = 2, TIP_NOTCH = 3;

    private static int hsv(float h, float s, float v) {
        float hh = ((h % 360f) + 360f) % 360f;
        return Color.HSVToColor(new float[]{hh,
                Math.min(1f, Math.max(0f, s)), Math.min(1f, Math.max(0f, v))});
    }

    /** Тот же цвет, но темнее (v · k) и чуть насыщеннее. */
    private static int dim(int col, float k) {
        float[] c = new float[3];
        Color.colorToHSV(col, c);
        return hsv(c[0], Math.min(1f, c[1] * (1f + (1f - k) * 0.4f)), c[2] * k);
    }

    private static float hueOf(int col) {
        float[] c = new float[3];
        Color.colorToHSV(col, c);
        return c[0];
    }

    /** Палитра вида: пять схем цвета вместо одного рецепта. */
    private static final class Pal {
        int base, tip, base2, tip2, rim, core, stamen, throat;
    }

    private static Pal palette(int species) {
        float h = cuteHue(species);
        float shift = Species.accentShift(species);
        int scheme = Species.colorScheme(species);
        Pal p = new Pal();

        if (scheme == 1) {
            // светлая кайма того же тона: не «ёлочная игрушка», а край лепестка
            p.base = hsv(h, 0.26f, 1.00f);   p.tip = hsv(h, 0.86f, 0.96f);
            p.rim = hsv(h, 0.06f, 1.00f);
            p.base2 = hsv(h, 0.32f, 1.00f);
            p.tip2 = hsv(h, 0.80f, 0.94f);
        } else if (scheme == 2) {
            // ряды близких тонов
            p.base = hsv(h, 0.26f, 1.00f);   p.tip = hsv(h, 0.88f, 0.98f);
            p.base2 = hsv(h + shift, 0.30f, 1.00f);
            p.tip2 = hsv(h + shift, 0.86f, 0.98f);
            p.rim = p.tip;
        } else if (scheme == 3) {
            // контрастная серединка (лепестки при этом одного тона)
            p.base = hsv(h, 0.24f, 1.00f);   p.tip = hsv(h, 0.90f, 0.99f);
            p.base2 = hsv(h + 6f, 0.28f, 1.00f);
            p.tip2 = hsv(h + 6f, 0.88f, 0.98f);
            p.rim = p.tip;
        } else if (scheme == 4) {
            // насыщенный лепесток и светлый кончик, но не темнее 0.90 — иначе тонет в фоне
            p.base = hsv(h, 0.84f, 0.94f);   p.tip = hsv(h, 0.12f, 1.00f);
            p.base2 = hsv(h + shift, 0.84f, 0.92f);
            p.tip2 = hsv(h + shift, 0.16f, 1.00f);
            p.rim = p.tip;
        } else {
            // одноцвет: светлое горло, насыщенный кончик
            p.base = hsv(h, 0.30f, 1.00f);   p.tip = hsv(h, 0.86f, 1.00f);
            p.base2 = hsv(h + 4f, 0.34f, 1.00f);
            p.tip2 = hsv(h + 4f, 0.84f, 0.98f);
            p.rim = p.tip;
        }
        p.throat = scheme == 4 ? hsv(h, 0.18f, 1.00f) : hsv(h, 0.08f, 1.00f);
        // серединка: золотая у всех, контрастная — только у своей схемы
        p.core = scheme == 3 ? hsv(h + 180f, 0.42f, 1.00f) : hsv(44f, 0.52f, 1.00f);
        p.stamen = hsv(36f, 0.78f, 0.86f);
        return p;
    }

    /** Признаки вида для головки. */
    private static final class Tr {
        int tip;
        float wave, tilt, width, head, litAz, lightK;
    }

    private static Tr traits(int species, float lightX) {
        Tr t = new Tr();
        t.tip = Species.petalTip(species);
        t.wave = Species.petalWave(species);
        t.tilt = Species.petalTilt(species);
        t.width = Species.petalWidth(species);
        t.head = Species.headScale(species);
        t.litAz = -90f + 90f * lightX;
        t.lightK = 0.55f + 0.45f * Math.abs(lightX);
        return t;
    }

    // ---- геометрия кривой ----

    private static float bx(float t, float p0, float p1, float p2, float p3) {
        float u = 1f - t;
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3;
    }

    /** Рисует растение. time — секунды (для мелкого дрожания), wind — [-1..1]. */
    public static void draw(Canvas c, int species, int stage, float cx, float baseY,
                            float size, float time, float wind) {
        draw(c, species, stage, cx, baseY, size, time, wind, 0f);
    }

    /** То же, но со светом: lightX = −1 слева, 0 сверху, +1 справа. */
    public static void draw(Canvas c, int species, int stage, float cx, float baseY,
                            float size, float time, float wind, float lightX) {
        if (size <= 0) return;
        Random rnd = new Random(species * 977L + 13L);

        float grow = 0.26f + Math.min(10, Math.max(0, stage)) * 0.074f;   // 0.26 … 1.0
        // высота своя у каждого вида, но головка обязана влезть в кадр:
        // иначе крупный цветок срезается верхней кромкой ячейки
        float headR = size * headFor(Species.family(species), traits(species, 0f));
        float maxH = Math.max(size * 0.34f, size - headR * 1.32f);
        float h = Math.min(size * Species.stemHeight(species) * Math.min(1f, grow), maxH);

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

        // ---- стебель: кривая Безье + ветер + собственный наклон вида ----
        float lean = wind * size * 0.045f + Species.stemLean(species) * size * 0.10f
                + (rnd.nextFloat() - 0.5f) * size * 0.02f;
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
        float spread = Species.leafSpread(species);
        for (int i = 0; i < leaves; i++) {
            float t = 0.16f + 0.62f * (i + 0.6f) / Math.max(1, leaves);
            float lx = bx(t, x0, x1, x2, x3);
            float ly = bx(t, y0, y1, y2, y3);
            boolean right = (i % 2 == 0);
            float len = size * (0.165f - t * 0.05f) * (0.85f + rnd.nextFloat() * 0.3f) * spread;
            float baseAngle = 34f + rnd.nextFloat() * 22f;
            // справа лист отходит вверх-вправо, слева — зеркально вверх-влево
            float ang = (right ? -baseAngle : -(180f - baseAngle));
            float sway = wind * (7f + 5f * (1f - t)) * (right ? 1f : -1f)
                    + (float) Math.sin(time * 1.9f + i * 1.3f) * 2.6f;
            leaf(c, q, lx, ly, len, ang + sway, leafForm, leafA, leafB, right);
        }

        // ---- головка ----
        if (stage <= 4) {
            bud(c, p, species, x3, y3, size, stage, rnd, wind, time);
        } else {
            float open = Math.min(1f, (stage - 4) / 6f);
            head(c, p, species, x3, y3, size, open, rnd, wind, time, lightX);
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
        Pal pal = palette(species);

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
                    pal.tip, i == 0 ? pal.base : pal.tip, Shader.TileMode.CLAMP));
            c.drawPath(pet, p);
            p.setShader(null);
            c.restore();
        }
    }

    // ---- головка: разводка по семействам ----

    /** Размер головки зависит от семейства: у ромашки она мелкая, у пиона — крупная. */
    private static float headFor(int fam, Tr tr) {
        float k;
        switch (fam) {
            case 3:  case 10: k = 0.70f; break;                       // колос, зонтик
            case 0:  case 12: case 20: case 23: k = 0.84f; break;     // ромашка, маргаритка, василёк, астра
            case 2:  case 19: k = 1.14f; break;                       // мак, лютик
            case 1:  case 16: k = 1.16f; break;                       // пион, ранункулюс
            case 13: case 14: k = 1.04f; break;                       // камелия, гибискус
            case 17: k = 1.06f; break;                                // орхидея
            default: k = 1f;
        }
        return tr.head * k;
    }

    private static void head(Canvas c, Paint p, int species, float x, float y, float size,
                             float open, Random rnd, float wind, float time, float lightX) {
        Pal pal = palette(species);
        Tr tr = traits(species, lightX);
        int fam = Species.family(species);
        float R = size * headFor(fam, tr) * (0.55f + 0.45f * open);
        if (R <= 0.5f) return;
        float tilt = wind * 5f + (float) Math.sin(time * 1.4f + species) * 1.4f
                + Species.headTurn(species);

        c.save();
        c.translate(x, y);
        c.rotate(tilt);

        // мягкое свечение за головкой: даёт глубину, но не «неон»
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0, 0, R * 1.75f,
                (pal.tip & 0x00FFFFFF) | 0x22000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, R * 1.75f, p);
        p.setShader(null);
        p.setAlpha(255);

        switch (fam) {
            case 2:  case 19: famPoppy(c, p, rnd, pal, tr, R, open); break;
            case 3:           famSpike(c, p, rnd, pal, tr, R, open, wind); break;
            case 4:           famIris(c, p, rnd, pal, tr, R, open); break;
            case 5:           famCup(c, p, rnd, pal, tr, R, open, false); break;
            case 6:           famCup(c, p, rnd, pal, tr, R, open, true); break;
            case 7:  case 8:  famAngular(c, p, rnd, pal, tr, R, open, fam == 8); break;
            case 9:  case 22: famBell(c, p, rnd, pal, tr, R, open, fam == 22); break;
            case 10:          famUmbel(c, p, rnd, pal, tr, R, open); break;
            case 11:          famDahlia(c, p, rnd, pal, tr, R, open); break;
            case 13:          famCamellia(c, p, rnd, pal, tr, R, open); break;
            case 14:          famHibiscus(c, p, rnd, pal, tr, R, open); break;
            case 15:          famProtea(c, p, rnd, pal, tr, R, open); break;
            case 17:          famOrchid(c, p, rnd, pal, tr, R, open); break;
            case 20:          famCornflower(c, p, rnd, pal, tr, R, open); break;
            case 21:          famThistle(c, p, rnd, pal, tr, R, open); break;
            case 1:  case 16: famBall(c, p, rnd, pal, tr, R, open); break;
            default:          famDaisy(c, p, rnd, pal, tr, R, open); break;
        }

        c.restore();
    }

    // ---- примитивы ----

    /** Насколько лепесток повёрнут к свету: 0 — в тени, 1 — на свету. */
    private static float lit(float angleDeg, Tr tr) {
        float d = (float) Math.cos(Math.toRadians(angleDeg - tr.litAz));
        return Math.max(0f, Math.min(1f, 0.5f + 0.5f * d)) * tr.lightK;
    }

    /** Контур лепестка: основание в (0,0), кончик на (len,0). */
    private static void petalPath(Path p, float len, float halfW, int tip, float wave, float curl) {
        float w = halfW * (1f + wave * 0.14f);
        float bend = curl * len * 0.10f;
        p.reset();
        p.moveTo(0f, 0f);
        if (tip == TIP_POINT) {
            p.cubicTo(len * 0.30f, -w * 1.30f, len * 0.74f, -w * 0.62f, len, bend);
            p.cubicTo(len * 0.74f, w * 0.62f, len * 0.30f, w * 1.30f, 0f, 0f);
        } else if (tip == TIP_NOTCH) {
            p.cubicTo(len * 0.30f, -w * 1.26f, len * 0.86f, -w * 1.00f, len * 0.99f, -w * 0.30f + bend);
            p.lineTo(len * 0.78f, bend);
            p.lineTo(len * 0.99f, w * 0.30f + bend);
            p.cubicTo(len * 0.86f, w * 1.00f, len * 0.30f, w * 1.26f, 0f, 0f);
        } else {
            // круглый кончик; при сильной волнистости кончик делается трёхдольным
            if (wave > 0.55f) {
                p.cubicTo(len * 0.28f, -w * 1.26f, len * 0.72f, -w * 1.04f, len * 0.94f, -w * 0.52f + bend);
                p.quadTo(len * 1.03f, -w * 0.26f + bend, len * 0.93f, -w * 0.02f + bend);
                p.quadTo(len * 1.02f, w * 0.24f + bend, len * 0.93f, w * 0.50f + bend);
                p.cubicTo(len * 0.72f, w * 1.04f, len * 0.28f, w * 1.26f, 0f, 0f);
            } else {
                p.cubicTo(len * 0.30f, -w * 1.26f, len * 0.80f, -w * 1.04f, len, bend);
                p.cubicTo(len * 0.80f, w * 1.04f, len * 0.30f, w * 1.26f, 0f, 0f);
            }
        }
        p.close();
    }

    /** Один лепесток с основанием в (0,0), повёрнутый на angleDeg. */
    private static void petal(Canvas c, Paint p, float angleDeg, float len, float halfW,
                              int tip, float wave, float curl,
                              int baseCol, int tipCol, int rimCol, float light) {
        c.save();
        c.rotate(angleDeg);

        Path path = new Path();
        petalPath(path, len, halfW, tip, wave, curl);

        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
        if (rimCol != 0 && rimCol != tipCol) {
            p.setShader(new LinearGradient(0f, 0f, len, 0f,
                    new int[]{baseCol, tipCol, rimCol}, new float[]{0f, 0.70f, 1f},
                    Shader.TileMode.CLAMP));
        } else {
            p.setShader(new LinearGradient(0f, 0f, len, 0f, baseCol, tipCol, Shader.TileMode.CLAMP));
        }
        c.drawPath(path, p);
        p.setShader(null);

        // прожилки и блик рисуем только на крупных лепестках: в миниатюрах
        // гербария они всё равно не видны, а кадры стоят времени
        if (len >= 6f) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.5f, len * 0.016f));
            p.setColor(0x26FFFFFF);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(len * 0.10f, 0f, len * 0.74f, 0f, p);
            c.drawLine(len * 0.12f, -halfW * 0.34f, len * 0.52f, -halfW * 0.26f, p);
            c.drawLine(len * 0.12f, halfW * 0.34f, len * 0.52f, halfW * 0.26f, p);

            int a = (int) (0x34 * light);
            if (a > 4) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                p.setAlpha(a);
                c.drawCircle(len * 0.66f, -halfW * 0.10f, Math.max(0.8f, halfW * 0.36f), p);
            }
            p.setAlpha(255);
        }

        // бахрома на кончике (василёк, чертополох)
        if (tip == TIP_FRINGE) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.6f, halfW * 0.09f));
            p.setColor(tipCol);
            p.setAlpha(220);
            for (int k = -2; k <= 2; k++) {
                float yy = k * halfW * 0.28f;
                c.drawLine(len * 0.93f, yy, len * (1.10f - 0.03f * Math.abs(k)), yy * 1.45f, p);
            }
            p.setAlpha(255);
        }
        c.restore();
    }

    /** Лепесток, отходящий от центра на радиусе radius под углом a. */
    private static void petalAt(Canvas c, Paint p, Tr tr, float a, float radius,
                                float len, float halfW, int tip,
                                int baseCol, int tipCol, int rimCol, float tilt) {
        c.save();
        c.rotate(a);
        c.translate(radius, 0f);
        c.scale(1f, 1f - 0.30f * tilt);
        petal(c, p, 0f, len, halfW, tip, tr.wave, tilt, baseCol, tipCol, rimCol, lit(a, tr));
        c.restore();
    }

    /** Тень у основания лепестков — без неё головка выглядит плоским блином. */
    private static void coreShadow(Canvas c, Paint p, float r) {
        if (r <= 0.5f) return;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0f, 0f, r, 0x4A000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(0f, 0f, r, p);
        p.setShader(null);
    }

    /** Серединка цветка: тёплый центр, тёмная кромка и точки-тычинки. */
    private static void core(Canvas c, Paint p, float r, Pal pal, int stamens) {
        if (r <= 0.4f) return;
        int hot = dim(pal.core, 0.72f);
        // объём: светлый блик сверху-слева, к краю — насыщеннее и темнее
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(-r * 0.28f, -r * 0.32f, r * 1.45f,
                Color.WHITE, hot, Shader.TileMode.CLAMP));
        c.drawCircle(0f, 0f, r, p);
        p.setShader(null);

        // тёмная кромка: серединка не сливается с лепестками
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.7f, r * 0.16f));
        p.setColor(dim(pal.core, 0.58f));
        p.setAlpha(140);
        c.drawCircle(0f, 0f, r * 0.92f, p);
        p.setAlpha(255);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66FFFFFF);
        c.drawCircle(-r * 0.30f, -r * 0.34f, Math.max(0.7f, r * 0.18f), p);

        if (stamens > 0) {
            p.setColor(pal.stamen);
            p.setAlpha(220);
            float dot = Math.max(0.5f, Math.min(r * 0.13f, r * 0.5f / Math.max(1, stamens / 4f)));
            for (int i = 0; i < stamens; i++) {
                double a = Math.PI * 2 * i / stamens + 0.4;
                float dx = (float) Math.cos(a) * r * 0.55f;
                float dy = (float) Math.sin(a) * r * 0.55f;
                c.drawCircle(dx, dy, dot, p);
            }
            p.setAlpha(255);
        }
    }

    // ---- семейства ----

    /** Ромашка, маргаритка, анемон, астра: плоские ряды узких лепестков. */
    private static void famDaisy(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int n = 10 + rnd.nextInt(6);
        for (int l = 1; l >= 0; l--) {
            int m = Math.max(6, n - l * 3);
            float rr = R * (1f - l * 0.30f);
            int base = l == 0 ? pal.base : pal.base2;
            int tip = l == 0 ? pal.tip : pal.tip2;
            for (int i = 0; i < m; i++) {
                float a = 360f * i / m + l * 16f;
                petalAt(c, p, tr, a, rr * 0.16f, rr * 0.94f, rr * 0.19f * tr.width,
                        tr.tip, base, tip, pal.rim, 0.10f + l * 0.10f);
            }
        }
        coreShadow(c, p, R * 0.46f);
        core(c, p, R * 0.24f * (0.75f + 0.25f * open), pal, 10);
    }

    /** Мак и лютик: несколько очень крупных лепестков и тёмная серединка. */
    private static void famPoppy(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int n = 4 + rnd.nextInt(2);
        for (int i = n - 1; i >= 0; i--) {
            float a = 360f * i / n + 24f;
            petalAt(c, p, tr, a, R * 0.04f, R * 1.14f, R * 0.60f * tr.width,
                    TIP_ROUND, pal.base, pal.tip, pal.rim, 0.55f + tr.tilt * 0.30f);
        }
        coreShadow(c, p, R * 0.48f);
        int darkCore = dim(pal.core, 0.55f);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(-R * 0.05f, -R * 0.05f, R * 0.26f,
                pal.core, darkCore, Shader.TileMode.CLAMP));
        c.drawCircle(0f, 0f, R * 0.22f, p);
        p.setShader(null);
        p.setColor(pal.stamen);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + 0.3;
            c.drawCircle((float) Math.cos(a) * R * 0.30f, (float) Math.sin(a) * R * 0.30f,
                    Math.max(0.5f, R * 0.035f), p);
        }
    }

    /** Лаванда и другие колосья: ярус из мелких цветков вдоль оси. */
    private static void famSpike(Canvas c, Paint p, Random rnd, Pal pal, Tr tr,
                                 float R, float open, float wind) {
        int rows = 7;
        for (int i = 0; i < rows; i++) {
            float yy = -i * R * 0.40f;
            float rr = R * (0.30f - i * 0.022f) * (0.7f + 0.3f * open);
            for (int side = -1; side <= 1; side += 2) {
                float a = side < 0 ? 150f : 30f;
                c.save();
                c.translate(side * rr * 0.70f + wind * i * 0.6f, yy);
                c.rotate(side * 20f);
                petal(c, p, 0f, rr * 1.5f, rr * 0.55f, TIP_ROUND,
                        tr.wave, 0.3f, pal.base, i % 2 == 0 ? pal.tip : pal.tip2,
                        pal.rim, lit(a, tr));
                c.restore();
            }
        }
        core(c, p, R * 0.16f, pal, 3);
    }

    /** Ирис: три стоячих лепестка и три отогнутых вниз. */
    private static void famIris(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        for (int i = 0; i < 3; i++) {
            float a = -90f + (i - 1) * 42f;
            petalAt(c, p, tr, a, R * 0.06f, R * 0.95f, R * 0.32f * tr.width,
                    TIP_POINT, pal.base, pal.tip, pal.rim, 0.15f);
        }
        for (int i = 0; i < 3; i++) {
            float a = 90f + (i - 1) * 46f;
            petalAt(c, p, tr, a, R * 0.08f, R * 1.02f, R * 0.40f * tr.width,
                    TIP_ROUND, pal.base2, pal.tip2, pal.rim, 0.65f);
        }
        coreShadow(c, p, R * 0.34f);
        core(c, p, R * 0.20f, pal, 5);
    }

    /** Тюльпан и лотос: чаша из трёх разведённых лепестков и два внутренних. */
    private static void famCup(Canvas c, Paint p, Random rnd, Pal pal, Tr tr,
                               float R, float open, boolean lotus) {
        float len = R * (lotus ? 0.92f : 0.86f);
        float w = R * (lotus ? 0.52f : 0.62f);
        for (int i = -1; i <= 1; i++) {
            c.save();
            c.rotate(i * (lotus ? 46f : 38f));
            Path cup = new Path();
            cup.moveTo(0f, R * 0.40f);
            cup.cubicTo(-w, -len * 0.10f, -w * 0.92f, -len * 0.72f, 0f, -len);
            cup.cubicTo(w * 0.92f, -len * 0.72f, w, -len * 0.10f, 0f, R * 0.40f);
            cup.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0f, R * 0.4f, 0f, -len,
                    pal.base, pal.tip, Shader.TileMode.CLAMP));
            c.drawPath(cup, p);
            p.setShader(null);
            // тонкая обводка: без неё соседние лепестки сливаются в одну каплю
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.6f, R * 0.022f));
            p.setColor(dim(pal.tip, 0.60f));
            p.setAlpha(150);
            c.drawPath(cup, p);
            p.setAlpha(255);
            // прожилка вдоль лепестка
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.5f, len * 0.025f));
            p.setColor(0x2AFFFFFF);
            c.drawLine(0f, R * 0.25f, 0f, -len * 0.82f, p);
            c.restore();
        }
        // внутренние лепестки второго тона
        for (int i = -1; i <= 1; i += 2) {
            c.save();
            c.rotate(i * 20f);
            Path cup = new Path();
            cup.moveTo(0f, R * 0.24f);
            cup.cubicTo(-w * 0.56f, -len * 0.14f, -w * 0.50f, -len * 0.62f, 0f, -len * 0.80f);
            cup.cubicTo(w * 0.50f, -len * 0.62f, w * 0.56f, -len * 0.14f, 0f, R * 0.24f);
            cup.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0f, R * 0.2f, 0f, -len,
                    pal.throat, pal.tip2, Shader.TileMode.CLAMP));
            c.drawPath(cup, p);
            p.setShader(null);
            c.restore();
        }
        // тёмное горло внутри чаши
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0f, -R * 0.10f, R * 0.30f,
                dim(pal.tip, 0.55f), 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(0f, -R * 0.10f, R * 0.30f, p);
        p.setShader(null);
    }

    /** Звезда и кристалл: угловатые лепестки кольцами. */
    private static void famAngular(Canvas c, Paint p, Random rnd, Pal pal, Tr tr,
                                   float R, float open, boolean crystal) {
        int n = crystal ? 6 : 5 + rnd.nextInt(2);
        int rings = 1 + rnd.nextInt(2);
        for (int l = rings - 1; l >= 0; l--) {
            float rr = R * (1f - l * 0.26f);
            int m = Math.max(4, n - l);
            for (int i = 0; i < m; i++) {
                c.save();
                c.rotate(360f * i / m + l * GOLDEN * 0.5f);
                Path sp = new Path();
                float wide = rr * (crystal ? 0.20f : 0.28f);
                sp.moveTo(0f, -wide * 0.5f);
                sp.lineTo(rr * 0.60f, -wide);
                sp.lineTo(rr, 0f);
                sp.lineTo(rr * 0.60f, wide);
                sp.lineTo(0f, wide * 0.5f);
                sp.close();
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0f, 0f, rr, 0f,
                        l == 0 ? pal.base : pal.base2, l == 0 ? pal.tip : pal.tip2,
                        Shader.TileMode.CLAMP));
                c.drawPath(sp, p);
                p.setShader(null);
                // грань кристалла: тонкая светлая линия вдоль луча
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(0.5f, rr * 0.014f));
                p.setColor(0x40FFFFFF);
                c.drawLine(0f, 0f, rr * 0.92f, 0f, p);
                c.restore();
            }
        }
        coreShadow(c, p, R * (crystal ? 0.30f : 0.36f));
        core(c, p, R * (crystal ? 0.16f : 0.22f), pal, crystal ? 6 : 5);
    }

    /** Колокольчик и подснежник: три чашечки на цветоножках, с отворотом. */
    private static void famBell(Canvas c, Paint p, Random rnd, Pal pal, Tr tr,
                                float R, float open, boolean snowdrop) {
        float spread = snowdrop ? 34f : 48f;
        float k = snowdrop ? 0.30f : 0.34f;
        for (int i = -1; i <= 1; i++) {
            float a = i * spread;
            c.save();
            c.rotate(a);
            // цветоножка от центра к чашечке
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.7f, R * 0.035f));
            p.setColor(Species.stemColor(0) | 0xFF000000);
            p.setAlpha(190);
            c.drawLine(0f, 0f, 0f, R * 0.26f, p);
            p.setAlpha(255);

            c.translate(0f, R * 0.26f);
            Path bell = new Path();
            bell.moveTo(0f, 0f);
            bell.quadTo(-R * k, R * 0.34f, -R * k * 0.90f, R * 0.92f * open);
            bell.quadTo(0f, R * 1.16f * open, R * k * 0.90f, R * 0.92f * open);
            bell.quadTo(R * k, R * 0.34f, 0f, 0f);
            bell.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0f, 0f, 0f, R, pal.tip, pal.base, Shader.TileMode.CLAMP));
            c.drawPath(bell, p);
            p.setShader(null);

            // светлый отворот: без него чашечка выглядит каплей
            p.setColor(pal.throat);
            p.setAlpha(200);
            c.drawOval(-R * k * 0.66f, R * 0.70f * open, R * k * 0.66f, R * 0.96f * open, p);
            p.setAlpha(255);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.5f, R * 0.016f));
            p.setColor(0x55FFFFFF);
            c.drawLine(-R * k * 0.84f, R * 0.92f * open, R * k * 0.84f, R * 0.92f * open, p);
            c.restore();
        }
    }

    /** Зонтик: шапка мелких цветков на одном уровне, с серединкой. */
    private static void famUmbel(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 - Math.PI / 2;
            floret(c, p, pal, tr, (float) Math.cos(a) * R * 0.72f,
                    (float) Math.sin(a) * R * 0.72f, R * 0.26f, open);
        }
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3 + Math.PI / 6;
            floret(c, p, pal, tr, (float) Math.cos(a) * R * 0.30f,
                    (float) Math.sin(a) * R * 0.30f, R * 0.22f, open);
        }
    }

    /** Мелкий цветочек для зонтика и соцветий. */
    private static void floret(Canvas c, Paint p, Pal pal, Tr tr,
                               float px, float py, float r, float open) {
        c.save();
        c.translate(px, py);
        float k = 0.55f + 0.45f * open;
        for (int i = 0; i < 5; i++) {
            petal(c, p, 72f * i + 18f, r * k, r * 0.40f * k, TIP_ROUND,
                    tr.wave, 0.15f, pal.base, pal.tip, pal.rim, 0.55f);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal.core);
        c.drawCircle(0f, 0f, Math.max(0.5f, r * 0.26f * k), p);
        c.restore();
    }

    /** Георгин: много узких острых лепестков в четыре ряда. */
    private static void famDahlia(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int rings = 4;
        for (int l = rings - 1; l >= 0; l--) {
            float rr = R * (1f - l * 0.19f);
            int m = 10 + l * 2;
            for (int i = 0; i < m; i++) {
                float a = 360f * i / m + l * 18f;
                petalAt(c, p, tr, a, rr * 0.10f, rr * 0.90f, rr * 0.16f * tr.width,
                        TIP_POINT,
                        l == 0 ? pal.base : pal.base2, l == 0 ? pal.tip : pal.tip2,
                        pal.rim, 0.20f + l * 0.14f);
            }
        }
        coreShadow(c, p, R * 0.34f);
        core(c, p, R * 0.14f, pal, 4);
    }

    /** Камелия: плотные округлые лепестки внахлёст, тычинок почти не видно. */
    private static void famCamellia(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int rings = 3;
        for (int l = rings - 1; l >= 0; l--) {
            float rr = R * (1f - l * 0.26f);
            int m = 6 + l;
            for (int i = 0; i < m; i++) {
                float a = 360f * i / m + l * 24f;
                petalAt(c, p, tr, a, rr * 0.10f, rr * 0.92f, rr * 0.40f * tr.width,
                        TIP_ROUND, l == 0 ? pal.base : pal.base2,
                        l == 0 ? pal.tip : pal.tip2, pal.rim, 0.30f + l * 0.10f);
            }
            coreShadow(c, p, rr * 0.72f);
        }
        core(c, p, R * 0.12f, pal, 3);
    }

    /** Гибискус: пять широких лепестков и длинная колонка с пыльниками. */
    private static void famHibiscus(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        for (int i = 4; i >= 0; i--) {
            float a = 72f * i + 18f;
            petalAt(c, p, tr, a, R * 0.06f, R * 0.98f, R * 0.46f * tr.width,
                    TIP_ROUND, pal.base, pal.tip, pal.rim, 0.42f);
        }
        coreShadow(c, p, R * 0.40f);
        // основание колонки: тёплое горло, а не «дыра» между лепестками
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0f, -R * 0.02f, R * 0.14f,
                dim(pal.tip, 0.74f), dim(pal.tip, 0.52f), Shader.TileMode.CLAMP));
        c.drawCircle(0f, 0f, R * 0.13f, p);
        p.setShader(null);
        // колонка вверх с пятью пыльниками
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, R * 0.07f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(pal.stamen);
        c.drawLine(0f, 0f, 0f, -R * 0.62f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal.core);
        for (int i = -2; i <= 2; i++) {
            c.drawCircle(i * R * 0.09f, -R * 0.66f, Math.max(0.7f, R * 0.05f), p);
        }
        c.drawCircle(0f, -R * 0.74f, Math.max(0.8f, R * 0.06f), p);
    }

    /** Протея: чаша из острых прицветников и мохнатый центр. */
    private static void famProtea(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int n = 9;
        for (int i = 0; i < n; i++) {
            float a = 360f * i / n + 10f;
            petalAt(c, p, tr, a, R * 0.06f, R * 0.92f, R * 0.26f * tr.width,
                    TIP_POINT, pal.base2, pal.tip2, pal.rim, 0.72f);
        }
        coreShadow(c, p, R * 0.42f);
        // центр: щётка из мелких волосков
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.6f, R * 0.024f));
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            p.setColor(i % 2 == 0 ? dim(pal.core, 0.72f) : dim(pal.stamen, 0.80f));
            c.drawLine(0f, 0f, (float) Math.cos(a) * R * 0.30f,
                    (float) Math.sin(a) * R * 0.30f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(dim(pal.base2, 0.55f));
        c.drawCircle(0f, 0f, R * 0.12f, p);
    }

    /** Орхидея: три верхних узких лепестка, два боковых и губа с крапом. */
    private static void famOrchid(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        for (int i = -1; i <= 1; i++) {
            float a = -90f + i * 38f;
            petalAt(c, p, tr, a, R * 0.08f, R * 0.86f, R * 0.24f * tr.width,
                    TIP_POINT, pal.base, pal.tip, pal.rim, 0.20f);
        }
        for (int i = -1; i <= 1; i += 2) {
            float a = i * 62f;
            petalAt(c, p, tr, a, R * 0.10f, R * 0.94f, R * 0.30f * tr.width,
                    TIP_POINT, pal.base2, pal.tip2, pal.rim, 0.30f);
        }
        // губа: широкая, вниз, с пятнышками
        c.save();
        c.rotate(90f);
        c.translate(R * 0.16f, 0f);
        c.scale(1f, 0.80f);
        petal(c, p, 0f, R * 0.88f, R * 0.46f * tr.width, TIP_NOTCH,
                tr.wave, 0.25f, pal.base2, pal.tip2, pal.rim, lit(90f, tr));
        c.restore();
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal.core);
        for (int i = 0; i < 5; i++) {
            float xx = (i - 2) * R * 0.09f;
            c.drawCircle(xx * 0.7f, R * 0.36f + (i % 2) * R * 0.05f,
                    Math.max(0.5f, R * 0.022f), p);
        }
        // серединка-колонка: закрывает стык лепестков
        c.drawCircle(0f, 0f, R * 0.14f, p);
        coreShadow(c, p, R * 0.24f);
    }

    /** Василёк: узкие лепестки с бахромой на концах. */
    private static void famCornflower(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int n = 9 + rnd.nextInt(4);
        for (int l = 1; l >= 0; l--) {
            int m = Math.max(6, n - l * 2);
            float rr = R * (1f - l * 0.28f);
            for (int i = 0; i < m; i++) {
                float a = 360f * i / m + l * 20f;
                petalAt(c, p, tr, a, rr * 0.12f, rr * (0.96f - l * 0.12f), rr * 0.15f * tr.width,
                        TIP_FRINGE, l == 0 ? pal.base : pal.base2,
                        l == 0 ? pal.tip : pal.tip2, pal.rim, 0.15f + l * 0.10f);
            }
        }
        coreShadow(c, p, R * 0.30f);
        core(c, p, R * 0.18f, pal, 6);
    }

    /** Чертополох: колючая шишка из прицветников и хохолок сверху. */
    private static void famThistle(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int rings = 3;
        int bractBase = dim(pal.base2, 0.78f);
        int bractTip = dim(pal.tip2, 0.68f);
        for (int l = rings - 1; l >= 0; l--) {
            c.save();
            c.translate(0f, -R * 0.10f * l);
            float rr = R * (0.92f - l * 0.24f);
            int m = 9 + l * 2;
            for (int i = 0; i < m; i++) {
                float a = 360f * i / m + l * 15f;
                petalAt(c, p, tr, a, rr * 0.06f, rr * 0.66f, rr * 0.10f * tr.width,
                        TIP_POINT, bractBase, bractTip, pal.rim, 0.34f + l * 0.12f);
            }
            c.restore();
        }
        // хохолок: короткие волоски вверх
        int tuft = dim(pal.base2, 0.62f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.6f, R * 0.020f));
        p.setColor(tuft);
        for (int i = -3; i <= 3; i++) {
            c.drawLine(i * R * 0.04f, -R * 0.22f, i * R * 0.09f, -R * 0.50f, p);
        }
        coreShadow(c, p, R * 0.22f);
    }

    /** Пион и ранункулюс: плотный шар из округлых лепестков, серединки почти нет. */
    private static void famBall(Canvas c, Paint p, Random rnd, Pal pal, Tr tr, float R, float open) {
        int rings = 4;
        float[] rad = {1.00f, 0.78f, 0.56f, 0.34f};
        int[] cnt = {8, 7, 6, 5};
        for (int l = 0; l < rings; l++) {
            int m = cnt[l] + rnd.nextInt(2);
            float rr = R * rad[l];
            for (int i = 0; i < m; i++) {
                float a = 360f * i / m + l * GOLDEN * 0.42f;
                petalAt(c, p, tr, a, rr * 0.12f, rr * 0.94f, rr * 0.34f * tr.width,
                        TIP_ROUND,
                        l % 2 == 0 ? pal.base : pal.base2,
                        l % 2 == 0 ? pal.tip : pal.tip2,
                        pal.rim, 0.30f + l * 0.12f);
            }
            coreShadow(c, p, rr * 0.76f);
        }
        core(c, p, R * 0.14f, pal, 4);
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
}
