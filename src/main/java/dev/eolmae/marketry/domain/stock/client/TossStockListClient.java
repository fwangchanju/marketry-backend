package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.dto.TossStockDetailResponse;
import dev.eolmae.marketry.domain.stock.dto.TossStockListResponse;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 토스 종목 정보를 읽는다. 마켓별 종목 목록(`GET /api/v1/stocks/all`)에서 상장폐지된 국내 종목을, 종목 기본 정보
 * (`GET /api/v1/stocks`)에서 거래정지 중인 국내 종목을 찾는다.
 */
@Component
public class TossStockListClient {
    private static final List<String> KOREAN_MARKETS = List.of("KOSPI", "KOSDAQ");
    private static final String STATUS_DELISTED = "DELISTED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    // 종목 목록 조회는 초당 1회, 종목 기본 정보 조회는 초당 5회 한도라 호출 사이를 이만큼 띄운다.
    private static final long DEFAULT_LIST_INTERVAL_MILLIS = 1_100;
    private static final long DEFAULT_DETAIL_INTERVAL_MILLIS = 250;
    // 종목 기본 정보는 한 번에 200종목까지 조회한다.
    private static final int DETAIL_BATCH_SIZE = 200;
    // 토스가 종목 심볼로 허용하는 글자(영문 대소문자, 숫자, '.', '-').
    private static final Pattern ALLOWED_SYMBOL = Pattern.compile("[A-Za-z0-9.-]+");

    private final TossTokenManager tokens;
    private final RestClient restClient;
    private final long listIntervalMillis;
    private final long detailIntervalMillis;

    @Autowired
    public TossStockListClient(TossTokenManager tokens, RestClient restClient) {
        this(tokens, restClient, DEFAULT_LIST_INTERVAL_MILLIS, DEFAULT_DETAIL_INTERVAL_MILLIS);
    }

    TossStockListClient(
            TossTokenManager tokens, RestClient restClient, long listIntervalMillis, long detailIntervalMillis) {
        this.tokens = tokens;
        this.restClient = restClient;
        this.listIntervalMillis = listIntervalMillis;
        this.detailIntervalMillis = detailIntervalMillis;
    }

    /**
     * 상장폐지 상태인 코스피·코스닥 종목코드를 돌려준다. 종목코드가 다시 쓰이는 경우를 막으려고, 같은 코드가 상장 중인
     * 종목으로도 있으면 폐지 목록에서 뺀다.
     */
    public Set<String> fetchDelistedStockCodes() {
        Set<String> delisted = new HashSet<>();
        Set<String> active = new HashSet<>();
        boolean first = true;
        for (String market : KOREAN_MARKETS) {
            for (String status : List.of(STATUS_DELISTED, STATUS_ACTIVE)) {
                if (!first) {
                    pause(listIntervalMillis);
                }
                first = false;
                (STATUS_DELISTED.equals(status) ? delisted : active).addAll(fetchStockCodes(market, status));
            }
        }
        delisted.removeAll(active);
        return delisted;
    }

    /** 거래정지 중인 종목코드를 돌려준다. 토스가 모르는 종목은 응답에 없어 정지로 보지 않는다. */
    public Set<String> fetchSuspendedStockCodes(Collection<String> stockCodes) {
        Set<String> suspended = new HashSet<>();
        List<String> codes = stockCodes.stream()
                .filter(stockCode ->
                        stockCode != null && ALLOWED_SYMBOL.matcher(stockCode).matches())
                .toList();
        for (int from = 0; from < codes.size(); from += DETAIL_BATCH_SIZE) {
            if (from > 0) {
                pause(detailIntervalMillis);
            }
            List<String> batch = codes.subList(from, Math.min(from + DETAIL_BATCH_SIZE, codes.size()));
            // 쉼표로 이은 종목코드를 쉼표 그대로 보낸다(URI 변수로 넘기면 %2C로 바뀐다). 종목코드는 토스가 허용하는 글자만 남긴다.
            TossStockDetailResponse response =
                    get("/api/v1/stocks?symbols=" + String.join(",", batch), TossStockDetailResponse.class);
            if (response.result() == null) {
                throw new BadRequestException(ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, "RESPONSE_INVALID");
            }
            for (TossStockDetailResponse.Item item : response.result()) {
                if (item != null
                        && item.symbol() != null
                        && item.koreanMarketDetail() != null
                        && Boolean.TRUE.equals(item.koreanMarketDetail().krxTradingSuspended())) {
                    suspended.add(item.symbol().trim());
                }
            }
        }
        return suspended;
    }

    private Set<String> fetchStockCodes(String market, String status) {
        TossStockListResponse response =
                get("/api/v1/stocks/all?market={market}&status={status}", TossStockListResponse.class, market, status);
        if (response.result() == null) {
            throw new BadRequestException(ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, "RESPONSE_INVALID");
        }
        Set<String> codes = new HashSet<>();
        for (TossStockListResponse.Item item : response.result()) {
            if (item != null && item.symbol() != null && !item.symbol().isBlank()) {
                codes.add(item.symbol().trim());
            }
        }
        return codes;
    }

    private <T> T get(String path, Class<T> responseType, Object... uriVariables) {
        String accessToken = tokens.acquireToken();
        try {
            T response = restClient
                    .get()
                    .uri(TossTokenManager.BASE_URL + path, uriVariables)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new BadRequestException(ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, "RESPONSE_INVALID");
            }
            return response;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokens.invalidateToken(accessToken);
            }
            // 인증 오류 본문과 원인 예외에는 자격 증명이 포함될 수 있으므로 전달하지 않는다.
            throw new BadRequestException(
                    ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new BadRequestException(
                    ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, TossTokenManager.classifyConnectionFailure(e));
        } catch (BadRequestException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BadRequestException(ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, "RESPONSE_PARSE");
        } catch (RuntimeException e) {
            throw new BadRequestException(
                    ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, e.getClass().getSimpleName());
        }
    }

    private void pause(long intervalMillis) {
        if (intervalMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException(ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, "INTERRUPTED");
        }
    }
}
