package kr.co.marketry.domain.custom.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.co.marketry.domain.custom.dto.SnapshotItem;
import kr.co.marketry.domain.custom.dto.SnapshotLabelRequest;
import kr.co.marketry.domain.custom.service.MarketryPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MARKETRY 고정본 올리기·버전 목록·되돌리기 — /api/admin/** 규칙(ADMIN 전용)을 그대로 쓴다. */
@RequestMapping("/api/admin/marketry/publications")
@RestController
@RequiredArgsConstructor
public class MarketryPublishController {

    private final MarketryPublishService marketryPublishService;

    @PostMapping
    public SnapshotItem publish(@RequestBody @Valid SnapshotLabelRequest request) {
        return marketryPublishService.publish(request.label());
    }

    @GetMapping
    public List<SnapshotItem> getVersions() {
        return marketryPublishService.getVersions();
    }

    @PostMapping("/{id}/restore")
    public void restore(@PathVariable Long id) {
        marketryPublishService.restoreVersion(id);
    }
}
