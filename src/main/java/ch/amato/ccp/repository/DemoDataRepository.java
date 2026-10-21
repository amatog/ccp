package ch.amato.ccp.repository;

import ch.amato.ccp.model.Customer;
import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.model.Transaction.Type;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-Memory-Demodaten. Alle Personen, Händler und Kartennummern sind erfunden.
 * Die Transaktionen (20.10.2025 bis heute) stehen in {@value #TRANSACTIONS_RESOURCE}.
 */
@Repository
public class DemoDataRepository {

    static final String TRANSACTIONS_RESOURCE = "/demo/transactions.csv";

    private final Map<String, Customer> customers = Map.of(
            "anna", new Customer("anna", "Anna Muster", "**** **** **** 4242"),
            "marco", new Customer("marco", "Marco Beispiel", "**** **** **** 1881"),
            "nora", new Customer("nora", "Nora Keller", "**** **** **** 5151"),
            "lena", new Customer("lena", "Lena Probst", "**** **** **** 7070"));

    private final Map<String, List<Transaction>> transactions = loadTransactions();

    public Optional<Customer> findCustomer(String username) {
        return Optional.ofNullable(customers.get(username));
    }

    public List<Transaction> findTransactions(String username) {
        return transactions.getOrDefault(username, List.of());
    }

    public List<String> usernames() {
        return customers.keySet().stream().sorted().toList();
    }

    /** Liest die CSV-Datei: username;id;date;merchant;category;amount;type, Zeilen mit # sind Kommentare. */
    private static Map<String, List<Transaction>> loadTransactions() {
        Map<String, List<Transaction>> byUser = new HashMap<>();
        try (InputStream in = DemoDataRepository.class.getResourceAsStream(TRANSACTIONS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Demo-Daten fehlen: " + TRANSACTIONS_RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(";", -1);
                if (f.length != 7) {
                    throw new IllegalStateException("Ungültige Zeile " + lineNo + " in " + TRANSACTIONS_RESOURCE);
                }
                byUser.computeIfAbsent(f[0], k -> new ArrayList<>())
                        .add(new Transaction(f[1], LocalDate.parse(f[2]), f[3], f[4],
                                new BigDecimal(f[5]), "CHF", Type.valueOf(f[6])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, List<Transaction>> result = new HashMap<>();
        byUser.forEach((user, list) -> result.put(user, List.copyOf(list)));
        return Map.copyOf(result);
    }
}
