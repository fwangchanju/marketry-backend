-- 텔레그램 발송 설정(관리자 메시지 페이지). 행이 없으면 발송은 꺼짐이고 시각은 서버 설정값(telegram.*)을 쓴다.
CREATE TABLE telegram_send_setting (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    sector_send_minute INTEGER NOT NULL,
    sector_send_interval_minutes INTEGER NOT NULL,
    map_send_times VARCHAR(200) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
