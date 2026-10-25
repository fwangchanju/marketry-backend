package dev.eolmae.marketry.domain.banner.dto;

import java.time.LocalDateTime;

public record BannerRotationSettingResponse(int intervalSeconds, boolean saved, LocalDateTime updatedAt) {}
