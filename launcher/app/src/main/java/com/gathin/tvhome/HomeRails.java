package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Home app area: one row per group of apps, small groups (fewer than 3 apps) sharing a row with a divider.
 * The first row is "Đã ghim" (pinned apps; the first nine open with the number keys 1–9 and carry a badge).
 * Inside a group: pinned apps first, then by launches over the last 14 days, re-sorted at most once a day.
 * Sort mode (Sắp xếp, or long-press OK on a tile): OK picks an app up; ◀ ▶ reorder the pinned row; ▲ ▼ move the
 * app to the group above or below (into "Đã ghim" pins it, out of it unpins it); OK puts it down; Back leaves.
 * The app list itself comes from AppAdapter (hidden apps, library additions, labels and banners stay as they were).
 */
final class HomeRails {
    static final String COLLAPSE_KEY = "collapse_on_scroll";
    private static final String PREFS = "home_rails_v1";
    private static final String PINNED = "pinned", OTHER = "other";

    interface Host {
        void launch(AppAdapter.Entry e);
        /** Focus left the rails upwards. */
        void onRailsUp();
        void onCollapsedChanged(boolean collapsed);
        void onSortStateChanged(boolean sorting, String carriedLabel);
        /** Sort or filter changed: the header buttons show the new choice. */
        void onViewOptionsChanged();
        /** An app was hidden from Home: reload the app list. */
        void onAppsChanged();
    }

    /** A group: id, title, icon, and the packages that belong to it unless the owner moved them. */
    static final class Group {
        final String id, title;
        final int icon;
        final List<String> pkgs;
        Group(String id, String title, int icon, List<String> pkgs) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.pkgs = pkgs;
        }
    }

    private static List<Group> defaults() {
        List<Group> g = new ArrayList<>();
        g.add(new Group("tv", "Phim & truyền hình", R.drawable.g_tv, list("com.netflix.ninja",
                "net.fptplay.ottbox", "com.wbd.hbomax", "com.disney.disneyplus", "com.amazon.amazonvideo.livingroom")));
        g.add(new Group("film", "Thư viện phim", R.drawable.g_film, list("org.jellyfin.androidtv", "org.xbmc.kodi", "org.videolan.vlc")));
        g.add(new Group("video", "Video", R.drawable.g_play, list("com.google.android.youtube.tv", "tv.twitch.android.app")));
        g.add(new Group("music", "Nhạc", R.drawable.g_music, list("com.spotify.tv.android")));
        g.add(new Group("cast", "Chiếu màn hình", R.drawable.g_cast, list("com.ionitech.airscreen", "com.yablio.sendfilestotv")));
        g.add(new Group("web", "Duyệt web", R.drawable.g_globe, list("com.phlox.tvwebbrowser")));
        g.add(new Group("tools", "Công cụ", R.drawable.g_tool, list("com.liskovsoft.leankeyboard")));
        return g;
    }

    private static final String[] DEFAULT_PINS = {"com.google.android.youtube.tv", "org.jellyfin.androidtv",
            "com.netflix.ninja", "org.videolan.vlc", "org.xbmc.kodi", "net.fptplay.ottbox"};

    private static List<String> list(String... s) {
        List<String> l = new ArrayList<>();
        Collections.addAll(l, s);
        return l;
    }

    static boolean collapseOnScroll(Context c) {
        return c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(COLLAPSE_KEY, false);
    }

    private final Activity activity;
    private final Host host;
    private final ScrollView scroll;
    private final LinearLayout column;
    private final SharedPreferences prefs;
    private final float density;
    private final int accent;
    private final boolean collapse;
    private final List<Group> groups = new ArrayList<>();
    private List<AppAdapter.Entry> entries = new ArrayList<>();
    /** Row index of each focusable tile view, for collapse-on-scroll. */
    private final Map<View, Integer> rowOf = new HashMap<>();
    private final List<String> pins = new ArrayList<>();
    private final Map<String, String> assigned = new HashMap<>();
    private Map<String, Integer> ranks = new HashMap<>();
    private String lastFocusedKey;
    private boolean lastFocusedPinned;
    private boolean collapsed;
    private boolean sorting;
    private String carried;
    private boolean carriedPinned;
    private int appCount;
    private View firstCard;
    private final List<View> rowViews = new ArrayList<>();
    private final java.util.Set<View> edgeLeft = new java.util.HashSet<>(), edgeRight = new java.util.HashSet<>();
    /** Apps per row (launcher setting "Apps per row", 4–8, default 6); tiles size themselves to fill it. */
    private final int columns;
    private int tileW, tileH, lastRow;
    /** Header "Thứ tự": usage (pinned, then 14-day launches), name, recent (newest install first). */
    static final String[] SORT_IDS = {"usage", "name", "recent"};
    static final String[] SORT_NAMES = {"Dùng nhiều", "Tên A–Z", "Mới cài"};
    private String sortMode = "usage";
    /** Header "Lọc": "" shows every group, otherwise only that group (the pinned row always stays on top). */
    private String filter = "";
    private final Map<String, Long> installed = new HashMap<>();
    /** Names the owner gave apps ("Đổi tên hiển thị"), by package. */
    private final Map<String, String> names = new HashMap<>();
    /** The OK release that ends a long press must not drop the app it just picked up. */
    private boolean swallowOkUp;

    HomeRails(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        scroll = activity.findViewById(R.id.railsScroll);
        column = activity.findViewById(R.id.railsColumn);
        prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        density = activity.getResources().getDisplayMetrics().density;
        accent = activity.getColor(R.color.home_accent);
        collapse = collapseOnScroll(activity);
        columns = Math.max(4, Math.min(8, Config.load(activity).columns));
        loadState();
    }

    int appCount() { return appCount; }
    boolean isSorting() { return sorting; }

    // ---- state ------------------------------------------------------------------------------------

    private void loadState() {
        groups.clear();
        groups.addAll(defaults());
        try {
            JSONArray custom = new JSONArray(prefs.getString("custom", "[]"));
            for (int i = 0; i < custom.length(); i++) {
                JSONObject o = custom.getJSONObject(i);
                groups.add(new Group(o.getString("id"), o.getString("title"), R.drawable.g_apps, new ArrayList<>()));
            }
        } catch (Exception ignored) {
        }
        pins.clear();
        if (!prefs.contains("pins")) {
            Collections.addAll(pins, DEFAULT_PINS);
        } else {
            try {
                JSONArray a = new JSONArray(prefs.getString("pins", "[]"));
                for (int i = 0; i < a.length(); i++) pins.add(a.getString(i));
            } catch (Exception ignored) {
            }
        }
        names.clear();
        try {
            JSONObject o = new JSONObject(prefs.getString("names", "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                names.put(k, o.getString(k));
            }
        } catch (Exception ignored) {
        }
        sortMode = prefs.getString("sort", "usage");
        filter = prefs.getString("filter", "");
        assigned.clear();
        try {
            JSONObject o = new JSONObject(prefs.getString("assign", "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                assigned.put(k, o.getString(k));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveState() {
        JSONObject a = new JSONObject();
        try {
            for (Map.Entry<String, String> e : assigned.entrySet()) a.put(e.getKey(), e.getValue());
        } catch (Exception ignored) {
        }
        prefs.edit().putString("pins", new JSONArray(pins).toString()).putString("assign", a.toString()).apply();
    }

    private String groupOf(String pkg) {
        String id = assigned.get(pkg);
        if (id != null) for (Group g : groups) if (g.id.equals(id)) return id;
        for (Group g : groups) if (g.pkgs.contains(pkg)) return g.id;
        return OTHER;
    }

    /** Launch counts per day, kept for 14 days. */
    void recordLaunch(String pkg) {
        String today = LocalDate.now(VietnameseLunar.HANOI).toString();
        try {
            JSONObject usage = new JSONObject(prefs.getString("usage", "{}"));
            JSONObject days = usage.optJSONObject(pkg);
            if (days == null) usage.put(pkg, days = new JSONObject());
            days.put(today, days.optInt(today) + 1);
            prefs.edit().putString("usage", usage.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Usage ranks: recomputed at most once a day, so tiles do not move around while the owner uses them. */
    private void refreshRanks() {
        String today = LocalDate.now(VietnameseLunar.HANOI).toString();
        if (today.equals(prefs.getString("rank_day", "")) && !ranks.isEmpty()) return;
        Map<String, Integer> fresh = new HashMap<>();
        String cutoff = LocalDate.now(VietnameseLunar.HANOI).minusDays(14).toString();
        try {
            JSONObject usage = new JSONObject(prefs.getString("usage", "{}"));
            JSONObject kept = new JSONObject();
            for (java.util.Iterator<String> it = usage.keys(); it.hasNext(); ) {
                String pkg = it.next();
                JSONObject days = usage.getJSONObject(pkg), keep = new JSONObject();
                int sum = 0;
                for (java.util.Iterator<String> d = days.keys(); d.hasNext(); ) {
                    String day = d.next();
                    if (day.compareTo(cutoff) > 0) {
                        sum += days.getInt(day);
                        keep.put(day, days.getInt(day));
                    }
                }
                if (keep.length() > 0) kept.put(pkg, keep);
                fresh.put(pkg, sum);
            }
            prefs.edit().putString("usage", kept.toString()).putString("rank_day", today).apply();
        } catch (Exception ignored) {
        }
        ranks = fresh;
    }

    // ---- building ---------------------------------------------------------------------------------

    /** Rebuilds every row from the adapter's current app list, keeping focus on the same app. */
    void rebuild(List<AppAdapter.Entry> apps) {
        entries = new ArrayList<>(apps);
        appCount = entries.size();
        refreshRanks();
        boolean hadFocus = column.hasFocus();
        column.removeAllViews();
        rowOf.clear();
        rowViews.clear();
        edgeLeft.clear();
        edgeRight.clear();
        firstCard = null;

        Map<String, AppAdapter.Entry> byPkg = new LinkedHashMap<>();
        for (AppAdapter.Entry e : entries) if (!byPkg.containsKey(e.pkg)) byPkg.put(e.pkg, e);
        Map<String, List<AppAdapter.Entry>> members = new LinkedHashMap<>();
        for (Group g : groups) members.put(g.id, new ArrayList<>());
        members.put(OTHER, new ArrayList<>());
        for (AppAdapter.Entry e : entries) members.get(groupOf(e.pkg)).add(e);
        for (List<AppAdapter.Entry> l : members.values()) {
            Collections.sort(l, (a, b) -> {
                if ("name".equals(sortMode)) return label(a).compareToIgnoreCase(label(b));
                if ("recent".equals(sortMode)) {
                    int c = Long.compare(installTime(b.pkg), installTime(a.pkg));
                    return c != 0 ? c : a.label.compareToIgnoreCase(b.label);
                }
                int pa = pins.indexOf(a.pkg), pb = pins.indexOf(b.pkg);
                if (pa >= 0 || pb >= 0) {
                    if (pa < 0) return 1;
                    if (pb < 0) return -1;
                    return Integer.compare(pa, pb);
                }
                int ua = ranks.containsKey(a.pkg) ? ranks.get(a.pkg) : 0, ub = ranks.containsKey(b.pkg) ? ranks.get(b.pkg) : 0;
                if (ua != ub) return Integer.compare(ub, ua);
                return a.label.compareToIgnoreCase(b.label);
            });
        }

        List<List<Seg>> rows = new ArrayList<>();
        List<AppAdapter.Entry> pinned = new ArrayList<>();
        for (String p : pins) if (byPkg.containsKey(p)) pinned.add(byPkg.get(p));
        {   // "Đã ghim" is always the first row, even while empty
            List<Seg> r = new ArrayList<>();
            r.add(new Seg(PINNED, "Đã ghim", R.drawable.g_pin, pinned));
            rows.add(r);
        }
        List<Seg> segs = new ArrayList<>();
        for (Group g : groups) {
            List<AppAdapter.Entry> m = members.get(g.id);
            boolean custom = g.pkgs.isEmpty() && !isDefault(g.id);
            if (m.isEmpty() && !custom) continue;
            segs.add(new Seg(g.id, g.title, g.icon, m));
        }
        if (!members.get(OTHER).isEmpty()) segs.add(new Seg(OTHER, "Ứng dụng khác", R.drawable.g_apps, members.get(OTHER)));
        if (!filter.isEmpty()) {
            List<Seg> only = new ArrayList<>();
            for (Seg sg : segs) if (sg.id.equals(filter)) only.add(sg);
            if (only.isEmpty()) {
                filter = "";   // the group is gone (emptied or removed): show everything again
                prefs.edit().remove("filter").apply();
            } else {
                segs = only;
            }
        }
        // Tile size from the apps-per-row setting: N tiles and their gaps fill the width between the side margins.
        int side = activity.getResources().getDimensionPixelSize(R.dimen.home_side_margin);
        int gap = activity.getResources().getDimensionPixelSize(R.dimen.home_tile_gap);
        int divider = 2 * activity.getResources().getDimensionPixelSize(R.dimen.home_segment_divider_space) + dp(1);
        int avail = activity.getResources().getDisplayMetrics().widthPixels - 2 * side;
        tileW = (avail - (columns - 1) * gap) / columns;
        tileH = Math.round(tileW * 124f / 220f);

        // Groups under 3 apps share a row (a run of small groups, or one small group and the next one) as long as
        // the row still fits on one line; otherwise every group has its own row, wrapping after N apps.
        for (int i = 0; i < segs.size(); ) {
            List<Seg> r = new ArrayList<>();
            r.add(segs.get(i++));
            if (r.get(0).apps.size() < 3) {
                while (i < segs.size()) {
                    Seg next = segs.get(i);
                    if (!fits(r, next, gap, divider, avail)) break;
                    if (next.apps.size() < 3) {
                        r.add(next);
                        i++;
                    } else {
                        if (r.size() == 1) {
                            r.add(next);
                            i++;
                        }
                        break;
                    }
                }
            }
            rows.add(r);
        }

        View focusTarget = null;
        for (int ri = 0; ri < rows.size(); ri++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(side, dp(3), side, dp(8));
            List<Seg> r = rows.get(ri);
            for (int si = 0; si < r.size(); si++) {
                Seg seg = r.get(si);
                View v = segmentView(seg, ri, r.size() == 1);
                row.addView(v, new LinearLayout.LayoutParams(-2, -2));
                if (si < r.size() - 1) {
                    View div = new View(activity);
                    div.setBackgroundColor(0x29FFFFFF);
                    int space = activity.getResources().getDimensionPixelSize(R.dimen.home_segment_divider_space);
                    LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.max(1, dp(0.75f)), -1);
                    dl.leftMargin = space;
                    dl.rightMargin = space;
                    row.addView(div, dl);
                }
                for (View tile : seg.tiles) {
                    String key = (String) tile.getTag(R.id.railsColumn);
                    boolean inPinned = PINNED.equals(seg.id);
                    if (key != null && key.equals(sorting && carried != null ? carried : lastFocusedKey)
                            && (sorting && carried != null ? inPinned == carriedPinned : inPinned == lastFocusedPinned)
                            && focusTarget == null) {
                        focusTarget = tile;
                    }
                }
            }
            // The ends of each line hold focus: Left/Right at the edge of a group stay in it instead of jumping.
            if (r.size() == 1) {
                for (List<View> line : r.get(0).lines) {
                    if (line.isEmpty()) continue;
                    edgeLeft.add(line.get(0));
                    edgeRight.add(line.get(line.size() - 1));
                }
            } else {
                Seg first = r.get(0), last = r.get(r.size() - 1);
                if (!first.tiles.isEmpty()) edgeLeft.add(first.tiles.get(0));
                if (!last.tiles.isEmpty()) edgeRight.add(last.tiles.get(last.tiles.size() - 1));
            }
            rowViews.add(row);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
            if (ri > 0) hp.topMargin = activity.getResources().getDimensionPixelSize(R.dimen.home_rail_gap);
            column.addView(row, hp);
        }
        lastRow = rows.size() - 1;
        UiFont.apply(column);
        if (hadFocus || (sorting && carried != null) || activity.getCurrentFocus() == null) {
            View target = focusTarget;
            column.post(() -> {
                if (target != null) target.requestFocus();
                else focusFirst();
            });
        }
    }

    /** Whether next still fits on the same line after the groups already in row r. */
    private boolean fits(List<Seg> r, Seg next, int gap, int divider, int avail) {
        int width = 0;
        List<Seg> all = new ArrayList<>(r);
        all.add(next);
        for (int i = 0; i < all.size(); i++) {
            int n = Math.max(1, all.get(i).apps.size());
            width += n * tileW + (n - 1) * gap;
            if (i > 0) width += divider;
        }
        return width <= avail;
    }

    /** Each group's chip colour (a light-to-deep gradient), so groups are told apart at a glance. */
    private android.graphics.drawable.GradientDrawable chip(String id) {
        int[] c;
        switch (id) {
            case PINNED: c = new int[]{0xFFFFD98A, 0xFFE59A3C}; break;
            case "tv": c = new int[]{0xFFFF8A65, 0xFFE0453A}; break;
            case "film": c = new int[]{0xFFB18CFF, 0xFF7A4DE0}; break;
            case "video": c = new int[]{0xFFFF6B6B, 0xFFC92A3A}; break;
            case "music": c = new int[]{0xFFFF8FC7, 0xFFD94C93}; break;
            case "cast": c = new int[]{0xFF5EE0C8, 0xFF1E9C8A}; break;
            case "web": c = new int[]{0xFF7CB6FF, 0xFF3A70E0}; break;
            case "tools": c = new int[]{0xFF9BD27A, 0xFF4F9A3C}; break;
            default: c = new int[]{0xFFB8C2D3, 0xFF6F7C91}; break;
        }
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, c);
        g.setCornerRadius(dp(5));
        return g;
    }

    /** The name shown for an app: the owner's own, or the app's. */
    private String label(AppAdapter.Entry e) {
        String n = names.get(e.pkg);
        return n != null && !n.isEmpty() ? n : e.label;
    }

    /** The app opened once after each TV start ("Mở cùng TV"), or "". */
    static String bootApp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("boot_app", "");
    }

    private static boolean isDefault(String id) {
        for (Group g : defaults()) if (g.id.equals(id)) return true;
        return false;
    }

    private final class Seg {
        final String id, title;
        final int icon;
        final List<AppAdapter.Entry> apps;
        final List<View> tiles = new ArrayList<>();
        final List<List<View>> lines = new ArrayList<>();
        Seg(String id, String title, int icon, List<AppAdapter.Entry> apps) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.apps = apps;
        }
    }

    /** A group: title row, then its tiles in lines of at most N (only a group with a row of its own wraps). */
    private View segmentView(Seg seg, int rowIndex, boolean ownRow) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        int gap = activity.getResources().getDimensionPixelSize(R.dimen.home_tile_gap);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconBox = new FrameLayout(activity);
        iconBox.setBackground(chip(seg.id));
        iconBox.setElevation(dp(2));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(seg.icon);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(11), dp(11), Gravity.CENTER));
        int box16 = activity.getResources().getDimensionPixelSize(R.dimen.home_rail_icon_box);
        head.addView(iconBox, new LinearLayout.LayoutParams(box16, box16));
        TextView title = text(seg.title, R.dimen.home_rail_title_text, UiFont.bold(activity), 0xFFFFFFFF);
        title.setEllipsize(TextUtils.TruncateAt.END);
        int n = Math.max(1, Math.min(columns, seg.apps.size()));
        title.setMaxWidth(ownRow ? dp(420) : Math.max(dp(90), n * tileW + (n - 1) * gap - dp(40)));
        title.setShadowLayer(6 * density, 0, density, 0x99000000);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = dp(6);
        head.addView(title, tl);
        TextView count = text(String.valueOf(seg.apps.size()), R.dimen.home_rail_count_text, UiFont.bold(activity), accent);
        count.setShadowLayer(6 * density, 0, density, 0x99000000);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = dp(6);
        head.addView(count, cl);
        box.addView(head, new LinearLayout.LayoutParams(-2,
                activity.getResources().getDimensionPixelSize(R.dimen.home_rail_head_height)));

        LinearLayout lines = new LinearLayout(activity);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setClipChildren(false);
        lines.setClipToPadding(false);
        boolean pinnedRow = PINNED.equals(seg.id);
        if (seg.apps.isEmpty()) {
            TextView empty = text(pinnedRow ? "Chưa ghim ứng dụng nào — giữ OK trên một ứng dụng rồi chọn Ghim"
                    : "Nhóm trống — dùng Sắp xếp ▲ ▼ để chuyển ứng dụng vào", R.dimen.home_rail_count_text,
                    UiFont.medium(activity), 0xB3FFFFFF);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            lines.addView(empty, new LinearLayout.LayoutParams(-2, tileH));
        }
        int perLine = ownRow ? columns : Integer.MAX_VALUE;
        LinearLayout line = null;
        List<View> lineCards = null;
        for (int i = 0; i < seg.apps.size(); i++) {
            if (i % perLine == 0) {
                line = new LinearLayout(activity);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setClipChildren(false);
                line.setClipToPadding(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) lp.topMargin = dp(14);
                lines.addView(line, lp);
                lineCards = new ArrayList<>();
                seg.lines.add(lineCards);
            }
            View t = tile(seg.apps.get(i), pinnedRow, rowIndex, seg.lines.size() - 1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            if (i % perLine > 0) lp.leftMargin = gap;
            line.addView(t, lp);
            View card = t.findViewById(R.id.card);
            seg.tiles.add(card);
            lineCards.add(card);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = activity.getResources().getDimensionPixelSize(R.dimen.home_rail_head_gap);
        box.addView(lines, tp);
        return box;
    }

    private View tile(AppAdapter.Entry e, boolean inPinned, int rowIndex, int lineIndex) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClipChildren(false);
        item.setClipToPadding(false);
        int w = tileW;
        int h = tileH;

        FrameLayout card = new FrameLayout(activity);
        card.setId(R.id.card);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundResource(R.drawable.home_tile_bg);
        card.setForeground(activity.getDrawable(R.drawable.home_tile_ring));
        card.setClipToOutline(true);
        card.setTag(R.id.railsColumn, e.pkg);
        card.setTag(R.id.grid, inPinned);
        if (firstCard == null) firstCard = card;
        Drawable banner = e.banner != null && e.banner.getConstantState() != null
                ? e.banner.getConstantState().newDrawable(activity.getResources()).mutate() : e.banner;
        if (banner != null) {
            ImageView img = new ImageView(activity);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout inner = new LinearLayout(activity);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(activity);
            Drawable icon = e.icon != null && e.icon.getConstantState() != null
                    ? e.icon.getConstantState().newDrawable(activity.getResources()).mutate() : e.icon;
            if (icon == null) icon = activity.getPackageManager().getDefaultActivityIcon();
            ic.setImageDrawable(icon);
            inner.addView(ic, new LinearLayout.LayoutParams(h / 2, h / 2));
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        item.addView(card, new LinearLayout.LayoutParams(w, h));

        TextView name = text(label(e), R.dimen.home_tile_name_text, UiFont.medium(activity), 0xDBFFFFFF);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setShadowLayer(5 * density, 0, density, 0x99000000);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(w, -2);
        np.topMargin = dp(5);
        item.addView(name, np);

        boolean isCarried = sorting && e.pkg.equals(carried) && inPinned == carriedPinned;
        card.setActivated(isCarried);
        if (sorting && carried != null && !isCarried) {
            item.setAlpha(0.5f);
        }
        rowOf.put(card, rowIndex);
        card.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(140).start();
            name.animate().translationY(focused ? dp(3) : 0).setDuration(140).start();
            name.setTextColor(focused ? (isCarried ? accent : 0xFFFFFFFF) : 0xDBFFFFFF);
            name.setTypeface(focused ? UiFont.bold(activity) : UiFont.medium(activity));
            if (focused) {
                lastFocusedKey = e.pkg;
                lastFocusedPinned = inPinned;
                updateCollapse(rowIndex);
                scroll.post(() -> showRow(rowIndex));
            }
        });
        card.setOnClickListener(v -> {
            if (sorting) return;   // sort mode keys are handled in handleKey
            recordLaunch(e.pkg);
            host.launch(e);
        });
        card.setOnLongClickListener(v -> {
            if (sorting || swallowOkUp) return true;
            swallowOkUp = true;
            showAppMenu(e, inPinned, v);
            return true;
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (rowIndex != 0 || lineIndex != 0) return false;
                    host.onRailsUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return edgeLeft.contains(v);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return edgeRight.contains(v);
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // Nothing below the last line: stay, instead of letting the focus finder go somewhere odd.
                    return rowIndex == lastRow && v.focusSearch(View.FOCUS_DOWN) == null;
                default:
                    return false;
            }
        });
        return item;
    }

    private TextView text(String s, int size, Typeface face, int color) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getResources().getDimension(size));
        t.setTypeface(face);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(float v) { return Math.round(v * density); }

    // ---- focus and collapse -----------------------------------------------------------------------

    /** Focus back on the last app focused, else the first tile. */
    void restoreFocus() {
        View t = findTile(lastFocusedKey, lastFocusedPinned);
        if (t != null) t.requestFocus();
        else focusFirst();
    }

    void focusFirst() {
        View t = firstTile();
        if (t != null) t.requestFocus();
    }

    private View firstTile() {
        return firstCard;
    }

    private View findTile(String pkg, boolean pinned) {
        if (pkg == null) return null;
        View fallback = null;
        for (View v : rowOf.keySet()) {
            if (!pkg.equals(v.getTag(R.id.railsColumn))) continue;
            if (Boolean.valueOf(pinned).equals(v.getTag(R.id.grid))) return v;
            fallback = v;
        }
        return fallback;
    }

    /** Scrolls so the focused tile's whole row, its group title included, is on screen; row 0 means the top. */
    private void showRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowViews.size()) return;
        View r = rowViews.get(rowIndex);
        int pad = dp(4), sy = scroll.getScrollY(), h = scroll.getHeight();
        int top = rowIndex == 0 ? 0 : r.getTop() - pad, bottom = r.getBottom() + pad;
        if (top < sy) scroll.smoothScrollTo(0, top);
        else if (bottom > sy + h) scroll.smoothScrollTo(0, Math.min(top, bottom - h));
    }

    private void updateCollapse(int rowIndex) {
        boolean want = collapse && rowIndex >= 1;
        if (want == collapsed) return;
        collapsed = want;
        host.onCollapsedChanged(want);
    }

    /** Called when focus goes to the header or dock: the widget comes back. */
    void expand() {
        if (!collapsed) return;
        collapsed = false;
        host.onCollapsedChanged(false);
        scroll.smoothScrollTo(0, 0);
    }

    // ---- keys and sort mode -----------------------------------------------------------------------

    /** Number keys 1–9 and sort mode. True when the key was used. */
    boolean handleKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (!sorting && okKey) {
            // Holding OK: the remote repeats the key. Open the tile's menu on the first repeat and keep the release
            // from reaching the tile, where it would open the app.
            if (down && event.getRepeatCount() == 0) swallowOkUp = false;   // a fresh press: nothing pending
            View f = activity.getCurrentFocus();
            boolean onTile = f != null && f.getId() == R.id.card && rowOf.containsKey(f);
            if (down && event.getRepeatCount() > 0 && onTile) {
                if (!swallowOkUp) {
                    swallowOkUp = true;
                    String pkg = (String) f.getTag(R.id.railsColumn);
                    for (AppAdapter.Entry e : entries) {
                        if (e.pkg.equals(pkg)) {
                            showAppMenu(e, Boolean.TRUE.equals(f.getTag(R.id.grid)), f);
                            break;
                        }
                    }
                }
                return true;
            }
            if (!down && swallowOkUp) {
                swallowOkUp = false;
                return true;
            }
            return false;
        }
        if (!sorting) {
            if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
                if (down && event.getRepeatCount() == 0) openPinned(code - KeyEvent.KEYCODE_1);
                return true;
            }
            return false;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (back) {
            if (!down) endSort();
            return true;
        }
        View f = activity.getCurrentFocus();
        boolean onTile = f != null && f.getId() == R.id.card && rowOf.containsKey(f);
        if (carried == null) {
            if (ok && onTile) {
                if (!down) startSort((String) f.getTag(R.id.railsColumn), Boolean.TRUE.equals(f.getTag(R.id.grid)));
                return true;
            }
            return false;   // arrows move focus as usual
        }
        if (ok && swallowOkUp) {
            if (!down) swallowOkUp = false;
            return true;
        }
        if (ok) {
            if (!down) {
                carried = null;
                rebuild(entries);
                host.onSortStateChanged(true, null);
            }
            return true;
        }
        if (!down) return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (carriedPinned) {
                    int i = pins.indexOf(carried), j = i + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    if (i >= 0 && j >= 0 && j < pins.size()) {
                        Collections.swap(pins, i, j);
                        saveState();
                        rebuild(entries);
                    }
                } else {
                    Toast.makeText(activity, "Trong nhóm, ứng dụng tự xếp theo mức dùng 14 ngày; ghim để tự chọn thứ tự",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveGroup(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                return true;
            default:
                return false;
        }
    }

    private void openPinned(int index) {
        int n = 0;
        for (String p : pins) {
            for (AppAdapter.Entry e : entries) {
                if (!e.pkg.equals(p)) continue;
                if (n++ == index) {
                    recordLaunch(e.pkg);
                    host.launch(e);
                    return;
                }
                break;
            }
        }
    }

    /** The order ▲ ▼ walk through: pinned, then every group shown (or custom), then "other". */
    private List<String> groupOrder() {
        List<String> order = new ArrayList<>();
        order.add(PINNED);
        for (Group g : groups) order.add(g.id);
        order.add(OTHER);
        return order;
    }

    private void moveGroup(int step) {
        List<String> order = groupOrder();
        String current = carriedPinned ? PINNED : groupOf(carried);
        int i = order.indexOf(current) + step;
        if (i < 0 || i >= order.size()) return;
        String target = order.get(i);
        if (carriedPinned) {
            // Down out of "Đã ghim": unpin; the app stays in its own group.
            pins.remove(carried);
            carriedPinned = false;
        } else if (PINNED.equals(target)) {
            if (!pins.contains(carried)) pins.add(carried);
            carriedPinned = true;
        } else {
            assigned.put(carried, target);
        }
        saveState();
        rebuild(entries);
    }

    void startSort(String pkg, boolean inPinned) {
        sorting = true;
        carried = pkg;
        carriedPinned = inPinned && pkg != null && pins.contains(pkg);
        rebuild(entries);
        host.onSortStateChanged(true, pkg == null ? null : labelOf(pkg));
    }

    /** The Sort button: on, with nothing carried yet (OK on a tile picks one up); or off. */
    void toggleSort() {
        if (sorting) {
            endSort();
            return;
        }
        sorting = true;
        carried = null;
        rebuild(entries);
        host.onSortStateChanged(true, null);
        restoreFocus();
    }

    void endSort() {
        if (!sorting) return;
        sorting = false;
        carried = null;
        rebuild(entries);
        host.onSortStateChanged(false, null);
        column.post(this::restoreFocus);
    }

    /**
     * Hold OK on a tile: a small glass card next to it with the app's artwork and name, then Pin (or Unpin), Move to
     * another group and Open. Back closes it.
     */
    private void showAppMenu(AppAdapter.Entry e, boolean inPinned, View anchor) {
        boolean pinnedNow = pins.contains(e.pkg);
        android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.home_bright_bg);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(2), 0, dp(4), dp(8));
        FrameLayout thumb = new FrameLayout(activity);
        thumb.setBackgroundResource(R.drawable.home_tile_bg);
        thumb.setClipToOutline(true);
        Drawable art = e.banner != null && e.banner.getConstantState() != null
                ? e.banner.getConstantState().newDrawable(activity.getResources()).mutate()
                : e.icon != null && e.icon.getConstantState() != null
                ? e.icon.getConstantState().newDrawable(activity.getResources()).mutate() : null;
        if (art != null) {
            ImageView img = new ImageView(activity);
            img.setScaleType(e.banner != null ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            img.setImageDrawable(art);
            thumb.addView(img, new FrameLayout.LayoutParams(-1, -1));
        }
        head.addView(thumb, new LinearLayout.LayoutParams(dp(64), dp(36)));
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label(e), R.dimen.home_rail_title_text, UiFont.bold(activity), 0xFFFFFFFF);
        name.setSingleLine(false);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = text(pinnedNow ? "Đang ghim" : groupTitle(groupOf(e.pkg)), R.dimen.home_rail_count_text,
                UiFont.medium(activity), pinnedNow ? accent : 0xB3FFFFFF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = dp(3);
        titles.addView(sub, sp);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.leftMargin = dp(9);
        head.addView(titles, tp);
        card.addView(head);

        View rule = new View(activity);
        rule.setBackgroundColor(0x24FFFFFF);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.75f)));
        rp.bottomMargin = dp(6);
        card.addView(rule, rp);

        View first = menuRow(card, R.drawable.g_pin, pinnedNow ? "Bỏ ghim" : "Ghim lên đầu", () -> {
            dialog.dismiss();
            if (pinnedNow) pins.remove(e.pkg);
            else pins.add(0, e.pkg);
            saveState();
            lastFocusedKey = e.pkg;
            lastFocusedPinned = !pinnedNow;
            rebuild(entries);
            column.post(this::restoreFocus);
        });
        menuRow(card, R.drawable.g_swap, "Chuyển sang nhóm khác", () -> {
            dialog.dismiss();
            startSort(e.pkg, inPinned);
        });
        menuRow(card, R.drawable.g_open, "Mở ứng dụng", () -> {
            dialog.dismiss();
            recordLaunch(e.pkg);
            host.launch(e);
        });
        boolean bootNow = e.pkg.equals(bootApp(activity));
        menuRow(card, R.drawable.g_power, bootNow ? "Thôi mở cùng TV" : "Mở cùng TV", () -> {
            dialog.dismiss();
            prefs.edit().putString("boot_app", bootNow ? "" : e.pkg).apply();
            Toast.makeText(activity, bootNow ? "Không tự mở ứng dụng khi bật TV nữa"
                    : label(e) + " sẽ tự mở mỗi lần bật TV", Toast.LENGTH_LONG).show();
        });
        menuRow(card, R.drawable.g_edit, "Đổi tên hiển thị", () -> {
            dialog.dismiss();
            rename(e);
        });
        menuRow(card, R.drawable.g_hide, "Ẩn khỏi trang chủ", () -> {
            dialog.dismiss();
            if (LibraryApps.save(activity, e.pkg, false)) {
                pins.remove(e.pkg);
                saveState();
                Toast.makeText(activity, "Đã ẩn " + label(e) + " — mở lại từ nút Tất cả ứng dụng",
                        Toast.LENGTH_LONG).show();
                host.onAppsChanged();
            } else {
                Toast.makeText(activity, "Không lưu được", Toast.LENGTH_SHORT).show();
            }
        });
        menuRow(card, R.drawable.g_info, "Thông tin ứng dụng", () -> {
            dialog.dismiss();
            try {
                activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", e.pkg, null))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ex) {
                Toast.makeText(activity, "TV không mở được trang thông tin ứng dụng", Toast.LENGTH_SHORT).show();
            }
        });
        if (removable(e.pkg)) {
            // The system's own uninstall prompt asks for confirmation; Home rebuilds on the package broadcast.
            menuRow(card, R.drawable.g_trash, "Gỡ ứng dụng", () -> {
                dialog.dismiss();
                try {
                    activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_DELETE,
                            android.net.Uri.fromParts("package", e.pkg, null)));
                } catch (Exception ex) {
                    Toast.makeText(activity, "Không mở được trình gỡ ứng dụng", Toast.LENGTH_SHORT).show();
                }
            });
        }
        UiFont.apply(card);
        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.45f);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Beside the tile: to its right when there is room, else to its left; top aligned with it.
            int[] at = new int[2];
            anchor.getLocationOnScreen(at);
            int screenW = activity.getResources().getDisplayMetrics().widthPixels;
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.TOP | Gravity.START;
            int right = at[0] + anchor.getWidth() + dp(12);
            lp.x = right + dp(260) <= screenW - dp(20) ? right : Math.max(dp(20), at[0] - dp(12) - dp(260));
            lp.y = Math.max(dp(20), Math.min(at[1] - dp(8), screenH - dp(330)));
            w.setAttributes(lp);
        }
        dialog.setOnShowListener(d -> first.requestFocus());
        dialog.show();
    }

    private View menuRow(LinearLayout parent, int icon, String label, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.home_status_bg);
        row.setPadding(dp(10), 0, dp(12), 0);
        ImageView ic = new ImageView(activity);
        ic.setImageResource(icon);
        ic.setImageTintList(activity.getColorStateList(R.color.home_menu_icon));
        ic.setDuplicateParentStateEnabled(true);
        row.addView(ic, new LinearLayout.LayoutParams(dp(13), dp(13)));
        TextView t = text(label, R.dimen.home_rail_count_text, UiFont.bold(activity), 0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        t.setTextColor(activity.getColorStateList(R.color.pill_text));
        t.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(9);
        row.addView(t, lp);
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(30));
        rp.topMargin = dp(1);
        parent.addView(row, rp);
        return row;
    }

    /** "Đổi tên hiển thị": a name of the owner's own; empty restores the app's name. */
    private void rename(AppAdapter.Entry e) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(label(e));
        input.setSelectAllOnFocus(true);
        input.setHint(e.label);
        new AlertDialog.Builder(activity)
                .setTitle("Đổi tên hiển thị")
                .setMessage("Để trống để dùng lại tên gốc: " + e.label)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty() || n.equals(e.label)) names.remove(e.pkg);
                    else names.put(e.pkg, n);
                    JSONObject o = new JSONObject();
                    try {
                        for (Map.Entry<String, String> it : names.entrySet()) o.put(it.getKey(), it.getValue());
                    } catch (Exception ignored) {
                    }
                    prefs.edit().putString("names", o.toString()).apply();
                    lastFocusedKey = e.pkg;
                    rebuild(entries);
                    column.post(this::restoreFocus);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** False for apps built into the TV's system image (they cannot be uninstalled, only updated ones rolled back). */
    private boolean removable(String pkg) {
        try {
            android.content.pm.ApplicationInfo ai = activity.getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String groupTitle(String id) {
        for (Group g : groups) if (g.id.equals(id)) return g.title;
        return "Ứng dụng khác";
    }

    private long installTime(String pkg) {
        Long t = installed.get(pkg);
        if (t == null) {
            try {
                t = activity.getPackageManager().getPackageInfo(pkg, 0).firstInstallTime;
            } catch (Exception e) {
                t = 0L;
            }
            installed.put(pkg, t);
        }
        return t;
    }

    String sortLabel() {
        for (int i = 0; i < SORT_IDS.length; i++) if (SORT_IDS[i].equals(sortMode)) return SORT_NAMES[i];
        return SORT_NAMES[0];
    }

    String filterLabel() {
        return filter.isEmpty() ? "Tất cả" : groupTitle(filter);
    }

    /** "Thứ tự": how apps are ordered inside each group. */
    void showSortMenu(View anchor) {
        int selected = 0;
        for (int i = 0; i < SORT_IDS.length; i++) if (SORT_IDS[i].equals(sortMode)) selected = i;
        int[] icons = {R.drawable.g_sort, R.drawable.g_sort, R.drawable.g_sort};
        showChoices(anchor, "Thứ tự ứng dụng", SORT_NAMES, icons, selected, i -> {
            sortMode = SORT_IDS[i];
            prefs.edit().putString("sort", sortMode).apply();
            rebuild(entries);
            host.onViewOptionsChanged();
        });
    }

    /** "Lọc": every group, or just one (with the pinned row). */
    void showFilterMenu(View anchor) {
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        ids.add("");
        names.add("Tất cả nhóm");
        Map<String, Integer> counts = new HashMap<>();
        for (AppAdapter.Entry e : entries) counts.merge(groupOf(e.pkg), 1, Integer::sum);
        for (Group g : groups) {
            if (!counts.containsKey(g.id)) continue;
            ids.add(g.id);
            names.add(g.title + "  ·  " + counts.get(g.id));
        }
        if (counts.containsKey(OTHER)) {
            ids.add(OTHER);
            names.add("Ứng dụng khác  ·  " + counts.get(OTHER));
        }
        int[] icons = new int[ids.size()];
        for (int i = 0; i < icons.length; i++) icons[i] = i == 0 ? R.drawable.g_apps : iconOf(ids.get(i));
        showChoices(anchor, "Lọc theo nhóm", names.toArray(new String[0]), icons, Math.max(0, ids.indexOf(filter)), i -> {
            filter = ids.get(i);
            prefs.edit().putString("filter", filter).apply();
            rebuild(entries);
            host.onViewOptionsChanged();
            column.post(this::focusFirst);
            scroll.post(() -> scroll.scrollTo(0, 0));
        });
    }

    private int iconOf(String id) {
        for (Group g : groups) if (g.id.equals(id)) return g.icon;
        return R.drawable.g_apps;
    }

    /** A glass list under a header button; the current choice carries a check mark. */
    private void showChoices(View anchor, String title, String[] labels, int[] icons, int selected,
                             java.util.function.IntConsumer pick) {
        android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.home_bright_bg);
        card.setPadding(dp(10), dp(11), dp(10), dp(10));
        TextView head = text(title, R.dimen.home_rail_count_text, UiFont.bold(activity), accent);
        head.setPadding(dp(6), 0, 0, dp(7));
        card.addView(head);
        // More than six choices: two columns, so the list stays short enough to hang under the header.
        boolean twoColumns = labels.length > 6;
        int perColumn = twoColumns ? (labels.length + 1) / 2 : labels.length;
        LinearLayout columns = new LinearLayout(activity);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(columns);
        LinearLayout col = null;
        View first = null;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            if (i % perColumn == 0) {
                col = new LinearLayout(activity);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
                if (i > 0) cp.leftMargin = dp(6);
                columns.addView(col, cp);
            }
            View row = menuRow(col, icons[i], labels[i], () -> {
                dialog.dismiss();
                pick.accept(index);
            });
            if (i == selected) {
                ImageView check = new ImageView(activity);
                check.setImageResource(R.drawable.g_check);
                check.setImageTintList(activity.getColorStateList(R.color.home_menu_icon));
                check.setDuplicateParentStateEnabled(true);
                View spacer = new View(activity);
                ((LinearLayout) row).addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
                ((LinearLayout) row).addView(check, new LinearLayout.LayoutParams(dp(12), dp(12)));
                first = row;
            }
            if (first == null && i == labels.length - 1) first = row;
        }
        UiFont.apply(card);
        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        android.view.Window w = dialog.getWindow();
        int width = dp(twoColumns ? 470 : 250);
        if (w != null) {
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.45f);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int[] at = new int[2];
            anchor.getLocationOnScreen(at);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Math.max(dp(20), at[0] + anchor.getWidth() - width);
            lp.y = at[1] + anchor.getHeight() + dp(6);
            w.setAttributes(lp);
        }
        View focus = first;
        dialog.setOnShowListener(d -> { if (focus != null) focus.requestFocus(); });
        dialog.show();
    }

    String carriedLabel() { return carried == null ? null : labelOf(carried); }

    private String labelOf(String pkg) {
        for (AppAdapter.Entry e : entries) if (e.pkg.equals(pkg)) return label(e);
        return pkg;
    }

    /** "Nhóm mới": asks for a name and adds an empty group; Sort ▲ ▼ moves apps into it. */
    void newGroup() {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Tên nhóm");
        new AlertDialog.Builder(activity)
                .setTitle("Nhóm mới")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    try {
                        JSONArray custom = new JSONArray(prefs.getString("custom", "[]"));
                        custom.put(new JSONObject().put("id", "u" + System.currentTimeMillis()).put("title", name));
                        prefs.edit().putString("custom", custom.toString()).apply();
                    } catch (Exception ignored) {
                    }
                    loadState();
                    rebuild(entries);
                    Toast.makeText(activity, "Đã tạo nhóm “" + name + "” — dùng Sắp xếp ▲ ▼ để thêm ứng dụng",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
