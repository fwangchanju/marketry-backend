package dev.eolmae.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.eolmae.marketry.domain.auth.enums.VisitorSource;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.repository.VisitorDailyRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class VisitorServiceTest {

    private static final String BROWSER = "Mozilla/5.0 (Macintosh) AppleWebKit/605.1.15 Safari/605.1.15";

    private final VisitorDailyRepository repository = mock(VisitorDailyRepository.class);
    private final AuthProperties properties = new AuthProperties();
    private final VisitorService service;

    VisitorServiceTest() {
        properties.setJwtSecret("test-secret");
        service = new VisitorService(properties, repository);
    }

    private static MockHttpServletRequest request(String ip, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", ip);
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        return request;
    }

    @Test
    void 같은_방문자는_하루에_한_번만_기록한다() {
        service.record(request("1.2.3.4", BROWSER));
        service.record(request("1.2.3.4", BROWSER));

        verify(repository, times(1)).recordVisit(any(), anyString(), any());
    }

    @Test
    void 처음_들어온_곳과_꼬리표로_출처를_정해_기록한다() {
        MockHttpServletRequest fromNaver = request("1.2.3.4", BROWSER);
        fromNaver.addHeader("X-Visit-Referrer", "https://search.naver.com/search.naver?query=marketry");
        MockHttpServletRequest tagged = request("5.6.7.8", BROWSER);
        tagged.addHeader("X-Visit-Source", "Google");
        MockHttpServletRequest direct = request("9.9.9.9", BROWSER);

        service.record(fromNaver);
        service.record(tagged);
        service.record(direct);

        verify(repository).recordVisit(any(), anyString(), eq(VisitorSource.NAVER));
        verify(repository).recordVisit(any(), anyString(), eq(VisitorSource.GOOGLE));
        verify(repository).recordVisit(any(), anyString(), eq(VisitorSource.DIRECT));
    }

    @Test
    void IP나_브라우저가_다르면_다른_방문자다() {
        service.record(request("1.2.3.4", BROWSER));
        service.record(request("5.6.7.8", BROWSER));
        service.record(request("1.2.3.4", BROWSER + " Edg/1"));

        verify(repository, times(3)).recordVisit(any(), anyString(), any());
    }

    @Test
    void 로봇과_브라우저_정보가_없는_요청은_세지_않는다() {
        service.record(request("1.2.3.4", "Googlebot/2.1"));
        service.record(request("1.2.3.4", "curl/8.0"));
        service.record(request("1.2.3.4", null));

        verify(repository, never()).recordVisit(any(), anyString(), any());
    }

    @Test
    void 저장하는_값은_IP를_담지_않은_64자_해시이고_날짜마다_달라진다() {
        String today = service.visitorKey(LocalDate.of(2026, 10, 21), "1.2.3.4", BROWSER);
        String tomorrow = service.visitorKey(LocalDate.of(2026, 10, 22), "1.2.3.4", BROWSER);

        assertThat(today).hasSize(64).doesNotContain("1.2.3.4").isNotEqualTo(tomorrow);
    }

    @Test
    void 기록에_실패해도_예외를_던지지_않고_다음에_다시_시도한다() {
        doThrow(new IllegalStateException("db down"))
                .doNothing()
                .when(repository)
                .recordVisit(any(), anyString(), any());

        service.record(request("1.2.3.4", BROWSER));
        service.record(request("1.2.3.4", BROWSER));

        verify(repository, times(2)).recordVisit(any(), anyString(), any());
    }
}
