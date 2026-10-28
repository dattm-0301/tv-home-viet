package com.gathin.tvhome;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

final class WidgetOptions {
    private static void show(View root, int id, boolean yes) {
        View v = root.findViewById(id); if (v != null) v.setVisibility(yes ? View.VISIBLE : View.GONE);
    }
    private static void swap(ViewGroup parent, View a, View b) {
        int first = parent.indexOfChild(a), second = parent.indexOfChild(b);
        parent.removeViewAt(second); parent.removeViewAt(first);
        parent.addView(b, first); parent.addView(a, second);
    }
    static void home(View root, HomeSettings s) {
        if ("clock_right".equals(s.homeLayout)) {
            View info = root.findViewById(R.id.infoPanel);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)info.getLayoutParams();
            lp.setMarginEnd(lp.getMarginStart()); lp.setMarginStart(0); info.setLayoutParams(lp);
            swap(root.findViewById(R.id.dashboardRow), root.findViewById(R.id.leftBlock), info);
        } else if ("weather_right".equals(s.homeLayout)) {
            swap(root.findViewById(R.id.infoPanel), root.findViewById(R.id.weatherSection), root.findViewById(R.id.priceSection));
        }
        show(root, R.id.weatherSection, s.showWeather);
        show(root, R.id.compactWeather, s.showWeather);
        show(root, R.id.priceSection, s.showGold || s.showFuel);
        root.findViewById(R.id.infoPanel).setVisibility(s.showWeather || s.showGold || s.showFuel ? View.VISIBLE
                : "clock_right".equals(s.homeLayout) ? View.INVISIBLE : View.GONE);
        show(root, R.id.weatherPriceDivider, s.showWeather && (s.showGold || s.showFuel));
        show(root, R.id.priceDivider, s.showGold && s.showFuel);
        show(root, R.id.goldBlock, s.showGold); show(root, R.id.fuelBlock, s.showFuel);
        ((View)root.findViewById(R.id.homeLunarDate).getParent()).setVisibility(s.showLunar ? View.VISIBLE : View.GONE);
        show(root, R.id.chipRow, s.showHolidays);
        // Let weather use the panel width when the price column is disabled.
        if (s.showWeather && !s.showGold && !s.showFuel) {
            View weather = root.findViewById(R.id.weatherSection);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)weather.getLayoutParams(); lp.width=0; lp.weight=1; weather.setLayoutParams(lp);
        }
    }
    static void clock(View root, HomeSettings s) {
        ((View)root.findViewById(R.id.dreamPlace).getParent().getParent()).setVisibility(s.showWeather ? View.VISIBLE : View.GONE);
        ((View)root.findViewById(R.id.dreamLunar).getParent()).setVisibility(s.showLunar ? View.VISIBLE : View.GONE);
        show(root, R.id.dreamGold, s.showGold); show(root, R.id.dreamFuel, s.showFuel);
        show(root, R.id.dreamPrices, s.showGold || s.showFuel);
        show(root, R.id.dreamPriceDivider, s.showGold && s.showFuel);
    }
}
