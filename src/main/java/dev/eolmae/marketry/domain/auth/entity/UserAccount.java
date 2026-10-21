package dev.eolmae.marketry.domain.auth.entity;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.auth.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class UserAccount {

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

    public void updateEmail(String email) {
        this.email = email;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
