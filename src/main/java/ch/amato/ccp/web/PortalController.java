package ch.amato.ccp.web;

import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.service.TransactionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
    public String transactions(Authentication authentication, Model model) {
        String username = authentication.getName();
        List<Transaction> transactions = transactionService.transactionsFor(username);

        model.addAttribute("customer", transactionService.customer(username));
        model.addAttribute("transactions", transactions);
        model.addAttribute("balance", transactionService.balance(transactions));
        return "transactions";
    }
}
