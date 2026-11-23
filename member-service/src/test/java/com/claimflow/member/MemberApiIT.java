package com.claimflow.member;

import static com.claimflow.member.TestJwt.admin;
import static com.claimflow.member.TestJwt.adjuster;
import static com.claimflow.member.TestJwt.member;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@ActiveProfiles("demo")
class MemberApiIT {

    private static final UUID MARIA = UUID.fromString("eb47888d-3ffb-4ad2-940a-ae35e825b454");
    private static final UUID FATIMA = UUID.fromString("c108f60f-7cb6-46fe-80d9-2fe90a4acb76");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void adminCreatesPlanAndEnrollsMemberWhoIsThenEligible() throws Exception {
        mvc.perform(post("/api/plans").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "IT-HMO-2026", "name": "IT HMO", "type": "HMO",
                                 "annualDeductible": 500.00, "coinsurancePercent": 15, "outOfPocketMax": 4000.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        String body = mvc.perform(post("/api/members").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Nina", "lastName": "Patel", "dateOfBirth": "1993-02-02",
                                 "email": "nina.patel@example.com", "planCode": "IT-HMO-2026", "coverageStart": "2026-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberNumber").value("M100013"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get("id").asText());

        mvc.perform(get("/api/members/{id}/eligibility", id).param("serviceDate", "2026-02-10").with(member(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.coinsurancePercent").value(15));
    }

    @Test
    void terminatedMemberIsNotEligible() throws Exception {
        mvc.perform(get("/api/members/{id}/eligibility", FATIMA).param("serviceDate", "2026-08-01").with(adjuster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reason").value("MEMBER_NOT_ACTIVE"));
    }

    @Test
    void memberSeesOnlyOwnRecord() throws Exception {
        mvc.perform(get("/api/members/me").with(member(MARIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberNumber").value("M100001"));
        mvc.perform(get("/api/members/{id}", FATIMA).with(member(MARIA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchSupportsPagingAndSorting() throws Exception {
        String body = mvc.perform(get("/api/members").param("size", "5").param("sort", "memberNumber,desc").with(adjuster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andReturn().getResponse().getContentAsString();
        JsonNode page = json.readTree(body);
        String first = page.get("content").get(0).get("memberNumber").asText();
        String second = page.get("content").get(1).get("memberNumber").asText();
        org.assertj.core.api.Assertions.assertThat(first).isGreaterThan(second);
    }

    @Test
    void unknownSortPropertyIsBadRequest() throws Exception {
        mvc.perform(get("/api/members").param("sort", "password").with(adjuster()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retiringPlanIsReflectedInSearch() throws Exception {
        String body = mvc.perform(get("/api/plans").param("q", "platinum").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("content").get(0).get("id").asText();

        mvc.perform(put("/api/plans/{id}", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Platinum EPO", "type": "EPO", "annualDeductible": 250.00,
                                 "coinsurancePercent": 10, "outOfPocketMax": 3500.00, "active": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        mvc.perform(get("/api/plans").param("activeOnly", "true").with(member(MARIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code", hasItem("GOLD-PPO")));
    }
}
