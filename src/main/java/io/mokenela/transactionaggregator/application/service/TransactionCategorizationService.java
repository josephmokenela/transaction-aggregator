package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Rule-based transaction categoriser.
 *
 * <p>Rules are evaluated <em>in declaration order</em> — the first matching category wins.
 * Priority ordering is intentional:</p>
 * <ol>
 *   <li>SALARY / RENT before more generic credit/debit rules</li>
 *   <li>TRANSFER before FOOD/TRANSPORT — "payment to Uber Eats" is a transfer, not a food purchase</li>
 *   <li>FOOD_AND_DINING before TRANSPORT — "Uber Eats" should not match the "uber" transport keyword</li>
 * </ol>
 *
 * <p><strong>Important keyword hygiene:</strong> short keywords (≤ 4 chars) must be validated to not
 * be substrings of unrelated common words. For example, "tfl" is a substring of "netflix"
 * and is therefore excluded from transport keywords.</p>
 *
 * <p>A production system would use a configurable rules engine or an ML classifier.</p>
 */
@Service
public class TransactionCategorizationService {

    /**
     * Ordered list of (category → keywords) rules.
     * {@code List.of} preserves insertion order, making priority explicit and deterministic.
     */
    private static final List<Map.Entry<TransactionCategory, List<String>>> ORDERED_RULES = List.of(
            Map.entry(TransactionCategory.SALARY,
                    List.of("salary", "payroll", "wages", "monthly pay", "net pay", "payslip", "income credit")),

            Map.entry(TransactionCategory.RENT_AND_MORTGAGE,
                    List.of("rent payment", "monthly rent", "mortgage", "landlord", "letting agent", "lease payment")),

            // TRANSFER before FOOD/TRANSPORT to prevent "payment to Uber Eats" being miscategorised as FOOD
            Map.entry(TransactionCategory.TRANSFER,
                    List.of("transfer to", "payment to", "sent to", "wire transfer", "remittance", "bank transfer", "p2p")),

            // FOOD before TRANSPORT to prevent "Uber Eats" matching the "uber" transport keyword
            Map.entry(TransactionCategory.FOOD_AND_DINING,
                    List.of("restaurant", "cafe", "coffee", "pizza", "mcdonald", "kfc", "burger",
                            "sushi", "uber eats", "deliveroo", "just eat", "doordash", "grubhub",
                            "takeaway", "bakery", "deli", "bistro", "grill", "diner")),

            Map.entry(TransactionCategory.TRANSPORT,
                    // "tfl" excluded — it is a substring of "netflix" causing false positives
                    List.of("uber", "lyft", "taxi", "train", "rail pass", "bus ticket", "metro",
                            "tube station", "petrol", "fuel", "parking", "toll", "airways",
                            "airline", "flight", "transport for london", "national rail")),

            Map.entry(TransactionCategory.UTILITIES,
                    List.of("electricity", "water bill", "gas bill", "internet bill", "broadband",
                            "phone bill", "mobile bill", "british gas", "bt broadband", "virgin media", "sky broadband")),

            Map.entry(TransactionCategory.SHOPPING,
                    List.of("amazon", "ebay", "zara", "h&m", "primark", "marks & spencer",
                            "tesco", "sainsbury", "asda", "walmart", "target", "ikea", "argos")),

            Map.entry(TransactionCategory.ENTERTAINMENT,
                    List.of("netflix", "spotify", "apple music", "disney+", "hbo", "cinema",
                            "theatre", "concert", "playstation", "xbox", "steam", "twitch", "youtube premium")),

            Map.entry(TransactionCategory.HEALTHCARE,
                    List.of("pharmacy", "hospital", "doctor", "dentist", "optician", "clinic",
                            "nhs", "medical centre", "prescription")),

            Map.entry(TransactionCategory.INSURANCE,
                    List.of("insurance", "assurance", "aviva", "axa", "allianz", "zurich")),

            Map.entry(TransactionCategory.EDUCATION,
                    List.of("university", "college", "school fees", "tuition", "coursera",
                            "udemy", "linkedin learning"))
    );

    /**
     * Categorises a transaction by matching its description and merchant name against
     * the ordered keyword rules. Returns {@link TransactionCategory#OTHER} when no rule matches.
     */
    public TransactionCategory categorize(String description, String merchantName) {
        String text = normalize(description) + " " + normalize(merchantName);

        for (var rule : ORDERED_RULES) {
            for (String keyword : rule.getValue()) {
                if (text.contains(keyword)) {
                    return rule.getKey();
                }
            }
        }
        return TransactionCategory.OTHER;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }
}
