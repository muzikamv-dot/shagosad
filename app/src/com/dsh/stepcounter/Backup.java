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
        o.put("version", 1);
        o.put("exported", System.currentTimeMillis());
        o.put("garden", read(new File(c.getFilesDir(), "garden.json")));
        o.put("journal", read(new File(c.getFilesDir(), "journal.txt")));
        return o.toString();
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
            return journal.length() > 0 ? 2 : 1;
        } catch (Exception e) {
            return -1;
        }
    }
}
