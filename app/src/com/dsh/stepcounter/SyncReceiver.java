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


        runOnce(app);
    }

    /** Один замер в фоне: забрать показания, проверить пропуски, обновить виджет. */
    static void runOnce(final Context context) {
        runOnce(context, 1500L);
    }

    /** Замер с настраиваемым ожиданием пачки от датчика (тап по виджету ждёт дольше). */
    static void runOnce(final Context context, final long waitMs) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                syncNow(app, waitMs);
            }
        }, "sync-once").start();
    }

    /** Синхронный замер: ждёт датчик, сохраняет шаги и перерисовывает виджет. */
    static void syncNow(Context context, long waitMs) {
        Context app = context.getApplicationContext();
        try {
            SensorSync.sync(app, waitMs);
            GameState g = GameState.load(app);
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
