package dev.eolmae.marketry.domain.auth.dto;

public record AdminMemberSummaryResponse(
        long total, long admins, long specials, long users, long joinedRecently, long accessedRecently) {}
