package kr.co.marketry.runner;

import kr.co.marketry.domain.custom.service.ProtectedSectorService;
import kr.co.marketry.domain.custom.service.SuspendedStockClassificationService;
import kr.co.marketry.domain.stock.service.StockInfoCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

    private final StockInfoCacheService stockInfoCacheService;
    private final ProtectedSectorService protectedSectorService;
    private final SuspendedStockClassificationService suspendedStockClassificationService;

    @Override
    public void run(ApplicationArguments args) {
        loadStockInfoCache();
        ensureProtectedSectors();
        applyCurrentSuspensions();
    }

    /** 지금 정지로 제외 중인 종목을 모든 사용자의 거래 정지 업종에 맞춘다. 이미 맞으면 아무것도 바꾸지 않는다. */
    private void applyCurrentSuspensions() {
        try {
            suspendedStockClassificationService.applyCurrentExclusionsToAllUsers();
        } catch (Exception e) {
            log.error("[startup] 거래 정지 종목 분류 실패", e);
        }
    }

    /** 기존 사용자에게 없는 보호 업종을 추가한다. 이미 있으면 아무것도 바꾸지 않아 몇 번 시작해도 같다. */
    private void ensureProtectedSectors() {
        try {
            protectedSectorService.ensureForAllUsers();
        } catch (Exception e) {
            log.error("[startup] 보호 업종 보정 실패", e);
        }
    }

    private void loadStockInfoCache() {

        try {
            stockInfoCacheService.getCache();
            log.info("[startup] 전체 종목 정보 캐싱 완료");
        } catch (Exception e) {
            log.error("[startup] 전체 종목 정보 캐싱 실패", e);
        }
    }
}
