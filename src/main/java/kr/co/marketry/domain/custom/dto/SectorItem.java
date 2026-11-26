package kr.co.marketry.domain.custom.dto;

/** excluded는 업종 제외 여부다. 한국 화면은 지도 응답의 값을 쓰고, 일본·미국 내 기준 화면은 이 값을 쓴다. */
public record SectorItem(Long id, String name, Long parentId, int depth, boolean excluded) {}
