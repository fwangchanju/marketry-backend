package kr.co.marketry.domain.stock.repository;

import kr.co.marketry.domain.stock.entity.OverseasClassificationPublication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasClassificationPublicationRepository
        extends JpaRepository<OverseasClassificationPublication, String> {}
