package dev.eolmae.marketry.domain.custom.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 모든 사용자에게 항상 대분류로 존재하는 보호 업종. 삭제·이름 변경·이동·하위 업종 만들기가 불가능하고, 다른 업종이 같은 이름을 쓸 수도 없다.
 * 선언 순서가 화면의 표시 순서(맨 아래)다.
 */
public enum ProtectedSector {
    NEW_LISTING("신규 상장"), // 신규 상장 종목이 자동 배정된다
    SUSPENDED("거래 정지"), // 거래 정지 종목이 자동 배정된다
    UNCLASSIFIED("미분류"); // 업종 삭제 시 종목이 옮겨 온다

    private final String sectorName;

    ProtectedSector(String sectorName) {
        this.sectorName = sectorName;
    }

    public String getSectorName() {
        return sectorName;
    }

    public static List<String> sectorNames() {
        return Arrays.stream(values()).map(ProtectedSector::getSectorName).toList();
    }

    public static boolean isReservedName(String name) {
        return Arrays.stream(values()).anyMatch(sector -> sector.sectorName.equals(name));
    }
}
