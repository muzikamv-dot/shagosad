package com.dsh.stepcounter;

/**
 * Настоящее положение солнца и луны над горизонтом.
 *
 * Считается локально по дате и координатам, без сети и без разрешений:
 * формула солнца — стандартная NOAA/Меёс, луна — низкоточные главные члены
 * лунной теории (точность порядка половины градуса, для рисунка более чем).
 *
 * Углы в градусах: высота 0 — горизонт, +90 — зенит, отрицательная — под горизонтом.
 * Азимут: 0 — север, 90 — восток, 180 — юг, 270 — запад.
 */
final class SunMoon {

    private static final double RAD = Math.PI / 180.0;
    private static final double DEG = 180.0 / Math.PI;

    private SunMoon() { }

    static double julianDay(long ms) {
        return ms / 86400000.0 + 2440587.5;
    }

    /** {высота, азимут} солнца. */
    static float[] sun(long ms, double lat, double lon) {
        double jd = julianDay(ms);
        double t = (jd - 2451545.0) / 36525.0;

        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t;
        double mr = m * RAD;
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(mr)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * mr)
                + 0.000289 * Math.sin(3 * mr);
        double omega = 125.04 - 1934.136 * t;
        double lambda = l0 + c - 0.00569 - 0.00478 * Math.sin(omega * RAD);

        return altAz(lambda, 0.0, obliquity(t), jd, lat, lon);
    }

    /** Долгота солнца в эклиптике — нужна для освещённости луны. */
    private static double sunLambda(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t;
        double mr = m * RAD;
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(mr)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * mr)
                + 0.000289 * Math.sin(3 * mr);
        return l0 + c;
    }

    /** {высота, азимут} луны. */
    static float[] moon(long ms, double lat, double lon) {
        double jd = julianDay(ms);
        double t = (jd - 2451545.0) / 36525.0;

        double lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t;
        double d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t * t;
        double m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t * t;
        double mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t * t;
        double f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t * t;

        double lambda = lp
                + 6.289 * Math.sin(mp * RAD)
                + 1.274 * Math.sin((2 * d - mp) * RAD)
                + 0.658 * Math.sin(2 * d * RAD)
                + 0.214 * Math.sin(2 * mp * RAD)
                - 0.186 * Math.sin(m * RAD)
                - 0.114 * Math.sin(2 * f * RAD);
        double beta = 5.128 * Math.sin(f * RAD)
                + 0.281 * Math.sin((mp + f) * RAD)
                - 0.278 * Math.sin((f - mp) * RAD)
                - 0.173 * Math.sin((2 * d - f) * RAD);

        return altAz(lambda, beta, obliquity(t), jd, lat, lon);
    }

    /** Освещённая доля луны, 0 — новолуние, 1 — полная. */
    static float moonIllum(long ms) {
        double jd = julianDay(ms);
        double t = (jd - 2451545.0) / 36525.0;
        double lp = 218.3164477 + 481267.88123421 * t;
        double d = 297.8501921 + 445267.1114034 * t;
        double m = 357.5291092 + 35999.0502909 * t;
        double mp = 134.9633964 + 477198.8675055 * t;
        double lambda = lp + 6.289 * Math.sin(mp * RAD)
                + 1.274 * Math.sin((2 * d - mp) * RAD)
                + 0.658 * Math.sin(2 * d * RAD)
                + 0.214 * Math.sin(2 * mp * RAD)
                - 0.186 * Math.sin(m * RAD);
        double elong = norm(lambda - sunLambda(jd));
        return (float) ((1 - Math.cos(elong * RAD)) / 2);
    }

    /** Фаза как доля синодического месяца: 0 — новолуние, 0.5 — полная. */
    static float moonPhase(long ms) {
        double jd = julianDay(ms);
        double t = (jd - 2451545.0) / 36525.0;
        double lp = 218.3164477 + 481267.88123421 * t;
        double d = 297.8501921 + 445267.1114034 * t;
        double mp = 134.9633964 + 477198.8675055 * t;
        double lambda = lp + 6.289 * Math.sin(mp * RAD)
                + 1.274 * Math.sin((2 * d - mp) * RAD);
        return (float) (norm(lambda - sunLambda(jd)) / 360.0);
    }

    private static double obliquity(double t) {
        return 23.0 + (26.0 + (21.448 - 46.815 * t - 0.00059 * t * t
                + 0.001813 * t * t * t) / 60.0) / 60.0;
    }

    /** Перевод эклиптических координат в высоту и азимут для наблюдателя. */
    private static float[] altAz(double lambda, double beta, double eps, double jd,
                                 double lat, double lon) {
        double lr = lambda * RAD, br = beta * RAD, er = eps * RAD;
        double sinLam = Math.sin(lr), cosLam = Math.cos(lr);
        double sinBet = Math.sin(br), cosBet = Math.cos(br);
        double sinEps = Math.sin(er), cosEps = Math.cos(er);

        double ra = Math.atan2(sinLam * cosEps - (sinBet / cosBet) * sinEps, cosLam);
        double dec = Math.asin(sinBet * cosEps + cosBet * sinEps * sinLam);

        double gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0);
        double hourAngle = (gmst + lon) * RAD - ra;

        double latR = lat * RAD;
        double sinAlt = Math.sin(latR) * Math.sin(dec)
                + Math.cos(latR) * Math.cos(dec) * Math.cos(hourAngle);
        double alt = Math.asin(Math.max(-1, Math.min(1, sinAlt)));
        double az = Math.atan2(Math.sin(hourAngle),
                Math.cos(hourAngle) * Math.sin(latR) - Math.tan(dec) * Math.cos(latR));
        az = az * DEG + 180.0;
        if (az < 0) az += 360;

        return new float[]{(float) (alt * DEG), (float) az};
    }

    private static double norm(double deg) {
        double x = deg % 360.0;
        return x < 0 ? x + 360.0 : x;
    }
}
