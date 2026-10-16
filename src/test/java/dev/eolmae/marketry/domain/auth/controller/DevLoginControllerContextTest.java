package dev.eolmae.marketry.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthService;
import dev.eolmae.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

/**
 * DevLoginController(로컬 개발 전용 로그인)는 IP 화이트리스트 인증 브릿지(PR #132에서 제거)를 대체하므로
 * 운영 환경에 절대 떠서는 안 된다. 가드가 둘이다: {@code @Profile("!prod")}와
 * {@code auth.dev-login.enabled=true}(기본 application.properties에서 true로 고정, prod는
 * application-prod.properties가 false로 덮어씀). 이 둘이 실제로 독립적으로 작동하는지 빈 등록 여부로
 * 직접 확인한다.
 */
class DevLoginControllerContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevLoginController.class)
            .withBean(AuthService.class, () -> mock(AuthService.class))
            .withBean(AppJwtService.class, () -> mock(AppJwtService.class))
            .withBean(AuthCookies.class, AuthCookies::new)
            .withBean(
                    MarketryProperties.class, () -> new MarketryProperties("http://localhost:8081", 999999L, 900000L));

    @Test
    void local_프로필에서만_신규회원_초기화와_로그인_쿠키를_발급한다() {
        runner.withPropertyValues("auth.dev-login.enabled=true", "spring.profiles.active=local")
                .run(context -> {
                    AuthService authService = context.getBean(AuthService.class);
                    AppJwtService jwtService = context.getBean(AppJwtService.class);
                    var principal = new AuthenticatedUserPrincipal(42L, Role.USER);
                    var session =
                            new AuthSessionResponse(true, 42L, "test@example.invalid", Role.USER, null, null, null);
                    when(authService.signupForDevelopment())
                            .thenReturn(new AuthService.IssuedTokens("test-access", "test-refresh"));
                    when(jwtService.parse("test-access")).thenReturn(principal);
                    when(authService.session(principal)).thenReturn(session);
                    var response = new MockHttpServletResponse();

                    assertThat(context.getBean(DevLoginController.class).devSignup(response))
                            .isEqualTo(session);
                    assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
                    assertThat(response.getHeaders("Set-Cookie"))
                            .allMatch(cookie -> cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"));
                });
    }

    @Test
    void local이_아니면_신규회원_생성은_404이고_계정을_만들지_않는다() {
        runner.withPropertyValues("auth.dev-login.enabled=true").run(context -> {
            assertThatThrownBy(() -> context.getBean(DevLoginController.class).devSignup(new MockHttpServletResponse()))
                    .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
            verifyNoInteractions(context.getBean(AuthService.class), context.getBean(AppJwtService.class));
        });
    }

    @Test
    void local과_prod가_함께_있어도_개발_인증은_활성화되지_않는다() {
        runner.withPropertyValues("auth.dev-login.enabled=true", "spring.profiles.active=local,prod")
                .run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
    }

    @Test
    void 프로퍼티가_true이고_prod_프로필이_아니면_빈이_등록된다() {
        runner.withPropertyValues("auth.dev-login.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevLoginController.class));
    }

    @Test
    void prod_프로필이면_프로퍼티가_true여도_빈이_등록되지_않는다() {
        runner.withPropertyValues("auth.dev-login.enabled=true", "spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
    }

    @Test
    void 프로퍼티가_false면_prod가_아니어도_빈이_등록되지_않는다() {
        runner.withPropertyValues("auth.dev-login.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
    }

    @Test
    void 프로퍼티가_없으면_빈이_등록되지_않는다() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
    }
}
