package com.dsh.stepcounter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * Необязательная foreground-служба: живое уведомление и мгновенный виджет.
 * Счёт шагов от неё не зависит — датчик считает сам.
 */
public class StepService extends Service implements SensorEventListener {

    private static final String TAG = "StepService";
    private static final String CHANNEL_ID = "steps_foreground";
    private static final int NOTIFICATION_ID = 42;

    public static final String ACTION_START = "com.dsh.stepcounter.START";
    public static final String ACTION_STOP = "com.dsh.stepcounter.STOP";

    private SensorManager sensorManager;
    private Sensor stepCounter;
    private SharedPreferences prefs;
    private NotificationManager notificationManager;

    private long lastNotifyAt = 0L;
    private long lastSaveAt = 0L;
    private int lastNotifiedSteps = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Steps.prefs(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepCounter = sensorManager == null
                ? null : sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        startInForeground(buildNotification(currentSteps()));
        Steps.isServiceRunning = true;

        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "датчик шагов подключён");
        } else {
            Log.w(TAG, "аппаратного счётчика шагов нет");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Steps.isServiceRunning = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length == 0) return;

        long raw = Math.round(event.values[0]);
        long nowMs = System.currentTimeMillis();

        // пишем в журнал не чаще раза в 10 секунд — датчик тикает на каждый шаг
        if (nowMs - lastSaveAt > 10000L) {
            lastSaveAt = nowMs;
            Steps.add(this, nowMs, event.timestamp, raw);
        }
        int steps = Steps.refreshToday(this);

        String today = Steps.today();
        if (steps >= Steps.GOAL && !today.equals(prefs.getString(Steps.KEY_GOAL_DAY, ""))) {
            prefs.edit().putString(Steps.KEY_GOAL_DAY, today).apply();
            vibrateOnce();
        }

        long now = System.currentTimeMillis();
        if (now - lastNotifyAt > 5000L && Math.abs(steps - lastNotifiedSteps) >= 25) {
            lastNotifyAt = now;
            lastNotifiedSteps = steps;
            notificationManager.notify(NOTIFICATION_ID, buildNotification(steps));
            StepWidget.refresh(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private int currentSteps() {
        return prefs == null ? 0 : prefs.getInt(Steps.KEY_STEPS, 0);
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "ШагоСад", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Постоянный счётчик шагов");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        notificationManager.createNotificationChannel(ch);
    }

    private Notification buildNotification(int steps) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = steps >= Steps.GOAL
                ? "Цель достигнута: " + Ui.steps(steps)
                : Ui.steps(steps) + " сегодня";
        String text = steps >= Steps.GOAL ? "Отличная работа!" : "ШагоСад работает в фоне";

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void startInForeground(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "не удалось выйти в foreground: " + e);
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void vibrateOnce() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(220);
            }
        } catch (Exception ignored) { }
    }
}
