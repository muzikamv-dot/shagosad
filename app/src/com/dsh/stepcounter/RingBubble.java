package com.dsh.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.LinearLayout;
import java.util.ArrayList;

/**
 * Прямоугольное кольцо вокруг цифр шагов.
 *
 * То же самое кольцо, только прямоугольное: толстая дорожка, насечки снаружи,
 * градиентная дуга прогресса (от верха по центру по часовой стрелке).
 *
 * Насечки считаются по каждой стороне отдельно и раскладываются симметрично
 * относительно середины стороны — тогда слева и справа рисунок одинаковый
 * и не «пляшет» на углах, как при равномерном шаге по всему периметру.
 */
public class RingBubble extends LinearLayout {

    private static final float TICK_STEP_DP = 13f;   // желаемый шаг насечек

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickHot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path frame = new Path();
    private final Path frameIn = new Path();   // контур чуть внутри — для тени и блика
    private final Path segment = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final PathMeasure measureIn = new PathMeasure();

    /** Готовая насечка: отрезок, расстояние вдоль контура и признак «длинная». */
    private static final class Tick {
        float x1, y1, x2, y2, dist;
        boolean longTick;
    }

    private final ArrayList<Tick> ticks = new ArrayList<>();

    private float density;
    private float glow = 0f;
    private float progress = 0f;
    private float topOffset = 0f;   // расстояние вдоль контура до «верха по центру»
    private float frameLen = 0f;
    private int tickSeq = 0;   // сквозной номер насечки: длинная/короткая строго по очереди
    private float morph = 1f;  // 0 — пилюля (как круг), 1 — прямоугольник с малым скруглением
    private boolean volumetric = true;   // рисовать объём: тени и блики, как у кольца

    public RingBubble(Context c) {
        super(c);
        setWillNotDraw(false);
        density = getResources().getDisplayMetrics().density;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(14f * density);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(Theme.STROKE);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(14f * density);
        arc.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(26f * density);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setAlpha(60);

        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(2f * density);
        tick.setColor(Theme.STROKE);

        tickHot.setStyle(Paint.Style.STROKE);
        tickHot.setStrokeWidth(2.4f * density);
        tickHot.setColor(Theme.ACCENT);
    }

    /** k: 0 — прямоугольника нет, 1 — проявлен полностью. */
    public void setGlow(float k) {
        float v = Math.max(0f, Math.min(1f, k));
        if (Math.abs(v - glow) < 0.001f) return;
        glow = v;
        morph = v;
        if (v > 0f) invalidate();
    }

    /** Объёмный вид: включается, когда и кольцо нарисовано объёмно. */
    public void setVolumetric(boolean v) {
        if (volumetric == v) return;
        volumetric = v;
        if (glow > 0f) invalidate();
    }

    /** Доля закрытой нормы — как у круглого кольца. */
    public void setProgress(float p) {
        float v = Math.max(0f, Math.min(1f, p));
        if (Math.abs(v - progress) < 0.0005f) return;
        progress = v;
        if (glow > 0f) invalidate();
    }

    /** Контур, длина, точка «верх по центру» и раскладка насечек по сторонам. */
    private void buildFrame() {
        int w = getWidth(), h = getHeight();

        float stroke = 14f * density;
        float inset = stroke / 2f + 3f * density;
        box.set(inset, inset, w - inset, h - inset);
        frame.reset();
        ticks.clear();
        tickSeq = 0;
        if (box.width() <= 0 || box.height() <= 0) { frameLen = 0f; return; }
        // морф: сначала почти пилюля (как круглое кольцо), к концу — прямоугольник
        float pill = Math.min(box.width(), box.height()) / 2f;
        // сначала держим пилюлю (как круг), распрямляем углы ближе к концу
        float eased = morph * morph;
        float radius = pill + (24f * density - pill) * eased;
        float r = Math.min(radius, pill);
        frame.addRoundRect(box, r, r, Path.Direction.CW);
        float insetIn = 2.4f * density;
        RectF boxIn = new RectF(box.left + insetIn, box.top + insetIn,
                box.right - insetIn, box.bottom - insetIn);
        float rIn = Math.max(2f * density, r - insetIn);
        frameIn.reset();
        frameIn.addRoundRect(boxIn, rIn, rIn, Path.Direction.CW);
        measureIn.setPath(frameIn, false);

        measure.setPath(frame, false);
        frameLen = measure.getLength();
        topOffset = Math.max(0f, box.width() / 2f - r);

        // длины прямых участков и радиусных уголков
        float topLen = Math.max(0f, box.width() - 2f * r);
        float sideLen = Math.max(0f, box.height() - 2f * r);
        float corner = Math.max(0f, (frameLen - 2f * topLen - 2f * sideLen) / 4f);

        float base = stroke / 2f + 6f * density;
        float longLen = 7f * density;
        float shortLen = 4.5f * density;
        float step = TICK_STEP_DP * density;

        // addRoundRect начинает с левого верхнего угла и идёт по часовой стрелке:
        // верх -> угол -> право -> угол -> низ -> угол -> лево -> угол
        float offset = 0f;
        // верх: слева направо
        addEdgeTicks(box.left + r, box.top, box.right - r, box.top,
                0f, -1f, topLen, offset, step, base, longLen, shortLen);
        offset += topLen;
        addCornerTicks(box.right - r, box.top + r, r, -90f, 0f, offset, corner, step,
                base, longLen, shortLen);
        offset += corner;
        // право: сверху вниз
        addEdgeTicks(box.right, box.top + r, box.right, box.bottom - r,
                1f, 0f, sideLen, offset, step, base, longLen, shortLen);
        offset += sideLen;
        addCornerTicks(box.right - r, box.bottom - r, r, 0f, 90f, offset, corner, step,
                base, longLen, shortLen);
        offset += corner;
        // низ: справа налево
        addEdgeTicks(box.right - r, box.bottom, box.left + r, box.bottom,
                0f, 1f, topLen, offset, step, base, longLen, shortLen);
        offset += topLen;
        addCornerTicks(box.left + r, box.bottom - r, r, 90f, 180f, offset, corner, step,
                base, longLen, shortLen);
        offset += corner;
        // лево: снизу вверх
        addEdgeTicks(box.left, box.bottom - r, box.left, box.top + r,
                -1f, 0f, sideLen, offset, step, base, longLen, shortLen);
        offset += sideLen;
        addCornerTicks(box.left + r, box.top + r, r, 180f, 270f, offset, corner, step,
                base, longLen, shortLen);
    }

    /**
     * Насечки на скруглённом углу: идут по дуге, направление — радиально наружу,
     * то есть всегда перпендикулярно линии рамки, как и на прямых сторонах.
     * Углы в градусах: 0 — вправо, 90 — вниз (экранные координаты).
     */
    private void addCornerTicks(float cx, float cy, float r,
                                float startDeg, float endDeg, float offset,
                                float arcLen, float step,
                                float base, float longLen, float shortLen) {
        if (arcLen < step * 0.6f) return;
        int count = Math.max(1, Math.round(arcLen / step));
        for (int i = 0; i < count; i++) {
            float t = (i + 0.5f) / count;
            double deg = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            float ux = (float) Math.cos(deg);
            float uy = (float) Math.sin(deg);
            boolean longTick = (tickSeq++ % 2 == 0);
            float len = longTick ? longLen : shortLen;

            Tick tk = new Tick();
            tk.x1 = cx + ux * (r + base);
            tk.y1 = cy + uy * (r + base);
            tk.x2 = cx + ux * (r + base + len);
            tk.y2 = cy + uy * (r + base + len);
            tk.dist = offset + arcLen * t;
            tk.longTick = longTick;
            ticks.add(tk);
        }
    }

    /**
     * Раскладывает насечки вдоль одной прямой стороны. Количество округляется
     * до чётного, а позиции берутся симметрично от середины — поэтому слева и справа
     * рисунок совпадает. Длинные насечки чередуются от середины наружу.
     */
    private void addEdgeTicks(float x1, float y1, float x2, float y2,
                              float nx, float ny, float edgeLen, float offset,
                              float step, float base, float longLen, float shortLen) {
        if (edgeLen < step) return;
        int count = Math.max(2, Math.round(edgeLen / step));
        if (count % 2 != 0) count++;

        for (int i = 0; i < count; i++) {
            float t = (i + 0.5f) / count;
            float px = x1 + (x2 - x1) * t;
            float py = y1 + (y2 - y1) * t;
            boolean longTick = (tickSeq++ % 2 == 0);
            float len = longTick ? longLen : shortLen;

            Tick tk = new Tick();
            tk.x1 = px + nx * base;
            tk.y1 = py + ny * base;
            tk.x2 = px + nx * (base + len);
            tk.y2 = py + ny * (base + len);
            tk.dist = offset + edgeLen * t;
            tk.longTick = longTick;
            ticks.add(tk);
        }
    }


    @Override
    protected void dispatchDraw(Canvas c) {
        if (glow > 0.01f) buildFrame();
        if (glow > 0.01f && frameLen > 0f) {
            if (volumetric) {
                // тень под рамкой: две мягкие обводки со сдвигом вниз-вправо
                c.save();
                c.translate(2f * density, 3f * density);
                track.setStrokeWidth(30f * density);
                track.setAlpha((int) (0x0A * glow));
                c.drawPath(frame, track);
                track.setStrokeWidth(22f * density);
                track.setAlpha((int) (0x12 * glow));
                c.drawPath(frame, track);
                c.restore();
                track.setStrokeWidth(14f * density);
            }

            track.setAlpha((int) (255 * glow));
            c.drawPath(frame, track);

            if (volumetric) {
                // мягкий блик по верхней кромке — объём без тёмных полос
                Paint edge = glowPaint;
                edge.setShader(null);
                edge.setStrokeWidth(2.0f * density);
                edge.setColor(0x55FFFFFF);
                edge.setAlpha((int) (120 * glow));
                c.save();
                c.translate(-1.5f * density, -1.5f * density);
                c.drawPath(frameIn, edge);
                c.restore();
            }

            if (progress > 0.005f) {
                LinearGradient g = new LinearGradient(box.left, box.top, box.right, box.bottom,
                        new int[]{Theme.ACCENT, Theme.CYAN, Theme.ACCENT},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
                arc.setShader(g);
                glowPaint.setShader(g);
                arc.setAlpha((int) (255 * glow));
                glowPaint.setAlpha((int) (60 * glow));
                float len = frameLen * progress;
                drawRun(c, topOffset, len, glowPaint);
                drawRun(c, topOffset, len, arc);
            }

            float lit = progress * frameLen;
            tick.setAlpha((int) (185 * glow));
            tickHot.setAlpha((int) (255 * glow));
            for (int i = 0; i < ticks.size(); i++) {
                Tick tk = ticks.get(i);
                float fromStart = tk.dist - topOffset;
                while (fromStart < 0f) fromStart += frameLen;
                boolean on = progress > 0.005f && fromStart <= lit;
                c.drawLine(tk.x1, tk.y1, tk.x2, tk.y2, on ? tickHot : tick);
            }
        }
        super.dispatchDraw(c);
    }

    /** То же по внутреннему контуру — для тени и блика. */
    private void drawRunIn(Canvas c, float start, float len, Paint p) {
        float total = 0f;
        try { total = measureIn.getLength(); } catch (Throwable ignored) { }
        if (total <= 0f) return;
        while (start >= total) start -= total;
        float end = start + len;
        if (end <= total) {
            segment.reset();
            if (measureIn.getSegment(start, end, segment, true)) c.drawPath(segment, p);
        } else {
            segment.reset();
            if (measureIn.getSegment(start, total, segment, true)) c.drawPath(segment, p);
            segment.reset();
            if (measureIn.getSegment(0f, end - total, segment, true)) c.drawPath(segment, p);
        }
    }

    /** Рисует часть контура длиной len, начиная со start (с переходом через начало пути). */
    private void drawRun(Canvas c, float start, float len, Paint p) {
        float end = start + len;
        if (end <= frameLen) {
            segment.reset();
            if (measure.getSegment(start, end, segment, true)) c.drawPath(segment, p);
        } else {
            segment.reset();
            if (measure.getSegment(start, frameLen, segment, true)) c.drawPath(segment, p);
            segment.reset();
            if (measure.getSegment(0f, end - frameLen, segment, true)) c.drawPath(segment, p);
        }
    }
}
