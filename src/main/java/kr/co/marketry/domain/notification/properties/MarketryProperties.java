package kr.co.marketry.domain.notification.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketry")
public record MarketryProperties(String baseUrl, Long ownerUserId, Long publishedUserId) {

    /** 로그인 사용자가 없을 때(스케줄러 등) 커스텀 데이터는 운영자가 올린 MARKETRY 고정본(발행 사용자)을 쓴다 —
     * currentUserId가 null이면 publishedUserId를 쓴다. */
    public Long userIdOrPublished(Long currentUserId) {
        return currentUserId == null ? publishedUserId : currentUserId;
    }
}
