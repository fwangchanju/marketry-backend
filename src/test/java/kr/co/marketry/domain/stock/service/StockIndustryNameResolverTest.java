package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.IndustryInfo;
import kr.co.marketry.domain.stock.entity.StockIndustryOverride;
import kr.co.marketry.domain.stock.entity.StockInfo;
import kr.co.marketry.domain.stock.repository.IndustryInfoRepository;
import kr.co.marketry.domain.stock.repository.StockIndustryOverrideRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StockIndustryNameResolverTest {

    private final IndustryInfoRepository industryInfoRepository = mock(IndustryInfoRepository.class);
    private final StockIndustryOverrideRepository overrideRepository = mock(StockIndustryOverrideRepository.class);
    private final StockIndustryNameResolver resolver =
            new StockIndustryNameResolver(industryInfoRepository, overrideRepository);

    @Test
    void 키움_업종이_없는_종목은_보정표_분류를_쓴다() {
        when(overrideRepository.findAll()).thenReturn(List.of(override("024110", 7L)));
        when(industryInfoRepository.findAllById(Set.of(7L))).thenReturn(List.of(industry(7L, "금융")));

        Map<String, String> result = resolver.resolve(List.of(stock("024110", null)));

        assertThat(result).containsExactly(Map.entry("024110", "금융"));
    }

    @Test
    void 키움_업종이_있으면_보정표보다_키움_값을_쓴다() {
        when(overrideRepository.findAll()).thenReturn(List.of(override("024110", 7L)));
        when(industryInfoRepository.findAllById(Set.of(3L))).thenReturn(List.of(industry(3L, "기계")));

        Map<String, String> result = resolver.resolve(List.of(stock("024110", 3L)));

        assertThat(result).containsExactly(Map.entry("024110", "기계"));
    }

    @Test
    void 키움_업종도_보정표도_없는_종목은_담지_않는다() {
        when(overrideRepository.findAll()).thenReturn(List.of());

        Map<String, String> result = resolver.resolve(List.of(stock("000001", null)));

        assertThat(result).isEmpty();
    }

    private StockInfo stock(String code, Long industryId) {
        return StockInfo.create(code, "이름", Market.KOSPI, "0", industryId, 1L, BigDecimal.ONE, false);
    }

    private IndustryInfo industry(Long id, String name) {
        IndustryInfo industry = IndustryInfo.create(name);
        ReflectionTestUtils.setField(industry, "id", id);
        return industry;
    }

    private StockIndustryOverride override(String code, Long industryId) {
        return StockIndustryOverride.create(code, industryId);
    }
}
