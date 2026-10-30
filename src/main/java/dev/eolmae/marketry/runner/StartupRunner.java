package dev.eolmae.marketry.runner;

import dev.eolmae.marketry.domain.custom.service.ProtectedSectorService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
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

    @Override
    public void run(ApplicationArguments args) {
        loadStockInfoCache();
        ensureProtectedSectors();
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
