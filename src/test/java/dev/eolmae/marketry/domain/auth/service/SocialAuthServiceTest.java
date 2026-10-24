package dev.eolmae.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.eolmae.marketry.common.event.UserSignedUpEvent;
import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.repository.UserAccessDailyRepository;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import dev.eolmae.marketry.domain.auth.repository.UserRefreshTokenRepository;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class SocialAuthServiceTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(restClientBuilder).build();
    private final AuthProperties properties = new AuthProperties();
    private final UserAccountRepository accountRepository = mock(UserAccountRepository.class);
    private final UserRefreshTokenRepository refreshTokenRepository = mock(UserRefreshTokenRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AppJwtService jwtService = mock(AppJwtService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final UserAccessDailyRepository accessRepository = mock(UserAccessDailyRepository.class);
    private final UserProfileService profileService = mock(UserProfileService.class);
    private final AuthService authService = new AuthService(
            restClientBuilder.build(),
            properties,
            accountRepository,
            refreshTokenRepository,
            eventPublisher,
            jwtService,
            jdbcTemplate,
            accessRepository,
            profileService);

    @Test
    @DisplayName("네이버 신규 가입은 제공자 식별자로 회원을 만들고 인증 요청의 state를 전달한다")
    void naverSignupUsesProviderIdentityAndPassesStateToTokenRequest() {
        properties.getNaver().setClientId("client-id");
        properties.getNaver().setClientSecret("client-secret");
        UserAccount user = UserAccount.create("https://nid.naver.com", "naver-subject", null);
        ReflectionTestUtils.setField(user, "id", 9L);
        when(accountRepository.findByIssuerAndSub("https://nid.naver.com", "naver-subject"))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(9L)).thenReturn(Optional.of(user));
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any()))
                .thenReturn(List.of(9L));
        when(jwtService.issueAccessToken(any())).thenReturn("access-token");
        server.expect(request -> {
                    assertThat(request.getURI().toString()).isEqualTo("https://nid.naver.com/oauth2.0/token");
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                })
                .andExpect(content().string(Matchers.containsString("state=state-value")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"provider-token\",\"refresh_token\":\"refresh\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertThat(request.getURI().toString()).isEqualTo("https://openapi.naver.com/v1/nid/me");
                    assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer provider-token");
                })
                .andRespond(withSuccess(
                        "{\"resultcode\":\"00\",\"message\":\"success\",\"response\":{\"id\":\"naver-subject\",\"name\":\"member\"}}",
                        MediaType.APPLICATION_JSON));

        AuthService.IssuedTokens tokens = authService.loginWithSocialProvider(
                "naver", "oauth-code", "state-value", "https://marketry.co.kr/api/auth/naver/callback");

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        verify(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any());
        verify(eventPublisher).publishEvent(any(Object.class));
        server.verify();
    }

    @Test
    @DisplayName("카카오 재로그인은 카카오 회원만 조회하고 확인된 이메일을 갱신한다")
    void kakaoLoginReusesOnlyKakaoIdentityAndAcceptsVerifiedEmail() {
        properties.getKakao().setClientId("client-id");
        properties.getKakao().setClientSecret("client-secret");
        UserAccount user = UserAccount.create("https://kauth.kakao.com", "12345", "same@example.com");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(accountRepository.findByIssuerAndSub("https://kauth.kakao.com", "12345"))
                .thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(any())).thenReturn("access-token");
        server.expect(request ->
                        assertThat(request.getURI().toString()).isEqualTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"provider-token\",\"refresh_token\":\"refresh\",\"token_type\":\"Bearer\",\"expires_in\":21599,\"scope\":\"account_email\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().toString()).isEqualTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess(
                        "{\"id\":12345,\"connected_at\":\"2026-01-01T00:00:00Z\",\"properties\":{\"nickname\":\"member\"},\"kakao_account\":{\"email\":\"same@example.com\",\"is_email_valid\":true,\"is_email_verified\":true}}",
                        MediaType.APPLICATION_JSON));

        AuthService.IssuedTokens tokens = authService.loginWithSocialProvider(
                "kakao", "oauth-code", "state-value", "https://marketry.co.kr/api/auth/kakao/callback");

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(user.getEmail()).isEqualTo("same@example.com");
        verify(jdbcTemplate, never()).query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any());
        verify(accountRepository, never()).findByIssuerAndSub("https://nid.naver.com", "12345");
        server.verify();
    }

    @Test
    @DisplayName("사용자 식별자가 없으면 회원과 세션을 만들지 않는다")
    void missingProviderIdentityFailsWithoutCreatingAnAccount() {
        properties.getKakao().setClientId("client-id");
        server.expect(anything())
                .andRespond(withSuccess("{\"access_token\":\"provider-token\"}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"id\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> authService.loginWithSocialProvider(
                        "kakao", "oauth-code", "state-value", "https://marketry.co.kr/api/auth/kakao/callback"))
                .hasMessageContaining("Social login could not be completed");
        verify(jdbcTemplate, never()).query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"access_token\":\"\"}", "{\"error\":\"invalid_grant\"}"})
    @DisplayName("토큰 발급 실패 응답은 세부 내용을 노출하지 않고 가입을 중단한다")
    void 토큰이_없으면_가입하지_않는다(String response) {
        properties.getKakao().setClientId("client-id");
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertUnauthorized("kakao");
        verify(jwtService, never()).issueAccessToken(any());
        server.verify();
    }

    @Test
    void 외부_API_오류는_일반_인증실패로_반환한다() {
        properties.getKakao().setClientId("client-id");
        server.expect(anything()).andRespond(withServerError().body("private-provider-detail"));

        assertUnauthorized("kakao");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "\"123\"", "1.5", "0", "-1", "9223372036854775808", "null", "{}"})
    void 잘못된_카카오_식별자를_거절한다(String identity) {
        expectProfile("kakao", "{\"id\":" + identity + "}");

        assertUnauthorized("kakao");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"resultcode\":\"01\",\"response\":{\"id\":\"subject\"}}",
                "{\"resultcode\":\"00\",\"response\":{\"id\":true}}",
                "{\"resultcode\":\"00\",\"response\":{\"id\":123}}",
                "{\"resultcode\":\"00\",\"response\":{\"id\":\" \"}}",
                "{\"resultcode\":\"00\"}",
                "null"
            })
    void 네이버_오류와_잘못된_식별자를_거절한다(String profile) {
        expectProfile("naver", profile);

        assertUnauthorized("naver");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"email\":\"unverified@example.com\",\"is_email_valid\":true,\"is_email_verified\":false}",
                "{\"email\":\"unverified@example.com\",\"is_email_valid\":false,\"is_email_verified\":true}"
            })
    void 카카오_신규가입은_이메일이_없거나_확인되지_않아도_허용한다(String account) {
        expectProfile("kakao", "{\"id\":12345,\"kakao_account\":" + account + "}");
        UserAccount user = existingUser("https://kauth.kakao.com", "12345", null);
        when(accountRepository.findByIssuerAndSub("https://kauth.kakao.com", "12345"))
                .thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any()))
                .thenReturn(List.of(7L));
        when(accountRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThat(login("kakao").accessToken()).isEqualTo("access-token");
        verify(jdbcTemplate)
                .query(
                        anyString(),
                        ArgumentMatchers.<RowMapper<Long>>any(),
                        org.mockito.ArgumentMatchers.eq("https://kauth.kakao.com"),
                        org.mockito.ArgumentMatchers.eq("12345"),
                        org.mockito.ArgumentMatchers.isNull());
        verify(eventPublisher).publishEvent(new UserSignedUpEvent(7L));
        server.verify();
    }

    @Test
    void 네이버_재로그인은_회원_생성없이_이메일을_갱신한다() {
        expectProfile(
                "naver", "{\"resultcode\":\"00\",\"response\":{\"id\":\"subject\",\"email\":\"new@example.com\"}}");
        UserAccount user = existingUser("https://nid.naver.com", "subject", "old@example.com");

        assertThat(login("naver").accessToken()).isEqualTo("access-token");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        server.verify();
    }

    @Test
    void 동시_가입에서_이미_만들어진_회원은_가입초기화를_반복하지_않는다() {
        expectProfile("naver", "{\"resultcode\":\"00\",\"response\":{\"id\":\"subject\"}}");
        UserAccount user = existingUser("https://nid.naver.com", "subject", null);
        when(accountRepository.findByIssuerAndSub("https://nid.naver.com", "subject"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(user));
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(login("naver").accessToken()).isEqualTo("access-token");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        server.verify();
    }

    @Test
    void 가입초기화_실패는_인증실패로_감추거나_세션을_발급하지_않는다() {
        expectProfile("naver", "{\"resultcode\":\"00\",\"response\":{\"id\":\"subject\"}}");
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any()))
                .thenReturn(List.of(7L));
        RuntimeException failure = new RuntimeException("signup initialization failed");
        doThrow(failure).when(eventPublisher).publishEvent(any(Object.class));

        assertThatThrownBy(() -> login("naver")).isSameAs(failure);
        verify(jwtService, never()).issueAccessToken(any());
        server.verify();
    }

    private UserAccount existingUser(String issuer, String subject, String email) {
        UserAccount user = UserAccount.create(issuer, subject, email);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(accountRepository.findByIssuerAndSub(issuer, subject)).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(any())).thenReturn("access-token");
        return user;
    }

    private void expectProfile(String provider, String profile) {
        if ("naver".equals(provider)) {
            properties.getNaver().setClientId("client-id");
            properties.getNaver().setClientSecret("client-secret");
        } else {
            properties.getKakao().setClientId("client-id");
        }
        server.expect(anything())
                .andRespond(withSuccess("{\"access_token\":\"provider-token\"}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(profile, MediaType.APPLICATION_JSON));
    }

    private AuthService.IssuedTokens login(String provider) {
        return authService.loginWithSocialProvider(
                provider, "oauth-code", "state-value", "https://marketry.co.kr/api/auth/" + provider + "/callback");
    }

    private void assertUnauthorized(String provider) {
        assertThatThrownBy(() -> login(provider)).isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
            assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(failure.getReason()).isEqualTo("Social login could not be completed.");
        });
        verify(jdbcTemplate, never()).query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), any(), any(), any());
    }
}
