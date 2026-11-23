package com.claimflow.claims.repository;

import static com.claimflow.claims.ClaimFixtures.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimAction;
import com.claimflow.claims.domain.ClaimStatus;
import com.claimflow.claims.repository.ClaimSpecifications.ClaimFilter;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY, refresh = AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClaimRepositoryTest {

    @Autowired
    ClaimRepository claims;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
    }

    @Test
    void concurrentEditOfSameVersionFailsOptimisticLock() {
        Claim saved = claims.saveAndFlush(claim(MEMBER_ID, "CLM-T-1", LocalDate.of(2026, 10, 1), "120.00"));

        Claim first = tx.execute(s -> claims.findWithLinesById(saved.getId()).orElseThrow());
        Claim second = tx.execute(s -> claims.findWithLinesById(saved.getId()).orElseThrow());

        first.apply(ClaimAction.START_REVIEW, "ava", null);
        claims.saveAndFlush(first);

        second.apply(ClaimAction.START_REVIEW, "ben", null);
        assertThatThrownBy(() -> claims.saveAndFlush(second)).isInstanceOf(OptimisticLockingFailureException.class);

        Claim reloaded = claims.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getAssignedTo()).isEqualTo("ava");
    }

    @Test
    void filtersByStatusDateRangeAndText() {
        UUID other = UUID.randomUUID();
        claims.saveAndFlush(claim(MEMBER_ID, "CLM-T-10", LocalDate.of(2026, 9, 1), "10.00"));
        claims.saveAndFlush(claim(MEMBER_ID, "CLM-T-11", LocalDate.of(2026, 10, 15), "20.00"));
        Claim reviewed = claim(other, "CLM-T-12", LocalDate.of(2026, 10, 20), "30.00");
        reviewed.apply(ClaimAction.START_REVIEW, "ava", null);
        claims.saveAndFlush(reviewed);

        var october = claims.findAll(ClaimSpecifications.matching(
                new ClaimFilter(null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null)),
                PageRequest.of(0, 10, Sort.by("claimNumber")));
        var inReview = claims.findAll(ClaimSpecifications.matching(
                new ClaimFilter(Set.of(ClaimStatus.IN_REVIEW), null, null, null, null, "ava")), PageRequest.of(0, 10));
        var byText = claims.findAll(ClaimSpecifications.matching(
                new ClaimFilter(null, MEMBER_ID, null, null, "t-10", null)), PageRequest.of(0, 10));

        assertThat(october.getContent()).extracting(Claim::getClaimNumber).containsExactly("CLM-T-11", "CLM-T-12");
        assertThat(inReview.getContent()).extracting(Claim::getClaimNumber).containsExactly("CLM-T-12");
        assertThat(byText.getContent()).extracting(Claim::getClaimNumber).containsExactly("CLM-T-10");
    }

    @Test
    void duplicateCheckIgnoresDeniedClaims() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        Claim denied = claim(MEMBER_ID, "CLM-T-20", date, "75.00");
        denied.apply(ClaimAction.START_REVIEW, "ava", null);
        denied.apply(ClaimAction.DENY, "ava", "Wrong member");
        claims.saveAndFlush(denied);

        assertThat(claims.existsDuplicate(MEMBER_ID, "1234567893", date, new BigDecimal("75.00"), ClaimStatus.DENIED)).isFalse();

        claims.saveAndFlush(claim(MEMBER_ID, "CLM-T-21", date, "75.00"));
        assertThat(claims.existsDuplicate(MEMBER_ID, "1234567893", date, new BigDecimal("75.00"), ClaimStatus.DENIED)).isTrue();
    }

    @Test
    void statusCountsSumBilledAmounts() {
        claims.saveAndFlush(claim(MEMBER_ID, "CLM-T-30", LocalDate.of(2026, 10, 1), "10.00"));
        claims.saveAndFlush(claim(MEMBER_ID, "CLM-T-31", LocalDate.of(2026, 10, 2), "15.50"));

        List<StatusCount> counts = claims.countByStatus();

        assertThat(counts).singleElement().satisfies(c -> {
            assertThat(c.status()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(c.count()).isEqualTo(2);
            assertThat(c.totalBilled()).isEqualByComparingTo("25.50");
        });
    }

    @Test
    void claimNumbersComeFromSequence() {
        long first = claims.nextClaimSequence();
        assertThat(claims.nextClaimSequence()).isEqualTo(first + 1);
    }

    private static Claim claim(UUID memberId, String number, LocalDate serviceDate, String amount) {
        return Claim.submit(number, new Claim.MemberSnapshot(memberId, "M100001", "Maria Gonzalez", "GOLD-PPO", 20),
                "1234567893", "Lakeside Family Clinic", serviceDate, "J02.9", "maria",
                List.of(new Claim.LineItem("99213", "Office visit", 1, new BigDecimal(amount))));
    }
}
