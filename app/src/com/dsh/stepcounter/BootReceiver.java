package com.dsh.stepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** После перезагрузки: чиним будильник и, если включено, поднимаем службу. */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        Context app = context.getApplicationContext();
        AlarmScheduler.schedule(app);
        SyncJobService.schedule(app);
        AlarmScheduler.scheduleSync(app);

        boolean serviceOn = Steps.prefs(app).getBoolean(Steps.KEY_SERVICE, false);
        if (!serviceOn) {
            Log.i(TAG, "служба выключена — работаем в экономном режиме");
            return;
        }

        try {
            Intent svc = new Intent(app, StepService.class);
            svc.setAction(StepService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(svc);
            } else {
                app.startService(svc);
            }
            Log.i(TAG, "служба запущена после " + action);
        } catch (Exception e) {
            Log.e(TAG, "не удалось запустить службу: " + e);
        }
    }
}
