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

import java.util.List;
import java.util.Locale;

/**
 * Коллекция по сезонам: у каждого сезона своя полка собранных цветов.
 * Сбор не заканчивается, поэтому «из 365» здесь больше нет.
 * Отсюда же открывается открытка собранного цветка и список сохранённых открыток.
 */
public class CollectionActivity extends Activity {

    private GameState game;
    private CollectionView grid;
    private TextView countView;
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

        // фильтров больше нет: коллекция разложена по сезонам
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
        slp.topMargin = dp(5);
        for (int i = 0; i < 4; i++) {
            // первый слот — праздничный цветок, если открытка уже получена
            // ёлка: отдельный слот, если собрана новогодняя открытка
            if (i == 1 && game.nyCard) {
                ImageView nySlot = new ImageView(this);
                nySlot.setImageBitmap(Postcard.renderTreeThumb(this, game.nyPlaced));
                GradientDrawable nyBg = new GradientDrawable();
                nyBg.setColor(0xFF16233F);
                nyBg.setCornerRadius(dp(16));
                nyBg.setStroke(dp(1), 0xFF5C88C8);
                nySlot.setBackground(nyBg);
                nySlot.setPadding(dp(6), dp(6), dp(6), dp(6));
                nySlot.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { openNewYearCard(); }
                });
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, dp(104), 1f);
                nlp.rightMargin = dp(8);
                slots.addView(nySlot, nlp);
                continue;
            }
            if (i == 0 && game.eventCard) {
                ImageView got = new ImageView(this);
                int rid = getResources().getIdentifier("event_stage_20", "drawable", getPackageName());
                if (rid != 0) got.setImageResource(rid);
                got.setScaleType(ImageView.ScaleType.FIT_CENTER);
                GradientDrawable gold = new GradientDrawable();
                gold.setColor(Theme.SURFACE_HI);
                gold.setCornerRadius(dp(16));
                gold.setStroke(dp(1), Theme.GOLD);
                got.setBackground(gold);
                got.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { openEventCard(); }
                });
                got.setPadding(dp(6), dp(6), dp(6), dp(6));
                slots.addView(got, new LinearLayout.LayoutParams(0, dp(104), 1f));
                continue;
            }

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
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(104), 1f);
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

        // Необычные цветы: те, что выросли из семечек магазина (волшебная ромашка)
        LinearLayout unusual = new LinearLayout(this);
        unusual.setOrientation(LinearLayout.VERTICAL);
        unusual.setPadding(pad, dp(10), pad, dp(2));

        TextView unTitle = new TextView(this);
        unTitle.setText("НЕОБЫЧНЫЕ");
        unTitle.setTextColor(Theme.FAINT);
        unTitle.setTextSize(10);
        unTitle.setLetterSpacing(0.10f);
        unusual.addView(unTitle);

        LinearLayout unSlots = new LinearLayout(this);
        unSlots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams uslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uslp.topMargin = dp(5);

        // Необычные цветы: по рамке на каждый редкий вид из магазина.
        // Пока ничего не собрано — одна строка вместо пустых рамок.
        int filled = 0;
        for (int species : Rare.ALL) {
            final int sp = species;
            if (findCard(species) == null) continue;
            ImageView view = new ImageView(this);
            android.graphics.Bitmap art = Rare.artCropped(this, sp, Rare.frames(sp));
            if (art != null) view.setImageBitmap(art);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable gold = new GradientDrawable();
            gold.setColor(Theme.SURFACE_HI);
            gold.setCornerRadius(dp(16));
            gold.setStroke(dp(1), Theme.GOLD);
            view.setBackground(gold);
            view.setPadding(dp(6), dp(6), dp(6), dp(6));
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openCard(sp); }
            });
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, dp(104), 1f);
            if (filled > 0) gp.leftMargin = dp(10);
            unSlots.addView(view, gp);
            filled++;
        }

        if (filled > 0) {
            for (int i = filled; i < Math.max(4, Rare.ALL.length); i++) {
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
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(104), 1f);
                ilp.leftMargin = dp(10);
                unSlots.addView(slot, ilp);
            }
            unusual.addView(unSlots, uslp);

            TextView unHint = new TextView(this);
            unHint.setText("цветы из семечек магазина — их не встретить в саду случайно");
            unHint.setTextColor(Theme.FAINT);
            unHint.setTextSize(9.5f);
            LinearLayout.LayoutParams uhp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            uhp.topMargin = dp(7);
            unusual.addView(unHint, uhp);
        } else {
            TextView unEmpty = new TextView(this);
            unEmpty.setText("пока пусто — такие семена бывают в магазине");
            unEmpty.setTextColor(Theme.FAINT);
            unEmpty.setTextSize(9.5f);
            unEmpty.setPadding(0, dp(6), 0, 0);
            unusual.addView(unEmpty);
        }
        root.addView(unusual);

        // Медали: превью полученных, чтобы коллекция показывала не только цветы
        root.addView(medalsBlock());

        grid = new CollectionView(this);
        grid.setMinimumHeight(dp(240));
        grid.setListener(new CollectionView.Listener() {
            @Override public void onSpeciesTap(int species, boolean collected) {
                if (collected) openCard(species);
                else Toast.makeText(CollectionActivity.this,
                        "Этот вид ещё не собран", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        refresh();
        return root;
    }

    /** Превью полученных медалей: лента монет, по нажатию — экран награды. */
    /** Медали в коллекции: три ленты по металлам — бронза, серебро, золото. */
    private View medalsBlock() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(medalStrip("bronze", "БРОНЗА"));
        box.addView(medalStrip("silver", "СЕРЕБРО"));
        box.addView(medalStrip("gold", "ЗОЛОТО"));

        TextView hint = new TextView(this);
        hint.setText("нажми на медаль — вспомнишь, за что она");
        hint.setTextColor(Theme.FAINT);
        hint.setTextSize(9.5f);
        hint.setPadding(dp(16), dp(8), dp(16), dp(2));
        box.addView(hint);
        return box;
    }

    /** Одна лента медалей одного металла. */
    private View medalStrip(String metal, String label) {
        final int pad = dp(16);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(6), pad, dp(1));

        final java.util.ArrayList<Achievements.Ach> got = new java.util.ArrayList<>();
        int total = 0;
        for (Achievements.Ach a : Achievements.ALL) {
            if (!metal.equals(a.metal)) continue;
            total++;
            if (game.unlocked.contains(a.id)) got.add(a);
        }

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(Theme.FAINT);
        title.setTextSize(10);
        title.setLetterSpacing(0.10f);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(this);
        count.setText(got.size() + " из " + total);
        count.setTextColor(Theme.MUTED);
        count.setTextSize(10.5f);
        head.addView(count);
        box.addView(head);

        if (got.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("пока ни одной такой");
            empty.setTextColor(Theme.FAINT);
            empty.setTextSize(9.5f);
            empty.setPadding(0, dp(8), 0, 0);
            box.addView(empty);
            return box;
        }

        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < got.size(); i++) {
            final Achievements.Ach a = got.get(i);
            ImageView coin = new ImageView(this);
            int rid = getResources().getIdentifier(a.medal(), "drawable", getPackageName());
            if (rid == 0) {
                rid = getResources().getIdentifier("medal_" + a.id, "drawable", getPackageName());
            }
            if (rid != 0) coin.setImageResource(rid);
            coin.setScaleType(ImageView.ScaleType.FIT_CENTER);
            coin.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showAchievement(a); }
            });
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(42), dp(42));
            if (i > 0) ilp.leftMargin = dp(8);
            row.addView(coin, ilp);
        }
        android.widget.HorizontalScrollView holder = scroll;
        // ленту медалей тянут вбок, а страница листается вниз: при движении пальцем
        // вбок запрещаем вертикальному ScrollView перехватывать жест
        holder.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    android.view.ViewParent par = v.getParent();
                    if (par != null) par.requestDisallowInterceptTouchEvent(true);
                }
                return false;
            }
        });
        holder.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(5);
        box.addView(holder, slp);
        return box;
    }

    /** Показать экран награды для уже полученной медали. */
    private void showAchievement(Achievements.Ach a) {
        java.util.ArrayList<Achievements.Ach> one = new java.util.ArrayList<>();
        one.add(a);
        AchievementActivity.showQueue(this, one);
    }

    /** Пересобрать полки сезонов и подпись «собрано». */
    private void refresh() {
        grid.setCards(game.cards);
        int total = 0, now = 0;
        String nowKey = Season.now();
        for (GameState.Card c : game.cards) {
            if (c == null || c.species == -1) continue;   // праздничные считаются отдельно
            total++;
            if (c.species >= 0 && Season.key(c.date).equals(nowKey)) now++;
        }
        String season = Season.label(nowKey).toLowerCase(Locale.US);
        countView.setText("собрано " + total + " · в этом сезоне " + now
                + " · сейчас " + season);
    }

    /** Праздничная открытка: рисуем ту же, что в коллекции, но по своему шаблону. */
    private void openEventCard() {
        final GameState.Card card = GameState.holidayCard();
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = Postcard.render(CollectionActivity.this, card);
                ui.post(new Runnable() {
                    @Override public void run() { showCard(card, bmp); }
                });
            }
        }).start();
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

    /** Новогодняя открытка: рендерим заново из текущего состояния ёлки. */
    private void openNewYearCard() {
        final Bitmap bmp = Postcard.renderTree(this, game.nyPlaced, NewYear.openedCount(game));
        if (bmp == null) return;

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
        caption.setText("Ёлка-" + (NewYear.seasonYear() + 1) + " · новогодний ивент");
        caption.setTextColor(Theme.TEXT);
        caption.setTextSize(13);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(8), 0, 0);
        box.addView(caption);

        new AlertDialog.Builder(this)
                .setTitle("Ёлка-" + (NewYear.seasonYear() + 1))
                .setView(box)
                .setPositiveButton("Сохранить в галерею", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        boolean ok = Postcard.saveToGallery(CollectionActivity.this, bmp, ("ШагоСад-Ёлка-" + (NewYear.seasonYear() + 1)));
                        Toast.makeText(CollectionActivity.this,
                                ok ? ("Сохранено: Pictures/Сад/ШагоСад-Ёлка-" + (NewYear.seasonYear() + 1) + ".png") : "Не удалось сохранить",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
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
