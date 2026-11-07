package kr.co.marketry.domain.stock.repository;

import java.util.List;
import kr.co.marketry.domain.stock.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockInfoRepository extends JpaRepository<StockInfo, String> {

    List<StockInfo> findByActiveTrue();
}
