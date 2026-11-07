package kr.co.marketry.common.util;

import kr.co.marketry.common.enums.Market;

public final class MarketLabels {

    private MarketLabels() {}

    /** 화면·메시지 표시용 한글 마켓명. Market이 늘어나면 이 switch도 함께 고쳐야 하므로, 새 값을 빠뜨리면
     * 컴파일 에러로 바로 드러난다. */
    public static String toKorean(Market market) {
        return switch (market) {
            case KOSPI -> "코스피";
            case KOSDAQ -> "코스닥";
        };
    }
}
