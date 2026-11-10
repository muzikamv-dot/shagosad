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

    /**
     * Один «сеанс связи» с датчиком. synchronized: замер могут позвать сразу
     * будильник и разблокировка экрана — второй подождёт, а не полезет в тот же
     * журнал параллельно.
     */
    static synchronized Result sync(Context ctx) {
        return sync(ctx, 1500L);
    }

    /**
     * То же, но с настраиваемым ожиданием после flush: тап по виджету ждёт дольше,
     * потому что телефон отдаёт накопленную пачку шагов с задержкой.
     */
    static synchronized Result sync(Context ctx, long afterFlushMs) {
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

        // Просим сенсорный хаб сбросить буфер: так приходят замеры, сделанные без нас.
        // После flush ждём именно НОВОЕ событие, а не просто полсекунды: часть телефонов
        // отдаёт накопленную пачку с задержкой, и раньше мы уходили со старым значением
        // (виджет по тапу показывал цифру пятиминутной давности).
        int before = events.size();
        long rawBefore = -1L;
        synchronized (events) {
            if (!events.isEmpty()) {
                SensorEvent last = events.get(events.size() - 1);
                if (last != null && last.values != null && last.values.length > 0) {
                    rawBefore = Math.round(last.values[0]);
                }
            }
        }
        try {
            sm.flush(listener);
        } catch (Exception ignored) { }
        long deadline = SystemClock.elapsedRealtime() + Math.max(800L, afterFlushMs);
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean changed = false;
            synchronized (events) {
                if (events.size() > before) {
                    SensorEvent last = events.get(events.size() - 1);
                    if (last != null && last.values != null && last.values.length > 0) {
                        changed = Math.round(last.values[0]) != rawBefore;
                    }
                }
            }
            if (changed) break;                      // пришло новое значение — хватит
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ignored) {
                break;
            }
        }
        try {
            Thread.sleep(150L);      // даём последнему событию осесть в списке
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

        r.today = Steps.refreshToday(ctx);

        r.batching = fifoMax > 0;
        StringBuilder sb = new StringBuilder();
        sb.append(sensor.getName()).append(" · ").append(sensor.getVendor());
        sb.append("\nСчётчик сейчас: ").append(r.raw);
        sb.append("\nСегодня: ").append(r.today).append(" шагов");
        sb.append("\nБатчинг: ").append(fifoMax > 0 ? "да" : "нет");
        if (fifoMax > 0) sb.append(" (буфер ").append(fifoMax).append(", резерв ").append(fifoRes).append(")");
        else sb.append(" — история за прошлое не отдаётся");
        sb.append("\nМакс. задержка датчика: ").append(maxDelay / 1000000L).append(" мс");
        sb.append("\nПолучено за сеанс: ").append(events.size()).append(", новых: ").append(saved);
        sb.append("\nРазрешение ACTIVITY_RECOGNITION: ")
          .append(hasActivityPermission(ctx) ? "есть" : "нет");
        sb.append("\nЗамер: ").append(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date()));
        // время телефона и пояс: по ним видно, «прыгало» ли время у пользователя
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        sb.append("\nДата: ").append(Steps.today())
          .append(", пояс ").append(tz.getID())
          .append(" (").append(tz.getOffset(System.currentTimeMillis()) / 60000).append(" мин)");
        String note = Steps.clockNote(ctx);
        if (note.length() > 0) sb.append("\nВнимание: ").append(note);
        r.diag = sb.toString();
        android.util.Log.i("SensorSync", "событий " + events.size() + ", записано " + saved
                + ", батчинг " + (fifoMax > 0 ? "да" : "нет") + ", raw " + r.raw);

        return r;
    }

    /** Разрешение на распознавание активности: без него счётчик шагов молчит. */
    static boolean hasActivityPermission(Context ctx) {
        try {
            return ctx.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return true;
        }
    }
}
