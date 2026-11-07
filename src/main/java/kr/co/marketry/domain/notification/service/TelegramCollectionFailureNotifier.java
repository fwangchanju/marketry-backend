package kr.co.marketry.domain.notification.service;

import java.time.LocalDateTime;
import kr.co.marketry.common.enums.DateTimePattern;
import kr.co.marketry.domain.notification.client.TelegramClient;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 이번 사이클 데이터 수집이 실패했을 때, 이미지+캡션 대신 텍스트로만 알림. 발송 여부(발송 주기 게이팅)
// 판단은 TelegramReportSender와 마찬가지로 호출부(CollectionScheduler) 책임.
@Component
@RequiredArgsConstructor
public class TelegramCollectionFailureNotifier {

    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;

    public void notify(LocalDateTime dataTime) {
        String text = dataTime.format(DateTimePattern.DATETIME_MINUTE_WITH_WEEKDAY.formatter()) + "\n데이터 크롤링에 실패했습니다.";
        telegramClient.sendMessage(telegramProperties.chatId(), text);
    }
}
