package dev.eolmae.marketry.domain.auth.controller;

import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.auth.dto.AdminDashboardMetricResponse;
import dev.eolmae.marketry.domain.auth.repository.AdminDashboardMetricRepository;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardMetricController {
    private final AdminDashboardMetricRepository repository;

    public AdminDashboardMetricController(AdminDashboardMetricRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/metrics")
    public ResponseEntity<AdminDashboardMetricResponse> summary(@RequestParam(defaultValue = "14") int days) {
        if (!Set.of(7, 14, 30, 90).contains(days)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다.");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(repository.summarize(KstClock.now().toLocalDate(), days));
    }
}
