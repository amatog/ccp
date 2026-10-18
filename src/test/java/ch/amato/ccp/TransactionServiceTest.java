package ch.amato.ccp;

import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.repository.DemoDataRepository;
import ch.amato.ccp.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionServiceTest {

    private final TransactionService service = new TransactionService(new DemoDataRepository());

    @Test
    void transactionsAreSortedNewestFirst() {
        List<Transaction> txs = service.transactionsFor("anna");
        assertThat(txs).isNotEmpty();
        for (int i = 1; i < txs.size(); i++) {
            assertThat(txs.get(i - 1).date()).isAfterOrEqualTo(txs.get(i).date());
        }
    }

    @Test
    void balanceSubtractsDebitsAndAddsCredits() {
        // Marco: -56.20 -420.00 +80.00 -92.70 = -488.90
        BigDecimal balance = service.balance(service.transactionsFor("marco"));
        assertThat(balance).isEqualByComparingTo("-488.90");
    }

    @Test
    void unknownCustomerHasNoTransactions() {
        assertThat(service.transactionsFor("niemand")).isEmpty();
    }
    @ParameterizedTest
    @CsvSource({
            "anna, 2026-10-04, 2026-10-09, A-1004 A-1003 A-1002, -130.60",
            "anna, 2026-10-06, 2026-10-06, A-1003, 49.90",
            "anna, 2026-10-10, , A-1006 A-1005, -414.00",
            "anna, , 2026-10-05, A-1002 A-1001, -146.35",
            "marco, 2026-10-01, 2026-10-05, M-2002 M-2001, -476.20",
            "marco, 2026-10-01, 2026-10-31, M-2004 M-2003 M-2002 M-2001, -488.90",
            "anna, , , A-1006 A-1005 A-1004 A-1003 A-1002 A-1001, -628.95"
    })
    void filtersInclusiveDatesInDescendingOrderForCustomer(String username, String from, String to,
                                                          String ids, String sum) {
        List<Transaction> transactions = service.transactionsFor(username,
                from == null ? null : LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
        assertThat(transactions).extracting(Transaction::id).containsExactly(ids.split(" "));
        assertThat(service.balance(transactions)).isEqualByComparingTo(sum);
    }

    @Test
    void periodWithoutMatchesIsEmpty() {
        assertThat(service.transactionsFor("anna", LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 20))).isEmpty();
    }
}
