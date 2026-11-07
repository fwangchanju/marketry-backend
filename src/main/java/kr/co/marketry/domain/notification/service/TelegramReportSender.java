package kr.co.marketry.domain.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import kr.co.marketry.domain.notification.client.TelegramClient;
import kr.co.marketry.domain.notification.enums.RenderTarget;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import kr.co.marketry.domain.renderer.client.ScreenshotClient;
import kr.co.marketry.domain.view.enums.MarketQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 텔레그램으로 캡처 이미지+텍스트를 발송하는 공통 흐름. 새 발송 양식이 필요하면 이 클래스를 상속해서
// target()/buildText()/captureQueryValues()만 구현하면 된다. 발송 여부(발송 주기 게이팅, 데이터 존재
// 여부 등) 판단은 호출부(CollectionScheduler) 책임 — 여기선 호출되면 무조건 캡처+발송한다. query는
// "무엇을 요청받았는지"(KOSPI/KOSDAQ/ALL_STOCK)를 그대로 표현할 뿐, 이걸 캡처할 화면 목록으로 어떻게
// 펼칠지는 화면 구조가 sender마다 달라서(맵은 마켓별로 따로, 섹터는 ALL_STOCK 하나로 합쳐서 보여줌)
// captureQueryValues()에서 각자 정한다. 캡처 결과는 서로 합치지 않고 각각 별개 사진으로 앨범
// (sendMediaGroup) 하나에 담아 알림 1번으로 보낸다.
@Slf4j
@RequiredArgsConstructor
public abstract class TelegramReportSender {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;

    protected abstract RenderTarget target();

    protected abstract String buildText(LocalDateTime dataTime, MarketQuery query);

    // 캡처할 프론트 페이지의 마켓 경로 세그먼트로 변환할 값들 — 화면이 query를 어떻게 소화하는지는
    // sender마다 다르므로 여기서 직접 정한다(맵: 마켓별로 펼쳐서 각각 캡처).
    protected abstract List<String> captureQueryValues(MarketQuery query);

    public void send(LocalDateTime dataTime, MarketQuery query) {
        RenderTarget target = target();
        List<byte[]> images = captureQueryValues(query).stream()
                .flatMap(value ->
                        screenshotClient
                                .capture(target.path() + "/" + value.toLowerCase(Locale.ROOT), target.selector())
                                .stream())
                .toList();
        telegramClient.sendMediaGroup(telegramProperties.chatId(), images, buildText(dataTime, query));
        log.info("{}({}) 이미지 발송 완료: {}장", target, query, images.size());
    }
}
