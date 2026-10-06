package kz.almas.netoptimizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class NetworkSplashView extends View {
    private static final int NODE_COUNT = 22;
    private final float[] nx = new float[NODE_COUNT];
    private final float[] ny = new float[NODE_COUNT];
    private final float[] phase = new float[NODE_COUNT];
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long startedAt;

    public NetworkSplashView(Context context) {
        super(context);
        init();
    }

    public NetworkSplashView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NetworkSplashView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        Random r = new Random(16062026L);
        for (int i = 0; i < NODE_COUNT; i++) {
            nx[i] = 0.06f + r.nextFloat() * 0.88f;
            ny[i] = 0.08f + r.nextFloat() * 0.84f;
            phase[i] = r.nextFloat() * 6.28318f;
        }

        linePaint.setStrokeWidth(dp(0.8f));
        nodePaint.setStyle(Paint.Style.FILL);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(1.2f));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        scanPaint.setStrokeWidth(dp(1.2f));
        startedAt = System.currentTimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        float t = (now - startedAt) / 1000f;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.45f;

        drawCoreGlow(canvas, cx, cy, w, h, t);
        drawGrid(canvas, w, h, t);
        drawNetwork(canvas, w, h, t);
        drawRadar(canvas, cx, cy, w, t);
        drawScan(canvas, w, h, t);
        drawSignalBars(canvas, w, h, t);

        postInvalidateOnAnimation();
    }

    private void drawCoreGlow(Canvas canvas, float cx, float cy, float w, float h, float t) {
        float pulse = 0.92f + 0.08f * (float) Math.sin(t * 2.4f);
        float radius = Math.min(w, h) * 0.38f * pulse;
        glowPaint.setShader(new RadialGradient(
                cx, cy, radius,
                new int[]{Color.argb(52, 25, 211, 174), Color.argb(18, 21, 151, 255), Color.TRANSPARENT},
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, glowPaint);
        glowPaint.setShader(null);
    }

    private void drawGrid(Canvas canvas, float w, float h, float t) {
        Paint p = linePaint;
        p.setColor(Color.argb(18, 60, 180, 210));
        float gap = dp(30);
        float offset = (t * dp(8)) % gap;
        for (float x = -gap + offset; x < w + gap; x += gap) {
            canvas.drawLine(x, 0, x, h, p);
        }
        for (float y = -gap + offset; y < h + gap; y += gap) {
            canvas.drawLine(0, y, w, y, p);
        }
    }

    private void drawNetwork(Canvas canvas, float w, float h, float t) {
        float[] px = new float[NODE_COUNT];
        float[] py = new float[NODE_COUNT];

        for (int i = 0; i < NODE_COUNT; i++) {
            float driftX = (float) Math.sin(t * 0.65f + phase[i]) * dp(5);
            float driftY = (float) Math.cos(t * 0.52f + phase[i] * 1.3f) * dp(6);
            px[i] = nx[i] * w + driftX;
            py[i] = ny[i] * h + driftY;
        }

        linePaint.setStrokeWidth(dp(0.75f));
        for (int i = 0; i < NODE_COUNT; i++) {
            for (int j = i + 1; j < NODE_COUNT; j++) {
                float dx = px[i] - px[j];
                float dy = py[i] - py[j];
                float d2 = dx * dx + dy * dy;
                float max = dp(125);
                if (d2 < max * max) {
                    float alpha = Math.max(0f, 1f - (float) Math.sqrt(d2) / max);
                    linePaint.setColor(Color.argb((int) (58 * alpha), 80, 220, 206));
                    canvas.drawLine(px[i], py[i], px[j], py[j], linePaint);
                }
            }
        }

        for (int i = 0; i < NODE_COUNT; i++) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(t * 2.2f + phase[i]);
            nodePaint.setColor(Color.argb((int) (120 + 110 * pulse), 89, 235, 216));
            float r = dp(1.5f + 1.2f * pulse);
            canvas.drawCircle(px[i], py[i], r, nodePaint);

            glowPaint.setColor(Color.argb((int) (34 * pulse), 25, 211, 174));
            canvas.drawCircle(px[i], py[i], r * 3.6f, glowPaint);
        }
    }

    private void drawRadar(Canvas canvas, float cx, float cy, float w, float t) {
        float base = Math.min(w, getHeight()) * 0.16f;
        for (int i = 0; i < 4; i++) {
            float progress = (t * 0.33f + i * 0.25f) % 1f;
            float radius = base + progress * Math.min(w, getHeight()) * 0.28f;
            int alpha = (int) (70 * (1f - progress));
            arcPaint.setColor(Color.argb(alpha, 25, 211, 174));
            canvas.drawCircle(cx, cy, radius, arcPaint);
        }

        float spin = (t * 82f) % 360f;
        arcPaint.setStrokeWidth(dp(2));
        arcPaint.setColor(Color.argb(145, 25, 211, 174));
        canvas.drawArc(cx - base, cy - base, cx + base, cy + base, spin, 78, false, arcPaint);

        float base2 = base * 1.38f;
        arcPaint.setStrokeWidth(dp(1.3f));
        arcPaint.setColor(Color.argb(105, 66, 165, 255));
        canvas.drawArc(cx - base2, cy - base2, cx + base2, cy + base2, -spin * 0.72f, 112, false, arcPaint);
    }

    private void drawScan(Canvas canvas, float w, float h, float t) {
        float p = (t * 0.34f) % 1f;
        float y = h * (0.06f + p * 0.88f);

        scanPaint.setShader(new android.graphics.LinearGradient(
                0, y, w, y,
                new int[]{Color.TRANSPARENT, Color.argb(28, 25, 211, 174), Color.argb(185, 25, 211, 174), Color.argb(28, 25, 211, 174), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawLine(0, y, w, y, scanPaint);
        scanPaint.setShader(null);
    }

    private void drawSignalBars(Canvas canvas, float w, float h, float t) {
        float left = w * 0.10f;
        float bottom = h * 0.89f;
        float barW = dp(4);
        float gap = dp(4);
        for (int i = 0; i < 5; i++) {
            float wave = 0.5f + 0.5f * (float) Math.sin(t * 4f - i * 0.65f);
            float height = dp(7 + i * 5) * (0.55f + 0.45f * wave);
            nodePaint.setColor(Color.argb(95 + i * 22, 25, 211, 174));
            canvas.drawRoundRect(left + i * (barW + gap), bottom - height, left + i * (barW + gap) + barW, bottom, dp(2), dp(2), nodePaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
