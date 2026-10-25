package com.dsh.stepcounter;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Короткая тактильная отдача: приложение отвечает рукой, а не только глазами. */
public final class Haptics {

    private static long last = 0;

    private Haptics() { }

    /** Лёгкий щелчок: смена вкладки, полив. */
    public static void tap(Context c) { play(c, 26L); }

    /** Заметнее: собран цветок. */
    public static void bloom(Context c) { play(c, 45L); }

    private static void play(Context c, long ms) {
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - last < 35) return;   // не дребезжим при быстрых тапах
        last = now;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) { }
    }
}
