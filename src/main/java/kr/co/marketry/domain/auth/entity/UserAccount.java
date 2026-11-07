package kr.co.marketry.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.auth.enums.Role;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class UserAccount {

    /** 로컬 개발용 테스트 회원의 연동 값. */
    public static final String LOCAL_TEST_ISSUER = "local-test";

    /** 서비스가 내부에서 쓰는 시스템 회원의 연동 값(예: MARKETRY 분류 공개본을 담는 계정). 사람이 아니다. */
    public static final String SYSTEM_ISSUER = "marketry-system";

    /** 접속 시각을 다시 기록하기까지의 최소 간격 — 화면을 열 때마다 DB에 쓰지 않도록 이 안에서는 기록을 건너뛴다. */
    static final Duration ACCESS_RECORD_INTERVAL = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String issuer;

    @Column(length = 255)
    private String sub;

    @Column(length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 특별 회원 여부 — 권한과 별개다. 관리자가 아닌 회원에게만 의미가 있다.
    @Column(nullable = false)
    private boolean special;

    // 마지막으로 접속한 시각 — 로그인·세션 갱신·세션 확인에서 기록한다. 기록이 생기기 전의 회원은 비어 있다.
    private LocalDateTime lastAccessAt;

    @Column(name = "first_custom_classification_at")
    private LocalDateTime firstCustomClassificationAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserAccount() {}

    public static UserAccount create(String issuer, String sub, String email) {
        var user = new UserAccount();
        user.issuer = issuer;
        user.sub = sub;
        user.email = email;
        user.role = Role.USER;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public void changeSpecial(boolean special) {
        this.special = special;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    /**
     * 접속 시각을 기록한다. 마지막 기록 뒤 {@link #ACCESS_RECORD_INTERVAL}이 지나지 않았으면 건너뛰고 false를 돌려준다.
     * 실제로 기록했을 때만 true라서, 호출한 쪽이 일별 접속 기록도 같은 간격으로만 남길 수 있다.
     */
    public boolean recordAccess(LocalDateTime now) {
        if (lastAccessAt != null && lastAccessAt.plus(ACCESS_RECORD_INTERVAL).isAfter(now)) {
            return false;
        }
        this.lastAccessAt = now;
        return true;
    }

    public void updateEmail(String email) {
        this.email = email;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
