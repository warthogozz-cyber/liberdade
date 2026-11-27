# Liberdade · Plano 11: ajuda no app — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- Toda tela do app explica a si mesma, uma vez, em texto curto e amigável para qualquer idade.
- A família acha a ajuda inteira num lugar só ("Como funciona") e pode rever as dicas.
- Papéis, fila sem internet, sincronização ao vivo, Feira, Projetos e o jargão financeiro deixam de
  ser lacunas.
- Os 37 textos confusos da auditoria são reescritos.

**Architecture:**
- `shared/tips` vira `shared/help`, com um catálogo único (`catalog.ts`): dicas de tela, dicas de
  evento, tours, seções, glossário e a ajuda de cada rota.
- Um store reativo (`helpStore.ts`, `useSyncExternalStore`) guarda o que cada pessoa já viu, com as
  mesmas chaves de hoje.
- A tela "Como funciona" (`features/help`, núcleo, sob demanda) monta as seções a partir do
  catálogo. Um só `EventTipHost` no `WorkspaceLayout` mostra as dicas de evento.
- Um teste de cobertura percorre as rotas e falha se uma tela ficar sem ajuda.

**Tech Stack:**
- Web: React 19, react-router, react-intl, TanStack Query, Vitest, Testing Library, Playwright e axe.
- Sem dependência nova. Sem mudança na API.

**Spec:** [`docs/superpowers/specs/2026-11-27-liberdade-help-and-tips-design.md`](../specs/2026-11-27-liberdade-help-and-tips-design.md),
aprovado pelo mantenedor em 2026-11-27. O spec é a autoridade; onde este plano desvia, o desvio
está marcado (D12).

## Global Constraints

- **Idiomas:** código, comentários, commits e nomes em inglês; textos da tela e docs em PT-BR.
- **Commits:** Conventional Commits em minúsculas (`feat(help): …`, `test(help): …`,
  `fix(copy): …`); todo commit termina com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  `git add` só com caminhos explícitos.
- **Dados pessoais:** nenhum nome real. Antes de cada commit, as duas buscas da
  `personal-data-patterns` (no caminho de `git rev-parse --git-common-dir`) saem com código 1.
- **Limites de texto (spec 5.10):**
  - dica de tela, dica de evento e passo de tour: até 140 caracteres e até 2 frases;
  - seção, introdução, legenda e verbete do glossário: até 280 caracteres;
  - cada tour tem de 1 a 4 passos.
- **Ética:** nenhum texto bate na regex de perda dos testes atuais:
  `/perd|perca|resta|expir|quebr|corra|última chance|não deixe|falta/i`. Sem pressão para gastar,
  sem urgência, sem culpa.
- **Investimentos (CVM):** texto educativo; o aviso "Conteúdo educativo. Não é recomendação de
  investimento." continua visível no simulador.
- **Rótulos do e2e que não mudam:** 'Chave de 44 números', 'Registrar nota', 'Digitar a chave',
  'Continuar', 'Gravar', 'Preços registrados. A nota não vira um gasto; para lançar, use Lançar.',
  'Ver o cartão do mercado', 'Voltar para a Feira', 'Onde procurar mercados?', 'Digitar um
  endereço', 'Endereço', 'Buscar', 'Adicionar item', 'Produto', 'Preço da unidade', 'Data da
  compra', 'O mercado', 'Os preços', 'Em qual mercado foi esta compra?', 'Esta nota é deste
  mercado', 'Novo: …', 'Item N', 'Marcar …', /^Unidade/, 'Entendi', 'Pular', 'Próximo',
  'Concluir'. Antes de mudar um texto, `grep -rn "<texto>" e2e/` em `web/`.
- **Fronteiras (`web/eslint/boundaries.js`):** `help` entra em `CORE`. `shared` não importa
  `features` nem `app`; o núcleo não importa módulos opcionais; os módulos opcionais importam só
  `shared` e a si mesmos. O catálogo guarda rotas e ids de mensagem como texto, nunca importa telas.
- **Desempenho:** pacote inicial até 200 KB (`npm run check:chunks`). "Como funciona" é `lazy`; o
  balão da dica e o tour continuam `lazy`.
- **Acessibilidade:** WCAG 2.2 AA; axe sem violações nos temas claro e escuro. Dica de tela é
  `role="note"`; dica de evento é `role="status"`; tudo funciona por teclado.
- **Movimento (spec 7):** nenhuma animação nova; o app segue "Reduzir movimento" do aparelho.
- **Comandos (em `web/`):** `npx vitest run <caminho>`, `npm run typecheck`, `npm run lint`,
  `npm run format:check`, `npm run check:chunks`, `npm run build`; e2e só tipa localmente
  (`npx tsc -p tsconfig.node.json --noEmit`) e roda no CI.

### Pré-requisitos

- `main` em `b54b7db` ou depois (10.3 entregue).
- O e2e roda só no CI. Docker não é necessário (nada de API).

## Entrega

- **PR 1 (docs):** o spec, este plano e a linha 11 no README dos planos.
- **PR 2 (11.1):** branch `feat/plan-11-1-help-catalog`. Tasks 1 a 4.
- **PR 3 (11.2):** branch `feat/plan-11-2-help-gaps`, a partir do `main` com a 11.1. Tasks 5 a 9.
- **PR 4 (11.3):** branch `feat/plan-11-3-copy-rewrites`, a partir do `main` com a 11.2. Tasks 10 e 11.
- Cada PR: uma task por commit (ou poucos commits por task), revisão por task, revisão final no
  modelo mais capaz e merge com os obrigatórios verdes.

## Interfaces

Produzidas pela Task 1 e usadas pelas seguintes. Todas em `web/src/shared/help/`.

```ts
// catalog.ts
export type TipId =
  | 'home' | 'launch' | 'dashboard' | 'transactions' | 'accounts' | 'calculator' | 'import'
  | 'portrait' | 'categories' | 'family' | 'modules' | 'budget' | 'goals' | 'recurrences'
  | 'receipt' | 'shopping-list' | 'products' | 'market' | 'compare' | 'venture-real'
export type EventTipId = 'offline' | 'live'
export type TourId = 'planning' | 'indicators' | 'achievements' | 'markets' | 'ventures'
export type HelpMessageId = keyof typeof helpMessages
export interface TourStep { anchor: string; messageId: HelpMessageId }
export type RouteHelp =
  | { tip: TipId }
  | { tour: TourId }
  | { inline: string }            // id de mensagem do bundle da tela
  | { inherits: string }          // caminho de outra rota
  | { exception: string }         // motivo, em inglês
export interface ModuleHelp {
  module: ModuleKey; tour: TourId; path: string; titleId: string; hiddenForYouth: boolean
}
export const screenTips: Record<TipId, HelpMessageId>
export const eventTips: Record<EventTipId, HelpMessageId>
export const tours: Record<TourId, TourStep[]>
export const moduleHelp: ModuleHelp[]
export const glossary: { termId: string; bodyId: string }[]
export const routeHelp: Record<string, RouteHelp> // chave = caminho completo, ex. '/mais/feira/lista'
export const seenId: { tip: (id: TipId) => string; tour: (id: TourId) => string; event: (id: EventTipId) => string }
// seenId.tip('home') === 'home'; seenId.tour('markets') === 'tour-markets'; seenId.event('live') === 'event-live'

// helpStore.ts
export function hasSeen(userId: string, id: string): boolean
export function markSeen(userId: string, id: string): void
export function clearSeen(userId: string, id: string): void
export function resetAllSeen(userId: string): void
export function useSeen(userId: string | undefined, id: string): boolean
export function isTipsEnabled(): boolean
export function setTipsEnabled(on: boolean): void
export function useTipsEnabled(): [boolean, (on: boolean) => void]
export function setHelpOpen(open: boolean): void   // um balão, tour ou dica de evento na tela
export function isHelpOpen(): boolean

// queuedSignal.ts (Task 5, em shared/offline/)
export function notifyQueued(): void
export function onQueued(listener: () => void): () => void
```

Chaves no `localStorage`, iguais às de hoje: `liberdade.tip:<userId>:<id>` = `'1'` e
`liberdade.tips` = `'off'`.

## Decisões deste plano

- **D1 · Diretório.** `git mv web/src/shared/tips web/src/shared/help`, sem reexportar o caminho
  antigo. Os 9 lugares que importam `@/shared/tips` passam a `@/shared/help`. O bundle vira
  `helpMessages` (os ids `tips.*` ficam). `formatjs.d.ts` troca as linhas 45 e 79. `seenTips.ts` e
  `tipsPreference.ts` somem para dentro de `helpStore.ts`; `tours.ts` some para dentro de
  `catalog.ts`.
- **D2 · Store reativo.** `useSyncExternalStore` com uma versão que sobe a cada escrita e no evento
  `storage` de outra aba. Se o `localStorage` lança, o store guarda em memória até a página fechar
  (hoje "visto" se perde ao trocar de tela). `resetAllSeen` apaga só as chaves com o prefixo
  `liberdade.tip:<userId>:`.
- **D3 · Dicas de tela.** `PageTip` lê o texto em `screenTips` e usa `useSeen` e `useTipsEnabled`.
  Os `TipId` sem uso (`planning`, `indicators`, `achievements`, que são tours) saem do tipo.
- **D4 · "Ver o tour".** O link navega para `moduleHelp.path` com `state: { forceTour: id }`.
  `PageTour` abre se `location.state?.forceTour === id`, mesmo com dicas desligadas ou tour visto. Ao
  fechar: `markSeen`, depois `navigate(pathname, { replace: true, state: null })`, para o voltar do
  navegador não reabrir.
- **D5 · Tour espera o diálogo.** `PageTour` só abre quando não há `[role="dialog"]` na página
  (observa o `body`). Depois de aberto, fica aberto. Na Feira, o seletor de local abre primeiro.
- **D6 · Um balão por vez.** `TipBubble`, `Tour` e `EventTip` chamam `setHelpOpen(true)` ao montar e
  `false` ao desmontar. O `EventTipHost` não mostra nada com `isHelpOpen()` ou com
  `[role="dialog"]` na página; o evento é ignorado (sem fila, spec 5.6) e a dica continua não vista.
- **D7 · Dicas de evento.** `EventTipHost` em `app/EventTipHost.tsx`, montado no `WorkspaceLayout`
  depois do `<Outlet />`. Offline: `onQueued` com o aparelho sem sinal (`navigator.onLine === false`).
  Ao vivo: `onArrival` de `@/shared/live`. Cada uma aparece uma vez por pessoa e aparelho
  (`seenId.event`).
- **D8 · Fila.** `shared/offline/queuedSignal.ts` com um `Set` de ouvintes. `outbox.ts` chama
  `notifyQueued()` logo depois do primeiro `db.outbox.add(item)` do `enqueue`; a nova tentativa
  (~linha 122) não chama.
- **D9 · Rever dicas.** Em Ajustes, abaixo do interruptor: botão "Rever dicas". Chama
  `resetAllSeen(userId)` e `setTipsEnabled(true)` e mostra "Pronto. As dicas vão aparecer de novo."
  num `role="status"`. O interruptor reflete a mudança sem recarregar (D2).
- **D10 · Módulo ligado.** Em `ModulesPage`, depois de ligar um módulo com tour: `role="status"` com
  "{módulo} ligado." e o link "Abrir {módulo}" para `moduleHelp.path` (sem o link para Projetos
  quando a pessoa é Jovem), e `clearSeen(userId, seenId.tour(id))`.
- **D11 · Descoberta das rotas.** `features/ventures/VenturesRoutes.tsx` e
  `features/markets/MarketsRoutes.tsx` exportam `venturesRoutePaths` e `marketsRoutePaths`
  (`readonly string[]`, os mesmos `path` dos arrays do `useRoutes`, que passam a usá-los). O teste de
  cobertura fica em `app/`, que pode importar tudo, percorre `routes` e junta os caminhos internos.
- **D12 · Desvios do spec (marcados).**
  - Passo 2 da sequência: "Uma semana de folga por mês não interrompe a sequência." (o texto do
    spec batia na regex de perda).
  - Lacuna 24 (passos do projeto): "Liste os próximos passos. Marque como Feito ao concluir."
  - Lacuna 19 (fixos e variáveis): a legenda fica no `SpendingSplitHomeCard`, que é onde a divisão
    aparece no Início.
- **D13 · Onde mora cada texto.** Dicas de tela, de evento e passos de tour: `helpMessages`
  (`shared/help/messages.pt-BR.ts`). Seções e glossário: `howItWorksMessages`
  (`features/help/messages.pt-BR.ts`, carregado com a tela). Textos dentro das telas: no bundle da
  própria tela.
- **D14 · Sem tela com dica e tour.** `RouteHelp` é uma união, então o catálogo não permite as duas.
  O teste de cobertura também lê o código das telas e falha se um arquivo renderiza `<PageTip` e
  `<PageTour`.

## Review Focus

1. **Armazenamento indisponível** (aba anônima, cota cheia): nada quebra, a dica fechada não volta
   na mesma visita. Teste: `helpStore.test.ts › storageThrowsKeepsInMemory` (Task 1).
2. **Duas pessoas no mesmo aparelho:** o que uma viu ou reviu não muda para a outra. Testes:
   `helpStore.test.ts › seenIsPerPerson` e `› resetOnlyThisPerson` (Task 1).
3. **Dica de evento com tour ou diálogo aberto:** não aparece por cima e não conta como vista.
   Testes: `EventTipHost.test.tsx › skipsWhileDialogOpen` e `› skipsWhileTourOpen` (Task 5).
4. **Jovem e Leitor:** Como funciona e o aviso de módulo mostram só o que a pessoa vê no menu Mais.
   Testes: `HowItWorksPage.test.tsx › youthDoesNotSeeVentures`, `› viewerSeesEnabledModules` (Task 2)
   e `ModulesPage.test.tsx › youthGetsNoVenturesLink` (Task 6).
5. **"Ver o tour" com dicas desligadas ou tour já visto:** abre mesmo assim, e o voltar não reabre.
   Testes: `PageTour.test.tsx › forcedOpensWhenTipsOff`, `› forcedOpensWhenSeen` e
   `› closeClearsForceState` (Task 2).

## Mapa de arquivos

Caminhos relativos a `web/src/`, salvo quando começam com `web/` ou `docs/`.

| Arquivo | Task | Papel |
|---|---|---|
| `shared/help/{catalog.ts,helpStore.ts,index.ts,messages.pt-BR.ts,PageTip.tsx,PageTour.tsx,TipBubble.tsx,Tour.tsx}` | 1 | catálogo, store, dica e tour |
| `shared/i18n/formatjs.d.ts`, os 9 lugares que importam `@/shared/tips` | 1 | caminho novo |
| `features/help/{HowItWorksPage.tsx,messages.pt-BR.ts}`, `app/{routes.tsx,MorePage.tsx}`, `web/eslint/boundaries.js`, `web/e2e/a11y.spec.ts` | 2 | Como funciona |
| `features/settings/{SettingsPage.tsx,messages.pt-BR.ts}` | 3 | Rever dicas e movimento |
| `features/dashboard/HomePage.tsx`, `features/transactions/NewTransactionPage.tsx`, `features/members/{FamilyPage.tsx,InvitePage.tsx,messages.pt-BR.ts}` | 4 | Início, Lançar e papéis |
| `shared/offline/{queuedSignal.ts,outbox.ts,index.ts}`, `shared/help/EventTip.tsx`, `app/{EventTipHost.tsx,WorkspaceLayout.tsx}` | 5 | dicas de evento |
| `features/markets/FeiraPage.tsx`, `features/ventures/VenturesListPage.tsx`, `features/gamification/AchievementsPage.tsx`, `features/modules/ModulesPage.tsx`, `web/e2e/{tips.spec.ts,receipts.ts}` | 6 | tours e aviso de módulo |
| as telas da tabela da Task 7 | 7 | lacunas |
| `features/ventures/{FiguresPanel.tsx,figuresText.ts,investments/texts.ts,messages.pt-BR.ts}`, as telas com passkey, `shared/i18n/messages.pt-BR.ts` | 8 | jargão |
| `app/helpCoverage.test.ts`, `features/{ventures/VenturesRoutes.tsx,markets/MarketsRoutes.tsx}` | 9 | cobertura |
| os bundles da tabela da Task 10 e 11 | 10, 11 | reescritas |

## Tasks

### Task 1: `shared/help`, catálogo e store reativo

**Files:**
- Move: `shared/tips/*` → `shared/help/*` (`git mv`).
- Create: `shared/help/{catalog.ts,helpStore.ts}`.
- Delete: `shared/help/{seenTips.ts,tipsPreference.ts,tours.ts}` e seus testes (cobertos pelos novos).
- Modify: `shared/help/{index.ts,PageTip.tsx,PageTour.tsx,TipBubble.tsx,Tour.tsx,messages.pt-BR.ts}`,
  `shared/i18n/formatjs.d.ts`, os 9 importadores (`grep -rln "@/shared/tips" src`),
  `features/settings/SettingsPage.tsx` (usa o `useTipsEnabled` novo).
- Test: `shared/help/{helpStore.test.ts,catalog.test.ts,messages.test.ts,PageTip.test.tsx,Tour.test.tsx}`.

**Interfaces:**
- Produces: tudo da seção "Interfaces", menos `queuedSignal`. Nesta task, `screenTips` tem só as
  dicas que já existem (`dashboard`, `transactions`, `accounts`, `calculator`, `import`); `TipId` já
  tem a lista completa e `screenTips` é `Partial` até a Task 7, que o torna completo (o teste
  `catalog.test.ts › everyTipHasText` entra lá).
- `routeHelp` nasce vazio aqui; as Tasks 2 a 8 acrescentam as entradas das telas que tocam, e a
  Task 9 completa e testa.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `helpStore.test.ts › markThenHasSeen` | `markSeen('u1','home')` → `hasSeen('u1','home')` true e `localStorage['liberdade.tip:u1:home'] === '1'` |
| `› seenIsPerPerson` | `markSeen('u1','home')` → `hasSeen('u2','home')` false |
| `› resetOnlyThisPerson` | marca `u1:home`, `u1:tour-markets`, `u2:home`; `resetAllSeen('u1')` → só `u2:home` fica; `liberdade.tips` intacta |
| `› clearSeenOne` | `clearSeen('u1','tour-markets')` apaga só essa chave |
| `› storageThrowsKeepsInMemory` | `setItem`/`getItem` lançam → `markSeen` não lança e `hasSeen` true na mesma sessão |
| `› useSeenRerenders` | componente com `useSeen('u1','home')`; `act(() => markSeen(...))` → re-renderiza com true |
| `› storageEventRerenders` | `window.dispatchEvent(new StorageEvent('storage', { key: 'liberdade.tip:u1:home' }))` depois de gravar direto → `useSeen` true |
| `› tipsToggleIsShared` | dois componentes com `useTipsEnabled`; um desliga → o outro mostra desligado; `localStorage['liberdade.tips'] === 'off'` |
| `› helpOpenFlag` | `setHelpOpen(true)` → `isHelpOpen()` true; `false` → false |
| `catalog.test.ts › toursHaveOneToFourSteps` | todo tour tem 1 a 4 passos |
| `› tourAnchorsAreUnique` | dentro de cada tour, nenhuma âncora repetida |
| `› tourMessagesExist` | todo `messageId` de passo existe em `helpMessages` |
| `› seenIds` | `seenId.tip('home') === 'home'`, `seenId.tour('planning') === 'tour-planning'`, `seenId.event('offline') === 'event-offline'` |
| `messages.test.ts › tipsFit140AndTwoSentences` | todo texto de `helpMessages` tem até 140 caracteres e até 2 frases (frase = trecho terminado em `.`, `!` ou `?` seguido de espaço ou fim) |
| `› noLossPhrase` | a regex de perda não casa com nenhum texto |
| `PageTip.test.tsx › dismissHidesAcrossScreens` | fecha a dica numa montagem → outra montagem do mesmo id não mostra |
| `Tour.test.tsx` | os testes atuais passam sem mudança de comportamento |

- [ ] **Step 2: Rodar e ver falhar.** `npx vitest run src/shared/help` → FAIL (módulos não existem).
- [ ] **Step 3: Implementar.** D1, D2, D3. `TipBubble` e `Tour` chamam `setHelpOpen` (D6). O texto da
  regra das frases fica num ajudante de teste `shared/help/testing/copyRules.ts`
  (`countSentences(text): number`, `LOSS`), reusado pelas Tasks 2, 7 e 9.
- [ ] **Step 4: Rodar e ver passar** → PASS. Depois a suíte toda (`npx vitest run`), `typecheck`,
  `lint`, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "refactor(help): move tips to a single help catalog with a reactive store"`.

---

### Task 2: A tela "Como funciona"

**Files:**
- Create: `features/help/{HowItWorksPage.tsx,messages.pt-BR.ts}`.
- Modify: `app/routes.tsx` (rota `/mais/como-funciona`, `lazy`, junto de `/mais/ajustes`),
  `app/MorePage.tsx` (link "Como funciona" para todos, antes de `/mais/privacidade`, com a classe
  dos outros links), `web/eslint/boundaries.js` (`help` em `CORE`), `shared/help/PageTour.tsx` (D4,
  D5), `shared/help/catalog.ts` (`moduleHelp`, `glossary`), `shared/i18n/formatjs.d.ts`
  (`howItWorksMessages`), `web/e2e/a11y.spec.ts` (a tela nos dois temas).
- Test: `features/help/{HowItWorksPage.test.tsx,messages.test.ts}`, `shared/help/PageTour.test.tsx`,
  `app/MorePage.test.tsx`.

**Interfaces:**
- Consumes: `moduleHelp`, `glossary`, `tours`, `useTipsEnabled` (Task 1); `useModules`/`isModuleOn`
  e `useCurrentWorkspace` como a `MorePage` já usa.
- Produces: rota `/mais/como-funciona`; `routeHelp['/mais/como-funciona'] = { exception: 'help screen' }`.

Conteúdo da tela, nesta ordem (spec 5.2). Títulos `h2`; cada seção até 280 caracteres.

| Seção | Chave | Texto |
|---|---|---|
| Começar | `help.start` | "Anote cada gasto em Lançar. O Início mostra o resumo do mês e o Painel mostra para onde foi o dinheiro." |
| Papéis | `help.roles` | "Dono cuida de tudo e Adulto lança e planeja. Jovem lança só nas próprias contas, e Leitor só olha." |
| Sem internet | `help.offline` | "Sem internet, seus gastos ficam guardados neste aparelho. Eles sobem sozinhos quando o sinal voltar." |
| Ao vivo | `help.live` | "O que alguém da família lança aparece nos outros aparelhos na hora, sem recarregar." |
| um por módulo visível | `help.module.<module>` | uma frase do módulo + link "Ver o tour" |
| Palavras | `help.glossary` | lista `dl` com os verbetes abaixo |

Módulos (spec 5.1): planning → `/planejar`; indicators → `/mais/indicadores`; gamification →
`/mais/conquistas` (tour `achievements`); markets → `/mais/feira`; ventures → `/mais/projetos`
(`hiddenForYouth: true`). Textos:
`help.module.planning` "Orçamento, metas, reserva e contas previstas, num lugar só.";
`help.module.indicators` "Números oficiais da economia e uma calculadora de rendimento.";
`help.module.gamification` "Sua sequência de semanas e as conquistas, sem pressa.";
`help.module.markets` "Mercados perto de você, preços das notas, lista de compras e comparação.";
`help.module.ventures` "Projetos de renda extra: quanto sobra, quando o investimento volta.";
`help.tour.open` "Ver o tour".

Glossário (`help.term.<x>` / `help.def.<x>`, spec 5.8):

| Termo | Definição |
|---|---|
| Passkey | "Passkey é entrar com a digital, o rosto ou o PIN do aparelho, sem senha." |
| CDB | "Certificado de Depósito Bancário: você aplica dinheiro no banco e recebe juros." |
| LCI e LCA | "Letras de Crédito Imobiliário e do Agronegócio: parecidas com o CDB, sem Imposto de Renda para pessoa física." |
| CDI | "Taxa que os bancos usam entre si. Muitos investimentos rendem uma porcentagem dela." |
| IPCA | "O índice oficial da inflação: quanto os preços subiram." |
| Selic | "A taxa básica de juros do país, definida pelo Banco Central." |
| FGC | "Fundo Garantidor de Créditos: devolve até R$ 250 mil por pessoa e por banco se o banco falir." |

> Nenhum verbete usa "quebrar" nem "empresta" (a regex de perda casa com os dois). O valor do FGC
> é o mesmo de `FGC_LIMIT` (`features/ventures/investments/texts.ts:22`); o teste
> `messages.test.ts › fgcMatchesInvestments` confere que os dois textos contêm "R$ 250 mil".

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `HowItWorksPage.test.tsx › sectionsInOrder` | `h2` na ordem: Começar, Papéis, Sem internet, Ao vivo, módulos ligados, Palavras |
| `› onlyEnabledModules` | Feira desligada → sem a seção da Feira |
| `› youthDoesNotSeeVentures` | Jovem com Projetos ligado → sem a seção de Projetos |
| `› viewerSeesEnabledModules` | Leitor → vê as seções dos módulos ligados |
| `› seeTourNavigatesWithState` | "Ver o tour" da Feira → navega para `/mais/feira` com `state.forceTour === 'markets'` |
| `› glossaryHasEveryTerm` | um `dt` por verbete do `glossary` |
| `› noAxeViolations` | axe sem violações, `data-theme` claro e escuro |
| `messages.test.ts › fit280` | todo texto de `howItWorksMessages` até 280 caracteres; regex de perda não casa |
| `› fgcMatchesInvestments` | o verbete do FGC e o `FGC_LIMIT` contêm "R$ 250 mil" (`FGC_LIMIT` passa a ser exportado) |
| `PageTour.test.tsx › forcedOpensWhenTipsOff` | dicas desligadas + `state.forceTour` igual → o tour abre |
| `› forcedOpensWhenSeen` | `tour-markets` visto + `state.forceTour` → abre |
| `› closeClearsForceState` | fechar → `markSeen` e a rota fica com `state` nulo (`replace`) |
| `› waitsForDialog` | com um `[role="dialog"]` no `body` → não abre; ao remover → abre |
| `MorePage.test.tsx › howItWorksLinkForEveryone` | Dono, Adulto, Jovem e Leitor veem "Como funciona" → `/mais/como-funciona` |

- [ ] **Step 2: Rodar e ver falhar.** `npx vitest run src/features/help src/shared/help src/app/MorePage.test.tsx` → FAIL.
- [ ] **Step 3: Implementar.** D4, D5. A tela registra `howItWorksMessages`; os verbetes são `dl`.
  `a11y.spec.ts` ganha `/mais/como-funciona` no laço de telas que já roda nos dois temas.
- [ ] **Step 4: Rodar e ver passar** → PASS. `typecheck`, `lint`, `check:chunks`, e2e tipado.
- [ ] **Step 5: Commit.** `git commit -m "feat(help): add the how it works screen with module tours and glossary"`.

---

### Task 3: Rever dicas e a linha do movimento

**Files:**
- Modify: `features/settings/{SettingsPage.tsx,messages.pt-BR.ts}`, `shared/help/catalog.ts`
  (`routeHelp['/mais/ajustes'] = { inline: 'settings.motion' }`).
- Test: `features/settings/SettingsPage.test.tsx`.

**Interfaces:**
- Consumes: `resetAllSeen`, `useTipsEnabled`, `useMe` (Task 1).

Textos: `settings.tips.review` "Rever dicas"; `settings.tips.reviewed` "Pronto. As dicas vão aparecer
de novo."; `settings.motion` "O app segue a opção Reduzir movimento do seu aparelho." (lacuna 25).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `SettingsPage.test.tsx › reviewTipsResetsAndTurnsOn` | dicas desligadas e `u1:home` visto; "Rever dicas" → `hasSeen('u1','home')` false, interruptor ligado, status "Pronto. As dicas vão aparecer de novo." |
| `› reviewTipsKeepsOtherPeople` | `u2:home` continua visto |
| `› motionLine` | mostra "O app segue a opção Reduzir movimento do seu aparelho." |

- [ ] **Step 2: Rodar e ver falhar.** `npx vitest run src/features/settings` → FAIL.
- [ ] **Step 3: Implementar.** D9. Botão com a classe de contorno da tela; status em `role="status"`.
- [ ] **Step 4: Rodar e ver passar** → PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(help): let each person review the tips again"`.

---

### Task 4: Início, Lançar e papéis

**Files:**
- Modify: `features/dashboard/HomePage.tsx` (`<PageTip id="home" />` no topo do `main`),
  `features/transactions/NewTransactionPage.tsx` (`<PageTip id="launch" />`),
  `features/members/FamilyPage.tsx` (`<PageTip id="family" />`), `features/members/InvitePage.tsx`
  (linha do papel escolhido), `features/members/messages.pt-BR.ts`,
  `shared/help/{catalog.ts,messages.pt-BR.ts}`.
- Test: `features/dashboard/HomePage.test.tsx`, `features/transactions/NewTransactionPage.test.tsx`,
  `features/members/{FamilyPage.test.tsx,InvitePage.test.tsx}`.

Textos (spec 5.4 e 5.5):
- `tips.home` "Aqui está o resumo do mês. Toque em Novo gasto para anotar o que gastou."
- `tips.launch` "Digite o valor, escolha a categoria e salve. Dá para desfazer logo depois."
- `tips.family` "Dono cuida de tudo e Adulto lança e planeja. Jovem lança só nas próprias contas, e Leitor só olha."
- `members.invite.role.ADULT` "Lança, planeja e liga ou desliga módulos."
- `members.invite.role.YOUTH` "Vê e lança só nas próprias contas, e edita só os próprios lançamentos."
- `members.invite.role.VIEWER` "Vê tudo e não muda nada."
- `members.invite.role.OWNER` "Cuida de tudo, inclusive da família e dos papéis."

A linha do papel fica logo abaixo do `select` (`InvitePage.tsx` ~linha 140), ligada a ele por
`aria-describedby`, e muda com a escolha. `routeHelp`: `/inicio` home, `/lancar` launch,
`/mais/familia` family, `/mais/familia/convidar` `{ inherits: '/mais/familia' }`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `HomePage.test.tsx › homeTip` | dicas ligadas → nota com "Aqui está o resumo do mês. Toque em Novo gasto para anotar o que gastou." |
| `NewTransactionPage.test.tsx › launchTip` | nota com "Digite o valor, escolha a categoria e salve. Dá para desfazer logo depois." |
| `FamilyPage.test.tsx › rolesTip` | nota com o texto de `tips.family` |
| `InvitePage.test.tsx › roleLineFollowsSelect` | padrão Adulto → "Lança, planeja e liga ou desliga módulos."; escolher Jovem → a linha do Jovem; o `select` tem `aria-describedby` apontando para ela |
| `› ownerSeesOwnerLine` | Dono escolhe Dono → "Cuida de tudo, inclusive da família e dos papéis." |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** Conferir `e2e/tips.spec.ts` e `e2e/a11y.spec.ts:516` (dicas ligadas):
  se uma dica nova divide a tela com a dica que o teste fecha, o `getByRole('button', { name:
  'Entendi' })` passa a achar dois; escopar pela nota (`getByRole('note').filter({ hasText })`).
- [ ] **Step 4: Rodar e ver passar** → PASS. Suíte toda, `typecheck`, `lint`, `check:chunks`, e2e tipado.
- [ ] **Step 5: Commit.** `git commit -m "feat(help): explain the home, launch and family roles"`.

---

### Task 5: Dicas de evento (sem internet e ao vivo)

**Files:**
- Create: `shared/offline/queuedSignal.ts`, `shared/help/EventTip.tsx`, `app/EventTipHost.tsx`.
- Modify: `shared/offline/{outbox.ts,index.ts}` (D8; exporta `onQueued`), `app/WorkspaceLayout.tsx`,
  `shared/help/{catalog.ts,messages.pt-BR.ts}`.
- Test: `shared/offline/{queuedSignal.test.ts,outbox.test.ts}`, `app/EventTipHost.test.tsx`.

Textos (spec 5.6): `tips.event.offline` "Sem internet, seus gastos ficam guardados neste aparelho.
Eles sobem sozinhos quando o sinal voltar."; `tips.event.live` "Um lançamento chegou de outro
aparelho. A tela já se atualizou sozinha."

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `queuedSignal.test.ts › notifiesAndUnsubscribes` | ouvinte chamado uma vez por `notifyQueued`; depois de desinscrever, não |
| `outbox.test.ts › enqueueNotifiesQueued` | `enqueue` → `onQueued` chamado uma vez, depois do `add` |
| `EventTipHost.test.tsx › offlineTipOnce` | `navigator.onLine` false + `notifyQueued()` → status com o texto offline; "Entendi" → some; novo `notifyQueued()` → nada |
| `› noOfflineTipWhenOnline` | `onLine` true + `notifyQueued()` → nada |
| `› liveTipOnArrival` | `markRemoteArrival('t1')` → status com o texto ao vivo |
| `› skipsWhileDialogOpen` | `[role="dialog"]` no `body` + evento → nada e `hasSeen(u,'event-live')` false |
| `› skipsWhileTourOpen` | `setHelpOpen(true)` + evento → nada |
| `› oneAtATime` | offline mostrando + chegada ao vivo → continua só a offline |
| `› tipsOffShowsNothing` | dicas desligadas → nada |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D6, D7, D8. `EventTip` é o balão com `role="status"` e o botão
  "Entendi", `lazy` como o `TipBubble`. `routeHelp` não muda (dica de evento não é de rota).
- [ ] **Step 4: Rodar e ver passar** → PASS. `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(help): explain the offline queue and live updates when they happen"`.

---

### Task 6: Tours da Feira e de Projetos, sequência e aviso de módulo

**Files:**
- Modify: `features/markets/FeiraPage.tsx`, `features/ventures/VenturesListPage.tsx`,
  `features/gamification/AchievementsPage.tsx` (âncora `achievements-rest` na linha da folga),
  `features/modules/{ModulesPage.tsx,messages.pt-BR.ts}`, `shared/help/{catalog.ts,messages.pt-BR.ts}`,
  `web/e2e/{tips.spec.ts,receipts.ts}`.
- Test: `features/markets/FeiraPage.test.tsx`, `features/ventures/VenturesListPage.test.tsx`,
  `features/gamification/AchievementsPage.test.tsx`, `features/modules/ModulesPage.test.tsx`.

Passos (spec 5.7; D12 no passo 2 da sequência):

| Tour | Âncora | Chave | Texto |
|---|---|---|---|
| markets | `markets-nearby` (div do título com o 📍, ~linha 114) | `tips.tour.markets.nearby` | "Veja mercados perto de você e escolha onde procurar." |
| markets | `markets-receipt` (div do `canWrite`, ~linha 208) | `tips.tour.markets.receipt` | "Registre a nota da compra para guardar os preços." |
| markets | `markets-list` (div dos links lista/produtos, ~linha 219) | `tips.tour.markets.list` | "Monte a lista de compras e compare os mercados." |
| ventures | `ventures-intro` (o `h1`) | `tips.tour.ventures.intro` | "Projetos mostram quanto sobra de uma renda extra." |
| ventures | `ventures-start` (o botão de começar) | `tips.tour.ventures.start` | "Comece por um modelo e ajuste os números." |
| ventures | `ventures-actual` (a lista, ou o texto de vazio) | `tips.tour.ventures.actual` | "Marque lançamentos no projeto para ver o real contra o previsto." |
| achievements | `achievements-streak` | `tips.tour.achievements.streak` | "Sua sequência conta as semanas em que você lançou algo." |
| achievements | `achievements-rest` | `tips.tour.achievements.streakBreak` | "Uma semana de folga por mês não interrompe a sequência." |
| achievements | `achievements-list` | `tips.tour.achievements.list` | (sem mudança) |

Aviso de módulo (D10): `modules.enabled` "{module} ligado."; `modules.open` "Abrir {module}".
`routeHelp`: `/mais/feira` markets, `/mais/projetos` ventures, `/mais/projetos/comecar` e
`/mais/projetos/:id/editar` `{ inherits: '/mais/projetos' }`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `FeiraPage.test.tsx › tourAnchors` | Dono → as 3 âncoras na tela; Jovem → sem `markets-receipt` |
| `› tourWaitsForChooser` | primeira visita, sem local salvo → o seletor abre e o tour não; escolher o local → o tour abre no "Passo 1 de 3" |
| `› youthTourSkipsReceipt` | Jovem → "Passo 1 de 2" |
| `VenturesListPage.test.tsx › tourAnchors` | as 3 âncoras, com lista e vazia |
| `AchievementsPage.test.tsx › streakTourHasThreeSteps` | "Passo 1 de 3"; o passo 2 diz "Uma semana de folga por mês não interrompe a sequência." |
| `ModulesPage.test.tsx › enableShowsNoticeAndLink` | ligar a Feira → status "Feira ligado." e link "Abrir Feira" → `/mais/feira`; `hasSeen(u,'tour-markets')` false depois de marcado antes |
| `› youthGetsNoVenturesLink` | Jovem não muda módulos; para Adulto, ligar Projetos mostra o link; o componente do aviso com papel `YOUTH` não mostra o link de Projetos |
| `› disableShowsNoNotice` | desligar → sem "ligado." |

> O nome do módulo no aviso vem do mesmo id que o interruptor usa. Se o gênero não fecha ("Feira
> ligado"), o texto é "{module}: ligado." — decidir no Step 3 pelo que lê melhor com os 5 nomes e
> registrar como ruling.

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D5, D10. E2e:
  - `tips.spec.ts` ~linha 126: 'Passo 1 de 2' → 'Passo 1 de 3'.
  - `receipts.ts` `openFeira`, entre a linha 104 (`await expect(chooser).toBeHidden()`) e a 105: se o
    teste roda com dicas ligadas (`tipsOn()` de `support.ts`, ou o equivalente que a linha 45
    descreve), fechar o tour com "Pular". Com dicas desligadas, nada muda.
- [ ] **Step 4: Rodar e ver passar** → PASS. e2e tipado.
- [ ] **Step 5: Commit.** `git commit -m "feat(help): add the feira and projects tours and the module notice"`.

---

### Task 7: As lacunas

**Files:** as telas e bundles da tabela; `shared/help/{catalog.ts,messages.pt-BR.ts}`.
**Test:** o teste de cada tela (`<Tela>.test.tsx`), mais `catalog.test.ts › everyTipHasText`
(`screenTips` deixa de ser `Partial`).

| # | Tela (arquivo) | Forma | Chave | Texto |
|---|---|---|---|---|
| 12 | `privacy/PrivacyPage.tsx` | linha sob o `h1` | `privacy.intro` | "Aqui você baixa seus dados, vê quem entrou e pode excluir a conta. Excluir leva 30 dias e dá para cancelar." |
| 13 | `recurrences/RecurrencesPage.tsx` | `PageTip recurrences` | `tips.recurrences` | "Previstos são contas que você espera pagar. Toque em Confirmar quando acontecerem." |
| 14 | `markets/list/ShoppingListPage.tsx` | `PageTip shopping-list` | `tips.shoppingList` | "A lista é da família toda e muda ao vivo em todos os aparelhos." |
| 15 | `markets/products/ProductsPage.tsx` | `PageTip products` | `tips.products` | "Se dois nomes são o mesmo produto, junte para os preços ficarem em um só." |
| 16 | `members/WorkspaceSwitcher.tsx` | linha sob o `h2` | `members.switcher.help` | "Você faz parte de mais de uma família? Troque aqui." |
| 17 | `planning/BudgetPage.tsx` | `PageTip budget` | `tips.budget` | "Você recebe um aviso quando um gasto chega a 80% do limite. É só para ajudar." |
| 18 | `planning/GoalsPage.tsx` | `PageTip goals` | `tips.goals` | "Guardar soma dinheiro na meta. Se a meta acompanha uma conta, o valor vem do saldo dela." |
| 19 | `planning/SpendingSplitHomeCard.tsx` | legenda | `planning.split.caption` | "Fixos se repetem todo mês, como o aluguel. Variáveis mudam, como o mercado." |
| 20 | `categories/CategoriesPage.tsx` | `PageTip categories` | `tips.categories` | "Use Dentro de para criar subcategorias, como Mercado dentro de Casa." |
| 21 | `transactions/TransactionFilters.tsx` | linha no topo do painel | `transactions.filters.help` | "Use os filtros para achar um gasto por categoria, conta ou tag." |
| 22 | `transactions/TagInput.tsx` | descrição do campo (`aria-describedby`) | `transactions.tags.help` | "Tags são etiquetas livres. Use para achar gastos parecidos depois." |
| 23 | `portrait/PortraitPage.tsx` | `PageTip portrait` | `tips.portrait` | "Uma imagem do mês para compartilhar. Mostra só percentuais, nunca valores." |
| 24 | `ventures/StepsList.tsx` | linha sob o `h2` | `ventures.steps.help` | "Liste os próximos passos. Marque como Feito ao concluir." (D12) |
| — | `markets/receipt/ReceiptPage.tsx` | `PageTip receipt` | `tips.receipt` | "Aponte a câmera para o QR da nota. Os preços ficam guardados; a nota não vira gasto." |
| — | `modules/ModulesPage.tsx` | `PageTip modules` | `tips.modules` | "Ligue só o que quiser usar. Desligar esconde a parte, mas não apaga nada." |
| — | `markets/MarketCardPage.tsx` | `PageTip market` | `tips.market` | "O cartão mostra o endereço, os links e os preços que a família registrou." |
| — | `markets/compare/ComparePage.tsx` | `PageTip compare` | `tips.compare` | "O total conta só os itens com preço em todos os mercados escolhidos." |
| — | `ventures/ActualVsPlannedPage.tsx` | `PageTip venture-real` | `tips.ventureReal` | "Real é o que os lançamentos marcados mostram. Previsto é o que você planejou." |

`screenTips` completo também liga as dicas que já existiam: `dashboard`, `transactions`,
`accounts`, `calculator` (`/mais/indicadores/calculadora`), `import` (`/lancamentos/importar`).
`routeHelp` ganha a entrada de cada linha acima com caminho de rota (as linhas 16, 19, 21 e 22 são
componentes dentro de telas que já têm ajuda e não entram).

Os módulos opcionais importam `PageTip` de `@/shared/help` (permitido). A linha 16 aparece também no
`privacy/PendingFamilyCard.tsx`, que usa o mesmo componente.

- [ ] **Step 1: Escrever os testes (falhando).** Em cada `<Tela>.test.tsx`, um teste
  `helpText` que acha o texto exato da tabela (dica com dicas ligadas, `getByRole('note')`; linha ou
  legenda com `getByText`). Para a linha 22, o campo de tag tem `aria-describedby` com o texto. Em
  `catalog.test.ts › everyTipHasText`, todo `TipId` tem texto em `helpMessages`.
- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** Linhas em `text-sm text-text-muted`, como as legendas atuais.
- [ ] **Step 4: Rodar e ver passar** → PASS. Suíte toda, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(help): fill the screen help gaps"`.

---

### Task 8: Jargão

**Files:**
- Modify: `features/ventures/{FiguresPanel.tsx,figuresText.ts,messages.pt-BR.ts,RealPaybackCards.tsx,VenturesListPage.tsx}`,
  `features/ventures/investments/{texts.ts,InvestmentSimulatorPage.tsx}`,
  `shared/i18n/messages.pt-BR.ts`, e as telas com passkey (tabela abaixo).
- Test: `features/ventures/{FiguresPanel.test.tsx,investments/texts.test.ts,investments/InvestmentSimulatorPage.test.tsx}`,
  `shared/i18n/messages.test.ts`, os testes das telas com passkey.

1. **Legendas sob os números do projeto** (spec 5.8), em `FiguresPanel`:
   - Margem: "Margem: o que sobra de cada venda." (`ventures.caption.margin`)
   - Equilíbrio: "Equilíbrio: quanto vender para não ter prejuízo." (`ventures.caption.breakEven`)
   - Retorno: "Retorno: em quantos meses o investimento volta." (`ventures.caption.payback`)
   - Rótulos: "Margem por unidade", "Margem", "Equilíbrio (faturamento)", "Retorno do investimento",
     "Retorno em 12 meses". `ventures.how.payback`/`paybackEdge` trocam "Payback:" por "Retorno:";
     `ventures.how.roi`/`roiEdge` trocam "ROI em 12 meses:" por "Retorno em 12 meses:". Os rótulos
     iguais em `VenturesListPage` (`ventures.payback`) e `RealPaybackCards` (`ventures.real.payback`)
     acompanham.
2. **Investimentos** (`INVESTMENT_TOPICS`): cada campo com uma frase; cada sigla explicada na
   primeira vez que aparece na tela, na ordem dos cartões (poupança, renda fixa, Tesouro Direto,
   fundos, ações):
   - FGC em `poupanca.guarantee`: "Fundo Garantidor de Créditos (FGC)" + o limite;
   - CDB, LCI, LCA, CDI e IPCA em `renda-fixa.whatItIs`;
   - Selic em `tesouro-direto.whatItIs`;
   - "alíquota" em `renda-fixa.tax` ("a porcentagem de imposto") e "custódia" em `tesouro-direto.tax`
     ("a taxa de guarda dos títulos").
   - No simulador, as opções 'Selic' e 'CDI' (`InvestmentSimulatorPage.tsx:31-32`) ganham a mesma
     explicação curta como descrição do grupo.
3. **Passkey** (spec 5.8): na primeira menção de cada tela, a linha "Passkey é entrar com a digital,
   o rosto ou o PIN do aparelho, sem senha." (`passkey.what`, em `shared/i18n/messages.pt-BR.ts`):

   | Tela | Onde |
   |---|---|
   | Entrar | `features/auth/SignInPage.tsx` ~linha 131 |
   | Mais | `features/auth/PasskeyButton.tsx` |
   | Início | `features/dashboard/GettingStartedCard.tsx` ~linha 65 |
   | Privacidade | `features/privacy/PrivacyPage.tsx` ~linha 40 |
   | Convite | `features/members/AcceptInvitationPage.tsx` (primeira de `accept.recoveryHint`, `accept.youthPasskeyOnly`, `accept.passkeyTitle` que aparecer) |
   | Reautenticação | `shared/auth/ReauthDialog.tsx` ~linha 117 |

   Os textos legais (`/privacidade`, `/termos`) não mudam.
4. **TED:** `payment.BANK_TRANSFER` "TED" → "Transferência (TED)" (aparece em `PaymentMethodChips`,
   `TransactionFilters`, `TransactionDetailSheet`, `TransactionRow`, `TransactionsPage` e
   `DashboardPage`; nenhum muda além do texto).

`routeHelp`: `/mais/projetos/:id` `{ inline: 'ventures.caption.margin' }`,
`/mais/projetos/investimentos` `{ inline: 'ventures.invest.intro' }`,
`/mais/projetos/investimentos/simulador` `{ inline: 'ventures.invest.disclaimer' }`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `FiguresPanel.test.tsx › captions` | as 3 legendas e os 5 rótulos novos na tela |
| `texts.test.ts › oneSentencePerField` | todo campo de todo tópico tem 1 frase |
| `› acronymsExplainedOnFirstUse` | na ordem dos cartões, a primeira ocorrência de CDB, LCI, LCA, CDI, IPCA, Selic e FGC vem com a explicação (o nome por extenso no mesmo campo) |
| `› noLossPhrase` | a regex de perda não casa |
| `InvestmentSimulatorPage.test.tsx › rateOptionsExplained` | o grupo Selic/CDI tem descrição |
| `<cada tela da passkey>.test.tsx › passkeyExplained` | a linha `passkey.what` aparece uma vez |
| `messages.test.ts › bankTransferLabel` | `payment.BANK_TRANSFER === 'Transferência (TED)'` |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** Conferir `grep -rn "TED\|Payback\|ROI" e2e/` antes de trocar.
- [ ] **Step 4: Rodar e ver passar** → PASS. Suíte toda.
- [ ] **Step 5: Commit.** `git commit -m "feat(help): explain finance words and passkeys where they first appear"`.

---

### Task 9: Teste de cobertura

**Files:**
- Create: `app/helpCoverage.test.ts`.
- Modify: `features/ventures/VenturesRoutes.tsx`, `features/markets/MarketsRoutes.tsx` (D11),
  `shared/help/catalog.ts` (entradas que faltarem: herdadas, inline e exceções).

Exceções (spec 5.10): `/entrar`, `/convite`, `/privacidade`, `/termos`, `/robo`, `/boas-vindas`,
`/mais`, `/mais/recorrencias` (só redireciona), `*`, `/mais/como-funciona`. Inline restantes:
`/mais/privacidade` `privacy.intro`; `/mais/privacidade/atividade` `activity.hint`;
`/planejar/reserva` `planning.reserve.education`; `/mais/plano` `billing.freeForever`.
`/painel/retrato` é `portrait`; `/mais/indicadores/calculadora` é `calculator`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `helpCoverage.test.ts › everyRouteHasHelp` | todo caminho de `routes` (com os filhos) e de `venturesRoutePaths`/`marketsRoutePaths` (prefixados com `/mais/projetos` e `/mais/feira`) tem entrada em `routeHelp`; a mensagem de falha lista os caminhos sem entrada |
| `› noStaleEntries` | toda chave de `routeHelp` é um caminho que existe |
| `› inheritsPointToHelpedRoutes` | todo `inherits` aponta para uma rota com `tip`, `tour` ou `inline` |
| `› tipsAndToursRendered` | para cada `{ tip: id }`, algum arquivo de `src/features/**/*.tsx` contém `<PageTip id="id"`; para cada `{ tour: id }`, `<PageTour id="id"` (`import.meta.glob(..., { query: '?raw', eager: true })`) |
| `› noScreenWithTipAndTour` | nenhum arquivo contém `<PageTip` e `<PageTour` (D14) |
| `› inlineIdsExistAndFit` | todo `inline` existe nas mensagens registradas e tem até 280 caracteres; a regex de perda não casa |

- [ ] **Step 2: Rodar e ver falhar** → FAIL (exports e entradas faltando).
- [ ] **Step 3: Implementar.** Os arrays do `useRoutes` passam a ler os `path` de
  `venturesRoutePaths`/`marketsRoutePaths`, para os dois nunca divergirem.
- [ ] **Step 4: Rodar e ver passar** → PASS. Suíte toda, `typecheck`, `lint`, `check:chunks`, `build`,
  e2e tipado.
- [ ] **Step 5: Commit.** `git commit -m "test(help): fail when a screen has no help"`.

---

### Task 10: Reescritas · dicas, planejamento, contas, cobrança e importação

**Files:** os bundles abaixo e os componentes que mudam de forma; os testes que conferem esses
textos mudam junto. Antes de cada troca, `grep -rn` do texto antigo em `web/src` e `web/e2e`.

| Chave | Texto novo |
|---|---|
| `tips.calculator` | "Veja se o dinheiro rende mais que os preços sobem. É só uma conta, não um conselho." |
| `tips.import` | "Seu extrato fica só neste aparelho. Confira cada linha antes de salvar." |
| `tips.tour.planning.reserve` | "A reserva mostra por quantos meses a família se sustenta sem renda." |
| `tips.tour.indicators.cards` | "Cada cartão mostra um número oficial da economia, com a fonte e a data." |
| `planning.reserve.education` | "É o dinheiro guardado para imprevistos, como uma conta inesperada ou um mês sem renda. Fica fácil de sacar." |
| `planning.goals.limitReached` | "No plano Grátis são 3 metas ativas. Conclua ou arquive uma para criar outra." |
| `planning.projection.note` | "É uma estimativa: usa a média de gastos dos últimos 3 meses." |
| `accounts.hint.CREDIT_CARD` | "Os gastos deixam o saldo negativo. Para pagar a fatura, transfira de outra conta." |
| `accounts.archive.body` | "Some das escolhas. O histórico fica e você pode reativar." |
| `billing.stalled` | "O pagamento ainda não foi confirmado. Volte mais tarde: o plano aparece aqui quando chegar." |
| `billing.waiting` | "Esperando a confirmação do pagamento. Pode fechar; avisamos aqui." |
| `billing.refund.body` | "O plano termina agora e o tema volta ao padrão. Nenhum dado é apagado. O dinheiro volta pelo mesmo meio." |
| `billing.refund.keep` | "Continuar como Família Fundadora" |
| `billing.refund.open` | "Pedir o dinheiro de volta" |
| `billing.refund.title` | "Pedir o dinheiro de volta?" |
| `billing.refund.confirm` | "Pedir os R$ 89,00 de volta" (mantém o valor como hoje, se for variável) |
| `import.columns.intro` | "Não deu para saber qual coluna é qual. Confira e diga como os centavos aparecem." |
| `import.intro` | "Escolha a conta e o arquivo de extrato do banco (OFX ou CSV)." |
| `import.result.rejected` | "Não foi aceito, então tentar de novo não resolve: {detail}" |
| `import.result.rejectedGeneric` | "Não foi aceito, então tentar de novo não resolve. Confira a conta e as categorias escolhidas." |
| `import.state.billPayment` | "Pagamento de fatura?" |
| `import.state.balance` | "Isto é saldo?" |
| `import.billPaymentNotice` | começa com "Pagamento de fatura?" (hoje "Pagamento da fatura?"); o resto fica |

`import.result.counts` vira três linhas em `ImportResult`, cada uma só quando o número é maior que
zero: `import.result.created` "Entraram {n}."; `import.result.confirmed` "Confirmaram previstos:
{n}."; `import.result.skipped` "Já existiam: {n}." A chave antiga sai. (`billing.refund.confirm`
e as três linhas de importação conferem o número com `{n, number}`.)

- [ ] **Step 1: Atualizar os testes que conferem esses textos (falhando)** e escrever
  `ImportResult.test.tsx › threeLines` (5 criados, 2 confirmados, 0 já existentes → as duas
  primeiras linhas e nenhuma "Já existiam").
- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** → PASS. Suíte toda; e2e tipado; `grep` dos textos antigos em
  `web/e2e` vazio.
- [ ] **Step 5: Commit.** `git commit -m "fix(copy): rewrite the planning, accounts, billing and import texts"`.

---

### Task 11: Reescritas · privacidade, família, Feira, Projetos e geral

| Chave | Texto novo |
|---|---|
| `privacy.deleteFamily.body` | "A família e tudo nela somem em 30 dias. Até lá, só você, o Dono, pode cancelar." |
| `privacy.deleteAccount.body` | "Em 30 dias sua conta some. Seus lançamentos ficam na família, sem o seu nome. Até lá dá para cancelar." |
| `privacy.consent.revokeBody` | "O Jovem sai da família: sem o consentimento do responsável, o app não pode guardar os dados dele." |
| `members.home.help` | "Serve para achar mercados perto de casa. Guardamos só um ponto aproximado." |
| `markets.add.osmHelp` | "Mercado que não está no mapa? Cadastre no OpenStreetMap e ele aparece aqui depois." |
| `markets.convert.help` | "Este mercado saiu do mapa. Digite o nome e marque a posição para a família continuar usando." |
| `markets.receipt.scanner.paused` | "A câmera desligou. Toque em Ler o QR, ou cole o link ou digite a chave." |
| `markets.map.notice` | "O mapa é do Google. O Google vai saber que você abriu o mapa e pode guardar dados sobre você." |
| `markets.instagram.notice` | "A publicação é do Instagram, da Meta. A Meta vai saber que você abriu a publicação e pode guardar dados sobre você." |
| `markets.receipt.error.not-nfce` | "Esta nota não é de compra no mercado." |
| `markets.receipt.items.sefaz` | "Abrir a nota no site da Fazenda (SEFAZ)" |
| `markets.receipt.items.noReader` | "Ainda não lemos notas de {uf}. Digite os itens você mesmo." |
| `ventures.form.exampleBanner` | "Itens com o selo exemplo vêm do modelo. Ajuste para a sua realidade." |
| `ventures.form.conflictBody` | "Outra pessoa mudou este projeto. O que você digitou fica; recarregue para ver a versão salva." |
| `transaction.undoOffline` | "Sem internet. Para desfazer, exclua em Lançamentos quando o sinal voltar." |

`markets.receipt.key.typeLabel` ("Chave de 44 números") e `welcome.greeting` (com o 👋) não mudam.

- [ ] **Step 1: Atualizar os testes que conferem esses textos (falhando).**
- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** Se um texto com `{…}` perdeu ou ganhou variável, o componente acompanha.
- [ ] **Step 4: Rodar e ver passar** → PASS. Suíte toda, `typecheck`, `lint`, `format:check`,
  `check:chunks`, `build`, e2e tipado; `grep` dos textos antigos em `web/e2e` vazio.
- [ ] **Step 5: Commit.** `git commit -m "fix(copy): rewrite the privacy, family, feira and projects texts"`.

## Depois do merge (passos do mantenedor)

1. No staging, com uma conta nova: ver as dicas de Início e Lançar, abrir Como funciona, rodar "Ver
   o tour" da Feira e de Projetos, ficar sem internet e lançar (dica offline), lançar de outro
   aparelho (dica ao vivo) e usar "Rever dicas".
2. Repetir Como funciona com uma pessoa Jovem e uma Leitor.
3. PR de docs: README dos planos, linha 11, "entregue".
