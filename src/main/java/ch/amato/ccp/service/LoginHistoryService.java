package ch.amato.ccp.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginHistoryService {

    public static final String PREVIOUS_LOGIN = "previousLogin";

    private final ConcurrentHashMap<String, Instant> lastLogins = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginHistoryService(Clock clock) {
        this.clock = clock;
    }

    /** Speichert die erfolgreiche Anmeldung atomar und liefert die vorherige zurück. */
    public Instant recordLogin(String username) {
        // compute serialisiert auch die Zeitermittlung für denselben Benutzer.
        Instant[] previous = new Instant[1];
        lastLogins.compute(username, (name, lastLogin) -> {
            previous[0] = lastLogin;
            return clock.instant();
        });
        return previous[0];
    }
}
