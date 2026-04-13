package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.exception.CustomerNotFoundException;
import io.mokenela.transactionaggregator.domain.model.*;
import io.mokenela.transactionaggregator.domain.port.in.*;
import io.mokenela.transactionaggregator.domain.port.out.LoadCustomerPort;
import io.mokenela.transactionaggregator.domain.port.out.LoadTransactionPort;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerQueryServiceTest {

    @Mock
    LoadCustomerPort loadCustomerPort;

    @Mock
    LoadTransactionPort loadTransactionPort;

    CustomerQueryService service;

    static final CustomerId CUSTOMER_ID = new CustomerId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    static final Customer ALICE = new Customer(CUSTOMER_ID, "Alice", "alice@example.com");

    static final Instant FROM = Instant.parse("2024-01-01T00:00:00Z");
    static final Instant TO   = Instant.parse("2024-01-31T23:59:59Z");

    @BeforeEach
    void setUp() {
        service = new CustomerQueryService(loadCustomerPort, loadTransactionPort, "GBP", 10_000, 5_000);
    }

    // ── listCustomers ─────────────────────────────────────────────────────────

    @Test
    void listCustomers_shouldReturnPagedResponse_delegatingToPort() {
        var pageRequest = new PageRequest(0, 20);
        var expected = new PagedResponse<>(List.of(ALICE), 0L, 20L, 1L, 1L);
        when(loadCustomerPort.loadAll(pageRequest)).thenReturn(Mono.just(expected));

        StepVerifier.create(service.listCustomers(pageRequest))
                .assertNext(result -> {
                    assertThat(result.content()).containsExactly(ALICE);
                    assertThat(result.totalElements()).isEqualTo(1L);
                    assertThat(result.page()).isEqualTo(0L);
                    assertThat(result.size()).isEqualTo(20L);
                    assertThat(result.totalPages()).isEqualTo(1L);
                })
                .verifyComplete();

        verify(loadCustomerPort).loadAll(pageRequest);
    }

    @Test
    void listCustomers_shouldReturnEmptyPage_whenNoCustomersExist() {
        var pageRequest = new PageRequest(0, 20);
        var empty = new PagedResponse<Customer>(List.of(), 0L, 20L, 0L, 0L);
        when(loadCustomerPort.loadAll(pageRequest)).thenReturn(Mono.just(empty));

        StepVerifier.create(service.listCustomers(pageRequest))
                .assertNext(result -> {
                    assertThat(result.content()).isEmpty();
                    assertThat(result.totalElements()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void listCustomers_shouldPassPageOffsetCorrectly_forSubsequentPage() {
        var pageRequest = new PageRequest(2, 10);
        var paged = new PagedResponse<>(List.of(ALICE), 2L, 10L, 21L, 3L);
        when(loadCustomerPort.loadAll(pageRequest)).thenReturn(Mono.just(paged));

        StepVerifier.create(service.listCustomers(pageRequest))
                .assertNext(result -> {
                    assertThat(result.page()).isEqualTo(2L);
                    assertThat(result.totalPages()).isEqualTo(3L);
                })
                .verifyComplete();

        // Verify correct PageRequest was forwarded (offset = page * size = 2 * 10 = 20)
        var captor = org.mockito.ArgumentCaptor.forClass(PageRequest.class);
        verify(loadCustomerPort).loadAll(captor.capture());
        assertThat(captor.getValue().offset()).isEqualTo(20L);
    }

    // ── getCustomerSummary ────────────────────────────────────────────────────

    @Test
    void getCustomerSummary_shouldReturnCorrectTotals_withCreditAndDebitTransactions() {
        var query = new GetCustomerSummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.just(ALICE));
        when(loadTransactionPort.loadByFilter(any(), eq(10_000))).thenReturn(Flux.just(
                transaction(TransactionType.CREDIT, "3500.00"),
                transaction(TransactionType.CREDIT, "500.00"),
                transaction(TransactionType.DEBIT,  "200.00")
        ));

        StepVerifier.create(service.getCustomerSummary(query))
                .assertNext(summary -> {
                    assertThat(summary.totalInflow().amount()).isEqualByComparingTo("4000.00");
                    assertThat(summary.totalOutflow().amount()).isEqualByComparingTo("200.00");
                    assertThat(summary.netPosition().amount()).isEqualByComparingTo("3800.00");
                    assertThat(summary.totalTransactions()).isEqualTo(3L);
                    assertThat(summary.customerId()).isEqualTo(CUSTOMER_ID);
                    assertThat(summary.customerName()).isEqualTo("Alice");
                })
                .verifyComplete();
    }

    @Test
    void getCustomerSummary_shouldReturnZeroTotals_whenNoPeriodTransactions() {
        var query = new GetCustomerSummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.just(ALICE));
        when(loadTransactionPort.loadByFilter(any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(service.getCustomerSummary(query))
                .assertNext(summary -> {
                    assertThat(summary.totalTransactions()).isZero();
                    assertThat(summary.totalInflow().amount()).isEqualByComparingTo("0.00");
                    assertThat(summary.totalOutflow().amount()).isEqualByComparingTo("0.00");
                    assertThat(summary.netPosition().amount()).isEqualByComparingTo("0.00");
                    assertThat(summary.categoryBreakdown()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void getCustomerSummary_shouldEmitCustomerNotFoundException_whenCustomerDoesNotExist() {
        var query = new GetCustomerSummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getCustomerSummary(query))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(CustomerNotFoundException.class);
                    assertThat(((CustomerNotFoundException) ex).customerId()).isEqualTo(CUSTOMER_ID);
                })
                .verify();

        verifyNoInteractions(loadTransactionPort);
    }

    @Test
    void getCustomerSummary_shouldIncludeCategoryBreakdown_withPercentages() {
        var query = new GetCustomerSummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.just(ALICE));

        // 75% SALARY, 25% FOOD — verifies percentage calculation
        when(loadTransactionPort.loadByFilter(any(), anyInt())).thenReturn(Flux.just(
                transactionWithCategory(TransactionType.CREDIT, "75.00", TransactionCategory.SALARY),
                transactionWithCategory(TransactionType.DEBIT,  "25.00", TransactionCategory.FOOD_AND_DINING)
        ));

        StepVerifier.create(service.getCustomerSummary(query))
                .assertNext(summary -> {
                    assertThat(summary.categoryBreakdown()).hasSize(2);
                    var salaryBreakdown = summary.categoryBreakdown().stream()
                            .filter(c -> c.category() == TransactionCategory.SALARY)
                            .findFirst().orElseThrow();
                    assertThat(salaryBreakdown.percentage()).isEqualByComparingTo("75.00");
                })
                .verifyComplete();
    }

    @Test
    void getCustomerSummary_shouldPassMaxTransactionsLimitToPort() {
        var customService = new CustomerQueryService(loadCustomerPort, loadTransactionPort, "GBP", 500, 250);
        var query = new GetCustomerSummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.just(ALICE));
        when(loadTransactionPort.loadByFilter(any(), eq(500))).thenReturn(Flux.empty());

        StepVerifier.create(customService.getCustomerSummary(query)).expectNextCount(1).verifyComplete();

        verify(loadTransactionPort).loadByFilter(any(), eq(500));
    }

    // ── getCategorySummary ────────────────────────────────────────────────────

    @Test
    void getCategorySummary_shouldReturnBreakdown_groupedByCategory() {
        var query = new GetCategorySummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.just(ALICE));
        when(loadTransactionPort.loadByFilter(any(), anyInt())).thenReturn(Flux.just(
                transactionWithCategory(TransactionType.CREDIT, "3500.00", TransactionCategory.SALARY),
                transactionWithCategory(TransactionType.DEBIT,  "50.00", TransactionCategory.FOOD_AND_DINING),
                transactionWithCategory(TransactionType.DEBIT,  "30.00", TransactionCategory.FOOD_AND_DINING)
        ));

        StepVerifier.create(service.getCategorySummary(query))
                .recordWith(java.util.ArrayList::new)
                .thenConsumeWhile(ignored -> true)
                .consumeRecordedWith(summaries -> {
                    assertThat(summaries).hasSize(2);
                    var food = summaries.stream()
                            .filter(s -> s.category() == TransactionCategory.FOOD_AND_DINING)
                            .findFirst().orElseThrow();
                    assertThat(food.transactionCount()).isEqualTo(2);
                    assertThat(food.totalAmount().amount()).isEqualByComparingTo("80.00");
                })
                .verifyComplete();
    }

    @Test
    void getCategorySummary_shouldReturnEmpty_whenNoTransactionsInPeriod() {
        var query = new GetCategorySummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.just(ALICE));
        when(loadTransactionPort.loadByFilter(any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(service.getCategorySummary(query))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void getCategorySummary_shouldEmitCustomerNotFoundException_whenCustomerDoesNotExist() {
        var query = new GetCategorySummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getCategorySummary(query))
                .expectError(CustomerNotFoundException.class)
                .verify();

        verifyNoInteractions(loadTransactionPort);
    }

    @Test
    void getCategorySummary_shouldSortByTotalAmountDescending() {
        var query = new GetCategorySummaryQuery(CUSTOMER_ID, FROM, TO);
        when(loadCustomerPort.loadById(CUSTOMER_ID)).thenReturn(Mono.just(ALICE));
        when(loadTransactionPort.loadByFilter(any(), anyInt())).thenReturn(Flux.just(
                transactionWithCategory(TransactionType.DEBIT, "10.00", TransactionCategory.ENTERTAINMENT),
                transactionWithCategory(TransactionType.CREDIT, "500.00", TransactionCategory.SALARY),
                transactionWithCategory(TransactionType.DEBIT, "100.00", TransactionCategory.SHOPPING)
        ));

        StepVerifier.create(service.getCategorySummary(query))
                .recordWith(java.util.ArrayList::new)
                .thenConsumeWhile(ignored -> true)
                .consumeRecordedWith(summaries -> {
                    // Sorted descending by amount
                    var amounts = summaries.stream()
                            .map(s -> s.totalAmount().amount())
                            .toList();
                    assertThat(amounts).isSortedAccordingTo((a, b) -> b.compareTo(a));
                })
                .verifyComplete();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Transaction transaction(TransactionType type, String amount) {
        return transactionWithCategory(type, amount, TransactionCategory.OTHER);
    }

    private Transaction transactionWithCategory(TransactionType type, String amount,
                                                 TransactionCategory category) {
        return new Transaction(
                TransactionId.generate(),
                CUSTOMER_ID,
                new AccountId(UUID.randomUUID()),
                Money.of(new BigDecimal(amount), "GBP"),
                type,
                TransactionStatus.COMPLETED,
                "Test transaction",
                category,
                null,
                DataSourceId.MANUAL,
                Instant.now()
        );
    }
}
