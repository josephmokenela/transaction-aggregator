package io.mokenela.transactionaggregator.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    // ── Response header ───────────────────────────────────────────────────────

    @Test
    void filter_shouldEchoClientSuppliedRequestId_inResponseHeader() {
        var exchange = exchangeWithHeader("X-Request-ID", "client-provided-id");

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-ID"))
                .isEqualTo("client-provided-id");
    }

    @Test
    void filter_shouldGenerateRequestId_andSetResponseHeader_whenHeaderAbsent() {
        var exchange = exchangeWithoutHeader();

        filter.filter(exchange, chain()).block();

        String responseId = exchange.getResponse().getHeaders().getFirst("X-Request-ID");
        assertThat(responseId).isNotBlank();
        // generated IDs are UUIDs — validate the format
        assertThat(responseId).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void filter_shouldGenerateRequestId_andSetResponseHeader_whenHeaderIsBlank() {
        var exchange = exchangeWithHeader("X-Request-ID", "   ");

        filter.filter(exchange, chain()).block();

        String responseId = exchange.getResponse().getHeaders().getFirst("X-Request-ID");
        assertThat(responseId).isNotBlank();
        assertThat(responseId).isNotEqualTo("   ");
    }

    // ── Reactor context ───────────────────────────────────────────────────────

    @Test
    void filter_shouldWriteClientRequestId_toReactorContext() {
        var exchange = exchangeWithHeader("X-Request-ID", "client-provided-id");
        var capturedId = new AtomicReference<String>();

        WebFilterChain capturingChain = ex ->
                Mono.deferContextual(ctx -> {
                    capturedId.set(ctx.getOrDefault(CorrelationIdFilter.REQUEST_ID_MDC_KEY, null));
                    return Mono.empty();
                });

        filter.filter(exchange, capturingChain).block();

        assertThat(capturedId.get()).isEqualTo("client-provided-id");
    }

    @Test
    void filter_shouldWriteGeneratedRequestId_toReactorContext_whenHeaderAbsent() {
        var exchange = exchangeWithoutHeader();
        var capturedId = new AtomicReference<String>();

        WebFilterChain capturingChain = ex ->
                Mono.deferContextual(ctx -> {
                    capturedId.set(ctx.getOrDefault(CorrelationIdFilter.REQUEST_ID_MDC_KEY, null));
                    return Mono.empty();
                });

        filter.filter(exchange, capturingChain).block();

        assertThat(capturedId.get()).isNotBlank();
        assertThat(capturedId.get()).isEqualTo(
                exchange.getResponse().getHeaders().getFirst("X-Request-ID"));
    }

    @Test
    void filter_shouldGenerateDistinctRequestIds_forDifferentRequests() {
        var exchange1 = exchangeWithoutHeader();
        var exchange2 = exchangeWithoutHeader();

        filter.filter(exchange1, chain()).block();
        filter.filter(exchange2, chain()).block();

        String id1 = exchange1.getResponse().getHeaders().getFirst("X-Request-ID");
        String id2 = exchange2.getResponse().getHeaders().getFirst("X-Request-ID");
        assertThat(id1).isNotBlank();
        assertThat(id2).isNotBlank();
        assertThat(id1).isNotEqualTo(id2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockServerWebExchange exchangeWithHeader(String name, String value) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").header(name, value).build());
    }

    private MockServerWebExchange exchangeWithoutHeader() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());
    }

    private WebFilterChain chain() {
        return exchange -> Mono.empty();
    }
}
