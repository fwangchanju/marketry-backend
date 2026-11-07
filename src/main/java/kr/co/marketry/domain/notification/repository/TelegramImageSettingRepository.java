package kr.co.marketry.domain.notification.repository;

import kr.co.marketry.domain.notification.entity.TelegramImageSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramImageSettingRepository extends JpaRepository<TelegramImageSetting, Long> {}
