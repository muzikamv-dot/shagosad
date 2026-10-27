package com.dsh.stepcounter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Будильники приложения: полночь и частый замер шагов. */
final class AlarmScheduler {

    /** Как часто забирать показания счётчика в фоне. */
    static final long SYNC_EVERY_MS = 15 * 60 * 1000L;

    private AlarmScheduler() { }

    /** Один будильник в сутки — чтобы день закрывался ровно в полночь. */
    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long at = Steps.endOfDay(System.currentTimeMillis()) + 5000L;

        Intent i = new Intent(ctx, MidnightReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }

    /**
     * Частый замер шагов. Задача JobScheduler в глубоком сне откладывается
     * на часы, а setAndAllowWhileIdle пробивается раз в 15 минут — иначе
     * между замерами образуется дыра, и шаги за неё делятся между днями
     * «по времени», теряя сегодняшние.
     */
    static void scheduleSync(Context ctx) {
        scheduleSyncIn(ctx, SYNC_EVERY_MS);
    }

    static void scheduleSyncIn(Context ctx, long delayMs) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, SyncReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long at = System.currentTimeMillis() + Math.max(60_000L, delayMs);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Exception ignored) { }
    }
}
