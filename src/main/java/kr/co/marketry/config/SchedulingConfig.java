package kr.co.marketry.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

// 수집·발송·정리 작업은 운영 프로필에서만 예약한다. 운영 수동 발송 테스트는
// scheduling.enabled=false로 예약 작업을 따로 끌 수 있다.
@Configuration
@Profile("prod")
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {}
