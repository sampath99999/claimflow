package com.claimflow.audit.web;

import static com.claimflow.audit.AuditEvents.MEMBER_ID;
import static com.claimflow.audit.AuditEvents.event;
import static com.claimflow.audit.TestJwt.adjuster;
import static com.claimflow.audit.TestJwt.member;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.claimflow.audit.config.SecurityConfig;
import com.claimflow.audit.domain.AuditEntry;
import com.claimflow.audit.repository.AuditEntryRepository;
import com.claimflow.audit.service.TimelineService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, TimelineService.class})
class AuditControllerTest {

    private static final UUID CLAIM = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditEntryRepository entries;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void memberSeesTimelineOfOwnClaim() throws Exception {
        stubTimeline();

        mvc.perform(get("/api/audit/claims/{id}/timeline", CLAIM).with(member(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-2026-000007"))
                .andExpect(jsonPath("$.events").value(2))
                .andExpect(jsonPath("$.entries[1].toStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.lastChangeAt").value("2026-11-01T08:01:00Z"));
    }

    @Test
    void otherMembersGetNotFound() throws Exception {
        stubTimeline();

        mvc.perform(get("/api/audit/claims/{id}/timeline", CLAIM).with(member(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void activityFeedIsStaffOnly() throws Exception {
        mvc.perform(get("/api/audit/events").with(member(MEMBER_ID))).andExpect(status().isForbidden());
    }

    @Test
    void timelineNeedsAuthentication() throws Exception {
        mvc.perform(get("/api/audit/claims/{id}/timeline", CLAIM)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownClaimHasEmptyTimeline() throws Exception {
        when(entries.findByClaimIdOrderByOccurredAtAscClaimVersionAsc(CLAIM)).thenReturn(List.of());

        mvc.perform(get("/api/audit/claims/{id}/timeline", CLAIM).with(adjuster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    private void stubTimeline() {
        when(entries.findByClaimIdOrderByOccurredAtAscClaimVersionAsc(CLAIM)).thenReturn(List.of(
                AuditEntry.from(event(CLAIM, null, "SUBMITTED", "maria", 0, T0), T0, 0, 1),
                AuditEntry.from(event(CLAIM, "SUBMITTED", "IN_REVIEW", "ava", 1, T0.plusSeconds(60)), T0, 0, 2)));
    }
}
