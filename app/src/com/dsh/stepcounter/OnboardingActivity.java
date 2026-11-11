package com.dsh.stepcounter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Первое знакомство: что за игра, зачем цель, как получить карточку. */
public class OnboardingActivity extends Activity {

    private GameState game;
    private int chosen = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.load(this);
        setTheme(Theme.styleRes());
        super.onCreate(savedInstanceState);
        Theme.bars(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        game = GameState.load(this);
        chosen = game.goal;
        setContentView(build());
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private View build() {
        final int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM, Theme.BG_TOP}));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(pad, in.getSystemWindowInsetTop() + dp(8),
                        pad, in.getSystemWindowInsetBottom() + dp(8));
                return in;
            }
        });

        // содержимое прокручивается: на маленьких экранах всё влезает
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(box);

        // обложка: сад в сумерках, низ картинки тёмный — под ним живёт текст
        ImageView hero = new ImageView(this);
        hero.setImageResource(R.drawable.onboarding_hero);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240));
        hlp.bottomMargin = dp(6);
        box.addView(hero, hlp);

        TextView title = new TextView(this);
        title.setText("ШАГОСАД");
        title.setTextColor(Theme.TEXT);
        title.setTextSize(23);
        title.setLetterSpacing(0.14f);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Уютная коллекционная игра о шагах");
        sub.setTextColor(Theme.MUTED);
        sub.setTextSize(11.5f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(8));
        box.addView(sub);

        step(box, "1", "Ставите цель", "Сколько шагов в день комфортно именно вам.");
        step(box, "2", "Проходите их", "Лейки набегают по мере шагов. Десять поливов — и цветок ваш.");
        step(box, "3", "Собираете карточку", "Цветок уникален: своё имя, оттенок и пожелание. "
                + "Открытку можно сохранить в галерею.");

        TextView goalTitle = new TextView(this);
        goalTitle.setText("ЦЕЛЬ НА ДЕНЬ");
        goalTitle.setTextColor(Theme.FAINT);
        goalTitle.setTextSize(10);
        goalTitle.setLetterSpacing(0.22f);
        goalTitle.setPadding(0, dp(10), 0, dp(6));
        box.addView(goalTitle);

        final EditText goalInput = new EditText(this);
        goalInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        goalInput.setText(String.valueOf(chosen));
        goalInput.setTextColor(Theme.TEXT);
        goalInput.setTextSize(22);
        goalInput.setGravity(Gravity.CENTER);
        goalInput.setPadding(0, dp(10), 0, dp(10));
        GradientDrawable inp = new GradientDrawable();
        inp.setColor(Theme.SURFACE);
        inp.setCornerRadius(dp(14));
        inp.setStroke(dp(1), Theme.STROKE);
        goalInput.setBackground(inp);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(goalInput, ip);

        final TextView calc = new TextView(this);
        calc.setTextColor(Theme.MUTED);
        calc.setTextSize(10.5f);
        calc.setGravity(Gravity.CENTER);
        calc.setPadding(0, dp(8), 0, 0);
        box.addView(calc);
        updateCalc(calc, chosen);

        goalInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                int v = 10000;
                try { v = Integer.parseInt(e.toString().trim()); } catch (Exception ignored) { }
                chosen = Math.max(1000, Math.min(100000, v));
                updateCalc(calc, chosen);
            }
        });

        final CheckBox check = new CheckBox(this);
        check.setText("Больше не показывать");
        check.setTextColor(Theme.MUTED);
        check.setTextSize(12);
        // состояние берём из настроек: раньше галочка всегда открывалась отмеченной,
        // даже если пользователь её снимал и обучение показывалось снова
        check.setChecked(getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                .getBoolean("onboarding_off", true));

        Button start = Ui.primary(this, "Начать");
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                game.setGoal(chosen);
                game.save(OnboardingActivity.this);
                SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
                p.edit().putInt("goal", chosen)
                        .putBoolean("onboarding_off", check.isChecked())
                        .putBoolean("onboarding_seen", true)
                        .apply();
                finish();
            }
        });

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // галочка и кнопка всегда на виду
        root.addView(check, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        bp2.topMargin = dp(10);
        root.addView(start, bp2);
        return root;
    }

    private void updateCalc(TextView t, int goal) {
        int cost = Math.max(100, goal / 10);
        t.setText("Лейка = " + Ui.steps(cost) + " · весь цветок = " + Ui.steps(goal));
    }

    private void step(LinearLayout parent, String num, String title, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);

        ImageView icon = new ImageView(this);
        int res = num.equals("1") ? R.drawable.onboarding_goal
                : num.equals("2") ? R.drawable.onboarding_steps
                : R.drawable.onboarding_card;
        icon.setImageResource(res);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(52), dp(52));
        bp.rightMargin = dp(12);
        row.addView(icon, bp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(Theme.TEXT);
        t1.setTextSize(13);
        col.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(text);
        t2.setTextColor(Theme.MUTED);
        t2.setTextSize(10.5f);
        t2.setLineSpacing(dp(2), 1f);
        col.addView(t2);

        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, rp);
    }
}
