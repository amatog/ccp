package ch.amato.ccp.service;

import ch.amato.ccp.model.Customer;
import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.repository.DemoDataRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TransactionService {

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
        return repository.findTransactions(username).stream()
                .sorted(Comparator.comparing(Transaction::date).reversed())
                .toList();
    }

    /** Saldo aller Transaktionen (Gutschriften minus Belastungen). */
    public BigDecimal balance(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
