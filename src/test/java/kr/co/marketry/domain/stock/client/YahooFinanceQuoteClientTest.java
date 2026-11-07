package kr.co.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import kr.co.marketry.domain.stock.dto.YahooChartResponse;
import org.junit.jupiter.api.Test;

class YahooFinanceQuoteClientTest {

    @Test
    void 응답의_가격과_전일_종가와_체결_시각을_읽는다() {
        YahooFinanceQuoteClient.Quote quote = YahooFinanceQuoteClient.toQuote(
                "7203", response(new YahooChartResponse.Meta(3338.0, 3533.0, 1793946600L)));

        assertThat(quote.currentPrice()).isEqualByComparingTo("3338");
        assertThat(quote.previousClose()).isEqualByComparingTo("3533");
        assertThat(quote.marketTime()).isEqualTo(Instant.ofEpochSecond(1793946600L));
    }

    @Test
    void 시세가_없거나_0이면_예외를_던진다() {
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote("7203", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote(
                        "7203", new YahooChartResponse(new YahooChartResponse.Chart(List.of()))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote(
                        "7203", response(new YahooChartResponse.Meta(null, 3533.0, 1793946600L))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote(
                        "7203", response(new YahooChartResponse.Meta(0.0, 3533.0, 1793946600L))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static YahooChartResponse response(YahooChartResponse.Meta meta) {
        return new YahooChartResponse(new YahooChartResponse.Chart(List.of(new YahooChartResponse.Result(meta))));
    }
}
