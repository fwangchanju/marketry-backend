package kr.co.marketry.common.util;

public final class StockCode {

    private StockCode() {}

    private static final String SUFFIX_SEPARATOR = "_";

    /**
     * 종목코드에서 거래소 suffix(예: {@code _AL}, {@code _NX})를 제거하고 첫 {@code '_'} 앞부분만 반환.
     * <p>예) {@code "000660_AL"} → {@code "000660"}, {@code "000660"} → {@code "000660"}.
     * null·공백이면 빈 문자열, suffix가 없으면 trim한 전체를 반환한다.
     */
    public static String removeSuffix(String stockCode) {
        return Strings.substringBefore(Strings.trimToEmpty(stockCode), SUFFIX_SEPARATOR);
    }
}
