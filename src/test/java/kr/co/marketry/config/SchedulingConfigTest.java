package kr.co.marketry.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * 이 레포에는 manual 태그가 아닌 @SpringBootTest가 없어서 ./gradlew test로는 SchedulingConfig가 한
 * 번도 안 돈다. 이 설정이 잘못되면(프로퍼티 이름 오타, 스캔 누락) 증상이 "조용히 수집이 멈춤"이라 배포
 * 뒤 다음 tick까지 아무도 모른다. 프로퍼티가 없을 때 켜지고, false일 때만 꺼지는 것을 여기서 고정한다.
 * @EnableScheduling이 등록하는 ScheduledAnnotationBeanPostProcessor의 유무로 판정한다.
 */
class SchedulingConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

    @Test
    void prod_프로필에서_프로퍼티가_없으면_스케줄링이_켜진다() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void prod_프로필이_아니면_스케줄링이_꺼진다() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void scheduling_enabled가_false면_스케줄링이_꺼진다() {
        runner.withPropertyValues("spring.profiles.active=prod", "scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
