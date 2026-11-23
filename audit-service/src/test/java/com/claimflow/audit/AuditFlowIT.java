package com.claimflow.audit;

import static com.claimflow.audit.AuditEvents.MEMBER_ID;
import static com.claimflow.audit.AuditEvents.event;
import static com.claimflow.audit.TestJwt.adjuster;
import static com.claimflow.audit.TestJwt.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import com.claimflow.audit.repository.AuditEntryRepository;
import com.claimflow.common.event.ClaimStatusChangedEvent;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = {ClaimStatusChangedEvent.TOPIC, ClaimStatusChangedEvent.TOPIC + ".DLT"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class AuditFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    AuditEntryRepository entries;

    @Test
    void eventsBecomeAnOrderedTimelineAndDuplicatesAreDropped() throws Exception {
        UUID claim = UUID.randomUUID();
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ClaimStatusChangedEvent submitted = event(claim, null, "SUBMITTED", "maria", 0, t0);
        ClaimStatusChangedEvent review = event(claim, "SUBMITTED", "IN_REVIEW", "ava", 1, t0.plusSeconds(30));
        ClaimStatusChangedEvent approved = event(claim, "IN_REVIEW", "APPROVED", "ava", 2, t0.plusSeconds(90));

        KafkaTemplate<String, ClaimStatusChangedEvent> producer = jsonProducer();
        for (ClaimStatusChangedEvent e : new ClaimStatusChangedEvent[] {submitted, review, approved, review}) {
            producer.send(ClaimStatusChangedEvent.TOPIC, claim.toString(), e).get();
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(entries.findByClaimIdOrderByOccurredAtAscClaimVersionAsc(claim)).hasSize(3));
        Thread.sleep(500);
        assertThat(entries.findByClaimIdOrderByOccurredAtAscClaimVersionAsc(claim)).hasSize(3);

        mvc.perform(get("/api/audit/claims/{id}/timeline", claim).with(member(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").value(3))
                .andExpect(jsonPath("$.entries[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.entries[2].toStatus").value("APPROVED"))
                .andExpect(jsonPath("$.entries[2].amount").value(160.00));

        mvc.perform(get("/api/audit/events").param("actor", "ava").with(adjuster()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].toStatus").value("APPROVED"));
    }

    @Test
    void unreadableMessageGoesToDeadLetterTopic() throws Exception {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        var raw = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()));
        raw.send(ClaimStatusChangedEvent.TOPIC, "bad-key", "{not json").get();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-check", "false", broker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> dlt = new DefaultKafkaConsumerFactory<>(consumerProps,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(dlt, ClaimStatusChangedEvent.TOPIC + ".DLT");

            var record = KafkaTestUtils.getSingleRecord(dlt, ClaimStatusChangedEvent.TOPIC + ".DLT", Duration.ofSeconds(20));

            assertThat(record.key()).isEqualTo("bad-key");
            assertThat(record.value()).isEqualTo("{not json");
            assertThat(record.headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
        }
    }

    private KafkaTemplate<String, ClaimStatusChangedEvent> jsonProducer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>()));
    }
}
