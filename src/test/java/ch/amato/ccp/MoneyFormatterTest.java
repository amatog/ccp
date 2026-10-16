package ch.amato.ccp;

import ch.amato.ccp.web.MoneyFormatter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatterTest {

    private final MoneyFormatter formatter = new MoneyFormatter();

    @Test
    void formatsSwissStyle() {
        assertThat(formatter.format(new BigDecimal("1234.5"))).isEqualTo("1'234.50");
        assertThat(formatter.format(new BigDecimal("-488.90"))).isEqualTo("-488.90");
        assertThat(formatter.format(BigDecimal.ZERO)).isEqualTo("0.00");
    }
}
