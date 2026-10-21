package dev.eolmae.marketry.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthTokenFilter;
import dev.eolmae.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketry.domain.auth.service.OriginCheckFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

class AuthConfigurationTest {

    private final AuthConfiguration configuration = new AuthConfiguration();

    @Test
    void securityChainFiltersAreNotAlsoRegisteredByTheServletContainer() {
        FilterRegistrationBean<AuthTokenFilter> authRegistration =
                configuration.authTokenFilterServletRegistration(mock(AuthTokenFilter.class));
        FilterRegistrationBean<OriginCheckFilter> originRegistration =
                configuration.originCheckFilterServletRegistration(mock(OriginCheckFilter.class));

        assertThat(authRegistration.isEnabled()).isFalse();
        assertThat(originRegistration.isEnabled()).isFalse();
    }

    // DB 없이는 @SpringBootTest를 못 쓰므로(docs/rules/testing.md), @EnableWebSecurity만 올린 최소
    // 컨텍스트에서 실제 SecurityFilterChain을 만들어 FilterChainProxy로 직접 호출한다.
    @Test
    void 인증_없이_custom_API를_요청하면_401을_응답한다() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
            FilterChainProxy filterChainProxy = new FilterChainProxy(chain);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/custom/sectors");
            request.setServletPath("/api/custom/sectors");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filterChainProxy.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }
    }

    @Test
    void 인증_없이_프로필_API를_요청하면_모두_401을_응답한다() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            FilterChainProxy filterChainProxy = new FilterChainProxy(context.getBean(SecurityFilterChain.class));

            // 쓰기 요청은 Origin 검사(OriginCheckFilter)가 인가보다 먼저라서 허용된 Origin을 넣어야 401이 나온다.
            assertThat(statusOf(filterChainProxy, "GET", "/api/profile", null)).isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "GET", "/api/profile/image", null))
                    .isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "PUT", "/api/profile/nickname", "http://localhost"))
                    .isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "PUT", "/api/profile/image", "http://localhost"))
                    .isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "DELETE", "/api/profile/image", "http://localhost"))
                    .isEqualTo(401);
        }
    }

    @Test
    void 회원_목록은_관리자만_조회한다() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            FilterChainProxy filterChainProxy = new FilterChainProxy(context.getBean(SecurityFilterChain.class));
            AppJwtService appJwtService = context.getBean(AppJwtService.class);
            String userToken = appJwtService.issueAccessToken(new AuthenticatedUserPrincipal(1L, Role.USER));
            String adminToken = appJwtService.issueAccessToken(new AuthenticatedUserPrincipal(2L, Role.ADMIN));
            assertThat(statusOfWithToken(filterChainProxy, "GET", "/api/admin/members", null))
                    .isEqualTo(401);
            assertThat(statusOfWithToken(filterChainProxy, "GET", "/api/admin/members", userToken))
                    .isEqualTo(403);
            assertThat(statusOfWithToken(filterChainProxy, "GET", "/api/admin/members", adminToken))
                    .isEqualTo(200);
        }
    }

    @Test
    void 약칭_수정은_관리자만_허용한다() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            FilterChainProxy filterChainProxy = new FilterChainProxy(context.getBean(SecurityFilterChain.class));
            AppJwtService appJwtService = context.getBean(AppJwtService.class);
            String userToken = appJwtService.issueAccessToken(new AuthenticatedUserPrincipal(1L, Role.USER));
            String adminToken = appJwtService.issueAccessToken(new AuthenticatedUserPrincipal(2L, Role.ADMIN));
            String aliasPath = "/api/custom/stock-sectors/005930/alias";

            assertThat(statusOfWithToken(filterChainProxy, "PATCH", aliasPath, null))
                    .isEqualTo(401);
            assertThat(statusOfWithToken(filterChainProxy, "PATCH", aliasPath, userToken))
                    .isEqualTo(403);
            assertThat(statusOfWithToken(filterChainProxy, "DELETE", aliasPath, userToken))
                    .isEqualTo(403);
            assertThat(statusOfWithToken(filterChainProxy, "PATCH", aliasPath, adminToken))
                    .isEqualTo(200);
        }
    }

    @Test
    void 약칭이_아닌_커스텀_API는_일반_사용자도_허용한다() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            FilterChainProxy filterChainProxy = new FilterChainProxy(context.getBean(SecurityFilterChain.class));
            String userToken = context.getBean(AppJwtService.class)
                    .issueAccessToken(new AuthenticatedUserPrincipal(1L, Role.USER));

            assertThat(statusOfWithToken(filterChainProxy, "PUT", "/api/custom/stock-sectors/005930", userToken))
                    .isEqualTo(200);
        }
    }

    // 쓰기 요청이라 허용된 Origin을 넣고, 로그인 쿠키(mm_access)로 사용자를 흉내 낸다.
    private int statusOfWithToken(FilterChainProxy filterChainProxy, String method, String path, String accessToken)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("Origin", "http://localhost");
        if (accessToken != null) {
            request.setCookies(new Cookie("mm_access", accessToken));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterChainProxy.doFilter(request, response, new MockFilterChain());

        return response.getStatus();
    }

    private int statusOf(FilterChainProxy filterChainProxy, String method, String path, String origin)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterChainProxy.doFilter(request, response, new MockFilterChain());

        return response.getStatus();
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfig {

        @Bean
        AuthProperties authProperties() {
            AuthProperties properties = new AuthProperties();
            properties.setJwtSecret("test-jwt-secret-test-jwt-secret-test-jwt-secret");
            return properties;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AppJwtService appJwtService(AuthProperties authProperties, ObjectMapper objectMapper) {
            return new AppJwtService(authProperties, objectMapper);
        }

        @Bean
        AuthTokenFilter authTokenFilter(AppJwtService appJwtService) {
            return new AuthTokenFilter(appJwtService);
        }

        @Bean
        OriginCheckFilter originCheckFilter(AuthProperties authProperties) {
            return new OriginCheckFilter(authProperties);
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http, AuthTokenFilter authTokenFilter, OriginCheckFilter originCheckFilter)
                throws Exception {
            return new AuthConfiguration().authSecurityFilterChain(http, authTokenFilter, originCheckFilter);
        }
    }
}
