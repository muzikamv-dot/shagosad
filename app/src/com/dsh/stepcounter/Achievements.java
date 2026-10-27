package com.dsh.stepcounter;

import java.util.ArrayList;
import java.util.List;

/** Каталог достижений и проверка условий. */
public final class Achievements {

    public enum Metric { LIFE_STEPS, DAY_STEPS, BEST_DAY, WATERED, FLOWERS, STREAK, LEVEL, SPECIES }

    public static final class Ach {
        public final String id, title, desc;
        public final int target;
        public final Metric metric;
        public final int color;

        Ach(String id, String title, String desc, int target, Metric metric, int color) {
            this.id = id; this.title = title; this.desc = desc;
            this.target = target; this.metric = metric; this.color = color;
        }
    }

    public static final List<Ach> ALL = new ArrayList<>();

    static {
        add("first_flower", "Первый цветок", "Вырасти первый цветок", 1, Metric.FLOWERS, 0xFF7FD1AE);
        add("flowers_5", "Пять букетов", "Вырасти 5 цветов", 5, Metric.FLOWERS, 0xFF34D399);
        add("flowers_25", "Оранжерея", "Вырасти 25 цветов", 25, Metric.FLOWERS, 0xFF34D399);
        add("flowers_100", "Сто цветов", "Вырасти 100 цветов", 100, Metric.FLOWERS, 0xFFF4D58D);

        add("day_10k", "Десять тысяч", "10 000 шагов за день", 10000, Metric.DAY_STEPS, 0xFFF4D58D);
        add("day_15k", "Пятнадцать тысяч", "15 000 шагов за день", 15000, Metric.DAY_STEPS, 0xFFF4D58D);
        add("day_20k", "Марафонец", "20 000 шагов за день", 20000, Metric.BEST_DAY, 0xFFF4D58D);

        add("streak_7", "Неделя без пропуска", "Серия 7 дней", 7, Metric.STREAK, 0xFF34D399);
        add("streak_14", "Две недели", "Серия 14 дней", 14, Metric.STREAK, 0xFF34D399);
        add("streak_30", "Месяц в саду", "Серия 30 дней", 30, Metric.STREAK, 0xFFF4D58D);
        add("streak_100", "Сто дней", "Серия 100 дней", 100, Metric.STREAK, 0xFFF4D58D);

        add("level_5", "Пятый уровень", "Достигни 5 уровня", 5, Metric.LEVEL, 0xFF34D399);
        add("level_10", "Десятый", "Достигни 10 уровня", 10, Metric.LEVEL, 0xFFF4D58D);
        add("level_25", "Хранитель", "Достигни 25 уровня", 25, Metric.LEVEL, 0xFFF4D58D);

        add("species_3", "Коллекционер", "Открой 3 вида", 3, Metric.SPECIES, 0xFF34D399);
        add("species_6", "Тридцать видов", "Открой 30 видов", 30, Metric.SPECIES, 0xFF34D399);
        add("species_12", "Полный гербарий", "Собери все 365 видов", 365, Metric.SPECIES, 0xFFF4D58D);

        add("life_100k", "Сто тысяч", "100 000 шагов всего", 100000, Metric.LIFE_STEPS, 0xFF34D399);
        add("life_500k", "Полмиллиона", "500 000 шагов всего", 500000, Metric.LIFE_STEPS, 0xFFF4D58D);
        add("life_1m", "Миллион", "1 000 000 шагов всего", 1000000, Metric.LIFE_STEPS, 0xFFF4D58D);

        // ---- добавлены в 10.14: растягиваем награды, примерно одна в неделю ----
        add("flowers_10", "10 цветов", "Вырастить 10 цветов", 10, Metric.FLOWERS, 0xFF34D399);
        add("flowers_15", "15 цветов", "Вырастить 15 цветов", 15, Metric.FLOWERS, 0xFFF4D58D);
        add("flowers_20", "20 цветов", "Вырастить 20 цветов", 20, Metric.FLOWERS, 0xFF7FD1AE);
        add("flowers_30", "30 цветов", "Вырастить 30 цветов", 30, Metric.FLOWERS, 0xFF34D399);
        add("flowers_40", "40 цветов", "Вырастить 40 цветов", 40, Metric.FLOWERS, 0xFFF4D58D);
        add("flowers_50", "50 цветов", "Вырастить 50 цветов", 50, Metric.FLOWERS, 0xFF7FD1AE);
        add("flowers_60", "60 цветов", "Вырастить 60 цветов", 60, Metric.FLOWERS, 0xFF34D399);
        add("flowers_75", "75 цветов", "Вырастить 75 цветов", 75, Metric.FLOWERS, 0xFFF4D58D);
        add("flowers_90", "90 цветов", "Вырастить 90 цветов", 90, Metric.FLOWERS, 0xFF7FD1AE);
        add("flowers_110", "110 цветов", "Вырастить 110 цветов", 110, Metric.FLOWERS, 0xFF34D399);
        add("flowers_130", "130 цветов", "Вырастить 130 цветов", 130, Metric.FLOWERS, 0xFFF4D58D);
        add("flowers_150", "150 цветов", "Вырастить 150 цветов", 150, Metric.FLOWERS, 0xFF7FD1AE);
        add("flowers_175", "175 цветов", "Вырастить 175 цветов", 175, Metric.FLOWERS, 0xFF34D399);
        add("flowers_200", "200 цветов", "Вырастить 200 цветов", 200, Metric.FLOWERS, 0xFFF4D58D);
        add("flowers_225", "225 цветов", "Вырастить 225 цветов", 225, Metric.FLOWERS, 0xFF7FD1AE);
        add("flowers_250", "250 цветов", "Вырастить 250 цветов", 250, Metric.FLOWERS, 0xFF34D399);
        add("flowers_275", "275 цветов", "Вырастить 275 цветов", 275, Metric.FLOWERS, 0xFFF4D58D);
        add("flowers_300", "300 цветов", "Вырастить 300 цветов", 300, Metric.FLOWERS, 0xFF7FD1AE);
        add("flowers_325", "325 цветов", "Вырастить 325 цветов", 325, Metric.FLOWERS, 0xFF34D399);
        add("flowers_365", "365 цветов", "Вырастить 365 цветов", 365, Metric.FLOWERS, 0xFFF4D58D);
        add("streak_3", "Серия 3 дней", "Серия из 3 дней подряд", 3, Metric.STREAK, 0xFF7FD1AE);
        add("streak_5", "Серия 5 дней", "Серия из 5 дней подряд", 5, Metric.STREAK, 0xFF34D399);
        add("streak_10", "Серия 10 дней", "Серия из 10 дней подряд", 10, Metric.STREAK, 0xFFF4D58D);
        add("streak_21", "Серия 21 дней", "Серия из 21 дней подряд", 21, Metric.STREAK, 0xFF7FD1AE);
        add("streak_45", "Серия 45 дней", "Серия из 45 дней подряд", 45, Metric.STREAK, 0xFF34D399);
        add("streak_60", "Серия 60 дней", "Серия из 60 дней подряд", 60, Metric.STREAK, 0xFFF4D58D);
        add("streak_90", "Серия 90 дней", "Серия из 90 дней подряд", 90, Metric.STREAK, 0xFF7FD1AE);
        add("streak_120", "Серия 120 дней", "Серия из 120 дней подряд", 120, Metric.STREAK, 0xFF34D399);
        add("streak_150", "Серия 150 дней", "Серия из 150 дней подряд", 150, Metric.STREAK, 0xFFF4D58D);
        add("streak_200", "Серия 200 дней", "Серия из 200 дней подряд", 200, Metric.STREAK, 0xFF7FD1AE);
        add("streak_250", "Серия 250 дней", "Серия из 250 дней подряд", 250, Metric.STREAK, 0xFF34D399);
        add("streak_300", "Серия 300 дней", "Серия из 300 дней подряд", 300, Metric.STREAK, 0xFFF4D58D);
        add("streak_365", "Серия 365 дней", "Серия из 365 дней подряд", 365, Metric.STREAK, 0xFF7FD1AE);
        add("level_3", "3-й уровень", "Достигни 3 уровня", 3, Metric.LEVEL, 0xFF34D399);
        add("level_8", "8-й уровень", "Достигни 8 уровня", 8, Metric.LEVEL, 0xFFF4D58D);
        add("level_12", "12-й уровень", "Достигни 12 уровня", 12, Metric.LEVEL, 0xFF7FD1AE);
        add("level_15", "15-й уровень", "Достигни 15 уровня", 15, Metric.LEVEL, 0xFF34D399);
        add("level_20", "20-й уровень", "Достигни 20 уровня", 20, Metric.LEVEL, 0xFFF4D58D);
        add("level_30", "30-й уровень", "Достигни 30 уровня", 30, Metric.LEVEL, 0xFF7FD1AE);
        add("level_35", "35-й уровень", "Достигни 35 уровня", 35, Metric.LEVEL, 0xFF34D399);
        add("level_40", "40-й уровень", "Достигни 40 уровня", 40, Metric.LEVEL, 0xFFF4D58D);
        add("level_50", "50-й уровень", "Достигни 50 уровня", 50, Metric.LEVEL, 0xFF7FD1AE);
        add("level_60", "60-й уровень", "Достигни 60 уровня", 60, Metric.LEVEL, 0xFF34D399);
        add("level_75", "75-й уровень", "Достигни 75 уровня", 75, Metric.LEVEL, 0xFFF4D58D);
        add("level_90", "90-й уровень", "Достигни 90 уровня", 90, Metric.LEVEL, 0xFF7FD1AE);
        add("level_110", "110-й уровень", "Достигни 110 уровня", 110, Metric.LEVEL, 0xFF34D399);
        add("level_150", "150-й уровень", "Достигни 150 уровня", 150, Metric.LEVEL, 0xFFF4D58D);
        add("level_200", "200-й уровень", "Достигни 200 уровня", 200, Metric.LEVEL, 0xFF7FD1AE);
        add("level_250", "250-й уровень", "Достигни 250 уровня", 250, Metric.LEVEL, 0xFF34D399);
        add("level_300", "300-й уровень", "Достигни 300 уровня", 300, Metric.LEVEL, 0xFFF4D58D);
        add("life_10k", "Шагов: 10 000", "10 000 шагов всего", 10000, Metric.LIFE_STEPS, 0xFF7FD1AE);
        add("life_25k", "Шагов: 25 000", "25 000 шагов всего", 25000, Metric.LIFE_STEPS, 0xFF34D399);
        add("life_50k", "Шагов: 50 000", "50 000 шагов всего", 50000, Metric.LIFE_STEPS, 0xFFF4D58D);
        add("life_200k", "Шагов: 200 000", "200 000 шагов всего", 200000, Metric.LIFE_STEPS, 0xFF7FD1AE);
        add("life_350k", "Шагов: 350 000", "350 000 шагов всего", 350000, Metric.LIFE_STEPS, 0xFF34D399);
        add("life_750k", "Шагов: 750 000", "750 000 шагов всего", 750000, Metric.LIFE_STEPS, 0xFFF4D58D);
        add("life_1500k", "Шагов: 1 500 000", "1 500 000 шагов всего", 1500000, Metric.LIFE_STEPS, 0xFF7FD1AE);
        add("life_2m", "Шагов: 2 000 000", "2 000 000 шагов всего", 2000000, Metric.LIFE_STEPS, 0xFF34D399);
        add("life_3m", "Шагов: 3 000 000", "3 000 000 шагов всего", 3000000, Metric.LIFE_STEPS, 0xFFF4D58D);
        add("life_5m", "Шагов: 5 000 000", "5 000 000 шагов всего", 5000000, Metric.LIFE_STEPS, 0xFF7FD1AE);
        add("species_10", "10 видов", "Открыть 10 видов", 10, Metric.SPECIES, 0xFF34D399);
        add("species_20", "20 видов", "Открыть 20 видов", 20, Metric.SPECIES, 0xFFF4D58D);
        add("species_50", "50 видов", "Открыть 50 видов", 50, Metric.SPECIES, 0xFF7FD1AE);
        add("species_75", "75 видов", "Открыть 75 видов", 75, Metric.SPECIES, 0xFF34D399);
        add("species_100", "100 видов", "Открыть 100 видов", 100, Metric.SPECIES, 0xFFF4D58D);
        add("species_150", "150 видов", "Открыть 150 видов", 150, Metric.SPECIES, 0xFF7FD1AE);
        add("species_200", "200 видов", "Открыть 200 видов", 200, Metric.SPECIES, 0xFF34D399);
        add("species_250", "250 видов", "Открыть 250 видов", 250, Metric.SPECIES, 0xFFF4D58D);
        add("species_300", "300 видов", "Открыть 300 видов", 300, Metric.SPECIES, 0xFF7FD1AE);
        add("species_365", "365 видов", "Открыть 365 видов", 365, Metric.SPECIES, 0xFF34D399);
        add("watered_25", "25 поливов", "Полить растение 25 раз", 25, Metric.WATERED, 0xFFF4D58D);
        add("watered_50", "50 поливов", "Полить растение 50 раз", 50, Metric.WATERED, 0xFF7FD1AE);
        add("watered_100", "100 поливов", "Полить растение 100 раз", 100, Metric.WATERED, 0xFF34D399);
        add("watered_200", "200 поливов", "Полить растение 200 раз", 200, Metric.WATERED, 0xFFF4D58D);
        add("watered_500", "500 поливов", "Полить растение 500 раз", 500, Metric.WATERED, 0xFF7FD1AE);
        add("watered_1000", "1000 поливов", "Полить растение 1000 раз", 1000, Metric.WATERED, 0xFF34D399);
        add("best_25k", "День на 25 000", "25 000 шагов за день", 25000, Metric.BEST_DAY, 0xFFF4D58D);
        add("best_30k", "День на 30 000", "30 000 шагов за день", 30000, Metric.BEST_DAY, 0xFF7FD1AE);
        add("best_40k", "День на 40 000", "40 000 шагов за день", 40000, Metric.BEST_DAY, 0xFF34D399);
    }

    private static void add(String id, String title, String desc, int target, Metric m, int color) {
        ALL.add(new Ach(id, title, desc, target, m, color));
    }

    private Achievements() { }

    public static int progress(Ach a, GameState g, int daySteps) {
        switch (a.metric) {
            case LIFE_STEPS: return g.totalSteps;
            case DAY_STEPS: return daySteps;
            case BEST_DAY: return g.bestDay;
            case WATERED: return g.wateredTotal;
            case FLOWERS: return g.flowersTotal;
            case STREAK: return g.streak;
            case LEVEL: return g.level();
            case SPECIES: return g.collection().size();
            default: return 0;
        }
    }

    /** Проверить все условия, вернуть список только что открытых. */
    public static List<Ach> checkAndUnlock(GameState g, int daySteps) {
        List<Ach> fresh = new ArrayList<>();
        for (Ach a : ALL) {
            if (g.unlocked.contains(a.id)) continue;
            if (progress(a, g, daySteps) >= a.target) {
                g.unlocked.add(a.id);
                g.earn(60);
                fresh.add(a);
            }
        }
        return fresh;
    }

    // ---------------- показ наград ----------------

    public static Ach byId(String id) {
        if (id == null) return null;
        for (Ach a : ALL) if (a.id.equals(id)) return a;
        return null;
    }

    /** Какие достижения уже отпразднованы: хранится набором id в настройках. */
    public static java.util.Set<String> shown(android.content.Context ctx) {
        java.util.Set<String> base = Steps.prefs(ctx)
                .getStringSet(PREF_SHOWN, java.util.Collections.<String>emptySet());
        return new java.util.HashSet<>(base);
    }

    public static void markShown(android.content.Context ctx, String id) {
        java.util.Set<String> s = shown(ctx);
        if (!s.add(id)) return;
        Steps.prefs(ctx).edit().putStringSet(PREF_SHOWN, s).apply();
    }

    /**
     * Достижения, которые получены, но ещё не показывались полноэкранно.
     * После обновления, где появился показ наград, сюда попадут все ранее
     * полученные медали — они и проиграются по очереди.
     */
    public static List<Ach> unshown(GameState g, android.content.Context ctx) {
        java.util.Set<String> done = shown(ctx);
        List<Ach> out = new ArrayList<>();
        for (Ach a : ALL) {
            if (g.unlocked.contains(a.id) && !done.contains(a.id)) out.add(a);
        }
        return out;
    }

    static final String PREF_SHOWN = "ach_shown";
}
