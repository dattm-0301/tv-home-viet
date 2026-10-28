package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/** Source identity and timestamp rules; independent from Android and network I/O. */
final class PriceRules {
    static String goldMillions(long value) {
        return new java.text.DecimalFormat("0.0##", java.text.DecimalFormatSymbols.getInstance(new Locale("vi", "VN")))
                .format(value / 1_000_000d);
    }
    static boolean publisherMatches(String dealer, String publisher) {
        if (!Arrays.asList("doji", "pnj", "phuquy", "btmh").contains(dealer) || publisher == null) return false;
        String normalized = Normalizer.normalize(publisher, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('đ', 'd').replaceAll("[^a-z0-9]", "");
        return dealer.equals(normalized);
    }
    static boolean fresh(long now, long checked, long ttl) {
        return checked > 0 && now >= checked && now - checked <= ttl;
    }
    static boolean due(long now, long attempted, long interval) {
        return attempted <= 0 || now < attempted || now - attempted >= interval;
    }
    static boolean replaceGold(long incoming, boolean fallback, long previous, boolean previousFallback,
                               long previousCheck, long now, long primaryGrace) {
        if (previous <= 0) return true;
        // An observation time and a fallback's check time are different clocks, not an ordering.
        if (fallback != previousFallback) {
            return !fallback || !fresh(now, previousCheck, primaryGrace);
        }
        return incoming >= previous;
    }
}
