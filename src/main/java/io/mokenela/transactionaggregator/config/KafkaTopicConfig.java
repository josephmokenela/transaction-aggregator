package io.mokenela.transactionaggregator.config;

import io.mokenela.transactionaggregator.adapter.in.kafka.KafkaTransactionEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    NewTopic transactionsTopic(@Value("${app.kafka.topic}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    ProducerFactory<String, KafkaTransactionEvent> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
        ));
    }

    @Bean
    KafkaTemplate<String, KafkaTransactionEvent> kafkaTemplate(
            ProducerFactory<String, KafkaTransactionEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, KafkaTransactionEvent> consumerFactory() {
        var deserializer = new JsonDeserializer<>(KafkaTransactionEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, "transaction-aggregator",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class
                ),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, KafkaTransactionEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, KafkaTransactionEvent> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, KafkaTransactionEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        return factory;
    }
}
