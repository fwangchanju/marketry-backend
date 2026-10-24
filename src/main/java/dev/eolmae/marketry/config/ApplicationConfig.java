package dev.eolmae.marketry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.common.cache.CacheKey;
import dev.eolmae.marketry.domain.stock.properties.KiwoomProperties;
import jakarta.persistence.EntityManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class ApplicationConfig {

    public static final String CACHE_MANAGER = "cacheManager";

    // 외부 HTTPS 콜드 TLS 핸드셰이크(하루 첫 호출 등)엔 3초가 너무 빡빡해서 10초로 늘렸다 — 그래도
    // 무한 대기로 스케줄러가 전면 정지되는 건 충분히 막는다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean(CACHE_MANAGER)
    @Primary
    public CacheManager cacheManager() {
        return new CaffeineCacheManager(CacheKey.STOCK_INFO, CacheKey.CLOSING_PRICE);
    }

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }

    /** KrxCrawler 등 나머지 전부가 쓰는 기본 클라이언트. @Qualifier 없이 주입받는 곳은 전부 이 빈이다. */
    @Bean
    @Primary
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(10)))
                .build();
    }

    // 키움과 토스 호출만 고정 IP 프록시를 거친다. 값이 없으면 직접 연결한다.
    @Bean
    public RestClient kiwoomRestClient(KiwoomProperties kiwoomProperties) {
        return proxyRestClient("키움", kiwoomProperties);
    }

    // 토스도 허용 IP로만 호출되므로 키움과 같은 프록시 설정(kiwoom.proxy-host/port)을 쓴다.
    @Bean
    public RestClient tossRestClient(KiwoomProperties kiwoomProperties) {
        return proxyRestClient("토스", kiwoomProperties);
    }

    @Bean
    public RestClient telegramRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(30)))
                .build();
    }

    // page.goto 30초 + waitForSelector 15초(containers/renderer/server.js)를 감안한 값 — 짧게 걸면
    // 일일 리포트 스크린샷이 항상 실패한다.
    @Bean
    public RestClient rendererRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(90)))
                .build();
    }

    static Optional<InetSocketAddress> kiwoomProxyAddress(KiwoomProperties properties) {
        String host = properties.proxyHost();
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(InetSocketAddress.createUnresolved(host.strip(), properties.proxyPort()));
    }

    private RestClient proxyRestClient(String serviceName, KiwoomProperties kiwoomProperties) {
        Optional<InetSocketAddress> proxyAddress = kiwoomProxyAddress(kiwoomProperties);
        if (proxyAddress.isEmpty()) {
            return RestClient.builder()
                    .requestFactory(requestFactory(Duration.ofSeconds(10)))
                    .build();
        }
        InetSocketAddress address = proxyAddress.get();
        log.info("{} 호출 프록시 사용: {}:{}", serviceName, address.getHostString(), address.getPort());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .proxy(ProxySelector.of(address))
                .build();
        return RestClient.builder()
                .requestFactory(requestFactory(httpClient, Duration.ofSeconds(10)))
                .build();
    }

    private ClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        return requestFactory(httpClient, readTimeout);
    }

    private ClientHttpRequestFactory requestFactory(HttpClient httpClient, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    // 웹 직렬화는 Jackson 3(tools.jackson)을 쓰고 이 빈은 Jackson 2(com.fasterxml.jackson)라 웹
    // 레이어와 무관하다. KrxCrawler/TelegramClient/CustomSectorTreeService가 내부용으로 직접 쓴다.
    @Bean
    public ObjectMapper internalObjectMapper() {
        return new ObjectMapper();
    }
}
