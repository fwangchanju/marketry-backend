package dev.eolmae.marketry.domain.auth.dto;

public record AdminPageMetricResponse(String page, long pageLoads, long averageLoadMs, long clientErrors) {}
