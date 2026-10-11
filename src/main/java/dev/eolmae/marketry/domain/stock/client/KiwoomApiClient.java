package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.dto.KiwoomRequest;
import dev.eolmae.marketry.domain.stock.dto.KiwoomResponse;
import dev.eolmae.marketry.domain.stock.dto.KiwoomResponseHeader;
import dev.eolmae.marketry.domain.stock.exception.KiwoomRateLimitException;
import dev.eolmae.marketry.domain.stock.exception.KiwoomTransientFailureException;
import dev.eolmae.marketry.domain.stock.properties.KiwoomProperties;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomApiClient {

    private static final String BASE_URL = "https://api.kiwoom.com";
    public static final String SUCCESS_CODE = "0";

    // 초당 5회 제한 대응 — 크레딧 저장 없이 매 호출 최소 간격 고정(버스트 방지)
    private static final long MIN_FETCH_INTERVAL = TimeUnit.MILLISECONDS.toNanos(200);

    private final KiwoomProperties properties;
    private final KiwoomTokenManager tokenManager;

    @Qualifier("kiwoomRestClient")
    private final RestClient restClient;

    private long lastFetchNanos = 0;

    /** 첫 페이지만 반환하며 재시도는 호출하는 수집기가 담당한다. 기존 post의 연속조회 정책은 유지한다. */
    public Map<String, Object> postFirstPage(String apiId, String path, Map<String, String> payload) {
        // 토큰과 HTTP 요청은 각각 10초 제한이며, 진행 중인 요청은 끝까지 기다린다.
        String token = tokenManager.getToken();
        acquire();
        Map<String, Object> body;
        try {
            body = restClient
                    .post()
                    .uri(BASE_URL + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.set("appkey", properties.appKey());
                        headers.set("secretkey", properties.secret());
                        headers.set("api-id", apiId);
                    })
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            // 인증 헤더가 포함될 수 있는 HTTP 예외 원문은 새 일봉 수집 경계 밖으로 전달하지 않는다.
            throw new BadRequestException(ErrorCode.KIWOOM_HTTP_ERROR, apiId);
        }
        if (body == null || SUCCESS_CODE.equals(String.valueOf(body.get("return_code"))) == false) {
            throw new BadRequestException(ErrorCode.KIWOOM_ERROR_RESPONSE, apiId);
        }
        return body;
    }

    /**
     * 타입 안전 API 호출. request DTO가 직렬화되어 요청 바디로 전송되고, 응답은 dataClass 타입으로 역직렬화된다.
     * 응답 헤더의 cont-yn이 Y인 동안 next-key로 계속 이어서 호출하고, 페이지들을 병합해서 하나로 리턴한다.
     *
     * 429·연결 실패/타임아웃·5xx 응답 시 최대 3회 재시도(2초 간격), 초과 시 해당 사이클 스킵.
     * 재시도는 이 메서드 전체 단위로 걸리므로, 페이지네이션 도중 재시도 대상 오류가 나면 첫 페이지부터 다시 돈다.
     */
    @Retryable(
            retryFor = {KiwoomRateLimitException.class, KiwoomTransientFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public <T extends KiwoomResponse> T post(KiwoomRequest request, Class<T> dataClass) {
        log.debug("Kiwoom API 호출: apiId={}, path={}", request.apiId(), request.path());
        return fetchAll(request, dataClass);
    }

    @Recover
    public <T> T recoverFromRateLimit(KiwoomRateLimitException e, KiwoomRequest request, Class<T> dataClass) {
        log.warn("Kiwoom API rate limit 재시도 횟수 초과, 사이클 스킵: apiId={}", request.apiId());
        throw new BadRequestException(ErrorCode.KIWOOM_RATE_LIMIT, request.apiId());
    }

    @Recover
    public <T> T recoverFromTransientFailure(
            KiwoomTransientFailureException e, KiwoomRequest request, Class<T> dataClass) {
        log.warn("Kiwoom API 연결 실패/서버 오류 재시도 횟수 초과, 사이클 스킵: apiId={}", request.apiId());
        throw new BadRequestException(ErrorCode.KIWOOM_CONNECTION_FAILED, request.apiId());
    }

    @SuppressWarnings("unchecked")
    private <T extends KiwoomResponse> T fetchAll(KiwoomRequest request, Class<T> dataClass) {
        PageResult<T> page = fetchPage(request, dataClass);
        T merged = page.body();

        while (page.header().hasNext()) {
            page = fetchPage(request, dataClass, page.header().nextKey());
            merged = (T) merged.mergeNext(page.body());
        }

        return (T) merged.dedupe();
    }

    private <T extends KiwoomResponse> PageResult<T> fetchPage(KiwoomRequest request, Class<T> dataClass) {
        return fetchPage(request, dataClass, null);
    }

    private <T extends KiwoomResponse> PageResult<T> fetchPage(
            KiwoomRequest request, Class<T> dataClass, String nextKey) {
        acquire();
        ResponseEntity<T> entity = fetch(request, dataClass, nextKey);
        return toPageResult(request, entity);
    }

    // 이전 fetch로부터 MIN_FETCH_INTERVAL만큼 안 지났으면 그 차이만큼 대기 후 진행
    private synchronized void acquire() {
        long waitNanos = MIN_FETCH_INTERVAL - (System.nanoTime() - lastFetchNanos);
        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastFetchNanos = System.nanoTime();
    }

    private <T> ResponseEntity<T> fetch(KiwoomRequest request, Class<T> dataClass, String nextKey) {
        try {
            return restClient
                    .post()
                    .uri(BASE_URL + request.path())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> {
                        httpHeaders.set("authorization", "Bearer " + tokenManager.getToken());
                        httpHeaders.set("appkey", properties.appKey());
                        httpHeaders.set("secretkey", properties.secret());
                        httpHeaders.set("api-id", request.apiId());

                        if (nextKey != null) {
                            httpHeaders.set("cont-yn", "Y");
                            httpHeaders.set("next-key", nextKey);
                        }
                    })
                    .body(request)
                    .retrieve()
                    .toEntity(dataClass);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Kiwoom API 429 rate limit: apiId={}", request.apiId());
                throw new KiwoomRateLimitException();
            }
            throw new BadRequestException(ErrorCode.KIWOOM_HTTP_ERROR, e, request.apiId());
        } catch (HttpServerErrorException e) {
            log.warn("Kiwoom API 5xx 오류, 재시도: apiId={}, status={}", request.apiId(), e.getStatusCode());
            throw new KiwoomTransientFailureException();
        } catch (ResourceAccessException e) {
            log.warn("Kiwoom API 연결 실패/타임아웃, 재시도: apiId={}", request.apiId());
            throw new KiwoomTransientFailureException();
        } catch (RestClientException e) {
            throw new BadRequestException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, e, request.apiId());
        }
    }

    private <T extends KiwoomResponse> PageResult<T> toPageResult(KiwoomRequest request, ResponseEntity<T> entity) {
        T validated = Optional.ofNullable(entity.getBody())
                .orElseThrow(() -> new BadRequestException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, request.apiId()));

        if (!SUCCESS_CODE.equals(validated.returnCode())) {
            log.warn(
                    "Kiwoom API 오류 응답: apiId={}, return_code={}, msg={}",
                    request.apiId(),
                    validated.returnCode(),
                    validated.returnMsg());
            throw new BadRequestException(ErrorCode.KIWOOM_ERROR_RESPONSE, request.apiId(), validated.returnMsg());
        }

        return new PageResult<>(KiwoomResponseHeader.from(entity.getHeaders()), validated);
    }

    private record PageResult<T extends KiwoomResponse>(KiwoomResponseHeader header, T body) {}
}
