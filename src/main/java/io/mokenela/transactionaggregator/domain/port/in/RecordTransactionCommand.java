package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.Money;
import io.mokenela.transactionaggregator.domain.model.TransactionType;

public record RecordTransactionCommand(
        AccountId accountId,
        Money amount,
        TransactionType type,
        String description
) {

    public RecordTransactionCommand {
        if (accountId == null) throw new IllegalArgumentException("accountId cannot be null");
        if (amount == null) throw new IllegalArgumentException("amount cannot be null");
        if (type == null) throw new IllegalArgumentException("type cannot be null");
    }
}
