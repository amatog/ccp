package ch.amato.ccp.model;

/**
 * Fiktiver Kunde des Demo-Portals. Kartennummern werden nur maskiert gespeichert.
 */
public record Customer(String username, String fullName, String maskedCardNumber) {
}
