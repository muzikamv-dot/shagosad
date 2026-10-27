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
 */
public class SyncReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        // следующая побудка — сразу, не дожидаясь успеха замера
        AlarmScheduler.scheduleSync(app);

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
        }, "sync-alarm").start();
    }
}
