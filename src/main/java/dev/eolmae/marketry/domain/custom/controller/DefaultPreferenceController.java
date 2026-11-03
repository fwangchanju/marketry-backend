package dev.eolmae.marketry.domain.custom.controller;

import dev.eolmae.marketry.domain.custom.service.DefaultPreferenceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/default-preferences")
@RequiredArgsConstructor
public class DefaultPreferenceController {
    private final DefaultPreferenceService service;

    @GetMapping
    public ResponseEntity<Map<String, Object>> find() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.find());
    }
}
