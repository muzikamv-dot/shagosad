package com.dsh.stepcounter;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

/**
 * Палитра приложения.
 *
 * Две темы:
 *  • «Ночной сад» — тёплая тёмно-зелёная, мята в акценте;
 *  • «Обсидиан» — чёрный интерфейс, белое свечение, цвет остаётся только
 *    у живого контента: цветы, открытки, медали, рамки.
 *
 * Значения раньше были final-константами и вкомпилировались в код, поэтому
 * теперь это обычные static-поля: палитра выбирается при старте активности
 * (Theme.load) и применяется к уже создаваемым вью. Переключение темы
 * перезапускает MainActivity, чтобы все элементы пересобрались с новыми цветами.
 */
public final class Theme {

    public static final int T_NIGHT = 0;
    public static final int T_OBSIDIAN = 1;
    public static final int T_LAVENDER = 2;
    public static final int T_KAWAI = 3;
    public static final int T_LIGHT = 4;
    public static final int T_OLED = 5;

    public static final String[] NAMES = {"Ночной сад", "Обсидиан", "Лаванда", "Кавай",
            "Светлая", "OLED"};

    private static final String PREFS = "ui";
    private static final String KEY = "theme";

    private static int current = T_NIGHT;

    public static int BG_TOP, BG_BOTTOM, SURFACE, SURFACE_HI, STROKE;
    public static int TEXT, DIM, MUTED, FAINT;
    public static int ACCENT, ACCENT_SOFT, ACCENT_DEEP, CYAN, GOLD;
    public static int PINK, PINK_DEEP, LAVENDER, BLUSH;
    public static int CARD, CARD_STROKE, NAV;
    public static int PANEL_TOP, PANEL_BOTTOM, PANEL_BORDER, PANEL_TEXT;
    public static int CHIP, CHIP_BORDER, ACTION_OFF, SOIL_EDGE, GLOW;
    /** Цвет текста на акцентной кнопке: на мяте — тёмный, на белом — почти чёрный. */
    public static int ON_ACCENT;

    private Theme() { }

    public static int mode() { return current; }

    public static String name() { return NAMES[current]; }

    public static void load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        apply(p.getInt(KEY, T_NIGHT));
    }

    public static void save(Context c, int mode) {
        int m = (mode >= 0 && mode < NAMES.length) ? mode : T_NIGHT;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, m).apply();
        apply(m);
    }

    public static void apply(int mode) {
        current = (mode >= 0 && mode < NAMES.length) ? mode : T_NIGHT;
        switch (current) {
            case T_OBSIDIAN: obsidian(); break;
            case T_LAVENDER: lavender(); break;
            case T_KAWAI: kawai(); break;
            case T_LIGHT: light(); break;
            case T_OLED: oled(); break;
            default: nightGarden(); break;
        }
    }

    /** Светлая ли тема — от этого зависит цвет иконок в системной строке. */
    public static boolean isLight() { return current == T_KAWAI || current == T_LIGHT; }

    /**
     * Стиль окна активности. В светлой теме берём Material.Light, иначе системные
     * диалоги (сброс прогресса, ввод нормы, выбор аватара) остаются тёмными
     * поверх светлого экрана.
     */
    public static int styleRes() {
        return isLight()
                ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar;
    }

    // ---------------- ночной сад ----------------

    private static void nightGarden() {
        BG_TOP = 0xFF12211C;
        BG_BOTTOM = 0xFF1B3028;
        SURFACE = 0xFF1A2C25;
        SURFACE_HI = 0xFF243A31;
        STROKE = 0xFF2E4A3E;

        TEXT = 0xFFF6FBF8;
        DIM = 0xFFCBDCD5;
        MUTED = 0xFF93AFA4;
        FAINT = 0xFF6E8A80;

        ACCENT = 0xFF6FE7B7;
        ACCENT_SOFT = 0xFF8FF0C6;
        ACCENT_DEEP = 0xFF2AA98A;
        CYAN = 0xFF7FD6F0;
        GOLD = 0xFFFFD98E;

        PINK = 0xFFFFA6C9;
        PINK_DEEP = 0xFFE8749F;
        LAVENDER = 0xFFC6B4FF;
        BLUSH = 0x33FF9EC4;

        CARD = 0xFF1A2C25;
        CARD_STROKE = 0xFF2E4A3E;
        NAV = 0xFF12211C;

        PANEL_TOP = 0xFF254236;
        PANEL_BOTTOM = 0xFF15251E;
        PANEL_BORDER = 0xFF35604D;
        PANEL_TEXT = 0xFFF6FBF8;
        CHIP = 0xFF21362C;
        CHIP_BORDER = 0xFF2B453A;
        ACTION_OFF = 0xFF23382E;
        SOIL_EDGE = 0xFF2A1A0C;
        GLOW = 0x33FFE6A8;
        ON_ACCENT = 0xFF123326;
    }

    // ---------------- обсидиан ----------------

    private static void obsidian() {
        BG_TOP = 0xFF070709;
        BG_BOTTOM = 0xFF15151A;
        SURFACE = 0xFF13131A;
        SURFACE_HI = 0xFF1E1E27;
        STROKE = 0xFF2A2A35;

        TEXT = 0xFFFDFDFF;
        DIM = 0xFFDCDCE4;
        MUTED = 0xFF9E9EAA;
        FAINT = 0xFF75757F;

        // всё, что должно светиться — белое
        ACCENT = 0xFFF4F4F7;
        ACCENT_SOFT = 0xFFFFFFFF;
        ACCENT_DEEP = 0xFF8C8C97;
        CYAN = 0xFFDCE9F2;
        GOLD = 0xFFF1E6C9;

        PINK = 0xFFE9DFEE;
        PINK_DEEP = 0xFFC6B6D2;
        LAVENDER = 0xFFCFC9DD;
        BLUSH = 0x33FFFFFF;

        CARD = 0xFF13131A;
        CARD_STROKE = 0xFF2A2A35;
        NAV = 0xFF070709;

        PANEL_TOP = 0xFF1C1C24;
        PANEL_BOTTOM = 0xFF0C0C11;
        PANEL_BORDER = 0xFF33333F;
        PANEL_TEXT = 0xFFFDFDFF;
        CHIP = 0xFF1A1A22;
        CHIP_BORDER = 0xFF292933;
        ACTION_OFF = 0xFF1D1D25;
        SOIL_EDGE = 0xFF16120E;
        GLOW = 0x44FFFFFF;
        ON_ACCENT = 0xFF0A0A0C;
    }

    // ---------------- лаванда ----------------

    private static void lavender() {
        BG_TOP = 0xFF151024;
        BG_BOTTOM = 0xFF201A38;
        SURFACE = 0xFF1C1733;
        SURFACE_HI = 0xFF2B2449;
        STROKE = 0xFF3B3263;

        TEXT = 0xFFF6F2FF;
        DIM = 0xFFD6CEF2;
        MUTED = 0xFFA99BD2;
        FAINT = 0xFF7C6FA8;

        ACCENT = 0xFFC3A8FF;
        ACCENT_SOFT = 0xFFDCCBFF;
        ACCENT_DEEP = 0xFF8E6FDB;
        CYAN = 0xFFA9C8FF;
        GOLD = 0xFFF0DCA8;

        PINK = 0xFFF0A8D0;
        PINK_DEEP = 0xFFC86BA8;
        LAVENDER = 0xFFC3A8FF;
        BLUSH = 0x33E0A8FF;

        CARD = 0xFF1C1733;
        CARD_STROKE = 0xFF3B3263;
        NAV = 0xFF151024;

        PANEL_TOP = 0xFF302854;
        PANEL_BOTTOM = 0xFF171233;
        PANEL_BORDER = 0xFF483D78;
        PANEL_TEXT = 0xFFF6F2FF;
        CHIP = 0xFF241C40;
        CHIP_BORDER = 0xFF342B58;
        ACTION_OFF = 0xFF271F46;
        SOIL_EDGE = 0xFF2A1A0C;
        GLOW = 0x33DCCBFF;
        ON_ACCENT = 0xFF241A3D;
    }

    // ---------------- кавай (светлая) ----------------

    private static void kawai() {
        BG_TOP = 0xFFFFF4F8;
        BG_BOTTOM = 0xFFFFE7F0;
        SURFACE = 0xFFFFFFFF;
        SURFACE_HI = 0xFFFFE3EE;
        STROKE = 0xFFF5CADB;

        TEXT = 0xFF3A1E2A;
        DIM = 0xFF6E4A58;
        MUTED = 0xFF8F6373;
        FAINT = 0xFFB38C9C;

        ACCENT = 0xFFFF7FA8;
        // на светлом фоне подписи должны быть тёмными, иначе не читаются
        ACCENT_SOFT = 0xFFD6336C;
        ACCENT_DEEP = 0xFFE8558A;
        CYAN = 0xFFFFB38A;
        GOLD = 0xFFC8892A;

        PINK = 0xFFE84E7A;
        PINK_DEEP = 0xFFB0004F;
        LAVENDER = 0xFF8E6BD0;
        BLUSH = 0x33FF7FA8;

        CARD = 0xFFFFFFFF;
        CARD_STROKE = 0xFFF5CADB;
        NAV = 0xFFFFF4F8;

        PANEL_TOP = 0xFFFFDCE9;
        PANEL_BOTTOM = 0xFFFFF1F6;
        PANEL_BORDER = 0xFFF6C4D7;
        PANEL_TEXT = 0xFF3A1E2A;
        CHIP = 0xFFFFE7F0;
        CHIP_BORDER = 0xFFF6C8DA;
        ACTION_OFF = 0xFFFBE2EC;
        SOIL_EDGE = 0xFFE2C3A0;
        GLOW = 0x33FF8FB5;
        ON_ACCENT = 0xFF4A0E24;
    }

    // ---------------- светлая (стандартная) ----------------

    private static void light() {
        BG_TOP = 0xFFF7F8FA;
        BG_BOTTOM = 0xFFEDEFF3;
        SURFACE = 0xFFFFFFFF;
        SURFACE_HI = 0xFFF1F3F7;
        STROKE = 0xFFE1E5EB;

        TEXT = 0xFF1B1F24;
        DIM = 0xFF3F454D;
        MUTED = 0xFF6B7280;
        FAINT = 0xFF6F7885;

        // спокойный синий: привычно и не спорит с цветами на экране
        ACCENT = 0xFF2E6BE6;
        ACCENT_SOFT = 0xFF1D4ED8;
        ACCENT_DEEP = 0xFF1E40AF;
        CYAN = 0xFF0E7490;
        GOLD = 0xFFB45309;

        PINK = 0xFFBE185D;
        PINK_DEEP = 0xFF9D174D;
        LAVENDER = 0xFF6D28D9;
        BLUSH = 0x22E11D48;

        CARD = 0xFFFFFFFF;
        CARD_STROKE = 0xFFE1E5EB;
        NAV = 0xFFF7F8FA;

        PANEL_TOP = 0xFFEAF0F7;
        PANEL_BOTTOM = 0xFFFFFFFF;
        PANEL_BORDER = 0xFFD8DEE8;
        PANEL_TEXT = 0xFF1B1F24;
        CHIP = 0xFFEDF1F6;
        CHIP_BORDER = 0xFFDCE2EA;
        ACTION_OFF = 0xFFE8ECF2;
        SOIL_EDGE = 0xFFC9AE8C;
        GLOW = 0x1A2E6BE6;
        ON_ACCENT = 0xFFFFFFFF;
    }

    // ---------------- OLED: настоящий чёрный ----------------

    private static void oled() {
        // Фон ровно #000000: на OLED такие пиксели физически выключены.
        // Поверхности почти чёрные — их отделяют тонкие обводки, а не заливка.
        BG_TOP = 0xFF000000;
        BG_BOTTOM = 0xFF000000;
        SURFACE = 0xFF070707;
        SURFACE_HI = 0xFF101010;
        STROKE = 0xFF1A1A1A;

        TEXT = 0xFFEDEDED;
        DIM = 0xFFB9B9B9;
        MUTED = 0xFF808080;
        FAINT = 0xFF5F5F5F;

        // акцент приглушённый: яркая кнопка на весь экран — это лишний свет
        ACCENT = 0xFF178F6B;
        ACCENT_SOFT = 0xFF5AD0A8;
        ACCENT_DEEP = 0xFF0B5F47;
        CYAN = 0xFF8FBFCF;
        GOLD = 0xFFC9A15E;

        PINK = 0xFFCC8FA4;
        PINK_DEEP = 0xFF9E6478;
        LAVENDER = 0xFFA093D0;
        BLUSH = 0x22D08FA4;

        CARD = 0xFF070707;
        CARD_STROKE = 0xFF1A1A1A;
        NAV = 0xFF000000;

        PANEL_TOP = 0xFF080808;
        PANEL_BOTTOM = 0xFF000000;
        PANEL_BORDER = 0xFF1E1E1E;
        PANEL_TEXT = 0xFFEDEDED;
        CHIP = 0xFF0D0D0D;
        CHIP_BORDER = 0xFF262626;
        ACTION_OFF = 0xFF111111;
        SOIL_EDGE = 0xFF0A0705;
        GLOW = 0x00000000;   // свечения нет: каждый светлый пиксель — расход
        ON_ACCENT = 0xFF04120D;
    }
    public static void bars(Activity a) {
        View d = a.getWindow().getDecorView();
        int flags = d.getSystemUiVisibility();
        if (isLight()) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        d.setSystemUiVisibility(flags);
    }
}
