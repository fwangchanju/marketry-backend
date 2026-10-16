package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ConflictException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.auth.dto.NicknameAvailabilityResponse;
import dev.eolmae.marketry.domain.auth.dto.ProfileResponse;
import dev.eolmae.marketry.domain.auth.entity.UserProfile;
import dev.eolmae.marketry.domain.auth.repository.ProfileSummary;
import dev.eolmae.marketry.domain.auth.repository.UserProfileRepository;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    static final int MAX_UPLOAD_BYTES = 512 * 1024;
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9]{2,12}$");
    private static final String NICKNAME_UNIQUE_INDEX = "uk_user_profile_nickname_lower";
    private static final int SAVE_ATTEMPTS = 2;
    // 닉네임을 한 번 바꾼 뒤 다시 바꿀 수 있게 되기까지의 간격. 닉네임이 없던 계정의 첫 등록에는 적용하지 않는다.
    static final Duration NICKNAME_CHANGE_INTERVAL = Duration.ofDays(7);

    private final UserProfileRepository userProfileRepository;
    private final ProfileImageProcessor profileImageProcessor;
    private final TransactionTemplate transactionTemplate;

    public ProfileResponse getProfile(Long userId) {
        return userProfileRepository
                .findByUserId(userId, ProfileSummary.class)
                .map(UserProfileService::toResponse)
                .orElseGet(ProfileResponse::empty);
    }

    public ProfileResponse updateNickname(Long userId, String requestedNickname) {
        String nickname = normalizeNickname(requestedNickname);
        ProfileSummary current =
                userProfileRepository.findByUserId(userId, ProfileSummary.class).orElse(null);
        if (current != null && nickname.equals(current.getNickname())) {
            return toResponse(current);
        }
        assertNicknameChangeable(userId, current);
        if (userProfileRepository.existsByNicknameIgnoreCaseAndUserIdNot(nickname, userId)) {
            throw new ConflictException(ErrorCode.PROFILE_NICKNAME_DUPLICATE, nickname);
        }
        save(userId, profile -> profile.changeNickname(nickname));
        return getProfile(userId);
    }

    /** 이 닉네임으로 등록할 수 있는지 미리 확인한다 — 내 닉네임과 같거나 아무도 쓰지 않으면 true. 저장은 하지 않는다. */
    public NicknameAvailabilityResponse checkNicknameAvailability(Long userId, String requestedNickname) {
        String nickname = normalizeNickname(requestedNickname);
        ProfileSummary current =
                userProfileRepository.findByUserId(userId, ProfileSummary.class).orElse(null);
        if (current != null && nickname.equals(current.getNickname())) {
            return new NicknameAvailabilityResponse(true);
        }
        assertNicknameChangeable(userId, current);
        return new NicknameAvailabilityResponse(
                !userProfileRepository.existsByNicknameIgnoreCaseAndUserIdNot(nickname, userId));
    }

    public ProfileResponse updateImage(Long userId, byte[] source) {
        if (source.length > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진은 512KB 이하만 올릴 수 있습니다.");
        }
        byte[] image = profileImageProcessor.toProfileJpeg(source);
        save(userId, profile -> profile.changeImage(image));
        return getProfile(userId);
    }

    public void deleteImage(Long userId) {
        if (userProfileRepository.existsById(userId)) {
            save(userId, UserProfile::removeImage);
        }
    }

    public byte[] getImage(Long userId) {
        return userProfileRepository
                .findById(userId)
                .map(UserProfile::getImage)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROFILE_IMAGE_NOT_FOUND, userId));
    }

    private String normalizeNickname(String requestedNickname) {
        if (requestedNickname == null) {
            throw new BadRequestException(ErrorCode.PROFILE_NICKNAME_INVALID);
        }
        String nickname =
                Normalizer.normalize(requestedNickname, Normalizer.Form.NFC).strip();
        if (NICKNAME_PATTERN.matcher(nickname).matches()) {
            return nickname;
        }
        throw new BadRequestException(ErrorCode.PROFILE_NICKNAME_INVALID, requestedNickname);
    }

    // 닉네임 유니크 인덱스 위반만 409로 바꾼다. 같은 사용자의 첫 저장이 동시에 겹쳐 기본키(user_id)가 충돌하면
    // 한 번만 다시 읽고 저장한다 — 그래도 실패하면 그대로 던진다.
    private void save(Long userId, Consumer<UserProfile> change) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    UserProfile profile =
                            userProfileRepository.findById(userId).orElseGet(() -> new UserProfile(userId));
                    change.accept(profile);
                    userProfileRepository.saveAndFlush(profile);
                });
                return;
            } catch (DataIntegrityViolationException e) {
                if (isNicknameConflict(e)) {
                    throw new ConflictException(ErrorCode.PROFILE_NICKNAME_DUPLICATE, e);
                }
                if (attempt == SAVE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private boolean isNicknameConflict(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains(NICKNAME_UNIQUE_INDEX)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static ProfileResponse toResponse(ProfileSummary summary) {
        Long nicknameChangeableAt = toEpochMillis(nicknameChangeableAt(summary));
        LocalDateTime imageUpdatedAt = summary.getImageUpdatedAt();
        if (imageUpdatedAt == null) {
            return new ProfileResponse(summary.getNickname(), false, null, nicknameChangeableAt);
        }
        return new ProfileResponse(summary.getNickname(), true, toEpochMillis(imageUpdatedAt), nicknameChangeableAt);
    }

    // 최근에 바꿔서 아직 바꿀 수 없으면 409를 던진다(닉네임을 처음 등록하는 계정은 늘 통과한다).
    private void assertNicknameChangeable(Long userId, ProfileSummary current) {
        if (current == null) {
            return;
        }
        LocalDateTime changeableAt = nicknameChangeableAt(current);
        if (changeableAt != null) {
            throw new ConflictException(ErrorCode.PROFILE_NICKNAME_CHANGE_LIMITED, userId, changeableAt);
        }
    }

    // 닉네임이 있고 최근에 바꿨다면 다시 바꿀 수 있게 되는 시각, 지금 바꿀 수 있으면 null.
    private static LocalDateTime nicknameChangeableAt(ProfileSummary summary) {
        LocalDateTime changedAt = summary.getNicknameChangedAt();
        if (summary.getNickname() == null || changedAt == null) {
            return null;
        }
        LocalDateTime changeableAt = changedAt.plus(NICKNAME_CHANGE_INTERVAL);
        return changeableAt.isAfter(LocalDateTime.now(Zone.KST.zoneId())) ? changeableAt : null;
    }

    private static Long toEpochMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(Zone.KST.zoneId()).toInstant().toEpochMilli();
    }
}
