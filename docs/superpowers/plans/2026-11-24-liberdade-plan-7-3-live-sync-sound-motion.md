# Liberdade · Plano 7.3: Sincronização ao vivo, som e movimento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a terceira e última entrega da v0.2.
- Quem está com o app aberto vê, em até 5 segundos, o que outra pessoa da família lançou ou mudou em
  outro aparelho, sem recarregar. A API manda só um aviso curto ("mudou X"), filtrado pelo que cada
  pessoa pode ver, e o app busca de novo (US-25).
- O lançamento que chegou de outro aparelho ganha um destaque de 200 ms e um aviso para leitor de
  tela, sem som.
- Em Ajustes, "Sons de conquista" (desligado por padrão) toca um som curto quando uma meta é atingida
  ou a reserva chega ao alvo, logo depois de uma ação feita neste aparelho (US-28).
- A base de movimento entra no design system: tokens de duração, um hook de movimento reduzido, a
  classe de destaque e um componente de celebração. Com `prefers-reduced-motion`, nada anima.

**Architecture:**
- **`live` (módulo novo da API):** uma rota SSE por família, um registro em memória dos streams
  abertos e ouvintes depois do commit que transformam os eventos dos outros módulos em avisos
  `changed`. O registro é uma interface, para trocar por LISTEN/NOTIFY quando houver mais de uma
  instância. Nenhum módulo depende de `live`.
- **Eventos novos, só com ids:** `ledger`, `planning` e `workspace` publicam o que ainda não
  publicavam (criação de conta, troca de dono, categoria, tag, orçamento, meta e apelido).
- **Front:**
  - `shared/live` abre um `EventSource` por aba visível e invalida as consultas da família por
    entidade;
  - `shared/motion` traz os tokens, `useReducedMotion` e `Celebration`;
  - `shared/sound` guarda a preferência e carrega o sintetizador num chunk próprio, só depois que a
    pessoa liga o som ou aperta "Ouvir";
  - a feature nova `settings` (núcleo) é a tela Ajustes.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, `SseEmitter` do Spring MVC,
  PostgreSQL 18, JdbcClient. Testes com Testcontainers, `MockMvc`/`MockMvcTester`.
- **Web:** React 19, react-router v8, TanStack Query, react-intl, MSW 3, Vitest (jsdom, sem
  `EventSource` nem `AudioContext`) e Playwright com axe. `EventSource` e Web Audio são do navegador.
- **Sem dependências novas.**

**Spec:**
- [Spec do Plano 7](../specs/2026-11-23-liberdade-plan-7-planning-design.md), aprovado pelo
  mantenedor em 2026-11-23. Este plano cobre só a 7.3: as seções 6.9 e 6.10, a parte 7.3 de 3, 7 e
  8. É a autoridade: este plano argumenta a partir dele.
- [PRD](../../prd.md) (US-25 e US-28), [Roadmap](../../roadmap.md),
  [Arquitetura](../../architecture.md), [Modelo de dados](../../data-model.md),
  [Design system](../../design-system.md).

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits (`feat(live): ...`, `feat(ledger): ...`,
  `feat(web): ...`, `test(e2e): ...`, `docs: ...`), com o assunto em minúsculas. Cada commit
  termina com a linha `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **O aviso não carrega dado:** `event: changed` com `data: {"entity":"...","id":"..."}` e nada
  mais. Entidades: `transaction`, `account`, `category`, `tag`, `rule`, `budget`, `goal`,
  `reserve`, `members`, `modules`. Nunca valores, nomes nem descrições.
- **Isolamento (ADR-0008):** o stream de uma família só recebe avisos dela. A rota usa
  `@WorkspaceScoped`; família alheia responde 404.
- **Visibilidade do Jovem (US-10):** `transaction` e `account` só chegam a quem vê a conta (origem ou
  destino, a mesma regra de `AccountScope.sqlTouches`). `budget`, `rule` e `reserve` nunca chegam
  ao Jovem.
- **Papéis:** os quatro papéis abrem o stream.
- **Módulo:** com o Planejamento desligado, nenhum aviso de `budget`, `goal`, `rule` nem `reserve`
  sai.
- **Sessão:** o stream usa o cookie da sessão e a mesma guarda de família. A cada batida (25 s) a API
  confere a sessão e a participação; tirar alguém da família fecha os streams dela na hora.
- **Limites:** 5 streams por pessoa e 1.000 por processo. Acima disso, 429 `rate.limited`.
- **Tempo:** o `Clock` da API em tudo que a API decide. Scheduling fica desligado nos testes; os
  testes chamam a batida direto.
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR.
- **Logs:** nunca o id da sessão, o conteúdo de um aviso nem ids de pessoa. Falhas logam só
  `failure.getClass().getName()`.
- **Fronteiras de módulo:**
  - `live` declara `allowedDependencies = {"identity", "workspace", "ledger", "planning", "shared"}`;
  - nenhum módulo depende de `live`;
  - `ledger` continua com `{"workspace", "shared"}`;
  - o `ApplicationModulesTest` passa.
- **Contrato:** a rota nova entra no `api/openapi.json`, no `web/src/shared/api/schema.ts` e no
  `RoleMatrixIT` (quatro papéis), no mesmo commit.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `shared*` e `*.domain*`.
- **Movimento (spec, seção 3):** só `transform` e `opacity` animam. Toda duração vem dos tokens. Com
  `prefers-reduced-motion: reduce`, nada anima e a celebração mostra o ícone parado.
- **Som (spec 6.10):** desligado por padrão, guardado no `localStorage` do aparelho, Web Audio, no
  máximo 300 ms e volume baixo. Toca só quando uma meta é atingida ou a reserva chega ao alvo, logo
  depois de uma ação local. Nunca em aviso ao vivo, alerta, erro ou orçamento.
- **Desempenho:** o carregamento inicial do web continua até 200 KB (`check:chunks`, hoje 195,1 KB).
  O sintetizador fica num chunk à parte, e Ajustes carrega sob demanda.
- **Acessibilidade:** axe no Playwright cobre Ajustes. O interruptor é `role="switch"` com
  `aria-checked`. O aviso de lançamento novo é `aria-live="polite"`, no máximo uma vez a cada 10 s.
- **Offline (ADR-0007):** sem rede, o stream fecha; ao voltar, o app reabre e busca tudo da família.
- **Ética (design system §8):** sem som por padrão, sem som em aviso de outra pessoa, sem efeito que
  prenda a atenção. A celebração dura até 1 s de animação e não bloqueia a tela.
- **Custo:** R$ 0 fixo (spec, seção 9). Nada de serviço novo; tudo roda no processo da API.
- **GitHub Actions:** nenhum workflow novo e nenhuma permissão nova.
- **Versões:** nenhuma biblioteca nova nem versão nova.
- **Ações externas:** nenhuma. O staging recebe o deploy pelo pipeline de sempre, depois do merge.

### Pré-requisitos locais

Os mesmos da 7.2: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o compose de
e2e.

## Entrega

- **PR 1 (docs):** este plano e o README dos planos. Entra antes do código.
- **PR 2 (código):** branch `feat/plan-7-3-live-sync-sound-motion`, cortada da `main` depois do
  PR 1. Um commit por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o deploy do staging sai pelo pipeline. O mantenedor confere no staging o
  critério de saída da 7.3 (seção 8 do spec):
  - um lançamento aparece em outro navegador em até 5 s;
  - o som só toca depois de ligado;
  - com `prefers-reduced-motion`, nada anima;
  - o Caddy não segura o stream (D14).
- **Depois disso:** a v0.2 fecha, e o próximo passo é o roteiro pós-Plano 7.

### Itens herdados

| Item | Onde |
|---|---|
| Tempo da celebração usando ref (7.2) | Task 6, no `Celebration` novo |
| Javadoc sobre a idempotência do ouvinte de metas (7.2) | Task 1, junto do `GoalChanged` que o ouvinte passa a publicar |
| Testes de borda do orçamento, das metas e da projeção (7.2) | Fora da 7.3 |
| Fidelidade dos mocks (MSW) (7.2) | Fora da 7.3, salvo o handler novo do stream |
| `month` obrigatório no `spending-split` e no orçamento (7.2) | Fora da 7.3 (muda o contrato sem relação com esta entrega) |
| Limpeza com RETURNING no soft delete (7.2) | Fora da 7.3 |
| Esqueleto nos cards, salto de layout, GET a mais no Início (7.2) | Fora da 7.3 |
| "Tentar de novo" quando os módulos falham (7.2) | Fora da 7.3 |
| Higiene (literais, imports, checagem morta) (7.2) | Fora da 7.3 |
| `PATCH` de cliente antigo limpando `paymentMethod` (7.1) | Fora da 7.3 |
| 422 antes de 404 no `PATCH` de regra (7.1) | Fora da 7.3 |
| Dica de maiúsculas no nome da tag (7.1) | Fora da 7.3 |
| Limite de erro do `TagsTab` sob demanda (7.1) | PR próprio, com a tela de erro de rota em PT-BR |
| Issues #36, #38, #39 e #40 | Fora da 7.3 (infra, testes de proxy e prettier de docs) |

## Decisões deste plano

A seção 11 do spec deixa estes pontos ao construtor, e o plano decide:

- **D1. `live` é um módulo da API, não um pacote compartilhado.** Ele lê de `identity`, `workspace`,
  `ledger` e `planning` e ninguém lê dele, o que o Modulith verifica.
  - `live/package-info.java` com
    `@ApplicationModule(allowedDependencies = {"identity", "workspace", "ledger", "planning", "shared"})`.
  - Tudo em `live/internal`:
    - `LiveController`;
    - `LiveStreams` (interface) e `InMemoryLiveStreams`;
    - `LiveStream` (record: `streamId`, `workspaceId`, `membershipId`, `userId`, `role`,
      `sessionId`, `emitter`);
    - `LiveChange(String entity, @Nullable UUID id)`;
    - `Audience` (sealed: `Everyone`, `NoYouth`,
      `AccountViewers(UUID accountId, @Nullable UUID toAccountId, Set<UUID> extraMemberships)`);
    - `LiveRelay` (os ouvintes);
    - `LiveHeartbeat`;
    - `LiveProperties` (`liberdade.live.max-per-person=5`, `liberdade.live.max-streams=1000`,
      `liberdade.live.heartbeat=25s`, `liberdade.live.stream-timeout=30m`).
- **D2. Eventos.** Records públicos na raiz do módulo que publica, só com ids:
  - `ledger`:
    - `AccountService.create` passa a publicar o `AccountChanged` que já existe;
    - `AccountOwnerChanged(UUID workspaceId, UUID accountId, @Nullable UUID formerOwnerMembershipId, @Nullable UUID ownerMembershipId)`
      sai de `setOwner`;
    - `CategoryChanged(UUID workspaceId, UUID categoryId)` sai de criar, editar, arquivar e
      desarquivar no `CategoryService`;
    - `TagChanged(UUID workspaceId, UUID tagId)` sai de `rename` no `TagService`;
    - `TransactionChanged` fica como está.
  - `planning`:
    - `BudgetChanged(UUID workspaceId, YearMonth month)` sai de `set`, `delete` e `copyPrevious`;
    - `GoalChanged(UUID workspaceId, UUID goalId)` sai de `create`, `update` e `contribute`, e do
      `GoalAchievementListener` quando ele grava `achieved_at`;
    - ficam como estão `GoalArchived`, `ReserveTargetChanged`, `ReserveCostChanged` e
      `RecurringRuleCreated`/`Edited`/`Archived`.
  - `workspace`:
    - `MemberProfileChanged(UUID workspaceId, UUID membershipId)` sai quando o apelido muda em
      `MemberService.update`;
    - ficam como estão `MemberJoined`, `MemberRemoved`, `MemberRoleChanged`, `ModuleToggled` e
      `WorkspaceDeletionRequested`.
  - Sem evento para convites, nome da família e o job diário das recorrências (o job grava
    previstos do mês seguinte; quem abre a tela busca). Sem `id` no aviso de `budget`, `reserve`,
    `members` e `modules`.
- **D3. Quem recebe o quê.** O `LiveRelay` traduz cada evento num `LiveChange` e numa `Audience`:

  | Evento | `entity` | `id` | Audiência |
  |---|---|---|---|
  | `TransactionChanged` | `transaction` | transação | `AccountViewers(accountId, toAccountId, {})` |
  | `AccountChanged` | `account` | conta | `AccountViewers(accountId, null, {})` |
  | `AccountOwnerChanged` | `account` | conta | `AccountViewers(accountId, null, {formerOwner})` |
  | `CategoryChanged` | `category` | categoria | `Everyone` |
  | `TagChanged` | `tag` | tag | `Everyone` |
  | `RecurringRule*` | `rule` | regra | `NoYouth` |
  | `BudgetChanged` | `budget` | — | `NoYouth` |
  | `GoalChanged`, `GoalArchived` | `goal` | meta | `Everyone` |
  | `ReserveTargetChanged`, `ReserveCostChanged` | `reserve` | — | `NoYouth` |
  | `MemberJoined`, `MemberRemoved`, `MemberRoleChanged`, `MemberProfileChanged` | `members` | — | `Everyone` |
  | `ModuleToggled` | `modules` | — | `Everyone` |

  - `AccountViewers` resolve por stream: papel diferente de `YOUTH` recebe; o Jovem recebe se
    `AccountVisibility.canSee(...)` disser que sim ou se a participação dele estiver em
    `extraMemberships` (o antigo dono precisa saber que perdeu a conta).
  - `ledger/AccountVisibility` (interface pública nova, implementada em `ledger/internal` sobre
    `AccountRepository.scopeOf`):
    `boolean canSee(UUID workspaceId, UUID membershipId, Role role, UUID accountId, @Nullable UUID toAccountId)`.
    Só o Jovem consulta o banco.
  - `rule`, `budget`, `goal` e `reserve` só saem com `Modules.isEnabled(ws, ModuleKey.PLANNING)`.
    O relay confere uma vez por evento, não por stream.
- **D4. Sessão e participação vivas.**
  - `identity/SessionLiveness` (interface pública nova, implementação em `identity/internal`):
    `Set<String> aliveAmong(Collection<String> sessionIds)`, um `SELECT session_id FROM spring_session WHERE session_id = ANY(?) AND expiry_time > ?`
    com o instante do `Clock` em milissegundos.
  - `workspace/Memberships` ganha `Set<UUID> activeAmong(Collection<UUID> membershipIds)`.
  - Uma consulta de cada por batida, para todos os streams do processo.
- **D5. Registro em memória.**
  - `InMemoryLiveStreams` guarda os streams por família num `ConcurrentHashMap`.
  - A entrega roda num executor de threads virtuais que o próprio bean cria e fecha. O relay nunca
    escreve no socket na thread do commit.
  - Um `IOException` (ou `IllegalStateException` de emitter já fechado) tira o stream e o completa.
  - `SmartLifecycle` com fase `SmartLifecycle.DEFAULT_PHASE`, acima da do
    `WebServerGracefulShutdownLifecycle`: no `stop`, completa todos os emitters, para o deploy não
    esperar streams abertos.
  - A interface `LiveStreams` não expõe o `ConcurrentHashMap`, para uma versão com LISTEN/NOTIFY
    entrar no lugar:
    - `Optional<LiveStream> open(LiveStream stream)` (vazio quando passa do limite);
    - `void publish(UUID workspaceId, LiveChange change, Audience audience)`;
    - `void closeMember(UUID workspaceId, UUID membershipId)`;
    - `void closeWorkspace(UUID workspaceId)`;
    - `Collection<LiveStream> all()`;
    - `void close(LiveStream stream)`.
- **D6. A rota.** `GET /v1/workspaces/{ws}/events`, `produces = text/event-stream`, com
  `@WorkspaceScoped` e os quatro papéis.
  - O `SseEmitter` tem tempo explícito de 30 min (`liberdade.live.stream-timeout`), porque o padrão
    do Tomcat para async é 30 s. Ao passar o tempo, o emitter completa e o navegador reconecta
    sozinho.
  - O id da sessão vem de `request.getSession(false).getId()`, na thread do pedido. Depois de
    devolver o emitter, ninguém toca na sessão: a sessão JDBC não é segura entre threads.
  - Antes de devolver, manda `retry: 5000` e um comentário de abertura, para o navegador ver o
    `open` na hora.
  - O limite é conferido antes de criar o emitter: passou de 5 da pessoa ou de 1.000 no processo →
    `RateLimitedException` (429 `rate.limited`, com `Retry-After: 60`).
- **D7. A batida.** `LiveHeartbeat.beat()` com `@Scheduled(fixedRateString = "${liberdade.live.heartbeat}")`.
  - Fecha os streams cuja sessão não está em `aliveAmong` ou cuja participação não está em
    `activeAmong`, e manda um comentário (`:`) aos outros.
  - `MemberRemoved` e `MemberRoleChanged` fecham na hora os streams daquela participação (quem mudou
    de papel reconecta com o papel novo). `WorkspaceDeletionRequested` fecha os da família.
  - Os testes chamam `beat()` direto.
- **D8. O fim do stream não pode virar 401.** Quando o emitter completa, o Tomcat faz um dispatch
  `ASYNC` que passa de novo pelos filtros e pelos interceptadores.
  - `SecurityConfig` ganha `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`, porque o
    pedido original já foi autorizado. Sem isso, um stream de sessão encerrada termina em 401.
  - `WorkspaceGuardInterceptor.preHandle` devolve `true` quando
    `request.getDispatcherType() == DispatcherType.ASYNC`.
- **D9. O app.** `shared/live/useLiveSync(workspaceId, options?)`, montado por um componente
  `LiveSync` no último ramo do `WorkspaceLayout` (com a família pronta e sem exclusão agendada).
  - Sem `EventSource` no ambiente (jsdom) e sem `createSource` nas opções, não faz nada. Os testes
    de hoje não mudam.
  - Abre só com a aba visível e com rede. Fecha com a aba escondida por mais de 5 min ou sem rede.
  - Todo `open` depois do primeiro (reconexão, volta da aba, volta da rede, fim dos 30 min) invalida
    `['ws', ws]`.
  - Erro com `readyState === CLOSED` (429, 204, 401) põe o app no modo sem stream: invalida
    `['ws', ws]` no `visibilitychange` para visível e no `online`, e tenta abrir de novo no máximo
    uma vez a cada 60 s.
  - Os avisos se juntam por 250 ms e invalidam por prefixo:

    | `entity` | Invalida |
    |---|---|
    | `transaction` | `transactions`, `dashboard`, `accounts`, `tags`, `planning` |
    | `account` | `accounts`, `transactions`, `dashboard`, `planning` |
    | `category` | `categories`, `transactions`, `dashboard`, `planning` |
    | `tag` | `tags`, `transactions` |
    | `rule` | `recurring-rules`, `transactions`, `planning` |
    | `budget` | `planning`, `budgets` |
    | `goal` | `planning`, `goals` |
    | `reserve` | `planning`, `reserve` e `planning`, `projection` |
    | `members` | `members`, `invitations`, `accounts` |
    | `modules` | `modules`, `planning` |

    Cada linha vira `['ws', ws, ...prefixo]`. Entidade desconhecida invalida `['ws', ws]`.
  - O MSW responde a rota com 204, então o app de desenvolvimento fica no modo sem stream.
- **D10. Destaque e leitor de tela.**
  - `shared/live/localChanges.ts`: `markLocalChange(id)` guarda o id por 5 min e é chamado onde o
    app salva um lançamento (`enqueueTransaction`, `useUpdateTransaction`,
    `useRestoreTransaction`, `useConfirmPlanned`).
  - Um aviso `transaction` cujo id não é local vira chegada remota por 60 s
    (`markRemoteArrival(id)`, `isRecentArrival(id)`).
  - `TransactionRow` com chegada recente ganha `motion-safe:animate-arrive` (200 ms, `transform` e
    `opacity`). Sem som.
  - `TransactionsPage` tem uma região `aria-live="polite"` que diz "Lançamento novo na lista." quando
    a lista mostra uma chegada ainda não anunciada, no máximo uma vez a cada 10 s.
- **D11. Movimento.**
  - Em `index.css`, no `@theme`: `--motion-fast: 150ms`, `--motion-base: 200ms`,
    `--motion-sheet: 250ms`, `--motion-celebration: 900ms`, `--motion-ease: ease-out`,
    `--animate-arrive` e `--animate-goal-pop` usando os tokens, e `--animate-sheet-in`.
  - `@media (prefers-reduced-motion: reduce)` zera as durações e põe `animation: none` nas classes
    de animação.
  - `shared/motion/useReducedMotion(): boolean` com `useSyncExternalStore` sobre `matchMedia`.
    Substitui `readAnimate` (`DashboardPage`) e `prefersReducedMotion` (`GoalCelebration`).
  - `shared/motion/Celebration` sai de `GoalCelebration`: ícone, texto, até 1 s de animação, some em
    6 s, não bloqueia (`pointer-events-none` no contêiner), ícone parado com movimento reduzido. O
    tempo usa ref para `onDone`, então uma nova renderização do pai não reinicia o relógio (backlog
    da 7.2).
  - A `Sheet` entra com `animate-sheet-in` (`--motion-sheet`).
- **D12. Celebração da reserva.** Um slot novo `planning-reserve-celebration` (Dono e Adulto) com o
  `ReserveCelebrationWatcher`.
  - Depois de um `transaction-saved` que não veio da fila, busca a reserva e celebra quando
    `hasReserveAccounts && months !== null && months >= targetMonths`.
  - Guarda em `localStorage` `liberdade.reserve-celebrated:{ws}`. Uma leitura depois com cobertura
    abaixo do alvo apaga a marca, e a próxima chegada ao alvo celebra de novo.
  - Texto: "Reserva completa: {months} meses guardados!".
- **D13. Som.**
  - `shared/sound/soundPreference.ts`: `useSoundEnabled(): [boolean, (on: boolean) => void]` sobre
    `localStorage` `liberdade.sound` (`'on'` ou ausente). Sem `localStorage`, fica desligado.
  - `shared/sound/playAchievement(): Promise<void>` faz `import('./synth')` e toca. O chunk só é
    pedido quando o som está ligado e há o que tocar, ou quando a pessoa aperta "Ouvir".
  - `synth.ts`: um `AudioContext` criado no primeiro toque (gesto), `navigator.audioSession.type = 'ambient'`
    quando existe, duas notas senoidais (C6 e E6), 120 ms cada, envelope de ataque de 10 ms, ganho
    máximo 0,08. Total abaixo de 300 ms.
  - **Regra do momento:** `shared/events` ganha `lastLocalActionAt(workspaceId): number | null`,
    marcado só por ações que não vieram da fila. Uma celebração toca som só com o som ligado e com
    uma ação local nos últimos 10 s. Celebração que nasce de um refetch ao vivo (por exemplo, na tela
    de Metas aberta) nunca toca.
  - A feature nova `features/settings` (núcleo, entra na lista `CORE` do `eslint/boundaries.js`):
    rota sob demanda `/mais/ajustes`, título "Ajustes", interruptor "Sons de conquista" com a ajuda
    "Um som curto quando uma meta é atingida ou a reserva fica completa." e o botão "Ouvir". A
    `MorePage` ganha o link "Ajustes".
- **D14. Caddy.** O `reverse_proxy` do Caddy já repassa `text/event-stream` sem segurar. O
  `deploy/Caddyfile` não tem `encode` nem `flush_interval`. A conferência no staging decide: só se o
  aviso demorar mais de 5 s, entra `flush_interval -1` num `handle` de `/v1/workspaces/*/events`, num
  PR próprio.
- **D15. Docs.** ADR-0016 (avisos ao vivo por SSE no processo, com o caminho para LISTEN/NOTIFY),
  `docs/architecture.md` (módulo `live`, eventos novos e a rota) e o README dos planos.

## Review Focus

1. **Vazar para o Jovem:** um lançamento numa conta só do Adulto chega ao stream do Adulto e não ao
   do Jovem; o Jovem que deixou de ser dono de uma conta recebe o aviso que o tira dela. Testes
   `adultOnlyAccountNeverReachesYouth`, `formerOwnerIsTold` e `youthNeverGetsBudgetRuleOrReserve`
   (Task 3) e `AudienceTest` (Task 2).
2. **Stream que sobra aberto:** sessão encerrada ou pessoa tirada da família continua recebendo
   avisos, ou o fim do stream vira 401 ou exceção no log. Testes `removedMemberStreamClosesAtOnce`,
   `signedOutStreamClosesOnHeartbeat`, `closedStreamEndsCleanly` e `signedOutStreamEndsCleanly`
   (Task 3).
3. **Módulo desligado:** com o Planejamento desligado, nenhum aviso de meta, orçamento, regra ou
   reserva sai. Teste `planningOffSendsNoPlanningChanges` (Task 3).
4. **Limites e queda:** o 6º stream da mesma pessoa recebe 429, e o app volta a buscar quando a aba
   volta, sem tentar abrir em laço. Testes `sixthStreamIsRateLimited` (Task 3) e
   `fallsBackWhenClosed`, `retriesAtMostOncePerMinute` (Task 4).
5. **Som ou celebração no lugar errado:** som numa celebração vinda de aviso ao vivo ou da fila
   offline, som desligado tocando, animação com movimento reduzido. Testes
   `noSoundForLiveOnlyCelebration`, `noSoundWhenOff` (Task 8), `ignoresQueuedActions` (Task 7),
   `stillIconWithReducedMotion` (Task 6) e o e2e `reducedMotionDoesNotAnimate` (Task 9).

---

## Mapa de arquivos

**API** (`api/src/main/java/br/com/liberdadefamilia/`):

| Pacote | Novo | Muda |
|---|---|---|
| `live` | `package-info` | — |
| `live/internal` | `LiveController`, `LiveStreams`, `InMemoryLiveStreams`, `LiveStream`, `LiveChange`, `Audience`, `LiveRelay`, `LiveHeartbeat`, `LiveProperties` | — |
| `identity` | `SessionLiveness` | — |
| `identity/internal` | `JdbcSessionLiveness` | `SecurityConfig` (dispatch `ASYNC`) |
| `workspace` | `MemberProfileChanged` | `Memberships` (`activeAmong`) |
| `workspace/internal` | — | `JdbcMemberships` (ou a implementação de `Memberships`), `MemberService`, `WorkspaceGuardInterceptor` |
| `ledger` | `AccountVisibility`, `AccountOwnerChanged`, `CategoryChanged`, `TagChanged` | — |
| `ledger/internal` | `JdbcAccountVisibility` | `AccountService`, `CategoryService`, `TagService` |
| `planning` | `BudgetChanged`, `GoalChanged` | — |
| `planning/internal` | — | `BudgetService`, `GoalService`, `GoalAchievementListener` |

**Config:** `api/src/main/resources/application.properties` (as quatro `liberdade.live.*`).

**Testes da API** (`api/src/test/java/br/com/liberdadefamilia/`): `support/Eventually`, `support/SseStreams`,
`ledger/` (`LedgerEventsIT` estendido, `AccountVisibilityIT`), `identity/SessionLivenessIT`,
`workspace/MembershipsActiveAmongIT`, `planning/PlanningEventsIT`, `live/` (`AudienceTest`,
`InMemoryLiveStreamsTest`, `LiveRelayIT`, `LiveStreamIT`), `RoleMatrixIT`, `ApplicationModulesTest`
e `OpenApiDriftIT`.

**Web** (`web/src/`):

| Pasta | Novo | Muda |
|---|---|---|
| `shared/live` | `useLiveSync.ts`, `LiveSync.tsx`, `invalidation.ts`, `localChanges.ts`, `index.ts` e os testes | — |
| `shared/motion` | `useReducedMotion.ts`, `Celebration.tsx`, `index.ts` e os testes | — |
| `shared/sound` | `soundPreference.ts`, `playAchievement.ts`, `synth.ts`, `index.ts` e os testes | — |
| `shared/events` | — | `localActions.ts` (`lastLocalActionAt`) |
| `shared/ui` | — | `Sheet.tsx` (entrada animada) |
| `shared/api` | — | `mutations.ts`, `recurrences.ts`, `schema.ts` (gerado) |
| `shared/offline` | — | `outbox.ts` (`markLocalChange`) |
| `app` | — | `WorkspaceLayout.tsx` (`<LiveSync />`), `routes.tsx` (`/mais/ajustes`), `MorePage.tsx` |
| `features/settings` | `SettingsPage.tsx`, `messages.pt-BR.ts` e o teste | — |
| `features/transactions` | — | `TransactionRow.tsx`, `TransactionsPage.tsx` |
| `features/dashboard` | — | `DashboardPage.tsx` (`useReducedMotion`) |
| `features/planning` | `ReserveCelebrationWatcher.tsx`, `reserveCelebration.ts` e os testes | `GoalCelebration.tsx` (usa `Celebration`), `GoalCelebrationWatcher.tsx`, `GoalsPage.tsx`, `register.ts`, `messages.pt-BR.ts` |
| `shared/modules` | — | `slots.ts` (`planning-reserve-celebration`) |
| `mocks` | — | `handlers.ts` (stream com 204) |
| `index.css` | — | tokens, `arrive`, `sheet-in`, movimento reduzido |
| `eslint` | — | `boundaries.js` (`settings` no `CORE`) |
| `scripts` | — | `chunk-budget.mjs` e o teste (sintetizador fora do inicial) |

**E2E** (`web/e2e/`): `live-sync.spec.ts` (novo), `settings.spec.ts` (novo), `a11y.spec.ts` e
`support.ts`.

**Docs:** `docs/adr/0016-live-sync-sse-in-process.md`, `docs/architecture.md` e o README dos
planos.

**Em toda task de API:** rota nova ou mudada regenera, no mesmo commit:
- o OpenAPI: `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` em `api/`;
- o `schema.ts`: `corepack pnpm --dir web gen:api`;
- os handlers do MSW.

Nesse mesmo commit, o `RoleMatrixIT` ganha as linhas das rotas novas. Eventos são records públicos
no pacote raiz do módulo, só com ids.

## Tasks

### Task 1: Eventos novos, visibilidade de conta e sessão viva

**Files:**
- Create: `ledger/AccountVisibility.java`, `ledger/AccountOwnerChanged.java`,
  `ledger/CategoryChanged.java`, `ledger/TagChanged.java`,
  `ledger/internal/JdbcAccountVisibility.java`, `planning/BudgetChanged.java`,
  `planning/GoalChanged.java`, `workspace/MemberProfileChanged.java`,
  `identity/SessionLiveness.java`, `identity/internal/JdbcSessionLiveness.java`.
- Modify: `ledger/internal/AccountService.java` (`create` :41, `setOwner` :86),
  `ledger/internal/CategoryService.java`, `ledger/internal/TagService.java` (`rename` :82),
  `planning/internal/BudgetService.java` (`set` :69, `copyPrevious` :109, `delete`),
  `planning/internal/GoalService.java` (`create` :100, `update` :142, `contribute` :193),
  `planning/internal/GoalAchievementListener.java`, `workspace/Memberships.java` e a implementação,
  `workspace/internal/MemberService.java` (`update` :68).
- Test: `ledger/LedgerEventsIT.java`, `ledger/AccountVisibilityIT.java`,
  `planning/PlanningEventsIT.java`, `identity/SessionLivenessIT.java`,
  `workspace/MembershipsActiveAmongIT.java`.

**Interfaces:**
- Consumes: `AccountRepository.scopeOf(ws, membershipId, role)`, `ApplicationEventPublisher`, a
  tabela `spring_session`, `CsrfSession.sessionId()`.
- Produces (as Tasks 2 e 3 dependem destes nomes):
  - `AccountOwnerChanged(UUID workspaceId, UUID accountId, @Nullable UUID formerOwnerMembershipId, @Nullable UUID ownerMembershipId)`;
  - `CategoryChanged(UUID workspaceId, UUID categoryId)`, `TagChanged(UUID workspaceId, UUID tagId)`;
  - `BudgetChanged(UUID workspaceId, YearMonth month)`, `GoalChanged(UUID workspaceId, UUID goalId)`;
  - `MemberProfileChanged(UUID workspaceId, UUID membershipId)`;
  - `AccountVisibility.canSee(UUID workspaceId, UUID membershipId, Role role, UUID accountId, @Nullable UUID toAccountId): boolean`;
  - `SessionLiveness.aliveAmong(Collection<String> sessionIds): Set<String>`;
  - `Memberships.activeAmong(Collection<UUID> membershipIds): Set<UUID>`.

**Como fica:**
- Cada evento sai só quando a escrita mudou algo (um `PATCH` sem mudança, ou um `copyPrevious` com
  `copied 0`, não publica).
- `GoalAchievementListener` publica `GoalChanged` só quando o `UPDATE ... SET achieved_at` mudou uma
  linha. Ganha o Javadoc: o ouvinte é idempotente porque o `UPDATE` só grava onde `achieved_at IS NULL`,
  e a conferência na leitura cobre um ouvinte que falhou.
- `canSee` com papel diferente de `YOUTH` devolve `true` sem consultar. Com `YOUTH`, vê se a conta ou
  a conta de destino está no escopo.
- `aliveAmong` com coleção vazia devolve vazio sem consultar.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `LedgerEventsIT.createAccountPublishesAccountChanged` | `POST` de conta → um `AccountChanged` com o id novo |
| `LedgerEventsIT.setOwnerPublishesFormerAndNewOwner` | conta do Jovem passa para o Adulto → `AccountOwnerChanged(ws, conta, jovem, adulto)` |
| `LedgerEventsIT.categoryLifecyclePublishes` | criar, editar, arquivar e desarquivar → 4 `CategoryChanged` |
| `LedgerEventsIT.tagRenamePublishes` | renomear tag → `TagChanged` |
| `PlanningEventsIT.budgetSetAndCopyPublish` | `PUT` de limite → `BudgetChanged(ws, 2026-11)`; `copy-previous` com 0 copiados → nenhum |
| `PlanningEventsIT.goalWritesPublish` | criar, editar e aportar → 3 `GoalChanged` |
| `PlanningEventsIT.achievementPublishesOnce` | aporte que atinge a meta → um `GoalChanged` do ouvinte; um segundo aporte → nenhum a mais do ouvinte |
| `MembersIT` ou `MemberProfileEventIT.nicknameChangePublishes` | trocar apelido → `MemberProfileChanged`; `PATCH` igual → nenhum |
| `AccountVisibilityIT.adultSeesEverything` | `ADULT` e conta de outra pessoa → `true`, sem consulta |
| `AccountVisibilityIT.youthSeesOwnAndShared` | `YOUTH`: conta própria → `true`; conta só do Adulto → `false` |
| `AccountVisibilityIT.youthSeesTransferIntoOwn` | conta do Adulto como origem e conta do Jovem como destino → `true` |
| `SessionLivenessIT.aliveAndExpired` | uma sessão aberta e uma com `expiry_time` no passado → só a primeira |
| `SessionLivenessIT.deletedSessionIsNotAlive` | sessão apagada → vazio |
| `MembershipsActiveAmongIT.removedIsNotActive` | duas participações, uma removida → só a ativa |

Os testes de evento usam `@RecordApplicationEvents` (ou o padrão que o `LedgerEventsIT` já usa).

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*LedgerEventsIT' --tests '*PlanningEventsIT' --tests '*AccountVisibilityIT' --tests '*SessionLivenessIT' --tests '*MembershipsActiveAmongIT'`
Expected: FAIL (tipos que não existem).

- [ ] **Step 3: Implementar.** Siga D2, D3 e D4. Nenhuma rota muda, então o contrato fica igual.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*ApplicationModulesTest'`.
  Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(ledger): publish the change events live sync needs"
```

---

### Task 2: Registro dos streams, audiência e o relay depois do commit

**Files:**
- Create: `live/package-info.java`, `live/internal/LiveStreams.java`,
  `live/internal/InMemoryLiveStreams.java`, `live/internal/LiveStream.java`,
  `live/internal/LiveChange.java`, `live/internal/Audience.java`, `live/internal/LiveRelay.java`,
  `live/internal/LiveProperties.java`.
- Modify: `application.properties`.
- Test: `live/AudienceTest.java`, `live/InMemoryLiveStreamsTest.java`, `live/LiveRelayIT.java`,
  `support/Eventually.java`, `ApplicationModulesTest`.

**Interfaces:**
- Consumes: os eventos e `AccountVisibility` (Task 1), `Modules.isEnabled(ws, ModuleKey.PLANNING)`,
  `Role`.
- Produces (a Task 3 usa):
  - a interface `LiveStreams` de D5;
  - `LiveStream(UUID streamId, UUID workspaceId, UUID membershipId, UUID userId, Role role, String sessionId, SseEmitter emitter)`;
  - `LiveChange(String entity, @Nullable UUID id)` com `String toJson()`
    (`{"entity":"transaction","id":"..."}`; sem `id` → `{"entity":"budget"}`);
  - `Audience.includes(LiveStream stream, AccountVisibility visibility): boolean`;
  - `LiveProperties(int maxPerPerson, int maxStreams, Duration heartbeat, Duration streamTimeout)`;
  - `support/Eventually.within(Duration timeout, ThrowingRunnable assertion)`: repete a asserção
    até passar ou estourar o prazo (o laço com `System.nanoTime()` do `MembersIT:538`), e relança
    o último erro.

**Como fica:**
- O `LiveRelay` tem um método `@TransactionalEventListener(phase = AFTER_COMMIT)` por evento (D3).
  Não abre transação nova: só lê `Modules` e entrega. Falha é pega e logada só com
  `failure.getClass().getName()`.
- `publish` filtra os streams da família por `Audience.includes` na thread do executor e manda
  `SseEmitter.event().name("changed").data(change.toJson(), MediaType.APPLICATION_JSON)`.
- `open` recusa (devolve vazio) quando a pessoa já tem `maxPerPerson` streams ou o processo tem
  `maxStreams`. A contagem e a inserção ficam atômicas (`compute` no mapa por família e um contador
  global).
- O emitter registra `onCompletion`, `onTimeout` e `onError` para sair do registro.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `AudienceTest.everyoneIncludesYouth` | `Everyone` e stream `YOUTH` → `true` |
| `AudienceTest.noYouthExcludesOnlyYouth` | `NoYouth`: `VIEWER` → `true`; `YOUTH` → `false` |
| `AudienceTest.accountViewersAsksVisibilityOnlyForYouth` | `ADULT` → `true` sem chamar a visibilidade; `YOUTH` → o que `canSee` disser |
| `AudienceTest.extraMembershipIsIncluded` | `YOUTH` fora do escopo, mas em `extraMemberships` → `true` |
| `InMemoryLiveStreamsTest.sixthForSamePersonRefused` | 5 `open` da mesma pessoa → presentes; o 6º → vazio |
| `InMemoryLiveStreamsTest.processLimitRefused` | `maxStreams = 2` nas propriedades do teste, 3 pessoas → o 3º vazio |
| `InMemoryLiveStreamsTest.closedStreamFreesTheSlot` | 5 abertos, um completa → o 6º `open` passa |
| `InMemoryLiveStreamsTest.failedSendRemovesStream` | emitter que lança `IOException` no `send` → sai de `all()` |
| `InMemoryLiveStreamsTest.otherFamilyNeverReceives` | `publish` na família A → o emitter da família B não recebe nada |
| `InMemoryLiveStreamsTest.stopCompletesEveryEmitter` | `stop()` do ciclo de vida → todos completados e `all()` vazio |
| `LiveRelayIT.transactionBecomesChange` | lançamento gravado → `publish(ws, LiveChange("transaction", id), AccountViewers(conta, null, {}))` (com um `LiveStreams` espião) |
| `LiveRelayIT.rolledBackWriteSendsNothing` | escrita que falha e volta → nenhum `publish` |
| `LiveRelayIT.planningOffSkipsPlanningEvents` | módulo desligado, `GoalChanged` → nenhum `publish` |
| `LiveRelayIT.ownerChangeIncludesFormerOwner` | `AccountOwnerChanged` → `extraMemberships` com o antigo dono |
| `ApplicationModulesTest` | passa com `live` e suas dependências |

Os testes de unidade usam um `SseEmitter` subclasse de teste que grava o que recebe e pode lançar
`IOException` sob comando.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*AudienceTest' --tests '*InMemoryLiveStreamsTest' --tests '*LiveRelayIT'`
Expected: FAIL (tipos que não existem).

- [ ] **Step 3: Implementar.** Siga D1, D3 e D5.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*ApplicationModulesTest'`.
  Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(live): add the in-memory stream registry and the after-commit relay"
```

---

### Task 3: A rota SSE, a batida, os limites e o fim limpo

**Files:**
- Create: `live/internal/LiveController.java`, `live/internal/LiveHeartbeat.java`.
- Modify: `identity/internal/SecurityConfig.java:86`,
  `workspace/internal/WorkspaceGuardInterceptor.java`, `api/openapi.json`,
  `web/src/shared/api/schema.ts`, `web/src/mocks/handlers.ts`.
- Test: `live/LiveStreamIT.java`, `support/SseStreams.java`, `RoleMatrixIT`, `OpenApiDriftIT`.

**Interfaces:**
- Consumes: `LiveStreams`, `LiveStream`, `LiveProperties` (Task 2); `SessionLiveness`,
  `Memberships.activeAmong` (Task 1); `WorkspaceRequests` (membro e papel do pedido);
  `RateLimitedException`.
- Produces:
  - `GET /v1/workspaces/{ws}/events` (`text/event-stream`), que a Task 4 abre;
  - `LiveHeartbeat.beat()`;
  - `support/SseStreams.open(MockMvc mvc, UUID ws, CsrfSession session): MvcResult` e
    `SseStreams.changes(MvcResult result): List<String>` (os `data` dos eventos `changed` já
    escritos na resposta).

**Como fica:**
- O controller pega o membro e o papel de `WorkspaceRequests`, confere o limite, cria o emitter com
  o tempo de D6, manda `retry: 5000` e o comentário de abertura e registra o stream.
- `beat()` lê `all()`, chama `aliveAmong` e `activeAmong` uma vez cada, fecha quem caiu e manda `:`
  aos outros.
- O relay da Task 2 ganha os ouvintes de fechamento: `MemberRemoved` e `MemberRoleChanged` →
  `closeMember`; `WorkspaceDeletionRequested` → `closeWorkspace`. Eles também publicam o aviso
  `members` aos que ficam.
- Nos testes, o stream abre com `MockMvc.perform(...)` (o `MockMvcTester.exchange()` espera o fim do
  async), com o cookie da `CsrfSession`. O `SseEmitter` escreve na `MockHttpServletResponse`, então
  `SseStreams.changes` lê o que já chegou sem fechar o stream.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `LiveStreamIT.opensWithRetryAndComment` | `asyncStarted`, `Content-Type: text/event-stream`, conteúdo começa com `retry: 5000` |
| `LiveStreamIT.adultOnlyAccountNeverReachesYouth` | streams do Adulto e do Jovem; lançamento numa conta só do Adulto → em 5 s o Adulto tem `{"entity":"transaction","id":"<id>"}`; o Jovem não tem nenhum `changed` |
| `LiveStreamIT.sharedAccountReachesBoth` | lançamento numa conta que o Jovem vê → os dois recebem |
| `LiveStreamIT.formerOwnerIsTold` | conta do Jovem passa para o Adulto → o Jovem recebe `{"entity":"account",...}` |
| `LiveStreamIT.youthNeverGetsBudgetRuleOrReserve` | limite, regra e meta de reserva mudam → o Adulto recebe `budget`, `rule` e `reserve`; o Jovem nenhum |
| `LiveStreamIT.planningOffSendsNoPlanningChanges` | módulo desligado, aporte numa meta já existente pelo banco e regra criada pelo banco → nenhum `goal` nem `rule` no stream do Adulto |
| `LiveStreamIT.noValuesInChanges` | depois de um lançamento de R$ 123,45 com descrição "Mercado" → o conteúdo do stream não tem `123` nem `Mercado` |
| `LiveStreamIT.otherFamilyIsNotFound` | stream da família B com sessão da família A → 404 |
| `LiveStreamIT.sixthStreamIsRateLimited` | 5 streams da mesma pessoa abertos → o 6º responde 429 `rate.limited` |
| `LiveStreamIT.removedMemberStreamClosesAtOnce` | Dono remove o Adulto → o stream do Adulto completa sem esperar a batida |
| `LiveStreamIT.roleChangeClosesStream` | Adulto vira Visualizador → o stream completa |
| `LiveStreamIT.signedOutStreamClosesOnHeartbeat` | apagar a linha da sessão em `spring_session` e chamar `beat()` → o stream completa; um stream de outra sessão recebe `:` |
| `LiveStreamIT.closedStreamEndsCleanly` | stream que completa → `asyncDispatch` responde 200, sem exceção |
| `LiveStreamIT.signedOutStreamEndsCleanly` | sessão apagada, `beat()`, `asyncDispatch` → nem 401 nem 404, sem exceção |
| `LiveStreamIT.deletionRequestClosesFamilyStreams` | pedido de exclusão da família → todos os streams dela completam |
| `RoleMatrixIT` (linha nova) | os quatro papéis abrem o stream (status do início async, não 403) |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*LiveStreamIT'`
Expected: FAIL (rota 404).

- [ ] **Step 3: Implementar.** Siga D6, D7 e D8. Regenere o contrato. No MSW, um `http.get` em
  `/v1/workspaces/:ws/events` responde `new HttpResponse(null, { status: 204 })`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*RoleMatrixIT' --tests '*OpenApiDriftIT' --tests '*ApplicationModulesTest'`.
  Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL e `corepack pnpm --dir web check`
  limpo.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts web/src/mocks
git commit -m "feat(live): add the family event stream with heartbeat and limits"
```

---

### Task 4: O app abre o stream e busca de novo

**Files:**
- Create: `web/src/shared/live/useLiveSync.ts`, `LiveSync.tsx`, `invalidation.ts`, `index.ts`,
  `useLiveSync.test.tsx`, `invalidation.test.ts`.
- Modify: `web/src/app/WorkspaceLayout.tsx`.
- Test: os dois acima e `web/src/app/WorkspaceLayout.test.tsx` (se existir; senão, um caso em
  `SessionLayout.test.tsx`).

**Interfaces:**
- Consumes: a rota da Task 3; `API_URL` de `shared/api/config.ts`; `useOnline()`; `queryKeys`,
  `planningKeys`.
- Produces:
  - `useLiveSync(workspaceId: string, options?: { createSource?: (url: string) => EventSource; now?: () => number }): void`;
  - `LiveSync({ workspaceId }: { workspaceId: string })` (rende `null`);
  - `invalidationPrefixes(entity: string): (readonly string[])[]` (os prefixos depois de
    `['ws', ws]`, a tabela de D9);
  - `onLiveChange(listener: (change: { workspaceId: string; entity: string; id?: string }) => void): () => void`,
    que a Task 5 usa.

**Como fica:**
- A URL é `${API_URL}/v1/workspaces/${ws}/events`, aberta com `{ withCredentials: true }`.
- `changed` faz `JSON.parse` defensivo (aviso mal formado é ignorado), emite `onLiveChange` e
  acumula a entidade; depois de 250 ms sem aviso novo, invalida os prefixos acumulados.
- A aba escondida liga um relógio de 5 min; voltar antes disso cancela o relógio; passar disso
  fecha o stream. Voltar abre de novo.

- [ ] **Step 1: Escrever os testes (falhando)**

Uma `FakeEventSource` no próprio teste (como a `FakeBroadcastChannel` do `SessionLayout.test.tsx`),
com `emitOpen()`, `emitChanged(data)`, `emitError(readyState)` e `close()`, passada por
`createSource`. Relógios falsos do Vitest para os 250 ms, 5 min e 60 s.

| Teste | Esperado |
|---|---|
| `invalidation.test.ts › mapsEveryEntity` | cada linha da tabela de D9; `unknown` → `[[]]` (a família inteira) |
| `useLiveSync › doesNothingWithoutEventSource` | sem `EventSource` global e sem `createSource` → nenhum erro, nenhum pedido |
| `useLiveSync › opensWithCredentials` | a fábrica recebe a URL da família; a `EventSource` real seria criada com `withCredentials` (conferido com `vi.stubGlobal('EventSource', ...)`) |
| `useLiveSync › debouncesAndInvalidatesByEntity` | dois `transaction` e um `tag` em 100 ms → depois de 250 ms, uma rodada de invalidação com os prefixos unidos; `members` não é tocado |
| `useLiveSync › firstOpenDoesNotRefetch` | o primeiro `open` → nenhuma invalidação |
| `useLiveSync › reopenRefetchesFamily` | segundo `open` → invalida `['ws', ws]` |
| `useLiveSync › closesAfterFiveHiddenMinutes` | aba escondida por 5 min → `close()`; visível de novo → nova fonte e invalidação de `['ws', ws]` |
| `useLiveSync › shortHideKeepsStream` | escondida por 4 min e volta → a mesma fonte |
| `useLiveSync › closesOffline` | evento `offline` → `close()`; `online` → nova fonte |
| `useLiveSync › fallsBackWhenClosed` | erro com `readyState` 2 → modo sem stream; aba volta → invalida `['ws', ws]` |
| `useLiveSync › retriesAtMostOncePerMinute` | no modo sem stream, três voltas da aba em 30 s → uma fonte nova só depois de 60 s |
| `useLiveSync › ignoresMalformedData` | `data: "x{"` → nenhuma exceção, nenhuma invalidação |
| `useLiveSync › closesOnUnmount` | desmontar → `close()` |
| `WorkspaceLayout › mountsLiveSyncOnlyWhenReady` | família com exclusão agendada → a fábrica não é chamada |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/shared/live`
Expected: FAIL (módulo não existe).

- [ ] **Step 3: Implementar.** Siga D9.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `src/app`. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` limpo.

- [ ] **Step 6: Commit**

```bash
git add web/src/shared/live web/src/app
git commit -m "feat(web): refetch family data when the live stream says something changed"
```

---

### Task 5: Destaque do lançamento que chegou e aviso para leitor de tela

**Files:**
- Create: `web/src/shared/live/localChanges.ts`, `localChanges.test.ts`.
- Modify: `web/src/shared/live/useLiveSync.ts` (chama `markRemoteArrival`),
  `web/src/shared/offline/outbox.ts` (`enqueueTransaction` :26),
  `web/src/shared/api/mutations.ts` (`useUpdateTransaction` :41, `useRestoreTransaction` :50),
  `web/src/shared/api/recurrences.ts` (`useConfirmPlanned` :121),
  `web/src/features/transactions/TransactionRow.tsx`,
  `web/src/features/transactions/TransactionsPage.tsx`, as mensagens de `transactions`,
  `web/src/index.css` (`--animate-arrive` e `@keyframes arrive`, com 200 ms; a Task 6 troca a
  duração pelo token `--motion-base`).
- Test: `localChanges.test.ts`, `TransactionRow.test.tsx`, `TransactionsPage.test.tsx`.

**Interfaces:**
- Consumes: `onLiveChange` (Task 4).
- Produces:
  - `markLocalChange(id: string, now?: number): void`, `isLocalChange(id: string, now?: number): boolean` (5 min);
  - `markRemoteArrival(id: string, now?: number): void`, `isRecentArrival(id: string, now?: number): boolean` (60 s);
  - `onArrival(listener: (id: string) => void): () => void`.

**Como fica:**
- O `useLiveSync` chama `markRemoteArrival(id)` para `transaction` com `id` que não é local.
- `TransactionRow` adiciona `motion-safe:animate-arrive` quando `isRecentArrival(row.id)`.
- `TransactionsPage` guarda os ids já anunciados e o instante do último anúncio. Quando a lista
  renderizada traz uma chegada não anunciada e já passaram 10 s, põe "Lançamento novo na lista." na
  região `aria-live="polite"` (`sr-only`).
- `@keyframes arrive`: de `opacity: 0.4; transform: translateY(-4px)` para `opacity: 1; transform: none`,
  em 200 ms.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `localChanges › localExpiresAfterFiveMinutes` | marcado em t → `true` em t+4min59s; `false` em t+5min |
| `localChanges › arrivalExpiresAfterSixtySeconds` | marcado em t → `true` em t+59s; `false` em t+60s |
| `useLiveSync › localIdIsNotAnArrival` | `markLocalChange(id)` e depois aviso `transaction` com o mesmo id → `isRecentArrival(id)` falso |
| `useLiveSync › remoteIdIsAnArrival` | aviso com id desconhecido → `isRecentArrival(id)` verdadeiro |
| `outbox › enqueueMarksLocalChange` | `enqueueTransaction` → `isLocalChange(id)` |
| `mutations › updateRestoreAndConfirmMarkLocalChange` | os três salvamentos → `isLocalChange(id)` |
| `TransactionRow › arrivalGetsHighlight` | id em chegada → classe `motion-safe:animate-arrive`; id comum → sem a classe |
| `TransactionsPage › announcesArrivalOnce` | lista com uma chegada → região polite com "Lançamento novo na lista." |
| `TransactionsPage › announcesAtMostEveryTenSeconds` | segunda chegada 3 s depois → o texto não muda; 11 s depois → anuncia de novo |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/shared/live src/shared/offline src/shared/api src/features/transactions`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D10.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` limpo.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): highlight entries that arrive from another device"
```

---

### Task 6: Tokens de movimento, `useReducedMotion` e o `Celebration`

**Files:**
- Create: `web/src/shared/motion/useReducedMotion.ts`, `Celebration.tsx`, `index.ts`,
  `useReducedMotion.test.tsx`, `Celebration.test.tsx`.
- Modify: `web/src/index.css`, `web/src/shared/ui/Sheet.tsx`,
  `web/src/features/dashboard/DashboardPage.tsx` (:51 e :111),
  `web/src/features/planning/GoalCelebration.tsx`.
- Test: os novos, `Sheet.test.tsx`, `GoalCelebration` (os testes de hoje continuam passando),
  `DashboardPage` (os de hoje).

**Interfaces:**
- Produces:
  - `useReducedMotion(): boolean`;
  - `Celebration({ icon, message, onDone, shownMs = 6000 }: { icon: ReactNode; message: string; onDone: () => void; shownMs?: number })`;
  - `CelebrationStack({ children }: { children: ReactNode })` (o contêiner fixo de hoje,
    `pointer-events-none fixed inset-x-0 bottom-24 z-50 ...`);
  - as classes `animate-arrive`, `animate-goal-pop` e `animate-sheet-in`, todas com duração de
    token.

**Como fica:**
- `useReducedMotion` usa `useSyncExternalStore` com `matchMedia('(prefers-reduced-motion: reduce)')`,
  `false` no servidor e sem `matchMedia`.
- `Celebration` é `role="status"`; com movimento reduzido, nenhuma classe de animação; o tempo fica
  num `useEffect` com `[shownMs]` e lê `onDone` de um ref.
- `GoalCelebration` passa a ser `Celebration` com `goalEmoji(icon)` e a mensagem
  `planning.goals.celebration`. O texto e o comportamento visível não mudam.
- `DashboardPage` usa `!useReducedMotion()` no lugar de `readAnimate()`.
- `@keyframes sheet-in`: de `transform: translateY(16px); opacity: 0` para `none; 1`.
- Busca por durações soltas: `grep -rnE "[0-9]+ms" web/src --include=*.css --include=*.tsx` só pode
  achar os tokens no `@theme` e os testes.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `useReducedMotion › followsTheMediaQuery` | `matchMedia` falso → `false`; disparar `change` com `matches: true` → `true` |
| `useReducedMotion › falseWithoutMatchMedia` | sem `matchMedia` → `false` |
| `Celebration › animatesWithMotion` | classe `motion-safe:animate-goal-pop` |
| `Celebration › stillIconWithReducedMotion` | movimento reduzido → sem classe de animação, ícone e texto visíveis |
| `Celebration › parentRerenderDoesNotRestartTimer` | renderizar de novo com um `onDone` novo aos 5 s → `onDone` chamado aos 6 s, uma vez |
| `Celebration › doesNotBlockTheScreen` | `CelebrationStack` com `pointer-events-none` |
| `Sheet › entersWithSheetAnimation` | o painel tem `motion-safe:animate-sheet-in` |
| `GoalCelebration` (testes de hoje) | passam sem mudança de texto |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/shared/motion src/shared/ui src/features/planning src/features/dashboard`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D11.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` limpo e a busca de durações soltas sem
  achados fora do `@theme`.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(web): add motion tokens, reduced-motion hook and a shared celebration"
```

---

### Task 7: Celebração da reserva

**Files:**
- Create: `web/src/features/planning/ReserveCelebrationWatcher.tsx`, `reserveCelebration.ts` e os
  testes.
- Modify: `web/src/shared/modules/slots.ts`, `web/src/features/planning/register.ts`,
  `web/src/features/planning/messages.pt-BR.ts`, `web/src/app/WorkspaceLayout.tsx` (o
  `ModuleSlot` do slot novo, ao lado do de hoje).
- Test: `ReserveCelebrationWatcher.test.tsx`, `reserveCelebration.test.ts`.

**Interfaces:**
- Consumes: `onLocalAction` (`shared/events`), `getReserve`, `planningKeys.reserve`,
  `Celebration`/`CelebrationStack` (Task 6).
- Produces:
  - `reserveReached(view: ReserveView): boolean`;
  - `reserveStorageKey(ws: string) = 'liberdade.reserve-celebrated:' + ws`;
  - `shouldCelebrateReserve(ws: string, view: ReserveView): boolean` (marca quando celebra, apaga a
    marca quando a cobertura caiu);
  - o slot `planning-reserve-celebration`, que a Task 8 liga ao som.

**Como fica:**
- O observador assina `onLocalAction`, ignora `fromQueue` e tipos que não são `transaction-saved`,
  busca a reserva com `staleTime: 0` e celebra uma vez.
- Texto: `planning.reserve.celebration` = "Reserva completa: {months} meses guardados!" (com
  `months` arredondado para baixo como na tela da Reserva). Ícone 🛟.
- O Jovem e o Visualizador não montam o slot (Dono e Adulto, como a tela da Reserva).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `reserveCelebration › reachedOnlyWithReserveAccounts` | `hasReserveAccounts:false` → `false`; `months 6`, alvo 6 → `true`; `months null` → `false` |
| `reserveCelebration › celebratesOnceUntilItDrops` | chega ao alvo → `true`; de novo → `false`; cai abaixo → `false` e apaga a marca; chega de novo → `true` |
| `ReserveCelebrationWatcher › celebratesAfterLocalSave` | MSW com a reserva no alvo; `emitLocalAction({ type: 'transaction-saved', fromQueue: false, ... })` → "Reserva completa: 6 meses guardados!" |
| `ReserveCelebrationWatcher › ignoresQueuedActions` | o mesmo com `fromQueue: true` → nada |
| `ReserveCelebrationWatcher › notForYouthOrViewer` | papel `YOUTH` ou `VIEWER` → o slot não monta |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/features/planning`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D12.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` limpo.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat(planning): celebrate when the emergency reserve reaches its target"
```

---

### Task 8: Ajustes, a preferência de som e o sintetizador sob demanda

**Files:**
- Create: `web/src/shared/sound/soundPreference.ts`, `playAchievement.ts`, `synth.ts`, `index.ts`
  e os testes; `web/src/features/settings/SettingsPage.tsx`, `messages.pt-BR.ts`,
  `SettingsPage.test.tsx`.
- Modify: `web/src/shared/events/localActions.ts` (`lastLocalActionAt`),
  `web/src/app/routes.tsx`, `web/src/app/MorePage.tsx`, `web/eslint/boundaries.js` (`settings` no
  `CORE`), `web/src/features/planning/GoalCelebrationWatcher.tsx`, `GoalsPage.tsx`,
  `ReserveCelebrationWatcher.tsx`, as mensagens do app (título e link).
- Test: os novos, `localActions.test.ts`, `GoalCelebrationWatcher.test.tsx`,
  `ReserveCelebrationWatcher.test.tsx`, `MorePage.test.tsx`.

**Interfaces:**
- Consumes: `onLocalAction`/`emitLocalAction`, os observadores das Tasks 6 e 7.
- Produces:
  - `useSoundEnabled(): [boolean, (on: boolean) => void]`, `isSoundEnabled(): boolean`;
  - `lastLocalActionAt(workspaceId: string): number | null`;
  - `celebrationSound(workspaceId: string, now?: number): void`: toca só se `isSoundEnabled()` e
    `now - lastLocalActionAt(ws) <= 10_000`;
  - `playAchievement(): Promise<void>` (faz `import('./synth')`);
  - `synth.ts`: `export function play(context?: AudioContext): void`.

**Como fica:**
- Os dois observadores e a `GoalsPage` chamam `celebrationSound(ws)` quando acrescentam uma
  celebração. A `GoalsPage` pode celebrar depois de um refetch ao vivo; a regra dos 10 s deixa essa
  sem som.
- `SettingsPage`: título "Ajustes"; uma seção "Sons" com o interruptor "Sons de conquista"
  (`role="switch"`, `aria-checked`, desligado por padrão), a ajuda de D13 e o botão "Ouvir", que
  chama `playAchievement()` mesmo com o som desligado (é um gesto da pessoa).
- `routes.tsx`: `/mais/ajustes` com `lazy`, como as outras telas de Mais. `MorePage`: link "Ajustes"
  depois de "Privacidade".
- `synth.ts` não é importado de forma estática em lugar nenhum.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `soundPreference › offByDefault` | `localStorage` vazio → `false` |
| `soundPreference › persistsOn` | ligar → `localStorage['liberdade.sound'] === 'on'`; desligar → a chave sai |
| `soundPreference › offWhenStorageThrows` | `localStorage` que lança → `false`, sem exceção |
| `localActions › lastLocalActionSkipsQueue` | ação com `fromQueue: true` → `lastLocalActionAt` continua `null`; sem fila → o instante |
| `celebrationSound › noSoundWhenOff` | som desligado e ação local agora → `playAchievement` não é chamado |
| `celebrationSound › playsRightAfterLocalAction` | som ligado e ação local há 3 s → chamado uma vez |
| `celebrationSound › noSoundForLiveOnlyCelebration` | som ligado e nenhuma ação local (ou há 11 s) → não chamado |
| `GoalCelebrationWatcher › soundOnlyWhenEnabled` | meta atingida depois de um aporte local, som ligado → `playAchievement` chamado (com `vi.mock('@/shared/sound/playAchievement')`) |
| `SettingsPage › switchStartsOff` | `role="switch"` "Sons de conquista" com `aria-checked="false"` |
| `SettingsPage › toggleAndListen` | clicar → `aria-checked="true"` e a chave gravada; "Ouvir" → `playAchievement` chamado |
| `MorePage › linksToSettings` | link "Ajustes" para `/mais/ajustes` |
| `synth › playsTwoShortQuietNotes` | com um `AudioContext` falso: dois osciladores `sine`, fim de cada um até 0,3 s depois do início, ganho máximo ≤ 0,1 |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/shared/sound src/shared/events src/features/settings src/features/planning src/app`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D13.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` limpo (o lint aceita `settings` no
  núcleo).

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add settings with an opt-in achievement sound"
```

---

### Task 9: E2E, bundle, Caddy e docs

**Files:**
- Create: `web/e2e/live-sync.spec.ts`, `web/e2e/settings.spec.ts`,
  `docs/adr/0016-live-sync-sse-in-process.md`.
- Modify: `web/e2e/a11y.spec.ts` (Ajustes nos dois temas), `web/e2e/support.ts` (se precisar de
  um ajudante de duas sessões), `web/scripts/chunk-budget.mjs` e o teste,
  `docs/architecture.md`, `docs/superpowers/plans/README.md` (linha 7).

**Interfaces:**
- Consumes: tudo das Tasks 1 a 8; `withContext` (:230) e `recordExpense` do `e2e/support.ts`.
- Produces: `lazyOnly(manifest, sources: string[]): { ok: boolean; offenders: string[] }` em
  `chunk-budget.mjs`, chamado pelo `check-chunks.mjs` com `['src/shared/sound/synth.ts']`.

**Como fica:**
- `live-sync.spec.ts`: duas `BrowserContext` da mesma família (Dono e Adulto, com
  `withContext`), as duas na lista de lançamentos. O Dono lança um gasto; o Adulto vê a linha em até
  5 s sem recarregar. Se a massa do e2e permitir uma conta só do Adulto, um terceiro contexto do
  Jovem confere que a linha não aparece.
- `settings.spec.ts`: `/mais/ajustes`, o interruptor começa desligado, ligar sobrevive a um reload.
- Movimento reduzido: `page.emulateMedia({ reducedMotion: 'reduce' })`, abrir uma `Sheet` e
  conferir `getComputedStyle(painel).animationName === 'none'`.
- `lazyOnly` falha se algum arquivo do carregamento inicial (`initialFiles`) vier de uma das fontes.
- O ADR-0016 registra: SSE no processo, filtro por pessoa, refetch no cliente, limite de 1.000
  streams, a interface `LiveStreams` como ponto de troca para LISTEN/NOTIFY, e o que fica fora
  (WebSocket, push, payload com dados).
- `docs/architecture.md`: o módulo `live` no mapa, os eventos novos e a rota.
- README dos planos, linha 7: 7.3 concluída, a conferir no staging (item de Caddy na lista do PR).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `chunk-budget.test › lazyOnlyFlagsInitialSynth` | manifesto de exemplo com `synth` importado pela entrada → `ok: false` |
| `chunk-budget.test › lazyOnlyPassesDynamicSynth` | `synth` só como `dynamicImports` → `ok: true` |
| `live-sync.spec › entryAppearsInOtherBrowser` | a linha nova aparece no outro contexto em até 5 s |
| `live-sync.spec › youthDoesNotSeeAdultOnlyEntry` (se viável) | a linha não aparece no contexto do Jovem em 5 s |
| `settings.spec › soundOffByDefaultAndPersists` | `aria-checked="false"`; liga; reload → `true` |
| `settings.spec › reducedMotionDoesNotAnimate` | `animationName` `none` na `Sheet` |
| `a11y.spec › settings` | sem violações nos dois temas |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- scripts` e, com a stack de e2e no ar
(`docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait`),
`corepack pnpm --dir web e2e -- live-sync.spec.ts settings.spec.ts a11y.spec.ts`.
Expected: FAIL nos testes novos.

- [ ] **Step 3: Implementar.** Siga D14 e D15. Nada muda no `deploy/Caddyfile`.

- [ ] **Step 4: Rodar e ver passar.** Os mesmos comandos, mais
  `corepack pnpm --dir web build` e `corepack pnpm --dir web check:chunks` (inicial até 200 KB e
  `synth` fora dele). Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` limpo e `./gradlew build` → BUILD
  SUCCESSFUL em `api/`.

- [ ] **Step 6: Commit**

```bash
git add web docs
git commit -m "test(e2e): cover live sync, settings and reduced motion"
```
