package com.dsh.stepcounter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Экран новогоднего ивента: фон, снег, ёлка на сугробе, адвент-календарь и
 * перетаскивание украшений на ёлку.
 *
 * Пока идёт разработка — даты не проверяются, окошки открываются любые.
 */
public class NewYearActivity extends Activity {

    private GameState game;
    private SceneView scene;
    private LinearLayout tray;
    private TextView adventButton;
    private FrameLayout adventOverlay;
    private TextView adventCounter;
    private FrameLayout trayLayer;
    private int adventCellPx;
    private LinearLayout trayHolder;
    private float downRawX, downRawY;
    private boolean dragFar;
    private int boxOrn = -1;
    private SoundPool sounds;
    private int bellSound = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = GameState.load(this);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        sounds = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build();
        int bellId = getResources().getIdentifier("ny_bell", "raw", getPackageName());
        if (bellId != 0) bellSound = sounds.load(this, bellId, 1);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1020);

        scene = new SceneView(this);
        root.addView(scene, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildTopBar(), topBarParams());
        root.addView(buildTray(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(buildAdventOverlay(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        refreshTray();
    }

    private FrameLayout.LayoutParams topBarParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(34);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        return lp;
    }

    /** Шапка: назад и название, ниже — три одинаковые кнопки в один ряд. */
    private View buildTopBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(0xFFDCE6F5);
        back.setPadding(dp(12), dp(2), dp(14), dp(6));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setCornerRadius(dp(18));
        bbg.setColor(0x66101830);
        back.setBackground(bbg);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        row1.addView(back);

        TextView title = new TextView(this);
        title.setText("ЁЛКА");
        title.setTextSize(20);
        title.setTextColor(0xFFF2F6FF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(0.06f);
        title.setPadding(dp(12), 0, 0, 0);
        row1.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wrap.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = dp(10);

        adventButton = menuButton("Адвент");
        adventButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAdvent(true); }
        });
        row2.addView(adventButton, menuParams(0));

        TextView boxBtn = menuButton("Коробка");
        boxBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTray(); }
        });
        row2.addView(boxBtn, menuParams(dp(4)));

        TextView cardBtn = menuButton("Открытка");
        cardBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { makePostcard(); }
        });
        row2.addView(cardBtn, menuParams(dp(4)));

        wrap.addView(row2, r2lp);
        return wrap;
    }

    /** Кнопка меню — единый вид для «Адвент», «Коробка», «Открытка». */
    private TextView menuButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(0xFFEAF2FF);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xCC233A61);
        bg.setStroke(dp(1), 0x6680B4FF);
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams menuParams(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = leftMargin;
        return lp;
    }


    /** Всплывающая коробка с украшениями: появляется по тапу, гаснет сама. */
    private View buildTray() {
        FrameLayout layer = new FrameLayout(this);
        trayLayer = layer;
        layer.setVisibility(View.GONE);

        View scrim = new View(this);
        scrim.setBackgroundColor(0x55000000);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideTray(); }
        });
        layer.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0xF2101828);
        bg.setStroke(dp(1), 0x44577BB0);
        holder.setBackground(bg);
        holder.setPadding(dp(10), dp(8), dp(10), dp(8));
        trayHolder = holder;

        TextView hint = new TextView(this);
        hint.setText("Тапни игрушку — повесится на ёлку · тяни по ёлке, чтобы перевесить");
        hint.setTextSize(11);
        hint.setTextColor(0x99C8D6EC);
        hint.setPadding(dp(6), 0, dp(6), dp(6));
        holder.addView(hint);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        tray = new LinearLayout(this);
        tray.setOrientation(LinearLayout.HORIZONTAL);
        tray.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(tray);
        holder.addView(scroll);


        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.BOTTOM;
        hlp.bottomMargin = dp(26);
        hlp.leftMargin = dp(10);
        hlp.rightMargin = dp(10);
        layer.addView(holder, hlp);
        return layer;
    }

    /** Коробка выезжает снизу и остаётся открытой, пока её не закроют. */
    private void showTray() {
        if (trayLayer == null || trayHolder == null) return;
        if (trayLayer.getVisibility() == View.VISIBLE) {
            hideTray();
            return;
        }
        refreshTray();
        trayLayer.setVisibility(View.VISIBLE);
        trayHolder.setTranslationY(trayHolder.getHeight() > 0 ? trayHolder.getHeight() + dp(40) : dp(400));
        trayHolder.animate().translationY(0f).setDuration(220).start();
    }

    /** Закрыть коробку (тап мимо неё или повторный тап по кнопке). */
    private void hideTray() {
        if (trayLayer == null || trayHolder == null) return;
        if (trayLayer.getVisibility() != View.VISIBLE) return;
        final float out = trayHolder.getHeight() > 0 ? trayHolder.getHeight() + dp(40) : dp(400);
        trayHolder.animate().translationY(out).setDuration(200).withEndAction(new Runnable() {
            @Override public void run() {
                if (trayLayer != null) trayLayer.setVisibility(View.GONE);
            }
        }).start();
    }

    /** Окно адвента: просторная карточка, крупные окошки, 4 в ряд. */
    private View buildAdventOverlay() {
        adventOverlay = new FrameLayout(this);
        adventOverlay.setBackgroundColor(0xE6060A16);
        adventOverlay.setVisibility(View.GONE);
        adventOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAdvent(false); }
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setCornerRadius(dp(28));
        cbg.setColor(0xFF111A2E);
        cbg.setStroke(dp(1), 0x44577BB0);
        card.setBackground(cbg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setOnClickListener(null);

        TextView title = new TextView(this);
        title.setText("АДВЕНТ-КАЛЕНДАРЬ");
        title.setTextSize(18);
        title.setLetterSpacing(0.10f);
        title.setTextColor(0xFFEAF2FF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        adventCounter = new TextView(this);
        adventCounter.setTextSize(12.5f);
        adventCounter.setTextColor(0x99BFD2EA);
        adventCounter.setGravity(Gravity.CENTER);
        adventCounter.setPadding(0, dp(4), 0, dp(14));
        card.addView(adventCounter);

        ScrollView sc = new ScrollView(this);
        sc.setClipToPadding(false);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_HORIZONTAL);
        int cols = 4;
        // окошки подгоняем под ширину экрана: 4 колонки с зазорами должны влезть
        int scrW = getResources().getDisplayMetrics().widthPixels;
        int inner = scrW - dp(16) - dp(32) - dp(8);
        adventCellPx = Math.min(dp(80), Math.max(dp(46), inner / cols - dp(8)));
        LinearLayout row = null;
        for (int day = 1; day <= NewYear.DAYS; day++) {
            if ((day - 1) % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                grid.addView(row);
            }
            row.addView(adventCell(day), cellParams());
        }
        sc.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int gridH = Math.min(dp(560), (int) (getResources().getDisplayMetrics().heightPixels * 0.62f));
        card.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridH));

        TextView hint = new TextView(this);
        hint.setText("Нажми на окошко — украшение упадёт в коробку");
        hint.setTextSize(11.5f);
        hint.setTextColor(0x66BFD2EA);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, 0);
        card.addView(hint);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        lp.gravity = Gravity.CENTER;
        adventOverlay.addView(card, lp);
        return adventOverlay;
    }

    private LinearLayout.LayoutParams cellParams() {
        int s = adventCellPx > 0 ? adventCellPx : dp(70);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s * 6 / 5);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private View adventCell(final int day) {
        final LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClickable(true);
        updateCell(cell, day);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (NewYear.isOpen(game, day)) {
                    Toast.makeText(NewYearActivity.this,
                            "Окошко " + day + " уже открыто", Toast.LENGTH_SHORT).show();
                    return;
                }
                NewYear.open(game, day);
                game.save(NewYearActivity.this);
                updateCell(cell, day);
                refreshTray();
                updateAdventCounter();
                playBell();
                int orn = NewYear.ornamentForDay(day);
                Toast.makeText(NewYearActivity.this,
                        "День " + day + ": " + NewYear.NAMES[orn], Toast.LENGTH_SHORT).show();
            }
        });
        return cell;
    }

    private void updateCell(LinearLayout cell, int day) {
        boolean open = NewYear.isOpen(game, day);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(open ? 0xFF1E3358 : 0xFF16203A);
        bg.setStroke(dp(1), open ? 0xFF5C88C8 : 0xFF2A3A5C);
        cell.setBackground(bg);
        cell.removeAllViews();

        TextView num = new TextView(this);
        num.setText(String.valueOf(day));
        num.setTextSize(17);
        num.setTextColor(open ? 0xFFEAF2FF : 0xFF7D8CA8);
        num.setGravity(Gravity.CENTER);
        cell.addView(num);

        if (open) {
            ImageView icon = new ImageView(this);
            android.graphics.drawable.Drawable d = NewYear.ornamentDrawable(this, NewYear.ornamentForDay(day));
            if (d != null) icon.setImageDrawable(d);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        }
    }

    private void updateAdventCounter() {
        if (adventCounter != null) {
            adventCounter.setText("Открыто " + NewYear.openedCount(game) + " из " + NewYear.DAYS
                    + " · пока без дат, можно открыть все");
        }
        if (adventButton != null) {
            adventButton.setText("Адвент " + NewYear.openedCount(game) + "/" + NewYear.DAYS);
        }
    }

    private void showAdvent(boolean show) {
        adventOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) updateAdventCounter();
    }

    /** Пересобирает поднос: украшения, которые открыты, но не висят на ёлке. */
    private void refreshTray() {
        if (tray == null) return;
        tray.removeAllViews();
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < NewYear.ORNAMENTS; i++) {
            if (NewYear.hasOrnament(game, i) && !scene.isPlaced(i)) free.add(i);
        }
        if (free.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Открой окошки адвента — украшения появятся здесь");
            empty.setTextSize(12);
            empty.setTextColor(0x99C8D6EC);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            tray.addView(empty);
        }
        for (final int orn : free) {
            final ImageView iv = new ImageView(this);
            android.graphics.drawable.Drawable d = NewYear.ornamentDrawable(this, orn);
            if (d != null) iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable slot = new GradientDrawable();
            slot.setCornerRadius(dp(14));
            slot.setColor(0xFF16203A);
            slot.setStroke(dp(1), 0xFF2A3A5C);
            iv.setBackground(slot);
            iv.setPadding(dp(6), dp(6), dp(6), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(60), dp(60));
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            iv.setTag(orn);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    scene.beginBoxDrag(orn, 0.5f, 0.42f);
                    scene.endBoxDrag();
                    refreshTray();
                }
            });
            tray.addView(iv, lp);
        }
        updateAdventCounter();
    }

    /** Собирает открытку с ёлкой игрока: превью, сохранение флага и кнопка «в галерею». */
    private void makePostcard() {
        final Bitmap bmp = Postcard.renderTree(this, game.nyPlaced, NewYear.openedCount(game));
        game.nyCard = true;
        game.save(this);

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
        caption.setText("Открытка в коллекции · «ИЗ ИВЕНТОВ»");
        caption.setTextColor(0xFFEAF2FF);
        caption.setTextSize(13);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(8), 0, 0);
        box.addView(caption);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Ёлка-" + (NewYear.seasonYear() + 1))
                .setView(box)
                .setPositiveButton("Сохранить в галерею", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        boolean ok = Postcard.saveToGallery(NewYearActivity.this, bmp, ("ШагоСад-Ёлка-" + (NewYear.seasonYear() + 1)));
                        Toast.makeText(NewYearActivity.this,
                                ok ? ("Сохранено: Pictures/Сад/ШагоСад-Ёлка-" + (NewYear.seasonYear() + 1) + ".png") : "Не удалось сохранить",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }
    private void playBell() {
        if (sounds != null && bellSound > 0) {
            sounds.play(bellSound, 0.85f, 0.85f, 1, 0, 1f);
        }
    }

    @Override
    protected void onDestroy() {
        if (sounds != null) {
            sounds.release();
            sounds = null;
        }
        super.onDestroy();
    }

    /** Временная отладка: пишем в файл, потому что logcat на телефоне скрыт. */
    private void dbg(String s) {
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "dbg.txt");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                    .format(new java.util.Date()) + " " + s + "\n");
            w.close();
        } catch (Exception ignored) { }
    }
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Сцена: небо, сугроб, ёлка, гирлянда, украшения и снег. */
    private class SceneView extends View {

        private Bitmap bg, drift, tree, garland, star;
        private final Bitmap[] ornaments = new Bitmap[NewYear.ORNAMENTS];
        private final Bitmap[] flakes = new Bitmap[3];

        /** Для каждой строки картинки ёлки: полуширина кроны в долях половины ширины. */
        private float[] silhouette;

        private final List<Placed> placed = new ArrayList<>();
        private final Random rnd = new Random(2026);

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();

        private final float[] snowX = new float[46];
        private final float[] snowY = new float[46];
        private final float[] snowS = new float[46];
        private final float[] snowV = new float[46];
        private final int[] snowKind = new int[46];
        private long lastFrame = 0;

        private int dragIndex = -1;
        private float dragDx, dragDy;
        private long downTime = 0;
        private float downX, downY;
        private boolean moved = false;

        SceneView(Context ctx) {
            super(ctx);
            bg = decode("ny_bg");
            drift = decode("ny_drift");
            tree = decode("ny_tree");
            garland = decode("ny_garland");
            star = decode("ny_star");
            for (int i = 0; i < NewYear.ORNAMENTS; i++) {
                ornaments[i] = decode(String.format("ny_orn_%02d", i + 1));
            }
            for (int i = 0; i < 3; i++) {
                flakes[i] = decode("ny_flake_" + (i + 1));
            }
            buildSilhouette();

            for (int i = 0; i < snowX.length; i++) {
                snowX[i] = rnd.nextFloat();
                snowY[i] = rnd.nextFloat();
                snowS[i] = 0.010f + rnd.nextFloat() * 0.026f;
                snowV[i] = 0.035f + rnd.nextFloat() * 0.075f;
                snowKind[i] = rnd.nextInt(3);
            }
            parsePlaced();
        }

        private Bitmap decode(String name) {
            int id = NewYear.res(getContext(), name);
            return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
        }

        /** Считает силуэт ёлки по альфе: где кончаются ветки на каждой строке. */
        private void buildSilhouette() {
            if (tree == null) return;
            int bw = tree.getWidth();
            int bh = tree.getHeight();
            int[] px = new int[bw * bh];
            tree.getPixels(px, 0, bw, 0, 0, bw, bh);
            float[] raw = new float[bh];
            for (int y = 0; y < bh; y++) {
                int l = -1, r = -1;
                int row = y * bw;
                for (int x = 0; x < bw; x++) {
                    if ((px[row + x] >>> 24) > 40) {
                        if (l < 0) l = x;
                        r = x;
                    }
                }
                if (l < 0) { raw[y] = -1f; continue; }
                float half = Math.max(r - bw / 2f, bw / 2f - l);
                raw[y] = half / (bw / 2f);
            }
            // сглаживаем: берём максимум в окне, чтобы нить не проваливалась между ярусами
            silhouette = new float[bh];
            int win = Math.max(1, bh / 400);
            for (int y = 0; y < bh; y++) {
                float best = -1f;
                for (int k = -win; k <= win; k++) {
                    int yy = y + k;
                    if (yy < 0 || yy >= bh) continue;
                    if (raw[yy] > best) best = raw[yy];
                }
                silhouette[y] = best;
            }
        }

        /** Полуширина кроны в пикселях экрана на заданной высоте. */
        private float crownHalfAt(RectF tr, float y) {
            if (silhouette == null || silhouette.length == 0 || tree == null) {
                return tr.width() * 0.4f;
            }
            float f = (y - tr.top) / tr.height();
            int row = (int) (f * (silhouette.length - 1));
            row = Math.max(0, Math.min(silhouette.length - 1, row));
            float frac = silhouette[row];
            if (frac < 0f) return 0f;
            return tr.width() * 0.5f * frac;
        }
        private void parsePlaced() {
            placed.clear();
            String s = game.nyPlaced == null ? "" : game.nyPlaced;
            for (String part : s.split(";")) {
                if (part.trim().isEmpty()) continue;
                String[] kv = part.split(":");
                if (kv.length != 2) continue;
                String[] xy = kv[1].split(",");
                if (xy.length != 2) continue;
                try {
                    Placed p = new Placed();
                    p.id = Integer.parseInt(kv[0]);
                    p.nx = Float.parseFloat(xy[0]);
                    p.ny = Float.parseFloat(xy[1]);
                    placed.add(p);
                } catch (NumberFormatException ignored) { }
            }
        }

        void savePlaced() {
            StringBuilder sb = new StringBuilder();
            for (Placed p : placed) {
                sb.append(p.id).append(':')
                        .append(String.format(java.util.Locale.US, "%.4f,%.4f", p.nx, p.ny))
                        .append(';');
            }
            game.nyPlaced = sb.toString();
        }

        boolean isPlaced(int id) {
            for (Placed p : placed) if (p.id == id) return true;
            return false;
        }

        /** Взять игрушку из коробки и тащить по экрану. */
        void beginBoxDrag(int id, float nx, float ny) {
            if (isPlaced(id)) return;
            Placed p = new Placed();
            p.id = id;
            p.nx = clamp(nx, 0.02f, 0.98f);
            p.ny = clamp(ny, 0.03f, 0.98f);
            placed.add(p);
            dragIndex = placed.size() - 1;
            invalidate();
        }

        void endBoxDrag() {
            if (dragIndex < 0 || dragIndex >= placed.size()) return;
            Placed p = placed.get(dragIndex);
            if (p.id == NewYear.TOP_ORNAMENT) {
                RectF tr = treeRect();
                // макушку ставим на верхушку, если её положили в верхнюю половину ёлки
                if (p.ny < 0.55f) {
                    p.nx = 0.5f;
                    p.ny = (tr.top - getHeight() * 0.05f) / getHeight();
                }
            }
            dragIndex = -1;
            savePlaced();
            invalidate();
        }

        /** Рамка игрушки с сохранением пропорций: не расплющивает форму. */
        private RectF ornRect(Bitmap b, float cx, float cy, float size) {
            float bw = b.getWidth();
            float bh = b.getHeight();
            float k = size / Math.max(bw, bh);
            float w = bw * k;
            float h = bh * k;
            return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        }
        private float ornSize() {
            return getWidth() * 0.115f;
        }
        /** Размер игрушки: макушка-звезда заметно крупнее. */
        private float ornSizeFor(int id) {
            float s = ornSize();
            return id == NewYear.TOP_ORNAMENT ? s * 1.85f : s;
        }

        /** Прямоугольник сугроба: ширина по экрану, высота по пропорции картинки. */
        private RectF driftRect() {
            float w = getWidth();
            float h = getHeight();
            float dw = w * 1.12f;
            float dh = drift == null ? h * 0.14f : dw * drift.getHeight() / (float) drift.getWidth();
            float bottom = h - h * 0.005f;
            return new RectF((w - dw) / 2f, bottom - dh, (w + dw) / 2f, bottom);
        }

        private RectF treeRect() {
            float w = getWidth();
            float h = getHeight();
            RectF dr = driftRect();
            float th = h * 0.62f;
            float tw = tree == null ? w * 0.6f : th * tree.getWidth() / tree.getHeight();
            float bottom = dr.bottom - dr.height() * 0.55f;
            return new RectF((w - tw) / 2f, bottom - th, (w + tw) / 2f, bottom);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();

            drawCover(c, bg, w, h);

            RectF tr = treeRect();

            if (drift != null) {
                c.drawBitmap(drift, null, driftRect(), paint);
            }

            if (tree != null) {
                // тень под ёлкой
                shadowPaint.setColor(0x44000000);
                float sw = tr.width() * 0.30f;
                float sh = h * 0.012f;
                c.drawOval(new RectF(tr.centerX() - sw, tr.bottom - sh * 0.5f,
                        tr.centerX() + sw, tr.bottom + sh * 0.5f), shadowPaint);
                c.drawBitmap(tree, null, tr, paint);
            }


            drawGarland(c, tr);

            long nowMs = System.currentTimeMillis();
            for (Placed p : placed) {
                Bitmap b = ornaments[p.id];
                if (b == null) continue;
                RectF r = ornRect(b, p.nx * w, p.ny * h, ornSizeFor(p.id));
                // лёгкое покачивание: поворот вокруг точки подвеса (верх игрушки)
                // макушка-звезда жёстко сидит на верхушке и не качается
                float sway = p.id == NewYear.TOP_ORNAMENT ? 0f
                        : (float) Math.sin(nowMs / 900.0 + p.id * 1.7) * 2.6f;
                m.reset();
                m.postRotate(sway, r.centerX(), r.top);
                c.save();
                c.concat(m);
                c.drawBitmap(b, null, r, paint);
                c.restore();
            }

            if (dragIndex >= 0) {
                Placed p = placed.get(dragIndex);
                Bitmap b = ornaments[p.id];
                if (b != null) {
                    paint.setAlpha(230);
                    c.drawBitmap(b, null, ornRect(b, p.nx * w, p.ny * h, ornSizeFor(p.id) * 1.12f), paint);
                    paint.setAlpha(255);
                }
            }

            drawSnow(c, w, h);
            stepSnow();
        }

        /**
         * Гирлянда по силуэту ёлки: на каждой высоте нить идёт от края веток к центру
         * с провисом — две половины, каждая повёрнута к центру вниз.
         */
        private void drawGarland(Canvas c, RectF tr) {
            if (garland == null) return;
            float[] fracs = {0.33f, 0.55f, 0.75f};
            float[] sags = {0.026f, 0.030f, 0.034f};
            for (int i = 0; i < fracs.length; i++) {
                float y = tr.top + tr.height() * fracs[i];
                // ширину берём по высоте концов нити: там крона уже, чем в середине провиса
                float half = crownHalfAt(tr, y) * 0.95f;
                if (half <= 0f) continue;
                float sag = tr.height() * sags[i];
                drawGarlandHalf(c, tr.centerX() - half, y, half, sag, true);
                drawGarlandHalf(c, tr.centerX(), y + sag, half, sag, false);
            }
        }

        private void drawGarlandHalf(Canvas c, float left, float yEdge, float halfW, float sag,
                                     boolean toCenter) {
            float segH = garland.getHeight() * halfW / (float) garland.getWidth();
            float cx = left + halfW / 2f;
            float cy = yEdge + (toCenter ? -sag / 2f : sag / 2f);
            float angle = (float) Math.toDegrees(Math.atan2(sag, halfW)) * (toCenter ? 1f : -1f);
            m.reset();
            m.postRotate(angle, cx, cy);
            c.save();
            c.concat(m);
            RectF r = new RectF(cx - halfW / 2f, cy - segH / 2f, cx + halfW / 2f, cy + segH / 2f);
            c.drawBitmap(garland, null, r, paint);
            c.restore();
        }

        private void drawCover(Canvas c, Bitmap b, float w, float h) {
            if (b == null) {
                c.drawColor(0xFF0B1020);
                return;
            }
            float k = Math.max(w / b.getWidth(), h / b.getHeight());
            float dw = b.getWidth() * k;
            float dh = b.getHeight() * k;
            RectF r = new RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
            c.drawBitmap(b, null, r, paint);
        }

        private void drawSnow(Canvas c, float w, float h) {
            buildSilhouette();

            for (int i = 0; i < snowX.length; i++) {
                Bitmap b = flakes[snowKind[i] % 3];
                if (b == null) continue;
                float s = w * snowS[i];
                float x = snowX[i] * w;
                float y = snowY[i] * h;
                m.reset();
                m.postRotate((snowX[i] * 720f) % 360f, x, y);
                c.save();
                c.concat(m);
                RectF r = new RectF(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f);
                c.drawBitmap(b, null, r, paint);
                c.restore();
            }
        }

        private void stepSnow() {
            long now = System.currentTimeMillis();
            if (lastFrame == 0) lastFrame = now;
            float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            buildSilhouette();

            for (int i = 0; i < snowX.length; i++) {
                snowY[i] += snowV[i] * dt;
                snowX[i] += (float) Math.sin(now / 1800.0 + i) * 0.004f * dt * 60f;
                if (snowY[i] > 1.02f) {
                    snowY[i] = -0.03f;
                    snowX[i] = rnd.nextFloat();
                }
                if (snowX[i] < -0.02f) snowX[i] = 1.02f;
                if (snowX[i] > 1.02f) snowX[i] = -0.02f;
            }
            postInvalidateOnAnimation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth();
            float h = getHeight();
            float x = e.getX();
            float y = e.getY();
            float s = ornSize();

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downX = x; downY = y; moved = false;
                    downTime = System.currentTimeMillis();
                    for (int i = placed.size() - 1; i >= 0; i--) {
                        Placed p = placed.get(i);
                        float px = p.nx * w, py = p.ny * h;
                        if (Math.abs(x - px) < s * 0.62f && Math.abs(y - py) < s * 0.62f) {
                            dragIndex = i;
                            dragDx = px - x;
                            dragDy = py - y;
                            break;
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (dragIndex < 0) return false;
                    if (Math.abs(x - downX) > dp(6) || Math.abs(y - downY) > dp(6)) moved = true;
                    Placed p = placed.get(dragIndex);
                    p.nx = clamp((x + dragDx) / w, 0.04f, 0.96f);
                    p.ny = clamp((y + dragDy) / h, 0.08f, 0.94f);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (dragIndex < 0) {
                        // тап по пустому месту — всплывает коробка с украшениями
                        if (!moved && System.currentTimeMillis() - downTime < 450) showTray();
                        return true;
                    }
                    Placed p = placed.get(dragIndex);
                    long held = System.currentTimeMillis() - downTime;

                    if (!moved && held > 550) {
                        // долгое нажатие — снять украшение
                        placed.remove(dragIndex);
                        savePlaced();
                        game.save(NewYearActivity.this);
                        dragIndex = -1;
                        invalidate();
                        refreshTray();
                        Toast.makeText(getContext(), "Украшение снято", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    savePlaced();
                    game.save(NewYearActivity.this);
                    dragIndex = -1;
                    invalidate();
                    return true;
                }
            }
            return false;
        }

        private float clamp(float v, float lo, float hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }

        private class Placed {
            int id;
            float nx, ny;
        }
    }
}
