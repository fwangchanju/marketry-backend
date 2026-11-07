package kr.co.marketry.domain.stock.client;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.dto.NextradeStockListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * NEXTRADE 홈페이지의 "매매체결대상종목" 조회가 쓰는 공개 주소에서 NXT 거래 가능 종목 코드를 받는다.
 *
 * <p>공식 데이터 제공 API가 아니라 사이트 화면이 쓰는 주소라서, 주소나 응답 모양이 바뀌면 깨질 수 있다. 그래서 키움
 * {@code nxtEnable}이 오지 않을 때만 쓰는 보조 출처이고(StockInfoCollector), 호출은 종목 정보 동기화 때 하루 한 번이다.
 * 응답이 비정상으로 보이면 예외를 던져 호출부가 기존 값을 유지하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NextradeStockListClient {

    private static final String LIST_URL = "https://www.nextrade.co.kr/trdisuChg/trdisuChgList.do";
    private static final String REFERER = "https://www.nextrade.co.kr/menu/transactionStatusConclusion/menuList.do";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    // NXT 출범(2025-03-04) 이전부터 조회해서 모든 편입·편출을 받는다.
    private static final String BEGIN_DATE = "20250301";
    private static final String INCLUSION = "편입";
    // 한 번에 모두 받기 위한 넉넉한 페이지 크기(전체 약 1,000건).
    private static final String PAGE_SIZE = "5000";
    // 이보다 적으면 응답이 잘렸거나 형식이 바뀐 것으로 본다(실제로는 약 600개).
    static final int MIN_PLAUSIBLE_COUNT = 300;

    private final RestClient restClient;

    /** NXT에 편입돼 있는 주권의 6자리 종목코드 집합. 응답이 비정상이면 예외를 던진다. */
    public Set<String> fetchTradableStockCodes() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("scSecuGroup", "STOCK");
        form.add("scBeginDe", BEGIN_DATE);
        form.add("scEndDe", LocalDate.now(Zone.KST.zoneId()).format(DATE_FORMAT));
        form.add("pageIndex", "1");
        form.add("pageUnit", PAGE_SIZE);

        NextradeStockListResponse response = restClient
                .post()
                .uri(LIST_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Referer", REFERER)
                .header("X-Requested-With", "XMLHttpRequest")
                .body(form)
                .retrieve()
                .body(NextradeStockListResponse.class);

        if (response == null || response.list() == null) {
            throw new IllegalStateException("NEXTRADE 응답에 종목 목록이 없습니다.");
        }
        Set<String> codes = activeStockCodes(response.list());
        if (codes.size() < MIN_PLAUSIBLE_COUNT) {
            throw new IllegalStateException("NEXTRADE 편입 종목 수가 비정상입니다: " + codes.size());
        }
        log.info(
                "NEXTRADE 편입 종목 조회 완료: 편입={}개 (전체 이력 {}건)",
                codes.size(),
                response.list().size());
        return codes;
    }

    /** 종목별로 가장 마지막 상태를 남기고, 그중 "편입"인 종목의 6자리 코드를 모은다. */
    static Set<String> activeStockCodes(List<NextradeStockListResponse.Item> items) {
        Map<String, NextradeStockListResponse.Item> latestByCode = new HashMap<>();
        items.stream()
                .filter(item -> item.shortCode() != null && item.status() != null)
                .sorted(Comparator.comparing(item -> item.date() == null ? "" : item.date()))
                .forEach(item -> latestByCode.put(toSixDigitCode(item.shortCode()), item));
        return latestByCode.entrySet().stream()
                .filter(entry -> INCLUSION.equals(entry.getValue().status()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static String toSixDigitCode(String shortCode) {
        String code = shortCode.trim();
        return code.length() == 7 && code.startsWith("A") ? code.substring(1) : code;
    }
}
