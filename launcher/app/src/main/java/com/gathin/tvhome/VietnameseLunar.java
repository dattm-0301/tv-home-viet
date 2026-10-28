package com.gathin.tvhome;
import android.content.Context;
import org.json.JSONArray;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
/** Month starts computed offline with Ho Ngoc Duc's algorithm, UTC+7; see assets/NOTICE.txt. */
final class VietnameseLunar {
    static final ZoneId HANOI = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String[] MONTHS = {"Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười",
            "Mười Một", "Chạp"};
    private static final String[] STEMS = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    private static final String[] BRANCHES = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu",
            "Tuất", "Hợi"};
    private static JSONArray months;

    private static JSONArray months(Context context) throws Exception {
        if (months == null) {
            try (InputStream in=context.getAssets().open("lunar-months.json")) {
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
                while((n=in.read(b))!=-1)out.write(b,0,n);
                months=new JSONArray(out.toString("UTF-8"));
            }
        }
        return months;
    }

    /** {day, month, year, leap (0/1)} of the lunar date on this solar day, or null outside 1999–2100. */
    static int[] of(Context context, LocalDate date) {
        try {
            JSONArray m = months(context);
            long day = date.toEpochDay();
            int lo=0,hi=m.length()-1;
            while(lo<=hi){int mid=(lo+hi)>>>1;if(m.getJSONArray(mid).getLong(0)<=day)lo=mid+1;else hi=mid-1;}
            if(hi<0 || hi>=m.length()-1)return null;
            JSONArray row=m.getJSONArray(hi);
            return new int[]{(int)(day-row.getLong(0)+1), row.getInt(1), row.getInt(2), row.getInt(3)};
        } catch(Exception e){return null;}
    }

    /** The solar day of lunar day/month (not the leap month) in a lunar year, or null when that day does not exist. */
    static LocalDate toSolar(Context context, int year, int month, int day) {
        try {
            JSONArray m = months(context);
            for (int i = 0; i < m.length() - 1; i++) {
                JSONArray row = m.getJSONArray(i);
                if (row.getInt(2) != year || row.getInt(1) != month || row.getInt(3) != 0) continue;
                long start = row.getLong(0), length = m.getJSONArray(i + 1).getLong(0) - start;
                return day >= 1 && day <= length ? LocalDate.ofEpochDay(start + day - 1) : null;
            }
        } catch (Exception ignored) { }
        return null;
    }

    static String today(Context context) {
        int[] l = of(context, LocalDate.now(HANOI));
        if (l == null) return "";
        return "Âm lịch · "+l[0]+"/"+l[1]+(l[3]==1?" nhuận":"")+"/"+l[2];
    }

    /** "Rằm tháng Chín, năm Bính Ngọ", "Mùng 3 tháng Giêng, năm …", "Ngày 23 tháng Chạp, năm …". */
    static String longText(int[] l) {
        String day = l[0] == 15 ? "Rằm" : l[0] <= 10 ? "Mùng " + l[0] : "Ngày " + l[0];
        String month = "tháng " + MONTHS[l[1] - 1] + (l[3] == 1 ? " nhuận" : "");
        return day + " " + month + ", năm " + STEMS[(l[2] + 6) % 10] + " " + BRANCHES[(l[2] + 8) % 12];
    }
}
