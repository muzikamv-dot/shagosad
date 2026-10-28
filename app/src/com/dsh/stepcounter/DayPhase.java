package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

/**
 * Сцена в карточке растения: небо, светила, облака, осадки.
 *
 * Положение солнца и луны — настоящее: считается по дате и координатам (SunMoon),
 * поэтому рассвет, закат и ночь наступают тогда, когда они наступают на улице.
 * Палитра неба выбирается по высоте солнца над горизонтом, а не по часам.
 *
 * Погода в режиме «авто» берётся из open-meteo (Weather) и кэшируется на 20 минут;
 * если сети нет, остаётся последняя известная, а если нет и её — ясно.
 * Ручные значения («ясно / облачно / дождь / снег») по-прежнему главнее автоматики.
 */
final class DayPhase {

    static final int WEATHER_AUTO = 0;
    static final int WEATHER_CLEAR = 1;
    static final int WEATHER_CLOUDY = 2;
    static final int WEATHER_RAIN = 3;
    static final int WEATHER_SNOW = 4;

    static final String PREF_WEATHER = "scene_weather";
    static final String PREF_HOUR = "scene_hour";

    private DayPhase() { }

    // ---------------- время и координаты ----------------

    /** Час дня 0..24 для подписи; тест может подменить. */
    static float hour(Context ctx) {
        float test = Steps.prefs(ctx).getFloat(PREF_HOUR, -1f);
        if (test >= 0f) return test % 24f;
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f;
    }

    /** Момент, для которого считаем астрономию. Учитывает тестовый час. */
    static long stamp(Context ctx) {
        float test = Steps.prefs(ctx).getFloat(PREF_HOUR, -1f);
        if (test < 0f) return System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        int h = (int) test;
        c.set(Calendar.HOUR_OF_DAY, Math.min(23, Math.max(0, h)));
        c.set(Calendar.MINUTE, Math.round((test - h) * 60f));
        c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }

    static double lat(Context ctx) {
        float v = Weather.lat(ctx);
        return Float.isNaN(v) ? 55.75 : v;
    }

    static double lon(Context ctx) {
        float v = Weather.lon(ctx);
        if (!Float.isNaN(v)) return v;
        // без координат — грубая долгота из часового пояса, 15° на час
        int off = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000;
        return off * 15.0;
    }

    // ---------------- погода ----------------

    static int weatherPref(Context ctx) {
        return Steps.prefs(ctx).getInt(PREF_WEATHER, WEATHER_AUTO);
    }

    static int nextWeather(int w) { return w >= WEATHER_SNOW ? WEATHER_AUTO : w + 1; }

    static String weatherName(int w) {
        switch (w) {
            case WEATHER_CLEAR: return "ясно";
            case WEATHER_CLOUDY: return "облачно";
            case WEATHER_RAIN: return "дождь";
            case WEATHER_SNOW: return "снег";
            default: return "авто";
        }
    }

    /** Итог сцены: ручная настройка или реальная погода из сети. */
    static int scene(Context ctx) {
        // нет доступа к погоде — всегда ясно (решение владельца)
        if (!Weather.hasData(ctx)) return WEATHER_CLEAR;
        int pref = weatherPref(ctx);
        if (pref != WEATHER_AUTO) return pref;
        return Weather.sceneWeather(Weather.code(ctx), Weather.cloud(ctx),
                Weather.snow(ctx), Weather.precip(ctx));
    }

    /** Облачность в процентах: реальная или из ручной настройки. */
    static int cloudPercent(Context ctx) {
        if (weatherPref(ctx) == WEATHER_AUTO) {
            int c = Weather.cloud(ctx);
            return c < 0 ? 5 : c;
        }
        switch (weatherPref(ctx)) {
            case WEATHER_CLOUDY: return 85;
            case WEATHER_RAIN: return 95;
            case WEATHER_SNOW: return 90;
            default: return 5;
        }
    }

    // ---------------- палитра по высоте солнца ----------------

    /** Ключи по высоте солнца (градусы) и цвета неба сверху и у горизонта. */
    private static final float[] A_H = {65f, 25f, 8f, 0f, -6f, -14f, -60f};
    private static final int[] A_TOP = {
            0xFF0E2A3C, 0xFF102C3F, 0xFF172538, 0xFF1B1A36, 0xFF12102A, 0xFF070A12, 0xFF05070C};
    private static final int[] A_BOT = {
            0xFF1B4655, 0xFF244A55, 0xFF4C3B36, 0xFF4A2A3A, 0xFF2A1830, 0xFF0C1020, 0xFF0A0F18};

    static int skyTop(float alt) { return keyColor(A_TOP, alt); }
    static int skyBottom(float alt) { return keyColor(A_BOT, alt); }

    private static int keyColor(int[] colors, float alt) {
        if (alt >= A_H[0]) return colors[0];
        for (int i = 1; i < A_H.length; i++) {
            if (alt >= A_H[i]) {
                float span = A_H[i - 1] - A_H[i];
                float t = span <= 0f ? 0f : (A_H[i - 1] - alt) / span;
                return blend(colors[i - 1], colors[i], t);
            }
        }
        return colors[colors.length - 1];
    }

    static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
        int rr = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int gg = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bb = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.argb(aa, rr, gg, bb);
    }

    /** 0 — день, 1 — ночь: по высоте солнца, а не по часам. */
    static float night(float alt) {
        if (alt > 6f) return 0f;
        if (alt < -12f) return 1f;
        return (6f - alt) / 18f;
    }

    // ---------------- отрисовка ----------------

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path CLIP = new Path();
    private static final RectF TMP = new RectF();

    /** Небо, светила, облака и земля — позади растения. */
    static void drawBack(Context ctx, Canvas c, RectF card, float radius, float d, float timeSec) {
        float w = card.width(), h = card.height();
        if (w <= 0 || h <= 0) return;

        long stamp;
        float[] sun;
        float[] moon;
        int cloud;
        float alt;
        float night;

        boolean testHour = Steps.prefs(ctx).getFloat(PREF_HOUR, -1f) >= 0f;
        if (!Weather.hasData(ctx) && !testHour) {
            // Доступа к погоде нет — по решению владельца сцена всегда полдень и ясно:
            // солнце в зените юга, ни облаков, ни осадков, никакой ночи.
            stamp = System.currentTimeMillis();
            sun = new float[]{56f, 180f};
            moon = new float[]{-60f, 0f};
            cloud = 0;
        } else {
            stamp = stamp(ctx);
            sun = SunMoon.sun(stamp, lat(ctx), lon(ctx));
            moon = SunMoon.moon(stamp, lat(ctx), lon(ctx));
            cloud = cloudPercent(ctx);
        }
        alt = sun[0];
        night = night(alt);

        int top = skyTop(alt);
        int bottom = skyBottom(alt);
        if (cloud > 10) {
            float k = Math.min(0.62f, cloud / 100f * 0.62f);
            top = blend(top, 0xFF232A33, k);
            bottom = blend(bottom, 0xFF39424E, k * 0.85f);
        }

        CLIP.reset();
        CLIP.addRoundRect(card, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(CLIP);

        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(card.left, card.top, card.left, card.bottom,
                top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(card, P);
        P.setShader(null);

        // звёзды: тем заметнее, чем ниже солнце
        if (night > 0.05f && cloud < 90) {
            Random rnd = new Random(daySeed());
            int stars = (int) (46 * night * (1f - cloud / 130f));
            for (int i = 0; i < stars; i++) {
                float sx = card.left + rnd.nextFloat() * w;
                float sy = card.top + rnd.nextFloat() * h * 0.72f;
                float r = (0.7f + rnd.nextFloat() * 1.1f) * d;
                float tw = 0.55f + 0.45f * (float) Math.sin(timeSec * 1.6f + i * 1.7f);
                P.setStyle(Paint.Style.FILL);
                P.setColor(0xFFFFFFFF);
                P.setAlpha(Math.min(255, (int) (235 * night * (0.35f + 0.5f * tw))));
                c.drawCircle(sx, sy, r, P);
            }
            P.setAlpha(255);
        }

        // насколько вообще видно светила: чем больше облаков, тем слабее,
        // при сплошной облачности (100 %) — не видно совсем
        float seeCelestial = (float) Math.max(0.0, 1.0 - Math.pow(cloud / 100.0, 1.5));

        // солнце на своём настоящем месте: азимут → по горизонтали, высота → по вертикали.
        // Клип по небу: ниже горизонта светило не рисуется, поэтому оно «садится» за край.
        float horizonY = card.bottom - h * 0.20f;
        long today = dayStart(stamp);
        float sunMax = maxAltFor(today, ctx, true);
        float moonMax = maxAltFor(today, ctx, false);
        int skyLayer = c.save();
        c.clipRect(card.left, card.top, card.right, horizonY);

        if (sun[0] > -2f && seeCelestial > 0.06f) {
            float sx = posX(card, sun[1]);
            float sy = posY(card, sun[0], sun[1], sunMax);
            // диск того же размера, что у луны: их угловые размеры почти равны
            float r = 20f * d;
            int glow = (int) (0x66 * seeCelestial);
            P.setShader(new RadialGradient(sx, sy, r * 3.2f,
                    (Math.max(0, glow) << 24) | 0xFFE0A8, 0x00FFE0A8, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, r * 3.2f, P);
            P.setShader(null);
            P.setColor(0xFFFFE9C4);
            P.setAlpha((int) (240 * seeCelestial));
            c.drawCircle(sx, sy, r, P);
            P.setAlpha(255);
        } else if (moon[0] > -2f && night > 0.12f && seeCelestial > 0.06f) {
            float mx = posX(card, moon[1]);
            float my = posY(card, moon[0], moon[1], moonMax);
            float r = 20f * d;                       // луна вдвое крупнее
            float illum = SunMoon.moonIllum(stamp);
            float phase = SunMoon.moonPhase(stamp);
            int alpha = (int) (255 * night * seeCelestial);

            P.setShader(new RadialGradient(mx, my, r * 3.4f,
                    ((int) (0x44 * seeCelestial) << 24) | 0xDCE6FF,
                    0x00DCE6FF, Shader.TileMode.CLAMP));
            c.drawCircle(mx, my, r * 3.4f, P);
            P.setShader(null);

            // только освещённая часть: никакого чёрного круга рядом с луной
            if (illum > 0.02f) {
                drawPhaseDisc(c, mx, my, r, illum, phase, 0xFFF2F5FF, alpha);
            }
        }

        c.restoreToCount(skyLayer);

        // облака прямо из процента облачности: 0 % — чистое небо, 100 % — сплошная пелена
        int clouds = cloud < 5 ? 0 : cloud < 25 ? 1 : cloud < 50 ? 2 : cloud < 75 ? 4
                : cloud < 90 ? 6 : 8;
        int cloudAlpha = (int) (10 + cloud * 0.82f);
        if (cloud >= 88) {
            // сплошная облачность: ровная пелена сверху, чтобы «хмуро» читалось сразу.
            // Слабая, чтобы не забивать точки стадий и подписи в карточке.
            int band = (int) Math.min(110, (cloud - 85) * 7f);
            P.setShader(new LinearGradient(card.left, card.top, card.left, card.top + h * 0.42f,
                    (band << 24) | 0xE8EEF5, 0x00E8EEF5, Shader.TileMode.CLAMP));
            c.drawRect(card.left, card.top, card.right, card.top + h * 0.42f, P);
            P.setShader(null);
        }
        Random cr = new Random(daySeed() + 77);
        for (int i = 0; i < clouds; i++) {
            float baseX = cr.nextFloat();
            float by = card.top + h * (0.08f + cr.nextFloat() * 0.34f);
            float cw = w * (0.26f + cr.nextFloat() * 0.20f);
            float speed = 4f + cr.nextFloat() * 6f;
            float x = card.left - cw + (baseX * (w + cw * 2) + timeSec * speed) % (w + cw * 2);
            // каждое облако — два мягких пятна, у каждого свой радиальный градиент
            // от центра: иначе видны круглые кромки и облако похоже на пузыри
            int a = Math.max(0, Math.min(255, cloudAlpha));
            softCloud(c, x, by, x + cw, by + cw * 0.26f, a);
            softCloud(c, x + cw * 0.30f, by - cw * 0.12f, x + cw * 0.92f, by + cw * 0.20f, a);
        }
        P.setAlpha(255);

        // земля: заметная полоса ниже горизонта, чтобы горизонт читался
        float ground = card.bottom - h * 0.20f;
        P.setShader(new LinearGradient(card.left, ground, card.left, card.bottom,
                new int[]{0x00000000, 0x26000000, 0x4A000000},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(card.left, ground, card.right, card.bottom, P);
        P.setShader(null);
        // тонкая линия горизонта
        P.setColor(0x1FFFFFFF);
        P.setStrokeWidth(Math.max(1f, d));
        c.drawLine(card.left + radius * 0.3f, ground, card.right - radius * 0.3f, ground, P);
        P.setStrokeWidth(1f);

        // виньетка
        P.setShader(new RadialGradient(card.centerX(), card.centerY(),
                Math.max(w, h) * 0.72f, 0x00000000, 0x4A000000, Shader.TileMode.CLAMP));
        c.drawRect(card, P);
        P.setShader(null);

        c.restore();
        P.setAlpha(255);
        P.setStyle(Paint.Style.FILL);
    }

    /** Осадки — поверх растения. */
    static void drawFront(Context ctx, Canvas c, RectF card, float radius, float d, float timeSec) {
        int weather = scene(ctx);
        if (weather != WEATHER_RAIN && weather != WEATHER_SNOW) return;

        float w = card.width(), h = card.height();
        if (w <= 0 || h <= 0) return;

        CLIP.reset();
        TMP.set(card.left, card.top, card.right, card.bottom - 42f * d);
        CLIP.addRoundRect(TMP, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(CLIP);
        P.setAlpha(255);

        if (weather == WEATHER_RAIN) {
            float prec = weatherPref(ctx) == WEATHER_AUTO ? Weather.precip(ctx) : 1.5f;
            int drops = (int) Math.max(26, Math.min(95, 26 + prec * 22));
            Random rnd = new Random(4242);
            P.setColor(0xFFBFD8E8);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(1.1f * d);
            for (int i = 0; i < drops; i++) {
                float x0 = card.left + rnd.nextFloat() * w;
                float speed = (760f + rnd.nextFloat() * 520f) * d;
                float len = (12f + rnd.nextFloat() * 14f) * d;
                float phase = rnd.nextFloat() * h;
                float y = card.top + (phase + timeSec * speed) % (h + len) - len;
                float slant = 3.2f * d;
                P.setAlpha(70 + (int) (rnd.nextFloat() * 70));
                c.drawLine(x0, y, x0 + slant, y + len, P);
            }
        } else {
            float sn = weatherPref(ctx) == WEATHER_AUTO ? Weather.snow(ctx) : 1f;
            int flakes = (int) Math.max(22, Math.min(70, 22 + sn * 12));
            Random rnd = new Random(9090);
            P.setStyle(Paint.Style.FILL);
            for (int i = 0; i < flakes; i++) {
                float x0 = card.left + rnd.nextFloat() * w;
                float speed = (52f + rnd.nextFloat() * 70f) * d;
                float r = (1.4f + rnd.nextFloat() * 2.0f) * d;
                float phase = rnd.nextFloat() * h;
                float y = card.top + (phase + timeSec * speed) % (h + 8 * d);
                float sway = (float) Math.sin(timeSec * 0.9f + i) * 6f * d;
                P.setColor(0xFFFFFFFF);
                P.setAlpha(120 + (int) (rnd.nextFloat() * 90));
                c.drawCircle(x0 + sway, y, r, P);
            }
        }

        P.setAlpha(255);
        P.setStyle(Paint.Style.FILL);
        c.restore();
    }

    /**
     * Луна в фазе: заливается только освещённая часть, тёмная сторона просто
     * прозрачна — поэтому рядом с луной не видно чёрного круга.
     *
     * Освещённая часть ограничена дугой по краю диска (со стороны света) и
     * полуэллипсом-терминатором: при растущей луне он выгнут в тёмную сторону
     * (горб), при молодой — в светлую (серп).
     */
    private static void drawPhaseDisc(Canvas c, float cx, float cy, float r,
                                      float illum, float phase, int color, int alpha) {
        float b = r * Math.abs(1f - 2f * illum);
        RectF outer = new RectF(cx - r, cy - r, cx + r, cy + r);
        RectF inner = new RectF(cx - b, cy - r, cx + b, cy + r);
        boolean waxing = phase < 0.5f;

        Path p = new Path();
        p.moveTo(cx, cy - r);
        // дуга по освещённому краю: справа при растущей, слева при убывающей
        p.arcTo(outer, -90, waxing ? 180 : -180, false);
        // терминатор — полуэллипс от нижней точки к верхней
        if (illum >= 0.5f) {
            p.arcTo(inner, 90, waxing ? 180 : -180, false);   // горб в тёмную сторону
        } else {
            p.arcTo(inner, 90, waxing ? -180 : 180, false);   // серп: выгиб в светлую
        }
        p.close();

        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        P.setAlpha(alpha);
        c.drawPath(p, P);
        P.setAlpha(255);
    }

    /** Мягкое облачное пятно: радиальный градиент от центра овала, без резкой кромки. */
    private static void softCloud(Canvas c, float l, float t, float r, float b, int alpha) {
        if (alpha <= 0) return;
        float cx = (l + r) / 2f, cy = (t + b) / 2f;
        float rad = Math.max(l < r ? (r - l) / 2f : 1f, 1f);
        P.setShader(new RadialGradient(cx, cy, rad,
                (alpha << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawOval(l, t, r, b, P);
        P.setShader(null);
    }

    /** Нормированный азимут: -1 (восток) .. 0 (юг) .. +1 (запад). */
    private static float skyU(float az) {
        float u = (az - 180f) / 90f;
        return Math.max(-1.15f, Math.min(1.15f, u)) / 1.15f;
    }

    /** Горизонталь светила: раздвинута к краям, чтобы диск обходил цветок. */
    private static float posX(RectF card, float az) {
        return card.centerX() + skyU(az) * card.width() * 0.46f;
    }

    /**
     * Вертикаль светила. Купол идёт от горизонта у краёв к верху карточки по эллипсу
     * (sqrt(1-u^2)), а высота поднимает диск от горизонта к этому куполу. Так путь
     * Светило едет по куполу: положение зависит только от азимута, поэтому солнце
     * и луна идут по одной и той же широкой дуге. В полдень (азимут 180) — у верха,
     * на восходе и закате (края) — у горизонта.
     */
    private static float posY(RectF card, float alt, float az, float maxAlt) {
        float u = skyU(az);
        float horizon = card.bottom - card.height() * 0.20f;
        float top = card.top + card.height() * 0.06f;
        return horizon - (horizon - top) * (float) Math.sqrt(Math.max(0f, 1f - u * u));
    }

    // кеш: максимум за сутки считаем один раз, иначе 97 расчётов на каждый кадр
    private static long cachedDay = -1;
    private static float cachedSunMax = -1f;
    private static float cachedMoonMax = -1f;

    /** Максимальная высота светила за сутки — по ней и растягиваем дугу. */
    private static float maxAltFor(long dayStart, Context ctx, boolean isSun) {
        if (cachedDay != dayStart) {
            cachedDay = dayStart;
            cachedSunMax = -1f;
            cachedMoonMax = -1f;
        }
        if (isSun ? cachedSunMax >= 0f : cachedMoonMax >= 0f) {
            return isSun ? cachedSunMax : cachedMoonMax;
        }
        double lat = lat(ctx), lon = lon(ctx);
        float best = 0f;
        for (int m = 0; m <= 24 * 60; m += 15) {
            float[] p = isSun ? SunMoon.sun(dayStart + m * 60000L, lat, lon)
                              : SunMoon.moon(dayStart + m * 60000L, lat, lon);
            if (p[0] > best) best = p[0];
        }
        if (isSun) cachedSunMax = best; else cachedMoonMax = best;
        return best;
    }

    /** Начало текущих суток. */
    private static long dayStart(long stamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(stamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Старое отображение без нормировки — оставлено как справка. */

    private static int daySeed() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    }
