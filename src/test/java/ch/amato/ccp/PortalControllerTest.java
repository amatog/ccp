package ch.amato.ccp;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest
@AutoConfigureMockMvc
class PortalControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    Clock clock;

    @BeforeEach
    void fixedExportClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T22:30:00Z"));
        when(clock.withZone(ZoneId.of("Europe/Zurich")))
                .thenReturn(Clock.fixed(Instant.parse("2026-10-21T22:30:00Z"),
                        ZoneId.of("Europe/Zurich")));
    }

    @ParameterizedTest
    @CsvSource({
            "anna, Anna, Anna Muster",
            "marco, Marco, Marco Beispiel",
            "lena, Lena, Lena Probst"
    })
    void greetingUsesAuthenticatedCustomersFirstName(String username, String firstName, String fullName)
            throws Exception {
        mvc.perform(get("/transactions").with(user(username)).param("username", "lena"))
                .andExpect(status().isOk())
                .andExpect(xpath("//main/*[@data-testid='greeting']").string("Guten Tag, " + firstName))
                .andExpect(xpath("//main/*[@data-testid='greeting']/following-sibling::*[1]"
                        + "[@data-testid='account-summary']").exists())
                .andExpect(xpath("//*[@data-testid='customer-name']").string(fullName));
    }

    @Test
    @WithMockUser(username = "anna")
    void greetingRemainsWhenFilteringAndResetting() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(xpath("//main/*[@data-testid='greeting']").string("Guten Tag, Anna"))
                .andExpect(xpath("//*[@data-testid='customer-name']").string("Anna Muster"))
                .andExpect(xpath("//*[@data-testid='filter-reset']/@href").string("/transactions"));
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//main/*[@data-testid='greeting']").string("Guten Tag, Anna"))
                .andExpect(xpath("//*[@data-testid='customer-name']").string("Anna Muster"))
                .andExpect(xpath("//*[@data-testid='filter-reset']").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "anna, Elektronik, , , 7, '7 Transaktionen in der Kategorie Elektronik, Summe: -1’650.02 CHF'",
            "anna, Zahlung, , , 11, '11 Transaktionen in der Kategorie Zahlung, Summe: 8’904.51 CHF'",
            "marco, Mobilität, , , 30, '30 Transaktionen in der Kategorie Mobilität, Summe: -1’940.36 CHF'",
            "marco, Reisen, 2026-10-01, 2026-10-31, 2, '2 Transaktionen in der Kategorie Reisen, Summe: -340.00 CHF'",
            "anna, Elektronik, 2026-10-12, 2026-10-12, 1, '1 Transaktion in der Kategorie Elektronik, Summe: -349.00 CHF'"
    })
    void categoryFilterRendersSummaryAndRetainsSelection(String username, String category, String from,
                                                        String to, int count, String summary) throws Exception {
        mvc.perform(get("/transactions").with(user(username)).param("category", category)
                        .param("from", from == null ? "" : from).param("to", to == null ? "" : to))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(count))
                .andExpect(xpath("//*[@data-testid='filter-summary']").string(summary.replace('’', '\'')))
                .andExpect(xpath("//*[@data-testid='filter-category']/option[@selected]/@value").string(category))
                .andExpect(xpath("//*[@data-testid='filter-from']/@value").string(from == null ? "" : from))
                .andExpect(xpath("//*[@data-testid='filter-to']/@value").string(to == null ? "" : to))
                .andExpect(xpath("//*[@data-testid='filter-reset']/@href").string("/transactions"));
    }

    @ParameterizedTest
    @CsvSource({
            "Haustiere, 2026-10-01, 2026-10-31, Unbekannte Kategorie.",
            "elektronik, , , Unbekannte Kategorie.",
            "Elektronik, 2026-10-10, 2026-10-05, Das Von-Datum darf nicht nach dem Bis-Datum liegen.",
            "Elektronik, abc, , Ungültiges Datum. Bitte im Format TT.MM.JJJJ wählen."
    })
    @WithMockUser(username = "anna")
    void anyFilterErrorDisablesAllFiltering(String category, String from, String to, String error) throws Exception {
        mvc.perform(get("/transactions").param("category", category)
                        .param("from", from == null ? "" : from).param("to", to == null ? "" : to))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='filter-error']").string(error))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(155))
                .andExpect(xpath("//*[@data-testid='filter-summary']").doesNotExist());
    }

    @Test
    @WithMockUser(username = "anna")
    void emptyCategoryPeriodShowsCategoryMessage() throws Exception {
        mvc.perform(get("/transactions").param("category", "Elektronik")
                        .param("from", "2026-10-01").param("to", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='filter-no-results']").string("Keine Transaktionen gefunden."))
                .andExpect(xpath("//*[@data-testid='transactions-table']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='filter-summary']").doesNotExist());
    }

    @Test
    @WithMockUser(username = "marco")
    void categoryFilterPreservesAccountTotalsAndCustomerIsolation() throws Exception {
        mvc.perform(get("/transactions").param("category", "Elektronik").param("username", "anna"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(3))
                .andExpect(xpath("//*[@data-testid='transaction-row'][not(starts-with(@data-transaction-id, 'M-'))]")
                        .doesNotExist())
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-1'040.68 CHF"))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("130 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("16 Gutschriften"));
    }

    @Test
    @WithMockUser(username = "marco")
    void defaultCategoryOffersAllOptionsAndResetsMarcoAccount() throws Exception {
        String options = "//*[@data-testid='filter-category']/option";
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath(options).nodeCount(9))
                .andExpect(xpath(options + "[1][@selected]/@value").string(""))
                .andExpect(xpath(options + "[1]").string("Alle Kategorien"))
                .andExpect(xpath("//*[@data-testid='filter-category']/@name").string("category"))
                .andExpect(xpath("//label[@for='filter-category']").string("Kategorie"))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(146))
                .andExpect(xpath("//*[@data-testid='filter-summary']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='filter-reset']").doesNotExist());
        String[] categories = {"Elektronik", "Gastronomie", "Lebensmittel", "Mobilität",
                "Reisen", "Shopping", "Telekom", "Zahlung"};
        for (int i = 0; i < categories.length; i++) {
            mvc.perform(get("/transactions"))
                    .andExpect(xpath(options + "[" + (i + 2) + "]/@value").string(categories[i]));
        }
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
    void dateFilterDoesNotChangeBookingCountsOrTotalBalance() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-06").param("to", "2026-10-06"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(1))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("142 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("13 Gutschriften"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-1'382.07 CHF"));
    }

    @ParameterizedTest
    @CsvSource({
            "anna, , , , 10386.51, 9004.44, 1382.07, 155",
            "anna, 2026-10-01, 2026-10-31, , 678.85, 49.90, 628.95, 6",
            "marco, 2026-10-01, 2026-10-05, , 476.20, 0.00, 476.20, 2",
            "anna, 2026-10-06, 2026-10-06, , 0.00, 49.90, -49.90, 1",
            "lena, 2026-10-01, 2026-10-31, , 2212.45, 750.00, 1462.45, 5",
            "anna, 2026-10-15, 2026-10-20, , 0.00, 0.00, 0.00, 0",
            "lena, 2026-10-01, 2026-10-31, Reisen, 500.05, 750.00, -249.95, 2"
    })
    void periodTotalsMatchDisplayedTransactions(String username, String from, String to, String category,
                                               String debits, String credits, String net, int count) throws Exception {
        var money = new ch.amato.ccp.web.MoneyFormatter();
        String totals = "//*[@data-testid='account-summary']/*[@data-testid='period-totals']";
        mvc.perform(get("/transactions").with(user(username))
                        .param("from", from == null ? "" : from).param("to", to == null ? "" : to)
                        .param("category", category == null ? "" : category))
                .andExpect(status().isOk())
                .andExpect(xpath(totals + "/*[@class='label']").string("Summen"))
                .andExpect(xpath(totals + "/*[@class='value']/*[@data-testid='sum-debits']")
                        .string(money.format(new java.math.BigDecimal(debits)) + " CHF"))
                .andExpect(xpath(totals + "/*[@class='value']/*[@data-testid='sum-credits']")
                        .string(money.format(new java.math.BigDecimal(credits)) + " CHF"))
                .andExpect(xpath(totals + "/*[@class='value']/*[@data-testid='sum-net']")
                        .string(money.format(new java.math.BigDecimal(net)) + " CHF"))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(count))
                .andExpect(xpath("//*[@data-testid='transaction-counts']/following-sibling::*[1]"
                        + "[@data-testid='period-totals']/following-sibling::*[1]/*[@data-testid='last-login']")
                        .exists());
    }

    @Test
    @WithMockUser(username = "anna")
    void octoberTotalsPreserveWholeAccountValuesAndResetToAllTotals() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='sum-debits']").string("678.85 CHF"))
                .andExpect(xpath("//*[@data-testid='sum-credits']").string("49.90 CHF"))
                .andExpect(xpath("//*[@data-testid='sum-net']").string("628.95 CHF"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-1'382.07 CHF"))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("142 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("13 Gutschriften"))
                .andExpect(xpath("//*[@data-testid='filter-reset']/@href").string("/transactions"));
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='sum-debits']").string("10'386.51 CHF"))
                .andExpect(xpath("//*[@data-testid='sum-credits']").string("9'004.44 CHF"))
                .andExpect(xpath("//*[@data-testid='sum-net']").string("1'382.07 CHF"));
    }

    @Test
    @WithMockUser(username = "marco")
    void invalidPeriodTotalsUseWholeAccount() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-10").param("to", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='filter-error']").exists())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(146))
                .andExpect(xpath("//*[@data-testid='sum-debits']").string("10'619.48 CHF"))
                .andExpect(xpath("//*[@data-testid='sum-credits']").string("9'578.80 CHF"))
                .andExpect(xpath("//*[@data-testid='sum-net']").string("1'040.68 CHF"));
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
    @CsvSource({"2026-10-05, 2", "2026-10-31, 4"})
    @WithMockUser(username = "marco")
    void filteredViewPreservesTotalBalanceAndCustomerIsolation(String to, int count) throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-01").param("to", to)
                        .param("username", "anna"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-1'040.68 CHF"))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(count))
                .andExpect(xpath("//*[@data-testid='transaction-row'][starts-with(@data-transaction-id, 'A-')]")
                        .doesNotExist());
    }
    @Test
    @WithMockUser(username = "anna")
    void exportHasExactOctoberContentAndDownloadHeaders() throws Exception {
        String expected = "\uFEFFDatum;Händler;Kategorie;Betrag\r\n"
                + "14.10.2026;Swisscom;Telekom;-65.00\r\n"
                + "12.10.2026;Digitec Galaxus;Elektronik;-349.00\r\n"
                + "09.10.2026;Restaurant Seeblick;Gastronomie;-118.50\r\n"
                + "06.10.2026;Zalando Rückerstattung;Shopping;49.90\r\n"
                + "04.10.2026;SBB Online;Reisen;-62.00\r\n"
                + "02.10.2026;Migros Zürich;Lebensmittel;-84.35\r\n";
        mvc.perform(get("/transactions/export").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(header()
                        .string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header()
                        .string("Content-Disposition", "attachment; filename=\"transaktionen_2026-10-22.csv\""))
                .andExpect(header()
                        .string("Cache-Control", "no-store"))
                .andExpect(content().bytes(expected.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @CsvSource({
            "anna, , , , 155",
            "marco, , , , 146",
            "marco, Mobilität, , , 30",
            "marco, Reisen, 2026-10-01, 2026-10-31, 2",
            "lena, , 2026-10-01, 2026-10-31, 5",
            "anna, Elektronik, 2026-10-01, 2026-10-10, 0",
            "anna, Haustiere, , , 155",
            "anna, Reisen, abc, , 155",
            "anna, Reisen, 2026-10-31, 2026-10-01, 155",
            "anna, , 2026-02-30, , 155"
    })
    void exportRowCountMatchesTableAndIgnoresRequestedUsername(String username, String category,
                                                               String from, String to, int count) throws Exception {
        String actual = mvc.perform(get("/transactions/export").with(user(username))
                        .param("category", category == null ? "" : category)
                        .param("from", from == null ? "" : from).param("to", to == null ? "" : to)
                        .param("username", "lena"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals(count + 1, actual.split("\r\n").length);
        mvc.perform(get("/transactions").with(user(username))
                        .param("category", category == null ? "" : category)
                        .param("from", from == null ? "" : from).param("to", to == null ? "" : to))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(count))
                .andExpect(xpath("//*[@data-testid='export-csv'][@disabled]").nodeCount(count == 0 ? 1 : 0));
        if ("Mobilität".equals(category)) {
            assertTrue(actual.startsWith(
                    "\uFEFFDatum;Händler;Kategorie;Betrag\r\n11.10.2026;Tankstelle Aare;Mobilität;-92.70\r\n"));
        }
        if ("marco".equals(username) && "Reisen".equals(category)) {
            assertEquals("\uFEFFDatum;Händler;Kategorie;Betrag\r\n"
                    + "08.10.2026;Hotel Alpenblick Gutschrift;Reisen;80.00\r\n"
                    + "05.10.2026;Hotel Alpenblick;Reisen;-420.00\r\n", actual);
        }
        if ("lena".equals(username)) {
            assertTrue(actual.contains(
                    "13.10.2026;Elektro Zentrum Baden;Elektronik;-1200.00\r\n"));
            assertTrue(actual.contains(
                    "10.10.2026;Reisebüro Horizont Gutschrift;Reisen;750.00\r\n"));
            assertFalse(actual.contains("CHF"));
        }
    }

    @Test
    @WithMockUser(username = "marco")
    void exportFormPreservesFiltersAndPageAfterDownload() throws Exception {
        var page = get("/transactions").param("from", "2026-10-01")
                .param("to", "2026-10-31").param("category", "Reisen");
        mvc.perform(page)
                .andExpect(xpath("//*[@data-testid='filter-form']/following-sibling::*[1][@data-testid='export-form']")
                        .exists())
                .andExpect(xpath("//*[@data-testid='export-form']/@method").string("get"))
                .andExpect(xpath("//*[@data-testid='export-form']/@action").string("/transactions/export"))
                .andExpect(xpath("//*[@data-testid='export-form']/input[@name='from']/@value").string("2026-10-01"))
                .andExpect(xpath("//*[@data-testid='export-form']/input[@name='to']/@value").string("2026-10-31"))
                .andExpect(xpath("//*[@data-testid='export-form']/input[@name='category']/@value").string("Reisen"))
                .andExpect(xpath("//*[@data-testid='export-csv']").string("Als CSV exportieren"))
                .andExpect(xpath("//*[@data-testid='filter-category']/option[@selected]/@value").string("Reisen"))
                .andExpect(xpath("//*[@data-testid='filter-summary']")
                        .string("2 Transaktionen in der Kategorie Reisen, Summe: -340.00 CHF"));
        mvc.perform(get("/transactions/export").param("from", "2026-10-01")
                .param("to", "2026-10-31").param("category", "Reisen")).andExpect(status().isOk());
        mvc.perform(page)
                .andExpect(xpath("//*[@data-testid='filter-category']/option[@selected]/@value").string("Reisen"))
                .andExpect(xpath("//*[@data-testid='filter-from']/@value").string("2026-10-01"))
                .andExpect(xpath("//*[@data-testid='filter-to']/@value").string("2026-10-31"))
                .andExpect(xpath("//*[@data-testid='filter-summary']")
                        .string("2 Transaktionen in der Kategorie Reisen, Summe: -340.00 CHF"));
    }

    @Test
    @WithMockUser(username = "anna")
    void emptyExportHasOnlyHeaderAndDisabledButtonHasTooltip() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-01")
                        .param("to", "2026-10-10").param("category", "Elektronik"))
                .andExpect(xpath("//*[@data-testid='filter-no-results']").exists())
                .andExpect(xpath("//*[@data-testid='export-csv'][@disabled]/@title")
                        .string("Keine Transaktionen zum Exportieren"));
        mvc.perform(get("/transactions/export").param("from", "2026-10-01")
                        .param("to", "2026-10-10").param("category", "Elektronik"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("\uFEFFDatum;Händler;Kategorie;Betrag\r\n"
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void unauthenticatedExportRedirectsToLoginWithoutFile() throws Exception {
        mvc.perform(get("/transactions/export"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(header()
                        .doesNotExist("Content-Disposition"));
    }

}
