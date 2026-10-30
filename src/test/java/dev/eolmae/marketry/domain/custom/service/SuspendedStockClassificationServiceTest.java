package dev.eolmae.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.event.StockSuspensionSyncedEvent;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import dev.eolmae.marketry.domain.stock.service.StockTradingSuspensionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

class SuspendedStockClassificationServiceTest {

    private static final long USER_ID = 41L;
    private static final long PUBLISHED_USER_ID = 8L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ProtectedSectorService protectedSectorService = mock(ProtectedSectorService.class);
    private final StockTradingSuspensionService suspensionService = mock(StockTradingSuspensionService.class);
    private final MarketryProperties marketryProperties = new MarketryProperties(null, 1L, PUBLISHED_USER_ID);
    private final SuspendedStockClassificationService service = new SuspendedStockClassificationService(
            jdbcTemplate, protectedSectorService, suspensionService, marketryProperties);

    @Test
    void 정지_종목은_거래_정지로_재개_종목은_미분류로_모든_사용자에게_옮긴다() {
        service.onSuspensionSynced(new StockSuspensionSyncedEvent(Set.of("005930"), Set.of("000660")));

        verify(protectedSectorService).ensureForAllUsers();
        verify(jdbcTemplate).update(contains("INSERT INTO custom_stock_sector"), any(PreparedStatementSetter.class));
        verify(jdbcTemplate).update(contains("UPDATE custom_stock_sector"), any(PreparedStatementSetter.class));
    }

    @Test
    void 옮길_종목이_없으면_아무것도_하지_않는다() {
        service.onSuspensionSynced(new StockSuspensionSyncedEvent(Set.of(), Set.of()));

        verify(protectedSectorService, never()).ensureForAllUsers();
        verify(jdbcTemplate, never()).update(anyString(), any(PreparedStatementSetter.class));
    }

    @Test
    void 정지_종목만_있으면_재개_쪽_갱신은_하지_않는다() {
        service.onSuspensionSynced(new StockSuspensionSyncedEvent(Set.of("005930"), Set.of()));

        verify(jdbcTemplate).update(contains("INSERT INTO custom_stock_sector"), any(PreparedStatementSetter.class));
        verify(jdbcTemplate, never())
                .update(contains("UPDATE custom_stock_sector"), any(PreparedStatementSetter.class));
    }

    @Test
    void MARKETRY_발행_사용자는_자동으로_옮기지_않는다() {
        when(suspensionService.findExcludedStockCodes(any(LocalDate.class))).thenReturn(Set.of("005930"));

        service.applyCurrentExclusionsToUser(PUBLISHED_USER_ID);

        verify(jdbcTemplate, never()).update(anyString(), any(PreparedStatementSetter.class));
    }

    @Test
    void 한_사용자만_다시_맞출_때는_그_사용자의_보호_업종을_채운다() {
        when(suspensionService.findExcludedStockCodes(any(LocalDate.class))).thenReturn(Set.of("005930"));

        service.applyCurrentExclusionsToUser(USER_ID);

        verify(protectedSectorService).ensureForUser(USER_ID);
        verify(jdbcTemplate).update(contains("INSERT INTO custom_stock_sector"), any(PreparedStatementSetter.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 잠긴_종목은_지금_정지_중인_종목과_이_사용자의_거래_정지_업종에_있는_종목이다() {
        when(suspensionService.findExcludedStockCodes(any(LocalDate.class))).thenReturn(Set.of("005930", "999999"));
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenReturn(List.of("000660"));

        Set<String> locked = service.findLockedStockCodes(USER_ID, List.of("005930", "000660", "035420"));

        // 999999는 이번 요청에 없고, 035420은 정지도 거래 정지 업종도 아니다.
        assertThat(locked).containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    void 요청한_종목이_없으면_조회하지_않고_빈_집합을_돌려준다() {
        when(suspensionService.findExcludedStockCodes(any(LocalDate.class))).thenReturn(Set.of("005930"));

        assertThat(service.findLockedStockCodes(USER_ID, List.of())).isEmpty();
        verify(jdbcTemplate, never()).query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class));
        verify(protectedSectorService, never()).ensureForUser(eq(USER_ID));
    }
}
