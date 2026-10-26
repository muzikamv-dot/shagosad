package com.dsh.stepcounter;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 100;

    private TextView headerTitle;
    private SwipeFrameLayout container;
    private LinearLayout stepsPage;
    private GardenView garden;
    private QuestsView quests;
    private ScrollView profilePage;
    private AvatarView avatar;
    private TextView nickView, rankView, profSub, profAch;
    private TextView statP, statS, statW, statT;
    private TextView dBest, dAvg, dTotal, dDays, dFlowers, dSpecies;
    private Button goalButton;
    private LinearLayout medalRow;
    private Button shopButton, herbButton, bagButton;
    private Bitmap avatarBitmap;
    private ScrollView achPage;
    private LinearLayout achList;

    private TextView stepsView;
    private TextView goalView;
    private TextView pill;
    private TextView diagView;
    private RingView ring;
    private HistoryView history;
    private TextView historyHint;
    private Button serviceButton;
    private Button batteryButton;
    private Button manualButton;
    private TextView cardWater, cardStreak, nextLeek;
    private CountUp waterCount, pointsCount;

    private LinearLayout nav;
    private final List<LinearLayout> navItems = new ArrayList<>();
    private final List<TabIcon> navIcons = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();

    private GameState game;
    private View lastPage;
    private LinearLayout pageDots;
    private int tab = 0;
    private final String[] TITLES = {"ШАГОСАД", "САД", "ЗАДАНИЯ", "ПРОФИЛЬ", "НАГРАДЫ"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean syncing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.load(this);
        setTheme(Theme.styleRes());
        super.onCreate(savedInstanceState);
        Theme.bars(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        game = GameState.load(this);
        game.ensureIdentity();

        loadAvatar();
        if (game.wasReset) {
            getSharedPreferences(Steps.PREFS, MODE_PRIVATE).edit()
                    .putInt("manual_steps", 0).putString("manual_day", Steps.today()).apply();
            Toast.makeText(this, "Новая версия экономики: прогресс начат заново",
                    Toast.LENGTH_LONG).show();
        }
        game.save(this);
        setContentView(buildUi());
        AlarmScheduler.schedule(this);
        SyncJobService.schedule(this);   // замер шагов в фоне: точная разбивка по дням
        askFirstRunPermissions();

        SharedPreferences sp = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("onboarding_off", false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }
        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // настройки могли изменить состояние игры — перечитываем файл
        game = GameState.load(this);
        if (garden != null) garden.setGame(game);
        if (quests != null) quests.setGame(game);
        resumed = true;
        loadAvatar();
        // пропущенные дни проверяем при каждом возврате: сорняки и засыхание
        if (game.checkMissedDays(this)) game.save(this);
        refreshAll();
        runSync();
        ui.removeCallbacks(ticker);
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        resumed = false;
        ui.removeCallbacks(ticker);
        super.onPause();
    }

    // ---------------- размеры/шрифты ----------------

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    // ---------------- каркас ----------------

    private View buildUi() {
        final int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM,
                           Theme.BG_BOTTOM}));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            }
        });

        // шапка
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, dp(14), pad, dp(6));

        headerTitle = new TextView(this);
        headerTitle.setText(TITLES[0]);
        headerTitle.setTextColor(Theme.ACCENT_SOFT);
        headerTitle.setTextSize(Ui.T_TITLE);
        headerTitle.setLetterSpacing(0.10f);
        headerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerTitle.setMaxLines(1);
        headerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headerTitle.setAutoSizeTextTypeUniformWithConfiguration(
                11, 20, 1, TypedValue.COMPLEX_UNIT_SP);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView date = new TextView(this);
        date.setText(new SimpleDateFormat("d MMMM", new Locale("ru")).format(new Date()));
        date.setTextColor(Theme.MUTED);
        date.setTextSize(13);
        header.addView(date);

        GearView gear = new GearView(this);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(30), dp(30));
        glp.leftMargin = dp(12);
        header.addView(gear, glp);
        root.addView(header);

        root.addView(Ui.divider(this));

        container = new SwipeFrameLayout(this);
        ((SwipeFrameLayout) container).setListener(new SwipeFrameLayout.Listener() {
            @Override public void onSwipe(int direction) {
                int next = tab + (direction < 0 ? 1 : -1);
                if (next < 0 || next >= TITLES.length) return;
                showTab(next, direction);
            }
        });
        root.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        stepsPage = buildStepsPage();
        garden = new GardenView(this);
        garden.setGame(game);
        garden.setListener(new GardenView.Listener() {
            @Override public void onTap() {
                boolean collected = false;
                if (game.withered) {
                    // сначала перекопать: цветок засох, рост не идёт
                    if (game.shovels <= 0) {
                        openShopTools();
                        return;
                    }
                    garden.digOut(new Runnable() {
                        @Override public void run() {
                            game.digUp();
                            Haptics.tap(MainActivity.this);
                            Toast.makeText(MainActivity.this,
                                    "Земля перекопана — сажаем то же семя", Toast.LENGTH_SHORT).show();
                            afterGardenAction();
                        }
                    });
                    return;
                } else if (game.weeds > 0) {
                    // сорняки глушат цветок, пока их не срежут
                    if (game.hoes <= 0) {
                        openShopTools();
                        return;
                    }
                    garden.cutWeeds(new Runnable() {
                        @Override public void run() {
                            game.cutWeed();
                            Haptics.tap(MainActivity.this);
                            Toast.makeText(MainActivity.this,
                                    game.weeds > 0 ? "Срезан один сорняк, осталось " + game.weeds
                                            : "Сорняки срезаны — можно поливать",
                                    Toast.LENGTH_SHORT).show();
                            afterGardenAction();
                        }
                    });
                    return;
                } else if (game.stage[0] >= GameState.STAGES) {
                    game.collect();
                    collected = true;
                    cardFlowActive = true;   // уведомление о достижении ждёт закрытия открытки
                    garden.celebrate();
                    Haptics.bloom(MainActivity.this);
                } else if (game.waterPlot(0, Steps.today())) {
                    garden.water();
                    Haptics.tap(MainActivity.this);
                } else {
                    Toast.makeText(MainActivity.this,
                            "Нужна вода: " + Ui.steps(game.stepCost()) + " = 1 лейка", Toast.LENGTH_SHORT).show();
                }
                game.save(MainActivity.this);
                garden.setGame(game);
                quests.setGame(game);
                refreshWaterCard();
                refreshProfile();
                checkAchievements();
                // сначала блёстки, потом открытка: иначе диалог закрывает конфетти
                if (collected) {
                    garden.postDelayed(new Runnable() {
                        @Override public void run() { showNewCard(); }
                    }, 800);
                }
            }

        });

        quests = new QuestsView(this);
        quests.setGame(game);

        container.addView(stepsPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(garden, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(quests, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        profilePage = buildProfilePage();
        container.addView(profilePage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        achPage = new ScrollView(this);
        achPage.setFillViewport(true);
        achList = new LinearLayout(this);
        achList.setOrientation(LinearLayout.VERTICAL);
        achList.setPadding(dp(16), dp(4), dp(16), dp(16));
        achPage.addView(achList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(achPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageDots = new LinearLayout(this);
        pageDots.setOrientation(LinearLayout.HORIZONTAL);
        pageDots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
        dotsLp.topMargin = dp(6);
        for (int i = 0; i < TITLES.length; i++) {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(Theme.STROKE);
            dot.setBackground(dg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(5), dp(5));
            p.leftMargin = dp(3);
            p.rightMargin = dp(3);
            pageDots.addView(dot, p);
        }
        root.addView(pageDots, dotsLp);

        root.addView(Ui.divider(this));
        nav = buildNav();
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));

        return root;
    }

    /** Открыть магазин сразу на вкладке «Инструменты». */
    private void openShopTools() {
        Intent i = new Intent(this, ShopActivity.class);
        i.putExtra("tab", 1);
        startActivity(i);
    }

    /** Сохранить состояние и обновить экраны после действия в саду. */
    private void afterGardenAction() {
        game.save(this);
        if (garden != null) garden.setGame(game);
        if (quests != null) quests.setGame(game);
        refreshWaterCard();
        refreshProfile();
        checkAchievements();
    }

    private LinearLayout buildNav() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Theme.BG_TOP);
        // по 8dp сверху и снизу: подсветка вкладки не прилипает к разделителю
        bar.setPadding(dp(6), dp(8), dp(6), dp(8));

        String[] names = {"Шаги", "Сад", "Задания", "Профиль", "Награды"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), dp(4), dp(4), dp(4));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTab(index); }
            });

            TabIcon icon = new TabIcon(this);
            icon.setType(i);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
            ilp.bottomMargin = dp(3);
            item.addView(icon, ilp);

            TextView label = new TextView(this);
            label.setText(names[i]);
            label.setTextSize(10.5f);
            // без этого TextView добавляет шрифтовые поля сверху и снизу,
            // и подпись визуально «висит» ниже иконки
            label.setIncludeFontPadding(false);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setTextColor(Theme.FAINT);
            // на всю ширину колонки: тогда текст центрируется ровно в ней,
            // а не гуляет на пиксель из-за wrap_content
            item.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            bar.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            navItems.add(item);
            navIcons.add(icon);
            navLabels.add(label);
        }
        return bar;
    }

    private void showTab(int index) { showTab(index, 0); }

    private void showTab(int index, int dir) {
        if (tab != index) Haptics.tap(this);
        tab = index;
        stepsPage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        garden.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        quests.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        profilePage.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        achPage.setVisibility(index == 4 ? View.VISIBLE : View.GONE);
        headerTitle.setText(TITLES[index]);

        for (int i = 0; i < navItems.size(); i++) {
            boolean on = i == index;
            GradientDrawable dg = new GradientDrawable();
            dg.setCornerRadius(dp(18));
            // у невыбранной вкладки фон должен совпадать с полосой меню,
            // иначе тёмные плашки висят под всеми пятью пунктами
            dg.setColor(on ? Theme.SURFACE : Theme.BG_TOP);
            navItems.get(i).setBackground(dg);
            navIcons.get(i).setActive(on);
            navLabels.get(i).setTextColor(on ? Theme.ACCENT_SOFT : Theme.FAINT);
        }

        if (index == 0) refreshWaterCard();
        if (index == 1) garden.setGame(game);
        if (index == 2) quests.setGame(game);
        if (index == 3) refreshProfile();
        if (index == 4) rebuildAchievements();

        View page = index == 0 ? stepsPage : index == 1 ? garden
                : index == 2 ? quests : index == 3 ? profilePage : achPage;
        if (page == null) return;

        float w = container.getWidth();
        if (dir != 0 && w > 0) {
            final View old = lastPage;

            page.setTranslationX(-dir * w);
            page.setAlpha(0.55f);
            page.setScaleX(0.97f);
            page.setScaleY(0.97f);
            page.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(340)
                    // пружинка: страница чуть «перелетает» и возвращается
                    .setInterpolator(new OvershootInterpolator(1.05f))
                    .start();

            if (old != null && old != page) {
                old.animate()
                        .translationX(dir * w * 0.6f)
                        .alpha(0f)
                        .setDuration(220)
                        .setInterpolator(new DecelerateInterpolator(1.4f))
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                old.setTranslationX(0f);
                                old.setAlpha(1f);
                            }
                        })
                        .start();
            }


        } else {
            page.setTranslationX(0f);
            page.setAlpha(1f);
        }
        lastPage = page;

        if (pageDots != null) {
            for (int i = 0; i < pageDots.getChildCount(); i++) {
                View dot = pageDots.getChildAt(i);
                boolean on = i == index;
                GradientDrawable dg = new GradientDrawable();
                dg.setShape(GradientDrawable.OVAL);
                dg.setColor(on ? Theme.ACCENT : Theme.STROKE);
                dot.setBackground(dg);
                dot.animate().scaleX(on ? 1.5f : 1f).scaleY(on ? 1.5f : 1f)
                        .setDuration(180).start();
            }
        }
    }

    // ---------------- вкладка шагов ----------------

    private LinearLayout buildStepsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), 0, dp(16), dp(8));

        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout ringBox = new FrameLayout(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(206), dp(206));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        rlp.topMargin = dp(6);
        page.addView(ringBox, rlp);

        ring = new RingView(this);
        ringBox.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.VERTICAL);
        numbers.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.gravity = Gravity.CENTER;
        ringBox.addView(numbers, nlp);

        stepsView = new TextView(this);
        stepsView.setText("0");
        stepsView.setTextColor(Theme.TEXT);
        stepsView.setTextSize(52);
        stepsView.setIncludeFontPadding(false);
        stepsView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        stepsView.setGravity(Gravity.CENTER);
        stepsView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        numbers.addView(stepsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        goalView = new TextView(this);
        goalView.setText("из 10 000 шагов");
        goalView.setTextColor(Theme.MUTED);
        goalView.setTextSize(12);
        goalView.setGravity(Gravity.CENTER);
        goalView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        numbers.addView(goalView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pill = new TextView(this);
        pill.setTextSize(12.5f);
        pill.setTypeface(Ui.medium());
        pill.setTextColor(Theme.ACCENT_SOFT);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.topMargin = dp(10);
        page.addView(pill, plp);

        TextView weekTitle = new TextView(this);
        weekTitle.setText("7 ДНЕЙ");
        weekTitle.setTextColor(Theme.FAINT);
        weekTitle.setTextSize(Ui.T_CAPS);
        weekTitle.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams wtl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wtl.topMargin = dp(12);
        page.addView(weekTitle, wtl);

        history = new HistoryView(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        hlp.topMargin = dp(6);
        page.addView(history, hlp);

        historyHint = new TextView(this);
        historyHint.setText("График появится, когда наберётся пара дней.");
        historyHint.setTextColor(Theme.FAINT);
        historyHint.setTextSize(10);
        historyHint.setGravity(Gravity.CENTER);
        historyHint.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        page.addView(historyHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // «до лейки» раньше жило только в саду — теперь видно и здесь
        nextLeek = new TextView(this);
        nextLeek.setTextColor(Theme.ACCENT_SOFT);
        nextLeek.setTextSize(12.5f);
        nextLeek.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nll = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nll.topMargin = dp(8);
        page.addView(nextLeek, nll);

        // распорка: карточка воды держится у нижнего края, а не висит посередине
        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout gardenCard = new LinearLayout(this);
        gardenCard.setOrientation(LinearLayout.HORIZONTAL);
        gardenCard.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable gc = new GradientDrawable();
        gc.setColor(Theme.SURFACE_HI);
        gc.setCornerRadius(dp(26));
        gardenCard.setBackground(gc);
        gardenCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        gardenCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(1); }
        });
        LinearLayout.LayoutParams gclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gclp.topMargin = dp(10);
        page.addView(gardenCard, gclp);

        LinearLayout col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        TextView l1 = new TextView(this);
        l1.setText("ВОДА");
        l1.setTextColor(Theme.FAINT);
        l1.setTextSize(Ui.T_CAPS);
        l1.setLetterSpacing(0.10f);
        cardWater = new TextView(this);
        cardWater.setText(Ui.leeks(0));
        cardWater.setTextColor(Theme.TEXT);
        cardWater.setTextSize(16);
        waterCount = new CountUp(cardWater, new CountUp.Fmt() {
            @Override public String at(int v) { return Ui.leeks(v); }
        });
        col1.addView(l1);
        col1.addView(cardWater);
        gardenCard.addView(col1, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        TextView l2 = new TextView(this);
        l2.setText("СЕРИЯ");
        l2.setTextColor(Theme.FAINT);
        l2.setTextSize(Ui.T_CAPS);
        l2.setLetterSpacing(0.10f);
        cardStreak = new TextView(this);
        cardStreak.setText("0 дн.");
        cardStreak.setTextColor(Theme.GOLD);
        cardStreak.setTextSize(16);
        col2.addView(l2);
        col2.addView(cardStreak);
        gardenCard.addView(col2);

        TextView arrow = new TextView(this);
        arrow.setText("в сад  ›");
        arrow.setTextColor(Theme.ACCENT_SOFT);
        arrow.setTextSize(12);
        arrow.setPadding(dp(14), 0, 0, 0);
        gardenCard.addView(arrow);

        syncLine = new TextView(this);
        syncLine.setTextSize(11.5f);
        syncLine.setTextColor(Theme.FAINT);
        syncLine.setGravity(Gravity.CENTER);
        // строка не выглядит кнопкой, но по ней можно обновить вручную,
        // если датчик почему-то отстал
        syncLine.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runSync(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(10);
        page.addView(syncLine, blp);
        updateSyncLine();

        return page;
    }

    private Button outlined(String text) {
        return Ui.secondary(this, text);
    }

    private LinearLayout.LayoutParams outlineParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        p.topMargin = dp(6);
        return p;
    }

    // ---------------- данные ----------------

    private int sensorSteps() {
        return getSharedPreferences(Steps.PREFS, MODE_PRIVATE).getInt(Steps.KEY_STEPS, 0);
    }

    private int manualSteps() {
        SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
        if (!Steps.today().equals(p.getString("manual_day", ""))) {
            p.edit().putString("manual_day", Steps.today()).putInt("manual_steps", 0).apply();
        }
        return p.getInt("manual_steps", 0);
    }

    private int totalSteps() {
        return sensorSteps() + manualSteps();
    }

    // ---------------- шаги на экране ----------------

    private int shownSteps = -1;
    private ValueAnimator stepsAnim;

    /** Цифры шагов «набегают» к новому значению, а не прыгают мгновенно. */
    private void animateSteps(final int target) {
        if (shownSteps < 0 || Math.abs(target - shownSteps) > 20000) {
            shownSteps = target;
            stepsView.setText(String.valueOf(target));
            return;
        }
        if (shownSteps == target) return;
        if (stepsAnim != null) stepsAnim.cancel();
        stepsAnim = ValueAnimator.ofInt(shownSteps, target);
        stepsAnim.setDuration(520L);
        stepsAnim.setInterpolator(new DecelerateInterpolator(1.8f));
        stepsAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                shownSteps = (Integer) a.getAnimatedValue();
                stepsView.setText(String.valueOf(shownSteps));
            }
        });
        stepsAnim.start();
    }

    /** Карточка воды и строка «до лейки» — общие для сада и вкладки шагов. */
    private void refreshWaterCard() {
        if (waterCount != null) waterCount.set(game.water);
        if (cardStreak != null) cardStreak.setText(game.streak + " дн.");
        if (nextLeek != null) nextLeek.setText("до лейки " + Ui.steps(game.waterToNext()));
    }

    private void refreshAll() {
        int steps = totalSteps();
        animateSteps(steps);
        ring.setProgress(steps, game.goal);

        double pctRaw = steps * 100.0 / Math.max(1, game.goal);
        String pctText = pctRaw < 1.0
                ? String.format(Locale.US, "%.1f", pctRaw).replace('.', ',')
                : String.valueOf((int) Math.min(100, pctRaw));
        goalView.setText("из " + fmt(game.goal) + " · " + pctText + "%");

        game.syncSteps(Steps.today(), steps);
        game.save(this);

        if (nextLeek != null) {
            int toNext = game.waterToNext();
            nextLeek.setText("до лейки " + Ui.steps(toNext));
        }
        updateHistory(steps);
        updatePill(steps);

        garden.setGame(game);
        quests.setGame(game);
        refreshProfile();
        if (waterCount != null) waterCount.set(game.water);
        cardStreak.setText(game.streak + " дн.");
        rebuildAchievements();

        checkAchievements();
    }

    private TextView permBtn;

    // Достижение за собранный цветок иначе всплывает поверх диалога с открыткой
    // и закрывает его кнопки — придерживаем его до закрытия диалога.
    private boolean cardFlowActive;
    private String pendingAch;

    private void permNeeded() {
        if (permBtn == null) return;
        permBtn.setVisibility(View.VISIBLE);
    }

    private void permDone() {
        if (permBtn == null) return;
        permBtn.setVisibility(View.GONE);
    }

    private void updateHistory(int steps) {
        java.util.LinkedHashMap<String, Integer> days = Steps.dailySteps(this);
        String today = Steps.today();
        days.put(today, Math.max(steps, days.containsKey(today) ? days.get(today) : 0));

        int[] v = new int[7];
        String[] l = new String[7];
        int sum = 0;

        // идём календарём назад — без арифметики по эпохе (иначе съезжает часовой пояс)
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12);
        cal.add(java.util.Calendar.DAY_OF_YEAR, -6);

        for (int i = 0; i < 7; i++) {
            String key = fmt.format(cal.getTime());
            Integer val = days.get(key);
            v[i] = val == null ? 0 : val;
            l[i] = key.substring(8, 10);
            sum += v[i];
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        history.setData(v, l, game.goal);
        history.setVisibility(View.VISIBLE);
        historyHint.setVisibility(sum == 0 ? View.VISIBLE : View.GONE);
    }

    private void updatePill(int steps) {
        boolean sensorOk = hasStepPermission();
        // плашка берёт цвета из темы: в «Обсидиане» акцент белый, а не мята
        int bg = Theme.SURFACE_HI;
        if (!Steps.hasSensor(getApplicationContext())) pill("Нет счётчика шагов", 0xFFFFB49A, bg);
        else if (!sensorOk) pill("Нужно разрешение", 0xFFFFB49A, bg);
        else if (steps >= game.goal) pill("Цель достигнута", Theme.GOLD, bg);
        else if (game.streak > 0) pill("Серия " + game.streak + " дн. · вода " + game.water, Theme.ACCENT_SOFT, bg);
        else pill("Экономный режим", Theme.ACCENT_SOFT, bg);
    }

    private void pill(String s, int fg, int bg) {
        pill.setText(s);
        pill.setTextColor(fg);
        pill.setBackground(rounded(bg, 30));
    }



    // ---------------- профиль ----------------

    private TextView statCard(LinearLayout row, String label, boolean first) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SURFACE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Theme.STROKE);
        col.setBackground(bg);
        col.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Theme.FAINT);
        l.setTextSize(8.5f);
        l.setLetterSpacing(0.18f);
        col.addView(l);

        TextView v = new TextView(this);
        v.setText("0");
        v.setTextColor(Theme.TEXT);
        v.setTextSize(17);
        col.addView(v);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        // отступ только у левой карточки: если дать обеим, пара уезжает влево
        p.rightMargin = first ? dp(8) : 0;
        row.addView(col, p);
        return v;
    }

    private ScrollView buildProfilePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(4), dp(16), dp(16));

        avatar = new AvatarView(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(92), dp(92));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(8);
        root.addView(avatar, alp);

        nickView = new TextView(this);
        nickView.setTextSize(19);
        nickView.setTextColor(Theme.TEXT);
        nickView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(10);
        root.addView(nickView, nlp);

        rankView = new TextView(this);
        rankView.setTextSize(12);
        rankView.setTextColor(Theme.ACCENT_SOFT);
        rankView.setGravity(Gravity.CENTER);
        root.addView(rankView);

        profSub = new TextView(this);
        avatar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickAvatar(); }
        });

        profSub.setTextSize(10);
        profSub.setTextColor(Theme.FAINT);
        profSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        root.addView(profSub, slp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(12);

        shopButton = outlined("Магазин");
        shopButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ShopActivity.class));
            }
        });
        // 10dp: при 8dp третья кнопка выходила на пиксель шире
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        bp1.rightMargin = dp(10);
        actions.addView(shopButton, bp1);

        herbButton = outlined("Коллекция");
        herbButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CollectionActivity.class));
            }
        });
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        bp2.rightMargin = dp(10);
        actions.addView(herbButton, bp2);

        bagButton = outlined("Инвентарь");
        bagButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, InventoryActivity.class));
            }
        });
        actions.addView(bagButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(actions, ap);

        medalRow = new LinearLayout(this);
        medalRow.setOrientation(LinearLayout.HORIZONTAL);
        medalRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mrp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrp.topMargin = dp(14);
        root.addView(medalRow, mrp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r1p.topMargin = dp(16);
        LinearLayout editRow = new LinearLayout(this);
        editRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams erp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        erp.topMargin = dp(14);

        Button nickBtn = outlined("Сменить ник");
        nickBtn.setSingleLine(true);
        nickBtn.setAutoSizeTextTypeUniformWithConfiguration(
                9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        nickBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editNick(); }
        });
        LinearLayout.LayoutParams nbp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        nbp.rightMargin = dp(8);
        editRow.addView(nickBtn, nbp);

        Button avBtn = outlined("Сменить аватар");
        avBtn.setSingleLine(true);
        avBtn.setAutoSizeTextTypeUniformWithConfiguration(
                9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        avBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickAvatar(); }
        });
        editRow.addView(avBtn, new LinearLayout.LayoutParams(0, dp(46), 1f));

        root.addView(editRow, erp);

        statP = statCard(row1, "ОЧКИ", true);
        pointsCount = new CountUp(statP, new CountUp.Fmt() {
            @Override public String at(int v) { return String.valueOf(v); }
        });
        statS = statCard(row1, "СЕРИЯ", false);
        root.addView(row1, r1p);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2p.topMargin = dp(8);
        statW = statCard(row2, "ПОЛИТО", true);
        statT = statCard(row2, "ШАГИ СЕГОДНЯ", false);
        root.addView(row2, r2p);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable dbg = new GradientDrawable();
        dbg.setColor(Theme.SURFACE);
        dbg.setCornerRadius(dp(22));
        dbg.setStroke(dp(1), Theme.STROKE);
        details.setBackground(dbg);
        details.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(14);

        dBest = addStatRow(details, "Лучший день", false);
        dAvg = addStatRow(details, "В среднем за день", false);
        dTotal = addStatRow(details, "Шагов всего", false);
        dDays = addStatRow(details, "Дней в саду", false);
        dFlowers = addStatRow(details, "В букете", false);
        dSpecies = addStatRow(details, "Открыток собрано", true);
        root.addView(details, dlp);

        profAch = new TextView(this);
        profAch.setTextSize(11);
        profAch.setTextColor(Theme.MUTED);
        LinearLayout.LayoutParams alp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp2.topMargin = dp(14);
        root.addView(profAch, alp2);

        scroll.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }


    private void editGoal() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(game.goal));
        input.setTextColor(Theme.TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Норма шагов в день")
                .setMessage("От неё считается кольцо прогресса и процент.")
                .setView(input)
                .setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            int g = Integer.parseInt(input.getText().toString().trim());
                            if (g >= 1000 && g <= 100000) {
                                game.goal = g;
                                game.save(MainActivity.this);
                                getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                                        .edit().putInt("goal", g).apply();
                                refreshAll();
                            }
                        } catch (Exception ignored) { }
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void editNick() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(game.nick);
        input.setSelection(input.getText().length());
        input.setTextColor(Theme.TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Ник игрока")
                .setView(input)
                .setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String n = input.getText().toString().trim();
                        if (n.isEmpty()) return;
                        if (n.length() > 24) n = n.substring(0, 24);
                        game.nick = n;
                        game.save(MainActivity.this);
                        refreshProfile();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickAvatar() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(4));

        final AlertDialog[] holder = new AlertDialog[1];
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Аватар");

        for (int st = 0; st < 4; st++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(10);
            for (int pl = 0; pl < 5; pl++) {
                final int style = st, palette = pl;
                AvatarView v = new AvatarView(this);
                v.setStyle(style, palette);
                v.setNick("preview");

                GradientDrawable sel = new GradientDrawable();
                sel.setShape(GradientDrawable.OVAL);
                sel.setColor(Theme.SURFACE);
                if (style == game.avatarStyle && palette == game.avatarPalette) {
                    sel.setStroke(dp(2), Theme.ACCENT);
                }
                v.setBackground(sel);

                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        game.avatarStyle = style;
                        game.avatarPalette = palette;
                        game.save(MainActivity.this);
                        refreshProfile();
                        if (holder[0] != null) holder[0].dismiss();
                    }
                });

                LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(56), dp(56));
                vp.rightMargin = dp(10);
                row.addView(v, vp);
            }
            box.addView(row, rp);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(6);

        Button photoBtn = outlined("Своё фото");
        photoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (holder[0] != null) holder[0].dismiss();
                pickPhoto();
            }
        });
        LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        pbp.rightMargin = dp(8);
        actions.addView(photoBtn, pbp);

        Button clearBtn = outlined("Убрать фото");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                clearAvatar();
                if (holder[0] != null) holder[0].dismiss();
            }
        });
        actions.addView(clearBtn, new LinearLayout.LayoutParams(0, dp(46), 1f));
        box.addView(actions, ap);

        builder.setView(box);
        builder.setNegativeButton("Закрыть", null);
        holder[0] = builder.show();
    }


    private TextView addStatRow(LinearLayout parent, String label, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(Theme.MUTED);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText("—");
        v.setTextSize(12);
        v.setTextColor(Theme.TEXT);
        row.addView(v);

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // разделитель только между строками: под последней он висел в воздухе
        if (!last) {
            View div = new View(this);
            div.setBackgroundColor(Theme.STROKE);
            parent.addView(div, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
        return v;
    }

    private String fmt(int n) {
        StringBuilder sb = new StringBuilder();
        String s = String.valueOf(n);
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) sb.append(' ');
        }
        return sb.reverse().toString();
    }

    private void loadAvatar() {
        // сбрасываем кэш: после сброса прогресса фото удалено, и старая
        // картинка не должна висеть в профиле до перезапуска
        avatarBitmap = null;
        try {
            File f = new File(getFilesDir(), "avatar.png");
            if (f.exists()) avatarBitmap = BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception ignored) { }
    }

    private void pickPhoto() {
        try {
            Intent pick = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pick.setType("image/*");
            startActivityForResult(pick, 200);
        } catch (Exception e) {
            try {
                Intent any = new Intent(Intent.ACTION_GET_CONTENT);
                any.setType("image/*");
                startActivityForResult(Intent.createChooser(any, "Выберите фото"), 200);
            } catch (Exception e2) {
                Toast.makeText(this, "Галерея недоступна", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void clearAvatar() {
        avatarBitmap = null;
        try {
            File f = new File(getFilesDir(), "avatar.png");
            if (f.exists()) f.delete();
        } catch (Exception ignored) { }
        refreshProfile();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 200 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            android.net.Uri uri = data.getData();

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, bounds);
            if (in != null) in.close();

            int scale = 1;
            while (bounds.outWidth / scale > 640) scale *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = scale;
            in = getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(in, null, opts);
            if (in != null) in.close();

            if (bm == null) {
                Toast.makeText(this, "Не удалось прочитать фото", Toast.LENGTH_SHORT).show();
                return;
            }

            avatarBitmap = bm;
            FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "avatar.png"), false);
            bm.compress(Bitmap.CompressFormat.PNG, 92, out);
            out.close();

            refreshProfile();
            Toast.makeText(this, "Фото установлено", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }


    private void rebuildMedals() {
        if (medalRow == null) return;
        medalRow.removeAllViews();

        TextView label = new TextView(this);
        label.setText("МЕДАЛИ");
        label.setTextColor(Theme.FAINT);
        label.setTextSize(Ui.T_CAPS);
        label.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        medalRow.addView(label, lp);

        int shown = 0;
        for (Achievements.Ach a : Achievements.ALL) {
            if (!game.unlocked.contains(a.id) || shown >= 6) continue;
            ImageView iv = new ImageView(this);
            int rid = getResources().getIdentifier("medal_" + a.id, "drawable", getPackageName());
            if (rid != 0) iv.setImageResource(rid);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(34), dp(34));
            p.rightMargin = dp(6);
            medalRow.addView(iv, p);
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("пока нет — вырастите первый цветок");
            empty.setTextColor(Theme.FAINT);
            empty.setTextSize(10);
            medalRow.addView(empty);
        }
    }

    private void refreshProfile() {
        if (nickView == null) return;
        avatar.setStyle(game.avatarStyle, game.avatarPalette);
        avatar.setPhoto(avatarBitmap);
        avatar.setFrame(game.avatarFrame);
        avatar.setNick(game.nick);
        nickView.setText(game.nick);
        rankView.setText(game.rank() + " · " + game.level() + " уровень");
        // сколько осталось до следующего звания — видно, к чему идёшь
        int toNext = game.pointsToNextRank();
        String nextRank = game.nextRank();
        profSub.setText(nextRank.isEmpty()
                ? "в саду с " + humanDate(game.created)
                : "до звания «" + nextRank + "» " + fmt(toNext) + " очков");
        if (pointsCount != null) pointsCount.set(game.points);
        statS.setText(game.streak + " дн.");
        statW.setText(String.valueOf(game.wateredTotal));
        statT.setText(String.valueOf(totalSteps()));

        int opened = 0;
        for (Achievements.Ach a : Achievements.ALL) if (game.unlocked.contains(a.id)) opened++;
        profAch.setText("Достижения: " + opened + " из " + Achievements.ALL.size());
        if (goalButton != null) goalButton.setText("Норма шагов в день: " + fmt(game.goal));
        if (shopButton != null) shopButton.setText("Магазин");
        if (herbButton != null) herbButton.setText("Коллекция");
        if (bagButton != null) bagButton.setText("Инвентарь");
        rebuildMedals();

        int days = Math.max(1, game.daysInGarden());

        dBest.setText(fmt(game.bestDay));
        dAvg.setText(fmt(game.totalSteps / days));
        dTotal.setText(fmt(game.totalSteps));
        dDays.setText(String.valueOf(days));
        dFlowers.setText(game.bloomed.size() + " из " + Species.COUNT);
        dSpecies.setText(String.valueOf(game.cards.size()));
    }

    private String humanDate(String iso) {
        try {
            String[] p = iso.split("-");
            String[] months = {"января", "февраля", "марта", "апреля", "мая", "июня",
                    "июля", "августа", "сентября", "октября", "ноября", "декабря"};
            return Integer.parseInt(p[2]) + " " + months[Integer.parseInt(p[1]) - 1];
        } catch (Exception e) {
            return iso;
        }
    }

    // ---------------- достижения ----------------


    /** Показать открытку собранного цветка и предложить сохранить. */
    private void showNewCard() {
        if (game.cards.isEmpty()) return;
        final GameState.Card card = game.cards.get(game.cards.size() - 1);
        cardFlowActive = true;

        final Bitmap[] bmp = new Bitmap[1];
        new Thread(new Runnable() {
            @Override public void run() {
                bmp[0] = Postcard.render(MainActivity.this, card);
                ui.post(new Runnable() {
                    @Override public void run() { openCardDialog(card, bmp[0]); }
                });
            }
        }).start();
    }

    private void openCardDialog(final GameState.Card card, Bitmap bmp) {
        if (bmp == null || isFinishing()) { flushPendingAch(); return; }

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
                .setTitle("Цветок собран")
                .setView(box)
                .setPositiveButton("Сохранить в галерею", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Bitmap full = Postcard.render(MainActivity.this, card);
                        java.io.File f = Postcard.save(MainActivity.this, full, card.name);
                        if (f != null) {
                            Toast.makeText(MainActivity.this,
                                    "Сохранено: Pictures/Сад/" + f.getName(),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Не удалось сохранить",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Позже", null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface d) { flushPendingAch(); }
                })
                .show();
    }

    private void flushPendingAch() {
        cardFlowActive = false;
        if (pendingAch != null) {
            Toast.makeText(this, pendingAch, Toast.LENGTH_SHORT).show();
            pendingAch = null;
        }
    }

    private void checkAchievements() {
        java.util.List<Achievements.Ach> fresh = Achievements.checkAndUnlock(game, totalSteps());
        if (!fresh.isEmpty()) {
            game.save(this);
            String msg = "Достижение: " + fresh.get(0).title;
            if (cardFlowActive) pendingAch = msg;
            else Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            rebuildAchievements();
            if (waterCount != null) waterCount.set(game.water);
        }
    }

    private void rebuildAchievements() {
        if (achList == null) return;
        achList.removeAllViews();

        int opened = 0;
        for (Achievements.Ach a : Achievements.ALL) {
            if (game.unlocked.contains(a.id)) opened++;
        }

        TextView head = new TextView(this);
        head.setText("ОТКРЫТО " + opened + " ИЗ " + Achievements.ALL.size());
        head.setTextColor(Theme.FAINT);
        head.setTextSize(10);
        head.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(8);
        hp.bottomMargin = dp(10);
        achList.addView(head, hp);

        for (Achievements.Ach a : Achievements.ALL) {
            achList.addView(achCard(a));
        }
    }

    private View achCard(Achievements.Ach a) {
        boolean on = game.unlocked.contains(a.id);
        int cur = Math.min(Achievements.progress(a, game, totalSteps()), a.target);
        boolean levelAch = a.metric == Achievements.Metric.LEVEL;
        if (levelAch) {
            // уровень стартует с 1 — показываем прогресс от нуля
            cur = Math.max(0, Math.min(a.target - 1, game.level() - 1));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        // цвета из темы: раньше здесь были вкомпилированы тёмно-зелёные, и в светлой
        // теме карточки достижений выглядели тёмными блоками
        bg.setColor(on ? Theme.SURFACE_HI : Theme.SURFACE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), on ? Theme.ACCENT_DEEP : Theme.CARD_STROKE);
        row.setBackground(bg);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));

        ImageView badge = new ImageView(this);
        try {
            int rid = getResources().getIdentifier("medal_" + a.id, "drawable", getPackageName());
            if (rid != 0) badge.setImageResource(rid);
            if (on) {
                badge.setColorFilter(null);
                badge.setAlpha(1f);
            } else {
                badge.setColorFilter(Theme.FAINT,
                        android.graphics.PorterDuff.Mode.MULTIPLY);
                badge.setAlpha(0.65f);
            }
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(38), dp(38));
        blp.rightMargin = dp(10);
        row.addView(badge, blp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(a.title);
        title.setTextSize(13.5f);
        title.setTextColor(Color.parseColor(on ? "#F2FBF6" : "#7E9A8F"));
        col.addView(title);

        TextView desc = new TextView(this);
        desc.setText(a.desc);
        desc.setTextSize(10);
        desc.setTextColor(Color.parseColor(on ? "#6E8A80" : "#3F5A50"));
        col.addView(desc);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(levelAch ? Math.max(1, a.target - 1) : a.target);
        bar.setProgress(cur);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        brp.topMargin = dp(6);
        col.addView(bar, brp);

        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(this);
        state.setText(on ? "готово"
                : (levelAch ? "уровень " + game.level() + " из " + a.target
                            : cur + " / " + a.target));
        state.setTextSize(10);
        state.setTextColor(Color.parseColor(on ? "#4BE3B0" : "#3F5A50"));
        state.setPadding(dp(10), 0, 0, 0);
        row.addView(state);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        row.setLayoutParams(rp);
        return row;
    }

    // ---------------- действия ----------------

    private TextView syncLine;
    private long lastSyncAt = 0;
    private boolean resumed = false;
    private int tickCount = 0;

    /** Строка состояния вместо кнопки: «обновлено только что». */
    private void updateSyncLine() {
        if (syncLine == null) return;
        if (syncing) { syncLine.setText("читаю датчик…"); return; }
        if (lastSyncAt == 0) { syncLine.setText("шаги обновляются автоматически"); return; }
        long sec = Math.max(0, (System.currentTimeMillis() - lastSyncAt) / 1000);
        String when = sec < 5 ? "только что"
                : (sec < 60 ? sec + " с назад"
                : (sec / 60) + " мин назад");
        syncLine.setText("обновлено " + when);
    }

    /** Раз в секунду обновляем подпись и раз в минуту тихо перечитываем датчик. */
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateSyncLine();
            tickCount++;
            if (tickCount % 60 == 0 && tab == 0 && !syncing && resumed) runSync();
            ui.postDelayed(this, 1000);
        }
    };

    private void runSync() {
        if (syncing) return;
        syncing = true;
        updateSyncLine();
        final MainActivity self = this;

        new Thread(new Runnable() {
            @Override public void run() {
                final SensorSync.Result r;
                try {
                    r = SensorSync.sync(self);
                } catch (Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            syncing = false;
                            updateSyncLine();
                        }
                    });
                    return;
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        syncing = false;
                        lastSyncAt = System.currentTimeMillis();
                        updateSyncLine();
                        StepWidget.refresh(self);
                        refreshAll();
                    }
                });
            }
        }).start();
    }

    private void askFirstRunPermissions() {
        SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
        if (p.getBoolean("asked_perms", false)) return;
        p.edit().putBoolean("asked_perms", true).apply();
        askRuntimePermissions();
    }

    private void askRuntimePermissions() {
        List<String> need = new ArrayList<>();
        if (!hasStepPermission()) need.add("android.permission.ACTIVITY_RECOGNITION");
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                   != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.WRITE_EXTERNAL_STORAGE");
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (need.isEmpty()) return;
        requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    private void askManualSteps() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("например 5000");
        input.setTextColor(Theme.TEXT);
        input.setHintTextColor(Theme.FAINT);

        new AlertDialog.Builder(this)
                .setTitle("Начислить шаги (тест)")
                .setMessage("Добавится к сегодняшним шагам и превратится в воду.")
                .setView(input)
                .setPositiveButton("Начислить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            int add = Integer.parseInt(input.getText().toString().trim());
                            SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
                            manualSteps();
                            p.edit().putInt("manual_steps", p.getInt("manual_steps", 0) + add).apply();
                            refreshAll();
                        } catch (Exception ignored) { }
                    }
                })
                .setNeutralButton("Сбросить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                                .edit().putInt("manual_steps", 0).apply();
                        refreshAll();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private boolean hasStepPermission() {
        if (Build.VERSION.SDK_INT < 29) return true;
        return checkSelfPermission("android.permission.ACTIVITY_RECOGNITION")
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean batteryExempt() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void askBatteryExemption() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private void toggleService() {
        SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
        boolean on = p.getBoolean(Steps.KEY_SERVICE, false);
        boolean next = !on;
        p.edit().putBoolean(Steps.KEY_SERVICE, next).apply();

        Intent svc = new Intent(this, StepService.class);
        if (next) {
            svc.setAction(StepService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } else {
            svc.setAction(StepService.ACTION_STOP);
            startService(svc);
        }
        refreshAll();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refreshAll();
    }
}
