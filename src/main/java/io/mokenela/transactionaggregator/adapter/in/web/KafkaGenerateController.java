package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.adapter.out.kafka.KafkaTransactionProducer;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/kafka")
@ConditionalOnBean(KafkaTransactionProducer.class)
@Tag(name = "Kafka", description = "Simulate high-volume transaction ingestion via Kafka")
class KafkaGenerateController {

    private final KafkaTransactionProducer producer;

    KafkaGenerateController(KafkaTransactionProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/generate")
    @Operation(
            summary = "Generate mock transactions",
            description = "Publishes synthetic transactions to the Kafka topic. The consumer " +
                          "picks them up and persists them to the database asynchronously."
    )
    Mono<GenerateResponse> generate(
            @RequestParam String customerId,
            @RequestParam(defaultValue = "1000") int count) {

        return producer.generate(CustomerId.of(customerId), count)
                .map(published -> new GenerateResponse(customerId, published));
    }

    record GenerateResponse(String customerId, int published) {}
}
