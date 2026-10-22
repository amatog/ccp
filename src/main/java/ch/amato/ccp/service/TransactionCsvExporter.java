package ch.amato.ccp.service;

import ch.amato.ccp.model.Transaction;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TransactionCsvExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] export(List<Transaction> transactions) {
        StringBuilder csv = new StringBuilder("\uFEFFDatum;Händler;Kategorie;Betrag\r\n");
        for (Transaction transaction : transactions) {
            csv.append(DATE.format(transaction.date())).append(';')
                    .append(escapeText(transaction.merchant())).append(';')
                    .append(escapeText(transaction.category())).append(';')
                    .append(transaction.signedAmount().setScale(2).toPlainString()).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeText(String value) {
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
