package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SectorPriceCacheServiceTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 8, 10, 0);
    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final SectorPriceCacheService service = new SectorPriceCacheService(repository);

    @Test
    void 전일_종가는_같은_현재가_데이터의_현재가에서_변동액을_뺀_값이다() {
        when(repository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), TIME))
                .thenReturn(List.of(price("000001", "1000", "20"), price("000002", "1000", "-50")));

        var prices = service.getCache(Market.KOSPI, TIME);

        assertThat(prices.get("000001").lastPrice()).isEqualByComparingTo("980");
        assertThat(prices.get("000002").lastPrice()).isEqualByComparingTo("1050");
        assertThat(prices.get("000001").currentPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void 현재가에서_변동액을_뺀_값이_0_이하이면_전일_종가를_비워_둔다() {
        when(repository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), TIME))
                .thenReturn(List.of(price("000001", "100", "100"), price("000002", "100", "130")));

        var prices = service.getCache(Market.KOSPI, TIME);

        assertThat(prices.get("000001").lastPrice()).isNull();
        assertThat(prices.get("000002").lastPrice()).isNull();
    }

    private SectorPriceSnapshot price(String code, String currentPrice, String changeValue) {
        return SectorPriceSnapshot.create(
                Market.KOSPI,
                TIME,
                code,
                ExchangeType.KRX,
                "종목",
                new BigDecimal(currentPrice),
                new BigDecimal(changeValue),
                BigDecimal.ONE);
    }
}
