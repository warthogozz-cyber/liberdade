# Liberdade · Plano 8.3: Sequência, conquistas e dicas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a terceira entrega da v0.3 (US-32, US-33, US-34).
- Com o módulo Conquistas ligado, cada pessoa vê a própria sequência semanal ("N semanas seguidas")
  num cartão do Início e uma tela "Conquistas" no menu Mais, com as conquistas ganhas e as próximas.
- Uma conquista nova celebra só depois de uma ação da pessoa naquele aparelho. O que entra por outro
  caminho (ligar o módulo, membro novo, papel novo, primeira leitura) entra em silêncio.
- Dicas curtas na primeira visita a oito telas e tours de até 4 passos nas telas principais de
  Planejar, Indicadores e Conquistas. Tudo desliga em Ajustes.

**Architecture:**
- **API (`gamification`, módulo novo):** domínio puro da sequência (`WeeklyStreak`) e das chaves
  (`AchievementKey`); V21 com `achievements` e o índice por quem criou; avaliação depois do commit
  dos eventos de `ledger` e `planning`, com `ON CONFLICT DO NOTHING`; duas rotas `GET`; um job no
  dia 1 para `MONTH_IN_BUDGET`.
- **Leituras públicas novas:** `ledger/EntryWeeks` (semanas com lançamento de uma pessoa) e
  `planning/PlanningFacts` (meta criada, meta alcançada, meses de reserva, mês no orçamento). Nenhum
  SQL da gamificação toca tabela de outro módulo.
- **Ao vivo e exportação:** um aviso `achievement` só para quem ganhou; a exportação pessoal ganha
  as conquistas.
- **Web:** `features/gamification` (cartão, tela, celebração) e `shared/tips` (dicas e tours),
  ambos sob demanda.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, PostgreSQL 18, Flyway, JdbcClient.
  Testes com Testcontainers e `MockMvcTester`.
- **Web:** React 19, react-router v8, TanStack Query, react-intl, MSW 3, Vitest e Playwright com
  axe.
- **Sem dependências novas.**

**Spec:**
- [Spec do Plano 8](../specs/2026-11-24-liberdade-plan-8-indicators-gamification-design.md),
  aprovado pelo mantenedor em 2026-11-24. Este plano cobre só a 8.3: as seções 5, 6.1 e 6.2 no que
  tocam a 8.3, as 6.6, 6.7 e 6.8, a 6.9 no que toca a 8.3 e a parte 8.3 das seções 7 e 8. É a
  autoridade: este plano argumenta a partir dele.
- [PRD](../../prd.md) (US-32, US-33, US-34), [Arquitetura](../../architecture.md),
  [Modelo de dados](../../data-model.md), [Design system](../../design-system.md).

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits (`feat(gamification): ...`, `feat(web): ...`,
  `test(e2e): ...`, `docs: ...`), com o assunto em minúsculas. Cada commit termina com a linha
  `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **Independente da 8.2:** a 8.3 não usa nada da 8.2. As migrações da 8.3 começam em **V21** (a 8.2
  usa V19 e V20; buraco na numeração é normal no Flyway).
- **Sequência (spec 6.6):** semana de segunda a domingo no fuso da família; conta lançamento
  confirmado, não excluído, pelo `created_at` e `created_by`; lançamento de recorrência
  (`recurring_rule_id` preenchido) não conta; uma semana congelada por mês (o mês da segunda-feira),
  que mantém a sequência viva sem somar; a semana corrente nunca quebra.
- **Conquistas (spec 6.7):** só com o módulo `gamification` ligado (começa desligado, decisão 5 do
  mantenedor). As de família vão a quem vê aquilo; o Jovem não ganha as de reserva e orçamento.
  Nenhuma premia gastar, dívida, crédito, risco ou número de lançamentos.
- **Silêncio (spec 6.7):** o que entra fora de uma ação da pessoa grava `silent = true`.
  `MONTH_IN_BUDGET` não é retroativo.
- **Ética (spec 2 e 10):** nunca "perdeu", nunca contagem regressiva, nenhuma notificação, nenhuma
  pressa. Um teste por feature confere as frases.
- **Dicas (spec 6.8):** núcleo, sem módulo, ligadas por padrão; até 140 caracteres; estado no
  aparelho, como o som.
- **Tempo:** o `Clock` da API em tudo que a API decide. O job do dia 1 roda às 4h20 em
  `America/Sao_Paulo`.
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR.
- **Fronteiras de módulo:** `gamification` depende de `workspace`, `ledger`, `planning` e `shared`.
  `live` e `privacy` passam a depender de `gamification` só por tipos públicos. O
  `ApplicationModulesTest` passa.
- **Contrato:** cada rota nova entra no `api/openapi.json`, no `web/src/shared/api/schema.ts` e no
  `RoleMatrixIT`, no mesmo commit.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `shared*` e `*.domain*`.
- **Desempenho:** o carregamento inicial do web não cresce (`check:chunks`, limite 200 KB). Todo
  `features/gamification` e todo `shared/tips` carregam sob demanda; no chunk inicial entram só os
  `register` e o teste síncrono de "já vi esta dica".
- **Acessibilidade:** axe no Playwright cobre a tela de Conquistas, a dica e o tour aberto. O tour
  move o foco, aceita teclado e Esc, e devolve o foco ao fechar. Movimento respeita
  `prefers-reduced-motion`; som só com o som ligado.
- **Custo:** R$ 0. Nenhum serviço novo.
- **GitHub Actions:** nenhum workflow novo e nenhuma permissão nova.
- **Ações externas:** nenhuma durante a execução. A conferência com duas pessoas é do mantenedor,
  no staging, depois do merge.

### Pré-requisitos locais

Os mesmos da 8.1: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o compose de
e2e.

## Entrega

- **PR 1 (docs):** este plano e o README dos planos. Entra antes do código.
- **PR 2 (código):** branch `feat/plan-8-3-streak-achievements-tips`, cortada da `main` depois do
  PR 1. Um commit por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o staging recebe o deploy pelo pipeline. O mantenedor confere o critério de
  saída da 8.3 (seção 8 do spec): a sequência e as conquistas aparecem para duas pessoas
  diferentes, e as dicas desligam. O corpo do PR 2 traz o roteiro.
- **Depois disso:** um PR de docs (README dos planos, linha 8).

### Itens herdados

| Item | Onde |
|---|---|
| Rota por módulo volta para `/inicio` ao recarregar logo depois de ligar o módulo (8.1) | Fora da 8.3; a tela de Conquistas herda o mesmo comportamento; proposta de issue |
| Limite de tamanho do corpo nas respostas do Banco Central (8.1) | Fora da 8.3; proposta de issue |
| Alerta falso se um deploy interrompe a espera entre tentativas (8.1) | Fora da 8.3; proposta de issue |
| Abrir espaço no carregamento inicial (7.3) | Task 13 confere que a 8.3 não soma ao inicial; abrir espaço fica fora |
| Celebração da reserva quando muda a meta ou a conta (7.3) | Fora da 8.3: a celebração das conquistas é separada da das metas |
| Interruptor de 48 px com rótulo clicável (7.3) | O interruptor "Mostrar dicas" segue o do som como está hoje; ajustar os dois fica fora |
| E2E do Jovem esperando o `/events` (7.3) | Fora da 8.3 |
| Limite de erro do `TagsTab` sob demanda (7.1) | PR próprio |
| Issues #36, #38, #39 e #40 | Fora da 8.3 |
| Dica da tela Importar (8.2) | Task 11: liga na tela se `features/import` já estiver na `main` no corte da branch; senão o texto fica registrado e a ligação vira item do PR 2 |

## Decisões deste plano

A seção 11 do spec deixa estes pontos ao construtor, e o plano decide:

**D1. Pacotes.**
- API: `gamification/package-info.java` (`@ApplicationModule(allowedDependencies = {"workspace","ledger","planning","shared"})`);
  públicos `gamification/AchievementsEarned`, `gamification/AchievementExport`,
  `gamification/ExportedAchievement`; puros `gamification/domain/AchievementKey`,
  `WeeklyStreak`, `Streak`; o resto em `gamification/internal`.
- Web: `features/gamification/` (register, cartão, tela, celebração, mensagens),
  `shared/api/gamification.ts` (fora do barril) e `shared/tips/`.

**D2. V21** (`V21__achievements.sql`): a tabela `achievements` e o índice
`transactions_by_creator (workspace_id, created_by, created_at)` exatamente como na seção 6.2 do spec.
- **Desvio do spec:** o spec chama a migração de V20, que a 8.2 já usa. A 8.3 fica com V21.
- **Exclusão de conta:** a cascata do spec não basta, porque a linha de `memberships` nunca é
  apagada (a exclusão de conta só marca `REMOVED` e solta o `user_id`). Um ouvinte de
  `MemberRemoved`, com qualquer motivo, apaga as conquistas da participação na mesma transação (D6).
  A exclusão da família segue pela cascata de `workspaces`.

**D3. `ledger/EntryWeeks`** (público), implementado em `ledger/internal/JdbcEntryWeeks`:
- `SortedSet<LocalDate> mondaysWithEntries(UUID workspaceId, UUID membershipId, ZoneId zone)`;
- SQL: `SELECT DISTINCT date_trunc('week', created_at AT TIME ZONE :zone)::date FROM transactions WHERE workspace_id = :ws AND created_by = :membership AND status = 'CONFIRMED' AND deleted_at IS NULL AND recurring_rule_id IS NULL`,
  com `:zone` = `zone.getId()`;
- `FIRST_TRANSACTION` vale quando o conjunto não está vazio (a mesma regra da sequência, spec 6.7).

**D4. `planning/PlanningFacts`** (público), implementado em `planning/internal/PlanningFactsService`,
que usa os serviços que já existem:
- `boolean createdAGoal(UUID workspaceId, UUID membershipId)`: alguma meta com
  `created_by = membershipId`, arquivada ou não;
- `boolean anyGoalReached(UUID workspaceId)`: alguma meta com `achieved_at` preenchido, arquivada ou
  não;
- `Optional<BigDecimal> reserveMonths(WorkspaceMember member)`: `ReserveService.view(member).months()`;
- `Optional<Boolean> withinBudget(WorkspaceMember member, YearMonth month)`: vazio sem nenhum limite
  no mês; senão `true` quando nenhuma linha de `BudgetService.month(member, month)` está `OVER`
  (`AT` conta como dentro);
- `List<UUID> familiesWithBudgets(YearMonth month)`: famílias com alguma linha em `budgets` para o
  dia 1 do mês.

**D5. Quem ganha o quê** (`AchievementKey`, nesta ordem, que é a ordem da tela):

| Chave | Papéis | Vale quando | Depende de Planejar |
|---|---|---|---|
| `FIRST_TRANSACTION` | OWNER, ADULT, YOUTH | `mondaysWithEntries` não vazio | Não |
| `STREAK_4_WEEKS` | OWNER, ADULT, YOUTH | `WeeklyStreak.longest() >= 4` | Não |
| `FIRST_GOAL` | OWNER, ADULT (quem cria metas) | `createdAGoal` | Sim |
| `GOAL_ACHIEVED` | OWNER, ADULT, YOUTH, VIEWER (todos veem metas) | `anyGoalReached` | Sim |
| `RESERVE_1_MONTH`, `RESERVE_3_MONTHS`, `RESERVE_6_MONTHS` | OWNER, ADULT, VIEWER | `reserveMonths >= 1, 3, 6` | Sim |
| `MONTH_IN_BUDGET` | OWNER, ADULT, VIEWER | Só pelo job (D7) | Sim |

- `AchievementKey` guarda `Set<Role> roles()` e `boolean needsPlanning()`.
- A lista da tela mostra só as chaves do papel atual. As que dependem de Planejar aparecem com
  Planejar desligado só se já foram ganhas. Uma conquista ganha num papel que a pessoa não tem mais
  (Adulto que vira Jovem) fica guardada e some da lista (o Jovem não vê nada da reserva).

**D6. Avaliação** (`gamification/internal/AchievementService`):
- `void evaluate(UUID workspaceId, UUID membershipId, boolean silent)` e
  `void evaluateFamily(UUID workspaceId, boolean silent)`. A da família calcula uma vez o que é da
  família (meta alcançada, meses de reserva) e chama a regra por membro ativo.
- Por membro: lê as chaves que ele já tem e pula as leituras das que já ganhou ou que o papel não
  ganha; pula as de Planejar com Planejar desligado; não faz nada com Conquistas desligado.
  `MONTH_IN_BUDGET` nunca entra aqui.
- Grava com `INSERT ... ON CONFLICT DO NOTHING` e `earned_at` = `clock.instant()`.
- Com `silent = false` e pelo menos uma linha nova, publica `AchievementsEarned(UUID workspaceId, UUID membershipId)`.
  Com `silent = true` não publica (ninguém precisa do aviso).
- **Ouvintes** (`AchievementListeners`, como o `GoalAchievementListener`: `AFTER_COMMIT`, chamada
  numa transação própria `REQUIRES_NEW`, `try/catch` que loga só o nome da classe; uma falha nunca
  desfaz a ação de quem fez):
  - `TransactionChanged`, `AccountChanged`, `GoalChanged`, `ReserveCostChanged`,
    `ReserveTargetChanged` → `evaluateFamily(ws, false)`. O `GoalChanged` cobre a meta alcançada por
    um lançamento, porque o `reachLinked` publica o evento na transação dele;
  - `ModuleToggled` ligando `GAMIFICATION`, ou ligando `PLANNING` com Conquistas ligado →
    `evaluateFamily(ws, true)`;
  - `MemberJoined` e `MemberRoleChanged` → `evaluate(ws, membershipId, true)`.
- `MemberRemoved` (qualquer motivo) → `@EventListener` dentro da transação, apaga as conquistas da
  participação (D2).
- A leitura da lista (D9) chama `evaluate(ws, quem lê, true)` antes de ler, na mesma transação.

**D7. `MONTH_IN_BUDGET`** (`gamification/internal/MonthInBudgetJob`, o padrão do `RecurrenceJob`):
- `@Scheduled(cron = "0 20 4 1 * *", zone = "America/Sao_Paulo")`;
- mês = o mês anterior em São Paulo (`YearMonth.now(clock.withZone(SAO_PAULO)).minusMonths(1)`);
  o `BudgetService` já calcula os limites do mês no fuso da família. Famílias em fuso atrás de São
  Paulo por mais de 4h20 seriam lidas antes do fim do mês delas; com o produto no Brasil isso não
  acontece, e o plano registra o limite;
- para cada família de `familiesWithBudgets(mês)` com Conquistas e Planejar ligados, numa
  `TransactionTemplate` por família: se `withinBudget` dá `true`, grava `MONTH_IN_BUDGET` com
  `silent = false` para cada membro ativo OWNER, ADULT ou VIEWER e publica `AchievementsEarned` por
  membro com linha nova. Ninguém celebra (não houve ação local); a conquista aparece na lista;
- uma família com erro não para as outras; o log leva só o nome da classe e as contagens.

**D8. Algoritmo da sequência** (`WeeklyStreak.of(SortedSet<LocalDate> mondays, LocalDate currentMonday): Streak`,
`Streak(int weeks, int frozenWeeks, int longest)`):

```text
percorre as segundas-feiras da primeira de `mondays` até `currentMonday`, inclusive
  (segundas depois de currentMonday são ignoradas; sem nenhuma, devolve 0, 0, 0)
  semana com lançamento:          run += 1; vivo = true; longest = max(longest, run)
  semana corrente sem lançamento: não muda nada
  semana passada sem lançamento:
    vivo e o mês da segunda ainda sem congelada: congela (guarda o mês); frozenInRun += 1
    vivo e o mês já tem congelada:               vivo = false; run = 0; frozenInRun = 0
    não vivo:                                    não muda nada (não gasta o congelamento do mês)
devolve (vivo ? run : 0, vivo ? frozenInRun : 0, longest)
```

- O mês já congelado vale para o histórico inteiro: uma sequência que recomeça no mesmo mês não
  ganha outro congelamento nele.

**D9. Rotas** (`gamification/internal/GamificationController`, as duas com
`@RequiresModule(ModuleKey.GAMIFICATION)` e `@WorkspaceScoped(allow = {OWNER, ADULT, VIEWER, YOUTH})`):
- `GET /v1/workspaces/{ws}/gamification/streak` → `StreakView(int weeks, int frozenWeeks, int longest)`
  da pessoa que chama, com `currentMonday` = hoje no fuso da família
  (`with(TemporalAdjusters.previousOrSame(MONDAY))`). O Leitor recebe zeros;
- `GET /v1/workspaces/{ws}/gamification/achievements` →
  `AchievementsView(List<AchievementView> items)`,
  `AchievementView(AchievementKey key, @Nullable @Schema(nullable = true) Instant earnedAt, boolean silent)`,
  na ordem de D5 e com o filtro de D5. `@Transactional` (não só leitura), por causa da avaliação em
  silêncio.

**D10. Ao vivo.**
- `live/package-info` ganha `"gamification"`. `Audience` ganha `record Member(UUID membershipId)`,
  que inclui só o stream dessa participação.
- `LiveRelay.send` troca `boolean needsPlanning` por `@Nullable ModuleKey module` (os chamados de
  hoje passam `ModuleKey.PLANNING` ou `null`). Um handler `AFTER_COMMIT` de `AchievementsEarned`
  manda `{"entity":"achievement"}` com `Audience.Member` e `ModuleKey.GAMIFICATION`.
- No web, `shared/live/invalidation.ts` ganha `achievement: [gamification]` e o prefixo
  `gamification` em `transaction` e `account` (a sequência muda com um lançamento).

**D11. Celebração** (`features/gamification/AchievementCelebrationWatcher`, slot `workspace`,
papéis OWNER, ADULT e YOUTH, o padrão do `GoalCelebrationWatcher`):
- mantém a lista no cache com `useAchievements(ws)`;
- em `onLocalAction` da família, fora da fila (`fromQueue` falso), numa fila de promessas em série:
  `antes` = a lista no cache; busca de novo com `fetchQuery({ staleTime: 0 })`; invalida a
  sequência;
- sem `antes` (nada no cache ainda), só guarda a lista como base e não celebra;
- celebra cada item que está ganho agora e não estava em `antes`, com `silent` falso e `earnedAt`
  nos últimos 5 minutos; um `Celebration` por item, com `celebrationSound(ws)`;
- o aviso ao vivo `achievement` só invalida a consulta: nunca celebra.

**D12. Exportação.** A exportação pessoal ganha `achievements` em `dados.json`, uma entrada por
conquista de cada participação da pessoa: `workspaceId`, `key`, `earnedAt`. `FORMAT_VERSION` sobe 1
sobre o valor da `main` no corte da branch (5 se a 8.2 ainda não entrou, 6 se já entrou). A
exportação da família não muda. Interface pública `gamification/AchievementExport` com
`List<ExportedAchievement> of(UUID workspaceId, UUID membershipId)`;
`ExportedAchievement(String key, Instant earnedAt)`; `privacy/package-info` ganha `"gamification"`.

**D13. Política de privacidade: sem versão nova.** As conquistas são derivadas de dados que a
política já cobre (lançamentos, metas, reserva, orçamento), ficam na família, entram na exportação e
somem com a participação. O estado das dicas fica no aparelho, como o som. Sem texto novo, a 8.3 não
disputa número de versão com a 8.2. O `data-model.md` ganha a tabela e a retenção.

**D14. Dicas** (`shared/tips`):
- `tipsPreference.ts`: chave `liberdade.tips`, ligada por padrão, `'off'` desliga;
  `isTipsEnabled()`, `useTipsEnabled(): [boolean, (on: boolean) => void]`, `try/catch` em toda
  leitura e escrita (sem armazenamento, fica ligada e não lembra o "Entendi").
- `seenTips.ts`: chave `liberdade.tip:<userId>:<tipId>` = `'1'`; `hasSeenTip`, `markTipSeen`.
- `TipId` = `'dashboard' | 'transactions' | 'accounts' | 'planning' | 'indicators' | 'calculator' | 'import' | 'achievements'`.
- `PageTip({ id })`: síncrono e pequeno, lê `useMe()`, a preferência e o "já vi"; só então
  renderiza `TipBubble` com `lazy`. `TipBubble` é um bloco no topo da tela, dentro do fluxo (não
  cobre nenhum controle), `role="note"`, texto e botão "Entendi", que marca e some.
- Telas: Painel do mês (`/painel`), Lançamentos, Contas, Calculadora, Importar (Itens herdados) com
  `PageTip`; Planejar, Indicadores e Conquistas usam o tour (D15), cuja primeira tela é a dica.
- Ajustes ganha a seção "Dicas" com o interruptor "Mostrar dicas", igual ao do som.
- Testes e e2e: `src/test-setup.ts` e o fixture `test` de `web/e2e/support.ts` desligam as dicas por
  padrão; os testes de dicas ligam de propósito (Review Focus 1).

**D15. Tours** (`shared/tips/Tour.tsx`, carregado com `lazy` por `PageTour({ id })`):
- `TourId` = `'planning' | 'indicators' | 'achievements'`; passos em `tours.ts`, cada um
  `{ anchor: string, messageId: string }`, até 4; âncora = `data-tour="<valor>"` na tela. Passo sem
  âncora na tela é pulado; tour sem nenhum passo não abre.
- Mesmo "já vi" das dicas (`liberdade.tip:<userId>:tour-<id>`) e a mesma preferência.
- Cartão fixo embaixo, `role="dialog"`, `aria-modal="false"`, `aria-labelledby`, "Passo N de M",
  botões "Pular", "Voltar" (do passo 2 em diante) e "Próximo"/"Concluir". Esc = Pular. O foco vai ao
  cartão ao abrir e volta ao elemento de antes ao fechar. A âncora ganha contorno e
  `scrollIntoView` (`behavior: 'auto'` com movimento reduzido).
- Fechar de qualquer jeito marca como visto.

**D16. Cartão e tela** (`features/gamification`):
- `register.ts`: `registerModule({ key: 'gamification', available: true })`; slot `home`
  `StreakHomeCard` (papéis OWNER, ADULT, YOUTH) e slot `workspace` `AchievementCelebrationWatcher`.
  `app/routes.tsx` importa o `register` como faz com os outros.
- **Desvio do spec:** o spec põe o cartão "no painel". Ele vai no slot `home` do Início, onde ficam
  os cartões dos módulos; o Painel do mês não tem slot. A dica "Painel" continua no Painel do mês.
- `AchievementsPage` em `/mais/conquistas`, sob `<ModuleRoute module="gamification">`, com um link
  em `MorePage` quando o módulo está ligado (o padrão do link de Indicadores).
- Textos (PT-BR, fixados pelo plano):

| Id | Texto |
|---|---|
| `gamification.streak.weeks` | `{weeks, plural, one {# semana seguida} other {# semanas seguidas}}` |
| `gamification.streak.frozen` | `{frozen, plural, one {# semana de folga incluída} other {# semanas de folga incluídas}}` |
| `gamification.streak.none` | `Lance algo esta semana para começar uma sequência.` |
| `gamification.title` | `Conquistas` |
| `gamification.earned` | `Ganhas` |
| `gamification.next` | `Próximas` |
| `gamification.earnedOn` | `Ganha em {date}` |
| `gamification.celebrate` | `Conquista: {name}` |

| Chave | Nome | Como chegar |
|---|---|---|
| `FIRST_TRANSACTION` | Primeiro lançamento | Registre um lançamento. |
| `STREAK_4_WEEKS` | 4 semanas seguidas | Registre algo em 4 semanas seguidas. |
| `FIRST_GOAL` | Primeira meta | Crie uma meta em Planejar. |
| `GOAL_ACHIEVED` | Meta alcançada | Uma meta da família chega ao valor. |
| `RESERVE_1_MONTH` | Reserva de 1 mês | A reserva cobre 1 mês de custo. |
| `RESERVE_3_MONTHS` | Reserva de 3 meses | A reserva cobre 3 meses de custo. |
| `RESERVE_6_MONTHS` | Reserva de 6 meses | A reserva cobre 6 meses de custo. |
| `MONTH_IN_BUDGET` | Mês dentro do orçamento | Um mês fecha com todas as categorias dentro do limite. |

- Teste de frases (gamificação e dicas): nenhum texto casa com
  `/perd|perca|resta|expir|quebr|corra|última chance|não deixe|falta/i`.

## Review Focus

1. **Testes que já existem quebrando com as dicas.** Uma dica ou um tour na primeira visita muda o
   que os testes de tela e os e2e de Planejar e Indicadores encontram. Esperado: dicas desligadas
   por padrão em `src/test-setup.ts` e no fixture `test` do e2e, ligadas só nos testes delas. Teste
   `support desliga as dicas` (Task 11) e a suíte toda verde (Task 13).
2. **Primeira ação depois de abrir o app.** Sem a lista no cache, uma conquista não silenciosa de
   ontem (meta alcançada por outra pessoa) não pode celebrar na primeira ação de hoje. Esperado: sem
   base, só guarda; e `earnedAt` com mais de 5 minutos nunca celebra. Testes
   `sem base não celebra` e `conquista antiga não celebra` (Task 10).
3. **Adulto que vira Jovem.** As conquistas de reserva já gravadas não podem aparecer para o Jovem.
   Esperado: a lista filtra pelo papel atual, e o aviso ao vivo não leva conteúdo. Teste
   `lista do jovem não mostra reserva já ganha` (Task 5).
4. **Falha na avaliação.** Um erro ao avaliar (por exemplo, numa leitura da reserva) não pode
   recusar o lançamento de quem registrou. Esperado: o `POST` do lançamento devolve 201 e o log
   leva só o nome da classe. Teste `falha na avaliação não desfaz o lançamento` (Task 4).
5. **Conquistas que sobram depois da exclusão de conta.** A participação nunca é apagada, então a
   cascata sozinha deixaria as conquistas. Esperado: some tudo da pessoa ao excluir a conta ou ao
   sair da família. Teste `excluir a conta apaga as conquistas` (Task 4).

---

## Mapa de arquivos

Caminhos da API relativos a `api/src/main/java/br/com/liberdadefamilia/`; do web, a `web/src/`.

| Arquivo | Task | Papel |
|---|---|---|
| `gamification/package-info.java` | 1 | Módulo novo e dependências |
| `gamification/domain/AchievementKey.java`, `WeeklyStreak.java`, `Streak.java` | 1 | Domínio puro |
| `api/src/main/resources/db/migration/V21__achievements.sql` | 2 | Tabela e índice |
| `ledger/EntryWeeks.java`, `ledger/internal/JdbcEntryWeeks.java` | 2 | Semanas com lançamento |
| `planning/PlanningFacts.java`, `planning/internal/PlanningFactsService.java` | 3 | Fatos de Planejar |
| `gamification/AchievementsEarned.java` | 4 | Evento público |
| `gamification/internal/AchievementRepository.java`, `AchievementService.java`, `AchievementListeners.java` | 4 | Gravação, avaliação, ouvintes |
| `gamification/internal/GamificationController.java`, `StreakView.java`, `AchievementsView.java`, `AchievementView.java` | 5 | Rotas |
| `gamification/internal/MonthInBudgetJob.java` | 6 | Job do dia 1 |
| `live/package-info.java`, `live/internal/Audience.java`, `live/internal/LiveRelay.java` | 7 | Aviso ao vivo |
| `gamification/AchievementExport.java`, `ExportedAchievement.java`, `gamification/internal/AchievementJdbcExport.java` | 8 | Leitura para exportar |
| `privacy/package-info.java`, `privacy/internal/ExportService.java` | 8 | `dados.json` |
| `docs/data-model.md`, `docs/architecture.md` | 8 | V21, retenção, módulo |
| `shared/api/gamification.ts`, `shared/live/invalidation.ts` | 9 | Cliente e invalidação |
| `features/gamification/register.ts`, `messages.pt-BR.ts`, `StreakHomeCard.tsx`, `AchievementsPage.tsx`, `achievementCopy.ts` | 9 | Cartão e tela |
| `app/routes.tsx`, `app/MorePage.tsx` | 9 | Rota e link |
| `features/gamification/AchievementCelebrationWatcher.tsx`, `celebrations.ts` | 10 | Celebração |
| `shared/tips/tipsPreference.ts`, `seenTips.ts`, `PageTip.tsx`, `TipBubble.tsx`, `messages.pt-BR.ts`, `index.ts` | 11 | Dicas |
| `features/settings/SettingsPage.tsx`, `test-setup.ts`, `web/e2e/support.ts` | 11 | Interruptor e testes |
| `shared/tips/PageTour.tsx`, `Tour.tsx`, `tours.ts` | 12 | Tours |
| `web/e2e/gamification.spec.ts`, `web/e2e/tips.spec.ts` | 13 | E2E |

---

### Task 1: Domínio da gamificação

**Files:**
- Create: `gamification/package-info.java`, `gamification/domain/AchievementKey.java`,
  `gamification/domain/WeeklyStreak.java`, `gamification/domain/Streak.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/gamification/domain/WeeklyStreakTest.java`,
  `AchievementKeyTest.java`

**Interfaces:**
- Consumes: `workspace.Role`.
- Produces: `enum AchievementKey { FIRST_TRANSACTION, STREAK_4_WEEKS, FIRST_GOAL, GOAL_ACHIEVED, RESERVE_1_MONTH, RESERVE_3_MONTHS, RESERVE_6_MONTHS, MONTH_IN_BUDGET }`
  com `Set<Role> roles()`, `boolean needsPlanning()`, `boolean earnableBy(Role role)` e, nas de
  reserva, `int reserveMonths()` (1, 3, 6; 0 nas outras); `record Streak(int weeks, int frozenWeeks, int longest)`;
  `static Streak WeeklyStreak.of(SortedSet<LocalDate> mondays, LocalDate currentMonday)`.

**Como fica:** as regras de D5 e D8 em classes puras, cobertas a 90%.

- [ ] **Step 1: Escrever os testes**

`WeeklyStreakTest` (todas as datas são segundas-feiras de 2026; `seg(...)` monta o conjunto):

| Teste | Entrada | Esperado |
|---|---|---|
| `sem lançamento` | `{}`, corrente 11-23 | `(0, 0, 0)` |
| `só a semana corrente` | `{11-23}`, corrente 11-23 | `(1, 0, 1)` |
| `quatro seguidas` | `{11-02, 11-09, 11-16, 11-23}`, corrente 11-23 | `(4, 0, 4)` |
| `semana corrente vazia não quebra` | `{11-02, 11-09, 11-16}`, corrente 11-23 | `(3, 0, 3)` |
| `congelada não soma` | `{11-02, 11-09, 11-23}`, corrente 11-30 | `(3, 1, 3)` |
| `duas faltas no mesmo mês quebram` | `{11-02, 11-16, 11-30}`, corrente 11-30 | `(1, 0, 2)` |
| `virada de mês congela de novo` | `{10-19, 11-02, 11-16, 11-23}`, corrente 11-23 | `(4, 2, 4)` |
| `antes do primeiro lançamento não conta` | `{11-16}`, corrente 11-30 | `(1, 1, 1)` |
| `falta sem sequência não gasta o mês` | `{10-05, 11-09, 11-23}`, corrente 11-23 | `(2, 1, 2)` |
| `segunda futura é ignorada` | `{11-23, 11-30}`, corrente 11-23 | `(1, 0, 1)` |

`AchievementKeyTest`: os papéis de cada chave são os de D5 (uma linha por chave, parametrizado);
`YOUTH` não ganha nenhuma de reserva nem `MONTH_IN_BUDGET`; `needsPlanning` só falso em
`FIRST_TRANSACTION` e `STREAK_4_WEEKS`; a ordem de `values()` é a de D5.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*WeeklyStreakTest' --tests '*AchievementKeyTest'`
Expected: FAIL, as classes não existem.

- [ ] **Step 3: Implementar**

`package-info` com `@ApplicationModule(allowedDependencies = {"workspace","ledger","planning","shared"})`
(as dependências de `ledger` e `planning` só ganham uso nas Tasks 2 a 4). `WeeklyStreak` segue o
pseudocódigo de D8; o mês da segunda é `YearMonth.from(monday)`.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*WeeklyStreakTest' --tests '*AchievementKeyTest' --tests '*ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` → BUILD SUCCESSFUL.

```bash
git add api/src/main/java/br/com/liberdadefamilia/gamification api/src/test/java/br/com/liberdadefamilia/gamification
git commit -m "feat(gamification): add the weekly streak and the achievement keys"
```

### Task 2: V21 e as semanas com lançamento

**Files:**
- Create: `api/src/main/resources/db/migration/V21__achievements.sql`, `ledger/EntryWeeks.java`,
  `ledger/internal/JdbcEntryWeeks.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/ledger/EntryWeeksIT.java`

**Interfaces:**
- Produces: `SortedSet<LocalDate> EntryWeeks.mondaysWithEntries(UUID workspaceId, UUID membershipId, ZoneId zone)`;
  a tabela `achievements` (D2).

**Como fica:** D2 e D3.

- [ ] **Step 1: Escrever os testes** (os lançamentos são gravados com `created_at` fixo pelo SQL do
  teste; fuso `America/Sao_Paulo`)

| Teste | Esperado |
|---|---|
| `domingo à noite conta na semana local` | `created_at` 2026-11-23T02:30Z (domingo 22, 23h30 em São Paulo) → `{2026-11-16}` |
| `segunda de madrugada no fuso` | 2026-11-23T03:30Z → `{2026-11-23}` |
| `recorrência não conta` | lançamento com `recurring_rule_id` → `{}` |
| `previsto não conta` | `status = 'PLANNED'` → `{}` |
| `excluído não conta` | `deleted_at` preenchido → `{}` |
| `de outra pessoa não conta` | `created_by` de outra participação → `{}` |
| `três na mesma semana` | três lançamentos de 11-17 a 11-21 → `{2026-11-16}` |
| `outra família não vaza` | mesma participação, outro `workspace_id` não aparece |
| `a V21 cria a tabela` | inserir duas vezes a mesma `(membership_id, achievement_key)` com `ON CONFLICT DO NOTHING` grava uma linha |

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*EntryWeeksIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar** a migração (SQL da seção 6.2 do spec) e `JdbcEntryWeeks` com o SQL de
  D3, devolvendo um `TreeSet`.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*EntryWeeksIT' --tests '*ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` → BUILD SUCCESSFUL.

```bash
git add api/src/main/resources/db/migration/V21__achievements.sql api/src/main/java/br/com/liberdadefamilia/ledger/EntryWeeks.java api/src/main/java/br/com/liberdadefamilia/ledger/internal/JdbcEntryWeeks.java api/src/test/java/br/com/liberdadefamilia/ledger/EntryWeeksIT.java
git commit -m "feat(ledger): add the weeks with entries reading and the achievements table"
```

### Task 3: Fatos de Planejar

**Files:**
- Create: `planning/PlanningFacts.java`, `planning/internal/PlanningFactsService.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/planning/PlanningFactsIT.java`

**Interfaces:**
- Consumes: `ReserveService.view(WorkspaceMember)`, `BudgetService.month(WorkspaceMember, YearMonth)`
  (package-private, mesmo pacote).
- Produces: a interface de D4.

**Como fica:** a gamificação lê Planejar só por esta interface.

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `meta criada por mim` | Adulto cria uma meta → `createdAGoal(ws, adulto)` `true`; `createdAGoal(ws, dono)` `false` |
| `meta arquivada ainda conta` | criar e arquivar → `true` |
| `meta alcançada` | meta com `achieved_at` → `anyGoalReached` `true`; arquivada depois, ainda `true` |
| `nenhuma alcançada` | → `false` |
| `meses de reserva` | reserva com saldo para 3,4 meses → `Optional.of(3.4)` |
| `reserva sem custo` | sem histórico nem custo manual → `Optional.empty()` |
| `sem limite no mês` | → `withinBudget` vazio |
| `no limite conta como dentro` | uma categoria `AT`, outra `UNDER` → `true` |
| `uma acima` | uma `OVER` → `false` |
| `famílias com limite` | só a família com linha em `budgets` no mês aparece |

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*PlanningFactsIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar** `PlanningFactsService` (`@Service`, `@Transactional(readOnly = true)`),
  com duas consultas simples em `goals` e `budgets` e o resto pelos serviços.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*PlanningFactsIT' --tests '*ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` → BUILD SUCCESSFUL.

```bash
git add api/src/main/java/br/com/liberdadefamilia/planning/PlanningFacts.java api/src/main/java/br/com/liberdadefamilia/planning/internal/PlanningFactsService.java api/src/test/java/br/com/liberdadefamilia/planning/PlanningFactsIT.java
git commit -m "feat(planning): expose the facts the achievements read"
```

### Task 4: Avaliação das conquistas

**Files:**
- Create: `gamification/AchievementsEarned.java`, `gamification/internal/AchievementRepository.java`,
  `AchievementService.java`, `AchievementListeners.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/gamification/AchievementsIT.java`,
  `AchievementListenersIT.java`

**Interfaces:**
- Consumes: `EntryWeeks` (Task 2), `PlanningFacts` (Task 3), `WeeklyStreak`, `AchievementKey`
  (Task 1), `Modules.isEnabled`, `Memberships.all` e `activeMember`.
- Produces: `record AchievementsEarned(UUID workspaceId, UUID membershipId)`;
  `AchievementService.evaluate(UUID, UUID, boolean)`, `evaluateFamily(UUID, boolean)`,
  `List<AchievementRepository.Row> earned(UUID workspaceId, UUID membershipId)` com
  `Row(AchievementKey key, Instant earnedAt, boolean silent)`;
  `AchievementRepository.grant(UUID ws, UUID membershipId, AchievementKey key, Instant at, boolean silent): boolean`
  e `deleteFor(UUID ws, UUID membershipId): int`.

**Como fica:** D5 e D6. Um lançamento grava as conquistas de quem tem direito antes da resposta
HTTP.

- [ ] **Step 1: Escrever os testes**

`AchievementsIT` (chama o serviço direto):

| Teste | Esperado |
|---|---|
| `primeiro lançamento` | Dono com um lançamento → `FIRST_TRANSACTION`, `silent` falso |
| `recorrência não dá o primeiro` | só lançamento de regra → nada |
| `quatro semanas` | entradas em 4 segundas seguidas → `STREAK_4_WEEKS` |
| `jovem sem reserva` | reserva de 6 meses → Dono, Adulto e Leitor ganham as três de reserva; o Jovem nenhuma |
| `meta alcançada para todos` | uma meta alcançada → os quatro papéis ganham `GOAL_ACHIEVED` |
| `primeira meta só de quem criou` | Adulto cria → só ele ganha `FIRST_GOAL` |
| `planejar desligado` | com Planejar desligado, nenhuma chave de Planejar é gravada |
| `módulo desligado` | com Conquistas desligado, `evaluateFamily` não grava nada |
| `idempotente` | avaliar duas vezes grava uma linha por chave e não muda `earned_at` |
| `evento só com linha nova` | primeira avaliação publica `AchievementsEarned`; a segunda não; `silent = true` nunca publica |
| `mês no orçamento fora daqui` | `evaluate` nunca grava `MONTH_IN_BUDGET` |

`AchievementListenersIT` (pelas rotas de verdade, `MockMvcTester`):

| Teste | Esperado |
|---|---|
| `lançar grava antes da resposta` | `POST` de um gasto e, logo depois, a linha `FIRST_TRANSACTION` existe com `silent` falso |
| `ligar o módulo grava em silêncio` | família com lançamentos, módulo desligado; ligar → `FIRST_TRANSACTION` com `silent` verdadeiro |
| `ligar planejar grava em silêncio` | Conquistas ligado, meta alcançada com Planejar desligado; ligar Planejar → `GOAL_ACHIEVED` silencioso |
| `membro novo grava em silêncio` | meta já alcançada; um Adulto entra → `GOAL_ACHIEVED` silencioso para ele |
| `jovem que vira adulto` | reserva de 3 meses; Jovem promovido → `RESERVE_1_MONTH` e `RESERVE_3_MONTHS` silenciosos |
| `falha na avaliação não desfaz o lançamento` | `PlanningFacts` falso (`@MockitoBean`) que lança na reserva → o `POST` do lançamento devolve 201 e o lançamento existe (Review Focus 4) |
| `excluir a conta apaga as conquistas` | Adulto com conquistas exclui a conta → nenhuma linha dele (Review Focus 5) |
| `remover da família apaga` | Dono remove o Adulto → nenhuma linha dele |

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*AchievementsIT' --tests '*AchievementListenersIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar** o repositório (`JdbcClient`, `INSERT ... ON CONFLICT DO NOTHING`,
  `grant` devolve se gravou), o serviço (D6) e os ouvintes (D6; o de `MemberRemoved` é
  `@EventListener` na transação).

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*AchievementsIT' --tests '*AchievementListenersIT' --tests '*ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` → BUILD SUCCESSFUL, JaCoCo no limite.

```bash
git add api/src/main/java/br/com/liberdadefamilia/gamification api/src/test/java/br/com/liberdadefamilia/gamification
git commit -m "feat(gamification): grant achievements after ledger and planning changes"
```

### Task 5: Rotas da sequência e das conquistas

**Files:**
- Create: `gamification/internal/GamificationController.java`, `StreakView.java`,
  `AchievementsView.java`, `AchievementView.java`
- Modify: `api/src/test/java/br/com/liberdadefamilia/RoleMatrixIT.java`, `api/openapi.json`,
  `web/src/shared/api/schema.ts`
- Test: `api/src/test/java/br/com/liberdadefamilia/gamification/GamificationControllerIT.java`

**Interfaces:**
- Consumes: `AchievementService` (Task 4), `EntryWeeks`, `WeeklyStreak`, `Clock`.
- Produces: as rotas e os records de D9; no `schema.ts`, `StreakView`, `AchievementsView`,
  `AchievementView` e o enum `AchievementKey`.

**Como fica:** D9.

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `sequência de quem chama` | Dono com 2 semanas, Adulto sem nada → Dono `{weeks: 2}`, Adulto `{weeks: 0, frozenWeeks: 0, longest: 0}` |
| `semana pelo fuso da família` | `Clock` em domingo 23h30 de São Paulo (segunda em UTC) e lançamento de agora → `weeks: 1` |
| `lista na ordem` | Dono → as 8 chaves na ordem de D5, `earnedAt` nulo nas não ganhas |
| `primeira leitura grava em silêncio` | lançamento gravado com o módulo desligado; ligar sem ouvinte (linha apagada no teste); `GET` → `FIRST_TRANSACTION` com `silent: true` |
| `lista do jovem` | Jovem → só `FIRST_TRANSACTION`, `STREAK_4_WEEKS`, `GOAL_ACHIEVED` |
| `lista do jovem não mostra reserva já ganha` | Adulto ganha `RESERVE_1_MONTH`, vira Jovem → a lista não traz chave de reserva (Review Focus 3) |
| `planejar desligado esconde as não ganhas` | Planejar desligado → `FIRST_GOAL` some; `GOAL_ACHIEVED` já ganha continua |
| `módulo desligado` | as duas rotas → 403 `module.disabled` |

`RoleMatrixIT`: as duas rotas com os quatro papéis → 200; sem participação → 404, como as outras.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*GamificationControllerIT' --tests '*RoleMatrixIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar** o controller (o padrão do `IndicatorController`) e, depois,
  atualizar o contrato.

Run: `cd api && UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` e
`corepack pnpm --dir web gen:api`.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*GamificationControllerIT' --tests '*RoleMatrixIT' --tests '*OpenApiDriftIT'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` e `corepack pnpm --dir web check` → verdes.

```bash
git add api/src/main/java/br/com/liberdadefamilia/gamification/internal api/src/test/java/br/com/liberdadefamilia/gamification api/src/test/java/br/com/liberdadefamilia/RoleMatrixIT.java api/openapi.json web/src/shared/api/schema.ts
git commit -m "feat(gamification): add the streak and achievements routes"
```

### Task 6: Mês dentro do orçamento

**Files:**
- Create: `gamification/internal/MonthInBudgetJob.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/gamification/MonthInBudgetJobIT.java`

**Interfaces:**
- Consumes: `PlanningFacts.familiesWithBudgets`, `withinBudget` (Task 3),
  `AchievementRepository.grant` (Task 4), `Modules`, `Memberships`, `Clock`.
- Produces: `void MonthInBudgetJob.run()` (o método do `@Scheduled`).

**Como fica:** D7.

- [ ] **Step 1: Escrever os testes** (`Clock` fixo em 2026-12-01T04:20 de São Paulo; mês avaliado
  2026-11)

| Teste | Esperado |
|---|---|
| `mês dentro` | limites de novembro, nenhum `OVER` → Dono, Adulto e Leitor ganham `MONTH_IN_BUDGET` com `silent` falso; o Jovem não |
| `uma categoria acima` | → ninguém ganha |
| `sem limite` | família sem `budgets` em novembro → não é lida |
| `módulo desligado` | Conquistas ou Planejar desligado → ninguém ganha |
| `não retroativo` | limites só em outubro, todos dentro → ninguém ganha |
| `uma família com erro não para as outras` | a primeira família lança (falso) → a segunda ganha |
| `aviso por membro` | publica um `AchievementsEarned` por membro com linha nova |

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*MonthInBudgetJobIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar** o job (D7), no molde do `RecurrenceJob`.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*MonthInBudgetJobIT'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` → BUILD SUCCESSFUL.

```bash
git add api/src/main/java/br/com/liberdadefamilia/gamification/internal/MonthInBudgetJob.java api/src/test/java/br/com/liberdadefamilia/gamification/MonthInBudgetJobIT.java
git commit -m "feat(gamification): grant the month in budget achievement on day one"
```

### Task 7: Aviso ao vivo

**Files:**
- Modify: `live/package-info.java`, `live/internal/Audience.java`, `live/internal/LiveRelay.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/live/internal/AudienceTest.java` (ou o teste
  existente de `Audience`), `api/src/test/java/br/com/liberdadefamilia/live/LiveAchievementIT.java`

**Interfaces:**
- Consumes: `AchievementsEarned` (Task 4).
- Produces: `Audience.Member(UUID membershipId)`; `LiveRelay.send(UUID, String, @Nullable UUID, Audience, @Nullable ModuleKey)`;
  o aviso `{"entity":"achievement"}`.

**Como fica:** D10 (parte da API).

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `Member inclui só a participação` | stream da participação → `true`; de outra da mesma família → `false` |
| `aviso só para quem ganhou` | dois streams abertos (Dono e Adulto); o Dono ganha `FIRST_TRANSACTION` → só o stream do Dono recebe `achievement` |
| `sem aviso com o módulo desligado` | `AchievementsEarned` publicado com Conquistas desligado → nenhum `achievement` |
| `os avisos de Planejar seguem iguais` | o teste existente dos avisos de orçamento sem Planejar continua verde |

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*Audience*' --tests '*LiveAchievementIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar** D10 na API.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*live*' --tests '*ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` → BUILD SUCCESSFUL.

```bash
git add api/src/main/java/br/com/liberdadefamilia/live api/src/test/java/br/com/liberdadefamilia/live
git commit -m "feat(live): tell only the earner about a new achievement"
```

### Task 8: Exportação e docs

**Files:**
- Create: `gamification/AchievementExport.java`, `gamification/ExportedAchievement.java`,
  `gamification/internal/AchievementJdbcExport.java`
- Modify: `privacy/package-info.java`, `privacy/internal/ExportService.java`, `docs/data-model.md`,
  `docs/architecture.md`
- Test: o IT da exportação pessoal que já existe (`ExportServiceIT` ou equivalente em `privacy`)

**Interfaces:**
- Produces: `List<ExportedAchievement> AchievementExport.of(UUID workspaceId, UUID membershipId)`
  (ordenada por `earned_at`); `ExportedAchievement(String key, Instant earnedAt)`; `dados.json` com
  `achievements: [{workspaceId, key, earnedAt}]`.

**Como fica:** D12 e D13. O `data-model.md` ganha a tabela `achievements`, o índice e a retenção
("some com a participação: ao sair da família, ao excluir a conta ou com a família"); o
`architecture.md` ganha o módulo `gamification` na tabela de módulos e as dependências novas de
`live` e `privacy`.

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `exportação traz as conquistas` | pessoa com `FIRST_TRANSACTION` em duas famílias → duas entradas com `workspaceId`, `key` e `earnedAt` |
| `só as da pessoa` | conquistas de outro membro não aparecem |
| `versão nova` | `formatVersion` = valor anterior + 1 |
| `exportação da família não muda` | o teste existente da exportação da família segue verde |

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*Export*'`
Expected: FAIL.

- [ ] **Step 3: Implementar** D12 e atualizar os dois docs.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*Export*' --tests '*ApplicationModulesTest'`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `cd api && ./gradlew build` → BUILD SUCCESSFUL.

```bash
git add api/src/main/java/br/com/liberdadefamilia/gamification api/src/main/java/br/com/liberdadefamilia/privacy api/src/test/java/br/com/liberdadefamilia/privacy docs/data-model.md docs/architecture.md
git commit -m "feat(privacy): export each person's achievements"
```

### Task 9: Cartão da sequência e tela de Conquistas

**Files:**
- Create: `shared/api/gamification.ts`, `features/gamification/register.ts`, `messages.pt-BR.ts`,
  `achievementCopy.ts`, `StreakHomeCard.tsx`, `AchievementsPage.tsx`
- Modify: `app/routes.tsx`, `app/MorePage.tsx`, `shared/live/invalidation.ts`, os handlers MSW
- Test: `features/gamification/StreakHomeCard.test.tsx`, `AchievementsPage.test.tsx`,
  `messages.test.ts`, `shared/live/invalidation.test.ts`

**Interfaces:**
- Consumes: `StreakView`, `AchievementsView`, `AchievementKey` do `schema.ts` (Task 5).
- Produces: `gamificationKeys = { all(ws), streak(ws), achievements(ws) }` (a partir de
  `[...queryKeys.workspace(ws), 'gamification']`); `useStreak(ws)`, `useAchievements(ws)`;
  `achievementName(intl, key)`, `achievementHint(intl, key)`; `data-tour` `achievements-streak` e
  `achievements-list` na tela (Task 12).

**Como fica:** D16 e a parte web de D10.

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `cartão com sequência` | `{weeks: 3, frozenWeeks: 1}` → "3 semanas seguidas" e "1 semana de folga incluída" |
| `cartão sem sequência` | `{weeks: 0}` → "Lance algo esta semana para começar uma sequência." |
| `cartão some com o módulo desligado` | módulo desligado → o slot não renderiza |
| `tela separa ganhas e próximas` | uma ganha e duas não → "Ganhas" com a data em PT-BR, "Próximas" com o "como chegar" |
| `link no Mais` | módulo ligado → link "Conquistas" para `/mais/conquistas`; desligado → sem link |
| `nenhuma frase de perda` | todos os textos de `messages.pt-BR.ts` → nenhum casa com o regex de D16 |
| `aviso invalida a lista` | `achievement` → invalida `gamificationKeys.all(ws)`; `transaction` também invalida esse prefixo |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- gamification invalidation`
Expected: FAIL.

- [ ] **Step 3: Implementar** o cliente (molde de `shared/api/planning.ts`, fora do barril), o
  `register`, o cartão, a tela (rota sob `ModuleRoute module="gamification"`, `lazy`), o link e a
  invalidação. Handlers MSW para as duas rotas.

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --dir web test -- gamification invalidation`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `corepack pnpm --dir web check`, `corepack pnpm --dir web build` e
`corepack pnpm --dir web check:chunks` → verdes, inicial abaixo de 200 KB.

```bash
git add web/src/shared/api/gamification.ts web/src/features/gamification web/src/app/routes.tsx web/src/app/MorePage.tsx web/src/shared/live web/src/mocks
git commit -m "feat(web): add the streak card and the achievements screen"
```

(Os handlers MSW ficam onde já estão os do projeto; o implementador ajusta o caminho do `git add`.)

### Task 10: Celebração depois de uma ação local

**Files:**
- Create: `features/gamification/AchievementCelebrationWatcher.tsx`, `celebrations.ts`
- Modify: `features/gamification/register.ts`
- Test: `features/gamification/AchievementCelebrationWatcher.test.tsx`, `celebrations.test.ts`

**Interfaces:**
- Consumes: `useAchievements`, `gamificationKeys` (Task 9); `onLocalAction` de `@/shared/events`;
  `Celebration`, `CelebrationStack` de `@/shared/motion`; `celebrationSound` de `@/shared/sound`.
- Produces: `newlyEarned(before: AchievementView[], after: AchievementView[], now: number): AchievementKey[]`
  em `celebrations.ts`.

**Como fica:** D11.

- [ ] **Step 1: Escrever os testes**

`celebrations.test.ts`:

| Teste | Esperado |
|---|---|
| `nova e não silenciosa` | `FIRST_TRANSACTION` ganha agora, ausente antes → `[FIRST_TRANSACTION]` |
| `silenciosa não celebra` | `silent: true` → `[]` |
| `conquista antiga não celebra` | `earnedAt` de 6 minutos atrás → `[]` (Review Focus 2) |
| `já estava antes` | → `[]` |

`AchievementCelebrationWatcher.test.tsx`:

| Teste | Esperado |
|---|---|
| `celebra depois de uma ação local` | base no cache, ação local, a resposta traz uma nova → um `role="status"` com "Conquista: Primeiro lançamento" |
| `sem base não celebra` | nada no cache, ação local, a resposta traz uma nova → nenhum `status` (Review Focus 2) |
| `fila não celebra` | ação com `fromQueue: true` → nenhum `status` |
| `aviso ao vivo não celebra` | invalidação por `achievement` com item novo → nenhum `status` |
| `som só ligado` | som desligado → `playAchievement` não é chamado |
| `outra família` | ação local de outro `workspaceId` → nada |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- AchievementCelebrationWatcher celebrations`
Expected: FAIL.

- [ ] **Step 3: Implementar** D11 e registrar o slot `workspace` com `lazy`.

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --dir web test -- AchievementCelebrationWatcher celebrations`
Expected: PASS.

- [ ] **Step 5: Conferir e commitar**

Run: `corepack pnpm --dir web check`, `build` e `check:chunks` → verdes.

```bash
git add web/src/features/gamification
git commit -m "feat(web): celebrate a new achievement after a local action"
```

### Task 11: Dicas

**Files:**
- Create: `shared/tips/tipsPreference.ts`, `seenTips.ts`, `PageTip.tsx`, `TipBubble.tsx`,
  `messages.pt-BR.ts`, `index.ts`
- Modify: `features/settings/SettingsPage.tsx` e as mensagens de Ajustes, `test-setup.ts`,
  `web/e2e/support.ts`, `features/dashboard/DashboardPage.tsx`,
  `features/transactions/TransactionsPage.tsx`, `features/accounts/AccountsPage.tsx`, a tela da
  calculadora em `features/indicators/`, e a tela de importação se existir (Itens herdados)
- Test: `shared/tips/tipsPreference.test.ts`, `seenTips.test.ts`, `PageTip.test.tsx`,
  `messages.test.ts`, `features/settings/SettingsPage.test.tsx`

**Interfaces:**
- Produces: `TipId`; `isTipsEnabled()`, `useTipsEnabled()`; `hasSeenTip(userId, key)`,
  `markTipSeen(userId, key)`; `<PageTip id={TipId} />`. Os testes ligam e desligam as dicas
  escrevendo `liberdade.tips` no `localStorage`; o e2e usa a opção `tips` do fixture.

**Como fica:** D14. Textos das dicas (até 140 caracteres; o implementador pode ajustar a redação
dentro da regra):

| Id | Texto |
|---|---|
| `tips.dashboard` | Aqui está o mês da família: quanto entrou, quanto saiu e para onde foi. |
| `tips.transactions` | Todos os lançamentos do mês. Toque em um para editar ou excluir. |
| `tips.accounts` | Cada conta mostra o saldo. Marque as que são reserva de emergência. |
| `tips.calculator` | Compare o rendimento com a inflação do período. É uma conta, não uma recomendação. |
| `tips.import` | O arquivo do banco é lido neste aparelho. Você confere cada linha antes de gravar. |

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `ligada por padrão` | sem chave → `isTipsEnabled()` `true` |
| `desligar grava off` | `useTipsEnabled` → `setOn(false)` → `localStorage['liberdade.tips'] = 'off'` |
| `sem armazenamento` | `localStorage` que lança → ligada, sem erro |
| `visto por pessoa` | `markTipSeen('u1', 'transactions')` → `hasSeenTip('u2', 'transactions')` `false` |
| `primeira visita mostra` | dicas ligadas, não vista → texto e "Entendi" |
| `Entendi some e não volta` | clicar → some; remontar → não aparece |
| `desligada não mostra` | `liberdade.tips = 'off'` → nada |
| `até 140 caracteres` | todo `tips.*` e todo passo de tour (Task 12) → `length <= 140` |
| `nenhuma frase de perda` | regex de D16 sobre `shared/tips/messages.pt-BR.ts` |
| `interruptor em Ajustes` | "Mostrar dicas" `role="switch"`, `aria-checked` verdadeiro; clicar → falso e `off` gravado |
| `support desliga as dicas` | uma página aberta pelo fixture `test` tem `liberdade.tips = 'off'`; com `tips: true` no `test.use`, não tem (Review Focus 1) |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- tips SettingsPage`
Expected: FAIL.

- [ ] **Step 3: Implementar** D14: os arquivos de `shared/tips`, o interruptor, `PageTip` nas telas
  da lista, `test-setup.ts` gravando `liberdade.tips = 'off'` antes de cada teste e o fixture do
  e2e com a opção `tips` (padrão `false`) que faz `addInitScript` com a mesma chave. O teste do
  fixture fica em `web/e2e/tips.spec.ts` (Task 13 o completa).

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --dir web test`
Expected: PASS, a suíte inteira (os testes de tela que já existem continuam verdes).

- [ ] **Step 5: Conferir e commitar**

Run: `corepack pnpm --dir web check`, `build` e `check:chunks` → verdes.

```bash
git add web/src/shared/tips web/src/features/settings web/src/test-setup.ts web/e2e/support.ts web/src/features/dashboard web/src/features/transactions web/src/features/accounts web/src/features/indicators
git commit -m "feat(web): add first visit tips with a switch in settings"
```

### Task 12: Tours

**Files:**
- Create: `shared/tips/PageTour.tsx`, `Tour.tsx`, `tours.ts`
- Modify: `shared/tips/messages.pt-BR.ts`, `shared/tips/index.ts`, `features/planning/PlanHub.tsx`,
  a tela principal de `features/indicators/`, `features/gamification/AchievementsPage.tsx`
  (`data-tour` e `<PageTour>`)
- Test: `shared/tips/Tour.test.tsx`

**Interfaces:**
- Consumes: `useTipsEnabled`, `hasSeenTip`, `markTipSeen` (Task 11).
- Produces: `TourId`; `<PageTour id={TourId} />`; os passos de D15.

**Como fica:** D15. Passos (texto até 140 caracteres; âncoras `data-tour`):

| Tour | Âncora | Texto |
|---|---|---|
| planning | `planning-budget` | Defina um limite por categoria e acompanhe o mês. |
| planning | `planning-goals` | Metas guardam dinheiro para um objetivo, no seu ritmo. |
| planning | `planning-reserve` | A reserva mostra quantos meses de custo a família já cobre. |
| planning | `planning-recurring` | Contas que se repetem entram sozinhas como previstas. |
| indicators | `indicators-cards` | Cada cartão mostra um indicador oficial, com a fonte e a data. |
| indicators | `indicators-calculator` | A calculadora compara um rendimento com a inflação. |
| achievements | `achievements-streak` | Sua sequência conta as semanas em que você registrou algo. Uma semana de folga por mês não interrompe. |
| achievements | `achievements-list` | As conquistas aparecem aqui, com a data. Sem pressa. |

- [ ] **Step 1: Escrever os testes**

| Teste | Esperado |
|---|---|
| `abre na primeira visita` | dicas ligadas, tour não visto → `role="dialog"` com "Passo 1 de 4" e o foco nele |
| `Próximo e Concluir` | avançar até o fim → "Concluir" fecha e marca visto; remontar → não abre |
| `Pular em todo passo` | no passo 2, "Pular" → fecha e marca visto |
| `Esc pula` | `Escape` → fecha |
| `foco volta` | o foco volta ao elemento que tinha antes |
| `âncora ausente é pulada` | sem `planning-reserve` na tela → "Passo N de 3" |
| `desligado não abre` | `liberdade.tips = 'off'` → nada |
| `movimento reduzido` | `prefers-reduced-motion` → `scrollIntoView` com `behavior: 'auto'` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- Tour`
Expected: FAIL.

- [ ] **Step 3: Implementar** D15 e pôr as âncoras e o `PageTour` nas três telas.

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --dir web test`
Expected: PASS, a suíte inteira.

- [ ] **Step 5: Conferir e commitar**

Run: `corepack pnpm --dir web check`, `build` e `check:chunks` → verdes.

```bash
git add web/src/shared/tips web/src/features/planning/PlanHub.tsx web/src/features/indicators web/src/features/gamification/AchievementsPage.tsx
git commit -m "feat(web): add short tours to the planning, indicators and achievements screens"
```

### Task 13: E2E, acessibilidade e conferência final

**Files:**
- Create: `web/e2e/gamification.spec.ts`
- Modify: `web/e2e/tips.spec.ts`, `web/e2e/a11y.spec.ts` (se as telas novas entram lá)

**Interfaces:**
- Consumes: `test`, `createFamily`, `setModules`, `createInvitation`, `openInvitation`,
  `recordExpense`, `withContext`, `CONTEXT_OPTIONS` de `web/e2e/support.ts`.

**Como fica:** o critério de saída da 8.3 coberto no CI, com duas pessoas.

- [ ] **Step 1: Escrever os e2e**

| Teste | Esperado |
|---|---|
| `sequência e conquistas para duas pessoas` | Dono cria a família, liga os módulos, convida um Adulto. Dono lança um gasto → celebração "Conquista: Primeiro lançamento"; Início mostra "1 semana seguida". Adulto entra → Início mostra "Lance algo esta semana..."; em Conquistas, "Primeiro lançamento" está em "Próximas". Adulto lança → celebra e passa para "Ganhas" |
| `ligar o módulo não celebra` | família com lançamento e módulo desligado; ligar → Conquistas mostra "Primeiro lançamento" ganha e nenhuma celebração aparece |
| `dica aparece uma vez` (`test.use({ tips: true })`) | primeira visita a Lançamentos → dica; "Entendi"; recarregar → não volta |
| `dicas desligam em Ajustes` (`tips: true`) | desligar "Mostrar dicas" → Painel do mês sem dica; Planejar sem tour |
| `tour com teclado` (`tips: true`) | Conquistas → tour abre; Tab chega a "Pular"; Esc fecha; o foco volta |
| `axe` | Conquistas, a dica aberta e o tour aberto sem violações |

- [ ] **Step 2: Rodar**

Run: `corepack pnpm --dir web e2e -- gamification tips` (o compose de e2e de pé, como nas outras
entregas)
Expected: PASS.

- [ ] **Step 3: Rodar a suíte toda**

Run: `cd api && ./gradlew build`, `corepack pnpm --dir web check`, `corepack pnpm --dir web build`,
`corepack pnpm --dir web check:chunks` e `corepack pnpm --dir web e2e`
Expected: tudo verde; `check:chunks` com o inicial no mesmo tamanho de antes da 8.3 (diferença só
dos `register`, abaixo de 1 KB) e abaixo de 200 KB (Review Focus 1: os e2e de Planejar e
Indicadores seguem verdes).

- [ ] **Step 4: Commitar**

```bash
git add web/e2e/gamification.spec.ts web/e2e/tips.spec.ts web/e2e/a11y.spec.ts
git commit -m "test(e2e): cover the streak, achievements, tips and tours"
```

- [ ] **Step 5: Corpo do PR 2** com o roteiro do mantenedor no staging: ligar Conquistas; duas
  pessoas lançam e veem a própria sequência e a tela de Conquistas; uma meta alcançada aparece para
  as duas; o Jovem não vê conquista de reserva; desligar "Mostrar dicas" some com dicas e tours.
