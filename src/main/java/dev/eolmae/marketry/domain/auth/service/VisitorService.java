package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.auth.enums.VisitorSource;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.repository.VisitorDailyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비회원 방문자 집계. 로그인하지 않고 들어온 사람을 하루에 한 번만 센다.
 * 방문자는 그날의 접속 IP와 브라우저 정보로 구분하되 그 값을 저장하지 않는다 — 날짜와 함께 서버 비밀값으로 해시한 값만 남기므로
 * 되돌릴 수 없고 다른 날의 방문과 이어지지 않는다. 쿠키는 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class VisitorService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_PURPOSE = "visitor:";
    // 화면이 세션을 물을 때 처음 들어온 곳(document.referrer)과 주소의 utm_source를 실어 보낸다. 서버가 받는 Referer는 우리 사이트 자신이라 쓸 수 없다.
    private static final String REFERRER_HEADER = "X-Visit-Referrer";
    private static final String UTM_SOURCE_HEADER = "X-Visit-Source";
    private static final Pattern BOT_PATTERN = Pattern.compile(
            "bot|crawl|spider|slurp|preview|monitor|uptime|curl|wget|python|java/|headless|facebookexternalhit",
            Pattern.CASE_INSENSITIVE);
    // 오늘 이미 기록한 방문자 — 같은 사람이 화면을 열 때마다 DB를 건드리지 않게 한다. 날짜가 바뀌면 비운다.
    private final Set<String> recordedToday = ConcurrentHashMap.newKeySet();
    private volatile LocalDate recordedDate;

    private final AuthProperties authProperties;
    private final VisitorDailyRepository visitorDailyRepository;

    /** 비회원 요청이면 오늘의 방문자로 기록한다. 로봇으로 보이는 요청은 센 수에서 뺀다. 기록에 실패해도 요청은 막지 않는다. */
    public void record(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null
                || userAgent.isBlank()
                || BOT_PATTERN.matcher(userAgent).find()) {
            return;
        }
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        String key = visitorKey(today, clientIp(request), userAgent);
        resetIfNewDay(today);
        if (!recordedToday.add(key)) {
            return;
        }
        try {
            visitorDailyRepository.recordVisit(
                    today,
                    key,
                    VisitorSource.from(request.getHeader(REFERRER_HEADER), request.getHeader(UTM_SOURCE_HEADER)));
        } catch (RuntimeException e) {
            recordedToday.remove(key);
        }
    }

    /** 보관 기간이 지난 방문 기록을 지운다. */
    @Transactional
    public int purgeOlderThan(int retentionDays) {
        return visitorDailyRepository.deleteVisitedBefore(
                LocalDate.now(Zone.KST.zoneId()).minusDays(retentionDays));
    }

    private void resetIfNewDay(LocalDate today) {
        if (!today.equals(recordedDate)) {
            synchronized (this) {
                if (!today.equals(recordedDate)) {
                    recordedToday.clear();
                    recordedDate = today;
                }
            }
        }
    }

    // nginx가 Cloudflare가 알려 준 실제 방문자 IP를 X-Real-IP로 넘긴다. 없으면(로컬 등) 연결 주소를 쓴다.
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Real-IP");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.strip();
    }

    String visitorKey(LocalDate date, String ip, String userAgent) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    (KEY_PURPOSE + authProperties.getJwtSecret()).getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String source = date + "|" + ip + "|" + userAgent.toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
