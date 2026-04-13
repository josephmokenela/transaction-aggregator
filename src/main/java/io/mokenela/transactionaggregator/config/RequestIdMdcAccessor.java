package io.mokenela.transactionaggregator.config;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Bridges the {@code requestId} Reactor context entry to the SLF4J {@link MDC}.
 *
 * <p>Spring Boot 3.2+ auto-configuration ({@code MicrometerTracingAutoConfiguration})
 * collects all {@link ThreadLocalAccessor} beans and registers them with the global
 * {@link io.micrometer.context.ContextRegistry}. Combined with
 * {@code Hooks.enableAutomaticContextPropagation()} (also set up automatically),
 * Reactor will restore MDC state from the context on each operator boundary,
 * making the correlation ID visible in log lines across thread hops without any
 * per-call boilerplate.
 *
 * @see CorrelationIdFilter
 */
@Component
class RequestIdMdcAccessor implements ThreadLocalAccessor<String> {

    @Override
    public Object key() {
        return CorrelationIdFilter.REQUEST_ID_MDC_KEY;
    }

    @Override
    public String getValue() {
        return MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY);
    }

    @Override
    public void setValue(String value) {
        MDC.put(CorrelationIdFilter.REQUEST_ID_MDC_KEY, value);
    }

    /** Called by Reactor when leaving the scope — removes the MDC key. */
    @Override
    public void setValue() {
        MDC.remove(CorrelationIdFilter.REQUEST_ID_MDC_KEY);
    }
}
