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
    /** Сколько дней журнала храним: нужен запас на графики и теплокарту за 3 месяца. */
    static final int KEEP_DAYS = 95;
    /** Последняя дата, которую видело приложение (для защиты от скачков часов). */
    static final String KEY_LAST_DAY = "last_day";
    /** Текст последнего замеченного скачка времени. */
    static final String KEY_CLOCK_NOTE = "clock_note";
    /** Дата, за которую итоги уже закреплены в настройках. */
    static final String KEY_HIST_DAY = "hist_day";
    /** Префикс дневных итогов: hist_2026-10-29 = 17012 */
    static final String KEY_HIST = "hist_";

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

    /** Разница в днях между двумя датами yyyy-MM-dd (b − a). */
    static int daysBetween(String a, String b) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long ta = f.parse(a).getTime();
            long tb = f.parse(b).getTime();
            return (int) Math.round((tb - ta) / 86400000.0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Дата из числа вида 20261029 → строка 2026-10-29. */
    static String keyToDay(long dayKey) {
        if (dayKey <= 0) return null;
        String s = Long.toString(dayKey);
        if (s.length() != 8) return null;
        return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6);
    }

    /** Строка 2026-10-29 → число 20261029. */
    static long dayToKey(String day) {
        if (day == null || day.length() != 10) return 0;
        try {
            return Long.parseLong(day.substring(0, 4) + day.substring(5, 7) + day.substring(8, 10));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Смещение пояса в минутах на момент wall. */
    static int zoneOffset(long wall) {
        return java.util.TimeZone.getDefault().getOffset(wall) / 60000;
    }

    /** Сохранить дневные итоги в настройки: журнал может пострадать, история — нет. */
    static void persistDailyTotals(Context ctx) {
        LinkedHashMap<String, Integer> days = dailySteps(ctx);
        android.content.SharedPreferences.Editor e = prefs(ctx).edit();
        for (Map.Entry<String, Integer> en : days.entrySet()) {
            if (en.getKey().equals(today())) continue;   // сегодня ещё меняется
            e.putInt(KEY_HIST + en.getKey(), en.getValue());
        }
        e.apply();
    }

    /** Дни для графика: сохранённые итоги + то, что даёт журнал (журнал главнее). */
    static LinkedHashMap<String, Integer> mergedDays(Context ctx) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        android.content.SharedPreferences p = prefs(ctx);
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            String k = en.getKey();
            if (!k.startsWith(KEY_HIST)) continue;
            if (!(en.getValue() instanceof Integer)) continue;
            out.put(k.substring(KEY_HIST.length()), (Integer) en.getValue());
        }
        out.putAll(dailySteps(ctx));
        return out;
    }

    /** Последний замеченный скачок времени (пусто, если не было). */
    static String clockNote(Context ctx) {
        return prefs(ctx).getString(KEY_CLOCK_NOTE, "");
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
                if (line.startsWith("#")) continue;          // служебные пометки
                String[] p = line.split("\\|");
                if (p.length < 3) continue;
                try {
                    long wall = Long.parseLong(p[0]);
                    long elapsed = Long.parseLong(p[1]);
                    long raw = Math.round(Double.parseDouble(p[2]));
                    long dayKey = p.length >= 4 ? Long.parseLong(p[3]) : 0L;
                    long offset = p.length >= 5 ? Long.parseLong(p[4]) : 0L;
                    out.add(new long[]{wall, elapsed, raw, dayKey, offset});
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
        // Защита от скачка времени: если «сегодня» уехало больше чем на два дня,
        // чистку не делаем — иначе одним махом вырезается вся история (именно так
        // у пользователя с VPN получилось «вчера 19, сегодня 4»).
        String today = today();
        String lastDay = prefs(ctx).getString(KEY_LAST_DAY, "");
        boolean jumped = lastDay.length() > 0 && Math.abs(daysBetween(lastDay, today)) > 2;
        if (jumped) {
            prefs(ctx).edit()
                    .putString(KEY_CLOCK_NOTE, "скачок времени: " + lastDay + " → " + today)
                    .apply();
        }
        if (!lastDay.equals(today)) prefs(ctx).edit().putString(KEY_LAST_DAY, today).apply();

        StringBuilder sb = new StringBuilder();
        if (jumped) sb.append("# скачок времени ").append(lastDay).append(" -> ")
                .append(today).append('\n');
        long lastKept = 0;
        int lastStart = -1;
        for (long[] e : entries) {
            if (!jumped && e[0] < cutoff) continue;
            // прореживаем до одного замера в минуту. Слишком частые не выбрасываем,
            // а заменяем предыдущую запись свежей — так в журнале всегда самое
            // актуальное значение, но файл не пухнет на тысячи строк в сутки.
            if (lastKept != 0 && e[0] - lastKept < 60000L) {
                sb.setLength(lastStart);
            }
            lastStart = sb.length();
            sb.append(e[0]).append('|').append(e[1]).append('|').append(e[2]);
            if (e.length >= 5 && e[3] != 0) {
                // дата и смещение пояса на момент замера: день больше не
                // пересчитывается по текущему поясу, история не «переезжает»
                sb.append('|').append(e[3]).append('|').append(e[4]);
            }
            sb.append('\n');
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
            if (n.length < 5) {
                // дата и смещение пояса фиксируются здесь, при записи
                n = new long[]{n[0], n[1], n[2], dayToKey(dayOf(n[0])), zoneOffset(n[0])};
            }
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
            // День берём из записи (дата и пояс на момент замера). У старых строк
            // даты нет — тогда считаем по текущему поясу, как раньше.
            String dayA = a.length >= 5 ? keyToDay(a[3]) : null;
            String dayB = b.length >= 5 ? keyToDay(b[3]) : null;
            if (dayA == null) dayA = dayOf(t1);
            if (dayB == null) dayB = dayOf(t2);

            if (t2 <= t1 || dayA.equals(dayB)) {
                add(days, dayB, (int) delta);
                continue;
            }

            // Длинный разрыв (телефон спал, VPN держал сеть, замеров не было) —
            // не размазываем шаги по времени, относим целиком туда, где замер.
            if (t2 - t1 > 2 * 3600000L) {
                add(days, dayB, (int) delta);
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

    /**
     * Шаги за последние n дней: массив от самого старого дня к сегодняшнему.
     * Нужен и для графика на главном, и для теплокарты в статистике.
     */
    static int[] lastDays(Context ctx, int n) {
        if (n < 1) n = 1;
        LinkedHashMap<String, Integer> days = mergedDays(ctx);
        int[] out = new int[n];
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12);
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(n - 1));
        for (int i = 0; i < n; i++) {
            String key = fmt.format(cal.getTime());
            Integer v = days.get(key);
            out[i] = v == null ? 0 : v;
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }

    /**
     * Время в движении за сегодня.
     *
     * Считаем по журналу: между двумя точками берём прирост шагов и переводим его
     * в минуты по обычному темпу ходьбы (100 шагов в минуту), но не больше самого
     * промежутка. Раньше промежутки длиннее 10 минут выбрасывались целиком —
     * телефон синхронизируется раз в 15 минут, и время выходило смешным:
     * 10 000 шагов превращались в 14 минут.
     */
    static int activeMinutes(Context ctx) {
        List<long[]> list = load(ctx);
        if (list.size() < 2) return 0;
        String today = today();
        double minutes = 0;
        for (int i = 1; i < list.size(); i++) {
            long[] a = list.get(i - 1);
            long[] b = list.get(i);
            long dt = b[0] - a[0];
            if (dt <= 0) continue;
            double gapMin = dt / 60000.0;
            if (gapMin > 180) continue;          // телефон лежал полдня — не считаем
            String dayB = b.length >= 5 ? keyToDay(b[3]) : null;
            if (dayB == null) dayB = dayOf(b[0]);
            if (!dayB.equals(today)) continue;
            long delta = b[2] - a[2];
            if (delta < 0) delta = b[2];
            if (delta < 15) continue;            // пара шагов по квартире — не прогулка
            minutes += Math.min(delta / 100.0, gapMin);
        }
        return (int) Math.max(0, Math.min(24 * 60, Math.round(minutes)));
    }

    /**
     * Шаги по часам за конкретный день: массив из 24 чисел.
     *
     * Журнал хранит замеры примерно раз в 15 минут, поэтому прирост между двумя
     * замерами раскладываем по часам пропорционально времени — получается честная
     * картина «когда ходил».
     */
    static int[] hourly(Context ctx, int year, int month0, int day) {
        int[] out = new int[24];
        List<long[]> list = load(ctx);
        if (list.size() < 2) return out;

        String target = String.format(Locale.US, "%04d-%02d-%02d", year, month0 + 1, day);
        Calendar cal = Calendar.getInstance();
        for (int i = 1; i < list.size(); i++) {
            long[] a = list.get(i - 1);
            long[] b = list.get(i);
            long ta = a[0], tb = b[0];
            if (tb <= ta) continue;

            String dayB = keyToDay(b[3]);
            if (dayB == null) dayB = dayOf(tb);
            if (!target.equals(dayB)) continue;

            long delta = b[2] - a[2];
            if (delta < 0) delta = b[2];          // счётчик сбросился (перезагрузка)
            if (delta <= 0) continue;

            long span = tb - ta;
            if (span > 8 * 3600_000L) {
                // телефон лежал слишком долго — весь прирост отдаём часу замера
                cal.setTimeInMillis(tb);
                out[cal.get(Calendar.HOUR_OF_DAY)] += (int) delta;
                continue;
            }

            // раскладываем прирост по часам, которые накрывает промежуток
            long from = ta;
            while (from < tb) {
                cal.setTimeInMillis(from);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                long hourEnd = from + (3600_000L - (from % 3600_000L));
                long segEnd = Math.min(tb, hourEnd);
                double part = (segEnd - from) / (double) span;
                out[hour] += (int) Math.round(delta * part);
                from = segEnd;
            }
        }
        return out;
    }

    /** Шаги и минуты в движении за конкретный день. */
    static int[] dayStats(Context ctx, int year, int month0, int day) {
        String target = String.format(Locale.US, "%04d-%02d-%02d", year, month0 + 1, day);
        List<long[]> list = load(ctx);
        double minutes = 0;
        for (int i = 1; i < list.size(); i++) {
            long[] a = list.get(i - 1);
            long[] b = list.get(i);
            long dt = b[0] - a[0];
            if (dt <= 0) continue;
            double gapMin = dt / 60000.0;
            if (gapMin > 180) continue;
            String dayB = keyToDay(b[3]);
            if (dayB == null) dayB = dayOf(b[0]);
            if (!target.equals(dayB)) continue;
            long delta = b[2] - a[2];
            if (delta < 0) delta = b[2];
            if (delta < 15) continue;
            minutes += Math.min(delta / 100.0, gapMin);
        }
        int steps = 0;
        java.util.Map<String, Integer> all = mergedDays(ctx);
        Integer v = all.get(String.format(Locale.US, "%04d-%02d-%02d",
                year, month0 + 1, day));
        if (v != null) steps = v;
        return new int[]{steps, (int) Math.round(minutes)};
    }

    /** Часы, когда был пик шагов: индекс 0–23 и значение. */
    static int[] peakHour(int[] hours) {
        int best = 0, at = -1;
        for (int i = 0; i < hours.length; i++) {
            if (hours[i] > best) { best = hours[i]; at = i; }
        }
        return new int[]{at, best};
    }

    /**
     * Серия активности: дни подряд, когда были хоть какие-то шаги.
     * Сегодняшний день считается, только если шаги уже есть.
     */
    static int activityStreak(Context ctx) {
        int[] days = lastDays(ctx, KEEP_DAYS);
        int n = days.length;
        if (n == 0) return 0;
        int streak = 0;
        for (int i = n - 1; i >= 0; i--) {
            if (days[i] <= 0) {
                if (i == n - 1) continue;   // сегодня ещё можно начать
                break;
            }
            streak++;
        }
        return streak;
    }

    /**
     * Серия дней подряд, когда пройдена дневная норма шагов.
     * Сегодняшний день считается, только если норма уже набрана, — иначе
     * вечером серия выглядела бы обнулённой.
     */
    static int stepsStreak(Context ctx, int goal) {
        if (goal < 1) goal = GOAL;
        int[] days = lastDays(ctx, KEEP_DAYS);
        int n = days.length;
        if (n == 0) return 0;
        int streak = 0;
        // идём от сегодня назад
        for (int i = n - 1; i >= 0; i--) {
            boolean reached = days[i] >= goal;
            if (i == n - 1 && !reached) continue;   // сегодня ещё можно добить норму
            if (!reached) break;
            streak++;
        }
        return streak;
    }

    /** Длина шага в метрах: из роста (см), по умолчанию 170 см. */
    static double strideMeters(Context ctx) {
        int height = prefs(ctx).getInt("height_cm", 170);
        if (height < 120 || height > 220) height = 170;
        // 0,42 — тот же коэффициент, что у системного «Здоровья» Honor:
        // так наши километры не спорят с телефоном (разница 1,2 %)
        return height * 0.42 / 100.0;
    }

    /**
     * Минуты в движении для показа: из журнала, а если журнал редкий (приложение
     * не открывали) — грубая оценка по шагам, чтобы не показывать «0 мин» при
     * 6 000 пройденных шагов.
     * Возвращает минуты, а признак оценки — в estimated[0].
     */
    static int minutesShown(Context ctx, int steps, boolean[] estimated) {
        int real = activeMinutes(ctx);
        if (real > 0) {
            // время считается по шагам, поэтому это оценка — показываем «≈»
            if (estimated != null) estimated[0] = true;
            return real;
        }
        if (steps < 100) return 0;
        if (estimated != null) estimated[0] = true;
        return Math.max(1, steps / 100);
    }

    /** Вес для оценки калорий, кг (по умолчанию 70). */
    static int weightKg(Context ctx) {
        int w = prefs(ctx).getInt("weight_kg", 70);
        if (w < 30 || w > 200) w = 70;
        return w;
    }

    /** Показывать ли калории: по умолчанию выключено — это оценка, а не факт. */
    static boolean showCalories(Context ctx) {
        return prefs(ctx).getBoolean("show_calories", false);
    }

    /**
     * Примерный расход калорий: ~0,045 ккал на шаг при 70 кг, дальше по весу.
     * Формула грубая: не учитывает темп, рельеф, возраст — поэтому в приложении
     * всегда пишем «≈» и предупреждаем, что это оценка.
     */
    static int calories(Context ctx, int steps) {
        return Math.round(steps * 0.045f * weightKg(ctx) / 70f);
    }
    /** Пересчитать кэш «шаги сегодня» — его читают виджет и уведомление. */
    static int refreshToday(Context ctx) {        // при смене суток закрепляем итоги прошлых дней в настройках.
        // Отдельный ключ: last_day обновляется ещё и при сохранении журнала.
        String histDay = prefs(ctx).getString(KEY_HIST_DAY, "");
        if (!histDay.equals(today())) {
            persistDailyTotals(ctx);
            prefs(ctx).edit().putString(KEY_HIST_DAY, today()).apply();
        }

        LinkedHashMap<String, Integer> days = dailySteps(ctx);
        Integer today = days.get(today());
        int steps = today == null ? 0 : today;
        prefs(ctx).edit().putInt(KEY_STEPS, steps).putString(KEY_DAY, today()).apply();
        return steps;
    }
}
