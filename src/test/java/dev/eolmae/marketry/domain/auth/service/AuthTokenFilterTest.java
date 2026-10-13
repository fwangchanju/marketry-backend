package dev.eolmae.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.auth.enums.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthTokenFilterTest {

    private final AppJwtService appJwtService = mock(AppJwtService.class);
    private final AuthTokenFilter filter = new AuthTokenFilter(appJwtService);

    @BeforeEach
    void clearSecurityContextBeforeEach() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContextAfterEach() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidJwtDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = request("/api/admin/watch-stocks");
        request.addHeader("Authorization", "Bearer invalid-token");
        when(appJwtService.parse("invalid-token")).thenReturn(null);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/custom/preferences", "/api/auth/session"})
    void validCaptureTokenAuthenticatesGetRequestsAsAReadOnlyUser(String path) throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new AuthenticatedUserPrincipal(42L, Role.USER));
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void invalidCaptureTokenIsRejectedWithUnauthorized() throws Exception {
        when(appJwtService.parseCaptureToken("capture-token")).thenReturn(null);
        MockHttpServletRequest request = captureRequest("GET", "/api/auth/session");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void captureTokenRejectsNonGetRequests(String method) throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest(method, "/api/custom/preferences");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/admin/watch-stocks",
            })
    void captureTokenRejectsAdminRoutes(String path) throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void captureTokenRejectsRefreshEvenAsAGetRequest() throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", "/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void captureTokenRejectsLogoutEvenAsAGetRequest() throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", "/api/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void captureTokenHeaderIsIgnoredWhenReadingFromCookies() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/custom/preferences");
        request.setServletPath("/api/custom/preferences");
        request.setCookies(new Cookie("X-Capture-Token", "capture-token"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest captureRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("X-Capture-Token", "capture-token");
        return request;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
