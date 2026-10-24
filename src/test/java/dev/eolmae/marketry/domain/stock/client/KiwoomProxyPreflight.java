package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import dev.eolmae.marketry.config.ApplicationConfig;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceRequest;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceResponse;
import dev.eolmae.marketry.domain.stock.properties.KiwoomProperties;
import dev.eolmae.marketry.domain.stock.util.KiwoomValueParser;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/** 앱·DB를 기동하지 않는 프록시 사전 검사. 입력은 프로세스에 주입된 값만 사용한다. */
final class KiwoomProxyPreflight {

    private static final int DEFAULT_PROXY_PORT = 8888;

    private KiwoomProxyPreflight() {}

    static BigDecimal run(Map<String, String> environment) {
        KiwoomProperties properties = properties(environment);
        if (System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank() == false) {
            throw new AssertionError("인증 정보 노출을 막기 위해 JDK HTTP 요청 로깅을 끄고 실행하세요.");
        }

        Map<Logger, Level> previousLevels = new LinkedHashMap<>();
        for (String name : new String[] {
            Logger.ROOT_LOGGER_NAME,
            ApplicationConfig.class.getName(),
            KiwoomTokenManager.class.getName(),
            KiwoomApiClient.class.getName(),
            "org.springframework.web.client",
            "org.springframework.http"
        }) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            previousLevels.put(logger, logger.getLevel());
            logger.setLevel(Level.OFF);
        }

        try {
            var client = new ApplicationConfig().kiwoomRestClient(properties);
            var tokenManager = new KiwoomTokenManager(properties, client);
            String token = request("프록시 인증", tokenManager::getToken);
            assertThat(token != null && token.isBlank() == false)
                    .as("프록시 인증에서 접근 토큰을 받아야 한다")
                    .isTrue();

            var query = new SectorCurrentPriceRequest("0", "001");
            // 응답에 연속조회 헤더가 있어도 이번 검사는 첫 페이지 조회 한 번만 수행한다.
            SectorCurrentPriceResponse response = request("프록시 조회", () -> client.post()
                    .uri("https://api.kiwoom.com" + query.path())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.set("appkey", properties.appKey());
                        headers.set("secretkey", properties.secret());
                        headers.set("api-id", query.apiId());
                    })
                    .body(query)
                    .retrieve()
                    .body(SectorCurrentPriceResponse.class));
            assertThat(response != null).as("읽기 전용 지수 조회 응답이 있어야 한다").isTrue();
            assertThat("0".equals(response.returnCode()))
                    .as("읽기 전용 지수 조회의 성공 코드")
                    .isTrue();
            BigDecimal indexValue =
                    KiwoomValueParser.parseBigDecimal(response.curPrc()).abs();
            assertThat(indexValue.signum() > 0)
                    .as("읽기 전용 지수 조회에 숫자 데이터가 있어야 한다")
                    .isTrue();
            return indexValue;
        } finally {
            previousLevels.forEach(Logger::setLevel);
        }
    }

    static KiwoomProperties properties(Map<String, String> environment) {
        if ("1".equals(environment.get("MARKETRY_KIWOOM_TOKEN_TEST")) == false) {
            throw new AssertionError("기존 운영 토큰 영향을 확인한 뒤 MARKETRY_KIWOOM_TOKEN_TEST=1로 발급을 허용하세요.");
        }
        String host = required(environment, "MARKETRY_PROXY_HOST");
        if (host.matches("[a-zA-Z0-9.-]+") == false) {
            throw new AssertionError("MARKETRY_PROXY_HOST에는 주소만 입력하세요. URL·포트·경로는 포함하지 않습니다.");
        }
        String portValue = environment.getOrDefault("MARKETRY_PROXY_PORT", "").strip();
        int port = DEFAULT_PROXY_PORT;
        if (portValue.isBlank() == false) {
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException ignored) {
                throw new AssertionError("MARKETRY_PROXY_PORT는 1~65535 사이의 정수여야 합니다.");
            }
        }
        if (port < 1 || port > 65535) {
            throw new AssertionError("MARKETRY_PROXY_PORT는 1~65535 사이의 정수여야 합니다.");
        }
        return new KiwoomProperties(
                required(environment, "KIWOOM_APP_KEY"), required(environment, "KIWOOM_APP_SECRET"), host, port);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError(name + " 설정이 필요합니다. 직접 연결로 대신하지 않습니다.");
        }
        return value.strip();
    }

    static <T> T request(String stage, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException ignored) {
            // 원래 예외·cause에는 응답과 인증 정보가 들어갈 수 있어 테스트 보고서에도 전달하지 않는다.
            throw new AssertionError(stage + " 실패: 프록시 연결·키움 허용 IP·인증 설정을 확인하세요.");
        }
    }
}
