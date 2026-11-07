package org.springframework.boot.logging.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.FileSize;
import java.net.URL;
import kr.co.marketry.common.logging.ThrowableFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * logback-spring.xml의 prod 프로파일 결선을 검증한다. {@code <springProfile>} 평가는
 * SpringBootJoranConfigurator/SpringProfileModelHandler(둘 다 패키지 전용)가 담당하므로, 이 테스트를
 * 같은 패키지에 둬서 실제 Spring Boot 로직 그대로 태그를 평가하게 한다. 전역(스태틱 싱글턴) LoggerContext를
 * 건드리지 않도록, 매 테스트마다 이 테스트 전용 LoggerContext 인스턴스를 새로 만들어 그 안에서만 로드한다.
 */
class LogbackSpringConfigurationTest {

    private LoggerContext context;

    @BeforeEach
    void loadProdConfiguration() throws Exception {
        context = new LoggerContext();
        context.setName("test");

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");

        SpringBootJoranConfigurator configurator =
                new SpringBootJoranConfigurator(new LoggingInitializationContext(environment));
        configurator.setContext(context);

        URL resource = getClass().getClassLoader().getResource("logback-spring.xml");
        configurator.doConfigure(resource);
    }

    // 설정을 로드하면 두 RollingFileAppender가 start()되면서 logs/ 아래 파일을 실제로 연다. 테스트마다
    // 새 LoggerContext를 만들므로 닫지 않으면 열린 파일이 테스트 수만큼 쌓인다.
    @AfterEach
    void stopContext() {
        context.stop();
    }

    // 7-3의 %i 누락처럼, logback은 설정에 문제가 있어도 예외를 던지지 않고 StatusManager에 ERROR로만
    // 쌓아둔 채 넘어간다. appender 존재만 확인하면 이런 오류가 조용히 통과할 수 있어 별도로 확인한다.
    @Test
    void 설정_로드_중_오류가_없다() {
        assertThat(context.getStatusManager().getCopyOfStatusList())
                .noneMatch(status -> status.getLevel() == Status.ERROR);
    }

    @Test
    void EXCEPTION_FILE이_root에_ThrowableFilter와_함께_붙는다() {
        Appender<ILoggingEvent> exceptionFile =
                context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("EXCEPTION_FILE");

        assertThat(exceptionFile).isInstanceOf(RollingFileAppender.class);
        assertThat(exceptionFile.getCopyOfAttachedFiltersList()).anyMatch(filter -> filter instanceof ThrowableFilter);
        assertThat(((RollingFileAppender<?>) exceptionFile).getFile()).isEqualTo("logs/exception.log");
    }

    @Test
    void ESCALATION_로거가_더_이상_없다() {
        assertThat(context.exists("ESCALATION")).isNull();
    }

    @Test
    void application_log_롤링_정책이_표대로_설정된다() {
        SizeAndTimeBasedRollingPolicy<?> policy = rollingPolicyOf("APP_FILE");

        assertThat(policy.getFileNamePattern()).contains("%i");
        assertThat(policy.getMaxHistory()).isEqualTo(7);
        assertThat(maxFileSizeOf(policy).getSize())
                .isEqualTo(FileSize.valueOf("100MB").getSize());
        assertThat(totalSizeCapOf(policy).getSize())
                .isEqualTo(FileSize.valueOf("1GB").getSize());
    }

    @Test
    void exception_log_롤링_정책이_표대로_설정된다() {
        SizeAndTimeBasedRollingPolicy<?> policy = rollingPolicyOf("EXCEPTION_FILE");

        assertThat(policy.getFileNamePattern()).contains("%i");
        assertThat(policy.getMaxHistory()).isEqualTo(30);
        assertThat(maxFileSizeOf(policy).getSize())
                .isEqualTo(FileSize.valueOf("50MB").getSize());
        assertThat(totalSizeCapOf(policy).getSize())
                .isEqualTo(FileSize.valueOf("500MB").getSize());
    }

    private SizeAndTimeBasedRollingPolicy<?> rollingPolicyOf(String appenderName) {
        RollingFileAppender<?> appender = (RollingFileAppender<?>)
                context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender(appenderName);
        return (SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy();
    }

    // maxFileSize/totalSizeCap은 getter가 없다(logback이 설정 전용으로만 노출) — 리플렉션으로 읽는다.
    private FileSize maxFileSizeOf(SizeAndTimeBasedRollingPolicy<?> policy) {
        return (FileSize) ReflectionTestUtils.getField(policy, "maxFileSize");
    }

    private FileSize totalSizeCapOf(SizeAndTimeBasedRollingPolicy<?> policy) {
        return (FileSize) ReflectionTestUtils.getField(policy, "totalSizeCap");
    }
}
