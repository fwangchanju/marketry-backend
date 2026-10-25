package dev.eolmae.marketry.domain.banner.controller;

import dev.eolmae.marketry.domain.banner.dto.BannerCampaignResponse;
import dev.eolmae.marketry.domain.banner.dto.BannerRotationSettingRequest;
import dev.eolmae.marketry.domain.banner.dto.BannerRotationSettingResponse;
import dev.eolmae.marketry.domain.banner.service.BannerCampaignService;
import dev.eolmae.marketry.domain.banner.service.BannerCampaignService.ImagePayload;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerCampaignService service;

    @GetMapping
    public ResponseEntity<List<BannerCampaignResponse>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());
    }

    @GetMapping("/rotation-setting")
    public ResponseEntity<BannerRotationSettingResponse> rotationSetting() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.rotationSetting());
    }

    @PutMapping("/rotation-setting")
    public ResponseEntity<BannerRotationSettingResponse> updateRotationSetting(
            @RequestBody BannerRotationSettingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.updateRotationSetting(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BannerCampaignResponse> create(
            @RequestParam String type,
            @RequestParam String title,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String linkUrl,
            @RequestParam String startsAt,
            @RequestParam(required = false) String endsAt,
            @RequestParam(defaultValue = "100") int textScalePercent,
            @RequestParam(defaultValue = "false") boolean textBold,
            @RequestParam(required = false) MultipartFile file)
            throws IOException {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.create(
                        type,
                        title,
                        text,
                        linkUrl,
                        startsAt,
                        endsAt,
                        textScalePercent,
                        textBold,
                        file == null ? null : file.getBytes()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BannerCampaignResponse> update(
            @PathVariable long id,
            @RequestParam String type,
            @RequestParam String title,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String linkUrl,
            @RequestParam String startsAt,
            @RequestParam(required = false) String endsAt,
            @RequestParam(defaultValue = "100") int textScalePercent,
            @RequestParam(defaultValue = "false") boolean textBold,
            @RequestParam(required = false) MultipartFile file)
            throws IOException {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.update(
                        id,
                        type,
                        title,
                        text,
                        linkUrl,
                        startsAt,
                        endsAt,
                        textScalePercent,
                        textBold,
                        file == null ? null : file.getBytes()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<BannerCampaignResponse> approve(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.approve(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> adminImage(@PathVariable long id) {
        return imageResponse(service.adminImage(id));
    }

    private ResponseEntity<byte[]> imageResponse(ImagePayload image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
