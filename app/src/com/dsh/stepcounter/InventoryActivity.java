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

/**
 * Инвентарь: купленные и бесплатные инструменты.
 * Тяпка срезает сорняки после пропущенного дня, лопата перекапывает засохший цветок.
 */
public class InventoryActivity extends Activity {

    private GameState game;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.load(this);
        setTheme(Theme.styleRes());
        super.onCreate(savedInstanceState);
        Theme.bars(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        game = GameState.load(this);
        setContentView(build());
        fill();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private View build() {
        final int pad = dp(18);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM}));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(pad, in.getSystemWindowInsetTop() + dp(10),
                        pad, in.getSystemWindowInsetBottom());
                return in;
            }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("ИНВЕНТАРЬ");
        title.setTextColor(Theme.ACCENT_SOFT);
        title.setTextSize(Ui.T_TITLE);
        title.setLetterSpacing(0.10f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView back = new TextView(this);
        back.setText("закрыть");
        back.setTextColor(Theme.MUTED);
        back.setTextSize(12);
        back.setPadding(dp(10), dp(6), 0, dp(6));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(back);
        root.addView(head);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, 0);
        scroll.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void fill() {
        list.removeAllViews();
        boolean empty = game.hoes == 0 && game.shovels == 0;

        if (empty) {
            list.addView(card("Пока пусто",
                    "Инструменты покупаются в магазине, на вкладке «Инструменты». "
                            + "Тяпка нужна, чтобы срезать сорняки после пропущенного дня. "
                            + "Лопата — чтобы перекопать землю, если цветок засох."));
        }

        list.addView(tool("Тяпка", game.hoes,
                "срезает один сорняк. Сорняки появляются, если не выполнить норму шагов.",
                GameState.HOE_PRICE, ToolIcon.HOE));
        list.addView(tool("Лопата", game.shovels,
                "перекапывает землю, если цветок засох после трёх пропусков подряд.",
                GameState.SHOVEL_PRICE, ToolIcon.SHOVEL));

        if (game.neglect > 0) {
            list.addView(card("Пропусков подряд: " + game.neglect,
                    game.withered ? "Цветок засох — нужна лопата."
                            : (game.weeds > 0 ? "На цветке сорняков: " + game.weeds + "." : "")));
        }
    }

    /** Карточка инструмента с количеством. */
    private View tool(String name, int count, String desc, int price, int kind) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(Theme.SURFACE);
        bg.setStroke(dp(1), Theme.CARD_STROKE);
        box.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ToolIcon.View icon = new ToolIcon.View(this, kind);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(44), dp(44));
        ip.rightMargin = dp(12);
        row.addView(icon, ip);

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(Theme.TEXT);
        t.setTextSize(13.5f);
        t.setTypeface(Ui.medium());
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView c = new TextView(this);
        c.setText(count > 0 ? "× " + count : "нет");
        c.setTextSize(13);
        c.setTypeface(Ui.medium());
        c.setTextColor(count > 0 ? Theme.ACCENT_SOFT : Theme.FAINT);
        row.addView(c);
        box.addView(row);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(Theme.MUTED);
        d.setTextSize(11.5f);
        d.setPadding(0, dp(6), 0, 0);
        d.setLineSpacing(dp(2), 1f);
        box.addView(d);

        TextView pr = new TextView(this);
        pr.setText("в магазине: " + Shop.money(price) + " очков");
        pr.setTextColor(Theme.FAINT);
        pr.setTextSize(10.5f);
        pr.setPadding(0, dp(6), 0, 0);
        box.addView(pr);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        return box;
    }

    /** Простая карточка с заголовком и текстом. */
    private View card(String title, String text) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(Theme.SURFACE);
        bg.setStroke(dp(1), Theme.CARD_STROKE);
        box.setBackground(bg);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Theme.TEXT);
        t.setTextSize(13.5f);
        t.setTypeface(Ui.medium());
        box.addView(t);

        if (text != null && !text.isEmpty()) {
            TextView d = new TextView(this);
            d.setText(text);
            d.setTextColor(Theme.MUTED);
            d.setTextSize(11.5f);
            d.setPadding(0, dp(6), 0, 0);
            d.setLineSpacing(dp(2), 1f);
            box.addView(d);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        return box;
    }
}
