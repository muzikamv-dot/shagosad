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

    public static int level(int points) { return 1 + points / 100; }
    public int level() { return level(points); }
    public int levelProgress() { return points % 100; }

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
            g.created = o.optString("created", "");
            g.totalSteps = o.optInt("totalSteps", 0);
            g.bestDay = o.optInt("bestDay", 0);
            g.stepsCache = o.optInt("stepsCache", 0);
            g.stepsCacheDay = o.optString("stepsCacheDay", "");
            g.gameVersion = o.optInt("gameVersion", 0);

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

    /** Купить окантовку за очки. */
    public boolean buyFrame(int id) {
        if (id <= 0 || id >= Shop.FRAME_PRICES.length) return false;
        if (ownedFrames.contains(id)) return false;
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

    public String rank() {
        int l = level();
        if (l < 3) return "Новичок";
        if (l < 6) return "Садовник";
        if (l < 11) return "Знаток";
        if (l < 21) return "Мастер";
        return "Хранитель сада";
    }

    public void save(Context ctx) {
        try {
            JSONObject o = new JSONObject();
            o.put("water", water);
            o.put("points", points);
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
        }
        if (!today.equals(waterDay)) {
            waterDay = today;
            stepsAtWater = 0;
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
        }

        int cost = Math.max(1, stepCost());

        if (!today.equals(waterDay)) {
            waterDay = today;
            stepsMark = stepsToday;
            waterProgress = 0;
        }

        int delta = stepsToday - stepsMark;
        if (delta < 0) delta = stepsToday;
        stepsMark = stepsToday;

        if (delta > 0) {
            waterProgress += delta;
            while (waterProgress >= cost) {
                waterProgress -= cost;
                water++;
            }
        }

        if (today.equals(stepsCacheDay)) {
            if (stepsToday > stepsCache) totalSteps += stepsToday - stepsCache;
        }
        stepsCache = stepsToday;
        stepsCacheDay = today;
        if (stepsToday > bestDay) bestDay = stepsToday;
        if (stepsToday > q1Done) q1Done = Math.min(stepsToday, q1Target);
        return true;
    }

    /** Сколько шагов осталось до следующей лейки. */
    public int waterToNext() {
        int cost = Math.max(1, stepCost());
        return Math.max(0, cost - waterProgress);
    }

    public boolean questsClaimed() {
        int add = 0;
        if (q1Done >= q1Target && q1Claimed == 0) { q1Claimed = 1; add += 1; }
        if (q2Done >= q2Target && q2Claimed == 0) { q2Claimed = 1; add += 1; }
        if (q3Done >= 1 && q3Claimed == 0) { q3Claimed = 1; add += 1; }
        if (add > 0) { points += add * 40; return true; }
        return false;
    }

    /** Полить грядку. Возвращает true, если получилось. */
    /** Собрать выросший цветок в букет и посадить следующий. */
    public void collect() {
        int sp = species[0];
        if (!bloomed.contains(sp)) bloomed.add(sp);
        points += 100;
        flowersToday++;
        flowersTotal++;
        if (weekStartOf(Steps.today()) != weekStart) {
            weekStart = weekStartOf(Steps.today());
            weekFlowers = 0;
        }
        weekFlowers++;
        cards.add(new Card(flowersTotal, sp, Species.name(sp), Steps.today(), Species.wish(sp)));

        stage[0] = 0;
        species[0] = nextSpecies();
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
        if (water <= 0) return false;
        if (stage[index] >= STAGES) return false;

        water--;
        stage[index]++;
        points += 10;
        wateredTotal++;
        wateredToday++;
        q2Done = wateredToday;

        if (!today.equals(lastWaterDay)) {
            int yesterday = dayNumber(today) - 1;
            streak = dayNumber(lastWaterDay) == yesterday ? streak + 1 : 1;
            lastWaterDay = today;
            if (streak > bestStreak) bestStreak = streak;
        }
        q3Done = 1;
        questsClaimed();
        return true;
    }

    /** Список открытых видов — по максимальной стадии. */
    public List<Integer> collection() {
        return new ArrayList<>(bloomed);
    }
}
