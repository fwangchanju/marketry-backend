package dev.eolmae.marketry.domain.auth.enums;

/** 관리자 회원 목록에서 쓰는 회원 구분 — 저장하는 권한(Role)과 특별 회원 표시를 합쳐 한 값으로 본다. */
public enum MemberGrade {
    USER,
    SPECIAL,
    ADMIN
}
