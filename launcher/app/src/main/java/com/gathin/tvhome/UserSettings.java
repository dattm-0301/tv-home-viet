package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/** Personal overrides are separate from a theme, so theme and APK updates preserve them. */
final class UserSettings {
    static final String FILE = "personal.json";
    static JSONObject read(Context c) {
        File dir = Config.dir(c);
        File cache = new File(c.getFilesDir(), FILE);
        JSONObject o = dir == null ? null : Config.read(new File(dir, FILE));
        if (o != null) try {
            validate(o);
            keep(cache, o);
            return o;
        } catch (Exception ignored) { }
        o = Config.read(cache);
        if (o != null) try { validate(o); return o; } catch (Exception ignored) { }
        return new JSONObject();
    }
    private static void keep(File file, JSONObject o) throws Exception {
        JSONObject old = Config.read(file);
        if (old != null && old.toString().equals(o.toString())) return;
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(o.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (Exception e) { if (out != null) atomic.failWrite(out); throw e; }
    }
    static JSONObject merge(Context c, JSONObject theme) {
        JSONObject o = theme == null ? new JSONObject() : theme;
        JSONObject personal = read(c);
        try { for (Iterator<String> keys = personal.keys(); keys.hasNext();) {
            String key = keys.next(); o.put(key, personal.get(key));
        } } catch (Exception ignored) { }
        return o;
    }
    static void validate(JSONObject o) throws Exception {
        for (Iterator<String> keys = o.keys(); keys.hasNext();) {
            String key = keys.next(); Object value = o.get(key);
            switch (key) {
                case "weather_latitude": case "weather_longitude":
                    if (!(value instanceof Number)) throw new IllegalArgumentException(key + ": cần số");
                    double limit = key.equals("weather_latitude") ? 90 : 180;
                    UserOptions.coordinate(((Number)value).doubleValue(), -limit, limit); break;
                case "home_idle_clock_minutes": case "sky_intensity":
                case "gold_refresh_minutes": case "fuel_refresh_minutes":
                    if (!(value instanceof Number)) throw new IllegalArgumentException(key + ": cần số");
                    int min = key.equals("gold_refresh_minutes") ? 5 : key.equals("fuel_refresh_minutes") ? 15 : 0;
                    int max = key.equals("sky_intensity") ? 100 : key.equals("gold_refresh_minutes") ? 240 : key.equals("fuel_refresh_minutes") ? 720 : 1440;
                    UserOptions.integer(((Number)value).doubleValue(), min, max); break;
                case "show_weather": case "show_aqi": case "show_gold": case "show_fuel":
                case "show_lunar": case "show_holidays":
                    if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + ": cần true/false"); break;
                case "weather_place":
                    if (!(value instanceof String) || ((String)value).trim().isEmpty() || ((String)value).length() > 60)
                        throw new IllegalArgumentException("Tên vị trí cần 1–60 ký tự"); break;
                case "display_timezone":
                    if (!(value instanceof String)) throw new IllegalArgumentException("Múi giờ không hợp lệ");
                    UserOptions.zone((String)value); break;
                case "background_color":
                    if (!(value instanceof String) || !((String)value).matches("#[0-9a-fA-F]{6}"))
                        throw new IllegalArgumentException("Màu nền cần dạng #08121F"); break;
                case "home_layout":
                    if (!java.util.Arrays.asList("clock_left", "clock_right", "weather_right").contains(value))
                        throw new IllegalArgumentException("Bố cục không hợp lệ"); break;
                case "gold_brand":
                    if (!(value instanceof String) || !java.util.Arrays.asList("doji", "pnj", "phuquy", "btmh").contains(value))
                        throw new IllegalArgumentException("Đại lý vàng không hợp lệ"); break;
                default: throw new IllegalArgumentException("Tùy chọn cá nhân chưa hỗ trợ: " + key);
            }
        }
        if (o.has("weather_latitude") != o.has("weather_longitude"))
            throw new IllegalArgumentException("Cần nhập cả vĩ độ và kinh độ");
    }
    static void save(Context c, JSONObject o) throws Exception {
        validate(o);
        File dir = Config.dir(c);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) throw new IllegalStateException("Không mở được bộ nhớ cấu hình");
        AtomicFile file = new AtomicFile(new File(dir, FILE));
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(o.toString(2).getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); keep(new File(c.getFilesDir(), FILE), o); }
        catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
    }
}
