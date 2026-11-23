package com.claimflow.audit.repository;

import java.util.List;
import java.util.UUID;

import com.claimflow.audit.domain.AuditEntry;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEntryRepository extends MongoRepository<AuditEntry, UUID>, AuditEntrySearch {

    List<AuditEntry> findByClaimIdOrderByOccurredAtAscClaimVersionAsc(UUID claimId);
}
