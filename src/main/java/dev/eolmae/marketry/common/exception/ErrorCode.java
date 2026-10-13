package dev.eolmae.marketry.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 에러 카탈로그(초안). code는 {@link #name()} 사용, message는 간략 유지. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT("입력값이 올바르지 않습니다."),
    INTERNAL_ERROR("서버 내부 오류가 발생했습니다."),

    // Toss market calendar
    MARKET_CALENDAR_RESPONSE_INVALID("시장 시간표 응답이 올바르지 않습니다."),
    TOSS_AUTH_NOT_CONFIGURED("토스 API 인증 설정이 없습니다."),
    TOSS_TOKEN_ISSUE_FAILED("토스 토큰 발급에 실패했습니다."),
    TOSS_CALENDAR_FETCH_FAILED("토스 시장 시간표 조회에 실패했습니다."),

    // Kiwoom API
    KIWOOM_HTTP_ERROR("키움 API HTTP 오류가 발생했습니다."),
    KIWOOM_RATE_LIMIT("키움 API 호출 한도를 초과했습니다."),
    KIWOOM_CONNECTION_FAILED("키움 API 연결에 실패했습니다."),
    KIWOOM_SERVER_ERROR("키움 API 서버 오류가 발생했습니다."),
    KIWOOM_RESPONSE_PARSE_FAILED("키움 API 응답 파싱에 실패했습니다."),
    KIWOOM_ERROR_RESPONSE("키움 API가 오류 응답을 반환했습니다."),
    KIWOOM_TOKEN_ISSUE_FAILED("키움 토큰 발급에 실패했습니다."),

    // KRX
    KRX_LOGIN_FAILED("KRX 로그인에 실패했습니다."),
    KRX_SESSION_EXPIRED("KRX 세션이 만료되었습니다."),
    KRX_RESPONSE_INVALID("KRX 응답 구조가 올바르지 않습니다."),

    // Auth
    REFRESH_TOKEN_CLEANUP_FAILED("갱신 토큰 정리 배치 실행에 실패했습니다."),
    DEV_LOGIN_OWNER_NOT_FOUND("개발용 로그인 소유자 계정을 찾을 수 없습니다."),
    PROFILE_NICKNAME_INVALID("닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있습니다."),
    PROFILE_NICKNAME_DUPLICATE("이미 사용 중인 닉네임입니다."),
    PROFILE_IMAGE_TOO_LARGE_DIMENSION("사진의 가로·세로 크기가 너무 큽니다."),
    PROFILE_IMAGE_NOT_FOUND("등록된 프로필 사진이 없습니다."),

    // Stock 수집
    STOCK_INFO_SYNC_FAILED("종목 기준정보 동기화에 실패했습니다."),
    COLLECTOR_EXECUTION_FAILED("수집기 실행에 실패했습니다."),
    SNAPSHOT_RETENTION_FAILED("스냅샷 정리 배치 실행에 실패했습니다."),
    COMPOSITE_INDEX_ROW_NOT_FOUND("투자자별매매종합 종합지수 행을 찾을 수 없습니다."),
    BASE_SNAPSHOT_NOT_FOUND("연산 기준 스냅샷 데이터가 없습니다."),

    // Market summary
    RENDERER_DISABLED("렌더러가 비활성 상태입니다."),
    SCREENSHOT_CAPTURE_FAILED("스크린샷 캡처에 실패했습니다."),
    IMAGE_STITCH_FAILED("이미지 병합에 실패했습니다."),
    TELEGRAM_MESSAGE_SEND_FAILED("텔레그램 메시지 발송에 실패했습니다."),
    TELEGRAM_IMAGE_SEND_FAILED("텔레그램 이미지 발송에 실패했습니다."),

    // Market map
    SECTOR_TREE_SERIALIZE_FAILED("섹터 트리 직렬화에 실패했습니다."),
    SECTOR_TREE_PARSE_FAILED("섹터 스냅샷 파싱에 실패했습니다."),
    SECTOR_NOT_FOUND("이미 삭제되었거나 존재하지 않는 섹터입니다."),
    SECTOR_NAME_DUPLICATE("이미 사용 중인 섹터명입니다."),
    SECTOR_CIRCULAR_REFERENCE("해당 위치로 섹터 위치 변경이 불가능합니다."),
    SECTOR_HAS_ASSIGNED_STOCK("배정된 종목이 있어 삭제할 수 없습니다."),
    SECTOR_NEW_LISTING_PROTECTED("신규 상장시 자동 분류되는 항목이라 삭제가 불가능합니다."),
    STOCK_SECTOR_NOT_FOUND("섹터 배정 정보가 없는 종목입니다."),
    SNAPSHOT_NOT_FOUND("이미 삭제되었거나 존재하지 않는 스냅샷입니다."),
    SCALE_THRESHOLD_NOT_FOUND("이미 삭제되었거나 존재하지 않는 색상 기준값입니다."),
    SCALE_THRESHOLD_DUPLICATE("이미 사용 중인 기준 등락률입니다."),
    SNAPSHOT_FORMAT_UNSUPPORTED("이 스냅샷 형식은 복원할 수 없습니다."),
    CLASSIFICATION_SOURCE_INVALID("지원하지 않는 분류입니다.");

    private final String message;
}
