package kr.co.marketry.domain.custom.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.co.marketry.domain.custom.dto.SnapshotItem;
import kr.co.marketry.domain.custom.dto.SnapshotLabelRequest;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.service.CustomSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/custom/snapshots")
@RestController
@RequiredArgsConstructor
public class CustomSnapshotController {

    private final CustomSnapshotService customSnapshotService;

    @GetMapping
    public List<SnapshotItem> getVersions(@RequestParam(defaultValue = "KOREA") ClassificationMarket market) {
        return customSnapshotService.getVersions(market);
    }

    @GetMapping("/current")
    public SnapshotItem current(@RequestParam(defaultValue = "KOREA") ClassificationMarket market) {
        return customSnapshotService.currentVersion(market);
    }

    @PostMapping
    public SnapshotItem save(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @RequestBody @Valid SnapshotLabelRequest request) {
        return customSnapshotService.save(market, request.label());
    }

    @PatchMapping("/{id}")
    public SnapshotItem overwrite(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @PathVariable Long id,
            @RequestBody @Valid SnapshotLabelRequest request) {
        return customSnapshotService.overwrite(market, id, request.label());
    }

    @PostMapping("/{id}/restore")
    public void restore(@RequestParam(defaultValue = "KOREA") ClassificationMarket market, @PathVariable Long id) {
        customSnapshotService.restore(market, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestParam(defaultValue = "KOREA") ClassificationMarket market, @PathVariable Long id) {
        customSnapshotService.delete(market, id);
    }
}
