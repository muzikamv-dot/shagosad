package com.dsh.stepcounter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** Виджет 2×2 на рабочий стол: шаги без открытия приложения. */
public class StepWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, build(context));
        }
    }

    /** Обновить все виджеты (вызывается службой при изменении шагов). */
    static void refresh(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, StepWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews v = build(ctx);
            for (int id : ids) m.updateAppWidget(id, v);
        } catch (Exception ignored) { }
    }

    private static RemoteViews build(Context ctx) {
        SharedPreferences p = Steps.prefs(ctx);
        int steps = p.getInt(Steps.KEY_STEPS, 0);

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_stepper);
        v.setTextViewText(R.id.widget_steps, String.valueOf(steps));

        String caption;
        if (!Steps.hasSensor(ctx)) caption = "нет счётчика шагов";
        int goal = Steps.prefs(ctx).getInt("goal", 10000);
        if (goal < 1000) goal = 10000;
        if (steps >= goal) caption = "цель достигнута";
        else caption = "из " + goal + " · " + (steps * 100 / goal) + "%";
        v.setTextViewText(R.id.widget_caption, caption);

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_steps, pi);
        return v;
    }
}
