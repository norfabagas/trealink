package dev.trealink.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.view.View;

/** App-owned test content. Never reads or sends input to another app or display. */
final class ExtensionTestView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private int ticks;
    private boolean downInside;
    private final Runnable tick = new Runnable() {
        public void run() { ticks++; invalidate(); handler.postDelayed(this, 1000); }
    };
    ExtensionTestView(Context context) { super(context); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick); }
    @Override protected void onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow(); }
    void resetPointer() { downInside = false; }
    void pointer(int mask, int x, int y) {
        boolean inside = x >= 40 && x < 760 && y >= 240 && y < 330;
        if ((mask & 1) != 0) { downInside = inside; return; }
        if (downInside && inside) { MirrorService.carTapCount++; invalidate(); }
        downInside = false;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save(); canvas.scale(getWidth() / 800f, getHeight() / 480f);
        canvas.drawColor(Color.rgb(18, 29, 38));
        paint.setColor(Color.rgb(106, 224, 196)); paint.setTextSize(20);
        canvas.drawText("TREALINK / INDEPENDENT DISPLAY TEST", 40, 55, paint);
        paint.setColor(Color.WHITE); paint.setTextSize(44);
        canvas.drawText("Your phone screen is separate.", 40, 132, paint);
        paint.setTextSize(23); paint.setColor(Color.LTGRAY);
        canvas.drawText("Test surface only. Android Auto is not connected.", 40, 185, paint);
        paint.setColor(Color.rgb(106, 224, 196));
        canvas.drawRoundRect(40, 240, 760, 330, 16, 16, paint);
        paint.setColor(Color.rgb(18, 29, 38)); paint.setTextSize(32);
        canvas.drawText("Tap test — car taps: " + MirrorService.carTapCount, 65, 296, paint);
        paint.setTextSize(22); paint.setColor(Color.LTGRAY);
        canvas.drawText("Independent heartbeat: " + ticks, 40, 390, paint);
        canvas.drawText("No phone capture · No system input injection", 40, 435, paint);
        canvas.restore();
    }
}
