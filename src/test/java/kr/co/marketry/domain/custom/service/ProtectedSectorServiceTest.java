package kr.co.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.enums.ProtectedSector;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProtectedSectorServiceTest {

    private static final long USER_ID = 41L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ProtectedSectorService service = new ProtectedSectorService(jdbcTemplate);

    @Test
    void ensureForUser_보호_업종_세_개를_각각_없을_때만_추가한다() {
        service.ensureForUser(USER_ID, ClassificationMarket.KOREA);

        for (ProtectedSector kind : ProtectedSector.values()) {
            verify(jdbcTemplate).update(anyString(), eq(USER_ID), eq("KOREA"), eq(kind.getSectorName()));
        }
    }

    @Test
    void ensureForAllUsers_추가된_행_수를_합해서_돌려준다() {
        when(jdbcTemplate.update(anyString(), eq("신규 상장"))).thenReturn(2);
        when(jdbcTemplate.update(anyString(), eq("거래 정지"))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), eq("미분류"))).thenReturn(1);

        assertThat(service.ensureForAllUsers()).isEqualTo(3);
        verify(jdbcTemplate, times(3)).update(anyString(), anyString());
    }

    @Test
    void 보호_업종_이름은_띄어쓰기까지_정해진_값이다() {
        assertThat(ClassificationMarket.KOREA.protectedSectorNames()).containsExactly("미분류", "신규 상장", "거래 정지");
        assertThat(ClassificationMarket.KOREA.isReservedName("거래정지")).isFalse();
        assertThat(ClassificationMarket.KOREA.isReservedName("거래 정지")).isTrue();
    }

    @Test
    void ensureForUser_일본과_미국에는_미분류만_추가한다() {
        service.ensureForUser(USER_ID, ClassificationMarket.JAPAN);

        verify(jdbcTemplate).update(anyString(), eq(USER_ID), eq("JAPAN"), eq("미분류"));
        verify(jdbcTemplate, times(1)).update(anyString(), eq(USER_ID), anyString(), anyString());
        assertThat(ClassificationMarket.USA.isProtectedName("거래 정지")).isFalse();
        assertThat(ClassificationMarket.USA.isReservedName("거래 정지")).isTrue();
    }
}
