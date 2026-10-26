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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Магазин визуала: окантовки аватара за очки. */
public class ShopActivity extends Activity {

    private GameState game;
    private LinearLayout list;
    private TextView pointsView;

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
        title.setText("МАГАЗИН РАМОК");
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

        pointsView = new TextView(this);
        pointsView.setTextColor(Theme.GOLD);
        pointsView.setTextSize(12);
        pointsView.setPadding(0, dp(6), 0, dp(12));
        root.addView(pointsView);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void fill() {
        list.removeAllViews();
        pointsView.setText("Очки: " + Shop.money(game.points) + " · " + game.level() + " уровень");

        for (int id = 0; id < Shop.FRAME_NAMES.length; id++) {
            list.addView(row(id));
        }
    }

    private View row(final int id) {
        boolean owned = Shop.owned(game, id);
        boolean active = game.avatarFrame == id;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        // цвета из темы: здесь были вкомпилированы тёмно-зелёные, и в «Кавае»
        // строки магазина оставались тёмными блоками
        bg.setColor(active ? Theme.SURFACE_HI : Theme.SURFACE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), active ? Theme.ACCENT : Theme.CARD_STROKE);
        row.setBackground(bg);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        AvatarView av = new AvatarView(this);
        av.setStyle(game.avatarStyle, game.avatarPalette);
        av.setNick(game.nick);
        av.setFrame(id);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(64), dp(64));
        ap.rightMargin = dp(12);
        row.addView(av, ap);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(Shop.FRAME_NAMES[id]);
        name.setTextColor(Theme.TEXT);
        name.setTextSize(13.5f);
        col.addView(name);

        int needLevel = Shop.FRAME_LEVEL[id];
        boolean levelOk = game.level() >= needLevel;

        TextView sub = new TextView(this);
        sub.setText(owned
                ? (active ? "выбрана" : "куплена")
                : Shop.money(Shop.FRAME_PRICES[id]) + " очков"
                        + (needLevel > 0 ? " · с " + needLevel + " уровня" : ""));
        sub.setTextColor(Color.parseColor(owned ? "#6FD6AE" : "#7E9A8F"));
        sub.setTextSize(10.5f);
        col.addView(sub);

        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button action = new Button(this);
        action.setAllCaps(false);
        action.setTextSize(11);
        // «купить» — акцентным цветом темы (в «Обсидиане» белый), «надеть» — спокойным
        action.setTextColor(owned ? Theme.DIM : Theme.ON_ACCENT);
        GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(dp(14));
        bd.setColor(owned ? (active ? Theme.SURFACE_HI : Theme.SURFACE) : Theme.ACCENT);
        action.setBackground(bd);
        action.setStateListAnimator(null);
        action.setText(owned ? (active ? "выбрана" : "надеть")
                : (levelOk ? "купить" : "с " + needLevel + " ур."));
        action.setEnabled(!active);

        action.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Shop.owned(game, id)) {
                    game.selectFrame(id);
                    game.save(ShopActivity.this);
                    fill();
                } else if (!levelOk) {
                    Toast.makeText(ShopActivity.this,
                            "Рамка откроется с " + needLevel + " уровня", Toast.LENGTH_SHORT).show();
                } else {
                    if (game.buyFrame(id)) {
                        game.save(ShopActivity.this);
                        Toast.makeText(ShopActivity.this, "Куплено", Toast.LENGTH_SHORT).show();
                        fill();
                    } else {
                        Toast.makeText(ShopActivity.this, "Не хватает очков", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(88), dp(38));
        row.addView(action, bp);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        row.setLayoutParams(rp);
        return row;
    }
}
