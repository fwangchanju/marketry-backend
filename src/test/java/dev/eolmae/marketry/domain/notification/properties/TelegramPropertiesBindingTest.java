package dev.eolmae.marketry.domain.notification.properties;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.domain.view.enums.AverageMode;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * TelegramProperties는 record라 프로퍼티 이름을 하나라도 틀리면 조용히 0/null로 바인딩되고, 그
 * 뒤에야 {@link dev.eolmae.marketry.domain.notification.schedule.TelegramSendSchedule}의 기동
 * 검증이 실패한다 — 드러나는 시점이 배포 직후 컨테이너 기동 실패다. 이 레포에는 @Tag("manual")이 아닌
 * @SpringBootTest가 없어서 ./gradlew test로는 이 배선이 한 번도 돌지 않는다. DB도 외부 API도 필요
 * 없어 CI에서 도는 이 테스트로 배선 자체를 검증한다.
 *
 * <p>프로퍼티 값을 여기서 나열하지 않고 ConfigDataApplicationContextInitializer로 실제
 * application.properties를 읽는다. 값을 테스트가 직접 들고 있으면 record 컴포넌트 이름이 바뀌는 것만
 * 잡고, 정작 application.properties 쪽 키가 바뀌는 것은 못 잡는다 — 이 테스트가 막으려는 것이 바로
 * 그 상황이다. 환경변수로 받는 세 값만 여기서 채운다.
 */
class TelegramPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "TELEGRAM_BOT_TOKEN=token", "TELEGRAM_CHAT_ID=chat-id", "DEVELOPER_CHAT_ID=dev-chat-id");

    @Test
    void application_properties에_적힌_이름으로_바인딩된다() {
        contextRunner.run(context -> {
            TelegramProperties properties = context.getBean(TelegramProperties.class);
            assertThat(properties.botToken()).isEqualTo("token");
            assertThat(properties.chatId()).isEqualTo("chat-id");
            assertThat(properties.developerChatId()).isEqualTo("dev-chat-id");
            // application.properties에 적힌 값 그대로. 발송 주기를 바꾸면 여기도 같이 바꾼다 —
            // 의도적인 변경일 때만 고치게 되는 것이 이 단언의 목적이다.
            assertThat(properties.sendMinute()).isEqualTo(5);
            assertThat(properties.sendIntervalMinutes()).isEqualTo(30);
            assertThat(properties.beforeMinutes()).isEqualTo(15);
            assertThat(properties.averageMode()).isEqualTo(AverageMode.SIMPLE);
            assertThat(properties.sectorFilter()).isTrue();
            assertThat(properties.mapSendTimes())
                    .containsExactly(LocalTime.of(8, 15), LocalTime.of(9, 15), LocalTime.of(15, 30));
        });
    }

    @EnableConfigurationProperties(TelegramProperties.class)
    static class TestConfig {}
}
