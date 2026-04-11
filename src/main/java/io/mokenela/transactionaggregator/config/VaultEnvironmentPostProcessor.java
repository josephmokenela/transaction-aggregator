package io.mokenela.transactionaggregator.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches secrets from HashiCorp Vault's KV v2 HTTP API at startup and exposes
 * them as a {@code vault} {@link MapPropertySource} in the Spring Environment.
 *
 * <p>This replaces Spring Cloud Vault, which is not yet compatible with Spring
 * Boot 4.x. The implementation intentionally has no third-party dependencies —
 * it uses only {@link java.net.http.HttpClient} (available since Java 11).</p>
 *
 * <p>Configuration (environment variables or Spring properties):</p>
 * <ul>
 *   <li>{@code VAULT_ENABLED} — set to {@code true} to activate (default: {@code false})</li>
 *   <li>{@code VAULT_URI}     — Vault base URL (default: {@code http://localhost:8200})</li>
 *   <li>{@code VAULT_TOKEN}   — Vault root/service token (default: {@code root})</li>
 *   <li>{@code VAULT_PATH}    — KV v2 secret path (default: {@code transaction-aggregator})</li>
 * </ul>
 *
 * <p>Secrets returned by Vault are added to the environment prefixed with {@code vault.},
 * so a Vault key {@code db.password} becomes available as {@code ${vault.db.password}}.</p>
 */
public class VaultEnvironmentPostProcessor implements EnvironmentPostProcessor {

    // Minimal JSON value extractor — avoids pulling in Jackson before the context starts.
    // Matches: "key":"value" (handles escaped quotes inside the value)
    private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = Boolean.parseBoolean(
                environment.getProperty("VAULT_ENABLED", environment.getProperty("vault.enabled", "false")));
        if (!enabled) {
            return;
        }

        String uri   = environment.getProperty("VAULT_URI",   "http://localhost:8200");
        String token = environment.getProperty("VAULT_TOKEN", "root");
        String path  = environment.getProperty("VAULT_PATH",  "transaction-aggregator");

        try {
            String body = fetchSecrets(uri, token, path);
            Map<String, Object> secrets = parseDataBlock(body);
            if (!secrets.isEmpty()) {
                // Prefix every key with "vault." so existing ${vault.db.password:...}
                // placeholders resolve without any change to application.yaml.
                Map<String, Object> prefixed = new HashMap<>();
                secrets.forEach((k, v) -> prefixed.put("vault." + k, v));
                environment.getPropertySources()
                        .addFirst(new MapPropertySource("vault", prefixed));
            }
        } catch (Exception ex) {
            // Non-fatal: log a warning and fall through to the fallback values
            // defined in application.yaml (${vault.db.password:${DB_PASSWORD:postgres}}).
            System.err.println("[VaultEnvironmentPostProcessor] Could not load secrets from Vault at "
                    + uri + ": " + ex.getMessage());
        }
    }

    private String fetchSecrets(String baseUri, String token, String path) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "/v1/secret/data/" + path))
                .header("X-Vault-Token", token)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Vault returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Extracts the {@code data.data} block from a KV v2 JSON response and returns
     * a flat map of key→value pairs. Uses regex rather than an object mapper so
     * this class has zero extra dependencies and works before the application
     * context (and Jackson) are initialised.
     */
    private Map<String, Object> parseDataBlock(String json) {
        Map<String, Object> result = new HashMap<>();
        // KV v2 wraps secrets: { "data": { "data": { "key": "value" } } }
        // Find the inner "data" block by locating the second occurrence of "data":{
        int firstData  = json.indexOf("\"data\"");
        int secondData = firstData >= 0 ? json.indexOf("\"data\"", firstData + 1) : -1;
        if (secondData < 0) return result;

        int braceStart = json.indexOf('{', secondData);
        int braceEnd   = findMatchingBrace(json, braceStart);
        if (braceStart < 0 || braceEnd < 0) return result;

        String dataBlock = json.substring(braceStart, braceEnd + 1);
        Matcher m = JSON_STRING.matcher(dataBlock);
        while (m.find()) {
            result.put(m.group(1), m.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return result;
    }

    private int findMatchingBrace(String s, int open) {
        if (open < 0 || open >= s.length()) return -1;
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') { if (--depth == 0) return i; }
        }
        return -1;
    }
}
