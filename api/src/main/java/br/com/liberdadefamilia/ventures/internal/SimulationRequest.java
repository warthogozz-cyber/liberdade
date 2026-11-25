package br.com.liberdadefamilia.ventures.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** The numbers on the project form, to see the figures before saving (D9). Lists may be empty; nothing is stored. */
record SimulationRequest(
        List<SimulationCost> initialItems, List<SimulationCost> fixedItems, List<SimulationProduct> products) {

    /** A cost line: a decimal text such as "12.50". */
    record SimulationCost(
            @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "12.50")
            BigDecimal amount) {}

    /** A product: amounts are decimal texts such as "4.50". */
    record SimulationProduct(
            @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "1.80")
            BigDecimal unitVariableCost,

            @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "4.50")
            BigDecimal unitPrice,

            int plannedUnitsMonth) {}
}
