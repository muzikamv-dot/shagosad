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
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    SensorSync.sync(app);
                    GameState g = GameState.load(app);
                    g.checkMissedDays(app);
                    g.save(app);
                    StepWidget.refresh(app);
                } catch (Exception ignored) {
                }
            }
        }, "sync-once").start();
    }
}
