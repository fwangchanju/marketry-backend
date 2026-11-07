package kr.co.marketry.domain.custom.dto;

public record CustomValueTierItem(Long id, String label, Long thresholdValue, boolean isExcludedByDefault) {}
