package kr.co.marketry.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import kr.co.marketry.common.event.DailyPriceCollectionFailedEvent;
import kr.co.marketry.domain.notification.client.TelegramClient;
import kr.co.marketry.domain.notification.exception.TelegramSendException;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import kr.co.marketry.domain.view.enums.AverageMode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);

    private DailyPriceCollectionNotifier notifierFor(String developerChatId) {
        return new DailyPriceCollectionNotifier(
                telegramClient,
                new TelegramProperties("token", "chat", developerChatId, 10, 15, 15, AverageMode.SIMPLE, true));
    }

    private DailyPriceCollectionFailedEvent failedEvent() {
        return new DailyPriceCollectionFailedEvent(LocalDate.of(2026, 10, 8), "INCOMPLETE", List.of("005930(삼성전자)"));
    }

    @Test
    void onFailure_개발자_채팅방이_여러_곳이면_모두에게_보낸다() {
        notifierFor("dev-1,dev-2").onFailure(failedEvent());

        verify(telegramClient).sendMessage(eq("dev-1"), anyString());
        verify(telegramClient).sendMessage(eq("dev-2"), anyString());
    }

    @Test
    void onFailure_앞_채팅방_발송이_실패해도_뒤_채팅방에는_보낸다() {
        doThrow(new TelegramSendException("RestClientException", "발송 실패"))
                .when(telegramClient)
                .sendMessage(eq("dev-1"), anyString());

        notifierFor("dev-1,dev-2").onFailure(failedEvent());

        verify(telegramClient, times(1)).sendMessage(eq("dev-2"), anyString());
    }

    @Test
    void onFailure_개발자_채팅방이_없으면_보내지_않는다() {
        notifierFor("").onFailure(failedEvent());

        verify(telegramClient, never()).sendMessage(anyString(), anyString());
    }
}
