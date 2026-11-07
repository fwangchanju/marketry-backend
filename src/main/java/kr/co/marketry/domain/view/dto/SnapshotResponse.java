package kr.co.marketry.domain.view.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SnapshotResponse<T>(LocalDateTime snapshotTime, List<T> items) {
    public static <T> SnapshotResponse<T> empty() {
        return new SnapshotResponse<>(null, List.of());
    }
}
