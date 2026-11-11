package ch.amato.ccp.service;

import ch.amato.ccp.model.Dispute;
import ch.amato.ccp.model.DisputeReason;
import ch.amato.ccp.model.Transaction;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class DisputeService {
    private static final String SESSION_KEY = DisputeService.class.getName() + ".disputes";
    private final Clock clock;

    public DisputeService(Clock clock) {
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Dispute> disputes(HttpSession session) {
        Map<String, Dispute> disputes = (Map<String, Dispute>) session.getAttribute(SESSION_KEY);
        return disputes == null ? Map.of() : disputes;
    }

    public Set<String> disputedIds(HttpSession session) {
        synchronized (session) {
            return Set.copyOf(disputes(session).keySet());
        }
    }

    public String blockedReason(Transaction transaction, HttpSession session) {
        synchronized (session) {
            if (transaction.type() == Transaction.Type.CREDIT) {
                return "Gutschriften können nicht beanstandet werden.";
            }
            if (disputes(session).containsKey(transaction.id())) {
                return "Diese Transaktion ist bereits in Abklärung.";
            }
            return null;
        }
    }

    /** Prüfung und Speicherung gemeinsam sperren, damit parallele POSTs nichts überschreiben. */
    public String submit(Transaction transaction, DisputeReason reason, String comment, HttpSession session) {
        synchronized (session) {
            String blocked = blockedReason(transaction, session);
            if (blocked != null) return blocked;
            if (reason == null) throw new IllegalArgumentException("Bitte einen Grund wählen.");
            if (comment != null && comment.length() > 500) {
                throw new IllegalArgumentException("Der Kommentar darf höchstens 500 Zeichen lang sein.");
            }
            String trimmed = comment == null ? null : comment.trim();
            Map<String, Dispute> updated = new HashMap<>(disputes(session));
            updated.put(transaction.id(), new Dispute(transaction.id(), reason,
                    trimmed == null || trimmed.isEmpty() ? null : trimmed, Instant.now(clock)));
            session.setAttribute(SESSION_KEY, updated);
            return null;
        }
    }
}
