package io.mokenela.transactionaggregator.domain.port.in;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestTest {

    @Test
    void offset_shouldBeZero_forFirstPage() {
        assertThat(new PageRequest(0, 20).offset()).isZero();
    }

    @Test
    void offset_shouldCalculateCorrectly_forSecondPage() {
        assertThat(new PageRequest(1, 20).offset()).isEqualTo(20L);
    }

    @Test
    void offset_shouldCalculateCorrectly_forArbitraryPage() {
        assertThat(new PageRequest(5, 10).offset()).isEqualTo(50L);
    }

    @Test
    void offset_shouldHandleLargePageNumbers_withoutOverflow() {
        // page=1_000_000, size=100 → offset=100_000_000 — stays within long range
        assertThat(new PageRequest(1_000_000, 100).offset()).isEqualTo(100_000_000L);
    }
}
