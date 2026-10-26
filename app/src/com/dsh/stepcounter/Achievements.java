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
}
