package com.claimflow.claims.repository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimStatus;

import org.springframework.data.jpa.domain.Specification;

public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    public record ClaimFilter(Set<ClaimStatus> statuses, UUID memberId, LocalDate serviceFrom, LocalDate serviceTo,
                              String q, String assignedTo) {
    }

    public static Specification<Claim> matching(ClaimFilter filter) {
        return Specification.allOf(
                statusIn(filter.statuses()),
                equalTo("memberId", filter.memberId()),
                serviceOnOrAfter(filter.serviceFrom()),
                serviceOnOrBefore(filter.serviceTo()),
                equalTo("assignedTo", filter.assignedTo()),
                text(filter.q()));
    }

    private static Specification<Claim> statusIn(Set<ClaimStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? null : (root, query, cb) -> root.get("status").in(statuses);
    }

    private static Specification<Claim> equalTo(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static Specification<Claim> serviceOnOrAfter(LocalDate date) {
        return date == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("serviceDate"), date);
    }

    private static Specification<Claim> serviceOnOrBefore(LocalDate date) {
        return date == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("serviceDate"), date);
    }

    private static Specification<Claim> text(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("claimNumber")), pattern),
                cb.like(cb.lower(root.get("providerName")), pattern),
                cb.like(cb.lower(root.get("memberName")), pattern),
                cb.like(cb.lower(root.get("memberNumber")), pattern));
    }
}
