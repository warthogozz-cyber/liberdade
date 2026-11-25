package br.com.liberdadefamilia.ventures.internal;

import java.math.BigDecimal;
import java.util.List;

/**
 * The checks on what the caller sends: lengths, ceilings and counts. A wrong field is a 422 whose detail says which
 * one. Nothing here puts a value or a name in a message.
 */
final class VentureValidation {

    static final int MAX_ITEMS = 20;
    static final int MAX_PRODUCTS = 10;
    static final int MAX_UNITS = 1_000_000;

    /** The largest amount, the same ceiling a transaction has. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private static final int MAX_NAME = 40;

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

    private static void requireName(String name, String detail) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME || name.indexOf('\0') >= 0) {
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
