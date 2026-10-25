package com.dsh.stepcounter;

import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Плавное «набегание» числа в TextView.
 *
 * Шаги так уже анимировались, а очки и вода прыгали мгновенно. Если значение
 * изменилось сильно (например, после восстановления копии), число ставим сразу:
 * анимировать скачок на сотни единиц бессмысленно.
 */
public final class CountUp {

    public interface Fmt { String at(int v); }

    private final TextView view;
    private final Fmt fmt;
    private ValueAnimator anim;
    private int shown = Integer.MIN_VALUE;

    public CountUp(TextView view, Fmt fmt) {
        this.view = view;
        this.fmt = fmt;
    }

    public void set(int target) {
        if (view == null) return;
        if (shown == Integer.MIN_VALUE || Math.abs(target - shown) > 500) {
            shown = target;
            view.setText(fmt.at(target));
            return;
        }
        if (shown == target) return;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofInt(shown, target);
        anim.setDuration(420L);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                shown = (Integer) a.getAnimatedValue();
                view.setText(fmt.at(shown));
            }
        });
        anim.start();
    }
}
