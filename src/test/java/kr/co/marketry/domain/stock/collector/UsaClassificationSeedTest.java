package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

// V45 분류 시드가 지도가 기대하는 모양인지 SQL 파일을 읽어 확인한다. 지도는 분류를 이름으로 찾으므로 이름이 겹치면 안 된다.
class UsaClassificationSeedTest {
    private static final String MARKET = "USA";
    private static final Pattern PAIR =
            Pattern.compile("^\\s+\\('((?:[^']|'')+)', '((?:[^']|'')+)'\\),?$", Pattern.MULTILINE);
    private static final Pattern STOCK = Pattern.compile(
            "^\\s+\\('USA', '((?:[^']|'')+)', '((?:[^']|'')+)', '((?:[^']|'')+)'\\),?$", Pattern.MULTILINE);
    private static final Pattern ROOT = Pattern.compile("^\\s+\\('USA', '((?:[^']|'')+)'\\),?$", Pattern.MULTILINE);

    private final String sql = readSeed();
    private final List<String> roots = matches(ROOT, 1);
    private final List<String[]> industries = pairs();
    private final Map<String, String[]> stocks = stocks();

    @Test
    void 모든_분류_이름은_40자_이하다() {
        List<String> names = new ArrayList<>(roots);
        industries.forEach(pair -> names.add(pair[1]));
        stocks.values().forEach(row -> names.addAll(List.of(row[0], row[1])));

        assertThat(names).allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(40));
    }

    @Test
    void 섹터와_업종_이름은_시장_안에서_겹치지_않는다() {
        List<String> names = new ArrayList<>(roots);
        industries.forEach(pair -> names.add(pair[1]));

        assertThat(new HashSet<>(names)).hasSameSizeAs(names);
    }

    @Test
    void 섹터_11개와_미분류가_있다() {
        assertThat(roots).hasSize(12).contains("미분류");
    }

    @Test
    void 모든_구성종목이_업종에_배정되고_업종은_자기_섹터_아래에_있다() {
        Set<String> industryKeys = new HashSet<>();
        industries.forEach(pair -> industryKeys.add(pair[0] + "/" + pair[1]));
        Set<String> constituents = new HashSet<>();
        new UsaConstituents().all().forEach(constituent -> constituents.add(constituent.symbol()));

        assertThat(stocks.keySet()).isEqualTo(constituents);
        assertThat(stocks.values()).allSatisfy(row -> assertThat(industryKeys).contains(row[0] + "/" + row[1]));
    }

    @Test
    void 이미_있는_값을_지우거나_덮어쓰는_문장이_없다() {
        assertThat(sql).doesNotContainIgnoringCase("DELETE").doesNotContainIgnoringCase("DROP");
        assertThat(sql).doesNotContainIgnoringCase("TRUNCATE").doesNotContainIgnoringCase("UPDATE ");
    }

    private static String readSeed() {
        try {
            return new String(
                    new ClassPathResource("flyway/V45__usa_classification_seed.sql")
                            .getInputStream()
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> matches(Pattern pattern, int group) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) result.add(matcher.group(group).replace("''", "'"));
        return result;
    }

    // 업종 VALUES 줄은 (섹터, 업종) 두 칸이고 종목 줄은 ('USA', 티커, 섹터, 업종) 네 칸이라 정규식이 서로 겹치지 않는다.
    private List<String[]> pairs() {
        List<String[]> result = new ArrayList<>();
        Matcher matcher = PAIR.matcher(sql);
        while (matcher.find()) {
            // 루트 줄 ('USA', 섹터)도 두 칸이라 업종에서 뺀다.
            if (matcher.group(1).equals(MARKET)) continue;
            result.add(new String[] {
                matcher.group(1).replace("''", "'"), matcher.group(2).replace("''", "'")
            });
        }
        return result;
    }

    private Map<String, String[]> stocks() {
        Map<String, String[]> result = new HashMap<>();
        Matcher matcher = STOCK.matcher(sql);
        while (matcher.find()) {
            result.put(matcher.group(1), new String[] {
                matcher.group(2).replace("''", "'"), matcher.group(3).replace("''", "'")
            });
        }
        return result;
    }
}
