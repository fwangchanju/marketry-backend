package kr.co.marketry.domain.stock.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface OverseasStockQuoteRepositoryCustom {
    List<LocalDateTime> findSnapshotTimes(String market);
}
