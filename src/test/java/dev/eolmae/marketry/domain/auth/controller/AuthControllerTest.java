package dev.eolmae.marketry.domain.auth.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eolmae.marketry.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthService;
import dev.eolmae.marketry.domain.auth.service.AuthService.IssuedTokens;
import dev.eolmae.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerTest {

    private static final AuthenticatedUserPrincipal PRINCIPAL = new AuthenticatedUserPrincipal(42L, Role.USER);

    private final AuthService authService = mock(AuthService.class);
    private final AppJwtService appJwtService = mock(AppJwtService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new AuthController(authService, new AuthProperties(), appJwtService, new AuthCookies()))
            .build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @Test
    void session_인증된_사용자면_기존_세션을_반환하고_refresh를_호출하지_않는다() throws Exception {
        setAuthenticatedUser();
        when(authService.session(PRINCIPAL))
                .thenReturn(new AuthSessionResponse(true, 42L, "user@example.com", Role.USER, null, null, null));

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
        verify(authService, never()).refresh(any());
    }

    @Test
    void session_인증없고_갱신쿠키가_있으면_서버에서_갱신하고_로그인_상태를_반환한다() throws Exception {
        IssuedTokens tokens = new IssuedTokens("access-token", "rotated-refresh-token");
        when(authService.refresh("refresh-token")).thenReturn(tokens);
        when(appJwtService.parse("access-token")).thenReturn(PRINCIPAL);
        when(authService.session(PRINCIPAL))
                .thenReturn(new AuthSessionResponse(true, 42L, "user@example.com", Role.USER, null, null, null));

        mockMvc.perform(get("/api/auth/session").cookie(new Cookie("mm_refresh", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(header().stringValues(
                                "Set-Cookie",
                                containsInAnyOrder(
                                        containsString("mm_access=access-token"),
                                        containsString("mm_refresh=rotated-refresh-token"))));
    }

    @Test
    void session_갱신에_실패하면_익명_응답과_함께_갱신_쿠키를_만료시킨다() throws Exception {
        when(authService.refresh("stale-refresh-token"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/auth/session").cookie(new Cookie("mm_refresh", "stale-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("mm_refresh=;"))));
    }

    @Test
    void session_갱신_쿠키가_없으면_익명이고_refresh를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
        verify(authService, never()).refresh(any());
    }

    @Test
    void refresh_returnsJsonResponseAndRotatesCookies() throws Exception {
        IssuedTokens tokens = new IssuedTokens("access-token", "rotated-refresh-token");
        when(authService.refresh("refresh-token")).thenReturn(tokens);
        when(appJwtService.parse("access-token")).thenReturn(PRINCIPAL);
        when(authService.session(PRINCIPAL))
                .thenReturn(new AuthSessionResponse(true, 42L, "user@example.com", Role.USER, null, null, null));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("mm_refresh", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(header().stringValues(
                                "Set-Cookie",
                                containsInAnyOrder(
                                        containsString("mm_access=access-token"),
                                        containsString("mm_refresh=rotated-refresh-token"))));
    }
}
