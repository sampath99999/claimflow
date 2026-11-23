package com.claimflow.claims.validation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.claimflow.claims.config.ClaimsProperties;
import com.claimflow.claims.web.dto.ClaimDtos.LineRequest;
import com.claimflow.claims.web.dto.ClaimDtos.SubmitClaimRequest;

import org.springframework.stereotype.Component;

/**
 * Business rules that run before any remote call. Field format checks live on the request
 * DTO as Bean Validation annotations; these are the rules that need context (today's date,
 * the whole claim, configured limits).
 */
@Component
public class ClaimRules {

    private final Clock clock;
    private final ClaimsProperties properties;

    public ClaimRules(Clock clock, ClaimsProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public List<Violation> check(SubmitClaimRequest request) {
        List<Violation> violations = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);

        if (request.serviceDate().isAfter(today)) {
            violations.add(new Violation("SERVICE_DATE_IN_FUTURE", "Service date cannot be in the future"));
        } else if (request.serviceDate().isBefore(today.minusDays(properties.timelyFilingDays()))) {
            violations.add(new Violation("TIMELY_FILING_EXCEEDED",
                    "Claims must be filed within " + properties.timelyFilingDays() + " days of the service date"));
        }

        if (!Npi.isValid(request.providerNpi())) {
            violations.add(new Violation("INVALID_NPI", "Provider NPI " + request.providerNpi() + " fails the check digit"));
        }

        Set<String> seen = new HashSet<>();
        for (LineRequest line : request.lines()) {
            if (!seen.add(line.procedureCode())) {
                violations.add(new Violation("DUPLICATE_LINE",
                        "Procedure " + line.procedureCode() + " appears on more than one line; use units instead"));
            }
        }

        BigDecimal total = request.lines().stream().map(LineRequest::chargeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(properties.maxClaimAmount()) > 0) {
            violations.add(new Violation("AMOUNT_OVER_LIMIT",
                    "Total billed " + total + " is above the electronic submission limit of " + properties.maxClaimAmount()));
        }
        return violations;
    }
}
