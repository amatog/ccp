package ch.amato.ccp;

import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.service.TransactionCsvExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionCsvExporterTest {

    private final TransactionCsvExporter exporter = new TransactionCsvExporter();
    private static final String HEADER = "\uFEFFDatum;Händler;Kategorie;Betrag\r\n";

    @Test
    void exportsExactUtf8WithBomSignedAmountsAndCrlf() {
        byte[] actual = exporter.export(List.of(
                transaction("Migros Zürich", "Lebensmittel", "1200", Transaction.Type.DEBIT),
                transaction("Rückerstattung", "Reisen", "750.00", Transaction.Type.CREDIT)));
        assertArrayEquals((HEADER
                + "13.10.2026;Migros Zürich;Lebensmittel;-1200.00\r\n"
                + "13.10.2026;Rückerstattung;Reisen;750.00\r\n").getBytes(StandardCharsets.UTF_8), actual);
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                java.util.Arrays.copyOf(actual, 3));
    }

    @Test
    void emptyExportContainsOnlyBomAndHeader() {
        assertEquals(HEADER, csv(List.of()));
    }

    @Test
    void quotesSeparatorsQuotesAndLineBreaksInBothTextColumns() {
        assertEquals(HEADER + "13.10.2026;\"Shop; \"\"Zürich\"\"\";\"A\r\nB\";-1.00\r\n",
                csv(List.of(transaction("Shop; \"Zürich\"", "A\r\nB", "1", Transaction.Type.DEBIT))));
        assertEquals(HEADER + "13.10.2026;\"A\nB\";\"C\rD\";-1.00\r\n",
                csv(List.of(transaction("A\nB", "C\rD", "1", Transaction.Type.DEBIT))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"=SUM(1)", "+1", "-1", "@command"})
    void neutralizesFormulaPrefixesInBothTextColumns(String text) {
        assertEquals(HEADER + "13.10.2026;'" + text + ";'" + text + ";-1.00\r\n",
                csv(List.of(transaction(text, text, "1", Transaction.Type.DEBIT))));
    }

    @Test
    void escapesTextAfterFormulaProtection() {
        assertEquals(HEADER + "13.10.2026;\"'=A;\"\"B\"\"\";Reisen;-1.00\r\n",
                csv(List.of(transaction("=A;\"B\"", "Reisen", "1", Transaction.Type.DEBIT))));
    }

    private String csv(List<Transaction> transactions) {
        return new String(exporter.export(transactions), StandardCharsets.UTF_8);
    }

    private Transaction transaction(String merchant, String category, String amount, Transaction.Type type) {
        return new Transaction("test", LocalDate.of(2026, 10, 13), merchant, category,
                new BigDecimal(amount), "CHF", type);
    }
}
