package com.dsh.stepcounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Гербарий игрока: все собранные цветы с именами и датами. */
public class GalleryActivity extends Activity {

    private GameState game;
    private final Handler ui = new Handler(Looper.getMainLooper());

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

    /** Склонение переехало в {@link Ui#flowers(int)}, чтобы правило было одно на всё приложение. */

    /** «2026-10-24» → «24 октября 2026»: в списке не должно быть сырого ISO. */
    private static String humanDate(String iso) {
        try {
            String[] p = iso.split("-");
            String[] months = {"января", "февраля", "марта", "апреля", "мая", "июня",
                    "июля", "августа", "сентября", "октября", "ноября", "декабря"};
            return Integer.parseInt(p[2]) + " " + months[Integer.parseInt(p[1]) - 1] + " " + p[0];
        } catch (Exception e) {
            return iso;
        }
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
        title.setText("ГЕРБАРИЙ");
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

        TextView sub = new TextView(this);
        sub.setText(Ui.flowers(game.cards.size()) + " в коллекции");
        sub.setTextColor(Theme.FAINT);
        sub.setTextSize(11);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        if (game.cards.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Пока пусто. Вырастите первый цветок — он появится здесь "
                    + "с именем, датой и открыткой.");
            empty.setTextColor(Theme.FAINT);
            empty.setTextSize(12);
            empty.setLineSpacing(dp(3), 1f);
            list.addView(empty);
        } else {
            List<GameState.Card> cards = game.cards;
            for (int i = cards.size() - 1; i >= 0; i--) {
                list.addView(cardRow(cards.get(i)));
            }
        }

        scroll.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View cardRow(final GameState.Card card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SURFACE);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), Theme.STROKE);
        row.setBackground(bg);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        ImageView iv = new ImageView(this);
        Bitmap b = Postcard.flowerTinted(this, card.species, card.seed);
        if (b != null) iv.setImageBitmap(b);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(64), dp(64));
        ilp.rightMargin = dp(12);
        row.addView(iv, ilp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(card.name);
        name.setTextColor(Theme.TEXT);
        name.setTextSize(13);
        col.addView(name);

        TextView date = new TextView(this);
        date.setText(humanDate(card.date));
        date.setTextColor(Theme.MUTED);
        date.setTextSize(11);
        col.addView(date);

        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView open = new TextView(this);
        open.setText("открытка ›");
        open.setTextColor(Theme.ACCENT_SOFT);
        open.setTextSize(10.5f);
        row.addView(open);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCard(card); }
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(8);
        row.setLayoutParams(p);
        return row;
    }

    private void openCard(final GameState.Card card) {
        final Bitmap bmp = Postcard.render(this, card);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
        box.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));

        new AlertDialog.Builder(this)
                .setTitle(card.name)
                .setView(box)
                .setPositiveButton("Сохранить в галерею", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        java.io.File f = Postcard.save(GalleryActivity.this, bmp, card.name);
                        Toast.makeText(GalleryActivity.this,
                                f != null ? "Сохранено: Pictures/Сад/" + f.getName() : "Ошибка",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }
}
