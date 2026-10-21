package ch.amato.ccp.web;

import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.service.TransactionService;
import ch.amato.ccp.service.LoginHistoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class PortalController {

    private final TransactionService transactionService;

    public PortalController(TransactionService transactionService) {
        this.transactionService = transactionService;
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
        model.addAttribute("balance", transactionService.balance(allTransactions));
        model.addAttribute("debitCountLabel",
                transactionService.transactionCountLabel(allTransactions, Transaction.Type.DEBIT));
        model.addAttribute("creditCountLabel",
                transactionService.transactionCountLabel(allTransactions, Transaction.Type.CREDIT));
        return "transactions";
    }
}
