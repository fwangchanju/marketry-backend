package dev.eolmae.marketry.domain.auth.dto;

import dev.eolmae.marketry.domain.auth.enums.Role;
import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long id, String email, String nickname, Role role, LocalDateTime createdAt, LocalDateTime lastAccessAt) {}
