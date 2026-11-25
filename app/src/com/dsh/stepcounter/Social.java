package com.dsh.stepcounter;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Связь с облаком ШагоСада: рейтинги и друзья.
 *
 * Сервер — Cloud Function, один адрес, маршрут передаётся полем `r` в теле.
 * Все запросы идут в фоновом потоке, ответ — в главном (чтобы можно было сразу в UI).
 * Без сети ничего не падает: возвращается понятная ошибка.
 */
public final class Social {

    /** Публичный адрес функции. */
    public static final String API = "https://functions.yandexcloud.net/d4elahkn6kaaeio8c0dk";

    private static final String PREFS = "social";
    private static final String KEY_ID = "garden_id";
    private static final String KEY_SECRET = "garden_secret";
    private static final String KEY_DEVICE = "device_key";
    private static final Object ENSURE_LOCK = new Object();
    private static volatile boolean bindTried = false;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Друг из облака. */
    public static class Friend {
        public String code = "";
        public String nick = "";
        public int flowers;
        public int steps;
        public String avatar = "";
        public int frame;
        public String photo = "";
        public int value;
        public boolean anomaly;
        public boolean self;
    }

    /** Общий ответ: либо данные, либо текст ошибки. */
    public interface Callback {
        void done(boolean ok, String error);
    }

    public interface FriendsCallback {
        void done(List<Friend> rows, String error);
    }

    public interface TopCallback {
        void done(List<Friend> rows, String error);
    }

    private Social() { }

    // ---------------------------------------------------------------- ключи сада

    public static String gardenId(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ID, "");
    }

    public static void forget(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        bindTried = false;
    }

    /** Имя набора настроек: backup сохраняет id/secret вместе с прогрессом. */
    static String prefsName() {
        return PREFS;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Стабильный хеш телефона: ANDROID_ID + подпись APK + package.
     * На Android 8+ ANDROID_ID не меняется при переустановке с той же подписью,
     * поэтому сервер находит прежний сад, а не регистрирует новый.
     * Наружу уходит только SHA-256, без самого ANDROID_ID.
     */
    static String deviceKey(Context c) {
        android.content.SharedPreferences p =
                c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = p.getString(KEY_DEVICE, "");
        if (saved != null && !saved.isEmpty()) return saved;
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) { }
        String cert = "";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(
                    c.getPackageName(), PackageManager.GET_SIGNATURES);
            if (pi != null && pi.signatures != null && pi.signatures.length > 0
                    && pi.signatures[0] != null) {
                cert = sha256(new String(pi.signatures[0].toByteArray(), StandardCharsets.ISO_8859_1));
            }
        } catch (Exception ignored) { }
        String key = sha256((androidId == null ? "" : androidId) + "|" + cert + "|"
                + c.getPackageName() + "|shagosad-device-v1");
        p.edit().putString(KEY_DEVICE, key).apply();
        return key;
    }

    /**
     * Регистрирует сад, если кода ещё нет. synchronized: два одновременных
     * вызова не должны создать два сада (именно так 23.11.2026 появились
     * два «Садовника» в одну секунду).
     */
    public static String ensure(Context c, String nick) {
        synchronized (ENSURE_LOCK) {
            String id = gardenId(c);
            if (id != null && !id.isEmpty()) return id;
            try {
                JSONObject body = new JSONObject();
                body.put("r", "register");
                body.put("device", deviceKey(c));
                String n = (nick == null || nick.trim().isEmpty())
                        ? ("Садовник #" + (1000 + new java.util.Random().nextInt(9000)))
                        : nick.trim();
                body.put("nick", n);
                JSONObject res = post(body);
                if (res == null) return null;
                String newId = res.optString("id", "");
                String secret = res.optString("secret", "");
                if (newId.isEmpty() || secret.isEmpty()) return null;
                c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_ID, newId)
                        .putString(KEY_SECRET, secret).apply();
                return newId;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Привязывает существующий сад к телефону. Если сервер говорит
     * not_found — сад удалён: забываем старый id/secret (deviceKey остаётся),
     * регистрируем новый сад и продолжаем без ошибки на экране.
     */
    private static void bindExisting(final Context c, final String nick, final Runnable after) {
        if (bindTried) {
            if (after != null) MAIN.post(after);
            return;
        }
        bindTried = true;
        final String id = gardenId(c);
        final String secret = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SECRET, "");
        if (id == null || id.isEmpty() || secret == null || secret.isEmpty()) {
            if (after != null) MAIN.post(after);
            return;
        }
        background(new Runnable() {
            @Override public void run() {
                boolean retry = false;
                try {
                    JSONObject body = new JSONObject();
                    body.put("r", "bind");
                    body.put("id", id);
                    body.put("secret", secret);
                    body.put("device", deviceKey(c));
                    JSONObject res = post(body);
                    if (res == null) {
                        retry = true;                  // сеть, не трогаем сад
                    } else if ("not_found".equals(res.optString("error", ""))) {
                        // Сада на сервере больше нет — регистрируем новый.
                        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .remove(KEY_ID).remove(KEY_SECRET).apply();
                        if (ensure(c, nick) == null) retry = true;
                    } else if (res.has("error")) {
                        retry = true;                  // auth/route — не плодим сады
                    }
                } catch (Exception e) {
                    retry = true;
                } finally {
                    if (retry) bindTried = false;
                    if (after != null) MAIN.post(after);
                }
            }
        });
    }

    // ---------------------------------------------------------------- отправка счёта

    /** Регистрация сада в фоне: сеть нельзя трогать в главном потоке. */
    public static void ensureAsync(final Context c, final String nick, final Runnable after) {
        String id = gardenId(c);
        if (id != null && !id.isEmpty()) {
            bindExisting(c, nick, after);
            return;
        }
        background(new Runnable() {
            @Override public void run() {
                ensure(c, nick);
                if (after != null) MAIN.post(after);
            }
        });
    }

    /** Отправляет свой счёт (в фоне). Ничего не ломает, если сети нет. */
    public static void pushScore(final Context app, final GameState game) {
        final String id = gardenId(app);
        if (id == null || id.isEmpty()) return;
        final String secret = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SECRET, "");
        if (secret == null || secret.isEmpty()) return;

        final String avatar = game.avatarImage >= 0
                ? String.valueOf(game.avatarImage)
                : (hasPhoto(app) ? "photo" : "");
        final int frame = game.avatarFrame;
        final String photo = hasPhoto(app) ? photoBase64(app) : "";

        background(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("r", "score");
                    body.put("id", id);
                    body.put("secret", secret);
                    body.put("nick", game.nick);
                    game.resetFlowerPeriods();
                    body.put("flowers", totalFlowers(game));
                    body.put("flowersWeek", Math.max(0, game.flowersWeek));
                    body.put("flowersMonth", Math.max(0, game.flowersMonth));
                    // неделя — с понедельника, месяц — с 1-го числа (как в статистике)
                    int week = Steps.stepsThisWeek(app);
                    int month = Steps.stepsThisMonth(app);
                    body.put("steps", week);
                    body.put("stepsWeek", week);
                    body.put("stepsMonth", month);
                    body.put("stepsAll", Math.max(game.totalSteps, month));
                    body.put("flowersAll", totalFlowers(game));
                    int portrait = game.avatarImage >= 0 ? game.avatarImage : game.avatarLastArt;
                    body.put("portrait", portrait);
                    body.put("avatar", avatar);
                    body.put("frame", frame);
                    if (!photo.isEmpty()) body.put("photo", photo);
                    post(body);
                } catch (Exception ignored) { }
            }
        });
    }

    /** Сколько цветов выращено всего — то, что показываем в рейтинге. */
    static int totalFlowers(GameState g) {
        // у старых садов счётчик мог остаться нулевым, а открытые виды есть —
        // поэтому берём большее из двух
        int collected = Math.max(0, g.flowersCollected);
        int species = 0;
        try { species = g.collection().size(); } catch (Throwable ignored) { }
        return Math.max(collected, species);
    }

    // ---------------------------------------------------------------- запросы

    /**
     * Кэш рейтинга на телефоне: пять минут. Раньше был час, и после смены ника
     * или появления нового игрока вкладка могла сутки показывать старое: кэш
     * считался «свежим», даже если время записи оказалось в будущем (сбитые часы).
     */
    private static final long TOP_TTL_MS = 5 * 60 * 1000L;
    private static final String CACHE_PREFS = "social_cache";
    /** Версия ключей: старые записи (v1) просто перестают читаться. */
    private static final String CACHE_V = "v2:";

    /**
     * Ключ кэша с привязкой к текущему периоду: неделя — номер недели, месяц — год-месяц,
     * «всё время» — без отметки. Иначе после смены суток, недели или месяца вкладка
     * отдавала бы цифры прошлого периода, пока не истечёт срок кэша.
     */
    private static String cacheKey(String kind, String period, boolean friendsOnly) {
        return CACHE_V + "top:" + kind + ":" + period + ":" + periodStamp(period)
                + ":" + (friendsOnly ? "friends" : "all");
    }

    /** Метка периода: «2026-11», «2026-W47» или пусто для «всё время». */
    private static String periodStamp(String period) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        if ("week".equals(period)) {
            return String.format(java.util.Locale.US, "%d-W%02d",
                    c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.WEEK_OF_YEAR));
        }
        if ("month".equals(period)) {
            return String.format(java.util.Locale.US, "%d-%02d",
                    c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1);
        }
        return "all";
    }


    /** Сбрасываем кэш рейтинга, когда меняется список друзей. */
    public static void clearTopCache(Context c) {
        c.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static void top(final Context app, final String kind, final String period,
                           final boolean friendsOnly, final TopCallback cb) {
        final String id = app == null ? "" : gardenId(app);
        final String secret = app == null ? ""
                : app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_SECRET, "");
        if (app != null) {
            String key = cacheKey(kind, period, friendsOnly);
            android.content.SharedPreferences cache =
                    app.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
            long savedAt = cache.getLong(key + ":at", 0);
            String savedJson = cache.getString(key, null);
            long age = System.currentTimeMillis() - savedAt;
            // возраст не может быть отрицательным: если часы прыгнули назад,
            // кэш с «временем из будущего» надо считать устаревшим
            if (savedJson != null && age >= 0 && age < TOP_TTL_MS) {
                try {
                    JSONObject cached = new JSONObject(savedJson);
                    List<Friend> rows = parseRows(cached.optJSONArray("rows"));
                    finishTop(cb, rows, null);
                    return;                       // в сеть не идём
                } catch (Exception ignored) { }
            }
        }
        background(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("r", "top");
                    body.put("kind", kind);
                    body.put("period", period);
                    body.put("limit", 50);
                    body.put("scope", friendsOnly ? "friends" : "all");
                    if (friendsOnly) {
                        body.put("id", id);
                        body.put("secret", secret);
                    }
                    JSONObject res = post(body);
                    if (res == null) {
                        // связи нет — отдаём старый кэш, если он есть, вместо ошибки
                        if (app != null) {
                            String key = cacheKey(kind, period, friendsOnly);
                            String stale = app.getSharedPreferences(CACHE_PREFS,
                                    Context.MODE_PRIVATE).getString(key, null);
                            if (stale != null) {
                                try {
                                    JSONObject cached = new JSONObject(stale);
                                    finishTop(cb, parseRows(cached.optJSONArray("rows")),
                                            null);
                                    return;
                                } catch (Exception ignored) { }
                            }
                        }
                        finishTop(cb, null, "нет связи");
                        return;
                    }
                    if (app != null && !res.has("error")) {
                        String key = cacheKey(kind, period, friendsOnly);
                        app.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                                .putString(key, res.toString())
                                .putLong(key + ":at", System.currentTimeMillis())
                                .apply();
                    }
                    List<Friend> rows = parseRows(res.optJSONArray("rows"));
                    finishTop(cb, rows, null);
                } catch (Exception e) {
                    finishTop(cb, null, "нет связи");
                }
            }
        });
    }

    public static void friends(final Context app, final FriendsCallback cb) {
        final String id = gardenId(app);
        final String secret = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SECRET, "");
        if (id == null || id.isEmpty() || secret == null || secret.isEmpty()) {
            finishFriends(cb, null, "нет кода сада");
            return;
        }
        background(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("r", "friends");
                    body.put("id", id);
                    body.put("secret", secret);
                    JSONObject res = post(body);
                    if (res == null) { finishFriends(cb, null, "нет связи"); return; }
                    finishFriends(cb, parseRows(res.optJSONArray("rows")), null);
                } catch (Exception e) {
                    finishFriends(cb, null, "нет связи");
                }
            }
        });
    }

    public static void addFriend(final Context app, final String code, final Callback cb) {
        final String id = gardenId(app);
        final String secret = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SECRET, "");
        background(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("r", "friend/add");
                    body.put("id", id);
                    body.put("secret", secret);
                    body.put("code", code);
                    JSONObject res = post(body);
                    boolean ok = res != null && !res.has("error");
                    String err = ok ? null : (res == null ? "нет связи" : errorText(res));
                    if (ok) clearTopCache(app);     // друг появится сразу, а не через час
                    finish(cb, ok, err);
                } catch (Exception e) {
                    finish(cb, false, "нет связи");
                }
            }
        });
    }

    public static void removeFriend(final Context app, final String code, final Callback cb) {
        clearTopCache(app);
        final String id = gardenId(app);
        final String secret = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SECRET, "");
        background(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("r", "friend/remove");
                    body.put("id", id);
                    body.put("secret", secret);
                    body.put("code", code);
                    JSONObject res = post(body);
                    boolean ok = res != null && !res.has("error");
                    finish(cb, ok, ok ? null : "нет связи");
                } catch (Exception e) {
                    finish(cb, false, "нет связи");
                }
            }
        });
    }


    /** Расход бесплатных лимитов облака (для меню разработчика). */
    public static void stats(final StatsCallback cb) {
        background(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("r", "stats");
                    JSONObject res = post(body);
                    finishStats(cb, res, res == null ? "нет связи" : null);
                } catch (Exception e) {
                    finishStats(cb, null, "нет связи");
                }
            }
        });
    }

    public interface StatsCallback {
        void done(JSONObject data, String error);
    }

    private static void finishStats(final StatsCallback cb, final JSONObject data, final String err) {
        if (cb == null) return;
        MAIN.post(new Runnable() {
            @Override public void run() { cb.done(data, err); }
        });
    }

    // ---------------------------------------------------------------- внутреннее

    private static List<Friend> parseRows(JSONArray arr) {
        List<Friend> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Friend f = new Friend();
            f.code = o.optString("code", "");
            f.nick = o.optString("nick", "сад");
            f.flowers = o.optInt("flowers", 0);
            f.steps = o.optInt("steps", 0);
            f.value = o.optInt("value", 0);
            f.avatar = o.optString("avatar", "");
            f.frame = o.optInt("frame", 0);
            f.photo = o.optString("photo", "");
            f.anomaly = o.optBoolean("anomaly", false);
            f.self = o.optBoolean("self", false);
            out.add(f);
        }
        return out;
    }

    private static String errorText(JSONObject res) {
        String e = res.optString("error", "");
        if ("auth".equals(e)) return "код сада не подошёл";
        if ("not_found".equals(e)) return "такого сада нет";
        if ("code".equals(e)) return "нужен код друга";
        return e.isEmpty() ? "ошибка" : e;
    }

    private static JSONObject post(JSONObject body) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(API);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is == null) return null;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) > 0) buf.write(b, 0, n);
            is.close();
            String raw = buf.toString("UTF-8");
            return raw.isEmpty() ? null : new JSONObject(raw);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void background(Runnable r) {
        Thread t = new Thread(r, "shagosad-net");
        t.setDaemon(true);
        t.start();
    }

    private static void finish(final Callback cb, final boolean ok, final String err) {
        if (cb == null) return;
        MAIN.post(new Runnable() {
            @Override public void run() { cb.done(ok, err); }
        });
    }

    private static void finishFriends(final FriendsCallback cb, final List<Friend> rows,
                                      final String err) {
        if (cb == null) return;
        MAIN.post(new Runnable() {
            @Override public void run() { cb.done(rows, err); }
        });
    }

    private static void finishTop(final TopCallback cb, final List<Friend> rows, final String err) {
        if (cb == null) return;
        MAIN.post(new Runnable() {
            @Override public void run() { cb.done(rows, err); }
        });
    }

    // ---------------------------------------------------------------- фото

    static boolean hasPhoto(Context c) {
        return new File(c.getFilesDir(), "avatar.png").exists();
    }

    /** Миниатюра 96×96 в base64 — маленькая, чтобы не забивать облако. */
    static String photoBase64(Context c) {
        try {
            File f = new File(c.getFilesDir(), "avatar.png");
            if (!f.exists()) return "";
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int scale = 1;
            int min = Math.min(o.outWidth, o.outHeight);
            while (min / scale > 192) scale *= 2;

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (src == null) return "";
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap sq = Bitmap.createBitmap(src,
                    (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap small = Bitmap.createScaledBitmap(sq, 96, 96, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 70, out);
            if (sq != src) sq.recycle();
            src.recycle();
            small.recycle();
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Разбирает миниатюру друга обратно в картинку. */
    public static Bitmap decodePhoto(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] b = Base64.decode(base64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Throwable t) {
            return null;
        }
    }
}
