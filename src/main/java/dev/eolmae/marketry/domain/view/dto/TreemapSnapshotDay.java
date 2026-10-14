package dev.eolmae.marketry.domain.view.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 지도를 볼 수 있는 날짜 하나 — snapshotTime은 그날 종가 스냅샷 시각(`GET /api/map`의 snapshotTime에 그대로 쓴다). */
public record TreemapSnapshotDay(LocalDate date, LocalDateTime snapshotTime) {}
