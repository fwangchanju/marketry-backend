package kr.co.marketry.common.enums;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public enum DateTimePattern {
    DATE("yyyyMMdd"),
    TIME("HHmmss"),
    DATETIME("yyyyMMddHHmmss"),
    DATETIME_MINUTE_WITH_WEEKDAY("yyyy-MM-dd (E) HH:mm");

    private final DateTimeFormatter formatter;

    DateTimePattern(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern, Locale.KOREAN);
    }

    public DateTimeFormatter formatter() {
        return formatter;
    }
}
