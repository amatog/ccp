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
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionServiceTest {

    private final TransactionService service = new TransactionService(new DemoDataRepository());

    @ParameterizedTest
    @CsvSource({
            "anna, 142 Belastungen, 13 Gutschriften",
            "marco, 130 Belastungen, 16 Gutschriften",
            "lena, 146 Belastungen, 16 Gutschriften"
    })
    void countsOnlyTransactionsOfEachTypeForCustomer(String username, String debits, String credits) {
        List<Transaction> transactions = service.transactionsFor(username);
        assertThat(service.transactionCountLabel(transactions, Transaction.Type.DEBIT)).isEqualTo(debits);
        assertThat(service.transactionCountLabel(transactions, Transaction.Type.CREDIT)).isEqualTo(credits);
    }

    @ParameterizedTest
    @CsvSource({
            "DEBIT, 0, 0 Belastungen",
            "DEBIT, 1, 1 Belastung",
            "DEBIT, 2, 2 Belastungen",
            "CREDIT, 0, 0 Gutschriften",
            "CREDIT, 1, 1 Gutschrift",
            "CREDIT, 2, 2 Gutschriften"
    })
    void countLabelsUseSingularOnlyForOne(Transaction.Type type, int count, String expected) {
        Transaction transaction = new Transaction("test", LocalDate.of(2026, 10, 6), "Testhändler",
                "Test", BigDecimal.ONE, "CHF", type);
        List<Transaction> transactions = Collections.nCopies(count, transaction);
        assertThat(service.transactionCountLabel(transactions, type)).isEqualTo(expected);

        List<Transaction> mixedTransactions = new ArrayList<>(transactions);
        mixedTransactions.add(new Transaction("other", transaction.date(), "Testhändler", "Test",
                BigDecimal.ONE, "CHF", type == Transaction.Type.DEBIT
                ? Transaction.Type.CREDIT : Transaction.Type.DEBIT));
        assertThat(service.transactionCountLabel(mixedTransactions, type)).isEqualTo(expected);
    }

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
        // Marco Oktober 2026: -56.20 -420.00 +80.00 -92.70 = -488.90
        BigDecimal october = service.balance(service.transactionsFor("marco",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertThat(october).isEqualByComparingTo("-488.90");
        assertThat(service.balance(service.transactionsFor("marco"))).isEqualByComparingTo("-1040.68");
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
            "anna, 2026-10-01, , A-1006 A-1005 A-1004 A-1003 A-1002 A-1001, -628.95"
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

    @ParameterizedTest
    @CsvSource({
            "anna, 155, A-1006, A-0001, -1382.07",
            "marco, 146, M-2004, M-0001, -1040.68",
            "lena, 162, L-3005, L-0001, -2595.70"
    })
    void demoDataCoversOneYearFromOctober2025(String username, int count, String newest, String oldest,
                                              String balance) {
        List<Transaction> txs = service.transactionsFor(username);
        assertThat(txs).hasSize(count);
        assertThat(txs.get(0).id()).isEqualTo(newest);
        assertThat(txs.get(txs.size() - 1).id()).isEqualTo(oldest);
        assertThat(txs).allSatisfy(tx -> assertThat(tx.date())
                .isBetween(LocalDate.of(2025, 10, 20), LocalDate.of(2026, 10, 20)));
        // Jeder Monat von Oktober 2025 bis Oktober 2026 enthält Buchungen.
        assertThat(txs.stream().map(tx -> tx.date().withDayOfMonth(1)).distinct()).hasSize(13);
        assertThat(service.balance(txs)).isEqualByComparingTo(balance);
    }

    @Test
    void historicalDebitsStayBelowHighAmountThreshold() {
        // Nur die bekannten Oktober-Fälle von Lena (L-3002, L-3004) gelten als hohe Belastung.
        assertThat(List.of("anna", "marco", "lena").stream()
                .flatMap(user -> service.transactionsFor(user).stream())
                .filter(Transaction::isHighAmount)
                .map(Transaction::id))
                .containsExactlyInAnyOrder("L-3002", "L-3004");
    }
}
