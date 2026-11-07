package kr.co.marketry.domain.auth.enums;

import java.net.URI;
import java.util.Locale;

/** 비회원 방문자가 들어온 곳. 주소 전체는 저장하지 않고 이 네 가지로만 나눈다. */
public enum VisitorSource {
    NAVER,
    GOOGLE,
    DIRECT,
    OTHER;

    private static final String OWN_HOST = "marketry.co.kr";

    /**
     * 꼬리표(utm_source)가 있으면 그것을, 없으면 이전 페이지(referrer) 주소의 도메인을 본다. 둘 다 없거나 우리 사이트 안에서 온 것이면 DIRECT다.
     * 읽을 수 없는 값은 OTHER로 둔다.
     */
    public static VisitorSource from(String referrerUrl, String utmSource) {
        if (utmSource != null && !utmSource.isBlank()) {
            return fromName(utmSource.strip().toLowerCase(Locale.ROOT));
        }
        if (referrerUrl == null || referrerUrl.isBlank()) {
            return DIRECT;
        }
        String host = hostOf(referrerUrl.strip());
        if (host == null) {
            return OTHER;
        }
        if (host.equals(OWN_HOST) || host.endsWith("." + OWN_HOST)) {
            return DIRECT;
        }
        return fromName(host);
    }

    private static VisitorSource fromName(String name) {
        if (name.contains("naver")) {
            return NAVER;
        }
        if (name.contains("google")) {
            return GOOGLE;
        }
        return OTHER;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
