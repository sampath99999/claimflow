package com.claimflow.claims.service;

import static com.claimflow.claims.ClaimFixtures.MEMBER_ID;
import static com.claimflow.claims.ClaimFixtures.VALID_NPI;
import static com.claimflow.claims.ClaimFixtures.claim;
import static com.claimflow.claims.ClaimFixtures.line;
import static com.claimflow.claims.ClaimFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.claimflow.claims.client.EligibilityResult;
import com.claimflow.claims.client.MemberClient;
import com.claimflow.claims.client.MemberNotFoundException;
import com.claimflow.claims.config.ClaimsProperties;
import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimAction;
import com.claimflow.claims.domain.ClaimStatus;
import com.claimflow.claims.repository.ClaimRepository;
import com.claimflow.claims.validation.ClaimRejectedException;
import com.claimflow.claims.validation.ClaimRules;
import com.claimflow.claims.validation.Violation;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimActionRequest;
import com.claimflow.common.event.ClaimStatusChangedEvent;
import com.claimflow.common.security.CurrentUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final CurrentUser MARIA = new CurrentUser("maria", "MEMBER", Optional.of(MEMBER_ID), "token");
    private static final CurrentUser AVA = new CurrentUser("ava", "ADJUSTER", Optional.empty(), "token");

    @Mock
    ClaimRepository claims;
    @Mock
    MemberClient memberClient;
    @Mock
    ApplicationEventPublisher events;
    @Mock
    TransactionTemplate tx;

    private ClaimService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var properties = new ClaimsProperties(365, new BigDecimal("100000.00"), new BigDecimal("25000.00"),
                new ClaimsProperties.MemberService(URI.create("http://member"), null, null));
        service = new ClaimService(claims, new ClaimRules(clock, properties), new ClaimPolicy(properties),
                memberClient, events, tx, clock);
        lenient().when(tx.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void submitsEligibleClaimAndPublishesSubmittedEvent() {
        when(memberClient.checkEligibility(MEMBER_ID, LocalDate.of(2026, 11, 18), "token")).thenReturn(eligible(true));
        when(claims.nextClaimSequence()).thenReturn(42L);

        var detail = service.submit(request(LocalDate.of(2026, 11, 18), VALID_NPI, line("99213", "120.00")), MARIA);

        assertThat(detail.claimNumber()).isEqualTo("CLM-2026-000042");
        assertThat(detail.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(detail.memberNumber()).isEqualTo("M100001");
        var event = ArgumentCaptor.forClass(ClaimStatusChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().fromStatus()).isNull();
        assertThat(event.getValue().toStatus()).isEqualTo("SUBMITTED");
        assertThat(event.getValue().actor()).isEqualTo("maria");
    }

    @Test
    void ruleViolationsStopBeforeCallingMemberService() {
        assertThatThrownBy(() -> service.submit(request(LocalDate.of(2027, 1, 1), VALID_NPI, line("99213", "1.00")), MARIA))
                .isInstanceOf(ClaimRejectedException.class);
        verifyNoInteractions(memberClient);
    }

    @Test
    void ineligibleMemberIsRejectedAndNothingSaved() {
        when(memberClient.checkEligibility(any(), any(), any())).thenReturn(eligible(false));

        assertThatThrownBy(() -> service.submit(request(LocalDate.of(2026, 11, 18), VALID_NPI, line("99213", "1.00")), MARIA))
                .isInstanceOfSatisfying(ClaimRejectedException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::code).containsExactly("MEMBER_NOT_ELIGIBLE"));
        verify(claims, never()).saveAndFlush(any());
    }

    @Test
    void unknownMemberBecomesRuleViolation() {
        UUID unknown = UUID.randomUUID();
        when(memberClient.checkEligibility(eq(unknown), any(), any())).thenThrow(new MemberNotFoundException(unknown));
        var request = new com.claimflow.claims.web.dto.ClaimDtos.SubmitClaimRequest(unknown, VALID_NPI, "Clinic",
                LocalDate.of(2026, 11, 18), "J02.9", List.of(line("99213", "1.00")));

        assertThatThrownBy(() -> service.submit(request, AVA))
                .isInstanceOfSatisfying(ClaimRejectedException.class, e ->
                        assertThat(e.getViolations().getFirst().code()).isEqualTo("MEMBER_NOT_FOUND"));
    }

    @Test
    void duplicateClaimIsRejected() {
        when(memberClient.checkEligibility(any(), any(), any())).thenReturn(eligible(true));
        when(claims.existsDuplicate(eq(MEMBER_ID), eq(VALID_NPI), any(), any(), eq(ClaimStatus.DENIED))).thenReturn(true);

        assertThatThrownBy(() -> service.submit(request(LocalDate.of(2026, 11, 18), VALID_NPI, line("99213", "1.00")), MARIA))
                .isInstanceOfSatisfying(ClaimRejectedException.class, e ->
                        assertThat(e.getViolations().getFirst().code()).isEqualTo("DUPLICATE_CLAIM"));
    }

    @Test
    void memberCannotSubmitForSomeoneElse() {
        var request = new com.claimflow.claims.web.dto.ClaimDtos.SubmitClaimRequest(UUID.randomUUID(), VALID_NPI, "Clinic",
                LocalDate.of(2026, 11, 18), "J02.9", List.of(line("99213", "1.00")));

        assertThatThrownBy(() -> service.submit(request, MARIA)).isInstanceOf(ActionNotAllowedException.class);
    }

    @Test
    void staleVersionIsRejectedBeforeAnyChange() {
        Claim claim = claim("maria", "100.00");
        when(claims.findWithLinesById(claim.getId())).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.apply(claim.getId(), 3, new ClaimActionRequest(ClaimAction.START_REVIEW, null), AVA))
                .isInstanceOf(StaleClaimException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        verifyNoInteractions(events);
    }

    @Test
    void appliesActionAndPublishesTransition() {
        Claim claim = claim("maria", "100.00");
        when(claims.findWithLinesById(claim.getId())).thenReturn(Optional.of(claim));

        var detail = service.apply(claim.getId(), 0, new ClaimActionRequest(ClaimAction.START_REVIEW, "  "), AVA);

        assertThat(detail.status()).isEqualTo(ClaimStatus.IN_REVIEW);
        assertThat(detail.allowedActions()).containsExactlyInAnyOrder(ClaimAction.APPROVE, ClaimAction.DENY);
        var event = ArgumentCaptor.forClass(ClaimStatusChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().fromStatus()).isEqualTo("SUBMITTED");
        assertThat(event.getValue().toStatus()).isEqualTo("IN_REVIEW");
        assertThat(event.getValue().note()).isNull();
    }

    @Test
    void memberCannotReadAnotherMembersClaim() {
        Claim claim = claim("maria", "100.00");
        when(claims.findWithLinesById(claim.getId())).thenReturn(Optional.of(claim));
        var other = new CurrentUser("james", "MEMBER", Optional.of(UUID.randomUUID()), "t");

        assertThatThrownBy(() -> service.get(claim.getId(), other)).isInstanceOf(ClaimNotFoundException.class);
    }

    private static EligibilityResult eligible(boolean eligible) {
        return new EligibilityResult(MEMBER_ID, "M100001", "Maria Gonzalez", LocalDate.of(2026, 11, 18), eligible,
                eligible ? "ELIGIBLE" : "OUTSIDE_COVERAGE_PERIOD", "GOLD-PPO", 20, new BigDecimal("1000.00"));
    }
}
