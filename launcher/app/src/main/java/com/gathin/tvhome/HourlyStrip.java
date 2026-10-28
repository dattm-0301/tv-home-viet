package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The 12-hour forecast strip shared by the Home panel and the screensaver. */
final class HourlyStrip {
    private static final int RAIN = 0xFF8EBBFF;

    private HourlyStrip() { }

    /** The 12 hourly columns: hour, icon, temperature, rain bar and %; the first hour at or over the threshold is
     *  boxed in blue, the current hour lightly. */
    static void fill(LinearLayout hourlyRow, WeatherClient.Now w, int rainPercent) {
        Context activity = hourlyRow.getContext();
        float density = activity.getResources().getDisplayMetrics().density;
        int accent = activity.getColor(R.color.home_accent);
        hourlyRow.removeAllViews();
        int firstRain = -1;
        for (int i = 0; i < w.hours.size(); i++) {
            if (w.hours.get(i).rain >= rainPercent) { firstRain = i; break; }
        }
        Typeface bold = UiFont.bold(activity);
        java.util.TimeZone zone = java.util.TimeZone.getTimeZone(HomeSettings.load(activity).timezone);
        for (int i = 0; i < w.hours.size(); i++) {
            WeatherClient.Hour h = w.hours.get(i);
            boolean rainy = h.rain >= rainPercent;
            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(0, dp(density, 5), 0, dp(density, 4));
            if (i == firstRain) col.setBackgroundResource(R.drawable.home_hour_rain_bg);
            else if (i == 0) col.setBackgroundResource(R.drawable.home_hour_now_bg);
            java.util.Calendar cal = java.util.Calendar.getInstance(zone);
            cal.setTimeInMillis(h.time);
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            TextView label = small(activity, hour + "h", R.dimen.home_hour_text, bold);
            label.setTextColor(i == firstRain ? 0xFFBFD8FF : hour == 0 ? accent : 0xD1FFFFFF);
            col.addView(label, new LinearLayout.LayoutParams(-2, -2));
            ImageView icon = new ImageView(activity);
            icon.setImageResource(HomeDashboard.icon(h.code, h.day));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(density, 13), dp(density, 13));
            ip.topMargin = dp(density, 3);
            col.addView(icon, ip);
            TextView temp = small(activity, h.temp + "°", R.dimen.home_hour_temp_text, bold);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.topMargin = dp(density, 2);
            col.addView(temp, tp);
            FrameLayout bar = new FrameLayout(activity);
            bar.setBackgroundResource(R.drawable.home_hour_bar_track);
            View fill = new View(activity);
            fill.setBackgroundResource(R.drawable.home_hour_bar_fill);
            int fillH = 0;
            bar.addView(fill, new FrameLayout.LayoutParams(-1, fillH, Gravity.BOTTOM));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(density, 5), 0, 1);
            bp.topMargin = dp(density, 3);
            col.addView(bar, bp);
            bar.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                int height = Math.round((bottom - top) * Math.max(0, h.rain) / 100f);
                if (fp.height != height) { fp.height = height; fill.setLayoutParams(fp); }
            });
            TextView pct = small(activity, h.rain < 0 ? "–" : h.rain + "%", R.dimen.home_hour_pct_text, bold);
            pct.setTextColor(rainy ? RAIN : 0xB3FFFFFF);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
            pp.topMargin = dp(density, 2);
            col.addView(pct, pp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
            cp.leftMargin = cp.rightMargin = dp(density, 0.5f);
            hourlyRow.addView(col, cp);
        }
    }

    private static TextView small(Context activity, String text, int size, Typeface face) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getResources().getDimension(size));
        t.setTypeface(face);
        t.setTextColor(0xFFFFFFFF);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private static int dp(float density, float v) { return Math.round(v * density); }
}
