package dev.eolmae.marketry.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.domain.stock.properties.KiwoomProperties;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class ApplicationConfigTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("프록시 host가 null, 빈 문자열, 공백이면 프록시를 쓰지 않는다")
    void host가_비어_있으면_프록시가_없다(String host) {
        KiwoomProperties properties = new KiwoomProperties("key", "secret", host, 8888);

        Optional<InetSocketAddress> result = ApplicationConfig.kiwoomProxyAddress(properties);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("프록시 host가 있으면 그 host와 port로 프록시를 쓴다")
    void host가_있으면_그_host와_port로_프록시를_쓴다() {
        KiwoomProperties properties = new KiwoomProperties("key", "secret", "proxy.example.com", 8888);

        Optional<InetSocketAddress> result = ApplicationConfig.kiwoomProxyAddress(properties);

        assertThat(result).hasValueSatisfying(address -> {
            assertThat(address.getHostString()).isEqualTo("proxy.example.com");
            assertThat(address.getPort()).isEqualTo(8888);
        });
    }

    @Test
    @DisplayName("프록시 host가 있어도 토스 RestClient가 정상 생성된다")
    void host가_있으면_프록시를_거치는_토스_RestClient가_생성된다() {
        ApplicationConfig config = new ApplicationConfig();
        KiwoomProperties properties = new KiwoomProperties("key", "secret", "proxy.example.com", 8888);

        RestClient result = config.tossRestClient(properties);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("프록시 host가 비어 있어도 토스 RestClient가 직접 연결용으로 정상 생성된다")
    void host가_비어_있으면_직접_연결하는_토스_RestClient가_생성된다() {
        ApplicationConfig config = new ApplicationConfig();
        KiwoomProperties properties = new KiwoomProperties("key", "secret", "", 8888);

        RestClient result = config.tossRestClient(properties);

        assertThat(result).isNotNull();
    }
}
