package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureFigures;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The checks on what the caller sends: lengths, ceilings and counts. A wrong field is a 422 whose detail says which
 * one. Nothing here puts a value or a name in a message.
 */
final class VentureValidation {

    static final int MAX_ITEMS = 20;
    static final int MAX_PRODUCTS = 10;
    static final int MAX_UNITS = 1_000_000;
    static final int MAX_STEPS = 20;

    /** The largest amount, the same ceiling a transaction has. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private static final int MAX_NAME = 40;
    private static final int MAX_SCENARIO_NAME = 30;
    private static final int MAX_SCENARIOS = 3;
    private static final int MAX_YEARS = 50;
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    /** What a {@code Money} can hold; a result at or above it cannot be answered. */
    private static final BigDecimal MONEY_LIMIT = BigDecimal.TEN.pow(15);

    private static final int MAX_STEP_TITLE = 80;

    private VentureValidation() {}

    static void projectName(String name) {
        requireName(name, "O nome do projeto precisa ter de 1 a 40 caracteres.");
    }

    static void itemName(String name) {
        requireName(name, "O nome do item precisa ter de 1 a 40 caracteres.");
    }

    static void productName(String name) {
        requireName(name, "O nome do produto precisa ter de 1 a 40 caracteres.");
    }

    /** A cost line amount: above zero, up to the ceiling, in cents. */
    static void itemAmount(BigDecimal amount) {
        requireAmount(
                amount, true, "O valor do item precisa ser maior que zero, ter até 2 casas e até 999.999.999,99.");
    }

    /** A unit cost: zero or more. */
    static void unitCost(BigDecimal amount) {
        requireAmount(
                amount, false, "O custo por unidade precisa ser zero ou mais, ter até 2 casas e até 999.999.999,99.");
    }

    /** A unit price: above zero. */
    static void unitPrice(BigDecimal amount) {
        requireAmount(amount, true, "O preço precisa ser maior que zero, ter até 2 casas e até 999.999.999,99.");
    }

    static void plannedUnits(int units) {
        if (units < 0 || units > MAX_UNITS) {
            throw VentureRejectedException.invalid("A venda prevista precisa ficar entre 0 e 1.000.000 por mês.");
        }
    }

    static void itemCount(List<?> items) {
        if (items == null || items.size() > MAX_ITEMS || items.contains(null)) {
            throw VentureRejectedException.invalid("Cada lista de custos aceita até 20 itens.");
        }
    }

    static void productCount(List<?> products) {
        if (products == null || products.size() > MAX_PRODUCTS || products.contains(null)) {
            throw VentureRejectedException.invalid("O projeto aceita até 10 produtos.");
        }
    }

    /** A step title: 1 to 80 characters, not blank. */
    static void stepTitle(String title) {
        if (title == null || title.isBlank() || title.length() > MAX_STEP_TITLE || title.indexOf('\0') >= 0) {
            throw VentureRejectedException.invalid("O passo precisa ter de 1 a 80 caracteres.");
        }
    }

    /** The investment simulator's inputs (D3); any other value is a 422 {@code investment-simulation.invalid}. */
    static void investment(
            BigDecimal initial,
            BigDecimal monthly,
            int years,
            List<InvestmentSimulationRequest.ScenarioInput> scenarios) {
        if (!validAmount(initial) || !validAmount(monthly)) {
            throw VentureRejectedException.investmentInvalid(
                    "Os valores precisam ser zero ou mais, ter até 2 casas e até 999.999.999,99.");
        }
        if (years < 1 || years > MAX_YEARS) {
            throw VentureRejectedException.investmentInvalid("O prazo precisa ficar entre 1 e 50 anos.");
        }
        if (scenarios == null || scenarios.isEmpty() || scenarios.size() > MAX_SCENARIOS || scenarios.contains(null)) {
            throw VentureRejectedException.investmentInvalid("A simulação aceita de 1 a 3 cenários.");
        }
        for (InvestmentSimulationRequest.ScenarioInput scenario : scenarios) {
            String name = scenario.name();
            if (name == null || name.isEmpty() || name.length() > MAX_SCENARIO_NAME || name.indexOf('\0') >= 0) {
                throw VentureRejectedException.investmentInvalid("O nome do cenário precisa ter de 1 a 30 caracteres.");
            }
            BigDecimal percent = scenario.annualPercent();
            if (percent == null
                    || percent.signum() < 0
                    || percent.compareTo(MAX_PERCENT) > 0
                    || percent.stripTrailingZeros().scale() > 2) {
                throw VentureRejectedException.investmentInvalid(
                        "A taxa ao ano precisa ficar entre 0 e 100, com até 2 casas.");
            }
        }
    }

    /** A result the answer cannot hold: the inputs are in range but the growth is not. */
    static void investmentResult(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value.abs().compareTo(MONEY_LIMIT) >= 0) {
                throw VentureRejectedException.investmentInvalid(
                        "Com esses números o resultado fica grande demais. Reduza o valor, o prazo ou a taxa.");
            }
        }
    }

    /** The figures of a project the answer cannot hold: the inputs are in range but their sums are not. */
    static void figuresResult(VentureFigures figures) {
        List<BigDecimal> values = new ArrayList<>(List.of(
                figures.initialInvestment(),
                figures.fixedMonthly(),
                figures.plannedRevenue(),
                figures.plannedCost(),
                figures.totalMargin(),
                figures.monthlyProfit()));
        if (figures.breakEven().revenue() != null) {
            values.add(figures.breakEven().revenue());
        }
        figures.products().forEach(product -> values.add(product.margin()));
        for (BigDecimal value : values) {
            if (value.abs().compareTo(MONEY_LIMIT) >= 0) {
                throw VentureRejectedException.invalid(
                        "Com esses números o resultado fica grande demais. Confira os valores.");
            }
        }
    }

    private static boolean validAmount(BigDecimal amount) {
        return amount != null
                && amount.signum() >= 0
                && amount.compareTo(MAX_AMOUNT) <= 0
                && amount.stripTrailingZeros().scale() <= 2;
    }

    private static void requireName(String name, String detail) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME || name.indexOf('\0') >= 0) {
            throw VentureRejectedException.invalid(detail);
        }
    }

    private static void requireAmount(BigDecimal amount, boolean aboveZero, String detail) {
        if (amount == null
                || (aboveZero ? amount.signum() <= 0 : amount.signum() < 0)
                || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw VentureRejectedException.invalid(detail);
        }
    }
}
