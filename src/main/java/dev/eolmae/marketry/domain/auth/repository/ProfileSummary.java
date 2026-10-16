package dev.eolmae.marketry.domain.auth.repository;

import java.time.LocalDateTime;

/** 세션 응답용 프로젝션 — 사진 바이트(image)는 읽지 않는다. */
public interface ProfileSummary {

    String getNickname();

    LocalDateTime getImageUpdatedAt();

    LocalDateTime getNicknameChangedAt();
}
