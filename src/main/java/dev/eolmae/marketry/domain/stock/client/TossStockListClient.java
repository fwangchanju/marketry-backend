package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.dto.TossStockListResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 토스 마켓별 종목 목록(`GET /api/v1/stocks/all`)에서 상장폐지된 국내 종목을 찾는다. */
@Component
public class TossStockListClient {
    private static final List<String> KOREAN_MARKETS = List.of("KOSPI", "KOSDAQ");
    private static final String STATUS_DELISTED = "DELISTED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    // 종목 목록 조회는 초당 1회 한도라 호출 사이를 이만큼 띄운다.
    private static final long DEFAULT_REQUEST_INTERVAL_MILLIS = 1_100;

    private final TossTokenManager tokens;
    private final RestClient restClient;
    private final long requestIntervalMillis;

    @Autowired
    public TossStockListClient(TossTokenManager tokens, RestClient restClient) {
        this(tokens, restClient, DEFAULT_REQUEST_INTERVAL_MILLIS);
    }

    TossStockListClient(TossTokenManager tokens, RestClient restClient, long requestIntervalMillis) {
        this.tokens = tokens;
        this.restClient = restClient;
        this.requestIntervalMillis = requestIntervalMillis;
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
                    pause();
                }
                first = false;
                (STATUS_DELISTED.equals(status) ? delisted : active).addAll(fetchStockCodes(market, status));
            }
        }
        delisted.removeAll(active);
        return delisted;
    }

    private Set<String> fetchStockCodes(String market, String status) {
        String accessToken = tokens.acquireToken();
        try {
            TossStockListResponse response = restClient
                    .get()
                    .uri(
                            TossTokenManager.BASE_URL + "/api/v1/stocks/all?market={market}&status={status}",
                            market,
                            status)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(TossStockListResponse.class);
            if (response == null || response.result() == null) {
                throw new BadRequestException(ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, "RESPONSE_INVALID");
            }
            Set<String> codes = new HashSet<>();
            for (TossStockListResponse.Item item : response.result()) {
                if (item != null && item.symbol() != null && !item.symbol().isBlank()) {
                    codes.add(item.symbol().trim());
                }
            }
            return codes;
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

    private void pause() {
        if (requestIntervalMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(requestIntervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException(ErrorCode.TOSS_STOCK_LIST_FETCH_FAILED, "INTERRUPTED");
        }
    }
}
