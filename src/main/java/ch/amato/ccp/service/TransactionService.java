package ch.amato.ccp.service;

import ch.amato.ccp.model.Customer;
import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.repository.DemoDataRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
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

    /** Begrenzt die bereits sortierte Auswahl auf Seiten mit je 20 Transaktionen. */
    public TransactionPage page(List<Transaction> transactions, String requestedPage) {
        int totalPages = Math.max(1, (transactions.size() + 19) / 20);
        int currentPage = 1;
        try {
            currentPage = new BigInteger(requestedPage).max(BigInteger.ONE)
                    .min(BigInteger.valueOf(totalPages)).intValue();
        } catch (NumberFormatException exception) {
            // Ungültige Seitenzahlen zeigen ohne Fehlermeldung die erste Seite.
        }
        int start = (currentPage - 1) * 20;
        return new TransactionPage(transactions.subList(start, Math.min(start + 20, transactions.size())),
                currentPage, totalPages);
    }

    public record TransactionPage(List<Transaction> transactions, int number, int totalPages) {
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

    /** Positive Summe eines Buchungstyps in der aktuellen Auswahl. */
    public BigDecimal total(List<Transaction> transactions, Transaction.Type type) {
        return transactions.stream()
                .filter(tx -> tx.type() == type)
                .map(Transaction::amount)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    /** Ausgaben in der aktuellen Auswahl (Belastungen minus Gutschriften). */
    public BigDecimal netSpending(List<Transaction> transactions) {
        return total(transactions, Transaction.Type.DEBIT)
                .subtract(total(transactions, Transaction.Type.CREDIT));
    }

    /** Saldo aller Transaktionen (Gutschriften minus Belastungen). */
    public BigDecimal balance(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
