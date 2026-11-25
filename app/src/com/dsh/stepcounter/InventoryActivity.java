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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Инвентарь: купленные и бесплатные инструменты, а также редкие семечки.
 * Тяпка срезает сорняки после пропущенного дня, лопата перекапывает засохший цветок,
 * редкое семечко сажается на свободную грядку.
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

        // редкие семечки: купил в магазине — лежит здесь, посадил — пропадает отсюда
        for (java.util.Map.Entry<Integer, Integer> e : game.rareSeeds.entrySet()) {
            int species = e.getKey().intValue();
            int count = e.getValue() == null ? 0 : e.getValue().intValue();
            if (count > 0) list.addView(rareSeedCard(species, count));
        }

        // пока инструмент не куплен — его строки нет: инвентарь просто пустой,
        // без заглушек и слова «нет»
        if (game.hoes > 0) {
            list.addView(tool("Тяпка", game.hoes,
                    "срезает один сорняк. Сорняки появляются, если не выполнить норму шагов.",
                    GameState.HOE_PRICE, ToolIcon.HOE));
        }
        if (game.shovels > 0) {
            list.addView(tool("Лопата", game.shovels,
                    "перекапывает землю, если цветок засох после трёх пропусков подряд.",
                    GameState.SHOVEL_PRICE, ToolIcon.SHOVEL));
        }
        if (game.gardeners > 0) {
            list.addView(gardener(game.gardeners));
        }
        if (game.isFrozenToday()) {
            list.addView(card("Грядка заморожена до полуночи",
                    "Пока лёд стоит, с цветком ничего нельзя. Завтра шаги будут считаться "
                            + "вдвое для закрытия дневной нормы."));
        }

        if (game.neglect > 0) {
            list.addView(card("Пропусков подряд: " + game.neglect,
                    game.withered ? "Цветок засох — нужна лопата."
                            : (game.weeds > 0 ? "На цветке сорняков: " + game.weeds + "." : "")));
        }
    }

    /**
     * Редкое семечко в инвентаре. Кнопка «Посадить» работает, когда грядка свободна;
     * после посадки семечко из инвентаря исчезает.
     */
    private View rareSeedCard(final int species, int count) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(Theme.SURFACE_HI);
        bg.setStroke(dp(1), Theme.GOLD);
        box.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        android.widget.ImageView icon = new android.widget.ImageView(this);
        int seedIconRes = Rare.seedIcon(this, species);
        android.graphics.Bitmap seedArt = seedIconRes == 0 ? Rare.art(this, species, 0) : null;
        if (seedIconRes != 0) icon.setImageResource(seedIconRes);
        else if (seedArt != null) icon.setImageBitmap(seedArt);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        GradientDrawable ico = new GradientDrawable();
        ico.setColor(Theme.SURFACE);
        ico.setCornerRadius(dp(14));
        ico.setStroke(dp(1), Theme.CARD_STROKE);
        icon.setBackground(ico);
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        // тап по иконке — описание именно этого семечка
        icon.setClickable(true);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ItemInfo.show(InventoryActivity.this, Rare.name(species),
                        "Редкое семечко: посадите на грядку и полейте 10 раз — "
                                + "вырастет необычный цветок и даст открытку в коллекцию.\n\n"
                                + "После посадки семечко из инвентаря исчезает.");
            }
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
        ilp.rightMargin = dp(12);
        row.addView(icon, ilp);

        TextView t = new TextView(this);
        t.setText(Rare.name(species));
        t.setTextColor(Theme.GOLD);
        t.setTextSize(13.5f);
        t.setTypeface(Ui.medium());
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView c = new TextView(this);
        c.setText("× " + count);
        c.setTextSize(13);
        c.setTypeface(Ui.medium());
        c.setTextColor(Theme.ACCENT_SOFT);
        row.addView(c);
        box.addView(row);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);

        TextView d = new TextView(this);
        d.setText("нажми на иконку — что делает");
        d.setTextColor(Theme.FAINT);
        d.setTextSize(10);
        d.setPadding(0, dp(6), 0, dp(10));
        box.addView(d);

        Button plant = new Button(this);
        plant.setAllCaps(false);
        plant.setTextSize(13.5f);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setCornerRadius(dp(24));
        if (game.plotEmpty()) {
            bbg.setColor(Theme.GOLD);
            plant.setText("Посадить");
            plant.setTextColor(0xFF2A1E00);
            plant.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { plantSeed(species); }
            });
        } else {
            // грядка занята: сажать пока некуда, но семечко остаётся в инвентаре
            bbg.setColor(Theme.SURFACE);
            plant.setText("Грядка занята — соберите цветок в саду");
            plant.setTextColor(Theme.MUTED);
            plant.setEnabled(false);
        }
        plant.setBackground(bbg);
        box.addView(plant, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return box;
    }

    /** Посадить семечко из инвентаря: оно уходит на грядку и пропадает из списка. */
    private void plantSeed(int species) {
        if (!game.plantRareSeed(species)) {
            Toast.makeText(this, "Грядка занята: сначала соберите цветок в саду",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        game.save(this);
        fill();
        Toast.makeText(this, "Семечко посажено — растите его в саду",
                Toast.LENGTH_SHORT).show();
    }

    /** Карточка инструмента: иконка, название и количество. Описание — по тапу на иконку. */
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
        icon.setClickable(true);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ItemInfo.show(InventoryActivity.this, kind); }
        });
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

        // короткая подсказка вместо абзаца: подробности — в окне по тапу на иконку
        TextView hint = new TextView(this);
        hint.setText("нажми на иконку — что делает");
        hint.setTextColor(Theme.FAINT);
        hint.setTextSize(10);
        hint.setPadding(0, dp(6), 0, 0);
        box.addView(hint);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        return box;
    }

    /**
     * Садовник: применяется на грядку и даёт день без штрафов.
     * Из инвентаря уходит в момент применения.
     */
    private View gardener(int count) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(Theme.SURFACE);
        bg.setStroke(dp(1), Theme.GOLD);
        box.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ToolIcon.View icon = new ToolIcon.View(this, ToolIcon.GARDENER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(44), dp(44));
        ip.rightMargin = dp(12);
        icon.setClickable(true);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ItemInfo.show(InventoryActivity.this, ToolIcon.GARDENER);
            }
        });
        row.addView(icon, ip);

        TextView t = new TextView(this);
        t.setText("Садовник");
        t.setTextColor(Theme.TEXT);
        t.setTextSize(13.5f);
        t.setTypeface(Ui.medium());
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView c = new TextView(this);
        c.setText("× " + count);
        c.setTextSize(13);
        c.setTypeface(Ui.medium());
        c.setTextColor(Theme.ACCENT_SOFT);
        row.addView(c);
        box.addView(row);

        // подробности — в окне по тапу на иконку
        TextView d = new TextView(this);
        d.setText("нажми на иконку — что делает");
        d.setTextColor(Theme.FAINT);
        d.setTextSize(10);
        d.setPadding(0, dp(6), 0, dp(10));
        box.addView(d);

        Button apply = new Button(this);
        apply.setAllCaps(false);
        apply.setTextSize(13.5f);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setCornerRadius(dp(24));
        if (game.isFrozenToday()) {
            // второй садовник сегодня не нужен: грядка уже подо льдом
            bbg.setColor(Theme.SURFACE);
            apply.setText("Уже заморожено до полуночи");
            apply.setTextColor(Theme.MUTED);
            apply.setEnabled(false);
        } else {
            bbg.setColor(Theme.GOLD);
            apply.setText("Применить на грядку");
            apply.setTextColor(0xFF2A1E00);
            apply.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { applyGardener(); }
            });
        }
        apply.setBackground(bbg);
        box.addView(apply, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        return box;
    }

    /** Применить садовника: +1 день заморозки, садовник уходит из инвентаря. */
    private void applyGardener() {
        if (!game.applyGardener()) return;
        game.save(this);
        fill();
        Toast.makeText(this, "Грядка заморожена: пропущенный день пройдёт без штрафа",
                Toast.LENGTH_LONG).show();
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
