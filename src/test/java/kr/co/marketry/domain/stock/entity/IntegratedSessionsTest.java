package kr.co.marketry.domain.stock.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IntegratedSessionsTest {
    @Test
    void DB용_JSON에서도_거래시간과_누락세션을_왕복한다() {
        var mapper = new JacksonJsonFormatMapper();
        var start = OffsetDateTime.parse("2026-10-09T09:00:00+09:00");
        var end = OffsetDateTime.parse("2026-10-09T15:30:00+09:00");
        var integrated = new IntegratedSessions(null, new TradingSession(start, null, null, end), null);

        String json = mapper.toString(integrated, IntegratedSessions.class);
        IntegratedSessions restored = mapper.fromString(json, IntegratedSessions.class);

        assertThat(restored.preMarket()).isNull();
        assertThat(restored.afterMarket()).isNull();
        assertThat(restored.regularMarket().startTime().toInstant()).isEqualTo(start.toInstant());
        assertThat(restored.regularMarket().endTime().toInstant()).isEqualTo(end.toInstant());
    }

    @Test
    void 시간대와누락세션을JSON왕복에서보존한다() {
        var mapper = JsonMapper.builder().build();
        var start = OffsetDateTime.parse("2025-11-13T10:00:00+09:00");
        var end = OffsetDateTime.parse("2025-11-13T16:30:00+09:00");
        var integrated = new IntegratedSessions(null, new TradingSession(start, null, null, end), null);
        var restored = mapper.readValue(mapper.writeValueAsString(integrated), IntegratedSessions.class);
        assertThat(restored).isEqualTo(integrated);
        assertThat(restored.regularMarket().startTime().getOffset()).isEqualTo(start.getOffset());
    }
}
