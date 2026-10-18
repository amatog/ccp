package ch.amato.ccp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Eine Kreditkarten-Transaktion. {@code amount} ist immer positiv,
 * die Richtung ergibt sich aus {@link Type}.
 */
public record Transaction(
        String id,
        LocalDate date,
        String merchant,
        String category,
        BigDecimal amount,
        String currency,
        Type type) {

    public enum Type {
        /** Belastung, z. B. ein Einkauf. */
        DEBIT,
        /** Gutschrift, z. B. eine Rückerstattung. */
        CREDIT
    }

    /** Betrag mit Vorzeichen: Belastungen negativ, Gutschriften positiv. */
    public BigDecimal signedAmount() {
        return type == Type.DEBIT ? amount.negate() : amount;
    }

    /** Stabiler Schlüssel für die Darstellung der Kategorie als Badge. */
    public String categoryKey() {
        return switch (category) {
            case "Lebensmittel" -> "lebensmittel";
            case "Reisen" -> "reisen";
            case "Shopping" -> "shopping";
            case "Gastronomie" -> "gastronomie";
            case "Elektronik" -> "elektronik";
            case "Telekom" -> "telekom";
            case "Mobilität" -> "mobilitaet";
            default -> "andere";
        };
    }
}
