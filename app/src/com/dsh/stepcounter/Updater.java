package com.dsh.stepcounter;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Обновление приложения напрямую с GitHub.
 *
 * Данные о свежей версии лежат в `version.json` в репозитории:
 *   { "versionName": "11.56", "versionCode": 240, "apk": "https://…/ShagoSad.apk" }
 *
 * Скачанный APK кладём в кэш и отдаём системному установщику через собственный
 * провайдер {@link ApkProvider}: file://-ссылки Android с 7-й версии не принимает.
 */
public final class Updater {

    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/muzikamv-dot/shagosad/main/version.json";
    private static final String API_URL =
            "https://api.github.com/repos/muzikamv-dot/shagosad/releases/latest";
    private static final String APK_URL =
            "https://github.com/muzikamv-dot/shagosad/releases/latest/download/ShagoSad.apk";

    /** Что удалось узнать о новой версии. */
    public static class Info {
        public final String versionName;
        public final int versionCode;
        public final String apkUrl;

        Info(String versionName, int versionCode, String apkUrl) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.apkUrl = apkUrl;
        }
    }

    /** Результат проверки для интерфейса. */
    public interface Callback {
        /** info == null — не удалось проверить (нет сети и т. п.). */
        void done(Info info, boolean updateAvailable, String error);
    }

    private Updater() { }

    /** Текущий versionCode установленного приложения. */
    public static int installedCode(Activity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Текущий versionName установленного приложения. */
    public static String installedName(Activity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Проверяет последнюю версию в фоне и отвечает в главном потоке. */
    public static void check(final Activity activity, final Callback cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                Info info = null;
                String error = null;
                // 1) официальный API релизов: без кэша CDN, всегда свежие данные
                try {
                    String body = read(API_URL);
                    JSONObject o = new JSONObject(body);
                    String tag = o.optString("tag_name", "").replace("v", "");
                    String apk = "";
                    org.json.JSONArray assets = o.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject a = assets.getJSONObject(i);
                            String nm = a.optString("name", "");
                            if (nm.endsWith(".apk")) {
                                apk = a.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }
                    if (apk.isEmpty()) apk = APK_URL;
                    if (!tag.isEmpty()) {
                        info = new Info(tag, codeFromName(tag), apk);
                    }
                } catch (Exception e) {
                    error = e.getClass().getSimpleName();
                }
                // 2) запасной путь: version.json в репозитории
                if (info == null) {
                    try {
                        String body = read(VERSION_URL + "?t=" + System.currentTimeMillis());
                        JSONObject o = new JSONObject(body);
                        info = new Info(o.optString("versionName", "?"),
                                o.optInt("versionCode", 0),
                                o.optString("apk", APK_URL));
                    } catch (Exception e) {
                        if (error == null) error = e.getClass().getSimpleName();
                    }
                }
                final Info fi = info;
                final String fe = error;
                final int installed = Math.max(installedCode(activity),
                        codeFromName(installedName(activity)));
                final boolean available = info != null && info.versionCode > installed;
                ui.post(new Runnable() {
                    @Override public void run() { cb.done(fi, available, fe); }
                });
            }
        }).start();
    }

    /** «11.58» → 11 * 1000 + 58: сравниваем версии без versionCode. */
    static int codeFromName(String name) {
        try {
            String[] parts = name.split("[.]");
            int major = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
            int minor = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
            return major * 1000 + minor;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String read(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Cache-Control", "no-cache, no-store");
        try {
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Скачивает APK и открывает системный установщик. */
    public static void downloadAndInstall(final Activity activity, final Info info,
                                          final Runnable onError) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                File apk = new File(activity.getCacheDir(), "update.apk");
                boolean ok = false;
                String error = "?";
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(info.apkUrl).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(true);
                    InputStream in = c.getInputStream();
                    FileOutputStream out = new FileOutputStream(apk);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                    c.disconnect();
                    ok = apk.length() > 100000;
                    error = "размер " + apk.length();
                } catch (Exception e) {
                    error = e.getClass().getSimpleName();
                }
                final boolean fok = ok;
                final String ferr = error;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!fok) {
                            if (onError != null) onError.run();
                            return;
                        }
                        install(activity, apk);
                    }
                });
            }
        }).start();
    }

    private static void install(Activity activity, File apk) {
        // Android 8+: нужно разрешение «Установка неизвестных приложений»
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                i.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
            } catch (Exception ignored) { }
            return;
        }
        Uri uri = ApkProvider.uriFor(apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }

    /** Установлено ли приложение из магазина (тогда обновления приходят оттуда). */
    public static boolean fromStore(Activity a) {
        try {
            String installer = a.getPackageManager()
                    .getInstallSourceInfo(a.getPackageName()).getInstallingPackageName();
            return installer != null && (installer.contains("rustore") || installer.contains("vending"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Окно при запуске: «Есть обновление» с кнопками «Позже» и «Обновить». */
    public static void promptIfUpdate(final Activity activity) {
        promptAttempt(activity, 0);
    }

    /**
     * Проверка с повторами: на мобильной сети первый запрос иногда не проходит,
     * и раньше окно просто не показывалось. Теперь до трёх попыток.
     */
    private static void promptAttempt(final Activity activity, final int attempt) {
        check(activity, new Callback() {
            @Override public void done(Info info, boolean available, String error) {
                android.util.Log.i("Updater", "попытка " + (attempt + 1) + ": info="
                        + (info == null ? "null" : info.versionName + "/" + info.versionCode)
                        + " available=" + available + " error=" + error);
                if (info == null) {
                    if (attempt < 2 && !activity.isFinishing()) {
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override public void run() { promptAttempt(activity, attempt + 1); }
                        }, 3000);
                    }
                    return;
                }
                if (!available || activity.isFinishing()) return;
                try {
                    showDialog(activity, info);
                } catch (Throwable th) {
                    android.util.Log.e("Updater", "окно не показалось", th);
                }
            }
        });
    }

    private static void showDialog(final Activity a, final Info info) {
        int bg = Theme.SURFACE;
        int stroke = Theme.STROKE;
        int text = Theme.TEXT;
        int muted = Theme.MUTED;
        int accent = Theme.ACCENT;
        int onAccent = Theme.ON_ACCENT;

        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(pad * 1.3f);
        cardBg.setColor(bg);
        cardBg.setStroke(1, stroke);
        card.setBackground(cardBg);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(a);
        title.setText("Есть обновление");
        title.setTextColor(text);
        title.setTextSize(19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = new TextView(a);
        sub.setText("Сейчас стоит " + installedName(a) + ", доступна " + info.versionName);
        sub.setTextColor(muted);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, pad / 3, 0, pad);
        card.addView(sub);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView later = new TextView(a);
        later.setText("Позже");
        later.setTextSize(15);
        later.setTextColor(text);
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, pad * 3 / 4, 0, pad * 3 / 4);
        GradientDrawable laterBg = new GradientDrawable();
        laterBg.setCornerRadius(pad);
        laterBg.setColor(0x00000000);
        laterBg.setStroke(1, stroke);
        later.setBackground(laterBg);

        TextView now = new TextView(a);
        now.setText("Обновить");
        now.setTextSize(15);
        now.setTextColor(onAccent);
        now.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        now.setGravity(Gravity.CENTER);
        now.setPadding(0, pad * 3 / 4, 0, pad * 3 / 4);
        GradientDrawable nowBg = new GradientDrawable();
        nowBg.setCornerRadius(pad);
        nowBg.setColor(accent);
        now.setBackground(nowBg);

        // кнопки одинаковой ширины — окно выглядит симметрично
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = pad / 3;
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = pad / 3;
        row.addView(later, left);
        row.addView(now, right);
        card.addView(row);

        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(card);
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f);
            dialog.getWindow().setAttributes(lp);
        }

        later.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        now.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                downloadAndInstall(a, info, new Runnable() {
                    @Override public void run() {
                        android.widget.Toast.makeText(a, "Не удалось скачать обновление",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        dialog.show();
    }
    static boolean hasInstallPermission(Activity a) {
        if (Build.VERSION.SDK_INT < 26) return true;
        PackageManager pm = a.getPackageManager();
        return pm.canRequestPackageInstalls();
    }
}
