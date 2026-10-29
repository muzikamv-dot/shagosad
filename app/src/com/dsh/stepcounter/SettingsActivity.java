package com.dsh.stepcounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/** Настройки: норма, разрешения, служба, тест шагов, диагностика, сброс. */
public class SettingsActivity extends Activity {

    private static final int REQ_PERMS = 300;
    /** Почта разработчика для обратной связи. */
    static final String DEV_EMAIL = "muzikamv@gmail.com";

    private GameState game;
    private TextView diagView;
    private Button weatherButton;
    private LinearLayout testBox;
    private ScrollView scroll;
    private void chooseTheme() {
        new AlertDialog.Builder(this)
                .setTitle("Тема оформления")
                .setItems(Theme.NAMES, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == Theme.mode()) return;
                        Theme.save(SettingsActivity.this, which);
                        // пересобираем интерфейс с нуля: цвета вкомпилированы в вью
                        // при их создании, на ходу их не поменять
                        Intent i = new Intent(SettingsActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------------- резервная копия ----------------

    private static final int REQ_SAVE = 4101;
    private static final int REQ_LOAD = 4102;

    /** Сохранение через системный выбор файла: разрешений не требует, работает с 8 по 16. */
    private void backupSave() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "shagosad-" + Steps.today() + ".json");
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            Toast.makeText(this, "На телефоне нет приложения для сохранения файла",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void backupLoad() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_LOAD);
        } catch (Exception e) {
            Toast.makeText(this, "На телефоне нет приложения для выбора файла",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (req == REQ_SAVE) {
            OutputStream out = null;
            try {
                out = getContentResolver().openOutputStream(uri);
                out.write(Backup.build(this).getBytes("UTF-8"));
                Toast.makeText(this, "Копия сохранена", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось сохранить: " + e.getClass().getSimpleName(),
                        Toast.LENGTH_LONG).show();
            } finally {
                try { if (out != null) out.close(); } catch (Exception ignored) { }
            }
        } else if (req == REQ_LOAD) {
            String text = null;
            BufferedReader r = null;
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                text = sb.toString();
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось прочитать файл", Toast.LENGTH_LONG).show();
            } finally {
                try { if (r != null) r.close(); } catch (Exception ignored) { }
            }
            if (text != null) confirmRestore(text);
        }
    }

    private void confirmRestore(final String text) {
        new AlertDialog.Builder(this)
                .setTitle("Восстановить прогресс?")
                .setMessage("Текущий сад и журнал шагов будут заменены копией из файла.")
                .setPositiveButton("Восстановить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int n = Backup.restore(SettingsActivity.this, text);
                        if (n < 0) {
                            Toast.makeText(SettingsActivity.this,
                                    "Файл не похож на копию ШагоСада", Toast.LENGTH_LONG).show();
                            return;
                        }
                        // перезапуск: в памяти остались данные до восстановления
                        Intent i = new Intent(SettingsActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private Button goalButton;
    private Button themeButton;
    private Button serviceButton;
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

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Theme.FAINT);
        t.setTextSize(Ui.T_CAPS);
        t.setLetterSpacing(0.10f);
        t.setPadding(0, dp(20), 0, dp(8));
        return t;
    }

    private Button row(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14f);
        b.setTypeface(Ui.medium());
        b.setTextColor(Theme.TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.SURFACE);
        d.setCornerRadius(dp(22));
        d.setStroke(dp(1), Theme.STROKE);
        b.setBackground(d);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.bottomMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private View build() {
        final int pad = dp(18);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM}));
        root.setPadding(pad, 0, pad, dp(20));
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
        head.setPadding(0, dp(8), 0, dp(4));

        TextView title = new TextView(this);
        title.setText("НАСТРОЙКИ");
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

        scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        // ИГРА
        box.addView(section("ИГРА"));
        goalButton = row("Норма шагов в день: " + game.goal);
        goalButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editGoal(); }
        });
        box.addView(goalButton);

        themeButton = row("Тема: " + Theme.name());
        themeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseTheme(); }
        });
        box.addView(themeButton);

        // Погода в сцене сада. В «авто» — настоящая из open-meteo по координатам,
        // ручные значения её перекрывают.
        weatherButton = row(weatherLabel());
        weatherButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleWeather(); }
        });
        box.addView(weatherButton);

        Button reset = row("Сбросить прогресс");
        reset.setTextColor(0xFFFF9A8A);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmReset(); }
        });
        box.addView(reset);

        // РАЗРЕШЕНИЯ
        box.addView(section("РАЗРЕШЕНИЯ"));
        Button perm = row("Доступ к шагам и уведомления");
        perm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askPermissions(); }
        });
        box.addView(perm);

        Button battery = row("Работа в фоне (исключение батареи)");
        battery.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askBattery(); }
        });
        box.addView(battery);

        // СЛУЖБА
        box.addView(section("ФОН"));
        boolean serviceOn = getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                .getBoolean(Steps.KEY_SERVICE, false);
        serviceButton = row("Фоновая служба: " + (serviceOn ? "включена" : "выключена"));
        serviceButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleService(); }
        });
        box.addView(serviceButton);

        // ДАННЫЕ
        box.addView(section("ДАННЫЕ"));
        Button saveCopy = row("Сохранить копию прогресса");
        saveCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { backupSave(); }
        });
        box.addView(saveCopy);

        Button loadCopy = row("Восстановить из копии");
        loadCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { backupLoad(); }
        });
        box.addView(loadCopy);

        // ТЕСТ (скрыт, открывается 7 тапами по версии)
        final LinearLayout testBoxRef = new LinearLayout(this);
        testBox = testBoxRef;
        testBox.setOrientation(LinearLayout.VERTICAL);
        testBox.setVisibility(View.GONE);
        testBox.addView(section("ТЕСТ И ДИАГНОСТИКА"));

        Button manual = row("Начислить шаги вручную");
        manual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askManual(); }
        });
        testBox.addView(manual);

        // состояние датчика: значение счётчика, сегодняшние шаги, батчинг, разрешение
        Button check = row("Состояние датчика шагов");
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runDiag(); }
        });
        testBox.addView(check);

        // сам журнал датчика: что реально накопилось в journal.txt
        Button journal = row("Журнал шагов (содержимое)");
        journal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showJournal(); }
        });
        testBox.addView(journal);

        Button export = row("Выгрузить журнал шагов в файл");
        export.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportJournal(); }
        });
        testBox.addView(export);

        // проверка сцены: посмотреть утро/день/вечер/ночь, не дожидаясь часов
        final Button hourTest = row(hourTestLabel());
        hourTest.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
                float cur = p.getFloat(DayPhase.PREF_HOUR, -1f);
                float next;
                if (cur < 0f) next = 7f;
                else if (cur < 10f) next = 13f;
                else if (cur < 16f) next = 19f;
                else if (cur < 21f) next = 23f;
                else next = -1f;
                p.edit().putFloat(DayPhase.PREF_HOUR, next).apply();
                hourTest.setText(hourTestLabel());
            }
        });
        testBox.addView(hourTest);

        // состояние погоды: реальные данные, кэш и ручное обновление
        final Button weatherStatus = row(weatherStatusLabel());
        weatherStatus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                weatherStatus.setText("Обновляю погоду…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final boolean ok = Weather.refreshNow(SettingsActivity.this);
                        ui.post(new Runnable() {
                            @Override public void run() {
                                weatherStatus.setText(weatherStatusLabel());
                                weatherButton.setText(weatherLabel());
                                Toast.makeText(SettingsActivity.this,
                                        ok ? "Погода обновлена"
                                                : "Не удалось: " + Weather.note(SettingsActivity.this),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }, "weather-manual").start();
            }
        });
        testBox.addView(weatherStatus);

        // посмотреть награждение ещё раз, не зарабатывая медаль
        Button achAgain = row("Показать награды заново (тест)");
        achAgain.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                        .edit().remove(Achievements.PREF_SHOWN).apply();
                Toast.makeText(SettingsActivity.this,
                        "Награды покажутся при следующем запуске", Toast.LENGTH_LONG).show();
            }
        });
        testBox.addView(achAgain);

        // Обучение показывается один раз; чтобы посмотреть его снова или проверить
        // галочку «больше не показывать», сбрасываем флаг и открываем экран
        Button again = row("Показать обучение заново");
        again.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences(Steps.PREFS, MODE_PRIVATE).edit()
                        .putBoolean("onboarding_off", false).apply();
                startActivity(new Intent(SettingsActivity.this, OnboardingActivity.class));
            }
        });
        testBox.addView(again);

        // для проверки сорняков и увядания без ожидания дней
        Button skipDay = row("Смоделировать пропуск дня (тест)");
        skipDay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                game.simulateMiss();
                game.save(SettingsActivity.this);
                String msg = game.withered ? "Цветок засох" : "Сорняков: " + game.weeds;
                Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
        testBox.addView(skipDay);

        // проверка магазина и инструментов без недели ходьбы
        Button addPoints = row("Начислить 1000 очков (тест)");
        addPoints.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                game.earn(1000);
                game.save(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this,
                        "Очков теперь: " + game.points, Toast.LENGTH_SHORT).show();
            }
        });
        testBox.addView(addPoints);

        // для проверки самого сброса: подтверждение словом СБРОС мешает
        // автоматическим прогонам и клавиатурам без кириллицы
        Button hardReset = row("Сброс прогресса (тест, без подтверждения)");
        hardReset.setTextColor(0xFFFF9A8A);
        hardReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doReset(); }
        });
        testBox.addView(hardReset);

        diagView = new TextView(this);
        diagView.setText("Датчик ещё не проверялся");
        diagView.setTextColor(Theme.FAINT);
        diagView.setTextSize(10);
        diagView.setLineSpacing(dp(3), 1f);
        diagView.setTextIsSelectable(true);
        diagView.setPadding(dp(4), dp(4), dp(4), dp(8));
        testBox.addView(diagView);

        // О ПРИЛОЖЕНИИ
        box.addView(section("О ПРИЛОЖЕНИИ"));

        // Открыть меню разработчика обычной кнопкой: семь тапов по версии остались,
        // но это уже не единственный вход — владелец попросил нормальную кнопку.
        final Button devMenu = row("Меню разработчика: открыть");
        // Меню разработчика показываем только для сборок «не из магазина»:
        // на телефоне владельца (adb) оно есть всегда, а в RuStore у обычных
        // пользователей и модерации кнопки не будет.
        if (installedFromStore()) devMenu.setVisibility(View.GONE);
        devMenu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean open = testBox.getVisibility() != View.VISIBLE;
                testBox.setVisibility(open ? View.VISIBLE : View.GONE);
                devMenu.setText(open ? "Меню разработчика: скрыть"
                        : "Меню разработчика: открыть");
                if (open) {
                    scroll.post(new Runnable() {
                        @Override public void run() {
                            scroll.smoothScrollTo(0, testBox.getTop());
                        }
                    });
                }
            }
        });
        box.addView(devMenu);

        Button changelog = row("История изменений");
        changelog.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ChangelogActivity.class));
            }
        });
        box.addView(changelog);

        // Связь с разработчиком: открываем почтовое приложение с готовым письмом,
        // а если его нет — показываем адрес и копируем в буфер.
        Button contact = row("Связаться с разработчиком");
        contact.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { contactDeveloper(); }
        });
        box.addView(contact);

        TextView version = new TextView(this);
        version.setText("ШагоСад · версия " + versionName() + " (" + versionCode() + ")");
        version.setTextColor(Theme.FAINT);
        version.setTextSize(10);
        version.setPadding(0, dp(10), 0, dp(10));
        version.setOnClickListener(new View.OnClickListener() {
            private int taps = 0;
            @Override public void onClick(View v) {
                taps++;
                if (taps >= 7) {
                    testBox.setVisibility(View.VISIBLE);
                    Toast.makeText(SettingsActivity.this,
                            "Тестовые инструменты открыты", Toast.LENGTH_SHORT).show();
                    v.setEnabled(false);
                } else if (taps >= 4) {
                    Toast.makeText(SettingsActivity.this,
                            "Ещё " + (7 - taps) + " до тестовых инструментов",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(version);
        box.addView(testBox);

        scroll.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    // ---------------- действия ----------------

    private void editGoal() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(game.goal));
        input.setTextColor(Theme.TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Норма шагов в день")
                .setView(input)
                .setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            int g = Integer.parseInt(input.getText().toString().trim());
                            if (g < 1000 || g > 100000) return;
                            game.setGoal(g);
                            game.save(SettingsActivity.this);
                            getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                                    .edit().putInt("goal", g).apply();
                            goalButton.setText("Норма шагов в день: " + g);
                        } catch (Exception ignored) { }
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Сбросить прогресс?")
                .setMessage("Будут стёрты:\n\n"
                        + "• выращенный цветок и его стадия\n"
                        + "• вода и очки\n"
                        + "• серия дней\n"
                        + "• вся коллекция и букет\n"
                        + "• все собранные открытки\n"
                        + "• все достижения и медали\n"
                        + "• купленные рамки\n"
                        + "• имя и аватар (фото)\n"
                        + "• историю шагов за неделю\n"
                        + "• ручные шаги (тестовые)\n\n"
                        + "Останутся: норма шагов и настройки (тема, служба, напоминание).\n\n"
                        + "Действие необратимо.")
                .setPositiveButton("Продолжить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { secondConfirm(); }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void secondConfirm() {
        final EditText input = new EditText(this);
        input.setHint("напишите СБРОС");
        input.setTextColor(Theme.TEXT);
        input.setHintTextColor(Theme.FAINT);
        input.setGravity(Gravity.CENTER);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Последнее подтверждение")
                .setMessage("Чтобы стереть прогресс, введите слово СБРОС заглавными буквами.")
                .setView(input)
                .setPositiveButton("Стереть", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                final Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                ok.setEnabled(false);
                ok.setTextColor(Theme.MUTED);
                input.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                    @Override public void afterTextChanged(android.text.Editable e) {
                        boolean match = e.toString().trim().equals("СБРОС");
                        ok.setEnabled(match);
                        ok.setTextColor(match ? Color.parseColor("#FF8A7A")
                                : Theme.MUTED);
                    }
                });
                ok.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        doReset();
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }

    private void doReset() {
        game.resetProgress();
        game.ensureIdentity();   // новый ник «Садовник #NNNN» и дата старта — сегодня
        game.gameVersion = GameState.GAME_VERSION;
        game.save(SettingsActivity.this);
        // фото аватара тоже убираем: после полного сброса профиль должен быть чистым
        try {
            java.io.File av = new java.io.File(getFilesDir(), "avatar.png");
            if (av.exists()) av.delete();
        } catch (Exception ignored) { }
        // журнал шагов тоже стираем: иначе недельный график остаётся,
        // хотя «Шагов всего 0» — сброс выглядел недоделанным
        Steps.clearJournal(SettingsActivity.this);
        Steps.prefs(SettingsActivity.this).edit()
                .putInt("manual_steps", 0)
                .putString("manual_day", Steps.today())
                .apply();
        StepWidget.refresh(SettingsActivity.this);
        Toast.makeText(SettingsActivity.this, "Прогресс сброшен", Toast.LENGTH_SHORT).show();
    }

    private void askPermissions() {
        java.util.List<String> need = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission("android.permission.ACTIVITY_RECOGNITION")
                   != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.ACTIVITY_RECOGNITION");
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (need.isEmpty()) {
            Toast.makeText(this, "Все разрешения уже выданы", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    private void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show();
        }
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
        serviceButton.setText("Фоновая служба: " + (next ? "включена" : "выключена"));
    }

    private void askManual() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("например 10000");
        input.setTextColor(Theme.TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Начислить шаги")
                .setMessage("Для теста: шаги добавятся к сегодняшним и дадут воду.")
                .setView(input)
                .setPositiveButton("Добавить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            int add = Integer.parseInt(input.getText().toString().trim());
                            SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
                            p.edit().putInt("manual_steps", p.getInt("manual_steps", 0) + add).apply();
                            Toast.makeText(SettingsActivity.this, "Добавлено", Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) { }
                    }
                })
                .setNeutralButton("Сбросить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                                .edit().putInt("manual_steps", 0).apply();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /** Погода в саду: авто → ясно → облачно → дождь → снег → авто. */
    private void cycleWeather() {
        SharedPreferences p = getSharedPreferences(Steps.PREFS, MODE_PRIVATE);
        int next = DayPhase.nextWeather(p.getInt(DayPhase.PREF_WEATHER, DayPhase.WEATHER_AUTO));
        p.edit().putInt(DayPhase.PREF_WEATHER, next).apply();
        weatherButton.setText(weatherLabel());
        if (next == DayPhase.WEATHER_AUTO) Weather.refreshAsync(this);
        Toast.makeText(this, "Погода в саду: " + DayPhase.weatherName(next),
                Toast.LENGTH_SHORT).show();
    }

    private String weatherLabel() {
        int pref = DayPhase.weatherPref(this);
        if (pref != DayPhase.WEATHER_AUTO) {
            return "Погода в саду: " + DayPhase.weatherName(pref);
        }
        if (Weather.hasData(this)) {
            return "Погода в саду: реальная — " + Weather.describe(Weather.code(this));
        }
        return "Погода в саду: авто (нет данных)";
    }

    private String weatherStatusLabel() {
        StringBuilder sb = new StringBuilder("Погода: ");
        if (!Weather.hasData(this)) {
            sb.append("нет данных");
            String n = Weather.note(this);
            if (n != null && n.length() > 0) sb.append(" · ").append(n);
            return sb.toString();
        }
        sb.append(Weather.describe(Weather.code(this)));
        float t = Weather.temp(this);
        if (!Float.isNaN(t)) sb.append(String.format(java.util.Locale.US, " · %.0f°", t));
        int cloud = Weather.cloud(this);
        if (cloud >= 0) sb.append(" · облачность ").append(cloud).append('%');
        long age = (System.currentTimeMillis() - Weather.updatedAt(this)) / 60000L;
        sb.append(" · ").append(age).append(" мин назад");
        return sb.toString();
    }

    private String hourTestLabel() {
        float h = getSharedPreferences(Steps.PREFS, MODE_PRIVATE)
                .getFloat(DayPhase.PREF_HOUR, -1f);
        if (h < 0f) return "Время суток в саду (тест): авто";
        return "Время суток в саду (тест): " + Math.round(h) + ":00";
    }

    /** Почта разработчика: письмо с уже заполненной темой, иначе — адрес и копирование. */
    private void contactDeveloper() {
        String subject = "ШагоСад " + versionName() + " (" + versionCode() + ")";
        Intent mail = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + DEV_EMAIL + "?subject=" + Uri.encode(subject)));
        try {
            startActivity(mail);
            return;
        } catch (Exception ignored) { }

        new AlertDialog.Builder(this)
                .setTitle("Связаться с разработчиком")
                .setMessage(DEV_EMAIL + "\n\nПочтового приложения на телефоне нет — "
                        + "адрес можно скопировать и написать с другого устройства.")
                .setPositiveButton("Копировать", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        android.content.ClipboardManager cb = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        cb.setPrimaryClip(ClipData.newPlainText("email", DEV_EMAIL));
                        Toast.makeText(SettingsActivity.this,
                                "Адрес скопирован", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    /** Выгрузить журнал шагов в файл, чтобы прислать разработчику. */
    private void exportJournal() {
        try {
            java.io.File src = new java.io.File(getFilesDir(), "journal.txt");
            java.io.File dir = getExternalFilesDir(null);
            java.io.File out = new java.io.File(dir, "journal-" + Steps.today() + ".txt");
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            java.io.FileOutputStream os = new java.io.FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            os.close();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Журнал выгружен")
                    .setMessage("Файл: " + out.getAbsolutePath()
                            + "\\n\\nЕго можно отправить с телефона (файловый менеджер, "
                            + "«Поделиться») или забрать по USB.")
                    .setPositiveButton("Закрыть", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось выгрузить: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Установлено ли приложение из магазина (RuStore, Google Play и подобные).
     * При сайдлоаде через adb установщика нет — значит это сборка для разработки,
     * и меню разработчика можно показывать.
     */
    private boolean installedFromStore() {
        if (android.os.Build.VERSION.SDK_INT < 30) return false;
        try {
            String installer = getPackageManager()
                    .getInstallSourceInfo(getPackageName())
                    .getInstallingPackageName();
            if (installer == null) return false;
            String p = installer.toLowerCase();
            return p.contains("rustore") || p.contains("store") || p.contains("vending")
                    || p.contains("huawei") || p.contains("samsung");
        } catch (Exception e) {
            return false;
        }
    }
    private void runDiag() {
        diagView.setText("Опрашиваю датчик…");
        new Thread(new Runnable() {
            @Override public void run() {
                final SensorSync.Result r = SensorSync.sync(SettingsActivity.this);
                ui.post(new Runnable() {
                    @Override public void run() { diagView.setText(r.diag); }
                });
            }
        }).start();
    }

    /**
     * Показать содержимое журнала датчика (journal.txt): сводку и последние строки.
     * Файл лежит в приватной папке приложения, снаружи его не прочитать —
     * поэтому единственный способ увидеть его на телефоне без отладки это окно.
     */
    private void showJournal() {
        File f = new File(getFilesDir(), "journal.txt");
        String all = readText(f);
        String[] lines = all.length() == 0 ? new String[0] : all.split("\n");

        StringBuilder body = new StringBuilder();
        body.append("Файл: ").append(f.getAbsolutePath()).append('\n');
        body.append("Размер: ").append(f.exists() ? f.length() + " Б" : "файла нет").append('\n');
        body.append("Строк: ").append(lines.length).append('\n');
        if (lines.length > 0) {
            body.append("Первая: ").append(lines[0]).append('\n');
            body.append("Последняя: ").append(lines[lines.length - 1]).append('\n');
        }
        body.append("Формат: wallMillis|elapsedNanos|raw\n\n");

        int show = Math.min(lines.length, 400);
        if (lines.length > show) {
            body.append("… ниже последние ").append(show).append(" строк из ").append(lines.length).append('\n');
        }
        for (int i = lines.length - show; i < lines.length; i++) {
            body.append(lines[i]).append('\n');
        }
        if (lines.length == 0) body.append("Журнал пуст: замеров ещё не было.\n");

        TextView tv = new TextView(this);
        tv.setText(body.toString());
        tv.setTextSize(9);
        tv.setTextColor(Theme.TEXT);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int pad = dp(14);
        tv.setPadding(pad, pad, pad, pad);

        ScrollView sc = new ScrollView(this);
        sc.addView(tv);

        final String copySource = all;
        new AlertDialog.Builder(this)
                .setTitle("Журнал шагов")
                .setView(sc)
                .setPositiveButton("Копировать", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        android.content.ClipboardManager cb = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        cb.setPrimaryClip(ClipData.newPlainText("journal.txt", copySource));
                        Toast.makeText(SettingsActivity.this,
                                "Журнал скопирован в буфер", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    /** Прочитать текстовый файл целиком; отсутствие файла — пустая строка. */
    private String readText(File f) {
        if (f == null || !f.exists()) return "";
        StringBuilder sb = new StringBuilder();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (Exception ignored) {
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
        return sb.toString();
    }

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) { return "?"; }
    }

    private int versionCode() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) { return 0; }
    }
}
