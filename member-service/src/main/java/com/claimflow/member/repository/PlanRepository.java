package com.claimflow.member.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.claimflow.member.domain.Plan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select p from Plan p
            where (:q is null
                   or lower(p.code) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.name) like lower(concat('%', cast(:q as string), '%')))
              and (:activeOnly = false or p.active = true)
            """)
    Page<Plan> search(@Param("q") String q, @Param("activeOnly") boolean activeOnly, Pageable pageable);

    @Query("select m.plan.id as planId, count(m) as members from Member m where m.plan.id in :planIds group by m.plan.id")
    List<PlanMemberCount> countMembers(@Param("planIds") List<UUID> planIds);

    interface PlanMemberCount {
        UUID getPlanId();

        long getMembers();
    }
}
