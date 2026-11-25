package com.dsh.stepcounter;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Политика конфиденциальности — простым языком, без юридического тумана.
 * Лежит прямо в приложении: работает без сети и всегда под рукой.
 */
public class PrivacyActivity extends android.app.Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(build());
    }

    private int dp(int v) { return Ui.dp(this, v); }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.BG_TOP, Theme.BG_BOTTOM, Theme.BG_BOTTOM}));
        final int pad = dp(18);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(pad, in.getSystemWindowInsetTop() + dp(10), pad, dp(10));
                return in;
            }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("КОНФИДЕНЦИАЛЬНОСТЬ");
        title.setTextColor(Theme.ACCENT_SOFT);
        title.setTextSize(Ui.T_TITLE);
        title.setLetterSpacing(0.10f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = Ui.secondary(this, "закрыть");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(close, new LinearLayout.LayoutParams(dp(110), dp(40)));
        root.addView(head);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, dp(24));
        scroll.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        add(box, "Что приложение берёт с телефона");
        add(box, "• Шаги — с датчика шагов телефона (разрешение «Доступ к шагам»). "
                + "Если разрешения нет, приложение ими не пользуется.");
        add(box, "• Своё фото для аватара — только если ты сам его выбрал. Фото хранится "
                + "в памяти приложения на телефоне.");
        add(box, "• Погоду в саду — по грубым координатам (район города), чтобы показать небо. "
                + "Точное местоположение не запрашивается.");

        add(box, "Что остаётся на телефоне");
        add(box, "Прогресс игры, коллекция цветов, магазин, настройки и фото аватара — всё это "
                + "лежит в памяти приложения. Без твоего действия мы ничего не отправляем.");

        add(box, "Что уходит в интернет");
        add(box, "• Рейтинги и друзья. Если ты открываешь эти экраны, на сервер отправляются: код "
                + "твоего сада, ник, число цветов и шагов, номер аватара и номер рамки, а также "
                + "маленькая миниатюра твоего фото (96×96). Сервер наш, живёт в Yandex Cloud "
                + "в России. Миниатюру фото видят только те, кому ты дал код сада (друзья).");
        add(box, "• Погода. Грубые координаты уходят в сервисы прогноза погоды"
                + " (api.met.no, open-meteo.com, wttr.in), больше ничего из данных туда не попадает.");
        add(box, "• Хеш устройства — чтобы после переустановки вернуть тот же сад. "
                + "Сам идентификатор телефона на сервер не отправляется.");

        add(box, "Чего мы не делаем");
        add(box, "Нет рекламы, нет встроенных счётчиков и трекеров, нет аналитики поведения, "
                + "нет продажи данных. Приложение не читает сообщения, контакты, календарь, "
                + "звонки и микрофон.");

        add(box, "Согласие и правовые основания");
        add(box, "Обработка данных идёт на основании твоего согласия (статья 6 и 9 Федерального "
                + "закона № 152-ФЗ «О персональных данных»). Согласие даётся на входе в игру — "
                + "кнопкой «Согласен на обработку данных»; без него игра не запускается, потому что рейтинги "
                + "и друзья — часть игры.");
        add(box, "Ник в рейтинге виден другим игрокам, и миниатюра фото видна друзьям — это "
                + "распространение данных в объёме, который ты выбрал сам, давая согласие.");
        add(box, "Отозвать согласие можно в любой момент: «Настройки» → «Отозвать согласие на "
                + "обработку данных». После отзыва приложение закроется и при следующем запуске "
                + "спросит согласие снова, а сетевые экраны работать не будут.");
        add(box, "Оператор обработки: разработчик приложения ШагоСад. Связаться можно письмом на "
                + "muzikamv@gmail.com или со страницы приложения в RuStore.");

        add(box, "Сколько хранится");
        add(box, "Данные сада хранятся, пока ты ими пользуешься. Кнопка «Сбросить прогресс» в "
                + "настройках очищает прогресс на телефоне. Чтобы удалить сад из облака "
                + "(рейтинг и друзья), напиши в поддержку со страницы приложения в RuStore — "
                + "удалим в течение недели.");

        add(box, "Дети");
        add(box, "Приложение подходит и детям: нет чатов, рекламы и покупок за реальные деньги "
                + "без согласия взрослых. Ник, который ты вводишь, виден другим в рейтинге — "
                + "поэтому не стоит писать в него фамилию, адрес или телефон.");

        add(box, "Изменения");
        add(box, "Если политика поменяется, изменения появятся в этом же разделе вместе с "
                + "обновлением приложения.");

        add(box, "Дата последнего изменения: 15 ноября 2026 года.");

        return root;
    }

    private void add(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Theme.TEXT);
        t.setTextSize(13.5f);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(0, dp(6), 0, dp(6));
        boolean isHeading = !text.startsWith("•") && !text.startsWith("Данные")
                && Character.isUpperCase(text.charAt(0)) && text.length() < 60;
        if (isHeading) {
            t.setTextColor(Theme.ACCENT_SOFT);
            t.setTextSize(15f);
            t.setTypeface(Ui.medium());
            t.setPadding(0, dp(14), 0, dp(2));
        }
        box.addView(t);
    }
}
