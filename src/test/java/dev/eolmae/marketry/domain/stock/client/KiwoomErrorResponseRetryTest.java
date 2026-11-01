package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceRequest;
import dev.eolmae.marketry.domain.stock.dto.SectorCurrentPriceResponse;
import dev.eolmae.marketry.domain.stock.properties.KiwoomProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KiwoomErrorResponseRetryTest {
    @Test
    void 스냅샷_요청의_키움_오류는_전체_수집_재시도에_전달한다() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything())
                .andRespond(withSuccess(
                        "{\"return_code\":7,\"return_msg\":\"서비스를 처리하는 중에 오류가 발생했습니다[1631]\"}",
                        MediaType.APPLICATION_JSON));
        var tokens = mock(KiwoomTokenManager.class);
        when(tokens.getToken()).thenReturn("test-token");
        var client = new KiwoomApiClient(
                new KiwoomProperties("test-key", "test-secret", null, null), tokens, builder.build());

        assertThatThrownBy(() -> client.postForSnapshot(
                        new SectorCurrentPriceRequest("0", "001"), SectorCurrentPriceResponse.class))
                .isInstanceOf(BadRequestException.class);
        server.verify();
    }
}
