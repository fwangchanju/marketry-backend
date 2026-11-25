package kr.co.marketry.domain.custom.enums;

import java.util.List;

/**
 * 회원 분류(업종·종목 배정·버전)가 속한 시장. 분류를 읽거나 지울 때는 항상 시장을 함께 넘긴다.
 * 분류 동작은 시장과 상관없이 같고, 시장마다 다른 규칙만 여기에 둔다.
 */
public enum ClassificationMarket {
    KOREA,
    JAPAN,
    USA;

    private List<ProtectedSector> protectedSectors() {
        return switch (this) {
            case KOREA -> List.of(ProtectedSector.values());
            case JAPAN, USA -> List.of(ProtectedSector.UNCLASSIFIED);
        };
    }

    public List<String> protectedSectorNames() {
        return protectedSectors().stream().map(ProtectedSector::getSectorName).toList();
    }

    public boolean isReservedName(String name) {
        return protectedSectorNames().contains(name);
    }

    /** 거래 정지 종목을 거래 정지 업종에 자동으로 넣고 잠그는 시장인지. */
    public boolean locksSuspendedStocks() {
        return this == KOREA;
    }

    /** 버전에 약칭·색상·시가총액 구간·화면 설정까지 담는 시장인지. 아니면 업종과 종목 배정만 담고 되돌린다. */
    public boolean keepsSettingsInSnapshot() {
        return this == KOREA;
    }
}
