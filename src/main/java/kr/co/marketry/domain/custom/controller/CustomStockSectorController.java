package kr.co.marketry.domain.custom.controller;

import jakarta.validation.Valid;
import java.util.Map;
import kr.co.marketry.domain.custom.dto.AliasRequest;
import kr.co.marketry.domain.custom.dto.BulkAssignRequest;
import kr.co.marketry.domain.custom.dto.BulkAssignResponse;
import kr.co.marketry.domain.custom.dto.ClassificationUploadRequest;
import kr.co.marketry.domain.custom.dto.ClassificationUploadResponse;
import kr.co.marketry.domain.custom.dto.SectorIdRequest;
import kr.co.marketry.domain.custom.dto.StockSectorListItem;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.service.CustomStockSectorService;
import kr.co.marketry.domain.view.dto.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/custom/stock-sectors")
@RestController
@RequiredArgsConstructor
public class CustomStockSectorController {

    private final CustomStockSectorService customStockSectorService;

    @GetMapping
    public SnapshotResponse<StockSectorListItem> getStockSectors() {
        return customStockSectorService.getStockSectors();
    }

    @GetMapping("/assignments")
    public Map<String, Long> getAssignments(@RequestParam(defaultValue = "KOREA") ClassificationMarket market) {
        return customStockSectorService.getAssignments(market);
    }

    @PutMapping("/{stockCode}")
    public void assign(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @PathVariable String stockCode,
            @RequestBody @Valid SectorIdRequest request) {
        customStockSectorService.assign(market, stockCode, request.sectorId());
    }

    @PatchMapping("/bulk")
    public BulkAssignResponse bulkAssign(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @RequestBody @Valid BulkAssignRequest request) {
        return customStockSectorService.bulkAssign(market, request.stockCodes(), request.sectorId());
    }

    @PostMapping("/upload")
    public ClassificationUploadResponse upload(
            @RequestParam(defaultValue = "KOREA") ClassificationMarket market,
            @RequestBody @Valid ClassificationUploadRequest request) {
        return customStockSectorService.uploadClassification(market, request);
    }

    @PatchMapping("/{stockCode}/alias")
    public void updateAlias(@PathVariable String stockCode, @RequestBody @Valid AliasRequest request) {
        customStockSectorService.updateAlias(stockCode, request.alias());
    }
}
