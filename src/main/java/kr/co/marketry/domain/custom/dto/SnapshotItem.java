package kr.co.marketry.domain.custom.dto;

import java.time.LocalDateTime;

public record SnapshotItem(Long id, String label, LocalDateTime createdAt, LocalDateTime updatedAt) {}
