package com.dsh.stepcounter;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Согласие на обработку персональных данных.
 *
 * Показывается один раз, на входе, до игры: без согласия сетевая часть (рейтинги, друзья,
 * профиль сада в облаке) не работает, а значит и игра не запускается. Отказ закрывает приложение.
 * Отозвать согласие можно в настройках — тогда экран появится снова.
 */
public class ConsentActivity extends android.app.Activity {

    /** Ключ в общих настройках: 1 — согласие получено. */
    static final String PREFS = "consent";
    static final String KEY_AGREED = "agreed";

    static boolean agreed(android.content.Context c) {
        return c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_AGREED, false);
    }

    static void setAgreed(android.content.Context c, boolean value) {
        c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AGREED, value).apply();
    }

    @Override protected void onCreate(Bundle b) {
        // ConsentActivity — лаунчер и на свежей установке открывается раньше
        // MainActivity. Без Theme.load палитра остаётся нулевой (все цвета
        // прозрачные), и модератор видит только серый windowBackground.
        Theme.load(this);
        setTheme(Theme.styleRes());
        super.onCreate(b);
        Theme.bars(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (agreed(this)) {           // уже согласился — сразу в игру
            startGame();
            return;
        }
        setContentView(build());
    }

    private void startGame() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private int dp(int v) { return Ui.dp(this, v); }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM, Theme.BG_BOTTOM}));
        final int pad = dp(20);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(pad, in.getSystemWindowInsetTop() + dp(12), pad, dp(14));
                return in;
            }
        });

        TextView title = new TextView(this);
        title.setText("СОГЛАСИЕ НА ОБРАБОТКУ ДАННЫХ");
        title.setTextColor(Theme.ACCENT_SOFT);
        title.setTextSize(19f);
        title.setLetterSpacing(0.06f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title);

        TextView lead = new TextView(this);
        lead.setText("ШагоСад — игра с общим рейтингом и друзьями, поэтому без согласия она "
                + "не сможет работать. Ниже коротко: что именно мы берём и куда это уходит.");
        lead.setTextColor(Theme.MUTED);
        lead.setTextSize(13f);
        lead.setLineSpacing(dp(3), 1f);
        lead.setPadding(0, dp(8), 0, dp(6));
        root.addView(lead);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        line(box, "• Шаги и цветы — чтобы считать прогресс и показывать тебя в рейтинге.");
        line(box, "• Ник, номер аватара и рамки — чтобы тебя узнавали друзья и в списке садов.");
        line(box, "• Своё фото (если поставишь) — отправляется маленькой миниатюрой 96×96 и видно "
                + "тем, кому ты дал код сада.");
        line(box, "• Грубые координаты (район города) — только для прогноза погоды в саду.");
        line(box, "• Хеш устройства — чтобы после переустановки вернуть тот же сад, "
                + "а не создавать новый. Сам идентификатор телефона не отправляется.");
        line(box, "Всё это хранится на нашем сервере в Yandex Cloud (Россия). Рекламы, трекеров "
                + "и аналитики в приложении нет, данные мы не продаём.");
        line(box, "Ник в рейтинге виден другим игрокам — поэтому не пиши туда фамилию, адрес "
                + "или телефон.");
        line(box, "Согласие можно отозвать в любой момент: «Настройки» → «Отозвать согласие на "
                + "обработку данных». Тогда игра попросит согласие заново, а сад из облака "
                + "можно удалить по запросу в поддержку.");

        Button more = Ui.secondary(this, "Полная политика конфиденциальности");
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(ConsentActivity.this, PrivacyActivity.class));
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        mlp.topMargin = dp(6);
        root.addView(more, mlp);

        // две одинаковые по размеру и виду кнопки: различаются только подписью и цветом текста
        Button agree = Ui.secondary(this, "Согласен на обработку данных");
        agree.setTextColor(Theme.ACCENT_SOFT);
        agree.setTextSize(13f);
        agree.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setAgreed(ConsentActivity.this, true);
                Social.ensureAsync(getApplicationContext(), null, null);
                startGame();
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        alp.topMargin = dp(12);
        alp.leftMargin = dp(18);
        alp.rightMargin = dp(18);
        root.addView(agree, alp);

        Button deny = Ui.secondary(this, "Не согласен");
        deny.setTextColor(Theme.MUTED);
        deny.setTextSize(13f);
        deny.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // без согласия играть нельзя — закрываем сразу, ничего не показывая
                finishAffinity();
            }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        dlp.topMargin = dp(8);
        dlp.leftMargin = dp(18);
        dlp.rightMargin = dp(18);
        root.addView(deny, dlp);

        return root;
    }

    private void line(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Theme.TEXT);
        t.setTextSize(13f);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(6), 0, dp(6));
        box.addView(t);
    }
}
