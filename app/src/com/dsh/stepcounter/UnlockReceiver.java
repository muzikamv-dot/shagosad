package com.dsh.stepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Замер шагов в момент разблокировки телефона.
 *
 * Зачем: будильник срабатывает раз в 15 минут, и шаги, пройденные между двумя
 * замерами, живут только в счётчике. Если телефон перезагрузят, этот хвост
 * исчезает. Разблокировка — момент, когда телефон и так проснулся, поэтому
 * лишнего пробуждения не нужно: замер здесь бесплатный по батарее.
 */
public class UnlockReceiver extends BroadcastReceiver {

    /** Чаще раза в 2 минуты не замеряем: разблокировок за день бывает много. */
    private static final long MIN_GAP_MS = 2 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_USER_PRESENT.equals(intent.getAction())) return;

        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        long last = Steps.prefs(app).getLong("last_unlock_sync", 0L);
        if (now - last < MIN_GAP_MS) return;
        Steps.prefs(app).edit().putLong("last_unlock_sync", now).apply();

        SyncReceiver.runOnce(app);
    }
}
