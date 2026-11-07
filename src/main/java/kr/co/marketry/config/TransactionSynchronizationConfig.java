package kr.co.marketry.config;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

@Configuration
public class TransactionSynchronizationConfig {

    /**
     * NOT_SUPPORTED로 시작한 메서드에서도 기본값(ALWAYS)은 트랜잭션 동기화를 켜서, 그 안에서 repository를 부르면 EntityManager가 메서드가
     * 끝날 때까지 묶이고 연결도 풀에 돌아가지 않는다. 그 상태로 달력 조회(REQUIRES_NEW)가 두 번째 연결을 기다리면 풀 고갈이 된다. 실제
     * 트랜잭션이 있을 때만 동기화하게 해서 트랜잭션 밖 조회는 쿼리마다 연결을 반납한다.
     */
    @Bean
    public TransactionManagerCustomizer<JpaTransactionManager> synchronizeOnActualTransactionOnly() {
        return transactionManager -> transactionManager.setTransactionSynchronization(
                AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
    }
}
