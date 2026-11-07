package kr.co.marketry.domain.stock.enums;

public enum StexType {
    KRX("1"), // stex_tp: 1=KRX 단독
    NXT("2"), // stex_tp: 2=NXT 단독
    KRX_NXT("3"); // stex_tp: 3=KRX+NXT 합산

    private final String code;

    StexType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
