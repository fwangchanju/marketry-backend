package kr.co.marketry.domain.stock.repository;

import java.util.Collection;
import java.util.List;
import kr.co.marketry.domain.stock.entity.IndustryInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryInfoRepository extends JpaRepository<IndustryInfo, Long> {

    List<IndustryInfo> findByNameIn(Collection<String> names);
}
