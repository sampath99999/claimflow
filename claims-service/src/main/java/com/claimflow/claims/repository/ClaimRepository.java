package com.claimflow.claims.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {

    @EntityGraph(attributePaths = "lines")
    Optional<Claim> findWithLinesById(UUID id);

    @Query("""
            select count(c) > 0 from Claim c
            where c.memberId = :memberId
              and c.providerNpi = :npi
              and c.serviceDate = :serviceDate
              and c.totalBilled = :totalBilled
              and c.status <> :excluded
            """)
    boolean existsDuplicate(@Param("memberId") UUID memberId, @Param("npi") String npi,
                            @Param("serviceDate") LocalDate serviceDate, @Param("totalBilled") BigDecimal totalBilled,
                            @Param("excluded") ClaimStatus excluded);

    @Query("""
            select new com.claimflow.claims.repository.StatusCount(c.status, count(c), sum(c.totalBilled))
            from Claim c group by c.status
            """)
    List<StatusCount> countByStatus();

    @Query(value = "select nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimSequence();
}
