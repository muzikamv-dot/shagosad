package com.dsh.stepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Полночь: снимаем показание счётчика и закрываем день. */
public class MidnightReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    SensorSync.sync(app);
                } catch (Exception ignored) { }
                AlarmScheduler.schedule(app);
                StepWidget.refresh(app);
            }
        }).start();
    }
}
