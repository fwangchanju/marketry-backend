package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.dto.TossMarketCalendarResponse;
import dev.eolmae.marketry.domain.stock.properties.TossProperties;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TossMarketCalendarClient {
    private final TossTokenManager tokens;
    private final RestClient restClient;

    @Autowired
    public TossMarketCalendarClient(TossTokenManager tokens, RestClient restClient) {
        this.tokens = tokens;
        this.restClient = restClient;
    }

    TossMarketCalendarClient(TossProperties properties, RestClient restClient, Clock clock) {
        this(new TossTokenManager(properties, restClient, clock), restClient);
    }

    public TossMarketCalendarResponse fetch(LocalDate date) {
        String accessToken = tokens.acquireToken();
        try {
            TossMarketCalendarResponse response = restClient
                    .get()
                    .uri(TossTokenManager.BASE_URL + "/api/v1/market-calendar/KR?date={date}", date)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(TossMarketCalendarResponse.class);
            if (response == null) {
                throw new BadRequestException(ErrorCode.MARKET_CALENDAR_RESPONSE_INVALID);
            }
            return response;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokens.invalidateToken(accessToken);
            }
            // 인증 오류 본문과 원인 예외에는 자격 증명이 포함될 수 있으므로 전달하지 않는다.
            throw new BadRequestException(
                    ErrorCode.TOSS_CALENDAR_FETCH_FAILED, e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new BadRequestException(
                    ErrorCode.TOSS_CALENDAR_FETCH_FAILED, TossTokenManager.classifyConnectionFailure(e));
        } catch (BadRequestException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BadRequestException(ErrorCode.TOSS_CALENDAR_FETCH_FAILED, "RESPONSE_PARSE");
        } catch (RuntimeException e) {
            throw new BadRequestException(
                    ErrorCode.TOSS_CALENDAR_FETCH_FAILED, e.getClass().getSimpleName());
        }
    }
}
