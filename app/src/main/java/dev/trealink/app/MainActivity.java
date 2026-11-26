package dev.trealink.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class MainActivity extends Activity {
    private static final int WINE = Color.rgb(118, 15, 37), INK = Color.rgb(37, 30, 33), MUTED = Color.rgb(114, 102, 106);
    private final Handler handler = new Handler();
    private SharedPreferences preferences;
    private boolean wifi, requesting, extend;
    private TextView status, connection, session;
    private Button start, usb, wireless;
    private final Runnable refresh = new Runnable() { public void run() { updateState(); handler.postDelayed(this, 1000); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        wifi = preferences.getBoolean("wifi", false);
        extend = preferences.getBoolean("extend", false);
        LinearLayout page = column(); page.setBackgroundColor(Color.rgb(250, 247, 248));
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20), dp(8), dp(12), dp(8)); header.setBackgroundColor(WINE);
        TextView brand = text("Trealink", 24, Color.WHITE); brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1)); brand.setGravity(Gravity.CENTER_VERTICAL);
        Button menu = button("Menu"); menu.setTextColor(Color.WHITE); menu.setBackgroundColor(Color.TRANSPARENT); menu.setOnClickListener(v -> menu()); header.addView(menu);
        page.addView(header);
        ScrollView scroll = new ScrollView(this); LinearLayout body = column(); body.setPadding(dp(22), dp(16), dp(22), dp(24)); scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView eyebrow = text("PHONE → DISPLAY", 11, WINE); eyebrow.setLetterSpacing(0.16f); body.addView(eyebrow);
        TextView title = text("Your screen.\nConnected.", 28, INK); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, dp(10), 0, dp(8)); body.addView(title);
        body.addView(text("Mirror your Android screen to a VNC viewer.", 15, MUTED));
        View illustration = new LinkDrawing(); body.addView(illustration, new LinearLayout.LayoutParams(-1, dp(108)));
        LinearLayout modes = new LinearLayout(this); usb = button("USB (ADB)"); wireless = button("Wi-Fi");
        usb.setOnClickListener(v -> chooseMode(false)); wireless.setOnClickListener(v -> chooseMode(true));
        modes.addView(usb, new LinearLayout.LayoutParams(0, dp(48), 1)); modes.addView(wireless, new LinearLayout.LayoutParams(0, dp(48), 1)); body.addView(modes);
        LinearLayout card = column(); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackground(round(Color.WHITE));
        LinearLayout.LayoutParams cm = new LinearLayout.LayoutParams(-1, -2); cm.topMargin = dp(16); body.addView(card, cm);
        status = text("Ready to connect", 20, INK); status.setTypeface(null, Typeface.BOLD); card.addView(status);
        connection = text("", 14, MUTED); connection.setTextIsSelectable(true); connection.setPadding(0, dp(10), 0, dp(10)); card.addView(connection);
        session = text("", 16, WINE); session.setTextIsSelectable(true); card.addView(session);
        start = button("Start mirroring"); start.setTextColor(Color.WHITE); start.setBackground(round(WINE));
        LinearLayout.LayoutParams sm = new LinearLayout.LayoutParams(-1, dp(54)); sm.topMargin = dp(16); card.addView(start, sm);
        start.setOnClickListener(v -> {
            if (MirrorService.running) { stopService(new Intent(this, MirrorService.class)); updateState(); }
            else requestProjection();
        });
        TextView launches = text("YOUR SHORTCUTS", 11, MUTED); launches.setLetterSpacing(0.12f); launches.setPadding(0, dp(26), 0, dp(8)); body.addView(launches);
        LinearLayout shortcuts = new LinearLayout(this);
        addShortcut(shortcuts, "Maps", new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")));
        addShortcut(shortcuts, "Music", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC));
        addShortcut(shortcuts, "Web", new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
        body.addView(shortcuts);
        Button help = button("Connection guide"); help.setOnClickListener(v -> guide()); body.addView(help);
        TextView note = text("Independent T-Link-inspired implementation.\nNo Android Auto host · No audio · Receiver pairing not supported", 12, MUTED); note.setGravity(Gravity.CENTER); note.setPadding(0, dp(12), 0, 0); body.addView(note);
        setContentView(page); updateState();
    }

    private void chooseMode(boolean value) { wifi = value; preferences.edit().putBoolean("wifi", value).apply(); updateState(); }
    private void requestProjection() {
        if (extend) {
            startForegroundService(new Intent(this, MirrorService.class).putExtra("extend", true).putExtra("wifi", wifi));
            return;
        }
        requesting = true; updateState();
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        startActivityForResult(Build.VERSION.SDK_INT >= 34 && RemoteTouchService.enabled(this)
                ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) : manager.createScreenCaptureIntent(), 42);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 42) {
            requesting = false;
            if (result == RESULT_OK && data != null) {
                Intent service = new Intent(this, MirrorService.class).putExtra("token", data).putExtra("wifi", wifi).putExtra("wholeDisplay", RemoteTouchService.enabled(this));
                startForegroundService(service);
            } else MirrorService.status = "Screen sharing cancelled. Tap Start to try again.";
            updateState();
        }
    }
    private void updateState() {
        if (start == null) return;
        boolean active = MirrorService.running;
        boolean independent = active ? MirrorService.extending : extend;
        start.setEnabled(!requesting); start.setText(active ? (independent ? "Stop extend test" : "Stop mirroring") : requesting ? "Waiting for permission…" : independent ? "Start extend test" : "Start mirroring");
        usb.setEnabled(!active && !requesting); wireless.setEnabled(!active && !requesting);
        usb.setTextColor(wifi ? MUTED : WINE); wireless.setTextColor(wifi ? WINE : MUTED);
        status.setText(MirrorService.status);
        boolean network = active ? MirrorService.mode.equals("Wi-Fi") : wifi;
        connection.setText(network ? "Viewer address\n" + addresses() + "\nVNC 3.8 · Same network" : "USB: 127.0.0.1:5901\nadb forward tcp:5901 tcp:5900");
        session.setText(active ? "Session password: " + MirrorService.password + "\n" + MirrorService.frameCount + " frames · " + (independent ? "test taps: " + MirrorService.carTapCount : RemoteTouchService.state(this))
                : independent ? "Independent test surface only.\nAndroid Auto and car pairing are not implemented." : "A new 8-character password appears when sharing starts.");
    }
    private String addresses() {
        java.util.Set<String> addresses = new java.util.LinkedHashSet<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (java.net.InetAddress ip : Collections.list(nic.getInetAddresses())) if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) {
                    addresses.add(ip.getHostAddress() + ":5900");
                }
            }
        } catch (java.net.SocketException ignored) { }
        return addresses.isEmpty() ? "No network address · connect to Wi-Fi first" : android.text.TextUtils.join("\n", addresses);
    }
    private void menu() {
        new AlertDialog.Builder(this).setTitle("Trealink").setItems(new String[]{"Connection guide", "Wi-Fi settings", "Output mode", "Touch control", "Touch test", "About Trealink"}, (d, choice) -> {
            if (choice == 0) guide();
            else if (choice == 1) launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
            else if (choice == 2) outputMode();
            else if (choice == 3) touchControl();
            else if (choice == 4) startActivity(new Intent(this, TouchTestActivity.class));
            else new AlertDialog.Builder(this).setTitle("Trealink 0.3.1").setMessage("Inspired by T-Link 1.3.12, analyzed with REA. Phone mirroring with optional Accessibility-based direct touch, plus an independent display diagnostic.\n\nJVC/Kenwood receiver pairing, Android Auto hosting and audio remain unimplemented. Touch control requires explicit setup and full-screen capture.").setPositiveButton("OK", null).show();
        }).show();
    }
    private void touchControl() {
        new AlertDialog.Builder(this).setTitle("Direct touch control")
                .setMessage(RemoteTouchService.state(this) + "\n\nAllow the authenticated viewer to tap, drag and hold on your mirrored phone. Android requires you to enable Trealink Touch Control in Accessibility. Enable this before starting a full-screen mirroring session.\n\nUse your viewer's direct/absolute touch mode. The Kenwood USB connection is not implemented yet.")
                .setPositiveButton("Enable direct touch", (d, w) -> {
                    preferences.edit().putBoolean("touch_control", true).apply();
                    if (!RemoteTouchService.connected()) launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    updateState();
                }).setNeutralButton("Turn off", (d, w) -> {
                    preferences.edit().putBoolean("touch_control", false).apply();
                    RemoteTouchService.release(MirrorService.controlSessionId); updateState();
                }).setNegativeButton("Cancel", null).show();
    }
    private void outputMode() {
        if (MirrorService.running || requesting) {
            new AlertDialog.Builder(this).setMessage("Stop the current session before changing output mode.").setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Output mode")
                .setSingleChoiceItems(new String[]{"Phone screen mirroring", "Independent display test (no Android Auto)"}, extend ? 1 : 0, (d, item) -> {
                    extend = item == 1; preferences.edit().putBoolean("extend", extend).apply(); updateState(); d.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }
    private void guide() {
        new AlertDialog.Builder(this).setTitle("Connect your display").setMessage("1. For direct touch, open Menu → Touch control, enable it, and follow Android Accessibility setup. Then choose phone screen mirroring and start a new full-screen sharing session.\n\n2. USB (ADB): enable USB debugging, connect your phone to a computer, and run adb forward tcp:5901 tcp:5900. Connect a VNC viewer to 127.0.0.1:5901.\n\nWi-Fi: put both devices on the same trusted network. Connect a VNC viewer to the displayed address on port 5900. Standard VNC traffic is not encrypted.\n\n3. Enter Trealink's session password. Use RFB 3.8 / VNC authentication. Select direct/absolute touch mode in the viewer. Taps, holds and drags use one finger; pinch and remote typing are unavailable. Without touch setup, mirroring is view only.\n\n4. Turn touch control off locally or stop sharing to end remote control. The phone must be unlocked. Rotation may letterbox the picture; black bars are not touch targets.\n\nThis version does not pair with JVC/Kenwood receivers or carry audio. USB here uses ADB forwarding, not the receiver's T-Link USB protocol.")
                .setPositiveButton("Got it", null).show();
    }
    private void addShortcut(LinearLayout row, String label, Intent intent) { Button b = button(label); b.setOnClickListener(v -> launch(intent)); row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1)); }
    private void launch(Intent intent) { try { startActivity(intent); } catch (ActivityNotFoundException e) { new AlertDialog.Builder(this).setMessage("No app is installed to open this shortcut.").setPositiveButton("OK", null).show(); } }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setMinWidth(0); return b; }
    private GradientDrawable round(int color) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(14)); return g; }
    private final class LinkDrawing extends View {
        final Paint paint = new Paint(3);
        LinkDrawing() { super(MainActivity.this); setContentDescription("A phone connected to a larger display"); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float s = getWidth() / 320f; canvas.save(); canvas.scale(s, s);
            paint.setColor(Color.rgb(239, 225, 230)); canvas.drawCircle(165, 72, 62, paint);
            paint.setColor(WINE); canvas.drawRoundRect(40, 32, 85, 110, 7, 7, paint); canvas.drawRoundRect(150, 34, 280, 108, 7, 7, paint);
            paint.setColor(Color.WHITE); canvas.drawRoundRect(45, 41, 80, 98, 2, 2, paint); canvas.drawRoundRect(157, 41, 273, 98, 2, 2, paint);
            paint.setColor(WINE); canvas.drawRoundRect(196, 108, 234, 113, 2, 2, paint); canvas.drawRect(211, 106, 220, 118, paint); canvas.drawRoundRect(190, 117, 241, 121, 2, 2, paint);
            paint.setStrokeWidth(3); canvas.drawLine(98, 73, 135, 73, paint); canvas.drawLine(128, 67, 135, 73, paint); canvas.drawLine(128, 79, 135, 73, paint);
            paint.setColor(Color.rgb(215, 180, 191)); canvas.drawRect(52, 49, 73, 66, paint); canvas.drawRect(165, 49, 210, 83, paint); canvas.drawRect(216, 49, 264, 62, paint); canvas.drawRect(216, 68, 250, 75, paint);
            canvas.restore();
        }
    }
}
