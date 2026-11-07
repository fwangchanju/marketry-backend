package kr.co.marketry.domain.custom.dto;

import java.math.BigDecimal;
import kr.co.marketry.domain.custom.enums.ColorLabel;

public record ScaleThresholdItem(Long id, BigDecimal thresholdPercent, String color, ColorLabel colorLabel) {}
