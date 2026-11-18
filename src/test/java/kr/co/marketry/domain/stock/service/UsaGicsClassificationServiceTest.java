package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UsaGicsClassificationServiceTest {

    private final UsaGicsClassificationService service = new UsaGicsClassificationService(new ObjectMapper());

    @Test
    void 시드의_S_P_500_종목_500개를_모두_읽는다() {
        assertThat(service.findAll()).hasSize(500);
    }

    @Test
    void 종목의_GICS_4단계를_모두_돌려준다() {
        var apple = service.findAll().get("AAPL");

        assertThat(apple.sector()).isEqualTo("Information Technology");
        assertThat(apple.industryGroup()).isEqualTo("Technology Hardware & Equipment");
        assertThat(apple.industry()).isEqualTo("Technology Hardware, Storage & Peripherals");
        assertThat(apple.subIndustry()).isEqualTo("Technology Hardware, Storage & Peripherals");
        assertThat(apple.gicsCode()).isEqualTo("45202030");
    }

    @Test
    void 돌려준_목록은_고칠_수_없다() {
        assertThat(service.findAll()).isUnmodifiable();
    }
}
