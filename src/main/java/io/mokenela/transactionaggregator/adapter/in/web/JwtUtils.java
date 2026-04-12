package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

/**
 * Stateless helpers for extracting auth context from a JWT.
 *
 * <p>Roles are stored in the {@code roles} claim as strings (e.g. {@code "ROLE_ADMIN"}).
 * The {@code sub} claim holds the customer's UUID for ROLE_CUSTOMER tokens,
 * and the literal string {@code "admin"} for ROLE_ADMIN tokens.
 */
final class JwtUtils {

    private JwtUtils() {}

    static boolean isAdmin(Jwt jwt) {
        return roles(jwt).contains("ROLE_ADMIN");
    }

    static CustomerId customerId(Jwt jwt) {
        return CustomerId.of(jwt.getSubject());
    }

    /**
     * Returns the effective customerId for a search/filter request.
     * Customers are always scoped to their own ID; admins may pass any ID (or null for all).
     */
    static String effectiveCustomerId(Jwt jwt, String requestedCustomerId) {
        return isAdmin(jwt) ? requestedCustomerId : jwt.getSubject();
    }

    private static List<String> roles(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());
    }
}
