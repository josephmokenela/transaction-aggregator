package io.mokenela.transactionaggregator.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;
import java.util.UUID;

/**
 * Extracts or generates an {@code X-Request-ID} correlation identifier for every
 * HTTP request and propagates it through the reactive pipeline via the Reactor
 * {@link Context}.
 *
 * <p>The filter runs at highest precedence so the ID is available to all downstream
 * filters (including Spring Security). {@link RequestIdMdcAccessor} bridges the
 * Reactor context entry to MDC, making it visible in log lines without any
 * per-operator boilerplate.
 *
 * <p>Clients MAY supply their own {@code X-Request-ID}; if absent, a UUID is
 * generated. Either way, the value is echoed back in the response so callers
 * can correlate logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter implements WebFilter {

    static final String REQUEST_ID_MDC_KEY = "requestId";
    static final String REQUEST_ID_HEADER  = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = Optional.ofNullable(
                        exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER))
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(exchange)
                .contextWrite(Context.of(REQUEST_ID_MDC_KEY, requestId));
    }
}
