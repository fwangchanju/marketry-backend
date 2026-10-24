package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private final AuthProperties authProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") && isWriteMethod(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin == null || !allowedOrigins(request).contains(normalizeOrigin(origin))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "요청 출처를 확인할 수 없습니다.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isWriteMethod(String method) {
        return !HttpMethod.GET.matches(method)
                && !HttpMethod.HEAD.matches(method)
                && !HttpMethod.OPTIONS.matches(method);
    }

    private Set<String> allowedOrigins(HttpServletRequest request) {
        String requestOrigin = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if (!("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                && !("https".equalsIgnoreCase(request.getScheme()) && port == 443)) {
            requestOrigin += ":" + port;
        }
        Set<String> origins = new HashSet<>();
        origins.add(normalizeOrigin(requestOrigin));
        origins.add(normalizeOrigin(authProperties.getFrontendUrl()));
        addLoopbackFrontendOrigins(origins);
        return origins;
    }

    // 로컬 화면은 localhost와 127.0.0.1 두 주소로 열릴 수 있다. 설정된 프론트엔드 주소가 루프백일 때만
    // 같은 포트의 다른 루프백 별칭도 허용한다. 공개 도메인 설정에는 별칭을 추가하지 않는다.
    private void addLoopbackFrontendOrigins(Set<String> origins) {
        URI frontendUri;
        try {
            frontendUri = URI.create(authProperties.getFrontendUrl());
        } catch (Exception e) {
            return;
        }
        if (frontendUri.getHost() == null) {
            return;
        }
        String frontendHost = frontendUri.getHost().toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
        if (!LOOPBACK_HOSTS.contains(frontendHost)) {
            return;
        }
        for (String host : LOOPBACK_HOSTS) {
            try {
                origins.add(normalizeOrigin(new URI(
                                frontendUri.getScheme(), null, host, frontendUri.getPort(), null, null, null)
                        .toString()));
            } catch (Exception ignored) {
                // 잘못된 별칭은 허용 목록에 넣지 않는다.
            }
        }
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
                return "";
            }
            int port = uri.getPort();
            if (port == 80 && "http".equalsIgnoreCase(uri.getScheme())) {
                port = -1;
            }
            if (port == 443 && "https".equalsIgnoreCase(uri.getScheme())) {
                port = -1;
            }
            return new URI(
                            uri.getScheme().toLowerCase(Locale.ROOT),
                            null,
                            uri.getHost().toLowerCase(Locale.ROOT),
                            port,
                            null,
                            null,
                            null)
                    .toString();
        } catch (Exception e) {
            return "";
        }
    }
}
