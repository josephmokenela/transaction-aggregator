package io.mokenela.transactionaggregator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class KafkaTopicConfig {

    @Bean
    NewTopic transactionsTopic(@Value("${app.kafka.topic}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(6)   // allows 6 parallel consumer threads
                .replicas(1)     // single-broker dev setup
                .build();
    }
}
