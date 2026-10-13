package dev.eolmae.marketry.runner;

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

    @Override
    public void run(ApplicationArguments args) {
        loadStockInfoCache();
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
