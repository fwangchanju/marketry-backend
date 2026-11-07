package kr.co.marketry.domain.auth.controller;

import java.util.Arrays;
import java.util.Set;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.auth.dto.ClientMetricEventRequest;
import kr.co.marketry.domain.auth.repository.AdminDashboardMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/metrics/client-events")
@RequiredArgsConstructor
public class ClientMetricController {
    private static final String[] PAGES = {"HOME", "MAP", "GRAPH", "CUSTOM"};
    private static final Set<String> TYPES = Set.of("PAGE_LOAD", "CLIENT_ERROR", "MAP_MOBILE_CAPTURE");
    private final AdminDashboardMetricRepository repository;

    @PostMapping
    public ResponseEntity<Void> record(@RequestBody ClientMetricEventRequest event) {
        if (event == null
                || event.page() == null
                || event.type() == null
                || !Arrays.asList(PAGES).contains(event.page())
                || !TYPES.contains(event.type())
                || ((event.type().equals("PAGE_LOAD") || event.type().equals("MAP_MOBILE_CAPTURE"))
                        && (event.durationMs() == null || event.durationMs() < 0 || event.durationMs() > 120_000))
                || (event.type().equals("CLIENT_ERROR") && event.durationMs() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "측정 정보가 올바르지 않습니다.");
        }
        repository.record(KstClock.now().toLocalDate(), event);
        return ResponseEntity.noContent().build();
    }
}
