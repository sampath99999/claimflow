package com.claimflow.claims.validation;

import static com.claimflow.claims.ClaimFixtures.VALID_NPI;
import static com.claimflow.claims.ClaimFixtures.line;
import static com.claimflow.claims.ClaimFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.claimflow.claims.config.ClaimsProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClaimRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private final ClaimRules rules = new ClaimRules(
            Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC),
            new ClaimsProperties(365, new BigDecimal("100000.00"), new BigDecimal("25000.00"),
                    new ClaimsProperties.MemberService(URI.create("http://localhost"), null, null)));

    @Test
    void cleanClaimPasses() {
        assertThat(rules.check(request(TODAY.minusDays(3), VALID_NPI, line("99213", "120.00"), line("87880", "35.00"))))
                .isEmpty();
    }

    @Test
    void futureServiceDateIsRejected() {
        assertThat(codes(request(TODAY.plusDays(1), VALID_NPI, line("99213", "120.00"))))
                .containsExactly("SERVICE_DATE_IN_FUTURE");
    }

    @Test
    void timelyFilingWindowIsEnforced() {
        assertThat(rules.check(request(TODAY.minusDays(365), VALID_NPI, line("99213", "1.00")))).isEmpty();
        assertThat(codes(request(TODAY.minusDays(366), VALID_NPI, line("99213", "1.00"))))
                .containsExactly("TIMELY_FILING_EXCEEDED");
    }

    @Test
    void duplicateProcedureLinesAreRejected() {
        assertThat(codes(request(TODAY, VALID_NPI, line("99213", "50.00"), line("99213", "50.00"))))
                .containsExactly("DUPLICATE_LINE");
    }

    @Test
    void totalAboveSubmissionLimitIsRejected() {
        assertThat(codes(request(TODAY, VALID_NPI, line("27447", "99000.00"), line("01402", "1500.00"))))
                .containsExactly("AMOUNT_OVER_LIMIT");
    }

    @Test
    void reportsEveryViolationAtOnce() {
        assertThat(codes(request(TODAY.plusDays(2), "1234567890", line("99213", "1.00"), line("99213", "1.00"))))
                .containsExactlyInAnyOrder("SERVICE_DATE_IN_FUTURE", "INVALID_NPI", "DUPLICATE_LINE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567893", "1452367893", "1987654328", "1765432103"})
    void acceptsValidNpis(String npi) {
        assertThat(Npi.isValid(npi)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "123456789", "12345678931", "abcdefghij", ""})
    void rejectsInvalidNpis(String npi) {
        assertThat(Npi.isValid(npi)).isFalse();
    }

    private java.util.List<String> codes(com.claimflow.claims.web.dto.ClaimDtos.SubmitClaimRequest request) {
        return rules.check(request).stream().map(Violation::code).toList();
    }
}
