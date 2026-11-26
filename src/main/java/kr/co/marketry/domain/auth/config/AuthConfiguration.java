package kr.co.marketry.domain.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import kr.co.marketry.domain.auth.properties.AuthProperties;
import kr.co.marketry.domain.auth.service.AuthTokenFilter;
import kr.co.marketry.domain.auth.service.OriginCheckFilter;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    FilterRegistrationBean<AuthTokenFilter> authTokenFilterServletRegistration(AuthTokenFilter filter) {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<OriginCheckFilter> originCheckFilterServletRegistration(OriginCheckFilter filter) {
        FilterRegistrationBean<OriginCheckFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http, AuthTokenFilter authTokenFilter, OriginCheckFilter originCheckFilter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**", "/actuator/health")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/metrics/client-events")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")
                        // 종목명 약칭 지정은 관리자 전용이다 — 종목 이름을 바꿔 악용할 소지가 있어 일반 사용자에게 열지 않는다.
                        // 아래 /api/custom/** 규칙보다 앞에 둬야 한다(먼저 일치한 규칙이 적용된다).
                        .requestMatchers("/api/custom/stock-sectors/*/alias")
                        .hasRole("ADMIN")
                        // 일본·미국 내 기준은 회원에게 공개하기 전까지 관리자만 쓴다. 아래 /api/custom/** 규칙보다 앞에 둔다.
                        .requestMatchers(AuthConfiguration::isOtherMarketClassification)
                        .hasRole("ADMIN")
                        .requestMatchers("/api/custom/**")
                        .authenticated()
                        // 일본·미국 조회(GET)는 2026-11-16부터 비로그인도 본다(사용자 결정, 전체 공개). 그 밖의 메서드는 로그인이 필요하다.
                        .requestMatchers(HttpMethod.GET, "/api/japan/**", "/api/usa/**")
                        .permitAll()
                        .requestMatchers("/api/japan/**", "/api/usa/**")
                        .authenticated()
                        .requestMatchers("/api/profile", "/api/profile/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(originCheckFilter, AuthTokenFilter.class)
                .build();
    }

    private static final RequestMatcher CLASSIFICATION_PATHS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/custom/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/api/map/excluded-sectors/**"));

    /** 한국이 아닌 시장의 내 기준 요청인지. 주소는 앱이 컨트롤러를 찾을 때와 같은 방식으로 견준다(인코딩한 주소로 피해 가지 못하게). */
    static boolean isOtherMarketClassification(HttpServletRequest request) {
        String[] markets = request.getParameterValues("market");
        return markets != null
                && Arrays.stream(markets)
                        .anyMatch(market -> !ClassificationMarket.KOREA.name().equals(market))
                && CLASSIFICATION_PATHS.matches(request);
    }
}
