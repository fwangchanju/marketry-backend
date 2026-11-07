package kr.co.marketry.common.util;

/** apache commons-lang3 StringUtils 의존성 제거를 위한 내부 문자열 유틸리티. */
public final class Strings {

    // errorprone은 인라인을 제안하지만, style.md §2(매직 리터럴 → 명명 상수)를 우선한다.
    @SuppressWarnings("InlineTrivialConstant")
    private static final String EMPTY = "";

    private Strings() {}

    public static String trimToEmpty(String value) {
        return value == null ? EMPTY : value.trim();
    }

    public static String substringBefore(String value, String separator) {
        if (value == null || value.isEmpty()) {
            return EMPTY;
        }
        int index = value.indexOf(separator);
        return index == -1 ? value : value.substring(0, index);
    }
}
