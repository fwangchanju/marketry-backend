package dev.eolmae.marketry.domain.auth.scheduler;

import dev.eolmae.marketry.domain.auth.repository.AdminDashboardMetricRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 익명 운영 지표는 최근 30일만 보관한다. */
@Component
@RequiredArgsConstructor
public class AdminClientMetricCleanupScheduler {
    private final AdminDashboardMetricRepository repository;

    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    public void purgeOldMetrics() {
        repository.purgeBefore(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(29));
    }
}
