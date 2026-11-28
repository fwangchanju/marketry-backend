package kr.co.marketry.rulesettest;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

// 보호 규칙 시험용: 일부러 실패한다. 이 PR은 병합하지 않고 닫는다.
class RulesetFailTest {
    @Test
    void 일부러_실패한다() {
        fail("보호 규칙 시험용 실패");
    }
}
