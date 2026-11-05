package com.dsh.stepcounter;

import android.app.Activity;
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
                try {
                    String body = read(VERSION_URL);
                    JSONObject o = new JSONObject(body);
                    info = new Info(o.optString("versionName", "?"),
                            o.optInt("versionCode", 0),
                            o.optString("apk", ""));
                } catch (Exception e) {
                    error = e.getClass().getSimpleName();
                }
                final Info fi = info;
                final String fe = error;
                final boolean available = info != null && info.versionCode > installedCode(activity);
                ui.post(new Runnable() {
                    @Override public void run() { cb.done(fi, available, fe); }
                });
            }
        }).start();
    }

    private static String read(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
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

    static boolean hasInstallPermission(Activity a) {
        if (Build.VERSION.SDK_INT < 26) return true;
        PackageManager pm = a.getPackageManager();
        return pm.canRequestPackageInstalls();
    }
}
