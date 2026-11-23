package com.claimflow.member.repository;

import java.util.Locale;

import com.claimflow.member.domain.Member;
import com.claimflow.member.domain.MemberStatus;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;

public final class MemberSpecifications {

    private MemberSpecifications() {
    }

    public static Specification<Member> search(String q, MemberStatus status, String planCode) {
        return Specification.allOf(matchesText(q), hasStatus(status), onPlan(planCode), fetchPlan());
    }

    static Specification<Member> matchesText(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern),
                cb.like(cb.lower(root.get("memberNumber")), pattern),
                cb.like(cb.lower(root.get("email")), pattern));
    }

    static Specification<Member> hasStatus(MemberStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Member> onPlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.upper(root.get("plan").get("code")), planCode.toUpperCase(Locale.ROOT));
    }

    // Fetch the plan in the page query, but not in the count query Spring Data runs alongside it.
    static Specification<Member> fetchPlan() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("plan", JoinType.INNER);
            }
            return null;
        };
    }
}
