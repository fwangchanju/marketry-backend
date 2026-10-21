package dev.eolmae.marketry.domain.auth.dto;

import java.util.List;

public record AdminMemberPageResponse(List<AdminMemberResponse> items, long total, boolean accessTrackingAvailable) {}
