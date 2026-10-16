package ch.amato.ccp.repository;

import ch.amato.ccp.model.Customer;
import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.model.Transaction.Type;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-Memory-Demodaten. Alle Personen, Händler und Kartennummern sind erfunden.
 */
@Repository
public class DemoDataRepository {

    private final Map<String, Customer> customers = Map.of(
            "anna", new Customer("anna", "Anna Muster", "**** **** **** 4242"),
            "marco", new Customer("marco", "Marco Beispiel", "**** **** **** 1881"));

    private final Map<String, List<Transaction>> transactions = Map.of(
            "anna", List.of(
                    tx("A-1001", "2026-10-02", "Migros Zürich", "Lebensmittel", "84.35", Type.DEBIT),
                    tx("A-1002", "2026-10-04", "SBB Online", "Reisen", "62.00", Type.DEBIT),
                    tx("A-1003", "2026-10-06", "Zalando Rückerstattung", "Shopping", "49.90", Type.CREDIT),
                    tx("A-1004", "2026-10-09", "Restaurant Seeblick", "Gastronomie", "118.50", Type.DEBIT),
                    tx("A-1005", "2026-10-12", "Digitec Galaxus", "Elektronik", "349.00", Type.DEBIT),
                    tx("A-1006", "2026-10-14", "Swisscom", "Telekom", "65.00", Type.DEBIT)),
            "marco", List.of(
                    tx("M-2001", "2026-10-01", "Coop Bern", "Lebensmittel", "56.20", Type.DEBIT),
                    tx("M-2002", "2026-10-05", "Hotel Alpenblick", "Reisen", "420.00", Type.DEBIT),
                    tx("M-2003", "2026-10-08", "Hotel Alpenblick Gutschrift", "Reisen", "80.00", Type.CREDIT),
                    tx("M-2004", "2026-10-11", "Tankstelle Aare", "Mobilität", "92.70", Type.DEBIT)));

    public Optional<Customer> findCustomer(String username) {
        return Optional.ofNullable(customers.get(username));
    }

    public List<Transaction> findTransactions(String username) {
        return transactions.getOrDefault(username, List.of());
    }

    public List<String> usernames() {
        return customers.keySet().stream().sorted().toList();
    }

    private static Transaction tx(String id, String date, String merchant, String category,
                                  String amount, Type type) {
        return new Transaction(id, LocalDate.parse(date), merchant, category,
                new BigDecimal(amount), "CHF", type);
    }
}
