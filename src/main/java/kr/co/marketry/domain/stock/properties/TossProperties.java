package kr.co.marketry.domain.stock.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossProperties(String clientId, String clientSecret) {
    @Override
    public String toString() {
        return "TossProperties[credentials=redacted]";
    }
}
