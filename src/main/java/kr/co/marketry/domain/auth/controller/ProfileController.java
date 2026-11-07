package kr.co.marketry.domain.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import kr.co.marketry.domain.auth.dto.NicknameAvailabilityResponse;
import kr.co.marketry.domain.auth.dto.NicknameRequest;
import kr.co.marketry.domain.auth.dto.ProfileResponse;
import kr.co.marketry.domain.auth.service.AccountWithdrawalService;
import kr.co.marketry.domain.auth.service.CurrentUser;
import kr.co.marketry.domain.auth.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final Duration IMAGE_CACHE_DURATION = Duration.ofDays(1);

    private final UserProfileService userProfileService;
    private final AccountWithdrawalService accountWithdrawalService;
    private final AuthCookies authCookies;

    @GetMapping
    public ProfileResponse getProfile() {
        return userProfileService.getProfile(CurrentUser.requireId());
    }

    // 회원 탈퇴 — 회원의 데이터를 즉시 모두 지우고 로그인 쿠키를 만료시킨다.
    @DeleteMapping("/account")
    public void withdraw(HttpServletResponse response) {
        accountWithdrawalService.withdraw(CurrentUser.requireId());
        authCookies.expireSessionCookies(response);
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    @GetMapping("/nickname/availability")
    public NicknameAvailabilityResponse checkNicknameAvailability(@RequestParam String nickname) {
        return userProfileService.checkNicknameAvailability(CurrentUser.requireId(), nickname);
    }

    @PutMapping("/nickname")
    public ProfileResponse updateNickname(@RequestBody NicknameRequest request) {
        return userProfileService.updateNickname(CurrentUser.requireId(), request.nickname());
    }

    @PutMapping("/image")
    public ProfileResponse updateImage(@RequestPart("file") MultipartFile file) throws IOException {
        Long userId = CurrentUser.requireId();
        return userProfileService.updateImage(userId, file.getBytes());
    }

    @DeleteMapping("/image")
    public ResponseEntity<Void> deleteImage() {
        userProfileService.deleteImage(CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> getImage() {
        byte[] image = userProfileService.getImage(CurrentUser.requireId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(IMAGE_CACHE_DURATION).cachePrivate())
                .body(image);
    }
}
