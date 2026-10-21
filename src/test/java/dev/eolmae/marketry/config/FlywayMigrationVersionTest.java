package dev.eolmae.marketry.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** DB 없이도 마이그레이션 파일의 버전 번호가 겹치지 않는지 확인한다(겹치면 앱이 시작하지 못한다). */
class FlywayMigrationVersionTest {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/flyway");

    @Test
    void 마이그레이션_버전_번호는_겹치지_않는다() throws IOException {
        Map<Integer, String> filesByVersion = new TreeMap<>();
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.matches("V\\d+__.+\\.sql")) {
                    continue;
                }
                int version = Integer.parseInt(name.substring(1, name.indexOf("__")));
                String previous = filesByVersion.put(version, name);
                assertThat(previous)
                        .as("V%d 버전이 두 파일에 있다: %s, %s", version, previous, name)
                        .isNull();
            }
        }
        assertThat(filesByVersion).isNotEmpty();
    }
}
