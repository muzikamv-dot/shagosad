package com.dsh.stepcounter;

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
import android.widget.Toast;

import java.util.List;

/** Экран «Рейтинги»: цветы и шаги за неделю, месяц и всё время. */
public class RankingActivity extends android.app.Activity {

    private static final String[] KINDS = {"Цветы", "Шаги"};
    private static final String[] KIND_KEYS = {"flowers", "steps"};
    private static final String[] PERIODS = {"Неделя", "Месяц", "Всё время"};
    private static final String[] SCOPES = {"Все", "Друзья"};
    /** Одна высота у переключателя и всех кнопок — ровная строка, текст помещается. */
    private static final int CHIP_DP = 34;
    private static final String[] PERIOD_KEYS = {"week", "month", "all"};

    private GameState game;
    private LinearLayout listBox;
    // по умолчанию открываем «Все · Шаги · Неделя» — самое понятное для нового игрока
    private int kind = 1;      // 0 — цветы, 1 — шаги
    private int period = 0;    // 0 — неделя, 1 — месяц, 2 — всё время
    /** 0 — общий рейтинг, 1 — только среди друзей. */
    private int scope = 0;
    private TextView[] scopeViews;
    private Button[] kindButtons = new Button[2];
    private Button[] periodButtons = new Button[3];

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        game = GameState.load(this);
        setContentView(build());
        Social.ensureAsync(this, game.nick, null);
        load();
    }

    private int dp(int v) { return Ui.dp(this, v); }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM, Theme.BG_BOTTOM}));
        final int pad = dp(18);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(pad, in.getSystemWindowInsetTop() + dp(10), pad, dp(10));
                return in;
            }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("РЕЙТИНГ ТОП-50");
        title.setTextSize(Ui.T_TITLE - 2f);   // чуть меньше, чтобы заголовок влез целиком
        title.setTextColor(Theme.ACCENT_SOFT);
        title.setTextSize(Ui.T_TITLE);
        title.setLetterSpacing(0.10f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(this);
        close.setText("закрыть");
        close.setAllCaps(false);
        close.setTextColor(Theme.MUTED);
        close.setTextSize(13);
        close.setBackground(null);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(close);
        root.addView(head);
        root.addView(Ui.divider(this));

        // один компактный переключатель: Все / Друзья (два сегмента в общей рамке)
        LinearLayout scopeSwitch = new LinearLayout(this);
        scopeSwitch.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable switchBg = new GradientDrawable();
        switchBg.setColor(Theme.SURFACE_HI);
        switchBg.setCornerRadius(dp(18));
        switchBg.setStroke(dp(1), Theme.STROKE);
        scopeSwitch.setBackground(switchBg);
        scopeSwitch.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(CHIP_DP));
        swp.topMargin = dp(12);
        swp.bottomMargin = dp(4);
        scopeViews = new TextView[SCOPES.length];
        for (int i = 0; i < SCOPES.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(SCOPES[i]);
            t.setTextSize(13f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, 0, 0, 0);
            t.setIncludeFontPadding(false);
            t.setSingleLine(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    scope = idx;
                    paintChips();
                    load();
                }
            });
            scopeViews[i] = t;
            // обе половины одинаковой ширины: переключатель симметричный
            scopeSwitch.addView(t, new LinearLayout.LayoutParams(
                    dp(74), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(scopeSwitch, swp);

        // что считаем: две кнопки, но ниже прежнего
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(10), 0, dp(2));
        for (int i = 0; i < KINDS.length; i++) {
            final int idx = i;
            Button b = chip(KINDS[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    kind = idx;
                    paintChips();
                    load();
                }
            });
            kindButtons[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(CHIP_DP), 1f);
            lp.rightMargin = i == 0 ? dp(8) : 0;
            chips.addView(b, lp);
        }
        root.addView(chips);

        // период: три кнопки, тоже ниже
        LinearLayout periods = new LinearLayout(this);
        periods.setOrientation(LinearLayout.HORIZONTAL);
        periods.setPadding(0, dp(6), 0, dp(10));
        for (int i = 0; i < PERIODS.length; i++) {
            final int idx = i;
            Button b = chip(PERIODS[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    period = idx;
                    paintChips();
                    load();
                }
            });
            periodButtons[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(CHIP_DP), 1f);
            lp.rightMargin = i < PERIODS.length - 1 ? dp(6) : 0;
            periods.addView(b, lp);
        }
        root.addView(periods);

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        paintChips();
        return root;
    }



    private Button chip(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13f);
        b.setTag(text);
        // у стандартной кнопки свои внутренние отступы и минимальная высота:
        // при маленькой высоте они и срезают текст
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        b.setIncludeFontPadding(false);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        return b;
    }

    private void paintChips() {
        // «Все / Друзья»: выбранная половина — акцентная плашка внутри общей рамки
        if (scopeViews != null) {
            for (int i = 0; i < scopeViews.length; i++) {
                TextView t = scopeViews[i];
                boolean active = i == scope;
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(15));
                bg.setColor(active ? Theme.ACCENT : 0x00000000);
                t.setBackground(bg);
                t.setTextColor(active ? Theme.ON_ACCENT : Theme.MUTED);
                t.setTypeface(active ? Ui.medium() : Ui.sans());
            }
        }
        for (int i = 0; i < kindButtons.length; i++) style(kindButtons[i], i == kind);
        for (int i = 0; i < periodButtons.length; i++) style(periodButtons[i], i == period);
    }

    private void style(Button b, boolean active) {
        if (b == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(active ? Theme.ACCENT : Theme.CARD);
        bg.setStroke(dp(1), active ? Theme.ACCENT : Theme.STROKE);
        b.setBackground(bg);
        b.setTextColor(active ? Theme.ON_ACCENT : Theme.TEXT);
    }

    private void load() {
        if (listBox == null) return;
        listBox.removeAllViews();
        listBox.addView(note("Загружаю…"));
        final boolean onlyFriends = scope == 1;
        Social.top(this, KIND_KEYS[kind], PERIOD_KEYS[period], onlyFriends,
                new Social.TopCallback() {
            @Override public void done(List<Social.Friend> rows, String error) {
                if (isFinishing() || listBox == null) return;
                listBox.removeAllViews();
                if (error != null) {
                    if (onlyFriends && "нет кода сада".equals(error)) {
                        listBox.addView(note("Сначала загляни в «Друзья» — код сада появится там."));
                    } else {
                        listBox.addView(note("Нет связи с облаком: " + error));
                    }
                    return;
                }
                if (rows == null || rows.isEmpty()) {
                    if (onlyFriends) {
                        listBox.addView(note("Среди друзей пока пусто. Добавь друга по коду — "
                                + "и здесь появится ваше соревнование."));
                    } else {
                        listBox.addView(note("Пока пусто — собери первый цветок."));
                    }
                    return;
                }
                String me = Social.gardenId(RankingActivity.this);
                int place = 1;
                for (Social.Friend f : rows) {
                    listBox.addView(row(place++, f));
                }
                if (me == null || me.isEmpty()) {
                    listBox.addView(note("Код сада появится здесь, когда будет связь."));
                }
            }
        });
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Theme.MUTED);
        t.setTextSize(13);
        t.setPadding(0, dp(10), 0, dp(6));
        return t;
    }

    private View row(int place, Social.Friend f) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.CARD);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Theme.STROKE);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        card.setLayoutParams(lp);

        TextView pos = new TextView(this);
        pos.setText(String.valueOf(place));
        pos.setTextColor(Theme.ACCENT);
        pos.setTextSize(16);
        pos.setTypeface(Ui.medium());
        pos.setMinWidth(dp(26));
        card.addView(pos);

        AvatarView av = new AvatarView(this);
        if ("photo".equals(f.avatar)) {
            android.graphics.Bitmap ph = Social.decodePhoto(f.photo);
            if (ph != null) av.setPhoto(ph);
        } else if (!f.avatar.isEmpty()) {
            try { av.setArtIndex(Integer.parseInt(f.avatar)); } catch (Exception ignored) { }
        }
        av.setFrame(f.frame);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(44), dp(44));
        alp.leftMargin = dp(8);
        alp.rightMargin = dp(12);
        card.addView(av, alp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView nick = new TextView(this);
        nick.setText(f.nick + (f.anomaly ? "  ⚠" : ""));
        nick.setTextColor(Theme.TEXT);
        nick.setTextSize(15.5f);
        texts.addView(nick);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(this);
        val.setText(kind == 0 ? Ui.num(f.value) + " " + Ui.flowers(f.value)
                : Ui.num(f.value) + " " + Ui.pluralWord(f.value));
        val.setTextColor(Theme.ACCENT_SOFT);
        val.setTextSize(15.5f);
        val.setTypeface(Ui.medium());
        card.addView(val);
        return card;
    }
}
