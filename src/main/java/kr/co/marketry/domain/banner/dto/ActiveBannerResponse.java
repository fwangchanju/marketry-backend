package kr.co.marketry.domain.banner.dto;

import java.time.LocalDateTime;
import kr.co.marketry.domain.banner.enums.BannerType;

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
