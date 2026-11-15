package dev.trealink.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.SystemClock;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayDeque;

/** Explicitly enabled, session-scoped single-finger input. No window-content access. */
public final class RemoteTouchService extends AccessibilityService {
    private static volatile RemoteTouchService instance;
    public static volatile String lastResult = "";
    private final Handler handler = new Handler();
    private final ArrayDeque<Sample> queue = new ArrayDeque<>();
    private GestureDescription.StrokeDescription stroke;
    private boolean busy, inputDown;
    private float lastX, lastY;
    private long owner, dispatchedAt;
    private int generation;
    private final Runnable releaseTimeout = () -> releaseOwned(owner);

    public static boolean enabled(Context context) { return context.getSharedPreferences("settings", MODE_PRIVATE).getBoolean("touch_control", false); }
    public static boolean connected() { return instance != null; }
    public static void pointer(long session, int buttons, int x, int y) {
        RemoteTouchService service = instance;
        if (service != null) service.accept(session, buttons, x, y);
    }
    public static void release(long session) { RemoteTouchService service = instance; if (service != null) service.releaseOwned(session); }
    public static String state(Context context) {
        if (!enabled(context)) return "Touch control off";
        if (!connected()) return "Enable Trealink Touch Control in Accessibility";
        if (MirrorService.running && !MirrorService.extending && !MirrorService.wholeDisplayCapture) return "Restart sharing with the entire screen for touch control";
        if (lastResult.startsWith("Android rejected")) return lastResult;
        return "Direct touch ready · single finger";
    }
    private boolean allowed(long session) {
        return instance == this && enabled(this) && MirrorService.running && MirrorService.captureActive
                && !MirrorService.extending && MirrorService.wholeDisplayCapture && MirrorService.controlSessionId == session
                && !getSystemService(KeyguardManager.class).isKeyguardLocked() && getSystemService(PowerManager.class).isInteractive();
    }
    private void accept(long session, int buttons, int x, int y) {
        if (!allowed(session)) { releaseOwned(owner); return; }
        DisplayMetrics phone = new DisplayMetrics();
        getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(phone);
        if (phone.widthPixels != MirrorService.capturedSourceWidth || phone.heightPixels != MirrorService.capturedSourceHeight) {
            releaseOwned(owner); lastResult = "Touch paused while capture size changes"; return;
        }
        float[] point = TouchMapper.map(x, y, MirrorService.frameWidth, MirrorService.frameHeight, phone.widthPixels, phone.heightPixels);
        boolean pressed = (buttons & 1) != 0;
        if (point == null) {
            if (!pressed) releaseOwned(owner);
            return;
        }
        if (owner != session) { if (busy || stroke != null) return; owner = session; }
        int kind = pressed ? (inputDown ? 1 : 0) : 2; // DOWN, MOVE, UP
        if (!pressed && !inputDown) return; // Hover never injects input or moves a cursor.
        inputDown = pressed;
        if (kind == 1 && !queue.isEmpty() && queue.peekLast().kind == 1) queue.removeLast();
        if (queue.size() >= 128) { releaseOwned(owner); lastResult = "Input queue reset"; return; }
        queue.addLast(new Sample(kind, point[0], point[1]));
        handler.removeCallbacks(releaseTimeout);
        if (pressed) handler.postDelayed(releaseTimeout, 10000);
        pump();
    }
    private void releaseOwned(long session) {
        if (owner != session) return;
        queue.clear(); inputDown = false; handler.removeCallbacks(releaseTimeout);
        if (stroke != null) queue.add(new Sample(2, lastX, lastY));
        pump();
    }
    private void pump() {
        if (busy) return;
        if (!allowed(owner)) {
            queue.clear(); inputDown = false;
            if (stroke != null) queue.add(new Sample(2, lastX, lastY));
        }
        while (!queue.isEmpty()) {
            Sample sample = queue.removeFirst();
            if (sample.kind != 0 && stroke == null) continue;
            Path path = new Path();
            if (sample.kind == 0) path.moveTo(sample.x, sample.y);
            else { path.moveTo(lastX, lastY); path.lineTo(sample.x, sample.y); }
            boolean continuing = sample.kind != 2;
            long duration = sample.kind == 0 ? 1 : Math.max(1, Math.min(40, SystemClock.uptimeMillis() - dispatchedAt));
            GestureDescription.StrokeDescription next = sample.kind == 0
                    ? new GestureDescription.StrokeDescription(path, 0, duration, true)
                    : stroke.continueStroke(path, 0, duration, continuing);
            final int callbackGeneration = generation;
            busy = true; stroke = next; lastX = sample.x; lastY = sample.y; dispatchedAt = SystemClock.uptimeMillis();
            boolean accepted = dispatchGesture(new GestureDescription.Builder().addStroke(next).build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) {
                    if (callbackGeneration != generation) return;
                    busy = false;
                    if (!continuing) { stroke = null; lastResult = "Touch completed"; }
                    pump();
                }
                @Override public void onCancelled(GestureDescription description) {
                    if (callbackGeneration != generation) return;
                    clear(); lastResult = "Touch cancelled by Android";
                }
            }, handler);
            if (!accepted) { clear(); lastResult = "Android rejected touch; check Accessibility permission"; }
            return;
        }
    }
    private void clear() { generation++; busy = false; stroke = null; inputDown = false; queue.clear(); handler.removeCallbacks(releaseTimeout); }
    @Override protected void onServiceConnected() { instance = this; lastResult = "Accessibility connected"; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { releaseOwned(owner); }
    @Override public boolean onUnbind(Intent intent) { releaseOwned(owner); instance = null; return super.onUnbind(intent); }
    @Override public void onDestroy() { clear(); if (instance == this) instance = null; super.onDestroy(); }
    private static final class Sample {
        final int kind; final float x, y;
        Sample(int kind, float x, float y) { this.kind = kind; this.x = x; this.y = y; }
    }
}
