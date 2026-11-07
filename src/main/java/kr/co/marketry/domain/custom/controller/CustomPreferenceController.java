package kr.co.marketry.domain.custom.controller;

import java.util.Map;
import kr.co.marketry.domain.custom.service.CustomPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom/preferences")
@RequiredArgsConstructor
public class CustomPreferenceController {

    private final CustomPreferenceService customPreferenceService;

    @GetMapping
    public Map<String, Object> getPreferences() {
        return customPreferenceService.getPreferences();
    }

    @PutMapping
    public void replacePreferences(@RequestBody Map<String, Object> payload) {
        customPreferenceService.replacePreferences(payload);
    }
}
