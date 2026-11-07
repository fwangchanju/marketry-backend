package kr.co.marketry.domain.custom.controller;

import java.util.List;
import kr.co.marketry.domain.auth.service.CurrentUser;
import kr.co.marketry.domain.custom.dto.CustomValueTierItem;
import kr.co.marketry.domain.custom.service.CustomValueTierThresholdService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom/value-tiers")
@RequiredArgsConstructor
public class CustomValueTierController {

    private final CustomValueTierThresholdService customValueTierThresholdService;

    @GetMapping
    public List<CustomValueTierItem> getValueTiers() {
        return customValueTierThresholdService.getValueTiers(CurrentUser.requireId());
    }
}
