package kr.co.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.properties.KiwoomProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KiwoomTokenManagerTest {

    @ParameterizedTest
    @CsvSource(
            value = {"NULL, NULL", "'', secret", "key, ''", "' ', secret", "key, ' '"},
            nullValues = "NULL")
    void 인증정보가비어있으면토큰요청없이인증설정없음으로실패한다(String appKey, String secret) {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KiwoomTokenManager tokenManager =
                new KiwoomTokenManager(new KiwoomProperties(appKey, secret, null, null), builder.build());

        // when & then
        assertThatThrownBy(tokenManager::getToken).isInstanceOfSatisfying(BadRequestException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED);
            assertThat(e.createLogMessage()).contains("AUTH_NOT_CONFIGURED");
        });
        server.verify();
    }
}
