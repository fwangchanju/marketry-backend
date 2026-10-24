package dev.eolmae.marketry.domain.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.event.UserSignedUpEvent;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketry.domain.auth.dto.ProfileResponse;
import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import dev.eolmae.marketry.domain.auth.entity.UserRefreshToken;
import dev.eolmae.marketry.domain.auth.exception.EmailRequiredException;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.repository.UserAccessDailyRepository;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import dev.eolmae.marketry.domain.auth.repository.UserRefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String LOCAL_TEST_ISSUER = UserAccount.LOCAL_TEST_ISSUER;
    private static final String LOCAL_TEST_SUBJECT = "local-test-member";
    // 테스트 회원은 실제 가입 번호(users.id 자동 번호)를 쓰지 않고 따로 정한 번호를 쓴다 — 실제 가입자 번호가 섞이거나 건너뛰지 않게 한다.
    private static final long LOCAL_TEST_USER_ID = 800001L;
    private static final int REFRESH_TOKEN_DAYS = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final AuthProperties authProperties;
    private final UserAccountRepository userAccountRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AppJwtService appJwtService;
    private final JdbcTemplate jdbcTemplate;
    private final UserAccessDailyRepository userAccessDailyRepository;
    private final UserProfileService userProfileService;

    @Transactional
    public IssuedTokens loginWithGoogle(String code, String codeVerifier, String redirectUri) {
        log.info("Google 로그인 콜백 진입");
        requireGoogleConfiguration();
        String idToken = exchangeCodeForIdToken(code, codeVerifier, redirectUri);
        Jwt googleUser = verifyGoogleIdToken(idToken);

        String issuer = googleUser.getIssuer().toString();
        String subject = googleUser.getSubject();
        String email = googleUser.getClaimAsString("email");
        if (subject == null
                || email == null
                || email.isBlank()
                || !Boolean.TRUE.equals(googleUser.getClaim("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "확인된 Google 계정 정보가 필요합니다.");
        }

        return loginWithProviderIdentity(issuer, subject, email);
    }

    @Transactional
    public IssuedTokens loginWithSocialProvider(String provider, String code, String state, String redirectUri) {
        String tokenUri;
        String clientId;
        String clientSecret;
        String profileUri;
        String issuer;
        if ("naver".equals(provider)) {
            tokenUri = "https://nid.naver.com/oauth2.0/token";
            clientId = authProperties.getNaver().getClientId();
            clientSecret = authProperties.getNaver().getClientSecret();
            profileUri = "https://openapi.naver.com/v1/nid/me";
            issuer = "https://nid.naver.com";
        } else if ("kakao".equals(provider)) {
            tokenUri = "https://kauth.kakao.com/oauth/token";
            clientId = authProperties.getKakao().getClientId();
            clientSecret = authProperties.getKakao().getClientSecret();
            profileUri = "https://kapi.kakao.com/v2/user/me";
            issuer = "https://kauth.kakao.com";
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social login provider is invalid.");
        }
        if (clientId.isBlank()
                || ("naver".equals(provider) && clientSecret.isBlank())
                || code == null
                || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social login could not be completed.");
        }

        String accessToken;
        String subject;
        String email;
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("client_id", clientId);
            if (!clientSecret.isBlank()) {
                body.add("client_secret", clientSecret);
            }
            if ("kakao".equals(provider)) {
                body.add("redirect_uri", redirectUri);
            }
            body.add("code", code);
            if ("naver".equals(provider)) {
                body.add("state", state);
            }
            var tokenResponse = restClient
                    .post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(SocialTokenResponse.class);
            accessToken = tokenResponse == null ? null : tokenResponse.accessToken();
            if (accessToken == null || accessToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social login could not be completed.");
            }
            var profile = restClient
                    .get()
                    .uri(profileUri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
            if ("naver".equals(provider)
                    && !"00".equals(profile.path("resultcode").asString())) {
                throw new IllegalArgumentException("Invalid profile response");
            }
            subject = socialSubject(profile, provider);
            email = socialEmail(profile, provider);
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("Missing provider identity");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social login could not be completed.");
        }
        if (email == null || email.isBlank()) {
            throw new EmailRequiredException();
        }
        return loginWithProviderIdentity(issuer, subject, email);
    }

    private String socialSubject(JsonNode profile, String provider) {
        if (profile == null) {
            return null;
        }
        JsonNode identity = "naver".equals(provider) ? profile.path("response").path("id") : profile.path("id");
        if (identity.isMissingNode() || identity.isNull()) {
            return null;
        }
        if ("naver".equals(provider) && !identity.isString()) {
            return null;
        }
        if ("kakao".equals(provider)
                && (!identity.isIntegralNumber() || !identity.canConvertToLong() || identity.asLong() <= 0)) {
            return null;
        }
        return identity.isValueNode() ? identity.asString() : null;
    }

    private String socialEmail(JsonNode profile, String provider) {
        if (profile == null) {
            return null;
        }
        JsonNode email = "naver".equals(provider)
                ? profile.path("response").path("email")
                : profile.path("kakao_account").path("email");
        if (email.isMissingNode()
                || email.isNull()
                || !email.isString()
                || email.asString().isBlank()) {
            return null;
        }
        if ("kakao".equals(provider)
                && (!profile.path("kakao_account").path("is_email_valid").asBoolean(false)
                        || !profile.path("kakao_account")
                                .path("is_email_verified")
                                .asBoolean(false))) {
            return null;
        }
        return email.asString();
    }

    private record SocialTokenResponse(
            @JsonProperty("access_token") String accessToken) {}

    // 동시 가입에서 이번 요청이 실제로 삽입한 행만 판별한다.
    // ON CONFLICT DO NOTHING RETURNING은 Spring Data 파생 메서드와 QueryDSL로 표현할 수 없어 SQL을 사용한다.
    private IssuedTokens loginWithProviderIdentity(String issuer, String subject, String email) {
        UserAccount user =
                userAccountRepository.findByIssuerAndSub(issuer, subject).orElse(null);
        if (user == null) {
            List<Long> insertedIds =
                    jdbcTemplate.query("""
                    INSERT INTO users (issuer, sub, email, role, created_at, updated_at)
                    VALUES (?, ?, ?, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (issuer, sub) DO NOTHING
                    RETURNING id
                    """, (resultSet, rowNumber) -> resultSet.getLong(1), issuer, subject, email);
            if (!insertedIds.isEmpty()) {
                Long newUserId = insertedIds.getFirst();
                eventPublisher.publishEvent(new UserSignedUpEvent(newUserId));
                user = userAccountRepository.findById(newUserId).orElseThrow();
            } else {
                user = userAccountRepository.findByIssuerAndSub(issuer, subject).orElseThrow();
            }
        }
        user.updateEmail(email);
        return createTokens(user);
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션을 갱신할 수 없습니다.");
        }
        UserRefreshToken token = userRefreshTokenRepository
                .findByTokenHash(hash(rawRefreshToken))
                .filter(refreshToken -> refreshToken.isUsableAt(LocalDateTime.now(Zone.KST.zoneId())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션을 갱신할 수 없습니다."));
        token.replace();
        return createTokens(token.getUser());
    }

    // 로컬 개발 전용 로그인(DevLoginController)에서만 호출한다. 소유자 계정으로 구글 로그인과 동일한
    // 토큰 발급 경로(createTokens)를 태운다.
    @Transactional
    public IssuedTokens loginAsForDevelopment(Long userId) {
        UserAccount user = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.DEV_LOGIN_OWNER_NOT_FOUND, userId));
        return createTokens(user);
    }

    // 테스트 회원은 한 명만 둔다 — 없으면 정해 둔 번호로 만들고(가입 초기화 포함), 있으면 그 회원으로 로그인한다.
    @Transactional
    public IssuedTokens signupForDevelopment() {
        List<Long> insertedIds = jdbcTemplate.query(
                """
                INSERT INTO users (id, issuer, sub, email, role, created_at, updated_at)
                OVERRIDING SYSTEM VALUE
                VALUES (?, ?, ?, ?, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                RETURNING id
                """,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                LOCAL_TEST_USER_ID,
                LOCAL_TEST_ISSUER,
                LOCAL_TEST_SUBJECT,
                "local-test-member@example.invalid");
        if (!insertedIds.isEmpty()) {
            eventPublisher.publishEvent(new UserSignedUpEvent(insertedIds.getFirst()));
        }
        UserAccount user = userAccountRepository
                .findByIssuerAndSub(LOCAL_TEST_ISSUER, LOCAL_TEST_SUBJECT)
                .orElseThrow();
        return createTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        // findByTokenHash가 PESSIMISTIC_WRITE로 행을 먼저 잠근다. 동시에 다른 탭이 같은 토큰으로 갱신
        // 중이면 그 트랜잭션이 끝난 뒤(새 토큰 커밋 후) 아래 일괄 폐기가 그 새 토큰까지 잡아낸다.
        UserRefreshToken token = userRefreshTokenRepository
                .findByTokenHash(hash(rawRefreshToken))
                .orElse(null);
        if (token == null) {
            return;
        }
        token.revoke();
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        LocalDateTime cutoff = now.minus(UserRefreshToken.REPLACEMENT_GRACE);
        userRefreshTokenRepository.revokeTokensCreatedOrReplacedSince(
                token.getUser().getId(), cutoff, now);
    }

    @Transactional
    public void cleanupRefreshTokens() {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        LocalDateTime staleCutoff = now.minusDays(1);
        int deletedCount = userRefreshTokenRepository.deleteExpiredOrStaleTokens(now, staleCutoff);
        log.info("갱신 토큰 정리 완료: deletedCount={}", deletedCount);
    }

    // 화면을 열 때의 세션 확인도 접속으로 본다 — 접속 시각 기록(회원당 5분에 한 번만 DB에 쓴다) 때문에 읽기 전용이 아니다.
    @Transactional
    public AuthSessionResponse session(AuthenticatedUserPrincipal principal) {
        if (principal == null) {
            return AuthSessionResponse.anonymous();
        }
        return userAccountRepository
                .findById(principal.userId())
                .map(user -> {
                    recordAccess(user);
                    return toSessionResponse(user);
                })
                .orElseGet(AuthSessionResponse::anonymous);
    }

    private AuthSessionResponse toSessionResponse(UserAccount user) {
        ProfileResponse profile = userProfileService.getProfile(user.getId());
        return new AuthSessionResponse(
                true,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                profile.nickname(),
                profile.imageVersion(),
                profile.nicknameChangeableAt(),
                user.isSpecial(),
                user.getIssuer());
    }

    // 마지막 접속 시각과 일별 접속 기록을 같은 간격(회원당 5분에 한 번)으로 남긴다.
    private void recordAccess(UserAccount user) {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        if (user.recordAccess(now)) {
            userAccessDailyRepository.recordAccess(user.getId(), now);
        }
    }

    private IssuedTokens createTokens(UserAccount user) {
        recordAccess(user);
        var principal = new AuthenticatedUserPrincipal(user.getId(), user.getRole());
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        userRefreshTokenRepository.save(UserRefreshToken.create(
                user, hash(refreshToken), LocalDateTime.now(Zone.KST.zoneId()).plusDays(REFRESH_TOKEN_DAYS)));
        return new IssuedTokens(appJwtService.issueAccessToken(principal), refreshToken);
    }

    private String exchangeCodeForIdToken(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", authProperties.getGoogle().getClientId());
        body.add("client_secret", authProperties.getGoogle().getClientSecret());
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");
        body.add("code_verifier", codeVerifier);
        try {
            var response = restClient
                    .post()
                    .uri(GOOGLE_TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<GoogleTokenResponse>() {});
            if (response == null || response.idToken() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증 응답을 확인할 수 없습니다.");
            }
            return response.idToken();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다.");
        }
    }

    private Jwt verifyGoogleIdToken(String idToken) {
        try {
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER));
            Jwt jwt = decoder.decode(idToken);
            OAuth2TokenValidatorResult audienceValidation = jwt.getAudience()
                            .contains(authProperties.getGoogle().getClientId())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token", "Google token audience mismatch", null));
            if (audienceValidation.hasErrors()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증 토큰이 유효하지 않습니다.");
            }
            return jwt;
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증 토큰이 유효하지 않습니다.");
        }
    }

    private void requireGoogleConfiguration() {
        if (authProperties.getGoogle().getClientId().isBlank()
                || authProperties.getGoogle().getClientSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google 로그인이 설정되지 않았습니다.");
        }
        if (authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "JWT 서명 키가 설정되지 않았습니다.");
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 처리에 실패했습니다.", e);
        }
    }

    private record GoogleTokenResponse(String id_token) {

        String idToken() {
            return id_token;
        }
    }

    public record IssuedTokens(String accessToken, String refreshToken) {}
}
