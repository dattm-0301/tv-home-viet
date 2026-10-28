package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** The quick brightness panel under the dock (layout/home_brightness_panel.xml). */
final class BrightnessPanel {
    interface Listener { void onBrightnessChanged(); }

    private final Activity activity;
    private final Listener listener;
    private final View overlay, auto, autoSwitch, autoKnob;
    private final TextView value, hint;
    private final BrightnessSlider slider;
    private final TextView[] presets = new TextView[BrightnessControl.PRESETS.length];

    BrightnessPanel(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        overlay = activity.findViewById(R.id.brightnessOverlay);
        value = activity.findViewById(R.id.brightValue);
        hint = activity.findViewById(R.id.brightHint);
        auto = activity.findViewById(R.id.brightAuto);
        autoSwitch = activity.findViewById(R.id.brightAutoSwitch);
        autoKnob = activity.findViewById(R.id.brightAutoKnob);
        slider = activity.findViewById(R.id.brightSlider);
        LinearLayout row = activity.findViewById(R.id.brightPresets);
        float d = activity.getResources().getDisplayMetrics().density;
        for (int i = 0; i < presets.length; i++) {
            TextView t = new TextView(activity);
            t.setGravity(Gravity.CENTER);
            t.setFocusable(true);
            t.setBackgroundResource(R.drawable.home_preset_bg);
            t.setTextColor(activity.getColorStateList(R.color.home_preset_text));
            t.setTypeface(UiFont.bold(activity));
            t.setTextSize(10.5f);
            t.setIncludeFontPadding(false);
            SpannableStringBuilder b = new SpannableStringBuilder(BrightnessControl.PRESET_NAMES[i] + "\n");
            int from = b.length();
            b.append(BrightnessControl.PRESETS[i] + "%");
            b.setSpan(new AbsoluteSizeSpan(Math.round(8.5f * activity.getResources().getDisplayMetrics().scaledDensity)), from, b.length(), 0);
            t.setText(b);
            final int level = BrightnessControl.PRESETS[i];
            t.setOnClickListener(v -> apply(level));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) lp.leftMargin = Math.round(6 * d);
            row.addView(t, lp);
            presets[i] = t;
        }
        slider.setListener(this::apply);
        auto.setOnClickListener(v -> {
            BrightnessControl.setAutoDim(activity, !BrightnessControl.autoDim(activity));
            refresh();
        });
        overlay.setOnClickListener(v -> close());
    }

    boolean isOpen() { return overlay.getVisibility() == View.VISIBLE; }

    void open() {
        overlay.setVisibility(View.VISIBLE);
        refresh();
        slider.requestFocus();
    }

    void close() {
        overlay.setVisibility(View.GONE);
        View b = activity.findViewById(R.id.btnBrightness);
        if (b != null) b.requestFocus();
    }

    private void apply(int level) {
        if (BrightnessControl.set(activity, level)) {
            refresh();
            listener.onBrightnessChanged();
        } else {
            // No WRITE_SETTINGS: hand over to Xiaomi's own backlight popup.
            Toast.makeText(activity, "Không chỉnh trực tiếp được — mở bảng độ sáng của TV",
                    Toast.LENGTH_LONG).show();
            close();
            BrightnessControl.openVendorPanel(activity);
        }
    }

    private void refresh() {
        int v = BrightnessControl.get(activity);
        slider.setValue(Math.max(0, v));
        SpannableStringBuilder b = new SpannableStringBuilder(v < 0 ? "—" : String.valueOf(v));
        int from = b.length();
        b.append("%");
        b.setSpan(new AbsoluteSizeSpan(Math.round(26 * activity.getResources().getDisplayMetrics().scaledDensity)), from, b.length(), 0);
        b.setSpan(new ForegroundColorSpan(activity.getColor(R.color.home_accent)), from, b.length(), 0);
        value.setText(b);
        for (int i = 0; i < presets.length; i++) presets[i].setSelected(BrightnessControl.PRESETS[i] == v);
        boolean on = BrightnessControl.autoDim(activity);
        autoSwitch.setBackgroundTintList(ColorStateList.valueOf(on ? activity.getColor(R.color.home_accent) : 0x52FFFFFF));
        android.widget.FrameLayout.LayoutParams kp = (android.widget.FrameLayout.LayoutParams) autoKnob.getLayoutParams();
        kp.gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
        autoKnob.setLayoutParams(kp);
        hint.setText(BrightnessControl.canWrite(activity) ? "◀ ▶ Chỉnh   ·   ▼ Chọn mức sẵn   ·   Back Đóng"
                : "OK mở bảng độ sáng của TV   ·   Back Đóng");
    }

    /** All keys while the panel is open; focus stays inside it. */
    boolean dispatch(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (!down) close();
            return true;
        }
        View f = activity.getCurrentFocus();
        int preset = -1;
        for (int i = 0; i < presets.length; i++) if (presets[i] == f) preset = i;
        if (!down) return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ? true : false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (f == auto) slider.requestFocus();
                else if (f == slider) presets[nearestPreset()].requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (preset >= 0) slider.requestFocus();
                else if (f == slider) auto.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (preset >= 0) {
                    int next = preset + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    if (next >= 0 && next < presets.length) presets[next].requestFocus();
                    return true;
                }
                if (f == slider) {
                    if (!BrightnessControl.canWrite(activity)) {
                        apply(slider.getValue());
                        return true;
                    }
                    return false;   // the slider handles it
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (f == slider && !BrightnessControl.canWrite(activity)) {
                    apply(slider.getValue());
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private int nearestPreset() {
        int v = slider.getValue(), best = 0;
        for (int i = 1; i < presets.length; i++) {
            if (Math.abs(BrightnessControl.PRESETS[i] - v) < Math.abs(BrightnessControl.PRESETS[best] - v)) best = i;
        }
        return best;
    }
}
