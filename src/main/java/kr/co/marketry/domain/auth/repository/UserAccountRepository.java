package kr.co.marketry.domain.auth.repository;

import java.util.Optional;
import kr.co.marketry.domain.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, UserAccountRepositoryCustom {

    Optional<UserAccount> findByIssuerAndSub(String issuer, String sub);
}
