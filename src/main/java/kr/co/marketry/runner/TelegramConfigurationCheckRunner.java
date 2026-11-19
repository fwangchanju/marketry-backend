package kr.co.marketry.runner;

import java.util.ArrayList;
import java.util.List;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영에서 텔레그램 봇 토큰이나 채팅방이 비어 있으면 앱을 띄우지 않는다.
 * 비어 있어도 앱은 뜨지만 정기 발송과 오류 알림(같은 봇)이 전부 실패해 아무도 알 수 없다(2026-11-19).
 * 시작이 실패하면 배포 게이트가 이전 컨테이너로 되돌린다. 값은 메시지에 싣지 않고 이름만 적는다.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class TelegramConfigurationCheckRunner implements ApplicationRunner {

    private final TelegramProperties telegramProperties;

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = findMissingEnvironmentVariables(telegramProperties);
        if (missing.isEmpty()) {
            return;
        }
        throw new IllegalStateException("운영 환경파일에 값이 비어 있는 텔레그램 설정이 있어 시작하지 않는다: " + missing);
    }

    static List<String> findMissingEnvironmentVariables(TelegramProperties properties) {
        List<String> missing = new ArrayList<>();
        if (isBlank(properties.botToken())) {
            missing.add("TELEGRAM_BOT_TOKEN");
        }
        if (isBlank(properties.chatId())) {
            missing.add("TELEGRAM_CHAT_ID");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
