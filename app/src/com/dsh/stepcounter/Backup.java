package com.dsh.stepcounter;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Резервная копия прогресса: состояние сада и журнал шагов в один JSON.
 *
 * Раньше это лежало в MainActivity, никем не вызывалось и писало напрямую
 * в /sdcard/Download — на Android 10+ такой путь без разрешения недоступен.
 * Теперь файл отдаётся через системный выбор документа (SAF), разрешения не нужны.
 */
public final class Backup {

    private Backup() { }

    private static String read(File f) {
        if (!f.exists()) return "";
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
    }

    private static void write(File f, String body) {
        FileWriter w = null;
        try {
            w = new FileWriter(f, false);
            w.write(body);
        } catch (Exception ignored) {
        } finally {
            try { if (w != null) w.close(); } catch (Exception ignored) { }
        }
    }

    /** Собрать копию в JSON-строку. */
    public static String build(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("app", "stepcounter");
        o.put("version", 4);
        o.put("exported", System.currentTimeMillis());
        o.put("garden", read(new File(c.getFilesDir(), "garden.json")));
        o.put("journal", read(new File(c.getFilesDir(), "journal.txt")));
        // фото профиля: без него копия не полная, и после сброса его не вернуть
        String avatar = "";
        try {
            java.io.File av = new java.io.File(c.getFilesDir(), "avatar.png");
            if (av.exists()) {
                java.io.FileInputStream in = new java.io.FileInputStream(av);
                byte[] buf = new byte[(int) av.length()];
                int off = 0;
                while (off < buf.length) {
                    int n = in.read(buf, off, buf.length - off);
                    if (n <= 0) break;
                    off += n;
                }
                in.close();
                avatar = android.util.Base64.encodeToString(buf, android.util.Base64.NO_WRAP);
            }
        } catch (Exception ignored) { }
        o.put("avatar", avatar);

        // медали: какие достижения открыты (это в garden.json) и какие уже отпразднованы
        org.json.JSONArray achShown = new org.json.JSONArray();
        for (String id : Achievements.shown(c)) achShown.put(id);
        o.put("achShown", achShown);

        // ВСЕ настройки приложения: рост, вес, калории, тема, дневные итоги, служба,
        // обучение, служебные метки журнала. Раньше копия их не сохраняла.
        o.put("prefsSteps", dumpPrefs(c, Steps.PREFS));
        o.put("prefsUi", dumpPrefs(c, "ui"));
        return o.toString();
    }

    /** Сложить все значения набора настроек в JSON с типами. */
    private static JSONObject dumpPrefs(Context c, String name) {
        JSONObject out = new JSONObject();
        try {
            android.content.SharedPreferences p =
                    c.getSharedPreferences(name, Context.MODE_PRIVATE);
            for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject item = new JSONObject();
                if (v instanceof Boolean) { item.put("t", "bool"); item.put("v", ((Boolean) v).booleanValue()); }
                else if (v instanceof Integer) { item.put("t", "int"); item.put("v", ((Integer) v).intValue()); }
                else if (v instanceof Long) { item.put("t", "long"); item.put("v", ((Long) v).longValue()); }
                else if (v instanceof Float) { item.put("t", "float"); item.put("v", ((Float) v).doubleValue()); }
                else if (v instanceof String) { item.put("t", "str"); item.put("v", v.toString()); }
                else if (v instanceof java.util.Set) {
                    org.json.JSONArray a = new org.json.JSONArray();
                    for (Object s : (java.util.Set<?>) v) a.put(String.valueOf(s));
                    item.put("t", "set");
                    item.put("v", a);
                } else continue;
                out.put(e.getKey(), item);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Вернуть набор настроек ровно к состоянию копии: сначала очищаем, потом пишем. */
    private static void restorePrefs(Context c, String name, JSONObject o) {
        if (o == null) return;
        try {
            android.content.SharedPreferences.Editor ed =
                    c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject item = o.optJSONObject(k);
                if (item == null) continue;
                String t = item.optString("t");
                if ("bool".equals(t)) ed.putBoolean(k, item.optBoolean("v"));
                else if ("int".equals(t)) ed.putInt(k, item.optInt("v"));
                else if ("long".equals(t)) ed.putLong(k, item.optLong("v"));
                else if ("float".equals(t)) ed.putFloat(k, (float) item.optDouble("v"));
                else if ("str".equals(t)) ed.putString(k, item.optString("v"));
                else if ("set".equals(t)) {
                    java.util.Set<String> set = new java.util.HashSet<>();
                    org.json.JSONArray a = item.optJSONArray("v");
                    if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.optString(i));
                    ed.putStringSet(k, set);
                }
            }
            ed.apply();
        } catch (Exception ignored) { }
    }

    /** Восстановить из JSON-строки. Возвращает число восстановленных файлов или -1. */
    public static int restore(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!"stepcounter".equals(o.optString("app"))) return -1;
            String garden = o.optString("garden", "");
            String journal = o.optString("journal", "");
            if (garden.length() == 0) return -1;
            write(new File(c.getFilesDir(), "garden.json"), garden);
            if (journal.length() > 0) write(new File(c.getFilesDir(), "journal.txt"), journal);

            String avatar = o.optString("avatar", "");
            if (avatar.length() > 0) {
                try {
                    byte[] data = android.util.Base64.decode(avatar, android.util.Base64.NO_WRAP);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(
                            new java.io.File(c.getFilesDir(), "avatar.png"), false);
                    out.write(data);
                    out.close();
                } catch (Exception ignored) { }
            }

            // медали: набор «отпразднованных» возвращаем к состоянию копии,
            // иначе после загрузки остаются медали, полученные уже после сохранения
            try {
                java.util.Set<String> set = new java.util.HashSet<>();
                if (o.has("achShown")) {
                    org.json.JSONArray arr = o.optJSONArray("achShown");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) set.add(arr.optString(i));
                    }
                }
                Steps.prefs(c).edit().putStringSet(Achievements.PREF_SHOWN, set).apply();
            } catch (Exception ignored) { }

            // настройки: возвращаем оба набора целиком
            try {
                restorePrefs(c, Steps.PREFS, o.optJSONObject("prefsSteps"));
                restorePrefs(c, "ui", o.optJSONObject("prefsUi"));
            } catch (Exception ignored) { }

            int restored = journal.length() > 0 ? 2 : 1;
            if (avatar.length() > 0) restored++;
            return restored;
        } catch (Exception e) {
            return -1;
        }
    }
}
