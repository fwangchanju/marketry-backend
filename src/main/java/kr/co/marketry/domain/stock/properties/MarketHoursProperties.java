package kr.co.marketry.domain.stock.properties;

import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 거래소 시간표에서 나온 경계 시각 둘 — 종가 윈도우와 시간외 기준 적용 시작.
 *
 * <p>{@code closeWindowStart}(15:30)는 KRX 정규장 마감, {@code afterHoursStart}(15:40)는 NXT 애프터마켓 개장이다.
 * 그 10분은 KRX·NXT 둘 다 닫혀 있어 가격이 변하지 않는다. 종가 기준가는 시각 하나를 박지 않고 이 구간
 * {@code [closeWindowStart, afterHoursStart)}에 있는 스냅샷 중 가장 늦은 것을 쓴다.
 */
@ConfigurationProperties(prefix = "market")
public record MarketHoursProperties(LocalTime closeWindowStart, LocalTime afterHoursStart) {}
