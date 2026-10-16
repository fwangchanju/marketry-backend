package dev.eolmae.marketry.domain.auth.controller;

import dev.eolmae.marketry.domain.auth.dto.NicknameAvailabilityResponse;
import dev.eolmae.marketry.domain.auth.dto.NicknameRequest;
import dev.eolmae.marketry.domain.auth.dto.ProfileResponse;
import dev.eolmae.marketry.domain.auth.service.CurrentUser;
import dev.eolmae.marketry.domain.auth.service.UserProfileService;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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

    @GetMapping
    public ProfileResponse getProfile() {
        return userProfileService.getProfile(CurrentUser.requireId());
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
