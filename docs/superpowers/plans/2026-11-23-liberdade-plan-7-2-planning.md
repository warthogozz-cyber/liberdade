# Liberdade · Plano 7.2: Orçamento, metas, reserva e projeção — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a segunda das três entregas da v0.2.
- Dono e Adulto definem um limite por categoria de gasto em cada mês. A tela, o card no Início e um
  toast gentil mostram o uso, e no começo de um mês novo o card resume o anterior e oferece copiar
  os limites (US-20).
- A família cria metas com alvo, prazo e ícone, aporta ou vincula uma conta, vê o aporte sugerido e
  celebra quando atinge. O plano Grátis tem até 3 metas ativas (US-21).
- Contas marcadas como reserva mostram quantos meses de gasto a família tem guardados e quanto
  falta para a meta (US-22).
- O Início mostra o saldo previsto para o fim deste mês e dos 3 seguintes, e os gastos fixos e
  variáveis do mês (US-29).
- O item **Planejar** entra na barra de baixo. Com o módulo desligado, tudo isso some e o núcleo
  continua inteiro (S2).

**Architecture:**
- **`ledger` (núcleo):** ganha `accounts.is_reserve` e `categories.is_fixed` nos formulários, os
  eventos `TransactionChanged` e `AccountChanged` e uma porta de leitura, `LedgerFigures`, com as
  somas que o `planning` precisa. O `ledger` continua sem saber nada de orçamento ou metas.
- **`planning`:** orçamentos, metas e aportes, reserva, projeção e a divisão fixo/variável. As contas
  puras moram em `planning.domain`. Passa a depender de `billing` (só a interface `Plans`) para o
  limite de metas.
- **Front:**
  - a feature `planning` (opcional) traz o hub Planejar, as telas de Orçamento, Metas e Reserva e os
    cards do Início;
  - o Início (núcleo) e o layout da família (app) recebem os cards e os observadores por *slots* em
    `shared/modules`, sem importar a feature;
  - um barramento local (`shared/events`) avisa quando um lançamento ou aporte foi salvo neste
    aparelho, para o toast do orçamento e a celebração da meta.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, PostgreSQL 18 com Flyway, JdbcClient.
  Testes com Testcontainers e `MockMvcTester`. Propriedades com `SplittableRandom` (como na 7.1).
- **Web:** React 19, react-router v8, TanStack Query, react-intl, MSW, Vitest e Playwright com axe.
- **Sem dependências novas.**

**Spec:**
- [Spec do Plano 7](../specs/2026-11-23-liberdade-plan-7-planning-design.md), aprovado pelo
  mantenedor em 2026-11-23. Este plano cobre só a 7.2: a navegação de 6.1, a V17 de 6.2, as seções
  6.5 a 6.8, a parte 7.2 de 6.11, 7 e 8. É a autoridade: este plano argumenta a partir dele.
- [PRD](../../prd.md) (US-20, US-21, US-22, US-29 e as regras 7.3, 7.4 e 7.5),
  [Roadmap](../../roadmap.md), [Arquitetura](../../architecture.md),
  [Modelo de dados](../../data-model.md), [Design system](../../design-system.md).

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits (`feat(planning): ...`, `feat(ledger): ...`,
  `feat(web): ...`, `test(e2e): ...`, `docs: ...`). Cada commit termina com a linha
  `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **Isolamento (ADR-0008):** FKs compostas `(workspace_id, id)` em toda tabela nova. Toda consulta
  filtra por família. Toda rota de família usa `@WorkspaceScoped`. Recurso de outra família responde
  404, nunca 403.
- **Visibilidade do Jovem (US-10):** o Jovem não vê orçamento, reserva, projeção nem a divisão
  fixo/variável (403 nessas rotas, e o web nem chama). Vê as metas da família, mas nunca o id nem o
  nome da conta vinculada. Cada rota nova tem um teste com o Jovem.
- **Papéis:**
  - Orçamento, reserva, projeção e fixo/variável: `OWNER`, `ADULT` e `VIEWER` leem; só `OWNER` e
    `ADULT` mudam.
  - Metas: os quatro papéis leem; só `OWNER` e `ADULT` criam, editam, aportam e arquivam.
  - Marcar conta como reserva e categoria como fixa: `OWNER` e `ADULT`, como a edição de hoje.
- **Módulo:** toda rota do `planning` leva `@RequiresModule(ModuleKey.PLANNING)`. Desligado → 403
  `module.disabled`.
- **Dinheiro:** `Money` e `BigDecimal` (ADR-0006), nunca `double`. Arredondamentos só os de D10 a
  D13, cada um com o modo escrito.
- **Tempo:** sempre o `Clock` da API. "Hoje" e "mês atual" são do fuso da família. Os testes usam o
  `TestClock`.
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR. Formato → 400
  `request.invalid`, regra de negócio → 422, versão → 409, ausente → 404, plano → 403.
- **Logs:** ouvintes e jobs logam só `failure.getClass().getName()`. Nunca valores, nomes de meta,
  de categoria nem ids de pessoa.
- **Fronteiras de módulo:**
  - `planning` declara `allowedDependencies = {"workspace", "ledger", "billing", "shared"}`;
  - `ledger` continua com `{"workspace", "shared"}` e nunca depende de `planning`;
  - `billing` não depende de `planning`;
  - o `ApplicationModulesTest` passa.
- **Contrato:** toda rota nova ou mudada regenera, no mesmo commit, o `api/openapi.json`, o
  `web/src/shared/api/schema.ts` e os handlers do MSW. O `RoleMatrixIT` ganha uma linha por rota nova,
  com os quatro papéis.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `shared*` e `*.domain*`, o que inclui
  `planning.domain`.
- **Testes de propriedade (ADR-0013):** `SplittableRandom` com semente `20261102L` e 1000 casos.
- **Desempenho:** o carregamento inicial do web continua até 200 KB (`check:chunks`). Telas e cards
  do planejamento carregam sob demanda.
- **Acessibilidade:** axe no Playwright cobre o hub Planejar, Orçamento, Metas, Reserva e o Início
  com os cards. Barras de uso são `role="meter"` (ou `progressbar`) com `aria-valuenow` e rótulo; a
  cor nunca é o único sinal. A celebração respeita `prefers-reduced-motion`.
- **Offline (ADR-0007):** limites, metas, aportes e reserva exigem rede e dizem isso, como as regras
  na 7.1. As telas leem do cache quando não há rede.
- **Ética (design system §8):** sem vermelho antes de passar de 100%, sem cor de alarme na projeção,
  sem celebração no orçamento, sem contador de metas restantes, sem "última meta", sem som.
- **GitHub Actions:** nenhum workflow novo e nenhuma permissão nova.
- **Versões:** nenhuma biblioteca nova nem versão nova.
- **Ações externas:** nenhuma. O staging recebe o deploy pelo pipeline de sempre, depois do merge.

### Pré-requisitos locais

Os mesmos da 7.1: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o compose de
e2e.

## Entrega

- **PR 1 (docs):** este plano e o README dos planos. Entra antes do código.
- **PR 2 (código):** branch `feat/plan-7-2-planning`, cortada da `main` depois do PR 1. Um commit por
  task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o deploy do staging sai pelo pipeline. O mantenedor confere no staging o
  critério de saída da 7.2 (seção 8 do spec): uma família de teste define limites, lança gastos, vê o
  resumo do mês e copia os limites para o mês seguinte. Os exemplos do PRD e da projeção já passam
  como teste no CI.
- **Plano seguinte:** o da 7.3 é escrito depois que esta entrega entra na `main`.

### Itens herdados

| Item | Onde |
|---|---|
| `PATCH` de cliente antigo limpando `paymentMethod` (7.1) | Fora da 7.2. Fica anotado para a 7.3 |
| 422 antes de 404 no `PATCH` de regra (7.1) | Fora da 7.2. Fica anotado para a 7.3 |
| Dica de maiúsculas no nome da tag (7.1) | Fora da 7.2 |
| Limite de erro do `TagsTab` sob demanda (7.1) | PR próprio, com a tela de erro de rota em PT-BR |
| Folga do carregamento inicial (~9 KB) | Task 14 mede de novo; a 7.2 não pode pôr nada no chunk inicial |
| Issues #36, #38, #39 e #40 | Fora da 7.2 (infra, testes de proxy e prettier de docs) |
| Go-live da cobrança | Passos do mantenedor (Plano 6). Com a cobrança desligada, não há limite de metas |

## Decisões deste plano

A seção 11 do spec deixa estes pontos ao construtor, e o plano decide:

- **D1. V17.** `V17__planning.sql`, ids com `DEFAULT uuidv7()`:
  - `budgets (id, workspace_id, category_id, month date, limit_amount numeric(19,4), currency char(3), version bigint, created_at, updated_at)`,
    `CHECK (limit_amount > 0)`, `CHECK (extract(day FROM month) = 1)`,
    `UNIQUE (workspace_id, category_id, month)`, FK composta para `categories`.
  - `goals` como no spec 6.2: `name` com `CHECK (char_length(name) BETWEEN 1 AND 40)`,
    `target_amount > 0`, `due_on date NULL CHECK (extract(day FROM due_on) = 1)`, `icon text NOT NULL`,
    `linked_account_id uuid NULL` com FK composta `ON DELETE SET NULL (linked_account_id)`,
    `created_by` com `ON DELETE SET NULL (created_by)`, `version bigint NOT NULL DEFAULT 0`.
  - `goal_contributions (id, workspace_id, goal_id, amount, occurred_on, member_id, created_at)`,
    `CHECK (amount <> 0)`, FK composta para `goals` `ON DELETE CASCADE`, `member_id` com
    `ON DELETE SET NULL (member_id)`.
  - `accounts.is_reserve`, `categories.is_fixed`, `workspaces.reserve_target_months` e
    `workspaces.manual_monthly_cost` como no spec 6.2.
  - O CHECK de `audit_log.target_type` ganha `'GOAL'` (a V16 redefiniu a lista nas linhas 55 a 57;
    a V17 redefine de novo, com todos os valores de hoje mais `'GOAL'`).
- **D2. Flags no `ledger`.** `UpdateAccountRequest` ganha `@Nullable Boolean isReserve` e
  `UpdateCategoryRequest` ganha `@Nullable Boolean isFixed`. Nulo mantém o valor de hoje, porque
  clientes antigos não mandam o campo. `isFixed = true` numa categoria que não é `EXPENSE` → 422
  `category.fixed-not-expense` ("Só categorias de gasto podem ser fixas."). `AccountView` ganha
  `boolean isReserve` e `CategoryView` ganha `boolean isFixed`. A API aceita as flags com o módulo
  desligado; o web só mostra os interruptores com o módulo ligado.
- **D3. Eventos do `ledger`.** Records na raiz do `ledger`, só com ids:
  - `TransactionChanged(UUID workspaceId, UUID transactionId, UUID accountId, @Nullable UUID toAccountId)`,
    publicado por `record`, `update`, `confirm`, `skip`, `delete` e `restore` do
    `TransactionService`. Num `update` que troca a conta, sai um evento por conta tocada.
  - `AccountChanged(UUID workspaceId, UUID accountId)`, publicado por `update`, `setBalance`,
    `archive` e `unarchive`.
  - A 7.3 reaproveita os dois para o SSE.
- **D4. Ouvinte depois do commit sem dependência nova.** O jar `spring-modulith-events-api` (onde
  mora `@ApplicationModuleListener`) não está no projeto e a regra de versões proíbe trazê-lo. O
  `GoalAchievementListener` usa `@TransactionalEventListener(phase = AFTER_COMMIT)` com
  `@Transactional(propagation = REQUIRES_NEW)`, que é o que a anotação do Modulith faz por baixo.
  Falha é pega e logada só com `failure.getClass().getName()`; a conferência na leitura (D9) cobre o
  que escapar.
- **D5. Porta de leitura `ledger.LedgerFigures`** (interface pública na raiz, implementação em
  `ledger.internal.JdbcLedgerFigures`). Todas as somas contam só `CONFIRMED` e não excluídos, salvo
  onde está escrito:
  - `Optional<CategoryRef> category(UUID ws, UUID categoryId)`;
  - `List<CategorySpending> confirmedExpenseByCategory(UUID ws, LocalDate from, LocalDate to)`, uma
    linha por categoria com gasto, com o `parentId`;
  - `Optional<AccountRef> account(UUID ws, UUID accountId)`;
  - `BigDecimal accountBalance(UUID ws, UUID accountId)`, igual ao saldo da tela de contas;
  - `BigDecimal activeBalanceOn(UUID ws, LocalDate day)`: contas não arquivadas, data ≤ `day`;
  - `BigDecimal reserveBalanceOn(UUID ws, LocalDate day)`: contas não arquivadas com `is_reserve`,
    data ≤ `day`;
  - `List<MonthTotal> confirmedExpenseByMonth(UUID ws, YearMonth from, YearMonth to, boolean withoutRuleOnly)`,
    sempre sem transferências;
  - `Optional<LocalDate> firstConfirmedOn(UUID ws)`;
  - `SpendingSplit fixedAndVariable(UUID ws, LocalDate from, LocalDate to)`;
  - `List<ScheduledFlow> openFlowsAfter(UUID ws, LocalDate today, LocalDate until)`: os `PLANNED`
    abertos com data ≤ `until` (os vencidos também) e os `CONFIRMED` com data em `(today, until]`;
  - `Set<LocalDate> occupiedDates(UUID ws, UUID ruleId)`: todo `scheduled_on` da regra, inclusive
    das linhas excluídas.
  - Records na raiz: `CategoryRef(UUID id, @Nullable UUID parentId, TransactionKind kind, boolean archived)`,
    `CategorySpending(UUID categoryId, @Nullable UUID parentId, BigDecimal amount)`,
    `AccountRef(UUID id, String currency, boolean archived)`, `MonthTotal(YearMonth month, BigDecimal amount)`,
    `SpendingSplit(BigDecimal fixed, BigDecimal variable)`,
    `ScheduledFlow(LocalDate on, TransactionKind kind, BigDecimal amount)`.
- **D6. Orçamento na API** (`/v1/workspaces/{ws}/budgets`):
  - `GET ?month=YYYY-MM` (`OWNER`, `ADULT`, `VIEWER`) →
    `BudgetMonthView(YearMonth month, List<BudgetLineView> lines)`, com
    `BudgetLineView(UUID categoryId, Money limit, Money spent, int usagePercent, BudgetState state, @Nullable Money overBy, long version)`.
    Linhas na ordem do nome da categoria.
  - `PUT /{month}/{categoryId}` com `SetBudgetRequest(@NotNull Money limitAmount, @Nullable Long version)`
    (`OWNER`, `ADULT`). Sem linha e `version` nulo cria (201); com linha e versão igual troca (200);
    versão diferente, ou nula com linha existente → 409 `budget.version-conflict`.
  - `DELETE /{month}/{categoryId}` → 204, idempotente.
  - `POST /{month}/copy-previous` → `CopyBudgetsView(int copied)`. Copia do mês anterior só as
    categorias sem limite no mês de destino e pula as arquivadas.
  - Erros: categoria ausente ou de outra família → 404 `category.not-found`; categoria que não é
    `EXPENSE` → 422 `budget.category-not-expense` ("Orçamento só vale para categorias de gasto.");
    limite ≤ 0 → 400 `request.invalid`; moeda diferente da moeda base → 422 `budget.currency`.
  - Gasto: `EXPENSE` `CONFIRMED` do mês, todas as contas. Limite da mãe soma as filhas; limite da
    filha conta só ela (spec 6.5).
- **D7. Estado do orçamento** (`BudgetState`, PRD 7.3): `usagePercent = floor(spent × 100 ÷ limit)`.
  `OVER` se `spent > limit` (e `overBy = spent − limit`), `AT` se `spent = limit`, `NEAR` se
  `usagePercent ≥ 80`, senão `UNDER`.
- **D8. Metas na API** (`/v1/workspaces/{ws}/goals`):
  - `GET` (quatro papéis) → `GoalsView(List<GoalView> goals, boolean limitReached)`. Ativas primeiro
    (por `created_at`), depois as atingidas (por `achieved_at` desc). Arquivadas não vêm.
    `limitReached` é falso para `YOUTH` e `VIEWER`.
  - `GoalView(UUID id, String name, String icon, Money targetAmount, Money saved, int percent, @Nullable YearMonth dueOn, @Nullable Money suggestedMonthly, boolean linked, @Nullable UUID linkedAccountId, @Nullable Instant achievedAt, long version)`.
    `percent = floor(saved × 100 ÷ target)`, até 100. Para o Jovem, `linkedAccountId` é sempre nulo.
  - `POST` (`OWNER`, `ADULT`) com
    `CreateGoalRequest(@NotBlank @Size(max = 40) String name, @NotNull Money targetAmount, @Nullable YearMonth dueOn, @NotNull String icon, @Nullable UUID linkedAccountId)`
    → 201. Moeda da meta = moeda base. Conta vinculada ausente, arquivada ou de outra família → 422
    `goal.linked-account-invalid`; moeda diferente → 422 `goal.currency`; ícone fora da lista → 400
    `request.invalid`; prazo num mês que já passou → 422 `goal.due-in-past` ("O prazo não pode ser
    um mês que já passou.").
  - `PATCH /{id}` (`@PartialRequest`, com `version`): `name`, `targetAmount`, `dueOn` e `icon`. A
    conta vinculada não muda depois de criar. Baixar o alvo até o guardado grava `achieved_at`.
  - `POST /{id}/archive` → 204, idempotente. Audita `goal.archived` só no primeiro.
  - `POST /{id}/contributions` (`OWNER`, `ADULT`) com
    `CreateContributionRequest(@NotNull Money amount, @NotNull LocalDate occurredOn)` → 201
    `ContributionView(UUID id, Money amount, LocalDate occurredOn, @Nullable UUID memberId)`.
    `amount = 0` → 400; meta vinculada → 422 `goal.linked-account` ("Esta meta acompanha o saldo
    de uma conta."); retirada que deixaria a soma negativa → 422 `goal.negative-balance` ("O guardado
    não pode ficar negativo."); meta arquivada ou ausente → 404 `goal.not-found`.
  - `GET /{id}/contributions` (quatro papéis), do mais novo para o mais velho.
  - Guardado: vinculada → `max(0, accountBalance)`; sem vínculo → `max(0, soma dos aportes)`.
  - `achieved_at` não aumenta `version` (é campo do sistema), para não dar 409 em quem está editando.
- **D9. Quem grava `achieved_at`** (spec 6.6): `UPDATE goals SET achieved_at = now() WHERE id = ? AND achieved_at IS NULL`,
  chamado por:
  - aporte e `PATCH`, na mesma transação;
  - o `GoalAchievementListener` (D4), ao ouvir `TransactionChanged` (contas `accountId` e
    `toAccountId`) e `AccountChanged`;
  - o `GET /goals`, que roda numa transação de escrita e confere as metas vinculadas não atingidas.
- **D10. Aporte sugerido** (PRD 7.4): `meses = max(1, mês do prazo − mês atual)`;
  `sugestão = (alvo − guardado) ÷ meses`, teto ao centavo (`RoundingMode.CEILING`, escala 2). Sem
  prazo ou meta atingida → nulo.
- **D11. Limite do Grátis** (decisão 2): ao criar, se `plans.legalSetEnabled()` e
  `plans.current(ws).key() == FREE` e as ativas (`archived_at IS NULL AND achieved_at IS NULL`) são
  3 ou mais → 403 `goal.limit-reached`, com o detail "No plano Grátis, a família tem até 3 metas
  ativas. Conclua ou arquive uma, ou veja o plano Família em Mais > Plano." A contagem roda depois de
  `pg_advisory_xact_lock` com uma chave da família, para duas criações ao mesmo tempo não passarem de
  3. `GoalsView.limitReached` usa a mesma conta.
- **D12. Reserva na API** (`/v1/workspaces/{ws}/reserve`):
  - `GET` (`OWNER`, `ADULT`, `VIEWER`) →
    `ReserveView(Money balance, boolean hasReserveAccounts, @Nullable Money averageMonthlyCost, CostSource costSource, @Nullable BigDecimal months, int targetMonths, @Nullable Money missing, @Nullable Money manualMonthlyCost)`,
    `CostSource { HISTORY, MANUAL, NONE }`.
  - `PATCH` (`@PartialRequest`, `OWNER`, `ADULT`) com
    `UpdateReserveRequest(@Min(1) @Max(24) Integer targetMonths, @Nullable Money manualMonthlyCost)`.
    `manualMonthlyCost` nulo explícito limpa. Audita `reserve.target_changed` e
    `reserve.cost_changed` (alvo `WORKSPACE`) só quando o valor muda.
  - Conta (PRD 7.5): saldo = `reserveBalanceOn(ws, hoje)`. Soma = gastos `CONFIRMED` sem
    transferências dos 3 meses antes do atual. Histórico curto = `firstConfirmedOn` num mês depois do
    mais antigo dos três, ou soma zero. Com histórico, `costSource = HISTORY`; sem, usa o custo
    informado (`MANUAL`, soma = custo × 3) ou fica `NONE` com `months` e `missing` nulos.
  - `months = floor(saldo × 3 ÷ soma, 1 casa)`; `missing = max(0, alvo × soma ÷ 3 − saldo)`, meia
    para cima ao centavo; `averageMonthlyCost = soma ÷ 3`, meia para cima.
  - Os campos moram em `workspaces` (spec 6.1). O `planning.internal.ReserveSettingsRepository` é o
    único que grava `reserve_target_months` e `manual_monthly_cost`.
- **D13. Projeção na API** (`GET /v1/workspaces/{ws}/projection`, `OWNER`, `ADULT`, `VIEWER`) →
  `ProjectionView(LocalDate today, Money balanceToday, boolean hasHistory, @Nullable Money averageSpending, List<ProjectionPointView> points)`,
  com `ProjectionPointView(LocalDate monthEnd, Money balance, Money scheduled, Money estimatedSpending)`
  e 4 pontos: fim do mês atual e dos 3 seguintes. `scheduled` e `estimatedSpending` são acumulados
  até aquele ponto.
  - `balanceToday = activeBalanceOn(ws, hoje)`.
  - Fluxos gravados: `openFlowsAfter(ws, hoje, último ponto)`.
  - Fluxos calculados: para cada regra ativa cuja conta e conta destino não estão arquivadas, as
    datas de `RecurrenceSchedule` em `(hoje, último ponto]`, até `end_on`, menos
    `occupiedDates(ws, regra)`. Assim, dentro e depois do horizonte do job, cada ocorrência conta uma
    vez só.
  - Receita soma, gasto subtrai, transferência vale 0.
  - Gasto estimado, com a soma sem regra dos 3 meses antes do atual (`withoutRuleOnly = true`): no
    mês atual, `soma × dias depois de hoje ÷ (3 × dias do mês)`; nos seguintes, `soma ÷ 3`; cada
    parcela meia para cima ao centavo.
  - Sem histórico (mesma definição da reserva): gasto estimado 0 e `hasHistory = false`.
  - **Emenda de 2026-11-24:** a transferência conta pelo efeito nas contas não arquivadas, nos fluxos
    gravados e nos calculados. Com as duas pontas ativas, vale 0; com uma ponta arquivada, conta como
    receita ou gasto na ponta ativa. Isso substitui o texto "cuja conta e conta destino não estão
    arquivadas" acima.
- **D14. Fixo e variável** (`GET /v1/workspaces/{ws}/spending-split?month=YYYY-MM`, `OWNER`,
  `ADULT`, `VIEWER`) → `SpendingSplitView(YearMonth month, Money fixed, Money variable)`. Fixo = tem
  `recurring_rule_id`, ou a categoria ou a mãe dela tem `is_fixed`.
- **D15. Contas puras em `planning.domain`:**
  - `BudgetUsage.of(BigDecimal spent, BigDecimal limit)` → `BudgetUsage(int percent, BudgetState state, @Nullable BigDecimal overBy)`;
  - `BudgetRollup.spentFor(UUID categoryId, List<CategorySpending> rows)`: a própria mais as filhas;
  - `SuggestedContribution.of(BigDecimal target, BigDecimal saved, @Nullable YearMonth due, YearMonth current)` → `Optional<BigDecimal>`;
  - `ShortHistory.of(@Nullable LocalDate firstConfirmedOn, @Nullable BigDecimal threeMonthSum, YearMonth current)` → `boolean`:
    `true` quando não há lançamento confirmado, quando `firstConfirmedOn` cai num mês depois de
    `current.minusMonths(3)`, ou quando a soma é nula ou zero. Reserva (D12) e projeção (D13) usam
    só este método;
  - `ReserveCoverage.of(BigDecimal balance, @Nullable BigDecimal threeMonthSum, int targetMonths)` → `ReserveCoverage(@Nullable BigDecimal months, @Nullable BigDecimal missing, @Nullable BigDecimal average)`;
  - `Projection.compute(LocalDate today, BigDecimal balanceToday, List<Flow> flows, @Nullable BigDecimal threeMonthSum)`
    → `List<ProjectionPoint>` (4), com `Flow(LocalDate on, BigDecimal signedAmount)` e
    `ProjectionPoint(LocalDate monthEnd, BigDecimal balance, BigDecimal scheduled, BigDecimal estimatedSpending)`.
  - `GoalIcon`: a lista fixa de chaves `savings`, `home`, `car`, `travel`, `education`, `health`,
    `gift`, `party`, `tech` e `other`.
- **D16. Slots do web** (`shared/modules/slots.ts`):
  - `registerSlot(slot: SlotName, entry: SlotEntry)` e `listSlot(slot)`, com
    `type SlotName = 'home' | 'workspace'` e
    `type SlotEntry = { key: string; module: ModuleKey; roles: readonly Role[]; Component: LazyExoticComponent<ComponentType> }`;
    `resetSlots()` para os testes.
  - `<ModuleSlot name />` filtra por `useModuleEnabled(entry.module)` e pelo papel da família, e põe
    cada entrada num `Suspense` e num limite de erro com fallback nulo.
  - O `HomePage` (núcleo) renderiza `<ModuleSlot name="home" />` depois do resumo do mês. O
    `WorkspaceLayout` (app) renderiza `<ModuleSlot name="workspace" />` junto do `Outlet`, para os
    observadores invisíveis.
- **D17. Barramento local** (`shared/events/localActions.ts`):
  - `emitLocalAction(action: LocalAction)` e `onLocalAction(listener): () => void`, com
    `type LocalAction = { type: 'transaction-saved' | 'contribution-saved'; workspaceId: string; categoryId?: string; month?: string; fromQueue: boolean }`.
  - Quem emite: o salvar e o editar online de lançamento (núcleo `transactions`, `fromQueue: false`);
    o `onSent` da fila no `SessionLayout`, com
    `fromQueue = item.attempts > 0 || Date.now() - item.createdAt > 60_000`; o aporte salvo
    (`contribution-saved`, `fromQueue: false`).
  - O observador do planejamento invalida as consultas `planning` da família a cada ação (também da
    fila). Toast e celebração ignoram `fromQueue: true`.
- **D18. Toast do orçamento** (spec 6.5): num `transaction-saved` local que não veio da fila, busca o
  orçamento daquele mês e olha as linhas da categoria salva e da mãe dela.
  - Uso ≥ 100 e a chave `100` vazia → toast e grava `100` e `80`.
  - Senão, uso ≥ 80 e a chave `80` vazia → toast e grava `80`.
  - Chave: `liberdade.budget-toast:<ws>:<YYYY-MM>:<categoryId>:<80|100>`, em `try/catch`.
  - Textos: "{categoria}: chegou a 80% do limite", "{categoria}: chegou ao limite" e
    "{categoria}: passou {valor} do limite". Sem som e sem vibração.
- **D19. Resumo do mês anterior** (spec 6.5): o card do orçamento no Início mostra
  "{Mês}: {n} de {total} categorias dentro do limite" quando o mês anterior teve limites e a chave
  `liberdade.budget-summary:<ws>:<YYYY-MM do mês atual>` está vazia. "Dentro" é `spent ≤ limit`. O
  botão "Copiar limites" (só `OWNER` e `ADULT`) chama o copy-previous e grava a chave; "Agora não"
  também grava. Sem celebração.
- **D20. Celebração da meta** (spec 6.6): `GoalCelebration` na feature `planning`, até 1 s, só
  `opacity` e `transform`; com `prefers-reduced-motion`, o ícone parado e o texto "Meta atingida!".
  - Dispara num `transaction-saved` ou `contribution-saved` local que não veio da fila, e ao abrir
    Planejar > Metas.
  - Ids celebrados ficam em `liberdade.goal-celebrated:<ws>`.
  - Só celebra meta com `achievedAt` nos últimos 7 dias. As mais antigas entram na lista sem
    celebrar, para um aparelho novo não soltar uma fila de celebrações velhas.
  - A 7.3 troca este componente pela `Celebration` do `shared`, com som.
- **D21. Navegação** (spec 6.1):
  - rotas `/planejar` (hub), `/planejar/orcamento`, `/planejar/metas`, `/planejar/reserva` e
    `/planejar/recorrencias`; `/mais/recorrencias` vira `<Navigate replace>` e a entrada sai do
    `MorePage`;
  - o `AppShell` passa a `grid-cols-5` com "Planejar" entre "+ Novo gasto" e "Mais" quando o módulo
    está ligado, e fica em `grid-cols-4` quando não;
  - o hub mostra Orçamento e Reserva só para `OWNER`, `ADULT` e `VIEWER`; para o Jovem, só Metas e
    Recorrências. As rotas de Orçamento e Reserva mandam o Jovem para `/planejar`.
  - A feature `recurrences` continua separada e opcional; só muda de rota.
- **D22. Exportação v4** (spec 6.11): `FORMAT_VERSION = 4`.
  - Família: novos `orcamentos.csv`, `metas.csv` e `aportes.csv`; `contas.csv` ganha a coluna
    `reserva`; `categorias.csv` ganha `fixa`; o JSON da família ganha `reserveTargetMonths` e
    `manualMonthlyCost`.
  - Pessoal: novo `aportes.csv` com os aportes que a pessoa fez.
  - O `planning.PlanningExport` ganha os métodos; o `privacy` só monta os arquivos.
- **D23. Política de privacidade** (spec 6.11): a versão `privacy-2026-12.1` cita tags, forma de
  pagamento, recorrências e previstos, mas não orçamentos, metas, aportes nem reserva. Entram
  `privacy-2026-11.2` e `privacy-2026-12.2`, com aviso e sem novo aceite:
  - o texto acrescenta "orçamentos, metas e aportes, reserva de emergência (contas marcadas e custo
    mensal informado) e a marcação de gastos fixos";
  - `KEEPS_ACCEPTANCE` passa a ser percorrido em cadeia: `11.2 → 11.1 → 11` e `12.2 → 12.1 → 12`;
  - `legalConfig.latest.privacy` vira `privacy-2026-12.2`; o `PolicyUpdatedNotice` da 7.1 serve sem
    mudança.

## Review Focus

1. **Jovem e o que não é dele:** o Jovem recebe 403 em orçamento, reserva, projeção e fixo/variável,
   e na lista de metas nunca vem o id da conta vinculada. Testes `youthIsForbidden` em cada IT
   (Tasks 3, 5 e 6) e `youthNeverSeesLinkedAccount` (Task 4); no web, `PlanHub.test.tsx` sem
   Orçamento e Reserva para o Jovem (Task 9).
2. **Celebrar no lugar errado:** um aparelho novo não celebra metas atingidas há mais de 7 dias, e um
   lançamento que sai da fila offline não celebra. Testes `doesNotCelebrateOldGoals` e
   `ignoresQueuedActions` (Task 11), e `fromQueueWhenRetriedOrOld` (Task 8).
3. **Projeção contando duas vezes:** um previsto pulado, um confirmado com outra data e um ajustado
   no valor contam uma vez só, dentro e depois do horizonte do job. Testes
   `skippedPlannedNeverCounts`, `confirmedEarlyCountsOnce` e `adjustedPlannedCountsOnce` (Task 6).
4. **Pouco histórico:** família criada neste mês, ou com gastos zerados nos 3 meses, não divide por
   zero na reserva nem na projeção, e a reserva pede o custo mensal. Testes
   `reserveWithoutHistoryAsksForCost`, `zeroSpendingIsShortHistory` (Task 5) e
   `projectionWithoutHistory` (Tasks 2 e 6).
5. **Limite de metas fora do caminho feliz:** o plano Família acaba com 5 metas ativas (todas
   continuam, só não cria a 6ª), duas criações ao mesmo tempo com 2 ativas, e a cobrança desligada.
   Testes `familyEndedKeepsGoals`, `concurrentCreatesStopAtThree` e `billingOffHasNoLimit` (Task 4).

---

## Mapa de arquivos

**API** (`api/src/main/java/br/com/liberdadefamilia/`):

| Pacote | Novo | Muda |
|---|---|---|
| `ledger` | `LedgerFigures`, `CategoryRef`, `CategorySpending`, `AccountRef`, `MonthTotal`, `SpendingSplit`, `ScheduledFlow`, `TransactionChanged`, `AccountChanged` | `ExportedAccount` (`isReserve`), `ExportedCategory` (`isFixed`) |
| `ledger/internal` | `JdbcLedgerFigures` | `UpdateAccountRequest`, `AccountView`, `AccountRepository`, `AccountService`, `UpdateCategoryRequest`, `CategoryView`, `CategoryRepository`, `CategoryService`, `TransactionService`, `LedgerJdbcExport` |
| `planning` | `ExportedBudget`, `ExportedGoal`, `ExportedContribution` | `package-info` (`"billing"`), `PlanningExport` |
| `planning/domain` | `BudgetState`, `BudgetUsage`, `BudgetRollup`, `SuggestedContribution`, `ShortHistory`, `ReserveCoverage`, `Projection`, `Flow`, `ProjectionPoint`, `GoalIcon` | — |
| `planning/internal` | `BudgetController`, `BudgetService`, `BudgetRepository`, `BudgetMonthView`, `BudgetLineView`, `SetBudgetRequest`, `CopyBudgetsView`; `GoalController`, `GoalService`, `GoalRepository`, `GoalsView`, `GoalView`, `CreateGoalRequest`, `UpdateGoalRequest`, `CreateContributionRequest`, `ContributionView`, `GoalAchievementListener`; `ReserveController`, `ReserveService`, `ReserveSettingsRepository`, `ReserveView`, `UpdateReserveRequest`, `CostSource`; `ProjectionController`, `ProjectionService`, `ProjectionView`, `ProjectionPointView`, `SpendingSplitView`; as exceções (`BudgetRejectedException`, `GoalNotFoundException`, `GoalRejectedException`, `GoalLimitReachedException`) e os eventos `GoalArchived`, `ReserveTargetChanged`, `ReserveCostChanged` (na raiz do `planning`) | `PlanningJdbcExport` |
| `audit` | — | `AuditEntry` (javadoc com `GOAL`) |
| `audit/internal` | — | `AuditListeners` (`goal.archived`, `reserve.target_changed`, `reserve.cost_changed`) |
| `privacy/internal` | — | `ExportService`, `LegalDocuments` |

**Migração:** `api/src/main/resources/db/migration/V17__planning.sql`.

**Testes da API** (`api/src/test/java/br/com/liberdadefamilia/`): `ledger/` (`MigrationV17IT`,
`AccountReserveFlagIT`, `CategoryFixedFlagIT`, `LedgerFiguresIT`, `LedgerEventsIT`), `planning/`
(`BudgetUsageTest`, `BudgetRollupTest`, `SuggestedContributionTest`, `ReserveCoverageTest`,
`ProjectionTest`, `ProjectionProperties`, `BudgetsIT`, `GoalsIT`, `GoalLimitIT`,
`GoalAchievementIT`, `ReserveIT`, `ProjectionIT`, `SpendingSplitIT`), `privacy/` (export e
documentos), `RoleMatrixIT`, `ApplicationModulesTest` e `OpenApiDriftIT`.

**Web** (`web/src/`):

| Pasta | Novo | Muda |
|---|---|---|
| `shared/modules` | `slots.ts`, `ModuleSlot.tsx` e os testes | `index.ts` |
| `shared/events` | `localActions.ts`, `index.ts` e o teste | — |
| `shared/api` | `planning.ts` (fora do barrel) | `types.ts`, `schema.ts` (gerado), `hooks.ts`/`mutations.ts` (contas e categorias com as flags) |
| `app` | — | `AppShell.tsx`, `routes.tsx`, `MorePage.tsx`, `WorkspaceLayout.tsx`, `SessionLayout.tsx` |
| `features/dashboard` | — | `HomePage.tsx` (`<ModuleSlot name="home" />`) |
| `features/transactions` | — | onde o salvar online termina (emite `transaction-saved`) |
| `features/accounts` | — | o formulário de edição (interruptor "Esta conta é reserva") |
| `features/categories` | — | o formulário de edição (interruptor "Gasto fixo", só em gasto) |
| `features/planning` | `register.ts`, `messages.pt-BR.ts`, `PlanHub.tsx`, `BudgetPage.tsx`, `BudgetLine.tsx`, `BudgetLimitSheet.tsx`, `budgetToast.ts`, `BudgetToastWatcher.tsx`, `BudgetHomeCard.tsx`, `monthSummary.ts`, `GoalsPage.tsx`, `GoalSheet.tsx`, `GoalForm.tsx`, `ContributionSheet.tsx`, `goalIcons.ts`, `GoalCelebration.tsx`, `celebration.ts`, `GoalCelebrationWatcher.tsx`, `ReservePage.tsx`, `ReserveHomeCard.tsx`, `ProjectionHomeCard.tsx`, `ProjectionSheet.tsx`, `SpendingSplitHomeCard.tsx`, `PlanningInvalidator.tsx` e os testes | — |
| `features/legal` | `texts/privacy-2026-11.2.ts`, `texts/privacy-2026-12.2.ts` | `legalConfig.ts`, `legalTexts.test.ts` |
| `mocks` | — | `handlers.ts`, `data.ts` |
| `eslint` | — | `boundaries.js` (`planning` já é opcional; confere que importa só `shared`) |

**E2E** (`web/e2e/`): `planning.spec.ts` (novo), `recurrences.spec.ts` (rota nova), `a11y.spec.ts`,
`support.ts` e `core-without-modules.spec.ts`.

**Docs:** `docs/data-model.md`, `docs/architecture.md` e o README dos planos.

**Em toda task de API:** as rotas de família usam `@WorkspaceScoped` e, no `planning`,
`@RequiresModule(ModuleKey.PLANNING)`. Rota nova ou mudada regenera, no mesmo commit:
- o OpenAPI: `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` em `api/`;
- o `schema.ts`: `corepack pnpm --dir web gen:api`;
- os handlers do MSW.

Nesse mesmo commit, o `RoleMatrixIT` ganha as linhas das rotas novas. Eventos são records públicos
no pacote raiz do módulo, só com ids.

## Tasks

### Task 1: V17, flags, eventos e a porta `LedgerFigures`

**Files:**
- Create: `db/migration/V17__planning.sql` (D1, inteira).
- Create em `ledger/`: `LedgerFigures.java` e os records de D5, `TransactionChanged.java`,
  `AccountChanged.java`.
- Create: `ledger/internal/JdbcLedgerFigures.java`.
- Modify em `ledger/internal/`: `UpdateAccountRequest`, `AccountView`, `AccountRepository`,
  `AccountService`, `UpdateCategoryRequest`, `CategoryView`, `CategoryRepository`, `CategoryService`,
  `TransactionService`, `LedgerJdbcExport`; em `ledger/`: `ExportedAccount`, `ExportedCategory`.
- Test em `ledger/`: `MigrationV17IT`, `AccountReserveFlagIT`, `CategoryFixedFlagIT`,
  `LedgerFiguresIT`, `LedgerEventsIT`.

**Interfaces:**
- Produces: tudo de D2, D3 e D5, com as assinaturas exatas de lá. As Tasks 3 a 7 usam só
  `LedgerFigures`, `TransactionChanged` e `AccountChanged` do `ledger`.

**Como fica:**
- A V17 cria as três tabelas, as quatro colunas e o CHECK do `audit_log` de D1. As tabelas de
  orçamento e metas ficam vazias até as Tasks 3 e 4.
- `SELECT_WITH_BALANCE` do `AccountRepository` passa a trazer `is_reserve`; o `CategoryRepository`
  traz `is_fixed`.
- O `TransactionService` publica `TransactionChanged` pelo `ApplicationEventPublisher` depois de
  gravar, dentro da transação. O `AccountService` publica `AccountChanged`.
- `fixedAndVariable` usa
  `CASE WHEN t.recurring_rule_id IS NOT NULL OR c.is_fixed OR p.is_fixed THEN 'FIXED' ELSE 'VARIABLE' END`,
  com `p` a categoria mãe (`LEFT JOIN`).
- `JdbcLedgerFigures` é `@Transactional(readOnly = true)` e filtra sempre por `workspace_id`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MigrationV17IT.defaultsForExistingRows` | conta, categoria e família criadas antes → `is_reserve = false`, `is_fixed = false`, `reserve_target_months = 6`, `manual_monthly_cost IS NULL` |
| `MigrationV17IT.checksHold` | insert direto: orçamento com `month` dia 2, limite 0, meta com nome de 41 caracteres, aporte 0, `reserve_target_months = 25` → violação de CHECK em cada um |
| `MigrationV17IT.auditAcceptsGoalTarget` | insert em `audit_log` com `target_type = 'GOAL'` → ok |
| `AccountReserveFlagIT.patchSetsAndKeeps` | `PATCH` com `isReserve: true` → `GET` traz `true`; `PATCH` sem o campo → continua `true` |
| `CategoryFixedFlagIT.onlyExpenseCanBeFixed` | categoria de receita com `isFixed: true` → 422 `category.fixed-not-expense` |
| `CategoryFixedFlagIT.patchSetsAndKeeps` | gasto com `isFixed: true` → `true`; `PATCH` sem o campo → `true` |
| `LedgerFiguresIT.expenseByCategoryCountsConfirmedOnly` | gastos de R$ 100 (`CONFIRMED`), R$ 50 (`PLANNED`) e R$ 30 (excluído) na mesma categoria → 100 |
| `LedgerFiguresIT.balancesRespectDateAndArchive` | conta ativa com R$ 1.000 até hoje e R$ 200 amanhã, conta arquivada com R$ 500 → `activeBalanceOn(hoje) = 1000` |
| `LedgerFiguresIT.reserveBalanceOnlyMarkedAccounts` | duas contas, só uma com `is_reserve` e R$ 18.000 → 18000 |
| `LedgerFiguresIT.monthTotalsWithoutRule` | gasto avulso de R$ 300 e gasto de regra de R$ 2.000 no mês → `withoutRuleOnly = true` dá 300; `false` dá 2300; transferência não entra em nenhum |
| `LedgerFiguresIT.splitUsesRuleAndParent` | gasto de regra R$ 2.000, gasto em filha de mãe fixa R$ 150, gasto livre R$ 80 → fixo 2150, variável 80 |
| `LedgerFiguresIT.openFlowsIncludeOverdueAndFutureConfirmed` | `PLANNED` vencido ontem, `PLANNED` dia 25, `CONFIRMED` dia 28, `PLANNED` excluído → três fluxos, sem o excluído |
| `LedgerFiguresIT.occupiedDatesIncludeDeleted` | regra com previsto pulado em 25/11 e confirmado em 25/12 → `{2026-11-25, 2026-12-25}` |
| `LedgerFiguresIT.otherFamilyIsInvisible` | tudo acima com a família B → somas zero e `Optional.empty()` |
| `LedgerEventsIT.transactionMutationsPublish` | `record`, `update`, `confirm`, `skip`, `delete` e `restore` → um `TransactionChanged` cada, com `accountId`; transferência traz `toAccountId` |
| `LedgerEventsIT.accountMutationsPublish` | `update`, `setBalance`, `archive` e `unarchive` → um `AccountChanged` cada |

`LedgerEventsIT` usa `@RecordApplicationEvents` e `ApplicationEvents`.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*MigrationV17IT' --tests '*ReserveFlagIT' --tests '*FixedFlagIT' --tests '*LedgerFiguresIT' --tests '*LedgerEventsIT'`
Expected: FAIL na compilação (`LedgerFigures` não existe).

- [ ] **Step 3: Implementar.** Siga "Como fica" e D1 a D5. Regenere o contrato (as flags mudam
  `AccountView`, `CategoryView` e os requests).

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*AccountsIT' --tests '*CategoriesIT' --tests '*ApplicationModulesTest'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(ledger): add reserve and fixed flags, change events and planning figures"
```

---

### Task 2: Contas puras do planejamento

**Files:**
- Create em `planning/domain/`: os tipos de D15 e `BudgetState`.
- Test em `planning/`: `BudgetUsageTest`, `BudgetRollupTest`, `SuggestedContributionTest`,
  `ShortHistoryTest`, `ReserveCoverageTest`, `ProjectionTest`, `ProjectionProperties`.

**Interfaces:**
- Consumes: `ledger.CategorySpending` (Task 1).
- Produces: as assinaturas de D15, usadas pelas Tasks 3 a 6.

**Como fica:**
- Nada aqui toca banco, Spring nem `Clock`: datas entram como argumento.
- `Projection.compute`:
  - pontos = último dia do mês de `today` e dos 3 meses seguintes;
  - `scheduled` de um ponto = soma dos `signedAmount` com `on ≤ monthEnd` (fluxos com data ≤ hoje,
    os vencidos, entram no primeiro ponto);
  - `estimatedSpending` do primeiro ponto = `threeMonthSum × (dias do mês − dia de hoje) ÷ (3 × dias do mês)`,
    meia para cima ao centavo; cada ponto seguinte soma `threeMonthSum ÷ 3`, meia para cima;
    `threeMonthSum` nulo → 0;
  - `balance = balanceToday + scheduled − estimatedSpending`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `BudgetUsageTest.prdEightyPercent` | `of(960, 1200)` → 80, `NEAR`, `overBy` nulo |
| `BudgetUsageTest.prdOverBySixty` | `of(1260, 1200)` → 105, `OVER`, `overBy = 60.00` |
| `BudgetUsageTest.exactlyAtLimit` | `of(1200, 1200)` → 100, `AT` |
| `BudgetUsageTest.underIsUnder` | `of(959.99, 1200)` → 79, `UNDER` |
| `BudgetRollupTest.parentIncludesChildren` | mãe R$ 100, filhas R$ 50 e R$ 25 → mãe 175, filha 50 |
| `SuggestedContributionTest.prdNineMonths` | alvo 12000, guardado 3000, 9 meses → `1000.00` |
| `SuggestedContributionTest.prdSevenMonthsRoundsUp` | 7 meses → `1285.72` |
| `SuggestedContributionTest.pastDueUsesOneMonth` | prazo no mês passado → `9000.00` |
| `SuggestedContributionTest.noDueOrAchievedIsEmpty` | sem prazo → vazio; guardado ≥ alvo → vazio |
| `ShortHistoryTest.firstConfirmedInsideWindowIsShort` | `current` 11/2026, primeiro lançamento em 09/2026, soma 9000 → `true` |
| `ShortHistoryTest.firstConfirmedInOldestMonthIsLong` | primeiro lançamento em 08/2026, soma 9000 → `false` |
| `ShortHistoryTest.zeroOrNullSumIsShort` | primeiro em 01/2026, soma 0 → `true`; soma nula → `true` |
| `ShortHistoryTest.noConfirmedIsShort` | `firstConfirmedOn` nulo → `true` |
| `ReserveCoverageTest.prdFourMonths` | saldo 18000, soma 13500 (4000 + 4500 + 5000), alvo 6 → `months = 4.0`, `missing = 9000.00`, `average = 4500.00` |
| `ReserveCoverageTest.floorsToOneDecimal` | saldo 10000, soma 9000 → `months = 3.3` |
| `ReserveCoverageTest.missingNeverNegative` | saldo 50000, soma 13500, alvo 6 → `missing = 0` |
| `ReserveCoverageTest.withoutCostIsEmpty` | soma nula → `months`, `missing` e `average` nulos |
| `ProjectionTest.specExampleWithHistory` | hoje 20/11/2026, saldo 5000, fluxos −2000 em 25/11, +6000 em 05/12, −2000 em 25/12, +6000 em 05/01, −2000 em 25/01, +6000 em 05/02, −2000 em 25/02, soma 9900 → saldos `1900.00`, `2600.00`, `3300.00`, `4000.00` |
| `ProjectionTest.specExampleWithoutHistory` | mesmos fluxos, soma nula → `3000.00`, `7000.00`, `11000.00`, `15000.00` |
| `ProjectionTest.projectionWithoutHistory` | sem fluxos e soma nula → os quatro iguais ao saldo de hoje |
| `ProjectionTest.overdueFlowCountsInFirstPoint` | fluxo −100 em 10/11 (vencido) → primeiro ponto já com −100 |
| `ProjectionTest.lastDayOfMonthHasNoSpending` | hoje 30/11 → `estimatedSpending` do primeiro ponto = 0 |
| `ProjectionProperties.balanceIsSumOfParts` | 1000 casos, semente `20261102L`: para todo ponto, `balance = balanceToday + scheduled − estimatedSpending`, e `estimatedSpending` não diminui de um ponto para o seguinte |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests 'br.com.liberdadefamilia.planning.*Test' --tests '*ProjectionProperties'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Records imutáveis e métodos estáticos, com os modos de arredondamento
  de D7, D10, D12 e D13.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL, com `planning.domain` ≥ 90%.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java/br/com/liberdadefamilia/planning/domain api/src/test/java/br/com/liberdadefamilia/planning
git commit -m "feat(planning): add the budget, goal, reserve and projection calculations"
```

---

### Task 3: Orçamento na API

**Files:**
- Create em `planning/internal/`: `BudgetController`, `BudgetService`, `BudgetRepository`,
  `BudgetMonthView`, `BudgetLineView`, `SetBudgetRequest`, `CopyBudgetsView`,
  `BudgetRejectedException`.
- Test: `planning/BudgetsIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `LedgerFigures.category`, `confirmedExpenseByCategory` (Task 1); `BudgetUsage`,
  `BudgetRollup` (Task 2); a moeda base da família pelo `workspace`.
- Produces: as rotas e os DTOs de D6, que a Task 8 consome pelo `schema.ts`.

**Como fica:**
- `PUT` grava com `INSERT ... ON CONFLICT (workspace_id, category_id, month) DO NOTHING` quando
  `version` é nulo e, se nada entrou, responde 409. Com `version`, `UPDATE ... WHERE version = ?`;
  zero linhas → 409.
- O mês vem na rota como `YYYY-MM` e vira o primeiro dia; formato errado → 400 `request.invalid`.
- `copy-previous` faz um `INSERT ... SELECT` do mês anterior com
  `WHERE NOT EXISTS` no destino e `JOIN categories ... archived_at IS NULL`.
- Nada de auditoria (spec 6.11).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `BudgetsIT.createReadUpdate` | `PUT` R$ 1.200 sem `version` → 201; `GET` → uma linha com `limit 1200`; `PUT` R$ 1.500 com `version` → 200 |
| `BudgetsIT.staleVersionConflicts` | `PUT` com `version` antiga → 409 `budget.version-conflict`; `PUT` sem `version` numa linha que existe → 409 |
| `BudgetsIT.spentAndStateFromPrdExample` | limite R$ 1.200 e gastos de R$ 960 → `usagePercent 80`, `NEAR`; mais R$ 300 → `105`, `OVER`, `overBy 60.00` |
| `BudgetsIT.parentLimitIncludesChildren` | limite na mãe, gasto de R$ 100 na filha → `spent 100` na linha da mãe |
| `BudgetsIT.childLimitCountsOnlyItself` | limites na mãe e na filha, gasto de R$ 40 na mãe e R$ 60 na filha → mãe 100, filha 60 |
| `BudgetsIT.plannedAndOtherMonthsDoNotCount` | `PLANNED` no mês e `CONFIRMED` no mês seguinte → `spent 0` |
| `BudgetsIT.incomeCategoryRejected` | `PUT` em categoria de receita → 422 `budget.category-not-expense` |
| `BudgetsIT.otherFamilyCategoryNotFound` | categoria da família B → 404 `category.not-found` |
| `BudgetsIT.zeroLimitInvalid` | limite 0 → 400 `request.invalid` |
| `BudgetsIT.deleteIsIdempotent` | `DELETE` duas vezes → 204 e 204 |
| `BudgetsIT.copyPreviousSkipsExistingAndArchived` | outubro com 3 limites (um de categoria arquivada), novembro já com um deles → `copied 1` e novembro com 2 linhas |
| `BudgetsIT.viewerReadsButCannotWrite` | `VIEWER`: `GET` → 200; `PUT` → 403 |
| `BudgetsIT.youthIsForbidden` | `YOUTH`: `GET` → 403 |
| `BudgetsIT.moduleOff` | módulo desligado → 403 `module.disabled` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*BudgetsIT'`
Expected: FAIL (rotas 404).

- [ ] **Step 3: Implementar.** Siga "Como fica" e D6. Regenere o contrato e ponha as quatro rotas no
  `RoleMatrixIT`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*RoleMatrixIT' --tests '*OpenApiDriftIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(planning): add monthly budgets per category"
```

---

### Task 4: Metas, aportes e o limite do Grátis

**Files:**
- Modify: `planning/package-info.java` (`"billing"` em `allowedDependencies`).
- Create em `planning/internal/`: `GoalController`, `GoalService`, `GoalRepository`, `GoalsView`,
  `GoalView`, `CreateGoalRequest`, `UpdateGoalRequest`, `CreateContributionRequest`,
  `ContributionView`, `GoalAchievementListener`, `GoalNotFoundException`, `GoalRejectedException`,
  `GoalLimitReachedException`; em `planning/`: `GoalArchived`.
- Modify: `audit/internal/AuditListeners.java` (`goal.archived`, alvo `GOAL`), `audit/AuditEntry`.
- Test: `planning/GoalsIT.java`, `planning/GoalLimitIT.java`, `planning/GoalAchievementIT.java`,
  `RoleMatrixIT`, `ApplicationModulesTest`.

**Interfaces:**
- Consumes: `LedgerFigures.account`, `accountBalance`, `TransactionChanged`, `AccountChanged`
  (Task 1); `SuggestedContribution`, `GoalIcon` (Task 2); `billing.Plans.current(UUID)` e
  `Plans.legalSetEnabled()`.
- Produces: as rotas e os DTOs de D8; `GoalArchived(UUID workspaceId, UUID goalId, UUID actorUserId)`.

**Como fica:**
- O `GoalService` monta a `GoalView` com o papel de quem pede: para `YOUTH`, `linkedAccountId` vai
  nulo; para `YOUTH` e `VIEWER`, `limitReached` vai falso.
- O `GET` roda em `@Transactional` de escrita e, antes de montar a resposta, grava `achieved_at` nas
  vinculadas não atingidas com saldo ≥ alvo (D9).
- O limite (D11) chama `pg_advisory_xact_lock(hashtextextended(:ws::text, 0))` antes de contar.
- O `GoalAchievementListener` segue D4: ouve os dois eventos, acha as metas não atingidas e não
  arquivadas ligadas às contas do evento, e grava `achieved_at` quando `accountBalance ≥ alvo`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `GoalsIT.createAndList` | `POST` "Viagem", alvo R$ 12.000, prazo 9 meses à frente, ícone `travel` → 201; `GET` → `saved 0`, `percent 0`, `suggestedMonthly 1333.34` |
| `GoalsIT.contributionsAddUpAndSuggest` | aporte de R$ 3.000 → `saved 3000`, `percent 25`, `suggestedMonthly 1000.00` |
| `GoalsIT.withdrawalCannotGoNegative` | guardado R$ 3.000, retirada de R$ 3.500 → 422 `goal.negative-balance`; retirada de R$ 500 → 201, `saved 2500` |
| `GoalsIT.reachingTargetSetsAchievedAt` | aporte que chega ao alvo → `achievedAt` preenchido; retirada depois → `achievedAt` continua |
| `GoalsIT.loweringTargetAchieves` | `PATCH` do alvo para o guardado → `achievedAt` preenchido; `version` sobe uma vez só |
| `GoalsIT.linkedGoalFollowsBalanceAndRejectsContributions` | meta vinculada a conta com R$ 4.000 → `saved 4000`, `linked true`; aporte → 422 `goal.linked-account` |
| `GoalsIT.linkedAccountMustMatch` | conta arquivada → 422 `goal.linked-account-invalid`; conta em outra moeda (insert direto) → 422 `goal.currency` |
| `GoalsIT.pastDueRejected` | prazo no mês passado → 422 `goal.due-in-past` |
| `GoalsIT.unknownIconInvalid` | `icon: "rocket"` → 400 `request.invalid` |
| `GoalsIT.archiveIsIdempotentAndAuditedOnce` | arquivar duas vezes → 204 e 204; uma linha `goal.archived` na trilha; `GET` sem a meta; aporte → 404 `goal.not-found` |
| `GoalsIT.youthNeverSeesLinkedAccount` | `YOUTH`: `GET` → a meta vinculada com `linked true` e `linkedAccountId` nulo; `POST` → 403 |
| `GoalsIT.viewerReadsOnly` | `VIEWER`: `GET` e `GET /contributions` → 200; aporte → 403 |
| `GoalsIT.otherFamilyNotFound` | meta da família B → 404 `goal.not-found` |
| `GoalsIT.moduleOff` | módulo desligado → 403 `module.disabled` |
| `GoalLimitIT.freeStopsAtThreeActive` | cobrança ligada, Grátis, 3 ativas → 4ª: 403 `goal.limit-reached` com o detail de D11; `GET` → `limitReached true` |
| `GoalLimitIT.achievedAndArchivedDoNotCount` | 3 ativas, uma atingida → cria a 4ª: 201 |
| `GoalLimitIT.familyAndFounderHaveNoLimit` | plano Família e Fundadora com 3 ativas → 201 |
| `GoalLimitIT.familyEndedKeepsGoals` | 5 ativas criadas no Família, plano acaba → `GET` traz as 5; criar a 6ª → 403; aportar numa delas → 201 |
| `GoalLimitIT.billingOffHasNoLimit` | cobrança desligada, Grátis, 3 ativas → 201 |
| `GoalLimitIT.concurrentCreatesStopAtThree` | 2 ativas e duas criações em paralelo → uma 201 e uma 403 |
| `GoalAchievementIT.transactionOnLinkedAccountAchieves` | meta vinculada de R$ 1.000, receita de R$ 1.000 na conta → depois do commit, `achieved_at` gravado |
| `GoalAchievementIT.initialBalanceChangeAchieves` | `setBalance` que leva a conta ao alvo → `achieved_at` gravado |
| `GoalAchievementIT.listenerFailureDoesNotBreakTheSave` | ouvinte forçado a falhar → o lançamento continua 201, e o próximo `GET /goals` grava `achieved_at` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*GoalsIT' --tests '*GoalLimitIT' --tests '*GoalAchievementIT'`
Expected: FAIL (rotas 404).

- [ ] **Step 3: Implementar.** Siga "Como fica" e D8 a D11. Regenere o contrato e ponha as seis
  rotas no `RoleMatrixIT`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*RoleMatrixIT' --tests '*ApplicationModulesTest' --tests '*AuditIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(planning): add goals, contributions and the free plan goal limit"
```

---

### Task 5: Reserva de emergência na API

**Files:**
- Create em `planning/internal/`: `ReserveController`, `ReserveService`, `ReserveSettingsRepository`,
  `ReserveView`, `UpdateReserveRequest`, `CostSource`; em `planning/`: `ReserveTargetChanged`,
  `ReserveCostChanged`.
- Modify: `audit/internal/AuditListeners.java` (`reserve.target_changed`, `reserve.cost_changed`,
  alvo `WORKSPACE`).
- Test: `planning/ReserveIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `LedgerFigures.reserveBalanceOn`, `confirmedExpenseByMonth(..., false)`,
  `firstConfirmedOn` (Task 1); `ShortHistory`, `ReserveCoverage` (Task 2).
- Produces: as rotas e os DTOs de D12;
  `ReserveTargetChanged(UUID workspaceId, UUID actorUserId)` e
  `ReserveCostChanged(UUID workspaceId, UUID actorUserId)`.

**Como fica:** siga D12. O `ReserveSettingsRepository` lê e grava só as duas colunas da reserva em
`workspaces`, sempre com `WHERE id = :ws`. Os eventos de auditoria só saem quando o valor gravado
muda. "Histórico curto" é sempre `ShortHistory.of(...)`, nunca uma conta local.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ReserveIT.prdExample` | conta reserva com R$ 18.000, gastos de R$ 4.000, R$ 4.500 e R$ 5.000 nos 3 meses antes do atual → `months 4.0`, `missing 9000.00`, `averageMonthlyCost 4500.00`, `costSource HISTORY` |
| `ReserveIT.currentMonthDoesNotCount` | o mesmo, mais R$ 10.000 de gasto no mês atual → mesmo resultado |
| `ReserveIT.reserveWithoutHistoryAsksForCost` | primeiro lançamento no mês passado, sem custo informado → `costSource NONE`, `months` e `missing` nulos |
| `ReserveIT.manualCostIsUsed` | mesmo caso com `manualMonthlyCost 4500` → `costSource MANUAL`, `months 4.0` |
| `ReserveIT.zeroSpendingIsShortHistory` | histórico longo, mas sem gastos nos 3 meses → igual a sem histórico |
| `ReserveIT.noReserveAccounts` | nenhuma conta marcada → `hasReserveAccounts false`, `balance 0` |
| `ReserveIT.patchTargetAndCost` | `PATCH {targetMonths: 12}` → `targetMonths 12`, `missing` recalculado; `{manualMonthlyCost: null}` → limpa |
| `ReserveIT.targetOutOfRangeInvalid` | `targetMonths 0` e `25` → 400 `request.invalid` |
| `ReserveIT.auditsOnlyChanges` | `PATCH` com o mesmo alvo → nenhuma linha nova na trilha; com outro → uma `reserve.target_changed` |
| `ReserveIT.viewerReadsButCannotWrite` | `VIEWER`: `GET` → 200; `PATCH` → 403 |
| `ReserveIT.youthIsForbidden` | `YOUTH`: `GET` → 403 |
| `ReserveIT.moduleOff` | módulo desligado → 403 `module.disabled` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ReserveIT'`
Expected: FAIL (rotas 404).

- [ ] **Step 3: Implementar.** Siga D12. Regenere o contrato e ponha as duas rotas no `RoleMatrixIT`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*RoleMatrixIT' --tests '*AuditIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(planning): add the emergency reserve coverage"
```

---

### Task 6: Projeção e gastos fixos e variáveis na API

**Files:**
- Create em `planning/internal/`: `ProjectionController` (as duas rotas), `ProjectionService`,
  `ProjectionView`, `ProjectionPointView`, `SpendingSplitView`.
- Test: `planning/ProjectionIT.java`, `planning/SpendingSplitIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `LedgerFigures.activeBalanceOn`, `openFlowsAfter`, `occupiedDates`,
  `confirmedExpenseByMonth(..., true)`, `firstConfirmedOn`, `fixedAndVariable` (Task 1);
  `ShortHistory`, `Projection` (Task 2); `RecurrenceSchedule` e o `RecurringRuleRepository` da 7.1 (regras ativas).
- Produces: as rotas e os DTOs de D13 e D14.

**Como fica:** siga D13. O `ProjectionService` junta os fluxos gravados e os calculados numa lista de
`Flow` com sinal e chama `Projection.compute`. "Sem histórico" é `ShortHistory.of(...)` (Task 2),
o mesmo que a reserva usa; quando `true`, passa soma nula para `Projection.compute`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ProjectionIT.specExample` | `TestClock` em 20/11/2026; contas com R$ 5.000 até hoje; regras de salário R$ 6.000 (dia 5) e aluguel R$ 2.000 (dia 25) com os previstos que o job gera; gastos avulsos de R$ 3.000, R$ 3.300 e R$ 3.600 em agosto, setembro e outubro → `1900.00`, `2600.00`, `3300.00`, `4000.00` |
| `ProjectionIT.projectionWithoutHistory` | família criada em novembro, mesmas regras → `hasHistory false`, `3000.00`, `7000.00`, `11000.00`, `15000.00` |
| `ProjectionIT.skippedPlannedNeverCounts` | pular o aluguel de 25/12 → dezembro em diante R$ 2.000 a mais que no exemplo |
| `ProjectionIT.confirmedEarlyCountsOnce` | confirmar o aluguel de 25/11 hoje, com data 20/11 → fim de novembro igual ao exemplo (sai do saldo, não sai de novo) |
| `ProjectionIT.adjustedPlannedCountsOnce` | aluguel de 25/12 confirmado adiantado em 22/11 com R$ 2.100 → fim de dezembro R$ 100 abaixo do exemplo |
| `ProjectionIT.beyondHorizonIsComputed` | apagar (insert direto) os previstos gerados de janeiro → o fim de janeiro continua `3300.00` |
| `ProjectionIT.archivedAccountRuleIgnored` | regra numa conta arquivada → não entra |
| `ProjectionIT.transferIsNeutral` | regra de transferência mensal → saldos iguais ao exemplo |
| `ProjectionIT.youthIsForbidden` | `YOUTH` → 403 |
| `ProjectionIT.moduleOff` | módulo desligado → 403 `module.disabled` |
| `SpendingSplitIT.ruleAndFixedCategoryAreFixed` | gasto de regra R$ 2.000, gasto em categoria fixa R$ 300, gasto em filha de mãe fixa R$ 50, gasto livre R$ 400 → `fixed 2350`, `variable 400` |
| `SpendingSplitIT.plannedDoesNotCount` | `PLANNED` no mês → não entra |
| `SpendingSplitIT.youthIsForbidden` | `YOUTH` → 403 |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ProjectionIT' --tests '*SpendingSplitIT'`
Expected: FAIL (rotas 404).

- [ ] **Step 3: Implementar.** Siga D13 e D14. Regenere o contrato e ponha as duas rotas no
  `RoleMatrixIT`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*RoleMatrixIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(planning): add the balance projection and the fixed and variable split"
```

---

### Task 7: Exportação v4 e política de privacidade

**Files:**
- Create em `planning/`: `ExportedBudget`, `ExportedGoal`, `ExportedContribution`.
- Modify: `planning/PlanningExport.java`, `planning/internal/PlanningJdbcExport.java`,
  `privacy/internal/ExportService.java`, `privacy/internal/LegalDocuments.java`.
- Create em `web/src/features/legal/texts/`: `privacy-2026-11.2.ts`, `privacy-2026-12.2.ts`.
- Modify: `web/src/features/legal/legalConfig.ts`, `legalTexts.test.ts`.
- Test: os ITs de exportação e de documentos do `privacy`.

**Interfaces:**
- Consumes: `ExportedAccount.isReserve`, `ExportedCategory.isFixed` (Task 1); as tabelas das Tasks 3
  e 4.
- Produces: `PlanningExport.budgets(UUID ws)`, `goals(UUID ws)`, `contributions(UUID ws)`,
  `contributionsBy(UUID ws, UUID userId)`, `reserveSettings(UUID ws)`.

**Como fica:** siga D22 e D23. `LegalDocuments.KEEPS_ACCEPTANCE` ganha
`privacy-2026-11.2 → privacy-2026-11.1` e `privacy-2026-12.2 → privacy-2026-12.1`, e a checagem de
`acceptanceRequired` sobe a cadeia até achar a versão aceita ou acabar. Os textos novos copiam os
`.1` e só mudam a lista de dados e a data.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ExportIT.familyExportV4` | família com orçamento, meta, aporte, conta reserva e categoria fixa → `formatVersion 4`; `orcamentos.csv`, `metas.csv` e `aportes.csv` com uma linha cada; `contas.csv` com a coluna `reserva = true`; `categorias.csv` com `fixa = true`; JSON com `reserveTargetMonths 6` |
| `ExportIT.personalExportHasOwnContributions` | aportes de duas pessoas → a exportação pessoal de cada uma traz só os dela |
| `ExportIT.moneyColumnsFormatted` | `limit_amount`, `target_amount` e `amount` saem no formato de dinheiro dos outros CSVs |
| `DocumentsIT.chainKeepsAcceptance` | quem aceitou `privacy-2026-12` → `acceptanceRequired false` com a atual `privacy-2026-12.2` |
| `DocumentsIT.chainKeepsAcceptanceForNovemberSet` | quem aceitou `privacy-2026-11` → `false` com `privacy-2026-11.2` |
| `legalTexts.test.ts` › `privacy 12.2 lists planning data` | o texto cita "orçamentos", "metas", "aportes" e "reserva" |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ExportIT' --tests '*DocumentsIT'` e (na raiz) `corepack pnpm --dir web test -- legalTexts`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar.** Mesmos comandos. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` e `corepack pnpm --dir web check` → verdes.

- [ ] **Step 6: Commit**

```bash
git add api web/src/features/legal
git commit -m "feat(privacy): export planning data and update the privacy policy"
```

---

### Task 8: Base do web: API do planejamento, slots e barramento local

**Files:**
- Create: `shared/api/planning.ts` (fora do barrel), `shared/modules/slots.ts`,
  `shared/modules/ModuleSlot.tsx`, `shared/events/localActions.ts`, `shared/events/index.ts` e os
  testes.
- Modify: `shared/modules/index.ts`, `app/SessionLayout.tsx`, `app/WorkspaceLayout.tsx`,
  `features/dashboard/HomePage.tsx`, o ponto do núcleo `transactions` onde o salvar e o editar online
  terminam, `mocks/handlers.ts`, `mocks/data.ts`.

**Interfaces:**
- Consumes: o `schema.ts` das Tasks 3 a 6.
- Produces:
  - em `shared/api/planning.ts`: `useBudgets(ws, month)`, `useSetBudget`, `useDeleteBudget`,
    `useCopyBudgets`, `useGoals(ws)`, `useCreateGoal`, `useUpdateGoal`, `useArchiveGoal`,
    `useContributions(ws, goalId)`, `useAddContribution`, `useReserve(ws)`, `useUpdateReserve`,
    `useProjection(ws)`, `useSpendingSplit(ws, month)`, todos com a chave `['planning', ws, ...]`;
  - D16 (`registerSlot`, `listSlot`, `resetSlots`, `ModuleSlot`) e D17 (`emitLocalAction`,
    `onLocalAction`, `LocalAction`).

**Como fica:** siga D16 e D17. O `SessionLayout` emite no `onSent` com o `categoryId` e o mês do
lançamento enviado. O núcleo não importa nada de `features/planning`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `slots.test.ts` › `lists entries in registration order` | duas entradas em `home` → `listSlot('home')` na ordem |
| `ModuleSlot.test.tsx` › `hides when module is off` | módulo `planning` desligado → nada renderizado |
| `ModuleSlot.test.tsx` › `filters by role` | entrada com `roles: ['OWNER']` e família com `YOUTH` → nada |
| `ModuleSlot.test.tsx` › `a failing entry renders nothing` | componente que lança → o resto da página continua |
| `localActions.test.ts` › `delivers and unsubscribes` | ouvinte recebe a ação; depois do retorno de `onLocalAction`, não recebe mais |
| `SessionLayout.test.tsx` › `fromQueueWhenRetriedOrOld` | item com `attempts 1` → `fromQueue true`; item criado há 2 min com `attempts 0` → `true`; item novo com `attempts 0` → `false` |
| `HomePage.test.tsx` › `renders the home slot` | slot com um componente de teste → aparece no Início |
| teste do salvar online de lançamento | salvar → `emitLocalAction` com `type 'transaction-saved'`, a categoria, o mês e `fromQueue false` |

- [ ] **Step 2: Rodar e ver falhar**

Run (na raiz): `corepack pnpm --dir web test -- slots ModuleSlot localActions SessionLayout HomePage`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". Os handlers do MSW respondem às rotas novas com os
  dados de `mocks/data.ts` (um orçamento com uma linha `NEAR`, duas metas, reserva `HISTORY` e uma
  projeção com quatro pontos).

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` (lint, tipos, testes) → verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add planning api hooks, module slots and local action events"
```

---

### Task 9: Planejar na barra, hub e Recorrências no lugar novo

**Files:**
- Create: `features/planning/register.ts`, `features/planning/messages.pt-BR.ts`,
  `features/planning/PlanHub.tsx`, `features/planning/PlanningInvalidator.tsx` e os testes.
- Modify: `app/AppShell.tsx`, `app/routes.tsx`, `app/MorePage.tsx`, `app/routes.test.tsx`,
  `app/MorePage.test.tsx`, `web/e2e/recurrences.spec.ts`.

**Interfaces:**
- Consumes: D16, D17 e os hooks da Task 8.
- Produces: as rotas de D21; `features/planning/register.ts` importado pelo `routes.tsx`, que
  registra as mensagens, `registerModule({ key: 'planning', available: true })` e as entradas de slot
  (as telas das Tasks 10 a 13 se registram aqui, cada uma com `lazy`).

**Como fica:** siga D21. O `PlanningInvalidator` é a primeira entrada do slot `workspace` (os quatro
papéis): a cada ação local, invalida `['planning', ws]`. O hub é uma lista de quatro links com uma
linha de descrição cada:
- "Orçamento · Limites por categoria no mês";
- "Metas · O que a família está juntando";
- "Reserva · Quantos meses de gastos estão guardados";
- "Recorrências · Contas que se repetem".

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `AppShell.test.tsx` › `shows Planejar when module is on` | módulo ligado → cinco itens, "Planejar" entre "Novo gasto" e "Mais" |
| `AppShell.test.tsx` › `four items when module is off` | desligado → quatro itens, sem "Planejar" |
| `PlanHub.test.tsx` › `adult sees four entries` | `ADULT` → Orçamento, Metas, Reserva e Recorrências |
| `PlanHub.test.tsx` › `youth sees goals and recurrences only` | `YOUTH` → só Metas e Recorrências |
| `routes.test.tsx` › `old recurrences path redirects` | `/mais/recorrencias` → `/planejar/recorrencias` |
| `routes.test.tsx` › `youth is sent back from budget` | `YOUTH` em `/planejar/orcamento` → `/planejar` |
| `MorePage.test.tsx` › `no recurrences entry` | `MorePage` sem "Recorrências" |
| `PlanningInvalidator.test.tsx` › `invalidates on any local action` | ação com `fromQueue true` → `invalidateQueries` com `['planning', ws]` |

- [ ] **Step 2: Rodar e ver falhar**

Run (na raiz): `corepack pnpm --dir web test -- AppShell PlanHub routes MorePage PlanningInvalidator`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". O `recurrences.spec.ts` passa a entrar por Planejar.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` → verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the Planejar tab and hub, and move recurrences under it"
```

---

### Task 10: Tela de Orçamento, toast e resumo do mês

**Files:**
- Create em `features/planning/`: `BudgetPage.tsx`, `BudgetLine.tsx`, `BudgetLimitSheet.tsx`,
  `budgetToast.ts`, `BudgetToastWatcher.tsx`, `BudgetHomeCard.tsx`, `monthSummary.ts` e os testes.
- Modify: `features/planning/register.ts`, `features/planning/messages.pt-BR.ts`.

**Interfaces:**
- Consumes: `useBudgets`, `useSetBudget`, `useDeleteBudget`, `useCopyBudgets` (Task 8);
  `useCategories` e `useToast` do `shared`; `MonthSwitcher` do `shared/ui`.
- Produces:
  - `budgetToastFor(lines, categoryId, parentId, seen: (key) => boolean)` →
    `{ message: 'near' | 'at' | 'over'; categoryId: string; overBy?: Money; keys: string[] } | null`
    (D18, puro);
  - `monthSummary(lines)` → `{ within: number; total: number }` (D19, puro);
  - entradas de slot: `BudgetToastWatcher` (`workspace`, `OWNER` e `ADULT`) e `BudgetHomeCard`
    (`home`, `OWNER`, `ADULT` e `VIEWER`).

**Como fica:**
- **Linha (spec 6.5):** nome, barra, "R$ 960,00 de R$ 1.200,00" e o uso em %. `UNDER` usa a cor
  primária; `NEAR` e `AT`, âmbar com "Chegou a 80% do limite" ou "Chegou ao limite"; `OVER`,
  vermelho com "Passou R$ 60,00". Texto sempre junto da cor.
- **Tela:** seletor de mês, as linhas, "Definir limite" (abre `BudgetLimitSheet` com a lista das
  categorias de gasto sem limite naquele mês) e "Copiar do mês anterior". Tocar numa linha abre o
  mesmo sheet com o valor e "Remover limite". 409 mostra "Esse limite mudou. Confira o valor." e
  recarrega. Sem rede, os botões ficam desligados com "Sem conexão. Limites precisam de internet."
- **Vazio:** "Nenhum limite neste mês." e, se o mês anterior teve limites, o botão de copiar.
- **Card do Início:** até 3 linhas com maior uso e "Ver orçamento"; no começo do mês, o resumo de
  D19 acima delas.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `budgetToast.test.ts` › `first crossing of 80` | linha `NEAR` e nada visto → `near`, chaves `[...:80]` |
| `budgetToast.test.ts` › `jumping past 100 marks both` | linha `OVER` e nada visto → `over` com `overBy`, chaves `[...:100, ...:80]` |
| `budgetToast.test.ts` › `already seen is silent` | `NEAR` com `80` visto → `null` |
| `budgetToast.test.ts` › `parent line counts` | gasto na filha, limite só na mãe em `AT` → `at` para a mãe |
| `BudgetToastWatcher.test.tsx` › `toasts once per threshold` | duas ações locais seguidas em `NEAR` → um toast "Mercado: chegou a 80% do limite" |
| `BudgetToastWatcher.test.tsx` › `ignores queued actions` | ação com `fromQueue true` → nenhum toast |
| `BudgetToastWatcher.test.tsx` › `storage failure is silent` | `localStorage` que lança → nenhum erro na tela |
| `monthSummary.test.ts` › `counts within limit` | 8 linhas, 2 com `spent > limit` → `{ within: 6, total: 8 }`; `AT` conta como dentro |
| `BudgetHomeCard.test.tsx` › `shows previous month summary and copies` | mês anterior com limites, chave vazia → "Novembro: 6 de 8 categorias dentro do limite"; "Copiar limites" → chama a cópia, grava a chave e o resumo some |
| `BudgetHomeCard.test.tsx` › `viewer sees summary without copy` | `VIEWER` → resumo sem o botão |
| `BudgetPage.test.tsx` › `line shows text with color` | linha `OVER` → "Passou R$ 60,00" visível e `aria-valuenow 105` |
| `BudgetPage.test.tsx` › `version conflict reloads` | `PUT` com 409 → a mensagem de conflito e nova busca |
| `BudgetPage.test.tsx` › `copy from previous month` | botão → chamada e "1 limite copiado" |

- [ ] **Step 2: Rodar e ver falhar**

Run (na raiz): `corepack pnpm --dir web test -- budgetToast BudgetToastWatcher monthSummary BudgetHomeCard BudgetPage`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica", D18 e D19.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` → verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the budget screen, the limit toast and the month summary"
```

---

### Task 11: Tela de Metas e a celebração

**Files:**
- Create em `features/planning/`: `GoalsPage.tsx`, `GoalSheet.tsx`, `GoalForm.tsx`,
  `ContributionSheet.tsx`, `goalIcons.ts`, `GoalCelebration.tsx`, `celebration.ts`,
  `GoalCelebrationWatcher.tsx` e os testes.
- Modify: `features/planning/register.ts`, `features/planning/messages.pt-BR.ts`.

**Interfaces:**
- Consumes: `useGoals`, `useCreateGoal`, `useUpdateGoal`, `useArchiveGoal`, `useContributions`,
  `useAddContribution` (Task 8); `useAccounts` do `shared`; `emitLocalAction`, `onLocalAction`.
- Produces:
  - `goalsToCelebrate(goals, celebrated: Set<string>, now: Date)` →
    `{ celebrate: string[]; markSilently: string[] }` (D20, puro);
  - entrada de slot `GoalCelebrationWatcher` (`workspace`, `OWNER`, `ADULT` e `YOUTH`).

**Como fica:**
- **Lista:** cartão por meta com ícone (`goalIcons.ts` liga as chaves de D15 a um emoji com
  `aria-hidden`), nome, "R$ 3.000,00 de R$ 12.000,00", barra com %, prazo ("até ago/2027") e
  "Sugestão: R$ 1.000,00 por mês". Atingidas ficam abaixo com "Atingida".
- **Criar:** com `limitReached`, o botão "Nova meta" abre só o texto de D11, antes de qualquer
  campo. A conta vinculada é opcional ("Acompanhar o saldo de uma conta"), só com contas ativas da
  moeda base.
- **Detalhe:** aportes, "Guardar" e "Retirar" (sem vínculo), "Editar" e "Arquivar" (com
  confirmação). Com vínculo: "Acompanha o saldo de {conta}" (para o Jovem, "Acompanha o saldo de
  uma conta da família").
- O aporte salvo emite `contribution-saved`. Sem rede, as ações ficam desligadas com "Sem conexão.
  Metas precisam de internet."
- **Celebração:** `GoalCelebration` em `role="status"`, "Meta atingida: {nome}!", animação de até
  1 s com `opacity` e `transform`, parada com `prefers-reduced-motion`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `celebration.test.ts` › `celebrates recent unseen` | meta atingida ontem, não celebrada → em `celebrate` |
| `celebration.test.ts` › `doesNotCelebrateOldGoals` | atingida há 8 dias → em `markSilently` |
| `celebration.test.ts` › `already celebrated is skipped` | id já guardado → em nenhuma das duas |
| `GoalCelebrationWatcher.test.tsx` › `celebrates after a local save` | ação `transaction-saved` local e meta recém-atingida → "Meta atingida: Viagem!" uma vez |
| `GoalCelebrationWatcher.test.tsx` › `ignoresQueuedActions` | ação com `fromQueue true` → nada |
| `GoalsPage.test.tsx` › `opening celebrates once` | abrir Metas com meta recém-atingida → celebra; abrir de novo → não |
| `GoalsPage.test.tsx` › `limit reached explains first` | `limitReached true` → "Nova meta" mostra o texto de D11 e nenhum campo |
| `GoalsPage.test.tsx` › `youth sees no actions` | `YOUTH` → sem "Nova meta", "Guardar" e "Arquivar"; meta vinculada com "uma conta da família" |
| `ContributionSheet.test.tsx` › `negative balance message` | API com 422 `goal.negative-balance` → "O guardado não pode ficar negativo." |
| `ContributionSheet.test.tsx` › `emits contribution-saved` | aporte salvo → `emitLocalAction` com `contribution-saved` |
| `GoalCelebration.test.tsx` › `reduced motion is static` | `matchMedia('(prefers-reduced-motion: reduce)')` → sem classe de animação |

- [ ] **Step 2: Rodar e ver falhar**

Run (na raiz): `corepack pnpm --dir web test -- celebration GoalCelebration GoalsPage ContributionSheet`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica" e D20.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` → verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the goals screen and the goal celebration"
```

---

### Task 12: Tela da Reserva e os interruptores de conta e categoria

**Files:**
- Create em `features/planning/`: `ReservePage.tsx`, `ReserveHomeCard.tsx` e os testes.
- Modify: `features/planning/register.ts`, `features/planning/messages.pt-BR.ts`; o formulário de
  edição em `features/accounts` e o de `features/categories`, mais os testes deles.

**Interfaces:**
- Consumes: `useReserve`, `useUpdateReserve` (Task 8); `useModuleEnabled('planning')`; as mutações de
  conta e categoria com `isReserve` e `isFixed` (Task 1).
- Produces: entrada de slot `ReserveHomeCard` (`home`, `OWNER`, `ADULT` e `VIEWER`).

**Como fica:**
- **Card e tela (spec 6.7):** "A reserva cobre 4,0 meses de gastos", barra até a meta e "Faltam
  R$ 9.000,00 para 6 meses" (com `missing = 0`, "A reserva já cobre a meta de 6 meses").
  - `NONE`: no lugar do número, "Quanto a família gasta por mês, mais ou menos?" com um campo de
    valor (Dono e Adulto).
  - Sem conta marcada: "Marque uma conta como reserva em Contas."
- **Texto educativo fixo da tela:** "Uma reserva de emergência é um dinheiro guardado para
  imprevistos, como uma conta inesperada ou um mês sem renda. Ela fica fácil de usar e não serve para
  outras metas." Nenhum produto financeiro citado.
- **Meta da reserva:** de 1 a 24 meses, com seletor, para Dono e Adulto.
- **Interruptores:** "Esta conta é reserva" na edição da conta e "Gasto fixo" na edição da categoria
  de gasto, só com o módulo ligado e para Dono e Adulto. Sempre mandam o valor no `PATCH`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ReservePage.test.tsx` › `shows coverage and missing` | `months 4.0`, `missing 9000` → "A reserva cobre 4,0 meses de gastos" e "Faltam R$ 9.000,00 para 6 meses" |
| `ReservePage.test.tsx` › `asks for monthly cost` | `costSource NONE` → a pergunta e o campo; salvar → `PATCH` com `manualMonthlyCost` |
| `ReservePage.test.tsx` › `viewer cannot edit` | `VIEWER` → sem campo nem seletor |
| `ReservePage.test.tsx` › `no reserve accounts hint` | `hasReserveAccounts false` → "Marque uma conta como reserva em Contas." |
| `ReserveHomeCard.test.tsx` › `links to the screen` | card → link para `/planejar/reserva` |
| teste da edição de conta | módulo ligado e `ADULT` → interruptor "Esta conta é reserva"; desligado → sem interruptor |
| teste da edição de categoria | categoria de receita → sem "Gasto fixo"; de gasto → com ele |

- [ ] **Step 2: Rodar e ver falhar**

Run (na raiz): `corepack pnpm --dir web test -- ReservePage ReserveHomeCard accounts categories`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". O núcleo lê a flag do módulo pelo
  `useModuleEnabled`, sem importar `features/planning`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` → verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the reserve screen and the reserve and fixed toggles"
```

---

### Task 13: Cards de projeção e de fixos e variáveis no Início

**Files:**
- Create em `features/planning/`: `ProjectionHomeCard.tsx`, `ProjectionSheet.tsx`,
  `SpendingSplitHomeCard.tsx` e os testes.
- Modify: `features/planning/register.ts`, `features/planning/messages.pt-BR.ts`.

**Interfaces:**
- Consumes: `useProjection`, `useSpendingSplit` (Task 8).
- Produces: entradas de slot `ProjectionHomeCard` e `SpendingSplitHomeCard` (`home`, `OWNER`,
  `ADULT` e `VIEWER`), registradas nesta ordem depois do orçamento e da reserva.

**Como fica:**
- **Projeção (spec 6.8):** card "Saldo previsto · estimativa" com os quatro valores e o mês de cada
  um ("fim de nov", "fim de dez"...). Valor negativo aparece com o sinal e a cor do texto normal, sem
  vermelho.
- Tocar abre o `ProjectionSheet`, com a conta de cada ponto em três linhas: "Saldo de hoje",
  "Recorrências e previstos" e "Gasto estimado". Embaixo, "É uma estimativa com a média dos
  gastos sem recorrência dos últimos 3 meses."
- Sem histórico, a nota vira "Ainda não há 3 meses de gastos para estimar o resto."
- **Fixos e variáveis:** "Gastos fixos R$ X · Variáveis R$ Y" do mês atual, com uma barra dividida.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ProjectionHomeCard.test.tsx` › `shows four values labelled estimate` | quatro pontos → o título com "estimativa" e quatro valores |
| `ProjectionHomeCard.test.tsx` › `negative is not red` | ponto negativo → sem a classe de cor de perigo |
| `ProjectionSheet.test.tsx` › `explains each part` | abrir → as três linhas com os valores do ponto |
| `ProjectionSheet.test.tsx` › `without history note` | `hasHistory false` → "Ainda não há 3 meses de gastos para estimar o resto." |
| `SpendingSplitHomeCard.test.tsx` › `shows both totals` | `fixed 2350`, `variable 400` → "R$ 2.350,00" e "R$ 400,00" |
| `HomePage.test.tsx` › `youth sees no planning cards` | `YOUTH` com o módulo ligado → nenhum dos quatro cards |

- [ ] **Step 2: Rodar e ver falhar**

Run (na raiz): `corepack pnpm --dir web test -- ProjectionHomeCard ProjectionSheet SpendingSplitHomeCard HomePage`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` → verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the projection and fixed and variable cards to the home screen"
```

---

### Task 14: E2E, bundle e docs

**Files:**
- Create: `web/e2e/planning.spec.ts`.
- Modify: `web/e2e/a11y.spec.ts`, `web/e2e/support.ts`, `web/e2e/core-without-modules.spec.ts`,
  `docs/data-model.md`, `docs/architecture.md`, `docs/superpowers/plans/README.md`.

**Interfaces:**
- Consumes: tudo das Tasks 1 a 13.

**Como fica:**
- `planning.spec.ts` roda contra a API real do compose de e2e, como os outros.
- O `data-model.md` registra as tabelas e colunas da V17. O `architecture.md` registra a porta
  `LedgerFigures`, os eventos do `ledger`, a dependência `planning → billing`, os slots e o
  barramento local.
- O README dos planos marca a 7.2 como concluída na linha 7.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `planning.spec.ts` › `budget crossing 80% shows the toast` | limite de R$ 100 em Mercado, gasto de R$ 85 → toast "Mercado: chegou a 80% do limite"; outro gasto de R$ 1 → nenhum toast novo |
| `planning.spec.ts` › `closing the month copies limits` | limites no mês anterior (seed) → o Início mostra o resumo; "Copiar limites" → a tela de Orçamento do mês atual com os limites |
| `planning.spec.ts` › `goal reached celebrates once` | meta de R$ 100, aporte de R$ 100 → "Meta atingida"; recarregar e abrir Metas → não celebra de novo |
| `planning.spec.ts` › `free plan goal limit` | cobrança ligada no e2e, 3 metas ativas → "Nova meta" mostra o texto do limite |
| `a11y.spec.ts` | axe sem violações em `/planejar`, `/planejar/orcamento`, `/planejar/metas`, `/planejar/reserva` e `/inicio` com os cards |
| `core-without-modules.spec.ts` (S2) | módulo desligado → barra com quatro itens, Início sem cards do planejamento, `/planejar` some, e o núcleo inteiro continua |

O teste do limite só roda se o compose de e2e liga a cobrança; se não liga, ele fica de fora com um
comentário e o `GoalLimitIT` cobre a regra.

- [ ] **Step 2: Rodar e ver falhar**

Run (na raiz): `corepack pnpm --dir web e2e -- planning a11y core-without-modules`
Expected: FAIL em `planning.spec.ts` (o arquivo é novo).

- [ ] **Step 3: Implementar.** Ajuste `support.ts` (seed de limites no mês anterior e helpers de
  meta) e escreva as docs.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `corepack pnpm --dir web check:chunks`.
  Expected: PASS, com o carregamento inicial ≤ 200 KB e nenhum arquivo de `features/planning` no
  chunk inicial.

- [ ] **Step 5: Conferir.** `./gradlew build` em `api/` e `corepack pnpm --dir web check` → verdes.

- [ ] **Step 6: Commit**

```bash
git add web/e2e docs
git commit -m "test(e2e): cover budget, goals and the module-off core for Plan 7.2"
```
