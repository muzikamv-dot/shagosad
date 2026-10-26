package com.dsh.stepcounter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Один «сеанс связи» с датчиком: забираем текущее значение, просим хаб отдать
 * накопленный буфер (батчинг) и раскладываем всё по журналу с метками времени.
 */
final class SensorSync {

    static final class Result {
        boolean hasSensor;
        int today;
        int raw;
        int batchSize;
        String diag = "";
        boolean batching;
    }

    private SensorSync() { }

    static Result sync(Context ctx) {
        Result r = new Result();

        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) {
            r.hasSensor = false;
            r.diag = "Датчик шагов не найден";
            return r;
        }
        r.hasSensor = true;

        final List<SensorEvent> events = Collections.synchronizedList(new ArrayList<SensorEvent>());
        final CountDownLatch first = new CountDownLatch(1);

        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                events.add(e);
                first.countDown();
            }
            @Override public void onAccuracyChanged(Sensor s, int accuracy) { }
        };

        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        try {
            first.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) { }

        // просим сенсорный хаб сбросить буфер: так приходят замеры, сделанные без нас
        try {
            sm.flush(listener);
        } catch (Exception ignored) { }
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) { }

        sm.unregisterListener(listener);

        // пересчёт времени события в «настенное» время телефона
        long anchorWall = System.currentTimeMillis();
        long anchorElapsed = SystemClock.elapsedRealtimeNanos();

        int saved = 0;
        synchronized (events) {
            List<long[]> batch = new ArrayList<>(events.size());
            for (SensorEvent e : events) {
                if (e == null || e.values == null || e.values.length == 0) continue;
                long wall = anchorWall - (anchorElapsed - e.timestamp) / 1000000L;
                long raw = Math.round(e.values[0]);
                batch.add(new long[]{wall, e.timestamp, raw});
                r.raw = (int) raw;
            }
            saved = Steps.addAll(ctx, batch);
            r.batchSize = events.size();
        }

        int fifoMax = sensor.getFifoMaxEventCount();
        int fifoRes = sensor.getFifoReservedEventCount();
        long maxDelay = sensor.getMaxDelay();

        r.batching = fifoMax > 0;
        StringBuilder sb = new StringBuilder();
        sb.append(sensor.getName()).append(" · ").append(sensor.getVendor());
        sb.append("\nБатчинг: ").append(fifoMax > 0 ? "да" : "нет");
        if (fifoMax > 0) sb.append(" (буфер ").append(fifoMax).append(", резерв ").append(fifoRes).append(")");
        sb.append("\nМакс. задержка датчика: ").append(maxDelay / 1000000L).append(" мс");
        sb.append("\nПолучено за сеанс: ").append(events.size()).append(", новых: ").append(saved);
        r.diag = sb.toString();
        android.util.Log.i("SensorSync", "событий " + events.size() + ", записано " + saved
                + ", батчинг " + (fifoMax > 0 ? "да" : "нет") + ", raw " + r.raw);

        r.today = Steps.refreshToday(ctx);
        return r;
    }
}
