package dev.eolmae.marketry.domain.notification.repository;

import dev.eolmae.marketry.domain.notification.entity.TelegramSendSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramSendSettingRepository extends JpaRepository<TelegramSendSetting, Long> {}
