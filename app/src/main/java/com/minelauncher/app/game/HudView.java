package com.minelauncher.app.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Transparent overlay: crosshair and the virtual movement joystick.
 */
public class HudView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GameSurfaceView game;

    public HudView(Context context) {
        super(context);
    }

    public HudView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGame(GameSurfaceView g) {
        game = g;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float arm = Math.min(w, h) * 0.022f;
        float thick = Math.max(2f, arm * 0.2f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000000);
        canvas.drawRect(cx - thick / 2f - 1f, cy - arm - 1f, cx + thick / 2f + 1f, cy - thick, paint);
        canvas.drawRect(cx - thick / 2f - 1f, cy + thick, cx + thick / 2f + 1f, cy + arm + 1f, paint);
        canvas.drawRect(cx - arm - 1f, cy - thick / 2f - 1f, cx - thick, cy + thick / 2f + 1f, paint);
        canvas.drawRect(cx + thick, cy - thick / 2f - 1f, cx + arm + 1f, cy + thick / 2f + 1f, paint);

        paint.setColor(0xF0FFFFFF);
        canvas.drawRect(cx - thick / 2f, cy - arm, cx + thick / 2f, cy - thick, paint);
        canvas.drawRect(cx - thick / 2f, cy + thick, cx + thick / 2f, cy + arm, paint);
        canvas.drawRect(cx - arm, cy - thick / 2f, cx - thick, cy + thick / 2f, paint);
        canvas.drawRect(cx + thick, cy - thick / 2f, cx + arm, cy + thick / 2f, paint);

        if (game != null && game.isMoveActive()) {
            float r = game.getJoyRadius();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, r * 0.04f));
            paint.setColor(0x66FFFFFF);
            canvas.drawCircle(game.getMoveAnchorX(), game.getMoveAnchorY(), r, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33000000);
            canvas.drawCircle(game.getMoveKnobX(), game.getMoveKnobY(), r * 0.34f, paint);
            paint.setColor(0x99FFFFFF);
            canvas.drawCircle(game.getMoveKnobX(), game.getMoveKnobY(), r * 0.28f, paint);
            paint.setColor(0x55FFFFFF);
            canvas.drawCircle(game.getMoveKnobX(), game.getMoveKnobY(), r * 0.12f, paint);
        }
    }
}
