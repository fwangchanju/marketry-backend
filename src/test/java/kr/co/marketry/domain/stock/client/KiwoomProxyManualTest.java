package kr.co.marketry.domain.stock.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 이관 전 실제 프록시 인증과 읽기 전용 지수 조회를 확인한다. Spring 컨텍스트와 DB는 사용하지 않는다.
 *
 * 실행 조건: KIWOOM_APP_KEY, KIWOOM_APP_SECRET, MARKETRY_PROXY_HOST를 프로세스에 주입한다.
 * MARKETRY_PROXY_PORT는 생략하면 8888이다. 기존 운영 토큰에 미치는 영향을 확인한 뒤
 * MARKETRY_KIWOOM_TOKEN_TEST=1로 실제 토큰 발급을 허용한다.
 * 실행 명령: ./gradlew kiwoomProxyTest
 *
 * 맥미니의 앱 실행 환경과 같은 Colima 컨테이너에서 사용자가 실행한다.
 * 인증값·토큰·응답 원문은 출력하지 않고 실패를 건너뛰지 않는다.
 */
@Tag("manual")
class KiwoomProxyManualTest {

    @Test
    void 프록시를_통해_인증하고_읽기_전용_지수를_조회한다() {
        var indexValue = KiwoomProxyPreflight.run(System.getenv());
        System.out.println("PASS: api=ka20001 market=KOSPI return_code=0 index=" + indexValue.toPlainString());
    }
}
