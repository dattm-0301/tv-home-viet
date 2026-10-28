package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The Home's interface font, a launcher setting with three choices: Comfortaa (default), Baloo 2 and Lexend.
 * Layouts name the placeholders @font/ui_medium and @font/ui_bold (Comfortaa files); apply() swaps every TextView
 * using them for the chosen family. Numbers stay Playfair Display and script accents Dancing Script.
 */
final class UiFont {
    static final String KEY = "ui_font";
    static final String COMFORTAA = "Comfortaa", BALOO = "Baloo 2", LEXEND = "Lexend";
    private static final String[] ALL = {COMFORTAA, BALOO, LEXEND};

    private UiFont() { }

    static String current(Context c) {
        String v = c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, COMFORTAA);
        for (String f : ALL) if (f.equals(v)) return f;
        return COMFORTAA;
    }

    static String next(String font) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(font)) return ALL[(i + 1) % ALL.length];
        return COMFORTAA;
    }

    static Typeface medium(Context c) {
        switch (current(c)) {
            case BALOO: return c.getResources().getFont(R.font.ui_baloo_medium);
            case LEXEND: return c.getResources().getFont(R.font.ui_lexend_medium);
            default: return c.getResources().getFont(R.font.ui_medium);
        }
    }

    static Typeface bold(Context c) {
        switch (current(c)) {
            case BALOO: return c.getResources().getFont(R.font.ui_baloo_bold);
            case LEXEND: return c.getResources().getFont(R.font.ui_lexend_bold);
            default: return c.getResources().getFont(R.font.ui_bold);
        }
    }

    /** Replaces the placeholder typefaces under root with the chosen family (nothing to do for Comfortaa). */
    static void apply(View root) {
        Context c = root.getContext();
        if (COMFORTAA.equals(current(c))) return;
        swap(root, c.getResources().getFont(R.font.ui_medium), c.getResources().getFont(R.font.ui_bold),
                medium(c), bold(c));
    }

    private static void swap(View v, Typeface fromMedium, Typeface fromBold, Typeface toMedium, Typeface toBold) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Typeface face = t.getTypeface();
            if (face == fromMedium) t.setTypeface(toMedium);
            else if (face == fromBold) t.setTypeface(toBold);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) swap(g.getChildAt(i), fromMedium, fromBold, toMedium, toBold);
        }
    }
}
