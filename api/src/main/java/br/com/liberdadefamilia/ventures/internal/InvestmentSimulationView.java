package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import java.util.List;

/** The outcome of each scenario, in the order they were sent. */
record InvestmentSimulationView(List<ScenarioView> scenarios) {

    record ScenarioView(String name, Money finalBalance, Money totalContributed, Money earnings) {}
}
