package com.dsh.stepcounter;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/** Настройки: норма, разрешения, служба, тест шагов, диагностика, сброс. */
public class SettingsActivity extends Activity {

    private static final int REQ_PERMS = 300;

    private GameState game;
    private TextView diagView;
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

        ScrollView scroll = new ScrollView(this);
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
        final LinearLayout testBox = new LinearLayout(this);
        testBox.setOrientation(LinearLayout.VERTICAL);
        testBox.setVisibility(View.GONE);
        testBox.addView(section("ТЕСТ И ДИАГНОСТИКА"));

        Button manual = row("Начислить шаги вручную");
        manual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askManual(); }
        });
        testBox.addView(manual);

        Button check = row("Проверить датчик шагов");
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runDiag(); }
        });
        testBox.addView(check);

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
        diagView.setPadding(dp(4), dp(4), dp(4), dp(8));
        testBox.addView(diagView);
        box.addView(testBox);

        // О ПРИЛОЖЕНИИ
        box.addView(section("О ПРИЛОЖЕНИИ"));

        Button changelog = row("История изменений");
        changelog.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ChangelogActivity.class));
            }
        });
        box.addView(changelog);

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
