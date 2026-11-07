package kr.co.marketry.domain.view.enums;

import java.util.Locale;

public enum AverageMode {
    WEIGHTED,
    SIMPLE;

    public String queryValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
