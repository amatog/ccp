package ch.amato.ccp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
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

    @Test
    @WithMockUser(username = "anna")
    void everyCategoryCellContainsExactlyOneBadge() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(xpath("//td[@data-testid='tx-category']").nodeCount(6))
                .andExpect(xpath("//td[@data-testid='tx-category'][count(*)=1"
                        + " and count(span[@data-testid='category-badge'])=1]").nodeCount(6));
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
                        .string("5 Belastungen"))
                .andExpect(xpath("//*[@data-testid='account-summary']//*[@data-testid='count-credits']")
                        .string("1 Gutschrift"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-628.95 CHF"))
                .andExpect(xpath("//*[@data-testid='balance-total']/../following-sibling::*[1]"
                        + "[@data-testid='transaction-counts']/*[@class='label']").string("Buchungen"));
    }

    @Test
    @WithMockUser(username = "marco")
    void marcoSeesOnlyHisBookingCounts() throws Exception {
        mvc.perform(get("/transactions").param("username", "anna"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='count-debits']").string("3 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("1 Gutschrift"));
    }

    @Test
    @WithMockUser(username = "anna")
    void dateFilterDoesNotChangeBookingCountsOrTotalBalance() throws Exception {
        mvc.perform(get("/transactions").param("from", "2026-10-06").param("to", "2026-10-06"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(1))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("5 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("1 Gutschrift"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-628.95 CHF"));
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
            ", 2026-10-05, 2, '2 Transaktionen im Zeitraum, Summe: -146.35 CHF'"
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
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(6))
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
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(6))
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
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-488.90 CHF"))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(count))
                .andExpect(xpath("//*[@data-testid='transaction-row'][starts-with(@data-transaction-id, 'A-')]")
                        .doesNotExist());
    }
}
