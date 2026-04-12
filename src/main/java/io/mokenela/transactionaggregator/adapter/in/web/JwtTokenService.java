package io.mokenela.transactionaggregator.adapter.in.web;

import io.mokenela.transactionaggregator.domain.model.CustomerId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final long expiryHours;

    JwtTokenService(JwtEncoder jwtEncoder,
                    @Value("${app.security.jwt.expiry-hours:24}") long expiryHours) {
        this.jwtEncoder = jwtEncoder;
        this.expiryHours = expiryHours;
    }

    TokenResponse issueCustomerToken(CustomerId customerId) {
        return issue(customerId.value().toString(), List.of("ROLE_CUSTOMER"));
    }

    TokenResponse issueAdminToken() {
        return issue("admin", List.of("ROLE_ADMIN", "ROLE_CUSTOMER"));
    }

    private TokenResponse issue(String subject, List<String> roles) {
        var now = Instant.now();
        var expiresAt = now.plus(expiryHours, ChronoUnit.HOURS);
        var claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        var token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();
        return new TokenResponse(token, "Bearer", expiryHours * 3600);
    }
}
