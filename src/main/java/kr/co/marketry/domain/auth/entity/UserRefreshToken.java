package kr.co.marketry.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import lombok.Getter;

@Entity
@Table(name = "user_refresh_token")
@Getter
public class UserRefreshToken {

    // 갱신 토큰 교체 후 옛 토큰을 계속 사용할 수 있는 유예 시간. 동시 탭이 같은 옛 토큰으로 갱신을
    // 시도해도 이 시간 안이면 허용해 로그아웃되지 않게 한다.
    public static final Duration REPLACEMENT_GRACE = Duration.ofSeconds(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 255)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private LocalDateTime replacedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected UserRefreshToken() {}

    public static UserRefreshToken create(UserAccount user, String tokenHash, LocalDateTime expiresAt) {
        var token = new UserRefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return token;
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now(Zone.KST.zoneId());
        }
    }

    // 이미 교체된 토큰이면 시각을 덮어쓰지 않는다. 덮어쓰면 유예 중 재사용마다 30초가 밀려 옛 토큰을
    // 영원히 쓸 수 있게 된다.
    public void replace() {
        if (replacedAt == null) {
            replacedAt = LocalDateTime.now(Zone.KST.zoneId());
        }
    }

    public boolean isUsableAt(LocalDateTime time) {
        return revokedAt == null
                && expiresAt.isAfter(time)
                && (replacedAt == null || replacedAt.plus(REPLACEMENT_GRACE).isAfter(time));
    }
}
