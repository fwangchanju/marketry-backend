package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 토스 일봉·키움 일별주가·키움 일봉의 첫 페이지를 Map으로 확인한다. 앱·DB는 기동하지 않는다.
 *
 * <p>실행: ./gradlew.bat manualTest --tests "*.DailyClosingPriceApiManualTest" --rerun-tasks --info
 * <p>기본값: 삼성전자(005930), 2026-09-30. PRICE_PROBE_SYMBOL / PRICE_PROBE_DATE 환경변수로 변경한다.
 * <p>전용 인증 검사 프로그램으로 실행 중 infra/.env의 인증값만 로드하며 인증 응답·값은 출력하지 않는다.
 * PRICE_PROBE_ENV_FILE로 파일 경로를 변경할 수 있다. 스케줄러·수집·DB 저장·연속조회·자동 재시도는 없다.
 */
@Tag("manual")
public class DailyClosingPriceApiManualTest {
    private static final Set<String> CREDENTIAL_KEYS =
            Set.of("KIWOOM_APP_KEY_B", "KIWOOM_SECRET_B", "TOSS_CLIENT_ID", "TOSS_CLIENT_SECRET");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final Map<String, String> CREDENTIALS = loadCredentials();
    private static final String SYMBOL = option("PRICE_PROBE_SYMBOL", "005930");
    private static final LocalDate DATE = LocalDate.parse(option("PRICE_PROBE_DATE", "2026-09-30"));
    private static String kiwoomToken;

    @Test
    void 토스_일봉_첫페이지의_전체응답과_건수와_날짜범위를_출력한다() throws IOException {
        String token = authenticateToss();
        String before = URLEncoder.encode(
                DATE.atStartOfDay().atOffset(ZoneOffset.ofHours(9)).toString(), StandardCharsets.UTF_8);
        String url = "https://openapi.tossinvest.com/api/v1/candles?symbol=" + SYMBOL
                + "&interval=1d&count=200&adjusted=false&before=" + before;
        var response = request(
                "TOSS candles", HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token));
        Map<String, Object> body = readBody("TOSS candles", response);
        Map<?, ?> result = (Map<?, ?>) body.get("result");
        assertThat(result != null).as("토스 가격 응답 result 존재").isTrue();
        List<?> rows = (List<?>) result.get("candles");
        printResponse("TOSS candles", response, body, token);
        printSummary("TOSS candles", rows, "timestamp", result.get("nextBefore") != null);
        assertThat(rows.size()).as("요청한 일봉 최대 건수").isBetween(1, 200);
    }

    @Test
    void 키움_일별주가_첫페이지의_전체응답과_건수와_날짜범위를_출력한다() throws IOException {
        probeKiwoom(
                "ka10086",
                "/api/dostk/mrkcond",
                Map.of("stk_cd", SYMBOL, "qry_dt", DATE.format(DateTimeFormatter.BASIC_ISO_DATE), "indc_tp", "0"),
                "daly_stkpc",
                "date");
    }

    @Test
    void 키움_일봉_첫페이지의_전체응답과_건수와_날짜범위를_출력한다() throws IOException {
        probeKiwoom(
                "ka10081",
                "/api/dostk/chart",
                Map.of("stk_cd", SYMBOL, "base_dt", DATE.format(DateTimeFormatter.BASIC_ISO_DATE), "upd_stkpc_tp", "0"),
                "stk_dt_pole_chart_qry",
                "dt");
    }

    private void probeKiwoom(String apiId, String path, Map<String, String> payload, String listKey, String dateKey)
            throws IOException {
        String token = authenticateKiwoom();
        var response = request(
                apiId,
                HttpRequest.newBuilder(URI.create("https://api.kiwoom.com" + path))
                        .header("Authorization", "Bearer " + token)
                        .header("api-id", apiId)
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload))));
        Map<String, Object> body = readBody(apiId, response);
        printResponse(apiId, response, body, token);
        assertThat("0".equals(String.valueOf(body.get("return_code"))))
                .as(apiId + " 가격 조회 성공 코드")
                .isTrue();
        printSummary(
                apiId,
                (List<?>) body.get(listKey),
                dateKey,
                "Y".equals(response.headers().firstValue("cont-yn").orElse("N")));
    }

    private static String authenticateKiwoom() throws IOException {
        if (kiwoomToken != null) {
            return kiwoomToken;
        }
        var payload = Map.of(
                "grant_type", "client_credentials",
                "appkey", credential("KIWOOM_APP_KEY_B"),
                "secretkey", credential("KIWOOM_SECRET_B"));
        var response = request(
                "KIWOOM AUTH",
                HttpRequest.newBuilder(URI.create("https://api.kiwoom.com/oauth2/token"))
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload))));
        Map<String, Object> body = readBody("KIWOOM AUTH", response);
        if ("0".equals(String.valueOf(body.get("return_code"))) == false) {
            throw new AssertionError("KIWOOM AUTH: 인증 실패 (인증 응답은 출력하지 않습니다.)");
        }
        kiwoomToken = requireToken("KIWOOM AUTH", body.get("token"));
        return kiwoomToken;
    }

    private static String authenticateToss() throws IOException {
        String form = "grant_type=client_credentials&client_id="
                + URLEncoder.encode(credential("TOSS_CLIENT_ID"), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(credential("TOSS_CLIENT_SECRET"), StandardCharsets.UTF_8);
        var response = request(
                "TOSS AUTH",
                HttpRequest.newBuilder(URI.create("https://openapi.tossinvest.com/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)));
        return requireToken("TOSS AUTH", readBody("TOSS AUTH", response).get("access_token"));
    }

    private static HttpResponse<String> request(String label, HttpRequest.Builder builder) {
        if (System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank() == false) {
            throw new AssertionError("인증값 보호를 위해 JDK HTTP 로깅을 끄고 실행하세요.");
        }
        long started = System.nanoTime();
        try {
            var response =
                    HTTP.send(builder.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
            System.out.printf(
                    "HTTP: api=%s status=%d elapsedMs=%d%n",
                    label,
                    response.statusCode(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
            if (response.statusCode() != 200) {
                throw new AssertionError(label + ": HTTP " + response.statusCode() + " (오류 본문은 출력하지 않습니다.)");
            }
            return response;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new AssertionError(label + ": 요청 중단");
        } catch (IOException | RuntimeException failure) {
            // 원인 예외의 요청·응답 원문에 인증값이 포함될 수 있어 예외 종류만 전달한다.
            throw new AssertionError(label + ": " + failure.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> readBody(String label, HttpResponse<String> response) {
        try {
            return JSON.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException ignored) {
            throw new AssertionError(label + ": JSON 응답 파싱 실패 (원문은 출력하지 않습니다.)");
        }
    }

    private static void printResponse(
            String label, HttpResponse<String> response, Map<String, Object> body, String token) throws IOException {
        for (String header : List.of(
                "cont-yn",
                "next-key",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "Retry-After")) {
            response.headers()
                    .firstValue(header)
                    .ifPresent(
                            value -> System.out.printf("HEADER: api=%s %s=%s%n", label, header, redact(value, token)));
        }
        System.out.printf(
                "RESPONSE BEGIN: api=%s%n%s%nRESPONSE END: api=%s%n",
                label, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(redactBody(body, token)), label);
    }

    private static void printSummary(String label, List<?> rows, String dateKey, boolean hasNext) {
        assertThat(rows != null && rows.isEmpty() == false)
                .as(label + " 첫 페이지 가격 데이터 존재")
                .isTrue();
        List<LocalDate> dates = rows.stream()
                .map(row -> {
                    String value = String.valueOf(((Map<?, ?>) row).get(dateKey));
                    return value.length() == 8
                            ? LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                            : LocalDate.parse(value.substring(0, 10));
                })
                .sorted()
                .toList();
        assertThat(dates.getLast()).as(label + " 기준일 이후 데이터 제외").isBeforeOrEqualTo(DATE);
        long withinYear =
                dates.stream().filter(date -> date.isAfter(DATE.minusYears(1))).count();
        System.out.printf(
                "SUMMARY: api=%s symbol=%s anchor=%s rows=%d firstDate=%s lastDate=%s withinYear=%d coversYear=%s hasNext=%s%n",
                label,
                SYMBOL,
                DATE,
                rows.size(),
                dates.getFirst(),
                dates.getLast(),
                withinYear,
                dates.getFirst().isAfter(DATE.minusYears(1)) == false,
                hasNext);
    }

    private static Object redactBody(Object value, String token) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((key, item) -> safe.put(String.valueOf(key), redactBody(item, token)));
            return safe;
        }
        if (value instanceof List<?> list) {
            List<Object> safe = new ArrayList<>();
            list.forEach(item -> safe.add(redactBody(item, token)));
            return safe;
        }
        return value instanceof String text ? redact(text, token) : value;
    }

    private static String redact(String text, String token) {
        String safe = text.replace(token, "[REDACTED]");
        for (String credential : CREDENTIALS.values()) {
            if (credential.isBlank() == false) {
                safe = safe.replace(credential, "[REDACTED]");
            }
        }
        return safe;
    }

    private static String requireToken(String label, Object value) {
        if (value instanceof String token && token.isBlank() == false) {
            return token;
        }
        throw new AssertionError(label + ": 인증 토큰 없음 (인증 응답은 출력하지 않습니다.)");
    }

    private static String credential(String name) {
        String value = CREDENTIALS.get(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("API 인증 설정이 없습니다. 환경 파일 경로와 실행 환경을 확인하세요.");
        }
        return value;
    }

    private static String option(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, String> loadCredentials() {
        Map<String, String> values = new HashMap<>();
        Path path = Path.of(option("PRICE_PROBE_ENV_FILE", "infra/.env"));
        // 파일의 인증값은 이 전용 프로그램이 API 인증 용도로만 읽고, 값·인증 응답을 로그로 내보내지 않는다.
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String key = line.substring(0, separator).strip();
                if (CREDENTIAL_KEYS.contains(key) == false) {
                    continue;
                }
                String value = line.substring(separator + 1).strip();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            throw new AssertionError("전용 API 검사 프로그램이 환경 파일을 읽을 수 없습니다.");
        }
        for (String key : CREDENTIAL_KEYS) {
            String value = System.getenv(key);
            if (value != null && value.isBlank() == false) {
                values.put(key, value);
            }
        }
        return values;
    }
}
