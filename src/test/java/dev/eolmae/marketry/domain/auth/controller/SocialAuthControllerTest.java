package dev.eolmae.marketry.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthService;
import dev.eolmae.marketry.domain.auth.service.AuthService.IssuedTokens;
import dev.eolmae.marketry.domain.auth.service.VisitorService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class SocialAuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthProperties properties = new AuthProperties();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                    authService, properties, mock(AppJwtService.class), new AuthCookies(), mock(VisitorService.class)))
            .build();

    @Test
    @DisplayName("설정된 로그인 제공자만 사용 가능으로 응답하며 응답을 캐시하지 않는다")
    void providerAvailabilityReflectsConfiguredCredentials() throws Exception {
        properties.getGoogle().setClientId("google-id");
        properties.getGoogle().setClientSecret("google-secret");
        properties.getKakao().setClientId("kakao-id");

        mockMvc.perform(get("/api/auth/social/providers"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.google").value(true))
                .andExpect(jsonPath("$.naver").value(false))
                .andExpect(jsonPath("$.kakao").value(true));
    }

    @Test
    @DisplayName("네이버 로그인 시작은 등록된 콜백과 제공자 전용 상태 쿠키를 사용한다")
    void naverStartUsesConfiguredCallbackAndProviderScopedStateCookie() throws Exception {
        properties.getNaver().setClientId("naver-client");
        properties.getNaver().setClientSecret("naver-secret");
        properties.setFrontendUrl("https://marketry.co.kr");

        mockMvc.perform(get("/api/auth/naver").param("returnTo", "/account?tab=profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                                "Location",
                                containsString("redirect_uri=https://marketry.co.kr/api/auth/naver/callback")))
                .andExpect(header().string("Set-Cookie", containsString("mm_naver_state=")));
    }

    @Test
    @DisplayName("로그인 취소는 기존 쿼리와 해시를 보존하고 인증을 진행하지 않는다")
    void cancellationRedirectPreservesExistingQueryAndFragment() throws Exception {
        String returnCookie = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("/account?tab=login#social".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/auth/kakao/callback")
                        .param("error", "access_denied")
                        .cookie(new Cookie("mm_kakao_return", returnCookie), new Cookie("mm_kakao_state", "state")))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        header().string("Location", containsString("/account?tab=login&loginError=cancelled#social")));
        verify(authService, never()).loginWithSocialProvider(any(), any(), any(), any());
    }

    @Test
    @DisplayName("상태 불일치는 인증 요청 없이 일반 실패 안내로 돌려보낸다")
    void invalidStateRedirectsGenericFailureWithoutCallingProviderLogin() throws Exception {
        String returnCookie =
                Base64.getUrlEncoder().withoutPadding().encodeToString("/".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/auth/naver/callback")
                        .param("code", "authorization-code")
                        .param("state", "wrong")
                        .cookie(new Cookie("mm_naver_return", returnCookie), new Cookie("mm_naver_state", "expected")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("loginError=oauth_failed")));
        verify(authService, never()).loginWithSocialProvider(any(), any(), any(), any());
    }

    @Test
    @DisplayName("올바른 상태의 콜백은 세션 쿠키를 발급하고 검증한 상태를 전달한다")
    void validStateIssuesSessionCookiesAndSendsStateToService() throws Exception {
        String returnCookie =
                Base64.getUrlEncoder().withoutPadding().encodeToString("/".getBytes(StandardCharsets.UTF_8));
        when(authService.loginWithSocialProvider(any(), any(), any(), any()))
                .thenReturn(new IssuedTokens("access", "refresh"));

        mockMvc.perform(get("/api/auth/naver/callback")
                        .param("code", "code")
                        .param("state", "state")
                        .cookie(new Cookie("mm_naver_return", returnCookie), new Cookie("mm_naver_state", "state")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:5173/"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("mm_access=access"))));
        verify(authService)
                .loginWithSocialProvider("naver", "code", "state", "http://localhost/api/auth/naver/callback");
    }

    @ParameterizedTest
    @ValueSource(strings = {"naver", "kakao"})
    void 운영_콜백은_요청_Host와_무관하게_등록된_주소를_사용한다(String provider) throws Exception {
        configureProviders();
        properties.setFrontendUrl("https://marketry.co.kr");

        mockMvc.perform(get("/api/auth/" + provider).header("Host", "untrusted.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                                "Location",
                                containsString(
                                        "redirect_uri=https://marketry.co.kr/api/auth/" + provider + "/callback")))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/api/auth/" + provider))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"naver", "kakao"})
    void 설정되지_않은_제공자로_로그인을_시작하지_않는다(String provider) throws Exception {
        mockMvc.perform(get("/api/auth/" + provider)).andExpect(status().isServiceUnavailable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"//external.example", "https://external.example", "/\\external.example"})
    void 외부_복귀_경로를_거절한다(String returnTo) throws Exception {
        configureProviders();

        mockMvc.perform(get("/api/auth/naver").param("returnTo", returnTo)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"naver", "kakao"})
    void 상태가_없으면_서비스를_호출하거나_세션을_발급하지_않는다(String provider) throws Exception {
        mockMvc.perform(get("/api/auth/" + provider + "/callback").param("code", "code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("loginError=oauth_failed")))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getHeaders("Set-Cookie"))
                        .noneMatch(value -> value.startsWith("mm_access=") || value.startsWith("mm_refresh=")));
        verify(authService, never()).loginWithSocialProvider(any(), any(), any(), any());
    }

    @Test
    void 다른_제공자의_상태_쿠키를_인증에_사용하지_않는다() throws Exception {
        mockMvc.perform(get("/api/auth/kakao/callback")
                        .param("code", "code")
                        .param("state", "state")
                        .cookie(new Cookie("mm_naver_state", "state")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("loginError=oauth_failed")));
        verify(authService, never()).loginWithSocialProvider(any(), any(), any(), any());
    }

    @Test
    void 인증_API_실패는_세부_정보없이_안내하고_임시_쿠키를_만료한다() throws Exception {
        when(authService.loginWithSocialProvider(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "private-provider-detail"));

        mockMvc.perform(get("/api/auth/naver/callback")
                        .param("code", "code")
                        .param("state", "state")
                        .cookie(new Cookie("mm_naver_state", "state")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:5173/?loginError=oauth_failed"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Max-Age=0"))));
    }

    private void configureProviders() {
        properties.getNaver().setClientId("naver-client");
        properties.getNaver().setClientSecret("naver-secret");
        properties.getKakao().setClientId("kakao-client");
    }
}
