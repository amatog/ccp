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
