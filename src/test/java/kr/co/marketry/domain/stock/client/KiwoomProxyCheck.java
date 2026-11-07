package kr.co.marketry.domain.stock.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 사용자가 OrbStack 또는 Colima에서 실행하는 독립 검사 프로그램. Spring 애플리케이션을 기동하지 않는다. */
public final class KiwoomProxyCheck {

    private static final Set<String> CONNECTION_KEYS =
            Set.of("KIWOOM_APP_KEY", "KIWOOM_APP_SECRET", "MARKETRY_PROXY_HOST", "MARKETRY_PROXY_PORT");

    private KiwoomProxyCheck() {}

    public static void main(String[] args) {
        try {
            Map<String, String> environment = new HashMap<>(System.getenv());
            if (args.length == 2 && "--env-file".equals(args[0])) {
                environment.putAll(connectionValues(Path.of(args[1])));
            } else if (args.length != 0) {
                throw new AssertionError("사용법: java -jar kiwoom-proxy-check.jar [--env-file <환경 파일 경로>]");
            }
            var indexValue = KiwoomProxyPreflight.run(environment);
            System.out.println("PASS: api=ka20001 market=KOSPI return_code=0 index=" + indexValue.toPlainString());
        } catch (AssertionError failure) {
            System.err.println(failure.getMessage());
            System.exit(1);
        } catch (RuntimeException failure) {
            // 예기치 못한 예외에도 파일 경로·연결 주소·인증 정보나 응답 원문을 출력하지 않는다.
            System.err.println("FAIL: 키움 프록시 검사 프로그램 실행에 실패했습니다.");
            System.exit(1);
        }
    }

    static Map<String, String> connectionValues(Path path) {
        Map<String, String> values = new HashMap<>();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String name = line.substring(0, separator).strip();
                if (CONNECTION_KEYS.contains(name) == false) {
                    continue;
                }
                String value = line.substring(separator + 1).strip();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(name, value);
            }
        } catch (IOException ignored) {
            throw new AssertionError("검사 프로그램에서 환경 파일을 읽을 수 없습니다. 계정과 파일 권한을 확인하세요.");
        }
        return values;
    }
}
