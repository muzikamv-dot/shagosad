package com.dsh.stepcounter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Один будильник в сутки — чтобы день закрывался ровно в полночь. */
final class AlarmScheduler {

    private AlarmScheduler() { }

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
}
