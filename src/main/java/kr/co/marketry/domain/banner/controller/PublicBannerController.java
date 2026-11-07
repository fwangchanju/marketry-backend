package kr.co.marketry.domain.banner.controller;

import java.time.Duration;
import kr.co.marketry.domain.banner.dto.ActiveBannerResponse;
import kr.co.marketry.domain.banner.service.BannerCampaignService;
import kr.co.marketry.domain.banner.service.BannerCampaignService.ImagePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class PublicBannerController {

    private final BannerCampaignService service;

    @GetMapping("/active")
    public ResponseEntity<ActiveBannerResponse> active() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.active());
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        ImagePayload image = service.publicImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
