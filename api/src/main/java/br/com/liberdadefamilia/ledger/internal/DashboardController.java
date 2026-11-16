package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.time.YearMonth;
import java.util.Currency;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The month at a glance: what the family spent in it, and what it has now. Both are in the family's base currency;
 * accounts and expenses in other currencies are left out rather than mixed in.
 */
@RestController
class DashboardController {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    DashboardController(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @GetMapping("/v1/workspaces/{ws}/dashboard")
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    DashboardView dashboard(@RequestParam YearMonth month, WorkspaceMember member) {
        Currency base = member.baseCurrency();
        Money expenses = transactions.expenses(
                member.workspaceId(), base, month.atDay(1), month.plusMonths(1).atDay(1));
        Money balance = accounts.allWithBalances(member.workspaceId()).stream()
                .filter(account -> account.archivedAt() == null)
                .map(AccountView::balance)
                .filter(money -> money.currency().equals(base))
                .reduce(Money.zero(base), Money::plus);
        return new DashboardView(month.toString(), expenses, balance);
    }

    /** @param month the month asked for, as {@code yyyy-MM} */
    record DashboardView(String month, Money expenses, Money balance) {}
}
