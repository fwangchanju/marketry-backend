package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, UserAccountRepositoryCustom {

    Optional<UserAccount> findByIssuerAndSub(String issuer, String sub);
}
