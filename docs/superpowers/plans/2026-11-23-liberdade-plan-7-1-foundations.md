# Liberdade · Plano 7.1: País, forma de pagamento, tags e recorrências — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a primeira das três entregas da v0.2.
- Cada família passa a ter país, moeda e idioma no modelo (BR, BRL e pt-BR). O front formata com o
  que vem da API.
- Receitas e gastos ganham forma de pagamento e até 5 tags, com filtro na lista e no painel.
- Dono e Adulto cadastram contas que se repetem (aluguel, salário, escola). O servidor gera os
  lançamentos previstos até o fim do mês seguinte. Na lista, a família confirma ou pula cada um.
- Nada disso conta em saldo, painel ou Retrato enquanto está previsto.
- O módulo Planejamento já vem ligado. Desligado, a família não vê as recorrências nem os previstos,
  e o núcleo continua inteiro (S2).

**Architecture:**
- **`ledger` (núcleo):** forma de pagamento, tags e status `PLANNED`/`CONFIRMED` moram no
  lançamento. A API pública do `ledger` ganha:
  - `TransactionKind`, movido de `ledger.domain`;
  - `PaymentMethod` e `PaymentMethods`;
  - `PlannedTemplate` e `PlannedTransactions`, a porta que o `planning` usa para validar o molde
    e gravar previstos;
  - os records da exportação.
- **Confirmar e pular** são rotas do `ledger`, porque mexem no lançamento. Elas levam
  `@RequiresModule(PLANNING)`.
- **`planning` (módulo novo):** regras de recorrência, a conta das datas (`RecurrenceSchedule`,
  pura), o gerador, o job diário e o ouvinte de `ModuleToggled`. Depende de `workspace`, `ledger` e
  `shared`.
- **`workspace`:** ganha `country` e `locale` e dois métodos em `Memberships` para achar o autor dos
  previstos.
- **Front:**
  - a formatação de dinheiro e datas passa a usar o `locale` e a moeda da família;
  - a tela Recorrências é um chunk sob demanda em `/mais/recorrencias`;
  - a seção "Previstos" entra na lista de lançamentos.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, PostgreSQL 18 com Flyway, JdbcClient.
  Testes com Testcontainers, `MockMvcTester` e jqwik.
- **Web:** React 19, react-router v8, TanStack Query, react-intl, MSW, Vitest e Playwright com axe.
- **Sem dependências novas.**

**Spec:**
- [Spec do Plano 7](../specs/2026-11-23-liberdade-plan-7-planning-design.md), aprovado pelo
  mantenedor em 2026-11-23. Este plano cobre só a 7.1 (seções 6.1 a 6.4, a parte 7.1 de 6.11, 7 e
  8). É a autoridade: este plano argumenta a partir dele.
- [PRD](../../prd.md) (US-23, US-24, US-26 e US-27), [Roadmap](../../roadmap.md),
  [Arquitetura](../../architecture.md), [Modelo de dados](../../data-model.md),
  [Design system](../../design-system.md).

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits, por exemplo `feat(planning): ...`,
  `feat(ledger): ...`, `test(e2e): ...` ou `docs: ...`. Cada commit termina com a linha
  `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **Isolamento (ADR-0008):** FKs compostas `(workspace_id, id)` em toda tabela nova. Toda consulta
  filtra por família. Toda rota de família usa `@WorkspaceScoped`. Um recurso de outra família
  responde 404, nunca 403.
- **Visibilidade do Jovem (US-10, D13 do Plano 4):** a regra única é `AccountScope.sqlTouches`.
  Toda rota nova que lista ou abre lançamentos, previstos ou regras passa por ela. Cada rota nova tem
  um teste com o Jovem.
- **Papéis:** só `OWNER` e `ADULT` criam, editam e arquivam regras e renomeiam e arquivam tags.
  `YOUTH` põe tags e cria tags nos próprios lançamentos, e confirma e pula previstos das contas em
  que é titular. `VIEWER` só vê.
- **Dinheiro:** `Money` e `BigDecimal` (ADR-0006). Nunca `double`. A moeda do lançamento e da regra
  é a da conta.
- **Tempo:** sempre o `Clock` da API. "Hoje" é a data no fuso da família
  (`WorkspaceMember.timezone()`). Os testes usam o `TestClock`.
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR. Validação de formato responde
  400 `request.invalid`. Regra de negócio responde 422, conflito de versão 409 e recurso ausente 404.
- **Logs:** jobs logam só `failure.getClass().getName()`, como o `FamilyPurgeJob`. Nunca valores,
  descrições, nomes de tag nem ids de pessoa.
- **Fronteiras de módulo:**
  - `planning` declara `allowedDependencies = {"workspace", "ledger", "shared"}`;
  - `audit` e `privacy` passam a listar `"planning"`;
  - `ledger` continua com `{"workspace", "shared"}` e nunca depende de `planning`.
  - O `ApplicationModulesTest` passa.
- **Contrato:** toda rota nova ou mudada regenera, no mesmo commit:
  - o `api/openapi.json` (`OpenApiDriftIT`);
  - o `web/src/shared/api/schema.ts` (`gen:api`);
  - os handlers do MSW.
  - O `RoleMatrixIT` ganha uma linha por rota nova, com os quatro papéis.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `shared*` e `*.domain*`. O pacote
  `planning.domain` entra na regra dos 90%.
- **Testes de propriedade (ADR-0013):** jqwik com semente `20261102L` e 1000 casos para a conta das
  datas.
- **Desempenho:** o carregamento inicial do web continua até 200 KB (`check:chunks`). A tela de
  Recorrências e a aba Tags carregam sob demanda.
- **Acessibilidade:** axe no Playwright cobre a tela de Recorrências, a seção Previstos e a aba Tags.
  Chips de forma de pagamento são botões com `aria-pressed`. O campo de tags tem rótulo e lista de
  sugestões acessível.
- **Offline (ADR-0007):** criar e editar lançamento com forma de pagamento e tags entra na fila
  offline como hoje. Confirmar, pular e mexer em regras e tags exigem rede e dizem isso.
- **Ética:** nada de lembrete insistente nem cor de alarme nos previstos vencidos. O texto
  "venceu em dd/mm" é neutro.
- **GitHub Actions:** nenhum workflow novo e nenhuma permissão nova.
- **Versões:** nenhuma biblioteca nova nem versão nova.
- **Ações externas:** nenhuma. O deploy no staging sai pelo pipeline de sempre, depois do merge.

### Pré-requisitos locais

Os mesmos do Plano 6: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o
compose de e2e.

## Entrega

- **PR 1 (docs):** o PR #45, com o spec, este plano e o README dos planos. Entra antes do código.
- **PR 2 (código):** branch `feat/plan-7-1-foundations`, cortada da `main` depois do PR 1. Um commit
  por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o deploy do staging sai pelo pipeline. O mantenedor confere no staging o
  critério de saída da 7.1 (seção 8 do spec):
  - forma de pagamento, tags e recorrências no ar;
  - famílias atuais com BR, BRL e pt-BR;
  - S2 verde.
- **Planos seguintes:** os planos 7.2 e 7.3 são escritos depois que a entrega anterior entra na
  `main`.

### Itens herdados

| Item | Onde |
|---|---|
| US-29 (projeção, gastos fixos e variáveis) | 7.2, como no spec |
| Issues #36, #38, #39 e #40 | Fora da 7.1 (infra, testes de proxy e prettier de docs) |
| Go-live da cobrança | Passos do mantenedor (Plano 6). A 7.1 não depende dele |

## Decisões deste plano

A seção 11 do spec deixa estes pontos ao construtor, e o plano decide:

- **D1. Ids pelo banco.** Tags, regras e previstos usam `DEFAULT uuidv7()` do PostgreSQL 18, como
  as outras tabelas. O id do lançamento feito no aparelho continua vindo do aparelho.
- **D2. `TransactionKind` na raiz do `ledger`.** O enum sai de `ledger.domain` para
  `ledger.TransactionKind`, porque o `planning` precisa dele no molde. É só mudança de pacote.
- **D3. Forma de pagamento.**
  - `ledger.PaymentMethod { PIX, CREDIT_CARD, DEBIT_CARD, BOLETO, BANK_TRANSFER, CASH }`.
  - `ledger.PaymentMethods.forCountry(String country)` devolve as seis, nessa ordem, para `"BR"`.
    Para outro país, lança `IllegalArgumentException`.
  - Transferência com forma de pagamento: 422 `transaction.payment-method-transfer`.
  - Os rótulos ficam nas mensagens do web: Pix, Crédito, Débito, Boleto, TED e Dinheiro.
- **D4. Tags no lançamento.**
  - O lançamento manda `tags` como lista de nomes.
  - O formato é checado por `@TagNames` (`TagNamesValidator`) e, fora dele, responde 400
    `request.invalid`. Cada nome:
    - não é nulo;
    - depois do `strip()`, tem de 1 a 30 caracteres;
    - não tem caractere de controle.
  - Até 5 nomes distintos, contando sem diferenciar maiúsculas (`toLowerCase(Locale.ROOT)`).
  - **Resolver ou criar:** usa a tag ativa com o mesmo `lower(name)`, mesmo que o Jovem não a veja.
    Se não existir, cria com `INSERT ... ON CONFLICT DO NOTHING` e lê de novo. O primeiro jeito de
    escrever vale.
  - Criar a 201ª tag ativa: 422 `tag.limit`.
  - No `PATCH`, `tags` nulo mantém as tags de hoje (clientes antigos na fila offline). Uma lista,
    mesmo vazia, troca. O web sempre manda as duas: `paymentMethod` e `tags`.
  - `paymentMethod` nulo no `PATCH` limpa o campo, porque o `PATCH` substitui tudo.
  - O reenvio idempotente (`sameContent`) compara também a forma de pagamento e as tags.
- **D5. Rotas de tags.**
  - `GET /v1/workspaces/{ws}/tags` para todos os papéis devolve as ativas. O Jovem recebe só as que
    ele criou ou que estão em lançamentos que ele vê (`sqlTouches`).
  - `PATCH /tags/{id}` `{name}` para `OWNER` e `ADULT`. Responde 409 `tag.name-taken` ou 404
    `tag.not-found`.
  - `POST /tags/{id}/archive` para `OWNER` e `ADULT`. É idempotente. Não há desarquivar: digitar o
    nome de novo cria outra tag.
  - O lançamento mostra também as tags arquivadas que já tinha.
- **D6. Filtros.**
  - A lista ganha `tagId` e `paymentMethod`.
  - O painel ganha `paymentMethod`. Ele filtra receitas, gastos, resultado e categorias. Saldo e
    saldo diário não mudam.
- **D7. Previstos no `ledger`.**
  - Todas as somas filtram `status = 'CONFIRMED'`: saldos, fluxos, ranking de categorias, a contagem
    de uso de conta e categoria, e a atividade da família (`LedgerFamilyActivity`).
  - A lista do mês mostra `PLANNED` só com o módulo ligado. No mês atual, traz também os `PLANNED`
    vencidos de meses anteriores.
  - `TransactionView` ganha `status`, `paymentMethod`, `tags` (nomes, ordenados), `scheduledOn` e
    `recurringRuleId`.
  - `PATCH` num previsto: 422 `transaction.planned`. Excluir um previsto continua permitido.
- **D8. Confirmar e pular.** As duas rotas levam `@RequiresModule(PLANNING)` e aceitam `OWNER`,
  `ADULT` e `YOUTH`. A checagem do Jovem é a mesma do `PATCH` de hoje.
  - `POST /transactions/{id}/confirm` com
    `ConfirmTransactionRequest(@NotNull Long version, @Nullable Money amount, @Nullable LocalDate occurredOn, @Nullable UUID accountId)`.
  - Confirmar grava `CONFIRMED`, `member_id` igual a quem confirmou e `version + 1`. O ajuste segue
    as regras de criar: moeda da conta, data no intervalo e conta visível ao Jovem.
  - `POST /transactions/{id}/skip` com `{version}` faz a exclusão lógica. O `restore` de hoje desfaz.
  - Respostas de erro:

    | Caso | Resposta |
    |---|---|
    | Previsto inexistente, invisível ou já excluído | 404 `transaction.not-found` |
    | Lançamento já confirmado | 409 `transaction.not-planned` |
    | Versão diferente | 409 `transaction.version-conflict` |

- **D9. Rotas de regras** em `/v1/workspaces/{ws}/recurring-rules`, todas com
  `@RequiresModule(PLANNING)`:
  - `GET` para todos os papéis devolve as regras ativas. O Jovem recebe as que tocam contas dele
    (`sqlTouches`).
  - `POST` para `OWNER` e `ADULT`. O id vem do banco, e a resposta é 201.
  - `PATCH /{id}` substitui tudo, com `version`.
  - `POST /{id}/archive` é idempotente.
  - O molde é checado por `PlannedTransactions.check` e responde com os códigos `transaction.*` de
    criar lançamento. É uma lista de regras só.
  - `end_on` antes de `start_on`: 422 `recurring-rule.end-before-start`.
  - Regra ausente, de outra família ou arquivada: 404 `recurring-rule.not-found`. Versão diferente:
    409 `recurring-rule.version-conflict`.
- **D10. Datas.** `RecurrenceSchedule` calcula cada ocorrência a partir de `start_on`
  (`startOn.plusMonths(n * interval)`, e o mesmo com semanas e anos), nunca encadeando a anterior. O
  `java.time` já põe o dia 31 no último dia do mês e o 29/02 no 28/02.
- **D11. Gerar.**
  - **Intervalo:** do maior entre `next_due_on`, `start_on` e o dia 1º do mês atual, até o menor
    entre o último dia do mês seguinte e `end_on`.
  - **`next_due_on`:** a primeira ocorrência depois do horizonte, ou `null` quando não há mais
    nenhuma.
  - **Editar a regra:** `next_due_on` volta a `start_on`, apaga os previstos abertos com
    `scheduled_on` a partir de hoje (`dropOpenFrom`) e gera de novo. O índice único pula o que já
    existe.
  - **Conta ou categoria arquivada:** aquela ocorrência não é gravada, e o gerador segue.
- **D12. Autor do previsto.** `recurring_rules.created_by` é o id da `membership`, como em
  `transactions`. `workspace.Memberships` ganha dois métodos:
  - `Optional<WorkspaceMember> activeMember(UUID workspaceId, UUID membershipId)`;
  - `Optional<WorkspaceMember> activeOwner(UUID workspaceId)`, o Dono ativo mais antigo.
  - O autor é `activeMember(ws, rule.createdBy)` ou, se não houver, `activeOwner(ws)`. O
    `WorkspaceMember` também dá o fuso de "hoje".
- **D13. Job e módulo.**
  - `RecurrenceJob` roda com `@Scheduled(cron = "0 10 4 * * *", zone = "America/Sao_Paulo")`, uma
    transação por família, e pula famílias com o módulo desligado.
  - Um `@EventListener` síncrono em `ModuleToggled` (`PLANNING`, `enabled = true`) gera os
    previstos daquela família na hora.
- **D14. Limpeza.** O `TransactionPurgeJob` passa a ignorar linhas com `recurring_rule_id`. Assim, um
  previsto pulado nunca some de vez e nunca libera o índice para o job recriar a ocorrência.
- **D15. Módulo ligado por padrão.** A V16 insere `('planning', true)` para toda família, com
  `ON CONFLICT DO NOTHING`, e a criação de família passa a inserir a mesma linha. Os testes que
  esperam todos os módulos desligados numa família nova mudam (`ModulesIT`). O S2 já desliga tudo
  com `setModules(page, false)`.
- **D16. Auditoria.** O `planning` publica `RecurringRuleArchived(UUID workspaceId, UUID ruleId, UUID actorUserId)`.
  - O `AuditListeners` grava `recurring_rule.archived` com alvo `RECURRING_RULE`.
  - A V16 refaz o CHECK de `audit_events.target_type` com `'RECURRING_RULE'`.
- **D17. Exportação v3.**
  - `FORMAT_VERSION` passa a 3.
  - A família leva regras, tags, vínculos, `status`, `payment_method`, `scheduled_on`,
    `recurring_rule_id`, `country` e `locale`.
  - A exportação pessoal leva as tags que a pessoa criou.
  - O `planning` expõe `PlanningExport`.
- **D18. Política de privacidade.** A lista de dados de hoje não cita tags, forma de pagamento nem
  recorrências. Por isso entram as versões `privacy-2026-11.1` e `privacy-2026-12.1`, com aviso e
  sem novo aceite:
  - **Mapa:** `LegalDocuments.KEEPS_ACCEPTANCE` liga `privacy-2026-11.1` a `privacy-2026-11` e
    `privacy-2026-12.1` a `privacy-2026-12`.
  - **`acceptanceRequired`:** o novo campo `DocumentStatus.acceptanceRequired` é falso quando a
    versão aceita é a atual ou a que o mapa liga a ela.
  - **Aviso no web:** "Atualizamos a política de privacidade", que some ao fechar. Fica guardado por
    versão no `localStorage`, dentro de `try/catch`.
- **D19. Dinheiro e idioma no web.**
  - `formatMoney(cents: bigint, currency: string, locale: string)` troca o `formatBRL`, com cache
    de `Intl.NumberFormat` por par.
  - `useFormatMoney()` lê a família atual e cai em BRL e pt-BR.
  - O `IntlProvider` usa o `locale` da família.
  - Um teste de guarda proíbe `'pt-BR'` e `'BRL'` entre aspas fora de `shared/i18n/index.ts` e
    `shared/money/money.ts`.
- **D20. CHECK de `scheduled_on` num sentido só.** O spec (6.2) pede `ON DELETE SET NULL` na FK da
  regra e o CHECK de igualdade `(recurring_rule_id IS NULL) = (scheduled_on IS NULL)`. No
  PostgreSQL, os dois juntos quebram: o `SET NULL (recurring_rule_id)` limpa só a regra, e o CHECK
  recusa a linha. A coluna `scheduled_on` não pode entrar na lista do `SET NULL`, porque não faz
  parte da FK. O plano mantém o `SET NULL` do spec, que preserva o histórico confirmado, e usa
  `CHECK (recurring_rule_id IS NULL OR scheduled_on IS NOT NULL)`. Na prática, nada muda: o app
  nunca apaga regra de vez (só arquiva), e a exclusão da família apaga tudo junto.

## Review Focus

1. **Jovem e o que não é dele:** a lista de tags não traz o nome de uma tag usada só em conta do
   Adulto, e um previsto numa conta do Adulto não aparece nem pode ser confirmado pelo Jovem (404).
   Testes `youthSeesOnlyTagsOfVisibleTransactions` (Task 2) e `youthCannotConfirmAdultPlanned`
   (Task 5).
2. **Confirmar e pular ao mesmo tempo, em dois aparelhos:** quem chega depois recebe 404 ou 409, e o
   previsto termina num estado só. Teste `confirmAfterSkipIsNotFound` e
   `skipAfterConfirmIsNotPlanned` (Task 5); no web, `PlannedSheet.test.tsx` mostra "Esse previsto
   mudou. Confira a lista." (Task 12).
3. **Editar a regra depois de confirmar ou pular uma ocorrência:** o job e a edição não recriam a
   ocorrência confirmada (com outra data) nem a pulada, nem depois da limpeza de 30 dias. Testes
   `editKeepsConfirmedAndSkipped` (Task 6) e `purgeKeepsSkippedPlanned` (Task 3).
4. **Módulo desligado:** as rotas de regras, confirmar e pular respondem 403 `module.disabled`, a
   lista esconde os previstos, o job pula a família e religar completa o horizonte. Testes
   `ModuleOffIT` (Task 7) e o S2 (Task 14).
5. **Datas que enganam:** dia 31 em fevereiro, 29/02 em ano comum, semanal com intervalo 2 e
   `end_on` no meio do horizonte. Propriedade `occurrencesNeverDrift` e testes de exemplo (Task 4).

---

## Mapa de arquivos

**API** (`api/src/main/java/br/com/liberdadefamilia/`):

| Pacote | Novo | Muda |
|---|---|---|
| `ledger` | `TransactionKind` (movido), `PlannedTemplate`, `PlannedTransactions`, `ExportedTag` | `ExportedTransaction`, `LedgerExport` |
| `shared` | `PaymentMethod`, `PaymentMethods`, `TransactionAmount` (e o validador), movidos para cá nos rulings R4 e R11 | — |
| `ledger/domain` | — | `TransactionKind` sai daqui; quem importava passa a importar `ledger.TransactionKind` |
| `ledger/internal` | `TagController`, `TagService`, `TagRepository`, `TagView`, `RenameTagRequest`, `TagNames`, `TagNamesValidator`, `ConfirmTransactionRequest`, `SkipTransactionRequest`, `PlannedTransactionService`, `TagRejectedException` | `CreateTransactionRequest`, `UpdateTransactionRequest`, `TransactionView`, `TransactionService`, `TransactionRepository`, `TransactionController`, `DashboardController`, `DashboardService`, `LedgerJdbcExport`, `LedgerFamilyActivity`, `TransactionPurgeJob` |
| `planning` | `package-info`, `RecurringRuleArchived`, `PlanningExport`, `ExportedRecurringRule` | — |
| `planning/domain` | `Frequency`, `RecurrenceSchedule` | — |
| `planning/internal` | `RecurringRuleController`, `RecurringRuleService`, `RecurringRuleRepository`, `RecurringRuleView`, `CreateRecurringRuleRequest`, `UpdateRecurringRuleRequest`, `RecurrenceGenerator`, `RecurrenceJob`, `PlanningModuleListener`, `PlanningJdbcExport`, `RecurringRuleNotFoundException`, `RecurringRuleConflictException`, `RecurringRuleRejectedException` | — |
| `workspace` | — | `Memberships` (2 métodos) |
| `workspace/internal` | — | `JdbcMemberships`, `WorkspaceService` (linha do `planning`), `WorkspaceRepository` (insert), `MeWorkspace` e a consulta que o monta |
| `audit` | — | `AuditEntry` (javadoc com `RECURRING_RULE`), `package-info` (`"planning"`) |
| `audit/internal` | — | `AuditListeners` |
| `privacy` | — | `package-info` (`"planning"`) |
| `privacy/internal` | — | `ExportService`, `LegalDocuments`, `DocumentStatus`, `DocumentService` |

**Migrações:** `api/src/main/resources/db/migration/V15__country_tags_payment.sql` e
`V16__recurrences.sql`.

**Testes da API** (`api/src/test/java/br/com/liberdadefamilia/`): `ledger/` (`PaymentMethodIT`,
`TagsIT`, `PlannedReadsIT`, `ConfirmSkipIT`, `MigrationV15IT`, `MigrationV16IT`), `planning/`
(`RecurrenceScheduleTest`, `RecurrenceScheduleProperties`, `RecurringRulesIT`,
`RecurrenceGeneratorIT`, `RecurrenceJobIT`, `ModuleOffIT`), `workspace/ModulesIT`,
`workspace/MembershipsIT`, `privacy/` (export e documentos), `RoleMatrixIT`,
`ApplicationModulesTest` e `OpenApiDriftIT`.

**Web** (`web/src/`):

| Pasta | Novo | Muda |
|---|---|---|
| `shared/money` | — | `money.ts` (`formatMoney`; sai o `formatBRL`), `money.test.ts` |
| `shared/api` | `useFormatMoney.ts`, `tags.ts`, `recurrences.ts` (fora do barrel) | `endpoints.ts`, `hooks.ts`, `mutations.ts`, `types.ts`, `schema.ts` (gerado) |
| `shared/i18n` | `localeLiterals.test.ts` | `index.ts`, `messages.pt-BR.ts` |
| `app` | — | `providers.tsx` (`locale` da família), `routes.tsx` (rota `/mais/recorrencias`), `MorePage.tsx` (entrada "Recorrências") |
| `features/transactions` | `PaymentMethodChips.tsx`, `TagInput.tsx`, `PlannedSection.tsx`, `PlannedSheet.tsx` e os testes | `TransactionForm.tsx`, `TransactionFilters.tsx`, `TransactionsPage.tsx`, `listItems.ts`, `TransactionRow.tsx`, `TransactionDetailSheet.tsx`, `EditTransaction.tsx`, `messages.pt-BR.ts` |
| `features/dashboard` | — | `DashboardPage.tsx` (filtro), `charts.ts` (recebe `format`) |
| `features/categories` | `TagsTab.tsx` e o teste | `CategoriesPage.tsx`, `CategoryTabs.tsx` |
| `features/recurrences` | `RecurrencesPage.tsx`, `RuleForm.tsx`, `RuleSheet.tsx`, `ruleText.ts`, `messages.pt-BR.ts`, `register.ts` e os testes | — |
| `features/legal` | `texts/privacy-2026-11.1.ts`, `texts/privacy-2026-12.1.ts`, `PolicyUpdatedNotice.tsx` | `legalConfig.ts`, `useDocuments.ts`, `legalTexts.test.ts`, `messages.pt-BR.ts` |
| demais chamadores de `formatBRL` | — | `AccountsPage`, `BalanceInput`, `HomePage`, `VersionConflictDialog`, `portraitData`, `MonthSwitcher` |
| `mocks` | — | `handlers.ts`, `data.ts` |
| `eslint` | — | `boundaries.js` (`recurrences` como módulo opcional) |

**E2E** (`web/e2e/`): `recurrences.spec.ts` (novo), `a11y.spec.ts`, `support.ts` e
`core-without-modules.spec.ts`.

**Docs:** `docs/data-model.md`, `docs/architecture.md` e o README dos planos.

**Em toda task de API:** as rotas de família usam `@WorkspaceScoped`. Rota nova ou mudada regenera,
no mesmo commit:
- o OpenAPI: `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` em `api/`;
- o `schema.ts`: `corepack pnpm --dir web gen:api`;
- os handlers do MSW.

Nesse mesmo commit, o `RoleMatrixIT` ganha as linhas das rotas novas. Eventos são records públicos
no pacote raiz do módulo, só com ids.

## Tasks

### Task 1: País, idioma e forma de pagamento (V15, parte 1)

**Files:**
- Create em `ledger/`: `PaymentMethod.java`, `PaymentMethods.java`.
- Move: `ledger/domain/TransactionKind.java` → `ledger/TransactionKind.java` (e os imports).
- Create: `db/migration/V15__country_tags_payment.sql`. Esta task escreve as colunas de país,
  idioma e forma de pagamento. A Task 2 acrescenta as tabelas de tags no mesmo arquivo, antes do
  merge.
- Modify: `ledger/internal/CreateTransactionRequest.java`, `UpdateTransactionRequest.java`,
  `TransactionView.java`, `TransactionService.java`, `TransactionRepository.java`,
  `TransactionController.java` (filtro), `DashboardController.java`, `DashboardService.java`,
  `workspace/internal/MeWorkspace.java` e a consulta que o monta.
- Test: `ledger/PaymentMethodsTest.java`, `ledger/PaymentMethodIT.java`, `ledger/MigrationV15IT.java`,
  `workspace/MeWorkspaceIT` (ou o IT que hoje confere `/v1/me`).

**Interfaces:**
- Produces:
  - `public enum TransactionKind { INCOME, EXPENSE, TRANSFER }` em `ledger`;
  - `public enum PaymentMethod { PIX, CREDIT_CARD, DEBIT_CARD, BOLETO, BANK_TRANSFER, CASH }`;
  - `public final class PaymentMethods { public static List<PaymentMethod> forCountry(String country) }`;
  - `CreateTransactionRequest` e `UpdateTransactionRequest` ganham `@Nullable PaymentMethod paymentMethod`;
  - `TransactionView` ganha `@Nullable PaymentMethod paymentMethod`;
  - `MeWorkspace` ganha `String country`, `String locale` e `List<PaymentMethod> paymentMethods`;
  - `GET /transactions` aceita `paymentMethod` e `GET /dashboard` aceita `paymentMethod` (D6).

**Como fica:**
- A V15 (parte 1) tem:
  - `ALTER TABLE workspaces ADD COLUMN country char(2) NOT NULL DEFAULT 'BR' CHECK (country IN ('BR'))`;
  - `ADD COLUMN locale text NOT NULL DEFAULT 'pt-BR' CHECK (locale IN ('pt-BR'))`;
  - `ALTER TABLE transactions ADD COLUMN payment_method text NULL CHECK (payment_method IN ('PIX','CREDIT_CARD','DEBIT_CARD','BOLETO','BANK_TRANSFER','CASH'))`;
  - `CHECK (kind <> 'TRANSFER' OR payment_method IS NULL)`.
- O `TransactionService` rejeita transferência com forma de pagamento antes do banco: 422
  `transaction.payment-method-transfer` com o detail "Transferência não tem forma de pagamento."
- O `sameContent` compara a forma de pagamento.
- No painel, `paymentMethod` entra nas consultas de receitas, gastos e categorias e não entra nas
  de saldo (D6).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `PaymentMethodsTest.brazilHasSixInOrder` | `forCountry("BR")` → `[PIX, CREDIT_CARD, DEBIT_CARD, BOLETO, BANK_TRANSFER, CASH]` |
| `PaymentMethodsTest.unknownCountryFails` | `forCountry("US")` → `IllegalArgumentException` |
| `MigrationV15IT.existingFamiliesAreBrazilian` | família criada antes da V15 (insert direto) → `country = 'BR'`, `locale = 'pt-BR'`, `base_currency = 'BRL'` |
| `MigrationV15IT.transferWithPaymentMethodViolatesCheck` | insert direto de `TRANSFER` com `PIX` → violação de CHECK |
| `PaymentMethodIT.createAndReadBack` | `POST` de gasto com `"paymentMethod":"PIX"` → 201; `GET` → `paymentMethod = "PIX"` |
| `PaymentMethodIT.transferRejectsPaymentMethod` | transferência com `CASH` → 422 `transaction.payment-method-transfer` |
| `PaymentMethodIT.patchNullClears` | `PATCH` sem `paymentMethod` → campo nulo |
| `PaymentMethodIT.retryWithOtherMethodConflicts` | mesmo id e conteúdo, só a forma diferente → 409 `transaction.id-conflict` |
| `PaymentMethodIT.listFiltersByMethod` | um gasto `PIX` e um `CASH` no mês; `?paymentMethod=PIX` → só o primeiro |
| `PaymentMethodIT.dashboardFiltersFlowsNotBalance` | receita R$ 100 `PIX`, gasto R$ 30 `CASH`; `?paymentMethod=CASH` → receitas 0, gastos 30, categorias só a do gasto; saldo igual ao sem filtro (70) |
| `MeWorkspaceIT.exposesCountryLocaleAndMethods` | `/v1/me` → `country "BR"`, `locale "pt-BR"`, `paymentMethods` com os seis códigos |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*PaymentMethod*' --tests '*MigrationV15IT' --tests '*MeWorkspaceIT'`
Expected: FAIL na compilação (`PaymentMethod` não existe).

- [ ] **Step 3: Implementar.** Mova o `TransactionKind` primeiro, num passo só de imports, e rode
  `./gradlew compileJava compileTestJava`. Depois siga "Como fica". Regenere o contrato.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*TransactionsIT' --tests '*DashboardIT' --tests '*RoleMatrixIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(ledger): add payment method, country and locale"
```

---

### Task 2: Tags (V15, parte 2)

**Files:**
- Modify: `db/migration/V15__country_tags_payment.sql` (acrescenta as tabelas).
- Create em `ledger/internal/`: `TagController.java`, `TagService.java`, `TagRepository.java`,
  `TagView.java`, `RenameTagRequest.java`, `TagNames.java`, `TagNamesValidator.java`,
  `TagRejectedException.java`.
- Modify: `CreateTransactionRequest.java`, `UpdateTransactionRequest.java`, `TransactionView.java`,
  `TransactionService.java`, `TransactionRepository.java`, `TransactionController.java` (filtro
  `tagId`).
- Test: `ledger/internal/TagNamesValidatorTest.java`, `ledger/TagsIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `TransactionView` e os requests da Task 1.
- Produces:
  - `@TagNames` em `List<String> tags` (nulo permitido) nos dois requests;
  - `TransactionView.tags` como `List<String>`, ordenado por `lower(name)`;
  - `TagView(UUID id, String name, @Nullable Instant archivedAt)`;
  - `TagService.resolve(WorkspaceMember member, List<String> names) → List<UUID>`, chamado dentro
    da transação do lançamento;
  - rotas `GET /tags`, `PATCH /tags/{id}`, `POST /tags/{id}/archive` (D5);
  - `GET /transactions?tagId=`.

**Como fica:**
- A V15 (parte 2) cria:
  - `tags (id uuid PRIMARY KEY DEFAULT uuidv7(), workspace_id uuid NOT NULL REFERENCES workspaces ON DELETE CASCADE, name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 30), created_by uuid NULL, created_at timestamptz NOT NULL DEFAULT now(), archived_at timestamptz NULL, UNIQUE (workspace_id, id), FOREIGN KEY (workspace_id, created_by) REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by))`;
  - `CREATE UNIQUE INDEX tags_active_name ON tags (workspace_id, lower(name)) WHERE archived_at IS NULL`;
  - `transaction_tags (workspace_id uuid NOT NULL, transaction_id uuid NOT NULL, tag_id uuid NOT NULL, PRIMARY KEY (workspace_id, transaction_id, tag_id), FOREIGN KEY (workspace_id, transaction_id) REFERENCES transactions (workspace_id, id) ON DELETE CASCADE, FOREIGN KEY (workspace_id, tag_id) REFERENCES tags (workspace_id, id) ON DELETE CASCADE)`;
  - `CREATE INDEX transaction_tags_by_tag ON transaction_tags (workspace_id, tag_id)`;
  - se `transactions` ainda não tiver `UNIQUE (workspace_id, id)`, a V15 acrescenta, para a FK
    composta.
- **Limite de tags.** Ao criar uma tag, o serviço conta as ativas da família. Com 200, responde 422
  `tag.limit` e o detail "A família já tem 200 tags. Arquive alguma para criar outra."
- **Salvar o lançamento.** Criar e editar gravam os vínculos na mesma transação. No `PATCH`,
  `tags = null` não mexe nos vínculos. Uma lista apaga os vínculos e grava os novos.
- **Renomear.** Para um nome que já existe numa tag ativa (sem diferenciar maiúsculas), responde 409
  `tag.name-taken`.
- **Lista do Jovem:**

  ```sql
  where t.archived_at is null and (t.created_by = :membershipId or exists (
    select 1 from transaction_tags tt join transactions x on x.workspace_id = tt.workspace_id and x.id = tt.transaction_id
    where tt.workspace_id = t.workspace_id and tt.tag_id = t.id and x.deleted_at is null
      and <AccountScope.sqlTouches("x.account_id", "x.to_account_id")>))
  ```

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `TagNamesValidatorTest` (tabela) | `null` → válido; `[]` → válido; `["  Escola "]` → válido; `[""]`, `["   "]`, 31 caracteres, `"a\u0007"` → inválido; `["a","A","b","c","d","e"]` → válido (5 distintos); 6 distintos → inválido |
| `TagsIT.createTransactionCreatesTags` | gasto com `["Escola","viagem"]` → 201; `GET /tags` traz as duas; o lançamento traz `["Escola","viagem"]` |
| `TagsIT.reuseIgnoresCase` | segundo gasto com `["ESCOLA"]` → usa a mesma tag; o nome continua `Escola` |
| `TagsIT.patchNullKeepsListReplaces` | `PATCH` sem `tags` → tags iguais; `PATCH` com `[]` → nenhuma |
| `TagsIT.limit200` | 200 tags ativas; gasto com nome novo → 422 `tag.limit`; com nome existente → 201 |
| `TagsIT.renameTakenConflicts` | renomear `viagem` para `escola` → 409 `tag.name-taken` |
| `TagsIT.archiveHidesFromListKeepsOnTransaction` | arquivar `Escola` duas vezes → 204 e 204; `GET /tags` sem ela; o lançamento ainda mostra `Escola`; digitar `Escola` de novo cria tag nova |
| `TagsIT.listFiltersByTag` | `?tagId=` da `viagem` → só o lançamento com ela |
| `TagsIT.otherFamilyTagIsNotFound` | `PATCH` numa tag de outra família → 404 `tag.not-found` |
| `TagsIT.youthSeesOnlyTagsOfVisibleTransactions` | tag `Presente` só num gasto da conta do Adulto; Jovem `GET /tags` → sem `Presente`; Jovem cria gasto com `["Presente"]` na conta dele → 201, mesma tag (reuso silencioso); agora `GET /tags` do Jovem traz `Presente` |
| `TagsIT.youthAndViewerCannotRename` | `PATCH` e `archive` como `YOUTH` e `VIEWER` → 403 |
| `TagsIT.retryWithOtherTagsConflicts` | mesmo id, tags diferentes → 409 `transaction.id-conflict` |
| `RoleMatrixIT` | linhas das três rotas de tags |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*TagNamesValidatorTest' --tests '*TagsIT'`
Expected: FAIL na compilação (`TagNames` não existe).

- [ ] **Step 3: Implementar.** Siga "Como fica" e D4, D5. Regenere o contrato.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*TransactionsIT' --tests '*YouthScopeIT' --tests '*RoleMatrixIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(ledger): add family tags on transactions"
```

---

### Task 3: Previstos no `ledger` (V16)

**Files:**
- Create: `db/migration/V16__recurrences.sql`.
- Modify em `ledger/internal/`: `TransactionRepository.java`, `TransactionService.java`,
  `TransactionView.java`, `TransactionController.java`, `DashboardService.java`,
  `LedgerFamilyActivity.java`, `TransactionPurgeJob.java`.
- Modify: `workspace/internal/WorkspaceService.java` e `WorkspaceRepository` (linha do `planning`),
  `audit/AuditEntry.java` (javadoc).
- Test: `ledger/MigrationV16IT.java`, `ledger/PlannedReadsIT.java`,
  `ledger/TransactionPurgeJobIT.java`, `workspace/ModulesIT.java`.

**Interfaces:**
- Consumes: `Modules.isEnabled(UUID, ModuleKey)` do `workspace`.
- Produces:
  - `TransactionView` com `String status` (`"CONFIRMED"` ou `"PLANNED"`),
    `@Nullable LocalDate scheduledOn` e `@Nullable UUID recurringRuleId`;
  - tabela `recurring_rules` (para a Task 6);
  - helper de teste `TestLedger.insertPlanned(UUID ws, UUID accountId, UUID categoryId, Money amount, LocalDate scheduledOn, UUID ruleId, UUID membershipId) → UUID`, para as tasks seguintes.

**Como fica:**
- A V16 cria `recurring_rules` com as colunas da seção 6.2 do spec:
  - `id uuid PRIMARY KEY DEFAULT uuidv7()`, `kind` com o mesmo CHECK de `transactions`,
    `amount numeric(19,4) CHECK (amount > 0)`, `currency char(3)`, `account_id`,
    `to_account_id NULL`, `category_id NULL` e `payment_method NULL` com o mesmo CHECK;
  - `description text NULL CHECK (char_length(description) BETWEEN 1 AND 140)`;
  - `frequency text CHECK (frequency IN ('WEEKLY','MONTHLY','YEARLY'))`;
  - `interval smallint CHECK (interval BETWEEN 1 AND 12)`;
  - `start_on date NOT NULL`, `end_on date NULL CHECK (end_on >= start_on)` e
    `next_due_on date NULL`;
  - `created_by uuid NULL` com FK composta para `memberships` e `ON DELETE SET NULL (created_by)`;
  - `created_at`, `updated_at`, `archived_at` e `version bigint NOT NULL DEFAULT 0`;
  - `UNIQUE (workspace_id, id)`, FKs compostas para contas e categoria, e os mesmos três CHECKs de
    transferência de `transactions`, mais `CHECK (kind <> 'TRANSFER' OR payment_method IS NULL)`.
- Em `transactions`, a V16 acrescenta:
  - `status`, como no spec;
  - `recurring_rule_id uuid NULL` com
    `FOREIGN KEY (workspace_id, recurring_rule_id) REFERENCES recurring_rules (workspace_id, id) ON DELETE SET NULL (recurring_rule_id)`;
  - `scheduled_on date NULL`;
  - `CHECK (recurring_rule_id IS NULL OR scheduled_on IS NOT NULL)` (D20);
  - `CREATE UNIQUE INDEX transactions_one_per_occurrence ON transactions (workspace_id, recurring_rule_id, scheduled_on) WHERE recurring_rule_id IS NOT NULL`.
- A V16 ainda:
  - insere `('planning', true)` em `workspace_modules` para cada família, com
    `ON CONFLICT DO NOTHING` (D15);
  - refaz o CHECK de `audit_events.target_type` com a lista da V14 mais `'RECURRING_RULE'`. Antes,
    confira o nome do CHECK como na V14.
- **Previstos fora das somas.** Toda consulta de soma e de uso ganha `status = 'CONFIRMED'` (D7).
  Procure por `from transactions` em `TransactionRepository`, `DashboardService`,
  `LedgerFamilyActivity` e no cálculo de saldo das contas.
- **Lista do mês.** Sem o módulo, a lista leva `and status = 'CONFIRMED'`. Com o módulo, quando o
  mês pedido é o mês atual no fuso da família, a condição de data vira
  `(occurred_on between :from and :to or (status = 'PLANNED' and occurred_on < :from))`.
- **`PATCH` num previsto:** 422 `transaction.planned` com o detail "Confirme o previsto antes de
  editar."
- **Família nova.** `WorkspaceService.create` insere `('planning', true)` logo depois do Dono.
- **Limpeza.** O `TransactionPurgeJob` acrescenta `and recurring_rule_id is null` (D14).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MigrationV16IT.existingFamiliesGetPlanningOn` | família inserida antes da V16 → linha `planning` com `enabled = true` |
| `MigrationV16IT.occurrenceIsUniqueEvenWhenDeleted` | dois inserts com a mesma regra e `scheduled_on`, o primeiro com `deleted_at` → violação de `transactions_one_per_occurrence` |
| `MigrationV16IT.ruleWithoutScheduledOnViolatesCheck` | `recurring_rule_id` preenchido e `scheduled_on` nulo → violação de CHECK |
| `MigrationV16IT.auditAcceptsRecurringRule` | `target_type = 'RECURRING_RULE'` passa |
| `ModulesIT.newFamilyHasPlanningOn` | substitui a asserção da linha 120: família nova → `isEnabled(ws, PLANNING)` verdadeiro, os outros três falsos |
| `PlannedReadsIT.plannedDoesNotCountAnywhere` | gasto confirmado de R$ 50 e previsto de R$ 900 no mês → saldo da conta, painel (gastos 50), ranking de categorias e uso da categoria iguais ao cenário sem o previsto |
| `PlannedReadsIT.listShowsPlannedWithModuleOn` | `GET /transactions?month=` atual → traz o previsto com `status "PLANNED"`, `scheduledOn` e `recurringRuleId` |
| `PlannedReadsIT.listHidesPlannedWithModuleOff` | módulo desligado → o previsto não vem; religar → volta |
| `PlannedReadsIT.overdueShowsInCurrentMonthOnly` | `TestClock` em 2026-12-05; previsto de 2026-11-28 → aparece em `month=2026-12` e em `month=2026-11`; não aparece em `month=2027-01` |
| `PlannedReadsIT.patchPlannedIs422` | `PATCH` num previsto → 422 `transaction.planned` |
| `PlannedReadsIT.youthDoesNotSeeAdultPlanned` | previsto na conta do Adulto → ausente na lista do Jovem |
| `TransactionPurgeJobIT.purgeKeepsSkippedPlanned` | previsto excluído há 31 dias → continua no banco; lançamento comum excluído há 31 dias → some |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*MigrationV16IT' --tests '*PlannedReadsIT' --tests '*TransactionPurgeJobIT' --tests '*ModulesIT'`
Expected: FAIL (`status` não existe).

- [ ] **Step 3: Implementar.** Siga "Como fica". Regenere o contrato (a view mudou).

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*DashboardIT' --tests '*AccountsIT' --tests '*CategoriesIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(ledger): add planned transactions kept out of every total"
```

---

### Task 4: Conta das datas (`RecurrenceSchedule`)

**Files:**
- Create em `planning/`: `package-info.java`.
- Create em `planning/domain/`: `Frequency.java`, `RecurrenceSchedule.java`.
- Test: `planning/domain/RecurrenceScheduleTest.java`, `planning/domain/RecurrenceScheduleProperties.java`.

**Interfaces:**
- Produces:
  - `@ApplicationModule(allowedDependencies = {"workspace", "ledger", "shared"})` no
    `package-info` do `planning`;
  - `public enum Frequency { WEEKLY, MONTHLY, YEARLY }`;
  - `public static List<LocalDate> occurrences(Frequency frequency, int interval, LocalDate startOn, @Nullable LocalDate endOn, LocalDate from, LocalDate toInclusive)`,
    com as datas em ordem crescente, todas em `[max(from, startOn), min(toInclusive, endOn)]`;
  - `public static Optional<LocalDate> firstAfter(Frequency frequency, int interval, LocalDate startOn, @Nullable LocalDate endOn, LocalDate after)`.

**Como fica:** a n-ésima ocorrência é `startOn` mais `n × interval` unidades (D10). O laço começa no
primeiro `n` cuja data é ≥ `from`, sem andar a partir da data anterior. `interval` fora de 1 a 12
lança `IllegalArgumentException`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `day31FallsOnLastDay` | mensal, 1, início 2026-01-31, de 2026-01-01 a 2026-05-31 → `01-31, 02-28, 03-31, 04-30, 05-31` |
| `leapDayFallsOn28` | anual, 1, início 2028-02-29, de 2028-01-01 a 2031-12-31 → `2028-02-29, 2029-02-28, 2030-02-28, 2031-02-28` |
| `weeklyEveryTwo` | semanal, 2, início 2026-11-02 (segunda), de 2026-11-01 a 2026-12-31 → 2/11, 16/11, 30/11, 14/12, 28/12 |
| `endOnCutsTheHorizon` | mensal, 1, início 2026-11-10, fim 2026-12-01, até 2027-01-31 → só `2026-11-10` |
| `fromAfterStartSkipsPast` | mensal, 1, início 2026-01-05, de 2026-11-01 a 2026-12-31 → `2026-11-05, 2026-12-05` (a regra que começa no passado do spec) |
| `firstAfterReturnsEmptyPastEnd` | `firstAfter(..., fim 2026-12-01, after 2026-12-31)` → vazio |
| `intervalOutOfRangeFails` | `interval` 0 e 13 → `IllegalArgumentException` |
| `RecurrenceScheduleProperties.occurrencesNeverDrift` (jqwik, semente `20261102L`, 1000 casos; frequência, `interval` 1–12, `startOn` entre 2024 e 2030, janela de até 400 dias) | cada data é `startOn` mais `k × interval` unidades para algum `k ≥ 0`; nenhuma repete; todas dentro da janela e de `end_on`; dia do mês = `min(startOn.dayOfMonth, lengthOfMonth)` no mensal |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*RecurrenceSchedule*'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*ApplicationModulesTest'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL (o `planning.domain` entra nos 90%).

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(planning): add the recurrence date rules"
```

---

### Task 5: Porta dos previstos, confirmar e pular

**Files:**
- Create em `ledger/`: `PlannedTemplate.java`, `PlannedTransactions.java`.
- Create em `ledger/internal/`: `PlannedTransactionService.java`, `ConfirmTransactionRequest.java`,
  `SkipTransactionRequest.java`.
- Modify: `ledger/internal/TransactionController.java`, `TransactionRepository.java`,
  `TransactionService.java` (extrair a checagem de criar para reuso).
- Test: `ledger/PlannedTransactionsIT.java`, `ledger/ConfirmSkipIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `TestLedger.insertPlanned` (Task 3).
- Produces:
  - `public record PlannedTemplate(TransactionKind kind, Money amount, UUID accountId, @Nullable UUID toAccountId, @Nullable UUID categoryId, @Nullable PaymentMethod paymentMethod, @Nullable String description)`;
  - a interface `PlannedTransactions`:

    ```java
    public interface PlannedTransactions {
        void check(WorkspaceMember actor, PlannedTemplate template);
        boolean plan(UUID workspaceId, UUID ruleId, LocalDate scheduledOn, PlannedTemplate template, UUID authorMembershipId);
        int dropOpenFrom(UUID workspaceId, UUID ruleId, LocalDate from);
    }
    ```

    - `check` aplica as regras de criar lançamento e lança as mesmas exceções e os mesmos códigos.
    - `plan` grava `PLANNED` com `occurred_on = scheduled_on`, `member_id = created_by = author`,
      `ON CONFLICT DO NOTHING`. Devolve `false` se a ocorrência já existia ou se a conta ou a
      categoria está arquivada.
    - `dropOpenFrom` apaga de vez as linhas com `status = 'PLANNED'`, `deleted_at is null` e
      `scheduled_on >= from`, e devolve quantas apagou.
  - rotas `POST /transactions/{id}/confirm` e `POST /transactions/{id}/skip` (D8).

**Como fica:**
- O confirmar faz um `UPDATE` condicional
  `... set status = 'CONFIRMED', member_id = :me, amount = ..., occurred_on = ..., account_id = ..., version = version + 1 where workspace_id = :ws and id = :id and deleted_at is null and status = 'PLANNED' and version = :version`.
- Se nenhuma linha mudou, a API relê a linha para escolher o erro da tabela da D8.
- O pular usa o mesmo caminho da exclusão de hoje, com a mesma checagem de status e versão.
- Para o Jovem, o previsto e a conta do ajuste passam por `AccountScope` como no `PATCH`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `PlannedTransactionsIT.planIsIdempotent` | `plan` duas vezes com a mesma regra e data → `true` e depois `false`; uma linha |
| `PlannedTransactionsIT.planSkipsArchivedCategory` | categoria arquivada → `false`, nenhuma linha |
| `PlannedTransactionsIT.checkRejectsLikeCreate` | moeda diferente da conta → `transaction.currency-mismatch`; transferência com categoria → `transaction.transfer-invalid` |
| `PlannedTransactionsIT.dropOpenFromKeepsConfirmedDeletedAndPast` | quatro previstos (passado aberto, futuro aberto, futuro confirmado, futuro pulado); `dropOpenFrom(hoje)` → apaga só o futuro aberto e devolve 1 |
| `ConfirmSkipIT.confirmAsIs` | `confirm {version: 0}` → 200; `status "CONFIRMED"`, `memberId` de quem confirmou, `createdBy` igual, `version 1`; saldo da conta muda |
| `ConfirmSkipIT.confirmWithAdjustments` | `amount` R$ 187,40, `occurredOn` 2 dias depois, outra conta BRL → gravados; `scheduledOn` igual ao original |
| `ConfirmSkipIT.confirmWithOtherCurrencyFails` | conta em USD → 422 `transaction.currency-mismatch` |
| `ConfirmSkipIT.confirmTwiceIsNotPlanned` | segunda confirmação com `version 1` → 409 `transaction.not-planned` |
| `ConfirmSkipIT.staleVersionConflicts` | `version 5` → 409 `transaction.version-conflict` |
| `ConfirmSkipIT.confirmAfterSkipIsNotFound` | pular e depois confirmar → 404 `transaction.not-found` |
| `ConfirmSkipIT.skipAfterConfirmIsNotPlanned` | confirmar e depois pular com a versão nova → 409 `transaction.not-planned` |
| `ConfirmSkipIT.skipThenRestore` | pular → some da lista; `restore` → volta como `PLANNED` |
| `ConfirmSkipIT.youthConfirmsOwnAccount` | previsto na conta do Jovem, regra do Adulto → Jovem confirma, 200 |
| `ConfirmSkipIT.youthCannotConfirmAdultPlanned` | previsto na conta do Adulto → Jovem recebe 404 |
| `ConfirmSkipIT.viewerCannot` | `VIEWER` → 403 nas duas rotas |
| `ConfirmSkipIT.moduleOffIs403` | módulo desligado → 403 `module.disabled` nas duas rotas |
| `RoleMatrixIT` | linhas de `confirm` e `skip` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*PlannedTransactionsIT' --tests '*ConfirmSkipIT'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Siga "Como fica" e D8. Regenere o contrato.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*TransactionEditIT' --tests '*RoleMatrixIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(ledger): confirm and skip planned transactions"
```

---

### Task 6: Regras de recorrência e o gerador

**Files:**
- Create em `planning/`: `RecurringRuleArchived.java`.
- Create em `planning/internal/`: `RecurringRuleController.java`, `RecurringRuleService.java`,
  `RecurringRuleRepository.java`, `RecurringRuleView.java`, `CreateRecurringRuleRequest.java`,
  `UpdateRecurringRuleRequest.java`, `RecurrenceGenerator.java`, `RecurringRuleNotFoundException.java`,
  `RecurringRuleConflictException.java`, `RecurringRuleRejectedException.java`.
- Modify: `workspace/Memberships.java`, `workspace/internal/JdbcMemberships.java`,
  `audit/internal/AuditListeners.java`, `audit/package-info.java` (`"planning"`).
- Test: `workspace/MembershipsIT.java`, `planning/RecurringRulesIT.java`,
  `planning/RecurrenceGeneratorIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `PlannedTransactions` e `PlannedTemplate` (Task 5), `RecurrenceSchedule` (Task 4).
- Produces:
  - `Memberships.activeMember(UUID workspaceId, UUID membershipId)` e
    `Memberships.activeOwner(UUID workspaceId)` (D12);
  - `CreateRecurringRuleRequest(@NotNull TransactionKind kind, @NotNull @TransactionAmount Money amount, @NotNull UUID accountId, @Nullable UUID toAccountId, @Nullable UUID categoryId, @Nullable PaymentMethod paymentMethod, @Nullable @Size(max = 140) String description, @NotNull Frequency frequency, @Min(1) @Max(12) int interval, @NotNull LocalDate startOn, @Nullable LocalDate endOn)`;
  - `UpdateRecurringRuleRequest`: os mesmos campos mais `@NotNull Long version`;
  - `RecurringRuleView(UUID id, TransactionKind kind, Money amount, UUID accountId, @Nullable UUID toAccountId, @Nullable UUID categoryId, @Nullable PaymentMethod paymentMethod, @Nullable String description, Frequency frequency, int interval, LocalDate startOn, @Nullable LocalDate endOn, @Nullable LocalDate nextDueOn, @Nullable UUID createdBy, long version)`;
  - `RecurrenceGenerator.generate(UUID workspaceId, UUID ruleId)` (D11), usado pelo serviço, pelo
    job e pelo ouvinte;
  - rotas `GET`, `POST`, `PATCH /{id}` e `POST /{id}/archive` (D9);
  - evento `RecurringRuleArchived(UUID workspaceId, UUID ruleId, UUID actorUserId)`.

**Como fica:**
- **Descrição.** A descrição passa pelo mesmo `cleanDescription` dos lançamentos. Se o `ledger`
  não expõe o método, a regra usa `strip()`, e vazio vira `null`.
- **Criar:** valida `end_on`, chama `check(actor, template)`, grava a regra com
  `next_due_on = start_on` e `created_by = actor.membershipId()`, e chama
  `generate(ws, id)`. Tudo numa transação só.
- **Editar:**
  1. faz o `UPDATE` com `version` e `next_due_on = start_on`;
  2. chama `dropOpenFrom(ws, id, hoje)`;
  3. chama `generate`.
- **Arquivar:** grava `archived_at`, chama `dropOpenFrom(ws, id, hoje)` e publica o evento. Na
  segunda vez não faz nada.
- **`generate`:**
  - lê a regra ativa e o autor (D12);
  - calcula "hoje" no fuso do autor e o intervalo da D11;
  - grava cada data com `plan`;
  - grava `next_due_on` com `firstAfter(..., fim do horizonte)`.
  - Sem autor (família sem Dono ativo), não gera nada.
- **Auditoria.** O `AuditListeners` grava `recurring_rule.archived`, alvo `RECURRING_RULE` e o
  `ruleId`. O javadoc do `AuditEntry` cita o alvo novo.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MembershipsIT.activeMemberAndOwner` | membro ativo → presente com o fuso da família; membro `REMOVED` → vazio; `activeOwner` → o Dono |
| `RecurringRulesIT.createGeneratesUntilEndOfNextMonth` | `TestClock` em 2026-11-23; mensal no dia 10, início 2026-01-10 → 201; previstos em 2026-11-10 e 2026-12-10, não em janeiro de 2026; `nextDueOn = 2027-01-10` |
| `RecurringRulesIT.endBeforeStartIs422` | `endOn < startOn` → 422 `recurring-rule.end-before-start` |
| `RecurringRulesIT.templateErrorsUseTransactionCodes` | conta de outra família → 422 `transaction.account-not-found` |
| `RecurringRulesIT.youthAndViewerCannotWrite` | `POST`, `PATCH` e `archive` como `YOUTH` e `VIEWER` → 403 |
| `RecurringRulesIT.youthSeesOnlyRulesOnOwnAccounts` | regra na conta do Adulto e regra na conta do Jovem → o `GET` do Jovem traz só a segunda |
| `RecurringRulesIT.editKeepsConfirmedAndSkipped` | regra mensal; confirmar a de novembro com outra data; pular a de dezembro; editar o valor → novembro e dezembro não voltam como `PLANNED`; meses seguintes com o valor novo |
| `RecurringRulesIT.editReplacesOpenFuture` | previsto aberto de dezembro com R$ 100; editar para R$ 120 → um só previsto em dezembro, com R$ 120 |
| `RecurringRulesIT.editKeepsOverdue` | previsto aberto vencido de ontem → continua com o valor antigo depois da edição |
| `RecurringRulesIT.staleVersionConflicts` | `PATCH` com versão velha → 409 `recurring-rule.version-conflict` |
| `RecurringRulesIT.archiveDropsFutureAndAudits` | arquivar → previstos futuros abertos somem, o vencido fica; `audit_events` com `recurring_rule.archived`; segunda vez → 204 sem novo evento; `GET` sem a regra; `PATCH` → 404 `recurring-rule.not-found` |
| `RecurrenceGeneratorIT.authorFallsBackToOwner` | autor removido da família → próximo `generate` grava `created_by` e `member_id` do Dono |
| `RecurrenceGeneratorIT.runningTwiceCreatesNothing` | `generate` duas vezes → mesma contagem de linhas |
| `RecurrenceGeneratorIT.endOnMidHorizonStops` | `endOn` em 2026-12-01 → só novembro; `nextDueOn = null` |
| `RoleMatrixIT` | linhas das quatro rotas de regras |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*MembershipsIT' --tests '*RecurringRulesIT' --tests '*RecurrenceGeneratorIT'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Siga "Como fica", D9, D11 e D12. Regenere o contrato.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*ApplicationModulesTest' --tests '*RoleMatrixIT' --tests '*AuditIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(planning): add recurring rules that generate planned transactions"
```

---

### Task 7: Job diário e o módulo desligado

**Files:**
- Create em `planning/internal/`: `RecurrenceJob.java`, `PlanningModuleListener.java`.
- Test: `planning/RecurrenceJobIT.java`, `planning/ModuleOffIT.java`.

**Interfaces:**
- Consumes: `RecurrenceGenerator.generate` (Task 6), `Modules`, `ModuleToggled`.
- Produces:
  - `RecurrenceJob.run()`, público para os testes chamarem direto (o `SchedulingConfig` fica
    desligado nos testes);
  - `PlanningModuleListener.on(ModuleToggled event)`.

**Como fica:**
- **Job:** `@Scheduled(cron = "0 10 4 * * *", zone = "America/Sao_Paulo")`. Ele busca as famílias
  com regras ativas e `next_due_on` não nulo, e pula as que estão com o módulo desligado. Cada
  família roda num `TransactionTemplate` próprio, com todas as regras dela. Se uma família falha, o
  job loga `log.warn("recurrence job failed for a family: {}", failure.getClass().getName())` e
  segue para a próxima.
- **Ouvinte:** um `@EventListener` síncrono. Com `key == PLANNING` e `enabled`, gera as regras
  ativas daquela família na mesma transação do toggle.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `RecurrenceJobIT.jobExtendsTheHorizon` | regra criada em 2026-11-23 (gera até 31/12); `TestClock` em 2026-12-02; `run()` → previsto de janeiro de 2027 criado |
| `RecurrenceJobIT.jobTwiceIsIdempotent` | `run()` duas vezes → mesma contagem |
| `RecurrenceJobIT.oneFamilyFailingDoesNotStopOthers` | família A com regra cuja conta foi apagada à força no teste; família B normal → B gera |
| `ModuleOffIT.jobSkipsFamilyWithModuleOff` | módulo desligado; relógio avança um mês; `run()` → nada novo |
| `ModuleOffIT.turningOnFillsTheHorizon` | religar pelo `PUT` de módulos → previstos até o fim do mês seguinte na hora |
| `ModuleOffIT.routesAre403` | `GET` e `POST` de regras, `confirm` e `skip` → 403 `module.disabled` |
| `ModuleOffIT.nothingIsDeleted` | desligar e religar → regras e previstos de antes continuam |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*RecurrenceJobIT' --tests '*ModuleOffIT'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Siga "Como fica" e D13.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(planning): run the daily recurrence job and follow the module switch"
```

---

### Task 8: Exportação v3

**Files:**
- Create em `ledger/`: `ExportedTag.java`.
- Create em `planning/`: `PlanningExport.java`, `ExportedRecurringRule.java`.
- Create em `planning/internal/`: `PlanningJdbcExport.java`.
- Modify: `ledger/ExportedTransaction.java`, `ledger/LedgerExport.java`,
  `ledger/internal/LedgerJdbcExport.java`, `privacy/internal/ExportService.java`,
  `privacy/package-info.java` (`"planning"`).
- Test: os ITs de exportação que já existem em `privacy/` (família e pessoal).

**Interfaces:**
- Produces:
  - `ExportedTransaction` ganha `String status`, `@Nullable PaymentMethod paymentMethod`,
    `List<String> tags`, `@Nullable LocalDate scheduledOn` e `@Nullable UUID recurringRuleId`;
  - `ExportedTag(UUID id, String name, @Nullable UUID createdBy, Instant createdAt, @Nullable Instant archivedAt)`;
  - `LedgerExport.tags(UUID workspaceId)` e `LedgerExport.tagsCreatedBy(UUID workspaceId, UUID membershipId)`;
  - `ExportedRecurringRule`, com os campos da `RecurringRuleView` mais `createdAt` e `archivedAt`;
  - `PlanningExport.rules(UUID workspaceId)`, com as arquivadas.

**Como fica:**
- `FORMAT_VERSION = 3`.
- **Família:**
  - o JSON ganha `recurringRules` e `tags`;
  - `workspace` ganha `country` e `locale`;
  - cada lançamento leva os campos novos;
  - o CSV `lancamentos.csv` ganha no fim `status`, `forma_pagamento` e `tags`, com os nomes
    separados por `; `;
  - entram os CSVs `recorrencias.csv` (`tipo`, `valor`, `moeda`, `conta`, `categoria`,
    `forma_pagamento`, `descricao`, `frequencia`, `intervalo`, `inicio`, `fim`, `arquivada_em`) e
    `tags.csv` (`nome`, `arquivada_em`).
- **Pessoal:** o JSON ganha `tags` com `tagsCreatedBy`. Os lançamentos levam os campos novos.
- Lançamentos excluídos continuam fora, inclusive os previstos pulados.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `FamilyExportIT.formatVersion3WithPlanningData` | família com uma regra, um previsto, um gasto `PIX` com tag `Escola` → `formatVersion 3`, `workspace.country "BR"`, `workspace.locale "pt-BR"`, `recurringRules` com 1, `tags` com 1, o gasto com `paymentMethod "PIX"` e `tags ["Escola"]`, o previsto com `status "PLANNED"` |
| `FamilyExportIT.csvHasNewColumnsAndFiles` | zip com `recorrencias.csv` e `tags.csv`; cabeçalho de `lancamentos.csv` termina em `status,forma_pagamento,tags` |
| `FamilyExportIT.skippedPlannedStaysOut` | previsto pulado → ausente |
| `PersonalExportIT.tagsCreatedByThePerson` | tag criada pelo Adulto e tag criada pelo Dono → a exportação pessoal do Adulto traz só a dele |

Use os nomes de classe dos ITs de exportação que já existem; os nomes acima são os dos métodos.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*Export*IT'`
Expected: FAIL (`formatVersion` 2).

- [ ] **Step 3: Implementar.** Siga "Como fica" e D17.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*ApplicationModulesTest'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(privacy): export rules, tags, payment method and country (format 3)"
```

---

### Task 9: Nova versão da política de privacidade, sem novo aceite

**Files:**
- Modify: `privacy/internal/LegalDocuments.java`, `DocumentStatus.java`, `DocumentService.java`.
- Create: `web/src/features/legal/texts/privacy-2026-11.1.ts`, `privacy-2026-12.1.ts`,
  `web/src/features/legal/PolicyUpdatedNotice.tsx`.
- Modify: `web/src/features/legal/legalConfig.ts`, `useDocuments.ts`, `legalTexts.test.ts`,
  `messages.pt-BR.ts`, `DocumentsGate.test.tsx`, `PrivacyPolicyPage.test.tsx`,
  `web/src/mocks/data.ts`, o layout que mostra avisos (onde o `DocumentsGate` mora).
- Test: o IT de documentos que já existe em `privacy/`, mais `PolicyUpdatedNotice.test.tsx` e
  `useDocuments.test.ts`.

**Interfaces:**
- Produces:
  - `DocumentStatus` ganha `boolean acceptanceRequired`;
  - `LegalDocuments.KEEPS_ACCEPTANCE` como `Map<String, String>`;
  - `useDocuments` passa a usar `acceptanceRequired`.

**Como fica:**
- **Versões.** `WITHOUT_BILLING` passa a `terms-2026-11` com `privacy-2026-11.1`, e `WITH_BILLING`
  a `terms-2026-12` com `privacy-2026-12.1`. Os termos não mudam.
- **Aceite.** `acceptanceRequired` é falso quando `acceptedVersion` é igual à
  `currentVersion` ou a `KEEPS_ACCEPTANCE.get(currentVersion)`. Nos outros casos é verdadeiro.
- **Aceitar.** O aceite continua exigindo a versão atual (`.1`). O web manda a `currentVersion`,
  como hoje.
- **Textos.** Os textos `.1` são cópia dos de origem com duas mudanças:
  - o item de dados financeiros passa a citar "tags, forma de pagamento, recorrências e lançamentos
    previstos";
  - a data de atualização muda.
- **Aviso.** O `PolicyUpdatedNotice` aparece quando a política aceita difere da atual e
  `acceptanceRequired` é falso. O texto é "Atualizamos a política de privacidade.", com o link "Ler"
  e o botão "Fechar". Ao fechar, grava `liberdade.policyNotice.<versão>` no `localStorage` dentro de
  `try/catch`. Sem `localStorage`, o aviso fecha só naquela sessão.
- **Teste de versões.** No `legalTexts.test.ts`, a regex vira
  `/"((?:terms|privacy)-\d{4}-\d{2}(?:\.\d+)?)"/g`, as chaves esperadas ganham as duas `.1`, e o
  `latest` vira `{ terms: 'terms-2026-12', privacy: 'privacy-2026-12.1' }`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `DocumentsIT.oldAcceptanceStillCounts` | quem aceitou `privacy-2026-11` → `currentVersion "privacy-2026-11.1"`, `acceptanceRequired false` |
| `DocumentsIT.neverAcceptedRequires` | sem aceite → `acceptanceRequired true` |
| `DocumentsIT.acceptTakesNewVersion` | aceitar com `privacy-2026-11.1` → 204; com `privacy-2026-11` → 409 `documents.version-outdated` |
| `useDocuments.test.ts` | `acceptanceRequired false` → o gate não bloqueia |
| `PolicyUpdatedNotice.test.tsx` | mostra o aviso quando a versão aceita é a antiga; "Fechar" esconde e grava a chave; com `localStorage` lançando erro, fecha sem quebrar |
| `legalTexts.test.ts` | embute as seis versões e o `latest` novo |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*Documents*IT'` e (na raiz) `corepack pnpm --dir web test -- legal`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica" e D18. Regenere o contrato.

- [ ] **Step 4: Rodar e ver passar.** Mesmos comandos. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` e `corepack pnpm --dir web check` (ou o script de CI
  do web: lint, typecheck, test e build) → verdes.

- [ ] **Step 6: Commit**

```bash
git add api web
git commit -m "feat(privacy): publish the privacy policy update without a new acceptance"
```

---

### Task 10: Dinheiro e idioma da família no web

**Files:**
- Create: `web/src/shared/api/useFormatMoney.ts`, `web/src/shared/i18n/localeLiterals.test.ts`.
- Modify: `web/src/shared/money/money.ts` e o teste, `web/src/app/providers.tsx`,
  `web/src/shared/i18n/index.ts`, `features/dashboard/charts.ts`, e todos os chamadores de
  `formatBRL`: `AccountsPage`, `BalanceInput`, `DashboardPage`, `HomePage`,
  `TransactionDetailSheet`, `TransactionForm`, `TransactionRow`, `VersionConflictDialog`. Também
  `portraitData` e `MonthSwitcher`.

**Interfaces:**
- Consumes: `MeWorkspace.locale` e `MeWorkspace.currency` (Task 1, já no `schema.ts`).
- Produces:
  - `export function formatMoney(cents: bigint, currency: string, locale: string): string` em
    `money.ts`;
  - `export function useFormatMoney(): (cents: bigint) => string` e
    `export function useLocale(): string` em `useFormatMoney.ts`;
  - `charts.ts` recebe `format: (cents: bigint) => string` nas funções que hoje chamam
    `formatBRL`.

**Como fica:**
- `formatMoney` usa `Intl.NumberFormat(locale, { style: 'currency', currency })`, guardado num `Map`
  por `locale|currency`. O caminho com `bigint` que existe hoje continua.
- `useFormatMoney` lê `useCurrentWorkspace()`. Sem família, cai em `'BRL'` e `'pt-BR'`, e essas são
  as únicas constantes, em `money.ts`, como `DEFAULT_CURRENCY` e `DEFAULT_LOCALE`.
- O `IntlProvider` recebe `locale` de `useLocale()`.
- O `localeLiterals.test.ts` lê `import.meta.glob('/src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true })`.
  - Ficam de fora os `*.test.*`, `src/mocks/**` e `shared/api/schema.ts`.
  - O teste falha se algum outro arquivo tiver `'pt-BR'`, `"pt-BR"`, `'BRL'` ou `"BRL"`.
  - As exceções são `shared/i18n/index.ts` e `shared/money/money.ts`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `money.test.ts` `formatMoney` | `formatMoney(123456n, 'BRL', 'pt-BR')` → `R$ 1.234,56` (com o espaço que o `Intl` usa; compare normalizando ` `); `formatMoney(-500n, 'BRL', 'pt-BR')` → `-R$ 5,00` |
| `useFormatMoney.test.tsx` | com a família em `pt-BR`/`BRL` → `R$ 10,00`; sem família → o mesmo |
| `localeLiterals.test.ts` | lista vazia de arquivos fora da regra |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- money useFormatMoney localeLiterals`
Expected: FAIL (`formatMoney` não existe; o guarda lista os arquivos de hoje).

- [ ] **Step 3: Implementar.** Troque cada `formatBRL` por `useFormatMoney()` nos componentes e pelo
  parâmetro `format` em funções puras. Remova o `formatBRL`.

- [ ] **Step 4: Rodar e ver passar.** `corepack pnpm --dir web test`. Expected: PASS em tudo.

- [ ] **Step 5: Conferir.** Lint, typecheck, build e `check:chunks` do web → verdes, com o inicial
  ≤ 200 KB.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "refactor(web): format money and dates with the family locale"
```

---

### Task 11: Forma de pagamento e tags no formulário e nos filtros

**Files:**
- Create em `web/src/features/transactions/`: `PaymentMethodChips.tsx`, `TagInput.tsx` e os testes.
- Create: `web/src/shared/api/tags.ts` (`useTags`, `useRenameTag`, `useArchiveTag`).
- Modify: `TransactionForm.tsx`, `TransactionFilters.tsx`, `TransactionsPage.tsx`,
  `TransactionRow.tsx`, `TransactionDetailSheet.tsx`, `messages.pt-BR.ts`,
  `features/dashboard/DashboardPage.tsx`, `shared/api/types.ts` (`TransactionFilters` com
  `tagId?` e `paymentMethod?`), `hooks.ts` (`useDashboard(ws, month, paymentMethod?)`),
  `endpoints.ts`, `mutations.ts`, `mocks/handlers.ts`, `mocks/data.ts`.

**Interfaces:**
- Consumes: `MeWorkspace.paymentMethods` (Task 1), as rotas de tags (Task 2).
- Produces:
  - `PaymentMethodChips({ value, onChange, methods }: { value: PaymentMethod | null; onChange: (m: PaymentMethod | null) => void; methods: PaymentMethod[] })`;
  - `TagInput({ value, onChange }: { value: string[]; onChange: (names: string[]) => void })`;
  - `useTags(ws: string)`.

**Como fica:**
- **Chips.** Os chips mostram os rótulos Pix, Crédito, Débito, Boleto, TED e Dinheiro, na ordem de
  `paymentMethods`. Tocar no chip marcado desmarca. Em transferência, os chips somem e o valor vai
  `null`.
- **Campo de tags.** Sugere as tags de `useTags` que começam com o texto, sem diferenciar
  maiúsculas. Enter ou vírgula adiciona. No 5º nome, o campo para de aceitar e mostra "Até 5 tags".
  Cada tag tem um botão "Remover tag X".
- **Envio.** O formulário sempre manda `paymentMethod` e `tags`. A fila offline guarda os dois.
- **Linha e detalhe.** A linha mostra até 2 tags e o rótulo da forma de pagamento. O detalhe mostra
  tudo.
- **Filtros.** O `TransactionFilters` ganha "Tag" e "Forma de pagamento". O painel ganha um
  `<select>` nativo com o rótulo "Forma de pagamento" e a opção "Todas as formas".

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `PaymentMethodChips.test.tsx` | seis botões com `aria-pressed`; tocar em Pix → `onChange('PIX')`; tocar de novo → `onChange(null)` |
| `TagInput.test.tsx` | digitar "esc" sugere "Escola"; Enter adiciona; o 6º nome é recusado com "Até 5 tags"; "Remover tag Escola" tira |
| `TransactionForm.test.tsx` (novos casos) | gasto com Pix e tag "Escola" → corpo com `paymentMethod: 'PIX'`, `tags: ['Escola']`; trocar para transferência esconde os chips e manda `paymentMethod: null` |
| `TransactionFilters.test.tsx` (novos casos) | escolher a tag e a forma → a query leva `tagId` e `paymentMethod` |
| `DashboardPage.test.tsx` (novo caso) | escolher "Dinheiro" → o painel é buscado com `paymentMethod=CASH` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- PaymentMethodChips TagInput TransactionForm TransactionFilters DashboardPage`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar.** `corepack pnpm --dir web test`. Expected: PASS.

- [ ] **Step 5: Conferir.** Lint, typecheck, build e `check:chunks` → verdes.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): pick a payment method and tags, and filter by them"
```

---

### Task 12: Recorrências e Previstos no web

**Files:**
- Create em `web/src/features/recurrences/`: `RecurrencesPage.tsx`, `RuleForm.tsx`, `RuleSheet.tsx`,
  `ruleText.ts`, `messages.pt-BR.ts`, `register.ts` e os testes.
- Create em `web/src/features/transactions/`: `PlannedSection.tsx`, `PlannedSheet.tsx` e os testes.
- Create: `web/src/shared/api/recurrences.ts` (`useRules`, `useCreateRule`, `useUpdateRule`,
  `useArchiveRule`, `useConfirmPlanned`, `useSkipPlanned`).
- Modify: `TransactionsPage.tsx`, `listItems.ts` (previstos fora dos grupos por dia),
  `EditTransaction.tsx` (previsto abre o `PlannedSheet`), `app/routes.tsx`, `app/MorePage.tsx`,
  `eslint/boundaries.js`, `mocks/handlers.ts`, `mocks/data.ts`.

**Interfaces:**
- Consumes: rotas de regras (Task 6), `confirm` e `skip` (Task 5), `useFormatMoney` (Task 10),
  `PaymentMethodChips` (Task 11), `registerModule` do registro de módulos.
- Produces:
  - `registerModule({ key: 'planning', available: true })`;
  - a rota preguiçosa `/mais/recorrencias`;
  - `ruleText(rule): string`, por exemplo "Todo mês, dia 10", "A cada 2 semanas, segunda" ou
    "Todo ano, 29/02".

**Como fica:**
- **Recorrências.** A tela lista as regras com descrição ou categoria, valor, `ruleText` e "próximo:
  dd/mm".
  - Dono e Adulto têm "Nova recorrência", e tocar numa regra abre a edição. A edição tem "Arquivar
    recorrência", que confirma antes.
  - Jovem e Leitor só veem a lista.
  - O `RuleForm` reaproveita os campos do formulário de lançamento: tipo, valor, conta, destino,
    categoria, forma de pagamento e descrição. Depois vêm "Repete" (Toda semana, Todo mês, Todo
    ano), "A cada" (1 a 12), "Começa em" e "Termina em" (opcional).
- **Entrada em Mais.** "Recorrências" aparece só com o módulo ligado.
- **Previstos.** No topo do mês, a lista de lançamentos ganha a seção "Previstos", com os `PLANNED`
  ordenados por `scheduledOn`.
  - Cada linha mostra data, descrição ou categoria, valor e o botão "Confirmar".
  - Quando `scheduledOn` é anterior a hoje, a linha mostra "venceu em dd/mm".
  - Tocar na linha abre o `PlannedSheet`: valor, data e conta editáveis, mais "Confirmar" e
    "Pular".
  - Pular mostra o snackbar "Previsto pulado", com "Desfazer" (chama o `restore`).
- **Sem rede.** Os botões ficam desativados com a explicação que já existe para editar offline.
- **Erros.** Um 404 ou 409 mostra "Esse previsto mudou. Confira a lista." e busca a lista de novo.
- **Lista e saldos.** Os previstos nunca entram nos grupos por dia nem nos totais da tela.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ruleText.test.ts` | mensal 1 no dia 10 → "Todo mês, dia 10"; semanal 2 numa segunda → "A cada 2 semanas, segunda"; anual 29/02 → "Todo ano, 29/02" |
| `RecurrencesPage.test.tsx` | Dono vê "Nova recorrência"; Jovem não vê; criar manda o corpo da Task 6; arquivar pede confirmação |
| `PlannedSection.test.tsx` | previsto de ontem mostra "venceu em dd/mm"; "Confirmar" manda `{ version }`; offline o botão fica desativado |
| `PlannedSheet.test.tsx` | mudar o valor e confirmar manda `amount`; "Pular" mostra "Previsto pulado" e "Desfazer" chama `restore`; resposta 409 mostra "Esse previsto mudou. Confira a lista." |
| `TransactionsPage.test.tsx` (novo caso) | previsto não entra no grupo do dia nem no total do mês |
| `MorePage.test.tsx` (novo caso) | "Recorrências" com o módulo ligado; some com ele desligado |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- ruleText RecurrencesPage PlannedSection PlannedSheet TransactionsPage MorePage`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar.** `corepack pnpm --dir web test`. Expected: PASS.

- [ ] **Step 5: Conferir.** Lint, typecheck, build e `check:chunks` → verdes, com a tela de
  Recorrências num chunk próprio.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the recurrences screen and the planned section"
```

---

### Task 13: Aba Tags em Categorias

**Files:**
- Create: `web/src/features/categories/TagsTab.tsx` e o teste.
- Modify: `CategoriesPage.tsx`, `CategoryTabs.tsx`, `messages.pt-BR.ts` de categorias.

**Interfaces:**
- Consumes: `useTags`, `useRenameTag`, `useArchiveTag` (Task 11).

**Como fica:**
- `CategoryTabs` ganha a aba "Tags" ao lado de Gastos e Receitas, com o conteúdo carregado sob
  demanda.
- A aba lista as tags ativas em ordem alfabética.
- Dono e Adulto têm "Renomear" (folha com o campo "Nome") e "Arquivar", que confirma: "As tags
  arquivadas continuam nos lançamentos antigos."
- O 409 `tag.name-taken` mostra "Já existe uma tag com esse nome."
- Jovem e Leitor veem a lista sem ações.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `TagsTab.test.tsx` | lista ordenada; Dono renomeia e manda `{ name }`; 409 mostra "Já existe uma tag com esse nome."; arquivar pede confirmação; Leitor não vê "Renomear" |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- TagsTab CategoriesPage`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar.** `corepack pnpm --dir web test`. Expected: PASS.

- [ ] **Step 5: Conferir.** Lint, typecheck, build e `check:chunks` → verdes.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): rename and archive tags in categories"
```

---

### Task 14: E2E, acessibilidade e docs

**Files:**
- Create: `web/e2e/recurrences.spec.ts`.
- Modify: `web/e2e/a11y.spec.ts`, `web/e2e/support.ts` (helpers `createRule` e
  `addTagAndMethod`), `web/e2e/core-without-modules.spec.ts`, `docs/data-model.md`,
  `docs/architecture.md`.

**Interfaces:**
- Consumes: tudo das tasks anteriores.

**Como fica:**
- **`recurrences.spec.ts`:**
  1. o Dono cria a família e uma conta;
  2. em Mais > Recorrências, cria "Aluguel", R$ 1.500,00, todo mês no dia de hoje;
  3. na lista de lançamentos, o previsto aparece em "Previstos" e o saldo da conta não muda;
  4. "Confirmar" tira o previsto da seção, põe o lançamento no dia e o saldo cai R$ 1.500,00;
  5. um segundo cenário pula o previsto do mês seguinte e desfaz.
  - Um terceiro teste lança um gasto com Pix e a tag "Escola", filtra pela tag e vê só ele.
- **`a11y.spec.ts`:** ganha a tela de Recorrências com uma regra, a seção Previstos e a aba Tags.
- **`core-without-modules.spec.ts`:** acrescenta que Mais não mostra "Recorrências" e que a lista
  não tem "Previstos". O resto não muda e precisa continuar verde.
- **Docs:**
  - o `data-model.md` ganha as tabelas e colunas da V15 e da V16;
  - o `architecture.md` ganha o módulo `planning` e o job das 4h10.

- [ ] **Step 1: Escrever os testes (falhando).** Os três cenários e as checagens de a11y acima.

- [ ] **Step 2: Rodar e ver falhar.** O e2e de sempre (compose de e2e e Playwright), só com
  `recurrences` e `a11y`. Expected: FAIL antes dos helpers.

- [ ] **Step 3: Implementar.** Escreva os helpers e ajuste os seletores pelo nome acessível.

- [ ] **Step 4: Rodar e ver passar.** O e2e completo. Expected: PASS, com o S2 verde.

- [ ] **Step 5: Conferir.** `./gradlew build`, as checagens do web e o e2e completo → verdes.

- [ ] **Step 6: Commit**

```bash
git add web/e2e docs
git commit -m "test(e2e): cover recurrences, tags and payment method; document the 7.1 model"
```

---

## Depois das tasks

1. **Revisão final:** a branch inteira passa por uma revisão nova, no modelo mais capaz. A revisão
   confere os cinco pontos do Review Focus, a regra do Jovem em cada rota nova e a ausência de
   `PLANNED` em qualquer soma.
2. **PR 2:** abrir com `feat: add payment method, tags and recurrences (Plan 7.1)`. O corpo leva o
   resumo em PT-BR, o checklist da seção 8 do spec para o staging e as linhas de atribuição.
3. **Depois do merge:** o mantenedor confere a saída da 7.1 no staging. Um PR de docs registra o
   resultado no README dos planos, e então o plano 7.2 é escrito.
