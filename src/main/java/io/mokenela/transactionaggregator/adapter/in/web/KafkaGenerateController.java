package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.port.out.TransactionGeneratorPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Validated
@RestController
@RequestMapping("/api/v1/kafka")
@ConditionalOnBean(TransactionGeneratorPort.class)
@Tag(name = "Kafka", description = "Simulate high-volume transaction ingestion via Kafka")
class KafkaGenerateController {

    private final TransactionGeneratorPort generator;

    KafkaGenerateController(TransactionGeneratorPort generator) {
        this.generator = generator;
    }

    @PostMapping("/generate")
    @Operation(
            summary = "Generate mock transactions",
            description = "Publishes synthetic transactions to the Kafka topic. The consumer " +
                          "picks them up and persists them to the database asynchronously."
    )
    Mono<GenerateResponse> generate(
            @RequestParam String customerId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(10_000) int count) {

        return generator.generate(CustomerId.of(customerId), count)
                .map(published -> new GenerateResponse(customerId, published));
    }

    record GenerateResponse(String customerId, int published) {}
}
