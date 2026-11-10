package kr.co.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.properties.AuthProperties;
import org.junit.jupiter.api.Test;

class AppJwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issued_access_token_round_trips_user_identity_and_role() {
        AppJwtService service = serviceWithSecret("0123456789abcdef0123456789abcdef");
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(73L, Role.USER);

        String token = service.issueAccessToken(principal);

        assertThat(service.parse(token)).isEqualTo(principal);
    }

    @Test
    void changed_signature_is_rejected() {
        AppJwtService service = serviceWithSecret("0123456789abcdef0123456789abcdef");
        String[] parts = service.issueAccessToken(new AuthenticatedUserPrincipal(73L, Role.ADMIN))
                .split("\\.", -1);
        String signature = parts[2];
        parts[2] = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);

        assertThat(service.parse(String.join(".", parts))).isNull();
    }

    @Test
    void short_signing_secret_is_rejected() {
        AppJwtService service = serviceWithSecret("short");

        assertThatThrownBy(() -> service.issueAccessToken(new AuthenticatedUserPrincipal(73L, Role.USER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void issued_capture_token_round_trips_owner_id_as_a_read_only_user() {
        AppJwtService service = serviceWithSecret("0123456789abcdef0123456789abcdef");

        String token = service.issueCaptureToken(999999L);

        assertThat(service.parseCaptureToken(token))
                .isEqualTo(new AuthenticatedUserPrincipal(999999L, Role.USER, true));
    }

    @Test
    void capture_token_is_rejected_by_the_access_token_parser() {
        AppJwtService service = serviceWithSecret("0123456789abcdef0123456789abcdef");
        String token = service.issueCaptureToken(999999L);

        assertThat(service.parse(token)).isNull();
    }

    @Test
    void access_token_is_rejected_by_the_capture_token_parser() {
        AppJwtService service = serviceWithSecret("0123456789abcdef0123456789abcdef");
        String token = service.issueAccessToken(new AuthenticatedUserPrincipal(999999L, Role.ADMIN));

        assertThat(service.parseCaptureToken(token)).isNull();
    }

    @Test
    void expired_capture_token_is_rejected() {
        AppJwtService service = serviceWithSecret(SECRET);
        String expiredToken = captureTokenExpiringAt(Instant.now().getEpochSecond() - 1);

        assertThat(service.parseCaptureToken(expiredToken)).isNull();
    }

    // AppJwtService는 현재 시각을 직접 읽어 만료를 계산하므로(기존 access 토큰과 동일한 기존 패턴),
    // 이미 만료된 토큰을 직접 서명해 만들어 경계 조건을 검증한다.
    private String captureTokenExpiringAt(long expiresAtEpochSecond) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "marketry");
        claims.put("sub", "999999");
        claims.put("typ", "capture-readonly");
        claims.put("iat", expiresAtEpochSecond - 120);
        claims.put("exp", expiresAtEpochSecond);
        try {
            String encodedHeader = base64UrlEncode(objectMapper.writeValueAsBytes(header));
            String encodedClaims = base64UrlEncode(objectMapper.writeValueAsBytes(claims));
            String unsigned = encodedHeader + "." + encodedClaims;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = base64UrlEncode(mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
            return unsigned + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AppJwtService serviceWithSecret(String secret) {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret(secret);
        return new AppJwtService(properties, new ObjectMapper());
    }
}
