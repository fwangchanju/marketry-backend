package dev.eolmae.marketry.domain.custom.controller;

import dev.eolmae.marketry.domain.custom.dto.CreateSectorRequest;
import dev.eolmae.marketry.domain.custom.dto.ReparentRequest;
import dev.eolmae.marketry.domain.custom.dto.SectorDeletePreview;
import dev.eolmae.marketry.domain.custom.dto.SectorItem;
import dev.eolmae.marketry.domain.custom.dto.SectorNameRequest;
import dev.eolmae.marketry.domain.custom.service.CustomSectorService;
import jakarta.validation.Valid;
import java.util.List;
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

@RequestMapping("/api/custom/sectors")
@RestController
@RequiredArgsConstructor
public class CustomSectorController {

    private final CustomSectorService customSectorService;

    @GetMapping
    public List<SectorItem> getSectors() {
        return customSectorService.getSectors();
    }

    @DeleteMapping
    public void reset() {
        customSectorService.reset();
    }

    @PostMapping
    public SectorItem create(@RequestBody @Valid CreateSectorRequest request) {
        if (request.parentId() == null) {
            return customSectorService.createParent(request.name());
        }
        return customSectorService.createChild(request.name(), request.parentId());
    }

    @PatchMapping("/{id}/name")
    public void rename(@PathVariable Long id, @RequestBody @Valid SectorNameRequest request) {
        customSectorService.rename(id, request.name());
    }

    @PatchMapping("/{id}/parent")
    public void reparent(@PathVariable Long id, @RequestBody ReparentRequest request) {
        customSectorService.reparent(id, request.parentId());
    }

    @GetMapping("/{id}/delete-preview")
    public SectorDeletePreview deletePreview(@PathVariable Long id) {
        return customSectorService.deletePreview(id);
    }

    /** expectedMoveCount는 미리보기에서 확인한 이동 종목 수다. 보내면 지금 수와 다를 때 삭제하지 않고 409로 다시 확인하게 한다. */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestParam(required = false) Integer expectedMoveCount) {
        customSectorService.delete(id, expectedMoveCount);
    }
}
