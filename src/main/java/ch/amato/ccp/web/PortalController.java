package ch.amato.ccp.web;

import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.service.TransactionService;
import ch.amato.ccp.service.TransactionCsvExporter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import ch.amato.ccp.service.LoginHistoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class PortalController {

    private final TransactionService transactionService;
    private final TransactionCsvExporter csvExporter;
    private final Clock clock;

    public PortalController(TransactionService transactionService, TransactionCsvExporter csvExporter, Clock clock) {
        this.transactionService = transactionService;
        this.csvExporter = csvExporter;
        this.clock = clock;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/transactions";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/transactions")
    public String transactions(Authentication authentication, Model model, HttpSession session,
                               @RequestParam(defaultValue = "") String from,
                               @RequestParam(defaultValue = "") String to,
                               @RequestParam(defaultValue = "") String category) {
        String username = authentication.getName();
        Selection selection = selectTransactions(username, from, to, category);
        List<Transaction> allTransactions = selection.allTransactions();
        List<Transaction> transactions = selection.transactions();
        boolean filterActive = !from.isEmpty() || !to.isEmpty() || !category.isEmpty();
        String filterError = selection.error();

        model.addAttribute("lastLogin", LastLoginFormatter.format(
                (Instant) session.getAttribute(LoginHistoryService.PREVIOUS_LOGIN)));
        model.addAttribute("categories", transactionService.categories());
        model.addAttribute("category", category);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("filterActive", filterActive);
        model.addAttribute("filterValid", filterActive && filterError == null);
        model.addAttribute("filterError", filterError);
        model.addAttribute("filterSum", transactionService.balance(transactions));
        model.addAttribute("hasTransactions", !allTransactions.isEmpty());
        model.addAttribute("customer", transactionService.customer(username));
        model.addAttribute("transactions", transactions);
        model.addAttribute("sumDebits", transactionService.total(transactions, Transaction.Type.DEBIT));
        model.addAttribute("sumCredits", transactionService.total(transactions, Transaction.Type.CREDIT));
        model.addAttribute("sumNet", transactionService.netSpending(transactions));
        model.addAttribute("balance", transactionService.balance(allTransactions));
        model.addAttribute("debitCountLabel",
                transactionService.transactionCountLabel(allTransactions, Transaction.Type.DEBIT));
        model.addAttribute("creditCountLabel",
                transactionService.transactionCountLabel(allTransactions, Transaction.Type.CREDIT));
        return "transactions";
    }

    @GetMapping("/transactions/export")
    public ResponseEntity<byte[]> export(Authentication authentication,
                                        @RequestParam(defaultValue = "") String from,
                                        @RequestParam(defaultValue = "") String to,
                                        @RequestParam(defaultValue = "") String category) {
        Selection selection = selectTransactions(authentication.getName(), from, to, category);
        LocalDate exportDate = LocalDate.now(clock.withZone(ZoneId.of("Europe/Zurich")));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transaktionen_" + exportDate + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csvExporter.export(selection.transactions()));
    }

    private Selection selectTransactions(String username, String from, String to, String category) {
        List<Transaction> allTransactions = transactionService.transactionsFor(username);
        List<Transaction> transactions = allTransactions;
        boolean filterActive = !from.isEmpty() || !to.isEmpty() || !category.isEmpty();
        String filterError = null;
        try {
            LocalDate fromDate = from.isEmpty() ? null : LocalDate.parse(from);
            LocalDate toDate = to.isEmpty() ? null : LocalDate.parse(to);
            if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
                filterError = "Das Von-Datum darf nicht nach dem Bis-Datum liegen.";
            } else if (!category.isEmpty() && !transactionService.categories().contains(category)) {
                filterError = "Unbekannte Kategorie.";
            } else if (filterActive) {
                transactions = transactionService.transactionsFor(username, fromDate, toDate, category);
            }
        } catch (DateTimeParseException exception) {
            filterError = "Ungültiges Datum. Bitte im Format TT.MM.JJJJ wählen.";
        }

        return new Selection(allTransactions, transactions, filterError);
    }

    private record Selection(List<Transaction> allTransactions, List<Transaction> transactions, String error) {
    }

}
