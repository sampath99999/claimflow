package com.claimflow.audit.repository;

import java.time.Instant;

import com.claimflow.audit.domain.AuditEntry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditEntrySearch {

    Page<AuditEntry> search(ActivityFilter filter, Pageable pageable);

    record ActivityFilter(String actor, String toStatus, Instant from, Instant to) {
    }
}
