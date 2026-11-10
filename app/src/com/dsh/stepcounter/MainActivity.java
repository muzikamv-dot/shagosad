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
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
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
    private TextView statP, statS, statW, statT, statStepsStreak, statNormDays;
    private TextView dBest, dAvg, dTotal, dDays, dFlowers, dSpecies;
    private Button goalButton;
    private Button shopButton, herbButton, bagButton, achButton;

    private TextView headerTime, headerSub;
    private ScrollView eventPage;
    private LinearLayout eventBox;
    private final android.os.Handler clockHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 20000);
        }
    };
    private Bitmap avatarBitmap;
    private ScrollView achPage;

    private TextView stepsView;
    private TextView goalView;
    /** Строка «≈ 5,0 км · 62 мин» внутри кольца. */
    private TextView subView;
    /** Диапазон графика: 7, 30 или 90 дней. */
    private int histRange = 7;
    private final java.util.ArrayList<TextView> rangeTabs = new java.util.ArrayList<>();
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
    private final String[] TITLES = {"ШАГОСАД", "САД", "ЗАДАНИЯ", "ПРОФИЛЬ", "ИВЕНТЫ"};

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
        // фон и украшения уходят под интерфейс: ветки — подложка, а не поверх кнопок
        View content = buildUi();
        android.graphics.drawable.Drawable pageBg = content.getBackground();
        content.setBackground(null);
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        if (pageBg != null) holder.setBackground(pageBg);
        SnowBackdrop.attach(this, holder);   // статичные снежинки для новогодней темы
        NewYearDecor.attach(this, holder);
        holder.addView(content, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(holder);
        if (!AppFlavor.STORE) {
            getWindow().getDecorView().post(new Runnable() {
                @Override public void run() { Updater.promptIfUpdate(MainActivity.this); }
            });
        }

        // Награды: всё, что получено и ещё ни разу не показывалось, проигрываем
        // по очереди полноэкранно. После обновления сюда попадут и старые медали.
        final java.util.List<Achievements.Ach> toShow = Achievements.unshown(game, this);
        if (!toShow.isEmpty()) {
            getWindow().getDecorView().post(new Runnable() {
                @Override public void run() {
                    AchievementActivity.showQueue(MainActivity.this, toShow);
                }
            });
        }
        AlarmScheduler.schedule(this);
        SyncJobService.schedule(this);   // замер шагов в фоне: точная разбивка по дням
        AlarmScheduler.cancelLegacyWidgetTick(this);
        AlarmScheduler.scheduleSync(this);   // и будильник: он пробивается через сон
        askFirstRunPermissions();
        // погода для сцены: один запрос на запуск приложения, дальше живёт кэш
        Weather.refreshOnStart(this);

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
        rebuildEvents();   // вкладка ивентов зависит от даты и тестового флага
        runSync();
        startLiveSteps();   // живой счётчик: цифра меняется, как только сделан шаг
        ui.removeCallbacks(ticker);
        ui.post(ticker);
        // часы в шапке: обновляем время, дату и город
        updateClock();
        clockHandler.removeCallbacks(clockTick);
        clockHandler.postDelayed(clockTick, 20000);
    }

    @Override
    protected void onPause() {
        resumed = false;
        stopLiveSteps();
        clockHandler.removeCallbacks(clockTick);
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

        // Шапка справа: время, под ним дата и город (город приходит из погоды).
        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        when.setGravity(Gravity.END);

        headerTime = new TextView(this);
        headerTime.setTextColor(Theme.ACCENT_SOFT);
        headerTime.setTextSize(15);
        headerTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerTime.setGravity(Gravity.END);
        when.addView(headerTime);

        headerSub = new TextView(this);
        headerSub.setTextColor(Theme.MUTED);
        headerSub.setTextSize(10.5f);
        headerSub.setGravity(Gravity.END);
        headerSub.setMaxLines(1);
        headerSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        when.addView(headerSub);

        header.addView(when);
        updateClock();

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

        // Пятая вкладка — «Ивенты». Достижения переехали в профиль кнопкой,
        // чтобы освободить место под праздничные события.
        achPage = buildEventsPage();
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

    /** Время, дата и город в шапке. Город берём из погоды, если он уже известен. */
    private void updateClock() {
        if (headerTime == null) return;
        Date now = new Date();
        headerTime.setText(new SimpleDateFormat("HH:mm", new Locale("ru")).format(now));
        String date = new SimpleDateFormat("d MMMM", new Locale("ru")).format(now);
        String city = Weather.city(this);
        headerSub.setText(city.isEmpty() ? date : date + " · " + city);
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
        bar.setBackgroundColor(Theme.NAV);
        // по 8dp сверху и снизу: подсветка вкладки не прилипает к разделителю
        bar.setPadding(dp(6), dp(8), dp(6), dp(8));

        String[] names = {"Шаги", "Сад", "Задания", "Профиль", "Ивенты"};
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
            // «Достижения» длиннее прежних «Наград»: даём подписи ужиматься,
            // иначе на крупном системном шрифте она обрезается
            label.setTextSize(10.5f);
            if (Build.VERSION.SDK_INT >= 26) {
                label.setAutoSizeTextTypeUniformWithConfiguration(
                        8, 11, 1, TypedValue.COMPLEX_UNIT_SP);
            }
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
        if (index == 4) rebuildEvents();

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
        // кольцо крупнее: под ним умещаются и пяти-, и шестизначные шаги без мельчения
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(250), dp(250));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        rlp.topMargin = dp(6);
        page.addView(ringBox, rlp);

        ring = new RingView(this);
        ringBox.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.VERTICAL);
        numbers.setGravity(Gravity.CENTER);
        // три строки внутри кольца чуть выше центра, иначе нижняя лежит на дуге
        numbers.setTranslationY(-dp(7));
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
        // десятки тысяч шагов не должны вылезать за круг: размер подбирается сам,
        // а боковые отступы держат текст внутри дуги
        stepsView.setSingleLine(true);
        // ширина ровно по внутреннему кругу: иначе пять цифр вылезают за дугу
        stepsView.setAutoSizeTextTypeUniformWithConfiguration(18, 52, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                dp(150), ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.gravity = Gravity.CENTER_HORIZONTAL;
        numbers.addView(stepsView, sl);

        goalView = new TextView(this);
        goalView.setText("из 10 000 шагов");
        goalView.setTextColor(Theme.MUTED);
        goalView.setTextSize(12);
        goalView.setGravity(Gravity.CENTER);
        goalView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        numbers.addView(goalView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // расстояние и время в движении — одной мелкой строкой внутри кольца:
        // тем, кто пришёл за шагомером, это нужно сразу, а места почти не занимает
        subView = new TextView(this);
        subView.setTextColor(Theme.FAINT);
        subView.setTextSize(10.5f);
        // строка может вырасти до «км · мин · ккал» — подбираем размер сами
        subView.setSingleLine(false);
        subView.setGravity(Gravity.CENTER);
        subView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        svlp.topMargin = dp(2);
        numbers.addView(subView, svlp);

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

        // график: заголовок слева, переключатель диапазона справа
        LinearLayout chartHead = new LinearLayout(this);
        chartHead.setOrientation(LinearLayout.HORIZONTAL);
        chartHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chl.topMargin = dp(12);
        page.addView(chartHead, chl);

        TextView weekTitle = new TextView(this);
        weekTitle.setText("ГРАФИК");
        weekTitle.setTextColor(Theme.FAINT);
        weekTitle.setTextSize(Ui.T_CAPS);
        weekTitle.setLetterSpacing(0.10f);
        chartHead.addView(weekTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final int[] ranges = {7, 30, 90};
        final String[] rangeNames = {"7 дней", "30 дней", "90 дней"};
        rangeTabs.clear();
        for (int i = 0; i < ranges.length; i++) {
            final int days = ranges[i];
            TextView tab = new TextView(this);
            tab.setText(rangeNames[i]);
            tab.setTextSize(11);
            tab.setTypeface(Ui.medium());
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(9), dp(3), dp(9), dp(3));
            GradientDrawable tbg = new GradientDrawable();
            tbg.setCornerRadius(dp(9));
            tbg.setColor(Theme.SURFACE_HI);
            tab.setBackground(tbg);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) tlp.leftMargin = dp(6);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (histRange == days) return;
                    histRange = days;
                    styleRangeTabs();
                    updateHistory(totalSteps());
                }
            });
            chartHead.addView(tab, tlp);
            rangeTabs.add(tab);
        }
        histRange = 7;
        styleRangeTabs();

        history = new HistoryView(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(118));
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
        l2.setText("ПОЛИВЫ ПОДРЯД");
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
    /** Размер цифр под число: до пяти знаков — 52 sp, дальше меньше. */
    private void setStepsText(int value) {
        String s = String.valueOf(value);
        // максимум, что бывает, — 99 000 (пять знаков): он влезает в 52 sp
        float sp = s.length() <= 5 ? 52f : 40f;
        stepsView.setTextSize(sp);
        stepsView.setText(s);
    }
    private void animateSteps(final int target) {
        if (shownSteps < 0 || Math.abs(target - shownSteps) > 20000) {
            shownSteps = target;
            setStepsText(target);
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
                setStepsText(shownSteps);
            }
        });
        stepsAnim.start();
    }

    /** Карточка воды и строка «до лейки» — общие для сада и вкладки шагов. */
    private void refreshWaterCard() {
        if (waterCount != null) waterCount.set(game.water);
        if (cardStreak != null) cardStreak.setText(game.streak + " дн.");
        if (nextLeek == null) return;
        if (game.waterStopped()) {
            // Засохший цветок: про «ещё 0 шагов» писать нечего, нужна одна понятная строка.
            nextLeek.setText("цветок засох: вода не копится");
            return;
        }
        String main = "ещё " + Ui.steps(game.waterToNext()) + " — и новая лейка";
        if (game.waterHalved()) {
            // Вторая строка поясняет, почему копится медленнее, — иначе цифра выглядит странно.
            String hint = "сорняк на грядке: замедляет накопление воды.";
            SpannableStringBuilder sb = new SpannableStringBuilder(main + "\n" + hint);
            sb.setSpan(new RelativeSizeSpan(0.86f), main.length() + 1, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(Theme.MUTED), main.length(), sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            nextLeek.setText(sb);
        } else {
            nextLeek.setText(main);
        }
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

        if (subView != null) {
            double km = steps * Steps.strideMeters(this) / 1000.0;
            boolean[] est = new boolean[1];
            int minutes = Steps.minutesShown(this, steps, est);
            String kmText = String.format(Locale.US, "%.1f", km).replace('.', ',');
            String line = kmText + " км · " + (est[0] ? "≈ " : "") + Ui.duration(minutes);
            if (Steps.showCalories(this)) {
                line += "\n≈ " + fmt(Steps.calories(this, steps)) + " ккал";
            }
            subView.setText(line);
        }

        game.syncSteps(Steps.today(), steps);
        game.save(this);

        // Надпись обновляем только здесь: раньше её перетирала эта же строка и
        // пропадали пояснения «сорняки» и «цветок засох».
        refreshWaterCard();
        updateHistory(steps);
        updatePill(steps);

        garden.setGame(game);
        quests.setGame(game);
        refreshProfile();
        if (waterCount != null) waterCount.set(game.water);
        cardStreak.setText(game.streak + " дн.");

        checkAchievements();
    }

    // Достижение за собранный цветок иначе всплывает поверх диалога с открыткой
    // и закрывает его кнопки — придерживаем его до закрытия диалога.
    private boolean cardFlowActive;
    private String pendingAch;

    /** Подсветить выбранный диапазон графика. */
    private void styleRangeTabs() {
        int i = 0;
        for (TextView t : rangeTabs) {
            int days = i == 0 ? 7 : (i == 1 ? 30 : 90);
            boolean on = days == histRange;
            t.setTextColor(on ? Theme.BG_TOP : Theme.MUTED);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(9));
            bg.setColor(on ? Theme.ACCENT : Theme.SURFACE_HI);
            t.setBackground(bg);
            i++;
        }
    }

    private void updateHistory(int steps) {
        int n = histRange;
        int[] v = Steps.lastDays(this, n);
        if (v.length == 0) return;
        // сегодня уточняем текущим значением: кэш может быть свежее журнала
        v[v.length - 1] = Math.max(v[v.length - 1], steps);

        String[] l = new String[n];
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12);
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(n - 1));

        int sum = 0;
        for (int i = 0; i < n; i++) {
            String key = fmt.format(cal.getTime());
            if (n <= 7) {
                l[i] = key.substring(8, 10);                 // дни недели: 03 04 05 …
            } else if (n == 30 && i % 5 == 4) {
                l[i] = key.substring(8, 10);                 // каждый пятый день месяца
            } else {
                l[i] = "";
            }
            sum += v[i];
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        history.setShowValues(n <= 7);   // цифры читаются только на недельном графике
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
        else if (game.streak > 0) pill("Поливы " + game.streak + " дн. · вода " + game.water, Theme.ACCENT_SOFT, bg);
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

    /**
     * Вкладка «Ивенты»: праздничный анонс. Всё по центру и в две колонки одинаковой
     * ширины — раньше текст прилипал влево, а справа оставалась пустота.
     */
    private ScrollView buildEventsPage() {
        eventPage = new ScrollView(this);
        eventPage.setFillViewport(true);
        eventBox = new LinearLayout(this);
        eventBox.setOrientation(LinearLayout.VERTICAL);
        eventBox.setGravity(Gravity.CENTER_HORIZONTAL);
        eventBox.setPadding(dp(18), dp(8), dp(18), dp(12));
        eventPage.addView(eventBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildEvents();
        return eventPage;
    }

    /** Перерисовать вкладку «Ивенты» под текущее состояние: заглушка, анонс или живой ивент. */
    /** Карточка новогоднего ивента на вкладке «Ивенты». */
    /** Афиша ивента за 20 дней до старта: картинка и обратный отсчёт на табличке. */
    /** Афиша ивента: картинка на весь экран вкладки и обратный отсчёт на табличке. */
    private void buildPromoCard() {
        final String caption = NewYear.promoCaption();
        eventBox.setPadding(0, 0, 0, 0);

        final FrameLayout card = new FrameLayout(this);
        card.setBackgroundColor(0xFF0B1430);
        card.setClipToOutline(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this,
                        "Новогодний ивент начнётся 1 декабря", Toast.LENGTH_SHORT).show();
            }
        });

        ImageView promo = new ImageView(this);
        int pid = getResources().getIdentifier("ny_promo", "drawable", getPackageName());
        if (pid != 0) promo.setImageResource(pid);
        promo.setScaleType(ImageView.ScaleType.FIT_XY);
        card.addView(promo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // отсчёт пишем прямо на деревянной табличке афиши
        final TextView counter = new TextView(this);
        counter.setText(caption);
        counter.setTextSize(30);
        counter.setTextColor(0xFFFFE9B0);
        counter.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        counter.setShadowLayer(12f, 0f, 3f, 0xFF000000);
        counter.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP;
        clp.leftMargin = dp(46);
        clp.rightMargin = dp(46);
        card.addView(counter, clp);
        card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int top, int r, int bottom,
                                                 int ol, int ot, int or, int ob) {
                int h = bottom - top;
                if (h <= 0) return;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) counter.getLayoutParams();
                // центр таблички в афише — 62,3 % высоты картинки
                int want = (int) (h * 0.6235f) - counter.getHeight() / 2;
                if (lp.topMargin != want) {
                    lp.topMargin = want;
                    counter.setLayoutParams(lp);
                }
            }
        });

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        eventBox.addView(card, lp);

        // картинка занимает всю видимую область вкладки
        eventPage.post(new Runnable() {
            @Override public void run() {
                int h = eventPage.getHeight();
                if (h <= 0) h = getResources().getDisplayMetrics().heightPixels * 3 / 4;
                lp.height = h;
                card.setLayoutParams(lp);
            }
        });
    }

    /** Большая праздничная карточка новогоднего ивента на вкладке «Ивенты». */
    private void buildNewYearCard() {
        final int nyYear = NewYear.seasonYear() + 1;
        FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(26));
        cardBg.setColor(0xFF0B1430);
        cardBg.setStroke(dp(1), 0xFF5C88C8);
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NewYearActivity.class));
            }
        });

        // фон зимнего леса
        ImageView nyBg = new ImageView(this);
        int bgId = getResources().getIdentifier("ny_bg", "drawable", getPackageName());
        if (bgId != 0) nyBg.setImageResource(bgId);
        nyBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(nyBg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));

        // затемнение снизу под текст
        View shade = new View(this);
        GradientDrawable shadeBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00070D1C, 0xE6070D1C});
        shade.setBackground(shadeBg);
        FrameLayout.LayoutParams shadeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        shadeLp.gravity = Gravity.BOTTOM;
        card.addView(shade, shadeLp);

        // ёлка с гирляндой и звездой
        FrameLayout treeBox = new FrameLayout(this);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(dp(188), dp(206));
        tlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tlp.topMargin = dp(12);

        ImageView nyTree = new ImageView(this);
        int treeId = getResources().getIdentifier("ny_tree", "drawable", getPackageName());
        if (treeId != 0) nyTree.setImageResource(treeId);
        nyTree.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams trLp = new FrameLayout.LayoutParams(dp(176), dp(200));
        trLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        treeBox.addView(nyTree, trLp);

        ImageView nyStar = new ImageView(this);
        int starId = getResources().getIdentifier("ny_star", "drawable", getPackageName());
        if (starId != 0) nyStar.setImageResource(starId);
        FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(dp(46), dp(46));
        stLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        treeBox.addView(nyStar, stLp);

        // снежинки по краям
        int[] flakeIds = new int[3];
        for (int i = 0; i < 3; i++) flakeIds[i] = getResources().getIdentifier("ny_flake_" + (i + 1), "drawable", getPackageName());
        int[][] flakePos = {{26, 30, 34}, {28, 210, 22}, {250, 44, 28}, {246, 168, 20}, {96, 96, 16}};
        for (int i = 0; i < flakePos.length; i++) {
            ImageView fl = new ImageView(this);
            int fid = flakeIds[i % 3];
            if (fid != 0) fl.setImageResource(fid);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(flakePos[i][2]), dp(flakePos[i][2]));
            flp.leftMargin = dp(flakePos[i][0]);
            flp.topMargin = dp(flakePos[i][1]);
            card.addView(fl, flp);
        }

        card.addView(treeBox, tlp);

        // текстовый блок снизу
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(18), 0, dp(18), dp(16));
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.gravity = Gravity.BOTTOM;

        TextView title = new TextView(this);
        title.setText("НОВОГОДНЯЯ ЁЛКА · " + nyYear);
        title.setTextSize(15);
        title.setLetterSpacing(0.10f);
        title.setTextColor(0xFFF6DFA6);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        info.addView(title);

        int opened = NewYear.openedCount(game);
        TextView sub = new TextView(this);
        sub.setText(opened == 0
                ? "Открой первое окошко адвента и наряди ёлку"
                : "Открыто " + opened + " из " + NewYear.DAYS + " · игрушки ждут на ёлке");
        sub.setTextSize(12);
        sub.setTextColor(0xFFCBD9F0);
        sub.setPadding(0, dp(4), 0, dp(10));
        info.addView(sub);

        // полоска прогресса
        FrameLayout barBox = new FrameLayout(this);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(4));
        barBg.setColor(0x55FFFFFF);
        barBox.setBackground(barBg);
        LinearLayout.LayoutParams bblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        bblp.bottomMargin = dp(12);
        info.addView(barBox, bblp);

        View barFill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(dp(4));
        fillBg.setColor(0xFFF0D48A);
        barFill.setBackground(fillBg);
        int fillW = (int) (getResources().getDisplayMetrics().widthPixels * 0.5f
                * Math.max(0, Math.min(1, opened / (float) NewYear.DAYS)));
        if (fillW < dp(4) && opened > 0) fillW = dp(4);
        barBox.addView(barFill, new FrameLayout.LayoutParams(fillW, dp(6)));

        TextView btn = new TextView(this);
        btn.setText("Открыть ёлку");
        btn.setTextSize(14);
        btn.setTextColor(0xFF0B1020);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dp(18));
        btnBg.setColor(0xFFF0D48A);
        btn.setBackground(btnBg);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NewYearActivity.class));
            }
        });
        info.addView(btn);

        card.addView(info, infoLp);

        LinearLayout.LayoutParams nylp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330));
        nylp.bottomMargin = dp(18);
        eventBox.addView(card, nylp);

        // --- блоки ниже, чтобы вкладка не пустовала ---
        TextView howTitle = new TextView(this);
        howTitle.setText("КАК ЭТО РАБОТАЕТ");
        howTitle.setTextSize(11);
        howTitle.setLetterSpacing(0.10f);
        howTitle.setTextColor(Theme.FAINT);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        htlp.topMargin = dp(6);
        htlp.bottomMargin = dp(8);
        eventBox.addView(howTitle, htlp);

        String[][] steps = {
                {"ny_orn_03", "Открывай окошко — внутри украшение для ёлки"},
                {"ny_orn_09", "Тяни игрушку из коробки в любое место экрана"},
                {"ny_star", "Долгое нажатие — вернуть игрушку в коробку"}
        };
        for (String[] step : steps) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(16));
            rowBg.setColor(0xFF101A2E);
            rowBg.setStroke(dp(1), 0xFF243652);
            row.setBackground(rowBg);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));

            ImageView icon = new ImageView(this);
            int rid = getResources().getIdentifier(step[0], "drawable", getPackageName());
            if (rid != 0) icon.setImageResource(rid);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

            TextView text = new TextView(this);
            text.setText(step[1]);
            text.setTextSize(12.5f);
            text.setTextColor(0xFFC9D8EE);
            LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp2.leftMargin = dp(12);
            row.addView(text, tlp2);

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(8);
            eventBox.addView(row, rlp);
        }

        TextView tip = new TextView(this);
        tip.setText("Открытка «Ёлка-" + nyYear + "» собирается на экране ёлки — кнопка в шапке.");
        tip.setTextSize(11.5f);
        tip.setTextColor(0xFF7E92B0);
        tip.setPadding(dp(4), dp(6), dp(4), dp(18));
        eventBox.addView(tip);
    }


    private void rebuildEvents() {
        if (eventBox == null) return;
        eventBox.removeAllViews();
        // 1 декабря — 12 января ивент; за 20 дней до старта — афиша с отсчётом.
        if (NewYear.active(this)) {
            buildNewYearCard();
            return;
        }
        if (NewYear.promoActive(this)) {
            buildPromoCard();
            return;
        }


        if (Event.finished(this)) {
            // ленту тут не показываем: она относится к прошедшему ивенту.
            // Вместо пустоты — тихая карточка-анонс будущего события.
            eventBox.addView(centered("СОБЫТИЯ", 10, Theme.FAINT, false, 26));

            LinearLayout stub = new LinearLayout(this);
            stub.setOrientation(LinearLayout.VERTICAL);
            stub.setGravity(Gravity.CENTER_HORIZONTAL);
            GradientDrawable sbg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Theme.SURFACE_HI, Theme.SURFACE});
            sbg.setCornerRadius(dp(26));
            sbg.setStroke(dp(1), Theme.CARD_STROKE);
            stub.setBackground(sbg);
            stub.setPadding(dp(18), dp(14), dp(18), dp(16));

            // силуэт будущего цветка: намёк, а не спойлер
            ImageView ghost = new ImageView(this);
            int gid = getResources().getIdentifier("event_stage_20", "drawable", getPackageName());
            if (gid != 0) ghost.setImageResource(gid);
            ghost.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ghost.setAlpha(0.20f);
            stub.addView(ghost, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

            TextView title = new TextView(this);
            title.setText("НОВЫЕ ИВЕНТЫ СКОРО");
            title.setTextColor(Theme.ACCENT_SOFT);
            title.setTextSize(17);
            title.setLetterSpacing(0.06f);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(6);
            stub.addView(title, tlp);

            stub.addView(centered("Следующий праздник уже готовится.\n"
                    + "Здесь появится анонс, дата и праздничное семечко.", 12.5f,
                    Theme.MUTED, false, 10));

            stub.addView(centered("✦   ✦   ✦", 12, Theme.FAINT, false, 12));
            stub.addView(centered("До встречи в саду", 11.5f, Theme.ACCENT_SOFT, true, 8));

            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(12);
            eventBox.addView(stub, slp);

            eventBox.addView(centered("Следите за новостями", 10, Theme.FAINT, false, 14));
            return;
        }

        if (Event.active(this)) {
            buildActiveEvent();
            return;
        }

        buildEventAnnounce();
    }

    /** Лента-триколор из референса. */
    private View eventRibbon() {
        FlagRibbon ribbon = new FlagRibbon(this);
        int rw = Math.min(dp(238), getResources().getDisplayMetrics().widthPixels - dp(48));
        int rh = Math.round(rw / FlagRibbon.aspect());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(rw, rh);
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        ribbon.setLayoutParams(rlp);
        return ribbon;
    }

    /** До ивента: анонс. */
    private void buildEventAnnounce() {
        eventBox.addView(eventRibbon());
        eventBox.addView(centered("СКОРО В ШАГОСАДЕ\nПРАЗДНИЧНОЕ СОБЫТИЕ!", 18.5f,
                Theme.ACCENT_SOFT, true, 10));
        eventBox.addView(eventBadge());
        eventBox.addView(eventAnnounceCard());
    }

    /** Ивент идёт: заголовок, окно выращивания, полив. */
    private void buildActiveEvent() {
        eventBox.addView(eventRibbon());
        eventBox.addView(centered(Event.NAME, 18, Theme.ACCENT_SOFT, true, 6));
        eventBox.addView(eventBadge());

        // открытка уже собрана: до конца ивента — благодарность с салютом
        if (game.eventCard) {
            LinearLayout thanks = new LinearLayout(this);
            thanks.setOrientation(LinearLayout.VERTICAL);
            thanks.setGravity(Gravity.CENTER_HORIZONTAL);
            GradientDrawable tbg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Theme.SURFACE_HI, Theme.SURFACE});
            tbg.setCornerRadius(dp(26));
            tbg.setStroke(dp(1), Theme.GOLD);
            thanks.setBackground(tbg);
            thanks.setPadding(dp(16), dp(10), dp(16), dp(14));

            AchievementActivity.Fireworks fw = new AchievementActivity.Fireworks(this, Theme.GOLD);
            thanks.addView(fw, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

            thanks.addView(centered("Спасибо за участие!", 17, Theme.GOLD, true, 4));
            thanks.addView(centered("Праздничный цветок уже в коллекции — раздел «ИЗ ИВЕНТОВ».\n"
                    + "Салют в вашу честь!", 12, Theme.MUTED, false, 8));

            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(14);
            eventBox.addView(thanks, tlp);
            return;
        }

        // карточка-окно выращивания
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.SURFACE_HI, Theme.SURFACE});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), Theme.CARD_STROKE);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));

        int stage = Math.max(0, Math.min(Event.STAGES, game.eventStage));
        // до первого полива — первый кадр (семечко), каждый полив сдвигает картинку на один
        int art = Math.max(1, Math.min(Event.STAGES, stage + 1));

        if (!game.eventSeed) {
            // Семечка ещё нет — в окне только небольшая горка земли, без растения.
            // Высота та же, что у окна растения, чтобы карточка не «прыгала».
            FrameLayout holder = new FrameLayout(this);
            ImageView mound = new ImageView(this);
            int mid = getResources().getIdentifier("event_mound", "drawable", getPackageName());
            if (mid != 0) mound.setImageResource(mid);
            mound.setScaleType(ImageView.ScaleType.FIT_CENTER);
            holder.addView(mound, new FrameLayout.LayoutParams(dp(104), dp(52),
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
            card.addView(holder, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        } else {
            ImageView plant = new ImageView(this);
            try {
                int rid = getResources().getIdentifier(
                        String.format(Locale.US, "event_stage_%02d", art),
                        "drawable", getPackageName());
                if (rid != 0) plant.setImageResource(rid);
            } catch (Exception ignored) { }
            plant.setAdjustViewBounds(false);
            // FIT_CENTER без отступов: кадр занимает всё окно, растение прижато к низу
            // (кадры обрезаны по нижней линии), поэтому семечко стоит на земле,
            // а выросший цветок упирается в верхнюю кромку окна
            plant.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(plant, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        }

        // точки стадий: 20 штук
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(8);
        for (int i = 0; i < Event.STAGES; i++) {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(i < stage ? Theme.ACCENT : Theme.STROKE);
            dot.setBackground(dg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.rightMargin = dp(4);
            dots.addView(dot, lp);
        }
        card.addView(dots, dlp);

        // без семечка подпись не нужна: то же написано на кнопке
        if (game.eventSeed) {
            TextView status = centered(stage >= Event.STAGES ? "Цветок расцвёл"
                            : "Поливов: " + stage + " из " + Event.STAGES,
                    11.5f, Theme.MUTED, false, 10);
            card.addView(status);
        }

        // кнопка: получить семечко → полить → собрать
        Button action = new Button(this);
        action.setAllCaps(false);
        action.setTextSize(14f);
        GradientDrawable abg = new GradientDrawable();
        abg.setCornerRadius(dp(24));
        action.setBackground(abg);

        if (stage >= Event.STAGES) {
            action.setText("Собрать открытку");
            abg.setColor(Theme.GOLD);
            action.setTextColor(0xFF2A1E00);
            action.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { collectEventCard(); }
            });
            action.setEnabled(true);
        } else if (!game.eventSeed) {
            action.setText("Семечко в магазине");
            abg.setColor(Theme.SURFACE);
            action.setTextColor(Theme.MUTED);
            action.setEnabled(false);
        } else {
            boolean can = game.water > 0;
            action.setText(can ? "Полить · лейка (" + game.water + ")"
                    : "Нужна вода · пройди " + Ui.steps(game.waterToNext()) + " шагов");
            abg.setColor(can ? Theme.ACCENT : Theme.SURFACE);
            action.setTextColor(can ? Theme.ON_ACCENT : Theme.MUTED);
            action.setEnabled(can);
            action.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { waterEventPlant(); }
            });
        }
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        alp.topMargin = dp(10);
        card.addView(action, alp);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        eventBox.addView(card, clp);

        TextView hint = centered("Полив тратит обычную лейку — ту же, что и для цветка в саду.\n"
                + "Выбирайте, что полить.", 10.5f, Theme.FAINT, false, 8);
        eventBox.addView(hint);
    }

    /** Золотой бейдж с датой ивента. */
    private TextView eventBadge() {
        TextView badge = new TextView(this);
        badge.setText(Event.WHEN);
        badge.setTextColor(Theme.GOLD);
        badge.setTextSize(9.5f);
        badge.setLetterSpacing(0.08f);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Theme.SURFACE_HI);
        pill.setCornerRadius(dp(20));
        pill.setStroke(dp(1), Theme.GOLD);
        badge.setBackground(pill);
        badge.setPadding(dp(16), dp(9), dp(16), dp(9));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(8);
        badge.setLayoutParams(blp);
        return badge;
    }

    /** Анонс до начала ивента (прежний текст). */
    private View eventAnnounceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.SURFACE_HI, Theme.SURFACE});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), Theme.CARD_STROKE);
        card.setBackground(bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        card.addView(centered("В честь Дня народного единства,\nкоторый отмечается 4 ноября,\n"
                + "в ШагоСаде готовится особенный сюрприз!", 13.5f, Theme.TEXT, false, 0));
        card.addView(centered("В праздничные дни в магазине появится бесплатное семечко, "
                + "из которого вырастет уникальный цветок.", 12.5f, Theme.MUTED, false, 11));
        card.addView(centered("Следите за новостями и готовьте грядки —\n"
                + "праздничное событие уже близко!", 12.5f, Theme.MUTED, false, 10));
        card.addView(centered("✦   ✦   ✦", 12, Theme.FAINT, false, 11));
        card.addView(centered("День народного единства.\nСкоро в игре!", 14,
                Theme.ACCENT_SOFT, true, 10));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        card.setLayoutParams(clp);
        return card;
    }

    /** Полить ивентовое растение: тратит одну обычную лейку. */
    private void waterEventPlant() {
        // через общий метод: тратит лейку, даёт очки и серию, как обычный полив
        if (!game.waterEvent(Steps.today())) return;
        game.save(this);
        rebuildEvents();
        refreshAll();
        if (game.eventStage >= Event.STAGES) {
            Toast.makeText(this, "Праздничный цветок расцвёл!", Toast.LENGTH_SHORT).show();
        }
    }

    /** Собрать праздничную открытку: она уходит в коллекцию, окно закрывается. */
    private void collectEventCard() {
        game.eventCard = true;
        game.eventStage = 0;
        game.eventSeed = false;
        game.save(this);
        rebuildEvents();
        refreshAll();

        // превью открытки — как в коллекции: картинка, подпись, «Сохранить в галерею»
        final GameState.Card card = GameState.holidayCard();
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = Postcard.render(MainActivity.this, card);
                runOnUiThread(new Runnable() {
                    @Override public void run() { showEventCard(card, bmp); }
                });
            }
        }).start();
    }

    /** Диалог с превью праздничной открытки: тот же вид, что у обычной карточки. */
    private void showEventCard(final GameState.Card card, final Bitmap bmp) {
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
        caption.setText("Праздничная открытка · уже в коллекции");
        caption.setTextColor(Theme.TEXT);
        caption.setTextSize(13);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(8), 0, 0);
        box.addView(caption);

        new AlertDialog.Builder(this)
                .setTitle("С праздником!")
                .setView(box)
                .setPositiveButton("Сохранить в галерею", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        final boolean ok = Postcard.saveToGallery(MainActivity.this, bmp,
                                "ШагоСад-День-народного-единства");
                        Toast.makeText(MainActivity.this,
                                ok ? "Сохранено: Pictures/Сад/ШагоСад-День-народного-единства.png"
                                        : "Не удалось сохранить",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    /** Текст по центру с одинаковым верхним отступом — чтобы блок был симметричным. */
    private TextView centered(String text, float size, int color, boolean medium, int topMargin) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        v.setLineSpacing(dp(5), 1f);
        if (medium) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMargin);
        v.setLayoutParams(lp);
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

        // два ряда по две кнопки: Магазин и Коллекция, под ними Инвентарь и Достижения
        shopButton = outlined("Магазин");
        shopButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ShopActivity.class));
            }
        });
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        bp1.rightMargin = dp(10);
        actions.addView(shopButton, bp1);

        herbButton = outlined("Коллекция");
        herbButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CollectionActivity.class));
            }
        });
        actions.addView(herbButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(actions, ap);

        LinearLayout actions2 = new LinearLayout(this);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap2.topMargin = dp(10);

        bagButton = outlined("Инвентарь");
        bagButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, InventoryActivity.class));
            }
        });
        LinearLayout.LayoutParams bp3 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        bp3.rightMargin = dp(10);
        actions2.addView(bagButton, bp3);

        achButton = outlined("Достижения");
        achButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AchievementsActivity.class));
            }
        });
        actions2.addView(achButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(actions2, ap2);

        // Статистика шагов — отдельным экраном, чтобы главная вкладка осталась лёгкой
        LinearLayout actions3 = new LinearLayout(this);
        actions3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap3 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap3.topMargin = dp(10);
        Button statBtn = outlined("Статистика шагов");
        statBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StatsActivity.class));
            }
        });
        actions3.addView(statBtn, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(actions3, ap3);

        // Медали в профиле не показываем: они живут в отдельном экране
        // «Достижения» (кнопка в ряду выше).

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
        statS = statCard(row1, "СЕРИЯ ПОЛИВОВ", false);
        root.addView(row1, r1p);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2p.topMargin = dp(8);
        statW = statCard(row2, "ПОЛИТО", true);
        statT = statCard(row2, "ШАГИ СЕГОДНЯ", false);
        root.addView(row2, r2p);

        // серия шагов: дни подряд, когда набрана дневная норма
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r3p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r3p.topMargin = dp(8);
        statStepsStreak = statCard(row3, "СЕРИЯ АКТИВНОСТИ", true);
        statNormDays = statCard(row3, "СЕРИЯ НОРМЫ", false);
        root.addView(row3, r3p);

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
        int stepsStreak = Steps.activityStreak(this);
        if (statStepsStreak != null) statStepsStreak.setText(stepsStreak + " дн.");
        if (statNormDays != null) {
            statNormDays.setText(Steps.stepsStreak(this, game.goal) + " дн.");
        }
        statW.setText(String.valueOf(game.wateredTotal));
        statT.setText(String.valueOf(totalSteps()));

        int opened = 0;
        for (Achievements.Ach a : Achievements.ALL) if (game.unlocked.contains(a.id)) opened++;
        profAch.setText("Достижения: " + opened + " из " + Achievements.ALL.size());
        if (goalButton != null) goalButton.setText("Норма шагов в день: " + fmt(game.goal));
        if (shopButton != null) shopButton.setText("Магазин");
        if (herbButton != null) herbButton.setText("Коллекция");
        if (bagButton != null) bagButton.setText("Инвентарь");

        int days = Math.max(1, game.daysInGarden());

        dBest.setText(fmt(game.bestDay));
        dAvg.setText(fmt(game.totalSteps / days));
        dTotal.setText(fmt(game.totalSteps));
        dDays.setText(String.valueOf(days));
        dFlowers.setText(String.valueOf(game.bloomed.size()));
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
            if (waterCount != null) waterCount.set(game.water);
        }
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

    // Датчик шагов говорит только когда сделан шаг: пока экран открыт, держим
    // слушатель подключённым, иначе цифра «замирает» до следующего пробуждения.
    private android.hardware.SensorManager liveSensorManager;
    private android.hardware.Sensor liveSensor;
    private long liveSyncAt = 0;

    private final android.hardware.SensorEventListener liveListener =
            new android.hardware.SensorEventListener() {
                @Override public void onSensorChanged(android.hardware.SensorEvent e) {
                    long now = System.currentTimeMillis();
                    if (now - liveSyncAt < 2000) return;   // не чаще раза в 2 секунды
                    liveSyncAt = now;
                    runSync();
                }
                @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) { }
            };

    /** Подписаться на живой счётчик шагов, пока экран приложения открыт. */
    private void startLiveSteps() {
        try {
            if (liveSensorManager == null) {
                liveSensorManager = (android.hardware.SensorManager)
                        getSystemService(SENSOR_SERVICE);
            }
            if (liveSensorManager == null || liveSensor != null) return;
            liveSensor = liveSensorManager.getDefaultSensor(
                    android.hardware.Sensor.TYPE_STEP_COUNTER);
            if (liveSensor == null) return;
            liveSensorManager.registerListener(liveListener, liveSensor,
                    android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception ignored) { }
    }

    private void stopLiveSteps() {
        try {
            if (liveSensorManager != null && liveSensor != null) {
                liveSensorManager.unregisterListener(liveListener);
            }
        } catch (Exception ignored) { }
        liveSensor = null;
    }


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
        // ключ сменили в 10.6: появилась геолокация для погоды, и выданные ранее
        // разрешения не должны «заглушать» новый запрос
        if (p.getBoolean("asked_perms2", false)) return;
        p.edit().putBoolean("asked_perms2", true).apply();
        askRuntimePermissions();
    }

    private void askRuntimePermissions() {
        List<String> need = new ArrayList<>();
        if (!hasStepPermission()) need.add("android.permission.ACTIVITY_RECOGNITION");
        if (checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.ACCESS_COARSE_LOCATION");
        }
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

    private boolean hasStepPermission() {
        if (Build.VERSION.SDK_INT < 29) return true;
        return checkSelfPermission("android.permission.ACTIVITY_RECOGNITION")
                == PackageManager.PERMISSION_GRANTED;
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

