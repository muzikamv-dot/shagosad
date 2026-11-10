package com.dsh.stepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Нажатия на виджет.
 *
 * Одинарный тап — быстро прочитать счётчик шагов и перерисовать виджет, приложение
 * при этом не открывается. Двойной тап — открыть приложение.
 *
 * Почему так: будильник каждые три минуты будил бы телефон ради одной цифры.
 * Читаем счётчик, только когда человек сам его спросил, и оставляем замер
 * раз в 15 минут как обычно — так расход батареи не растёт.
 */
public class WidgetClickReceiver extends BroadcastReceiver {

    /** Пауза, в пределах которой второй тап считается двойным. */
    private static final long DOUBLE_MS = 420L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingSingle;
    private static long lastTapAt = 0L;

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        boolean doubleTap = pendingSingle != null && now - lastTapAt <= DOUBLE_MS;
        lastTapAt = now;

        if (doubleTap) {
            // второй тап: отменяем одиночное действие и открываем приложение
            MAIN.removeCallbacks(pendingSingle);
            pendingSingle = null;
            Intent open = new Intent(app, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                app.startActivity(open);
            } catch (Exception ignored) { }
            return;
        }

        if (pendingSingle != null) MAIN.removeCallbacks(pendingSingle);
        pendingSingle = new Runnable() {
            @Override public void run() {
                pendingSingle = null;
                // одинарный тап: в фоне забираем свежие шаги, приёмник сам обновит виджет
                SyncReceiver.runOnce(app);
            }
        };
        MAIN.postDelayed(pendingSingle, DOUBLE_MS);
    }
}
