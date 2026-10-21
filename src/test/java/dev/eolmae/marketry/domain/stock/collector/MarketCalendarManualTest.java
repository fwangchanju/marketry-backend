package dev.eolmae.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.client.TossMarketCalendarClient;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.TossProperties;
import dev.eolmae.marketry.domain.stock.repository.MarketCalendarRepository;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * 월 또는 날짜 범위의 실제 토스 시간표를 운영 수집기로 요청하고 로컬 market_calendar에 적재한다.
 * 일반 test와 CI에서 제외된다. 애플리케이션을 따로 실행할 필요가 없다.
 *
 * <p>실행 조건: 127.0.0.1:15432/marketry_db에 현재 앱 전체 스키마를 준비하고 POSTGRES_PASSWORD 및
 * TOSS_CLIENT_ID / TOSS_CLIENT_SECRET을 프로세스 환경에 주입한다. 토스 허용 IP 등록이 필요하다.
 * 보호 env 파일은 읽지 않는다. local 프로파일에서도 실제 토스 API를 호출한다.
 * DB URL은 테스트에서 로컬로 고정한다. Flyway와 DDL 생성은 꺼져 있으며 실제 데이터는 롤백하지 않는다.
 * DB 또는 스키마가 준비되지 않으면 API를 호출하기 전에 테스트 기동이 실패한다.
 *
 * <p>Windows PowerShell 월별 실행: .\gradlew.bat marketCalendarTest -PcalendarMonth=202601.
 * -Dcalendar.month=202601도 지원한다. 월 입력은 여섯 자리 yyyyMM이며 날짜/범위 입력과 함께 지정할 수 없다.
 * CALENDAR_DATE 환경변수도 날짜 입력에 포함된다. 미래 월은 거부하고 현재 월은 오늘까지만 요청한다.
 * 각 날짜를 한 번 요청하며 토스 응답에 포함된 이전/다음 거래일은 요청 월 밖이어도 함께 저장될 수 있다.
 * 첫 실패에서 중단하고 이미 적재한 날짜는 유지된다. 같은 월 재실행은 기존 행을 갱신하며 중복 행을 만들지 않는다.
 * 마지막 COMPLETE 줄에 요청 범위·거래일 수·휴장일 수가 출력된다.
 *
 * <p>기존 날짜별 실행: ./gradlew.bat marketCalendarTest -PcalendarDate=2026-10-09
 * 또는 ./gradlew.bat marketCalendarTest -Dcalendar.date=2026-10-09.
 * CALENDAR_DATE 환경변수로도 날짜를 지정할 수 있다. 입력하지 않으면 현재 Asia/Seoul 날짜를 사용한다.
 * 날짜 형식은 yyyy-MM-dd이다. 이 전용 태스크는 다른 수동 테스트나 텔레그램을 실행하지 않는다.
 *
 * <p>범위 조회는 calendar.date를 시작일로, calendar.end-date를 종료일로 지정한다.
 * 종료일에 today를 지정하면 실행 시점의 Asia/Seoul 날짜까지 조회한다. 양 끝 날짜를 포함하고
 * 주말·공휴일도 조회한다. 날짜별 호출 사이에 1초를 대기하고 같은 클라이언트의 토큰을 재사용한다.
 * IntelliJ의 IntelliJ IDEA 테스트 실행기에서 VM options에
 * -Dcalendar.date=2026-09-01 -Dcalendar.end-date=today를 넣어 이 테스트를 실행한다.
 * Gradle에서는 marketCalendarTest -PcalendarDate=2026-09-01 -PcalendarEndDate=today로 실행한다.
 */
@Tag("manual")
@ActiveProfiles("local")
@SpringBootTest(
        classes = MarketCalendarManualTest.ManualConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "scheduling.enabled=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/marketry_db",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.show-sql=false",
            "logging.level.org.hibernate.SQL=OFF",
            "logging.level.org.hibernate.orm.jdbc.bind=OFF",
            "logging.level.org.springframework.web.client=OFF",
            "logging.level.org.springframework.boot.context.config=OFF",
            "logging.level.dev.eolmae.marketry.domain.stock.collector.MarketCalendarCollector=WARN"
        })
class MarketCalendarManualTest {
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(1);

    @Autowired
    private MarketCalendarCollector collector;

    @Autowired
    private MarketCalendarService service;

    @Value("${calendar.month:}")
    private String requestedMonth;

    @Value("${calendar.date:${CALENDAR_DATE:}}")
    private String requestedDate;

    @Value("${calendar.end-date:}")
    private String requestedEndDate;

    @Test
    void 지정월또는날짜범위의토스시간표를실제로조회하고저장한다() throws InterruptedException {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        var options = MarketCalendarManualOptions.resolve(requestedMonth, requestedDate, requestedEndDate, today);
        List<LocalDate> dates = options.dates();
        System.out.printf(
                "START: country=KR start=%s end=%s days=%d%n", options.startDate(), options.endDate(), dates.size());

        // 휴장일도 당일 응답을 저장해야 범위 내 모든 날짜의 수집 여부를 판단할 수 있다.
        for (LocalDate date : dates) {
            if (date.isAfter(options.startDate())) {
                Thread.sleep(REQUEST_INTERVAL);
            }
            collectAndVerify(date);
        }
        var stored = service.findByCountryAndDateIn(Country.KR, dates);
        assertThat(stored).containsOnlyKeys(dates.toArray(LocalDate[]::new));
        assertThat(stored.values()).allSatisfy(calendar -> assertThat(calendar.getStatus())
                .isIn(MarketCalendarStatus.TRADING_DAY, MarketCalendarStatus.HOLIDAY));
        long tradingDays = stored.values().stream()
                .filter(calendar -> calendar.getStatus() == MarketCalendarStatus.TRADING_DAY)
                .count();
        System.out.printf(
                "COMPLETE: country=KR start=%s end=%s days=%d trading=%d holiday=%d%n",
                options.startDate(), options.endDate(), dates.size(), tradingDays, dates.size() - tradingDays);
    }

    private void collectAndVerify(LocalDate date) {
        assertThat(collector.collect(date))
                .as("%s 토스 시간표 조회와 저장 성공 (이전 날짜의 적재는 유지됩니다.)", date)
                .isTrue();
        MarketCalendar calendar = service.findByCountryAndDate(Country.KR, date)
                .orElseThrow(() -> new AssertionError("요청 날짜의 시간표 행이 없습니다."));

        assertThat(calendar.getCountry()).isEqualTo(Country.KR);
        assertThat(calendar.getDate()).isEqualTo(date);
        assertThat(calendar.getStatus()).isIn(MarketCalendarStatus.TRADING_DAY, MarketCalendarStatus.HOLIDAY);
        if (calendar.getStatus() == MarketCalendarStatus.HOLIDAY) {
            assertThat(calendar.getIntegrated()).isNull();
        } else {
            assertThat(calendar.getIntegrated()).isNotNull();
            var integrated = calendar.getIntegrated();
            var sessions = Stream.of(integrated.preMarket(), integrated.regularMarket(), integrated.afterMarket())
                    .filter(Objects::nonNull)
                    .toList();
            assertThat(sessions).as("%s 거래 세션 복원", date).isNotEmpty();
            for (var session : sessions) {
                assertThat(session.startTime()).isNotNull();
                assertThat(session.endTime()).isNotNull().isAfter(session.startTime());
                assertThat(session.startTime()
                                .atZoneSameInstant(Zone.KST.zoneId())
                                .toLocalDate())
                        .isEqualTo(date);
                assertThat(session.endTime()
                                .atZoneSameInstant(Zone.KST.zoneId())
                                .toLocalDate())
                        .isEqualTo(date);
            }
        }
        System.out.printf("PASS: country=KR date=%s status=%s%n", date, calendar.getStatus());
    }

    // API와 저장에 필요한 빈만 등록하여 앱의 runner·수집·발송 경로를 실행하지 않는다.
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("dev.eolmae.marketry")
    @EnableConfigurationProperties(TossProperties.class)
    @EnableJpaRepositories(
            basePackageClasses = MarketCalendarRepository.class,
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MarketCalendarRepository.class))
    @Import({MarketCalendarCollector.class, MarketCalendarService.class, TossMarketCalendarClient.class})
    static class ManualConfiguration {
        @Bean
        RestClient restClient() {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(10));
            return RestClient.builder().requestFactory(requestFactory).build();
        }
    }
}
