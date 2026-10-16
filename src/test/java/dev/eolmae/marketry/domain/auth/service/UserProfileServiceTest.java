package dev.eolmae.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ConflictException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.auth.dto.ProfileResponse;
import dev.eolmae.marketry.domain.auth.entity.UserProfile;
import dev.eolmae.marketry.domain.auth.repository.ProfileSummary;
import dev.eolmae.marketry.domain.auth.repository.UserProfileRepository;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class UserProfileServiceTest {

    private static final Long USER_ID = 42L;

    private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
    private final ProfileImageProcessor profileImageProcessor = mock(ProfileImageProcessor.class);
    private final UserProfileService userProfileService = new UserProfileService(
            userProfileRepository,
            profileImageProcessor,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    void 닉네임은_앞뒤_공백을_자르고_저장한다() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        userProfileService.updateNickname(USER_ID, "  마켓러  ");

        assertThat(savedProfile().getNickname()).isEqualTo("마켓러");
    }

    @Test
    void 전각_공백도_앞뒤에서_잘라낸다() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        userProfileService.updateNickname(USER_ID, "　마켓러　");

        assertThat(savedProfile().getNickname()).isEqualTo("마켓러");
    }

    @Test
    void NFD로_입력된_한글은_NFC로_바꿔_저장한다() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        String decomposed = Normalizer.normalize("마켓러", Normalizer.Form.NFD);

        userProfileService.updateNickname(USER_ID, decomposed);

        assertThat(savedProfile().getNickname()).isEqualTo("마켓러");
    }

    @Test
    void 규칙에_맞지_않는_닉네임은_BadRequestException을_던지고_저장하지_않는다() {
        String[] invalidNicknames = {
            null, "", "   ", "가", "열세글자를넘는닉네임입니다만", "닉 네임", "닉네임!", "😀😀😀", "ㅋㅋ", "café", "닉네임_1", "_닉네임"
        };

        for (String nickname : invalidNicknames) {
            assertThatThrownBy(() -> userProfileService.updateNickname(USER_ID, nickname))
                    .as("닉네임: %s", nickname)
                    .isInstanceOf(BadRequestException.class);
        }
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void 경계_길이_2자와_12자와_영문_숫자는_통과한다() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        String[] validNicknames = {"가나", "가나다라마바사아자차카타", "Ab12", "user01"};

        for (String nickname : validNicknames) {
            userProfileService.updateNickname(USER_ID, nickname);
        }

        verify(userProfileRepository, times(validNicknames.length)).saveAndFlush(any());
    }

    @Test
    void 다른_사용자가_이미_쓰는_닉네임이면_ConflictException을_던지고_저장하지_않는다() {
        when(userProfileRepository.existsByNicknameIgnoreCaseAndUserIdNot("마켓러", USER_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> userProfileService.updateNickname(USER_ID, "마켓러"))
                .isInstanceOf(ConflictException.class);
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void 본인의_닉네임을_같은_값으로_다시_저장하면_아무것도_바꾸지_않고_성공한다() {
        ProfileSummary current = summary("마켓러", null, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));

        ProfileResponse response = userProfileService.updateNickname(USER_ID, "마켓러");

        assertThat(response.nickname()).isEqualTo("마켓러");
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void 닉네임이_없던_계정의_첫_등록은_교체_시각을_남기지_않는다() {
        UserProfile existing = new UserProfile(USER_ID);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        userProfileService.updateNickname(USER_ID, "마켓러");

        assertThat(existing.getNickname()).isEqualTo("마켓러");
        assertThat(existing.getNicknameChangedAt()).isNull();
    }

    @Test
    void 닉네임이_있던_계정이_바꾸면_교체_시각을_남긴다() {
        UserProfile existing = new UserProfile(USER_ID);
        existing.changeNickname("마켓러");
        ProfileSummary current = summary("마켓러", null, null);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        userProfileService.updateNickname(USER_ID, "투자왕");

        assertThat(existing.getNickname()).isEqualTo("투자왕");
        assertThat(existing.getNicknameChangedAt()).isNotNull();
    }

    @Test
    void 바꾼_지_7일이_안_지났으면_ConflictException을_던지고_저장하지_않는다() {
        LocalDateTime changedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(6);
        ProfileSummary current = summary("마켓러", null, changedAt);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> userProfileService.updateNickname(USER_ID, "투자왕"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.PROFILE_NICKNAME_CHANGE_LIMITED));
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void 바꾼_지_7일이_지났으면_다시_바꿀_수_있다() {
        LocalDateTime changedAt =
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(7).minusMinutes(1);
        UserProfile existing = new UserProfile(USER_ID);
        existing.changeNickname("마켓러");
        ProfileSummary current = summary("마켓러", null, changedAt);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        userProfileService.updateNickname(USER_ID, "투자왕");

        assertThat(existing.getNickname()).isEqualTo("투자왕");
    }

    @Test
    void 바꾼_지_얼마_안_됐어도_같은_닉네임이_아니면_다른_사용자의_닉네임_조회보다_먼저_막는다() {
        LocalDateTime changedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        ProfileSummary current = summary("마켓러", null, changedAt);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> userProfileService.updateNickname(USER_ID, "투자왕"))
                .isInstanceOf(ConflictException.class);
        verify(userProfileRepository, never()).existsByNicknameIgnoreCaseAndUserIdNot(any(), anyLong());
    }

    @Test
    void 사용_가능_여부는_다른_사용자가_쓰지_않으면_true다() {
        when(userProfileRepository.existsByNicknameIgnoreCaseAndUserIdNot("마켓러", USER_ID))
                .thenReturn(false);

        assertThat(userProfileService.checkNicknameAvailability(USER_ID, "마켓러").available())
                .isTrue();
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void 사용_가능_여부는_다른_사용자가_쓰면_false다() {
        when(userProfileRepository.existsByNicknameIgnoreCaseAndUserIdNot("마켓러", USER_ID))
                .thenReturn(true);

        assertThat(userProfileService.checkNicknameAvailability(USER_ID, "마켓러").available())
                .isFalse();
    }

    @Test
    void 사용_가능_여부는_내_닉네임과_같으면_조회하지_않고_true다() {
        ProfileSummary current = summary("마켓러", null, null);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));

        assertThat(userProfileService.checkNicknameAvailability(USER_ID, "마켓러").available())
                .isTrue();
        verify(userProfileRepository, never()).existsByNicknameIgnoreCaseAndUserIdNot(any(), anyLong());
    }

    @Test
    void 사용_가능_여부도_규칙에_맞지_않으면_BadRequestException을_던진다() {
        assertThatThrownBy(() -> userProfileService.checkNicknameAvailability(USER_ID, "닉 네임"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 사용_가능_여부도_바꾼_지_7일이_안_지났으면_ConflictException을_던진다() {
        ProfileSummary current =
                summary("마켓러", null, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1));
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> userProfileService.checkNicknameAvailability(USER_ID, "투자왕"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.PROFILE_NICKNAME_CHANGE_LIMITED));
    }

    @Test
    void 프로필_응답은_바꿀_수_있게_되는_시각을_한국시간_epoch_밀리초로_돌려준다() {
        LocalDateTime changedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2);
        ProfileSummary current = summary("마켓러", null, changedAt);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(current));

        ProfileResponse response = userProfileService.getProfile(USER_ID);

        long expected = changedAt
                .plus(UserProfileService.NICKNAME_CHANGE_INTERVAL)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant()
                .toEpochMilli();
        assertThat(response.nicknameChangeableAt()).isEqualTo(expected);
    }

    @Test
    void 제한_기간이_지났거나_바꾼_적이_없으면_바꿀_수_있게_되는_시각이_null이다() {
        ProfileSummary neverChanged = summary("마켓러", null, null);
        ProfileSummary longAgo =
                summary("마켓러", null, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(30));
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(neverChanged))
                .thenReturn(Optional.of(longAgo));

        assertThat(userProfileService.getProfile(USER_ID).nicknameChangeableAt())
                .isNull();
        assertThat(userProfileService.getProfile(USER_ID).nicknameChangeableAt())
                .isNull();
    }

    @Test
    void 사전_확인을_뚫고_닉네임_유니크_인덱스가_위반되면_ConflictException으로_바꾼다() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_user_profile_nickname_lower\""));

        assertThatThrownBy(() -> userProfileService.updateNickname(USER_ID, "마켓러"))
                .isInstanceOf(ConflictException.class);
        verify(userProfileRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void 기본키_충돌은_한_번_다시_시도해서_저장한다() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates \"pk_user_profile\""))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userProfileService.updateNickname(USER_ID, "마켓러");

        verify(userProfileRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void 기본키_충돌이_두_번_연속이면_그대로_던진다() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates \"pk_user_profile\""));

        assertThatThrownBy(() -> userProfileService.updateNickname(USER_ID, "마켓러"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(userProfileRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void 사진이_512KB를_넘으면_413을_던지고_처리하지_않는다() {
        byte[] tooLarge = new byte[512 * 1024 + 1];

        assertThatThrownBy(() -> userProfileService.updateImage(USER_ID, tooLarge))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        verify(profileImageProcessor, never()).toProfileJpeg(any());
    }

    @Test
    void 사진은_처리된_JPEG를_저장한다() {
        byte[] source = new byte[] {1, 2, 3};
        byte[] processed = new byte[] {9, 9};
        when(profileImageProcessor.toProfileJpeg(source)).thenReturn(processed);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        userProfileService.updateImage(USER_ID, source);

        UserProfile saved = savedProfile();
        assertThat(saved.getImage()).isEqualTo(processed);
        assertThat(saved.getImageUpdatedAt()).isNotNull();
    }

    @Test
    void 닉네임만_바꿔도_사진과_사진_시각은_그대로다() {
        UserProfile existing = new UserProfile(USER_ID);
        existing.changeImage(new byte[] {7});
        LocalDateTime imageUpdatedAt = existing.getImageUpdatedAt();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        userProfileService.updateNickname(USER_ID, "마켓러");

        assertThat(existing.getImage()).isEqualTo(new byte[] {7});
        assertThat(existing.getImageUpdatedAt()).isEqualTo(imageUpdatedAt);
    }

    @Test
    void 사진을_지우면_사진과_사진_시각이_모두_비워진다() {
        UserProfile existing = new UserProfile(USER_ID);
        existing.changeImage(new byte[] {7});
        when(userProfileRepository.existsById(USER_ID)).thenReturn(true);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        userProfileService.deleteImage(USER_ID);

        assertThat(existing.getImage()).isNull();
        assertThat(existing.getImageUpdatedAt()).isNull();
        verify(userProfileRepository).saveAndFlush(existing);
    }

    @Test
    void 프로필이_없으면_사진_삭제는_아무것도_저장하지_않는다() {
        when(userProfileRepository.existsById(USER_ID)).thenReturn(false);

        userProfileService.deleteImage(USER_ID);

        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void 프로필이_없으면_빈_프로필을_돌려준다() {
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.empty());

        ProfileResponse response = userProfileService.getProfile(USER_ID);

        assertThat(response).isEqualTo(new ProfileResponse(null, false, null, null));
    }

    @Test
    void 사진_버전은_한국시간_기준_epoch_밀리초다() {
        LocalDateTime imageUpdatedAt = LocalDateTime.of(2026, 10, 3, 9, 0, 0);
        ProfileSummary summary = summary("마켓러", imageUpdatedAt);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(summary));

        ProfileResponse response = userProfileService.getProfile(USER_ID);

        long expected =
                imageUpdatedAt.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
        assertThat(response.nickname()).isEqualTo("마켓러");
        assertThat(response.hasImage()).isTrue();
        assertThat(response.imageVersion()).isEqualTo(expected);
    }

    @Test
    void 사진이_없는_프로필은_hasImage가_false이고_버전이_null이다() {
        ProfileSummary summary = summary("마켓러", null);
        when(userProfileRepository.findByUserId(eq(USER_ID), eq(ProfileSummary.class)))
                .thenReturn(Optional.of(summary));

        ProfileResponse response = userProfileService.getProfile(USER_ID);

        assertThat(response.hasImage()).isFalse();
        assertThat(response.imageVersion()).isNull();
    }

    @Test
    void 사진이_없으면_getImage는_NotFoundException을_던진다() {
        UserProfile noImage = new UserProfile(USER_ID);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(noImage));

        assertThatThrownBy(() -> userProfileService.getImage(USER_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 프로필_행이_없어도_getImage는_NotFoundException을_던진다() {
        when(userProfileRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getImage(USER_ID)).isInstanceOf(NotFoundException.class);
    }

    private UserProfile savedProfile() {
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private ProfileSummary summary(String nickname, LocalDateTime imageUpdatedAt) {
        return summary(nickname, imageUpdatedAt, null);
    }

    private ProfileSummary summary(String nickname, LocalDateTime imageUpdatedAt, LocalDateTime nicknameChangedAt) {
        ProfileSummary summary = mock(ProfileSummary.class);
        when(summary.getNickname()).thenReturn(nickname);
        when(summary.getImageUpdatedAt()).thenReturn(imageUpdatedAt);
        when(summary.getNicknameChangedAt()).thenReturn(nicknameChangedAt);
        return summary;
    }
}
