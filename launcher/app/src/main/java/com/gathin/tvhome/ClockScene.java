package com.gathin.tvhome;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screensaver in the Home style: a time-of-day color background, time and calendar on the photo, weather with the
 * 12-hour forecast on glass, air quality and sunrise. Only minute updates, no continuous background animation.
 */
final class ClockScene {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final View root,content;
    private final ImageView photo;
    private final Context app;
    private boolean started;
    private int generation, period=-1;
    private static final String[] PERIODS={"đêm yên tĩnh","buổi sáng","ban ngày","chiều muộn","lên đèn"};
    private static final String[] WEEKDAYS={"Thứ Hai","Thứ Ba","Thứ Tư","Thứ Năm","Thứ Sáu","Thứ Bảy","Chủ Nhật"};
    private final HomeSettings settings;
    private final Holidays holidays;
    private final Config config;
    private java.time.LocalDate shownDay, shownLunarDay;
    private final PriceClient.Block gold, fuel;

    ClockScene(View root) {
        this.root=root;this.app=root.getContext().getApplicationContext();content=root.findViewById(R.id.dreamContent);photo=root.findViewById(R.id.dreamPhoto);
        settings=HomeSettings.load(app);
        config=Config.load(app);
        holidays=new Holidays(app);
        UiFont.apply(root);
        int accent=app.getColor(R.color.home_accent);
        android.text.SpannableString f=new android.text.SpannableString("HH:mm");
        f.setSpan(new android.text.style.ForegroundColorSpan(accent),2,3,0);
        android.widget.TextClock clock=root.findViewById(R.id.dreamClock);
        clock.setFormat24Hour(f);clock.setFormat12Hour(f);clock.setTimeZone(settings.timezone);
        text(R.id.dreamPlace).setText(settings.weatherPlace);
        gold=new PriceClient.Block(root.findViewById(R.id.dreamGold));
        fuel=new PriceClient.Block(root.findViewById(R.id.dreamFuel));
        PriceClient.setUp(app,gold,fuel);
        PriceClient.show(app,settings,gold,fuel);
        WidgetOptions.clock(root,settings);
        updateCalendar();
        showWeather();
    }
    private TextView text(int id){return root.findViewById(id);}
    static int periodForHour(int hour){return hour<5?0:hour<10?1:hour<16?2:hour<18?3:4;}
    void start(){
        if(started)return;started=true;generation++;
        updateScene();handler.postDelayed(tick,60000);refresh.run();
    }
    void stop(){started=false;generation++;handler.removeCallbacksAndMessages(null);content.animate().cancel();photo.animate().cancel();photo.setImageDrawable(null);period=-1;}
    private final Runnable tick=new Runnable(){public void run(){if(!started)return;updateScene();showWeather();PriceClient.show(app,settings,gold,fuel);handler.postDelayed(this,60000);}};
    private final Runnable refresh=new Runnable(){public void run(){
        if(!started)return;final int token=generation;
        IO.execute(()->{
            PriceClient.fetchIfDue(app,settings);
            handler.post(()->{if(started&&token==generation)PriceClient.show(app,settings,gold,fuel);});
            if(settings.showWeather || settings.showAqi)WeatherClient.fetch(app);
            handler.post(()->{if(started&&token==generation)showWeather();});
        });
        // Check the 5-minute gold interval; each client only goes to the network when its own TTL is due.
        handler.postDelayed(this,5*60000L);
    }};

    /** Weekday, date, lunar date and holiday chips; changes only when the day does. */
    private void updateCalendar(){
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of(settings.timezone));
        java.time.LocalDate lunarDay=java.time.LocalDate.now(VietnameseLunar.HANOI);
        if(today.equals(shownDay)&&lunarDay.equals(shownLunarDay))return;
        shownDay=today;shownLunarDay=lunarDay;
        text(R.id.dreamWeekday).setText(WEEKDAYS[today.getDayOfWeek().getValue()-1]);
        text(R.id.dreamDate).setText(today.getDayOfMonth()+" tháng "+today.getMonthValue()+", "+today.getYear());
        int[] l=VietnameseLunar.of(app,lunarDay);
        text(R.id.dreamLunar).setText(l==null?"":VietnameseLunar.longText(l));
        Holidays.Hit now=holidays.today(lunarDay), next=holidays.next(lunarDay);
        root.findViewById(R.id.dreamChipToday).setVisibility(!settings.showHolidays||now==null?View.GONE:View.VISIBLE);
        if(now!=null)text(R.id.dreamChipTodayText).setText(now.name);
        root.findViewById(R.id.dreamChipNext).setVisibility(!settings.showHolidays||next==null?View.GONE:View.VISIBLE);
        if(next!=null){text(R.id.dreamChipNextName).setText(next.name);text(R.id.dreamChipNextCountdown).setText("· "+Holidays.countdown(next.days));}
    }

    private void updateScene(){
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone(settings.timezone));int next=periodForHour(c.get(Calendar.HOUR_OF_DAY));
        updateCalendar();
        text(R.id.dreamPeriod).setText(PERIODS[next]);
        // A small, discrete shift once a minute instead of a continuously rendered animation.
        int minute=c.get(Calendar.MINUTE);float dp=root.getResources().getDisplayMetrics().density;
        content.post(()->{if(!started)return;View parent=(View)content.getParent();
            float x=(56+(minute%3)*3)*dp, y=(26+(minute%4)*2)*dp;
            content.setX(Math.min(x,Math.max(0,parent.getWidth()-content.getWidth()-20*dp)));
            content.setY(Math.min(y,Math.max(0,parent.getHeight()-content.getHeight()-20*dp)));});
        if(next==period)return;
        period=next;
        int top=next==0?0xFF080E1A:next==1?0xFF182A35:next==2?0xFF12313A:next==3?0xFF3A2437:0xFF111C30;
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{androidx.core.graphics.ColorUtils.blendARGB(config.backgroundColor,top,config.skyIntensity/100f),config.backgroundColor}));
    }

    /** The same weather, forecast, air quality and sunrise as the Home panel, from WeatherClient's cache. */
    private void showWeather(){
        WeatherClient.Now w=WeatherClient.now(app,settings.rainPercent,settings.rainHours,settings.weatherStaleMinutes*60_000L);
        View aqiPill=root.findViewById(R.id.dreamAqiPill);
        aqiPill.setVisibility(settings.showAqi&&w.aqi>=0?View.VISIBLE:View.GONE);
        if(w.aqi>=0){
            int v=w.aqi;
            String label=v<=50?"Tốt":v<=100?"Trung bình":v<=150?"Kém":v<=200?"Xấu":v<=300?"Rất xấu":"Nguy hại";
            int color=v<=50?0xFF8BE3A8:v<=100?0xFFF5D04C:v<=150?0xFFFFA14D:v<=200?0xFFFF6B5E:v<=300?0xFFC77DFF:0xFFB0485A;
            text(R.id.dreamAir).setText("AQI "+v+" · "+label);
            root.findViewById(R.id.dreamAqiDot).setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
        root.findViewById(R.id.dreamSunrisePill).setVisibility(settings.showWeather&&w.sunrise>0?View.VISIBLE:View.GONE);
        if(w.sunrise>0)text(R.id.dreamSunrise).setText("Mặt trời mọc "+clock(w.sunrise));
        android.widget.ImageView icon=root.findViewById(R.id.dreamWeatherIcon);
        android.widget.ImageView rainIcon=root.findViewById(R.id.dreamRainIcon);
        if(!w.ok){
            icon.setImageResource(R.drawable.wx_cloudy);
            text(R.id.dreamWeatherTemperature).setText("--°");
            text(R.id.dreamWeatherSummary).setText("Chưa có dữ liệu thời tiết");
            text(R.id.dreamWeatherDetails).setText("");
            text(R.id.dreamRain).setText("");rainIcon.setVisibility(View.GONE);
            text(R.id.dreamWeatherStamp).setText("Chờ cập nhật");
            ((android.widget.LinearLayout)root.findViewById(R.id.dreamHourly)).removeAllViews();
            return;
        }
        icon.setImageResource(HomeDashboard.icon(w.code,w.day));
        text(R.id.dreamWeatherTemperature).setText(w.temp+"°");
        text(R.id.dreamWeatherSummary).setText(WeatherClient.describe(w.code));
        text(R.id.dreamWeatherDetails).setText(w.min+"° – "+w.max+"° · Ẩm "+w.humidity+"%");
        TextView rain=text(R.id.dreamRain);
        if(w.rainAt>0){
            rainIcon.setVisibility(View.VISIBLE);rain.setTextColor(app.getColor(R.color.home_rain));
            rain.setText((w.rainAt<=System.currentTimeMillis()?"Đang có thể mưa":"Mưa từ "+clock(w.rainAt).substring(0,2)+":00")+" · "+w.rainChance+"%");
        }else{
            rainIcon.setVisibility(View.GONE);rain.setTextColor(app.getColor(R.color.home_text_80));
            rain.setText("Không mưa "+settings.rainHours+" giờ tới");
        }
        text(R.id.dreamWeatherStamp).setText(clock(w.observed));
        root.findViewById(R.id.dreamWeatherDot).setBackgroundTintList(android.content.res.ColorStateList.valueOf(app.getColor(w.stale?R.color.home_stale:R.color.home_fresh)));
        HourlyStrip.fill(root.findViewById(R.id.dreamHourly),w,settings.rainPercent);
    }
    private String clock(long ms){
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.US);f.setTimeZone(TimeZone.getTimeZone(settings.timezone));return f.format(new java.util.Date(ms));
    }
}
