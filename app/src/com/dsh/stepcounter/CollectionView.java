package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Сетка коллекции: все 365 видов, собранные цветными, остальные — силуэтами.
 *
 * Свой скролл вместо RecyclerView: в проекте нет androidx, а тянуть зависимость
 * ради одной сетки не хочется. Миниатюры рисуются параметрической моделью один
 * раз и лежат в маленьком кэше, поэтому прокрутка не пересчитывает цветы.
 */
public class CollectionView extends View {

    public interface Listener {
        /** Нажали на вид: собран — можно показать открытку. */
        void onSpeciesTap(int species, boolean collected);
    }

    public static final int FILTER_ALL = 0, FILTER_OWNED = 1, FILTER_MISSING = 2;

    private static final int COLS = 5;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final HashMap<Integer, Bitmap> cache = new HashMap<>();
    private final ArrayList<Integer> ids = new ArrayList<>();

    private HashSet<Integer> owned = new HashSet<>();
    private int filter = FILTER_ALL;

    private float d;
    private float offset = 0, maxOffset = 0, lastY = 0, velocity = 0;
    private boolean dragging = false;
    private float downX = 0, downY = 0;
    private boolean moved = false;
    private PorterDuffColorFilter silhouette;
    private Listener listener;

    public CollectionView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1 * d);
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        silhouette = new PorterDuffColorFilter(Theme.CHIP_BORDER, PorterDuff.Mode.SRC_IN);
    }

    public void setListener(Listener l) { listener = l; }

    public void setData(HashSet<Integer> ownedSpecies, int newFilter) {
        owned = ownedSpecies;
        filter = newFilter;
        rebuild();
    }

    private void rebuild() {
        ids.clear();
        for (int i = 0; i < Species.COUNT; i++) {
            boolean has = owned.contains(i);
            if (filter == FILTER_OWNED && !has) continue;
            if (filter == FILTER_MISSING && has) continue;
            ids.add(i);
        }
        cache.clear();
        offset = 0;
        maxOffset = Math.max(0, contentHeight() - getHeight());
        invalidate();
    }

    private float pad() { return 12 * d; }
    private float gap() { return 8 * d; }

    private float cell() {
        float w = getWidth() - pad() * 2 - gap() * (COLS - 1);
        return Math.max(1f, w / COLS);
    }

    private int rows() { return (ids.size() + COLS - 1) / COLS; }

    private float contentHeight() {
        int rws = rows();
        if (rws == 0) return 0;
        return pad() * 2 + rws * cell() + (rws - 1) * gap();
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
        float cellSize = cell();
        float gp = gap();
        float pd = pad();

        for (int i = 0; i < ids.size(); i++) {
            int col = i % COLS, row = i / COLS;
            float x = pd + col * (cellSize + gp);
            float y = pd + row * (cellSize + gp) - offset;
            if (y + cellSize < 0 || y > getHeight()) continue;   // не рисуем за экраном

            int species = ids.get(i);
            boolean has = owned.contains(species);

            r.set(x, y, x + cellSize, y + cellSize);
            cellPaint.setStyle(Paint.Style.FILL);
            cellPaint.setColor(has ? Theme.SURFACE_HI : Theme.SURFACE);
            c.drawRoundRect(r, cellSize * 0.22f, cellSize * 0.22f, cellPaint);

            strokePaint.setColor(has ? Theme.ACCENT_DEEP : Theme.CARD_STROKE);
            c.drawRoundRect(r, cellSize * 0.22f, cellSize * 0.22f, strokePaint);

            Bitmap b = thumb(species);
            imgPaint.setColorFilter(has ? null : silhouette);
            float inset = cellSize * 0.08f;
            r.set(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset);
            c.drawBitmap(b, null, r, imgPaint);
            imgPaint.setColorFilter(null);
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
        int col = (int) ((x - pd) / (cellSize + gp));
        int row = (int) ((cy - pd) / (cellSize + gp));
        if (col < 0 || col >= COLS || row < 0) return;
        int index = row * COLS + col;
        if (index < 0 || index >= ids.size()) return;
        int species = ids.get(index);
        listener.onSpeciesTap(species, owned.contains(species));
    }
}
