package dev.eolmae.marketry.domain.auth.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eolmae.marketry.domain.auth.dto.NicknameAvailabilityResponse;
import dev.eolmae.marketry.domain.auth.dto.ProfileResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketry.domain.auth.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileControllerTest {

    private static final AuthenticatedUserPrincipal PRINCIPAL = new AuthenticatedUserPrincipal(42L, Role.USER);

    private final UserProfileService userProfileService = mock(UserProfileService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(userProfileService))
            .build();

    @BeforeEach
    void setAuthenticatedUser() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 프로필_조회는_닉네임_사진여부_사진버전을_돌려준다() throws Exception {
        when(userProfileService.getProfile(42L)).thenReturn(new ProfileResponse("마켓러", true, 1_700_000_000_000L, null));

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("마켓러"))
                .andExpect(jsonPath("$.hasImage").value(true))
                .andExpect(jsonPath("$.imageVersion").value(1_700_000_000_000L));
    }

    @Test
    void 닉네임_저장은_본문의_닉네임을_서비스에_넘긴다() throws Exception {
        when(userProfileService.updateNickname(42L, "마켓러")).thenReturn(new ProfileResponse("마켓러", false, null, null));

        mockMvc.perform(put("/api/profile/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"마켓러\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("마켓러"));
    }

    @Test
    void 닉네임_사용_가능_여부는_쿼리의_닉네임을_서비스에_넘기고_결과를_돌려준다() throws Exception {
        when(userProfileService.checkNicknameAvailability(42L, "마켓러"))
                .thenReturn(new NicknameAvailabilityResponse(true));

        mockMvc.perform(get("/api/profile/nickname/availability").param("nickname", "마켓러"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 사진_올리기는_file_파트의_바이트를_서비스에_넘긴다() throws Exception {
        byte[] bytes = {1, 2, 3};
        when(userProfileService.updateImage(eq(42L), eq(bytes))).thenReturn(new ProfileResponse(null, true, 1L, null));

        mockMvc.perform(multipart("/api/profile/image")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", bytes))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasImage").value(true));
    }

    @Test
    void 사진_삭제는_204를_돌려준다() throws Exception {
        mockMvc.perform(delete("/api/profile/image")).andExpect(status().isNoContent());

        verify(userProfileService).deleteImage(42L);
    }

    @Test
    void 사진_조회는_JPEG이고_브라우저에만_하루_캐시한다() throws Exception {
        byte[] image = {9, 8, 7};
        when(userProfileService.getImage(42L)).thenReturn(image);

        mockMvc.perform(get("/api/profile/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "max-age=86400, private"))
                .andExpect(content().bytes(image));
    }
}
