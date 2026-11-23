package com.claimflow.member.repository;

import java.util.Optional;
import java.util.UUID;

import com.claimflow.member.domain.Member;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID>, JpaSpecificationExecutor<Member> {

    @EntityGraph(attributePaths = "plan")
    Optional<Member> findWithPlanById(UUID id);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = "select nextval('member_number_seq')", nativeQuery = true)
    long nextMemberNumber();
}
