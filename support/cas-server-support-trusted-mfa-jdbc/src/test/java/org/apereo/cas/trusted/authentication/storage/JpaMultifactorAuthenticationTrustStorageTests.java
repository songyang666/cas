package org.apereo.cas.trusted.authentication.storage;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.audit.spi.config.CasCoreAuditConfiguration;
import org.apereo.cas.config.support.EnvironmentConversionServiceInitializer;
import org.apereo.cas.trusted.authentication.api.MultifactorAuthenticationTrustRecord;
import org.apereo.cas.trusted.authentication.api.MultifactorAuthenticationTrustStorage;
import org.apereo.cas.trusted.config.JdbcMultifactorAuthnTrustConfiguration;
import org.apereo.cas.trusted.config.MultifactorAuthnTrustConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Test cases for {@link JpaMultifactorAuthenticationTrustStorage}.
 *
 * @author Daniel Frett
 * @since 5.3.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        JdbcMultifactorAuthnTrustConfiguration.class,
        MultifactorAuthnTrustConfiguration.class,
        CasCoreAuditConfiguration.class,
        RefreshAutoConfiguration.class})
@EnableTransactionManagement(proxyTargetClass = true)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableScheduling
@ContextConfiguration(initializers = EnvironmentConversionServiceInitializer.class)
@Slf4j
public class JpaMultifactorAuthenticationTrustStorageTests {
    private static final String PRINCIPAL = "principal";
    private static final String PRINCIPAL2 = "principal2";
    private static final String GEOGRAPHY = "geography";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Autowired
    @Qualifier("mfaTrustEngine")
    private MultifactorAuthenticationTrustStorage mfaTrustEngine;

    @Test
    public void verifyExpireByKey() {
        // create 2 records
        mfaTrustEngine.set(MultifactorAuthenticationTrustRecord.newInstance(PRINCIPAL, GEOGRAPHY));
        mfaTrustEngine.set(MultifactorAuthenticationTrustRecord.newInstance(PRINCIPAL, GEOGRAPHY));
        final Set<MultifactorAuthenticationTrustRecord> records = mfaTrustEngine.get(PRINCIPAL);
        assertEquals(2, records.size());

        // expire 1 of the records
        mfaTrustEngine.expire(records.stream().findFirst().get().getRecordKey());
        assertEquals(1, mfaTrustEngine.get(PRINCIPAL).size());

        emptyTrustEngine();
    }

    @Test
    public void verifyRetrieveAndExpireByDate() {
        // create records
        Stream.of(PRINCIPAL, PRINCIPAL2).forEach(p -> {
            for (int offset = 0; offset < 3; offset++) {
                final MultifactorAuthenticationTrustRecord record =
                        MultifactorAuthenticationTrustRecord.newInstance(p, GEOGRAPHY);
                record.setRecordDate(LocalDate.now().minusDays(offset));
                mfaTrustEngine.set(record);
            }
        });
        assertThat(mfaTrustEngine.get(LocalDate.now().minusDays(30)), hasSize(6));
        assertThat(mfaTrustEngine.get(LocalDate.now()), hasSize(2));

        // expire records older than today
        mfaTrustEngine.expire(LocalDate.now().minusDays(1));
        assertThat(mfaTrustEngine.get(LocalDate.now().minusDays(30)), hasSize(2));
        assertThat(mfaTrustEngine.get(LocalDate.now()), hasSize(2));

        emptyTrustEngine();
    }

    private void emptyTrustEngine() {
        Stream.of(PRINCIPAL, PRINCIPAL2)
                .map(mfaTrustEngine::get)
                .flatMap(Set::stream)
                .forEach(r -> mfaTrustEngine.expire(r.getRecordKey()));

        assertThat(mfaTrustEngine.get(PRINCIPAL), empty());
        assertThat(mfaTrustEngine.get(PRINCIPAL2), empty());
    }
}
