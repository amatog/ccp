package ch.amato.ccp.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class LastLoginFormatter {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMAN)
            .withZone(ZoneId.of("Europe/Zurich"));

    private LastLoginFormatter() {
    }

    public static String format(Instant previousLogin) {
        return previousLogin == null ? "Erste Anmeldung" : FORMAT.format(previousLogin);
    }
}
