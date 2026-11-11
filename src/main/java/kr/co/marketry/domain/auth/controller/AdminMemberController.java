package kr.co.marketry.domain.auth.controller;

import java.util.List;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.auth.dto.AdminAccessFrequencyResponse;
import kr.co.marketry.domain.auth.dto.AdminBotVisitResponse;
import kr.co.marketry.domain.auth.dto.AdminDailyAccessResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberNicknameResetRequest;
import kr.co.marketry.domain.auth.dto.AdminMemberNicknameResetResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberPageResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberSpecialRequest;
import kr.co.marketry.domain.auth.dto.AdminMemberSpecialResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import kr.co.marketry.domain.auth.dto.AdminSignupDailyResponse;
import kr.co.marketry.domain.auth.dto.AdminVisitorDailyResponse;
import kr.co.marketry.domain.auth.dto.AdminVisitorSourceResponse;
import kr.co.marketry.domain.auth.service.AdminMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {
    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<AdminMemberPageResponse> list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String issuer,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.findMembers(query, role, issuer, sort, order, page));
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminMemberSummaryResponse> summary() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.getSummary(KstClock.now()));
    }

    // 최근 N일(기본 14일, 오늘 포함)의 일별 접속 회원 수.
    @GetMapping("/access-daily")
    public ResponseEntity<List<AdminDailyAccessResponse>> accessDaily(@RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.getDailyAccess(KstClock.now().toLocalDate(), days));
    }

    // 최근 N일(기본 30일, 오늘 포함)의 일별 신규 가입 수와 누적 회원 수.
    @GetMapping("/signups")
    public ResponseEntity<List<AdminSignupDailyResponse>> signups(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.getSignups(KstClock.now().toLocalDate(), days));
    }

    // 최근 N일(기본 30일, 오늘 포함)의 일별 비회원 방문자 수와 가입 전환율.
    @GetMapping("/visitors")
    public ResponseEntity<List<AdminVisitorDailyResponse>> visitors(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.getVisitors(KstClock.now().toLocalDate(), days));
    }

    // 최근 N일(기본 30일, 오늘 포함)의 검색 로봇 방문. 사람 방문과 따로 센다.
    @GetMapping("/bot-visits")
    public ResponseEntity<AdminBotVisitResponse> botVisits(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.getBotVisits(KstClock.now().toLocalDate(), days));
    }

    // 최근 N일(기본 30일, 오늘 포함)의 비회원 방문을 유입 경로별로 센다.
    @GetMapping("/visitor-sources")
    public ResponseEntity<List<AdminVisitorSourceResponse>> visitorSources(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.getVisitorSources(KstClock.now().toLocalDate(), days));
    }

    // 최근 30일 중 접속한 날 수 구간별 회원 수.
    @GetMapping("/access-frequency")
    public ResponseEntity<List<AdminAccessFrequencyResponse>> accessFrequency() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.getAccessFrequency(KstClock.now().toLocalDate()));
    }

    @PatchMapping("/special")
    public ResponseEntity<AdminMemberSpecialResponse> changeSpecial(@RequestBody AdminMemberSpecialRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.changeSpecial(request.userIds(), request.special()));
    }

    @PatchMapping("/nickname-reset")
    public ResponseEntity<AdminMemberNicknameResetResponse> resetNicknames(
            @RequestBody AdminMemberNicknameResetRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.resetNicknames(request.userIds()));
    }
}
