package kr.co.marketry.domain.stock.controller;

import java.util.List;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService.Category;
import kr.co.marketry.domain.stock.service.OverseasClassificationDraftService;
import kr.co.marketry.domain.stock.service.OverseasClassificationDraftService.DraftView;
import kr.co.marketry.domain.stock.service.OverseasClassificationDraftService.PublishResult;
import kr.co.marketry.domain.stock.service.OverseasClassificationDraftService.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 일본·미국 내 기준(초안) 편집과 MARKETRY 반영·롤백. 관리자 전용이라 /api/admin/ 아래에만 둔다. */
@RestController
@RequestMapping("/api/admin/overseas-classification-drafts")
@RequiredArgsConstructor
public class AdminOverseasClassificationDraftController {
    private final OverseasClassificationDraftService service;

    @GetMapping("/{market}")
    public DraftView findDraft(@PathVariable String market) {
        return service.findDraft(market);
    }

    // 내 기준을 마지막으로 고친 시각(없으면 null). 설정창 맨 아래 시각에 쓴다(관리자 전용).
    @GetMapping("/{market}/edited-at")
    public EditedAt findEditedAt(@PathVariable String market) {
        return new EditedAt(service.findEditedAt(market).orElse(null));
    }

    public record EditedAt(java.time.LocalDateTime editedAt) {}

    @GetMapping("/{market}/status")
    public Status findStatus(@PathVariable String market) {
        return service.findStatus(market);
    }

    @PostMapping("/{market}/discard")
    public DraftView discard(@PathVariable String market) {
        return service.discard(market);
    }

    @PostMapping("/{market}/publish")
    public PublishResult publish(@PathVariable String market) {
        return service.publish(market);
    }

    @PostMapping("/{market}/rollback")
    public void rollback(@PathVariable String market) {
        service.rollback(market);
    }

    @PostMapping("/{market}/categories")
    public Category create(@PathVariable String market, @RequestBody CreateRequest request) {
        return service.createCategory(market, request.parentId(), request.name());
    }

    @PutMapping("/{market}/categories/{id}")
    public void rename(@PathVariable String market, @PathVariable long id, @RequestBody NameRequest request) {
        service.rename(market, id, request.name());
    }

    @PutMapping("/{market}/categories/{id}/parent")
    public void move(@PathVariable String market, @PathVariable long id, @RequestBody ParentRequest request) {
        service.move(market, id, request.parentId());
    }

    @DeleteMapping("/{market}/categories/{id}")
    public void delete(@PathVariable String market, @PathVariable long id, @RequestBody DeleteRequest request) {
        service.delete(market, id, request.expectedMoveCount());
    }

    @PutMapping("/{market}/assign")
    public void assign(@PathVariable String market, @RequestBody AssignRequest request) {
        service.assign(market, request.stockCodes(), request.categoryId());
    }

    public record CreateRequest(Long parentId, String name) {}

    public record NameRequest(String name) {}

    public record ParentRequest(Long parentId) {}

    public record DeleteRequest(int expectedMoveCount) {}

    public record AssignRequest(List<String> stockCodes, long categoryId) {}
}
