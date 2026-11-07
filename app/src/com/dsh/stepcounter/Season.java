package com.dsh.stepcounter;

/**
 * Сезон для коллекции: зима (декабрь–февраль), весна, лето, осень.
 *
 * Сезон — это только полка в гербарии и статистика «сколько успел собрать».
 * Виды цветов от сезона не зависят: генератор продолжает выдавать случайные.
 */
public final class Season {

    private Season() { }

    /** Ключ сезона по дню сбора: a2026 (осень), w2026 (зима 2026–27), s2027, u2027. */
    public static String key(String day) {
        if (day == null) return "a" + 2000;
        String[] p = day.split("-");
        if (p.length < 2) return "a" + 2000;
        int y;
        int m;
        try {
            y = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (Exception e) {
            return "a" + 2000;
        }
        if (m >= 9 && m <= 11) return "a" + y;          // осень
        if (m >= 3 && m <= 5) return "s" + y;           // весна
        if (m >= 6 && m <= 8) return "u" + y;           // лето
        if (m == 12) return "w" + y;                    // зима начинается в декабре
        return "w" + (y - 1);                           // январь и февраль
    }

    /** Подпись сезона: «Осень 2026», «Зима 2026–2027». */
    public static String label(String key) {
        if (key == null || key.length() < 5) return "";
        char c = key.charAt(0);
        int y;
        try {
            y = Integer.parseInt(key.substring(1));
        } catch (Exception e) {
            return "";
        }
        switch (c) {
            case 'w': return "Зима " + y + "–" + (y + 1);
            case 's': return "Весна " + y;
            case 'u': return "Лето " + y;
            default:  return "Осень " + y;
        }
    }

    /** Порядок для сортировки: чем больше, тем новее сезон. */
    public static long order(String key) {
        if (key == null || key.length() < 5) return 0;
        char c = key.charAt(0);
        int y;
        try {
            y = Integer.parseInt(key.substring(1));
        } catch (Exception e) {
            return 0;
        }
        long base = y * 12L;
        switch (c) {
            case 'w': return base;          // декабрь
            case 's': return base + 3;
            case 'u': return base + 6;
            default:  return base + 9;      // осень
        }
    }

    /** Текущий сезон (по часам телефона). */
    public static String now() { return key(Steps.today()); }
}
