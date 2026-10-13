package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.properties.TossProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossStockListClientTest {
    private static final String BASE_URL = "https://openapi.tossinvest.com";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final RestClient restClient = builder.build();
    private final TossTokenManager tokens =
            new TossTokenManager(new TossProperties("test-client", "test-secret"), restClient, Clock.systemUTC());
    private final TossStockListClient client = new TossStockListClient(tokens, restClient, 0, 0);

    @Test
    void 코스피와_코스닥의_상장폐지_종목코드를_모은다() {
        expectToken();
        expectList("KOSPI", "DELISTED", "{\"result\":[{\"symbol\":\"111111\"},{\"symbol\":\"222222\"}]}");
        expectList("KOSPI", "ACTIVE", "{\"result\":[{\"symbol\":\"005930\"}]}");
        expectList("KOSDAQ", "DELISTED", "{\"result\":[{\"symbol\":\"333333\"}]}");
        expectList("KOSDAQ", "ACTIVE", "{\"result\":[{\"symbol\":\"035720\"}]}");

        assertThat(client.fetchDelistedStockCodes()).containsExactlyInAnyOrder("111111", "222222", "333333");
        server.verify();
    }

    @Test
    void 같은_코드가_상장_중으로도_있으면_폐지_목록에서_뺀다() {
        expectToken();
        expectList("KOSPI", "DELISTED", "{\"result\":[{\"symbol\":\"111111\"},{\"symbol\":\"222222\"}]}");
        expectList("KOSPI", "ACTIVE", "{\"result\":[{\"symbol\":\"111111\"}]}");
        expectList("KOSDAQ", "DELISTED", "{\"result\":[]}");
        expectList("KOSDAQ", "ACTIVE", "{\"result\":[]}");

        assertThat(client.fetchDelistedStockCodes()).containsExactly("222222");
        server.verify();
    }

    @Test
    void HTTP_오류는_안전한_상태번호만_로그에_남긴다() {
        expectToken();
        server.expect(requestTo(BASE_URL + "/api/v1/stocks/all?market=KOSPI&status=DELISTED"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("test-secret"));

        assertThatThrownBy(client::fetchDelistedStockCodes)
                .isInstanceOf(BadRequestException.class)
                .hasCause(null)
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("TOSS_STOCK_LIST_FETCH_FAILED", "429")
                        .doesNotContain("test-secret", "token-1"));
        server.verify();
    }

    @Test
    void 응답에_result가_없으면_실패로_본다() {
        expectToken();
        expectList("KOSPI", "DELISTED", "{}");

        assertThatThrownBy(client::fetchDelistedStockCodes)
                .isInstanceOf(BadRequestException.class)
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("RESPONSE_INVALID"));
        server.verify();
    }

    @Test
    void 거래정지_종목은_200개씩_나눠_조회하고_KRX_거래정지만_모은다() {
        expectToken();
        var codes = java.util.stream.IntStream.rangeClosed(1, 201)
                .mapToObj(index -> String.format("%06d", index))
                .toList();
        server.expect(requestTo(BASE_URL + "/api/v1/stocks?symbols=" + String.join(",", codes.subList(0, 200))))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess(
                        "{\"result\":["
                                + "{\"symbol\":\"000001\",\"koreanMarketDetail\":{\"krxTradingSuspended\":true}},"
                                + "{\"symbol\":\"000002\",\"koreanMarketDetail\":{\"krxTradingSuspended\":false}},"
                                + "{\"symbol\":\"000003\",\"koreanMarketDetail\":null}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/stocks?symbols=000201"))
                .andRespond(withSuccess(
                        "{\"result\":[{\"symbol\":\"000201\",\"koreanMarketDetail\":{\"krxTradingSuspended\":true}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetchSuspendedStockCodes(codes)).containsExactlyInAnyOrder("000001", "000201");
        server.verify();
    }

    @Test
    void 거래정지_조회가_실패하면_안전한_상태번호만_로그에_남긴다() {
        expectToken();
        server.expect(requestTo(BASE_URL + "/api/v1/stocks?symbols=005930"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("test-secret"));

        assertThatThrownBy(() -> client.fetchSuspendedStockCodes(java.util.List.of("005930")))
                .isInstanceOf(BadRequestException.class)
                .hasCause(null)
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("TOSS_STOCK_LIST_FETCH_FAILED", "500")
                        .doesNotContain("test-secret"));
        server.verify();
    }

    private void expectToken() {
        server.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withSuccess("{\"access_token\":\"token-1\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
    }

    private void expectList(String market, String status, String body) {
        server.expect(requestTo(BASE_URL + "/api/v1/stocks/all?market=" + market + "&status=" + status))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
