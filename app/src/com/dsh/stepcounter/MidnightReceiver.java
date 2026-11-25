package com.dsh.stepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Полночь: снимаем показание счётчика и закрываем день. */
public class MidnightReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        // goAsync держит процесс живым, пока датчик отдаёт показание:
        // без него Android может убить receiver сразу после return.
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (ConsentActivity.agreed(app)) {
                        // Ждём датчик дольше обычного: в полночь нам нужен
                        // именно замер на границе суток, а не «сегодня утром».
                        SensorSync.sync(app, 3000L);
                        GameState g = GameState.load(app);
                        g.checkMissedDays(app);
                        g.save(app);
                        Steps.prefs(app).edit()
                                .putLong("last_midnight_sync", System.currentTimeMillis())
                                .apply();
                    }
                } catch (Exception ignored) {
                } finally {
                    AlarmScheduler.schedule(app);
                    pending.finish();
                }
            }
        }, "midnight-sync").start();
    }
}
