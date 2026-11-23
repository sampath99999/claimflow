package com.claimflow.claims.config;

import com.claimflow.common.event.ClaimStatusChangedEvent;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class KafkaTopicConfig {

    @Bean
    NewTopic claimStatusTopic(@Value("${claimflow.kafka.partitions:3}") int partitions,
                              @Value("${claimflow.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ClaimStatusChangedEvent.TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
