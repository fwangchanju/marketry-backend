package kr.co.marketry.domain.auth.dto;

import java.util.List;

public record AdminMemberSpecialRequest(List<Long> userIds, boolean special) {}
