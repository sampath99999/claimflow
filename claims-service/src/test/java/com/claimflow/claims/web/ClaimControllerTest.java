package com.claimflow.claims.web;

import static com.claimflow.claims.ClaimFixtures.MEMBER_ID;
import static com.claimflow.claims.TestJwt.adjuster;
import static com.claimflow.claims.TestJwt.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import com.claimflow.claims.ClaimFixtures;
import com.claimflow.claims.config.SecurityConfig;
import com.claimflow.claims.domain.ClaimAction;
import com.claimflow.claims.service.ClaimService;
import com.claimflow.claims.service.StaleClaimException;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimDetail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClaimController.class)
@Import(SecurityConfig.class)
class ClaimControllerTest {

    private static final String START_REVIEW = "{\"action\": \"START_REVIEW\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ClaimService claimService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void getReturnsVersionAsETag() throws Exception {
        ClaimDetail detail = ClaimDetail.from(ClaimFixtures.claim("maria", "80.00"), Set.of());
        when(claimService.get(eq(detail.id()), any())).thenReturn(detail);

        mvc.perform(get("/api/claims/{id}", detail.id()).with(member(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.lines[0].procedureCode").value("99213"));
    }

    @Test
    void actionWithoutIfMatchIsPreconditionRequired() throws Exception {
        mvc.perform(post("/api/claims/{id}/actions", UUID.randomUUID()).with(adjuster())
                        .contentType(MediaType.APPLICATION_JSON).content(START_REVIEW))
                .andExpect(status().isPreconditionRequired());
        verifyNoInteractions(claimService);
    }

    @Test
    void staleIfMatchIsPreconditionFailedWithCurrentETag() throws Exception {
        UUID id = UUID.randomUUID();
        when(claimService.apply(eq(id), eq(1L), any(), any())).thenThrow(new StaleClaimException(id, 1, 2));

        mvc.perform(post("/api/claims/{id}/actions", id).with(adjuster())
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON).content(START_REVIEW))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    void membersCannotActOnClaims() throws Exception {
        mvc.perform(post("/api/claims/{id}/actions", UUID.randomUUID()).with(member(MEMBER_ID))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(START_REVIEW))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownActionIsBadRequest() throws Exception {
        mvc.perform(post("/api/claims/{id}/actions", UUID.randomUUID()).with(adjuster())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\": \"APPROVE_EVERYTHING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submissionValidatesCodesAndLines() throws Exception {
        mvc.perform(post("/api/claims").with(member(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerNpi": "12345", "providerName": "Clinic", "serviceDate": "2026-11-01",
                                 "diagnosisCode": "flu", "lines": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "providerNpi", "diagnosisCode", "lines")));
    }

    @Test
    void sortingOnUnlistedFieldIsRejected() throws Exception {
        mvc.perform(get("/api/claims").param("sort", "decisionNote").with(adjuster()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsAreStaffOnly() throws Exception {
        mvc.perform(get("/api/claims/stats").with(member(MEMBER_ID))).andExpect(status().isForbidden());
    }

    @Test
    void weakETagIsAccepted() throws Exception {
        UUID id = UUID.randomUUID();
        ClaimDetail detail = ClaimDetail.from(ClaimFixtures.claim("maria", "80.00"), Set.of(ClaimAction.APPROVE));
        when(claimService.apply(eq(id), anyLong(), any(), any())).thenReturn(detail);

        mvc.perform(post("/api/claims/{id}/actions", id).with(adjuster())
                        .header(HttpHeaders.IF_MATCH, "W/\"4\"")
                        .contentType(MediaType.APPLICATION_JSON).content(START_REVIEW))
                .andExpect(status().isOk());
    }
}
