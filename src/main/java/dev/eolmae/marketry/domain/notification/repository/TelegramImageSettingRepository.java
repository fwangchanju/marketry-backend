package dev.eolmae.marketry.domain.notification.repository;

import dev.eolmae.marketry.domain.notification.entity.TelegramImageSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramImageSettingRepository extends JpaRepository<TelegramImageSetting, Long> {}
