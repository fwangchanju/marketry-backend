package kr.co.marketry.domain.stock.controller;

import kr.co.marketry.domain.stock.service.UsaStockKoreanNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/usa-stock-names")
@RequiredArgsConstructor
public class AdminUsaStockKoreanNameController {
    private final UsaStockKoreanNameService service;

    @PutMapping("/{symbol}")
    public void update(@PathVariable String symbol, @RequestBody NameRequest request) {
        service.update(symbol, request.name());
    }

    public record NameRequest(String name) {}
}
