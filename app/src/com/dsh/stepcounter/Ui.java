package com.dsh.stepcounter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Единая визуальная система: цвета, шрифты, отступы, кнопки. */
public final class Ui {

    // палитра
    
    
    
    
    
    

    
    
    
    

    
    
    
    
    

    // отступы
    public static final int S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 20, S6 = 24;

    // шкала шрифтов: вместо разнобоя 9–15sp — четыре ступени
    public static final float T_CAPS = 10.5f;   // подписи капсом
    public static final float T_BODY = 14f;     // обычный текст
    public static final float T_TITLE = 20f;    // заголовок экрана
    public static final float T_VALUE = 17f;    // числа в карточках

    private Ui() { }

    /**
     * Базовая линия для вертикального центра текста по метрикам шрифта.
     * Без этого базовая линия подбирается на глаз и текст в разных блоках
     * «висит» по-разному.
     */
    public static float baseline(Paint paint, float centerY) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) / 2f;
    }

    /**
     * Русское склонение по числу: 1 лейка, 2 лейки, 5 леек.
     * Отдельные случаи — 11…14 леек и 21 лейка: правило «по последней цифре»
     * на них не работает, поэтому сначала проверяем остаток от 100.
     */
    public static String plural(int n, String one, String few, String many) {
        int a = Math.abs(n) % 100;
        if (a >= 11 && a <= 14) return many;
        switch (Math.abs(n) % 10) {
            case 1: return one;
            case 2:
            case 3:
            case 4: return few;
            default: return many;
        }
    }

    /** «1 лейка», «2 лейки», «5 леек». */
    public static String leeks(int n) {
        return n + " " + plural(n, "лейка", "лейки", "леек");
    }

    /** «1 шаг», «2 шага», «5 шагов». */
    public static String steps(int n) {
        return n + " " + plural(n, "шаг", "шага", "шагов");
    }

    /** «1 цветок», «2 цветка», «5 цветов». */
    public static String flowers(int n) {
        return plural(n, "цветок", "цветка", "цветов");
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static Typeface sans() { return Typeface.create("sans-serif", Typeface.NORMAL); }
    public static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    public static Typeface light() { return Typeface.create("sans-serif-light", Typeface.NORMAL); }
    public static Typeface serif() { return Typeface.create("serif", Typeface.NORMAL); }

    /** Заголовок экрана. */
    public static TextView title(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(Theme.ACCENT_SOFT);
        t.setTextSize(T_TITLE);
        t.setLetterSpacing(0.10f);
        t.setTypeface(medium());
        return t;
    }

    /** Мелкая подпись-секция. */
    public static TextView section(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(Theme.FAINT);
        t.setTextSize(T_CAPS);
        t.setLetterSpacing(0.10f);
        t.setTypeface(medium());
        t.setPadding(0, dp(c, 20), 0, dp(c, 8));
        return t;
    }

    public static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    private static GradientDrawable round(Context c, int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    private static void ripple(Button b, Context c, int base, int pressed, int radiusDp, int stroke) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                round(c, pressed, radiusDp, stroke));
        states.addState(new int[]{}, round(c, base, radiusDp, stroke));
        b.setBackground(states);
    }

    /** Главная кнопка. */
    public static Button primary(Context c, String text) {
        Button b = baseButton(c, text, Theme.ON_ACCENT, 15.5f);
        ripple(b, c, Theme.ACCENT, Theme.ACCENT_DEEP, 22, 0);
        return b;
    }

    /** Второстепенная кнопка-контур. */
    public static Button secondary(Context c, String text) {
        Button b = baseButton(c, text, Theme.DIM, 13.5f);
        ripple(b, c, Theme.SURFACE_HI, Theme.CARD_STROKE, 22, 0);
        return b;
    }

    /** Строка-пункт списка с шевроном. */
    public static LinearLayout row(Context c, String text) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable d = round(c, Theme.SURFACE, 20, 0);
        row.setBackground(d);
        row.setPadding(dp(c, 16), 0, dp(c, 14), 0);
        row.setClickable(true);
        row.setFocusable(true);

        TextView t = text(c, text, 13.5f, Theme.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text(c, "›", 18f, Theme.FAINT);
        row.addView(chevron);

        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52)));
        return row;
    }

    private static Button baseButton(Context c, String text, int color, float size) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(size);
        b.setTextColor(color);
        b.setTypeface(medium());
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.STROKE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
        return v;
    }
}
