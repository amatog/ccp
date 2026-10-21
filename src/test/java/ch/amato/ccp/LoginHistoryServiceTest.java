package ch.amato.ccp;

import ch.amato.ccp.service.LoginHistoryService;
import ch.amato.ccp.web.LastLoginFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoginHistoryServiceTest {

    @ParameterizedTest
    @CsvSource({
            "2026-10-20T13:42:00Z, '20.10.2026, 15:42'",
            "2026-12-01T08:05:00Z, '01.12.2026, 09:05'"
    })
    void previousLoginUsesZurichSummerAndWinterTime(String timestamp, String expected) {
        var history = new LoginHistoryService(Clock.fixed(Instant.parse(timestamp), ZoneOffset.UTC));
        assertEquals("Erste Anmeldung", LastLoginFormatter.format(history.recordLogin("anna")));
        assertEquals(expected, LastLoginFormatter.format(history.recordLogin("anna")));
    }

    @Test
    void historiesAreSeparateAndNewServiceStartsEmpty() {
        var clock = Clock.fixed(Instant.parse("2026-10-20T08:00:00Z"), ZoneOffset.UTC);
        var history = new LoginHistoryService(clock);
        assertNull(history.recordLogin("anna"));
        assertNull(history.recordLogin("marco"));
        assertEquals(clock.instant(), history.recordLogin("anna"));
        assertNull(new LoginHistoryService(clock).recordLogin("anna"));
    }
}
