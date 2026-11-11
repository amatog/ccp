package ch.amato.ccp;

import ch.amato.ccp.service.DisputeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DisputeControllerTest {
    @Autowired MockMvc mvc;
    @Autowired DisputeService disputes;
    private static final String URL = "/transactions/A-1005/dispute";
    private static final String ROW = "//*[@data-transaction-id='A-1005']";

    @Test
    void octoberButtonsOnlyOnDebitsAndLinksPreserveFilters() throws Exception {
        mvc.perform(get("/transactions").with(user("anna")).param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(xpath("//*[@data-testid='transactions-table']/thead/tr/th[last()]").string("Status"))
                .andExpect(xpath("//*[@data-testid='dispute-button']").nodeCount(5))
                .andExpect(xpath("//*[@data-testid='dispute-status']").doesNotExist())
                .andExpect(xpath("//*[@data-transaction-id='A-1003']//*[@data-testid='dispute-button']").doesNotExist())
                .andExpect(xpath(ROW + "//a/@href").string(URL + "?from=2026-10-01&to=2026-10-31&category="))
                .andExpect(xpath(ROW + "//a/@aria-label").string("Transaktion Digitec Galaxus vom 12.10.2026 beanstanden"));
    }

    @Test
    void formHasTransactionOptionsDisabledSubmitAndCsrf() throws Exception {
        mvc.perform(get(URL).with(user("anna")).param("from", "2026-10-01").param("category", "Elektronik"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='dispute-transaction']").string(containsString("12.10.2026 · Digitec Galaxus · -349.00 CHF")))
                .andExpect(xpath("//form[@data-testid='dispute-form'][@method='post'][@action='" + URL + "']").exists())
                .andExpect(xpath("//input[@name='_csrf']").exists())
                .andExpect(xpath("//input[@name='from']/@value").string("2026-10-01"))
                .andExpect(xpath("//*[@data-testid='dispute-reason'][@required]/option").nodeCount(4))
                .andExpect(xpath("//select/option[1][@selected]/@value").string(""))
                .andExpect(xpath("//select/option[1]").string("Bitte Grund wählen"))
                .andExpect(xpath("//select/option[2]").string("Unbekannte Transaktion"))
                .andExpect(xpath("//select/option[3]").string("Doppelt belastet"))
                .andExpect(xpath("//select/option[4]").string("Falscher Betrag"))
                .andExpect(xpath("//*[@data-testid='dispute-comment'][@maxlength='500']").string(""))
                .andExpect(xpath("//*[@data-testid='dispute-submit'][@disabled]").exists())
                .andExpect(xpath("//*[@data-testid='dispute-cancel']/@href")
                        .string("/transactions?from=2026-10-01&to=&category=Elektronik"))
                .andExpect(content().string(containsString("reason.addEventListener('change', update)")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "INVALID", "wrong_amount"})
    void invalidReasonShows400WithoutSaving(String reason) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post(URL).with(user("anna")).with(csrf()).session(session).param("reason", reason))
                .andExpect(status().isBadRequest())
                .andExpect(xpath("//*[@data-testid='dispute-reason-error']").string("Bitte einen Grund wählen."))
                .andExpect(xpath("//*[@data-testid='dispute-submit'][@disabled]").exists());
        assertTrue(disputes.disputedIds(session).isEmpty());
    }

    @Test
    void missingReasonAndTooLongCommentAreRejectedAndFormPreservesValues() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post(URL).with(user("anna")).with(csrf()).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(xpath("//*[@data-testid='dispute-reason-error']").exists());
        mvc.perform(post(URL).with(user("anna")).with(csrf()).session(session)
                        .param("reason", "WRONG_AMOUNT").param("comment", "x".repeat(501))
                        .param("category", "Elektronik"))
                .andExpect(status().isBadRequest())
                .andExpect(xpath("//*[@data-testid='dispute-comment-error']")
                        .string("Der Kommentar darf höchstens 500 Zeichen lang sein."))
                .andExpect(xpath("//*[@data-testid='dispute-comment']").string("x".repeat(501)))
                .andExpect(xpath("//option[@selected]/@value").string("WRONG_AMOUNT"))
                .andExpect(xpath("//input[@name='category']/@value").string("Elektronik"));
        assertTrue(disputes.disputedIds(session).isEmpty());
    }

    @Test
    void successFlashIsConsumedStatusPersistsAcrossReloadFilterResetAndExport() throws Exception {
        var session = new MockHttpSession();
        var result = mvc.perform(post(URL).with(user("anna")).with(csrf()).session(session)
                        .param("reason", "WRONG_AMOUNT").param("comment", "Laut Quittung 249.00 CHF")
                        .param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(redirectedUrl("/transactions?from=2026-10-01&to=2026-10-31"))
                .andExpect(flash().attribute("disputeSuccess",
                        "Ihre Beanstandung wurde erfasst. Die Transaktion ist jetzt in Abklärung.")).andReturn();
        mvc.perform(get("/transactions?from=2026-10-01&to=2026-10-31").with(user("anna")).session(session))
                .andExpect(xpath("//*[@data-testid='dispute-success'][@role='status']").exists())
                .andExpect(xpath(ROW + "//*[@data-testid='dispute-status']").string("In Abklärung"))
                .andExpect(xpath(ROW + "//*[@data-testid='dispute-button']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='filter-summary']").string("6 Transaktionen im Zeitraum, Summe: -628.95 CHF"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-1'382.07 CHF"))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("142 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("13 Gutschriften"));
        mvc.perform(get("/transactions/export").with(user("anna")).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("12.10.2026;Digitec Galaxus;Elektronik;-349.00")));
        for (String query : new String[]{"", "?category=Elektronik"}) {
            mvc.perform(get("/transactions" + query).with(user("anna")).session(session))
                    .andExpect(xpath(ROW + "//*[@data-testid='dispute-status']").exists())
                    .andExpect(xpath("//*[@data-testid='dispute-success']").doesNotExist());
        }
        assertFalse(result.getFlashMap().isEmpty());
    }

    @Test
    void optionalCommentAndMarcoFiltersKeepSummaryAndCreditEmpty() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/transactions/M-2002/dispute").with(user("marco")).with(csrf()).session(session)
                        .param("reason", "DUPLICATE_CHARGE").param("from", "2026-10-01")
                        .param("to", "2026-10-31").param("category", "Reisen"))
                .andExpect(redirectedUrl("/transactions?from=2026-10-01&to=2026-10-31&category=Reisen"));
        mvc.perform(get("/transactions").with(user("marco")).session(session).param("from", "2026-10-01")
                        .param("to", "2026-10-31").param("category", "Reisen"))
                .andExpect(xpath("//*[@data-testid='filter-category']/option[@selected]/@value").string("Reisen"))
                .andExpect(xpath("//*[@data-testid='filter-summary']").string("2 Transaktionen in der Kategorie Reisen, Summe: -340.00 CHF"))
                .andExpect(xpath("//*[@data-transaction-id='M-2002']//*[@data-testid='dispute-status']").exists())
                .andExpect(xpath("//*[@data-transaction-id='M-2003']/td[last()]/*").doesNotExist());
        mvc.perform(post("/transactions/A-1002/dispute").with(user("anna")).with(csrf())
                        .param("reason", "UNKNOWN_TRANSACTION")).andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("disputeSuccess"));
    }

    @Test
    void getAndPostBlockCreditsAndDuplicatesWithFilters() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post(URL).with(user("anna")).with(csrf()).session(session).param("reason", "WRONG_AMOUNT"));
        for (boolean post : new boolean[]{false, true}) {
            for (String id : new String[]{"A-1003", "A-1005"}) {
                var request = post ? post("/transactions/" + id + "/dispute").with(csrf())
                        : get("/transactions/" + id + "/dispute");
                mvc.perform(request.with(user("anna")).session(session).param("category", "Mobilität"))
                        .andExpect(redirectedUrl("/transactions?category=Mobilit%C3%A4t"))
                        .andExpect(flash().attribute("disputeError", id.equals("A-1003")
                                ? "Gutschriften können nicht beanstandet werden." : "Diese Transaktion ist bereits in Abklärung."));
            }
        }
        mvc.perform(get(java.net.URI.create("/transactions?category=Mobilit%C3%A4t")).with(user("anna")).session(session))
                .andExpect(xpath("//*[@data-testid='dispute-error'][@role='alert']").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"M-2002", "unknown"})
    void getAndPostForForeignOrUnknownTransactionsReturn404(String id) throws Exception {
        mvc.perform(get("/transactions/" + id + "/dispute").with(user("anna")))
                .andExpect(status().isNotFound()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("dispute-form"))));
        mvc.perform(post("/transactions/" + id + "/dispute").with(user("anna")).with(csrf())
                        .param("reason", "WRONG_AMOUNT")).andExpect(status().isNotFound());
    }

    @Test
    void cancelReturnsToFiltersWithoutSaving() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get(URL).with(user("anna")).session(session)
                        .param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@data-testid='dispute-cancel']/@href")
                        .string("/transactions?from=2026-10-01&to=2026-10-31&category="));
        mvc.perform(get("/transactions?from=2026-10-01&to=2026-10-31&category=")
                        .with(user("anna")).session(session))
                .andExpect(xpath(ROW + "//*[@data-testid='dispute-button']").exists())
                .andExpect(xpath(ROW + "//*[@data-testid='dispute-status']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='dispute-success']").doesNotExist())
                .andExpect(xpath("//*[@data-testid='filter-from']/@value").string("2026-10-01"))
                .andExpect(xpath("//*[@data-testid='filter-to']/@value").string("2026-10-31"));
        assertTrue(disputes.disputedIds(session).isEmpty());
    }

    @Test
    void authenticationCsrfAndLogoutIsolation() throws Exception {
        mvc.perform(get(URL)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post(URL).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post(URL).with(user("anna")).param("reason", "WRONG_AMOUNT")).andExpect(status().isForbidden());
        var session = new MockHttpSession();
        mvc.perform(post(URL).with(user("anna")).with(csrf()).session(session).param("reason", "WRONG_AMOUNT"));
        mvc.perform(post("/logout").with(user("anna")).with(csrf()).session(session))
                .andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid());
        mvc.perform(get("/transactions").with(user("anna")).session(new MockHttpSession()))
                .andExpect(xpath(ROW + "//*[@data-testid='dispute-button']").exists())
                .andExpect(xpath(ROW + "//*[@data-testid='dispute-status']").doesNotExist());
    }
}
