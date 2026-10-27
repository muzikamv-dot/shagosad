package com.dsh.stepcounter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Экран «Достижения»: список всех медалей с прогрессом.
 *
 * Раньше это была пятая вкладка, но место внизу понадобилось для «Ивентов»,
 * поэтому достижения открываются кнопкой из профиля (Магазин и Коллекция,
 * под ними Инвентарь и Достижения).
 */
public class AchievementsActivity extends Activity {

    private GameState game;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.load(this);
        Theme.bars(this);
        game = GameState.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG_TOP);
        root.setPadding(dp(18), statusBarHeight() + dp(10), dp(18), 0);

        TextView title = new TextView(this);
        title.setText("ДОСТИЖЕНИЯ");
        title.setTextColor(Theme.ACCENT_SOFT);
        title.setTextSize(Ui.T_TITLE);
        title.setLetterSpacing(0.10f);
        title.setIncludeFontPadding(false);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));

        TextView close = new TextView(this);
        close.setText("закрыть");
        close.setTextColor(Theme.MUTED);
        close.setTextSize(12);
        close.setPadding(dp(10), dp(4), 0, dp(4));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(close);
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, dp(20));

        int opened = 0;
        for (Achievements.Ach a : Achievements.ALL) {
            if (game.unlocked.contains(a.id)) opened++;
        }

        TextView counter = new TextView(this);
        counter.setText("ОТКРЫТО " + opened + " ИЗ " + Achievements.ALL.size());
        counter.setTextColor(Theme.FAINT);
        counter.setTextSize(10);
        counter.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(8);
        cp.bottomMargin = dp(10);
        list.addView(counter, cp);

        for (Achievements.Ach a : Achievements.ALL) {
            list.addView(card(a));
        }

        scroll.addView(list, new FrameLayoutParams());
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    /** Обёртка, чтобы не тащить android.widget.FrameLayout.LayoutParams целиком. */
    private static class FrameLayoutParams extends android.widget.FrameLayout.LayoutParams {
        FrameLayoutParams() { super(android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT); }
    }

    private int daySteps() {
        return Steps.refreshToday(this);
    }

    private View card(Achievements.Ach a) {
        boolean on = game.unlocked.contains(a.id);
        int cur = Math.min(Achievements.progress(a, game, daySteps()), a.target);
        boolean levelAch = a.metric == Achievements.Metric.LEVEL;
        if (levelAch) cur = Math.max(0, Math.min(a.target - 1, game.level() - 1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(on ? Theme.SURFACE_HI : Theme.SURFACE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), on ? Theme.ACCENT_DEEP : Theme.CARD_STROKE);
        row.setBackground(bg);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));

        ImageView badge = new ImageView(this);
        try {
            int rid = getResources().getIdentifier("medal_" + a.id, "drawable", getPackageName());
            if (rid != 0) badge.setImageResource(rid);
            if (on) {
                badge.setColorFilter(null);
                badge.setAlpha(1f);
            } else {
                badge.setColorFilter(Theme.FAINT, android.graphics.PorterDuff.Mode.MULTIPLY);
                badge.setAlpha(0.65f);
            }
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(38), dp(38));
        blp.rightMargin = dp(10);
        row.addView(badge, blp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(a.title);
        t.setTextSize(13.5f);
        t.setTextColor(on ? Theme.TEXT : Theme.MUTED);
        col.addView(t);

        TextView d = new TextView(this);
        d.setText(a.desc);
        d.setTextSize(10);
        d.setTextColor(on ? Theme.MUTED : Theme.FAINT);
        col.addView(d);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(levelAch ? Math.max(1, a.target - 1) : a.target);
        bar.setProgress(cur);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        brp.topMargin = dp(6);
        col.addView(bar, brp);

        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(this);
        state.setText(on ? "готово"
                : (levelAch ? "уровень " + game.level() + " из " + a.target
                            : cur + " / " + a.target));
        state.setTextSize(10);
        state.setTextColor(on ? Theme.ACCENT : Theme.FAINT);
        state.setPadding(dp(10), 0, 0, 0);
        row.addView(state);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        row.setLayoutParams(rp);
        return row;
    }

    /** Высота строки состояния: иначе заголовок наезжает на часы. */
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
