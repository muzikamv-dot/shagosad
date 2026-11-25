package com.dsh.stepcounter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

    /**
     * Купленные редкие семечки ждут посадки в инвентаре: номер вида → сколько штук.
     * В сад сами не сажаются — человек сам решает, когда освободится грядка.
     */
    public final java.util.LinkedHashMap<Integer, Integer> rareSeeds = new java.util.LinkedHashMap<>();

    /**
     * Какие редкие семечки уже покупались: в магазине они больше не показываются.
     * Каждый редкий цветок даёт одну открытку, поэтому и семечко продаётся один раз.
     */
    public final java.util.LinkedHashSet<Integer> rareBought = new java.util.LinkedHashSet<>();

    /** Пустая грядка: можно посадить редкое семечко из инвентаря. */
    public boolean plotEmpty() {
        return !withered && weeds == 0 && stage[0] == 0;
    }

    /**
     * Купить редкое семечко: оно уходит в инвентарь и ждёт посадки.
     * Раньше семечко клали в невидимую очередь (`rareQueue`) и сажали сами —
     * из-за этого в магазине карточка оставалась, а в инвентаре семечка не было.
     */
    public boolean buyRareSeed(int speciesId) {
        if (!Rare.isRare(speciesId)) return false;
        rareBought.add(speciesId);
        rareSeeds.put(speciesId, rareSeedCount(speciesId) + 1);
        return true;
    }

    /** Сколько таких семечек лежит в инвентаре. */
    public int rareSeedCount(int speciesId) {
        Integer n = rareSeeds.get(speciesId);
        return n == null ? 0 : n.intValue();
    }

    /** Всего редких семечек в инвентаре. */
    public int rareSeedsTotal() {
        int sum = 0;
        for (Integer n : rareSeeds.values()) if (n != null) sum += n.intValue();
        return sum;
    }

    /** Продавалось ли это семечко раньше: если да, в магазине его больше нет. */
    public boolean rareSold(int speciesId) {
        return rareBought.contains(speciesId);
    }

    /**
     * Посадить редкое семечко из инвентаря. Возвращает true, если получилось:
     * грядка должна быть свободной, а семечко — лежать в инвентаре.
     */
    public boolean plantRareSeed(int speciesId) {
        if (!Rare.isRare(speciesId) || !plotEmpty() || rareSeedCount(speciesId) <= 0) return false;
        int left = rareSeedCount(speciesId) - 1;
        if (left <= 0) rareSeeds.remove(speciesId);
        else rareSeeds.put(speciesId, left);
        return plantRare(speciesId);
    }

    /**
     * Посадить редкий цветок на грядку. Возвращает true, если получилось:
     * грядка должна быть свободной, иначе прогресс обычного цветка потерялся бы.
     */
    public boolean plantRare(int speciesId) {
        if (!Rare.isRare(speciesId) || !plotEmpty()) return false;
        species[0] = speciesId;
        stage[0] = 0;
        withered = false;
        weeds = 0;
        neglect = 0;
        return true;
    }

    /** Сейчас на грядке растёт редкий цветок. */
    public boolean growingRare() {
        return Rare.isRare(species[0]);
    }

    /** Какой именно редкий цветок растёт (0 — обычный). */
    public int growingRareSpecies() {
        return Rare.isRare(species[0]) ? species[0] : 0;
    }

    /** Версия правил игры. Растёт при перебалансировке — прогресс сбрасывается. */
    public static final int GAME_VERSION = 4;

    public int gameVersion = 0;
    public boolean wasReset = false;

    public int water = 0;
    /** Пол для расчёта калорий: 0 — не указан, 1 — мужской, 2 — женский. */
    public int sex = 0;

    // ---- праздничный ивент: своё растение, одна попытка на игрока ----
    public boolean eventSeed = false;
    public int eventStage = 0;
    public boolean eventCard = false;

    // ---- новогодний ивент: адвент-календарь и украшенная ёлка ----
    /** Какие окошки адвента открыты: бит (day-1) = 1. */
    public int nyMask = 0;
    /** Украшения на ёлке: "id:x,y;id:x,y" — координаты в долях площади сцены (0..1). */
    public String nyPlaced = "";

    /** Собрана ли открытка «Ёлка-2026». */
    public boolean nyCard = false;

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
    /**
     * Садовники в инвентаре. Применённый садовник замораживает текущий день
     * до полуночи, а на следующий день даёт бонус: шаги считаются вдвое
     * для закрытия нормы (статистика и вода при этом настоящие).
     */
    public int gardeners = 0;
    /** День, который заморожен («2026-11-13»), пусто — не заморожен никто. */
    public String frozenDay = "";
    /** День, на который действует бонус ×2 к норме; это следующий день после заморозки. */
    public String bonusDay = "";
    /** Последний день, за который проверяли норму. */
    public String checkDay = "";

    public String nick = "";
    public int avatarImage = -1;      // готовый портрет с листа; -1 — старый значок
    public int avatarStyle = 0;
    public int avatarPalette = 0;
    public int avatarFrame = 0;
    /** Последний выбранный портрет: нужен, когда стоит своё фото, —
     *  в общем рейтинге фото не показываем, показываем этот портрет. */
    public int avatarLastArt = 0;
    public final java.util.List<Integer> ownedFrames = new ArrayList<>();
    public String created = "";

    public int totalSteps = 0;
    /** Сколько цветов собрано всего (для рейтинга в облаке). */
    public int flowersCollected = 0;
    /** Цветы за текущую неделю и месяц — отдельно, чтобы рейтинг не врал. */
    public int flowersWeek = 0;
    public int flowersMonth = 0;
    /** Метки периодов: когда меняются — счётчики обнуляются. */
    public String flowersWeekTag = "";
    public String flowersMonthTag = "";
    public int bestDay = 0;
    public int stepsCache = 0;
    public String stepsCacheDay = "";
    public final java.util.List<String> unlocked = new ArrayList<>();

    /** Когда получено достижение: id -> дата (день, «2026-11-07»). */
    public final java.util.LinkedHashMap<String, String> unlockedOn =
            new java.util.LinkedHashMap<>();
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

    /** Открытка праздничного ивента: отдельный «вид» с номером −1. */
    public static Card holidayCard() {
        return new Card(-1, -1, Event.NAME, Steps.today(),
                "С Днём народного единства! Пусть в саду и в жизни будет мир, тепло и много цветов.");
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
            if (g.avatarImage >= 0) g.avatarLastArt = g.avatarImage;
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
        g.sex = o.optInt("sex", 0);
            g.points = o.optInt("points", 0);
            g.earned = o.optInt("earned", -1);
            g.neglect = o.optInt("neglect", 0);
        g.checkDay = o.optString("checkDay", "");
            g.weeds = o.optInt("weeds", 0);
            g.withered = o.optBoolean("withered", false);
        g.eventSeed = o.optBoolean("evSeed", false);
        g.eventStage = o.optInt("evStage", 0);
        g.eventCard = o.optBoolean("evCard", false);
        // редкие семечки: новый формат — инвентарь {вид: сколько} и список покупок
        g.rareSeeds.clear();
        g.rareBought.clear();
        org.json.JSONArray rs = o.optJSONArray("rareSeeds");
        if (rs != null) {
            for (int i = 0; i < rs.length(); i++) {
                org.json.JSONObject it = rs.optJSONObject(i);
                if (it == null) continue;
                int id = it.optInt("id", 0);
                int n = it.optInt("n", 0);
                if (Rare.isRare(id) && n > 0) g.rareSeeds.put(id, n);
            }
        }
        org.json.JSONArray rb = o.optJSONArray("rareBought");
        if (rb != null) {
            for (int i = 0; i < rb.length(); i++) {
                int id = rb.optInt(i, 0);
                if (Rare.isRare(id)) g.rareBought.add(id);
            }
        }
        // совместимость: старые сборки держали купленные семечки в невидимой очереди
        org.json.JSONArray rq = o.optJSONArray("rareQueue");
        if (rq != null) {
            for (int i = 0; i < rq.length(); i++) {
                int id = rq.optInt(i, 0);
                if (Rare.isRare(id)) {
                    g.rareSeeds.put(id, g.rareSeedCount(id) + 1);
                    g.rareBought.add(id);
                }
            }
        }
        int legacyRare = o.optInt("rareQueued", o.optBoolean("magicReady", false) ? Rare.MAGIC : 0);
        if (Rare.isRare(legacyRare) && g.rareSeedCount(legacyRare) == 0) {
            g.rareSeeds.put(legacyRare, 1);
            g.rareBought.add(legacyRare);
        }
        g.nyMask = o.optInt("nyMask", 0);
        g.nyPlaced = o.optString("nyPlaced", "");
        g.nyCard = o.optBoolean("nyCard", false);
            g.hoes = o.optInt("hoes", 0);
            g.shovels = o.optInt("shovels", 0);
            g.gardeners = o.optInt("gardeners", 0);
            g.frozenDay = o.optString("frozenDay", "");
            g.bonusDay = o.optString("bonusDay", "");
            // совместимость: раньше заморозка была «запасом дней», теперь это состояние дня
            if (o.optInt("gardenFreeze", 0) > 0 && g.frozenDay.isEmpty()) {
                g.frozenDay = Steps.today();
                g.bonusDay = shiftDay(Steps.today(), 1);
            }
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
            // если редкий цветок уже растёт, семечко когда-то купили — в магазине его нет
            if (Rare.isRare(g.species[0])) g.rareBought.add(g.species[0]);

            g.waterDay = o.optString("waterDay", "");
            g.stepsAtWater = o.optInt("stepsAtWater", 0);
            g.waterProgress = o.optInt("waterProgress", -1);
            g.stepsMark = o.optInt("stepsMark", -1);
            g.nick = o.optString("nick", "");
            g.avatarImage = o.optInt("avatarImage", -1);
        g.avatarStyle = o.optInt("avatarStyle", 0);
            g.avatarPalette = o.optInt("avatarPalette", 0);
            g.avatarFrame = o.optInt("avatarFrame", 0);
        g.avatarLastArt = o.optInt("avatarLastArt", 0);
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
        g.flowersCollected = o.optInt("flowersCollected", 0);
        g.flowersWeek = o.optInt("flowersWeek", 0);
        g.flowersMonth = o.optInt("flowersMonth", 0);
        g.flowersWeekTag = o.optString("flowersWeekTag", "");
        g.flowersMonthTag = o.optString("flowersMonthTag", "");
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
            // собранный редкий цветок значит, что семечко уже куплено: в магазине не показываем
            for (Card c : g.cards) if (c != null && Rare.isRare(c.species)) g.rareBought.add(c.species);

            JSONArray bl = o.optJSONArray("bloomed");
            if (bl != null) for (int i = 0; i < bl.length(); i++) g.bloomed.add(bl.optInt(i));

            JSONArray un = o.optJSONArray("unlocked");
            if (un != null) {
                for (int i = 0; i < un.length(); i++) g.unlocked.add(un.optString(i));
            }

            // даты получения медалей: «id|2026-11-07»
            JSONArray unOn = o.optJSONArray("unlockedOn");
            if (unOn != null) {
                for (int i = 0; i < unOn.length(); i++) {
                    String raw = unOn.optString(i, "");
                    int bar = raw.indexOf('|');
                    if (bar > 0) g.unlockedOn.put(raw.substring(0, bar), raw.substring(bar + 1));
                }
            }
            // старые сохранения: даты нет — ставим сегодняшнюю один раз
            for (String id : g.unlocked) {
                if (!g.unlockedOn.containsKey(id)) g.unlockedOn.put(id, Steps.today());
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

        if (g.avatarImage >= 0) g.avatarLastArt = g.avatarImage;
        g.refreshDaily(Steps.today());
        // счётчик собранных цветов появился позже коллекции: если он ещё ноль,
        // а коллекция не пустая — берём число открытых видов, чтобы человек
        // не оказался с нулём в рейтинге
        if (g.flowersCollected == 0 && !g.bloomed.isEmpty()) {
            g.flowersCollected = g.bloomed.size();
        }
        return g;
    }

    /** Обнулить игровой прогресс, сохранив ник и аватар. */
    public void resetProgress() {
        water = 0;
        sex = 0;
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
        unlockedOn.clear();
        cards.clear();
        eventSeed = false;
        eventStage = 0;
        eventCard = false;
        rareSeeds.clear();
        rareBought.clear();
        nyMask = 0;
        nyPlaced = "";
        nyCard = false;
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
        avatarImage = -1;
        avatarStyle = 0;
        avatarPalette = 0;
        nick = "";
        created = "";
        neglect = 0;
        weeds = 0;
        withered = false;
        hoes = 0;
        shovels = 0;
        gardeners = 0;
        frozenDay = "";
        bonusDay = "";
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

    /**
     * После восстановления копии: не начислять воду за шаги, которые уже были
     * оплачены в копии. Иначе человек получает лейки второй раз за тот же день.
     */
    public void alignWaterBaseline(int stepsToday) {
        if (stepsToday > stepsMark) {
            stepsMark = stepsToday;
            waterProgress = 0;
        }
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
        o.put("sex", sex);
        o.put("evSeed", eventSeed);
        o.put("evStage", eventStage);
        o.put("evCard", eventCard);
        // редкие семечки лежат в инвентаре: {вид: сколько} + список уже купленных
        org.json.JSONArray rsOut = new org.json.JSONArray();
        for (java.util.Map.Entry<Integer, Integer> e : rareSeeds.entrySet()) {
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("id", e.getKey().intValue());
            item.put("n", e.getValue().intValue());
            rsOut.put(item);
        }
        o.put("rareSeeds", rsOut);
        org.json.JSONArray rbOut = new org.json.JSONArray();
        for (Integer id : rareBought) rbOut.put(id.intValue());
        o.put("rareBought", rbOut);
        o.put("nyMask", nyMask);
        o.put("nyPlaced", nyPlaced);
        o.put("nyCard", nyCard);
            o.put("points", points);
            o.put("earned", earned);
            o.put("neglect", neglect);
        o.put("checkDay", checkDay);
            o.put("weeds", weeds);
            o.put("withered", withered);
            o.put("hoes", hoes);
            o.put("shovels", shovels);
            o.put("gardeners", gardeners);
            o.put("frozenDay", frozenDay);
            o.put("bonusDay", bonusDay);
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
            o.put("avatarImage", avatarImage);
        o.put("avatarStyle", avatarStyle);
            o.put("avatarPalette", avatarPalette);
            o.put("avatarFrame", avatarFrame);
        o.put("avatarLastArt", avatarLastArt);
            o.put("ownedFrames", new JSONArray(ownedFrames));
            o.put("created", created);
            o.put("totalSteps", totalSteps);
        o.put("flowersCollected", flowersCollected);
        o.put("flowersWeek", flowersWeek);
        o.put("flowersMonth", flowersMonth);
        o.put("flowersWeekTag", flowersWeekTag);
        o.put("flowersMonthTag", flowersMonthTag);
            o.put("bestDay", bestDay);
            o.put("stepsCache", stepsCache);
            o.put("stepsCacheDay", stepsCacheDay);
            o.put("unlocked", new JSONArray(unlocked));
            JSONArray unOn = new JSONArray();
            for (java.util.Map.Entry<String, String> e : unlockedOn.entrySet()) {
                unOn.put(e.getKey() + "|" + e.getValue());
            }
            o.put("unlockedOn", unOn);
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

            // атомарно: сначала «.tmp», потом переименование — прогресс не пострадает,
            // если процесс убьют посреди записи
            Steps.writeAtomically(new File(ctx.getFilesDir(), FILE), o.toString());
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
            // q3 — недельное задание: его флаг сбрасывается только вместе с неделей,
            // иначе 700 очков можно было получать каждый день до конца недели
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

    /**
     * Номер понедельника недели, в которую попадает день.
     *
     * День недели берётся из самой даты, а не из «сегодня»: раньше здесь стоял
     * `Calendar.getInstance()`, и для любой даты, кроме сегодняшней, граница
     * недели считалась неверно.
     */
    public static int weekStartOf(String day) {
        int d = dayNumber(day);
        java.util.Calendar c = java.util.Calendar.getInstance();
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            java.util.Date parsed = f.parse(day);
            if (parsed != null) c.setTime(parsed);
        } catch (Exception ignored) { }
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

        // Запущенный сад платит меньше: с сорняками лейка копится вдвое дольше,
        // а у засохшего цветка вода не начисляется совсем.
        int factor = waterPenalty();
        if (factor == 0) {
            stepsMark = stepsToday;   // шаги уходят только в статистику
            waterProgress = 0;
        } else {
            int pay = Math.max(1, cost * factor);
            // если остался «хвост» от старой версии — выравниваем по целым лейкам
            if (stepsMark % pay != 0) stepsMark = (stepsMark / pay) * pay;
            // stepsMark — сколько шагов сегодня уже превращено в лейки.
            // Остаток считаем от него, а не «копим», поэтому число всегда сходится
            // с шагами за сегодня и не тянется со вчерашнего дня.
            if (stepsToday < stepsMark) stepsMark = 0;   // счётчик обнулился
            int add = (stepsToday - stepsMark) / pay;
            if (add > 0) {
                water += add;
                stepsMark += add * pay;
            }
            waterProgress = Math.max(0, Math.min(pay - 1, stepsToday - stepsMark));
        }

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
        if (withered) return 0;
        int pay = Math.max(1, stepCost()) * Math.max(1, waterPenalty());
        return Math.max(0, pay - waterProgress);
    }

    // ---------------- сорняки, увядание, инструменты ----------------
    /** Цена инструментов: тяпка 100, лопата 200 — доступно с первых дней. */
    public static final int HOE_PRICE = 100;
    public static final int SHOVEL_PRICE = 200;
    /** Садовник: дороже инструментов, зато снимает штраф за пропущенный день. */
    public static final int GARDENER_PRICE = 250;
    /** С какого пропущенного дня подряд цветок засыхает. */
    static final int WITHER_AFTER = 3;
    /** После стольких дней отсутствия лопата выдаётся бесплатно, чтобы не запирать. */
    static final int FORGIVE_AFTER = 7;

    /**
     * Во сколько раз дольше копится лейка: 1 — обычно, 2 — сорняки (половина воды),
     * 0 — цветок засох и вода не начисляется совсем.
     */
    public int waterPenalty() {
        if (withered) return 0;
        return weeds > 0 ? 2 : 1;
    }

    /** Цветок засох: шаги больше не дают воду. */
    public boolean waterStopped() { return withered; }

    /** Сорняки на грядке: лейка копится вдвое дольше. */
    public boolean waterHalved() { return !withered && weeds > 0; }

    /** Пока грядка заморожена, сорняки и засыхание блокируют полив и сбор. */
    public boolean plotBlocked() { return withered || weeds > 0 || isFrozenToday(); }

    /** Заморозка садовником: действия с цветком запрещены до полуночи. */
    public boolean freezeActive() { return isFrozenToday(); }

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

    /**
     * Купить садовника. В магазине их сколько угодно, каждый даёт один день
     * заморозки после применения на грядку.
     */
    public boolean buyGardener() {
        if (points < GARDENER_PRICE) return false;
        points -= GARDENER_PRICE;
        gardeners++;
        return true;
    }

    /**
     * Применить садовника: заморозить текущий день до полуночи.
     * Садовник уходит из инвентаря, следующий день получает бонус ×2 к норме.
     */
    public boolean applyGardener() {
        if (gardeners <= 0) return false;
        if (isFrozenToday()) return false;         // сегодня уже морозили
        gardeners--;
        frozenDay = Steps.today();
        bonusDay = shiftDay(Steps.today(), 1);
        return true;
    }

    /** Сегодня грядка заморожена? Тогда все действия с цветком запрещены. */
    public boolean isFrozenToday() {
        return frozenDay != null && frozenDay.equals(Steps.today());
    }

    /** Сегодня действует бонус ×2 к закрытию нормы (день после заморозки)? */
    public boolean bonusToday() {
        return bonusDay != null && bonusDay.equals(Steps.today());
    }

    /**
     * Шаги для закрытия дневной нормы: в бонусный день считаем их вдвое.
     * Для статистики, скорости и воды всегда берём настоящие шаги.
     */
    public int stepsForGoal(int realSteps) {
        return bonusToday() ? realSteps * 2 : realSteps;
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
    /**
     * Одноразовая починка после ошибки 20.11.2026: шаги, сделанные до полуночи,
     * раньше целиком уходили следующему дню. Если вчера из-за этого был
     * засчитан пропуск, а теперь шагов хватает, снимаем один пропуск и сорняк.
     */
    public void repairMidnightSteps(Context ctx) {
        SharedPreferences p = Steps.prefs(ctx);
        if (p.getBoolean("midnight_fix_113", false)) return;
        p.edit().putBoolean("midnight_fix_113", true).apply();

        int[] last = Steps.lastDays(ctx, 2);
        int yesterday = last.length > 1 ? last[0] : 0;
        int effective = shiftDay(Steps.today(), -1).equals(bonusDay) ? yesterday * 2 : yesterday;
        if (effective >= goal && neglect > 0) {
            neglect = Math.max(0, neglect - 1);
            if (!withered) weeds = Math.min(2, neglect);
            save(ctx);
        }
    }

    public boolean checkMissedDays(Context ctx) {
        String today = Steps.today();
        if (checkDay.isEmpty()) {          // новое сохранение: прошлое не судим,
            checkDay = shiftDay(today, -1);  // но день проверки надо сохранить,
            return true;                     // иначе он так и останется пустым
        }
        if (checkDay.equals(today)) return false;

        LinkedHashMap<String, Integer> days = Steps.dailySteps(ctx);
        boolean changed = false;
        String d = shiftDay(checkDay, 1);
        for (int guard = 0; guard < 400 && !d.equals(today); guard++) {
            Integer v = days.get(d);
            int steps = v == null ? 0 : v;
            // замороженный садовником день штрафов не приносит
            if (d.equals(frozenDay)) {
                d = shiftDay(d, 1);
                continue;
            }
            // день после заморозки: шаги для нормы считаются вдвое,
            // но в статистике и для воды остаются настоящими
            int effective = d.equals(bonusDay) ? steps * 2 : steps;
            if (effective >= goal) {
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
        if (isFrozenToday()) return;      // замороженный день штрафа не даёт
        neglect++;
        if (!withered) {
            if (neglect >= WITHER_AFTER) { withered = true; weeds = 0; stage[0] = 0; }
            else weeds = Math.min(2, neglect);
        }
    }

    /**
     * Забрать выполненные задания: три дневных и недельное.
     * Суммы совпадают с тем, что написано на экране заданий:
     * шаги — 100, цветок за день — 100, неделя из 7 цветов — 700.
     */
    public boolean questsClaimed() {
        int add = 0;
        if (q1Done >= q1Target && q1Claimed == 0) { q1Claimed = 1; add += 100; }
        if (flowersToday >= 1 && q2Claimed == 0) { q2Claimed = 1; add += 100; }
        if (weekFlowers >= 7 && q3Claimed == 0) { q3Claimed = 1; add += 700; }
        if (add > 0) { earn(add); return true; }
        return false;
    }

    /** Полить грядку. Возвращает true, если получилось. */
    /** Собрать выросший цветок в букет и посадить следующий. */
    public void collect() {
        if (plotBlocked()) return;         // сначала прополоть или перекопать
        int sp = species[0];
        flowersCollected++;
        resetFlowerPeriods();
        flowersWeek++;
        flowersMonth++;
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
        // у волшебной ромашки нет вида из Species — имя и пожелание берём у Magic
        boolean rare = Rare.isRare(sp);
        cards.add(new Card(flowersTotal, sp,
                rare ? Rare.name(sp) : Species.name(sp),
                Steps.today(),
                rare ? Rare.wish(sp) : Species.wish(sp)));

        stage[0] = 0;
        // редкие семечки больше не подставляются сами: они ждут в инвентаре,
        // пока человек сам не посадит их на свободную грядку
        species[0] = nextSpecies();
        questsClaimed();   // задания по цветку и неделе закрываются сразу
    }

    /** Следующий вид: сначала добираем классические 365, потом идут бесконечные. */
    public int nextSpecies() {
        java.util.Random rnd = new java.util.Random();
        if (bloomed.size() < SPECIES_COUNT) {
            for (int guard = 0; guard < 400; guard++) {
                int id = rnd.nextInt(SPECIES_COUNT);
                if (!bloomed.contains(id)) return id;
            }
        }
        // коллекция не заканчивается: берём виды из большого пространства имён
        int space = Species.NAME_SPACE;
        for (int guard = 0; guard < 400; guard++) {
            int id = SPECIES_COUNT + rnd.nextInt(Math.max(1, space - SPECIES_COUNT));
            if (!bloomed.contains(id)) return id;
        }
        return SPECIES_COUNT + rnd.nextInt(Math.max(1, space - SPECIES_COUNT));
    }

    /**
     * Полив праздничного растения. Считается так же, как обычный полив грядки:
     * тратит лейку, даёт очки, идёт в счётчики и в серию дней, — но растит
     * отдельное ивентовое растение, не трогая цветок в саду.
     */
    public boolean waterEvent(String today) {
        if (!eventSeed) return false;
        if (water <= 0) return false;
        if (eventStage >= Event.STAGES) return false;

        water--;
        eventStage++;
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

    /** Если наступила новая неделя или месяц — счётчики цветов начинаются с нуля. */
    public void resetFlowerPeriods() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int week = c.get(java.util.Calendar.WEEK_OF_YEAR);
        int year = c.get(java.util.Calendar.YEAR);
        String weekTag = year + "-W" + (week < 10 ? "0" + week : String.valueOf(week));
        String monthTag = year + "-" + (c.get(java.util.Calendar.MONTH) + 1);
        if (!weekTag.equals(flowersWeekTag)) {
            flowersWeekTag = weekTag;
            flowersWeek = 0;
        }
        if (!monthTag.equals(flowersMonthTag)) {
            flowersMonthTag = monthTag;
            flowersMonth = 0;
        }
    }

    /** Список открытых видов — по максимальной стадии. */
    public List<Integer> collection() {
        return new ArrayList<>(bloomed);
    }
}

