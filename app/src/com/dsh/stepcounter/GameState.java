package com.dsh.stepcounter;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

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

/**
 * Состояние игры «Сад»: вода, очки, серия, грядки, задания.
 * Хранится в приватном JSON-файле, сервера нет.
 */
public class GameState {

    public static final int PLOTS = 9;
    public static final int STAGES = 10;  // 0 семя, 1..10 рост, 10 цветок
    public static final int SPECIES_COUNT = Species.COUNT;

    /** Версия правил игры. Растёт при перебалансировке — прогресс сбрасывается. */
    public static final int GAME_VERSION = 4;

    public int gameVersion = 0;
    public boolean wasReset = false;

    public int water = 0;
    public int points = 0;
    /** Сколько очков заработано за всё время: от этого считается уровень, покупки его не снижают. */
    public int earned = 0;
    public int goal = 10000;
    public int streak = 0;
    public int bestStreak = 0;
    public String lastWaterDay = "";
    public int wateredToday = 0;
    public int wateredTotal = 0;
    public int flowersToday = 0;
    public int flowersTotal = 0;
    public int weekStart = 0;
    public int weekFlowers = 0;

    public int questDay = 0;
    public int q1Target = 3000, q1Done = 0, q1Claimed = 0;
    public int q2Target = 2,    q2Done = 0, q2Claimed = 0;
    public int q3Done = 0,      q3Claimed = 0;

    // ---------------- сорняки, увядание, инструменты ----------------
    /** Сколько раз подряд не выполнялась норма. */
    public int neglect = 0;
    /** Сорняков на цветке: каждый требует одной тяпки. */
    public int weeds = 0;
    /** Цветок засох: ни полить, ни собрать, нужна лопата. */
    public boolean withered = false;
    /** Инвентарь: тяпки и лопаты. */
    public int hoes = 0;
    public int shovels = 0;
    /** Последний день, за который проверяли норму. */
    public String checkDay = "";

    public String nick = "";
    public int avatarStyle = 0;
    public int avatarPalette = 0;
    public int avatarFrame = 0;
    public final java.util.List<Integer> ownedFrames = new ArrayList<>();
    public String created = "";

    public int totalSteps = 0;
    public int bestDay = 0;
    public int stepsCache = 0;
    public String stepsCacheDay = "";
    public final java.util.List<String> unlocked = new ArrayList<>();
    public final java.util.List<Integer> bloomed = new ArrayList<>();
    public final java.util.List<Card> cards = new ArrayList<>();

    /** Открытка собранного цветка. */
    public static final class Card {
        public int seed, species;
        public String name, date, wish;
        public Card() { }
        public Card(int seed, int species, String name, String date, String wish) {
            this.seed = seed; this.species = species;
            this.name = name; this.date = date; this.wish = wish;
        }
    }

    public int[] stage = new int[PLOTS];
    public int[] species = new int[PLOTS];

    // сколько шагов уже конвертировано в воду
    public String waterDay = "";
    public int stepsAtWater = 0;
    public int waterProgress = 0;
    public int stepsMark = 0;

    private static final String FILE = "garden.json";

    /** Уровень считается от заработанного за всё время, а не от остатка. */
    public static int level(int earnedPoints) { return 1 + earnedPoints / 100; }
    public int level() { return level(earned); }
    public int levelProgress() { return earned % 100; }

    /** Начислить очки: баланс и «заработано за всё время» растут вместе. */
    public void earn(int n) {
        if (n <= 0) return;
        points += n;
        earned += n;
    }

    // ---------------- загрузка/сохранение ----------------

    public static GameState load(Context ctx) {
        GameState g = new GameState();
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) {
            g.stage[0] = 0;
            g.species[0] = 0;
            g.refreshDaily(Steps.today());
            return g;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            JSONObject o = new JSONObject(sb.toString());

            g.water = o.optInt("water", 0);
            g.points = o.optInt("points", 0);
            g.earned = o.optInt("earned", -1);
            g.neglect = o.optInt("neglect", 0);
            g.weeds = o.optInt("weeds", 0);
            g.withered = o.optBoolean("withered", false);
            g.hoes = o.optInt("hoes", 0);
            g.shovels = o.optInt("shovels", 0);
            g.checkDay = o.optString("checkDay", "");
            g.goal = o.optInt("goal", 10000);
            g.streak = o.optInt("streak", 0);
            g.bestStreak = o.optInt("bestStreak", 0);
            g.lastWaterDay = o.optString("lastWaterDay", "");
            g.wateredToday = o.optInt("wateredToday", 0);
            g.wateredTotal = o.optInt("wateredTotal", 0);
            g.gameVersion = o.optInt("gameVersion", 0);
            g.flowersToday = o.optInt("flowersToday", 0);
            g.flowersTotal = o.optInt("flowersTotal", 0);
            g.weekStart = o.optInt("weekStart", 0);
            g.weekFlowers = o.optInt("weekFlowers", 0);

            JSONObject q = o.optJSONObject("quests");
            if (q != null) {
                g.questDay = q.optInt("day", 0);
                g.q1Target = q.optInt("q1t", 3000);
                g.q1Done = q.optInt("q1d", 0);
                g.q1Claimed = q.optInt("q1c", 0);
                g.q2Target = q.optInt("q2t", 2);
                g.q2Done = q.optInt("q2d", 0);
                g.q2Claimed = q.optInt("q2c", 0);
                g.q3Done = q.optInt("q3d", 0);
                g.q3Claimed = q.optInt("q3c", 0);
            }

            JSONArray st = o.optJSONArray("stage");
            JSONArray sp = o.optJSONArray("species");
            for (int i = 0; i < PLOTS; i++) {
                g.stage[i] = st != null ? st.optInt(i, 0) : 0;
                g.species[i] = sp != null ? sp.optInt(i, i % 5) : i % 5;
            }

            g.waterDay = o.optString("waterDay", "");
            g.stepsAtWater = o.optInt("stepsAtWater", 0);
            g.waterProgress = o.optInt("waterProgress", -1);
            g.stepsMark = o.optInt("stepsMark", -1);
            g.nick = o.optString("nick", "");
            g.avatarStyle = o.optInt("avatarStyle", 0);
            g.avatarPalette = o.optInt("avatarPalette", 0);
            g.avatarFrame = o.optInt("avatarFrame", 0);
            JSONArray fr = o.optJSONArray("ownedFrames");
            if (fr != null) for (int i = 0; i < fr.length(); i++) g.ownedFrames.add(fr.optInt(i));

            // старое сохранение не знает про earned: восстанавливаем его как
            // остаток плюс то, что уже потрачено на купленные рамки
            if (g.earned < 0) {
                int spent = 0;
                for (int id : g.ownedFrames) {
                    if (id > 0 && id < Shop.FRAME_PRICES.length) spent += Shop.FRAME_PRICES[id];
                }
                g.earned = g.points + spent;
            }

            g.created = o.optString("created", "");
            g.totalSteps = o.optInt("totalSteps", 0);
            g.bestDay = o.optInt("bestDay", 0);
            g.stepsCache = o.optInt("stepsCache", 0);
            g.stepsCacheDay = o.optString("stepsCacheDay", "");
            g.gameVersion = o.optInt("gameVersion", 0);

            // Прогресс до лейки привязан к дню. Если кэш шагов остался от другого
            // дня, остаток «до лейки» устарел — иначе получалось «шагов сегодня 0,
            // а до лейки 165»: число тянулось со вчерашнего вечера.
            if (!g.stepsCacheDay.equals(Steps.today())) {
                g.waterProgress = 0;
                g.stepsMark = 0;
                g.waterDay = "";
                g.stepsCache = 0;
            }

            JSONArray cd = o.optJSONArray("cards");
            if (cd != null) {
                for (int i = 0; i < cd.length(); i++) {
                    org.json.JSONObject it = cd.optJSONObject(i);
                    if (it == null) continue;
                    g.cards.add(new Card(it.optInt("seed", i), it.optInt("species", 0),
                            it.optString("name", ""), it.optString("date", ""),
                            it.optString("wish", "")));
                }
            }

            JSONArray bl = o.optJSONArray("bloomed");
            if (bl != null) for (int i = 0; i < bl.length(); i++) g.bloomed.add(bl.optInt(i));

            JSONArray un = o.optJSONArray("unlocked");
            if (un != null) {
                for (int i = 0; i < un.length(); i++) g.unlocked.add(un.optString(i));
            }
        } catch (Exception ignored) {
            for (int i = 0; i < PLOTS; i++) { g.stage[i] = 0; g.species[i] = i % 5; }
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
        if (g.waterProgress < 0 || g.stepsMark < 0) {
            int cost0 = Math.max(1, g.stepCost());
            g.stepsMark = g.stepsCache;
            g.waterProgress = g.stepsCache % cost0;
        }

        if (g.gameVersion != GAME_VERSION) {
            g.resetProgress();
            g.wasReset = true;
            g.gameVersion = GAME_VERSION;
            g.save(ctx);
        }

        g.refreshDaily(Steps.today());
        return g;
    }

    /** Обнулить игровой прогресс, сохранив ник и аватар. */
    public void resetProgress() {
        water = 0;
        points = 0;
        earned = 0;
        streak = 0;
        bestStreak = 0;
        lastWaterDay = "";
        wateredToday = 0;
        wateredTotal = 0;
        flowersToday = 0;
        flowersTotal = 0;
        weekFlowers = 0;
        weekStart = 0;
        bloomed.clear();
        unlocked.clear();
        cards.clear();
        stage[0] = 0;
        species[0] = 0;
        totalSteps = 0;
        bestDay = 0;
        stepsCache = 0;
        stepsCacheDay = "";
        waterDay = "";
        stepsAtWater = 0;
        questDay = 0;
        q1Done = 0; q1Claimed = 0;
        q2Done = 0; q2Claimed = 0;
        q3Done = 0; q3Claimed = 0;
        waterProgress = 0;
        stepsMark = 0;
        questDay = 0;

        // полный сброс: стираем и купленное, и личное — иначе после сброса
        // остаются рамки за очки, старый ник и аватар
        ownedFrames.clear();
        avatarFrame = 0;
        avatarStyle = 0;
        avatarPalette = 0;
        nick = "";
        created = "";
        neglect = 0;
        weeds = 0;
        withered = false;
        hoes = 0;
        shovels = 0;
        checkDay = "";
    }

    /** Ник при первом запуске: «Садовник #2841». */
    public void ensureIdentity() {
        if (nick == null || nick.isEmpty()) {
            java.util.Random rnd = new java.util.Random();
            int n = 1000 + rnd.nextInt(9000);
            nick = "Садовник #" + n;
        }
        if (created == null || created.isEmpty()) {
            created = Steps.today();
        }
    }

    /** Сколько дней живёт сад. */
    public int daysInGarden() {
        int d = dayNumber(Steps.today()) - dayNumber(created);
        return Math.max(1, d + 1);
    }

    /** Купить окантовку за очки. Дорогие рамки ещё и требуют уровня. */    public boolean buyFrame(int id) {
        if (id <= 0 || id >= Shop.FRAME_PRICES.length) return false;
        if (ownedFrames.contains(id)) return false;
        if (level() < Shop.FRAME_LEVEL[id]) return false;
        int price = Shop.FRAME_PRICES[id];
        if (points < price) return false;
        points -= price;
        ownedFrames.add(id);
        avatarFrame = id;
        return true;
    }

    public void selectFrame(int id) {
        if (id == 0 || ownedFrames.contains(id)) avatarFrame = id;
    }

    /** Цель задания «пройти шагов» — 80% дневной нормы, кратно 100. */
    public static int questTarget(int goal) {
        int t = Math.round(goal * 0.8f / 100f) * 100;
        return Math.max(1000, Math.min(90000, t));
    }

    /** Смена нормы: цену лейки пересчитываем, воду задним числом не начисляем. */
    public void setGoal(int g) {
        int oldCost = stepCost();
        goal = Math.max(1000, Math.min(100000, g));
        int newCost = stepCost();
        q1Target = questTarget(goal);
        if (q1Done > q1Target) q1Done = q1Target;
        if (oldCost != newCost && waterProgress > 0) {
            waterProgress = (int) Math.round(waterProgress * (double) newCost / oldCost);
            if (waterProgress >= newCost) waterProgress = newCost - 1;
        }
    }

    /** Сколько шагов стоит одна лейка: норма делится на 10 стадий. */
    public int stepCost() {
        return Math.max(100, goal / 10);
    }

    /**
     * Звания по уровню. Первые пять — прежние, поэтому у существующих игроков
     * звание не меняется; новые ступени добавлены выше и растянуты под экономику
     * (уровень = 1 + заработано/100, при ≈343 очках в день).
     */
    private static final String[] RANKS = {
            "Новичок", "Садовник", "Знаток", "Мастер", "Хранитель сада",
            "Легенда сада", "Хозяин сада", "Хранитель времени", "Сердце сада",
            "Звёздный садовник", "Вечный цвет", "Сад-вселенная"
    };

    /** С какого уровня начинается звание. */
    private static final int[] RANK_LEVEL = {1, 3, 6, 11, 21, 36, 56, 81, 116, 161, 221, 301};

    public String rank() {
        int l = level();
        int idx = 0;
        for (int i = 0; i < RANK_LEVEL.length; i++) {
            if (l >= RANK_LEVEL[i]) idx = i;
        }
        return RANKS[idx];
    }

    /** Сколько очков осталось до следующего звания; 0 — это последнее. */
    public int pointsToNextRank() {
        int l = level();
        for (int i = 0; i < RANK_LEVEL.length; i++) {
            if (l < RANK_LEVEL[i]) {
                int need = (RANK_LEVEL[i] - 1) * 100 - earned;
                return Math.max(0, need);
            }
        }
        return 0;
    }

    /** Название следующего звания; пусто, если достигнуто последнее. */
    public String nextRank() {
        int l = level();
        for (int i = 0; i < RANK_LEVEL.length; i++) {
            if (l < RANK_LEVEL[i]) return RANKS[i];
        }
        return "";
    }

    public void save(Context ctx) {
        try {
            JSONObject o = new JSONObject();
            o.put("water", water);
            o.put("points", points);
            o.put("earned", earned);
            o.put("neglect", neglect);
            o.put("weeds", weeds);
            o.put("withered", withered);
            o.put("hoes", hoes);
            o.put("shovels", shovels);
            o.put("checkDay", checkDay);
            o.put("goal", goal);
            o.put("streak", streak);
            o.put("bestStreak", bestStreak);
            o.put("lastWaterDay", lastWaterDay);
            o.put("wateredToday", wateredToday);
            o.put("wateredTotal", wateredTotal);
            o.put("gameVersion", GAME_VERSION);
            o.put("flowersToday", flowersToday);
            o.put("flowersTotal", flowersTotal);
            o.put("weekStart", weekStart);
            o.put("weekFlowers", weekFlowers);
            o.put("waterDay", waterDay);
            o.put("stepsAtWater", stepsAtWater);
            o.put("waterProgress", waterProgress);
            o.put("stepsMark", stepsMark);
            o.put("nick", nick);
            o.put("avatarStyle", avatarStyle);
            o.put("avatarPalette", avatarPalette);
            o.put("avatarFrame", avatarFrame);
            o.put("ownedFrames", new JSONArray(ownedFrames));
            o.put("created", created);
            o.put("totalSteps", totalSteps);
            o.put("bestDay", bestDay);
            o.put("stepsCache", stepsCache);
            o.put("stepsCacheDay", stepsCacheDay);
            o.put("unlocked", new JSONArray(unlocked));
            o.put("bloomed", new JSONArray(bloomed));
            JSONArray cr = new JSONArray();
            for (Card c : cards) {
                JSONObject co = new JSONObject();
                try {
                    co.put("seed", c.seed);
                    co.put("species", c.species);
                    co.put("name", c.name);
                    co.put("date", c.date);
                    co.put("wish", c.wish);
                } catch (Exception ignored) { }
                cr.put(co);
            }
            o.put("cards", cr);

            JSONObject q = new JSONObject();
            q.put("day", questDay);
            q.put("q1t", q1Target); q.put("q1d", q1Done); q.put("q1c", q1Claimed);
            q.put("q2t", q2Target); q.put("q2d", q2Done); q.put("q2c", q2Claimed);
            q.put("q3d", q3Done); q.put("q3c", q3Claimed);
            o.put("quests", q);

            o.put("stage", new JSONArray(stage));
            o.put("species", new JSONArray(species));

            FileWriter w = new FileWriter(new File(ctx.getFilesDir(), FILE), false);
            w.write(o.toString());
            w.close();
        } catch (Exception ignored) { }
    }

    // ---------------- логика дня ----------------

    /** Новый день: сбрасываем дневные счётчики и генерируем задания. */
    public void refreshDaily(String today) {
        int dayNum = dayNumber(today);
        if (questDay != dayNum) {
            questDay = dayNum;
            q1Target = questTarget(goal);
            q1Done = 0; q1Claimed = 0;
            q2Done = 0; q2Claimed = 0;
            q3Done = 0; q3Claimed = 0;
            wateredToday = 0;
            flowersToday = 0;
        }
        int wk = weekStartOf(today);
        if (weekStart != wk) {
            weekStart = wk;
            weekFlowers = 0;
            q3Claimed = 0;   // недельное задание можно забрать снова
        }
        if (!today.equals(waterDay)) {
            waterDay = today;
            stepsAtWater = 0;
            // Прогресс до следующей лейки считается заново каждый день. Раньше
            // здесь менялся только waterDay, а waterProgress тянулся со вчера:
            // получалось «шагов сегодня 0, а до лейки 165».
            waterProgress = 0;
            stepsMark = 0;
        }
    }

    /** Номер понедельника текущей недели. */
    public static int weekStartOf(String day) {
        int d = dayNumber(day);
        java.util.Calendar c = java.util.Calendar.getInstance();
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK); // 1 = воскресенье
        int mondayIndex = (dow + 5) % 7;                  // понедельник = 0
        return d - mondayIndex;
    }

    public static int dayNumber(String day) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = f.parse(day);
            return (int) (d.getTime() / 86400000L);
        } catch (Exception e) {
            return (int) (System.currentTimeMillis() / 86400000L);
        }
    }

    /** Шаги → вода: каждые 1000 шагов дают лейку. */
    public boolean syncSteps(String today, int stepsToday) {
        refreshDaily(today);

        int wk = weekStartOf(today);
        if (weekStart != wk) {
            weekStart = wk;
            weekFlowers = 0;
            q3Claimed = 0;   // недельное задание можно забрать снова
        }

        int cost = Math.max(1, stepCost());

        if (!today.equals(waterDay)) {
            waterDay = today;
            stepsMark = 0;      // за сегодня ещё ничего не начислено
            waterProgress = 0;
        }

        // если остался «хвост» от старой версии — выравниваем по целым лейкам
        if (stepsMark % cost != 0) stepsMark = (stepsMark / cost) * cost;
        // stepsMark — сколько шагов сегодня уже превращено в лейки.
        // Остаток считаем от него, а не «копим», поэтому число всегда сходится
        // с шагами за сегодня и не тянется со вчерашнего дня.
        if (stepsToday < stepsMark) stepsMark = 0;   // счётчик обнулился
        int add = (stepsToday - stepsMark) / cost;
        if (add > 0) {
            water += add;
            stepsMark += add * cost;
        }
        waterProgress = Math.max(0, Math.min(cost - 1, stepsToday - stepsMark));

        if (today.equals(stepsCacheDay)) {
            if (stepsToday > stepsCache) totalSteps += stepsToday - stepsCache;
        }
        stepsCache = stepsToday;
        stepsCacheDay = today;
        if (stepsToday > bestDay) bestDay = stepsToday;
        if (stepsToday > q1Done) q1Done = Math.min(stepsToday, q1Target);
        questsClaimed();   // задание по шагам закрывается сразу, без полива
        return true;
    }

    /** Сколько шагов осталось до следующей лейки. */
    public int waterToNext() {
        int cost = Math.max(1, stepCost());
        return Math.max(0, cost - waterProgress);
    }

    // ---------------- сорняки, увядание, инструменты ----------------

    /** Цена инструментов: тяпка ≈ 2 дня нормы, лопата ≈ 3 дня (по 300 очков в день). */
    public static final int HOE_PRICE = 600;
    public static final int SHOVEL_PRICE = 900;
    /** С какого пропущенного дня подряд цветок засыхает. */
    static final int WITHER_AFTER = 3;
    /** После стольких дней отсутствия лопата выдаётся бесплатно, чтобы не запирать. */
    static final int FORGIVE_AFTER = 7;

    /** Сорняки и засыхание блокируют и полив, и сбор. */
    public boolean plotBlocked() { return withered || weeds > 0; }

    /** Срезать один сорняк тяпкой. */
    public boolean cutWeed() {
        if (weeds <= 0 || hoes <= 0) return false;
        hoes--;
        weeds--;
        if (weeds == 0) neglect = 0;
        return true;
    }

    /** Перекопать засохший цветок лопатой: тот же вид начинает расти из семени. */
    public boolean digUp() {
        if (!withered || shovels <= 0) return false;
        shovels--;
        withered = false;
        weeds = 0;
        neglect = 0;
        stage[0] = 0;
        return true;
    }

    public boolean buyHoe() {
        if (points < HOE_PRICE) return false;
        points -= HOE_PRICE;
        hoes++;
        return true;
    }

    public boolean buyShovel() {
        if (points < SHOVEL_PRICE) return false;
        points -= SHOVEL_PRICE;
        shovels++;
        return true;
    }

    /** Сдвинуть день на delta: "2026-10-26" + 1 = "2026-10-27". */
    static String shiftDay(String day, int delta) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = f.parse(day);
            Calendar c = Calendar.getInstance();
            if (d != null) c.setTime(d);
            c.add(Calendar.DAY_OF_YEAR, delta);
            return Steps.dayOf(c.getTimeInMillis());
        } catch (Exception e) {
            return day;
        }
    }

    /**
     * Проверить прошедшие дни. Норма выполнена — серия пропусков обнуляется;
     * нет — появляются сорняки (по одному на день, максимум два), а с третьего
     * дня подряд цветок засыхает. Возвращает true, если что-то изменилось.
     */
    public boolean checkMissedDays(Context ctx) {
        String today = Steps.today();
        if (checkDay.isEmpty()) {          // новое сохранение: прошлое не судим
            checkDay = shiftDay(today, -1);
            return false;
        }
        if (checkDay.equals(today)) return false;

        LinkedHashMap<String, Integer> days = Steps.dailySteps(ctx);
        boolean changed = false;
        String d = shiftDay(checkDay, 1);
        for (int guard = 0; guard < 400 && !d.equals(today); guard++) {
            Integer v = days.get(d);
            int steps = v == null ? 0 : v;
            if (steps >= goal) {
                if (neglect != 0) { neglect = 0; changed = true; }
            } else {
                neglect++;
                changed = true;
                if (!withered) {
                    if (neglect >= WITHER_AFTER) {
                        withered = true;
                        weeds = 0;
                        stage[0] = 0;      // цветок осыпался
                    } else {
                        weeds = Math.min(2, neglect);
                    }
                }
            }
            d = shiftDay(d, 1);
        }
        checkDay = shiftDay(today, -1);

        // прощение: вернулся после долгой паузы — лопата бесплатно
        if (withered && shovels == 0 && neglect >= FORGIVE_AFTER) {
            shovels = 1;
            changed = true;
        }
        return changed;
    }

    /** Тест-инструмент: сымитировать пропущенный день. */
    public void simulateMiss() {
        neglect++;
        if (!withered) {
            if (neglect >= WITHER_AFTER) { withered = true; weeds = 0; stage[0] = 0; }
            else weeds = Math.min(2, neglect);
        }
    }

    /**
     * Забрать выполненные задания: три дневных и недельное.
     * Суммы совпадают с тем, что написано на экране заданий.
     */
    public boolean questsClaimed() {
        int add = 0;
        if (q1Done >= q1Target && q1Claimed == 0) { q1Claimed = 1; add += 60; }
        if (flowersToday >= 1 && q2Claimed == 0) { q2Claimed = 1; add += 40; }
        if (weekFlowers >= 7 && q3Claimed == 0) { q3Claimed = 1; add += 300; }
        if (add > 0) { earn(add); return true; }
        return false;
    }

    /** Полить грядку. Возвращает true, если получилось. */
    /** Собрать выросший цветок в букет и посадить следующий. */
    public void collect() {
        if (plotBlocked()) return;         // сначала прополоть или перекопать
        int sp = species[0];
        if (!bloomed.contains(sp)) bloomed.add(sp);
        earn(100);
        flowersToday++;
        flowersTotal++;
        q2Done = flowersToday;
        if (weekStartOf(Steps.today()) != weekStart) {
            weekStart = weekStartOf(Steps.today());
            weekFlowers = 0;
            q3Claimed = 0;
        }
        weekFlowers++;
        cards.add(new Card(flowersTotal, sp, Species.name(sp), Steps.today(), Species.wish(sp)));

        stage[0] = 0;
        species[0] = nextSpecies();
        questsClaimed();   // задания по цветку и неделе закрываются сразу
    }

    /** Следующий вид: сначала те, которых ещё нет в букете. */
    public int nextSpecies() {
        java.util.Random rnd = new java.util.Random();
        if (bloomed.size() >= SPECIES_COUNT) return rnd.nextInt(SPECIES_COUNT);
        for (int guard = 0; guard < 400; guard++) {
            int id = rnd.nextInt(SPECIES_COUNT);
            if (!bloomed.contains(id)) return id;
        }
        return 0;
    }

    public boolean waterPlot(int index, String today) {
        if (index < 0 || index >= PLOTS) return false;
        if (plotBlocked()) return false;   // сорняки или засохший цветок
        if (water <= 0) return false;
        if (stage[index] >= STAGES) return false;

        water--;
        stage[index]++;
        earn(10);
        wateredTotal++;
        wateredToday++;

        if (!today.equals(lastWaterDay)) {
            int yesterday = dayNumber(today) - 1;
            streak = dayNumber(lastWaterDay) == yesterday ? streak + 1 : 1;
            lastWaterDay = today;
            if (streak > bestStreak) bestStreak = streak;
        }
        questsClaimed();
        return true;
    }

    /** Список открытых видов — по максимальной стадии. */
    public List<Integer> collection() {
        return new ArrayList<>(bloomed);
    }
}
