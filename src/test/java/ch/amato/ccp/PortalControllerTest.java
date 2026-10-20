package ch.amato.ccp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest
@AutoConfigureMockMvc
class PortalControllerTest {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "marco, , , -1'040.68 CHF, 130 Belastungen, 16 Gutschriften, Gesamtes Konto, 146",
            "marco, 2026-10-01, 2026-10-05, -476.20 CHF, 2 Belastungen, 0 Gutschriften, 01.10.2026 – 05.10.2026, 2",
            "anna, 2026-10-06, 2026-10-06, 49.90 CHF, 0 Belastungen, 1 Gutschrift, 06.10.2026 – 06.10.2026, 1",
            "lena, 2026-10-05, 2026-10-10, 249.95 CHF, 1 Belastung, 1 Gutschrift, 05.10.2026 – 10.10.2026, 2",
            "marco, 2026-10-01, , -488.90 CHF, 3 Belastungen, 1 Gutschrift, ab 01.10.2026, 4",
            "anna, , 2025-10-31, -97.77 CHF, 4 Belastungen, 0 Gutschriften, bis 31.10.2025, 4",
            "anna, 2026-10-15, 2026-10-20, 0.00 CHF, 0 Belastungen, 0 Gutschriften, 15.10.2026 – 20.10.2026, 0",
            "marco, 2026-10-10, 2026-10-01, -1'040.68 CHF, 130 Belastungen, 16 Gutschriften, Gesamtes Konto, 146",
            "marco, abc, , -1'040.68 CHF, 130 Belastungen, 16 Gutschriften, Gesamtes Konto, 146",
            "marco, , 2026-02-30, -1'040.68 CHF, 130 Belastungen, 16 Gutschriften, Gesamtes Konto, 146",
            "lena, 2026-01-01, 2026-06-30, -641.14 CHF, 75 Belastungen, 8 Gutschriften, 01.01.2026 – 30.06.2026, 83",
            "anna, 2026-10-01, 2026-10-31, -628.95 CHF, 5 Belastungen, 1 Gutschrift, 01.10.2026 – 31.10.2026, 6",
            "marco, 2026-10-01, 2026-10-31, -488.90 CHF, 3 Belastungen, 1 Gutschrift, 01.10.2026 – 31.10.2026, 4"
    })
    void accountSummaryMatchesDisplayedPeriod(String username, String from, String to, String balance,
                                              String debits, String credits, String period, int rows) throws Exception {
        mvc.perform(get("/transactions").with(user(username)).param("username", "other")
                        .param("from", from == null ? "" : from).param("to", to == null ? "" : to))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='balance-total']").string(balance))
                .andExpect(xpath("//*[@data-testid='count-debits']").string(debits))
                .andExpect(xpath("//*[@data-testid='count-credits']").string(credits))
                .andExpect(xpath("//*[@data-testid='account-summary']//*[@data-testid='summary-period']")
                        .string(period))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(rows));
    }

    @Test
    @WithMockUser(username = "marco")
    void resetRestoresWholeAccountSummary() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-01").param("to", "2026-10-05"))
                .andExpect(xpath("//*[@data-testid='filter-reset']/@href").string("/transactions"));
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-1'040.68 CHF"))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("130 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("16 Gutschriften"))
                .andExpect(xpath("//*[@data-testid='summary-period']").string("Gesamtes Konto"));
    }

    @Test
    @WithMockUser(username = "lena")
    void accountBalanceMatchesFilterSummary() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-01-01").param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-641.14 CHF"))
                .andExpect(xpath("//*[@data-testid='filter-summary']")
                        .string("83 Transaktionen im Zeitraum, Summe: -641.14 CHF"));
    }

    @ParameterizedTest
    @CsvSource({
            "L-3001, 0",
            "L-3002, 1",
            "L-3003, 0",
            "L-3004, 1",
            "L-3005, 0"
    })
    @WithMockUser(username = "lena")
    void lenaSeesWarningsOnlyInsideHighDebitAmountCells(String id, int count) throws Exception {
        String warning = "//*[@data-transaction-id='" + id
                + "']/td[@data-testid='tx-amount']/span[@data-testid='high-amount-warning']";
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='high-amount-warning']").nodeCount(2))
                .andExpect(xpath(warning).nodeCount(count));
    }

    @ParameterizedTest
    @ValueSource(strings = {"L-3002", "L-3004"})
    @WithMockUser(username = "lena")
    void warningsHaveSymbolTooltipAndAccessibleName(String id) throws Exception {
        String warning = "//*[@data-transaction-id='" + id + "']//span[@data-testid='high-amount-warning']";
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath(warning).string("⚠"))
                .andExpect(xpath(warning + "/@title").string("Hoher Betrag – bitte prüfen"))
                .andExpect(xpath(warning + "/@aria-label").string("Hoher Betrag – bitte prüfen"))
                .andExpect(xpath(warning + "/@role").string("img"))
                .andExpect(xpath(warning + "/@class").string("high-amount-warning"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anna", "marco"})
    void existingCustomersSeeNoWarnings(String username) throws Exception {
        mvc.perform(get("/transactions").with(user(username)).param("username", "lena"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='high-amount-warning']").doesNotExist())
                .andExpect(xpath("//*[@data-transaction-id and starts-with(@data-transaction-id, 'L-')]")
                        .doesNotExist());
    }

    @Test
    @WithMockUser(username = "lena")
    void lenaDateFilterRetainsOnlyMatchingWarning() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-05").param("to", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(2))
                .andExpect(xpath("//*[@data-transaction-id='L-3002']").nodeCount(1))
                .andExpect(xpath("//*[@data-transaction-id='L-3003']").nodeCount(1))
                .andExpect(xpath("//*[@data-testid='high-amount-warning']").nodeCount(1))
                .andExpect(xpath("//*[@data-transaction-id='L-3002']/td[@data-testid='tx-amount']"
                        + "/span[@data-testid='high-amount-warning']").nodeCount(1));
    }

    @Test
    @WithMockUser(username = "lena")
    void lenaSummaryAndFormattedAmountsMatchDemoData() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(162))
                .andExpect(xpath("//*[@data-testid='customer-name']").string("Lena Probst"))
                .andExpect(xpath("//*[@data-testid='card-number']").string("**** **** **** 7070"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-2'595.70 CHF"))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("146 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("16 Gutschriften"))
                .andExpect(xpath("//*[@data-transaction-id='L-3002']/td[@data-testid='tx-amount']")
                        .string(containsString("-500.05 CHF")))
                .andExpect(xpath("//*[@data-transaction-id='L-3004']/td[@data-testid='tx-amount']")
                        .string(containsString("-1'200.00 CHF")));
    }

    @Test
    void lenaCanLogInWithDemoPassword() throws Exception {
        mvc.perform(formLogin("/login").user("lena").password("demo1234"))
                .andExpect(redirectedUrl("/transactions"));
    }

    @Test
    @WithMockUser(username = "anna")
    void everyCategoryCellContainsExactlyOneBadge() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//td[@data-testid='tx-category']").nodeCount(155))
                .andExpect(xpath("//td[@data-testid='tx-category'][count(*)=1"
                        + " and count(span[@data-testid='category-badge'])=1]").nodeCount(155));
    }

    @ParameterizedTest
    @CsvSource({
            "A-1001, Lebensmittel, lebensmittel",
            "A-1002, Reisen, reisen",
            "A-1003, Shopping, shopping",
            "A-1004, Gastronomie, gastronomie",
            "A-1005, Elektronik, elektronik",
            "A-1006, Telekom, telekom"
    })
    @WithMockUser(username = "anna")
    void categoryBadgeShowsTextKeyAndColorClass(String id, String category, String key) throws Exception {
        String cell = "//*[@data-transaction-id='" + id + "']/td[@data-testid='tx-category']";
        String badge = cell + "/span[@data-testid='category-badge']";
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("normalize-space(" + cell + ")").string(category))
                .andExpect(xpath(badge).string(category))
                .andExpect(xpath(badge + "/@data-category").string(key))
                .andExpect(xpath(badge + "/@class").string("badge badge--" + key));
    }

    @Test
    @WithMockUser(username = "marco")
    void travelDebitAndCreditUseSameBadgeKeyAndColorClass() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-transaction-id='M-2002' or @data-transaction-id='M-2003']"
                        + "/td[@data-testid='tx-category']/span[@data-testid='category-badge'"
                        + " and @data-category='reisen' and @class='badge badge--reisen']").nodeCount(2));
    }

    @Test
    @WithMockUser(username = "anna")
    void dateFilterRetainsCategoryBadges() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-01").param("to", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='category-badge']").nodeCount(2))
                .andExpect(xpath("//*[@data-transaction-id='A-1001']//span[@data-testid='category-badge']")
                        .string("Lebensmittel"))
                .andExpect(xpath("//*[@data-transaction-id='A-1002']//span[@data-testid='category-badge']")
                        .string("Reisen"));
    }

    @Test
    @WithMockUser(username = "anna")
    void annaSeesBookingCountsAfterBalanceInAccountSummary() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='account-summary']//*[@data-testid='count-debits']")
                        .string("142 Belastungen"))
                .andExpect(xpath("//*[@data-testid='account-summary']//*[@data-testid='count-credits']")
                        .string("13 Gutschriften"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-1'382.07 CHF"))
                .andExpect(xpath("//*[@data-testid='balance-total']/../following-sibling::*[1]"
                        + "[@data-testid='transaction-counts']/*[@class='label']").string("Buchungen"));
    }

    @Test
    @WithMockUser(username = "marco")
    void marcoSeesOnlyHisBookingCounts() throws Exception {
        mvc.perform(get("/transactions").param("username", "anna"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='count-debits']").string("130 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("16 Gutschriften"));
    }

    @Test
    @WithMockUser(username = "anna")
    void dateFilterAppliesToBookingCountsAndBalance() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-06").param("to", "2026-10-06"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(1))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("0 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("1 Gutschrift"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("49.90 CHF"))
                .andExpect(xpath("//*[@data-testid='balance-total']/@class").string("value credit"));
    }

    @Test
    void unauthenticatedUserIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginWithDemoPasswordSucceeds() throws Exception {
        mvc.perform(formLogin("/login").user("anna").password("demo1234"))
                .andExpect(redirectedUrl("/transactions"));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        mvc.perform(formLogin("/login").user("anna").password("falsch"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser(username = "anna")
    void customerSeesOnlyOwnTransactions() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anna Muster")))
                .andExpect(content().string(containsString("**** **** **** 4242")))
                .andExpect(content().string(containsString("Migros Zürich")))
                .andExpect(content().string(not(containsString("Coop Bern"))));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
    @ParameterizedTest
    @CsvSource({
            "2026-10-04, 2026-10-09, 3, '3 Transaktionen im Zeitraum, Summe: -130.60 CHF'",
            "2026-10-06, 2026-10-06, 1, '1 Transaktion im Zeitraum, Summe: 49.90 CHF'",
            "2026-10-10, , 2, '2 Transaktionen im Zeitraum, Summe: -414.00 CHF'",
            ", 2025-10-26, 2, '2 Transaktionen im Zeitraum, Summe: -44.54 CHF'"
    })
    @WithMockUser(username = "anna")
    void validFilterShowsSummaryAndRetainsValues(String from, String to, int count, String summary) throws Exception {
        mvc.perform(get("/transactions").param("from", from == null ? "" : from)
                        .param("to", to == null ? "" : to))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(count))
                .andExpect(xpath("//*[@data-testid='filter-summary']").string(summary))
                .andExpect(xpath("//*[@data-testid='filter-from']/@value").string(from == null ? "" : from))
                .andExpect(xpath("//*[@data-testid='filter-to']/@value").string(to == null ? "" : to))
                .andExpect(xpath("//*[@data-testid='filter-form']/@method").string("get"))
                .andExpect(xpath("//*[@data-testid='filter-form']/@action").string("/transactions"))
                .andExpect(xpath("//*[@data-testid='filter-reset']/@href").string("/transactions"))
                .andExpect(xpath("//*[@data-testid='filter-error']").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-10, 2026-10-05, Das Von-Datum darf nicht nach dem Bis-Datum liegen.",
            "abc, , Ungültiges Datum. Bitte im Format TT.MM.JJJJ wählen.",
            ", abc, Ungültiges Datum. Bitte im Format TT.MM.JJJJ wählen.",
            "2026-02-30, , Ungültiges Datum. Bitte im Format TT.MM.JJJJ wählen."
    })
    @WithMockUser(username = "anna")
    void invalidFilterShowsErrorAndAllTransactions(String from, String to, String error) throws Exception {
        mvc.perform(get("/transactions").param("from", from == null ? "" : from)
                        .param("to", to == null ? "" : to))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='filter-error']").string(error))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(155))
                .andExpect(xpath("//*[@data-testid='filter-summary']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='filter-no-results']").doesNotExist());
    }

    @Test
    @WithMockUser(username = "anna")
    void emptyPeriodReplacesTableWithFilterMessage() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-15").param("to", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='filter-no-results']")
                        .string(org.hamcrest.Matchers.containsString("Keine Transaktionen im gewählten Zeitraum.")))
                .andExpect(xpath("//*[@data-testid='transactions-table']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='transactions-empty']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='filter-summary']").doesNotExist());
    }

    @Test
    @WithMockUser(username = "anna")
    void resetShowsAllTransactionsWithoutActiveFilter() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(155))
                .andExpect(xpath("//*[@data-testid='filter-from']/@value").string(""))
                .andExpect(xpath("//*[@data-testid='filter-to']/@value").string(""))
                .andExpect(xpath("//*[@data-testid='filter-summary']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='filter-reset']").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"2026-10-05, 2, -476.20 CHF", "2026-10-31, 4, -488.90 CHF"})
    @WithMockUser(username = "marco")
    void filteredViewUsesPeriodBalanceAndPreservesCustomerIsolation(String to, int count, String balance) throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-01").param("to", to)
                        .param("username", "anna"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='balance-total']").string(balance))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(count))
                .andExpect(xpath("//*[@data-testid='transaction-row'][starts-with(@data-transaction-id, 'A-')]")
                        .doesNotExist());
    }
}
