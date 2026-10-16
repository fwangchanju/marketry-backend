package dev.eolmae.marketry.domain.auth.dto;

/** nicknameChangeableAt은 닉네임을 다시 바꿀 수 있게 되는 시각(KST 기준 epoch 밀리초)이고, 지금 바꿀 수 있으면 null이다. */
public record ProfileResponse(String nickname, boolean hasImage, Long imageVersion, Long nicknameChangeableAt) {

    public static ProfileResponse empty() {
        return new ProfileResponse(null, false, null, null);
    }
}
