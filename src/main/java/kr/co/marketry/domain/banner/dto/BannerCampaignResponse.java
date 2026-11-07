package kr.co.marketry.domain.banner.dto;

import java.time.LocalDateTime;
import kr.co.marketry.domain.banner.enums.BannerType;

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
