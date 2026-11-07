package kr.co.marketry.domain.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import kr.co.marketry.domain.auth.dto.AuthSessionResponse;
import kr.co.marketry.domain.auth.exception.EmailRequiredException;
import kr.co.marketry.domain.auth.properties.AuthProperties;
import kr.co.marketry.domain.auth.service.AppJwtService;
import kr.co.marketry.domain.auth.service.AuthService;
import kr.co.marketry.domain.auth.service.AuthService.IssuedTokens;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.auth.service.CurrentUser;
import kr.co.marketry.domain.auth.service.VisitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String STATE_COOKIE = "mm_oauth_state";
    private static final String VERIFIER_COOKIE = "mm_oauth_verifier";
    private static final String RETURN_TO_COOKIE = "mm_oauth_return";
    private static final String REFRESH_COOKIE = "mm_refresh";
    private static final String CAPTURE_TOKEN_HEADER = "X-Capture-Token";
    private static final String CALLBACK_PATH = "/api/auth/google/callback";
    private static final String NAVER_CALLBACK_PATH = "/api/auth/naver/callback";
    private static final String KAKAO_CALLBACK_PATH = "/api/auth/kakao/callback";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final AppJwtService appJwtService;
    private final AuthCookies authCookies;
    private final VisitorService visitorService;

    @GetMapping("/google")
    public RedirectView beginGoogleLogin(
            @RequestParam(required = false) String returnTo, HttpServletResponse response) {
        if (authProperties.getGoogle().getClientId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google 로그인이 설정되지 않았습니다.");
        }
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(48);
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        String redirectUri = callbackUri();
        String authorizationUrl = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", authProperties.getGoogle().getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
        authCookies.add(response, STATE_COOKIE, state, "/api/auth/google", Duration.ofMinutes(10));
        authCookies.add(response, VERIFIER_COOKIE, verifier, "/api/auth/google", Duration.ofMinutes(10));
        authCookies.add(
                response,
                RETURN_TO_COOKIE,
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(safeReturnTo(returnTo).getBytes(StandardCharsets.UTF_8)),
                "/api/auth/google",
                Duration.ofMinutes(10));
        return new RedirectView(authorizationUrl);
    }

    @GetMapping("/google/callback")
    public RedirectView completeGoogleLogin(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        String expectedState = cookie(request, STATE_COOKIE);
        String verifier = cookie(request, VERIFIER_COOKIE);
        if (expectedState == null
                || verifier == null
                || verifier.isBlank()
                || !MessageDigest.isEqual(
                        expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth state 검증에 실패했습니다.");
        }

        authCookies.expire(response, STATE_COOKIE, "/api/auth/google");
        authCookies.expire(response, VERIFIER_COOKIE, "/api/auth/google");
        String returnTo = decodedReturnTo(cookie(request, RETURN_TO_COOKIE));
        authCookies.expire(response, RETURN_TO_COOKIE, "/api/auth/google");
        IssuedTokens tokens = authService.loginWithGoogle(code, verifier, callbackUri());
        authCookies.setSessionCookies(response, tokens);
        return new RedirectView(authProperties.getFrontendUrl() + returnTo);
    }

    @GetMapping("/social/providers")
    @ResponseBody
    public SocialProviders socialProviders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return new SocialProviders(
                !authProperties.getGoogle().getClientId().isBlank()
                        && !authProperties.getGoogle().getClientSecret().isBlank(),
                !authProperties.getNaver().getClientId().isBlank()
                        && !authProperties.getNaver().getClientSecret().isBlank(),
                !authProperties.getKakao().getClientId().isBlank());
    }

    @GetMapping("/naver")
    public RedirectView beginNaverLogin(@RequestParam(required = false) String returnTo, HttpServletResponse response) {
        return beginSocialLogin("naver", returnTo, response);
    }

    @GetMapping("/kakao")
    public RedirectView beginKakaoLogin(@RequestParam(required = false) String returnTo, HttpServletResponse response) {
        return beginSocialLogin("kakao", returnTo, response);
    }

    @GetMapping("/naver/callback")
    public RedirectView completeNaverLogin(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {
        return completeSocialLogin("naver", code, state, error, request, response);
    }

    @GetMapping("/kakao/callback")
    public RedirectView completeKakaoLogin(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {
        return completeSocialLogin("kakao", code, state, error, request, response);
    }

    private RedirectView beginSocialLogin(String provider, String returnTo, HttpServletResponse response) {
        if (!isSocialProviderAvailable(provider)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Social login provider is unavailable.");
        }
        String state = randomUrlToken(32);
        String callbackUri = socialCallbackUri(provider);
        String authorizationEndpoint = "naver".equals(provider)
                ? "https://nid.naver.com/oauth2.0/authorize"
                : "https://kauth.kakao.com/oauth/authorize";
        String clientId = "naver".equals(provider)
                ? authProperties.getNaver().getClientId()
                : authProperties.getKakao().getClientId();
        String authorizationUrl = authorizationEndpoint + "?response_type=code&client_id="
                + UriUtils.encodeQueryParam(clientId, StandardCharsets.UTF_8) + "&redirect_uri="
                + UriUtils.encodeQueryParam(callbackUri, StandardCharsets.UTF_8) + "&state="
                + UriUtils.encodeQueryParam(state, StandardCharsets.UTF_8);
        String cookiePath = "/api/auth/" + provider;
        authCookies.add(response, "mm_" + provider + "_state", state, cookiePath, Duration.ofMinutes(10));
        authCookies.add(
                response,
                "mm_" + provider + "_return",
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(safeReturnTo(returnTo).getBytes(StandardCharsets.UTF_8)),
                cookiePath,
                Duration.ofMinutes(10));
        return new RedirectView(authorizationUrl);
    }

    private RedirectView completeSocialLogin(
            String provider,
            String code,
            String state,
            String error,
            HttpServletRequest request,
            HttpServletResponse response) {
        String cookiePath = "/api/auth/" + provider;
        String stateCookie = "mm_" + provider + "_state";
        String returnCookie = "mm_" + provider + "_return";
        String returnTo = decodedReturnTo(cookie(request, returnCookie));
        authCookies.expire(response, stateCookie, cookiePath);
        authCookies.expire(response, returnCookie, cookiePath);
        String result = isCancellation(error) ? "cancelled" : "oauth_failed";
        String expectedState = cookie(request, stateCookie);
        if (error == null
                && code != null
                && !code.isBlank()
                && state != null
                && expectedState != null
                && MessageDigest.isEqual(
                        expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            try {
                IssuedTokens tokens =
                        authService.loginWithSocialProvider(provider, code, state, socialCallbackUri(provider));
                authCookies.setSessionCookies(response, tokens);
                return new RedirectView(authProperties.getFrontendUrl() + returnTo);
            } catch (EmailRequiredException e) {
                result = "email_required";
            } catch (ResponseStatusException e) {
                // Provider response details can contain authorization codes or tokens; keep the redirect generic.
            }
        }
        String redirectUri = UriComponentsBuilder.fromUriString(returnTo)
                .replaceQueryParam("loginError", result)
                .encode()
                .build()
                .toUriString();
        return new RedirectView(authProperties.getFrontendUrl() + redirectUri);
    }

    private boolean isCancellation(String error) {
        return "access_denied".equals(error) || "user_cancelled".equals(error) || "cancelled".equals(error);
    }

    private boolean isSocialProviderAvailable(String provider) {
        return switch (provider) {
            case "naver" ->
                !authProperties.getNaver().getClientId().isBlank()
                        && !authProperties.getNaver().getClientSecret().isBlank();
            case "kakao" -> !authProperties.getKakao().getClientId().isBlank();
            default -> false;
        };
    }

    private String socialCallbackUri(String provider) {
        String frontendUrl = authProperties.getFrontendUrl();
        if (frontendUrl != null && isCanonicalProductionUrl(frontendUrl)) {
            return "https://marketry.co.kr" + ("naver".equals(provider) ? NAVER_CALLBACK_PATH : KAKAO_CALLBACK_PATH);
        }
        String path = "naver".equals(provider) ? NAVER_CALLBACK_PATH : KAKAO_CALLBACK_PATH;
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
    }

    private boolean isCanonicalProductionUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "marketry.co.kr".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public record SocialProviders(boolean google, boolean naver, boolean kakao) {}

    @GetMapping("/session")
    @ResponseBody
    public AuthSessionResponse session(HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUserPrincipal principal = CurrentUser.current();
        if (principal != null) {
            return authService.session(principal);
        }
        String refreshToken = cookie(request, REFRESH_COOKIE);
        if (request.getHeader(CAPTURE_TOKEN_HEADER) != null) {
            return AuthSessionResponse.anonymous();
        }
        if (refreshToken == null) {
            visitorService.record(request);
            return AuthSessionResponse.anonymous();
        }
        try {
            return refreshSession(refreshToken, response);
        } catch (ResponseStatusException e) {
            authCookies.expire(response, REFRESH_COOKIE, "/api/auth");
            visitorService.record(request);
            return AuthSessionResponse.anonymous();
        }
    }

    @PostMapping("/refresh")
    @ResponseBody
    public AuthSessionResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return refreshSession(cookie(request, REFRESH_COOKIE), response);
    }

    private AuthSessionResponse refreshSession(String rawRefreshToken, HttpServletResponse response) {
        IssuedTokens tokens = authService.refresh(rawRefreshToken);
        authCookies.setSessionCookies(response, tokens);
        return authService.session(appJwtService.parse(tokens.accessToken()));
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookie(request, REFRESH_COOKIE));
        authCookies.expireSessionCookies(response);
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    private String callbackUri() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(CALLBACK_PATH)
                .toUriString();
    }

    private String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return "/";
        }
        if (!returnTo.startsWith("/")
                || returnTo.startsWith("//")
                || returnTo.contains("\\")
                || returnTo.contains("://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 로그인 복귀 경로입니다.");
        }
        return returnTo;
    }

    private String decodedReturnTo(String encoded) {
        if (encoded == null) {
            return "/";
        }
        try {
            return safeReturnTo(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    private String randomUrlToken(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("OAuth code verifier를 처리할 수 없습니다.", e);
        }
    }

    private String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
