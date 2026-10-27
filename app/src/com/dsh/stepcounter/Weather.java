package com.dsh.stepcounter;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Реальная погода и координаты для сцены в саду.
 *
 * Основной источник — wttr.in: бесплатно, без ключа и аккаунта, отдаёт текущую
 * погоду по координатам и доступен из России. Резерв — open-meteo (он же даёт
 * более подробный код WMO, но с российских сетей часто недоступен).
 *
 * Данные кэшируются в SharedPreferences и обновляются не чаще раза в 20 минут.
 * Если сети нет или оба источника молчат — остаётся кэш, а если нет и его,
 * сцена работает по ручной настройке. Сеть никогда не ломает сцену.
 */
final class Weather {

    static final String K_TIME = "w_time";
    static final String K_TEMP = "w_temp";
    static final String K_CODE = "w_code";
    static final String K_CLOUD = "w_cloud";
    static final String K_PREC = "w_prec";
    static final String K_SNOW = "w_snow";
    static final String K_LAT = "w_lat";
    static final String K_LON = "w_lon";
    static final String K_NOTE = "w_note";
    static final String K_SRC = "w_src";

    /** Как часто ходим в сеть. Погода не меняется каждую минуту. */
    static final long FRESH_MS = 20 * 60 * 1000L;

    private static volatile boolean fetching = false;

    private Weather() { }

    static long updatedAt(Context ctx) { return Steps.prefs(ctx).getLong(K_TIME, 0L); }

    static boolean isFresh(Context ctx) {
        long t = updatedAt(ctx);
        return t > 0 && System.currentTimeMillis() - t < FRESH_MS;
    }

    static boolean hasData(Context ctx) { return updatedAt(ctx) > 0; }

    static int code(Context ctx) { return Steps.prefs(ctx).getInt(K_CODE, -1); }
    static int cloud(Context ctx) { return Steps.prefs(ctx).getInt(K_CLOUD, -1); }
    static float temp(Context ctx) { return Steps.prefs(ctx).getFloat(K_TEMP, Float.NaN); }
    static float precip(Context ctx) { return Steps.prefs(ctx).getFloat(K_PREC, 0f); }
    static float snow(Context ctx) { return Steps.prefs(ctx).getFloat(K_SNOW, 0f); }
    static String note(Context ctx) { return Steps.prefs(ctx).getString(K_NOTE, ""); }
    static String source(Context ctx) { return Steps.prefs(ctx).getString(K_SRC, ""); }

    // ---------------- координаты ----------------

    static boolean ensureCoords(Context ctx) {
        float lat = Steps.prefs(ctx).getFloat(K_LAT, Float.NaN);
        float lon = Steps.prefs(ctx).getFloat(K_LON, Float.NaN);
        if (!Float.isNaN(lat) && !Float.isNaN(lon)) return true;

        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            Location best = null;
            List<String> providers = lm.getProviders(true);
            for (int i = 0; i < providers.size(); i++) {
                Location l = null;
                try { l = lm.getLastKnownLocation(providers.get(i)); } catch (Exception ignored) { }
                if (l == null) continue;
                if (best == null || l.getTime() > best.getTime()) best = l;
            }

            // последнего известного может не быть — просим одно разовое определение
            if (best == null && android.os.Build.VERSION.SDK_INT >= 30) {
                best = requestOnce(lm);
            }
            if (best == null) return false;

            Steps.prefs(ctx).edit()
                    .putFloat(K_LAT, (float) best.getLatitude())
                    .putFloat(K_LON, (float) best.getLongitude())
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Один разовый запрос координат с ожиданием: сеть-провайдер, потом GPS. */
    private static Location requestOnce(LocationManager lm) {
        Location got = askOnce(lm, LocationManager.NETWORK_PROVIDER);
        if (got == null) got = askOnce(lm, LocationManager.GPS_PROVIDER);
        return got;
    }

    private static Location askOnce(LocationManager lm, String provider) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Location[] out = new Location[1];
        try {
            lm.getCurrentLocation(provider, null,
                    new java.util.concurrent.Executor() {
                        @Override public void execute(Runnable r) { r.run(); }
                    },
                    new java.util.function.Consumer<Location>() {
                        @Override public void accept(Location l) {
                            out[0] = l;
                            latch.countDown();
                        }
                    });
            latch.await(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return null;
        }
        return out[0];
    }

    static float lat(Context ctx) { return Steps.prefs(ctx).getFloat(K_LAT, Float.NaN); }
    static float lon(Context ctx) { return Steps.prefs(ctx).getFloat(K_LON, Float.NaN); }

    // ---------------- обновление ----------------

    /** Обновить в фоне, если кэш устарел. Безопасно звать часто. */
    static void refreshAsync(final Context ctx) {
        if (isFresh(ctx) || fetching) return;
        fetching = true;
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try { refreshNow(app); } catch (Exception ignored) { }
                fetching = false;
            }
        }, "weather").start();
    }

    /**
     * Погода на запуске приложения — один раз за жизнь процесса.
     * По решению владельца (27.10.2026) чаще не нужно: запрос идёт только при
     * старте, между запусками живёт кэш, ручное обновление осталось в тестах.
     */
    static void refreshOnStart(final Context ctx) {
        if (started || fetching) return;
        started = true;
        fetching = true;
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try { refreshNow(app); } catch (Exception ignored) { }
                fetching = false;
            }
        }, "weather-start").start();
    }

    private static volatile boolean started = false;

    /** Синхронное обновление. true — данные обновились. */
    static boolean refreshNow(Context ctx) {
        boolean real = ensureCoords(ctx);
        float lat, lon;
        if (real) {
            lat = lat(ctx);
            lon = lon(ctx);
        } else {
            // координат нет — берём приблизительные по часовому поясу: погода
            // по центру пояса всё равно полезнее, чем «нет данных»
            lat = 55.75f;
            lon = java.util.TimeZone.getDefault()
                    .getOffset(System.currentTimeMillis()) / 3600000f * 15f;
        }

        if (fetchMetNo(ctx, lat, lon)) return true;
        if (fetchWttr(ctx, lat, lon)) return true;
        if (fetchOpenMeteo(ctx, lat, lon)) return true;
        note(ctx, "нет связи с погодой");
        return false;
    }

    /**
     * met.no (Норвежский метеоинститут): бесплатно, без ключа, отдаёт
     * `cloud_area_fraction` — настоящий процент облачности, а не догадку
     * из текста. Ответ ~38 КБ и приходит меньше секунды. Требует внятный
     * User-Agent с ссылкой на проект, иначе 403.
     */
    private static boolean fetchMetNo(Context ctx, float lat, float lon) {
        try {
            String url = String.format(Locale.US,
                    "https://api.met.no/weatherapi/locationforecast/2.0/compact"
                            + "?lat=%.4f&lon=%.4f", lat, lon);
            String body = get(url, "ShagoSad/10.8 github.com/muzikamv-dot/shagosad");
            JSONObject root = new JSONObject(body);
            JSONObject props = root.optJSONObject("properties");
            JSONArray series = props == null ? null : props.optJSONArray("timeseries");
            if (series == null || series.length() == 0) {
                note(ctx, "met.no: пустой ряд");
                return false;
            }
            JSONObject first = series.getJSONObject(0);
            JSONObject data = first.optJSONObject("data");
            if (data == null) {
                note(ctx, "met.no: нет data");
                return false;
            }

            JSONObject details = data.optJSONObject("instant") == null ? null
                    : data.getJSONObject("instant").optJSONObject("details");
            float temp = (float) (details == null ? Float.NaN
                    : details.optDouble("air_temperature", Float.NaN));
            int cloud = details == null ? -1
                    : (int) Math.round(details.optDouble("cloud_area_fraction", -1));

            String symbol = "";
            float prec = 0f;
            JSONObject next = data.optJSONObject("next_1_hours");
            if (next != null) {
                JSONObject sum = next.optJSONObject("summary");
                if (sum != null) symbol = sum.optString("symbol_code", "");
                JSONObject nd = next.optJSONObject("details");
                if (nd != null) prec = (float) nd.optDouble("precipitation_amount", 0);
            }

            int code = codeFromSymbol(symbol);
            float snow = (code == 71 || code == 73 || code == 75 || code == 85) ? 1f : 0f;
            if (cloud < 0) cloud = cloudFromCode(code);

            save(ctx, temp, code, cloud, prec, snow, "met.no");
            note(ctx, "ок · " + symbol);
            return true;
        } catch (Exception e) {
            note(ctx, "met.no: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /** Символ met.no (например «partlycloudy_day») → код WMO. */
    private static int codeFromSymbol(String symbol) {
        String s = symbol == null ? "" : symbol.toLowerCase(Locale.US);
        int cut = s.indexOf('_');
        String base = cut > 0 ? s.substring(0, cut) : s;
        if (base.startsWith("clearsky")) return 0;
        if (base.startsWith("fair")) return 1;
        if (base.startsWith("partlycloudy")) return 2;
        if (base.startsWith("cloudy")) return 3;
        if (base.startsWith("fog")) return 45;
        if (base.contains("thunder")) return 95;
        if (base.contains("sleet")) return 66;
        if (base.contains("snowshowers")) return 85;
        if (base.contains("snow")) return base.startsWith("heavy") ? 75 : 71;
        if (base.contains("rainshowers")) return 80;
        if (base.contains("rain")) return base.startsWith("heavy") ? 65 : 61;
        if (base.contains("showers")) return 80;
        return -1;
    }

    /**
     * wttr.in: доступен из России, без ключа. Берём не полный JSON (он весит
     * ~200 КБ и отдаётся десятки секунд), а компактную строку формата —
     * она приходит за полсекунды: «Overcast|+6°C|0.0mm».
     */
    private static boolean fetchWttr(Context ctx, float lat, float lon) {
        try {
            String url = String.format(Locale.US,
                    "https://wttr.in/%.4f,%.4f?format=%%C|%%t|%%p", lat, lon);
            String body = get(url, "curl/8.5.0").trim();
            if (body.length() == 0 || body.startsWith("<")) {
                note(ctx, "wttr: пустой ответ");
                return false;
            }

            String[] parts = body.split("\\|");
            String cond = parts.length > 0 ? parts[0] : "";
            float temp = Float.NaN;
            float prec = 0f;
            if (parts.length > 1) {
                String t = parts[1].replace("°C", "").replace("°F", "").replace("+", "").trim();
                try { temp = Float.parseFloat(t); } catch (Exception ignored) { }
            }
            if (parts.length > 2) {
                String p = parts[2].replace("mm", "").replace("in", "").trim();
                try { prec = Float.parseFloat(p); } catch (Exception ignored) { }
            }

            int code = codeFromCondition(cond, prec);
            int cloud = cloudFromCode(code);
            float snow = (code == 71 || code == 73 || code == 75) ? 1f : 0f;

            save(ctx, temp, code, cloud, prec, snow, "wttr");
            note(ctx, "ок · " + cond);
            return true;
        } catch (Exception e) {
            note(ctx, "wttr: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /** Текст условия wttr → код WMO. */
    private static int codeFromCondition(String text, float prec) {
        String s = text == null ? "" : text.toLowerCase(Locale.US);
        if (s.contains("snow") || s.contains("blizzard") || s.contains("sleet")
                || s.contains("ice") || s.contains("freezing")) return 71;
        if (s.contains("rain") || s.contains("drizzle") || s.contains("shower")
                || s.contains("thunder")) return 61;
        if (s.contains("overcast")) return 3;
        if (s.contains("cloud")) return 2;
        if (s.contains("mist") || s.contains("fog") || s.contains("haze")) return 45;
        if (s.contains("sunny") || s.contains("clear")) return 0;
        if (prec > 0f) return 61;
        return -1;
    }

    /** Облачность по коду: у компактного формата нет процентов. */
    private static int cloudFromCode(int code) {
        switch (code) {
            case 0: return 5;
            case 1: return 25;
            case 2: return 55;
            case 3: return 95;
            case 45: case 48: return 80;
            case 61: case 63: case 65: return 90;
            case 71: case 73: case 75: return 90;
            case 80: case 95: return 95;
            default: return 40;
        }
    }

    /** open-meteo: точнее, но с российских сетей часто недоступен. */
    private static boolean fetchOpenMeteo(Context ctx, float lat, float lon) {
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,cloud_cover,precipitation,snowfall,weather_code",
                    lat, lon);
            String body = get(url, "ShagoSad/10.6");
            JSONObject cur = new JSONObject(body).optJSONObject("current");
            if (cur == null) {
                note(ctx, "open-meteo: ответ без current");
                return false;
            }
            float temp = (float) cur.optDouble("temperature_2m", Float.NaN);
            int cloud = (int) Math.round(cur.optDouble("cloud_cover", -1));
            float prec = (float) cur.optDouble("precipitation", 0);
            float snow = (float) cur.optDouble("snowfall", 0);
            int code = cur.optInt("weather_code", -1);
            save(ctx, temp, code, cloud, prec, snow, "open-meteo");
            return true;
        } catch (Exception e) {
            note(ctx, "open-meteo: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static String get(String url, String ua) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept", "application/json");
        StringBuilder sb = new StringBuilder();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        } finally {
            if (r != null) r.close();
            c.disconnect();
        }
        return sb.toString();
    }

    private static void save(Context ctx, float temp, int code, int cloud, float prec,
                             float snow, String src) {
        Steps.prefs(ctx).edit()
                .putLong(K_TIME, System.currentTimeMillis())
                .putFloat(K_TEMP, temp)
                .putInt(K_CODE, code)
                .putInt(K_CLOUD, cloud)
                .putFloat(K_PREC, prec)
                .putFloat(K_SNOW, snow)
                .putString(K_SRC, src)
                .apply();
    }

    private static void note(Context ctx, String s) {
        Steps.prefs(ctx).edit().putString(K_NOTE, s).apply();
    }

    // ---------------- коды погоды ----------------

    /** Код погоды (WMO) → наш вид сцены. */
    static int sceneWeather(int code, int cloud, float snowfall, float precip) {
        if (code < 0) return -1;
        if (code == 0) return DayPhase.WEATHER_CLEAR;
        if (code == 1 || code == 2 || code == 3) return DayPhase.WEATHER_CLOUDY;
        if (code == 45 || code == 48) return DayPhase.WEATHER_CLOUDY;
        if (code >= 51 && code <= 57) return DayPhase.WEATHER_RAIN;
        if (code >= 61 && code <= 67) return DayPhase.WEATHER_RAIN;
        if (code >= 71 && code <= 77) return DayPhase.WEATHER_SNOW;
        if (code >= 80 && code <= 82) return DayPhase.WEATHER_RAIN;
        if (code == 85 || code == 86) return DayPhase.WEATHER_SNOW;
        if (code >= 95) return DayPhase.WEATHER_RAIN;
        if (snowfall > 0f) return DayPhase.WEATHER_SNOW;
        if (precip > 0f) return DayPhase.WEATHER_RAIN;
        if (cloud >= 60) return DayPhase.WEATHER_CLOUDY;
        return DayPhase.WEATHER_CLEAR;
    }

    /** Человеческое описание для диагностики и настроек. */
    static String describe(int code) {
        if (code < 0) return "нет данных";
        if (code == 0) return "ясно";
        if (code == 1) return "почти ясно";
        if (code == 2) return "переменная облачность";
        if (code == 3) return "пасмурно";
        if (code == 45 || code == 48) return "туман";
        if (code >= 51 && code <= 57) return "морось";
        if (code >= 61 && code <= 67) return "дождь";
        if (code >= 71 && code <= 77) return "снег";
        if (code >= 80 && code <= 82) return "ливень";
        if (code == 85 || code == 86) return "снегопад";
        if (code >= 95) return "гроза";
        return "код " + code;
    }

    static String stamp() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }
}
