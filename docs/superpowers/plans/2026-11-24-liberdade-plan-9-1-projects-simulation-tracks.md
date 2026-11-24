# Liberdade · Plano 9.1: Projetos, simulação e trilhas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a primeira entrega da v0.4.
- Com o módulo Projetos ligado (ele começa desligado), Dono e Adulto criam um projeto de renda
  extra em branco ou a partir de um dos cinco modelos das quatro trilhas, com investimento inicial,
  custos fixos do mês, produtos e passos (US-40, US-43).
- A simulação mostra margem, margem %, ponto de equilíbrio, lucro do mês, ROI em 12 meses e payback,
  igual ao exemplo do PRD §7.7, com prévia enquanto a pessoa digita (US-41).
- A trilha de investimentos é só educativa: textos e um simulador de juros compostos, com o aviso
  fixo "Conteúdo educativo. Não é recomendação de investimento." (US-43).

**Architecture:**
- **`ventures` (módulo novo da API):** quatro tabelas por família (V30), um domínio puro que faz as
  contas em `BigDecimal`, um catálogo de modelos em código, rotas de CRUD, status, passos e duas rotas
  de simulação que não gravam nada.
- **Ao redor:** `audit` ouve encerrar e excluir; `privacy` exporta pela interface `VenturesExport`
  (formato v6); `live` avisa `{"entity":"venture"}` a quem vê o módulo, nunca ao Jovem.
- **Front:** a feature nova `features/ventures`, toda sob demanda, com um `register.ts` mínimo e o
  item Projetos em Mais.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, PostgreSQL 18, Flyway, JdbcClient.
  Testes com Testcontainers e `MockMvcTester`.
- **Web:** React 19, react-router v8, TanStack Query, react-intl, MSW 3, Vitest e Playwright com axe.
- **Sem dependências novas.**

**Spec:**
- [Spec do Plano 9](../specs/2026-11-24-liberdade-plan-9-extra-income-tracks-design.md), aprovado
  pelo mantenedor em 2026-11-24. Este plano cobre a 9.1: as seções 6.1 a 6.8, a 6.12 no que toca a
  9.1, e a parte 9.1 das seções 7 e 8. É a autoridade: este plano argumenta a partir dele.
- [PRD](../../prd.md) (US-10, US-40, US-41, US-43, §7.7, CVM), [Roadmap](../../roadmap.md),
  [Arquitetura](../../architecture.md), [Modelo de dados](../../data-model.md) (§5),
  [Wireframes](../../wireframes.md) (Novo Projeto), [ADR-0006](../../adr/0006-money-representation.md).

## Pré-requisito de execução

**Este plano só é executado com o 8.2 e o 8.3 já na `main`** (spec, seção 3). O Flyway roda sem
`outOfOrder`: a V30 não pode entrar antes da V19 (8.2) e da V20 (8.3).

Antes da Task 1, o implementador **confere este plano contra o código da `main`** e anota no relatório
o que mudou:
- a última migração é a V20 (ou outra abaixo de V30) e nenhuma usa V30 a V39;
- a lista vigente do `CHECK` `audit_events_target_type_check` (a V30 repete a lista e soma `'VENTURE'`);
- o `FORMAT_VERSION` do `ExportService` (o 8.3 deixa 5; a 9.1 sobe para 6);
- a assinatura de `LiveRelay.send` e as dependências declaradas de `audit`, `privacy` e `live`;
- o tamanho do carregamento inicial (`check:chunks`) depois do 8.3.

Se algo não bater, o implementador segue o código, ajusta o número ou a lista e anota. Se a mudança
mexer numa decisão do spec, para e pergunta ao mantenedor.

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits (`feat(ventures): ...`, `feat(web): ...`,
  `test(e2e): ...`, `docs: ...`), com o assunto em minúsculas. Cada commit termina com a linha
  `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **Migrações:** só V30 nesta entrega (V31 é da 9.2; V32 a V39 ficam livres).
- **Cálculo exato (ADR-0006):** `BigDecimal` sem arredondar no meio; divisões com
  `new MathContext(20, RoundingMode.HALF_EVEN)`. Arredonda só na resposta: dinheiro com 2 casas
  `HALF_EVEN`, percentual com 1 casa `HALF_EVEN`, ponto de equilíbrio em unidades `CEILING`.
- **Valores:** dinheiro de entrada até `999999999.99` (o mesmo `MAX_AMOUNT` de `GoalService`), com no
  máximo 2 casas; vendas previstas de 0 a 1.000.000 por mês.
- **Limites:** até 20 itens de custo por tipo, 10 produtos e 20 passos por projeto. Nomes de projeto,
  item e produto com 1 a 40 caracteres; passo com 1 a 80.
- **Papéis (US-10):** Dono e Adulto escrevem; Leitor só lê; **o Jovem recebe 403 em toda rota do
  módulo** e não vê o item em Mais.
- **Módulo:** `ventures` começa desligado. Desligado, as rotas respondem 403 `module.disabled` e o
  item some de Mais. Desligar não apaga nada.
- **Limite do plano:** no Grátis, com a cobrança ligada, só 1 projeto fora de `CLOSED`. Texto exato:
  "No plano Grátis, a família tem 1 projeto ativo. Encerre um, ou veja o plano Família em Mais >
  Plano."
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR.
- **Logs:** nunca um valor, um nome de projeto, de item ou de produto.
- **Fronteiras de módulo:** `ventures` declara `allowedDependencies = {"workspace", "ledger",
  "billing", "shared"}`; o núcleo nunca depende de `ventures`; `audit`, `privacy` e `live` somam
  `"ventures"`; o `ApplicationModulesTest` passa.
- **Contrato:** rota nova entra no `api/openapi.json`, no `web/src/shared/api/schema.ts` e no
  `RoleMatrixIT`, no mesmo commit.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `*.domain*` (o cálculo fica em `ventures/domain`).
- **Números na tela:** vírgula decimal, valores chegam como texto decimal e nunca passam por
  `Number`; sinal de menos U+2212.
- **CVM:** "Conteúdo educativo. Não é recomendação de investimento." fixo no topo da tela de textos e
  do simulador, pelo `Disclaimer` que já existe. Nenhum nome de banco, corretora, gestora, fundo ou
  título; nenhuma ordem por "melhor".
- **Notas fixas:** sob a simulação do projeto, "Com os números que você digitou. Não é promessa de
  resultado."; sob o simulador, "Simulação com taxa constante, sem imposto de renda nem taxas. Não é
  promessa de rendimento."
- **Ética:** ROI negativo e "não se paga" em texto neutro, sem vermelho nem ícone de alarme; nada de
  "invista mais", "falta pouco" ou conquista por faturar.
- **Offline:** criar, editar e a prévia precisam de rede; sem rede, salvar fica desabilitado e a
  prévia diz "conecte-se para ver as contas".
- **Desempenho:** o carregamento inicial segue até 200 KB (`check:chunks`). Tudo da 9.1 no web carrega
  sob demanda; o `register.ts` não importa nada além de `@/shared/modules`.
- **Acessibilidade:** WCAG 2.2 AA; axe no Playwright cobre a lista, as trilhas, o formulário, a tela
  do projeto, os textos e o simulador. Campos com `label`, erro com `aria-describedby`, foco
  gerenciado nas folhas.
- **Custo:** nenhum serviço pago e nenhuma chamada externa.
- **Versões:** nenhuma biblioteca nova nem versão nova. Nenhum workflow novo do GitHub Actions.

### Pré-requisitos locais

Os mesmos da 8.1: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o compose de
e2e.

## Entrega

- **PR 1 (docs):** o spec, este plano, o plano 9.2 e o README dos planos. Entra antes do código.
- **PR 2 (código):** branch `feat/plan-9-1-ventures`, cortada da `main` depois do 8.3 e do PR 1. Um
  commit por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o staging recebe o deploy pelo pipeline. O mantenedor confere o critério de
  saída da 9.1 (spec, seção 8): cria um projeto de cada modelo, confere o exemplo do PRD na tela, vê
  o aviso da CVM nas duas telas de investimentos e, se a cobrança estiver ligada no staging, testa o
  limite do Grátis. Antes do go-live da v0.4, o mantenedor confere de novo as normas da CVM.
- **Depois disso:** o plano 9.2 (real vs. planejado).

### Itens herdados

| Item | Onde |
|---|---|
| Itens abertos do 8.2 e do 8.3 | Conferidos no pré-requisito; ficam onde os planos deles disseram |
| Conquista "Primeiro projeto de renda extra" (adiada pelo Plano 8) | Plano 9.2, Task 5 |

## Decisões deste plano

A seção 11 do spec deixa estes pontos ao construtor, e o plano decide:

**D1. Módulo e pacotes.** Pacote `br.com.liberdadefamilia.ventures`.
- Raiz (pública): `VentureChanged(UUID workspaceId, UUID ventureId)`,
  `VentureClosed(UUID workspaceId, UUID ventureId, UUID actorUserId)`,
  `VentureDeleted(UUID workspaceId, UUID ventureId, UUID actorUserId)`, `VenturesExport` e os
  records `ExportedVenture`, `ExportedVentureItem`, `ExportedVentureProduct`, `ExportedVentureStep`.
- `ventures/domain` (puro, 90%): `VentureType`, `VentureStatus`, `StatusTransition`, `CostLine`,
  `ProductLine`, `VentureFigures`, `ProductFigures`, `BreakEven`, `BreakEvenState`, `PaybackState`,
  `RoiState`, `VentureCalculator`, `MonthlyRoot`, `InvestmentScenario`, `InvestmentOutcome`,
  `InvestmentSimulator`.
- `ventures/internal`: catálogo, repositório, serviços, rotas, exceções e exportação.

**D2. Domínio do cálculo** (`VentureCalculator.calculate(List<CostLine> initial, List<CostLine>
fixed, List<ProductLine> products): VentureFigures`).
- `record CostLine(BigDecimal amount)`; `record ProductLine(BigDecimal unitVariableCost, BigDecimal
  unitPrice, int plannedUnitsMonth)`.
- `VentureFigures` (valores já arredondados para a resposta): `initialInvestment`, `fixedMonthly`,
  `plannedRevenue`, `plannedCost` (Σ venda prevista × custo unitário + fixos; a 9.2 usa),
  `totalMargin`, `monthlyProfit`, `@Nullable roi12mPercent` com `RoiState { OK, NO_INVESTMENT }`,
  `@Nullable Integer paybackMonths` com `PaybackState { MONTHS, NEVER }`, `BreakEven breakEven` e
  `List<ProductFigures> products` (na ordem de entrada).
- `ProductFigures(BigDecimal margin, BigDecimal marginPercent, boolean priceCoversCost, @Nullable
  Long breakEvenUnits)`.
- `BreakEven(BreakEvenState state, @Nullable BigDecimal revenue, @Nullable BigDecimal
  weightedMarginPercent)`, `BreakEvenState { OK, NO_PRODUCTS, NEEDS_PLANNED_UNITS, NO_BREAK_EVEN }`.
- Ordem das regras do ponto de equilíbrio (os casos de borda do spec, seção 6.3, decididos assim):
  1. sem produto → `NO_PRODUCTS`;
  2. **um produto:** margem ≤ 0 → `NO_BREAK_EVEN`; senão unidades = ⌈fixos ÷ margem⌉, R$ =
     unidades × preço (fixos 0 → 0 unidades e R$ 0,00); não depende da venda prevista;
  3. **vários produtos:** venda prevista 0 em todos → `NEEDS_PLANNED_UNITS`; margem total ≤ 0 →
     `NO_BREAK_EVEN`; senão R$ = fixos × faturamento ÷ margem total (uma divisão só, o mesmo que fixos
     ÷ margem % ponderada) e unidades de cada produto = ⌈venda prevista × fixos ÷ margem total⌉ (a
     parte do mix, também para o produto de margem negativa; ele leva `priceCoversCost = false`).
- Payback: lucro do mês > 0 → `max(1, ⌈investimento ÷ lucro⌉)`, `MONTHS`; senão `NEVER`.
  ROI: investimento 0 → `NO_INVESTMENT`; senão `(12 × lucro − investimento) ÷ investimento × 100`.
- Margem % do produto = margem ÷ preço × 100 (o preço é sempre > 0).

**D3. Simulador de investimento** (`InvestmentSimulator.simulate(BigDecimal initial, BigDecimal
monthly, int years, List<InvestmentScenario> scenarios): List<InvestmentOutcome>`).
- `MonthlyRoot.of(BigDecimal annualPercent): BigDecimal` — `(1 + a/100)^(1/12) − 1` por Newton em
  `x¹² = 1 + a/100`, `MathContext(20, HALF_EVEN)`, partindo de `1 + a/1200`, até a diferença entre
  duas voltas ser menor que `1E-19` (no máximo 100 voltas). Taxa 0 → 0 exato.
- Saldo: começa no valor inicial; a cada mês, `saldo × (1 + r) + aporte`, em `MathContext(20)`.
  `InvestmentOutcome(String name, BigDecimal finalBalance, BigDecimal totalContributed, BigDecimal
  earnings)`, com 2 casas `HALF_EVEN`; `totalContributed = inicial + aporte × meses` e `earnings =
  final − totalContributed`.
- Entradas: inicial e aporte de 0 a `MAX_AMOUNT`; prazo de 1 a 50 anos; 1 a 3 cenários; nome do
  cenário com 1 a 30 caracteres; taxa ao ano de 0 a 100, até 2 casas. Fora disso, 422
  `investment-simulation.invalid`.

**D4. Catálogo de modelos** (`ventures/internal/TemplateCatalog`, texto PT-BR em código).
- `record Track(String key, String name, String description, boolean educational, List<Template>
  templates)` e `record Template(String key, String name, VentureType type, List<TemplateItem>
  initial, List<TemplateItem> fixed, List<TemplateProduct> products, List<String> steps)`.
- Trilhas, na ordem: `food` (Culinária: `food-sweets`), `digital` (Produtos digitais:
  `digital-ebook`), `content` (Conteúdo: `content-channel`), `resale-services` (Revenda e serviços:
  `resale-goods` e `service-hours`), `investments` (Investimentos (conteúdo educativo),
  `educational = true`, sem modelos).
- **`food-sweets` (o exemplo do PRD), valores exatos:**

| Tipo | Nome | Valor |
|---|---|---|
| INITIAL | Panelas e utensílios | 350,00 |
| INITIAL | Batedeira | 300,00 |
| INITIAL | Embalagens e forminhas iniciais | 150,00 |
| FIXED_MONTHLY | Gás | 90,00 |
| FIXED_MONTHLY | Energia da cozinha | 60,00 |
| FIXED_MONTHLY | Transporte das entregas | 70,00 |
| FIXED_MONTHLY | Divulgação | 50,00 |
| Produto | Brigadeiro gourmet | custo 1,80 · preço 4,50 · 300 por mês |

- Passos do `food-sweets`: "Confira as regras da sua cidade para vender comida feita em casa",
  "Teste a receita e anote o custo de cada unidade", "Defina o preço e a embalagem", "Faça uma
  primeira leva para amigos e família", "Divulgue para vizinhos e grupos do bairro", "Veja se o MEI
  faz sentido para você".
- Os outros quatro modelos: valores e 5 a 7 passos escritos pelo implementador, dentro do spec
  (seção 6.6): plausíveis, prático e neutro, sem regra legal, valor de imposto ou prazo, e cada um
  com lucro do mês positivo e payback ≤ 12 meses (teste).
- Projeto em branco: os passos "Anote os custos do começo e do mês", "Defina o preço de cada
  produto" e "Faça a primeira venda".

**D5. Criar e editar.**
- `POST /ventures` com `CreateVentureRequest(@Nullable String templateKey, String name,
  @Nullable VentureType type)`. Com `templateKey`, o tipo vem do modelo (o `type` do pedido é
  ignorado) e itens, produtos e passos são copiados com `is_example = true`; o nome vem do pedido
  (a tela sugere o nome do modelo). Sem modelo, `type` é obrigatório e o projeto nasce com os três
  passos genéricos. `templateKey` desconhecido → 422 `venture.template-unknown`.
- **Família com moeda diferente de BRL**, com modelo: só o tipo, o `template_key` e os passos; nenhum
  item e nenhum produto (o `CHECK` pede valor > 0 e os valores do catálogo são em reais).
- `PUT /ventures/{id}` com `UpdateVentureRequest(long version, String name, VentureType type,
  @Nullable LocalDate startedOn, List<CostItemInput> initialItems, List<CostItemInput> fixedItems,
  List<ProductInput> products)`; `CostItemInput(@Nullable UUID id, String name, BigDecimal amount)`;
  `ProductInput(@Nullable UUID id, String name, BigDecimal unitVariableCost, BigDecimal unitPrice,
  int plannedUnitsMonth)`. Troca tudo de uma vez: some o que não veio, a posição é a ordem da lista.
  Um item com `id` conhecido mantém `is_example` só se nome e valores forem iguais aos guardados;
  item novo ou `id` desconhecido nasce `false`.
- `startedOn`: em `PLANNING` precisa vir nulo; fora de `PLANNING` é obrigatório, não pode ser depois
  de hoje (fuso da família) nem depois de `closed_on`. Senão 422 `venture.started-on-invalid`.
- `version` diferente → 409 `venture.version-conflict` ("O projeto foi alterado por outra pessoa.
  Recarregue e tente de novo."). Projeto de outra família ou inexistente → 404 `venture.not-found`.
- Validação de campos (vazio, tamanho, valor, limite de itens) → 422 `venture.invalid` com o
  `detail` do campo, como `GoalRejectedException`.

**D6. Status** (`POST /ventures/{id}/status` com `ChangeStatusRequest(long version, VentureStatus
status, @Nullable LocalDate on)`).
- `StatusTransition.allowed(VentureStatus from, VentureStatus to): boolean` segue a tabela do spec
  (seção 6.5). Fora dela → 422 `venture.status-invalid`.
- `on` (padrão: hoje no fuso da família; nunca depois de hoje): `PLANNING → ACTIVE` grava
  `started_on`; `→ CLOSED` grava `closed_on` (de `PLANNING`, também `started_on = closed_on`);
  `closed_on` antes de `started_on` → 422 `venture.started-on-invalid`. `CLOSED → PAUSED` limpa
  `closed_on` e passa pelo limite.
- Fechar publica `VentureClosed`; toda mudança publica `VentureChanged`.

**D7. Limite do plano.** `FREE_ACTIVE_VENTURES = 1`. Criar e reabrir travam a família (o mesmo
`lockFamily` das metas) e contam os projetos com `status <> 'CLOSED'`.
`VentureLimitReachedException`: 403 `venture.limit-reached` com o texto exato das Global
Constraints. `VenturesView(List<VentureSummary> ventures, boolean limitReached)`, com
`limitReached` sempre `false` para o Leitor.

**D8. Passos.** `POST /ventures/{id}/steps` (`{title}`, 201, entra no fim), `PATCH
/ventures/{id}/steps/{stepId}` (`{title?, done?, position?}`; `done: true` grava `done_at = now`,
`false` limpa; `position` move o passo e renumera os outros) e `DELETE
/ventures/{id}/steps/{stepId}` (204). Sem `version`: a última escrita vence. 21º passo → 422
`venture.too-many-steps`. Toda mudança publica `VentureChanged`.

**D9. Respostas.**
- Dinheiro como o `Money` que a API já usa (`{amount, currency}`); percentuais como texto decimal com
  1 casa (`"60.0"`); `null` quando o estado diz que não há número.
- `VentureSummary(id, name, type, status, startedOn, closedOn, monthlyProfit, paybackState,
  paybackMonths, hasExamples, version)` na lista.
- `VentureView(id, name, type, templateKey, status, startedOn, closedOn, currency, initialItems,
  fixedItems, products, steps, figures, version)`; cada item e produto leva `id` e `isExample`;
  `steps` leva `id`, `title`, `position`, `doneAt`.
- `POST /ventures/simulation` recebe `SimulationRequest(List<SimulationCost> initialItems,
  List<SimulationCost> fixedItems, List<SimulationProduct> products)` (sem nome) e devolve
  `VentureFigures`. Mesma validação de valores do `PUT` e dos limites de itens; listas vazias valem.
- `GET /ventures/templates` devolve as trilhas com os modelos completos (para a tela mostrar o
  resumo do exemplo antes de criar).

**D10. Aviso ao vivo.** `LiveRelay.send` troca `boolean needsPlanning` por `@Nullable ModuleKey
needs` (as chamadas de hoje passam `ModuleKey.PLANNING` ou `null`). `VentureChanged`,
`VentureClosed` e `VentureDeleted` mandam `{"entity":"venture","id":...}` para `NoYouth`, só com
`VENTURES` ligado. No web, `PREFIXES` ganha `venture: [ventures]` e `modules` soma `ventures`.

**D11. Exportação v6.** `VenturesExport.ventures(ws)` e `VenturesExport.venturesCreatedBy(ws,
membershipId)`, com itens, produtos e passos dentro de cada `ExportedVenture`. No JSON da família,
`"ventures": [...]`; CSVs `projetos.csv` (`nome, tipo, situacao, inicio, encerramento, moeda`),
`projetos_itens.csv` (`projeto, tipo, nome, valor, moeda`, valor na coluna de dinheiro),
`projetos_produtos.csv` (`projeto, nome, custo_unitario, preco, venda_prevista_mes, moeda`) e
`projetos_passos.csv` (`projeto, posicao, passo, feito_em`). A exportação por pessoa leva os projetos
que ela criou. `FORMAT_VERSION` sobe uma versão (para 6, conferido no pré-requisito).

**D12. Web.**
- `features/ventures/register.ts`: só `registerModule({ key: 'ventures', available: true })`,
  importado em `app/routes.tsx`. As telas registram `venturesMessages` (`messages.pt-BR.ts`).
- Rotas sob demanda, dentro de `<ModuleRoute module="ventures">` e de `NoYouthRoute` (o Jovem que
  chega pelo endereço vai para `/mais`): `/mais/projetos`, `/mais/projetos/comecar`,
  `/mais/projetos/investimentos`, `/mais/projetos/investimentos/simulador`, `/mais/projetos/:id` e
  `/mais/projetos/:id/editar`.
- `shared/api/ventures.ts`: `venturesKeys` sob `queryKeys.workspace(ws)` e os hooks `useVentures`,
  `useVenture`, `useVentureTemplates`, `useVentureSimulation` e as mutações. A lista usa
  `meta: { persist: true }` (a 9.2 precisa dela offline); simulação, `meta: { persist: false }`.
- Prévia: `useVentureSimulation(ws, draft)` espera 400 ms sem mudança no rascunho e usa a chave
  `[...venturesKeys.all(ws), 'simulation', draftSemNomes]` com `placeholderData: keepPreviousData`
  (o último pedido vence e o valor anterior fica na tela). Linhas cujo número não lê ficam de fora
  do pedido. Sem rede, não chama e mostra "conecte-se para ver as contas".
- Valores digitados com `parseTypedAmount` e `centsToApi` de `@/shared/money`, como o formulário de
  metas; vendas previstas como inteiro.

**D13. Textos de investimento.** `features/ventures/investments/texts.ts` exporta
`INVESTMENT_TOPICS` (poupança, renda fixa, Tesouro Direto, fundos, ações, nessa ordem) com
`whatItIs`, `risk`, `liquidity`, `guarantee`, `tax` e `REVIEWED_ON`. O limite do FGC é conferido
pelo implementador na página oficial do FGC na hora de escrever e citado com a data. O teste de
palavras não guarda lista de instituições no repositório: toda palavra com maiúscula que não abre
frase precisa estar numa lista curta permitida (`FGC`, `CDB`, `LCI`, `LCA`, `CDI`, `Selic`, `IPCA`,
`Tesouro`, `Direto`, `Banco`, `Central`, `CVM`, `B3`, `Imposto`, `Renda`, `IR`), e as expressões
`recomend`, `melhor`, `ideal para você`, `indicamos`, `garantido` (fora de "garantia do FGC")
reprovam.

**D14. Docs.** `docs/data-model.md` §5 (tipos novos, `template_key`, `currency`, `is_example`,
`position`, `venture_steps`, `VENTURE` na auditoria), `docs/architecture.md` (o módulo `ventures` e
quem depende de quem) e o roadmap, se algo mudou.

**D15. Matriz de papéis.** Uma linha no `RoleMatrixIT` por rota: GETs → Dono, Adulto e Leitor 200,
Jovem 403; escritas → Dono e Adulto 2xx, Leitor e Jovem 403; as duas simulações → Dono, Adulto e
Leitor 200 (não gravam), Jovem 403. O preparo liga o módulo e cria um projeto.

## Review Focus

1. **Família no Grátis com um projeto em `PLANNING` tenta abrir outro modelo "só para ver":** recebe o
   texto do limite, não um erro genérico, e a tela leva a Mais > Plano. Testes:
   `VenturesIT.freeFamilySecondVentureIsLimited` (Task 4) e `VenturesListPage.test.tsx ›
   limitReachedShowsThePlanMessage` (Task 8).
2. **Valor com vírgula, campo vazio ou número enorme no formulário enquanto a prévia roda:** a linha
   inválida fica fora da prévia, a prévia não quebra e salvar mostra o erro do campo. Testes:
   `useVentureSimulation.test.tsx › skipsRowsThatDoNotParse` e `VentureForm.test.tsx ›
   amountAboveMaxShowsFieldError` (Task 9).
3. **Edição concorrente de dois adultos:** o segundo `PUT` recebe 409 e a tela oferece recarregar,
   sem perder o que a pessoa digitou. Testes: `VenturesIT.staleVersionIs409` (Task 4) e
   `VentureForm.test.tsx › conflictKeepsTheDraft` (Task 9).
4. **Jovem que recebe um link de projeto de um adulto:** a rota não abre e a API responde 403, sem
   nome nem valor. Testes: `RoleMatrixIT` (Task 4) e `NoYouthRoute.test.tsx ›
   youthGoesBackToMore` (Task 8).
5. **Simulador com 50 anos e taxa alta:** o resultado não estoura nem demora; números grandes
   aparecem com separador de milhar. Testes: `InvestmentSimulatorTest.fiftyYearsAtTenPercent`
   (Task 2) e `InvestmentSimulatorPage.test.tsx › formatsMillions` (Task 11).

## Mapa de arquivos

Caminhos da API relativos a `api/src/main/java/br/com/liberdadefamilia/` (testes em
`api/src/test/java/br/com/liberdadefamilia/`); do web, relativos a `web/src/`.

| Arquivo | Task | Papel |
|---|---|---|
| `api/src/main/resources/db/migration/V30__ventures.sql` | 1 | as quatro tabelas e o `CHECK` da auditoria |
| `ventures/package-info.java`, `ventures/domain/{VentureType,VentureStatus,StatusTransition}.java` | 1 | módulo, tipos e transições |
| `ventures/domain/{CostLine,ProductLine,VentureFigures,ProductFigures,BreakEven,BreakEvenState,PaybackState,RoiState,VentureCalculator}.java` | 2 | cálculo do projeto |
| `ventures/domain/{MonthlyRoot,InvestmentScenario,InvestmentOutcome,InvestmentSimulator}.java` | 2 | simulador de investimento |
| `ventures/internal/{TemplateCatalog,Track,Template,TemplateItem,TemplateProduct}.java` | 3 | trilhas e modelos |
| `ventures/{VentureChanged,VentureClosed,VentureDeleted}.java` | 4 | eventos públicos |
| `ventures/internal/{VentureController,VentureService,VentureRepository,VentureView,VentureSummary,VenturesView,CreateVentureRequest,UpdateVentureRequest,ChangeStatusRequest,CostItemInput,ProductInput}.java` | 4 | CRUD, status e limite |
| `ventures/internal/{VentureNotFoundException,VentureRejectedException,VentureLimitReachedException}.java` | 4 | erros |
| `audit/internal/AuditListeners.java`, `audit/package-info.java` | 4 | auditoria |
| `ventures/internal/{VentureStepController,VentureStepService,StepView,CreateStepRequest,UpdateStepRequest}.java` | 5 | passos |
| `ventures/internal/{SimulationController,SimulationRequest,InvestmentSimulationRequest,InvestmentSimulationView}.java` | 6 | rotas de simulação |
| `live/internal/LiveRelay.java`, `live/package-info.java` | 7 | aviso ao vivo |
| `ventures/{VenturesExport,ExportedVenture,ExportedVentureItem,ExportedVentureProduct,ExportedVentureStep}.java`, `ventures/internal/JdbcVenturesExport.java`, `privacy/internal/ExportService.java`, `privacy/package-info.java` | 7 | exportação v6 |
| `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java` | 4, 5, 6 | contrato |
| `docs/data-model.md`, `docs/architecture.md` | 7 | docs |
| `features/ventures/{register.ts,messages.pt-BR.ts,VenturesListPage.tsx}`, `app/{routes.tsx,MorePage.tsx,NoYouthRoute.tsx}`, `shared/api/ventures.ts`, `shared/live/invalidation.ts`, `mocks/{handlers.ts,data.ts}` | 8 | entrada, lista e cache |
| `features/ventures/{StartPage.tsx,NewVentureSheet.tsx,VentureForm.tsx,VentureEditPage.tsx,FiguresPanel.tsx,useVentureSimulation.ts,figuresText.ts}` | 9 | trilhas, formulário e prévia |
| `features/ventures/{VenturePage.tsx,HowWeCalculate.tsx,StepsList.tsx,StatusSheet.tsx}` | 10 | tela do projeto e passos |
| `features/ventures/investments/{texts.ts,InvestmentsPage.tsx,InvestmentSimulatorPage.tsx}` | 11 | trilha de investimentos |
| `web/e2e/ventures.spec.ts`, `web/e2e/a11y.spec.ts` | 12 | e2e |

## Tasks

### Task 1: V30, o esqueleto do módulo e o status

**Files:**
- Create: `db/migration/V30__ventures.sql`, `ventures/package-info.java`,
  `ventures/domain/{VentureType,VentureStatus,StatusTransition}.java`.
- Test: `ventures/domain/StatusTransitionTest.java`, `ventures/VenturesSchemaIT.java`,
  `ApplicationModulesTest`.

**Interfaces:**
- Consumes: o pré-requisito conferido (lista do `CHECK` da auditoria depois da V20).
- Produces:
  - `enum VentureType { FOOD, DIGITAL_PRODUCT, CONTENT, RESALE, SERVICE, OTHER }`;
  - `enum VentureStatus { PLANNING, ACTIVE, PAUSED, CLOSED }`;
  - `StatusTransition.allowed(VentureStatus from, VentureStatus to): boolean` (D6).

**Como fica:** a V30 é o DDL da seção 6.2 do spec, sem mudança, mais o `DROP` e `ADD` de
`audit_events_target_type_check` com a lista vigente e `'VENTURE'` no fim. O `package-info.java`
declara as dependências das Global Constraints.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `StatusTransitionTest.followsTheSpecTable` | `PLANNING→ACTIVE`, `ACTIVE→PAUSED`, `PAUSED→ACTIVE`, `{PLANNING,ACTIVE,PAUSED}→CLOSED`, `CLOSED→PAUSED` → `true`; todo o resto (inclusive `X→X`, `CLOSED→ACTIVE`, `PAUSED→PLANNING`) → `false` |
| `VenturesSchemaIT.closedNeedsClosedOn` | `status='CLOSED'` sem `closed_on` → violação de `CHECK` |
| `VenturesSchemaIT.activeNeedsStartedOn` | `status='ACTIVE'` sem `started_on` → violação |
| `VenturesSchemaIT.itemOfAnotherFamilyIsRefused` | `venture_cost_items` com `workspace_id` de outra família → violação da chave composta |
| `VenturesSchemaIT.deletingTheFamilyCascades` | apagar a família apaga projeto, itens, produtos e passos |
| `VenturesSchemaIT.auditAcceptsVenture` | `audit_events` com `target_type='VENTURE'` grava; os tipos antigos seguem aceitos |
| `ApplicationModulesTest` | passa com `ventures` |

- [ ] **Step 2: Rodar e ver falhar.** Run (em `api/`): `./gradlew test --tests '*ventures*'
  --tests '*ApplicationModulesTest'`. Expected: FAIL (tipos e tabelas que não existem).
- [ ] **Step 3: Implementar** a migração, o `package-info.java`, os dois enums e
  `StatusTransition`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(ventures): add the v30 tables, the module and the status rules"
```

### Task 2: O domínio do cálculo e o simulador de investimento

**Files:**
- Create: os arquivos de cálculo e de simulador em `ventures/domain` (mapa de arquivos).
- Test: `ventures/domain/VentureCalculatorTest.java`, `ventures/domain/MonthlyRootTest.java`,
  `ventures/domain/InvestmentSimulatorTest.java`.

**Interfaces:**
- Consumes: nada novo.
- Produces: `VentureCalculator.calculate(...)`, `VentureFigures`, `ProductFigures`, `BreakEven`
  e os enums de estado (D2); `MonthlyRoot.of(...)`, `InvestmentSimulator.simulate(...)`,
  `record InvestmentScenario(String name, BigDecimal annualPercent)` e `InvestmentOutcome` (D3).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Entrada | Esperado |
|---|---|---|
| `prdExample` | inicial 800,00; fixos 270,00; produto 1,80 / 4,50 / 300 | margem `2.70`, margem % `60.0`, equilíbrio `OK` com 100 unidades e R$ `450.00`, faturamento `1350.00`, lucro `540.00`, payback 2 `MONTHS`, ROI `710.0` |
| `breakEvenRoundsUp` | fixos 100,00; produto 1,00 / 4,00 / 0 | 34 unidades (33,33… para cima), R$ `136.00` |
| `singleProductIgnoresPlannedUnits` | o PRD com venda prevista 0 | equilíbrio igual ao `prdExample`; lucro `-270.00`; payback `NEVER` |
| `twoProductsUseTheMix` | fixos 300,00; A 2,00 / 5,00 / 100; B 1,00 / 2,00 / 100 | margem total `400.00`, faturamento `700.00`, % ponderada `57.1`, R$ `525.00`, unidades A 75 e B 75 |
| `negativeMarginProductInMix` | fixos 100,00; A 1,00 / 3,00 / 100; B 5,00 / 4,00 / 50 | B `priceCoversCost=false`, margem `-1.00`; margem total `150.00`; unidades A 67 e B 34 |
| `mixWithoutPlannedUnits` | dois produtos com venda prevista 0 | `NEEDS_PLANNED_UNITS`, sem números |
| `totalMarginNotPositive` | dois produtos que somam margem total ≤ 0 | `NO_BREAK_EVEN`; payback `NEVER` |
| `singleProductPriceBelowCost` | um produto 5,00 / 4,00 | `NO_BREAK_EVEN`; `priceCoversCost=false` |
| `noProducts` | só itens | `NO_PRODUCTS`; lucro = −fixos |
| `zeroFixedCosts` | fixos vazios; produto 1,80 / 4,50 / 300 | equilíbrio 0 unidades, R$ `0.00` |
| `zeroInvestment` | sem itens iniciais, lucro > 0 | ROI `NO_INVESTMENT` (nulo); payback 1 |
| `paybackAtLeastOneMonth` | investimento 100,00, lucro 540,00 | payback 1 |
| `negativeRoiIsReported` | investimento 10.000,00, lucro 100,00 | ROI `-88.0` |
| `roundsHalfEven` | margem % 12,25 e 12,35 exatos | `12.2` e `12.4`; dinheiro x,xx5 → par |
| `MonthlyRootTest.twelvePercent` | 12 | começa com `0.00948879293458297`; `(1 + r)¹²` fica a menos de `1E-18` de `1.12` |
| `MonthlyRootTest.zeroIsExact` | 0 | `0` |
| `InvestmentSimulatorTest.zeroRate` | 1000, 100, 1 ano, 0% | final `2200.00`, aportado `2200.00`, rendimento `0.00` |
| `InvestmentSimulatorTest.oneScenario` | 1000, 100, 1 ano, 12% | final `2384.65`, aportado `2200.00`, rendimento `184.65` |
| `InvestmentSimulatorTest.threeScenarios` | 5000, 200, 5 anos, 6% / 10,5% / 0% | finais `20588.29`, `23735.37`, `17000.00`, na ordem dada |
| `InvestmentSimulatorTest.lumpSumOnly` | 10000, 0, 10 anos, 15% | final `40455.58` |
| `InvestmentSimulatorTest.fiftyYearsAtTenPercent` | 0, 500, 50 anos, 10% | final `7298018.76`, aportado `300000.00`, em menos de 200 ms |

(Os valores esperados do simulador foram calculados à parte com 50 dígitos de precisão; com 20
dígitos dão os mesmos centavos.)

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*ventures.domain*'`.
  Expected: FAIL.
- [ ] **Step 3: Implementar** as classes de D2 e D3. Records imutáveis, listas copiadas com
  `List.copyOf`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir a cobertura.** `./gradlew build` → BUILD SUCCESSFUL, com o JaCoCo de 90% em
  `*.domain*` passando.
- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(ventures): add the venture calculator and the investment simulator"
```

### Task 3: O catálogo de trilhas e modelos

**Files:**
- Create: `ventures/internal/{TemplateCatalog,Track,Template,TemplateItem,TemplateProduct}.java`.
- Test: `ventures/internal/TemplateCatalogTest.java`.

**Interfaces:**
- Consumes: `VentureType`, `VentureCalculator` (Tasks 1 e 2).
- Produces:
  - `TemplateCatalog.tracks(): List<Track>` e `TemplateCatalog.template(String key):
    Optional<Template>`;
  - `TemplateCatalog.BLANK_STEPS: List<String>`;
  - `record TemplateItem(String name, BigDecimal amount)`, `record TemplateProduct(String name,
    BigDecimal unitVariableCost, BigDecimal unitPrice, int plannedUnitsMonth)` (D4).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `tracksInOrder` | chaves `food`, `digital`, `content`, `resale-services`, `investments`; só `investments` é `educational` e sem modelos |
| `fiveTemplates` | `food-sweets`, `digital-ebook`, `content-channel`, `resale-goods`, `service-hours`, com os tipos da tabela do spec (seção 6.6) |
| `foodSweetsIsThePrdExample` | soma dos `initial` = `800.00`, dos `fixed` = `270.00`, um produto 1,80 / 4,50 / 300, e o cálculo dá o `prdExample` da Task 2 |
| `everyTemplatePaysBackWithinAYear` | para cada modelo: lucro do mês > 0 e payback ≤ 12 |
| `everyTemplateHasFiveToSevenSteps` | 5 ≤ passos ≤ 7; cada passo com 1 a 80 caracteres |
| `templatesRespectTheLimits` | nomes com 1 a 40 caracteres, ≤ 20 itens por tipo, ≤ 10 produtos, valores > 0 com até 2 casas |
| `stepsMakeNoLegalClaims` | nenhum passo tem `R$`, `%`, `obrigatório`, `prazo` nem dígitos |
| `blankHasThreeSteps` | `BLANK_STEPS` com os três textos de D4 |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*TemplateCatalogTest'`.
  Expected: FAIL.
- [ ] **Step 3: Implementar** o catálogo com o `food-sweets` de D4 e os outros quatro modelos.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(ventures): add the tracks and the five templates"
```

### Task 4: Criar, ler, editar, mudar status, excluir e o limite

**Files:**
- Create: os eventos públicos, o controller, o serviço, o repositório, views, pedidos e exceções da
  Task 4 no mapa de arquivos.
- Modify: `audit/internal/AuditListeners.java`, `audit/package-info.java` (soma `"ventures"`),
  `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `ventures/VenturesIT.java`, `ventures/VentureStatusIT.java`, `audit/VentureAuditIT.java`,
  `RoleMatrixIT`, `OpenApiDriftIT`.

**Interfaces:**
- Consumes: Tasks 1 a 3; `Plans` e `PlanKey` de `billing`; `WorkspaceMember` (com
  `baseCurrency()` e `timezone()`).
- Produces:
  - rotas `GET/POST /v1/workspaces/{ws}/ventures`, `GET/PUT/DELETE .../ventures/{id}`,
    `POST .../ventures/{id}/status`, `GET .../ventures/templates` (D5, D6, D9);
  - `VentureRepository.find(UUID ws, UUID id): Optional<StoredVenture>`,
    `VentureRepository.lockFamily(UUID ws)`, `VentureRepository.countOpen(UUID ws): int`;
  - `VentureService.figuresOf(StoredVenture): VentureFigures` (a 9.2 reusa);
  - os eventos de D1, publicados dentro da transação.

**Como fica:**
- Todas as rotas com `@RequiresModule(ModuleKey.VENTURES)`; leitura com
  `@WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})`, escrita com `allow = {OWNER, ADULT}`.
- `DELETE` publica `VentureDeleted` e `VentureChanged`; o `AuditListeners` grava
  `venture.closed` e `venture.deleted` com alvo `VENTURE` e `Map.of()`.
- `currency` do projeto e dos itens é a moeda da família na criação; o `PUT` grava itens na moeda do
  projeto.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `VenturesIT.createBlankHasGenericSteps` | `POST {name, type: OTHER}` → 201, `PLANNING`, três passos de D4, sem itens |
| `VenturesIT.createFromFoodSweets` | `POST {templateKey: food-sweets, name}` → itens e produto do D4 com `isExample: true`, seis passos, `figures` do `prdExample` |
| `VenturesIT.unknownTemplateIs422` | `templateKey: x` → 422 `venture.template-unknown` |
| `VenturesIT.nonBrlFamilyGetsNoValues` | família em USD, `food-sweets` → tipo `FOOD`, seis passos, nenhum item nem produto |
| `VenturesIT.editingAnExampleClearsTheBadge` | `PUT` com o preço do produto mudado → só ele vira `isExample: false`; item igual segue `true` |
| `VenturesIT.putReplacesItemsInOrder` | item omitido some; ordem da lista vira `position` |
| `VenturesIT.staleVersionIs409` | `PUT` com `version` antiga → 409 `venture.version-conflict` |
| `VenturesIT.limitsAre422` | 21 itens iniciais, 11 produtos, nome com 41 caracteres, valor `1000000000.00`, 3 casas → 422 `venture.invalid` |
| `VenturesIT.startedOnRules` | em `PLANNING` com `startedOn` → 422; `ACTIVE` sem `startedOn` → 422; amanhã → 422 |
| `VenturesIT.freeFamilySecondVentureIsLimited` | cobrança ligada, Grátis, um projeto `PLANNING` → segundo `POST` 403 `venture.limit-reached` com o texto exato; a lista traz `limitReached: true` |
| `VenturesIT.closedDoesNotCount` | o primeiro encerrado → o segundo `POST` passa |
| `VenturesIT.familyPlanAndBillingOffHaveNoLimit` | Família → três projetos; cobrança desligada → três projetos |
| `VenturesIT.viewerNeverSeesLimitReached` | Leitor na família no limite → `limitReached: false` |
| `VenturesIT.otherFamilyIs404` | `GET`, `PUT`, `DELETE` de projeto de outra família → 404 `venture.not-found` |
| `VenturesIT.deleteRemovesEverything` | `DELETE` → 204; itens, produtos e passos somem |
| `VenturesIT.moduleOffIs403` | módulo desligado → 403 `module.disabled` em `GET` e `POST` |
| `VentureStatusIT.activateRecordsStartedOn` | `PLANNING→ACTIVE` sem `on` → `started_on` = hoje no fuso da família; com data passada → ela |
| `VentureStatusIT.closeFromPlanning` | `PLANNING→CLOSED` → `started_on = closed_on` |
| `VentureStatusIT.closeBeforeStartIs422` | `ACTIVE` desde 10/11, fechar em 05/11 → 422 `venture.started-on-invalid` |
| `VentureStatusIT.reopenClearsClosedOnAndChecksTheLimit` | `CLOSED→PAUSED` limpa `closed_on`; no Grátis com outro aberto → 403 `venture.limit-reached` |
| `VentureStatusIT.invalidTransitionIs422` | `CLOSED→ACTIVE` → 422 `venture.status-invalid` |
| `VentureAuditIT.closeAndDeleteAreAudited` | uma linha `venture.closed` e uma `venture.deleted`, alvo `VENTURE`, sem valores nem nome; criar e editar não gravam |
| `VenturesIT.logsCarryNoNames` | com o log capturado, criar e editar não escrevem o nome do projeto nem valores |
| `RoleMatrixIT` | as linhas de D15 para estas rotas |
| `OpenApiDriftIT` | passa com o `openapi.json` regerado |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*Venture*IT' --tests
  '*RoleMatrixIT'`. Expected: FAIL.
- [ ] **Step 3: Implementar** repositório (JdbcClient), serviço, controller, exceções e o ouvinte de
  auditoria. O serviço segue `GoalService`: valida, trava a família, confere o limite, grava,
  publica.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Regerar o contrato.** `UPDATE_OPENAPI=true ./gradlew test --rerun --tests
  '*OpenApiDriftIT'` em `api/`, depois `corepack pnpm --dir web gen:api`.
- [ ] **Step 6: Conferir.** `./gradlew build` e `corepack pnpm --dir web typecheck` → passam.
- [ ] **Step 7: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(ventures): add the venture routes, the status changes and the free plan limit"
```

### Task 5: Passos

**Files:**
- Create: os arquivos de passos da Task 5 no mapa de arquivos.
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `ventures/VentureStepsIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `VentureRepository.find`, os eventos (Task 4).
- Produces: `POST .../ventures/{id}/steps`, `PATCH` e `DELETE .../ventures/{id}/steps/{stepId}`
  (D8); `StepView(UUID id, String title, int position, @Nullable Instant doneAt)`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `addGoesToTheEnd` | três passos, `POST {title}` → 201, `position` 3 |
| `markAndUnmark` | `PATCH {done: true}` grava `doneAt`; `{done: false}` limpa |
| `moveRenumbers` | passos 0..3, mover o 3 para 0 → ordem nova sem buracos nem repetição |
| `renameValidates` | título vazio ou com 81 caracteres → 422 `venture.invalid` |
| `twentyFirstStepIs422` | com 20 passos → 422 `venture.too-many-steps` |
| `deleteRenumbers` | apagar o do meio → posições 0..n−1 |
| `stepOfAnotherVentureIs404` | `stepId` de outro projeto → 404 `venture.not-found` |
| `stepsPublishVentureChanged` | cada escrita publica `VentureChanged` |
| `RoleMatrixIT` | Dono e Adulto 2xx; Leitor e Jovem 403 |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*VentureStepsIT' --tests
  '*RoleMatrixIT'`. Expected: FAIL.
- [ ] **Step 3: Implementar** as rotas de D8. Mover e apagar renumeram na mesma transação.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Regerar o contrato** como na Task 4, Step 5.
- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(ventures): add the venture steps"
```

### Task 6: As rotas de simulação

**Files:**
- Create: os arquivos da Task 6 no mapa de arquivos.
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `ventures/SimulationIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `VentureCalculator`, `InvestmentSimulator` (Task 2).
- Produces:
  - `POST /v1/workspaces/{ws}/ventures/simulation` (D9) → `VentureFigures`;
  - `POST /v1/workspaces/{ws}/ventures/investment-simulation` com
    `InvestmentSimulationRequest(BigDecimal initialAmount, BigDecimal monthlyContribution, int
    years, List<ScenarioInput> scenarios)` → `InvestmentSimulationView(List<InvestmentOutcome>
    scenarios)`. As duas não gravam nada e não publicam evento.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `simulationMatchesThePrd` | os números do PRD → o `prdExample` em JSON (`"60.0"`, `{amount: "540.00"}`) |
| `simulationAcceptsAnEmptyForm` | listas vazias → 200 com `NO_PRODUCTS`, lucro `0.00` |
| `simulationValidatesLikePut` | valor negativo, 11 produtos → 422 `venture.invalid` |
| `simulationWritesNothing` | contagem de linhas de `ventures` igual antes e depois |
| `investmentThreeScenarios` | o `threeScenarios` da Task 2 em JSON, na ordem |
| `investmentLimits` | 0 anos, 51 anos, 4 cenários, taxa 100,01, nome vazio → 422 `investment-simulation.invalid` |
| `RoleMatrixIT` | Dono, Adulto e Leitor 200; Jovem 403 |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*SimulationIT' --tests
  '*RoleMatrixIT'`. Expected: FAIL.
- [ ] **Step 3: Implementar** o controller. As duas rotas usam `@RequiresModule(VENTURES)` e
  `allow = {OWNER, ADULT, VIEWER}`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Regerar o contrato** como na Task 4, Step 5.
- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(ventures): add the venture and the investment simulations"
```

### Task 7: Aviso ao vivo, exportação v6 e docs

**Files:**
- Modify: `live/internal/LiveRelay.java`, `live/package-info.java`,
  `privacy/internal/ExportService.java`, `privacy/package-info.java`, `docs/data-model.md`,
  `docs/architecture.md`.
- Create: `ventures/{VenturesExport,ExportedVenture,ExportedVentureItem,ExportedVentureProduct,ExportedVentureStep}.java`,
  `ventures/internal/JdbcVenturesExport.java`.
- Test: `live/VentureLiveIT.java`, `privacy/VentureExportIT.java`, os testes de `LiveRelay` e de
  exportação que já existem.

**Interfaces:**
- Consumes: os eventos (Task 4).
- Produces:
  - `VenturesExport.ventures(UUID ws): List<ExportedVenture>` e
    `VenturesExport.venturesCreatedBy(UUID ws, UUID membershipId): List<ExportedVenture>`, por
    criação;
  - `record ExportedVenture(UUID id, String name, String type, String status, @Nullable LocalDate
    startedOn, @Nullable LocalDate closedOn, String currency, @Nullable String templateKey,
    List<ExportedVentureItem> items, List<ExportedVentureProduct> products,
    List<ExportedVentureStep> steps)`;
  - `LiveRelay.send(UUID ws, String entity, @Nullable UUID id, Audience audience, @Nullable
    ModuleKey needs)` (D10).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `VentureLiveIT.adultsGetTheNotice` | `PUT` num projeto → Dono e Leitor recebem `{"entity":"venture","id":...}` depois do commit |
| `VentureLiveIT.youthNeverGetsIt` | o Jovem conectado não recebe nada |
| `VentureLiveIT.moduleOffSendsNothing` | módulo desligado → ninguém recebe |
| `VentureLiveIT.planningNoticesUnchanged` | `GoalChanged` segue indo só com `PLANNING` ligado |
| `VentureExportIT.familyExportHasVentures` | JSON com `formatVersion` 6 e `ventures` (itens, produtos, passos); os quatro CSVs de D11 com cabeçalhos exatos |
| `VentureExportIT.personExportHasOwnVentures` | a exportação por pessoa traz só os projetos que ela criou |
| `VentureExportIT.leaverKeepsVenturesWithNullCreator` | a pessoa sai → `created_by` nulo, projeto segue na família |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*VentureLiveIT' --tests
  '*VentureExportIT' --tests '*LiveRelay*' --tests '*Export*'`. Expected: FAIL.
- [ ] **Step 3: Implementar** D10 e D11. `ExportService` lê só por `VenturesExport`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Atualizar os docs** de D14 (`data-model.md` §5 e `architecture.md`).
- [ ] **Step 6: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 7: Commit**

```bash
git add api docs/data-model.md docs/architecture.md
git commit -m "feat(ventures): send live notices, export the ventures and document the model"
```

### Task 8: Web: registro, item em Mais, rotas, cache e a lista

**Files:**
- Create: `features/ventures/{register.ts,messages.pt-BR.ts,VenturesListPage.tsx}`,
  `app/NoYouthRoute.tsx`, `shared/api/ventures.ts`.
- Modify: `app/routes.tsx`, `app/MorePage.tsx`, `shared/live/invalidation.ts`,
  `mocks/handlers.ts`, `mocks/data.ts`.
- Test: `features/ventures/VenturesListPage.test.tsx`, `app/NoYouthRoute.test.tsx`,
  `app/MorePage.test.tsx`, `shared/api/ventures.test.tsx`, `shared/live/invalidation.test.ts`.

**Interfaces:**
- Consumes: o contrato das Tasks 4 a 6 (`schema.ts`).
- Produces:
  - `venturesKeys.all(ws)`, `venturesKeys.list(ws)`, `venturesKeys.detail(ws, id)`,
    `venturesKeys.templates(ws)`;
  - `useVentures(ws)`, `useVenture(ws, id)`, `useVentureTemplates(ws)`, `useCreateVenture(ws)`,
    `useUpdateVenture(ws, id)`, `useChangeVentureStatus(ws, id)`, `useDeleteVenture(ws, id)`,
    `useStepMutations(ws, id)`;
  - `NoYouthRoute` (sem props, `<Outlet/>` ou `Navigate to="/mais" replace`);
  - MSW: handlers das rotas de `ventures` com um projeto `food-sweets` em `mocks/data.ts`.

**Como fica:**
- Item "Projetos" (`more.ventures`) em Mais com o módulo ligado e a pessoa não sendo Jovem, com a
  classe de link dos outros itens.
- Lista: projetos fora de `CLOSED` primeiro, com status, lucro do mês previsto e payback ("2 meses",
  "não se paga com esses números"); encerrados num grupo recolhido "Encerrados (n)". Botão "Começar
  um projeto" (leva a `/mais/projetos/comecar`), escondido para o Leitor. Com `limitReached`, o botão
  abre o texto do limite com link para Mais > Plano. Lista vazia: "Nenhum projeto ainda."

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MorePage.test.tsx › showsProjectsWhenOn` | módulo ligado, Adulto → link "Projetos"; Jovem → nada; desligado → nada |
| `NoYouthRoute.test.tsx › youthGoesBackToMore` | Jovem → `/mais`; Adulto → filho renderiza |
| `VenturesListPage.test.tsx › openFirstClosedCollapsed` | um `ACTIVE`, um `CLOSED` → o ativo visível, o encerrado dentro de "Encerrados (1)" fechado |
| `VenturesListPage.test.tsx › showsProfitAndPayback` | `R$ 540,00` e "2 meses" do mock |
| `VenturesListPage.test.tsx › limitReachedShowsThePlanMessage` | `limitReached: true` → o texto exato e link para Mais > Plano |
| `VenturesListPage.test.tsx › viewerCannotStart` | Leitor → sem botão "Começar um projeto" |
| `ventures.test.tsx › listIsPersisted` | a consulta da lista tem `meta.persist === true` |
| `invalidation.test.ts › ventureInvalidatesVentures` | `{entity: "venture"}` invalida `venturesKeys.all(ws)`; `modules` também |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test -- src/app
  src/features/ventures src/shared/api/ventures.test.tsx src/shared/live`. Expected: FAIL.
- [ ] **Step 3: Implementar** os arquivos de D12 desta task. `register.ts` com uma linha só.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` e `corepack pnpm --dir web build &&
  corepack pnpm --dir web check:chunks` → passam, sem bytes novos no carregamento inicial além do
  item de Mais.
- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): add the projects entry, routes and list"
```

### Task 9: Web: começar, formulário e prévia

**Files:**
- Create: `features/ventures/{StartPage.tsx,NewVentureSheet.tsx,VentureForm.tsx,VentureEditPage.tsx,FiguresPanel.tsx,useVentureSimulation.ts,figuresText.ts}`.
- Modify: `app/routes.tsx`, `features/ventures/messages.pt-BR.ts`, `shared/api/ventures.ts`.
- Test: `features/ventures/{StartPage,NewVentureSheet,VentureForm,FiguresPanel}.test.tsx`,
  `features/ventures/useVentureSimulation.test.tsx`, `features/ventures/figuresText.test.ts`.

**Interfaces:**
- Consumes: os hooks e chaves da Task 8; `useOnline()` de `@/shared/offline`; `parseTypedAmount`
  e `centsToApi` de `@/shared/money`.
- Produces:
  - `useVentureSimulation(ws: string, draft: VentureDraft): { figures?: VentureFigures; offline:
    boolean; pending: boolean }`;
  - `type VentureDraft = { name: string; type: VentureType; startedOn: string | null;
    initialItems: DraftCost[]; fixedItems: DraftCost[]; products: DraftProduct[] }` (textos como a
    pessoa digitou);
  - `<FiguresPanel figures={...} currency={...} />` (a Task 10 reusa);
  - `figuresText` com `paybackText(figures)`, `breakEvenText(figures)`, `roiText(figures)`,
    `percentText("60.0") → "60,0%"`.

**Como fica:**
- **Começar:** cinco cartões de trilha (do `useVentureTemplates`) e "Em branco". "Revenda e
  serviços" abre a escolha entre os dois modelos; "Investimentos (conteúdo educativo)" leva a
  `/mais/projetos/investimentos`. Um modelo abre `NewVentureSheet` com o resumo do exemplo
  (investimento, lucro, payback) e o nome sugerido; "Em branco" abre com nome e tipo. Criar leva a
  `/mais/projetos/:id/editar`.
- **Formulário:** nome, tipo, data de início (fora de `PLANNING`), listas de itens iniciais, de
  custos fixos e de produtos com acrescentar e remover. Cada item `isExample` mostra o selo "exemplo:
  ajuste para a sua realidade"; com algum, a faixa no topo. Mexer no nome ou num valor tira o selo
  daquele item na tela (a API confirma ao salvar).
- **Prévia** (`FiguresPanel`): ao lado em tela larga, fixa embaixo no celular, com
  `aria-live="polite"`. Mostra margem e margem % por produto, equilíbrio, lucro, payback e ROI, os
  textos de borda do spec (seção 6.3) e a nota "Com os números que você digitou. Não é promessa de
  resultado."
- Salvar: desabilitado sem rede ou ocupado; 422 mostra o `detail` no campo; 409 abre o diálogo de
  conflito e mantém o rascunho.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `StartPage.test.tsx › fiveTracksAndBlank` | cinco cartões na ordem e "Em branco" |
| `StartPage.test.tsx › resaleOpensTwoTemplates` | toque em "Revenda e serviços" → "Revenda de produtos" e "Serviço por atendimento" |
| `StartPage.test.tsx › investmentsOpensTheEducationalScreen` | navega para `/mais/projetos/investimentos` |
| `NewVentureSheet.test.tsx › createsFromTemplateAndOpensTheForm` | `POST` com `templateKey`; navega para `/mais/projetos/<id>/editar` |
| `VentureForm.test.tsx › exampleBadgeAndBanner` | itens `isExample` com o selo; a faixa some quando o último é editado |
| `VentureForm.test.tsx › amountAboveMaxShowsFieldError` | `1.000.000.000,00` → erro no campo com `aria-describedby`; salvar não chama a API |
| `VentureForm.test.tsx › conflictKeepsTheDraft` | resposta 409 → diálogo de conflito; o texto digitado continua nos campos |
| `VentureForm.test.tsx › offlineDisablesSave` | `useOnline` falso → salvar desabilitado; a prévia diz "conecte-se para ver as contas" |
| `useVentureSimulation.test.tsx › waits400ms` | três mudanças em 300 ms → um pedido só, 400 ms depois da última |
| `useVentureSimulation.test.tsx › lastRequestWins` | resposta antiga chegando depois da nova não troca o painel |
| `useVentureSimulation.test.tsx › skipsRowsThatDoNotParse` | um item com `12,3,4` → pedido sem ele; o painel não quebra |
| `FiguresPanel.test.tsx › prdExample` | `2,70`, `60,0%`, `100 unidades`, `R$ 450,00`, `R$ 540,00`, `2 meses`, `710,0%` e a nota |
| `FiguresPanel.test.tsx › neutralEdgeTexts` | `NEVER` → "não se paga com esses números"; `NO_BREAK_EVEN` → "não há ponto de equilíbrio com esses números"; `NEEDS_PLANNED_UNITS` → "informe a venda prevista"; `NO_INVESTMENT` → "sem investimento inicial"; produto sem cobrir o custo → "O preço não cobre o custo deste produto"; nenhum com classe de cor de erro |
| `figuresText.test.ts › negativeUsesUnicodeMinus` | `"-88.0"` → `−88,0%` |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test --
  src/features/ventures`. Expected: FAIL.
- [ ] **Step 3: Implementar** as telas e o hook (D12). Os textos de borda ficam em
  `messages.pt-BR.ts`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check`, `build` e `check:chunks` → passam.
- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): add the track picker, the venture form and the live preview"
```

### Task 10: Web: tela do projeto, passos e status

**Files:**
- Create: `features/ventures/{VenturePage.tsx,HowWeCalculate.tsx,StepsList.tsx,StatusSheet.tsx}`.
- Modify: `app/routes.tsx`, `features/ventures/messages.pt-BR.ts`.
- Test: `features/ventures/{VenturePage,HowWeCalculate,StepsList,StatusSheet}.test.tsx`.

**Interfaces:**
- Consumes: `useVenture`, `useChangeVentureStatus`, `useDeleteVenture`, `useStepMutations`
  (Task 8); `FiguresPanel` e `figuresText` (Task 9); `ConfirmDialog` e `Sheet` de `shared/ui`.
- Produces: a rota `/mais/projetos/:id`. Sem a linha "Real vs. planejado ›" (é da 9.2).

**Como fica:**
- Como o wireframe: preço, custo, margem, equilíbrio, lucro, payback, ROI (pelo `FiguresPanel`),
  status com o botão de mudar, passos e "Editar".
- "Como calculamos", recolhível: a conta por extenso com os números do projeto (ex.: "Margem: R$ 4,50
  − R$ 1,80 = R$ 2,70"; "Ponto de equilíbrio: R$ 270,00 ÷ R$ 2,70 = 100 unidades, arredondado para
  cima"; "Payback: R$ 800,00 ÷ R$ 540,00 = 1,48 → 2 meses"; "ROI em 12 meses: (12 × R$ 540,00 −
  R$ 800,00) ÷ R$ 800,00 = 710,0%"). Os números vêm da resposta; a tela não faz conta além de
  montar o texto.
- `StatusSheet`: só as transições permitidas; ativar e encerrar pedem a data (hoje por padrão, sem
  data futura). Excluir abre `ConfirmDialog` ("Excluir o projeto? Itens, produtos e passos somem.
  Não dá para desfazer.").
- Passos: marcar (checkbox), renomear, mover para cima e para baixo (botões, não só arrastar),
  acrescentar e remover. Leitor vê sem controles.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `VenturePage.test.tsx › showsTheWireframeFigures` | os sete números do PRD e o status "Planejando" |
| `VenturePage.test.tsx › viewerSeesNoControls` | Leitor → sem "Editar", sem mudar status, passos sem checkbox ativo |
| `HowWeCalculate.test.tsx › spellsOutThePrdExample` | aberto → as quatro linhas acima |
| `StatusSheet.test.tsx › onlyAllowedTransitions` | `PLANNING` → "Começar a vender" e "Encerrar"; `CLOSED` → "Reabrir" |
| `StatusSheet.test.tsx › limitOnReopen` | resposta 403 `venture.limit-reached` → o texto do limite |
| `StatusSheet.test.tsx › deleteAsksFirst` | excluir → `ConfirmDialog`; cancelar não chama a API |
| `StepsList.test.tsx › moveWithButtons` | "mover para cima" no segundo → `PATCH {position: 0}` |
| `StepsList.test.tsx › keyboardOnly` | marcar, renomear e mover só com teclado; o foco fica no passo movido |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test --
  src/features/ventures`. Expected: FAIL.
- [ ] **Step 3: Implementar** as telas.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check`, `build` e `check:chunks` → passam.
- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): add the venture screen, its steps and status changes"
```

### Task 11: Web: trilha de investimentos

**Files:**
- Create: `features/ventures/investments/{texts.ts,InvestmentsPage.tsx,InvestmentSimulatorPage.tsx}`.
- Modify: `app/routes.tsx`, `features/ventures/messages.pt-BR.ts`, `shared/api/ventures.ts`.
- Test: `features/ventures/investments/{texts.test.ts,InvestmentsPage.test.tsx,InvestmentSimulatorPage.test.tsx}`.

**Interfaces:**
- Consumes: `Disclaimer` e `SourceStamp` de `shared/ui`; `useIndicators(ws)` e
  `useModuleEnabled('indicators')`; a rota de simulação de investimento (Task 6).
- Produces: `useInvestmentSimulation(ws)` (mutação); `INVESTMENT_TOPICS` e `REVIEWED_ON` (D13).

**Como fica:**
- **Textos:** `Disclaimer` fixo no topo; os cinco tópicos na ordem de D13, cada um com o que é,
  risco, liquidez, garantia e imposto; rodapé "Revisado em dd/mm/aaaa". Botão "Simular juros
  compostos".
- **Simulador:** `Disclaimer` fixo no topo; valor inicial, aporte mensal, prazo em anos (1 a 50) e
  até 3 cenários com nome e taxa ao ano. Com Indicadores ligado e dado presente, dois cenários vêm
  preenchidos: "Selic" com a Selic (meta) e "CDI" com o CDI, cada um com o `SourceStamp` enquanto o
  valor não for editado; editou, aparece "valor informado por você". Sem o módulo, os campos vêm
  vazios. Botão "Simular" (precisa de rede). Resultado por cenário: saldo final, total aportado e
  rendimento, e a nota fixa.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `texts.test.ts › fixedOrder` | `poupanca`, `renda-fixa`, `tesouro-direto`, `fundos`, `acoes` |
| `texts.test.ts › noRecommendationWords` | nenhuma expressão proibida de D13 em nenhum texto |
| `texts.test.ts › noProperNamesOutsideTheAllowList` | toda palavra com maiúscula fora do início da frase está na lista de D13 |
| `texts.test.ts › fgcCitesItsDate` | o texto do FGC tem um valor em R$ e uma data |
| `InvestmentsPage.test.tsx › showsTheDisclaimer` | "Conteúdo educativo. Não é recomendação de investimento." com `role="note"` |
| `InvestmentSimulatorPage.test.tsx › showsTheDisclaimerAndNote` | o aviso no topo e, depois de simular, a nota fixa |
| `InvestmentSimulatorPage.test.tsx › prefillsSelicAndCdi` | Indicadores ligado → cenários "Selic" e "CDI" com as taxas do mock e `SourceStamp` |
| `InvestmentSimulatorPage.test.tsx › editingDropsTheStamp` | mudar a taxa da Selic → selo some, "valor informado por você" |
| `InvestmentSimulatorPage.test.tsx › emptyWithoutIndicators` | módulo desligado → nenhum cenário preenchido |
| `InvestmentSimulatorPage.test.tsx › formatsMillions` | resposta `7298018.76` → `R$ 7.298.018,76` |
| `InvestmentSimulatorPage.test.tsx › atMostThreeScenarios` | com três, "Acrescentar cenário" some |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test --
  src/features/ventures/investments`. Expected: FAIL.
- [ ] **Step 3: Escrever os textos** (D13, conferindo o limite do FGC na fonte oficial) e
  implementar as duas telas.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.
- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check`, `build` e `check:chunks` → passam.
- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): add the educational investments track and simulator"
```

### Task 12: E2E e acessibilidade

**Files:**
- Create: `web/e2e/ventures.spec.ts`.
- Modify: `web/e2e/a11y.spec.ts`.

**Interfaces:**
- Consumes: tudo das Tasks 1 a 11; os ajudantes de `web/e2e/support.ts`.
- Produces: nada novo.

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `ventures.spec.ts › createFromTemplateAndSeeThePrd` | liga o módulo, Mais > Projetos > Começar > Culinária > criar → formulário com os selos "exemplo"; a prévia mostra `2,70`, `60,0%`, `100 unidades`, `R$ 450,00`, `R$ 540,00`, `2 meses`, `710,0%` |
| `ventures.spec.ts › editingUpdatesThePreview` | troca o preço para `5,00` → a prévia muda para margem `3,20` sem salvar; salvar → a tela do projeto mostra o mesmo |
| `ventures.spec.ts › stepsAndStatus` | marca um passo, começa a vender, encerra → o projeto vai para "Encerrados" |
| `ventures.spec.ts › investmentsTrack` | abre a trilha, vê o aviso nas duas telas, simula um cenário |
| `a11y.spec.ts` | axe sem violações na lista, em Começar, no formulário, na tela do projeto, nos textos e no simulador |
| `core-without-modules` | segue passando |

- [ ] **Step 2: Rodar.** Run: `corepack pnpm --dir web e2e -- ventures.spec.ts a11y.spec.ts`.
  Expected: PASS.
- [ ] **Step 3: Conferir tudo.** `./gradlew build` em `api/`; `corepack pnpm --dir web check`,
  `build` e `check:chunks` → passam.
- [ ] **Step 4: Commit**

```bash
git add web/e2e
git commit -m "test(e2e): cover ventures, the preview and the investments track"
```
