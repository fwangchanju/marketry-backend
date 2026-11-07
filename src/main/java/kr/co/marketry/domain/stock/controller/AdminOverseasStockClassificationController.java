package kr.co.marketry.domain.stock.controller;

import java.util.Map;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService.Classification;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overseas-stock-classifications")
@RequiredArgsConstructor
public class AdminOverseasStockClassificationController {
    private final OverseasStockClassificationService service;

    @GetMapping("/{market}")
    public Map<String, Classification> findAll(@PathVariable String market) {
        return service.findAll(market);
    }

    @PutMapping("/{market}/{stockCode}/{field}")
    public void update(
            @PathVariable String market,
            @PathVariable String stockCode,
            @PathVariable String field,
            @RequestBody ClassificationRequest request) {
        service.update(market, stockCode, field, request.value());
    }

    public record ClassificationRequest(String value) {}
}
