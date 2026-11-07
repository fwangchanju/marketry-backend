package kr.co.marketry.domain.stock.enums;

public enum StockMarketCode {
    KOSPI("0"), // 유가증권시장(코스피) 주권
    ELW("3"), // ELW
    MUTUAL_FUND("4"), // 뮤추얼펀드
    NEW_SHARES("5"), // 신주인수권
    REITS("6"), // 리츠(REITs)
    ETF("8"), // ETF
    HIGH_YIELD("9"), // 하이일드펀드
    KOSDAQ("10"), // 코스닥 주권
    K_OTC("30"), // K-OTC
    KONEX("50"); // 코넥스(KONEX)

    private final String code;

    StockMarketCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean matches(String marketCode) {
        return code.equals(marketCode);
    }

    public static boolean isOrdinaryShare(String marketCode) {
        return KOSPI.matches(marketCode) || KOSDAQ.matches(marketCode);
    }

    /** 신규 상장 업종에 자동 배정할 대상인지: 주권이거나, 이름에 "스팩"이 들어간 종목(신주인수권·ELW는 제외). */
    public static boolean isNewListingTarget(String marketCode, String stockName) {
        if (isOrdinaryShare(marketCode)) {
            return true;
        }
        return stockName != null
                && stockName.contains("스팩")
                && !NEW_SHARES.matches(marketCode)
                && !ELW.matches(marketCode);
    }
}
