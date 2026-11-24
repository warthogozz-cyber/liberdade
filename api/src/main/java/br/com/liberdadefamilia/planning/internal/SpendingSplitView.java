package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import java.time.YearMonth;

/**
 * The month's confirmed spending (D14). Fixed came from a recurring rule, or is in a fixed category or the child of
 * one; variable is the rest.
 */
record SpendingSplitView(YearMonth month, Money fixed, Money variable) {}
