package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Panel backlight on the Xiaomi L55MA-EP (MiTV_MTEQ0). Found on the TV (2026-10-24): Xiaomi's settings app sets it with
 * mitv.display.PictureSettingsManager.getInstance().setBacklight(int 0–100), a class in the shared library
 * "mitvmiddlewareimpl" (/system/framework/mitvmiddlewareimpl.jar, declared optional in the manifest). The settings
 * keys global picture_backlight and system backLightForNormal are only records: writing them changed nothing.
 * Reading works; setBacklight returns false for this sideloaded app (TvMiddleWare log, 2026-10-24), so Home only
 * shows the level and opens Xiaomi's own backlight popup (broadcast com.xiaomi.mitv.settings.BACKLIGHT_POPUP, as the installed
 * "Độ sáng TV" app does).
 */
final class BrightnessControl {
    static final int[] PRESETS = {100, 70, 45};
    static final String[] PRESET_NAMES = {"Ban ngày", "Buổi tối", "Xem phim"};
    private static final String PREFS = "home_brightness";
    private static Object manager;
    private static boolean managerTried;

    private BrightnessControl() { }

    private static Object manager() {
        if (!managerTried) {
            managerTried = true;
            try {
                manager = Class.forName("mitv.display.PictureSettingsManager").getMethod("getInstance").invoke(null);
            } catch (Throwable ignored) {
                manager = null;
            }
        }
        return manager;
    }

    /** True when the vendor backlight API is there to call. */
    static boolean canWrite(Context c) {
        return manager() != null;
    }

    /** Current backlight 0–100, or -1 when unknown. */
    static int get(Context c) {
        Object m = manager();
        if (m != null) {
            try {
                return Math.max(0, Math.min(100, (Integer) m.getClass().getMethod("getBacklight").invoke(m)));
            } catch (Throwable ignored) {
            }
        }
        return Settings.Global.getInt(c.getContentResolver(), "picture_backlight", -1);
    }

    /** True when the vendor API accepted the value. */
    static boolean set(Context c, int value) {
        value = Math.max(5, Math.min(100, value));
        Object m = manager();
        if (m == null) return false;
        try {
            Object ok = m.getClass().getMethod("setBacklight", int.class).invoke(m, value);
            return !(ok instanceof Boolean) || (Boolean) ok;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Xiaomi's own backlight popup. */
    static void openVendorPanel(Context c) {
        Intent i = new Intent("com.xiaomi.mitv.settings.BACKLIGHT_POPUP").setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.SettingsBroadcastReceiver"));
        try {
            c.sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    static String presetName(int value) {
        for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i] == value) return PRESET_NAMES[i];
        return "Tùy chỉnh";
    }

    /** The dock's value line: "70% · Buổi tối". */
    static String label(Context c) {
        int v = get(c);
        return v < 0 ? "Chỉnh trên TV" : v + "% · " + presetName(v);
    }

    static boolean autoDim(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_dim", true);
    }

    static void setAutoDim(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto_dim", on).apply();
    }

    /** After 22:00 (until 05:00), once a night, lowers a brighter backlight to the "Buổi tối" preset. */
    static boolean applyAutoDimIfDue(Context c) {
        if (!autoDim(c) || !canWrite(c)) return false;
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        int h = now.get(Calendar.HOUR_OF_DAY);
        if (h < 22 && h >= 5) return false;
        LocalDate night = LocalDate.now(VietnameseLunar.HANOI).minusDays(h < 5 ? 1 : 0);
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (night.toString().equals(p.getString("dimmed_night", ""))) return false;
        p.edit().putString("dimmed_night", night.toString()).apply();
        return get(c) > PRESETS[1] && set(c, PRESETS[1]);
    }
}
