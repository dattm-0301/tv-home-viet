package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** A TV-local form: no server, location permission or account required. */
public final class PersonalSettingsActivity extends Activity {
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private final Map<String, CheckBox> toggles = new LinkedHashMap<>();
    private LinearLayout form;
    private JSONObject current;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        current = Config.overlay(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101419);
        form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(64), dp(28), dp(64), dp(28)); scroll.addView(form); setContentView(scroll);
        label("Vị trí & widget", 28);
        label("Thay đổi chỉ lưu khi chọn Lưu. Tùy chọn cá nhân được giữ khi cập nhật APK hoặc theme.", 16);
        button("Chọn thành phố nhanh", this::cities);
        field("weather_place", "Tên hiển thị (1–60 ký tự)", "Hà Nội", false);
        field("weather_latitude", "Vĩ độ (-90 đến 90)", "21.0285", true);
        field("weather_longitude", "Kinh độ (-180 đến 180)", "105.8542", true);
        label("Tọa độ gửi tới Open-Meteo để lấy thời tiết; không dùng GPS. Chỉ đổi tên không đổi vị trí dự báo.", 14);
        field("display_timezone", "Múi giờ IANA hoặc system (theo TV)", "Asia/Ho_Chi_Minh", false);
        label("Lịch âm và ngày lễ dùng lịch Việt Nam UTC+7; đồng hồ/ngày dương theo múi giờ đã chọn.", 14);
        toggle("show_weather", "Thời tiết và dự báo", true);
        toggle("show_aqi", "Chất lượng không khí (AQI mô hình)", false);
        toggle("show_gold", "Giá vàng SJC", true);
        toggle("show_fuel", "Giá xăng dầu Petrolimex vùng 1", true);
        toggle("show_lunar", "Lịch âm Việt Nam", true);
        toggle("show_holidays", "Ngày lễ Việt Nam", true);
        button("Chọn bố cục màn hình chính", () -> new AlertDialog.Builder(this).setTitle("Bố cục Home")
                .setItems(new String[]{"Đồng hồ bên trái", "Đồng hồ bên phải", "Thời tiết bên phải bảng thông tin"}, (d, i) ->
                    fields.get("home_layout").setText(new String[]{"clock_left", "clock_right", "weather_right"}[i])).show());
        field("home_layout", "Bố cục Home", "clock_left", false);
        field("gold_brand", "Đại lý SJC: doji / pnj / phuquy / btmh", "doji", false);
        field("gold_refresh_minutes", "Kiểm tra vàng mỗi (5–240 phút)", "5", true);
        field("fuel_refresh_minutes", "Kiểm tra xăng dầu mỗi (15–720 phút)", "15", true);
        label("Nguồn chưa niêm yết giá mới thì kiểm tra thường hơn cũng không đổi giá. Giá không tự đổi theo vị trí thời tiết.", 14);
        field("home_idle_clock_minutes", "Mở đồng hồ sau (0 tắt, tối đa 1440 phút trên Home)", "5", true);
        button("Chọn màu nền nhanh", () -> new AlertDialog.Builder(this).setTitle("Màu nền")
                .setItems(new String[]{"Xanh đêm", "Tím than", "Xanh rêu", "Đen"}, (d, i) ->
                    fields.get("background_color").setText(new String[]{"#08121F", "#20182E", "#10251F", "#080808"}[i])).show());
        field("background_color", "Màu nền #RRGGBB", "#08121F", false);
        field("sky_intensity", "Nền màu theo giờ (0 tắt, 1–100)", "38", true);
        button("Lưu và quay về Home", this::save);
        button("Bỏ các tùy chọn cá nhân này, theo theme", () -> new AlertDialog.Builder(this)
                .setMessage("Chỉ đặt lại vị trí/widget/nền trong màn hình này. Không xóa ứng dụng, nhóm, thứ tự, font hay dữ liệu TV.")
                .setPositiveButton("Đặt lại", (d, i) -> { try { UserSettings.save(this, new JSONObject()); done(); }
                    catch (Exception e) { error(e); } }).setNegativeButton("Hủy", null).show());
        button("Hủy, không lưu", this::finish);
        if (state != null) {
            for (Map.Entry<String, EditText> e : fields.entrySet()) if (state.containsKey(e.getKey())) e.getValue().setText(state.getString(e.getKey()));
            for (Map.Entry<String, CheckBox> e : toggles.entrySet()) if (state.containsKey(e.getKey())) e.getValue().setChecked(state.getBoolean(e.getKey()));
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        for (Map.Entry<String, EditText> e : fields.entrySet()) out.putString(e.getKey(), e.getValue().getText().toString());
        for (Map.Entry<String, CheckBox> e : toggles.entrySet()) out.putBoolean(e.getKey(), e.getValue().isChecked());
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void label(String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(0xFFE0E7EF); form.addView(v); }
    private void field(String key, String title, String fallback, boolean number) {
        label(title, 17); EditText v = new EditText(this); v.setSingleLine(true); v.setTextSize(19);
        v.setTextColor(Color.WHITE); v.setSelectAllOnFocus(true);
        v.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        v.setText(current.optString(key, fallback)); fields.put(key, v); form.addView(v);
    }
    private void toggle(String key, String title, boolean fallback) { CheckBox v = new CheckBox(this); v.setText(title); v.setTextColor(Color.WHITE); v.setTextSize(18); v.setChecked(current.optBoolean(key, fallback)); toggles.put(key, v); form.addView(v); }
    private void button(String title, Runnable action) { Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); form.addView(b); }
    private void cities() {
        String[] names = {"Hà Nội", "Hải Phòng", "Đà Nẵng", "Huế", "TP. Hồ Chí Minh", "Cần Thơ", "Nha Trang", "Đà Lạt"};
        double[][] coords = {{21.0285,105.8542},{20.8449,106.6881},{16.0544,108.2022},{16.4637,107.5909},{10.8231,106.6297},{10.0452,105.7469},{12.2388,109.1967},{11.9404,108.4583}};
        new AlertDialog.Builder(this).setTitle("Vị trí trung tâm thành phố — có thể chỉnh tiếp")
                .setItems(names, (d, i) -> { fields.get("weather_place").setText(names[i]); fields.get("weather_latitude").setText(String.valueOf(coords[i][0])); fields.get("weather_longitude").setText(String.valueOf(coords[i][1])); fields.get("display_timezone").setText("Asia/Ho_Chi_Minh"); }).show();
    }
    private void save() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, EditText> e : fields.entrySet()) {
                String key = e.getKey(), value = e.getValue().getText().toString().trim();
                if (key.equals("weather_latitude") || key.equals("weather_longitude") || key.endsWith("_minutes") || key.equals("sky_intensity")) o.put(key, Double.parseDouble(value));
                else o.put(key, value);
            }
            for (Map.Entry<String, CheckBox> e : toggles.entrySet()) o.put(e.getKey(), e.getValue().isChecked());
            UserSettings.save(this, o); done();
        } catch (Exception e) { error(e); }
    }
    private void done() { setResult(RESULT_OK); finish(); }
    private void error(Exception e) { new AlertDialog.Builder(this).setTitle("Chưa lưu cấu hình")
            .setMessage(e.getMessage() == null ? "Kiểm tra các giá trị đã nhập" : e.getMessage()).setPositiveButton("OK", null).show(); }
}
