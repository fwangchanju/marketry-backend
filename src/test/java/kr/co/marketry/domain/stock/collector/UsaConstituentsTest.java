package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.co.marketry.domain.stock.collector.UsaConstituents.Constituent;
import org.junit.jupiter.api.Test;

class UsaConstituentsTest {
    private final List<Constituent> constituents = new UsaConstituents().all();

    @Test
    void 회사당_티커_하나로_500종목이고_티커가_겹치지_않는다() {
        Set<String> symbols = new HashSet<>();
        constituents.forEach(constituent -> symbols.add(constituent.symbol()));

        assertThat(constituents).hasSize(500);
        assertThat(symbols).hasSize(500);
    }

    @Test
    void 같은_회사의_다른_클래스는_빼고_대표_티커만_둔다() {
        Set<String> symbols = new HashSet<>();
        constituents.forEach(constituent -> symbols.add(constituent.symbol()));

        assertThat(symbols).contains("GOOGL", "FOXA", "NWSA");
        assertThat(symbols).doesNotContain("GOOG", "FOX", "NWS");
    }

    @Test
    void 종목_코드는_야후_표기라서_점_대신_하이픈을_쓴다() {
        Set<String> symbols = new HashSet<>();
        constituents.forEach(constituent -> symbols.add(constituent.symbol()));

        assertThat(symbols).contains("BRK-B", "BF-B");
        assertThat(symbols).noneMatch(symbol -> symbol.contains("."));
    }

    @Test
    void 쉼표가_든_회사_이름은_큰따옴표를_풀어서_읽는다() {
        assertThat(UsaConstituents.splitFields("XYZ,\"Block, Inc.\",2025-07-23"))
                .containsExactly("XYZ", "Block, Inc.", "2025-07-23");
        assertThat(constituents)
                .anyMatch(constituent ->
                        constituent.symbol().equals("XYZ") && constituent.name().equals("Block, Inc."));
    }

    @Test
    void 큰따옴표_두_개는_큰따옴표_한_개로_읽는다() {
        assertThat(UsaConstituents.splitFields("A,\"He said \"\"hi\"\"\",2020-01-01"))
                .containsExactly("A", "He said \"hi\"", "2020-01-01");
    }
}
