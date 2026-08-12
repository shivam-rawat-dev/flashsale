package com.enterprise.flashsale.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ORDER_FINALIZED_TOPIC = "order-finalized-topic";

    @Bean
    public NewTopic orderFinalizedTopic() {
        return TopicBuilder.name(ORDER_FINALIZED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}