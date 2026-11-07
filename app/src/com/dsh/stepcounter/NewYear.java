package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.drawable.Drawable;

/**
 * Новогодний ивент: адвент-календарь из 31 окошка и ёлка, которую игрок наряжает сам.
 *
 * Пока идёт разработка (до первого релиза ивента) даты НЕ проверяются: все окошки
 * доступны всегда. Дата-гейт включим позже — 1 декабря … 31 января.
 */
public final class NewYear {

    /** Сколько окошек в календаре (1–31 декабря). */
    public static final int DAYS = 31;

    /** Сколько разных украшений в наборе. */
    public static final int ORNAMENTS = 36;

    /** Индекс украшения-макушки (звезда на верхушку). */
    public static final int TOP_ORNAMENT = 4;

    /** Названия украшений — в том же порядке, что и ny_orn_01…ny_orn_36. */
    public static final String[] NAMES = {
            "Красный шар", "Золотой шар", "Синий шар", "Звёздная гирлянда", "Звезда-макушка", "Снегирь",
            "Мандарин", "Конфета", "Колокольчик", "Шишка", "Шар-снежинка", "Пряник",
            "Красный подарок", "Золотой подарок", "Снеговик", "Олень", "Варежка", "Носок",
            "Венок", "Свеча", "Сосулька", "Красный бант", "Фонарь", "Барабан",
            "Сани", "Ангел", "Пуансеттия", "Сердце", "Кристалл", "Мишура",
            "Звезда-печенье", "Ключ", "Домик в снегу", "Ветка с ягодами", "Стеклянный шар", "Скворечник"
    };

    private NewYear() { }

    /** Какое украшение даёт окошко дня (1…31). Порядок фиксирован, чтобы не «прыгало». */
    public static int ornamentForDay(int day) {
        int[] order = {
                0, 6, 8, 11, 4, 1, 17, 34, 14, 25, 2, 21, 10, 5, 12, 26,
                3, 7, 23, 15, 9, 13, 28, 31, 35, 22, 27, 16, 32, 19, 24
        };
        int d = Math.max(1, Math.min(DAYS, day));
        return order[d - 1] % ORNAMENTS;
    }

    /** Открыто ли окошко дня. */
    public static boolean isOpen(GameState game, int day) {
        return (game.nyMask & (1 << (day - 1))) != 0;
    }

    /** Открыть окошко. Возвращает true, если открылось впервые. */
    public static boolean open(GameState game, int day) {
        if (day < 1 || day > DAYS) return false;
        int bit = 1 << (day - 1);
        if ((game.nyMask & bit) != 0) return false;
        game.nyMask |= bit;
        return true;
    }

    /** Сколько окошек уже открыто. */
    public static int openedCount(GameState game) {
        int n = 0;
        for (int i = 0; i < DAYS; i++) {
            if ((game.nyMask & (1 << i)) != 0) n++;
        }
        return n;
    }

    /** Открыто ли украшение хоть каким-то окошком. */
    public static boolean hasOrnament(GameState game, int ornament) {
        for (int day = 1; day <= DAYS; day++) {
            if (isOpen(game, day) && ornamentForDay(day) == ornament) return true;
        }
        return false;
    }

    /** Идентификатор ресурса украшения (ny_orn_01 … ny_orn_36). */
    public static int ornamentRes(Context ctx, int index) {
        String name = String.format("ny_orn_%02d", index + 1);
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    public static Drawable ornamentDrawable(Context ctx, int index) {
        int id = ornamentRes(ctx, index);
        return id == 0 ? null : ctx.getResources().getDrawable(id, ctx.getTheme());
    }

    public static int res(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    // ---- окно ивента: 1 декабря … 31 января ----

    public static final String PREF_MODE = "ny_mode";        // 0 по датам, 1 включён, 2 выключен
    public static final String PREF_FORCE_PROMO = "nyForcePromo";
    public static final String PREF_FORCE_EVENT = "nyForceEvent";

    public static int mode(Context ctx) {
        return Steps.prefs(ctx).getInt(PREF_MODE, 0);
    }

    public static void setMode(Context ctx, int mode) {
        Steps.prefs(ctx).edit().putInt(PREF_MODE, mode).apply();
    }

    /** Год «сезона»: декабрь текущего года (в январе — предыдущий). */
    public static int seasonYear() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int year = c.get(java.util.Calendar.YEAR);
        int month = c.get(java.util.Calendar.MONTH);   // 0..11
        if (month == java.util.Calendar.JANUARY) return year - 1;
        return year;
    }

    private static long at(int year, int month, int day, int hour, int minute, int second) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.YEAR, year);
        c.set(java.util.Calendar.MONTH, month);
        c.set(java.util.Calendar.DAY_OF_MONTH, day);
        c.set(java.util.Calendar.HOUR_OF_DAY, hour);
        c.set(java.util.Calendar.MINUTE, minute);
        c.set(java.util.Calendar.SECOND, second);
        c.set(java.util.Calendar.MILLISECOND, second == 59 ? 999 : 0);
        return c.getTimeInMillis();
    }

    /** Начало: 1 декабря 00:00:00. */
    public static long start() {
        return at(seasonYear(), java.util.Calendar.DECEMBER, 1, 0, 0, 0);
    }

    /** Конец: 12 января следующего года, 23:59:59.999. */
    public static long end() {
        return at(seasonYear() + 1, java.util.Calendar.JANUARY, 12, 23, 59, 59);
    }

    /** Идёт ли ивент сейчас. */
    public static boolean active(Context ctx) {
        if (Steps.prefs(ctx).getBoolean(PREF_FORCE_EVENT, false)) return true;
        int m = mode(ctx);
        if (m == 1) return true;
        if (m == 2) return false;
        long now = System.currentTimeMillis();
        return now >= start() && now <= end();
    }

    /** Сколько дней осталось до старта ивента (0 — уже начался). */
    public static long daysToStart() {
        long ms = start() - System.currentTimeMillis();
        if (ms <= 0) return 0;
        return (ms + 86399999L) / 86400000L;
    }

    /** Пора показывать афишу: за 20 дней до старта и до самого старта. */
    public static boolean inPromoWindow(Context ctx) {
        if (mode(ctx) == 2) return false;
        long now = System.currentTimeMillis();
        if (now >= start() || now > end()) return false;
        return start() - now <= 20L * 86400000L;
    }

    /** Надо ли показывать афишу (с учётом тестовой кнопки). */
    public static boolean promoActive(Context ctx) {
        if (active(ctx)) return false;
        if (Steps.prefs(ctx).getBoolean(PREF_FORCE_PROMO, false)) return true;
        return inPromoWindow(ctx);
    }

    /** Только число и слово: «26 дней» — плашка в афише уже подписана. */
    public static String promoCaption() {
        long d = daysToStart();
        if (d <= 0) return "уже идёт";
        long last = d % 10;
        long last2 = d % 100;
        String word;
        if (last2 >= 11 && last2 <= 14) word = "дней";
        else if (last == 1) word = "день";
        else if (last >= 2 && last <= 4) word = "дня";
        else word = "дней";
        return d + " " + word;
    }
}