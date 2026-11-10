package com.dsh.stepcounter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

/**
 * Сезонные украшения для виджета: осень — кленовые листья, зима — снежинки,
 * лето — ромашки и зелень, весна — подснежники и цветущие веточки.
 *
 * Картинки лежат в res/drawable-nodpi под именами deco_aut_01, deco_win_01,
 * deco_sum_01, deco_spr_01 — при нарезке листов они получаются сами.
 */
final class SeasonDeco {

    static final int WINTER = 0, SPRING = 1, SUMMER = 2, AUTUMN = 3;

    /** Акцентный цвет полосы прогресса под сезон. */
    static int accent(Context ctx, int season) {
        switch (season) {
            case WINTER: return 0xFFB9D7F2;
            case SPRING: return 0xFFF6A8C6;
            case SUMMER: return 0xFFA9DE7C;
            default:     return 0xFFF0A65A;
        }
    }

    static String title(int season) {
        switch (season) {
            case WINTER: return "зима";
            case SPRING: return "весна";
            case SUMMER: return "лето";
            default:     return "осень";
        }
    }

    /** Ключ в настройках: ручной выбор сезона для проверки (-1 — по календарю). */
    static final String PREF = "widget_season";

    /** Ручной выбор сезона из меню разработчика: -1 — по месяцу. */
    static int override(Context ctx) {
        if (ctx == null) return -1;
        return ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt(PREF, -1);
    }

    static void setOverride(Context ctx, int season) {
        ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putInt(PREF, season).apply();
    }

    /** Сезон по текущему месяцу: декабрь–февраль зима и так далее. */
    static int season(Context ctx) {
        int forced = override(ctx);
        if (forced >= 0 && forced <= 3) return forced;
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.MONTH) + 1;
        if (m == 12 || m <= 2) return WINTER;
        if (m <= 5) return SPRING;
        if (m <= 8) return SUMMER;
        return AUTUMN;
    }


    /** Фон карточки под сезон: готовый лист с волнами и украшениями. */
    static int wave(Context ctx, int season) {
        if (ctx == null) return 0;
        String name;
        switch (season) {
            case WINTER: name = "wave_win"; break;
            case SPRING: name = "wave_spr"; break;
            case SUMMER: name = "wave_sum"; break;
            default:     name = "wave_aut"; break;
        }
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    private static final HashMap<Integer, Bitmap> WAVES = new HashMap<>();

    /** Битмап фона с кэшем: перерисовываем каждые 5 минут, декодировать каждый раз дорого. */
    static Bitmap waveBitmap(Context ctx, int season) {
        int id = wave(ctx, season);
        if (id == 0) return null;
        Bitmap b = WAVES.get(id);
        if (b != null) return b;
        try {
            b = BitmapFactory.decodeResource(ctx.getResources(), id);
        } catch (Exception ignored) {
            return null;
        }
        if (b != null) WAVES.put(id, b);
        return b;
    }

    private static String prefix(int season) {
        switch (season) {
            case WINTER: return "deco_win_";
            case SPRING: return "deco_spr_";
            case SUMMER: return "deco_sum_";
            default:     return "deco_aut_";
        }
    }

    private static final HashMap<String, int[]> IDS = new HashMap<>();
    private static final HashMap<Integer, Bitmap> CACHE = new HashMap<>();

    /** Все картинки одного сезона (порядок — как нарезались). */
    static int[] ids(Context ctx, int season) {
        if (ctx == null) return new int[0];
        String key = prefix(season);
        int[] cached = IDS.get(key);
        if (cached != null) return cached;

        Resources r = ctx.getResources();
        List<Integer> found = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            int id = r.getIdentifier(String.format(java.util.Locale.US, key + "%02d", i),
                    "drawable", ctx.getPackageName());
            if (id == 0) {
                if (i > 3) break;        // элементы кончились
                continue;
            }
            found.add(id);
        }
        int[] out = new int[found.size()];
        for (int i = 0; i < out.length; i++) out[i] = found.get(i);
        IDS.put(key, out);
        return out;
    }

    /** Битмап по индексу, с кэшем: виджет перерисовывается каждые 5 минут. */
    static Bitmap bitmap(Context ctx, int resId) {
        if (resId == 0) return null;
        Bitmap b = CACHE.get(resId);
        if (b != null) return b;
        try {
            b = BitmapFactory.decodeResource(ctx.getResources(), resId);
        } catch (Exception ignored) {
            return null;
        }
        if (b != null) CACHE.put(resId, b);
        return b;
    }
}
