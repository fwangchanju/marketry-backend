package kr.co.marketry.domain.stock.service;

import java.util.regex.Pattern;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;

/**
 * 나라별 분류 규칙. 분류 서비스의 동작(추가·이름 변경·이동·삭제·배정)은 나라와 상관없이 같고, 나라마다 다른 규칙만 여기에 둔다.
 * 나라가 늘어 규칙이 크게 달라지면 상수마다 메서드를 덮어써서 표현한다.
 */
public enum CountryRules {
    // 도쿄 거래소: 대분류·중분류·소분류 세 단계, 종목코드는 숫자로 시작하는 네 글자.
    JAPAN(3, "[0-9][0-9A-Z]{3}"),
    // 미국 S&P 500: 일본과 같은 대분류·중분류·소분류 세 단계, 종목코드는 영문 티커(BRK-B처럼 클래스 문자가 붙을 수 있다).
    USA(3, "[A-Z]{1,5}([.-][A-Z])?");

    private final int levels;
    private final Pattern stockCode;

    CountryRules(int levels, String stockCodePattern) {
        this.levels = levels;
        this.stockCode = Pattern.compile(stockCodePattern);
    }

    public int levels() {
        return levels;
    }

    public boolean isValidStockCode(String code) {
        return code != null && stockCode.matcher(code).matches();
    }

    public static CountryRules of(String market) {
        for (CountryRules candidate : values()) {
            if (candidate.name().equals(market)) return candidate;
        }
        throw new BadRequestException(ErrorCode.INVALID_INPUT);
    }
}
