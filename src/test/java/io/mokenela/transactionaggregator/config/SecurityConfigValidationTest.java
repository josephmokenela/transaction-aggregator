package io.mokenela.transactionaggregator.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SecurityConfig.validateSecrets().
 *
 * Tests the fail-fast startup validation that prevents weak/default JWT secrets
 * from being used in non-development profiles.
 */
class SecurityConfigValidationTest {

    private static final String STRONG_JWT_SECRET   = "super-strong-jwt-secret-at-least-32-chars!";
    private static final String STRONG_ADMIN_SECRET = "super-strong-admin-secret-at-least-32-chars!";

    private SecurityConfig config;

    @BeforeEach
    void setUp() {
        config = new SecurityConfig();
        // Provide a non-null allowedOrigins so bean wiring doesn't NPE
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of("http://localhost:3000"));
    }

    // Dev / default profile — weak secrets allowed
    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"default", "dev"})
    void validateSecrets_shouldAllowWeakJwtSecret_inDevelopmentProfiles(String profile) {
        setSecrets("local-dev-only-do-not-use-in-docker-or-prod",
                   "local-dev-admin-do-not-use-in-docker-or-prod", profile);

        assertThatCode(() -> config.validateSecrets()).doesNotThrowAnyException();
    }

    @Test
    void validateSecrets_shouldAllowChangeMe_inDefaultProfile() {
        setSecrets("change-me", "change-me", "default");

        assertThatCode(() -> config.validateSecrets()).doesNotThrowAnyException();
    }

    // Docker / prod — weak secrets must be rejected

    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"docker", "prod", "staging"})
    void validateSecrets_shouldFailFast_whenWeakJwtSecretInNonDevProfile(String profile) {
        setSecrets("local-dev-only-do-not-use-in-docker-or-prod", STRONG_ADMIN_SECRET, profile);

        assertThatThrownBy(() -> config.validateSecrets())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Weak JWT secret");
    }

    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"docker", "prod", "staging"})
    void validateSecrets_shouldFailFast_whenChangeMe_jwtSecret_inNonDevProfile(String profile) {
        setSecrets("change-me", STRONG_ADMIN_SECRET, profile);

        assertThatThrownBy(() -> config.validateSecrets())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Weak JWT secret");
    }

    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"docker", "prod", "staging"})
    void validateSecrets_shouldFailFast_whenWeakAdminSecretInNonDevProfile(String profile) {
        setSecrets(STRONG_JWT_SECRET, "local-dev-admin-do-not-use-in-docker-or-prod", profile);

        assertThatThrownBy(() -> config.validateSecrets())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin secret");
    }

    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"docker", "prod", "staging"})
    void validateSecrets_shouldFailFast_whenAdminSecretIsChangeMe_inNonDevProfile(String profile) {
        setSecrets(STRONG_JWT_SECRET, "change-me", profile);

        assertThatThrownBy(() -> config.validateSecrets())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin secret");
    }

    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"docker", "prod", "staging"})
    void validateSecrets_shouldFailFast_whenJwtSecretTooShort_inNonDevProfile(String profile) {
        // Does not contain any weak marker but is under 32 chars
        setSecrets("short-but-unique-secret", STRONG_ADMIN_SECRET, profile);

        assertThatThrownBy(() -> config.validateSecrets())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    @ParameterizedTest(name = "profile={0}")
    @ValueSource(strings = {"docker", "prod", "staging"})
    void validateSecrets_shouldPass_whenBothSecretsAreStrong_inNonDevProfile(String profile) {
        setSecrets(STRONG_JWT_SECRET, STRONG_ADMIN_SECRET, profile);

        assertThatCode(() -> config.validateSecrets()).doesNotThrowAnyException();
    }

    // helpers

    private void setSecrets(String jwtSecret, String adminSecret, String activeProfiles) {
        ReflectionTestUtils.setField(config, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(config, "adminSecret", adminSecret);
        ReflectionTestUtils.setField(config, "activeProfiles", activeProfiles);
    }
}
