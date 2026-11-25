package br.com.liberdadefamilia.ventures.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** The investment simulator's inputs (D3): up to 3 scenarios, each a name and a yearly rate in percent. */
record InvestmentSimulationRequest(
        @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "5000.00")
        BigDecimal initialAmount,

        @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "200.00")
        BigDecimal monthlyContribution,

        int years,
        List<ScenarioInput> scenarios) {

    /** A scenario: a name of 1 to 30 characters and a yearly rate from 0 to 100 with up to 2 decimals. */
    record ScenarioInput(
            String name,

            @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "10.50")
            BigDecimal annualPercent) {

        ScenarioInput {
            if (name != null) {
                name = name.strip();
            }
        }
    }
}
