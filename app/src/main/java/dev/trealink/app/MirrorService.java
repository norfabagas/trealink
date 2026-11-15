package dev.trealink.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicReference;

public final class MirrorService extends Service {
    public static volatile boolean running;
    public static volatile String status = "Ready to connect", password = "", mode = "USB";
    public static volatile int frameCount;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread captureThread;
    private RfbServer server;
    private long lastFrame;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        Intent token = intent.getParcelableExtra("token");
        if (token == null) { status = "Screen-sharing permission was not granted"; stopSelf(); return START_NOT_STICKY; }
        try {
            getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("mirror", "Screen sharing", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MirrorService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, "mirror").setSmallIcon(android.R.drawable.ic_menu_share)
                    .setContentTitle("Trealink is sharing your screen").setContentText("Open Trealink for your viewer address. Tap Stop to end.")
                    .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause, "Stop", stop).build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(1, notification);
            mode = intent.getBooleanExtra("wifi", false) ? "Wi-Fi" : "USB";
            password = newPassword(); frameCount = 0;
            captureThread = new HandlerThread("trealink-capture"); captureThread.start();
            Handler handler = new Handler(captureThread.getLooper());
            DisplayMetrics metrics = new DisplayMetrics();
            getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
            float scale = Math.min(1f, Math.min(720f / metrics.widthPixels, 1280f / metrics.heightPixels));
            int width = Math.max(2, ((int)(metrics.widthPixels * scale)) & ~1);
            int height = Math.max(2, ((int)(metrics.heightPixels * scale)) & ~1);
            AtomicReference<RfbServer.Frame> frames = new AtomicReference<>(new RfbServer.Frame(width, height, new int[width * height]));
            server = new RfbServer(mode.equals("Wi-Fi") ? "0.0.0.0" : "127.0.0.1", 5900, password, frames, value -> { if (running) status = value; });
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK, token);
            projection.registerCallback(new MediaProjection.Callback() { @Override public void onStop() { stopSelf(); } }, new Handler(getMainLooper()));
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(source -> {
                try (Image image = source.acquireLatestImage()) {
                    if (image == null || !running || System.currentTimeMillis() - lastFrame < 180) return;
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixel = plane.getPixelStride(), stride = plane.getRowStride();
                    int[] rgb = new int[width * height];
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int offset = y * stride + x * pixel;
                        rgb[y * width + x] = ((buffer.get(offset) & 255) << 16) | ((buffer.get(offset + 1) & 255) << 8) | (buffer.get(offset + 2) & 255);
                    }
                    frames.set(new RfbServer.Frame(width, height, rgb)); frameCount++;
                    lastFrame = System.currentTimeMillis();
                } catch (RuntimeException e) { Log.e("Trealink", "Capture failed", e); status = "Capture failed. Start a new session."; stopSelf(); }
            }, handler);
            display = projection.createVirtualDisplay("Trealink", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            running = true; status = "Waiting for viewer"; server.start();
        } catch (IOException | RuntimeException e) {
            Log.e("Trealink", "Cannot start sharing", e);
            status = "Cannot start: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private static String newPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom(); StringBuilder value = new StringBuilder();
        for (int i = 0; i < 8; i++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return value.toString();
    }

    @Override public void onDestroy() {
        running = false; password = "";
        if (server != null) try { server.close(); } catch (IOException ignored) { }
        if (display != null) display.release();
        if (projection != null) projection.stop();
        // Serialize reader disposal after any in-flight buffer conversion on its own thread.
        if (captureThread != null) {
            new Handler(captureThread.getLooper()).post(() -> { if (reader != null) reader.close(); captureThread.quitSafely(); });
        } else if (reader != null) reader.close();
        if (!status.startsWith("Cannot start") && !status.startsWith("Capture failed")) status = "Ready to connect";
        stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
