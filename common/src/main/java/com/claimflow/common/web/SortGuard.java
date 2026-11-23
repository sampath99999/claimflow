package com.claimflow.common.web;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Rejects sort parameters that are not on an allow list, so clients can't sort on
 * arbitrary (possibly unindexed or internal) entity attributes.
 */
public final class SortGuard {

    private SortGuard() {
    }

    public static Pageable allow(Pageable pageable, Set<String> properties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!properties.contains(order.getProperty())) {
                throw new IllegalArgumentException("Cannot sort by '" + order.getProperty() + "'. Allowed: " + properties);
            }
        }
        return pageable;
    }
}
