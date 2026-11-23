package com.claimflow.member.web;

import static com.claimflow.member.TestJwt.admin;
import static com.claimflow.member.TestJwt.adjuster;
import static com.claimflow.member.TestJwt.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.claimflow.member.config.SecurityConfig;
import com.claimflow.member.domain.Eligibility;
import com.claimflow.member.service.MemberService;
import com.claimflow.member.service.NotFoundException;
import com.claimflow.member.web.dto.MemberDtos.EligibilityResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MemberService memberService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void membersCannotSearchTheMemberDirectory() throws Exception {
        mvc.perform(get("/api/members").with(member(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjusterCannotEnrollMembers() throws Exception {
        mvc.perform(post("/api/members").with(adjuster())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidEnrollmentReturnsProblemDetailWithFieldErrors() throws Exception {
        mvc.perform(post("/api/members").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "Lee", "dateOfBirth": "2999-01-01",
                                 "email": "not-an-email", "planCode": "GOLD-PPO", "coverageStart": "2026-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void eligibilityReturnsDecision() throws Exception {
        UUID id = UUID.randomUUID();
        when(memberService.eligibility(eq(id), eq(LocalDate.of(2026, 3, 1)), any()))
                .thenReturn(new EligibilityResponse(id, "M100001", "Maria Gonzalez", LocalDate.of(2026, 3, 1),
                        false, Eligibility.Reason.OUTSIDE_COVERAGE_PERIOD, "GOLD-PPO", 20, java.math.BigDecimal.TEN));

        mvc.perform(get("/api/members/{id}/eligibility", id).param("serviceDate", "2026-03-01").with(member(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reason").value("OUTSIDE_COVERAGE_PERIOD"));
    }

    @Test
    void unknownMemberIsNotFoundProblem() throws Exception {
        UUID id = UUID.randomUUID();
        when(memberService.get(eq(id), any())).thenThrow(new NotFoundException("Member " + id + " not found"));

        mvc.perform(get("/api/members/{id}", id).with(adjuster()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Member " + id + " not found"));
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/members/not-a-uuid").with(adjuster()))
                .andExpect(status().isBadRequest());
    }

    private static String validBody() {
        return """
                {"firstName": "Grace", "lastName": "Lee", "dateOfBirth": "2001-05-03",
                 "email": "g@example.com", "planCode": "GOLD-PPO", "coverageStart": "2026-01-01"}
                """;
    }
}
