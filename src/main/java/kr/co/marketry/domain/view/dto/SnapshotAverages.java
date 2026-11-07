package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;

public record SnapshotAverages(BigDecimal weightedAvgChangeRate, BigDecimal simpleAvgChangeRate) {}
