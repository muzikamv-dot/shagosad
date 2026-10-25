package com.dsh.stepcounter;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Хранилище и вся арифметика шагов.
 *
 * Журнал — обычный файл, строки "wallMillis|elapsedNanos|raw".
 * wallMillis  — время замера по часам телефона;
 * elapsedNanos — метка датчика (не сбрасывается при сне процессора);
 * raw         — накопленное значение счётчика.
 *
 * Шаги за день считаются не «одним числом», а разбором интервалов журнала:
 * поэтому день закрывается точно, даже если приложение открывали неделю назад.
 */
final class Steps {

    static final String PREFS = "steps";
    static final String KEY_STEPS = "steps_today";
    static final String KEY_DAY = "day";
    static final String KEY_GOAL = "goal";
    static final String KEY_GOAL_DAY = "goal_day";
    static final String KEY_SERVICE = "service_enabled";
    static final String KEY_LAST_ELAPSED = "last_elapsed";

    static final int GOAL = 10000;
    static final int KEEP_DAYS = 21;

    private static final String FILE = "journal.txt";

    static volatile boolean isServiceRunning = false;

    private Steps() { }

    static String today() { return dayOf(System.currentTimeMillis()); }

    static String dayOf(long wallMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(wallMillis));
    }

    static boolean hasSensor(Context ctx) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- журнал ----------------

    static List<long[]> load(Context ctx) {
        List<long[]> out = new ArrayList<>();
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return out;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length != 3) continue;
                try {
                    out.add(new long[]{Long.parseLong(p[0]), Long.parseLong(p[1]),
                            Math.round(Double.parseDouble(p[2]))});
                } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) {
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
        return out;
    }

    /** Полностью стереть журнал: при сбросе прогресса график не должен оставаться. */
    static void clearJournal(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (f.exists()) f.delete();
        // метка датчика обнуляется: следующее чтение станет новой точкой отсчёта
        prefs(ctx).edit()
                .putLong(KEY_LAST_ELAPSED, 0)
                .putInt(KEY_STEPS, 0)
                .putString(KEY_DAY, today())
                .apply();
    }

    static void save(Context ctx, List<long[]> entries) {
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 86400000L;
        File f = new File(ctx.getFilesDir(), FILE);
        FileWriter w = null;
        try {
            w = new FileWriter(f, false);
            for (long[] e : entries) {
                if (e[0] < cutoff) continue;
                w.write(e[0] + "|" + e[1] + "|" + e[2] + "\n");
            }
        } catch (Exception ignored) {
        } finally {
            try { if (w != null) w.close(); } catch (Exception ignored) { }
        }
    }

    /** Добавить замер, если он новее последнего сохранённого. */
    static boolean add(Context ctx, long wall, long elapsed, long raw) {
        List<long[]> list = load(ctx);
        long lastElapsed = -1;
        if (!list.isEmpty()) {
            lastElapsed = list.get(list.size() - 1)[1];
        }
        SharedPreferences p = prefs(ctx);
        lastElapsed = Math.max(lastElapsed, p.getLong(KEY_LAST_ELAPSED, -1));

        // после перезагрузки метки датчика начинаются заново — сбрасываем «последнюю»
        if (lastElapsed >= 0 && elapsed + 1000L < lastElapsed) {
            lastElapsed = -1;
            p.edit().putLong(KEY_LAST_ELAPSED, 0).apply();
        }

        if (elapsed <= lastElapsed) return false;

        list.add(new long[]{wall, elapsed, raw});
        save(ctx, list);
        p.edit().putLong(KEY_LAST_ELAPSED, elapsed).apply();
        return true;
    }

    // ---------------- арифметика по дням ----------------

    /** Разложить журнал по дням. Ключи отсортированы по дате. */
    static LinkedHashMap<String, Integer> dailySteps(Context ctx) {
        LinkedHashMap<String, Integer> days = new LinkedHashMap<>();
        List<long[]> list = load(ctx);
        if (list.size() < 2) return days;

        for (int i = 1; i < list.size(); i++) {
            long[] a = list.get(i - 1);
            long[] b = list.get(i);

            long delta = b[2] - a[2];
            if (delta < 0) {
                // перезагрузка: счётчик начался с нуля
                delta = b[2];
            }
            if (delta <= 0) continue;

            long t1 = a[0], t2 = b[0];
            if (t2 <= t1 || dayOf(t1).equals(dayOf(t2))) {
                add(days, dayOf(t2), (int) delta);
                continue;
            }

            long total = t2 - t1;
            long cur = t1;
            while (cur < t2) {
                long endOfDay = endOfDay(cur);
                long segEnd = Math.min(endOfDay, t2);
                double share = (segEnd - cur) / (double) total;
                add(days, dayOf(cur), (int) Math.round(delta * share));
                cur = segEnd;
            }
        }
        return days;
    }

    private static void add(Map<String, Integer> map, String day, int v) {
        Integer prev = map.get(day);
        map.put(day, (prev == null ? 0 : prev) + v);
    }

    static long endOfDay(long wall) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(wall);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    /** Пересчитать кэш «шаги сегодня» — его читают виджет и уведомление. */
    static int refreshToday(Context ctx) {
        LinkedHashMap<String, Integer> days = dailySteps(ctx);
        Integer today = days.get(today());
        int steps = today == null ? 0 : today;
        prefs(ctx).edit().putInt(KEY_STEPS, steps).putString(KEY_DAY, today()).apply();
        return steps;
    }
}
