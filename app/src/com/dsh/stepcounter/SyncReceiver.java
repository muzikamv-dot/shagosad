package com.dsh.stepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Частый замер шагов по будильнику.
 *
 * Зачем: шаги за день считаются по разнице между замерами. Если замеров мало,
 * шаги за «дыру» делятся между днями по времени, и сегодняшние теряются.
 * Будильник с setAndAllowWhileIdle пробивается через сон устройства,
 * в отличие от фоновой задачи, которую система откладывает на часы.
 *
 * Тот же замер вызывается из UnlockReceiver при разблокировке экрана.
 */
public class SyncReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        // следующая побудка — сразу, не дожидаясь успеха замера
        AlarmScheduler.scheduleSync(app);

        // goAsync держит процесс, пока датчик отдаёт показание. Без него
        // Android убивал поток замера сразу после return — шаги не писались.
        final BroadcastReceiver.PendingResult pending = goAsync();
        runOnce(app, 1500L, pending);
    }

    /** Один замер в фоне: забрать показания, проверить пропуски, обновить виджет. */
    static void runOnce(final Context context) {
        runOnce(context, 1500L, null);
    }

    /** Замер с настраиваемым ожиданием пачки от датчика (тап по виджету ждёт дольше). */
    static void runOnce(final Context context, final long waitMs) {
        runOnce(context, waitMs, null);
    }

    static void runOnce(final Context context, final long waitMs,
                        final BroadcastReceiver.PendingResult pending) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    syncNow(app, waitMs);
                } finally {
                    if (pending != null) pending.finish();
                }
            }
        }, "sync-once").start();
    }

    /** Синхронный замер: ждёт датчик, сохраняет шаги и перерисовывает виджет. */
    static void syncNow(Context context, long waitMs) {
        Context app = context.getApplicationContext();
        try {
            SensorSync.sync(app, waitMs);
            GameState g = GameState.load(app);
            g.repairMidnightSteps(app);
            g.checkMissedDays(app);
            g.save(app);
            Steps.prefs(app).edit()
                    .putLong("last_step_sync", System.currentTimeMillis())
                    .remove("widget_busy_at")
                    .apply();
        } catch (Exception ignored) {
        }
    }
}
