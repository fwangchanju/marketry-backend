package kr.co.marketry.domain.auth.dto;

import java.util.List;

/** 검색 로봇 집계. 이름별 합과 날짜별 합을 함께 준다. 사람 방문 집계와 섞이지 않는다. */
public record AdminBotVisitResponse(List<AdminBotNameCountResponse> byName, List<AdminBotDayCountResponse> byDay) {}
