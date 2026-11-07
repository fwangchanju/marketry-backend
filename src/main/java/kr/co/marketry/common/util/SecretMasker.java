package kr.co.marketry.common.util;

// 로그·알림 문구에 비밀값(토큰 등)이 섞여 나올 때, 호출부가 이미 알고 있는 그 값을 문자열에서
// 치환해 가린다. URL 형태를 파싱하지 않고 알려진 값 자체를 찾아 지우므로 어디에 박혀 있어도 걸린다.
public final class SecretMasker {

    private static final String MASK = "***";

    private SecretMasker() {}

    public static String mask(String text, String secret) {
        if (text == null || secret == null || secret.isEmpty()) {
            return text;
        }
        return text.replace(secret, MASK);
    }
}
