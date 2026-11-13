package kr.co.marketry.domain.stock.collector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 미국 시세 수집 대상 종목 목록. 회사당 대표 티커 하나만 둔다(구글은 GOOGL, 폭스는 FOXA, 뉴스코프는 NWSA).
 *
 * <p>출처는 위키백과 "List of S&amp;P 500 companies"(CC BY-SA)이고 종목 코드는 야후 표기(BRK-B)다. S&amp;P가 종목을 바꾸면
 * 목록을 직접 갱신해야 한다.
 */
@Component
public class UsaConstituents {
    private static final String RESOURCE = "usa-constituents.csv";

    private final List<Constituent> constituents;

    @Autowired
    public UsaConstituents() {
        this(openResource());
    }

    /** 목록 파일 대신 다른 입력을 읽는 생성자. 테스트에서 작은 목록을 만들 때 쓴다. */
    public UsaConstituents(InputStream csv) {
        this.constituents = parse(csv);
    }

    public List<Constituent> all() {
        return constituents;
    }

    private static InputStream openResource() {
        try {
            return new ClassPathResource(RESOURCE).getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Constituent> parse(InputStream csv) {
        List<Constituent> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            reader.readLine();
            String line = reader.readLine();
            while (line != null) {
                List<String> fields = splitFields(line);
                result.add(new Constituent(fields.get(0), fields.get(1), fields.get(2)));
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(result);
    }

    /** 쉼표가 든 이름은 큰따옴표로 감싸 있고, 큰따옴표 두 개는 큰따옴표 한 개다. */
    static List<String> splitFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted && current == '"' && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                field.append('"');
                index++;
            } else if (current == '"') {
                quoted = quoted == false;
            } else if (current == ',' && quoted == false) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    public record Constituent(String symbol, String name, String dateAdded) {}
}
