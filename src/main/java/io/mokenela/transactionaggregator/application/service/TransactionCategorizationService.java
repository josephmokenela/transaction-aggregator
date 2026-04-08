package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TransactionCategorizationService {

    private static final Map<TransactionCategory, List<String>> RULES = Map.ofEntries(
            Map.entry(TransactionCategory.SALARY,
                    List.of("salary", "payroll", "wage", "wages", "income", "pay ")),
            Map.entry(TransactionCategory.RENT_AND_MORTGAGE,
                    List.of("rent", "mortgage", "landlord", "letting", "lease")),
            Map.entry(TransactionCategory.FOOD_AND_DINING,
                    List.of("restaurant", "cafe", "coffee", "pizza", "mcdonald", "kfc", "burger", "sushi",
                            "uber eats", "deliveroo", "just eat", "doordash", "grubhub", "takeaway", "bakery",
                            "deli", "bistro", "grill", "diner", "food")),
            Map.entry(TransactionCategory.TRANSPORT,
                    List.of("uber", "lyft", "taxi", "train", "rail", "bus", "metro", "tube", "petrol",
                            "fuel", "parking", "toll", "airways", "airline", "flight", "tfl", "national rail")),
            Map.entry(TransactionCategory.UTILITIES,
                    List.of("electricity", "water bill", "gas bill", "internet", "broadband", "phone bill",
                            "mobile bill", "british gas", "bt ", "virgin media", "sky broadband", "utility")),
            Map.entry(TransactionCategory.SHOPPING,
                    List.of("amazon", "ebay", "zara", "h&m", "primark", "marks & spencer", "tesco",
                            "sainsbury", "asda", "walmart", "target", "ikea", "john lewis", "argos")),
            Map.entry(TransactionCategory.ENTERTAINMENT,
                    List.of("netflix", "spotify", "apple music", "disney+", "hbo", "cinema", "theatre",
                            "concert", "playstation", "xbox", "steam", "twitch", "youtube premium")),
            Map.entry(TransactionCategory.HEALTHCARE,
                    List.of("pharmacy", "hospital", "doctor", "dentist", "optician", "clinic", "nhs",
                            "medical", "prescription", "health insurance")),
            Map.entry(TransactionCategory.INSURANCE,
                    List.of("insurance", "assurance", "aviva", "axa", "allianz", "zurich", "policy")),
            Map.entry(TransactionCategory.EDUCATION,
                    List.of("university", "college", "school", "tuition", "coursera", "udemy", "linkedin learning",
                            "education", "training")),
            Map.entry(TransactionCategory.TRANSFER,
                    List.of("transfer to", "payment to", "wire transfer", "remittance", "bank transfer",
                            "sent to", "p2p"))
    );

    public TransactionCategory categorize(String description, String merchantName) {
        String text = normalize(description) + " " + normalize(merchantName);

        for (var entry : RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return TransactionCategory.OTHER;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }
}
