package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONObject;

/**
 * Settings of the redesigned Home panel. Defaults here; a pushed theme's config.json can override any of them with
 * the same key (see README-HOME-REDESIGN.md). No API keys are needed: every source used is a public endpoint.
 */
final class HomeSettings {
    /** Top-left caption; empty hides it (the design for that corner is still open). */
    String caption = "";
    /** Place name over the weather; the coordinates themselves are in WeatherClient. */
    String weatherPlace = "Hà Nội";
    /** Rain chip: first hour within rainHours whose probability reaches rainPercent. */
    int rainPercent = 50;
    int rainHours = 12;
    /** Append the (modelled, regional) US AQI to the weather line. */
    boolean showAqi = false;
    /** Which dealer's SJC bar price to show from taichinh.com: doji, pnj, phuquy or btmh. */
    String goldBrand = "doji";
    int goldRefreshMinutes = 5;
    int fuelRefreshMinutes = 15;
    /** Amber dot after the soft age; the number is hidden ("—") after the hard age. */
    int goldStaleHours = 2;
    int goldMaxAgeHours = 72;
    int fuelStaleDays = 8;
    int fuelMaxAgeDays = 21;
    int weatherStaleMinutes = 90;

    static HomeSettings load(Context context) {
        HomeSettings s = new HomeSettings();
        JSONObject o = Config.overlay(context);
        if (o == null) return s;
        s.caption = o.optString("home_caption", s.caption).trim();
        s.weatherPlace = o.optString("weather_place", s.weatherPlace).trim();
        s.rainPercent = range(o.optInt("rain_alert_percent", s.rainPercent), 1, 100, s.rainPercent);
        s.rainHours = range(o.optInt("rain_alert_hours", s.rainHours), 1, 24, s.rainHours);
        s.showAqi = o.optBoolean("show_aqi", s.showAqi);
        s.goldBrand = o.optString("gold_brand", s.goldBrand).trim();
        s.goldRefreshMinutes = range(o.optInt("gold_refresh_minutes", s.goldRefreshMinutes), 5, 240, s.goldRefreshMinutes);
        s.fuelRefreshMinutes = range(o.optInt("fuel_refresh_minutes", s.fuelRefreshMinutes), 15, 720, s.fuelRefreshMinutes);
        s.goldStaleHours = range(o.optInt("gold_stale_hours", s.goldStaleHours), 1, 168, s.goldStaleHours);
        s.goldMaxAgeHours = range(o.optInt("gold_max_age_hours", s.goldMaxAgeHours), 1, 720, s.goldMaxAgeHours);
        s.fuelStaleDays = range(o.optInt("fuel_stale_days", s.fuelStaleDays), 1, 60, s.fuelStaleDays);
        s.fuelMaxAgeDays = range(o.optInt("fuel_max_age_days", s.fuelMaxAgeDays), 1, 90, s.fuelMaxAgeDays);
        s.weatherStaleMinutes = range(o.optInt("weather_stale_minutes", s.weatherStaleMinutes), 30, 720, s.weatherStaleMinutes);
        return s;
    }

    private static int range(int v, int min, int max, int fallback) {
        return v < min || v > max ? fallback : v;
    }
}
