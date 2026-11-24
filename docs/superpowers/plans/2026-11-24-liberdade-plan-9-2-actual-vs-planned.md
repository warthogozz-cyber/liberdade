# Liberdade · Plano 9.2: Real vs. planejado — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a segunda entrega da v0.4.
- Receitas e despesas ganham o campo opcional "Projeto", inclusive offline (US-42).
- A tela do projeto mostra o real contra o planejado mês a mês e o payback real por fluxo de caixa
  (US-42).
- A conquista "Primeiro projeto de renda extra" (`FIRST_VENTURE`) entra na gamificação do 8.3.

**Architecture:**
- **`ledger`:** a V31 põe `venture_id` em `transactions` com chave composta para `ventures`. O
  `ledger` não conhece `ventures`: grava o id, deixa o banco garantir a família e oferece uma
  leitura pública por projeto e mês.
- **`ventures`:** um domínio puro novo junta o planejado (o cálculo da 9.1) e o real (a leitura do
  `ledger`) e acha o payback real. Uma rota de leitura só.
- **`gamification`:** ouve `VentureActivated` (evento público novo de `ventures`).
- **Front:** o campo no formulário de lançamento, o filtro na lista e a tela de real vs. planejado.

**Tech Stack:** o mesmo da 9.1. Sem dependências novas.

**Spec:**
- [Spec do Plano 9](../specs/2026-11-24-liberdade-plan-9-extra-income-tracks-design.md), aprovado
  pelo mantenedor em 2026-11-24. Este plano cobre a 9.2: a V31 da seção 6.2, as seções 6.9 a 6.11,
  a 6.12 no que toca a 9.2 e a parte 9.2 das seções 7 e 8. É a autoridade.
- [Plano 9.1](2026-11-24-liberdade-plan-9-1-projects-simulation-tracks.md): os nomes que este plano
  consome (D1 a D12 de lá).

## Pré-requisito de execução

**Este plano só é executado com a 9.1 na `main`** (e, por ela, o 8.2 e o 8.3).

Antes da Task 1, o implementador **confere este plano contra o código da `main`** e anota no relatório
o que mudou. Este plano foi escrito antes do 8.3 e da 9.1 existirem em código, então a conferência
cobre:
- os nomes das colunas de `transactions` (`kind`, `occurred_on`, `status`, `deleted_at`) contra a V4
  e o que veio depois;
- a última migração é a V30 e nenhuma usa V31;
- o `FORMAT_VERSION` do `ExportService` (a 9.1 deixa 6; a 9.2 sobe para 7);
- **a API de conquistas do 8.3:** onde ficam as chaves (enum, tabela de definições ou texto), como
  um ouvinte avalia e grava (`Achievements.evaluate` ou outro nome), como fica o `silent`, como a
  tela "Conquistas" lista as que faltam, e as dependências declaradas de `gamification`;
- os nomes que a 9.1 entregou: `VentureService.figuresOf`, `VentureRepository`, `VentureFigures`,
  `venturesKeys`, `useVentures`, `FiguresPanel`, `figuresText`, `VenturePage`;
- como o formulário de lançamento e a fila offline guardam um campo novo hoje (etiquetas e pessoa),
  e o fluxo "precisa de atenção" para 422.

Se algo não bater, o implementador segue o código, ajusta o nome e anota. Se a mudança mexer numa
decisão do spec, para e pergunta ao mantenedor.

## Global Constraints

As Global Constraints da 9.1 valem aqui (idiomas, commits, dados pessoais, cálculo exato, papéis,
módulo, erros, logs, contrato, cobertura, números na tela, ética, desempenho, acessibilidade, custo,
versões). Além delas:

- **Migrações:** só V31 nesta entrega.
- **Fronteira:** `ledger` não importa nada de `ventures`; `ventures` lê o real só por
  `LedgerFigures`; `gamification` soma `"ventures"` às dependências e lê só eventos e tipos públicos.
  `ventures` nunca depende de `gamification`.
- **Marca:** só em `INCOME` e `EXPENSE`. Em transferência, 422 `transaction.venture-invalid`.
- **Edição:** `ventureId` nulo mantém o projeto; `clearVenture: true` tira. Os dois juntos → 422
  `transaction.venture-invalid`.
- **Jovem:** a resposta nunca traz `ventureId`; mandar `ventureId` ou `clearVenture` → 422
  `transaction.venture-invalid`; editar um lançamento marcado mantém a marca.
- **Texto do 422:** "Esse projeto não existe mais nesta família. Tire o projeto do lançamento e
  envie de novo."
- **Módulo desligado:** a marca fica no lançamento; o campo, o filtro e o nome do projeto no
  detalhe somem; a API segue aceitando e mantendo `ventureId` (o `ledger` não olha o módulo).
- **Real:** só lançamentos confirmados e não excluídos.
- **Textos neutros:** "abaixo do previsto", "acima do previsto", "igual ao previsto", "ainda não se
  pagou: faltam R$ X", "o resultado ficou positivo desde o início". Sem vermelho, sem projeção de
  data.
- **Conquista:** premia começar, não faturar; só com o módulo de gamificação ligado; segue as regras
  do 8.3 (`silent` fora de ação local, celebração só depois da ação).

### Pré-requisitos locais

Os mesmos da 9.1.

## Entrega

- **PR 1 (docs):** já entrou com o plano 9.1.
- **PR 2 (código):** branch `feat/plan-9-2-actual-vs-planned`, cortada da `main` depois da 9.1. Um
  commit por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o mantenedor confere o critério de saída da 9.2 (spec, seção 8): no staging,
  lançamentos marcados, inclusive um feito offline, aparecem no real vs. planejado e o payback real
  bate com a conta à mão. A v0.4 sai quando um projeto real da família for acompanhado por 1 mês.

### Itens herdados

| Item | Onde |
|---|---|
| Conquista adiada pelo Plano 8 | Task 5 |
| O que a conferência do pré-requisito achar | Anotado no relatório da Task 1 |

## Decisões deste plano

**D1. Gravação no `ledger`.**
- `CreateTransactionRequest` ganha `@Nullable UUID ventureId`; `UpdateTransactionRequest` ganha
  `@Nullable UUID ventureId` e `@Nullable Boolean clearVenture`; `TransactionView` ganha `@Nullable
  UUID ventureId`; `ExportedTransaction` também.
- O serviço não lê `ventures`: grava e traduz a violação da chave composta
  (`transactions_workspace_id_venture_id_fkey` ou o nome que o Postgres der; o teste prende o nome)
  e a do `CHECK` `transactions_venture_kind` em `LedgerRejectedException` 422
  `transaction.venture-invalid` com o texto das Global Constraints. Uma checagem antes de gravar
  (`kind = TRANSFER` com projeto) dá a mesma resposta sem esperar o banco.
- Mudar um lançamento marcado para transferência sem `clearVenture` → 422 (o `CHECK` pega).
- Confirmar um previsto (`PlannedTransactionService`) mantém o que estiver no lançamento.
- **Jovem:** o controller já sabe o papel; o serviço recebe `WorkspaceMember` e, para `YOUTH`,
  recusa os dois campos e devolve a view com `ventureId = null` em toda leitura de lançamento
  (lista, detalhe, criar, editar). A linha guardada nunca muda por causa dele.
- Lista: parâmetro `ventureId` em `GET /transactions`, ignorado para o Jovem (a lista dele sai
  como se o filtro não existisse).

**D2. Leitura do real** (nova em `LedgerFigures`).
- `List<VentureMonth> ventureMonths(UUID workspaceId, UUID ventureId)`: uma linha por mês com algum
  lançamento confirmado e não excluído marcado com o projeto, mais antigo primeiro.
- `public record VentureMonth(YearMonth month, BigDecimal income, BigDecimal expense)` no pacote
  raiz de `ledger`; somas exatas, sem arredondar; meses sem lançamento não vêm.
- O mês é o de `occurred_on`.

**D3. Domínio do real vs. planejado** (`ventures/domain`).
- `ActualVsPlanned.compute(VentureFigures planned, @Nullable LocalDate startedOn, @Nullable LocalDate
  closedOn, VentureStatus status, List<VentureMonth> actual, YearMonth currentMonth):
  ActualVsPlannedResult`. Recebe `VentureMonth` com `(month, income, expense)` — `ventures` usa o
  tipo do `ledger`, que é público.
- **Fim** = mês de `closedOn` ou `currentMonth`. **Começo do real** = mês do primeiro `VentureMonth`
  (nenhum → sem real). **Começo do planejado** = mês de `startedOn` (`PLANNING` → sem planejado).
  Lançamentos depois do fim (possível ao reabrir ou com data futura) ficam fora.
- `record MonthRow(YearMonth month, BigDecimal income, BigDecimal expense, BigDecimal result,
  @Nullable BigDecimal plannedIncome, @Nullable BigDecimal plannedCost, @Nullable BigDecimal
  plannedResult, @Nullable Comparison comparison)`; `enum Comparison { BELOW, ABOVE, EQUAL }`
  compara o resultado real com o previsto; meses fora do período do planejado levam `null` nos três
  previstos e na comparação. Planejado por mês = `plannedRevenue`, `plannedCost` e `monthlyProfit`
  da 9.1.
- `months`: os últimos 12 meses do período inteiro (do menor começo ao fim), inclusive meses sem
  lançamento (zeros), mais recente primeiro. `totals`: as mesmas somas sobre o período inteiro (não
  só os 12), com o planejado somado só nos meses do período do planejado.
- **Payback real (`RealPayback`):** acumula `income − expense` mês a mês desde o começo do real,
  meses vazios inclusive, até o fim.
  - `realInvestment` = −(menor acumulado) se ele for < 0; senão `null`;
  - `state`: `PAID_BACK` com `months` = meses do começo do real até o primeiro mês em que o acumulado
    volta a ≥ 0 depois de ter ficado < 0 (o mês do começo conta como 1); `NOT_YET` com `missing` =
    −acumulado final; `NEVER_NEGATIVE`; `NO_DATA` sem lançamento marcado;
  - depois de `PAID_BACK`, ficar negativo de novo não muda o estado (o payback já aconteceu); o
    `realInvestment` segue sendo o menor acumulado de todo o período.
- Dinheiro arredondado com 2 casas `HALF_EVEN` só na saída.

**D4. Rota.** `GET /v1/workspaces/{ws}/ventures/{id}/actual-vs-planned` (Dono, Adulto e Leitor;
Jovem 403; `@RequiresModule(VENTURES)`) devolve `ActualVsPlannedView(months, totals, planned:
{initialInvestment, paybackMonths, paybackState}, real: {investment, state, months, missing},
currency)`, com dinheiro como `Money`. O mês atual vem do fuso da família.

**D5. Conquista.**
- `ventures` publica `VentureActivated(UUID workspaceId, UUID ventureId, @Nullable UUID
  createdByMembershipId)` na passagem `PLANNING → ACTIVE` (não na reabertura).
- `gamification` soma `FIRST_VENTURE` (nome "Primeiro projeto de renda extra", o que leva a ela:
  "Começar a vender num projeto de renda extra") e um ouvinte depois do commit que dá a conquista a
  quem criou o projeto, se a pessoa ainda for membro, não for Jovem e o módulo estiver ligado. Não
  `silent` (é ação local de alguém; a celebração segue o padrão do 8.3 e só aparece para quem fez a
  ação).
- Na avaliação ampla do 8.3 (ligar o módulo, membro novo, primeira leitura): quem criou algum
  projeto que já tem `started_on` ganha `FIRST_VENTURE` como `silent`. A leitura vem de uma consulta
  pública nova `VenturesExport.anyStartedBy(UUID ws, UUID membershipId): boolean` (a interface já é
  pública e lida por fora).

**D6. Exportação v7.** `ExportedTransaction` ganha `ventureId`; `lancamentos.csv` ganha a coluna
`projeto` (nome do projeto, vazio sem marca; o nome vem de `VenturesExport.ventures`).
`FORMAT_VERSION` sobe uma versão (para 7, conferido no pré-requisito). `docs/data-model.md` registra
a V31.

**D7. Web: formulário e lista.**
- `TransactionForm` ganha o campo "Projeto (opcional)" (um `select` com "Nenhum" e os projetos
  fora de `CLOSED`, de `useVentures`), em receita e despesa, só com `useModuleEnabled('ventures')`,
  a pessoa não sendo Jovem e havendo projeto aberto. Um lançamento já marcado com um projeto
  encerrado mostra esse projeto como opção também.
- Criar manda `ventureId` (ou nada). Editar manda `ventureId` quando há projeto e `clearVenture:
  true` quando a pessoa escolheu "Nenhum" num lançamento que tinha; sem mudança, não manda nenhum
  dos dois. Trocar para transferência limpa o campo e manda `clearVenture: true` se havia marca.
- A fila offline guarda o pedido como hoje (os campos vão juntos). O 422
  `transaction.venture-invalid` cai em "precisa de atenção" pelo fluxo que já existe; o detalhe
  mostra o texto da API.
- `TransactionFilters` ganha "Projeto" com as mesmas condições do campo; o filtro entra na URL
  como `projeto=<id>` e vai à API como `ventureId`. `TransactionDetailSheet` mostra "Projeto: nome"
  (nome vindo de `useVentures`; projeto excluído não aparece).
- A lista de projetos já é persistida (`meta.persist`, 9.1), então o campo funciona offline.

**D8. Web: tela do real vs. planejado.** Rota `/mais/projetos/:id/real` (dentro das mesmas guardas
da 9.1), aberta pela linha "Real vs. planejado ›" na `VenturePage`, que aparece fora de
`PLANNING`.
- Cartões lado a lado: investimento e payback planejados × investimento real e payback real, com os
  textos de D3 ("se pagou em 3 meses", "ainda não se pagou: faltam R$ 120,00", "o resultado ficou
  positivo desde o início" com a dica "Marque também as compras do começo para ver o investimento
  real.", "nenhum lançamento marcado ainda").
- Tabela dos meses (mês, receita, despesa, resultado, previsto, diferença em R$ com o texto
  neutro), com `<caption>` e cabeçalhos de linha e coluna; no celular, um cartão por mês. Linha de
  total. Status atual do projeto acima da tabela.
- Link "Ver lançamentos do projeto" para `/lancamentos?projeto=<id>`.
- A tela não faz conta: só formata o que a API mandou.

## Review Focus

1. **Lançamento marcado feito offline, e o projeto excluído antes de sincronizar:** o item cai em
   "precisa de atenção" com o texto do 422, a pessoa tira o projeto e envia. Testes:
   `TransactionVentureIT.deletedVentureIs422` (Task 1) e `sync.test.ts ›
   ventureInvalidGoesToAttention` (Task 7).
2. **App antigo ou fila de antes da 9.2 editando um lançamento marcado:** o pedido sem os campos
   novos mantém a marca. Testes: `TransactionVentureIT.updateWithoutFieldsKeepsVenture` (Task 1) e
   `EditTransaction.test.tsx › untouchedVentureSendsNothing` (Task 7).
3. **Projeto encerrado e reaberto, com lançamentos depois do encerramento:** o período para no
   mês de `closed_on` enquanto encerrado e volta a correr ao reabrir. Testes:
   `ActualVsPlannedTest.closedStopsAtClosedMonth` e `reopenedRunsToCurrentMonth` (Task 3).
4. **Compras do começo marcadas antes de `started_on`:** entram no real e no payback real, e o
   planejado só começa em `started_on`. Teste: `ActualVsPlannedTest.purchasesBeforeStart`
   (Task 3).
5. **Jovem que edita um lançamento marcado por um adulto:** a marca fica e ele nunca vê o id.
   Testes: `TransactionVentureIT.youthEditKeepsVentureAndHidesIt` (Task 1) e
   `TransactionForm.test.tsx › youthNeverSeesTheField` (Task 7).

## Mapa de arquivos

Mesmas raízes da 9.1.

| Arquivo | Task | Papel |
|---|---|---|
| `api/src/main/resources/db/migration/V31__transaction_venture.sql` | 1 | coluna, chave, `CHECK` e índice |
| `ledger/internal/{CreateTransactionRequest,UpdateTransactionRequest,TransactionView,TransactionService,TransactionRepository,TransactionController,PlannedTransactionService}.java` | 1 | gravar, ler e filtrar |
| `ledger/{VentureMonth,LedgerFigures}.java`, `ledger/internal/JdbcLedgerFigures.java` | 2 | leitura por projeto e mês |
| `ventures/domain/{ActualVsPlanned,ActualVsPlannedResult,MonthRow,Comparison,RealPayback,RealPaybackState}.java` | 3 | domínio |
| `ventures/internal/{ActualVsPlannedController,ActualVsPlannedView}.java` | 4 | rota |
| `ventures/VentureActivated.java`, `ventures/VenturesExport.java`, `ventures/internal/{VentureService,JdbcVenturesExport}.java`, `gamification/**` | 5 | conquista |
| `ledger/ExportedTransaction.java`, `ledger/internal/LedgerJdbcExport.java`, `privacy/internal/ExportService.java`, `docs/data-model.md` | 6 | exportação v7 e docs |
| `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java` | 1, 4 | contrato |
| `features/transactions/{TransactionForm,EditTransaction,TransactionFilters,TransactionDetailSheet,TransactionsPage}.tsx`, `features/transactions/VenturePicker.tsx`, `mocks/{handlers,data}.ts` | 7 | campo, filtro e detalhe |
| `features/ventures/{ActualVsPlannedPage,RealPaybackCards,MonthsTable}.tsx`, `features/ventures/VenturePage.tsx`, `shared/api/ventures.ts`, `app/routes.tsx` | 8 | tela |
| `web/e2e/ventures.spec.ts`, `web/e2e/a11y.spec.ts` | 9 | e2e |

## Tasks

### Task 1: V31 e a marca nos lançamentos

**Files:**
- Create: `db/migration/V31__transaction_venture.sql` (o SQL da seção 6.2 do spec).
- Modify: os arquivos de `ledger/internal` da Task 1 no mapa, `api/openapi.json`,
  `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `ledger/TransactionVentureIT.java`, `RoleMatrixIT`, `OpenApiDriftIT`, os testes de
  lançamento que já existem.

**Interfaces:**
- Consumes: a tabela `ventures` (V30).
- Produces: `ventureId` em `CreateTransactionRequest`, `UpdateTransactionRequest` (com
  `clearVenture`) e `TransactionView`; o parâmetro `ventureId` em `GET /transactions`; o código
  `transaction.venture-invalid` (D1).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `createIncomeWithVenture` | receita com `ventureId` da família → 201, a resposta traz `ventureId` |
| `transferWithVentureIs422` | transferência com `ventureId` → 422 `transaction.venture-invalid` com o texto exato |
| `otherFamilyVentureIs422` | `ventureId` de outra família → 422 `transaction.venture-invalid` |
| `deletedVentureIs422` | projeto apagado → 422 `transaction.venture-invalid` |
| `closedVentureAccepts` | projeto `CLOSED` → 201 |
| `updateWithoutFieldsKeepsVenture` | `PUT` sem `ventureId` nem `clearVenture` → marca mantida |
| `clearVentureRemovesIt` | `clearVenture: true` → `ventureId` nulo |
| `bothFieldsIs422` | `ventureId` e `clearVenture: true` juntos → 422 |
| `toTransferWithoutClearIs422` | lançamento marcado mudado para transferência sem `clearVenture` → 422 |
| `deletingVentureUnmarks` | apagar o projeto → o lançamento continua, `ventureId` nulo |
| `confirmPlannedKeepsVenture` | previsto marcado, confirmar → segue marcado |
| `youthCannotSendVentureFields` | Jovem com `ventureId` ou `clearVenture` → 422 |
| `youthEditKeepsVentureAndHidesIt` | Jovem edita um lançamento marcado → marca guardada igual; a resposta, a lista e o detalhe dele trazem `ventureId: null` |
| `filterByVenture` | `GET /transactions?ventureId=` → só os marcados com ele; para o Jovem, o filtro é ignorado |
| `RoleMatrixIT` | as linhas de lançamento seguem iguais |
| `OpenApiDriftIT` | passa com o contrato regerado |

- [ ] **Step 2: Rodar e ver falhar.** Run (em `api/`): `./gradlew test --tests
  '*TransactionVentureIT' --tests '*Transaction*'`. Expected: FAIL.
- [ ] **Step 3: Implementar** a V31 e D1.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Regerar o contrato.** `UPDATE_OPENAPI=true ./gradlew test --rerun --tests
  '*OpenApiDriftIT'`, depois `corepack pnpm --dir web gen:api`.
- [ ] **Step 6: Conferir.** `./gradlew build` e `corepack pnpm --dir web typecheck` → passam.
- [ ] **Step 7: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(ledger): link incomes and expenses to a venture"
```

### Task 2: A leitura do real por projeto

**Files:**
- Create: `ledger/VentureMonth.java`.
- Modify: `ledger/LedgerFigures.java`, `ledger/internal/JdbcLedgerFigures.java`.
- Test: `ledger/LedgerFiguresVentureIT.java`.

**Interfaces:**
- Consumes: a coluna `venture_id` (Task 1).
- Produces: `LedgerFigures.ventureMonths(UUID workspaceId, UUID ventureId): List<VentureMonth>` e
  `record VentureMonth(YearMonth month, BigDecimal income, BigDecimal expense)` (D2).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `sumsByMonth` | duas receitas e uma despesa em nov., uma despesa em dez. → duas linhas, nov. primeiro, somas exatas |
| `skipsPlannedAndDeleted` | previsto e excluído marcados → fora |
| `skipsOtherVentures` | lançamento de outro projeto → fora |
| `monthsWithoutTransactionsAreAbsent` | out. e dez. com lançamento → sem linha de nov. |
| `otherFamilyGetsNothing` | `workspaceId` de outra família → lista vazia |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*LedgerFiguresVentureIT'`.
  Expected: FAIL.
- [ ] **Step 3: Implementar** a consulta (agrupada por `date_trunc('month', occurred_on)`, usando
  o índice da V31).
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(ledger): add the monthly figures of a venture"
```

### Task 3: O domínio do real vs. planejado e do payback real

**Files:**
- Create: os arquivos de `ventures/domain` da Task 3 no mapa.
- Test: `ventures/domain/ActualVsPlannedTest.java`, `ventures/domain/RealPaybackTest.java`.

**Interfaces:**
- Consumes: `VentureFigures`, `VentureStatus` (9.1); `VentureMonth` (Task 2).
- Produces: `ActualVsPlanned.compute(...)`, `ActualVsPlannedResult(List<MonthRow> months,
  Totals totals, RealPayback real)`, `MonthRow`, `Comparison`, `record RealPayback(RealPaybackState
  state, @Nullable BigDecimal investment, @Nullable Integer months, @Nullable BigDecimal missing)`,
  `enum RealPaybackState { PAID_BACK, NOT_YET, NEVER_NEGATIVE, NO_DATA }` (D3).

Planejado dos exemplos: o `prdExample` da 9.1 (faturamento 1.350,00, custo 810,00, lucro 540,00).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Entrada | Esperado |
|---|---|---|
| `paidBackInThirdMonth` | início 2026-09; set. −800 + 300 (despesa 800, receita 300), out. +300, nov. +300 | acumulado −500, −200, +100 → `PAID_BACK`, 3 meses, investimento `500.00` |
| `notYet` | set. −800, out. +300 | `NOT_YET`, faltam `500.00`, investimento `800.00` |
| `neverNegative` | só receitas | `NEVER_NEGATIVE`, investimento `null` |
| `noData` | nenhum mês | `NO_DATA`; meses do planejado com real 0 |
| `lowestPointIsTheInvestment` | resultados −300, −200, +200, +900 | acumulado −300, −500, −300, +600 → investimento `500.00` (o ponto mais negativo), `PAID_BACK` em 4 meses |
| `negativeAgainAfterPaidBackStaysPaid` | −100, +200, −300 | `PAID_BACK` em 2; investimento `200.00` |
| `emptyMonthsCountInPayback` | set. −100, nov. +200 (out. vazio) | `PAID_BACK` em 3 meses |
| `purchasesBeforeStart` | `startedOn` 2026-10-05; compras em set. | real começa em set.; planejado só a partir de out. (set. com previstos `null`) |
| `planningHasNoPlanned` | status `PLANNING`, lançamentos marcados | linhas com real e previstos `null` |
| `closedStopsAtClosedMonth` | `closedOn` 2026-10-20; lançamento em nov. | período até out.; nov. fora do real e do payback |
| `reopenedRunsToCurrentMonth` | `PAUSED` depois de reabrir, `closedOn` nulo | período até o mês atual |
| `startedOnCorrected` | o mesmo real com `startedOn` em out. e depois em set. | os previstos mudam de mês; o real e o payback não mudam |
| `lastTwelveMonthsNewestFirst` | 15 meses de período | `months` com 12 linhas, a mais recente primeiro; `totals` somando os 15 |
| `comparisonIsNeutralEnum` | resultado real 400 contra previsto 540 | `BELOW`; 540 → `EQUAL`; 600 → `ABOVE` |
| `futureTransactionsOutsideTheEnd` | lançamento confirmado com data no mês seguinte | fora |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*ventures.domain*'`.
  Expected: FAIL.
- [ ] **Step 3: Implementar** D3.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir a cobertura.** `./gradlew build` → BUILD SUCCESSFUL, 90% em `*.domain*`.
- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(ventures): compare actual and planned figures and find the real payback"
```

### Task 4: A rota do real vs. planejado

**Files:**
- Create: `ventures/internal/{ActualVsPlannedController,ActualVsPlannedView}.java`.
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `ventures/ActualVsPlannedIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `VentureRepository.find`, `VentureService.figuresOf` (9.1); `LedgerFigures.ventureMonths`
  (Task 2); `ActualVsPlanned.compute` (Task 3).
- Produces: `GET /v1/workspaces/{ws}/ventures/{id}/actual-vs-planned` → `ActualVsPlannedView`
  (D4).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `endToEndWithTransactions` | projeto `food-sweets` ativo, três lançamentos marcados → os meses, totais e payback real em JSON, dinheiro como `{amount, currency}` |
| `currentMonthUsesFamilyTimezone` | relógio no último dia do mês às 23h em São Paulo (já dia 1 em UTC) → o mês atual é o de São Paulo |
| `otherFamilyIs404` | projeto de outra família → 404 `venture.not-found` |
| `moduleOffIs403` | módulo desligado → 403 `module.disabled` |
| `RoleMatrixIT` | Dono, Adulto e Leitor 200; Jovem 403 |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*ActualVsPlannedIT' --tests
  '*RoleMatrixIT'`. Expected: FAIL.
- [ ] **Step 3: Implementar** a rota.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Regerar o contrato** como na Task 1, Step 5.
- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(ventures): add the actual versus planned route"
```

### Task 5: A conquista "Primeiro projeto de renda extra"

**Files:**
- Create: `ventures/VentureActivated.java`.
- Modify: `ventures/internal/VentureService.java` (publica na ativação), `ventures/VenturesExport.java`
  e `ventures/internal/JdbcVenturesExport.java` (`anyStartedBy`), os arquivos de `gamification`
  onde as chaves, a avaliação e os textos vivem (conferidos no pré-requisito),
  `gamification/package-info.java`, os textos do web da tela "Conquistas".
- Test: `gamification/FirstVentureIT.java`, os testes de conquista do 8.3 que listam as chaves.

**Interfaces:**
- Consumes: a API de conquistas do 8.3 (nomes conferidos no pré-requisito).
- Produces: `record VentureActivated(UUID workspaceId, UUID ventureId, @Nullable UUID
  createdByMembershipId)`; `VenturesExport.anyStartedBy(UUID ws, UUID membershipId): boolean`; a
  chave `FIRST_VENTURE` (D5).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `activatingAwardsTheCreator` | gamificação ligada, Adulto cria e ativa → `FIRST_VENTURE` para quem criou, `silent = false` |
| `someoneElseActivates` | Dono ativa o projeto que o Adulto criou → a conquista vai para o Adulto |
| `reopeningDoesNotAward` | `CLOSED → PAUSED → ACTIVE` sem `PLANNING → ACTIVE` antes → nada novo |
| `gamificationOffAwardsNothing` | módulo de gamificação desligado → nada |
| `turningGamificationOnIsSilent` | projeto já ativo, ligar a gamificação → `FIRST_VENTURE` com `silent = true` |
| `creatorWhoLeftGetsNothing` | quem criou saiu (`created_by` nulo) → ninguém ganha |
| `onlyOnce` | ativar um segundo projeto → nenhuma linha nova |
| `listShowsWhatLeadsToIt` | sem a conquista, a lista mostra "Começar a vender num projeto de renda extra" |
| `ApplicationModulesTest` | passa; `ventures` não depende de `gamification` |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*FirstVentureIT' --tests
  '*Achievement*' --tests '*ApplicationModulesTest'`. Expected: FAIL.
- [ ] **Step 3: Implementar** D5 no padrão que o 8.3 usou para `FIRST_GOAL`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `./gradlew build` e `corepack pnpm --dir web check` → passam.
- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(gamification): award the first extra income project"
```

### Task 6: Exportação v7 e o modelo de dados

**Files:**
- Modify: `ledger/ExportedTransaction.java`, `ledger/internal/LedgerJdbcExport.java`,
  `privacy/internal/ExportService.java`, `docs/data-model.md`.
- Test: `privacy/VentureExportIT.java` (o da 9.1), os testes de exportação que já existem.

**Interfaces:**
- Consumes: `ventureId` (Task 1), `VenturesExport.ventures` (9.1).
- Produces: o formato v7 (D6).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `VentureExportIT.transactionsCarryVenture` | JSON com `formatVersion` 7 e `ventureId` nos lançamentos; `lancamentos.csv` com a coluna `projeto` preenchida com o nome do projeto e vazia sem marca |
| `VentureExportIT.youthPersonExportHasNoVenture` | a exportação por pessoa de um Jovem não traz `ventureId` nem a coluna preenchida |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*Export*'`. Expected: FAIL.
- [ ] **Step 3: Implementar** D6 e registrar a V31 no `data-model.md`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add api docs/data-model.md
git commit -m "feat(privacy): export the venture of each transaction"
```

### Task 7: Web: o campo, o filtro e o detalhe

**Files:**
- Create: `features/transactions/VenturePicker.tsx`.
- Modify: `features/transactions/{TransactionForm,EditTransaction,TransactionFilters,TransactionDetailSheet,TransactionsPage}.tsx`,
  `mocks/handlers.ts`, `mocks/data.ts`.
- Test: `features/transactions/{VenturePicker,TransactionForm,EditTransaction,TransactionFilters}.test.tsx`,
  `shared/offline/sync.test.ts`.

**Interfaces:**
- Consumes: `useVentures(ws)` e `useModuleEnabled('ventures')` (9.1); `ventureId` e `clearVenture`
  no `schema.ts` (Task 1).
- Produces: `<VenturePicker value={id | null} onChange={...} keep={id | null} />` (mostra os
  projetos abertos e, se `keep` for um projeto encerrado, ele também).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `TransactionForm.test.tsx › showsTheFieldOnIncomeAndExpense` | módulo ligado, Adulto, um projeto aberto → "Projeto (opcional)" em receita e despesa, não em transferência |
| `TransactionForm.test.tsx › hiddenWithoutOpenVentures` | só projetos encerrados → sem o campo |
| `TransactionForm.test.tsx › youthNeverSeesTheField` | Jovem → sem o campo |
| `TransactionForm.test.tsx › moduleOffHidesTheField` | módulo desligado → sem o campo |
| `TransactionForm.test.tsx › worksOffline` | `useOnline` falso com a lista no cache → o campo aparece e o pedido vai para a fila com `ventureId` |
| `EditTransaction.test.tsx › untouchedVentureSendsNothing` | editar só o valor de um marcado → pedido sem `ventureId` nem `clearVenture` |
| `EditTransaction.test.tsx › choosingNoneSendsClear` | "Nenhum" num marcado → `clearVenture: true` |
| `EditTransaction.test.tsx › switchingToTransferClears` | marcado vira transferência → `clearVenture: true` e o campo some |
| `VenturePicker.test.tsx › keepsAClosedVenture` | `keep` encerrado → aparece junto dos abertos |
| `TransactionFilters.test.tsx › filtersByVenture` | escolher um projeto → `?projeto=<id>` e a chamada com `ventureId` |
| `sync.test.ts › ventureInvalidGoesToAttention` | o servidor responde 422 `transaction.venture-invalid` → item em "precisa de atenção" com o texto da API |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test --
  src/features/transactions src/shared/offline`. Expected: FAIL.
- [ ] **Step 3: Implementar** D7.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check`, `build` e `check:chunks` → passam (o
  formulário de lançamento está no carregamento inicial: o `VenturePicker` entra pequeno, sem
  importar nada de `features/ventures` além de `shared/api/ventures.ts`).
- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): add the project field and filter to transactions"
```

### Task 8: Web: a tela do real vs. planejado

**Files:**
- Create: `features/ventures/{ActualVsPlannedPage,RealPaybackCards,MonthsTable}.tsx`.
- Modify: `features/ventures/VenturePage.tsx`, `features/ventures/messages.pt-BR.ts`,
  `shared/api/ventures.ts`, `app/routes.tsx`, `mocks/handlers.ts`.
- Test: `features/ventures/{ActualVsPlannedPage,RealPaybackCards,MonthsTable,VenturePage}.test.tsx`.

**Interfaces:**
- Consumes: a rota da Task 4; `figuresText` (9.1).
- Produces: `useActualVsPlanned(ws, id)` com a chave `[...venturesKeys.detail(ws, id),
  'actual-vs-planned']` (o aviso ao vivo `venture` e `transaction` invalidam: `PREFIXES` de
  `transaction` soma `ventures`); a rota `/mais/projetos/:id/real` (D8).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `VenturePage.test.tsx › linkOutsidePlanning` | `ACTIVE` → "Real vs. planejado ›"; `PLANNING` → sem a linha |
| `RealPaybackCards.test.tsx › paidBack` | `PAID_BACK`, 3 → "se pagou em 3 meses", ao lado de "2 meses" planejado |
| `RealPaybackCards.test.tsx › notYet` | `NOT_YET`, `500.00` → "ainda não se pagou: faltam R$ 500,00", sem data |
| `RealPaybackCards.test.tsx › neverNegativeShowsTheTip` | → o texto e a dica de marcar as compras do começo |
| `RealPaybackCards.test.tsx › noData` | → "nenhum lançamento marcado ainda" |
| `MonthsTable.test.tsx › neutralComparison` | `BELOW` → "abaixo do previsto", `ABOVE` → "acima do previsto", sem classe de cor de erro |
| `MonthsTable.test.tsx › tableSemantics` | `<caption>`, `scope="col"` e `scope="row"` |
| `MonthsTable.test.tsx › monthsWithoutPlanned` | previstos `null` → "—" e sem diferença |
| `ActualVsPlannedPage.test.tsx › linksToFilteredTransactions` | "Ver lançamentos do projeto" → `/lancamentos?projeto=<id>` |
| `invalidation.test.ts › transactionInvalidatesVentures` | `{entity: "transaction"}` invalida `venturesKeys.all(ws)` |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test --
  src/features/ventures src/shared/live`. Expected: FAIL.
- [ ] **Step 3: Implementar** D8.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check`, `build` e `check:chunks` → passam.
- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): add the actual versus planned screen"
```

### Task 9: E2E e acessibilidade

**Files:**
- Modify: `web/e2e/ventures.spec.ts`, `web/e2e/a11y.spec.ts`.

**Interfaces:**
- Consumes: tudo das Tasks 1 a 8.
- Produces: nada novo.

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `ventures.spec.ts › markATransactionAndSeeTheActual` | projeto ativo, despesa de R$ 800,00 e receita de R$ 300,00 marcadas → real vs. planejado (no mesmo mês) mostra o mês, o investimento real `R$ 500,00` e "ainda não se pagou: faltam R$ 500,00" |
| `ventures.spec.ts › offlineMarkedTransactionSyncs` | sem rede, lançar uma receita marcada → volta a rede, sincroniza e aparece no real |
| `ventures.spec.ts › filterFromTheProject` | "Ver lançamentos do projeto" → a lista filtrada só com os marcados |
| `a11y.spec.ts` | axe sem violações na tela do real vs. planejado e no formulário com o campo |

- [ ] **Step 2: Rodar.** Run: `corepack pnpm --dir web e2e -- ventures.spec.ts a11y.spec.ts`.
  Expected: PASS.
- [ ] **Step 3: Conferir tudo.** `./gradlew build` em `api/`; `corepack pnpm --dir web check`,
  `build` e `check:chunks` → passam.
- [ ] **Step 4: Commit**

```bash
git add web/e2e
git commit -m "test(e2e): cover marked transactions and the actual versus planned screen"
```
