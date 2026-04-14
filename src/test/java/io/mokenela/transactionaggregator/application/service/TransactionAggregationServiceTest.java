package io.mokenela.transactionaggregator.application.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mokenela.transactionaggregator.domain.exception.TransactionNotFoundException;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
import io.mokenela.transactionaggregator.domain.port.out.SaveTransactionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionAggregationServiceTest {

    @Mock
    SaveTransactionPort saveTransactionPort;

    @Mock
    LoadTransactionPort loadTransactionPort;

    // Use the real categorisation service — it has no dependencies and is pure logic
    private final TransactionCategorizationService categorizationService = new TransactionCategorizationService();

    // In-memory registry that satisfies the MeterRegistry dependency without any infrastructure
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private TransactionAggregationService service;

    private static final CustomerId CUSTOMER_ID = new CustomerId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final AccountId ACCOUNT_ID = new AccountId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    @BeforeEach
    void setUp() {
        service = new TransactionAggregationService(saveTransactionPort, loadTransactionPort, categorizationService, meterRegistry);
    }

    // recordTransaction
    @Test
    void recordTransaction_shouldAssignCategory_basedOnDescription() {
        var command = new RecordTransactionCommand(
                CUSTOMER_ID, ACCOUNT_ID,
                Money.of(new BigDecimal("3500.00"), "GBP"),
                TransactionType.CREDIT,
                "Monthly salary payment",
                "Employer Ltd"
        );
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.recordTransaction(command))
                .assertNext(tx -> {
                    assertThat(tx.category()).isEqualTo(TransactionCategory.SALARY);
                    assertThat(tx.dataSourceId()).isEqualTo(DataSourceId.MANUAL);
                    assertThat(tx.customerId()).isEqualTo(CUSTOMER_ID);
                    assertThat(tx.accountId()).isEqualTo(ACCOUNT_ID);
                    assertThat(tx.status()).isEqualTo(TransactionStatus.COMPLETED);
                })
                .verifyComplete();
    }

    @Test
    void recordTransaction_shouldPersistExactlyOnce() {
        var command = new RecordTransactionCommand(
                CUSTOMER_ID, ACCOUNT_ID,
                Money.of(new BigDecimal("50.00"), "GBP"),
                TransactionType.DEBIT,
                "Uber ride", "Uber"
        );
        when(saveTransactionPort.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.recordTransaction(command)).expectNextCount(1).verifyComplete();
        verify(saveTransactionPort, times(1)).save(any());
    }

    // getTransaction
    @Test
    void getTransaction_shouldReturnTransaction_whenFound() {
        var tx = sampleTransaction(TransactionType.CREDIT, "100.00");
        when(loadTransactionPort.loadById(tx.id())).thenReturn(Mono.just(tx));

        StepVerifier.create(service.getTransaction(new GetTransactionQuery(tx.id())))
                .expectNext(tx)
                .verifyComplete();
    }

    @Test
    void getTransaction_shouldEmitTransactionNotFoundException_whenNotFound() {
        var id = TransactionId.generate();
        when(loadTransactionPort.loadById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.getTransaction(new GetTransactionQuery(id)))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(TransactionNotFoundException.class);
                    assertThat(((TransactionNotFoundException) ex).transactionId()).isEqualTo(id);
                })
                .verify();
    }

    // aggregate
    @Test
    void aggregate_shouldReturnEmptySummaries_whenNoTransactionsExist() {
        var from = Instant.parse("2024-01-01T00:00:00Z");
        var to = Instant.parse("2024-01-31T23:59:59Z");
        var query = new AggregateTransactionsQuery(ACCOUNT_ID, AggregationPeriod.MONTHLY, from, to, null);

        when(loadTransactionPort.loadByAccountIdAndPeriod(ACCOUNT_ID, from, to)).thenReturn(Flux.empty());

        StepVerifier.create(service.aggregate(query))
                .assertNext(result -> {
                    assertThat(result.summaries()).isEmpty();
                    assertThat(result.transactions()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void aggregate_shouldGroupTransactionsIntoDailyBuckets() {
        var day1 = Instant.parse("2024-01-15T10:00:00Z");
        var day2 = Instant.parse("2024-01-16T10:00:00Z");
        var from = Instant.parse("2024-01-01T00:00:00Z");
        var to = Instant.parse("2024-01-31T23:59:59Z");

        var tx1 = sampleTransactionAt(TransactionType.CREDIT, "100.00", day1);
        var tx2 = sampleTransactionAt(TransactionType.DEBIT, "40.00", day1);
        var tx3 = sampleTransactionAt(TransactionType.CREDIT, "200.00", day2);

        when(loadTransactionPort.loadByAccountIdAndPeriod(ACCOUNT_ID, from, to))
                .thenReturn(Flux.just(tx1, tx2, tx3));

        var query = new AggregateTransactionsQuery(ACCOUNT_ID, AggregationPeriod.DAILY, from, to, null);

        StepVerifier.create(service.aggregate(query))
                .assertNext(result -> {
                    assertThat(result.summaries()).hasSize(2);

                    var day1Summary = result.summaries().get(0);
                    assertThat(day1Summary.totalCredits().amount()).isEqualByComparingTo("100.00");
                    assertThat(day1Summary.totalDebits().amount()).isEqualByComparingTo("40.00");
                    assertThat(day1Summary.netAmount().amount()).isEqualByComparingTo("60.00");
                    assertThat(day1Summary.transactionCount()).isEqualTo(2);

                    var day2Summary = result.summaries().get(1);
                    assertThat(day2Summary.totalCredits().amount()).isEqualByComparingTo("200.00");
                    assertThat(day2Summary.totalDebits().amount()).isEqualByComparingTo("0.00");
                    assertThat(day2Summary.netAmount().amount()).isEqualByComparingTo("200.00");
                })
                .verifyComplete();
    }

    @Test
    void aggregate_shouldGroupTransactionsIntoMonthlyBuckets() {
        var jan = Instant.parse("2024-01-15T10:00:00Z");
        var feb = Instant.parse("2024-02-10T10:00:00Z");
        var from = Instant.parse("2024-01-01T00:00:00Z");
        var to = Instant.parse("2024-02-28T23:59:59Z");

        when(loadTransactionPort.loadByAccountIdAndPeriod(ACCOUNT_ID, from, to)).thenReturn(
                Flux.just(sampleTransactionAt(TransactionType.CREDIT, "3500.00", jan),
                          sampleTransactionAt(TransactionType.CREDIT, "3500.00", feb)));

        var query = new AggregateTransactionsQuery(ACCOUNT_ID, AggregationPeriod.MONTHLY, from, to, null);

        StepVerifier.create(service.aggregate(query))
                .assertNext(result -> assertThat(result.summaries()).hasSize(2))
                .verifyComplete();
    }

    // search
    @Test
    void search_shouldRespectLimit() {
        var filter = TransactionFilter.builder().customerId(CUSTOMER_ID).build();
        var query = new SearchTransactionsQuery(filter, 2);

        when(loadTransactionPort.loadByFilter(filter, 2)).thenReturn(Flux.just(
                sampleTransaction(TransactionType.CREDIT, "10.00"),
                sampleTransaction(TransactionType.DEBIT, "20.00")
        ));

        StepVerifier.create(service.search(query))
                .expectNextCount(2)
                .verifyComplete();
    }

    // helpers
    private Transaction sampleTransaction(TransactionType type, String amount) {
        return sampleTransactionAt(type, amount, Instant.now());
    }

    private Transaction sampleTransactionAt(TransactionType type, String amount, Instant occurredAt) {
        return new Transaction(
                TransactionId.generate(),
                CUSTOMER_ID,
                ACCOUNT_ID,
                Money.of(new BigDecimal(amount), "GBP"),
                type,
                TransactionStatus.COMPLETED,
                "Test transaction",
                TransactionCategory.OTHER,
                null,
                DataSourceId.MANUAL,
                occurredAt
        );
    }
}
