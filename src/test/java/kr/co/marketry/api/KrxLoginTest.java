package kr.co.marketry.domain.view;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import okhttp3.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * KRX 데이터마켓 로그인 플로우(세션 발급 → 로그인 → 중복 로그인 재시도) 검증용.
 *
 * 실행 조건: LOGIN_ID/LOGIN_PW 상수를 실제 KRX 계정 정보로 채워야 한다(현재 placeholder).
 * 실행 명령: ./gradlew manualTest --tests "*.KrxLoginTest" -i
 */
@Tag("manual")
class KrxLoginTest {

    static final String LOGIN_PAGE = "https://data.krx.co.kr/contents/MDC/COMS/client/MDCCOMS001.cmd";
    static final String LOGIN_JSP = "https://data.krx.co.kr/contents/MDC/COMS/client/view/login.jsp?site=mdc";
    static final String LOGIN_URL = "https://data.krx.co.kr/contents/MDC/COMS/client/MDCCOMS001D1.cmd";
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    static final String LOGIN_ID = "id";
    static final String LOGIN_PW = "password";

    @Test
    void KRX_로그인() throws Exception {

        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(new CookieManager()))
                .build();

        // Step 1: 초기 JSESSIONID 발급
        client.newCall(new Request.Builder()
                        .url(LOGIN_PAGE)
                        .header("User-Agent", USER_AGENT)
                        .build())
                .execute()
                .close();

        // Step 2: iframe 세션 초기화
        client.newCall(new Request.Builder()
                        .url(LOGIN_JSP)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", LOGIN_PAGE)
                        .build())
                .execute()
                .close();

        // Step 3: 로그인
        RequestBody body = new FormBody.Builder()
                .add("mbrNm", "")
                .add("telNo", "")
                .add("di", "")
                .add("certType", "")
                .add("mbrId", LOGIN_ID)
                .add("pw", LOGIN_PW)
                .build();

        String responseBody;
        try (Response resp = client.newCall(new Request.Builder()
                        .url(LOGIN_URL)
                        .post(body)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", LOGIN_PAGE)
                        .build())
                .execute()) {
            responseBody = resp.body().string();
            System.out.println("로그인 응답: " + responseBody);
        }

        // 중복 로그인 처리
        if (responseBody.contains("CD011")) {
            System.out.println("중복 로그인 감지, skipDup=Y 재시도...");
            try (Response resp2 = client.newCall(new Request.Builder()
                            .url(LOGIN_URL)
                            .post(new FormBody.Builder()
                                    .add("mbrNm", "")
                                    .add("telNo", "")
                                    .add("di", "")
                                    .add("certType", "")
                                    .add("mbrId", LOGIN_ID)
                                    .add("pw", LOGIN_PW)
                                    .add("skipDup", "Y")
                                    .build())
                            .header("User-Agent", USER_AGENT)
                            .header("Referer", LOGIN_PAGE)
                            .build())
                    .execute()) {
                responseBody = resp2.body().string();
                System.out.println("재시도 응답: " + responseBody);
            }
        }

        assertTrue(responseBody.contains("CD001"), "로그인 실패: " + responseBody);
    }
}
