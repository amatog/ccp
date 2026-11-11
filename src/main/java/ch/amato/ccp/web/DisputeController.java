package ch.amato.ccp.web;

import ch.amato.ccp.model.DisputeReason;
import ch.amato.ccp.model.Transaction;
import ch.amato.ccp.service.DisputeService;
import ch.amato.ccp.service.TransactionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/transactions/{id}/dispute")
public class DisputeController {
    private final TransactionService transactions;
    private final DisputeService disputes;

    public DisputeController(TransactionService transactions, DisputeService disputes) {
        this.transactions = transactions;
        this.disputes = disputes;
    }

    @GetMapping
    public String form(@PathVariable String id, Authentication authentication, HttpSession session,
                       @RequestParam(defaultValue = "") String from,
                       @RequestParam(defaultValue = "") String to,
                       @RequestParam(defaultValue = "") String category,
                       Model model, RedirectAttributes flash) {
        Transaction transaction = ownTransaction(id, authentication);
        String blocked = disputes.blockedReason(transaction, session);
        if (blocked != null) return blockedRedirect(blocked, from, to, category, flash);
        return showForm(transaction, from, to, category, null, "", model);
    }

    @PostMapping
    public String submit(@PathVariable String id, Authentication authentication, HttpSession session,
                         @RequestParam(defaultValue = "") String from,
                         @RequestParam(defaultValue = "") String to,
                         @RequestParam(defaultValue = "") String category,
                         @RequestParam(required = false) String reason,
                         @RequestParam(defaultValue = "") String comment,
                         Model model, RedirectAttributes flash, HttpServletResponse response) {
        Transaction transaction = ownTransaction(id, authentication);
        String blocked = disputes.blockedReason(transaction, session);
        if (blocked != null) return blockedRedirect(blocked, from, to, category, flash);
        DisputeReason parsed = DisputeReason.parse(reason);
        if (parsed == null || comment.length() > 500) {
            if (parsed == null) model.addAttribute("reasonError", "Bitte einen Grund wählen.");
            if (comment.length() > 500) model.addAttribute("commentError",
                    "Der Kommentar darf höchstens 500 Zeichen lang sein.");
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return showForm(transaction, from, to, category, parsed, comment, model);
        }
        blocked = disputes.submit(transaction, parsed, comment, session);
        if (blocked != null) return blockedRedirect(blocked, from, to, category, flash);
        flash.addFlashAttribute("disputeSuccess",
                "Ihre Beanstandung wurde erfasst. Die Transaktion ist jetzt in Abklärung.");
        return redirect(from, to, category);
    }

    private Transaction ownTransaction(String id, Authentication authentication) {
        return transactions.transactionsFor(authentication.getName()).stream()
                .filter(transaction -> transaction.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String showForm(Transaction transaction, String from, String to, String category,
                            DisputeReason reason, String comment, Model model) {
        model.addAttribute("transaction", transaction);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("category", category);
        model.addAttribute("reasons", DisputeReason.values());
        model.addAttribute("reason", reason);
        model.addAttribute("comment", comment);
        return "dispute";
    }

    private String blockedRedirect(String message, String from, String to, String category,
                                   RedirectAttributes flash) {
        flash.addFlashAttribute("disputeError", message);
        return redirect(from, to, category);
    }

    private String redirect(String from, String to, String category) {
        var builder = UriComponentsBuilder.fromPath("/transactions");
        if (!from.isEmpty()) builder.queryParam("from", from);
        if (!to.isEmpty()) builder.queryParam("to", to);
        if (!category.isEmpty()) builder.queryParam("category", category);
        return "redirect:" + builder.build().encode().toUriString();
    }
}
