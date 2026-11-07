package kr.co.marketry.domain.custom.enums;

// 마켓맵 색상 스케일 어드민 UI의 톤 프리셋 버튼과 1:1 대응. 실제 렌더링에 쓰는 색상값(hex)은
// 여기서 고른 톤에 명도(lightness)를 추가로 얹은 결과라 별도 필드(threshold_percent의 color)로 저장한다
// — 이 enum은 "어느 버튼을 눌렀는지"만 나타내는 값이다.
public enum ColorLabel {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    NAVY,
    PURPLE,
    GRAY
}
