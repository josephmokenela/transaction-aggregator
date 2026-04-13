package io.mokenela.transactionaggregator.domain.port.in;

import io.mokenela.transactionaggregator.domain.model.AccountId;
import io.mokenela.transactionaggregator.domain.model.CustomerId;
import io.mokenela.transactionaggregator.domain.model.Money;
import io.mokenela.transactionaggregator.domain.model.TransactionType;
import io.mokenela.transactionaggregator.util.Mask;

public record RecordTransactionCommand(
        CustomerId customerId,
        AccountId accountId,
        Money amount,
        TransactionType type,
        String description,
        String merchantName   // nullable
) {

    public RecordTransactionCommand {
        if (customerId == null) throw new IllegalArgumentException("customerId cannot be null");
        if (accountId == null) throw new IllegalArgumentException("accountId cannot be null");
        if (amount == null) throw new IllegalArgumentException("amount cannot be null");
        if (type == null) throw new IllegalArgumentException("type cannot be null");
    }

    @Override
    public String toString() {
        return "RecordTransactionCommand[customerId=" + customerId +
               ", accountId=" + accountId +
               ", amount=" + Mask.amount(amount.amount()) +
               ", type=" + type +
               ", description=" + Mask.text(description) +
               ", merchantName=" + Mask.text(merchantName) + "]";
    }
}
