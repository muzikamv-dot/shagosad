package com.dsh.stepcounter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Статистика шагов: сегодня, рекорды, теплокарта за три месяца и недельная цель.
 *
 * Отдельный экран, чтобы главная вкладка осталась лёгкой: там только кольцо,
 * график и сад. Сюда заходят осознанно — из профиля.
 */
public class StatsActivity extends Activity {

    private GameState game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.load(this);
        setTheme(Theme.styleRes());
        super.onCreate(savedInstanceState);
        Theme.bars(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        game = GameState.load(this);
        setContentView(build());
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private int stepsToday() {
        int cached = Steps.prefs(this).getInt(Steps.KEY_STEPS, 0);
        int[] last = Steps.lastDays(this, 1);
        return Math.max(cached, last.length > 0 ? last[0] : 0);
    }

    private View build() {
        final int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM}));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(0, in.getSystemWindowInsetTop(), 0, in.getSystemWindowInsetBottom());
                return in;
            }
        });

        // ---------- шапка ----------
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, dp(12), pad, dp(6));

        TextView title = new TextView(this);
        title.setText("СТАТИСТИКА");
        title.setTextColor(Theme.ACCENT_SOFT);
        title.setTextSize(Ui.T_TITLE);
        title.setLetterSpacing(0.10f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("закрыть");
        close.setTextColor(Theme.MUTED);
        close.setTextSize(12);
        close.setPadding(dp(8), dp(6), 0, dp(6));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(close);
        root.addView(head);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, dp(8), pad, dp(24));
        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int steps = stepsToday();
        int[] week = Steps.lastDays(this, 7);
        int[] month = Steps.lastDays(this, 30);
        int[] quarter = Steps.lastDays(this, 91);

        // ---------- сегодня ----------
        boolean[] est = new boolean[1];
        int minutes = Steps.minutesShown(this, steps, est);
        String kmText = String.format(Locale.US, "%.1f", steps * Steps.strideMeters(this) / 1000.0)
                .replace('.', ',');
        col.addView(card(new String[][]{
                {Ui.steps(steps), "сегодня"},
                {"≈ " + kmText + " км", "расстояние"},
                {(est[0] ? "≈ " : "") + minutes + " мин", "в движении"}
        }));

        // ---------- калории (только если включены в настройках) ----------
        if (Steps.showCalories(this)) {
            TextView cal = new TextView(this);
            cal.setText("≈ " + fmt(Steps.calories(this, steps)) + " ккал — оценка по весу ("
                    + Steps.weightKg(this) + " кг) и шагам. Точной она не бывает.");
            cal.setTextColor(Theme.FAINT);
            cal.setTextSize(10);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = dp(8);
            col.addView(cal, clp);
        }

        // ---------- рекорды ----------
        int best = 0, bestIdx = -1;
        for (int i = 0; i < quarter.length; i++) {
            if (quarter[i] > best) { best = quarter[i]; bestIdx = i; }
        }
        int sumMonth = 0, daysMonth = 0;
        for (int v : month) { sumMonth += v; if (v > 0) daysMonth++; }
        int sumWeek = 0;
        for (int v : week) sumWeek += v;
        int avgMonth = daysMonth > 0 ? Math.round(sumMonth / (float) daysMonth) : 0;
        int avgWeek = Math.round(sumWeek / 7f);

        col.addView(section("РЕКОРДЫ"));
        col.addView(card(new String[][]{
                {Ui.steps(best), "лучший день"},
                {Ui.steps(avgMonth), "среднее за месяц"},
                {Ui.steps(avgWeek), "среднее за неделю"},
                {game.streak + " дн.", "серия подряд"}
        }));

        // ---------- недельная цель ----------
        // недельная цель = семь дневных норм и меняется вместе с нормой
        final int weekGoal = Math.max(1000, game.goal) * 7;
        int percent = Math.min(100, Math.round(sumWeek * 100f / weekGoal));
        col.addView(section("НЕДЕЛЯ"));
        col.addView(weekCard(sumWeek, weekGoal, percent));

        // ---------- календарь месяца ----------
        col.addView(section("КАЛЕНДАРЬ"));
        col.addView(calendarBlock());
        return root;
    }

    /** Календарь месяца: шапка с переключением и сам календарь. */
    private View calendarBlock() {
        final java.util.Map<String, Integer> all = Steps.mergedDays(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(8);
        box.addView(head, hp);

        final TextView title = new TextView(this);
        title.setTextColor(Theme.TEXT);
        title.setTextSize(14);
        title.setTypeface(Ui.medium());
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView prev = new TextView(this);
        prev.setText("‹");
        final TextView next = new TextView(this);
        next.setText("›");
        for (TextView arrow : new TextView[]{prev, next}) {
            arrow.setTextColor(Theme.ACCENT_SOFT);
            arrow.setTextSize(22);
            arrow.setGravity(Gravity.CENTER);
            arrow.setPadding(dp(12), 0, dp(12), 0);
        }
        head.addView(prev);
        head.addView(next);

        final MonthCalendarView view = new MonthCalendarView(this);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        vp.topMargin = dp(6);
        box.addView(view, vp);

        final TextView summary = new TextView(this);
        summary.setTextColor(Theme.FAINT);
        summary.setTextSize(10);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        box.addView(summary, sp);

        final String[] months = {"январь", "февраль", "март", "апрель", "май", "июнь",
                "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};

        final java.util.Calendar now = java.util.Calendar.getInstance();
        final int thisYear = now.get(java.util.Calendar.YEAR);
        final int thisMonth = now.get(java.util.Calendar.MONTH);

        final Runnable[] show = new Runnable[1];
        show[0] = new Runnable() {
            @Override public void run() {
                int y = view.year();
                int m = view.month();
                int days = new java.util.GregorianCalendar(y, m, 1)
                        .getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
                int[] steps = new int[days];
                int sum = 0, active = 0;
                for (int day = 1; day <= days; day++) {
                    steps[day - 1] = MonthCalendarView.stepsOf(all, y, m, day);
                    sum += steps[day - 1];
                    if (steps[day - 1] > 0) active++;
                }
                view.setMonth(y, m, steps, game.goal);
                title.setText(months[m] + " " + y);
                summary.setText(fmt(sum) + " шагов за месяц · дней с прогулкой: " + active);
                // будущие месяцы листать некуда
                next.setTextColor((y == thisYear && m >= thisMonth) ? Theme.FAINT : Theme.ACCENT_SOFT);
            }
        };
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int y = view.year(), m = view.month() - 1;
                if (m < 0) { m = 11; y--; }
                view.setMonth(y, m, new int[31], game.goal);
                show[0].run();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int y = view.year(), m = view.month() + 1;
                if (m > 11) { m = 0; y++; }
                if (y > thisYear || (y == thisYear && m > thisMonth)) return;
                view.setMonth(y, m, new int[31], game.goal);
                show[0].run();
            }
        });

        show[0].run();
        return box;
    }
    /** Секция капсом, как в остальных экранах. */
    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Theme.FAINT);
        t.setTextSize(Ui.T_CAPS);
        t.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(18);
        t.setLayoutParams(p);
        return t;
    }

    /** Карточка с колонками «значение — подпись». */
    private View card(String[][] items) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Theme.CARD_STROKE);
        box.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8);
        box.setLayoutParams(bp);

        for (String[] it : items) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            TextView value = new TextView(this);
            value.setText(it[0]);
            value.setTextColor(Theme.TEXT);
            value.setTextSize(17);
            value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            value.setGravity(Gravity.CENTER);
            cell.addView(value);
            TextView label = new TextView(this);
            label.setText(it[1]);
            label.setTextColor(Theme.FAINT);
            label.setTextSize(9.5f);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(3), 0, 0);
            cell.addView(label);
            box.addView(cell, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return box;
    }

    /** Недельная цель: полоска прогресса и подпись. */
    private View weekCard(int sum, int goal, int percent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Theme.CARD_STROKE);
        box.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8);
        box.setLayoutParams(bp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView left = new TextView(this);
        left.setText(Ui.steps(sum) + " из " + fmt(goal));
        left.setTextColor(Theme.TEXT);
        left.setTextSize(13);
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView right = new TextView(this);
        right.setText(percent + "%");
        right.setTextColor(percent >= 100 ? Theme.GOLD : Theme.ACCENT_SOFT);
        right.setTextSize(13);
        right.setTypeface(Ui.medium());
        row.addView(right);
        box.addView(row);

        View bar = new View(this);
        GradientDrawable track = new GradientDrawable();
        track.setColor(Theme.SURFACE_HI);
        track.setCornerRadius(dp(5));
        bar.setBackground(track);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        blp.topMargin = dp(10);
        box.addView(bar, blp);

        // полоска прогресса: ProgressBar с заливкой в акцентный цвет
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(Math.max(0, Math.min(100, percent)));
        try {
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(
                    percent >= 100 ? Theme.GOLD : Theme.ACCENT));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Theme.SURFACE_HI));
            progress.setScaleY(0.7f);
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        plp.topMargin = dp(6);
        box.addView(progress, plp);
        return box;
    }

    private String fmt(int n) {
        return String.format(Locale.US, "%,d", n).replace(',', ' ');
    }
}
