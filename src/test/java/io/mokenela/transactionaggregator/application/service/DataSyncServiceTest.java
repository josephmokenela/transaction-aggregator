package io.mokenela.transactionaggregator.application.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.SyncTransactionsCommand;
import io.mokenela.transactionaggregator.domain.port.out.FetchTransactionsPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSyncServiceTest {

    @Mock FetchTransactionsPort sourceA;
    @Mock FetchTransactionsPort sourceB;
    @Mock SaveTransactionPort saveTransactionPort;

    private final TransactionCategorizationService categorizationService = new TransactionCategorizationService();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private static final CustomerId CUSTOMER_ID = CustomerId.of("11111111-1111-1111-1111-111111111111");
    private static final Instant FROM = Instant.now().minus(7, ChronoUnit.DAYS);
    private static final Instant TO   = Instant.now();

    // ── listAvailableSources ──────────────────────────────────────────────────

    @Test
    void listAvailableSources_shouldReturnSourceIdForEachRegisteredSource() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceB.sourceId()).thenReturn(DataSourceId.MOCK_CARD);

        var service = new DataSyncService(
                List.of(sourceA, sourceB), saveTransactionPort, categorizationService, meterRegistry);

        assertThat(service.listAvailableSources())
                .containsExactly(DataSourceId.MOCK_BANK, DataSourceId.MOCK_CARD);
    }

    @Test
    void listAvailableSources_shouldReturnEmptyList_whenNoSourcesRegistered() {
        var service = new DataSyncService(
                List.of(), saveTransactionPort, categorizationService, meterRegistry);

        assertThat(service.listAvailableSources()).isEmpty();
    }

    // ── sync — happy path ─────────────────────────────────────────────────────

    @Test
    void sync_shouldSaveAllTransactions_andReturnTotalCount() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceA.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.just(transaction(DataSourceId.MOCK_BANK), transaction(DataSourceId.MOCK_BANK)));
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var service = new DataSyncService(
                List.of(sourceA), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command()))
                .assertNext(result -> {
                    assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
                    assertThat(result.totalTransactionsSynced()).isEqualTo(2);
                    assertThat(result.syncedSources()).containsExactly(DataSourceId.MOCK_BANK);
                })
                .verifyComplete();

        verify(saveTransactionPort, times(2)).save(any());
    }

    @Test
    void sync_shouldAggregateCountsAcrossMultipleSources() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceB.sourceId()).thenReturn(DataSourceId.MOCK_CARD);
        when(sourceA.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.just(transaction(DataSourceId.MOCK_BANK)));
        when(sourceB.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.just(transaction(DataSourceId.MOCK_CARD), transaction(DataSourceId.MOCK_CARD)));
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var service = new DataSyncService(
                List.of(sourceA, sourceB), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command()))
                .assertNext(result -> {
                    assertThat(result.totalTransactionsSynced()).isEqualTo(3);
                    assertThat(result.syncedSources()).containsExactlyInAnyOrder(
                            DataSourceId.MOCK_BANK, DataSourceId.MOCK_CARD);
                })
                .verifyComplete();
    }

    @Test
    void sync_shouldReturnZeroTotal_whenSourceReturnsEmptyFlux() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceA.fetchTransactions(any(), any(), any())).thenReturn(Flux.empty());

        var service = new DataSyncService(
                List.of(sourceA), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command()))
                .assertNext(result -> assertThat(result.totalTransactionsSynced()).isZero())
                .verifyComplete();

        verifyNoInteractions(saveTransactionPort);
    }

    @Test
    void sync_shouldReturnZeroTotal_whenNoSourcesRegistered() {
        var service = new DataSyncService(
                List.of(), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command()))
                .assertNext(result -> {
                    assertThat(result.totalTransactionsSynced()).isZero();
                    assertThat(result.syncedSources()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void sync_shouldIncrementSyncedCounter_byTotalTransactionCount() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceA.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.just(transaction(DataSourceId.MOCK_BANK), transaction(DataSourceId.MOCK_BANK)));
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var service = new DataSyncService(
                List.of(sourceA), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command())).expectNextCount(1).verifyComplete();

        var counter = meterRegistry.find("transactions.synced").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    // ── sync — category application ───────────────────────────────────────────

    @Test
    void sync_shouldApplyCategory_basedOnTransactionDescription() {
        var uncategorized = transactionWithDescription(DataSourceId.MOCK_BANK, "Monthly salary payment", "Employer Ltd");
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceA.fetchTransactions(any(), any(), any())).thenReturn(Flux.just(uncategorized));
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var service = new DataSyncService(
                List.of(sourceA), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command())).expectNextCount(1).verifyComplete();

        verify(saveTransactionPort).save(argThat(tx -> tx.category() == TransactionCategory.SALARY));
    }

    @Test
    void sync_shouldPreserveOtherTransactionFields_whenApplyingCategory() {
        var original = transaction(DataSourceId.MOCK_BANK);
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceA.fetchTransactions(any(), any(), any())).thenReturn(Flux.just(original));
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var service = new DataSyncService(
                List.of(sourceA), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command())).expectNextCount(1).verifyComplete();

        verify(saveTransactionPort).save(argThat(tx ->
                tx.id().equals(original.id()) &&
                tx.customerId().equals(original.customerId()) &&
                tx.amount().equals(original.amount()) &&
                tx.dataSourceId().equals(original.dataSourceId())
        ));
    }

    // ── sync — source failure handling ────────────────────────────────────────

    @Test
    void sync_shouldContinueWithOtherSources_whenOneSourceFails() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceB.sourceId()).thenReturn(DataSourceId.MOCK_CARD);
        when(sourceA.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.error(new RuntimeException("Bank API unavailable")));
        when(sourceB.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.just(transaction(DataSourceId.MOCK_CARD)));
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var service = new DataSyncService(
                List.of(sourceA, sourceB), saveTransactionPort, categorizationService, meterRegistry);

        // sync should complete (not propagate the error) and count only the successful source
        StepVerifier.create(service.sync(command()))
                .assertNext(result -> assertThat(result.totalTransactionsSynced()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void sync_shouldIncrementErrorCounter_taggedBySource_whenSourceFails() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceA.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.error(new RuntimeException("Bank API unavailable")));

        var service = new DataSyncService(
                List.of(sourceA), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command())).expectNextCount(1).verifyComplete();

        var counter = meterRegistry.find("sync.errors")
                .tag("source", DataSourceId.MOCK_BANK.value())
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void sync_shouldReturnZeroForFailedSource_inTotal() {
        when(sourceA.sourceId()).thenReturn(DataSourceId.MOCK_BANK);
        when(sourceA.fetchTransactions(any(), any(), any()))
                .thenReturn(Flux.error(new RuntimeException("timeout")));

        var service = new DataSyncService(
                List.of(sourceA), saveTransactionPort, categorizationService, meterRegistry);

        StepVerifier.create(service.sync(command()))
                .assertNext(result -> assertThat(result.totalTransactionsSynced()).isZero())
                .verifyComplete();

        verifyNoInteractions(saveTransactionPort);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SyncTransactionsCommand command() {
        return new SyncTransactionsCommand(CUSTOMER_ID, FROM, TO);
    }

    private Transaction transaction(DataSourceId sourceId) {
        return transactionWithDescription(sourceId, "Coffee shop purchase", "Costa Coffee");
    }

    private Transaction transactionWithDescription(DataSourceId sourceId, String description, String merchantName) {
        return new Transaction(
                new TransactionId(UUID.randomUUID()),
                CUSTOMER_ID,
                new AccountId(UUID.randomUUID()),
                Money.of(new BigDecimal("25.00"), "GBP"),
                TransactionType.DEBIT,
                TransactionStatus.COMPLETED,
                description,
                TransactionCategory.OTHER,
                merchantName,
                sourceId,
                Instant.now()
        );
    }
}
