package kr.co.marketry.domain.custom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.repository.CustomSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomSnapshotRepository;
import kr.co.marketry.domain.custom.repository.CustomStockSectorRepository;
import org.junit.jupiter.api.Test;

/** 시장을 빠뜨린 조회·삭제 하나가 다른 시장의 분류를 지우거나 섞는다. 시장 없이 회원 전체를 다루는 길을 막는다. */
class ClassificationMarketScopeTest {
    private static final Path MAIN = Path.of("src/main/java");
    // 회원 탈퇴는 모든 시장을 지운다. 업종 번호로 찾는 조회는 그 업종의 시장을 벗어나지 않는다.
    private static final List<String> ALLOWED = List.of(
            "deleteAllOfWithdrawnUser",
            "findByIdUserIdAndSectorId",
            "findLockedByIdUserIdAndSectorId",
            "deleteByUserIdAndSectorIdIn");
    private static final Pattern SQL_TEXT = Pattern.compile("\"\"\"(.*?)\"\"\"|\"([^\"\\n]*)\"", Pattern.DOTALL);

    @Test
    void 분류_저장소의_조회와_삭제는_모두_시장을_받는다() {
        List<String> unscoped = Stream.of(
                        CustomSectorRepository.class, CustomStockSectorRepository.class, CustomSnapshotRepository.class)
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> method.getDeclaringClass().getName().startsWith("kr.co.marketry"))
                .filter(method -> ALLOWED.contains(method.getName()) == false)
                .filter(method ->
                        Arrays.asList(method.getParameterTypes()).contains(ClassificationMarket.class) == false)
                .map(Method::toString)
                .toList();

        assertThat(unscoped).isEmpty();
    }

    @Test
    void 회원_탈퇴용_삭제는_탈퇴_서비스만_부른다() throws IOException {
        assertThat(filesContaining("deleteAllOfWithdrawnUser("))
                .allMatch(name -> name.contains("Repository") || name.equals("AccountWithdrawalService.java"));
    }

    @Test
    void 분류_표를_직접_다루는_SQL에는_시장이_있다() throws IOException {
        List<String> unscoped = new ArrayList<>();
        for (Path file : javaFiles()) {
            Matcher matcher = SQL_TEXT.matcher(Files.readString(file));
            while (matcher.find()) {
                String sql = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
                boolean touches = Pattern.compile("(INTO|FROM|JOIN|UPDATE)\\s+custom_(sector|stock_sector|snapshot)\\b")
                        .matcher(sql)
                        .find();
                if (touches && sql.contains("market") == false) {
                    unscoped.add(file.getFileName() + ": " + sql.strip());
                }
            }
        }

        assertThat(unscoped).isEmpty();
    }

    private static List<String> filesContaining(String text) throws IOException {
        List<String> names = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (Files.readString(file).contains(text)) {
                names.add(file.getFileName().toString());
            }
        }
        return names;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
