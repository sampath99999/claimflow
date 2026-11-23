package com.claimflow.claims;

import static com.claimflow.claims.ClaimFixtures.MEMBER_ID;
import static com.claimflow.claims.TestJwt.admin;
import static com.claimflow.claims.TestJwt.adjuster;
import static com.claimflow.claims.TestJwt.member;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimflow.common.event.ClaimStatusChangedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@EmbeddedKafka(partitions = 1, topics = ClaimStatusChangedEvent.TOPIC, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class ClaimsFlowIT {

    // Started eagerly: @EmbeddedKafka's JUnit condition loads the Spring context before
    // extensions get their beforeAll callback, so WireMockExtension would be too late.
    static final WireMockServer memberService = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        memberService.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("claimflow.claims.member-service.base-url", memberService::baseUrl);
        registry.add("claimflow.claims.member-service.read-timeout", () -> "500ms");
        registry.add("resilience4j.retry.instances.memberService.wait-duration", () -> "10ms");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    // JUnit creates a new instance per test, the consumer and what it has read are shared.
    private static Consumer<String, String> consumer;
    private static final List<ClaimStatusChangedEvent> received = new ArrayList<>();

    @BeforeEach
    void setUp() {
        memberService.resetAll();
        // The failure tests below would otherwise trip the breaker for whichever test runs next.
        circuitBreakers.circuitBreaker("memberService").reset();
        if (consumer == null) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("claims-it", "false", broker);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()).createConsumer();
            broker.consumeFromAnEmbeddedTopic(consumer, ClaimStatusChangedEvent.TOPIC);
        }
    }

    @AfterAll
    static void close() {
        if (consumer != null) {
            consumer.close();
        }
        memberService.stop();
    }

    @Test
    void claimMovesFromSubmissionToPaymentAndEmitsEveryTransition() throws Exception {
        stubEligibility(MEMBER_ID, true);
        LocalDate serviceDate = LocalDate.now().minusDays(5);

        MvcResult submitted = mvc.perform(post("/api/claims").with(member(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(serviceDate, "250.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.memberNumber").value("M100001"))
                .andReturn();
        UUID claimId = UUID.fromString(read(submitted).get("id").asText());

        memberService.verify(getRequestedFor(urlPathEqualTo("/api/members/" + MEMBER_ID + "/eligibility"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
                .withQueryParam("serviceDate", equalTo(serviceDate.toString())));

        act(claimId, "\"0\"", "START_REVIEW", null, adjuster()).andExpect(status().isOk());
        act(claimId, "\"1\"", "APPROVE", "Within fee schedule", adjuster())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payableAmount").value(200.00))
                .andExpect(jsonPath("$.memberResponsibility").value(50.00))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
        act(claimId, "\"2\"", "MARK_PAID", null, admin())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""));

        List<ClaimStatusChangedEvent> events = eventsFor(claimId, 4);
        assertThat(events).extracting(ClaimStatusChangedEvent::toStatus)
                .containsExactly("SUBMITTED", "IN_REVIEW", "APPROVED", "PAID");
        assertThat(events).extracting(ClaimStatusChangedEvent::claimVersion).containsExactly(0L, 1L, 2L, 3L);
        assertThat(events.get(2).amount()).isEqualByComparingTo("200.00");
        assertThat(events.get(2).actor()).isEqualTo("ava");
    }

    @Test
    void staleIfMatchIsRejected() throws Exception {
        stubEligibility(MEMBER_ID, true);
        UUID claimId = submit("90.00");
        act(claimId, "\"0\"", "START_REVIEW", null, adjuster()).andExpect(status().isOk());

        act(claimId, "\"0\"", "DENY", "Not covered", adjuster())
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));
    }

    @Test
    void illegalTransitionIsConflict() throws Exception {
        stubEligibility(MEMBER_ID, true);
        UUID claimId = submit("91.00");

        act(claimId, "\"0\"", "MARK_PAID", null, adjuster())
                .andExpect(status().isForbidden());
        act(claimId, "\"0\"", "APPROVE", null, adjuster())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("SUBMITTED"));
    }

    @Test
    void ineligibleMemberGetsUnprocessableEntity() throws Exception {
        stubEligibility(MEMBER_ID, false);

        mvc.perform(post("/api/claims").with(member(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(LocalDate.now().minusDays(2), "40.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("MEMBER_NOT_ELIGIBLE"));
    }

    @Test
    void memberServiceErrorsAreRetriedThenReportedAsUnavailable() throws Exception {
        memberService.stubFor(get(urlPathEqualTo("/api/members/" + MEMBER_ID + "/eligibility"))
                .willReturn(aResponse().withStatus(503)));

        mvc.perform(post("/api/claims").with(member(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(LocalDate.now().minusDays(2), "41.00")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));

        memberService.verify(3, getRequestedFor(urlPathEqualTo("/api/members/" + MEMBER_ID + "/eligibility")));
    }

    @Test
    void slowMemberServiceTimesOut() throws Exception {
        memberService.stubFor(get(urlPathEqualTo("/api/members/" + MEMBER_ID + "/eligibility"))
                .willReturn(okJson(eligibilityJson(MEMBER_ID, true)).withFixedDelay(1500)));

        long start = System.nanoTime();
        mvc.perform(post("/api/claims").with(member(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(LocalDate.now().minusDays(2), "42.00")))
                .andExpect(status().isServiceUnavailable());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500 * 3));
    }

    @Test
    void membersOnlyListTheirOwnClaims() throws Exception {
        stubEligibility(MEMBER_ID, true);
        submit("93.00");
        UUID james = UUID.fromString("9a98105c-6ecc-4f82-aaf5-ad034623584f");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/claims")
                        .param("memberId", MEMBER_ID.toString()).with(member(james)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private UUID submit(String amount) throws Exception {
        MvcResult result = mvc.perform(post("/api/claims").with(member(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(LocalDate.now().minusDays(1), amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions act(UUID id, String etag, String action, String note,
                                                                   org.springframework.test.web.servlet.request.RequestPostProcessor user) throws Exception {
        String body = note == null
                ? "{\"action\": \"%s\"}".formatted(action)
                : "{\"action\": \"%s\", \"note\": \"%s\"}".formatted(action, note);
        return mvc.perform(post("/api/claims/{id}/actions", id).with(user)
                .header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<ClaimStatusChangedEvent> eventsFor(UUID claimId, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))) {
                received.add(json.readValue(record.value(), ClaimStatusChangedEvent.class));
            }
            List<ClaimStatusChangedEvent> matching = received.stream().filter(e -> e.claimId().equals(claimId)).toList();
            if (matching.size() >= expected) {
                return matching;
            }
        }
        throw new AssertionError("Expected " + expected + " events for claim " + claimId + " but saw " + received);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static void stubEligibility(UUID memberId, boolean eligible) {
        memberService.stubFor(get(urlPathEqualTo("/api/members/" + memberId + "/eligibility"))
                .willReturn(okJson(eligibilityJson(memberId, eligible))));
    }

    private static String eligibilityJson(UUID memberId, boolean eligible) {
        return """
                {"memberId": "%s", "memberNumber": "M100001", "memberName": "Maria Gonzalez",
                 "serviceDate": "2026-11-01", "eligible": %s, "reason": "%s", "planCode": "GOLD-PPO",
                 "coinsurancePercent": 20, "annualDeductible": 1000.00}
                """.formatted(memberId, eligible, eligible ? "ELIGIBLE" : "OUTSIDE_COVERAGE_PERIOD");
    }

    private static String claimBody(LocalDate serviceDate, String amount) {
        return """
                {"providerNpi": "1234567893", "providerName": "Lakeside Family Clinic",
                 "serviceDate": "%s", "diagnosisCode": "J02.9",
                 "lines": [{"procedureCode": "99213", "description": "Office visit", "units": 1, "chargeAmount": %s}]}
                """.formatted(serviceDate, amount);
    }
}
