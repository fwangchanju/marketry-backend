package kr.co.marketry.domain.custom.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.co.marketry.domain.custom.dto.SnapshotItem;
import kr.co.marketry.domain.custom.dto.SnapshotLabelRequest;
import kr.co.marketry.domain.custom.service.CustomSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/custom/snapshots")
@RestController
@RequiredArgsConstructor
public class CustomSnapshotController {

    private final CustomSnapshotService customSnapshotService;

    @GetMapping
    public List<SnapshotItem> getVersions() {
        return customSnapshotService.getVersions();
    }

    @GetMapping("/current")
    public SnapshotItem current() {
        return customSnapshotService.currentVersion();
    }

    @PostMapping
    public SnapshotItem save(@RequestBody @Valid SnapshotLabelRequest request) {
        return customSnapshotService.save(request.label());
    }

    @PatchMapping("/{id}")
    public SnapshotItem overwrite(@PathVariable Long id, @RequestBody @Valid SnapshotLabelRequest request) {
        return customSnapshotService.overwrite(id, request.label());
    }

    @PostMapping("/{id}/restore")
    public void restore(@PathVariable Long id) {
        customSnapshotService.restore(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        customSnapshotService.delete(id);
    }
}
