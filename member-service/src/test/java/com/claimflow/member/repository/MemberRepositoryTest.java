package com.claimflow.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.claimflow.member.domain.Member;
import com.claimflow.member.domain.MemberStatus;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@ActiveProfiles("demo")
class MemberRepositoryTest {

    @Autowired
    MemberRepository members;

    @Autowired
    PlanRepository plans;

    @Test
    void searchMatchesFullNameCaseInsensitively() {
        var page = members.findAll(MemberSpecifications.search("maria gonz", null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Member::getMemberNumber).containsExactly("M100001");
    }

    @Test
    void searchFiltersByStatusAndPlan() {
        var terminated = members.findAll(MemberSpecifications.search(null, MemberStatus.TERMINATED, null),
                PageRequest.of(0, 10, Sort.by("memberNumber")));
        var gold = members.findAll(MemberSpecifications.search(null, MemberStatus.ACTIVE, "gold-ppo"),
                PageRequest.of(0, 2));

        assertThat(terminated.getContent()).extracting(Member::getMemberNumber).containsExactly("M100005", "M100012");
        assertThat(gold.getTotalElements()).isEqualTo(4);
        assertThat(gold.getContent()).hasSize(2).allMatch(m -> m.getPlan().getCode().equals("GOLD-PPO"));
    }

    @Test
    void memberNumberSequenceContinuesAfterSeedData() {
        assertThat(members.nextMemberNumber()).isGreaterThan(100012L);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        var plan = plans.findByCodeIgnoreCase("SILVER-HMO").orElseThrow();
        members.saveAndFlush(new Member("M900001", "A", "B", LocalDate.of(1990, 1, 1), "Dup@Example.com", plan,
                LocalDate.of(2026, 1, 1), null));

        assertThatThrownBy(() -> members.saveAndFlush(new Member("M900002", "C", "D", LocalDate.of(1990, 1, 1),
                "dup@example.com", plan, LocalDate.of(2026, 1, 1), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void planSearchCountsMembers() {
        var page = plans.search("ppo", false, PageRequest.of(0, 10, Sort.by("code")));
        var counts = plans.countMembers(page.map(p -> p.getId()).getContent());

        assertThat(page.getContent()).extracting(p -> p.getCode()).containsExactly("GOLD-PPO", "LEGACY-PPO-2019");
        assertThat(counts).anyMatch(c -> c.getMembers() == 4);
    }
}
