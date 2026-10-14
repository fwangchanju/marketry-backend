package dev.eolmae.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TreemapItem(
        String stockCode,
        // 항상 실제 종목명(alias 치환 없음) — 관리 화면(StockSectorListItem)의 stockName/alias와
        // 동일한 관례. 박스 라벨처럼 alias를 우선해야 하는 표시는 프론트에서 "alias ?? stockName"으로 계산한다.
        String stockName,
        // 배정된 약칭. 없으면 null(빈 문자열 아님) — 커스텀 마켓맵에서만 값이 있을 수 있고, 기본 마켓맵은 항상 null.
        String alias,
        BigDecimal currentPrice,
        BigDecimal lastPrice,
        BigDecimal totalMarketValue,
        String marketValueTier,
        BigDecimal changeRate,
        LocalDateTime snapshotTime) {}
