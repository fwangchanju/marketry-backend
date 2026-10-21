package dev.eolmae.marketry.domain.auth.controller;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.service.AdminMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.findMembers(query, role, sort, page));
    }
}
