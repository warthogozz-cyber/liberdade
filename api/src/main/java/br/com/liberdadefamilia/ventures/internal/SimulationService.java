package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.domain.CostLine;
import br.com.liberdadefamilia.ventures.domain.InvestmentOutcome;
import br.com.liberdadefamilia.ventures.domain.InvestmentScenario;
import br.com.liberdadefamilia.ventures.domain.InvestmentSimulator;
import br.com.liberdadefamilia.ventures.domain.ProductLine;
import br.com.liberdadefamilia.ventures.domain.VentureCalculator;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Service;

/** The two previews. They read nothing, write nothing and publish nothing: the numbers come in the request. */
@Service
class SimulationService {

    /**
     * The project's figures from the numbers on the form, in the family's currency, validated like the PUT.
     *
     * @throws VentureRejectedException when a value or a list is out of its limits
     */
    FiguresView simulate(WorkspaceMember member, SimulationRequest request) {
        VentureValidation.itemCount(request.initialItems());
        VentureValidation.itemCount(request.fixedItems());
        VentureValidation.productCount(request.products());
        request.initialItems().forEach(item -> VentureValidation.itemAmount(item.amount()));
        request.fixedItems().forEach(item -> VentureValidation.itemAmount(item.amount()));
        request.products().forEach(product -> {
            VentureValidation.unitCost(product.unitVariableCost());
            VentureValidation.unitPrice(product.unitPrice());
            VentureValidation.plannedUnits(product.plannedUnitsMonth());
        });
        return FiguresView.of(
                VentureCalculator.calculate(
                        request.initialItems().stream()
                                .map(item -> new CostLine(item.amount()))
                                .toList(),
                        request.fixedItems().stream()
                                .map(item -> new CostLine(item.amount()))
                                .toList(),
                        request.products().stream()
                                .map(product -> new ProductLine(
                                        product.unitVariableCost(), product.unitPrice(), product.plannedUnitsMonth()))
                                .toList()),
                member.baseCurrency());
    }

    /**
     * The compound growth of each scenario, in the family's currency and the order sent.
     *
     * @throws VentureRejectedException when an input is out of its limits or a result is too big to answer
     */
    InvestmentSimulationView simulateInvestment(WorkspaceMember member, InvestmentSimulationRequest request) {
        VentureValidation.investment(
                request.initialAmount(), request.monthlyContribution(), request.years(), request.scenarios());
        List<InvestmentOutcome> outcomes = InvestmentSimulator.simulate(
                request.initialAmount(),
                request.monthlyContribution(),
                request.years(),
                request.scenarios().stream()
                        .map(scenario -> new InvestmentScenario(scenario.name(), scenario.annualPercent()))
                        .toList());
        Currency currency = member.baseCurrency();
        return new InvestmentSimulationView(outcomes.stream()
                .map(outcome -> {
                    VentureValidation.investmentResult(
                            outcome.finalBalance(), outcome.totalContributed(), outcome.earnings());
                    return new InvestmentSimulationView.ScenarioView(
                            outcome.name(),
                            Money.of(outcome.finalBalance(), currency),
                            Money.of(outcome.totalContributed(), currency),
                            Money.of(outcome.earnings(), currency));
                })
                .toList());
    }
}
