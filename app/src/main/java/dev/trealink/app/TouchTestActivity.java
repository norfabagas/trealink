package dev.trealink.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Disposable in-memory controls for exercising real Android touch dispatch. */
public final class TouchTestActivity extends Activity {
    private int taps, holds;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(16, 36, 16, 24);
        TextView finger = new TextView(this); finger.setText("Finger: up"); page.addView(finger);
        Button tap = new Button(this); tap.setText("Tap count: 0"); tap.setAllCaps(false);
        tap.setOnClickListener(v -> tap.setText("Tap count: " + (++taps))); page.addView(tap);
        Button hold = new Button(this); hold.setText("Hold count: 0"); hold.setAllCaps(false);
        hold.setOnLongClickListener(v -> { hold.setText("Hold count: " + (++holds)); return true; }); page.addView(hold);
        android.view.View.OnTouchListener phases = (v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) finger.setText("Finger: down");
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) finger.setText("Finger: up");
            return false;
        };
        tap.setOnTouchListener(phases); hold.setOnTouchListener(phases);
        TextView value = new TextView(this); value.setText("Slider: 0"); page.addView(value);
        SeekBar slider = new SeekBar(this); slider.setMax(100); slider.setContentDescription("Drag test");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean user) { value.setText("Slider: " + progress); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        }); page.addView(slider);
        ScrollView scroll = new ScrollView(this); LinearLayout items = new LinearLayout(this); items.setOrientation(LinearLayout.VERTICAL);
        for (int i = 1; i <= 30; i++) { TextView item = new TextView(this); item.setText("Scroll item " + i); item.setTextSize(20); item.setPadding(8, 18, 8, 18); items.addView(item); }
        scroll.addView(items); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(page);
    }
}
