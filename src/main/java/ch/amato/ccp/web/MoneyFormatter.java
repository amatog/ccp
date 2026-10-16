package ch.amato.ccp.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Formatiert Beträge im Schweizer Format, z. B. {@code -1'234.50}.
 * In Templates verfügbar als {@code ${@money.format(betrag)}}.
 */
@Component("money")
public class MoneyFormatter {

    public String format(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('\'');
        symbols.setDecimalSeparator('.');
        symbols.setMinusSign('-');
        return new DecimalFormat("#,##0.00", symbols).format(amount);
    }
}
