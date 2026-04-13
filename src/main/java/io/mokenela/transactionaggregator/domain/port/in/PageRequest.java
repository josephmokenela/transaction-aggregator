package io.mokenela.transactionaggregator.domain.port.in;

/**
 * Pagination parameters passed from the adapter layer into use cases.
 * Kept deliberately simple — page is zero-based, size is the number of items per page.
 */
public record PageRequest(int page, int size) {

    public long offset() {
        return (long) page * size;
    }
}
