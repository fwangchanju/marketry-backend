package dev.eolmae.marketry.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.common.event.DailyPriceCollectionFailedEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyPriceCollectionNotifierTest {
    @Test
    void 종목코드와이름을_유지하고_HTML과_메시지길이를_처리한다() {
        var stocks = new ArrayList<String>();
        stocks.add("005930(A&B<삼성>) [NXT]");
        for (int index = 0; index < 1000; index++) {
            stocks.add("CODE" + index + "(종목이름) [KRX, NXT]");
        }
        var messages = DailyPriceCollectionNotifier.createMessages(
                new DailyPriceCollectionFailedEvent(LocalDate.of(2026, 10, 8), "INCOMPLETE", stocks));
        assertThat(messages).hasSizeGreaterThan(1).allSatisfy(message -> assertThat(message.length())
                .isLessThanOrEqualTo(3000));
        assertThat(messages.getFirst()).contains("005930(A&amp;B&lt;삼성&gt;) [NXT]");
        assertThat(String.join("", messages)).contains("CODE999(종목이름)");
    }

    @Test
    void 종목목록이_없는_시간초과_알림은_문구만_한개의_메시지로_만든다() {
        var messages = DailyPriceCollectionNotifier.createMessages(new DailyPriceCollectionFailedEvent(
                LocalDate.of(2026, 10, 8), "일봉 수집을 제한 시간 내에 완료하지 못했습니다", List.of()));

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst()).isEqualTo("[일봉 수집 미완료] 2026-10-08\n일봉 수집을 제한 시간 내에 완료하지 못했습니다\n");
    }
}
