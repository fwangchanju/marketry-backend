package kr.co.marketry.domain.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import kr.co.marketry.common.event.StockInfoSyncedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

class NewStockListingPropagationServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NewStockListingPropagationService service = new NewStockListingPropagationService(jdbcTemplate);

    @Test
    void 신규_일반주가_없으면_전파하지_않는다() {
        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of()));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void 신규_일반주를_모든_사용자의_신규_상장_섹터에_일괄_배정한다() throws Exception {
        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of("005930", "000660")));

        InOrder order = Mockito.inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).execute(any(ConnectionCallback.class));
        order.verify(jdbcTemplate).update(contains("INSERT INTO custom_sector"), eq("신규 상장"));
        ArgumentCaptor<PreparedStatementSetter> setter = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        order.verify(jdbcTemplate).update(contains("INSERT INTO custom_stock_sector"), setter.capture());

        PreparedStatement statement = mock(PreparedStatement.class);
        Connection connection = mock(Connection.class);
        java.sql.Array array = mock(java.sql.Array.class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("varchar"), any(Object[].class))).thenReturn(array);

        setter.getValue().setValues(statement);

        ArgumentCaptor<Object[]> stockCodes = ArgumentCaptor.forClass(Object[].class);
        verify(connection).createArrayOf(eq("varchar"), stockCodes.capture());
        org.assertj.core.api.Assertions.assertThat(stockCodes.getValue()).containsExactly("005930", "000660");
        verify(statement).setArray(1, array);
        verify(statement).setString(2, "신규 상장");
    }
}
