package kr.co.marketry.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

class TransactionSynchronizationConfigTest {

    @Test
    void 실제_트랜잭션이_있을_때만_동기화한다() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();

        new TransactionSynchronizationConfig()
                .synchronizeOnActualTransactionOnly()
                .customize(transactionManager);

        assertThat(transactionManager.getTransactionSynchronization())
                .isEqualTo(AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
    }
}
