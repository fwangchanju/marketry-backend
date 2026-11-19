package kr.co.marketry.domain.stock.controller;

import kr.co.marketry.domain.stock.service.JapanStockKoreanNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/japan-stock-names")
@RequiredArgsConstructor
public class AdminJapanStockKoreanNameController {
    private final JapanStockKoreanNameService service;

    @PutMapping("/{stockCode}")
    public void update(@PathVariable String stockCode, @RequestBody NameRequest request) {
        service.update(stockCode, request.name());
    }

    public record NameRequest(String name) {}
}
