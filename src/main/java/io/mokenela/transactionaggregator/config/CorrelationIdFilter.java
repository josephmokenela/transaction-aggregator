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
 * Injects cross-service observability headers on every HTTP request/response.
 *
 * <p><strong>X-Request-ID</strong> — correlation identifier. Clients MAY supply their
 * own value; if absent a UUID is generated. The value is echoed in the response and
 * propagated through the reactive pipeline via Reactor {@link Context} so every log
 * line carries it automatically (see {@link RequestIdMdcAccessor}).
 *
 * <p><strong>X-API-Version</strong> — the current API major version. Stamped on every
 * response so clients and API gateways can identify which contract version served the
 * request without inspecting the URL. Defined in ADR-006.
 *
 * <p>The filter runs at highest precedence so both headers are set before Spring
 * Security and all downstream filters execute.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter implements WebFilter {

    static final String REQUEST_ID_MDC_KEY = "requestId";
    static final String REQUEST_ID_HEADER  = "X-Request-ID";
    static final String API_VERSION_HEADER = "X-API-Version";
    static final String API_VERSION        = "1";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = Optional.ofNullable(
                        exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER))
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.getResponse().getHeaders().set(API_VERSION_HEADER, API_VERSION);

        return chain.filter(exchange)
                .contextWrite(Context.of(REQUEST_ID_MDC_KEY, requestId));
    }
}
