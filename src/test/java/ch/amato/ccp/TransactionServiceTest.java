package ch.amato.ccp;

import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.repository.DemoDataRepository;
import ch.amato.ccp.service.TransactionService;
import org.junit.jupiter.api.Test;

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
}
