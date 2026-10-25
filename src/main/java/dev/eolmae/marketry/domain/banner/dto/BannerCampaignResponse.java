package dev.eolmae.marketry.domain.banner.dto;

import dev.eolmae.marketry.domain.banner.enums.BannerType;
import java.time.LocalDateTime;

public record BannerCampaignResponse(
        Long id,
        BannerType type,
        String title,
        String text,
        String linkUrl,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status,
        LocalDateTime updatedAt,
        boolean hasImage,
        String imageContentType,
        int textScalePercent,
        boolean textBold) {}
