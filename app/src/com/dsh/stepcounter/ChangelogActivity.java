package com.dsh.stepcounter;

import android.app.Activity;
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

/** История изменений: тот же язык, что у остальных экранов — карточки по датам. */
public class ChangelogActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.load(this);
        setTheme(Theme.styleRes());
        super.onCreate(savedInstanceState);
        Theme.bars(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(build());
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
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

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, dp(12), pad, dp(8));

        TextView title = new TextView(this);
        title.setText("ИСТОРИЯ");
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
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(10), pad, dp(24));

        for (int i = 0; i < Changelog.DAYS.length; i++) {
            box.addView(dayCard(Changelog.DAYS[i], i == 0));
        }

        scroll.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View dayCard(Changelog.Day day, boolean current) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(current ? Theme.SURFACE_HI : Theme.SURFACE);
        bg.setStroke(dp(1), current ? Theme.ACCENT : Theme.CARD_STROKE);
        card.setBackground(bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(12);
        card.setLayoutParams(clp);

        // строка даты: слева дата, справа версии этой даты
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView date = new TextView(this);
        date.setText(day.date.toUpperCase());
        date.setTextColor(current ? Theme.ACCENT_SOFT : Theme.MUTED);
        date.setTextSize(11);
        date.setLetterSpacing(0.08f);
        date.setTypeface(Ui.medium());
        top.addView(date, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (day.versions.length() > 0) {
            TextView chip = new TextView(this);
            chip.setText(day.versions);
            chip.setTextColor(Theme.FAINT);
            chip.setTextSize(10);
            GradientDrawable cbg = new GradientDrawable();
            cbg.setCornerRadius(dp(10));
            cbg.setColor(Theme.CHIP);
            chip.setBackground(cbg);
            chip.setPadding(dp(8), dp(3), dp(8), dp(3));
            top.addView(chip);
        }
        card.addView(top);

        for (String item : day.items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(9);

            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(current ? Theme.ACCENT : Theme.ACCENT_DEEP);
            dot.setBackground(dg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(5), dp(5));
            dlp.topMargin = dp(7);
            dlp.rightMargin = dp(9);
            row.addView(dot, dlp);

            TextView text = new TextView(this);
            text.setText(item);
            text.setTextColor(Theme.DIM);
            text.setTextSize(12.5f);
            text.setLineSpacing(dp(3), 1f);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            card.addView(row, rlp);
        }
        return card;
    }
}
