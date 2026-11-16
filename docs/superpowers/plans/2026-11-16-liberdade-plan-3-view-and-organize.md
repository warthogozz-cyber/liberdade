# Liberdade · Plano 3: Ver e organizar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A família registra receitas, gastos e transferências, organiza contas e categorias,
corrige e exclui lançamentos com "Desfazer" e vê o mês num painel com saldo e três gráficos. O
exemplo da seção 7.1 do PRD aparece correto no painel, e a cobertura do domínio fica em 90% ou mais
(S4). Cobre as US-04, US-06, US-07, US-08 e US-13.

**Architecture:** O módulo `ledger` da API ganha a gestão de contas e categorias, transferências,
edição com lock otimista, exclusão lógica com restauração e o painel. As regras puras vão para um
pacote novo, `ledger.domain` (`TransactionKind`, `TransactionDateRange`, `BalanceCalculator` e
`CategoryRanking`), coberto a 90% pelo gate do JaCoCo que já existe para `*.domain*` e por testes de
propriedade. O SQL só agrega por conta, por categoria e por dia. O CORS passa a aceitar `PATCH` e
`DELETE`. O `identity` passa a guardar na sessão um principal mínimo, só com o id da pessoa, e o
cookie ganha `Max-Age`. No web, as rotas fora do caminho do primeiro gasto passam a carregar sob
demanda, o CI ganha um orçamento de tamanho de chunk e o painel desenha os gráficos com ECharts,
carregado só nessa tela.

**Tech Stack:**
- API: Spring Boot 4.1.2, Spring Modulith, `JdbcClient`, Flyway, PostgreSQL 18, springdoc 3.2.0
  (sem novas dependências).
- Web: React 19, TanStack Query, Dexie, Radix, react-intl e, novo, `echarts` 6.1.0 (Apache-2.0,
  publicado em 2026-05-19; dependências `zrender` 6.1.0, BSD-3-Clause, e `tslib` 2.3.0, 0BSD).
- Ferramentas: Playwright e @axe-core/playwright, que já existem.

**Spec:**
- [PRD](../../prd.md) (US-04, US-06, US-07, US-08, US-13 e seções 7.1, 8 e 9)
- [Arquitetura](../../architecture.md) (seção 6, rotas)
- [Modelo de dados](../../data-model.md)
- [Design system](../../design-system.md) (gráficos, `AmountText`, `Toast`, `CategoryChip`)
- [Wireframes](../../wireframes.md) (telas 3, 4, 5 e 6)
- [Roadmap](../../roadmap.md) (v0.1 Núcleo)
- [ADRs](../../adr/README.md), em especial 0006 (arredondamento), 0007 (offline), 0008
  (isolamento), 0011 (gráficos), 0012 (`JdbcClient`) e 0013 (testes de propriedade)

## Global Constraints

- **Idiomas:** comunicação, UI, documentação de usuário e textos de PR em português do Brasil.
  Código, commits, nomes técnicos e comentários de código em inglês.
- **Commits e títulos de PR:** Conventional Commits.
  - Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Descrições de PR terminam com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Dados pessoais:** nenhum dado pessoal real (nome, cidade, e-mail) em arquivo versionado. Testes
  usam só nomes inventados ("Ana Teste") e domínios `example.com`.
  - `PROMPT_BIG_BRAIN.md` nunca é commitado.
  - Antes de cada commit, `git grep -nIiE -f .git/info/personal-data-patterns` não retorna nada
    (código de saída 1), e o mesmo vale com `--untracked`. O arquivo de padrões só existe na
    máquina do mantenedor e nunca é impresso.
- **Segredos:** nada de segredo em arquivo. Quem digita token é o mantenedor.
- **Senhas:** o app nunca pede nem guarda senha.
- **Sessão (ADR-0005):** cookie `__Host-sid` (HttpOnly, Secure, SameSite=Lax). Sessão anônima
  expira em 30 min sem uso; sessão autenticada, em 30 dias sem uso. Todo POST, PATCH e DELETE exige
  CSRF.
- **Minimização (LGPD):** a sessão guarda só o id da pessoa e o que o fluxo de entrada precisa
  enquanto dura. Nenhum id_token, e-mail ou nome fica na sessão (Task 1).
- **Isolamento (ADR-0008):** toda rota sob `/v1/workspaces/{ws}` tem `@WorkspaceScoped`, que nega
  por padrão. Todo método de repositório recebe `workspaceId`. FKs entre tabelas da família são
  compostas (`workspace_id`, `id`). O `WorkspaceGuardCoverageIT` continua passando.
- **Papéis:** leitura com `allow = {OWNER, ADULT, VIEWER}`; escrita com `allow = {OWNER, ADULT}`.
  YOUTH continua negado em tudo até o Plano 4 (D13 do Plano 2).
- **Acesso a dados (ADR-0012):** `JdbcClient`, sem JPA.
- **Dinheiro:** nunca em ponto flutuante. `BigDecimal` dentro de `Money` na API; texto no JSON
  (`{"amount":"1234.56","currency":"BRL"}`); `bigint` em centavos no web. Arredondamento
  `HALF_EVEN` só na exibição (ADR-0006). Saldos são calculados, nunca guardados.
- **Erros da API:** RFC 9457, com `code` estável e `detail` em pt-BR. Nunca expõem stack trace,
  nome de classe nem mensagem de exceção.
- **Valores fora do domínio:** mensagens de exceção e logs nunca contêm valores, saldos, nomes de
  conta ou categoria nem descrições (PRD §9).
- **Fronteiras de módulo:** Spring Modulith `verify()` na API e ESLint no web. `ledger` depende só
  de `workspace` e `shared`.
- **Contrato:** toda mudança de rota ou DTO regenera `api/openapi.json` e
  `web/src/shared/api/schema.ts` (`corepack pnpm --dir web gen:api`) no mesmo commit.
- **Cobertura:** domínio e `shared` ≥ 90% de linhas; geral ≥ 80%; no web, `src/shared/**` com o
  gate atual. O build falha abaixo disso.
- **Testes de propriedade (ADR-0013):** `@ParameterizedTest` com `@MethodSource`, semente
  `20261102L`, 1000 casos.
- **Desempenho (PRD §9):** API com p95 abaixo de 300 ms; primeira carga abaixo de 3 s em 4G. O
  orçamento de chunks da Task 6 protege a segunda meta.
- **Acessibilidade:** WCAG 2.2 AA; alvos de toque de 44 px ou mais; cor nunca sozinha (sinal, ícone
  ou texto junto); `prefers-reduced-motion` respeitado; todo gráfico tem tabela equivalente.
- **Offline (ADR-0007):** só criar funciona offline. Editar, excluir, restaurar e gerir contas e
  categorias ficam desabilitados sem conexão, com explicação.
- **GitHub Actions:** actions fixadas por SHA de 40 caracteres com comentário `# vX.Y.Z`;
  `permissions: {}` no topo e só o necessário por job.
- **Dependências:** só com licença compatível com AGPL-3.0 e publicadas em 2026-10-26 ou antes
  (`minimumReleaseAge` e o corte do lockfile). Exceções só com aprovação do mantenedor no chat.
- **Terceiros:** nenhuma chamada a serviço de terceiros além do login com Google. O ECharts vai no
  bundle, nunca por CDN.
- **Versões:** as pesquisadas em 2026-11-16. Registre no PR 2 toda versão escolhida e todo desvio.
- **Ações externas:** push, PR, merge, labels e mudanças de configuração exigem confirmação do
  mantenedor na hora.

### Pré-requisitos locais

Os mesmos do Plano 2: Git, `gh` autenticado, Java 21+ para o Gradle (a toolchain baixa o JDK 25),
Node 24 com corepack (`corepack pnpm …`), Docker Desktop em execução para Testcontainers e E2E, e
o Chromium do Playwright instalado.

## Entrega

- **PR 1** (docs, branch `docs/plan-3`): sem código. Leva:
  - este plano;
  - o README dos planos, com o Plano 3 "Em execução" e com link (o Plano 2 já está "Concluído");
  - no modelo de dados, a coluna `memberships.getting_started_dismissed_at` (D15) e a chave única
    do nome da conta (D6);
  - na arquitetura (seção 6), as rotas novas que o plano decide: `POST …/accounts/{id}/unarchive`,
    `POST …/categories/{id}/unarchive` e `POST /v1/me/getting-started/dismiss`.
- **PR 2** (código, branch `feat/view-and-organize`): Tasks 1 a 13, um commit por task.
  - O merge só acontece com os checks obrigatórios verdes.
  - A descrição registra as versões, os desvios e a medição do cookie no iOS (Task 13).
- O Plano 3 vira "Concluído" no README dos planos no primeiro PR do Plano 4.

### Itens herdados do Plano 2

| Item | Onde |
|---|---|
| Orçamento de tamanho de chunk no CI, com `lazy()` em `/mais` e `/lancamentos` | Task 6 |
| Principal mínimo na sessão, sem id_token, e-mail e nome (LGPD) | Task 1 |
| Limite para datas futuras | Task 4 (API) e Task 7 (web) |
| `Max-Age` do cookie de sessão, medido no iOS | Task 1 (código) e Task 13 (medição) |
| "Desfazer" de um gasto que já saiu da fila (D34 do Plano 2) | Task 7 |
| Plano 2 "Concluído" no README dos planos | PR 1 (conferido: já está) |

## Decisões deste plano

O spec deixa estes pontos em aberto, e o plano decide:

- **D1.** O painel é a rota `/painel`. Chega-se a ele pelo card do mês no Início ("Ver painel do
  mês") e pelo Mais. A navegação inferior continua como no Plano 2 (D4); a aba "Planejar" do
  wireframe é da v0.2.
- **D2.** Datas de lançamento valem de `2000-01-01` até hoje + 366 dias, no fuso da família. Fora
  disso, 422 `transaction.date-out-of-range`. O web aplica os mesmos limites no seletor de data, e
  a troca de mês no painel e em Lançamentos vai de janeiro de 2000 até o mês de hoje + 366 dias.
- **D3.** Dois saldos, com regras diferentes:
  - o saldo de cada conta (`GET /accounts`) soma todos os lançamentos não excluídos, de qualquer
    data, como no Plano 2;
  - o `balance` do painel é o saldo no fim do mês escolhido: soma só os lançamentos com
    `occurred_on` até o último dia do mês, das contas não arquivadas. O último ponto da linha
    diária é igual a ele. No exemplo da seção 7.1, os dois dão R$ 4.765,44.
- **D4.** Conta arquivada sai do saldo total e da linha diária. Os lançamentos dela continuam nas
  receitas, nos gastos e nas listas. O diálogo de arquivar avisa quando o saldo não é zero. A
  última conta ativa não pode ser arquivada (422 `account.last-active`). Arquivar se desfaz com
  `unarchive`.
- **D5.** "Saldo informado à mão" (US-06): o `PATCH` da conta aceita `balance` no lugar de
  `initialBalance`. A API grava `initial_balance = balance − movimentos`, num só comando. O web
  mostra "Acertar saldo" em toda conta, e no tipo investimento é o caminho principal.
- **D6.** Regras da conta:
  - o nome tem de 1 a 60 caracteres, sem espaços nas pontas, e é único entre as contas ativas da
    família (índice parcial; 409 `account.name-taken`);
  - a moeda é a da família (só BRL na v0.1; outra moeda dá 422 `account.currency-unsupported`) e
    não muda depois;
  - o saldo inicial pode ser negativo, vai até 999.999.999,99 em valor absoluto e tem no máximo 2
    casas;
  - o tipo pode mudar;
  - o titular (`ownerMembershipId`) existe na API, nulo por padrão, e precisa ser um vínculo ativo
    da família (422 `account.owner-not-found`). O web só mostra o campo no Plano 4.
- **D7.** Regras da categoria:
  - um nível de subcategoria: o pai precisa ser ativo, do mesmo tipo e de primeiro nível (422
    `category.parent-invalid`);
  - tipo e pai não mudam depois de criada;
  - arquivar um pai arquiva os filhos junto; reativar um filho exige o pai ativo (422
    `category.parent-archived`);
  - não dá para arquivar a última categoria ativa de primeiro nível de um tipo (422
    `category.last-active`);
  - o nome segue a D30 do Plano 2 (409 `category.name-taken`);
  - a cor é uma de oito chaves fixas (`CategoryColor`); o ícone é um emoji de uma lista curada no
    web, e a API aceita de 1 a 16 caracteres.
- **D8.** Os gráficos usam a paleta categórica do design system por posição (até 6 cores e
  "Outras"), não a cor escolhida para a categoria. A cor da categoria aparece só no
  `CategoryChip`, sempre com o ícone e o nome.
- **D9.** O painel agrupa os gastos pela categoria de primeiro nível: a subcategoria soma no pai. O
  filtro `categoryId` da lista também inclui as subcategorias.
- **D10.** Editar é substituir: o `PATCH` leva todos os campos editáveis e a `version`. O tipo pode
  mudar. Conta ou categoria arquivada só vale se for a mesma que o lançamento já tem. Versão velha
  dá 409 `transaction.version-conflict`, com o lançamento atual no corpo (`current`).
- **D11.** Excluir não leva `version`: é lógico e reversível. `DELETE` responde 204 e repetir não
  muda nada. `restore` responde 200 com o lançamento, também idempotente. Excluir e restaurar
  sobem a `version`.
- **D12.** O diálogo "Este lançamento mudou" mostra a versão atual e oferece "Ficar com a versão
  atual" (descarta a edição) ou "Salvar a minha mesmo assim" (reenvia com a versão atual). As duas
  saídas são escolha explícita; nada é sobrescrito em silêncio.
- **D13.** "Quem" continua sendo quem lança, na API e no web, até o Plano 4 (D1 do Plano 2).
- **D14.** O "Desfazer" do aviso de lançamento salvo: com o item ainda na fila, tira da fila (como
  no Plano 2); se ele já foi salvo e há conexão, chama `DELETE`; se já foi salvo e não há conexão,
  avisa que não dá para desfazer sem internet. Isso fecha a D34 do Plano 2 e segue o wireframe 4.
- **D15.** "Primeiros passos" (US-13):
  - aparece no Início depois do primeiro lançamento da família;
  - itens: "Adicionar suas contas" (feito com 2 contas ou mais, arquivadas ou não) e "Criar passkey
    neste aparelho" (feito com `passkeyCreatedAt`). "Convidar a família" entra no Plano 4, com os
    convites;
  - o ✕ grava `memberships.getting_started_dismissed_at` por `POST /v1/me/getting-started/dismiss`,
    e a lista não volta em nenhum aparelho. Ela também some quando todos os itens estão feitos.
- **D16.** A sessão autenticada guarda só `SessionUser(UUID userId)`. Nome e e-mail vêm do banco
  por `/v1/me`. A sugestão de nome da família (D7 do Plano 2) vira um atributo de sessão à parte,
  apagado quando a família é criada.
- **D17.** O cookie da sessão autenticada ganha `Max-Age` de 30 dias e é reenviado no máximo uma
  vez a cada 24 h, para acompanhar os 30 dias sem uso do servidor. A sessão anônima continua com
  cookie de sessão, sem `Max-Age`. Sem isso, o PWA instalado no iOS perde o login ao ser fechado.
- **D18.** Orçamento de chunks, medido em gzip a partir do `dist/.vite/manifest.json` (o build
  passa a gerá-lo com `build.manifest: true`):
  - carga inicial (a entrada e tudo que ela importa estaticamente): até 190 KB;
  - qualquer outro chunk: até 260 KB.
  - Os números valem como teto. Depois do `lazy()`, a Task 6 mede o build e, se a medição ficar
    bem abaixo, baixa cada limite para a medição + 10%, arredondada para cima em KB. Os limites
    finais ficam no próprio script e na descrição do PR 2.
  - O script `web/scripts/check-chunks.mjs` falha acima do limite e imprime a tabela; o job `web`
    do CI o roda depois do build.
- **D19.** O ECharts entra pelo `echarts/core`, com só `PieChart`, `LineChart`, `BarChart`,
  `GridComponent`, `DatasetComponent` e o `SVGRenderer`, num componente próprio (`EChart`), sem
  `echarts-for-react`. O SVG pesa menos na memória do celular. Os gráficos ficam com
  `aria-hidden`, e a tabela equivalente é que vai para o leitor de tela.
- **D20.** Um job apaga de vez os lançamentos excluídos há mais de 30 dias:
  `@Scheduled(cron = "0 30 3 * * *", zone = "America/Fortaleza")`, um `DELETE` só, sem trava (é
  idempotente). Restaurar depois disso dá 404 `transaction.not-found`.
- **D21.** Contas e categorias ficam no Mais: `/mais/contas` e `/mais/categorias`.
- **D22.** Na lista, a transferência mostra "↔ Transferência", o valor sem sinal, em cor neutra, e
  "Origem → Destino". O filtro por conta inclui as transferências que entram e as que saem.
- **D23.** O cartão de crédito mostra o saldo como é (negativo com gastos), a dica do tipo no
  formulário e a ação "Pagar fatura", que abre o novo lançamento em Transferência com o cartão
  como destino.
- **D24.** O `PATCH` de conta e de categoria é parcial e não leva `version`: o último a salvar
  vence. São poucos campos, mudam pouco, e um conflito ali não perde dinheiro de ninguém. O lock
  otimista fica só nos lançamentos (D10). O titular da conta só é definido na criação até o Plano 4.
- **D25.** Conta e categoria só se arquivam, nunca se apagam, mesmo sem lançamentos. A US-06 exige
  arquivar só a conta com lançamentos; arquivar sempre deixa uma regra só e um caminho de volta
  (`unarchive`). A exclusão definitiva vem com a exclusão da família (LGPD), no Plano 4.

## Review Focus

1. **Transferência e o total da família.** Transferir entre contas ativas não muda o saldo total,
   nem quando a transferência vira gasto na edição, nem quando uma das contas é arquivada depois
   (Tasks 4, 5 e 8).
2. **Duas pessoas, ou duas abas, editando o mesmo lançamento.** A segunda recebe "Este lançamento
   mudou"; se a primeira excluiu, a segunda vê que ele foi excluído, sem erro genérico (Tasks 4
   e 8).
3. **Conta ou categoria arquivada com lançamentos antigos.** Os antigos continuam mostrando o nome
   e o ícone; os novos não podem escolhê-la; editar um antigo sem trocar a conta continua
   funcionando (Tasks 2, 3, 4 e 8).
4. **Lançamento na virada do mês ou perto da meia-noite.** O painel e a lista usam a data civil da
   família; o limite de datas usa o fuso da família, não a UTC (Tasks 4, 5, 7 e 11).
5. **"Desfazer" com a conexão caindo, ou repetido em outra aba.** Restaurar duas vezes não
   duplica nada, e sem conexão o app explica em vez de falhar calado (Tasks 4, 7 e 8).

---

## Mapa de arquivos

API (`api/src/main/java/br/com/liberdadefamilia/`):

| Pacote | Novo | Muda |
|---|---|---|
| `identity` | `SignInHints` | — |
| `workspace` | `FamilyActivity` | — |
| `identity/internal` | `SessionUser`, `SessionUserAuthentication`, `MinimalSecurityContextRepository`, `SessionSignInHints`, `PersistentSessionCookieSerializer`, `SessionCookieRefreshFilter` | `SecurityConfig`, `CurrentUserArgumentResolver` |
| `workspace/internal` | `GettingStartedController` | `WorkspaceController`, `MeController`, `MeView`, `WorkspaceRepository` |
| `ledger/domain` | `TransactionKind` (movido), `TransactionDateRange`, `Flow`, `DailyBalance`, `MonthSummary`, `BalanceCalculator`, `CategoryTotal`, `RankedCategory`, `CategoryRanking` | — |
| `ledger/internal` | `CreateAccountRequest`, `UpdateAccountRequest`, `BalanceAmount` (+ validador), `AccountService`, `CategoryColor`, `CreateCategoryRequest`, `UpdateCategoryRequest`, `CategoryService`, `UpdateTransactionRequest`, `TransactionPurgeJob`, `LedgerSchedulingConfig`, `LedgerNotFoundException`, `LedgerConflictException`, `LedgerRejectedException` (renomeada), `DashboardCategory`, `DashboardDay`, `DashboardService`, `LedgerFamilyActivity` | `AccountController`, `AccountRepository`, `AccountView`, `CategoryController`, `CategoryRepository`, `CategoryView`, `CreateTransactionRequest`, `TransactionController`, `TransactionService`, `TransactionRepository`, `TransactionView`, `DashboardController`, `DashboardView`, `LedgerSeeder` |
| `shared` | — | `DomainException` |
| `shared/internal` | — | `ProblemHandler` |

Migração nova: `api/src/main/resources/db/migration/V5__organize.sql`.

Web (`web/src/`):

| Pasta | Novo | Muda |
|---|---|---|
| `app` | — | `routes.tsx`, `MorePage.tsx` |
| `features/accounts` | `AccountsPage`, `AccountForm`, `AdjustBalanceSheet`, `accountTypes.ts` | — |
| `features/categories` | `CategoriesPage`, `CategoryForm`, `categoryStyle.ts` | — |
| `features/dashboard` | `DashboardPage`, `EChart`, `charts.ts`, `GettingStartedCard` | `HomePage` |
| `features/transactions` | `KindTabs`, `TransactionDetailSheet`, `VersionConflictDialog`, `TransactionFilters` | `NewExpensePage` → `NewTransactionPage`, `CategoryPicker`, `ContextSheet`, `TransactionRow`, `TransactionsPage`, `listItems.ts` |
| `shared/api` | `mutations.ts` | `endpoints.ts`, `hooks.ts`, `queryKeys.ts`, `types.ts`, `errors.ts`, `schema.ts` |
| `shared/offline` | `undo.ts` | `outbox.ts`, `applySent.ts` |
| `shared/ui` | `MonthSwitcher` | `index.ts` |
| `shared/money` | — | `money.ts` |
| `shared/i18n` | — | `messages.pt-BR.ts`, `dates.ts` |
| `mocks` | — | `data.ts`, `handlers.ts` |

Mais: `web/scripts/chunk-budget.mjs`, `check-chunks.mjs` e `chunk-budget.test.mjs`; `web/vite.config.ts`;
`web/package.json` (o script `check:chunks` e o `echarts`); `.github/workflows/ci.yml`;
`web/e2e/month-dashboard.spec.ts`, `edit-delete.spec.ts` e `a11y.spec.ts`; e
`docs/runbooks/ios-session-cookie.md`.

**Em toda task de API:** cada rota nova sob `/v1/workspaces/{ws}` tem `@WorkspaceScoped` (leitura
com OWNER, ADULT e VIEWER; escrita com OWNER e ADULT). Toda mudança de rota ou DTO regenera
`api/openapi.json` (`UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'`) e
`web/src/shared/api/schema.ts` (`corepack pnpm --dir web gen:api`), e atualiza os handlers e o
`mockState` do MSW com as mesmas regras e códigos, no mesmo commit.

## Tasks

### Task 1: Sessão mínima e cookie persistente

**Files:**
- Create: `api/src/main/java/br/com/liberdadefamilia/identity/SignInHints.java`
- Create em `api/src/main/java/br/com/liberdadefamilia/identity/internal/`:
  - `SessionUser.java`, `SessionUserAuthentication.java`
  - `MinimalSecurityContextRepository.java`
  - `SessionSignInHints.java`
  - `PersistentSessionCookieSerializer.java`, `SessionCookieRefreshFilter.java`
- Modify: `identity/internal/SecurityConfig.java`, `identity/internal/CurrentUserArgumentResolver.java`
- Modify: `workspace/internal/WorkspaceController.java`
- Modify: `api/src/test/java/br/com/liberdadefamilia/support/TestUsers.java`
- Test: `identity/SignInIT.java`, `identity/PasskeyIT.java`, `identity/SecurityIT.java`,
  `identity/internal/CurrentUserArgumentResolverTest.java`, `workspace/WorkspaceIT.java`
- Test (novos), em `identity/internal/`: `MinimalSecurityContextRepositoryTest.java`,
  `SessionCookieRefreshFilterTest.java`, `PersistentSessionCookieSerializerTest.java`,
  `SessionUserTest.java`

**Interfaces:**
- Consumes: `UserAccounts`, `LiberdadeOidcUser`, o usuário do passkey, o `Clock` de `ClockConfig`.
- Produces:
  - `public record SessionUser(UUID userId) implements Serializable`.
  - `public final class SessionUserAuthentication extends AbstractAuthenticationToken`: construtor
    `(SessionUser user)`; `getName()` = `userId.toString()`; `getPrincipal()` = o `SessionUser`;
    `getCredentials()` = `null`; autenticado; sem authorities.
  - `public interface SignInHints { void forgetFamilyName(HttpServletRequest request); }` (API
    pública do `identity`), implementada por `SessionSignInHints` (`@Component`).
  - `SessionSignInHints.FAMILY_NAME_HINT = "liberdade.familyNameHint"` (atributo de sessão).
  - `PersistentSessionCookieSerializer.PERSISTENT = "liberdade.persistentCookie"` (atributo de
    requisição) e `MAX_AGE_SECONDS = 2_592_000`.
  - `TestUsers.signedIn(UUID userId, @Nullable String familyName)` mantém a assinatura; passa a
    autenticar com `authentication(new SessionUserAuthentication(new SessionUser(userId)))` e a pôr
    o hint na sessão quando não é nulo. As classes ficam públicas em `identity.internal`, como o
    `LiberdadeOidcUser` já é, para os testes as usarem.

**Como fica:**
- `MinimalSecurityContextRepository implements SecurityContextRepository` embrulha um
  `HttpSessionSecurityContextRepository`. Em `saveContext`:
  - autenticação nula ou já `SessionUserAuthentication`: repassa como veio;
  - qualquer outra (OIDC ou passkey): troca por
    `SessionUserAuthentication(new SessionUser(UUID.fromString(auth.getName())))`; se o principal é
    `OidcUser` com `family_name` não vazio, grava o valor no atributo `FAMILY_NAME_HINT`; repassa
    um contexto novo com a autenticação mínima.
  - `loadDeferredContext` e `containsContext` só delegam.
- `SecurityConfig` registra o repositório em `http.securityContext(...)`. O filtro de passkey cria
  o próprio repositório no construtor, então o `passkeySignIn` (o `ObjectPostProcessor` que já
  existe) também chama `filter.setSecurityContextRepository(...)` com o mesmo bean.
- `CurrentUserArgumentResolver` lê o hint do atributo de sessão (`request.getSession(false)`), não
  mais do principal. `WorkspaceController.createWorkspace` chama
  `signInHints.forgetFamilyName(request)` depois de criar a família.
- `PersistentSessionCookieSerializer implements CookieSerializer` embrulha um
  `DefaultCookieSerializer` com os ajustes de hoje (`__Host-sid`, `Path=/`, `Secure`, `HttpOnly`,
  `SameSite=Lax`) e substitui o bean `DefaultCookieSerializerCustomizer sessionCookie()` (o
  serializer do Boot é `@ConditionalOnMissingBean`). Em `writeCookieValue`, se o valor não é vazio
  e a requisição tem o atributo `PERSISTENT`, põe `cookieMaxAge = MAX_AGE_SECONDS` antes de
  delegar.
- A estratégia `signedInIdleTimeout()` passa a pôr o atributo `PERSISTENT` na requisição, além dos
  30 dias de inatividade.
- `SessionCookieRefreshFilter extends OncePerRequestFilter`, depois do `AuthorizationFilter`,
  recebe o `Clock` e o serializer. Com sessão autenticada e sem a marca `liberdade.cookieRefreshedAt`,
  ou com marca de mais de 24 h, grava a marca, põe o atributo `PERSISTENT` e escreve o cookie com o
  id da sessão atual. Sessão anônima nunca ganha `Max-Age`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `SignInIT.storedContextIsMinimal` | depois do login no mock (claims com `email` e `name`), o blob `SPRING_SECURITY_CONTEXT` em `spring_session_attributes`, desserializado, tem `SessionUserAuthentication` com o id do usuário; os bytes não contêm o e-mail, o nome nem `id_token` |
| `SignInIT.signInCookieIsPersistent` | o `Set-Cookie` de `__Host-sid` da volta do login tem `Max-Age=2592000` |
| `SignInIT.familyNameHintSurvivesUntilFamilyExists` | depois do login, `GET /v1/me` sugere "Família Teste"; depois de `POST /v1/workspaces`, o atributo `liberdade.familyNameHint` não está mais na sessão |
| `PasskeyIT.passkeySignInStoresMinimalContext` | depois do login por passkey, o contexto gravado é `SessionUserAuthentication`, e o cookie tem `Max-Age=2592000` |
| `SecurityIT.anonymousCookieHasNoMaxAge` | `GET /v1/csrf` anônimo: `Set-Cookie` sem `Max-Age` |
| `SecurityIT.logoutExpiresCookie` | `POST /logout` autenticado: `Set-Cookie` de `__Host-sid` com `Max-Age=0` |
| `MinimalSecurityContextRepositoryTest` (Mockito) | OIDC com `family_name` "Teste" → o delegate recebe `SessionUserAuthentication` e a sessão ganha o hint; passkey → `SessionUserAuthentication`, sem hint; contexto já mínimo → repassado como veio; contexto vazio → repassado como veio |
| `SessionCookieRefreshFilterTest` (`Clock` fixo, `MockHttpServletRequest`) | autenticado e sem marca → escreve o cookie com `Max-Age`; marca de 23 h → não escreve; marca de 25 h → escreve e atualiza a marca; anônimo → não escreve |
| `PersistentSessionCookieSerializerTest` | com o atributo e valor não vazio → `Max-Age=2592000`; sem o atributo → sem `Max-Age`; valor vazio (logout) → `Max-Age=0` |
| `CurrentUserArgumentResolverTest` | o caso "OIDC com family_name" vira "sessão com o atributo de hint" → `familyNameHint` "Teste"; sem atributo → nulo |
| `SessionUserTest.survivesJavaSerialization` | serializar e desserializar `SessionUserAuthentication` mantém `getName()` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*SignInIT' --tests '*PasskeyIT' --tests '*SecurityIT' --tests 'br.com.liberdadefamilia.identity.internal.*'`
Expected: FAIL na compilação (`SessionUser` não existe).

- [ ] **Step 3: Implementar**

Siga "Como fica". O `LiberdadeOidcUser` continua existindo: é o que o `LiberdadeOidcUserService`
devolve ao filtro, antes de o repositório trocar o principal.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir na aplicação inteira**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, com `ApplicationModulesTest` verde (o `workspace` usa `SignInHints`, API
pública do `identity`).

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(identity): keep only the user id in the session and persist the cookie for 30 days"
```

---

### Task 2: Contas

**Files:**
- Create: `api/src/main/resources/db/migration/V5__organize.sql`
- Create em `ledger/internal/`: `CreateAccountRequest.java`, `UpdateAccountRequest.java`,
  `BalanceAmount.java`, `BalanceAmountValidator.java`, `AccountService.java`,
  `LedgerNotFoundException.java`, `LedgerConflictException.java`
- Modify: `ledger/internal/AccountController.java`, `AccountRepository.java`, `AccountView.java`,
  `TransactionService.java` (só a busca da conta, que passa a usar `choice`)
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test: `ledger/AccountsIT.java` (novo), `ledger/IsolationIT.java`,
  `workspace/WorkspaceGuardCoverageIT.java`

**Interfaces:**
- Consumes: `WorkspaceScoped`, `WorkspaceMember`, `Money`, `TransactionRejectedException`.
- Produces:
  - `LedgerNotFoundException(String code, String detail)`: 404. `LedgerConflictException(String
    code, String detail)`: 409. As duas estendem `DomainException`; a 422 continua sendo
    `TransactionRejectedException` (renomeada para `LedgerRejectedException`, com os usos atuais
    atualizados).
  - `AccountView(UUID id, String name, AccountType type, Currency currency, Money initialBalance,
    Money balance, @Nullable UUID ownerMembershipId, @Nullable Instant archivedAt)`.
  - `CreateAccountRequest(@NotBlank @Size(max = 60) @Pattern(WITHOUT_NUL) String name,
    @NotNull AccountType type, @NotNull @BalanceAmount Money initialBalance,
    @Nullable UUID ownerMembershipId)`; o construtor compacto faz `strip()` no nome.
  - `UpdateAccountRequest(@Nullable @Size(min = 1, max = 60) @Pattern(WITHOUT_NUL) String name,
    @Nullable AccountType type, @Nullable @BalanceAmount Money initialBalance,
    @Nullable @BalanceAmount Money balance)`, com `strip()` no nome e dois `@AssertTrue`:
    `isOneBalanceAtMost()` e `isNotEmpty()`.
  - `@BalanceAmount`: valor absoluto até 999.999.999,99 e escala até as casas da moeda; aceita
    negativo e zero.
  - `AccountRepository`:
    - `List<AccountView> allWithBalances(UUID workspaceId)`: ativas primeiro, depois nome e id;
    - `Optional<AccountView> findWithBalance(UUID workspaceId, UUID accountId)`;
    - `Optional<AccountChoice> choice(UUID workspaceId, UUID accountId)`, com
      `record AccountChoice(Currency currency, boolean archived)`; substitui `unarchivedCurrency`;
    - `UUID insert(UUID workspaceId, String name, AccountType type, Money initialBalance,
      @Nullable UUID ownerMembershipId)` (o `LedgerSeeder` passa a usar este);
    - `boolean update(UUID workspaceId, UUID accountId, @Nullable String name,
      @Nullable AccountType type, @Nullable Money initialBalance)`;
    - `boolean setBalance(UUID workspaceId, UUID accountId, Money balance)`: grava
      `initial_balance = balance − movimentos` num `UPDATE` só;
    - `boolean archive(UUID workspaceId, UUID accountId, Instant now)` (só muda `archived_at` se
      era nulo) e `boolean unarchive(UUID workspaceId, UUID accountId)`;
    - `int countActive(UUID workspaceId)`;
    - `boolean isActiveMembership(UUID workspaceId, UUID membershipId)`.
  - `AccountService`: `create`, `update`, `archive`, `unarchive`, cada um recebendo
    `WorkspaceMember` e devolvendo `AccountView`, `@Transactional`. Traduz a
    `DuplicateKeyException` do índice `accounts_unique_name` em 409 `account.name-taken`.
  - Rotas (sob `/v1/workspaces/{ws}`):

| Método e rota | `operationId` | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /accounts` | `listAccounts` | leitura | 200, todas as contas | — |
| `POST /accounts` | `createAccount` | escrita | 201 `AccountView` | 409 `account.name-taken`; 422 `account.currency-unsupported`, `account.owner-not-found`, `request.invalid` |
| `PATCH /accounts/{id}` | `updateAccount` | escrita | 200 `AccountView` | 404 `account.not-found`; 409 `account.name-taken`; 422 `request.invalid` |
| `POST /accounts/{id}/archive` | `archiveAccount` | escrita | 200 `AccountView` | 404; 422 `account.last-active` |
| `POST /accounts/{id}/unarchive` | `unarchiveAccount` | escrita | 200 `AccountView` | 404; 409 `account.name-taken` |

**`V5__organize.sql`:**
- `create unique index accounts_unique_name on accounts (workspace_id, name) where archived_at is null;`
- `alter table memberships add column getting_started_dismissed_at timestamptz;` (usada na
  Task 12).
- `alter table categories add constraint categories_color_check check (color is null or color in
  ('TEAL', 'BLUE', 'INDIGO', 'PURPLE', 'PINK', 'RED', 'ORANGE', 'GREEN'));` (hoje nenhuma
  categoria tem cor, então nada quebra; usada na Task 3).

**Textos (`detail`):**
- `account.not-found`: "Conta não encontrada."
- `account.name-taken`: "Já existe uma conta com esse nome."
- `account.currency-unsupported`: "Por enquanto, as contas usam a moeda da família."
- `account.owner-not-found`: "Essa pessoa não faz parte da família."
- `account.last-active`: "A família precisa de pelo menos uma conta ativa."

**Regras (D4, D5, D6, D24, D25):** arquivar e reativar são idempotentes: repetir devolve 200 com a
conta como está. A moeda nunca muda. O `PATCH` vale também para conta arquivada.

- [ ] **Step 1: Escrever os testes (falhando)**

`AccountsIT` (Testcontainers, `TestUsers`, `TestWorkspaces`, `TestLedger`):

| Teste | Esperado |
|---|---|
| `listsActiveFirstThenArchived` | Carteira, "Banco" e "Antiga" (arquivada): ordem Banco, Carteira, Antiga; `archivedAt` só na última |
| `createsAccountWithNegativeInitialBalance` | `POST` com "Cartão", `CREDIT_CARD`, `-350.00` → 201; `balance` = `initialBalance` = `-350.00` |
| `rejectsDuplicateActiveName` | segundo `POST` com " Cartão " (espaços nas pontas) → 409 `account.name-taken` |
| `allowsNameOfArchivedAccount` | arquivar "Cartão" e criar outro "Cartão" → 201 |
| `rejectsOtherCurrency` | `initialBalance` em USD → 422 `account.currency-unsupported` |
| `rejectsTooManyDecimals` | `"10.001"` → 422 `request.invalid` |
| `rejectsOwnerFromOtherFamily` | `ownerMembershipId` de outra família → 422 `account.owner-not-found` |
| `renamesAndChangesType` | `PATCH` com `name` e `type` → 200; o resto igual |
| `adjustsBalanceByHand` | conta com inicial 100 e gasto de 30; `PATCH {"balance":{"amount":"1000.00","currency":"BRL"}}` → `balance` 1000.00, `initialBalance` 1030.00 |
| `rejectsBothBalances` | `PATCH` com `initialBalance` e `balance` → 422 `request.invalid` |
| `rejectsEmptyPatch` | `PATCH {}` → 422 `request.invalid` |
| `archiveIsIdempotent` | arquivar duas vezes → 200 nas duas; `archivedAt` igual |
| `cannotArchiveLastActive` | arquivar a Carteira sozinha → 422 `account.last-active` |
| `unarchiveFailsWhenNameTaken` | "Banco" arquivada, outra "Banco" ativa; reativar a primeira → 409 `account.name-taken` |
| `viewerCannotWrite` | VIEWER em `POST`, `PATCH`, `archive` e `unarchive` → 403 `auth.forbidden` |

`IsolationIT` ganha: `PATCH`, `archive` e `unarchive` numa conta de outra família → 404
`account.not-found`, e a conta não muda. `WorkspaceGuardCoverageIT` passa a contar as rotas novas.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*AccountsIT' --tests '*IsolationIT'`
Expected: FAIL (as rotas não existem).

- [ ] **Step 3: Implementar**

Migração, DTOs, validador, repositório, serviço e controller, pela tabela de rotas. O saldo de cada
conta continua sendo o do Plano 2 (todos os lançamentos não excluídos, D3).

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Contrato e mocks**

- Regenerar `openapi.json` e `schema.ts`.
- `mocks/data.ts`: `MockAccount` passa a ter `initialBalance`, `ownerMembershipId` e `archivedAt`; o
  handler de `GET /accounts` calcula `balance` e ordena como a API.
- `mocks/handlers.ts`: `POST`, `PATCH`, `archive` e `unarchive` com as mesmas regras e códigos.

- [ ] **Step 6: Conferir**

Run: `./gradlew build`; depois `corepack pnpm --filter web typecheck; corepack pnpm --filter web test`
Expected: BUILD SUCCESSFUL; typecheck e testes do web verdes (os campos novos só somam).

- [ ] **Step 7: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(ledger): create, edit, adjust and archive accounts"
```

---

### Task 3: Categorias

**Files:**
- Create em `ledger/internal/`: `CategoryColor.java`, `CreateCategoryRequest.java`,
  `UpdateCategoryRequest.java`, `CategoryService.java`
- Modify: `ledger/internal/CategoryController.java`, `CategoryRepository.java`, `CategoryView.java`,
  `TransactionService.java` (só a busca da categoria, que passa a usar `choice`)
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`, `web/src/features/transactions/CategoryPicker.tsx`
- Test: `ledger/CategoriesIT.java`, `ledger/IsolationIT.java`,
  `features/transactions/CategoryPicker.test.tsx`

**Interfaces:**
- Consumes: `LedgerNotFoundException`, `LedgerConflictException`, `LedgerRejectedException` (Task 2);
  o check `categories_color_check` da V5 (Task 2).
- Produces:
  - `enum CategoryColor { TEAL, BLUE, INDIGO, PURPLE, PINK, RED, ORANGE, GREEN }`.
  - `CategoryView(UUID id, String name, TransactionKind kind, @Nullable String icon,
    @Nullable CategoryColor color, @Nullable UUID parentId, @Nullable Instant archivedAt)`.
  - `CreateCategoryRequest(@NotBlank @Size(max = 40) @Pattern(WITHOUT_NUL) String name,
    @NotNull TransactionKind kind, @Nullable @Size(min = 1, max = 16) String icon,
    @Nullable CategoryColor color, @Nullable UUID parentId)`, com `strip()` no nome.
  - `UpdateCategoryRequest(@Nullable @Size(min = 1, max = 40) @Pattern(WITHOUT_NUL) String name,
    @Nullable @Size(min = 1, max = 16) String icon, @Nullable CategoryColor color)`, com
    `@AssertTrue isNotEmpty()`. Nulo quer dizer "não muda"; o web sempre manda ícone e cor.
  - `CategoryRepository`:
    - `List<CategoryView> allByUse(UUID workspaceId, TransactionKind kind, LocalDate usedSince)`:
      as ativas por uso nos últimos 90 dias, `sort_order`, nome e id; depois as arquivadas por nome
      e id;
    - `Optional<CategoryView> find(UUID workspaceId, UUID categoryId)`;
    - `Optional<CategoryChoice> choice(UUID workspaceId, UUID categoryId)`, com
      `record CategoryChoice(TransactionKind kind, boolean archived)`; substitui `unarchivedKind`;
    - `UUID insert(UUID workspaceId, String name, TransactionKind kind, @Nullable String icon,
      @Nullable CategoryColor color, @Nullable UUID parentId)`: `sort_order` = maior entre os irmãos
      + 1 (o `LedgerSeeder` continua com `insertDefault`);
    - `boolean update(UUID workspaceId, UUID categoryId, @Nullable String name,
      @Nullable String icon, @Nullable CategoryColor color)`;
    - `boolean archiveWithChildren(UUID workspaceId, UUID categoryId, Instant now)`: a categoria e
      os filhos ativos recebem o mesmo `archived_at`;
    - `boolean unarchiveWithChildren(UUID workspaceId, UUID categoryId)`: volta a categoria e os
      filhos com o mesmo `archived_at` dela;
    - `int countActiveTopLevel(UUID workspaceId, TransactionKind kind)`.
  - `CategoryService`: `create`, `update`, `archive`, `unarchive`, `@Transactional`; traduz a
    `DuplicateKeyException` de `categories_unique_name` em 409 `category.name-taken`.
  - Rotas (sob `/v1/workspaces/{ws}`):

| Método e rota | `operationId` | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /categories?kind=` | `listCategories` | leitura | 200, todas do tipo | 400 com `TRANSFER` (como hoje) |
| `POST /categories` | `createCategory` | escrita | 201 `CategoryView` | 409 `category.name-taken`; 422 `category.kind-invalid`, `category.parent-invalid`, `request.invalid` |
| `PATCH /categories/{id}` | `updateCategory` | escrita | 200 | 404 `category.not-found`; 409 `category.name-taken`; 422 `request.invalid` |
| `POST /categories/{id}/archive` | `archiveCategory` | escrita | 200 | 404; 422 `category.last-active` |
| `POST /categories/{id}/unarchive` | `unarchiveCategory` | escrita | 200 | 404; 409 `category.name-taken`; 422 `category.parent-archived` |

**Textos (`detail`):**
- `category.not-found`: "Categoria não encontrada."
- `category.name-taken`: "Já existe uma categoria com esse nome aqui."
- `category.kind-invalid`: "Categorias são de receita ou de gasto."
- `category.parent-invalid`: "Escolha uma categoria principal ativa, do mesmo tipo."
- `category.last-active`: "É preciso manter pelo menos uma categoria ativa desse tipo."
- `category.parent-archived`: "Reative a categoria principal primeiro."

**Regras (D7, D24, D25):** o pai precisa existir na família, estar ativo, ser do mesmo tipo e não
ter pai. `last-active` vale só para categoria de primeiro nível. Arquivar e reativar são
idempotentes.

- [ ] **Step 1: Escrever os testes (falhando)**

`CategoriesIT` (os testes do Plano 2 continuam; a ordem das ativas não muda):

| Teste | Esperado |
|---|---|
| `listsArchivedAfterActive` | arquivar "Pets" → ela vem por último, com `archivedAt`; as ativas na ordem de antes |
| `createsTopLevelWithColorAndIcon` | `POST` "Academia", `EXPENSE`, "🏋️", `GREEN` → 201; aparece na lista com `color` `GREEN` e `parentId` nulo |
| `createsSubcategory` | `POST` "Feira" com `parentId` de Mercado → 201, `parentId` de Mercado |
| `rejectsGrandchild` | `POST` com `parentId` de "Feira" → 422 `category.parent-invalid` |
| `rejectsParentOfOtherKind` | gasto com pai "Salário" → 422 `category.parent-invalid` |
| `rejectsTransferKind` | `kind` `TRANSFER` → 422 `category.kind-invalid` |
| `rejectsUnknownColor` | `color` `"BLACK"` → 400 `request.malformed` |
| `rejectsDuplicateSibling` | segunda "Feira" sob Mercado → 409; "Feira" sob Alimentação fora → 201 |
| `renamesAndRecolors` | `PATCH` com `name` e `color` → 200; ícone igual |
| `archivesParentWithChildren` | arquivar Mercado → Mercado e Feira com o mesmo `archivedAt` |
| `unarchivesParentWithChildren` | reativar Mercado → os dois ativos |
| `childNeedsActiveParent` | com Mercado arquivado, reativar Feira → 422 `category.parent-archived` |
| `keepsOneActiveTopLevel` | arquivar as 6 categorias de receita → a sexta dá 422 `category.last-active` |
| `viewerCannotWrite` | VIEWER nas quatro rotas de escrita → 403 |

`IsolationIT` ganha `PATCH`, `archive` e `unarchive` em categoria de outra família → 404
`category.not-found`; e `POST` com `parentId` de outra família → 422 `category.parent-invalid`.

`CategoryPicker.test.tsx` ganha: categoria arquivada não aparece nos chips nem no "Mais".

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*CategoriesIT' --tests '*IsolationIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pela tabela de rotas. Depois, regenerar o contrato; no MSW, `MockState.categories` ganha `color`,
`parentId` e `archivedAt`, e os handlers novos seguem as mesmas regras. O `CategoryPicker` filtra
`archivedAt` nulo; a Task 7 faz o resto.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2; depois `corepack pnpm --filter web test`
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `./gradlew build`; `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck`
Expected: BUILD SUCCESSFUL; lint e typecheck verdes.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(ledger): create, edit and archive categories and subcategories"
```

---

### Task 4: Receitas, transferências, edição e exclusão

**Files:**
- Create: `api/src/main/java/br/com/liberdadefamilia/ledger/domain/TransactionDateRange.java`
- Move: `ledger/internal/TransactionKind.java` → `ledger/domain/TransactionKind.java` (público)
- Create em `ledger/internal/`: `UpdateTransactionRequest.java`, `TransactionPurgeJob.java`,
  `LedgerSchedulingConfig.java`
- Modify: `shared/DomainException.java`, `shared/internal/ProblemHandler.java`
- Modify: `ledger/internal/CreateTransactionRequest.java`, `TransactionController.java`,
  `TransactionService.java`, `TransactionRepository.java`, `TransactionView.java`,
  `DashboardController.java` (só o limite de mês)
- Modify: `identity/internal/SecurityConfig.java` (CORS)
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test: `ledger/TransactionsIT.java`, `ledger/IsolationIT.java`, `identity/SecurityIT.java`,
  `shared/internal/ProblemHandlerTest.java`
- Test (novos): `ledger/TransactionEditIT.java`, `ledger/TransactionPurgeJobIT.java`,
  `ledger/domain/TransactionDateRangeTest.java`

**Interfaces:**
- Consumes: `AccountRepository.choice`, `CategoryRepository.choice` (Tasks 2 e 3),
  `LedgerNotFoundException`, `LedgerConflictException`, `LedgerRejectedException`.
- Produces:
  - `DomainException` ganha o construtor `(int status, String code, String detail,
    Map<String, Object> properties)` e `Map<String, Object> properties()` (vazio por padrão). O
    `ProblemHandler` copia cada propriedade com `setProperty`.
  - `public final class TransactionDateRange`:
    - `public static final LocalDate MIN = LocalDate.of(2000, 1, 1)`;
    - `public static LocalDate max(LocalDate today)` = `today.plusDays(366)`;
    - `public static boolean contains(LocalDate day, LocalDate today)`;
    - `public static YearMonth minMonth()` = `2000-01`;
    - `public static YearMonth maxMonth(LocalDate today)` = `YearMonth.from(max(today))`.
  - `CreateTransactionRequest` ganha `@Nullable UUID toAccountId`; `categoryId` vira `@Nullable`.
  - `TransactionView` ganha `@Nullable UUID toAccountId` e `long version` (o resto igual).
  - `UpdateTransactionRequest(@NotNull Long version, @NotNull TransactionKind kind,
    @NotNull @TransactionAmount Money amount, @NotNull LocalDate occurredOn,
    @NotNull UUID accountId, @Nullable UUID toAccountId, @Nullable UUID categoryId,
    @Nullable @Size(max = 140) @Pattern(WITHOUT_NUL) String description)`, com a mesma limpeza de
    descrição do create.
  - `TransactionRepository`:
    - `Optional<TransactionView> update(UUID workspaceId, UUID id, UpdateTransactionRequest
      request)`: `update … set …, version = version + 1, updated_at = now() where workspace_id = ?
      and id = ? and version = ? and deleted_at is null returning …`;
    - `boolean softDelete(UUID workspaceId, UUID id, Instant now)` e
      `Optional<TransactionView> restore(UUID workspaceId, UUID id)`, os dois subindo a `version`;
    - `boolean exists(UUID workspaceId, UUID id)` e `boolean isDeleted(…)` (ou `find` com
      `deletedAt`, à escolha);
    - `List<TransactionView> between(UUID workspaceId, LocalDate from, LocalDate until,
      @Nullable UUID categoryId, @Nullable UUID accountId)`: `categoryId` inclui as subcategorias;
      `accountId` pega `account_id` ou `to_account_id`;
    - `int purgeDeletedBefore(Instant cutoff)`: o único método sem `workspaceId`, porque varre
      todas as famílias; comentário explicando.
  - Rotas novas (sob `/v1/workspaces/{ws}`):

| Método e rota | `operationId` | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `PATCH /transactions/{id}` | `updateTransaction` | escrita | 200 `TransactionView` | 404 `transaction.not-found`; 409 `transaction.deleted`, `transaction.version-conflict` (com `current`); 422 como no create |
| `DELETE /transactions/{id}` | `deleteTransaction` | escrita | 204 | 404 |
| `POST /transactions/{id}/restore` | `restoreTransaction` | escrita | 200 `TransactionView` | 404 |
| `GET /transactions?month=&categoryId=&accountId=` | `listTransactions` | leitura | 200 | 422 `month.out-of-range` |

  O `GET /dashboard` também passa a dar 422 `month.out-of-range` fora de `minMonth()` a
  `maxMonth(hoje)`.

**Regras de família (create e update, nesta ordem, depois das checagens de id do Plano 2):**
1. `occurredOn` fora de `TransactionDateRange.contains(dia, hoje no fuso da família)` → 422
   `transaction.date-out-of-range`.
2. Forma: `TRANSFER` exige `toAccountId`, diferente de `accountId`, e `categoryId` nulo; `INCOME` e
   `EXPENSE` exigem `toAccountId` nulo. Fora disso → 422 `transaction.transfer-invalid`. `INCOME`
   e `EXPENSE` sem `categoryId` → 422 `transaction.category-required`.
3. Conta e destino existem na família → senão 422 `transaction.account-not-found`. Conta arquivada
   → `transaction.account-not-found` também, salvo no update quando é a mesma que o lançamento já
   tem (D10).
4. Categoria existe e é do tipo do lançamento → senão `transaction.category-not-found` ou
   `transaction.category-kind-mismatch`. Arquivada segue a regra do item 3.
5. Moeda do valor igual à da conta e à do destino → senão `transaction.currency-mismatch`.

`transaction.kind-unsupported` deixa de existir (API e web).

**Update, nesta ordem:** não existe → 404; excluído → 409 `transaction.deleted`; `version`
diferente → 409 `transaction.version-conflict` com `current` = o `TransactionView` atual; regras
de família → 422; `UPDATE` condicional. Se o `UPDATE` não pega linha nenhuma (corrida), relê e
responde `deleted` ou `version-conflict` pela mesma regra.

**Exclusão e restauração (D11, D20):** `DELETE` num lançamento já excluído responde 204; num que
não existe (ou já apagado de vez), 404. `restore` num lançamento ativo devolve 200 com ele como
está. Restaurar não olha se a conta foi arquivada depois: não é escolha nova.

**Purga (D20):** `TransactionPurgeJob.purge()`, `@Scheduled(cron = "0 30 3 * * *", zone =
"America/Fortaleza")`, chama `purgeDeletedBefore(clock.instant().minus(30, DAYS))` e loga só a
quantidade. `LedgerSchedulingConfig` tem `@EnableScheduling`.

**CORS:** `allowedMethods` passa a ser `GET`, `POST`, `PATCH`, `DELETE` e `OPTIONS`.

**Textos (`detail`):**
- `transaction.not-found`: "Lançamento não encontrado."
- `transaction.deleted`: "Este lançamento foi excluído."
- `transaction.version-conflict`: "Este lançamento mudou desde que você abriu."
- `transaction.date-out-of-range`: "Escolha uma data entre 2000 e o próximo ano."
- `transaction.transfer-invalid`: "Uma transferência precisa de duas contas diferentes e não leva categoria."
- `transaction.category-required`: "Escolha uma categoria."
- `month.out-of-range`: "Escolha um mês entre janeiro de 2000 e o próximo ano."

- [ ] **Step 1: Escrever os testes (falhando)**

`TransactionDateRangeTest`: `MIN` e `max(hoje)` contidos; um dia antes de `MIN` e um dia depois de
`max(hoje)` fora; `maxMonth(2026-11-16)` = `2027-11`; ano bissexto (`max(2027-03-01)` =
`2028-03-01`).

`TransactionsIT` (create e list):

| Teste | Esperado |
|---|---|
| `recordsIncome` | `INCOME` 5000.00 em Salário → 201; `version` 0; saldo da conta +5000 |
| `recordsTransfer` | `TRANSFER` 500 Carteira → Banco, sem categoria → 201 com `toAccountId`; saldos −500 e +500 |
| `rejectsTransferWithoutDestination` | → 422 `transaction.transfer-invalid` |
| `rejectsTransferToSameAccount` | → 422 `transaction.transfer-invalid` |
| `rejectsTransferWithCategory` | → 422 `transaction.transfer-invalid` |
| `rejectsDestinationOnExpense` | `EXPENSE` com `toAccountId` → 422 `transaction.transfer-invalid` |
| `rejectsExpenseWithoutCategory` | → 422 `transaction.category-required` |
| `rejectsArchivedDestination` | destino arquivado → 422 `transaction.account-not-found` |
| `acceptsDateBounds` | `2000-01-01` e hoje + 366 (no fuso da família) → 201 |
| `rejectsDatesOutOfRange` | `1999-12-31` e hoje + 367 → 422 `transaction.date-out-of-range` |
| `usesFamilyTimezoneForToday` | `Clock` de teste em 2026-11-17T02:00Z (ainda dia 16 em Fortaleza): `2027-11-17` → 422; `2027-11-16` → 201 |
| `filtersByCategoryIncludingChildren` | gastos em Mercado e em Feira (filha) → `?categoryId=<Mercado>` traz os dois |
| `filtersByAccountBothDirections` | transferência Carteira → Banco aparece com `?accountId=<Banco>` e com `?accountId=<Carteira>` |
| `rejectsMonthOutOfRange` | `?month=1999-12` → 422 `month.out-of-range` |

`TransactionEditIT`:

| Teste | Esperado |
|---|---|
| `editsAllFields` | `PATCH` com `version` 0, novo valor, data, descrição → 200, `version` 1 |
| `turnsExpenseIntoTransfer` | `PATCH` `kind` `TRANSFER`, `toAccountId`, `categoryId` nulo → 200; saldo total da família igual ao de antes do gasto |
| `rejectsStaleVersion` | dois `PATCH` com `version` 0 → o segundo dá 409 `transaction.version-conflict`, e `current.version` = 1, `current.amount.amount` = o valor do primeiro |
| `staleVersionWinsOverInvalidPayload` | `version` velha e `categoryId` inexistente → 409, não 422 |
| `editDeletedTransaction` | excluir e depois `PATCH` → 409 `transaction.deleted` |
| `keepsArchivedAccountWhenUnchanged` | arquivar a conta do lançamento; `PATCH` mudando só a descrição → 200 |
| `rejectsMovingToArchivedAccount` | `PATCH` para outra conta arquivada → 422 `transaction.account-not-found` |
| `keepsArchivedCategoryWhenUnchanged` | o mesmo com categoria arquivada → 200 |
| `deleteIsIdempotent` | `DELETE` duas vezes → 204 e 204; some do `GET` e do saldo |
| `deleteUnknown` | id inexistente → 404 `transaction.not-found` |
| `restoreBringsItBack` | excluir, `restore` → 200, `version` 2; volta ao `GET` e ao saldo |
| `restoreIsIdempotent` | `restore` duas vezes → 200 nas duas, sem duplicar no `GET` |
| `viewerCannotEditOrDelete` | VIEWER em `PATCH`, `DELETE` e `restore` → 403 |

`TransactionPurgeJobIT` (`Clock` fixo via `@TestConfiguration`): excluído há 31 dias → apagado;
há 29 dias → fica; não excluído → fica; depois do purge, `restore` do apagado → 404.

`IsolationIT` ganha `PATCH`, `DELETE` e `restore` em lançamento de outra família → 404, sem
efeito; e transferência para conta de outra família → 422 `transaction.account-not-found`.

`SecurityIT.corsAllowsPatchAndDelete`: preflight `OPTIONS` da origem do web com
`Access-Control-Request-Method: PATCH` (e `DELETE`) → 200 com o método em
`Access-Control-Allow-Methods`.

`ProblemHandlerTest.copiesDomainProperties`: `DomainException` com `{"current": …}` → o JSON do
problema tem `current`.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*TransactionsIT' --tests '*TransactionEditIT' --tests '*TransactionPurgeJobIT' --tests '*TransactionDateRangeTest' --tests '*IsolationIT' --tests '*SecurityIT' --tests '*ProblemHandlerTest'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pela tabela de rotas e pelas regras acima. As regras de família ficam num método só do
`TransactionService`, usado pelo create e pelo update (o update passa o lançamento atual, para a
exceção do item 3). Documente as respostas no OpenAPI como o `recordTransaction` já faz.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Contrato e mocks**

Regenerar o contrato. No MSW: `TransactionView` com `toAccountId` e `version`; `POST` aceita
transferência e receita; handlers de `PATCH` (com `version-conflict` e `current`), `DELETE` e
`restore`; filtros do `GET`; o saldo das contas conta transferências.

- [ ] **Step 6: Conferir**

Run: `./gradlew build`; `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test`
Expected: tudo verde.

- [ ] **Step 7: Commit**

```bash
git add api web/src
git commit -m "feat(ledger): record income and transfers, edit with optimistic lock, delete and restore"
```

---

### Task 5: Painel do mês na API

**Files:**
- Create em `api/src/main/java/br/com/liberdadefamilia/ledger/domain/`: `Flow.java`,
  `DailyBalance.java`, `MonthSummary.java`, `BalanceCalculator.java`, `CategoryTotal.java`,
  `RankedCategory.java`, `CategoryRanking.java`
- Create em `ledger/internal/`: `DashboardCategory.java`, `DashboardDay.java`,
  `DashboardService.java`
- Modify: `ledger/internal/DashboardController.java`, `DashboardView.java`,
  `TransactionRepository.java`, `AccountRepository.java`
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/handlers.ts`
- Test (novos), em `ledger/domain/`: `BalanceCalculatorTest.java`,
  `BalanceCalculatorPropertiesTest.java`, `CategoryRankingTest.java`,
  `CategoryRankingPropertiesTest.java`
- Test: `ledger/DashboardIT.java`

**Interfaces:**
- Consumes: `TransactionKind`, `TransactionDateRange` (Task 4); `Money`.
- Produces (tudo público em `ledger.domain`, sem Spring):
  - `record Flow(LocalDate day, TransactionKind kind, UUID accountId, @Nullable UUID toAccountId,
    Money amount)`.
  - `record DailyBalance(LocalDate day, Money balance)`.
  - `record MonthSummary(Money income, Money expenses, Money result, Money balance,
    List<DailyBalance> daily)`.
  - `final class BalanceCalculator`, com `static MonthSummary summarize(YearMonth month, Currency
    currency, Map<UUID, Money> activeInitialBalances, List<Flow> flows)`:
    - o saldo de abertura soma os saldos iniciais das contas ativas e os efeitos dos fluxos de
      antes do mês;
    - receitas e gastos contam só os fluxos do mês, de todas as contas (D4);
    - efeito no saldo: `INCOME` soma e `EXPENSE` subtrai quando a conta está no mapa; `TRANSFER`
      subtrai se a origem está no mapa e soma se o destino está;
    - `daily` tem um ponto para cada dia do mês, com o saldo no fim do dia; o último é `balance`;
    - fluxo depois do fim do mês, ou em outra moeda → `IllegalArgumentException` (sem valores na
      mensagem).
  - `record CategoryTotal(UUID categoryId, Money current, Money previous)` e
    `record RankedCategory(@Nullable UUID categoryId, Money current, Money previous)`.
  - `final class CategoryRanking`, com `static List<RankedCategory> rank(List<CategoryTotal>
    totals, int limit)`: descarta as linhas com os dois valores zero; ordena por `current`
    decrescente, `previous` decrescente e id; devolve as `limit` primeiras e, se sobrar alguma,
    uma última com `categoryId` nulo ("Outras") somando o resto. O painel usa `limit` 6 (D8).
  - `AccountRepository.activeInitialBalances(UUID workspaceId)` → `Map<UUID, Money>`.
  - `TransactionRepository.flowsUntil(UUID workspaceId, LocalDate monthStart, LocalDate monthEnd)`
    → `List<Flow>`: lançamentos não excluídos até `monthEnd`, agrupados por
    `greatest(occurred_on, monthStart - 1)`, tipo, conta e destino, com a soma dos valores. Assim
    o passado vira poucos fluxos num dia só.
  - `TransactionRepository.expensesByTopCategory(UUID workspaceId, YearMonth month)` →
    `List<CategoryTotal>`: gastos não excluídos do mês e do mês anterior, agrupados por
    `coalesce(parent_id, id)` da categoria (D9).
  - `DashboardView(String month, Money income, Money expenses, Money result, Money balance,
    List<DashboardCategory> byCategory, List<DashboardDay> dailyBalance)`;
    `DashboardCategory(@Nullable UUID categoryId, Money current, Money previous)`;
    `DashboardDay(LocalDate day, Money balance)`.
  - `DashboardService.monthOf(WorkspaceMember member, YearMonth month)` → `DashboardView`.

- [ ] **Step 1: Escrever os testes (falhando)**

`BalanceCalculatorTest`:

| Teste | Entrada | Esperado |
|---|---|---|
| `prdExample` (PRD §7.1) | Corrente inicial 1000, Poupança 0, Carteira 0; no dia 5: receita 5000 na Corrente, gasto 1234.56 na Corrente, transferência 500 Corrente → Poupança | `income` 5000.00, `expenses` 1234.56, `result` 3765.44, `balance` 4765.44; `daily` do dia 4 = 1000.00, do dia 5 em diante = 4765.44 |
| `openingBalanceIncludesPastFlows` | gasto de 100 no mês anterior | `balance` = inicial − 100; `expenses` 0 |
| `transferFromArchivedToActive` | origem fora do mapa, destino dentro, 300 | `balance` +300 |
| `transferBetweenArchivedAccounts` | as duas fora do mapa | `balance` igual |
| `archivedAccountStillCountsInFlows` | gasto de 50 numa conta fora do mapa, no mês | `expenses` 50, `balance` igual |
| `dailyCoversWholeMonth` | fevereiro de 2028 | 29 pontos, do dia 1 ao 29 |
| `rejectsFlowAfterMonth` | fluxo no dia 1 do mês seguinte | `IllegalArgumentException` |

`BalanceCalculatorPropertiesTest` (`@MethodSource`, `SplittableRandom(20261102L)`, 1000 casos, de 1
a 6 contas, até 40 fluxos com valores de 0,01 a 99.999,99):
- transferências entre contas ativas não mudam `balance`;
- o último ponto de `daily` é igual a `balance`;
- `result` = `income` − `expenses`;
- `daily` tem tantos pontos quanto dias no mês.

`CategoryRankingTest`: 8 categorias → 6 e "Outras" com a soma das 2 menores; 6 categorias → sem
"Outras"; empate em `current` desempata por `previous`, depois por id; linha com os dois zero some;
categoria só com `previous` aparece.

`CategoryRankingPropertiesTest` (mesma semente, 1000 casos): as somas de `current` e de `previous`
se mantêm; tamanho ≤ 7; `categoryId` nulo só na última posição.

`DashboardIT` (os do Plano 2 continuam):

| Teste | Esperado |
|---|---|
| `prdExampleThroughTheApi` | pela API, no mês atual: contas Corrente (inicial 1000) e Poupança (0) além da Carteira; receita 5000 na Corrente, gasto 1234.56 em Mercado na Corrente, transferência 500 Corrente → Poupança → `income` 5000.00, `expenses` 1234.56, `result` 3765.44, `balance` 4765.44; `byCategory` = [Mercado 1234.56 / 0.00]; último `dailyBalance` = 4765.44. `GET /accounts`: Corrente 4265.44, Poupança 500.00, Carteira 0.00 |
| `archivedAccountLeavesBalance` | arquivar uma conta com saldo 200 → `balance` cai 200; os gastos dela continuam em `expenses` |
| `pastMonthBalance` | gasto hoje; `?month=` do mês anterior → `balance` sem esse gasto e `expenses` 0 |
| `futureFlowsStayOut` | gasto com data no mês que vem → não entra no `balance` do mês atual |
| `previousMonthInByCategory` | gasto de 80 em Lazer no mês anterior e 20 neste → Lazer com `current` 20, `previous` 80 |
| `groupsBeyondSix` | gastos em 8 categorias → 7 linhas, a última com `categoryId` nulo |
| `subcategoryFoldsIntoParent` | gasto em Feira (filha de Mercado) → aparece como Mercado |
| `deletedTransactionsStayOut` | gasto excluído → fora de tudo |
| `dailyBalanceHasEveryDay` | → `dailyBalance` com um item por dia do mês |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests 'br.com.liberdadefamilia.ledger.domain.*' --tests '*DashboardIT'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar**

Domínio primeiro, depois repositório, serviço e controller. O serviço converte `MonthSummary` e
`RankedCategory` nos DTOs, na moeda da família.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Contrato, mocks e cobertura**

- Regenerar o contrato; o handler de `GET /dashboard` do MSW calcula os mesmos campos (pode usar uma
  versão simples: sem agrupar subcategoria se não houver nenhuma no mock).
- Run: `./gradlew build`
- Expected: BUILD SUCCESSFUL, com o pacote `br.com.liberdadefamilia.ledger.domain` ≥ 90% de linhas
  no relatório do JaCoCo (critério S4).

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(ledger): month dashboard with income, result, daily balance and category ranking"
```

---

### Task 6: Rotas sob demanda e orçamento de chunks

**Files:**
- Create: `web/scripts/chunk-budget.mjs`, `web/scripts/check-chunks.mjs`,
  `web/scripts/chunk-budget.test.mjs`
- Modify: `web/src/app/routes.tsx`, `web/src/app/routes.test.tsx`, `web/vite.config.ts`,
  `web/vitest.config.ts` (ou o bloco `test` do `vite.config.ts`, onde ele estiver),
  `web/package.json`, `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: nada de tasks anteriores.
- Produces:
  - `chunk-budget.mjs` (módulo puro, sem ler disco):
    - `export const LIMITS = { initialKb: 190, chunkKb: 260 }` (D18; a Step 5 pode baixá-los);
    - `export function initialFiles(manifest)` → `string[]`: o arquivo da entrada (`isEntry`) e,
      recursivamente, os de `imports`; nunca os de `dynamicImports`; inclui os `css` dessas
      entradas;
    - `export function checkBudget(manifest, gzipSize, limits = LIMITS)` →
      `{ ok: boolean, rows: Array<{ file: string, kb: number, limitKb: number, initial: boolean }> }`.
      `gzipSize(file)` devolve bytes; a carga inicial é uma linha só, com a soma; cada chunk JS fora
      dela é uma linha com o limite `chunkKb`. KB = bytes / 1024, com uma casa.
  - `check-chunks.mjs`: lê `dist/.vite/manifest.json`, mede com `zlib.gzipSync(…, { level: 9 })`,
    imprime a tabela (`console.table`) e sai com código 1 se `ok` for falso.
  - Script `"check:chunks": "node scripts/check-chunks.mjs"` no `package.json` do web.
  - Rotas com `lazy` (react-router 8, `lazy: () => import(...).then((m) => ({ Component: m.X }))`):
    `/lancamentos`, `/mais` e os filhos que as Tasks 9 a 11 criam (`/mais/contas`,
    `/mais/categorias`, `/painel`). Continuam estáticas, porque são o caminho do primeiro gasto:
    `/entrar`, `/boas-vindas`, `/inicio`, `/lancar` e os layouts.

**Regras:**
- `build.manifest: true` no `vite.config.ts`. Nada de `manualChunks`.
- No `ci.yml`, job `web`: um passo `- run: pnpm --filter web check:chunks` logo depois de
  `pnpm --filter web build`. Nenhuma action nova.
- O `include` do Vitest ganha `scripts/**/*.test.mjs`. O script não entra no relatório de
  cobertura de `src/**`.

- [ ] **Step 1: Escrever os testes (falhando)**

`chunk-budget.test.mjs` (manifesto de mentira, tamanhos por um `Map`):

| Teste | Esperado |
|---|---|
| `followsStaticImportsOnly` | entrada importa `a.js`, que importa `b.js`; entrada importa dinamicamente `c.js` → `initialFiles` = entrada, `a.js`, `b.js` e o CSS da entrada; sem `c.js` |
| `sumsInitialLoad` | entrada 100 KB e `a.js` 80 KB → uma linha `initial` com 180 KB e `ok` |
| `failsOverInitialLimit` | entrada 150 KB e `a.js` 50 KB → `ok` falso |
| `failsOverChunkLimit` | `c.js` (dinâmico) com 270 KB → `ok` falso e a linha de `c.js` com `limitKb` 260 |
| `ignoresSharedImportTwice` | `a.js` e `b.js` importam `x.js` → `x.js` conta uma vez |

`routes.test.tsx` ganha:

| Teste | Esperado |
|---|---|
| `lazyRoutesHaveNoStaticComponent` | as rotas `/lancamentos` e `/mais` têm `lazy` e não têm `Component` nem `element` |
| `firstExpensePathStaysStatic` | `/inicio` e `/lancar` têm `Component` e não têm `lazy` |
| `lazyRouteRenders` | com o router de memória em `/lancamentos`, o título "Lançamentos" aparece (`findByRole`) |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- scripts/chunk-budget.test.mjs src/app/routes.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pelas interfaces. Cada página que vira `lazy` exporta o componente com nome (já é assim).

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Medir e apertar o orçamento**

Run: `corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tabela impressa e código 0. Se a carga inicial ou o maior chunk ficar abaixo de 80% do
limite, troque o limite em `LIMITS` pela medição + 10%, arredondada para cima em KB inteiro, e
anote os dois números (medido e limite) no relatório da task, para a descrição do PR 2.

- [ ] **Step 6: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web format:check; corepack pnpm --filter web test:coverage`
Expected: tudo verde.

- [ ] **Step 7: Commit**

```bash
git add web .github/workflows/ci.yml
git commit -m "perf(web): load secondary routes on demand and enforce a chunk budget in CI"
```

---

### Task 7: Novo lançamento com receita e transferência

**Files:**
- Rename: `web/src/features/transactions/NewExpensePage.tsx` → `NewTransactionPage.tsx` (e o
  teste junto, com `git mv`)
- Create: `web/src/features/transactions/KindTabs.tsx`, `KindTabs.test.tsx`
- Create: `web/src/shared/offline/undo.ts`, `undo.test.ts`
- Create: `web/src/shared/api/mutations.ts`, `mutations.test.tsx`
- Modify: `web/src/features/transactions/CategoryPicker.tsx`, `ContextSheet.tsx`
- Modify: `web/src/shared/api/endpoints.ts`, `types.ts`, `index.ts`
- Modify: `web/src/shared/offline/outbox.ts`, `applySent.ts` (e os testes deles)
- Modify: `web/src/shared/i18n/dates.ts`, `dates.test.ts`, `messages.pt-BR.ts`
- Modify: `web/src/features/dashboard/HomePage.tsx`, `HomePage.test.tsx` (só os totais da fila)
- Modify: `web/src/app/routes.tsx` (o import renomeado)

**Interfaces:**
- Consumes: o contrato da Task 4 (`CreateTransactionRequest` com `toAccountId`; `DELETE
  /transactions/{id}`; `transaction.date-out-of-range`); `AccountView.archivedAt` (Task 2);
  `CategoryView.archivedAt` (Task 3).
- Produces:
  - `endpoints.ts`: `deleteTransaction(ws: string, id: string): Promise<void>`.
  - `mutations.ts`: o lugar dos hooks de escrita online. Nesta task, só
    `useDeleteTransaction(ws)`; as Tasks 8 a 10 acrescentam os outros.
  - `dates.ts`: `export const MIN_DATE = '2000-01-01'`; `maxDate(now: Date, timeZone: string):
    string` = hoje + 366 dias no fuso; `maxMonth(now, timeZone): string` (`yyyy-MM`);
    `MIN_MONTH = '2000-01'`; `addMonths(month: string, n: number): string`.
  - `outbox.ts`: `pendingTotals(items, month)` passa a devolver `{ monthIncome: bigint,
    monthExpenses: bigint, balanceDelta: bigint }`. Só itens `pending` contam; receita soma no
    `balanceDelta`, gasto subtrai, transferência não muda (as duas contas são ativas, porque
    arquivada não aparece para escolha).
  - `applySent.ts`: `applySentTransaction` trata `INCOME`, `EXPENSE` e `TRANSFER` (a transferência
    entra na lista do mês e não mexe em receitas nem gastos); depois invalida as consultas da
    família, como hoje.
  - `undo.ts`: `undoRecorded(input: { id: string, workspaceId: string, online: boolean, remove:
    (ws: string, id: string) => Promise<void> }): Promise<'undone' | 'offline' | 'failed'>`:
    1. `cancelOutboxItem(id)` deu certo → `'undone'`;
    2. senão, dentro de `withOutboxLock` (a mesma trava do envio, então nenhum envio está no meio):
       - item ainda na fila e `online` falso → `'offline'`, sem mexer na fila;
       - item ainda na fila e `online` → `discardOutboxItem(id)` e segue para o passo 3;
       - item fora da fila e `online` falso → `'offline'`;
    3. `remove(workspaceId, id)`; 204 ou 404 → `'undone'`; erro de rede → `'offline'`; outro erro
       → `'failed'`.
  - `KindTabs({ value, onChange })`: `role="tablist"` com três `tab`s, "Gasto", "Receita" e
    "Transferência", setas do teclado trocam a aba (padrão WAI-ARIA de abas com ativação
    automática), alvo de 44 px.
  - `/lancar` aceita `?tipo=receita` ou `?tipo=transferencia` e, na transferência, `?para=<id da
    conta>` (usado pelo "Pagar fatura" da Task 9). `?corrigir=<id>` continua como no Plano 2.

**Como fica:**
- Gasto e Receita: teclado de valor, `CategoryPicker` do tipo da aba e o `ContextSheet` (data e
  conta). Transferência: teclado, "De" e "Para" (duas listas de contas ativas; a mesma conta não
  aparece nas duas) e a data; sem categoria.
- Trocar de aba mantém o valor e a data e limpa a categoria; na Transferência, a conta lembrada vira
  o "De".
- "Salvar" só habilita com valor maior que zero e, conforme a aba, categoria escolhida ou duas
  contas diferentes. Com menos de duas contas ativas, a aba Transferência mostra o aviso e o link
  para `/mais/contas`, sem formulário.
- O seletor de data do `ContextSheet` usa `min = MIN_DATE` e `max = maxDate(agora, fuso da
  família)`. As opções "Hoje" e "Ontem" continuam.
- `CategoryPicker` e as listas de conta mostram só itens com `archivedAt` nulo.
- O aviso de salvo diz o tipo e oferece "Desfazer", que chama `undoRecorded` com
  `deleteTransaction` e mostra o resultado (D14).
- `HomePage` usa `monthIncome`, `monthExpenses` e `balanceDelta` da fila nos totais.

**Textos (`messages.pt-BR.ts`):**
- `transaction.kind.expense`: "Gasto"; `transaction.kind.income`: "Receita";
  `transaction.kind.transfer`: "Transferência"
- `transaction.transfer.from`: "De"; `transaction.transfer.to`: "Para"
- `transaction.transfer.needsTwoAccounts`: "Para transferir, cadastre outra conta."
  com o link "Cadastrar conta"
- `transaction.saved.expense`: "Gasto salvo." (substitui `expense.saved`);
  `transaction.saved.income`: "Receita salva."; `transaction.saved.transfer`: "Transferência salva."
- `transaction.undo`: "Desfazer"; `transaction.undone`: "Lançamento desfeito."
- `transaction.undoOffline`: "Sem internet agora. Para desfazer, exclua em Lançamentos quando a conexão voltar."
- `transaction.undoFailed`: "Não deu para desfazer. Tente excluir em Lançamentos."
- `problem.transaction.date-out-of-range`, `problem.transaction.transfer-invalid` e
  `problem.transaction.category-required`: os `detail` da Task 4, para a tela "Precisa de atenção".

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `dates.test.ts` | `maxDateUsesFamilyTimezone` | `2026-11-17T02:00:00Z` em `America/Fortaleza` → `2027-11-16` |
| `dates.test.ts` | `addMonthsCrossesYears` | `addMonths('2026-12', 1)` = `2027-01`; `addMonths('2000-01', -1)` = `1999-12` |
| `outbox.test.ts` | `pendingTotalsByKind` | receita 50, gasto 20 e transferência 30 pendentes no mês → `monthIncome` 5000n, `monthExpenses` 2000n, `balanceDelta` 3000n |
| `outbox.test.ts` | `needsAttentionDoesNotCount` | item `needs-attention` fica fora dos três totais |
| `applySent.test.ts` | `transferJoinsListOnly` | transferência enviada entra na lista em cache; `income` e `expenses` do painel em cache não mudam |
| `undo.test.ts` | `cancelsWhileQueued` | item `pending` sem tentativas → `'undone'`, sem chamar `remove` |
| `undo.test.ts` | `deletesWhenAlreadySent` | item fora da fila, online → chama `remove` uma vez → `'undone'` |
| `undo.test.ts` | `notFoundCountsAsUndone` | `remove` rejeita com `ApiError` 404 → `'undone'` |
| `undo.test.ts` | `offlineAfterSend` | item fora da fila, offline → `'offline'`, sem `remove` |
| `undo.test.ts` | `offlineMidSendKeepsItem` | item na fila com 1 tentativa, offline → `'offline'` e o item continua na fila |
| `undo.test.ts` | `onlineMidSendDiscardsThenDeletes` | item na fila com 1 tentativa, online → sai da fila e `remove` é chamado |
| `KindTabs.test.tsx` | `arrowKeysMoveSelection` | foco em "Gasto", seta para a direita → "Receita" selecionada e focada |
| `NewTransactionPage.test.tsx` | `recordsIncome` | aba Receita, 5000,00, Salário, Salvar → fila com `kind` `INCOME` e aviso "Receita salva." |
| `NewTransactionPage.test.tsx` | `recordsTransfer` | aba Transferência, 500,00, De Carteira, Para Banco → fila com `kind` `TRANSFER`, `toAccountId` do Banco e `categoryId` nulo |
| `NewTransactionPage.test.tsx` | `transferNeedsTwoAccounts` | família só com a Carteira → aba Transferência mostra o aviso e o link para `/mais/contas` |
| `NewTransactionPage.test.tsx` | `sameAccountNotOffered` | De Carteira → a lista "Para" não tem a Carteira |
| `NewTransactionPage.test.tsx` | `opensTransferToCard` | `/lancar?tipo=transferencia&para=<cartão>` → aba Transferência com o cartão em "Para" |
| `NewTransactionPage.test.tsx` | `switchingTabKeepsAmount` | 87,90 em Gasto com Mercado, troca para Receita → valor 87,90 e nenhuma categoria marcada |
| `NewTransactionPage.test.tsx` | `datePickerBounds` | o campo de data tem `min` `2000-01-01` e `max` = hoje + 366 no fuso |
| `NewTransactionPage.test.tsx` | `archivedHidden` | conta e categoria arquivadas no MSW não aparecem para escolha |
| `NewTransactionPage.test.tsx` | `undoAfterSendDeletes` | salvar, deixar a fila enviar (relógio falso), "Desfazer" → `DELETE` no MSW e "Lançamento desfeito." |
| `NewTransactionPage.test.tsx` | `undoAfterSendOffline` | o mesmo, offline na hora do "Desfazer" → o texto `transaction.undoOffline` |
| `HomePage.test.tsx` | `queuedIncomeRaisesBalance` | receita de 100 na fila → saldo total +100 |

Os testes do Plano 2 que usam `expense.saved` passam a usar `transaction.saved.expense`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/transactions src/shared/offline src/shared/i18n src/features/dashboard`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pelas interfaces e pelo "Como fica". O tipo do item da fila continua `'transaction.create'`; o
payload já é o `CreateTransactionRequest` regenerado, então o Dexie não muda de versão.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): record income and transfers, and undo a saved transaction"
```

---

### Task 8: Lançamentos: mês, filtros, editar e excluir

**Files:**
- Create: `web/src/shared/ui/MonthSwitcher.tsx`, `MonthSwitcher.test.tsx` (exportado em
  `shared/ui/index.ts`; o painel da Task 11 também usa)
- Create em `web/src/features/transactions/`: `TransactionFilters.tsx`,
  `TransactionDetailSheet.tsx`, `VersionConflictDialog.tsx` e os testes de cada um
- Modify: `web/src/features/transactions/TransactionsPage.tsx`, `TransactionRow.tsx`,
  `listItems.ts`, `NewTransactionPage.tsx` (modo de edição) e os testes
- Modify: `web/src/shared/api/errors.ts`, `errors.test.ts`, `endpoints.ts`, `hooks.ts`,
  `queryKeys.ts`, `mutations.ts`
- Modify: `web/src/shared/i18n/messages.pt-BR.ts`

**Interfaces:**
- Consumes: Task 4 (`PATCH`, `DELETE`, `restore`, filtros, `version`, `current`); `dates.ts`,
  `mutations.ts` e `useDeleteTransaction` (Task 7).
- Produces:
  - `ApiError` ganha `readonly extra: Readonly<Record<string, unknown>>`: as propriedades do
    problema além de `type`, `title`, `status`, `detail`, `code` e `instance`. Vazio quando não há.
  - `endpoints.ts`: `listTransactions(ws, { month, categoryId?, accountId? })`,
    `updateTransaction(ws, id, body: UpdateTransactionRequest): Promise<TransactionView>`,
    `restoreTransaction(ws, id): Promise<TransactionView>`.
  - `queryKeys.transactions(ws, month, filters?)`: os filtros entram na chave; invalidar
    `['ws', ws]` continua pegando tudo.
  - `useTransactions(ws, month, filters?)`.
  - `mutations.ts`: `useUpdateTransaction(ws)` e `useRestoreTransaction(ws)`; as três mutações de
    lançamento invalidam `['ws', ws]` ao terminar.
  - `MonthSwitcher({ month, onChange, min = MIN_MONTH, max })`: "‹", o mês por extenso ("Novembro
    de 2026") e "›"; botões desabilitados nos limites; `aria-label` "Mês anterior" e "Próximo mês".
  - `TransactionsPage` lê e grava o estado na URL: `?mes=yyyy-MM&categoria=<id>&conta=<id>`. Sem
    `mes`, é o mês atual no fuso da família.
  - `/lancar?editar=<id>&mes=yyyy-MM` abre o `NewTransactionPage` em modo de edição.

**Como fica:**
- A lista mostra o `MonthSwitcher` (de `MIN_MONTH` a `maxMonth`), o botão "Filtros" e, com filtro
  ativo, um chip por filtro com "Limpar". `TransactionFilters` é um `Sheet` com duas listas,
  "Categoria" (de primeiro nível e subcategorias, ativas e arquivadas, as arquivadas marcadas como
  "(arquivada)") e "Conta" (idem).
- Linha de transferência (D22): "↔ Transferência", valor sem sinal na cor do texto e
  "Origem → Destino". Nomes de conta e categoria arquivadas continuam aparecendo.
- Tocar numa linha que já está no servidor abre o `TransactionDetailSheet`: tipo, valor, data,
  conta (ou origem e destino), categoria, descrição e quem lançou; botões "Editar" e "Excluir".
  Linhas da fila continuam com o comportamento do Plano 2.
- Offline, "Editar" e "Excluir" ficam desabilitados com o texto `transactions.offlineEditing`, e o
  topo da lista mostra o mesmo aviso (wireframe 6).
- "Editar" leva a `/lancar?editar=<id>&mes=…`. O formulário vem preenchido, com a aba do tipo atual,
  e aceita trocar de aba (D10). A conta e a categoria atuais aparecem mesmo arquivadas, marcadas
  como "(arquivada)"; as outras arquivadas não. "Salvar" manda o `PATCH` com todos os campos e a
  `version` que abriu; deu certo → volta para a lista do mês e mostra "Lançamento atualizado.".
- 409 `transaction.version-conflict` → `VersionConflictDialog` (D12): mostra os campos de
  `error.extra.current` que diferem dos que a pessoa salvou e oferece "Ficar com a versão atual"
  (descarta e volta para a lista) e "Salvar a minha mesmo assim" (reenvia com `current.version`).
  Foco inicial em "Ficar com a versão atual".
- 409 `transaction.deleted` ou 404 → diálogo "Este lançamento foi excluído." com "Voltar para a
  lista".
- "Excluir" → `ConfirmDialog` "Excluir este lançamento?" / "Você pode desfazer logo depois." →
  `DELETE` → aviso "Lançamento excluído." com "Desfazer", que chama `restore` e avisa
  "Lançamento restaurado.". Sem conexão na hora do "Desfazer" → `transactions.restoreOffline`.

**Textos:**
- `transactions.filters`: "Filtros"; `transactions.filter.category`: "Categoria";
  `transactions.filter.account`: "Conta"; `transactions.filter.clear`: "Limpar";
  `transactions.filter.archived`: "(arquivada)"
- `transactions.transfer`: "Transferência"
- `transactions.offlineEditing`: "Sem conexão. Lançar funciona; editar volta com o sinal."
- `transactions.edit`: "Editar"; `transactions.delete`: "Excluir"
- `transactions.updated`: "Lançamento atualizado."
- `transactions.deleteConfirm.title`: "Excluir este lançamento?";
  `transactions.deleteConfirm.body`: "Você pode desfazer logo depois.";
  `transactions.deleted`: "Lançamento excluído."; `transactions.restored`: "Lançamento restaurado."
- `transactions.restoreOffline`: "Sem internet agora. Tente desfazer quando a conexão voltar."
- `conflict.title`: "Este lançamento mudou"; `conflict.body`: "Alguém salvou uma versão diferente
  enquanto você editava."; `conflict.keepTheirs`: "Ficar com a versão atual";
  `conflict.keepMine`: "Salvar a minha mesmo assim"
- `deletedElsewhere.title`: "Este lançamento foi excluído."; `deletedElsewhere.back`: "Voltar para a lista"
- `month.previous`: "Mês anterior"; `month.next`: "Próximo mês"

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `errors.test.ts` | `keepsExtraProperties` | problema com `current` → `error.extra.current` igual ao enviado; `code` e `detail` fora de `extra` |
| `MonthSwitcher.test.tsx` | `stopsAtBounds` | em `MIN_MONTH`, "Mês anterior" desabilitado; em `max`, "Próximo mês" desabilitado |
| `MonthSwitcher.test.tsx` | `namesTheMonth` | `2026-11` → "Novembro de 2026" |
| `TransactionsPage.test.tsx` | `navigatesMonths` | "Mês anterior" → a URL ganha `mes` do mês anterior e a lista mostra os lançamentos dele |
| `TransactionsPage.test.tsx` | `filtersFromUrl` | `?categoria=<Mercado>` → só os lançamentos de Mercado e das filhas; chip "Mercado" com "Limpar" |
| `TransactionsPage.test.tsx` | `transferRow` | transferência Carteira → Banco de 500 → "↔ Transferência", "R$ 500,00" sem sinal e "Carteira → Banco" |
| `TransactionsPage.test.tsx` | `archivedNamesStay` | lançamento numa conta arquivada mostra o nome dela |
| `TransactionsPage.test.tsx` | `offlineDisablesEditing` | offline → aviso no topo; no detalhe, "Editar" e "Excluir" desabilitados |
| `TransactionsPage.test.tsx` | `deleteAndUndo` | Excluir → confirmar → some da lista e aviso "Lançamento excluído."; "Desfazer" → volta e "Lançamento restaurado." |
| `NewTransactionPage.test.tsx` | `editsAmount` | `?editar=<id>` → valor preenchido; muda para 90,00 e salva → `PATCH` com a `version` aberta; volta à lista com "Lançamento atualizado." |
| `NewTransactionPage.test.tsx` | `editKeepsArchivedAccount` | lançamento numa conta arquivada → ela aparece marcada "(arquivada)" e salvar mudando só a descrição funciona |
| `NewTransactionPage.test.tsx` | `editTurnsExpenseIntoTransfer` | troca para Transferência, escolhe Para → `PATCH` com `kind` `TRANSFER` e `categoryId` nulo |
| `VersionConflictDialog.test.tsx` | `showsDifferences` | `current` com outro valor → o diálogo mostra o valor atual; os campos iguais não aparecem |
| `NewTransactionPage.test.tsx` | `conflictKeepTheirs` | o MSW responde 409 `version-conflict` → diálogo; "Ficar com a versão atual" → volta para a lista sem novo `PATCH` |
| `NewTransactionPage.test.tsx` | `conflictKeepMine` | "Salvar a minha mesmo assim" → segundo `PATCH` com `current.version` → "Lançamento atualizado." |
| `NewTransactionPage.test.tsx` | `editDeletedElsewhere` | o MSW responde 409 `transaction.deleted` → "Este lançamento foi excluído." e "Voltar para a lista" |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/transactions src/shared/api src/shared/ui`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pelas interfaces e pelo "Como fica". `listItems.ts` ganha o rótulo da transferência e a busca do
nome da conta de destino.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): browse transactions by month and filter, edit with conflict handling, delete with undo"
```

---

### Task 9: Contas

**Files:**
- Create em `web/src/features/accounts/`: `AccountsPage.tsx`, `AccountForm.tsx`,
  `AdjustBalanceSheet.tsx`, `accountTypes.ts` e os testes de cada um
- Modify: `web/src/app/routes.tsx` (`/mais/contas`, com `lazy`), `web/src/app/MorePage.tsx`
- Modify: `web/src/shared/api/endpoints.ts`, `mutations.ts`, `types.ts`
- Modify: `web/src/shared/money/money.ts`, `money.test.ts`
- Modify: `web/src/shared/i18n/messages.pt-BR.ts`

**Interfaces:**
- Consumes: Task 2 (`AccountView` com `initialBalance` e `archivedAt`; `createAccount`,
  `updateAccount` com `balance`, `archiveAccount`, `unarchiveAccount`; os códigos `account.*`);
  `/lancar?tipo=transferencia&para=<id>` (Task 7).
- Produces:
  - `endpoints.ts`: `createAccount(ws, body)`, `updateAccount(ws, id, body)`, `archiveAccount(ws,
    id)`, `unarchiveAccount(ws, id)`, todos devolvendo `AccountView`.
  - `mutations.ts`: `useCreateAccount(ws)`, `useUpdateAccount(ws)`, `useArchiveAccount(ws)`,
    `useUnarchiveAccount(ws)`; invalidam `['ws', ws]`.
  - `money.ts`: `negateCents(cents: bigint): bigint` e `MIN_CENTS = -99_999_999_999n`. O teclado
    continua só positivo; o sinal vem de um controle à parte.
  - `accountTypes.ts`: `ACCOUNT_TYPES`, em ordem: `CHECKING`, `SAVINGS`, `CASH`, `CREDIT_CARD`,
    `WALLET`, `INVESTMENT`, cada um com `labelId`, `hintId` e um ícone (emoji).

**Como fica:**
- `/mais/contas` lista as contas ativas (ícone do tipo, nome, tipo e saldo com `AmountText`) e, num
  bloco recolhido "Arquivadas (N)", as arquivadas com "Reativar". O Mais ganha o link "Contas".
- "Nova conta" abre um `Sheet` com o `AccountForm`: nome, tipo (grupo de rádio com a dica do tipo
  escolhido) e saldo inicial (teclado de valor e o interruptor "Saldo negativo"). A moeda é a da
  família e não aparece como campo.
- Tocar numa conta abre o `Sheet` de edição: nome e tipo; "Acertar saldo"; "Arquivar"; no cartão de
  crédito, também "Pagar fatura", que leva a `/lancar?tipo=transferencia&para=<id>` (D23).
- Na conta de investimento, "Acertar saldo" é o primeiro botão e tem destaque (D5).
- `AdjustBalanceSheet`: "Qual é o saldo hoje?", com teclado e "Saldo negativo"; salvar manda
  `PATCH` com `balance` e mostra "Saldo atualizado.".
- "Arquivar" → `ConfirmDialog`. Com saldo diferente de zero, o corpo avisa
  `accounts.archive.bodyWithBalance`; sem saldo, `accounts.archive.body`.
- Erros: `account.name-taken` aparece no campo nome; `account.last-active` e os outros, como texto
  de erro no `Sheet`, com o `detail` da API.
- Offline: os botões de escrita ficam desabilitados com `accounts.offline`.

**Textos:**
- `more.accounts`: "Contas"; `accounts.title`: "Contas"; `accounts.new`: "Nova conta"
- `accounts.archived`: "Arquivadas ({count})"; `accounts.unarchive`: "Reativar"
- `accounts.field.name`: "Nome"; `accounts.field.type`: "Tipo";
  `accounts.field.initialBalance`: "Saldo inicial"; `accounts.field.negative`: "Saldo negativo"
- Tipos: "Conta corrente", "Poupança", "Dinheiro", "Cartão de crédito", "Carteira digital",
  "Investimento"
- Dicas: corrente "Para o dia a dia: salário, contas, Pix."; poupança "Dinheiro guardado que rende
  pouco e sai fácil."; dinheiro "Notas e moedas na mão."; cartão "Os gastos deixam o saldo negativo.
  Pagar a fatura é uma transferência de outra conta para o cartão."; carteira digital "Saldo em app
  de pagamento, como carteiras de celular."; investimento "Atualize o saldo à mão quando quiser,
  com Acertar saldo."
- `accounts.adjust`: "Acertar saldo"; `accounts.adjust.question`: "Qual é o saldo hoje?";
  `accounts.adjusted`: "Saldo atualizado."
- `accounts.payBill`: "Pagar fatura"
- `accounts.archive`: "Arquivar"; `accounts.archive.title`: "Arquivar {name}?";
  `accounts.archive.body`: "Ela some das escolhas. Os lançamentos continuam no histórico, e você
  pode reativar depois."; `accounts.archive.bodyWithBalance`: "O saldo de {amount} sai do total da
  família. Os lançamentos continuam no histórico, e você pode reativar depois."
- `accounts.created`: "Conta criada."; `accounts.saved`: "Conta salva.";
  `accounts.archivedToast`: "Conta arquivada."; `accounts.unarchivedToast`: "Conta reativada."
- `accounts.offline`: "Sem conexão. Mudar contas volta com o sinal."

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `money.test.ts` | `negateCents` | `negateCents(100n)` = `-100n`; `formatBRL(-100n)` = "−R$ 1,00" |
| `AccountsPage.test.tsx` | `listsActiveAndArchived` | duas ativas com saldo e uma arquivada dentro de "Arquivadas (1)" |
| `AccountsPage.test.tsx` | `createsAccount` | Nova conta, "Corrente", Conta corrente, 1000,00 → `POST` com `initialBalance` `1000.00` e moeda da família; aviso "Conta criada." |
| `AccountsPage.test.tsx` | `createsNegativeBalance` | 250,00 com "Saldo negativo" → `initialBalance` `-250.00` |
| `AccountsPage.test.tsx` | `nameTakenOnField` | o MSW responde 409 `account.name-taken` → o texto aparece ligado ao campo nome (`aria-describedby`) |
| `AccountsPage.test.tsx` | `adjustsBalance` | Acertar saldo, 4265,44 → `PATCH` só com `balance` `4265.44`; "Saldo atualizado." |
| `AccountsPage.test.tsx` | `investmentLeadsWithAdjust` | numa conta Investimento, "Acertar saldo" é o primeiro botão |
| `AccountsPage.test.tsx` | `archiveWarnsAboutBalance` | arquivar conta com saldo R$ 200,00 → o corpo cita "R$ 200,00" |
| `AccountsPage.test.tsx` | `lastActiveExplains` | o MSW responde 422 `account.last-active` → o `detail` aparece |
| `AccountsPage.test.tsx` | `unarchives` | Reativar → `POST …/unarchive` e "Conta reativada." |
| `AccountsPage.test.tsx` | `payBillOpensTransfer` | no cartão, "Pagar fatura" → navega para `/lancar?tipo=transferencia&para=<id>` |
| `AccountsPage.test.tsx` | `offlineDisablesWrites` | offline → "Nova conta" desabilitado e o aviso aparece |
| `AccountForm.test.tsx` | `showsTypeHint` | escolher Cartão de crédito → a dica do cartão aparece |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/accounts src/shared/money`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pelas interfaces e pelo "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): manage accounts, adjust balances and pay the card bill"
```

---

### Task 10: Categorias

**Files:**
- Create em `web/src/features/categories/`: `CategoriesPage.tsx`, `CategoryForm.tsx`,
  `categoryStyle.ts` e os testes de cada um
- Modify: `web/src/app/routes.tsx` (`/mais/categorias`, com `lazy`), `web/src/app/MorePage.tsx`
- Modify: `web/src/features/transactions/CategoryPicker.tsx` (o ponto de cor no chip)
- Modify: `web/src/shared/api/endpoints.ts`, `mutations.ts`, `types.ts`
- Modify: `web/src/shared/i18n/messages.pt-BR.ts`

**Interfaces:**
- Consumes: Task 3 (`CategoryView` com `color`, `parentId` e `archivedAt`; `createCategory`,
  `updateCategory`, `archiveCategory`, `unarchiveCategory`; os códigos `category.*`).
- Produces:
  - `endpoints.ts`: `createCategory(ws, body)`, `updateCategory(ws, id, body)`,
    `archiveCategory(ws, id)`, `unarchiveCategory(ws, id)`, todos devolvendo `CategoryView`.
  - `mutations.ts`: `useCreateCategory(ws)`, `useUpdateCategory(ws)`, `useArchiveCategory(ws)`,
    `useUnarchiveCategory(ws)`; invalidam `['ws', ws]`.
  - `categoryStyle.ts`:
    - `CATEGORY_COLORS`, na ordem `TEAL`, `BLUE`, `INDIGO`, `PURPLE`, `PINK`, `RED`, `ORANGE`,
      `GREEN`, cada um com `labelId` e a classe do ponto (`bg-…`, nos dois temas). O ponto é
      decorativo (o chip sempre tem ícone e nome), mas cada cor tem contraste de 3:1 ou mais com
      `surface`, nos dois temas;
    - `CATEGORY_ICONS`: os 24 emojis abaixo, na ordem, cada um com o nome em pt-BR para o leitor de
      tela;
    - `categoryTree(categories, kind)` → `Array<{ category: CategoryView, children: CategoryView[] }>`,
      na ordem que a API devolve, com os filhos sob o pai.

**Ícones (`CATEGORY_ICONS`):** 🛒 Mercado, 🍽️ Restaurante, 🏠 Casa, 💡 Energia, 🚗 Carro,
🚌 Ônibus, ⛽ Combustível, 💊 Remédio, 🏥 Saúde, 🎓 Escola, 📚 Livros, 👕 Roupas, 🎁 Presente,
🎮 Jogos, 🎬 Cinema, ✈️ Viagem, 🐾 Pet, 👶 Criança, 💇 Beleza, 📱 Celular, 🧾 Contas,
💰 Dinheiro, 💼 Trabalho, 📈 Investimentos.

**Cores (`labelId` → texto):** Verde-azulado, Azul, Índigo, Roxo, Rosa, Vermelho, Laranja, Verde.

**Como fica:**
- `/mais/categorias` tem as abas "Gastos" e "Receitas" (o mesmo padrão de abas da Task 7). Cada aba
  lista as categorias ativas de primeiro nível, com as subcategorias recuadas embaixo, e um bloco
  recolhido "Arquivadas (N)". O Mais ganha o link "Categorias".
- No bloco de arquivadas, a categoria de primeiro nível tem "Reativar" e mostra as filhas
  arquivadas junto, sem botão próprio (reativar o pai reativa as filhas, Task 3). Uma filha
  arquivada com o pai ativo tem o próprio "Reativar".
- "Nova categoria" abre o `CategoryForm` num `Sheet`: nome, ícone (grade de rádio com os 24
  emojis), cor (grade de rádio com "Sem cor" e as 8 cores, cada uma com o nome visível no foco e no
  `aria-label`) e "Dentro de" (opcional: "Nenhuma" ou uma categoria ativa de primeiro nível do tipo
  da aba). O tipo vem da aba.
- Tocar numa categoria abre o mesmo formulário para editar nome, ícone e cor; tipo e pai aparecem
  como texto, porque não mudam (D7). Se o ícone atual não está na lista, ele aparece como primeira
  opção da grade. Botão "Arquivar" no rodapé.
- "Arquivar" → `ConfirmDialog`; com filhas ativas, o corpo diz quantas vão junto.
- Erros: `category.name-taken` aparece no campo nome; `category.last-active`,
  `category.parent-invalid` e `category.parent-archived`, com o `detail` da API.
- O chip do `CategoryPicker` ganha o ponto da cor antes do ícone, quando a categoria tem cor.
- Offline: os botões de escrita ficam desabilitados com `categories.offline`.

**Textos:**
- `more.categories`: "Categorias"; `categories.title`: "Categorias";
  `categories.tab.expense`: "Gastos"; `categories.tab.income`: "Receitas"
- `categories.new`: "Nova categoria"; `categories.archived`: "Arquivadas ({count})";
  `categories.unarchive`: "Reativar"
- `categories.field.name`: "Nome"; `categories.field.icon`: "Ícone";
  `categories.field.color`: "Cor"; `categories.color.none`: "Sem cor";
  `categories.field.parent`: "Dentro de"; `categories.parent.none`: "Nenhuma"
- `categories.archive`: "Arquivar"; `categories.archive.title`: "Arquivar {name}?";
  `categories.archive.body`: "{children, plural, =0 {} one {A subcategoria também será arquivada. } other {As # subcategorias também serão arquivadas. }}Os lançamentos continuam no histórico, e você pode reativar depois."
- `categories.created`: "Categoria criada."; `categories.saved`: "Categoria salva.";
  `categories.archivedToast`: "Categoria arquivada."; `categories.unarchivedToast`: "Categoria reativada."
- `categories.offline`: "Sem conexão. Mudar categorias volta com o sinal."

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `categoryStyle.test.ts` | `treeNestsChildren` | Mercado com a filha Feira e Lazer sem filhas → duas entradas; Feira dentro de Mercado |
| `categoryStyle.test.ts` | `eightColorsInOrder` | `CATEGORY_COLORS` tem as 8 chaves na ordem da Task 3; `CATEGORY_ICONS` tem 24 itens sem repetição |
| `CategoriesPage.test.tsx` | `tabsSplitByKind` | aba Gastos mostra Mercado e não Salário; aba Receitas mostra Salário |
| `CategoriesPage.test.tsx` | `createsSubcategory` | Nova categoria, "Feira", 🛒, Verde, Dentro de Mercado → `POST` com `kind` `EXPENSE`, `icon` "🛒", `color` `GREEN`, `parentId` de Mercado; "Categoria criada." |
| `CategoriesPage.test.tsx` | `parentOptionsAreTopLevelSameKind` | a lista "Dentro de" na aba Gastos não tem subcategorias nem categorias de receita |
| `CategoriesPage.test.tsx` | `editKeepsKindAndParent` | editar Feira → nome, ícone e cor editáveis; "Mercado" aparece como texto; salvar manda `PATCH` sem `parentId` |
| `CategoriesPage.test.tsx` | `archiveCountsChildren` | arquivar Mercado com 2 filhas ativas → o corpo diz "As 2 subcategorias também serão arquivadas." |
| `CategoriesPage.test.tsx` | `archivedParentCarriesChildren` | Mercado e Feira arquivadas → só Mercado tem "Reativar"; Feira aparece embaixo |
| `CategoriesPage.test.tsx` | `lastActiveExplains` | o MSW responde 422 `category.last-active` → o `detail` aparece |
| `CategoriesPage.test.tsx` | `nameTakenOnField` | o MSW responde 409 `category.name-taken` → o texto ligado ao campo nome |
| `CategoriesPage.test.tsx` | `offlineDisablesWrites` | offline → "Nova categoria" desabilitado e o aviso aparece |
| `CategoryForm.test.tsx` | `iconGridIsRadioGroup` | a grade de ícones é um `radiogroup` com 24 `radio`s nomeados ("Mercado", "Restaurante"…) |
| `CategoryForm.test.tsx` | `unknownIconComesFirst` | categoria com ícone 🧺 → 🧺 é a primeira opção e está marcada |
| `CategoryPicker.test.tsx` | `chipShowsColorDot` | categoria com cor `BLUE` → o chip tem o ponto (`aria-hidden`) antes do ícone; sem cor, não tem |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/categories src/features/transactions/CategoryPicker.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pelas interfaces e pelo "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): manage categories and subcategories with icon and color"
```

---

### Task 11: Painel do mês

**Files:**
- Create em `web/src/features/dashboard/`: `DashboardPage.tsx`, `EChart.tsx`, `charts.ts` e os
  testes de cada um
- Modify: `web/src/features/dashboard/HomePage.tsx`, `HomePage.test.tsx`
- Modify: `web/src/app/routes.tsx` (`/painel`, com `lazy`), `web/src/app/MorePage.tsx`
- Modify: `web/src/shared/i18n/messages.pt-BR.ts`
- Modify: `web/package.json`, `pnpm-lock.yaml` (`echarts` 6.1.0)

**Interfaces:**
- Consumes: `DashboardView` com `income`, `result`, `byCategory` e `dailyBalance` (Task 5);
  `MonthSwitcher`, `MIN_MONTH`, `maxMonth`, `addMonths` (Tasks 7 e 8); `usePendingTotals(userId, ws, month)`, que já existe, com o formato novo da Task 7;
  `?mes=&categoria=` da lista (Task 8); `useDashboard(ws, month)`, que já existe.
- Produces:
  - Dependência: `corepack pnpm --filter web add --save-exact echarts@6.1.0`. Só o
    `DashboardPage` importa o `EChart`, e o `EChart` é o único arquivo que importa `echarts`;
    assim o ECharts fica no chunk do `/painel` (a Task 6 mede).
  - `charts.ts` (puro, sem DOM nem `echarts`):
    - `CHART_PALETTE`: as 6 cores categóricas do design system, em ordem, e `OTHERS_COLOR`;
    - `categorySlices(byCategory, categories)` →
      `Array<{ id: string | null, label: string, current: bigint, previous: bigint, color: string }>`,
      com a cor por posição (D8) e "Outras" quando `categoryId` é nulo;
    - `donutOption(slices, opts)`, `comparisonOption(slices, monthLabels, opts)` e
      `balanceLineOption(days, opts)` → objetos de opção do ECharts; `opts = { animate: boolean,
      textColor: string }`. Valores em reais (centavos / 100) só dentro das opções; os rótulos
      usam `formatBRL`.
  - `EChart({ option, height, onItemClick? })`: `echarts/core` com `PieChart`, `LineChart`,
    `BarChart`, `GridComponent`, `DatasetComponent` e `SVGRenderer` (D19); `init` no mount,
    `setOption` quando a opção muda, `resize` por `ResizeObserver`, `dispose` no unmount; o
    contêiner tem `aria-hidden="true"`. `onItemClick` recebe o `id` da fatia.
  - `/painel?mes=yyyy-MM`.

**Como fica (wireframe 5):**
- Topo: `MonthSwitcher` (de `MIN_MONTH` a `maxMonth`), que grava `mes` na URL.
- Cartões: "Saldo total", "Receitas", "Gastos" e "Resultado do mês", com `AmountText`. No mês
  atual, somam a fila como o Início (Task 7) e mostram a nota `dashboard.pendingNote` quando há
  item pendente; nos outros meses, a fila não entra.
- "Gastos por categoria": a rosca e, ao lado ou embaixo, a lista das fatias (ponto da cor, nome,
  valor e porcentagem). Cada item da lista é um link para `/lancamentos?mes=…&categoria=<id>`;
  "Outras" não é link. Tocar na fatia da rosca faz o mesmo.
- "vs. {mês anterior}": barras agrupadas, mês atual e anterior, por categoria.
- "Saldo no mês": linha com um ponto por dia.
- Cada gráfico tem, embaixo, `<details>` com "Ver tabela" e a tabela equivalente (`<table>` com
  `<caption>` e cabeçalhos), que é o que o leitor de tela usa.
- Sem gasto no mês: os dois gráficos de categoria dão lugar ao texto `dashboard.noExpenses`; a linha
  do saldo continua.
- `prefers-reduced-motion: reduce` → `animate` falso. A cor do texto dos eixos vem do token `text`
  do tema atual.
- Offline: mostra o cache com `DataAsOf`; sem cache, o texto `home.offlineNoData`.
- Início: o cartão do mês ganha o link "Ver painel do mês" para `/painel`. Mais: o link "Painel do
  mês".

**Textos:**
- `dashboard.title`: "Painel do mês"; `home.openDashboard`: "Ver painel do mês";
  `more.dashboard`: "Painel do mês"
- `dashboard.balance`: "Saldo total"; `dashboard.income`: "Receitas"; `dashboard.expenses`:
  "Gastos"; `dashboard.result`: "Resultado do mês"
- `dashboard.byCategory`: "Gastos por categoria"; `dashboard.vsPrevious`: "vs. {month}";
  `dashboard.dailyBalance`: "Saldo no mês"; `dashboard.others`: "Outras"
- `dashboard.showTable`: "Ver tabela"; `dashboard.table.category`: "Categoria";
  `dashboard.table.amount`: "Valor"; `dashboard.table.share`: "Parte";
  `dashboard.table.day`: "Dia"; `dashboard.table.balance`: "Saldo"
- `dashboard.noExpenses`: "Nenhum gasto neste mês ainda."
- `dashboard.pendingNote`: "Inclui lançamentos que ainda não foram enviados."

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `charts.test.ts` | `colorsByPosition` | 7 linhas (a última com `categoryId` nulo) → cores `CHART_PALETTE[0..5]` e `OTHERS_COLOR`; rótulo da última "Outras" |
| `charts.test.ts` | `donutUsesSliceValues` | Mercado 1234,56 → a série tem o valor `1234.56` e o nome "Mercado" |
| `charts.test.ts` | `animationFollowsOption` | `animate: false` → `animation: false` nas três opções |
| `charts.test.ts` | `lineHasEveryDay` | 30 dias → 30 pontos no eixo X |
| `EChart.test.tsx` | `disposesOnUnmount` | com `echarts/core` simulado (`vi.mock`): `init` no mount, `dispose` no unmount, contêiner `aria-hidden` |
| `EChart.test.tsx` | `forwardsItemClick` | o evento `click` simulado com `data.id` → `onItemClick` recebe o id |
| `DashboardPage.test.tsx` | `prdExample` (PRD §7.1) | MSW com o cenário do `prdExampleThroughTheApi` (Task 5) → "R$ 4.765,44", "R$ 5.000,00", "R$ 1.234,56" e "R$ 3.765,44" nos quatro cartões; na tabela da rosca, Mercado com R$ 1.234,56 e 100% |
| `DashboardPage.test.tsx` | `monthFromUrl` | `?mes=2026-10` → o título do `MonthSwitcher` diz "Outubro de 2026" e o pedido vai com `month=2026-10` |
| `DashboardPage.test.tsx` | `categoryLinksToList` | o item Mercado da lista é um link para `/lancamentos?mes=…&categoria=<id de Mercado>` |
| `DashboardPage.test.tsx` | `tablesForScreenReaders` | três `table`s com `caption`; os contêineres dos gráficos estão `aria-hidden` |
| `DashboardPage.test.tsx` | `queuedOnlyInCurrentMonth` | gasto de 10 na fila → mês atual mostra Gastos +10 e a nota; mês anterior não |
| `DashboardPage.test.tsx` | `noExpensesMessage` | mês sem gasto → "Nenhum gasto neste mês ainda." e a linha do saldo continua |
| `DashboardPage.test.tsx` | `offlineShowsCache` | offline com cache → os valores e o `DataAsOf` |
| `HomePage.test.tsx` | `linksToDashboard` | o link "Ver painel do mês" aponta para `/painel` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/dashboard`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Pelas interfaces e pelo "Como fica". Os testes de página simulam o `EChart` (`vi.mock`), porque o
jsdom não desenha SVG do ECharts; o que importa na página são os números, as tabelas e os links.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde; o chunk do `/painel` dentro do limite de chunk e a carga inicial igual à da
Task 6 (o ECharts não entrou nela). Anote o tamanho do chunk do painel no relatório.

- [ ] **Step 6: Commit**

```bash
git add web/src web/package.json pnpm-lock.yaml
git commit -m "feat(web): month dashboard with category, comparison and balance charts"
```

---

### Task 12: Primeiros passos

**Files:**
- Create: `api/src/main/java/br/com/liberdadefamilia/workspace/FamilyActivity.java`
- Create: `api/src/main/java/br/com/liberdadefamilia/ledger/internal/LedgerFamilyActivity.java`
- Create: `api/src/main/java/br/com/liberdadefamilia/workspace/internal/GettingStartedController.java`
- Modify: `workspace/internal/MeController.java`, `MeView.java` (o `MeWorkspace`),
  `WorkspaceRepository.java`
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Create: `web/src/features/dashboard/GettingStartedCard.tsx`, `GettingStartedCard.test.tsx`
- Modify: `web/src/features/dashboard/HomePage.tsx`, `HomePage.test.tsx`,
  `web/src/shared/api/endpoints.ts`, `mutations.ts`, `web/src/shared/i18n/messages.pt-BR.ts`
- Test (novo): `api/src/test/java/br/com/liberdadefamilia/workspace/GettingStartedIT.java`

**Interfaces:**
- Consumes: a coluna `memberships.getting_started_dismissed_at` (V5, Task 2); `passkeyCreatedAt`
  em `MeUser`; `useAccounts` (com as arquivadas, Task 2); `useOutbox`.
- Produces:
  - `public interface FamilyActivity { boolean hasTransactions(UUID workspaceId); }` no pacote
    `workspace` (API pública do módulo). O `ledger` implementa com `LedgerFamilyActivity`
    (`@Component`, `select exists(…)` em `transactions` com `deleted_at is null`). Assim o
    `workspace` não depende do `ledger`, e o `verify()` do Modulith continua passando.
  - `MeWorkspace` ganha `@Nullable Instant gettingStartedDismissedAt` e `boolean hasTransactions`.
  - `WorkspaceRepository.dismissGettingStarted(UUID workspaceId, UUID userId)` → `boolean` (achou o
    vínculo ativo); grava `now()` só se ainda estiver nulo.
  - `POST /v1/me/getting-started/dismiss?workspaceId=<id>`: CSRF; 204; repetir não muda a data;
    sem vínculo ativo com essa família → 404 `workspace.not-found`. Não leva `@WorkspaceScoped`
    porque não está sob `/v1/workspaces/{ws}`; a checagem do vínculo é a do repositório.
  - Web: `dismissGettingStarted(workspaceId)` e `useDismissGettingStarted()` (atualiza o `me` em
    cache de forma otimista e desfaz se falhar).
  - `GettingStartedCard({ workspace, me })`.

**Regras (D15):**
- O cartão aparece quando: `hasTransactions` ou há item na fila; `gettingStartedDismissedAt` nulo;
  e algum item não está feito.
- Itens: "Adicionar suas contas" (feito com 2 contas ou mais, contando as arquivadas; link para
  `/mais/contas`) e "Criar passkey neste aparelho" (feito com `passkeyCreatedAt`; link para `/mais`,
  onde a passkey já é criada). Item feito mostra ✓ e o texto "Feito", não só a cor.
- O ✕ ("Fechar primeiros passos") esconde na hora e chama o `POST`; se falhar, o cartão volta e o
  aviso `gettingStarted.dismissFailed` aparece. Offline, o ✕ fica desabilitado com
  `gettingStarted.offline` no `title` e no texto de apoio.
- A chave `liberdade:first-steps-dismissed` do `localStorage` sai do código. No primeiro carregamento
  depois desta versão, se ela existir, o web a apaga e não chama nada: o cartão novo tem outros
  itens e outra regra, então mostrar de novo é o comportamento certo.

**Textos:**
- `gettingStarted.title`: "Primeiros passos"; `gettingStarted.close`: "Fechar primeiros passos"
- `gettingStarted.accounts`: "Adicionar suas contas"; `gettingStarted.passkey`: "Criar passkey neste aparelho"
- `gettingStarted.done`: "Feito"
- `gettingStarted.dismissFailed`: "Não deu para fechar agora. Tente de novo."
- `gettingStarted.offline`: "Sem conexão. Dá para fechar quando o sinal voltar."

- [ ] **Step 1: Escrever os testes (falhando)**

`GettingStartedIT`:

| Teste | Esperado |
|---|---|
| `freshFamilyHasNoTransactions` | `GET /v1/me` → `hasTransactions` falso e `gettingStartedDismissedAt` nulo |
| `firstTransactionFlipsFlag` | depois de um gasto → `hasTransactions` verdadeiro |
| `deletedTransactionDoesNotCount` | único gasto excluído → `hasTransactions` falso |
| `dismissPersists` | `POST …/dismiss` → 204; `GET /v1/me` traz a data; segundo `POST` → 204 e a mesma data |
| `dismissIsPerMember` | a dona dispensa; outra pessoa da família (vínculo inserido no teste) continua com nulo |
| `dismissOtherFamilyIsNotFound` | `workspaceId` de outra família → 404 `workspace.not-found` |
| `dismissNeedsCsrf` | sem o token → 403 `csrf.invalid` |

`ApplicationModulesTest` (já existe) continua verde: `workspace` não depende de `ledger`.

| Arquivo | Teste | Esperado |
|---|---|---|
| `GettingStartedCard.test.tsx` | `hiddenBeforeFirstTransaction` | `hasTransactions` falso e fila vazia → nada aparece |
| `GettingStartedCard.test.tsx` | `queuedItemShowsCard` | `hasTransactions` falso e um gasto na fila → o cartão aparece |
| `GettingStartedCard.test.tsx` | `accountsDoneAtTwo` | uma ativa e uma arquivada → "Adicionar suas contas" com ✓ e "Feito" |
| `GettingStartedCard.test.tsx` | `hiddenWhenAllDone` | 2 contas e passkey criada → nada aparece |
| `GettingStartedCard.test.tsx` | `dismissCallsApi` | ✕ → o cartão some e o MSW recebe o `POST` com o `workspaceId` |
| `GettingStartedCard.test.tsx` | `dismissFailureRestores` | o MSW responde 500 → o cartão volta e o aviso aparece |
| `GettingStartedCard.test.tsx` | `offlineDisablesClose` | offline → ✕ desabilitado e o texto de apoio aparece |
| `HomePage.test.tsx` | `dropsLegacyKey` | `localStorage` com `liberdade:first-steps-dismissed` → a chave some e nenhum `POST` sai |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*GettingStartedIT' --tests '*ApplicationModulesTest'`
Run: `corepack pnpm --filter web test -- src/features/dashboard`
Expected: FAIL nos dois.

- [ ] **Step 3: Implementar**

API primeiro (interface, implementação no `ledger`, repositório, controller e `MeView`), depois o
contrato, o MSW e o web.

- [ ] **Step 4: Rodar e ver passar**

Run: os mesmos comandos do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde, e `git diff --exit-code web/src/shared/api/schema.ts` limpo depois do commit.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat: getting-started checklist dismissed per member across devices"
```

---

### Task 13: E2E, acessibilidade e o cookie no iOS

**Files:**
- Create: `web/e2e/month-dashboard.spec.ts`, `web/e2e/edit-delete.spec.ts`
- Modify: `web/e2e/a11y.spec.ts`, `web/e2e/support.ts` (ajudantes novos, se precisar)
- Create: `docs/runbooks/ios-session-cookie.md`

**Interfaces:**
- Consumes: tudo das Tasks 1 a 12; o ambiente E2E do Plano 2 (`compose.e2e.yaml`, perfil `e2e`,
  mock-oauth2, Playwright em pt-BR e `America/Fortaleza`).
- Produces: dois specs novos, o `a11y.spec.ts` com as telas novas e o runbook.

**Specs:**
- `month-dashboard.spec.ts`, `prdExampleOnTheDashboard` (critério de saída do plano), pela UI:
  1. entrar e criar a família;
  2. em `/mais/contas`, criar "Corrente" (Conta corrente, R$ 1.000,00) e "Poupança" (Poupança,
     R$ 0,00);
  3. em `/lancar`: receita de R$ 5.000,00 em Salário na Corrente; gasto de R$ 1.234,56 em Mercado na
     Corrente; transferência de R$ 500,00 da Corrente para a Poupança;
  4. esperar a fila esvaziar (sem o selo "Enviando") e abrir `/painel`;
  5. conferir: Saldo total R$ 4.765,44, Receitas R$ 5.000,00, Gastos R$ 1.234,56, Resultado do mês
     R$ 3.765,44; na tabela da rosca, Mercado R$ 1.234,56; em `/mais/contas`, Corrente
     R$ 4.265,44 e Poupança R$ 500,00;
  6. tocar em Mercado na lista do painel → `/lancamentos` filtrado mostra só o gasto de Mercado.
- `edit-delete.spec.ts`:
  - `editsAmount`: lançar um gasto de R$ 10,00, abrir na lista, Editar, mudar para R$ 12,00,
    salvar → a lista e o Início mostram R$ 12,00;
  - `deleteAndUndo`: excluir o gasto → some; "Desfazer" → volta com o mesmo valor;
  - `conflictBetweenTabs`: duas páginas no mesmo contexto abrem a edição do mesmo lançamento; a
    primeira salva R$ 20,00; a segunda salva R$ 30,00 → "Este lançamento mudou"; "Salvar a minha
    mesmo assim" → a lista mostra R$ 30,00;
  - `offlineDisablesEditing`: `context.setOffline(true)` → o aviso no topo da lista, "Editar" e
    "Excluir" desabilitados no detalhe; lançar um gasto continua funcionando.
- `a11y.spec.ts`: acrescentar `/lancamentos` (com um lançamento e com o detalhe aberto),
  `/mais/contas` (com o formulário aberto), `/mais/categorias` (com o formulário aberto), `/painel`
  (com "Ver tabela" aberto) e `/lancar?tipo=transferencia`, nos dois temas, como os que já existem
  (sem violação `serious` ou `critical` do axe).

**Runbook `docs/runbooks/ios-session-cookie.md`** (pt-BR, para o mantenedor):
- objetivo: confirmar a D17 (o PWA instalado no iPhone continua logado depois de fechado);
- passos: abrir o ambiente de preview no Safari, entrar, "Adicionar à Tela de Início", abrir pelo
  ícone, fechar o app pelo seletor de apps, esperar 10 minutos, abrir de novo; repetir no dia
  seguinte;
- o que olhar: abre já logado; em Ajustes > Safari > Avançado > Dados de Sites, o domínio tem dados;
- onde anotar: na descrição do PR 2, com o modelo do aparelho, a versão do iOS, a data e o
  resultado (sem dado pessoal);
- se falhar: abrir uma issue com o resultado e não fazer o merge do PR 2 até decidir com o
  mantenedor.

- [ ] **Step 1: Escrever os specs**

Pelos roteiros acima, usando os ajudantes do `support.ts` (entrar, criar a família, esperar a fila).

- [ ] **Step 2: Rodar**

Run: `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait` (o Docker Desktop
já precisa estar em execução; o plano não o instala nem o configura)
Run: `corepack pnpm --filter web e2e`
Expected: todos os specs passam, os do Plano 2 também.

- [ ] **Step 3: Corrigir o que o E2E ou o axe acharem**

Uma correção por causa, no arquivo da task dona do código, com o teste unitário que pega o mesmo
caso. Rodar o Step 2 de novo até ficar verde.

- [ ] **Step 4: Conferir tudo**

Run (em `api/`): `./gradlew build`
Run: `corepack pnpm --filter web lint; corepack pnpm --filter web format:check; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde; o pacote `ledger.domain` com 90% de linhas ou mais (critério S4).

- [ ] **Step 5: Commit**

```bash
git add web/e2e docs/runbooks/ios-session-cookie.md
git commit -m "test(e2e): month dashboard, edit and delete flows, and the iOS session runbook"
```

- [ ] **Step 6: Medição no iOS (mantenedor)**

O mantenedor segue o runbook depois que o preview do PR 2 estiver no ar e anota o resultado na
descrição do PR. Sem a anotação, o PR 2 não vai para merge.
