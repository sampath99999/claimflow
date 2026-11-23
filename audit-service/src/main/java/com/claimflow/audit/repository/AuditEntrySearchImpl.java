package com.claimflow.audit.repository;

import java.util.ArrayList;
import java.util.List;

import com.claimflow.audit.domain.AuditEntry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

class AuditEntrySearchImpl implements AuditEntrySearch {

    private final MongoTemplate mongo;

    AuditEntrySearchImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Page<AuditEntry> search(ActivityFilter filter, Pageable pageable) {
        List<Criteria> criteria = new ArrayList<>();
        if (filter.actor() != null && !filter.actor().isBlank()) {
            criteria.add(Criteria.where("actor").is(filter.actor()));
        }
        if (filter.toStatus() != null && !filter.toStatus().isBlank()) {
            criteria.add(Criteria.where("toStatus").is(filter.toStatus()));
        }
        if (filter.from() != null || filter.to() != null) {
            Criteria occurred = Criteria.where("occurredAt");
            if (filter.from() != null) {
                occurred = occurred.gte(filter.from());
            }
            if (filter.to() != null) {
                occurred = occurred.lt(filter.to());
            }
            criteria.add(occurred);
        }
        Query query = criteria.isEmpty() ? new Query() : new Query(new Criteria().andOperator(criteria));
        List<AuditEntry> content = mongo.find(Query.of(query).with(pageable), AuditEntry.class);
        return PageableExecutionUtils.getPage(content, pageable, () -> mongo.count(query, AuditEntry.class));
    }
}
