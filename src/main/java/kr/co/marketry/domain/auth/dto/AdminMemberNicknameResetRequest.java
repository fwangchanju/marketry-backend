package kr.co.marketry.domain.auth.dto;

import java.util.List;

public record AdminMemberNicknameResetRequest(List<Long> userIds) {}
