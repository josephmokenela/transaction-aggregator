package io.mokenela.transactionaggregator.adapter.in.kafka;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mokenela.transactionaggregator.application.service.TransactionCategorizationService;
import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaTransactionConsumerTest {

    @Mock
    SaveTransactionPort saveTransactionPort;

    @Mock
    KafkaDltSender dltSender;

    @Mock
    Acknowledgment acknowledgment;

    // Real instances — no dependencies, pure logic
    private final TransactionCategorizationService categorizationService = new TransactionCategorizationService();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private KafkaTransactionConsumer consumer;

    private static final String TOPIC     = "transactions";
    private static final String DLT_TOPIC = "transactions.DLT";

    @BeforeEach
    void setUp() {
        consumer = new KafkaTransactionConsumer(
                saveTransactionPort, categorizationService, dltSender, meterRegistry, TOPIC);
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void consume_shouldSaveAllTransactions_whenBatchIsValid() {
        var events = List.of(validEvent(), validEvent(), validEvent());
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        consumer.consume(events, acknowledgment);

        verify(saveTransactionPort, timeout(2000).times(3)).save(any());
        verifyNoInteractions(dltSender);
    }

    @Test
    void consume_shouldAssignCategory_basedOnEventDescription() {
        var event = eventWith("Monthly salary payment", "Employer Ltd", "CREDIT");
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        consumer.consume(List.of(event), acknowledgment);

        verify(saveTransactionPort, timeout(2000)).save(
                argThat(tx -> tx.category() == TransactionCategory.SALARY)
        );
    }

    @Test
    void consume_shouldCompleteWithoutError_forEmptyBatch() {
        consumer.consume(List.of(), acknowledgment);

        verifyNoInteractions(saveTransactionPort);
        verifyNoInteractions(dltSender);
    }

    // ── Mapping errors → DLT ──────────────────────────────────────────────────

    @Test
    void consume_shouldRouteToDlt_whenEventHasInvalidTransactionType() {
        var badEvent = eventWithInvalidType();
        mockDlt();

        consumer.consume(List.of(badEvent), acknowledgment);

        // save should never be attempted — mapping fails before reaching the port
        verify(dltSender, timeout(2000)).send(eq(DLT_TOPIC), eq(badEvent.id()), eq(badEvent));
        verifyNoInteractions(saveTransactionPort);
    }

    @Test
    void consume_shouldIncrementDroppedCounter_withMappingErrorReason_whenRoutedToDlt() {
        var badEvent = eventWithInvalidType();
        mockDlt();

        consumer.consume(List.of(badEvent), acknowledgment);

        verify(dltSender, timeout(2000)).send(anyString(), anyString(), any());
        var counter = meterRegistry.find("kafka.messages.dropped")
                .tag("reason", "mapping_error")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ── Save failures → retry / DLT ───────────────────────────────────────────

    @Test
    void consume_shouldRetryThreeTimes_thenRouteToDlt_whenSaveFailsWithTransientR2dbcError() {
        var event = validEvent();
        when(saveTransactionPort.save(any()))
                .thenReturn(Mono.error(new RuntimeException("R2DBC Connection refused")));
        mockDlt();

        consumer.consume(List.of(event), acknowledgment);

        // Mono.defer() ensures each retry re-calls save() on the port, so we can
        // assert exact call count: initial attempt + 3 retries = 4 total
        verify(saveTransactionPort, timeout(10000).times(4)).save(any());
        verify(dltSender, timeout(10000)).send(eq(DLT_TOPIC), anyString(), any());
    }

    @Test
    void consume_shouldRouteToDltImmediately_withoutRetrying_whenSaveFailsWithNonTransientError() {
        var event = validEvent();
        when(saveTransactionPort.save(any()))
                .thenReturn(Mono.error(new RuntimeException("Constraint violation — duplicate key")));
        mockDlt();

        consumer.consume(List.of(event), acknowledgment);

        // non-transient: filter rejects the error, no retries — exactly one save attempt
        verify(saveTransactionPort, timeout(2000).times(1)).save(any());
        verify(dltSender, timeout(2000)).send(eq(DLT_TOPIC), anyString(), any());
    }

    @Test
    void consume_shouldIncrementDroppedCounter_withSaveErrorReason_whenSaveFailsAndRoutesDlt() {
        var event = validEvent();
        when(saveTransactionPort.save(any()))
                .thenReturn(Mono.error(new RuntimeException("Constraint violation")));
        mockDlt();

        consumer.consume(List.of(event), acknowledgment);

        verify(dltSender, timeout(2000)).send(anyString(), anyString(), any());
        var counter = meterRegistry.find("kafka.messages.dropped")
                .tag("reason", "save_error")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ── Mixed batch ───────────────────────────────────────────────────────────

    @Test
    void consume_shouldSaveValidEvents_andRoutInvalidEventsToDlt_inMixedBatch() {
        var valid1  = validEvent();
        var valid2  = validEvent();
        var invalid = eventWithInvalidType();
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        mockDlt();

        consumer.consume(List.of(valid1, invalid, valid2), acknowledgment);

        verify(saveTransactionPort, timeout(2000).times(2)).save(any());
        verify(dltSender, timeout(2000).times(1)).send(eq(DLT_TOPIC), anyString(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private KafkaTransactionEvent validEvent() {
        return new KafkaTransactionEvent(
                UUID.randomUUID().toString(),
                "11111111-1111-1111-1111-111111111111",
                UUID.randomUUID().toString(),
                new BigDecimal("50.00"),
                "GBP",
                "DEBIT",
                "COMPLETED",
                "Coffee shop purchase",
                "Costa Coffee",
                Instant.now()
        );
    }

    private KafkaTransactionEvent eventWith(String description, String merchantName, String type) {
        return new KafkaTransactionEvent(
                UUID.randomUUID().toString(),
                "11111111-1111-1111-1111-111111111111",
                UUID.randomUUID().toString(),
                new BigDecimal("3500.00"),
                "GBP",
                type,
                "COMPLETED",
                description,
                merchantName,
                Instant.now()
        );
    }

    private KafkaTransactionEvent eventWithInvalidType() {
        return new KafkaTransactionEvent(
                UUID.randomUUID().toString(),
                "11111111-1111-1111-1111-111111111111",
                UUID.randomUUID().toString(),
                new BigDecimal("50.00"),
                "GBP",
                "INVALID_TYPE",  // causes IllegalArgumentException in TransactionType.valueOf()
                "COMPLETED",
                "Test transaction",
                null,
                Instant.now()
        );
    }

    private void mockDlt() {
        when(dltSender.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }
}
