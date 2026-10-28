package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * SJC gold (taichinh.com, giavang.now fallback) and Petrolimex fuel prices for the Home panel and the screensaver:
 * fetched off the UI thread when due, cached in SharedPreferences "market_prices_v3", shown in
 * layout/home_price_block.xml blocks with the feed's observation date and a fresh/stale dot.
 */
final class PriceClient {
    private static final String TAG = "PriceClient";
    private static final Locale VI = new Locale("vi", "VN");
    private static final String GOLD_URL = "https://taichinh.com/api/v1/gold";
    private static final String GOLD_FALLBACK_URL = "https://giavang.now/api/prices?type=SJL1L10";
    private static final String FUEL_FILTER = "{\"FilterBy\":{\"And\":[{\"SystemID\":{\"Equals\":\"6783dc1271ff449e95b74a9520964169\"}},{\"RepositoryID\":{\"Equals\":\"a95451e23b474fe5886bfb7cf843f53c\"}},{\"RepositoryEntityID\":{\"Equals\":\"3801378fe1e045b1afa10de7c5776124\"}},{\"Status\":{\"Equals\":\"Published\"}}]},\"SortBy\":{\"LastModified\":\"Descending\"},\"Pagination\":{\"TotalRecords\":-1,\"TotalPages\":0,\"PageSize\":0,\"PageNumber\":0}}";

    private PriceClient() { }

    /** One price block's views. */
    static final class Block {
        final TextView kicker, stamp, sourceTime, caption1, value1, caption2, value2;
        final View dot;

        Block(View root) {
            kicker = root.findViewById(R.id.blockKicker);
            stamp = root.findViewById(R.id.blockStamp);
            sourceTime = root.findViewById(R.id.blockSourceTime);
            dot = root.findViewById(R.id.blockDot);
            caption1 = root.findViewById(R.id.blockCaption1);
            value1 = root.findViewById(R.id.blockValue1);
            caption2 = root.findViewById(R.id.blockCaption2);
            value2 = root.findViewById(R.id.blockValue2);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("market_prices_v3", Context.MODE_PRIVATE);
    }

    /** Captions and sizes: gold shows the sell price big (accent) and buy small; fuel shows E10 big and diesel small. */
    static void setUp(Context c, Block gold, Block fuel) {
        gold.kicker.setText("Vàng SJC");
        gold.caption1.setText("Bán ra · triệu/lượng");
        gold.caption2.setText("Mua vào");
        gold.value1.setTextColor(c.getColor(R.color.home_accent));
        bigValue(c, gold.value1, R.dimen.home_gold_big_text, R.dimen.home_gold_big_trim);
        fuel.kicker.setText("Xăng dầu");
        fuel.caption1.setText("E10 RON 95‑III · đ/lít");
        fuel.caption2.setText("Dầu diesel");
        bigValue(c, fuel.value1, R.dimen.home_fuel_big_text, R.dimen.home_fuel_big_trim);
    }

    private static void bigValue(Context c, TextView v, int size, int trim) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimension(size));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.topMargin = lp.bottomMargin = c.getResources().getDimensionPixelSize(trim);
        v.setLayoutParams(lp);
    }

    static boolean due(Context c, HomeSettings settings) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        return (settings.showGold && (!settings.goldBrand.equals(p.getString("gold_requested_brand", "")) || PriceRules.due(now, p.getLong("gold_attempt", 0), settings.goldRefreshMinutes * 60_000L)))
                || (settings.showFuel && PriceRules.due(now, p.getLong("fuel_attempt", 0), settings.fuelRefreshMinutes * 60_000L));
    }

    /** Fetches whatever is due. Blocking: call off the UI thread. */
    static synchronized void fetchIfDue(Context c, HomeSettings settings) {
        SharedPreferences prefs = prefs(c);
        long now = System.currentTimeMillis();
        boolean goldDue = settings.showGold && (!settings.goldBrand.equals(prefs.getString("gold_requested_brand", "")) || PriceRules.due(now, prefs.getLong("gold_attempt", 0), settings.goldRefreshMinutes * 60_000L));
        boolean fuelDue = settings.showFuel && PriceRules.due(now, prefs.getLong("fuel_attempt", 0), settings.fuelRefreshMinutes * 60_000L);
        SharedPreferences.Editor e = prefs.edit();
        if (goldDue) e.putLong("gold_attempt", now).putString("gold_requested_brand", settings.goldBrand);
        if (fuelDue) e.putLong("fuel_attempt", now);
        e.apply();
        if (goldDue) {
            try {
                if (!fetchGold(prefs, settings)) throw new Exception("Primary quote rejected");
                prefs.edit().remove("gold_error").apply();
            } catch (Exception error) {
                prefs.edit().putString("gold_error", detail(error)).apply();
                Log.w(TAG, "Gold fetch failed", error);
                try { fetchGoldFallback(prefs, settings); }
                catch (Exception again) { prefs.edit().putString("gold_error", detail(error) + "; " + detail(again)).apply(); }
            }
        }
        if (fuelDue) {
            try { fetchFuel(prefs, settings); prefs.edit().remove("fuel_error").apply(); }
            catch (Exception error) { prefs.edit().putString("fuel_error", detail(error)).apply(); Log.w(TAG, "Fuel fetch failed", error); }
        }
    }

    private static String detail(Exception e) {
        String text = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
        return text.substring(0, Math.min(240, text.length()));
    }

    /** Owner-only diagnostics contain public market data, never location or other app preferences. */
    static JSONObject status(Context c) {
        JSONObject out = new JSONObject(); SharedPreferences p = prefs(c);
        String[] allowed = {"gold_brand", "gold_attempt", "gold_checked_at", "gold_error", "gold_buy", "gold_sell",
                "gold_time", "gold_checked_only", "fuel_attempt", "fuel_checked_at", "fuel_error", "fuel_time", "petrol", "diesel"};
        java.util.Map<String, ?> all = p.getAll();
        try { for (String key : allowed) if (all.containsKey(key)) out.put(key, all.get(key)); }
        catch (Exception ignored) { }
        return out;
    }

    static synchronized void refreshNow(Context c) {
        prefs(c).edit().remove("gold_attempt").remove("fuel_attempt").apply();
        fetchIfDue(c, HomeSettings.load(c));
    }

    /** SJC bars from the chosen dealer on taichinh.com, with the feed's observation time. */
    private static boolean fetchGold(SharedPreferences prefs, HomeSettings settings) throws Exception {
        JSONObject response = new JSONObject(read(GOLD_URL));
        if (!"vnd_per_luong".equals(response.optString("unit"))
                || !"vang_mieng_sjc".equals(response.optString("product"))) throw new Exception("Unexpected gold product/unit");
        JSONObject dealer = response.getJSONObject("sjc").optJSONObject(settings.goldBrand);
        if (dealer == null) return false;
        JSONObject metrics = dealer.getJSONObject("metrics");
        JSONObject buy = metrics.getJSONObject("buy"), sell = metrics.getJSONObject("sell");
        if (!PriceRules.publisherMatches(settings.goldBrand, buy.getJSONObject("source").optString("publisher"))
                || !PriceRules.publisherMatches(settings.goldBrand, sell.getJSONObject("source").optString("publisher")))
            throw new Exception("Gold publisher does not match selected dealer");
        long b = buy.getLong("value"), s = sell.getLong("value");
        long stamp = Math.max(iso(buy.getString("observed_at")), iso(sell.getString("observed_at")));
        return storeGold(prefs, settings, b, s, stamp, false);
    }

    /** giavang.now: its timestamp is a check time, not the dealer's posting time. */
    private static void fetchGoldFallback(SharedPreferences prefs, HomeSettings settings) throws Exception {
        JSONObject data = new JSONObject(read(GOLD_FALLBACK_URL));
        if (!data.optBoolean("success") || !"SJL1L10".equals(data.optString("type"))
                || !data.optString("name").contains("SJC")) throw new Exception("Unexpected reference product");
        if (!storeGold(prefs, settings, data.optLong("buy"), data.optLong("sell"), data.optLong("timestamp") * 1000L, true))
            throw new Exception("Invalid reference quote");
    }

    private static boolean storeGold(SharedPreferences prefs, HomeSettings settings, long buy, long sell, long stamp, boolean checkedOnly) {
        if (buy < 10_000_000L || sell < buy || sell > 500_000_000L || !plausible(stamp, settings.goldMaxAgeHours * 3600_000L)) {
            return false;
        }
        boolean sameDealer = settings.goldBrand.equals(prefs.getString("gold_brand", ""));
        if (sameDealer && !PriceRules.replaceGold(stamp, checkedOnly, prefs.getLong("gold_time", 0),
                prefs.getBoolean("gold_checked_only", false), prefs.getLong("gold_checked_at", 0),
                System.currentTimeMillis(), Math.max(30, settings.goldRefreshMinutes * 2) * 60_000L)) return true;
        prefs.edit().putString("gold_brand", settings.goldBrand).putLong("gold_buy", buy).putLong("gold_sell", sell).putLong("gold_time", stamp)
                .putBoolean("gold_checked_only", checkedOnly).putLong("gold_checked_at", System.currentTimeMillis()).apply();
        Log.i(TAG, "Gold confirmed: " + (checkedOnly ? "reference" : settings.goldBrand) + " buy=" + buy + " sell=" + sell);
        return true;
    }

    /** Petrolimex region-1 retail prices; the date comes from each feed row's LastModified. */
    private static void fetchFuel(SharedPreferences prefs, HomeSettings settings) throws Exception {
        String encoded = Base64.encodeToString(FUEL_FILTER.getBytes("UTF-8"),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String url = "https://portals.petrolimex.com.vn/~apis/portals/cms.item/search?x-request="
                + encoded + "&language=vi-VN";
        JSONArray items = new JSONObject(read(url)).getJSONArray("Objects");
        long petrol = 0, diesel = 0, petrolStamp = 0, dieselStamp = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String title = item.optString("Title");
            if (title.equals("Xăng E10 RON 95-III")) {
                petrol = item.optLong("Zone1Price");
                petrolStamp = iso(item.getString("LastModified"));
            }
            if (title.equals("DO 0,05S-II")) {
                diesel = item.optLong("Zone1Price");
                dieselStamp = iso(item.getString("LastModified"));
            }
        }
        long stamp = Math.max(petrolStamp, dieselStamp);
        if (petrol < 10_000 || petrol > 100_000 || diesel < 10_000 || diesel > 100_000)
            throw new Exception("Missing/invalid fuel prices: " + petrol + "/" + diesel);
        if (!plausible(petrolStamp, settings.fuelMaxAgeDays * 86_400_000L)
                || !plausible(dieselStamp, settings.fuelMaxAgeDays * 86_400_000L))
            throw new Exception("Invalid fuel source timestamps: " + petrolStamp + "/" + dieselStamp);
        if (Math.abs(petrolStamp - dieselStamp) > 6 * 3600_000L) throw new Exception("Different fuel update cycles");
        if (stamp < prefs.getLong("fuel_time", 0)) throw new Exception("Fuel source timestamp regressed");
        prefs.edit().putLong("petrol", petrol).putLong("diesel", diesel).putLong("fuel_time", stamp)
                .putLong("fuel_checked_at", System.currentTimeMillis()).apply();
        Log.i(TAG, "Fuel confirmed: region1 petrol=" + petrol + " diesel=" + diesel);
    }

    /** ISO-8601 with an explicit offset; older feed values without one are UTC. */
    static long iso(String text) throws Exception {
        try { return Instant.parse(text).toEpochMilli(); }
        catch (Exception ignored) { }
        try { return OffsetDateTime.parse(text).toInstant().toEpochMilli(); }
        catch (Exception ignored) { }
        return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static boolean plausible(long stamp, long maxAge) {
        long now = System.currentTimeMillis();
        return stamp > 0 && stamp <= now + 60 * 60_000L && now - stamp < maxAge;
    }

    private static String read(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; CleanYourTV/2.6; Android TV)");
        connection.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                for (int n; (n = input.read(buffer)) != -1; ) {
                    out.write(buffer, 0, n);
                    if (out.size() > 200_000) throw new Exception("Response too large");
                }
            }
            return out.toString("UTF-8");
        } finally { connection.disconnect(); }
    }

    /** The cached prices, or "—" and "Chưa cập nhật" when there are none recent enough. */
    static void show(Context c, HomeSettings settings, Block gold, Block fuel) {
        SharedPreferences prefs = prefs(c);
        long now = System.currentTimeMillis();
        long goldTime = prefs.getLong("gold_time", 0);
        if (settings.goldBrand.equals(prefs.getString("gold_brand", "")) && plausible(goldTime, settings.goldMaxAgeHours * 3600_000L)) {
            gold.value1.setText(PriceRules.goldMillions(prefs.getLong("gold_sell", 0)));
            gold.value2.setText(PriceRules.goldMillions(prefs.getLong("gold_buy", 0)));
            boolean checkedOnly = prefs.getBoolean("gold_checked_only", false);
            checked(c, gold, now, prefs.getLong("gold_checked_at", 0), Math.max(30, settings.goldRefreshMinutes * 2) * 60_000L);
            String dealer = settings.goldBrand.equals("phuquy") ? "Phú Quý" : settings.goldBrand.toUpperCase(Locale.ROOT);
            gold.sourceTime.setText((checkedOnly ? "Tham khảo · mốc " : dealer + " · mốc ") + format("HH:mm dd/MM", goldTime));
            gold.sourceTime.setTextColor(c.getColor(checkedOnly || now - goldTime > settings.goldStaleHours * 3600_000L
                    ? R.color.home_stale : R.color.home_text_80));
        } else {
            gold.value1.setText("—");
            gold.value2.setText("—");
            noData(c, gold);
        }
        long fuelTime = prefs.getLong("fuel_time", 0);
        if (plausible(fuelTime, settings.fuelMaxAgeDays * 86_400_000L)) {
            DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(VI));
            fuel.value1.setText(f.format(prefs.getLong("petrol", 0)));
            fuel.value2.setText(f.format(prefs.getLong("diesel", 0)));
            checked(c, fuel, now, prefs.getLong("fuel_checked_at", 0), Math.max(90, settings.fuelRefreshMinutes * 2) * 60_000L);
            // LastModified is data edit time, not necessarily the price's effective time.
            fuel.sourceTime.setText("Vùng 1 · dữ liệu " + format("dd/MM", fuelTime));
            fuel.sourceTime.setTextColor(c.getColor(now - fuelTime > settings.fuelStaleDays * 86_400_000L
                    ? R.color.home_stale : R.color.home_text_80));
        } else {
            fuel.value1.setText("—");
            fuel.value2.setText("—");
            noData(c, fuel);
        }
    }

    private static String format(String pattern, long time) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, VI);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return f.format(new Date(time));
    }

    private static void checked(Context c, Block b, long now, long time, long ttl) {
        boolean fresh = PriceRules.fresh(now, time, ttl);
        b.stamp.setText(time <= 0 ? "Chưa xác nhận" : (fresh ? "Kiểm tra " : "Bản lưu ") + format("HH:mm", time));
        b.dot.setBackgroundTintList(ColorStateList.valueOf(c.getColor(fresh ? R.color.home_fresh : R.color.home_stale)));
    }

    private static void noData(Context c, Block b) {
        b.stamp.setText("Chưa cập nhật");
        b.sourceTime.setText("Chờ dữ liệu nguồn");
        b.dot.setBackgroundTintList(ColorStateList.valueOf(c.getColor(R.color.home_stale)));
    }
}
