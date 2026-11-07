package kr.co.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.properties.TossProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossMarketCalendarClientTest {
    private static final String BASE_URL = "https://openapi.tossinvest.com";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final MutableClock clock = new MutableClock();
    private final TossMarketCalendarClient client =
            new TossMarketCalendarClient(new TossProperties("test-client", "test-secret"), builder.build(), clock);
    private final LocalDate date = LocalDate.of(2026, 10, 9);

    @Test
    void 토큰을재사용하고만료직전재발급한다() {
        expectToken("first");
        expectCalendar("first");
        expectCalendar("first");
        expectToken("second");
        expectCalendar("second");
        assertThat(client.fetch(date).result().today().date()).isEqualTo(date);
        clock.now = clock.now.plusSeconds(60);
        client.fetch(date);
        clock.now = clock.now.plusSeconds(30);
        client.fetch(date);
        server.verify();
    }

    @Test
    void 동시조회도토큰을한번만발급한다() throws Exception {
        expectToken("shared");
        expectCalendar("shared");
        expectCalendar("shared");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> client.fetch(date));
            var second = executor.submit(() -> client.fetch(date));
            assertThat(first.get().result().today().date()).isEqualTo(date);
            assertThat(second.get().result().today().date()).isEqualTo(date);
        }
        server.verify();
    }

    @Test
    void 인증실패는토큰무효화후다음시도에재발급하고원문을숨긴다() {
        expectToken("first");
        server.expect(requestTo(BASE_URL + "/api/v1/market-calendar/KR?date=" + date))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-response"));
        expectToken("second");
        expectCalendar("second");
        assertThatThrownBy(() -> client.fetch(date))
                .isInstanceOf(BadRequestException.class)
                .hasCause(null)
                .hasMessageNotContaining("secret-response")
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("TOSS_CALENDAR_FETCH_FAILED", "401")
                        .doesNotContain("secret-response", "first"));
        client.fetch(date);
        server.verify();
    }

    @Test
    void 인증미설정은네트워크요청을하지않는다() {
        var unconfigured = new TossMarketCalendarClient(new TossProperties(null, null), builder.build(), clock);
        assertThatThrownBy(() -> unconfigured.fetch(date))
                .isInstanceOf(BadRequestException.class)
                .hasCause(null);
        server.verify();
        assertThat(new TossProperties("test-client", "test-secret").toString()).doesNotContain("test-secret");
    }

    @Test
    void 토큰실패원문과원인예외를노출하지않는다() {
        server.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("test-secret"));
        assertThatThrownBy(() -> client.fetch(date))
                .isInstanceOf(BadRequestException.class)
                .hasCause(null)
                .hasMessageNotContaining("test-secret")
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("TOSS_TOKEN_ISSUE_FAILED", "400")
                        .doesNotContain("test-secret"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429, 500})
    void 달력HTTP오류는안전한상태번호만로그에남긴다(int status) {
        expectToken("first");
        server.expect(requestTo(BASE_URL + "/api/v1/market-calendar/KR?date=" + date))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("test-secret"));
        assertThatThrownBy(() -> client.fetch(date))
                .isInstanceOf(BadRequestException.class)
                .hasCause(null)
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("TOSS_CALENDAR_FETCH_FAILED", String.valueOf(status))
                        .doesNotContain("test-secret", "first"));
        server.verify();
    }

    @Test
    void 타임아웃을원인메시지노출없이구분하고토큰을유지한다() {
        expectToken("first");
        server.expect(requestTo(BASE_URL + "/api/v1/market-calendar/KR?date=" + date))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("test-secret");
                });
        expectCalendar("first");
        assertThatThrownBy(() -> client.fetch(date))
                .isInstanceOf(BadRequestException.class)
                .hasCause(null)
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("TIMEOUT")
                        .doesNotContain("test-secret"));
        client.fetch(date);
        server.verify();
    }

    @Test
    void 달력역직렬화실패의원문을노출하지않는다() {
        expectToken("first");
        server.expect(requestTo(BASE_URL + "/api/v1/market-calendar/KR?date=" + date))
                .andRespond(withSuccess("test-secret", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.fetch(date))
                .isInstanceOf(BadRequestException.class)
                .hasCause(null)
                .satisfies(exception -> assertThat(((BadRequestException) exception).createLogMessage())
                        .contains("RESPONSE_PARSE")
                        .doesNotContain("test-secret"));
        server.verify();
    }

    private void expectToken(String token) {
        server.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .string("grant_type=client_credentials&client_id=test-client&client_secret=test-secret"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + token + "\",\"expires_in\":120}", MediaType.APPLICATION_JSON));
    }

    private void expectCalendar(String token) {
        server.expect(requestTo(BASE_URL + "/api/v1/market-calendar/KR?date=" + date))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(
                        "{\"result\":{\"today\":{\"date\":\"2026-10-09\",\"integrated\":null}}}",
                        MediaType.APPLICATION_JSON));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-08T21:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
