package com.dsh.stepcounter;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Контейнер с чувствительным горизонтальным свайпом. */
public class SwipeFrameLayout extends FrameLayout {

    public interface Listener { void onSwipe(int direction); } // -1 влево, +1 вправо

    private float startX, startY;
    private int slop;
    private boolean horizontal;
    private Listener listener;
    private VelocityTracker tracker;

    public SwipeFrameLayout(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
    }

    public void setListener(Listener l) { listener = l; }

    /** Не даём спискам отключать перехват — иначе свайп внутри них не работает. */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallow) {
        super.requestDisallowInterceptTouchEvent(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                horizontal = false;
                if (tracker == null) tracker = VelocityTracker.obtain();
                tracker.clear();
                tracker.addMovement(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracker != null) tracker.addMovement(ev);
                float dx = Math.abs(ev.getX() - startX);
                float dy = Math.abs(ev.getY() - startY);
                // достаточно сдвига в половину slop и минимального перевеса по горизонтали
                if (dx > slop * 0.6f && dx > dy * 1.05f) {
                    horizontal = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseTracker();
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (tracker == null) {
            tracker = VelocityTracker.obtain();
        }
        tracker.addMovement(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                horizontal = false;
                tracker.clear();
                tracker.addMovement(ev);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(ev.getX() - startX);
                float dy = Math.abs(ev.getY() - startY);
                if (!horizontal && dx > slop * 0.6f && dx > dy * 1.05f) {
                    horizontal = true;
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (horizontal && listener != null) {
                    float delta = ev.getX() - startX;
                    tracker.computeCurrentVelocity(1000);
                    float vx = tracker.getXVelocity();
                    boolean fast = Math.abs(vx) > 200f;
                    if (Math.abs(delta) > slop * 0.5f || fast) {
                        int dir = Math.abs(delta) > 0.01f ? (delta < 0 ? -1 : 1) : (vx < 0 ? -1 : 1);
                        listener.onSwipe(dir);
                    }
                }
                horizontal = false;
                releaseTracker();
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                horizontal = false;
                releaseTracker();
                return true;
        }
        return true;
    }

    private void releaseTracker() {
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
    }
}
