package kr.co.marketry.domain.stock.util;

import java.math.BigDecimal;
import kr.co.marketry.common.util.NumberParser;
import kr.co.marketry.common.util.Strings;

// 키움 API 응답의 숫자 필드(콤마, "-" 무데이터 마커, 이중 음수 부호 등) 정규화 후 파싱
public final class KiwoomValueParser {

    private static final String NO_DATA_MARKER = "-";
    private static final String COMMA = ",";
    // errorprone은 인라인을 제안하지만, style.md §2(매직 리터럴 → 명명 상수)를 우선한다.
    @SuppressWarnings("InlineTrivialConstant")
    private static final String EMPTY = "";

    private static final String DOUBLE_NEGATIVE_PREFIX = "--";

    private KiwoomValueParser() {}

    // 키움 API 음수 오류 패턴: --1234.56 형태로 오는 경우 -1234.56으로 정규화
    private static String fixDoubleNegative(String value) {
        if (value.startsWith(DOUBLE_NEGATIVE_PREFIX)) {
            return value.substring(1);
        }
        return value;
    }

    public static BigDecimal parseBigDecimal(String value) {
        String normalized = Strings.trimToEmpty(value).replace(COMMA, EMPTY);
        if (NO_DATA_MARKER.equals(normalized)) {
            return BigDecimal.ZERO;
        }
        return NumberParser.parseBigDecimal(fixDoubleNegative(normalized));
    }

    public static long parseLong(String value) {
        String normalized = Strings.trimToEmpty(value).replace(COMMA, EMPTY);
        if (NO_DATA_MARKER.equals(normalized)) {
            return 0L;
        }
        return NumberParser.parseLong(normalized);
    }

    public static int parseInt(String value) {
        String normalized = Strings.trimToEmpty(value).replace(COMMA, EMPTY);
        if (NO_DATA_MARKER.equals(normalized)) {
            return 0;
        }
        return NumberParser.parseInt(normalized);
    }
}
