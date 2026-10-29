package com.dsh.stepcounter;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Праздничный ивент «День народного единства»: 4–8 ноября включительно.
 *
 * Дата берётся по часам телефона. В меню разработчика есть три режима проверки:
 * по датам, «идёт сейчас» и «закончился» — чтобы увидеть все состояния без ожидания.
 */
public final class Event {

    static final String ID = "unity2026";
    static final String NAME = "День народного единства";
    static final String WHEN = "4–8 ноября";
    static final int STAGES = 20;

    /** Режим теста: 0 — по датам, 1 — принудительно идёт, 2 — принудительно закончился. */
    private static final String PREF_MODE = "event_mode";
    /** Старый булев ключ, переносим при первом чтении. */
    private static final String PREF_MODE_OLD = "event_force";

    private Event() { }

    /** Текущий режим (0 по датам, 1 идёт, 2 закончился). */
    static int mode(Context ctx) {
        SharedPreferences p = Steps.prefs(ctx);

        // Разовая очистка: тестовые режимы включались из меню разработчика, которое
        // теперь убрано. Если на телефоне остался «идёт»/«закончился» — сбрасываем
        // на даты, чтобы ивент не горел раньше времени.
        if (!p.getBoolean("event_mode_cleared", false)) {
            p.edit().remove(PREF_MODE_OLD)
                    .putInt(PREF_MODE, 0)
                    .putBoolean("event_mode_cleared", true)
                    .apply();
            return 0;
        }

        return p.getInt(PREF_MODE, 0);
    }

    static void setMode(Context ctx, int m) {
        Steps.prefs(ctx).edit().putInt(PREF_MODE, m).apply();
    }

    /** Ивент включён вручную. */
    static boolean forced(Context ctx) {
        return mode(ctx) == 1;
    }

    private static long at(int day, int hour, int minute, int second, int millis) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, 2026);
        c.set(Calendar.MONTH, Calendar.NOVEMBER);
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, second);
        c.set(Calendar.MILLISECOND, millis);
        return c.getTimeInMillis();
    }

    /** Начало: 4 ноября 00:00:00.000 по часам телефона. */
    static long start() { return at(4, 0, 0, 0, 0); }

    /** Конец: 8 ноября 23:59:59.999 по часам телефона. */
    static long end() { return at(8, 23, 59, 59, 999); }

    /** Ивент идёт прямо сейчас. */
    static boolean active(Context ctx) {
        int m = mode(ctx);
        if (m == 1) return true;
        if (m == 2) return false;
        long now = System.currentTimeMillis();
        return now >= start() && now <= end();
    }

    /** Ивент уже закончился: показываем заглушку «новые ивенты скоро». */
    static boolean finished(Context ctx) {
        int m = mode(ctx);
        if (m == 2) return true;
        if (m == 1) return false;
        return System.currentTimeMillis() > end();
    }

    /** До ивента ещё далеко: показываем анонс. */
    static boolean upcoming(Context ctx) {
        return mode(ctx) == 0 && System.currentTimeMillis() < start();
    }
}
