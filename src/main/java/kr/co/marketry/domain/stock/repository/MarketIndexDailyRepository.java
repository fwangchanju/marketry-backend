package kr.co.marketry.domain.stock.repository;

import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketIndexDailyRepository extends JpaRepository<MarketIndexDaily, MarketIndexDaily.Id> {}
