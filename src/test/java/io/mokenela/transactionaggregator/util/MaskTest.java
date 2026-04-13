package io.mokenela.transactionaggregator.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaskTest {

    // ── Mask.amount ───────────────────────────────────────────────────────────

    @Test
    void amount_shouldReturnStars_forAnyNonNullAmount() {
        assertThat(Mask.amount(new BigDecimal("3500.00"))).isEqualTo("***");
        assertThat(Mask.amount(new BigDecimal("0.01"))).isEqualTo("***");
        assertThat(Mask.amount(BigDecimal.ZERO)).isEqualTo("***");
        assertThat(Mask.amount(new BigDecimal("-99.99"))).isEqualTo("***");
    }

    @Test
    void amount_shouldReturnNullPlaceholder_forNullAmount() {
        assertThat(Mask.amount(null)).isEqualTo("[null]");
    }

    @Test
    void amount_shouldNeverRevealTheActualValue() {
        var amount = new BigDecimal("1234567.89");
        assertThat(Mask.amount(amount)).doesNotContain("1234567");
    }

    // ── Mask.text ─────────────────────────────────────────────────────────────

    @Test
    void text_shouldReturnNullPlaceholder_forNullInput() {
        assertThat(Mask.text(null)).isEqualTo("[null]");
    }

    @Test
    void text_shouldReturnBlankPlaceholder_forEmptyString() {
        assertThat(Mask.text("")).isEqualTo("[blank]");
    }

    @Test
    void text_shouldReturnBlankPlaceholder_forWhitespaceOnlyString() {
        assertThat(Mask.text("   ")).isEqualTo("[blank]");
        assertThat(Mask.text("\t")).isEqualTo("[blank]");
    }

    @Test
    void text_shouldReturnFullMask_forSingleCharacterInput() {
        assertThat(Mask.text("A")).isEqualTo("****");
    }

    @Test
    void text_shouldShowOnlyFirstChar_forMultiCharacterInput() {
        String masked = Mask.text("Monthly Salary");
        assertThat(masked).startsWith("M");
        assertThat(masked).endsWith("****");
        assertThat(masked).doesNotContain("onthly Salary");
    }

    @Test
    void text_shouldMaskMerchantName() {
        String masked = Mask.text("Costa Coffee");
        assertThat(masked).isEqualTo("C****");
    }

    @Test
    void text_shouldMaskDescription() {
        String masked = Mask.text("Coffee shop purchase at Paddington");
        assertThat(masked).isEqualTo("C****");
        assertThat(masked).doesNotContain("Paddington");
    }

    @Test
    void text_shouldNeverRevealMoreThanFirstChar_forAnyInput() {
        var inputs = new String[]{
                "John Smith",
                "john.smith@example.com",
                "07700900000",
                "Monthly salary",
                "Constraint violation"
        };
        for (String input : inputs) {
            String masked = Mask.text(input);
            assertThat(masked.length()).isLessThanOrEqualTo(5); // max: 1 char + "****"
            assertThat(masked).doesNotContain(input.substring(1));
        }
    }
}
