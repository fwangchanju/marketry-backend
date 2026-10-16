package dev.eolmae.marketry.domain.auth.dto;

/** available은 이 닉네임으로 지금 등록할 수 있는지(다른 사용자가 쓰지 않는지)를 뜻한다. */
public record NicknameAvailabilityResponse(boolean available) {}
