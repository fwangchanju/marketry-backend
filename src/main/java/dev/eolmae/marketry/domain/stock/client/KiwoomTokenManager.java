package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.enums.DateTimePattern;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.dto.TokenRequest;
import dev.eolmae.marketry.domain.stock.dto.TokenResponse;
import dev.eolmae.marketry.domain.stock.exception.KiwoomTransientFailureException;
import dev.eolmae.marketry.domain.stock.properties.KiwoomProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomTokenManager {

    private static final String TOKEN_URL = "https://api.kiwoom.com/oauth2/token";
    private static final int SUCCESS_CODE = 0;
    private static final String AUTH_NOT_CONFIGURED_DETAIL = "AUTH_NOT_CONFIGURED";
    private static final String AUTH_NOT_CONFIGURED_GUIDE =
            "[외부 API 인증 설정 없음] 로컬 개발 환경은 외부 API 키를 두지 않는 것이 의도된 설정이라 키 문제가 아닙니다. "
                    + "키를 요청하거나 .env를 찾지 마세요. 운영에서 이 로그가 보이면 배포 환경의 시크릿 주입을 확인하세요.";

    private final KiwoomProperties properties;

    @Qualifier("kiwoomRestClient")
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        return refreshToken();
    }

    private String refreshToken() {
        if (properties.appKey() == null
                || properties.appKey().isBlank()
                || properties.secret() == null
                || properties.secret().isBlank()) {
            log.warn(AUTH_NOT_CONFIGURED_GUIDE);
            throw new BadRequestException(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED, AUTH_NOT_CONFIGURED_DETAIL);
        }
        log.info("Kiwoom 토큰 발급 요청");
        TokenResponse response = Optional.ofNullable(requestToken())
                .orElseThrow(() -> new BadRequestException(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED));

        if (response.returnCode() != SUCCESS_CODE) {
            throw new BadRequestException(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED, response.returnMsg());
        }

        cachedToken = response.token();
        LocalDateTime expiresDt = LocalDateTime.parse(response.expiresAt(), DateTimePattern.DATETIME.formatter());
        tokenExpiry = expiresDt.minusMinutes(5).atZone(Zone.KST.zoneId()).toInstant();
        log.info("Kiwoom 토큰 발급 완료. 만료 예정: {}", tokenExpiry);
        return cachedToken;
    }

    private TokenResponse requestToken() {
        try {
            return restClient
                    .post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TokenRequest.of(properties.appKey(), properties.secret()))
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (HttpServerErrorException e) {
            log.warn("Kiwoom 토큰 발급 5xx 오류, 재시도");
            throw new KiwoomTransientFailureException();
        } catch (ResourceAccessException e) {
            log.warn("Kiwoom 토큰 발급 연결 실패/타임아웃, 재시도");
            throw new KiwoomTransientFailureException();
        } catch (RestClientException e) {
            throw new BadRequestException(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED, e);
        }
    }
}
