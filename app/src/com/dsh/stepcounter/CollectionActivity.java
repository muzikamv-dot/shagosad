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
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;

/**
 * Коллекция: сетка всех 365 видов. Собранные — цветными, остальные — силуэтами.
 * Отсюда же открывается открытка собранного цветка и список сохранённых открыток.
 */
public class CollectionActivity extends Activity {

    private GameState game;
    private CollectionView grid;
    private TextView countView;
    private final TextView[] chips = new TextView[3];
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

        // шапка
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, dp(12), pad, dp(6));

        TextView title = new TextView(this);
        title.setText("КОЛЛЕКЦИЯ");
        title.setTextColor(Theme.ACCENT_SOFT);
        title.setTextSize(Ui.T_TITLE);
        title.setLetterSpacing(0.10f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView cards = new TextView(this);
        cards.setText("открытки ›");
        cards.setTextColor(Theme.MUTED);
        cards.setTextSize(12);
        cards.setPadding(dp(8), dp(6), dp(8), dp(6));
        cards.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new android.content.Intent(CollectionActivity.this, GalleryActivity.class));
            }
        });
        head.addView(cards);

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

        countView = new TextView(this);
        countView.setTextColor(Theme.FAINT);
        countView.setTextSize(11);
        countView.setPadding(pad, 0, pad, dp(10));
        root.addView(countView);

        // фильтры
        // фильтры: три овала одного размера на всю ширину, текст по центру
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(pad, 0, pad, dp(12));
        String[] names = {"Все", "Собраны", "Осталось"};
        for (int i = 0; i < names.length; i++) {
            final int mode = i;
            TextView chip = new TextView(this);
            chip.setText(names[i]);
            chip.setTextSize(12);
            chip.setTypeface(Ui.medium());
            chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false);
            chip.setPadding(dp(4), 0, dp(4), 0);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectFilter(mode); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, dp(38), 1f);
            // зазор 10dp подобран так, чтобы три овала делились на ширину экрана
            // без остатка (при 8dp третья колонка была на пиксель шире)
            if (i > 0) lp.leftMargin = dp(10);
            filters.addView(chip, lp);
            chips[i] = chip;
        }
        root.addView(filters);

        root.addView(Ui.divider(this));

        // Уникальные цветы из ивентов: пока пустые места, они заполнятся,
        // когда в игре появятся праздничные семечки.
        LinearLayout events = new LinearLayout(this);
        events.setOrientation(LinearLayout.VERTICAL);
        events.setPadding(pad, dp(12), pad, dp(2));

        TextView evTitle = new TextView(this);
        evTitle.setText("ИЗ ИВЕНТОВ");
        evTitle.setTextColor(Theme.FAINT);
        evTitle.setTextSize(10);
        evTitle.setLetterSpacing(0.10f);
        events.addView(evTitle);

        LinearLayout slots = new LinearLayout(this);
        slots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        for (int i = 0; i < 4; i++) {
            TextView slot = new TextView(this);
            slot.setText("✿");
            slot.setTextSize(20);
            slot.setTextColor(Theme.STROKE);
            slot.setGravity(Gravity.CENTER);
            slot.setIncludeFontPadding(false);
            GradientDrawable empty = new GradientDrawable();
            empty.setColor(0x00000000);
            empty.setCornerRadius(dp(16));
            empty.setStroke(dp(1), Theme.CARD_STROKE);
            slot.setBackground(empty);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(54), 1f);
            if (i > 0) ilp.leftMargin = dp(10);
            slots.addView(slot, ilp);
        }
        events.addView(slots, slp);

        TextView evHint = new TextView(this);
        evHint.setText("места для цветов из праздничных событий");
        evHint.setTextColor(Theme.FAINT);
        evHint.setTextSize(9.5f);
        LinearLayout.LayoutParams ehp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ehp.topMargin = dp(7);
        events.addView(evHint, ehp);
        root.addView(events);

        grid = new CollectionView(this);
        grid.setListener(new CollectionView.Listener() {
            @Override public void onSpeciesTap(int species, boolean collected) {
                if (collected) openCard(species);
                else Toast.makeText(CollectionActivity.this,
                        "Этот вид ещё не собран", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        selectFilter(CollectionView.FILTER_ALL);
        return root;
    }

    private void selectFilter(int mode) {
        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) continue;
            boolean on = i == mode;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(19));   // половина высоты 38dp — настоящий овал
            bg.setColor(on ? Theme.ACCENT : Theme.SURFACE_HI);
            chips[i].setBackground(bg);
            chips[i].setTextColor(on ? Theme.ON_ACCENT : Theme.MUTED);
        }
        HashSet<Integer> owned = new HashSet<>();
        for (Integer sp : game.bloomed) owned.add(sp);
        grid.setData(owned, mode);
        int n = owned.size();
        countView.setText("собрано " + n + " из " + Species.COUNT);
    }

    /** Показать открытку собранного вида. Поиск — по номеру вида. */
    private void openCard(int species) {
        final GameState.Card card = findCard(species);
        if (card == null) {
            Toast.makeText(this, "Открытка не найдена", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = Postcard.render(CollectionActivity.this, card);
                ui.post(new Runnable() {
                    @Override public void run() { showCard(card, bmp); }
                });
            }
        }).start();
    }

    private GameState.Card findCard(int species) {
        List<GameState.Card> all = game.cards;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).species == species) return all.get(i);
        }
        return null;
    }

    private void showCard(final GameState.Card card, Bitmap bmp) {
        if (bmp == null || isFinishing()) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(4));

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
        box.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));

        TextView caption = new TextView(this);
        caption.setText(card.name);
        caption.setTextColor(Theme.TEXT);
        caption.setTextSize(14);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(8), 0, 0);
        box.addView(caption);

        new AlertDialog.Builder(this)
                .setTitle("Открытка")
                .setView(box)
                .setPositiveButton("Сохранить в галерею", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Bitmap full = Postcard.render(CollectionActivity.this, card);
                        java.io.File f = Postcard.save(CollectionActivity.this, full, card.name);
                        Toast.makeText(CollectionActivity.this,
                                f != null ? "Сохранено: Pictures/Сад/" + f.getName() : "Не удалось сохранить",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }
}
