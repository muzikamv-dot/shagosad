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
import java.util.Collections;
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

    static synchronized void save(Context ctx, List<long[]> entries) {
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 86400000L;
        StringBuilder sb = new StringBuilder();
        long lastKept = 0;
        int lastStart = -1;
        for (long[] e : entries) {
            if (e[0] < cutoff) continue;
            // прореживаем до одного замера в минуту. Слишком частые не выбрасываем,
            // а заменяем предыдущую запись свежей — так в журнале всегда самое
            // актуальное значение, но файл не пухнет на тысячи строк в сутки.
            if (lastKept != 0 && e[0] - lastKept < 60000L) {
                sb.setLength(lastStart);
            }
            lastStart = sb.length();
            sb.append(e[0]).append('|').append(e[1]).append('|').append(e[2]).append('\n');
            lastKept = e[0];
        }
        writeAtomically(new File(ctx.getFilesDir(), FILE), sb.toString());
    }

    /**
     * Атомарная запись файла: пишем в «.tmp» рядом и переименовываем.
     *
     * Переименование в одной папке неделимо, поэтому файл всегда либо целиком
     * старый, либо целиком новый. Раньше файл сначала обнулялся (FileWriter с
     * append=false) и только потом наполнялся: если процесс убивали посередине,
     * оставался огрызок — часть журнала пропадала навсегда.
     */
    static boolean writeAtomically(File target, String text) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        FileWriter w = null;
        try {
            w = new FileWriter(tmp, false);
            w.write(text);
            w.flush();
            w.close();
            w = null;
            if (tmp.renameTo(target)) return true;
        } catch (Exception ignored) {
        } finally {
            try { if (w != null) w.close(); } catch (Exception ignored) { }
        }
        // переименование не прошло — пишем напрямую, лишь бы данные не потерялись
        w = null;
        try {
            w = new FileWriter(target, false);
            w.write(text);
            w.flush();
            w.close();
            w = null;
            tmp.delete();
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            try { if (w != null) w.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * Добавить замер.
     *
     * Принимает и СТАРЫЕ замеры из буфера датчика: именно они дают точные метки
     * времени для шагов, сделанных без приложения. Раньше такие замеры
     * отбрасывались, журнал оставался редким, и пачка шагов за несколько часов
     * делилась между днями пропорционально времени — из-за этого «сегодня»
     * выходило на тысячи меньше, чем в Google Fit.
     */
    static synchronized boolean add(Context ctx, long wall, long elapsed, long raw) {
        return addAll(ctx, Collections.singletonList(new long[]{wall, elapsed, raw})) > 0;
    }

    /** То же, но пачкой: буфер датчика может принести сотни замеров за один сеанс. */
    static synchronized int addAll(Context ctx, List<long[]> fresh) {
        if (fresh == null || fresh.isEmpty()) return 0;

        List<long[]> list = load(ctx);
        int saved = 0;
        long maxElapsed = prefs(ctx).getLong(KEY_LAST_ELAPSED, -1);

        for (long[] n : fresh) {
            if (n == null) continue;

            // повтор уже записанного замера — буфер может прислать его снова
            boolean dup = false;
            int from = Math.max(0, list.size() - 80);
            for (int i = from; i < list.size(); i++) {
                long[] e = list.get(i);
                if (Math.abs(e[1] - n[1]) < 1000000000L && Math.abs(e[0] - n[0]) < 2000L) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;

            // порядок — по «настенным» часам: только он не путается после
            // перезагрузки телефона, когда метка датчика начинается с нуля
            int pos = list.size();
            while (pos > 0 && list.get(pos - 1)[0] > n[0]) pos--;
            list.add(pos, n);
            if (n[1] > maxElapsed) maxElapsed = n[1];
            saved++;
        }

        if (saved > 0) {
            save(ctx, list);
            prefs(ctx).edit().putLong(KEY_LAST_ELAPSED, maxElapsed).apply();
        }
        return saved;
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
