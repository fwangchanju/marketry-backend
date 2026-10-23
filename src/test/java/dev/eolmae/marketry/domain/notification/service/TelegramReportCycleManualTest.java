package dev.eolmae.marketry.domain.notification.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CollectionScheduler가 실제로 부르는 것과 똑같이 TelegramReportDispatcher를 그대로 호출하는 수동
 * 검증용 테스트 — 스케줄러의 "몇 분에만 보낸다" 시각 게이팅은 여기선 제외하고(테스트를 아무 때나 돌려도
 * 이미지를 바로 받아봐야 하므로) 발송 로직 자체만 그대로 재현한다. 각 마켓의 실제 마지막 수집 시각을
 * 그대로 쓴다(현재 시각을 쓰면 장 마감/주말처럼 그 시각에 실제 스냅샷이 없는 경우 랭킹 조회가 빈 결과로
 * 나와 발송 자체가 조용히 스킵됨 — SectorTelegramReportSender의 rankings.isEmpty() 가드 참고). 배포된
 * 컨테이너 안에서 그 환경의 실제 DB/renderer/텔레그램 설정을 그대로 쓰므로, 배포 후 확인할 때만
 * 실행한다.
 *
 * scheduling.enabled=false로 스프링 스케줄링을 꺼서, 캡처가 도는 몇 초 사이 정각 cron이 겹쳐 수집·
 * 발송이 한 번 더 나가는 것을 막는다.
 *
 * 실행 조건: 배포된 컨테이너 환경(실제 DB/renderer/텔레그램 설정)에서 실행해야 한다.
 * 실행 명령:
 *
 * <pre>{@code
 * cd "$HOME/repo/market-monitor-backend"
 * git fetch origin main && git checkout main && git pull
 * mkdir -p "$HOME/.gradle-cache"
 *
 * # 지도만 / 섹터만 / 둘 다 / 올스탁 한 장 — 넷 중 하나를 고른다
 * TEST='*.TelegramReportCycleManualTest.sendsMap'
 * # TEST='*.TelegramReportCycleManualTest.sendsSector'
 * # TEST='*.TelegramReportCycleManualTest.sendsAll'
 * # TEST='*.TelegramReportCycleManualTest.sendsMapAllStockOnePage'
 *
 * docker run --rm \
 *   --network host \
 *   --env-file "$HOME/env/market-monitor.env" \
 *   -e SPRING_PROFILES_ACTIVE=prod \
 *   -e DB_URL=jdbc:postgresql://localhost:5433/market_monitor_db \
 *   -v "$HOME/repo/market-monitor-backend:/workspace" \
 *   -v "$HOME/.gradle-cache:/root/.gradle" \
 *   -w /workspace \
 *   eclipse-temurin:21-jdk-jammy \
 *   bash -c "chmod +x gradlew && ./gradlew manualTest --tests '$TEST' -i --no-daemon"
 * }</pre>
 *
 * 확인할 것
 * <ol>
 *   <li>sendsMap: 코스피·코스닥 두 장 앨범 + 병합 랭킹 캡션. 스케줄러 15:30 발송과 같은 모양
 *   <li>sendsSector: 마켓별 섹터 이미지 한 장 + 캡션 한 개씩. 08:10 첫 발송처럼 before가 없으면
 *       등락률 폴백 캡션
 *   <li>sendsAll: 섹터 뒤에 맵. 메시지 순서가 스케줄러가 둘 다 due일 때와 같다
 *   <li>sendsMapAllStockOnePage: /map/allstock 한 장 + 병합 랭킹 캡션. 프론트에 그 라우트가 배포돼 있어야 한다
 *   <li>테스트가 도는 동안 앱 로그에 "장중 시장 데이터 수집 시작"이 찍히지 않는다
 *   <li>실제 채팅방(TELEGRAM_CHAT_ID)으로 나간다. 테스트 발송이면 env 파일의 값을 DEVELOPER_CHAT_ID로
 *       바꿔 돌린다
 * </ol>
 */
@Tag("manual")
@SpringBootTest(properties = "scheduling.enabled=false")
class TelegramReportCycleManualTest {

    @Autowired
    private SectorPriceSnapshotService sectorPriceSnapshotService;

    @Autowired
    private TelegramReportDispatcher dispatcher;

    @Test
    void sendsSector() {
        dispatcher.sendSector(findDataTime());
    }

    @Test
    void sendsMap() {
        dispatcher.sendMap(findDataTime());
    }

    @Test
    void sendsAll() {
        dispatcher.sendAll(findDataTime());
    }

    @Test
    void sendsMapAllStockOnePage() {
        dispatcher.sendMapSinglePage(findDataTime(), MarketQuery.ALL_STOCK);
    }

    // findLatestCommonSnapshotTime이 markets 전부가 공통으로 가진 최신 시각을 한 번에 구해주므로,
    // 마켓별로 따로 조회해서 min을 취할 필요 없이 이 호출 하나로 충분하다.
    private LocalDateTime findDataTime() {
        return sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(List.of(Market.KOSPI, Market.KOSDAQ))
                .orElseThrow(() -> new IllegalStateException("스냅샷 데이터가 없습니다"));
    }
}
