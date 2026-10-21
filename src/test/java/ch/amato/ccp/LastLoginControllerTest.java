package ch.amato.ccp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LastLoginControllerTest {

    private static final String LAST_LOGIN = "//*[@data-testid='account-summary']//*[@data-testid='last-login']";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestClock clock;

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    static class TestClock extends Clock {
        private Instant now = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private void at(String time) {
        clock.now = Instant.parse("2026-10-20T" + time + ":00Z");
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").user(username).password("demo1234"))
                .andExpect(redirectedUrl("/transactions"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void onlySuccessfulLoginsOfSameUserCount() throws Exception {
        at("08:00"); // 10:00 Europe/Zurich
        var anna = login("anna");
        mvc.perform(get("/transactions").session(anna))
                .andExpect(status().isOk())
                .andExpect(xpath(LAST_LOGIN).string("Erste Anmeldung"));
        at("08:05");
        mvc.perform(formLogin("/login").user("anna").password("falsch"))
                .andExpect(redirectedUrl("/login?error"));
        at("08:06");
        var marco = login("marco");
        mvc.perform(get("/transactions").session(marco))
                .andExpect(xpath(LAST_LOGIN).string("Erste Anmeldung"));
        at("08:07");
        mvc.perform(post("/logout").session(anna).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        at("08:10");
        var nextAnna = login("anna");
        mvc.perform(get("/transactions").session(nextAnna).param("username", "marco"))
                .andExpect(status().isOk())
                .andExpect(xpath(LAST_LOGIN).string("20.10.2026, 10:00"));
    }

    @Test
    void noraSummaryAndPreviousLoginRemainStableAcrossRequestsAndOtherSessions() throws Exception {
        at("13:42");
        var first = login("nora");
        mvc.perform(get("/transactions").session(first))
                .andExpect(xpath(LAST_LOGIN).string("Erste Anmeldung"));
        mvc.perform(post("/logout").session(first).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        at("13:43");
        var second = login("nora");
        mvc.perform(get("/transactions").session(second))
                .andExpect(status().isOk())
                .andExpect(xpath(LAST_LOGIN).string("20.10.2026, 15:42"))
                .andExpect(xpath("//*[@data-testid='transaction-counts']/following-sibling::*[1]/*[@class='label']")
                        .string("Letzte Anmeldung"))
                .andExpect(xpath("//*[@data-testid='customer-name']").string("Nora Keller"))
                .andExpect(xpath("//*[@data-testid='card-number']").string("**** **** **** 5151"))
                .andExpect(xpath("//*[@data-testid='balance-total']").string("-22.50 CHF"))
                .andExpect(xpath("//*[@data-testid='count-debits']").string("2 Belastungen"))
                .andExpect(xpath("//*[@data-testid='count-credits']").string("1 Gutschrift"))
                .andExpect(xpath("//*[@data-testid='transaction-row']").nodeCount(3))
                .andExpect(xpath("(//*[@data-testid='transaction-row'])[1]/@data-transaction-id").string("N-4003"))
                .andExpect(xpath("(//*[@data-testid='transaction-row'])[2]/@data-transaction-id").string("N-4002"))
                .andExpect(xpath("(//*[@data-testid='transaction-row'])[3]/@data-transaction-id").string("N-4001"));
        at("13:44");
        login("nora"); // Eine andere Sitzung darf den festgehaltenen Wert nicht verändern.
        mvc.perform(get("/transactions").session(second))
                .andExpect(xpath(LAST_LOGIN).string("20.10.2026, 15:42"));
        mvc.perform(get("/transactions").session(second).param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(xpath(LAST_LOGIN).string("20.10.2026, 15:42"));
        mvc.perform(get("/transactions").session(second))
                .andExpect(xpath(LAST_LOGIN).string("20.10.2026, 15:42"));
    }
}
