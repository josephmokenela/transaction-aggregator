package io.mokenela.transactionaggregator.application.service;

import io.mokenela.transactionaggregator.domain.model.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCategorizationServiceTest {

    private TransactionCategorizationService service;

    @BeforeEach
    void setUp() {
        service = new TransactionCategorizationService();
    }

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @CsvSource({
            "Monthly salary payment,Employer Ltd,SALARY",
            "Monthly payroll,ACME Corp,SALARY",
            "Wages transfer,,SALARY",
            "Monthly rent payment,Lettings Agency,RENT_AND_MORTGAGE",
            "Mortgage direct debit,,RENT_AND_MORTGAGE",
            "Dinner at Pizza Express,Pizza Express,FOOD_AND_DINING",
            "McDonald's order,McDonald's,FOOD_AND_DINING",
            "Uber Eats delivery,Uber Eats,FOOD_AND_DINING",
            "Uber ride to office,Uber,TRANSPORT",
            "National Rail monthly pass,National Rail,TRANSPORT",
            "Electricity Bill,EnergySupplier,UTILITIES",
            "Broadband Bill,Virgin Media,UTILITIES",
            "Weekly grocery shop,Tesco,SHOPPING",
            "Online purchase,Amazon,SHOPPING",
            "Netflix subscription,Netflix,ENTERTAINMENT",
            "Spotify Premium,Spotify,ENTERTAINMENT",
            "Pharmacy prescription,Boots Pharmacy,HEALTHCARE",
            "Medical centre visit,,HEALTHCARE",
            "Car insurance payment,Aviva Insurance,INSURANCE",
            "Transfer to savings,,TRANSFER",
            "Payment to John,,TRANSFER",
            "University tuition fee,,EDUCATION",
            "Some unknown merchant,XYZ Ltd,OTHER",
    })
    void shouldCategorizeByDescriptionAndMerchant(String description, String merchant,
                                                   TransactionCategory expected) {
        assertThat(service.categorize(description, merchant)).isEqualTo(expected);
    }

    @Test
    void shouldBeCaseInsensitive() {
        assertThat(service.categorize("MONTHLY SALARY", null)).isEqualTo(TransactionCategory.SALARY);
        assertThat(service.categorize("NETFLIX SUBSCRIPTION", "NETFLIX")).isEqualTo(TransactionCategory.ENTERTAINMENT);
    }

    @Test
    void shouldHandleNullDescription() {
        assertThat(service.categorize(null, "Netflix")).isEqualTo(TransactionCategory.ENTERTAINMENT);
    }

    @Test
    void shouldHandleNullMerchantName() {
        assertThat(service.categorize("Monthly salary", null)).isEqualTo(TransactionCategory.SALARY);
    }

    @Test
    void shouldHandleBothNullInputs() {
        assertThat(service.categorize(null, null)).isEqualTo(TransactionCategory.OTHER);
    }

    @Test
    void shouldPreferTransferOverFoodForAmbiguousKeywords() {
        // "payment to" should categorise as TRANSFER, not bleed into other categories
        assertThat(service.categorize("Payment to restaurant friend", null))
                .isEqualTo(TransactionCategory.TRANSFER);
    }
}
