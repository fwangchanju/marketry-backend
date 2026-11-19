package kr.co.marketry.domain.view.controller;

import java.util.List;
import kr.co.marketry.domain.view.dto.StockDailyCandle;
import kr.co.marketry.domain.view.service.StockChartQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/stocks")
@RestController
@RequiredArgsConstructor
public class StockChartController {

    private final StockChartQueryService stockChartQueryService;

    @GetMapping("/{stockCode}/daily-candles")
    public List<StockDailyCandle> getDailyCandles(
            @PathVariable String stockCode, @RequestParam(defaultValue = "365") int days) {
        return stockChartQueryService.getDailyCandles(stockCode, days);
    }

    @GetMapping("/japan/{stockCode}/daily-candles")
    public List<StockDailyCandle> getJapanDailyCandles(
            @PathVariable String stockCode, @RequestParam(defaultValue = "365") int days) {
        return stockChartQueryService.getJapanDailyCandles(stockCode, days);
    }

    @GetMapping("/usa/{stockCode}/daily-candles")
    public List<StockDailyCandle> getUsaDailyCandles(
            @PathVariable String stockCode, @RequestParam(defaultValue = "365") int days) {
        return stockChartQueryService.getUsaDailyCandles(stockCode, days);
    }
}
