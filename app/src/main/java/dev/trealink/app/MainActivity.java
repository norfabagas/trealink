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
    private boolean wifi, requesting;
    private TextView status, connection, session;
    private Button start, usb, wireless;
    private final Runnable refresh = new Runnable() { public void run() { updateState(); handler.postDelayed(this, 1000); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        wifi = preferences.getBoolean("wifi", false);
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
        TextView note = text("Independent T-Link-inspired implementation.\nView-only video · No audio · Receiver pairing not supported", 12, MUTED); note.setGravity(Gravity.CENTER); note.setPadding(0, dp(12), 0, 0); body.addView(note);
        setContentView(page); updateState();
    }

    private void chooseMode(boolean value) { wifi = value; preferences.edit().putBoolean("wifi", value).apply(); updateState(); }
    private void requestProjection() {
        requesting = true; updateState();
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        startActivityForResult(manager.createScreenCaptureIntent(), 42);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 42) {
            requesting = false;
            if (result == RESULT_OK && data != null) {
                Intent service = new Intent(this, MirrorService.class).putExtra("token", data).putExtra("wifi", wifi);
                startForegroundService(service);
            } else MirrorService.status = "Screen sharing cancelled. Tap Start to try again.";
            updateState();
        }
    }
    private void updateState() {
        if (start == null) return;
        boolean active = MirrorService.running;
        start.setEnabled(!requesting); start.setText(active ? "Stop mirroring" : requesting ? "Waiting for permission…" : "Start mirroring");
        usb.setEnabled(!active && !requesting); wireless.setEnabled(!active && !requesting);
        usb.setTextColor(wifi ? MUTED : WINE); wireless.setTextColor(wifi ? WINE : MUTED);
        status.setText(MirrorService.status);
        boolean network = active ? MirrorService.mode.equals("Wi-Fi") : wifi;
        connection.setText(network ? "Viewer address\n" + addresses() + "\nVNC 3.8 · Same network" : "USB: 127.0.0.1:5901\nadb forward tcp:5901 tcp:5900");
        session.setText(active ? "Session password: " + MirrorService.password + "\n" + MirrorService.frameCount + " frames captured · view only" : "A new 8-character password appears when sharing starts.");
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
        new AlertDialog.Builder(this).setTitle("Trealink").setItems(new String[]{"Connection guide", "Wi-Fi settings", "About Trealink"}, (d, choice) -> {
            if (choice == 0) guide();
            else if (choice == 1) launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
            else new AlertDialog.Builder(this).setTitle("Trealink 0.1.0").setMessage("Inspired by T-Link 1.3.12, analyzed with REA. Independently written screen capture and RFB 3.8 server.\n\nNo original binaries are included. JVC/Kenwood receiver pairing, audio and remote input are not implemented. Folder operations are not part of this mirroring app.").setPositiveButton("OK", null).show();
        }).show();
    }
    private void guide() {
        new AlertDialog.Builder(this).setTitle("Connect your display").setMessage("1. Choose USB (ADB) or Wi-Fi, then tap Start mirroring. Approve Android's screen-sharing prompt.\n\n2. USB: enable USB debugging on your phone, connect it to your computer, and run adb forward tcp:5901 tcp:5900. Connect a VNC viewer to 127.0.0.1:5901.\n\nWi-Fi: put both devices on the same trusted network. Connect a VNC viewer to the address shown here on port 5900. Standard VNC traffic is not encrypted.\n\n3. Enter the session password from Trealink. Use RFB 3.8 / VNC authentication. Video is view only, up to 5 frames/s. Android protects secure video and password screens.\n\n4. Tap Stop mirroring or Stop in the sharing notification. Rotation uses a fixed-size canvas and may add letterboxing.\n\nThis version does not pair with JVC/Kenwood receivers or carry audio. USB here uses ADB forwarding, not the receiver's USB accessory protocol.")
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
