package kr.co.marketry.domain.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.properties.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppJwtService {

    private static final String ISSUER = "marketry";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long ACCESS_TOKEN_SECONDS = 15 * 60;
    private static final long CAPTURE_TOKEN_SECONDS = 2 * 60;
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_CAPTURE = "capture-readonly";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public String issueAccessToken(AuthenticatedUserPrincipal user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", user.userId().toString());
        claims.put("typ", TOKEN_TYPE_ACCESS);
        claims.put("role", user.role().name());
        claims.put("iat", now);
        claims.put("exp", now + ACCESS_TOKEN_SECONDS);
        return sign(claims);
    }

    // 텔레그램 렌더러가 /capture 요청 본문으로만 전달하는 짧은 수명의 소유자 캡처 토큰이다. role 클레임을
    // 두지 않아 소유자 계정이 실제로 ADMIN이어도 이 토큰으로는 ROLE_ADMIN 권한이 생기지 않는다.
    public String issueCaptureToken(Long ownerUserId) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", ownerUserId.toString());
        claims.put("typ", TOKEN_TYPE_CAPTURE);
        claims.put("iat", now);
        claims.put("exp", now + CAPTURE_TOKEN_SECONDS);
        return sign(claims);
    }

    public AuthenticatedUserPrincipal parse(String token) {
        Map<String, Object> claims = verify(token);
        if (claims == null || !TOKEN_TYPE_ACCESS.equals(claims.get("typ"))) {
            return null;
        }
        Object subject = claims.get("sub");
        Object role = claims.get("role");
        if (!(subject instanceof String userId) || !(role instanceof String roleName)) {
            return null;
        }
        return new AuthenticatedUserPrincipal(Long.parseLong(userId), Role.valueOf(roleName));
    }

    public AuthenticatedUserPrincipal parseCaptureToken(String token) {
        Map<String, Object> claims = verify(token);
        if (claims == null || !TOKEN_TYPE_CAPTURE.equals(claims.get("typ"))) {
            return null;
        }
        Object subject = claims.get("sub");
        if (!(subject instanceof String userId)) {
            return null;
        }
        return new AuthenticatedUserPrincipal(Long.parseLong(userId), Role.USER, true);
    }

    // 서명·헤더·발급자·만료를 검증하고 클레임 맵을 돌려준다. typ별 필수 클레임 검증은 각 parse 메서드가 한다.
    private Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            String unsigned = parts[0] + "." + parts[1];
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(signature, sign(unsigned))) {
                return null;
            }
            Map<String, Object> header = decode(parts[0]);
            Map<String, Object> claims = decode(parts[1]);
            if (!"HS256".equals(header.get("alg")) || !ISSUER.equals(claims.get("iss"))) {
                return null;
            }
            Object expiresAt = claims.get("exp");
            if (!(expiresAt instanceof Number expiry)
                    || expiry.longValue() <= Instant.now().getEpochSecond()) {
                return null;
            }
            return claims;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sign(Map<String, Object> claims) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        String unsigned = encode(header) + "." + encode(claims);
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(unsigned));
    }

    private String encode(Object value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 생성에 실패했습니다.", e);
        }
    }

    private Map<String, Object> decode(String encoded) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        return objectMapper.readValue(bytes, new TypeReference<>() {});
    }

    private byte[] sign(String value) {
        try {
            byte[] key = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) {
                throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 bytes.");
            }
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명을 생성할 수 없습니다.", e);
        }
    }
}
