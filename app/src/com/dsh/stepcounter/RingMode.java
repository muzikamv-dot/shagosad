package com.dsh.stepcounter;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Как рисовать кольцо шагов на главной.
 *
 * Меняется в настройках, хранится в отдельном preference-файле, чтобы переживать
 * перезапуск. По умолчанию — объёмное (как сейчас нравится владельцу).
 */
public final class RingMode {

    /** Остался один вид кольца — плоское. Объёмное убрано по просьбе владельца. */
    public static final int FLAT = 0;

    public static final String[] NAMES = {"Плоское"};

    private static final String PREFS = "ring_style";
    private static final String KEY = "mode";

    private RingMode() { }

    public static int get(Context c) {
        return FLAT;
    }

    public static void set(Context c, int mode) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, FLAT).apply();
    }

    public static String name(int mode) {
        return NAMES[0];
    }
}
