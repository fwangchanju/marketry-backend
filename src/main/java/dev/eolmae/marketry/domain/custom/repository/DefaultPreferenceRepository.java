package dev.eolmae.marketry.domain.custom.repository;

import dev.eolmae.marketry.domain.custom.entity.DefaultPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefaultPreferenceRepository extends JpaRepository<DefaultPreference, Long> {}
