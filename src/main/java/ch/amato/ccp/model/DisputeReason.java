package ch.amato.ccp.model;

public enum DisputeReason {
    UNKNOWN_TRANSACTION("Unbekannte Transaktion"),
    DUPLICATE_CHARGE("Doppelt belastet"),
    WRONG_AMOUNT("Falscher Betrag");

    private final String label;

    DisputeReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DisputeReason parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
