package kr.co.marketry.domain.stock.enums;

import kr.co.marketry.common.util.Strings;

// 키움 stk_cd 거래소 suffix 규칙: 없음=KRX, _NX=NXT, _AL=SOR(최선주문집행)
public enum ExchangeType {
    KRX,
    NXT,
    SOR;

    private static final String NXT_SUFFIX = "_NX";
    private static final String SOR_SUFFIX = "_AL";

    public static ExchangeType from(String stockCode) {
        String code = Strings.trimToEmpty(stockCode);
        if (code.endsWith(NXT_SUFFIX)) {
            return NXT;
        }
        if (code.endsWith(SOR_SUFFIX)) {
            return SOR;
        }
        return KRX;
    }

    public boolean isKrx() {
        return this == KRX;
    }
}
