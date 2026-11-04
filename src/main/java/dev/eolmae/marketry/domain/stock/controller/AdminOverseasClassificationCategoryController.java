package dev.eolmae.marketry.domain.stock.controller;

import dev.eolmae.marketry.domain.stock.service.OverseasClassificationCategoryService;
import dev.eolmae.marketry.domain.stock.service.OverseasClassificationCategoryService.Category;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overseas-classification-categories")
@RequiredArgsConstructor
public class AdminOverseasClassificationCategoryController {
    private final OverseasClassificationCategoryService service;

    @GetMapping("/{market}")
    public List<Category> findAll(@PathVariable String market) {
        return service.findAll(market);
    }

    @PostMapping("/{market}")
    public Category create(@PathVariable String market, @RequestBody CreateRequest request) {
        return service.create(market, request.parentId(), request.name());
    }

    @PutMapping("/{market}/{id}")
    public void rename(@PathVariable String market, @PathVariable long id, @RequestBody NameRequest request) {
        service.rename(market, id, request.name());
    }

    @PutMapping("/{market}/{id}/parent")
    public void move(@PathVariable String market, @PathVariable long id, @RequestBody ParentRequest request) {
        service.move(market, id, request.parentId());
    }

    @DeleteMapping("/{market}/{id}")
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
