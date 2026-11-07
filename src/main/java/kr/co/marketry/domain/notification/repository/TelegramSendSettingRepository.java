package kr.co.marketry.domain.notification.repository;

import kr.co.marketry.domain.notification.entity.TelegramSendSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramSendSettingRepository extends JpaRepository<TelegramSendSetting, Long> {}
