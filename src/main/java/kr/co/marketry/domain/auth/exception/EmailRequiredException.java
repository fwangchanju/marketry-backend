package kr.co.marketry.domain.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EmailRequiredException extends ResponseStatusException {

    public EmailRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "로그인하려면 이메일 주소가 필요합니다.");
    }
}
