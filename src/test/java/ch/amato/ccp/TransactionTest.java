package ch.amato.ccp;

import ch.amato.ccp.model.Transaction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    @ParameterizedTest
    @CsvSource({
            "500.00, DEBIT, false",
            "500.01, DEBIT, true",
            "500.05, DEBIT, true",
            "500, DEBIT, false",
            "12.40, DEBIT, false",
            "1200.00, DEBIT, true",
            "1000.00, CREDIT, false"
    })
    void highAmountRequiresDebitStrictlyAboveThreshold(String amount, Transaction.Type type, boolean expected) {
        var transaction = new Transaction("TEST-1", LocalDate.of(2026, 10, 1),
                "Demo-Händler", "Shopping", new BigDecimal(amount), "CHF", type);

        assertThat(transaction.isHighAmount()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "Lebensmittel, lebensmittel",
            "Reisen, reisen",
            "Shopping, shopping",
            "Gastronomie, gastronomie",
            "Elektronik, elektronik",
            "Telekom, telekom",
            "Mobilität, mobilitaet",
            "Unbekannte Kategorie, andere"
    })
    void categoryKeyPreservesTextAndIsIndependentOfTransactionType(String category, String key) {
        for (Transaction.Type type : Transaction.Type.values()) {
            var transaction = new Transaction("TEST-1", LocalDate.of(2026, 10, 1),
                    "Demo-Händler", category, BigDecimal.ONE, "CHF", type);

            assertThat(transaction.categoryKey()).isEqualTo(key);
            assertThat(transaction.category()).isEqualTo(category);
        }
    }
}
