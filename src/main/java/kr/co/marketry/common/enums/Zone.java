package kr.co.marketry.common.enums;

import java.time.ZoneId;

public enum Zone {
    KST("Asia/Seoul");

    private final String id;

    Zone(String id) {
        this.id = id;
    }

    public ZoneId zoneId() {
        return ZoneId.of(id);
    }
}
