package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Коллекция по сезонам: у каждого сезона своя полка с собранными цветами.
 * Счётчика «из 365» больше нет — сбор не заканчивается, виды идут дальше.
 */
public class CollectionView extends View {

    public interface Listener {
        /** Нажали на собранный цветок: показать его открытку. */
        void onSpeciesTap(int species, boolean collected);
    }

    private static final int COLS = 5;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final HashMap<Integer, Bitmap> cache = new HashMap<>();

    /** Полка одного сезона. */
    private static final class Shelf {
        String key = "", title = "";
        final ArrayList<Integer> species = new ArrayList<>();
    }

    /** Есть ли вообще открытки: если да, пустая подсказка не нужна. */
    private boolean anyCards = false;

    private final ArrayList<Shelf> shelves = new ArrayList<>();

    private float d;
    private float offset = 0, maxOffset = 0, lastY = 0, velocity = 0;
    private boolean dragging = false;
    private float downX = 0, downY = 0;
    private boolean moved = false;
    private Listener listener;

    public CollectionView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1 * d);
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titlePaint.setLetterSpacing(0.10f);
        titlePaint.setTextSize(11 * d);
    }

    public void setListener(Listener l) { listener = l; }

    /** Разложить собранные цветы по сезонам: новые сезоны сверху. */
    public void setCards(List<GameState.Card> cards) {
        LinkedHashMap<String, Shelf> map = new LinkedHashMap<>();
        // необычные цветы (из магазина) идут в отдельный блок, в сезоны не попадают,
        // но если они есть — пустую подсказку показывать не надо
        anyCards = cards != null && !cards.isEmpty();
        for (GameState.Card c : cards) {
            if (c == null || c.species < 0) continue;   // праздничные — в блоке «ИЗ ИВЕНТОВ»
            String k = Season.key(c.date);
            Shelf s = map.get(k);
            if (s == null) {
                s = new Shelf();
                s.key = k;
                map.put(k, s);
            }
            s.species.add(c.species);
        }
        shelves.clear();
        shelves.addAll(map.values());
        Collections.sort(shelves, new Comparator<Shelf>() {
            @Override public int compare(Shelf a, Shelf b) {
                return Long.compare(Season.order(b.key), Season.order(a.key));
            }
        });
        for (Shelf s : shelves) {
            s.title = Season.label(s.key).toUpperCase(Locale.US) + " · "
                    + s.species.size() + " " + flowersWord(s.species.size());
        }
        cache.clear();
        offset = 0;
        maxOffset = Math.max(0, contentHeight() - getHeight());
        invalidate();
    }

    /** Склонение: 1 цветок, 2 цветка, 5 цветов. */
    private static String flowersWord(int n) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return "цветок";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "цветка";
        return "цветов";
    }

    private float pad() { return 12 * d; }

    private float gap() { return 8 * d; }

    private float headerH() { return 34 * d; }

    private float cell() {
        float w = getWidth() - pad() * 2 - gap() * (COLS - 1);
        return Math.max(1f, w / COLS);
    }

    private int rowsOf(Shelf s) { return (s.species.size() + COLS - 1) / COLS; }

    private float shelfHeight(Shelf s) {
        int rows = rowsOf(s);
        if (rows == 0) return 0;
        return headerH() + rows * cell() + (rows - 1) * gap() + gap();
    }

    private float contentHeight() {
        float h = pad();
        for (Shelf s : shelves) h += shelfHeight(s);
        return h + pad();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        maxOffset = Math.max(0, contentHeight() - h);
        if (offset > maxOffset) offset = maxOffset;
    }

    private Bitmap thumb(int species) {
        Bitmap b = cache.get(species);
        if (b != null) return b;
        int s = Math.max(64, Math.round(cell() * 0.94f));
        b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(b);
        KawaiiPlant.draw(cc, species, 10, s / 2f, s * 0.93f, s * 0.88f, 0f, 0.32f);
        if (cache.size() > 80) cache.clear();
        cache.put(species, b);
        return b;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float y = pad() - offset;
        float cellSize = cell();
        float gp = gap();
        float pd = pad();

        if (shelves.isEmpty()) {
            // коллекция ещё пустая: не оставляем экран белым пятном
            if (!anyCards) {
                titlePaint.setColor(Theme.FAINT);
                String hint = "Пока пусто — вырастите первый цветок";
                c.drawText(hint, (getWidth() - titlePaint.measureText(hint)) / 2f,
                        getHeight() * 0.28f, titlePaint);
            }
            return;
        }

        for (Shelf s : shelves) {
            if (y > getHeight()) break;
            // заголовок полки: «ОСЕНЬ 2026 · 13 цветов»
            if (y + headerH() > 0) {
                titlePaint.setColor(Theme.ACCENT_SOFT);
                c.drawText(s.title, pd,
                        y + headerH() * 0.60f + titlePaint.getTextSize() * 0.35f, titlePaint);
            }
            float gy = y + headerH();
            for (int i = 0; i < s.species.size(); i++) {
                int col = i % COLS, row = i / COLS;
                float x = pd + col * (cellSize + gp);
                float cy = gy + row * (cellSize + gp);
                if (cy + cellSize < 0 || cy > getHeight()) continue;   // не рисуем за экраном

                r.set(x, cy, x + cellSize, cy + cellSize);
                cellPaint.setStyle(Paint.Style.FILL);
                cellPaint.setColor(Theme.SURFACE_HI);
                c.drawRoundRect(r, cellSize * 0.22f, cellSize * 0.22f, cellPaint);

                strokePaint.setColor(Theme.ACCENT_DEEP);
                c.drawRoundRect(r, cellSize * 0.22f, cellSize * 0.22f, strokePaint);

                Bitmap b = thumb(s.species.get(i));
                float inset = cellSize * 0.08f;
                r.set(x + inset, cy + inset, x + cellSize - inset, cy + cellSize - inset);
                c.drawBitmap(b, null, r, imgPaint);
            }
            y += shelfHeight(s);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                moved = false;
                lastY = e.getY();
                downX = e.getX();
                downY = e.getY();
                velocity = 0;
                removeCallbacks(fling);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return true;
                float dy = e.getY() - lastY;
                if (Math.abs(e.getY() - downY) > 6 * d) moved = true;
                offset -= dy;
                offset = Math.max(0, Math.min(maxOffset, offset));
                velocity = -dy;
                lastY = e.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                if (!moved) { tap(downX, downY); return true; }
                if (Math.abs(velocity) > 3 * d) {
                    maxOffset = Math.max(0, contentHeight() - getHeight());
                    post(fling);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private final Runnable fling = new Runnable() {
        @Override public void run() {
            velocity *= 0.92f;
            if (Math.abs(velocity) < 0.6f * d) return;
            offset = Math.max(0, Math.min(maxOffset, offset + velocity));
            invalidate();
            postDelayed(this, 16);
        }
    };

    private void tap(float x, float y) {
        if (listener == null) return;
        float cellSize = cell(), gp = gap(), pd = pad();
        float cy = y + offset;
        float top = pd;
        for (Shelf s : shelves) {
            float h = shelfHeight(s);
            if (cy >= top && cy < top + h) {
                float gy = cy - (top + headerH());
                if (gy < 0) return;
                int row = (int) (gy / (cellSize + gp));
                int col = (int) ((x - pd) / (cellSize + gp));
                if (col < 0 || col >= COLS || row < 0) return;
                int index = row * COLS + col;
                if (index < 0 || index >= s.species.size()) return;
                listener.onSpeciesTap(s.species.get(index), true);
                return;
            }
            top += h;
        }
    }
}
