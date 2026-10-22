package ch.amato.ccp.service;

import ch.amato.ccp.model.Customer;
import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.repository.DemoDataRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TransactionService {

    private static final List<String> CATEGORIES = List.of(
            "Elektronik", "Gastronomie", "Lebensmittel", "Mobilität",
            "Reisen", "Shopping", "Telekom", "Zahlung");

    public List<String> categories() {
        return CATEGORIES;
    }

    private final DemoDataRepository repository;

    public TransactionService(DemoDataRepository repository) {
        this.repository = repository;
    }

    public Customer customer(String username) {
        return repository.findCustomer(username)
                .orElseThrow(() -> new NoSuchElementException("Unbekannter Kunde: " + username));
    }

    /** Transaktionen eines Kunden, neueste zuerst. */
    public List<Transaction> transactionsFor(String username) {
        return transactionsFor(username, null, null);
    }

    /** Transaktionen innerhalb der optionalen, inklusiven Datumsgrenzen. */
    public List<Transaction> transactionsFor(String username, LocalDate from, LocalDate to) {
        return transactionsFor(username, from, to, null);
    }

    /** Transaktionen mit optionaler Kategorie und inklusiven Datumsgrenzen. */
    public List<Transaction> transactionsFor(String username, LocalDate from, LocalDate to, String category) {
        return repository.findTransactions(username).stream()
                .filter(tx -> category == null || category.isEmpty() || tx.category().equals(category))
                .filter(tx -> from == null || !tx.date().isBefore(from))
                .filter(tx -> to == null || !tx.date().isAfter(to))
                .sorted(Comparator.comparing(Transaction::date).reversed())
                .toList();
    }

    /** Anzahl eines Buchungstyps mit deutscher Einzahl- bzw. Mehrzahlbeschriftung. */
    public String transactionCountLabel(List<Transaction> transactions, Transaction.Type type) {
        long count = transactions.stream().filter(tx -> tx.type() == type).count();
        String label = switch (type) {
            case DEBIT -> count == 1 ? "Belastung" : "Belastungen";
            case CREDIT -> count == 1 ? "Gutschrift" : "Gutschriften";
        };
        return count + " " + label;
    }

    /** Saldo aller Transaktionen (Gutschriften minus Belastungen). */
    public BigDecimal balance(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
