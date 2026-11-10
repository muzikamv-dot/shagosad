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

    // выезжающая панель статистики за день
    private android.widget.FrameLayout host;
    private android.view.View dayPanel;
    private android.view.View dayScrim;
    private android.widget.TextView dayTitle, daySteps, dayKm, dayTime, dayKcal, dayHint;
    private DayHoursView dayChart;

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
        boolean calOn = Steps.showCalories(this);
        if (calOn) {
            col.addView(card(new String[][]{
                    {fmt(steps), "сегодня"},
                    {"≈ " + kmText + " км", "расстояние"},
                    {(est[0] ? "≈ " : "") + Ui.duration(minutes), "в движении"},
                    {"≈ " + fmt(Steps.calories(this, steps)), "ккал"}
            }, 4));
        } else {
            col.addView(card(new String[][]{
                    {fmt(steps), "сегодня"},
                    {"≈ " + kmText + " км", "расстояние"},
                    {(est[0] ? "≈ " : "") + Ui.duration(minutes), "в движении"}
            }, 3));
        }

        // ---------- калории (только если включены в настройках) ----------
        if (Steps.showCalories(this)) {
            TextView cal = new TextView(this);
            cal.setText("ккал — оценка по весу (" + Steps.weightKg(this)
                    + " кг) и шагам, точной не бывает.");
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

        // в статистике ШАГОВ серия — про норму шагов, а не про поливы
        int normDays = 0;
        for (int v : quarter) if (v >= game.goal) normDays++;
        int normsStreak = Steps.stepsStreak(this, game.goal);
        int actStreak = Steps.activityStreak(this);

        col.addView(section("РЕКОРДЫ"));
        col.addView(card(new String[][]{
                {fmt(best), "лучший день"},
                {fmt(avgMonth), "средн. за месяц"},
                {actStreak + " дн.", "серия активности"},
                {normsStreak + " дн.", "серия нормы"}
        }));
        col.addView(card(new String[][]{
                {fmt(avgWeek), "средн. за неделю"},
                {fmt(sumMonth), "шагов за 30 дней"},
                {normDays + " дн.", "дней с нормой"}
        }, 3));

        // ---------- недельная цель ----------
        // недельная цель = семь дневных норм и меняется вместе с нормой
        final int weekGoal = Math.max(1000, game.goal) * 7;
        int percent = Math.min(100, Math.round(sumWeek * 100f / weekGoal));
        col.addView(section("НЕДЕЛЯ"));
        col.addView(weekCard(sumWeek, weekGoal, percent));

        // ---------- календарь месяца ----------
        col.addView(section("КАЛЕНДАРЬ"));
        col.addView(calendarBlock());

        // панель за день живёт поверх содержимого и выезжает сверху
        host = new android.widget.FrameLayout(this);
        host.addView(root, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildDayPanel();
        return host;
    }


    /** Панель «статистика за день»: карточка с цифрами и графиком по часам. */
    private void buildDayPanel() {
        // затемнение: тап по нему закрывает панель
        dayScrim = new android.view.View(this);
        dayScrim.setBackgroundColor(0x99000000);
        dayScrim.setVisibility(android.view.View.GONE);
        dayScrim.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { hideDayPanel(); }
        });
        host.addView(dayScrim, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.SURFACE_HI, Theme.BG_BOTTOM});
        float r = dp(22);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        bg.setStroke(dp(1), Theme.STROKE);
        panel.setBackground(bg);
        // отступ сверху с учётом строки состояния: иначе дата прячется под часами
        int statusH = 0;
        try {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) statusH = getResources().getDimensionPixelSize(id);
        } catch (Exception ignored) { }
        panel.setPadding(dp(16), dp(14) + statusH, dp(16), dp(18));

        // шапка: дата и крестик
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        dayTitle = new android.widget.TextView(this);
        dayTitle.setTextColor(Theme.TEXT);
        dayTitle.setTextSize(15);
        dayTitle.setTypeface(Ui.medium());
        head.addView(dayTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.TextView close = new android.widget.TextView(this);
        close.setText("закрыть");
        close.setTextColor(Theme.ACCENT_SOFT);
        close.setTextSize(12);
        close.setPadding(dp(10), dp(4), dp(2), dp(4));
        close.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { hideDayPanel(); }
        });
        head.addView(close);
        panel.addView(head);

        // цифры за день
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(10);
        panel.addView(row, rp);

        daySteps = dayCell(row, "шаги");
        dayKm = dayCell(row, "км");
        dayTime = dayCell(row, "в движении");
        dayKcal = dayCell(row, "ккал");

        dayChart = new DayHoursView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
        cp.topMargin = dp(12);
        panel.addView(dayChart, cp);

        dayHint = new android.widget.TextView(this);
        dayHint.setTextColor(Theme.FAINT);
        dayHint.setTextSize(10.5f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(6);
        panel.addView(dayHint, hp);

        dayPanel = panel;
        dayPanel.setVisibility(android.view.View.GONE);
        android.widget.FrameLayout.LayoutParams pp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.TOP;
        host.addView(dayPanel, pp);
    }

    /** Маленькая ячейка «число + подпись». */
    private android.widget.TextView dayCell(LinearLayout parent, String caption) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        parent.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.TextView value = new android.widget.TextView(this);
        value.setTextColor(Theme.TEXT);
        value.setTextSize(15);
        value.setTypeface(Ui.light());
        value.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(value);

        android.widget.TextView cap = new android.widget.TextView(this);
        cap.setText(caption);
        cap.setTextColor(Theme.FAINT);
        cap.setTextSize(9);
        cap.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(cap);
        return value;
    }

    private static final String[] MONTH_NAMES = {"января", "февраля", "марта", "апреля",
            "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    /** Показать панель за конкретный день. */
    void showDayPanel(int year, int month0, int day) {
        if (dayPanel == null) return;
        int[] stats = Steps.dayStats(this, year, month0, day);
        int steps = stats[0];
        int minutes = stats[1];
        int[] hours = Steps.hourly(this, year, month0, day);
        int[] peak = Steps.peakHour(hours);

        dayTitle.setText(day + " " + MONTH_NAMES[month0] + " " + year);
        daySteps.setText(fmt(steps));
        String km = String.format(Locale.US, "%.1f", steps * Steps.strideMeters(this) / 1000.0)
                .replace('.', ',');
        dayKm.setText(km);
        dayTime.setText(minutes > 0 ? Ui.duration(minutes) : "—");
        dayKcal.setText(Steps.showCalories(this) && steps > 0
                ? "≈ " + fmt(Steps.calories(this, steps)) : "—");
        dayChart.setHours(hours);

        if (steps <= 0) {
            dayHint.setText("за этот день записей нет");
        } else if (peak[0] >= 0) {
            dayHint.setText("максимальная активность — " + peak[0] + ":00, "
                    + fmt(peak[1]) + " шагов");
        } else {
            dayHint.setText("шаги за день: " + fmt(steps));
        }

        dayScrim.setVisibility(android.view.View.VISIBLE);
        dayPanel.setVisibility(android.view.View.VISIBLE);
        dayPanel.post(new Runnable() {
            @Override public void run() {
                dayPanel.setTranslationY(-dayPanel.getHeight() - dp(10));
                dayPanel.animate().translationY(0f).setDuration(240L).start();
            }
        });
    }

    /** Убрать панель. */
    void hideDayPanel() {
        if (dayPanel == null || dayPanel.getVisibility() != android.view.View.VISIBLE) return;
        dayScrim.setVisibility(android.view.View.GONE);
        dayPanel.animate().translationY(-dayPanel.getHeight() - dp(10)).setDuration(200L)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        dayPanel.setVisibility(android.view.View.GONE);
                    }
                }).start();
    }

    @Override
    public void onBackPressed() {
        if (dayPanel != null && dayPanel.getVisibility() == android.view.View.VISIBLE) {
            hideDayPanel();
            return;
        }
        super.onBackPressed();
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
        view.setOnDayTap(new MonthCalendarView.OnDayTap() {
            @Override public void onDay(int y, int m, int d) {
                view.setSelectedDay(d);
                showDayPanel(y, m, d);
            }
        });
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
        return card(items, items.length);
    }

    /** Карточка, где размер цифры зависит от числа ячеек: четыре — мельче. */
    private View card(String[][] items, int cells) {
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
            value.setTextSize(cells >= 4 ? 14f : 17f);
            // «40 000» и «100 000» не должны вылезать из своей ячейки
            value.setSingleLine(true);
            value.setAutoSizeTextTypeUniformWithConfiguration(9, (int) (cells >= 4 ? 14 : 17), 1,
                    TypedValue.COMPLEX_UNIT_SP);
            value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            value.setGravity(Gravity.CENTER);
            cell.addView(value);
            TextView label = new TextView(this);
            label.setText(it[1]);
            label.setTextColor(Theme.FAINT);
            label.setTextSize(cells >= 4 ? 8.5f : 9.5f);
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
