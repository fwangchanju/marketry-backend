package dev.eolmae.marketry.domain.banner.dto;

import dev.eolmae.marketry.domain.banner.enums.BannerType;
import java.time.LocalDateTime;

public record ActiveBannerResponse(
        boolean active,
        Long id,
        BannerType type,
        String title,
        String text,
        String linkUrl,
        String imageUrl,
        String imageContentType,
        LocalDateTime nextRefreshAt,
        int textScalePercent,
        boolean textBold) {
    public static ActiveBannerResponse empty() {
        return new ActiveBannerResponse(false, null, null, null, null, null, null, null, null, 100, false);
    }
}
