package com.claimflow.audit.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.claimflow.common.event.ClaimStatusChangedEvent;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Failed records are retried twice, then parked on "<topic>.DLT". Records that could not be
 * deserialized arrive as raw bytes, so they need a byte[] template; everything else is JSON.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    static final String DEAD_LETTER_TOPIC = ClaimStatusChangedEvent.TOPIC + ".DLT";

    // Same partition count as the source topic: the recoverer keeps the original partition number.
    @Bean
    NewTopic claimStatusDeadLetterTopic(@Value("${claimflow.kafka.partitions:3}") int partitions,
                                        @Value("${claimflow.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaProperties properties) {
        Map<String, Object> producer = properties.buildProducerProperties(null);
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        Map<String, Object> bytes = new HashMap<>(producer);
        bytes.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        Map<String, Object> json = new HashMap<>(producer);
        json.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        json.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(bytes)));
        templates.put(Object.class, new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(json)));

        var recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
