package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Holidays and observances for the Home chips, from assets/holidays.json, or holidays.json next to a pushed
 * theme's config.json when there is one. Each entry: {"name": "...", "solar": "MM-DD"} or
 * {"name": "...", "lunar": "MM-DD", "offset": -1} (offset in days, e.g. Giao thừa = the day before lunar 01-01).
 */
final class Holidays {
    static final class Hit {
        final String name;
        final int days;
        Hit(String name, int days) { this.name = name; this.days = days; }
    }

    private final Context context;
    private final JSONArray entries;

    Holidays(Context context) {
        this.context = context.getApplicationContext();
        entries = load(context);
    }

    /** Today's observance, or null. */
    Hit today(LocalDate today) {
        Hit hit = search(today, 0, 0);
        return hit;
    }

    /** The nearest observance after today (within a year), or null. */
    Hit next(LocalDate today) {
        return search(today, 1, 370);
    }

    private Hit search(LocalDate today, int from, int to) {
        Hit best = null;
        int[] lunarToday = VietnameseLunar.of(context, today);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.optJSONObject(i);
            if (e == null) continue;
            String name = e.optString("name").trim();
            if (name.isEmpty()) continue;
            for (LocalDate when : dates(e, today, lunarToday)) {
                int days = (int) ChronoUnit.DAYS.between(today, when);
                if (days < from || days > to) continue;
                if (best == null || days < best.days) best = new Hit(name, days);
            }
        }
        return best;
    }

    /** This entry's dates around today: this year's and next year's (solar), or last, this and next lunar year's. */
    private LocalDate[] dates(JSONObject e, LocalDate today, int[] lunarToday) {
        try {
            String solar = e.optString("solar"), lunar = e.optString("lunar");
            int offset = e.optInt("offset", 0);
            if (!solar.isEmpty()) {
                int m = Integer.parseInt(solar.substring(0, 2)), d = Integer.parseInt(solar.substring(3, 5));
                return new LocalDate[]{LocalDate.of(today.getYear(), m, d).plusDays(offset),
                        LocalDate.of(today.getYear() + 1, m, d).plusDays(offset)};
            }
            if (!lunar.isEmpty() && lunarToday != null) {
                int m = Integer.parseInt(lunar.substring(0, 2)), d = Integer.parseInt(lunar.substring(3, 5));
                LocalDate[] out = new LocalDate[3];
                int n = 0;
                for (int y = lunarToday[2] - 1; y <= lunarToday[2] + 1; y++) {
                    LocalDate s = VietnameseLunar.toSolar(context, y, m, d);
                    if (s != null) out[n++] = s.plusDays(offset);
                }
                LocalDate[] trimmed = new LocalDate[n];
                System.arraycopy(out, 0, trimmed, 0, n);
                return trimmed;
            }
        } catch (Exception ignored) { }
        return new LocalDate[0];
    }

    /** "hôm nay", "ngày mai", "còn 7 ngày". */
    static String countdown(int days) {
        return days == 0 ? "hôm nay" : days == 1 ? "ngày mai" : "còn " + days + " ngày";
    }

    private static JSONArray load(Context context) {
        File dir = Config.dir(context);
        File custom = dir != null ? new File(dir, "holidays.json") : null;
        try (InputStream in = custom != null && custom.isFile() && custom.length() < 64 * 1024
                ? new FileInputStream(custom) : context.getAssets().open("holidays.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
            return new JSONArray(out.toString("UTF-8"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
