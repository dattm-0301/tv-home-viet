package com.gathin.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The Home widget row (layout/home_dashboard.xml) and the top-left corner: greeting with air quality and sunrise,
 * clock and calendar with holiday chips on the photo, and the glass panel with the weather, a 12-hour forecast and
 * the gold and fuel prices. Prices come from PriceClient and the weather from WeatherClient, both fetched off the UI thread and cached.
 */
final class HomeDashboard {
    private static final String TAG = "HomeDashboard";
    private static final Locale VI = new Locale("vi", "VN");
    private static final String[] WEEKDAYS = {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"};
    private static final int RAIN = 0xFF8EBBFF;

    private final Activity activity;
    private final HomeSettings settings;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Holidays holidays;
    private final int accent, fresh, stale;
    private final float density;

    private final TextView weekday, date, lunar, chipTodayText, chipNextName, chipNextCountdown;
    private final View chipToday, chipNext;
    private final TextView greeting, aqiValue, aqiLabel, sunriseText, compactDate, compactWeather;
    private final View aqiPill, aqiDot, sunrisePill;
    private final ImageView weatherIcon, rainIcon;
    private final TextView weatherTemp, weatherPlace, weatherCondition, rainText, weatherRange, weatherStamp;
    private final View weatherDot;
    private final LinearLayout hourlyRow;
    private final PriceClient.Block gold, fuel;
    private boolean loading;
    private LocalDate shownDay, shownLunarDay;
    private String lunarShort = "";

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateCalendar();
            updateGreeting();
        }
    };

    HomeDashboard(Activity activity, HomeSettings settings) {
        this.activity = activity;
        this.settings = settings;
        prefs = activity.getSharedPreferences("market_prices_v2", Context.MODE_PRIVATE);
        holidays = new Holidays(activity);
        accent = activity.getColor(R.color.home_accent);
        fresh = activity.getColor(R.color.home_fresh);
        stale = activity.getColor(R.color.home_stale);
        density = activity.getResources().getDisplayMetrics().density;

        weekday = activity.findViewById(R.id.greeting);
        date = activity.findViewById(R.id.homeDate);
        lunar = activity.findViewById(R.id.homeLunarDate);
        chipToday = activity.findViewById(R.id.chipToday);
        chipTodayText = activity.findViewById(R.id.chipTodayText);
        chipNext = activity.findViewById(R.id.chipNext);
        chipNextName = activity.findViewById(R.id.chipNextName);
        chipNextCountdown = activity.findViewById(R.id.chipNextCountdown);
        greeting = activity.findViewById(R.id.homeGreeting);
        aqiPill = activity.findViewById(R.id.aqiPill);
        aqiDot = activity.findViewById(R.id.aqiDot);
        aqiValue = activity.findViewById(R.id.aqiValue);
        aqiLabel = activity.findViewById(R.id.aqiLabel);
        sunrisePill = activity.findViewById(R.id.sunrisePill);
        sunriseText = activity.findViewById(R.id.sunriseText);
        compactDate = activity.findViewById(R.id.compactDate);
        compactWeather = activity.findViewById(R.id.compactWeather);
        weatherIcon = activity.findViewById(R.id.weatherIcon);
        rainIcon = activity.findViewById(R.id.rainIcon);
        weatherTemp = activity.findViewById(R.id.weatherTemp);
        weatherPlace = activity.findViewById(R.id.weatherPlace);
        weatherCondition = activity.findViewById(R.id.weatherCondition);
        rainText = activity.findViewById(R.id.rainText);
        weatherRange = activity.findViewById(R.id.weatherRange);
        weatherDot = activity.findViewById(R.id.weatherDot);
        weatherStamp = activity.findViewById(R.id.weatherStamp);
        hourlyRow = activity.findViewById(R.id.hourlyRow);
        gold = new PriceClient.Block(activity.findViewById(R.id.goldBlock));
        fuel = new PriceClient.Block(activity.findViewById(R.id.fuelBlock));

        // The time's colon in the accent colour: TextClock keeps the format's spans.
        for (int id : new int[]{R.id.clock, R.id.compactClock}) {
            TextClock clock = activity.findViewById(id);
            clock.setFormat24Hour(colon("HH:mm"));
            clock.setFormat12Hour(colon("HH:mm"));
            clock.setTimeZone(settings.timezone);
        }

        weatherPlace.setText(settings.weatherPlace);
        PriceClient.setUp(activity, gold, fuel);

        WidgetOptions.home(activity.findViewById(android.R.id.content), settings);
        updateCalendar();
        updateGreeting();
        showCached();
    }

    private SpannableString colon(String format) {
        SpannableString s = new SpannableString(format);
        int i = format.indexOf(':');
        s.setSpan(new ForegroundColorSpan(accent), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** While Home is started: the calendar follows the system minute tick, so it turns over exactly at midnight. */
    void start() {
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(tick, f);
        }
        updateCalendar();
        updateGreeting();
    }

    void stop() {
        try {
            activity.unregisterReceiver(tick);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // ---- calendar and greeting ------------------------------------------------------------------------

    void updateCalendar() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of(settings.timezone));
        LocalDate lunarDay = LocalDate.now(VietnameseLunar.HANOI);
        if (today.equals(shownDay) && lunarDay.equals(shownLunarDay)) return;
        shownDay = today; shownLunarDay = lunarDay;
        String day = WEEKDAYS[today.getDayOfWeek().getValue() - 1];
        weekday.setText(day);
        date.setText(today.getDayOfMonth() + " tháng " + today.getMonthValue() + ", " + today.getYear());
        int[] l = VietnameseLunar.of(activity, lunarDay);
        String full = l == null ? "" : VietnameseLunar.longText(l);
        lunar.setText(full);
        int comma = full.indexOf(',');
        lunarShort = comma > 0 ? full.substring(0, comma) : full;
        Holidays.Hit now = holidays.today(lunarDay), next = holidays.next(lunarDay);
        chipToday.setVisibility(now == null ? View.GONE : View.VISIBLE);
        if (now != null) chipTodayText.setText(now.name);
        chipNext.setVisibility(next == null ? View.GONE : View.VISIBLE);
        if (next != null) {
            chipNextName.setText(next.name);
            chipNextCountdown.setText("· " + Holidays.countdown(next.days));
        }
        // Collapsed bar: "Thứ Bảy, 24.10 · " then the lunar day in the script face.
        SpannableStringBuilder b = new SpannableStringBuilder(day + ", " + today.getDayOfMonth() + "."
                + today.getMonthValue() + (!settings.showLunar || lunarShort.isEmpty() ? "" : " · "));
        int from = b.length();
        if (settings.showLunar) b.append(lunarShort);
        b.setSpan(new FaceSpan(activity.getResources().getFont(R.font.dancing_bold)), from, b.length(), 0);
        b.setSpan(new AbsoluteSizeSpan((int) activity.getResources().getDimension(R.dimen.home_compact_lunar_text)),
                from, b.length(), 0);
        b.setSpan(new ForegroundColorSpan(accent), from, b.length(), 0);
        compactDate.setText(b);
    }

    /** "Chào buổi sáng" … by the hour in Hanoi. */
    void updateGreeting() {
        int h = Calendar.getInstance(TimeZone.getTimeZone(settings.timezone)).get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 4 && h < 11 ? "Chào buổi sáng" : h < 13 && h >= 11 ? "Chào buổi trưa"
                : h >= 13 && h < 18 ? "Chào buổi chiều" : h >= 18 && h < 23 ? "Chào buổi tối" : "Chúc ngủ ngon");
    }

    /** A typeface for part of a line (TypefaceSpan(Typeface) needs API 28; minSdk is 26). */
    private static final class FaceSpan extends MetricAffectingSpan {
        private final Typeface face;
        FaceSpan(Typeface face) { this.face = face; }
        @Override public void updateDrawState(TextPaint p) { p.setTypeface(face); }
        @Override public void updateMeasureState(TextPaint p) { p.setTypeface(face); }
    }

    // ---- refresh ----------------------------------------------------------------------------------

    void refreshIfDue() {
        updateCalendar();
        showCached();
        boolean pricesDue = PriceClient.due(activity, settings);
        boolean weatherDue = (settings.showWeather || settings.showAqi) && WeatherClient.due(activity);
        if (loading || !(pricesDue || weatherDue)) return;
        loading = true;
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            if (pricesDue) {
                PriceClient.fetchIfDue(app, settings);
                main.post(() -> {
                    if (!activity.isDestroyed()) PriceClient.show(activity, settings, gold, fuel);
                });
            }
            if (weatherDue) {
                try { WeatherClient.fetch(app); } catch (Exception error) { Log.w(TAG, "Weather fetch failed", error); }
            }
            main.post(() -> {
                loading = false;
                if (!activity.isDestroyed()) showCached();
            });
        }, "home-dashboard").start();
    }

    // ---- display ----------------------------------------------------------------------------------

    private void showCached() {
        showWeather();
        PriceClient.show(activity, settings, gold, fuel);
    }

    private void showWeather() {
        WeatherClient.Now w = WeatherClient.now(activity, settings.rainPercent, settings.rainHours,
                settings.weatherStaleMinutes * 60_000L);
        showAir(w.aqi);
        sunrisePill.setVisibility(settings.showWeather && w.sunrise > 0 ? View.VISIBLE : View.GONE);
        if (w.sunrise > 0) sunriseText.setText("Mặt trời mọc " + format("HH:mm", w.sunrise));
        if (!w.ok) {
            weatherIcon.setImageResource(R.drawable.wx_cloudy);
            weatherTemp.setText("--°");
            weatherCondition.setText("Chưa có dữ liệu thời tiết");
            rainIcon.setVisibility(View.GONE);
            rainText.setText("");
            weatherRange.setText("");
            weatherStamp.setText("Chờ cập nhật");
            weatherDot.setBackgroundTintList(ColorStateList.valueOf(stale));
            hourlyRow.removeAllViews();
            compactWeather.setText("Chưa có dữ liệu thời tiết");
            return;
        }
        weatherIcon.setImageResource(icon(w.code, w.day));
        weatherTemp.setText(w.temp + "°");
        weatherCondition.setText(WeatherClient.describe(w.code) + ", " + wind(w.wind));
        String rainShort;
        if (w.rainAt > 0) {
            boolean nowish = w.rainAt <= System.currentTimeMillis();
            rainIcon.setVisibility(View.VISIBLE);
            rainText.setTextColor(activity.getColor(R.color.home_rain));
            rainText.setText((nowish ? "Đang có thể mưa" : "Mưa từ " + format("HH:00", w.rainAt)) + " · " + w.rainChance + "%");
            rainShort = nowish ? "có thể mưa trong giờ này" : "có thể mưa lúc " + format("HH:00", w.rainAt);
        } else {
            rainIcon.setVisibility(View.GONE);
            rainText.setTextColor(activity.getColor(R.color.home_text_80));
            rainText.setText("Không mưa " + settings.rainHours + " giờ tới");
            rainShort = "không mưa";
        }
        weatherRange.setText(w.min + "° – " + w.max + "° · Ẩm " + w.humidity + "%");
        weatherStamp.setText(format("HH:mm", w.observed));
        weatherDot.setBackgroundTintList(ColorStateList.valueOf(w.stale ? stale : fresh));
        compactWeather.setText(w.temp + "° · " + rainShort);
        showHours(w);
    }

    /** US AQI pill: dot and label by level. Hidden without a recent reading. */
    private void showAir(int aqi) {
        aqiPill.setVisibility(settings.showAqi && aqi >= 0 ? View.VISIBLE : View.GONE);
        if (!settings.showAqi || aqi < 0) return;
        String label = aqi <= 50 ? "Tốt" : aqi <= 100 ? "Trung bình" : aqi <= 150 ? "Kém" : aqi <= 200 ? "Xấu"
                : aqi <= 300 ? "Rất xấu" : "Nguy hại";
        int color = aqi <= 50 ? 0xFF8BE3A8 : aqi <= 100 ? 0xFFF5D04C : aqi <= 150 ? 0xFFFFA14D
                : aqi <= 200 ? 0xFFFF6B5E : aqi <= 300 ? 0xFFC77DFF : 0xFFB0485A;
        aqiValue.setText("AQI " + aqi);
        aqiLabel.setText("· " + label);
        aqiDot.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void showHours(WeatherClient.Now w) {
        HourlyStrip.fill(hourlyRow, w, settings.rainPercent);
    }

    private int dp(float v) { return Math.round(v * density); }

    static int icon(int code, boolean day) {
        if (code == 0 || code == 1) return day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
        if (code == 2) return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return R.drawable.wx_rain;
        if (code == 95 || code == 96 || code == 99) return R.drawable.wx_storm;
        return R.drawable.wx_cloudy;
    }

    private static String wind(double kmh) {
        if (kmh < 6) return "lặng gió";
        if (kmh < 20) return "gió nhẹ";
        if (kmh < 39) return "gió vừa";
        return "gió mạnh";
    }

    private String format(String pattern, long time) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, VI);
        f.setTimeZone(TimeZone.getTimeZone(settings.timezone));
        return f.format(new Date(time));
    }
}
