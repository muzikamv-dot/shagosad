package com.dsh.stepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Сторож фоновой службы. Приходит по AlarmManager раз в 15 минут (и через
 * несколько секунд после свайпа из «Недавних»). Если служба мертва — пытается
 * поднять её заново; следующий визит ставит сам себя снова.
 */
public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = "ServiceRestart";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        AlarmScheduler.scheduleServiceWatchdog(app);

        if (!ConsentActivity.agreed(app)) return;
        if (!Steps.serviceEnabled(app)) return;
        if (!SensorSync.hasActivityPermission(app)) return;
        if (Steps.isServiceRunning) return;

        try {
            Intent svc = new Intent(app, StepService.class);
            svc.setAction(StepService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(svc);
            } else {
                app.startService(svc);
            }
            Log.i(TAG, "служба перезапущена сторожем");
        } catch (Exception e) {
            Log.e(TAG, "не удалось перезапустить службу: " + e);
        }
    }
}
