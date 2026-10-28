package com.gathin.tvhome;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Cached regional forecasts; no location permissions or tracking. */
final class WeatherClient {
    // Coordinates are entered by the owner; no GPS or location permission is used.
    private static String place(Context c) {
        JSONObject o = Config.overlay(c);
        double lat = o == null ? 21.0285 : o.optDouble("weather_latitude", 21.0285);
        double lon = o == null ? 105.8542 : o.optDouble("weather_longitude", 105.8542);
        lat = coordinate(lat, -90, 90, 21.0285);
        lon = coordinate(lon, -180, 180, 105.8542);
        return "latitude=" + lat + "&longitude=" + lon
                + "&timezone=" + HomeSettings.load(c).timezone.replace("/", "%2F").replace("+", "%2B") + "&timeformat=unixtime";
    }
    static double coordinate(double value, double min, double max, double fallback) {
        return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
    }
    private static final String WEATHER="https://api.open-meteo.com/v1/forecast?"
        +"current=temperature_2m,apparent_temperature,is_day,relative_humidity_2m,weather_code,wind_speed_10m"
        +"&daily=temperature_2m_max,temperature_2m_min,sunrise&hourly=temperature_2m,precipitation_probability,weather_code,is_day&forecast_days=2&";
    private static final String AIR="https://air-quality-api.open-meteo.com/v1/air-quality?current=us_aqi,pm2_5&forecast_days=1&";
    private static final long WEATHER_TTL=30*60_000L, AIR_TTL=60*60_000L;
    static final class Snapshot {
        String temperature="--°",summary="Chưa có dữ liệu thời tiết",details="",icon="☁",forecast="",air="AQI · chưa có dữ liệu",status="";
        int airColor=0xFFB9C8C8;
    }
    static Snapshot cached(Context c) { return read(c); }
    private static String clock(long ms) {
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));return f.format(new Date(ms));
    }
    private static String degrees(double d) {return String.format(Locale.US,"%.0f°",d);}
    private static boolean fresh(SharedPreferences p,String key,long ttl) {
        long age=System.currentTimeMillis()-p.getLong(key,0);return age>=0&&age<ttl;
    }
    static Snapshot fetch(Context c) {
        SharedPreferences p=c.getSharedPreferences("ambient_weather_v3",Context.MODE_PRIVATE);
        String location = place(c);
        if (!location.equals(p.getString("location", ""))) {
            p.edit().remove("weather").remove("air").remove("weather_at").remove("air_at")
                    .putString("location", location).apply();
        }
        if(HomeSettings.load(c).showWeather && !fresh(p,"weather_at",WEATHER_TTL)) try {
            String s=get(WEATHER + location); new JSONObject(s).getJSONObject("current").getDouble("temperature_2m");
            p.edit().putString("weather",s).putLong("weather_at",System.currentTimeMillis()).apply();
        } catch(Exception ignored) { }
        if(HomeSettings.load(c).showAqi && !fresh(p,"air_at",AIR_TTL)) try {
            String s=get(AIR + location); new JSONObject(s).getJSONObject("current").getDouble("us_aqi");
            p.edit().putString("air",s).putLong("air_at",System.currentTimeMillis()).apply();
        } catch(Exception ignored) { }
        return read(c);
    }
    private static Snapshot read(Context c) {
        Snapshot s=new Snapshot(); SharedPreferences p=c.getSharedPreferences("ambient_weather_v3",Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if (!place(c).equals(p.getString("location", ""))) return s;
        try {
            JSONObject r=new JSONObject(p.getString("weather","")), w=r.getJSONObject("current"), d=r.getJSONObject("daily");
            long observed=w.getLong("time")*1000L;
            if(Math.abs(now-observed)>6*60*60_000L) {
                s.summary="Thời tiết đã cũ · chờ cập nhật";
            } else {
                int dayIndex=0; JSONArray days=d.getJSONArray("time");
                while(dayIndex+1<days.length() && days.getLong(dayIndex+1)*1000L<=now)dayIndex++;
                int code=w.getInt("weather_code"); s.temperature=degrees(w.getDouble("temperature_2m"));s.summary=description(code);s.icon=icon(code);
                s.details="Cảm giác "+degrees(w.getDouble("apparent_temperature"))+"   ·   "+degrees(d.getJSONArray("temperature_2m_min").getDouble(dayIndex))+" – "+degrees(d.getJSONArray("temperature_2m_max").getDouble(dayIndex))+"   ·   Ẩm "+w.getInt("relative_humidity_2m")+"%";
                JSONObject hourly=r.getJSONObject("hourly"); JSONArray times=hourly.getJSONArray("time"), temps=hourly.getJSONArray("temperature_2m"), rain=hourly.getJSONArray("precipitation_probability");
                StringBuilder line=new StringBuilder();
                for(int offset:new int[]{0,2,5}) {
                    long target=(now/3600000+1)*3600+offset*3600;int i=0;while(i<times.length()&&times.getLong(i)<target)i++;
                    if(i<times.length()) {
                        if(line.length()>0)line.append("     ");
                        line.append(clock(times.getLong(i)*1000)).append("  ").append(degrees(temps.getDouble(i))).append("  •  ").append(rain.isNull(i)?"--":rain.getInt(i)).append("% mưa");
                    }
                }
                s.forecast=line.toString();
                s.status="Thời tiết "+clock(observed)+(fresh(p,"weather_at",WEATHER_TTL)?"":" · bản lưu");
            }
        } catch(Exception ignored) { }
        try {
            JSONObject a=new JSONObject(p.getString("air","")).getJSONObject("current");long at=a.getLong("time")*1000L;
            if(Math.abs(now-at)<=6*60*60_000L) {
                int v=(int)Math.round(a.getDouble("us_aqi"));
                String label=v<=50?"Tốt":v<=100?"Trung bình":v<=150?"Kém cho nhóm nhạy cảm":v<=200?"Xấu":v<=300?"Rất xấu":"Nguy hại";
                s.air="●  US AQI "+v+" · "+label+" · "+clock(at);
                s.airColor=v<=50?0xFF9CE3AF:v<=100?0xFFF0D783:v<=150?0xFFFFB27C:v<=200?0xFFFF9393:0xFFE5A4DB;
            } else s.air="AQI đã cũ · chờ cập nhật";
        } catch(Exception ignored) { }
        return s;
    }
    /** Structured current weather for the Home strip (the strings above stay as the screensaver uses them). */
    static final class Now {
        boolean ok, day = true, stale;
        int temp, code, min, max, humidity, aqi = -1;
        double wind;
        long observed;
        /** First hour within the look-ahead whose rain probability reaches the threshold (ms), or 0. */
        long rainAt;
        int rainChance;
        /** The next 12 hours from the current one (the Home panel's hourly strip). */
        final java.util.List<Hour> hours = new java.util.ArrayList<>();
        /** Next sunrise (ms), or 0. */
        long sunrise;
    }

    static final class Hour {
        long time;
        int temp, rain = -1, code;
        boolean day = true;
    }

    /** True when either cache is past its TTL, so a background fetch() would go to the network. */
    static boolean due(Context c) {
        SharedPreferences p=c.getSharedPreferences("ambient_weather_v3",Context.MODE_PRIVATE);
        HomeSettings s = HomeSettings.load(c);
        return !place(c).equals(p.getString("location", "")) || (s.showWeather && !fresh(p,"weather_at",WEATHER_TTL)) || (s.showAqi && !fresh(p,"air_at",AIR_TTL));
    }

    static Now now(Context c, int rainPercent, int rainHours, long staleMs) {
        Now n=new Now(); SharedPreferences p=c.getSharedPreferences("ambient_weather_v3",Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if (!place(c).equals(p.getString("location", ""))) return n;
        try {
            JSONObject r=new JSONObject(p.getString("weather","")), w=r.getJSONObject("current"), d=r.getJSONObject("daily");
            n.observed=w.getLong("time")*1000L;
            if(Math.abs(now-n.observed)>6*60*60_000L) return n;
            int dayIndex=0; JSONArray days=d.getJSONArray("time");
            while(dayIndex+1<days.length() && days.getLong(dayIndex+1)*1000L<=now)dayIndex++;
            n.temp=(int)Math.round(w.getDouble("temperature_2m"));
            n.code=w.getInt("weather_code");
            n.day=w.optInt("is_day",1)==1;
            n.humidity=w.getInt("relative_humidity_2m");
            n.wind=w.optDouble("wind_speed_10m",0);
            n.min=(int)Math.round(d.getJSONArray("temperature_2m_min").getDouble(dayIndex));
            n.max=(int)Math.round(d.getJSONArray("temperature_2m_max").getDouble(dayIndex));
            JSONObject hourly=r.getJSONObject("hourly"); JSONArray times=hourly.getJSONArray("time"), rain=hourly.getJSONArray("precipitation_probability");
            long from=now/3600_000L*3600L, until=from+rainHours*3600L;
            for(int i=0;i<times.length();i++) {
                long t=times.getLong(i);
                if(t<from||t>until||rain.isNull(i))continue;
                if(rain.getInt(i)>=rainPercent){n.rainAt=t*1000L;n.rainChance=rain.getInt(i);break;}
            }
            JSONArray temps=hourly.getJSONArray("temperature_2m"), codes=hourly.optJSONArray("weather_code"), isDay=hourly.optJSONArray("is_day");
            for(int i=0;i<times.length()&&n.hours.size()<12;i++) {
                long t=times.getLong(i);
                if(t<from)continue;
                Hour h=new Hour();h.time=t*1000L;h.temp=(int)Math.round(temps.getDouble(i));
                h.rain=rain.isNull(i)?-1:rain.getInt(i);
                h.code=codes!=null&&!codes.isNull(i)?codes.getInt(i):(h.rain>=50?61:2);
                h.day=isDay==null||isDay.optInt(i,1)==1;
                n.hours.add(h);
            }
            JSONArray rises=d.optJSONArray("sunrise");
            if(rises!=null) for(int i=0;i<rises.length();i++){long t=rises.getLong(i)*1000L;if(t>now){n.sunrise=t;break;}}
            n.stale=now-n.observed>staleMs;
            n.ok=true;
        } catch(Exception ignored) { }
        try {
            JSONObject a=new JSONObject(p.getString("air","")).getJSONObject("current");
            if(Math.abs(now-a.getLong("time")*1000L)<=6*60*60_000L) n.aqi=(int)Math.round(a.getDouble("us_aqi"));
        } catch(Exception ignored) { }
        return n;
    }

    static String describe(int code) { return description(code); }

    private static String get(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(8000);
        try {
            if(conn.getResponseCode()!=200)throw new IllegalStateException("HTTP "+conn.getResponseCode());
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))) {
                StringBuilder b=new StringBuilder();String line;while((line=reader.readLine())!=null){b.append(line);if(b.length()>250000)throw new IllegalStateException("Response too large");}return b.toString();
            }
        } finally {conn.disconnect();}
    }
    private static String description(int code) {
        if (code == 0) return "Trời quang";
        if (code == 1) return "Ít mây";
        if (code == 2) return "Có mây";
        if (code == 3) return "Nhiều mây";
        if (code == 45 || code == 48) return "Sương mù";
        if (code >= 51 && code <= 57) return "Mưa phùn";
        if (code >= 61 && code <= 67) return "Mưa";
        if (code >= 71 && code <= 77) return "Mưa tuyết";
        if (code >= 80 && code <= 82) return "Mưa rào";
        if (code >= 85 && code <= 86) return "Mưa tuyết rào";
        if (code == 95 || code == 96 || code == 99) return "Dông";
        return "Thời tiết hiện tại";
    }

    private static String icon(int code) {
        if (code == 0 || code == 1) return "☀";
        if (code == 2) return "◐";
        if (code == 45 || code == 48) return "≋";
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return "☂";
        if (code == 95 || code == 96 || code == 99) return "ϟ";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        return "☁";
    }
}
