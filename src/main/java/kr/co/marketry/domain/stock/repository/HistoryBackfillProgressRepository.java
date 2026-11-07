package kr.co.marketry.domain.stock.repository;

import kr.co.marketry.domain.stock.entity.HistoryBackfillProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryBackfillProgressRepository
        extends JpaRepository<HistoryBackfillProgress, HistoryBackfillProgress.Id> {}
