package ch.amato.ccp;

import ch.amato.ccp.model.Dispute;
import ch.amato.ccp.model.DisputeReason;
import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.service.DisputeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DisputeServiceTest {
    private final Instant now = Instant.parse("2026-10-22T10:00:00Z");
    private final DisputeService service = new DisputeService(Clock.fixed(now, ZoneOffset.UTC));
    private final MockHttpSession session = new MockHttpSession();

    private Transaction transaction(Transaction.Type type) {
        return new Transaction("test", LocalDate.of(2026, 10, 12), "Demo", "Elektronik",
                new BigDecimal("349.00"), "CHF", type);
    }

    private Dispute saved() {
        return ((Map<?, ?>) session.getAttribute(session.getAttributeNames().nextElement()))
                .values().stream().map(Dispute.class::cast).findFirst().orElseThrow();
    }

    @Test
    void storesReasonTrimmedCommentAndClockTimestampOnlyInThisSession() {
        assertNull(service.submit(transaction(Transaction.Type.DEBIT), DisputeReason.WRONG_AMOUNT,
                "  Laut Quittung 249.00 CHF  ", session));
        assertEquals(new Dispute("test", DisputeReason.WRONG_AMOUNT,
                "Laut Quittung 249.00 CHF", now), saved());
        assertEquals(java.util.Set.of("test"), service.disputedIds(session));
        assertTrue(service.disputedIds(new MockHttpSession()).isEmpty());
    }

    @Test
    void blankCommentIsAbsentAndDuplicateCannotOverwriteOriginal() {
        service.submit(transaction(Transaction.Type.DEBIT), DisputeReason.UNKNOWN_TRANSACTION, "   ", session);
        assertNull(saved().comment());
        assertEquals("Diese Transaktion ist bereits in Abklärung.",
                service.submit(transaction(Transaction.Type.DEBIT), DisputeReason.WRONG_AMOUNT, "neu", session));
        assertEquals(DisputeReason.UNKNOWN_TRANSACTION, saved().reason());
    }

    @Test
    void creditAndInvalidInputStoreNothing() {
        assertEquals("Gutschriften können nicht beanstandet werden.",
                service.submit(transaction(Transaction.Type.CREDIT), DisputeReason.WRONG_AMOUNT, "", session));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(transaction(Transaction.Type.DEBIT), null, "", session));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(transaction(Transaction.Type.DEBIT), DisputeReason.WRONG_AMOUNT,
                        " ".repeat(501), session));
        assertTrue(service.disputedIds(session).isEmpty());
    }

    @Test
    void acceptsExactlyFiveHundredCharacters() {
        service.submit(transaction(Transaction.Type.DEBIT), DisputeReason.DUPLICATE_CHARGE, "a".repeat(500), session);
        assertEquals(500, saved().comment().length());
    }
}
