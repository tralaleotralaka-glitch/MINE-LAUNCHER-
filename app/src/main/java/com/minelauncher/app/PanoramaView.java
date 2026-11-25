package com.minelauncher.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Pixel-art "panorama" background: sky, sun, clouds, blocky hills and a grass/dirt field.
 * Everything is drawn procedurally, no image assets required.
 */
public class PanoramaView extends View {

    private final Paint paint = new Paint();

    public PanoramaView(Context context) {
        super(context);
    }

    public PanoramaView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private static int hash(int v) {
        int h = v * 374761393 + 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return Math.abs(h ^ (h >>> 16));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.85f,
                0xFF3F79BE, 0xFFBDE0F8, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // sun
        float sun = h * 0.075f;
        paint.setColor(0x33FFF7B0);
        canvas.drawRect(w * 0.72f, h * 0.08f, w * 0.72f + sun * 2f, h * 0.08f + sun * 2f, paint);
        paint.setColor(0xFFFFF8C0);
        canvas.drawRect(w * 0.755f, h * 0.115f, w * 0.755f + sun, h * 0.115f + sun, paint);

        // clouds
        drawCloud(canvas, w * 0.10f, h * 0.15f, w * 0.075f, h);
        drawCloud(canvas, w * 0.46f, h * 0.07f, w * 0.055f, h);
        drawCloud(canvas, w * 0.80f, h * 0.29f, w * 0.060f, h);

        // hills
        drawHills(canvas, w, h, 0.50f, 0xFF38603B, 13, 1.4f);
        drawHills(canvas, w, h, 0.575f, 0xFF477847, 9, 3.1f);
        drawHills(canvas, w, h, 0.635f, 0xFF54914E, 6, 5.7f);

        // ground
        final float groundY = h * 0.70f;
        final int bs = Math.max(4, Math.round(h * 0.020f));
        paint.setColor(0xFF63A83A);
        canvas.drawRect(0, groundY, w, groundY + bs, paint);
        for (int x = 0; x < w; x += bs) {
            int r = hash(x * 7 + 13);
            if (r % 3 == 0) {
                paint.setColor(0xFF7DC44C);
                canvas.drawRect(x, groundY - bs * 0.5f, x + bs, groundY, paint);
            } else if (r % 7 == 0) {
                paint.setColor(0xFF4E8A2C);
                canvas.drawRect(x, groundY + bs, x + bs, groundY + bs * 2f, paint);
            }
        }
        for (int x = 0; x < w; x += bs) {
            for (float y = groundY + bs; y < h; y += bs) {
                int v = hash(x * 31 + ((int) (y / bs)) * 17);
                paint.setColor(v % 6 == 0 ? 0xFF774E2C : (v % 11 == 0 ? 0xFF96663A : 0xFF8A5C33));
                canvas.drawRect(x, y, x + bs, y + bs, paint);
            }
        }

        drawTree(canvas, w * 0.14f, groundY, bs);
        drawTree(canvas, w * 0.68f, groundY, bs);
        drawTree(canvas, w * 0.90f, groundY, bs);

        // readability gradient
        paint.setShader(new LinearGradient(0, 0, 0, h, 0x58000000, 0xE0000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * 0.5f, h * 0.42f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x99000000}, new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private void drawCloud(Canvas canvas, float x, float y, float size, int h) {
        paint.setColor(0xF2FFFFFF);
        canvas.drawRect(x, y, x + size * 2.4f, y + size, paint);
        canvas.drawRect(x + size * 0.6f, y - size * 0.7f, x + size * 1.7f, y, paint);
        canvas.drawRect(x + size * 1.3f, y + size, x + size * 2.0f, y + size * 1.6f, paint);
        paint.setColor(0x33BFD9F2);
        canvas.drawRect(x, y + size * 0.75f, x + size * 2.4f, y + size, paint);
    }

    private void drawHills(Canvas canvas, int w, int h, float baseFrac, int color, int cols, float phase) {
        final float base = h * baseFrac;
        final float cw = w / (float) cols;
        paint.setColor(color);
        for (int i = 0; i < cols; i++) {
            float t = i / (float) cols;
            float n = (float) (Math.sin(t * 7.3f + phase) * 0.5 + Math.sin(t * 3.1f + phase * 2.2f) * 0.5);
            float peak = Math.abs(n) * h * 0.13f;
            canvas.drawRect(i * cw, base - peak, (i + 1) * cw, base + h * 0.06f, paint);
        }
    }

    private void drawTree(Canvas canvas, float x, float groundY, int bs) {
        final float bw = bs * 1.15f;
        paint.setColor(0xFF5B3B1B);
        canvas.drawRect(x, groundY - bw * 5f, x + bw, groundY, paint);
        paint.setColor(0xFF2F6B26);
        canvas.drawRect(x - bw * 2f, groundY - bw * 7f, x + bw * 3f, groundY - bw * 4f, paint);
        paint.setColor(0xFF3E8A2F);
        canvas.drawRect(x - bw * 1.2f, groundY - bw * 8.4f, x + bw * 2.2f, groundY - bw * 6.6f, paint);
        paint.setColor(0xFF245021);
        canvas.drawRect(x - bw * 0.4f, groundY - bw * 5.2f, x + bw * 0.6f, groundY - bw * 4.4f, paint);
    }
}
