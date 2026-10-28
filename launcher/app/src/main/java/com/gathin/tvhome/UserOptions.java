package com.gathin.tvhome;

import java.time.ZoneId;

/** Validation shared by the TV form and saved personal options. */
final class UserOptions {
    static String zone(String id) {
        if ("system".equals(id)) return ZoneId.systemDefault().getId();
        if (!ZoneId.getAvailableZoneIds().contains(id)) throw new java.time.DateTimeException("Múi giờ IANA không hợp lệ");
        return ZoneId.of(id).getId();
    }
    static double coordinate(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Tọa độ ngoài phạm vi");
        return value;
    }
    static int integer(double value, int min, int max) {
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max)
            throw new IllegalArgumentException("Giá trị cần là số nguyên từ " + min + " đến " + max);
        return (int) value;
    }
}
