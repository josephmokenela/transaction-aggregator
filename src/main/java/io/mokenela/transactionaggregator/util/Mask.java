package io.mokenela.transactionaggregator.util;

import java.math.BigDecimal;

/**
 * Central utility for masking personally identifiable information (PII) in log output.
 *
 * <p>Rules:
 * <ul>
 *   <li><b>amount</b> — financial values are masked completely ({@code "***"}).
 *       Even at DEBUG, exact transaction amounts are sensitive in a fintech context.
 *   <li><b>text</b> — free-text fields (descriptions, merchant names, search keywords)
 *       expose only the first character followed by {@code "****"}. This preserves
 *       just enough context for log correlation while preventing full content leakage.
 * </ul>
 *
 * <p>Usage pattern: call these methods directly at the log call site so masking is
 * explicit, visible, and compile-time checked — rather than relying on Logback
 * pattern-based redaction which is brittle and hard to test.
 *
 * <pre>{@code
 * log.debug("Recording amount={} keyword={}", Mask.amount(amount), Mask.text(keyword));
 * }</pre>
 */
public final class Mask {

    private Mask() {}

    /**
     * Masks a financial amount completely. Financial figures must never appear in
     * log files in plaintext.
     *
     * @param amount the value to mask (may be null)
     * @return {@code "***"} always, or {@code "[null]"} if the value is null
     */
    public static String amount(BigDecimal amount) {
        return amount == null ? "[null]" : "***";
    }

    /**
     * Partially masks a free-text field. Shows only the first character so that
     * an engineer can recognise the field is populated during debugging without
     * the full value being captured in logs.
     *
     * @param value the text to mask (may be null or empty)
     * @return {@code "[null]"} / {@code "[blank]"} for absent values;
     *         {@code "****"} for single-character values;
     *         otherwise {@code first_char + "****"}
     */
    public static String text(String value) {
        if (value == null)    return "[null]";
        if (value.isBlank())  return "[blank]";
        if (value.length() == 1) return "****";
        return value.charAt(0) + "****";
    }
}
