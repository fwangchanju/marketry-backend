package dev.eolmae.marketry.domain.auth.dto;

public record AdminMemberSummaryResponse(long total, long admins, long users, long joinedRecently) {}
