package kr.co.marketry.domain.custom.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.custom.dto.CreateSectorRequest;
import kr.co.marketry.domain.custom.dto.ReparentRequest;
import kr.co.marketry.domain.custom.dto.SectorDeletePreview;
import kr.co.marketry.domain.custom.dto.SectorItem;
import kr.co.marketry.domain.custom.dto.SectorNameRequest;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.service.CustomSectorService;
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
    public List<SectorItem> getSectors(@RequestParam(defaultValue = "KOREA") ClassificationMarket market) {
        return customSectorService.getSectors(market);
    }

    @GetMapping("/edited-at")
    public EditedAt getEditedAt(@RequestParam(defaultValue = "KOREA") ClassificationMarket market) {
        return new EditedAt(customSectorService.findEditedAt(market));
    }

    public record EditedAt(LocalDateTime editedAt) {}

    @DeleteMapping
    public void reset(@RequestParam(defaultValue = "KOREA") ClassificationMarket market) {
        customSectorService.reset(market);
    }

    @PostMapping
    public SectorItem create(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @RequestBody @Valid CreateSectorRequest request) {
        if (request.parentId() == null) {
            return customSectorService.createParent(market, request.name());
        }
        return customSectorService.createChild(market, request.name(), request.parentId());
    }

    @PatchMapping("/{id}/name")
    public void rename(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @PathVariable Long id,
            @RequestBody @Valid SectorNameRequest request) {
        customSectorService.rename(market, id, request.name());
    }

    @PatchMapping("/{id}/parent")
    public void reparent(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @PathVariable Long id,
            @RequestBody ReparentRequest request) {
        customSectorService.reparent(market, id, request.parentId());
    }

    @GetMapping("/{id}/delete-preview")
    public SectorDeletePreview deletePreview(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market, @PathVariable Long id) {
        return customSectorService.deletePreview(market, id);
    }

    /** expectedMoveCount는 미리보기에서 확인한 이동 종목 수다. 지금 수와 다르면 삭제하지 않고 409로 다시 확인하게 한다. */
    @DeleteMapping("/{id}")
    public void delete(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @PathVariable Long id,
            @RequestParam int expectedMoveCount) {
        customSectorService.delete(market, id, expectedMoveCount);
    }
}
