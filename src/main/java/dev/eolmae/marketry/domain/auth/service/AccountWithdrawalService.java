package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import dev.eolmae.marketry.domain.auth.repository.UserProfileRepository;
import dev.eolmae.marketry.domain.auth.repository.UserRefreshTokenRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomScaleThresholdRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomSnapshotRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketry.domain.custom.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 회원 탈퇴 — 회원의 이메일·프로필·내 분류·설정·로그인 기록을 한 번에 즉시 지운다(보관 기간 없음).
 * 관리자와 시스템 회원은 탈퇴할 수 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountWithdrawalService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomStockAliasRepository customStockAliasRepository;
    private final CustomSectorRepository customSectorRepository;
    private final CustomSnapshotRepository customSnapshotRepository;
    private final CustomScaleThresholdRepository customScaleThresholdRepository;
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository;

    @Transactional
    public void withdraw(Long userId) {
        UserAccount user = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        if (user.getRole() == Role.ADMIN || UserAccount.SYSTEM_ISSUER.equals(user.getIssuer())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "탈퇴할 수 없는 계정입니다.");
        }
        // 종목 배정 → 업종 → 스냅샷 순서다 — 배정이 업종을, 업종이 스냅샷을 가리키므로 가리키는 쪽을 먼저 지워야 FK를 어기지 않는다.
        customStockSectorRepository.deleteAllByUserId(userId);
        customStockAliasRepository.deleteAllOfUser(userId);
        customSectorRepository.deleteAllOfUser(userId);
        customSnapshotRepository.deleteAllOfUser(userId);
        customScaleThresholdRepository.deleteAllOfUser(userId);
        customValueTierThresholdRepository.deleteAllByUserId(userId);
        userPreferenceRepository.deleteAllOfUser(userId);
        userRefreshTokenRepository.deleteAllOfUser(userId);
        userProfileRepository.deleteAllOfUser(userId);
        userAccountRepository.deleteById(userId);
        log.info("회원 탈퇴 처리: userId={}", userId);
    }
}
