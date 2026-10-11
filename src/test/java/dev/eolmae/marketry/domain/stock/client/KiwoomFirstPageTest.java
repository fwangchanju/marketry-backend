package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.properties.KiwoomProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KiwoomFirstPageTest {
    @Test
    void 다음페이지가_있어도_첫응답만_한번_요청한다() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var tokens = mock(KiwoomTokenManager.class);
        when(tokens.getToken()).thenReturn("test-token");
        var client = new KiwoomApiClient(
                new KiwoomProperties("test-key", "test-secret", null, null), tokens, builder.build());
        server.expect(requestTo("https://api.kiwoom.com/api/dostk/chart"))
                .andRespond(withSuccess(
                                "{\"return_code\":0,\"result_list\":[{\"dt\":\"20261008\"}]}",
                                MediaType.APPLICATION_JSON)
                        .header("cont-yn", "Y")
                        .header("next-key", "next"));
        var response = client.postFirstPage("ka11081", "/api/dostk/chart", Map.of("stk_cd", "005930"));
        assertThat((java.util.List<?>) response.get("result_list")).hasSize(1);
        server.verify();
    }

    @Test
    void 오류_응답코드는_예외로_알린다() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var tokens = mock(KiwoomTokenManager.class);
        when(tokens.getToken()).thenReturn("test-token");
        var client = new KiwoomApiClient(
                new KiwoomProperties("test-key", "test-secret", null, null), tokens, builder.build());
        server.expect(requestTo("https://api.kiwoom.com/api/dostk/chart"))
                .andRespond(withSuccess("{\"return_code\":1,\"return_msg\":\"error\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.postFirstPage("ka11081", "/api/dostk/chart", Map.of()))
                .isInstanceOf(BadRequestException.class);
    }
}
