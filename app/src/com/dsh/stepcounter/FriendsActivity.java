package com.dsh.stepcounter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Экран «Друзья»: свой код сада, добавление друга и список друзей с аватарками. */
public class FriendsActivity extends android.app.Activity {

    private GameState game;
    private LinearLayout listBox;
    private TextView codeView;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        game = GameState.load(this);
        setContentView(build());
        Social.ensureAsync(this, game.nick, null);
        loadFriends();
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
                v.setPadding(pad, in.getSystemWindowInsetTop() + dp(10),
                        pad, dp(10));
                return in;
            }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("ДРУЗЬЯ");
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

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // свой код сада
        box.addView(Ui.section(this, "ТВОЙ КОД САДА"));
        LinearLayout codeCard = card();
        codeView = new TextView(this);
        String id = Social.gardenId(this);
        codeView.setText(id == null || id.isEmpty() ? "…" : id);
        codeView.setTextColor(Theme.TEXT);
        codeView.setTextSize(24);
        codeView.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        codeView.setLetterSpacing(0.12f);
        codeCard.addView(codeView);

        TextView hint = new TextView(this);
        hint.setText("Дай этот код другу — он добавит тебя и будет видеть твои цветы и шаги.\n"
                + "Код меняется, если начать новый сад.");
        hint.setTextColor(Theme.MUTED);
        hint.setTextSize(12.5f);
        hint.setPadding(0, dp(6), 0, dp(10));
        codeCard.addView(hint);

        Button copy = Ui.secondary(this, "Скопировать код");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = Social.gardenId(FriendsActivity.this);
                if (id == null || id.isEmpty()) {
                    Toast.makeText(FriendsActivity.this, "Код ещё готовится",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("код сада", id));
                Toast.makeText(FriendsActivity.this, "Код скопирован", Toast.LENGTH_SHORT).show();
            }
        });
        codeCard.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        box.addView(codeCard);

        // добавление друга
        box.addView(Ui.section(this, "ДОБАВИТЬ ДРУГА"));
        LinearLayout addCard = card();
        final EditText input = new EditText(this);
        input.setHint("код сада друга");
        input.setTextColor(Theme.TEXT);
        input.setHintTextColor(Theme.FAINT);
        input.setTextSize(16);
        input.setSingleLine(true);
        addCard.addView(input);
        Button add = Ui.primary(this, "Добавить");
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String code = input.getText().toString().trim();
                if (code.isEmpty()) {
                    Toast.makeText(FriendsActivity.this, "Вставь код друга",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                addFriend(code, input);
            }
        });
        addCard.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        box.addView(addCard);

        // список
        box.addView(Ui.section(this, "МОИ ДРУЗЬЯ"));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(listBox);

        return root;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.CARD);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Theme.STROKE);
        c.setBackground(bg);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(4);
        c.setLayoutParams(lp);
        return c;
    }

    private void addFriend(final String code, final EditText input) {
        Social.addFriend(this, code, new Social.Callback() {
            @Override public void done(boolean ok, String error) {
                if (!isFinishing()) {
                    if (ok) {
                        input.setText("");
                        Toast.makeText(FriendsActivity.this, "Друг добавлен",
                                Toast.LENGTH_SHORT).show();
                        loadFriends();
                    } else {
                        Toast.makeText(FriendsActivity.this,
                                error == null ? "не получилось" : error,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void loadFriends() {
        if (listBox == null) return;
        Social.friends(this, new Social.FriendsCallback() {
            @Override public void done(List<Social.Friend> rows, String error) {
                if (isFinishing() || listBox == null) return;
                listBox.removeAllViews();
                String id = Social.gardenId(FriendsActivity.this);
                if (codeView != null && id != null && !id.isEmpty()) codeView.setText(id);

                if (error != null) {
                    listBox.addView(note(error));
                    return;
                }
                if (rows == null || rows.isEmpty()) {
                    listBox.addView(note("Пока никого. Дай другу свой код — и он появится тут."));
                    return;
                }
                for (Social.Friend f : rows) listBox.addView(row(f));
            }
        });
    }

    /** Удаление сразу, без отдельного подтверждения — спрашиваем в самой строке. */
    private void removeFriend(String code) {
        if (code == null || code.isEmpty()) return;
        Social.removeFriend(this, code, new Social.Callback() {
            @Override public void done(boolean ok, String error) {
                if (isFinishing()) return;
                if (ok) {
                    loadFriends();
                } else {
                    Toast.makeText(FriendsActivity.this,
                            error == null ? "не получилось" : error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Theme.MUTED);
        t.setTextSize(13);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private View row(Social.Friend f) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        AvatarView av = new AvatarView(this);
        if ("photo".equals(f.avatar)) {
            android.graphics.Bitmap ph = Social.decodePhoto(f.photo);
            if (ph != null) av.setPhoto(ph);
        } else if (!f.avatar.isEmpty()) {
            try { av.setArtIndex(Integer.parseInt(f.avatar)); } catch (Exception ignored) { }
        }
        av.setFrame(f.frame);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(56), dp(56));
        alp.rightMargin = dp(14);
        card.addView(av, alp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView nick = new TextView(this);
        nick.setText(f.nick);
        nick.setTextColor(Theme.TEXT);
        nick.setTextSize(16);
        nick.setTypeface(Ui.medium());
        texts.addView(nick);
        final TextView nums = new TextView(this);
        nums.setText(Ui.num(f.flowers) + " " + Ui.flowers(f.flowers) + " · "
                + Ui.num(f.steps) + " " + Ui.pluralWord(f.steps));
        nums.setTextColor(Theme.MUTED);
        nums.setTextSize(12.5f);
        texts.addView(nums);

        // по тапу на друга — «Удалить?» и сразу «Да/Нет», без отдельных окон
        final LinearLayout ask = new LinearLayout(this);
        ask.setOrientation(LinearLayout.HORIZONTAL);
        ask.setGravity(Gravity.CENTER_VERTICAL);
        ask.setVisibility(View.GONE);
        TextView askText = new TextView(this);
        askText.setText("Удалить?");
        askText.setTextColor(Theme.TEXT);
        askText.setTextSize(12.5f);
        ask.addView(askText);

        Button yes = new Button(this);
        yes.setText("Да");
        yes.setAllCaps(false);
        yes.setTextSize(12.5f);
        yes.setTextColor(Theme.ACCENT_SOFT);
        yes.setBackground(null);
        yes.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                removeFriend(f.code);
            }
        });
        ask.addView(yes);

        Button no = new Button(this);
        no.setText("Нет");
        no.setAllCaps(false);
        no.setTextSize(12.5f);
        no.setTextColor(Theme.MUTED);
        no.setBackground(null);
        no.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ask.setVisibility(View.GONE);
                nums.setVisibility(View.VISIBLE);
            }
        });
        ask.addView(no);
        texts.addView(ask);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                nums.setVisibility(View.GONE);
                ask.setVisibility(View.VISIBLE);
            }
        });

        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }
}
