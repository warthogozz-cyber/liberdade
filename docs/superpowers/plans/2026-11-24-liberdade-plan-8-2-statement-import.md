# Liberdade · Plano 8.2: Importação de extrato OFX e CSV — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a segunda entrega da v0.3 (US-35).
- Em Lançamentos, "Importar extrato" lê um arquivo OFX ou CSV do banco **no aparelho**, mostra uma
  prévia com categoria sugerida e grava só as linhas que a pessoa confirma.
- Importar o mesmo arquivo de novo, ou um arquivo de período sobreposto, não duplica nada, nem um
  lançamento que a pessoa excluiu depois.
- Uma linha que bate com um lançamento previsto (recorrência) confirma o previsto em vez de criar
  outro. No cartão, toda compra entra como gasto; o pagamento da fatura vem desmarcado.

**Architecture:**
- **Web (`features/import`, sob demanda):** leitores OFX e CSV, detecção de colunas com mapeamento
  guardado no aparelho, normalização da descrição, `import_key` (SHA-256 com `crypto.subtle`),
  prévia com os selos e envio em lotes de até 500. O arquivo nunca sai do aparelho.
- **API (`ledger`):** V19 com `transactions.import_key` e `import_tombstones`; uma rota de
  importação em lote e uma de categorias sugeridas; o `TransactionPurgeJob` copia as chaves para os
  tombstones e apaga os de mais de 13 meses.
- **Guardas:** plano Família (aberta a todos com a cobrança desligada), Dono e Adulto, o Jovem só
  nas contas dele, o Leitor nunca.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, PostgreSQL 18, Flyway, JdbcClient.
  Testes com Testcontainers e `MockMvcTester`.
- **Web:** React 19, react-router v8, TanStack Query, react-intl, `TextDecoder`, `crypto.subtle`,
  MSW 3, Vitest e Playwright com axe.
- **Sem dependências novas.**

**Spec:**
- [Spec do Plano 8](../specs/2026-11-24-liberdade-plan-8-indicators-gamification-design.md),
  aprovado pelo mantenedor em 2026-11-24. Este plano cobre só a 8.2: as seções 6.1 e 6.2 no que
  tocam a 8.2, a 6.5, a 6.9 no que toca a 8.2 e a parte 8.2 das seções 7 e 8. É a autoridade: este
  plano argumenta a partir dele.
- [PRD](../../prd.md) (US-35), [Arquitetura](../../architecture.md),
  [Modelo de dados](../../data-model.md), [Design system](../../design-system.md).

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits (`feat(ledger): ...`, `feat(web): ...`,
  `test(e2e): ...`, `docs: ...`), com o assunto em minúsculas. Cada commit termina com a linha
  `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **Nenhum nome de banco no repositório.** Os arquivos de exemplo são escritos à mão, com
  estabelecimentos genéricos ("PADARIA PAO QUENTE", "MERCADO CENTRAL"), e nomeados pelo jeito do
  formato (`ofx-sgml-1252.ofx`), nunca pelo banco. Nada de agência, conta, CPF ou nome de pessoa,
  nem inventado.
- **O arquivo nunca sai do aparelho** (decisão 7 do mantenedor). A API recebe só linhas já
  confirmadas. Nenhum log, da API ou do web, leva valor, data ou descrição de linha importada.
- **Sem duplicar (spec 6.5):** `import_key` = SHA-256 de
  `account_id|data|valor|descrição normalizada|n`, a mesma regra para OFX e CSV; o `FITID` não entra.
  A API grava com `ON CONFLICT DO NOTHING` e pula as chaves de `import_tombstones`.
- **Cartão (decisão do mantenedor):** toda compra entra como gasto, qualquer que seja o sinal no
  arquivo. Linhas com o sinal oposto vêm desmarcadas, com o aviso exato "Pagamento da fatura? Ele já
  aparece como saída na conta de onde saiu o dinheiro." e entram como entrada se marcadas.
- **Guardas:** `@RequiresPlan(FAMILY)` aberta com a cobrança desligada (D4); Dono e Adulto; o Jovem
  só nas contas do escopo dele; o Leitor recebe 403.
- **Só com rede:** o lote não entra na fila offline. Sem rede, a tela diz "Sem conexão. A importação
  precisa de internet." e o botão fica desligado.
- **Limites:** até 500 linhas por chamada nas duas rotas; descrição cortada em 140 caracteres.
- **Tempo:** o `Clock` da API em tudo que a API decide; os 13 meses dos tombstones contam no fuso
  `America/Sao_Paulo`.
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR.
- **Fronteiras de módulo:** tudo novo da API fica em `ledger` (spec 6.1). `audit` passa a depender
  de `ledger` só pelo evento público `StatementImported`; o `ApplicationModulesTest` passa.
- **Contrato:** cada rota nova entra no `api/openapi.json`, no `web/src/shared/api/schema.ts` e no
  `RoleMatrixIT`, no mesmo commit.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `shared*` e `*.domain*`.
- **Dinheiro no web:** valores como centavos `bigint` (`parseCents`, `centsToApi` de
  `shared/money/money.ts`), nunca `Number`.
- **Desempenho:** o carregamento inicial do web não cresce (`check:chunks`, limite 200 KB, hoje
  203.587 B pelo relatório da 8.1). Todo o `features/import` carrega sob demanda; só o botão e a
  string dele ficam no chunk de Lançamentos, que já é sob demanda.
- **Acessibilidade:** axe no Playwright cobre a prévia e a tela de colunas. Selos são texto, não só
  cor. Cada linha da prévia tem caixa com rótulo.
- **Ética (spec 2):** nada de pressa nem de contagem que pressione; a tela de resultado só informa.
- **Custo:** R$ 0. Nenhum serviço novo.
- **GitHub Actions:** nenhum workflow novo e nenhuma permissão nova.
- **Ações externas:** nenhuma durante a execução. A conferência com extratos reais é do mantenedor,
  no staging, depois do merge.

### Pré-requisitos locais

Os mesmos da 8.1: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o compose de
e2e.

## Entrega

- **PR 1 (docs):** este plano e o README dos planos. Entra antes do código.
- **PR 2 (código):** branch `feat/plan-8-2-statement-import`, cortada da `main` depois do PR 1. Um
  commit por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o staging recebe o deploy pelo pipeline. O mantenedor confere o critério de
  saída da 8.2 (seção 8 do spec): importa um extrato real de cada banco da família no staging, duas
  vezes, sem duplicar. O corpo do PR 2 traz o roteiro (inclusive a conta de cartão e a tela de
  colunas).
- **Depois disso:** um PR de docs (README dos planos, linha 8) e o plano 8.3.

### Itens herdados

| Item | Onde |
|---|---|
| Rota por módulo volta para `/inicio` ao recarregar logo depois de ligar o módulo (8.1, já existia) | Fora da 8.2; proposta de issue depois do merge |
| Limite de tamanho do corpo nas respostas do Banco Central (8.1) | Fora da 8.2; proposta de issue |
| Alerta falso se um deploy interrompe a espera entre tentativas (8.1) | Fora da 8.2; proposta de issue |
| Abrir espaço no carregamento inicial (7.3) | Task 9 confere que a 8.2 não soma nada ao inicial; abrir espaço fica fora |
| Reconectar com a aba visível, celebração da reserva, batida do stream, `maxWait`, destaque antes do refetch, interruptor de 48 px, e2e do Jovem (7.3) | Fora da 8.2 |
| Limite de erro do `TagsTab` sob demanda (7.1) | PR próprio, com a tela de erro de rota em PT-BR |
| Issues #36, #38, #39 e #40 | Fora da 8.2 |

## Decisões deste plano

A seção 11 do spec deixa estes pontos ao construtor, e o plano decide:

**D1. Pacotes.**
- API: `ledger/internal/ImportController`, `ImportService`, `ImportRepository`, os records
  `ImportRequest`, `ImportRow`, `ImportResult`, `CategoryHintsRequest`, `CategoryHintItem`,
  `CategoryHintsResponse`, `CategoryHint`; `ledger/domain/DescriptionKey` (puro); o evento público
  `ledger/StatementImported`.
- Web: `features/import/` com `readers/` (decodificação, OFX, CSV), `fixtures/` (os arquivos de
  exemplo), a lógica pura (`normalize.ts`, `importKey.ts`, `kinds.ts`, `csvDetect.ts`,
  `csvMappingStore.ts`, `preview.ts`) e as telas.

**D2. V19** (`V19__statement_import.sql`):

```sql
ALTER TABLE transactions ADD COLUMN import_key text NULL
    CHECK (import_key IS NULL OR import_key ~ '^[0-9a-f]{64}$');
CREATE UNIQUE INDEX transactions_import_key
    ON transactions (workspace_id, import_key) WHERE import_key IS NOT NULL;
-- import_tombstones exatamente como na seção 6.2 do spec, mais:
CREATE INDEX import_tombstones_by_key ON import_tombstones (workspace_id, import_key);
CREATE INDEX import_tombstones_by_age ON import_tombstones (purged_at);
```

- **Desvio do spec:** o índice único é `(workspace_id, import_key)`, sem `account_id`. A chave já
  carrega a conta no hash, então para as importações os dois índices dão o mesmo. A diferença é um
  lançamento importado que a pessoa depois move para outra conta: com o índice do spec, reimportar o
  extrato da conta original criaria a linha de novo. A busca nos tombstones também é por
  `(workspace_id, import_key)`; a chave primária deles segue a do spec (com `account_id`, para a
  cascata com a conta).
- O índice vale também para os excluídos (sem `WHERE deleted_at IS NULL`), como pede o spec.
- Editar um lançamento importado mantém a `import_key`. Nenhuma rota de edição a recebe ou muda.

**D3. Limpeza.**
- `TransactionPurgeJob` troca o `DELETE` de `purgeDeletedBefore` por uma CTE, na mesma instrução e
  portanto na mesma transação:
  `WITH gone AS (DELETE ... RETURNING workspace_id, account_id, import_key) INSERT INTO import_tombstones (workspace_id, account_id, import_key) SELECT ... FROM gone WHERE import_key IS NOT NULL ON CONFLICT DO NOTHING`.
  Devolve a contagem apagada, como hoje.
- No mesmo job: `ImportRepository.purgeTombstonesBefore(Instant cutoff): int`, com
  `cutoff` = agora em São Paulo menos 13 meses (`ZonedDateTime.minusMonths(13)`). O log do job ganha
  só a contagem (`tombstones=<n>`).

**D4. Plano.**
- `RequiresPlan` ganha `boolean openWhileBillingOff() default false`. O `PlanGuardInterceptor` deixa
  passar quando o atributo é `true` e `plans.legalSetEnabled()` é `false` (a venda está desligada).
  As rotas que já usam `@RequiresPlan` não mudam.
- `PlanDetails` ganha `boolean importAllowed` = plano `FAMILY` ou venda desligada. O web lê por
  `usePlan(ws)` (rota aberta aos quatro papéis).

**D5. Rotas.**
- `POST /v1/workspaces/{ws}/accounts/{accountId}/imports`:
  - `@WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})` e
    `@RequiresPlan(value = FAMILY, openWhileBillingOff = true)`;
  - corpo `ImportRequest(@NotNull @Size(min = 1, max = 500) List<@Valid ImportRow> rows)`;
  - `ImportRow(@NotNull UUID id, @NotNull TransactionKind kind, @TransactionAmount Money amount, @NotNull LocalDate occurredOn, @Nullable @Size(max = MAX_DESCRIPTION_LENGTH) @Pattern(regexp = TextPatterns.WITHOUT_NUL) String description, @NotNull UUID categoryId, @NotNull @Pattern(regexp = "^[0-9a-f]{64}$") String importKey, @Nullable UUID confirmsPlannedId)`;
  - `kind` só `INCOME` ou `EXPENSE` (`TRANSFER` → 422 `import.invalid-kind`);
  - id que não é UUIDv7 → 422 `transaction.invalid-id`; id ou chave repetidos no mesmo lote → 422
    `import.duplicate-row`;
  - conta fora do escopo de quem chama, de outra família ou arquivada → 404 `account.not-found`;
  - os erros de linha que já existem (`transaction.category-not-found`,
    `transaction.category-kind-mismatch`, `transaction.currency-mismatch`,
    `transaction.date-out-of-range`) recusam o lote inteiro, sem gravar nada;
  - 200 `ImportResult(int created, int confirmed, int skipped)`.
- `POST /v1/workspaces/{ws}/imports/category-hints`, mesmos papéis e mesma guarda de plano:
  - corpo `CategoryHintsRequest(@NotNull @Size(min = 1, max = 500) List<@Valid CategoryHintItem> items)`,
    `CategoryHintItem(@NotNull TransactionKind kind, @NotNull @Size(max = 140) String key)`;
  - resposta `CategoryHintsResponse(List<CategoryHint> hints)` na ordem do pedido,
    `CategoryHint(@Nullable UUID categoryId)`;
  - busca os lançamentos confirmados, não excluídos, do escopo de quem chama (`AccountScope`), com
    categoria não arquivada, dos últimos 365 dias, do mais recente para o mais antigo; o Java
    normaliza cada descrição com `DescriptionKey.of` e fica com o primeiro de cada `(kind, chave)`.
    Chave vazia → sem categoria.

**D6. Gravação (`ImportService.importRows`), uma transação.**
1. Confere o lote (D5) e a conta (`accounts.choice(ws, accountId, scope)`, não arquivada).
2. Para cada linha, `TransactionService.checkImportRow(member, scope, accountId, row)` (nova,
   package-private, como `checkTemplate`): chama `checkAgainstFamily` com `memberId` = quem
   importa, sem forma de pagamento, sem `toAccountId`.
3. Com `confirmsPlannedId` e o módulo Planejamento ligado:
   - chave já usada (em `transactions` ou em `import_tombstones`) → `skipped`;
   - senão, `UPDATE transactions SET status = 'CONFIRMED', occurred_on = ?, amount = ?, member_id = ?, import_key = ?, version = version + 1, updated_at = now() WHERE workspace_id = ? AND id = ? AND account_id = ? AND kind = ? AND status = 'PLANNED' AND deleted_at IS NULL`
     com o filtro do escopo → 1 linha: `confirmed`;
   - 0 linhas (alguém confirmou, pulou ou excluiu antes) → segue para o passo 4.
4. `INSERT ... SELECT ... WHERE NOT EXISTS (SELECT 1 FROM import_tombstones WHERE workspace_id = ? AND import_key = ?) ON CONFLICT DO NOTHING RETURNING id`
   → linha devolvida: `created`; nenhuma: `skipped`. `status` confirmado, `member_id` e
   `created_by` = quem importa, `payment_method` nulo.
- Sem trava de conta: o índice único e a trava de linha do `UPDATE` resolvem duas importações ao
  mesmo tempo. Um reenvio depois de queda de rede conta as linhas já gravadas como `skipped`.
- Os nomes das colunas seguem o `TransactionRepository` (o implementador confere `status`,
  `deleted_at` e `version` lá).

**D7. Eventos.**
- **Desvio do spec:** em vez de um `TransactionChanged` por linha, um `AccountChanged(ws, accountId)`
  quando `created + confirmed > 0`. 500 eventos iriam um a um ao `LiveRelay` e ao ouvinte das metas;
  o `AccountChanged` já invalida no web as mesmas consultas (`account: [accounts, transactions,
  dashboard, planning]` em `shared/live/invalidation.ts`) e o ouvinte das metas já confere a conta.
- Sempre publica `StatementImported(UUID workspaceId, UUID accountId, UUID actorUserId, int created, int confirmed, int skipped)`.

**D8. Auditoria.** `audit/package-info` ganha `"ledger"` nas dependências. `AuditListeners` grava,
num `@EventListener` de `StatementImported` (dentro da transação, como os outros), a ação
`transactions.imported`, `targetType` `"ACCOUNT"` (acrescentado ao comentário de `AuditEntry`),
alvo = a conta, e as três contagens como texto em `metadata`. Sem valores nem descrições.

**D9. Normalização** (`normalizeDescription` no TS = `DescriptionKey.of` no Java, mesma tabela de
testes nos dois):
1. NFD e tira `\p{M}` (acentos e cedilha);
2. minúsculas (`Locale.ROOT` no Java);
3. tira parcelas: `\b(?:parc(?:ela)?\.?\s*)?\d{1,2}\s*(?:/|de)\s*\d{1,2}\b`;
4. troca `[^a-z0-9]+` por um espaço e tira os espaços das pontas.

| Entrada | Chave |
|---|---|
| `PADARIA SÃO JOÃO` | `padaria sao joao` |
| `  Uber   *Trip  ` | `uber trip` |
| `LOJA X PARC 03/10` | `loja x` |
| `Loja X parcela 3 de 10` | `loja x` |
| `LOJA X 03/10` | `loja x` |
| `Pgto. fatura—cartão` | `pgto fatura cartao` |
| (vazia) | (vazia) |
| `Ação 2025` | `acao 2025` |

**D10. `import_key`.**
- Texto: `${accountId}|${occurredOn}|${signed}|${normalized}|${n}`, com `occurredOn` em
  `aaaa-MM-dd`, `signed` = `centsToApi` do valor absoluto com `-` na frente para `EXPENSE` (pelo tipo
  final da linha, D12, para o OFX e o CSV do mesmo cartão darem a mesma chave), e `normalized` = D9
  da descrição já sem espaços nas pontas e cortada em 140.
- `n`: a ordem (de 0) da linha entre as do mesmo arquivo com o mesmo `(occurredOn, signed,
  normalized)`.
- Hash: `crypto.subtle.digest('SHA-256', UTF-8)`, em hex minúsculo.
- Vetor fixo: `0192f5a0-0000-7000-8000-000000000001|2026-11-03|-45.90|padaria pao quente|0` →
  `11761bb90289f327bb7434f27739e1c45d3b89f7c66311d810e433a290153f15`.

**D11. Leitores.**
- Arquivo até 5 MB e 2.000 lançamentos; mais que isso → "Este arquivo é grande demais. Exporte um
  período menor." O envio vai em lotes de 500, um depois do outro. Se um lote falha, a tela diz
  quantos entraram e oferece tentar de novo (a importação é idempotente).
- **Codificação:** OFX pelo cabeçalho (`CHARSET:1252` ou `ENCODING:` no SGML, `encoding="..."` no
  XML), padrão windows-1252 no SGML e UTF-8 no XML; CSV tenta `TextDecoder('utf-8', { fatal: true })`
  e cai para `windows-1252`. O BOM sai.
- **OFX:** um leitor tolerante serve aos dois: blocos `<STMTTRN>`, campos por `<(\w+)>([^<\r\n]*)`;
  `DTPOSTED` pelos 8 primeiros dígitos; `TRNAMT` com ponto ou vírgula; descrição = `NAME` e `MEMO`
  juntos com " - " quando os dois existem e são diferentes, senão o que houver; entidades
  (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`) decodificadas; `FITID` ignorado. Sem `<OFX>` ou sem
  `<STMTTRN>` → erro de leitura.
- **CSV:** aspas com `""` escapado; separador = o de `;`, `,` e tab que dá o mesmo número de colunas
  (≥ 3) na maioria das 20 primeiras linhas não vazias; linhas de título antes do cabeçalho são
  puladas; cabeçalho = a primeira linha com essa contagem que não tem data na coluna de data.
  - Data: a coluna em que ≥ 90% das linhas de dado casam `dd/MM/aaaa` ou `aaaa-MM-dd`.
  - Valor pelo nome do cabeçalho, sem acento e em minúsculas: `valor`, `amount`, `value` (uma coluna
    com sinal); `debito`, `saida` e `credito`, `entrada` (duas colunas). Coluna com `saldo` nunca é
    valor.
  - Decimal: vírgula se algum valor casa `\d,\d{2}$`; o milhar e o `R$` saem.
  - Descrição: `descricao`, `historico`, `lancamento`, `memo`, `description`; senão a coluna de texto
    com mais valores distintos.
  - Falta de certeza (data, valor ou descrição sem coluna clara, ou mais de uma candidata) →
    `needsMapping`.

**D12. Tipos.**
- Conta comum: valor negativo → `EXPENSE`, positivo → `INCOME`, zero sai da lista.
- Cartão (`CREDIT_CARD`): o sinal mais comum entre as linhas é o das compras (empate → negativo).
  Compras → `EXPENSE`, marcadas. Sinal oposto → `INCOME`, desmarcadas, com o aviso de pagamento da
  fatura (texto exato nas Global Constraints).
- O valor vai sempre em módulo; o tipo leva o sentido.

**D13. Prévia.**
- `TransactionView` ganha `importKey` (nulável) no OpenAPI e no `schema.ts`.
- `transactionsQuery` em `shared/api/hooks.ts` passa a ser exportada; a prévia busca os meses de
  (menor data − 5 dias) a (maior data + 5 dias) com o filtro da conta.
- Cada linha recebe um estado, nesta ordem de precedência:
  1. `alreadyImported`: a chave já está num lançamento (excluídos não vêm na lista, o servidor pula
     esses) → desmarcada, selo "Já importado";
  2. `confirmPlanned` (só com Planejamento ligado): previsto da mesma conta, mesmo tipo e mesmo
     valor, data a até 5 dias; pares escolhidos pela menor diferença de dias, empate pela ordem da
     linha no arquivo; cada previsto casa com uma linha só → marcada, selo "Confirmar o previsto";
  3. `possibleRepeat`: confirmado, sem `importKey`, mesma data, valor, tipo e descrição normalizada →
     desmarcada, selo "Possível repetido";
  4. `billPayment` (D12) → desmarcada, com o aviso;
  5. `normal` → marcada.
- Categoria sugerida por `(kind, chave normalizada)` pela rota de dicas. "Usar a mesma categoria nas
  N sem categoria" preenche as marcadas sem categoria do mesmo tipo.
- "Importar N lançamentos" fica desligado com 0 marcadas, com alguma marcada sem categoria, ou sem
  rede.

**D14. Telas.**
- Rota sob demanda `/lancamentos/importar` (`features/import/ImportPage.tsx`), em passos: conta e
  arquivo (`accept=".ofx,.csv,.txt"`) → colunas (só quando `needsMapping`) → prévia → resultado
  ("{created} entraram, {confirmed} confirmaram um previsto, {skipped} já existiam", com plural do
  ICU).
- O botão "Importar extrato" em `TransactionsPage` aparece quando o papel não é `VIEWER` e
  `importAllowed` é `true`. Abrir a rota direto sem isso → `Navigate to="/lancamentos" replace`.
- Conta: as ativas que a pessoa pode usar (para o Jovem, as do escopo dele).
- Erros: "Não conseguimos ler este arquivo. Ele precisa ser OFX ou CSV exportado pelo banco." e
  "Nenhum lançamento no arquivo."
- A string do botão vai em `features/transactions/more.messages.pt-BR.ts` (pelo
  `import './more.register'`); o resto em `features/import/messages.pt-BR.ts`, registrado pelo
  chunk da importação.

**D15. Política de privacidade.** Versões `privacy-2026-11.4` e `privacy-2026-12.4`: cópia da 11.3 e
da 12.3 com o parágrafo abaixo e a data nova. Só aviso, sem novo aceite: `KEEPS_ACCEPTANCE` ganha
`privacy-2026-11.4 → privacy-2026-11.3` e `privacy-2026-12.4 → privacy-2026-12.3`;
`LegalDocuments` passa a apontar as duas novas; `legalConfig.latest.privacy` vira
`privacy-2026-12.4`. Parágrafo:

> **Importação de extrato.** Quando você importa um extrato (OFX ou CSV), o arquivo é lido no seu
> aparelho e não é enviado nem guardado por nós. Só os lançamentos que você confirma vão para o
> Liberdade, como qualquer lançamento. Para não repetir um lançamento que você excluiu, guardamos por
> 13 meses um código calculado a partir da linha do extrato (um hash), sem valor, data nem descrição.
> Esse código é apagado junto com a conta bancária ou com a família.

**D16. Exportação, docs, e2e e papéis.**
- Exportação: `FORMAT_VERSION` 5 em `ExportService` e `@Nullable String importKey` em
  `ExportedTransaction`. Os tombstones não entram (não identificam nada). A 8.3 sobe para 6 com as
  conquistas.
- Docs: `docs/data-model.md` (V19, `import_key`, `import_tombstones` na retenção) e
  `docs/architecture.md` (o extrato é lido no aparelho; as duas rotas).
- E2E: `web/e2e/import.spec.ts` (Task 9).
- `RoleMatrixIT`: as duas rotas, Leitor 403. `YouthScopeIT`: o Jovem importa na conta dele e
  recebe 404 na de outro; as dicas não usam lançamentos de contas fora do escopo dele.

## Review Focus

1. **Arquivo do Windows lido como UTF-8:** um CSV em windows-1252 com "Ç" e "ã" decodificado
   errado vira chave diferente da do OFX e duplica. Testes: `decode.test.ts › csvFallsBackTo1252`
   e `importKey.test.ts › sameKeyFromOfxAndCsv` (Tasks 5 e 6).
2. **Sinal do cartão no empate e no estorno:** um cartão com tantas compras quanto estornos, ou uma
   fatura só de pagamento. Testes: `kinds.test.ts › cardTieMeansNegativePurchases` e
   `kinds.test.ts › cardOppositeSignComesUncheckedAsIncome` (Task 6).
3. **Reenvio depois de queda de rede:** o mesmo lote duas vezes conta tudo como `skipped` e cria
   nada; um lote com 501 linhas é recusado. Testes: `ImportIT.resendCountsAsSkipped` e
   `ImportIT.rejects501Rows` (Task 3).
4. **Previsto confirmado por outra pessoa durante a importação:** a linha vira lançamento novo, sem
   erro. Teste: `ImportIT.plannedNoLongerPlannedCreatesNew` (Task 3).
5. **CSV com descrição que parece data ou parcela:** "PIX 03/10" na descrição não pode virar a coluna
   de data nem quebrar a chave. Testes: `csvDetect.test.ts › dateColumnNeedsNinetyPercent` e
   `normalize.test.ts › vectors` (Tasks 6 e 7).

## Mapa de arquivos

Caminhos da API relativos a `api/src/main/java/br/com/liberdadefamilia/` (testes em
`api/src/test/java/br/com/liberdadefamilia/`); do web, relativos a `web/src/`.

| Arquivo | Task | Papel |
|---|---|---|
| `api/src/main/resources/db/migration/V19__statement_import.sql` | 1 | coluna, índice único e tombstones |
| `ledger/internal/TransactionRepository.java`, `TransactionPurgeJob.java`, `ImportRepository.java` | 1 | cópia para os tombstones e limpeza de 13 meses |
| `ledger/domain/DescriptionKey.java` | 2 | normalização |
| `ledger/internal/{ImportController,ImportService,CategoryHintsRequest,CategoryHintItem,CategoryHintsResponse,CategoryHint}.java` | 2 | rota das dicas |
| `billing/RequiresPlan.java`, `billing/internal/{PlanGuardInterceptor,PlanDetails,PlanDetailsService}.java` | 2 | guarda com a cobrança desligada e `importAllowed` |
| `ledger/internal/{ImportRequest,ImportRow,ImportResult}.java`, `TransactionService.java`, `TransactionView.java`, `ledger/StatementImported.java` | 3 | rota da importação |
| `audit/package-info.java`, `audit/internal/AuditListeners.java`, `audit/internal/AuditEntry.java` | 3 | auditoria |
| `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`, `ledger/YouthScopeIT.java` | 2, 3 | contrato e papéis |
| `privacy/internal/ExportService.java`, `ledger/ExportedTransaction.java` | 4 | exportação v5 |
| `privacy/internal/LegalDocuments.java`, `features/legal/texts/privacy-2026-1{1,2}.4.ts`, `features/legal/legalConfig.ts` | 4 | política |
| `docs/data-model.md`, `docs/architecture.md` | 4 | docs |
| `features/import/readers/{decode,ofx,csv}.ts`, `features/import/fixtures/*`, `.gitattributes` | 5 | leitores e exemplos |
| `features/import/{normalize,importKey,kinds}.ts` | 6 | chave e tipos |
| `features/import/{csvDetect,csvMappingStore}.ts`, `features/import/ColumnMapping.tsx` | 7 | detecção e colunas |
| `features/import/preview.ts`, `features/import/useImport.ts`, `shared/api/hooks.ts`, `shared/api/endpoints.ts` | 8 | prévia e envio |
| `features/import/{ImportPage,ImportPreview,ImportResult}.tsx`, `messages.pt-BR.ts`, `register.ts`, `app/routes.tsx`, `features/transactions/{TransactionsPage.tsx,more.messages.pt-BR.ts}` | 9 | telas e rota |
| `mocks/handlers.ts`, `mocks/data.ts` | 9 | MSW |
| `web/e2e/import.spec.ts`, `web/e2e/a11y.spec.ts` | 9 | e2e |

## Tasks

### Task 1: V19, os tombstones e a limpeza de 13 meses

**Files:**
- Create: `db/migration/V19__statement_import.sql`, `ledger/internal/ImportRepository.java`.
- Modify: `ledger/internal/TransactionRepository.java` (`purgeDeletedBefore`),
  `ledger/internal/TransactionPurgeJob.java`.
- Test: `ledger/ImportSchemaIT.java`, `ledger/TransactionPurgeJobIT.java`.

**Interfaces:**
- Consumes: nada novo.
- Produces:
  - a coluna `transactions.import_key` e a tabela `import_tombstones` (D2);
  - `ImportRepository` (`@Repository`, package-private) com
    `boolean keyTaken(UUID workspaceId, String importKey)` (em `transactions`, inclusive excluídos,
    ou em `import_tombstones`) e `int purgeTombstonesBefore(Instant cutoff)`.

**Como fica:** D2 e D3. Nenhuma rota muda ainda.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ImportSchemaIT.sameKeyTwiceInAFamilyFails` | duas linhas com a mesma chave na mesma família → violação de unicidade, também quando a primeira está excluída |
| `ImportSchemaIT.sameKeyInTwoFamiliesIsFine` | a mesma chave em famílias diferentes → as duas gravam |
| `ImportSchemaIT.keyMustBe64Hex` | `'ABC'` ou hex maiúsculo → violação do `CHECK` |
| `ImportSchemaIT.tombstonesGoWithTheAccount` | apagar a conta apaga os tombstones dela |
| `TransactionPurgeJobIT.purgeCopiesTheImportKey` | excluído há 31 dias com chave → some de `transactions` e a chave aparece em `import_tombstones` com a conta dele |
| `TransactionPurgeJobIT.purgeWithoutKeyLeavesNoTombstone` | excluído sem chave → nenhum tombstone |
| `TransactionPurgeJobIT.oldTombstonesGo` | tombstone com `purged_at` de 13 meses e 1 dia atrás → some; de 12 meses e 29 dias → fica |
| `TransactionPurgeJobIT.keyTakenSeesTombstones` | `keyTaken` → `true` para chave só no tombstone, `true` para excluído ainda não apagado, `false` para outra família |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ImportSchemaIT' --tests '*TransactionPurgeJobIT'`
Expected: FAIL (coluna e tabela não existem).

- [ ] **Step 3: Implementar.** A migração (D2), a CTE em `purgeDeletedBefore` e a limpeza de 13
  meses no job (D3), com o `Clock` e `ZoneId.of("America/Sao_Paulo")`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(ledger): add the v19 import key, the import tombstones and their purge"
```

---

### Task 2: A normalização, a guarda do plano e a rota das dicas

**Files:**
- Create: `ledger/domain/DescriptionKey.java`, `ledger/internal/{ImportController,ImportService,CategoryHintsRequest,CategoryHintItem,CategoryHintsResponse,CategoryHint}.java`.
- Modify: `ledger/internal/ImportRepository.java`, `billing/RequiresPlan.java`,
  `billing/internal/PlanGuardInterceptor.java`, `billing/internal/PlanDetails.java`,
  `billing/internal/PlanDetailsService.java`, `api/openapi.json`, `web/src/shared/api/schema.ts`.
- Test: `ledger/domain/DescriptionKeyTest.java`, `ledger/CategoryHintsIT.java`,
  `billing/internal/PlanGuardIT.java`, `RoleMatrixIT.java`,
  `ledger/YouthScopeIT.java`.

**Interfaces:**
- Consumes: `ImportRepository` (Task 1).
- Produces:
  - `DescriptionKey.of(@Nullable String description): String` (D9; `null` → `""`);
  - `RequiresPlan.openWhileBillingOff()` e `PlanDetails.importAllowed` (D4);
  - `ImportController` com a rota das dicas (D5) e `ImportService.categoryHints(WorkspaceMember member, CategoryHintsRequest request): CategoryHintsResponse`;
  - `ImportRepository.recentCategorized(UUID workspaceId, AccountScope scope, LocalDate since): List<CategorizedRow>`
    com `record CategorizedRow(TransactionKind kind, @Nullable String description, UUID categoryId)`,
    do mais recente para o mais antigo.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `DescriptionKeyTest.vectors` | a tabela de D9, linha por linha |
| `DescriptionKeyTest.nullIsEmpty` | `null` → `""` |
| `CategoryHintsIT.newestMatchWins` | "Padaria São João" com Alimentação há 10 dias e com Lazer há 2 dias; pedido `(EXPENSE, "padaria sao joao")` → Lazer |
| `CategoryHintsIT.kindMatters` | a mesma descrição como `INCOME` → `null` para `EXPENSE` |
| `CategoryHintsIT.ignoresDeletedPlannedArchivedAndOld` | excluído, previsto, categoria arquivada e 366 dias atrás → `null` |
| `CategoryHintsIT.keepsTheOrderOfTheRequest` | três itens, o do meio sem histórico → `[id, null, id]` |
| `CategoryHintsIT.rejects501Items` | 501 itens → 400 |
| `PlanGuardIT.importOpenWhileBillingOff` | venda desligada, plano FREE → a rota das dicas responde 200 |
| `PlanGuardIT.importNeedsFamilyWhileBillingOn` | venda ligada, plano FREE → 403 `plan.required`; plano FAMILY → 200 |
| `PlanGuardIT.otherPlanRoutesUnchanged` | a rota do tema segue 403 `plan.required` com FREE e a venda desligada |
| `PlanDetails` (IT da cobrança que já existe) | `importAllowed` = `true` com a venda desligada; com a venda ligada, `false` no FREE e `true` no FAMILY |
| `RoleMatrixIT` | `POST /imports/category-hints`: OWNER, ADULT, YOUTH → 200; VIEWER → 403 |
| `YouthScopeIT.hintsIgnoreAccountsOutsideTheScope` | categoria só num lançamento da conta de um adulto → `null` para o Jovem |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*DescriptionKeyTest' --tests '*CategoryHintsIT' --tests '*PlanGuardIT' --tests '*RoleMatrixIT' --tests '*YouthScopeIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar.** `DescriptionKey` com `Normalizer.Form.NFD` e os padrões de D9
  compilados uma vez. A guarda e a flag (D4). A rota (D5). O OpenAPI e o `schema.ts` gerados como
  na 8.1 (`./gradlew` gera o `openapi.json`; o `schema.ts` sai do script do web que já existe).

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL;
  `corepack pnpm --dir web typecheck` → sem erros.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(ledger): add the description key, the plan gate for imports and the category hints route"
```

---

### Task 3: A rota da importação, os eventos e a auditoria

**Files:**
- Create: `ledger/internal/{ImportRequest,ImportRow,ImportResult}.java`, `ledger/StatementImported.java`.
- Modify: `ledger/internal/ImportController.java`, `ImportService.java`, `ImportRepository.java`,
  `TransactionService.java` (`checkImportRow`), `TransactionView.java` (+ `importKey`) e a consulta
  que a monta, `audit/package-info.java`, `audit/internal/AuditListeners.java`,
  `audit/internal/AuditEntry.java` (comentário de `targetType`), `api/openapi.json`,
  `web/src/shared/api/schema.ts`.
- Test: `ledger/ImportIT.java`, `RoleMatrixIT.java`, `ledger/YouthScopeIT.java`,
  `audit/AuditIT.java` (ou o IT de auditoria que já existe), `ApplicationModulesTest`.

**Interfaces:**
- Consumes: `ImportRepository.keyTaken` (Task 1), `ImportController`/`ImportService` (Task 2).
- Produces:
  - `ImportService.importRows(WorkspaceMember member, UUID accountId, ImportRequest request): ImportResult` (D6);
  - `TransactionService.checkImportRow(WorkspaceMember member, AccountScope scope, UUID accountId, ImportRow row)`;
  - `ImportRepository.confirmPlanned(...)`: `boolean` e `ImportRepository.insertImported(...)`: `boolean` (D6);
  - `record StatementImported(UUID workspaceId, UUID accountId, UUID actorUserId, int created, int confirmed, int skipped)`;
  - `TransactionView.importKey` (`@Nullable String`).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ImportIT.createsConfirmedRows` | 3 linhas → `{3, 0, 0}`; os três confirmados, `member_id` e `created_by` = quem importou, `import_key` gravada, saldo da conta muda |
| `ImportIT.secondImportCreatesNothing` | o mesmo lote com ids novos → `{0, 0, 3}` |
| `ImportIT.resendCountsAsSkipped` | o mesmo lote com os mesmos ids → `{0, 0, 3}` |
| `ImportIT.overlappingPeriods` | lote A (dias 1 a 10) e lote B (dias 5 a 15) com chaves iguais nos dias 5 a 10 → B só cria os dias 11 a 15 |
| `ImportIT.deletedRowDoesNotComeBack` | importa, exclui uma → reimportar dá `skipped` para ela |
| `ImportIT.purgedRowDoesNotComeBack` | importa, exclui, roda o job 31 dias depois → reimportar dá `skipped` (tombstone) |
| `ImportIT.movedRowDoesNotComeBack` | importa, move a linha para outra conta → reimportar na conta original dá `skipped` |
| `ImportIT.confirmsThePlanned` | previsto de 120,00 no dia 10; linha de 118,50 no dia 12 com `confirmsPlannedId` → `{0, 1, 0}`; o previsto fica confirmado com 118,50, dia 12, a chave e `version + 1`; nenhuma linha nova |
| `ImportIT.plannedNoLongerPlannedCreatesNew` | previsto pulado antes do envio → `{1, 0, 0}` |
| `ImportIT.plannedWithTakenKeyIsSkipped` | chave já importada e `confirmsPlannedId` → `{0, 0, 1}`, o previsto segue previsto |
| `ImportIT.plannedIgnoredWithPlanningOff` | Planejamento desligado → a linha é criada nova, o previsto fica |
| `ImportIT.rejects501Rows` | 501 linhas → 400 |
| `ImportIT.duplicateKeyInBatch` | duas linhas com a mesma chave → 422 `import.duplicate-row`, nada gravado |
| `ImportIT.transferRejected` | `kind = TRANSFER` → 422 `import.invalid-kind` |
| `ImportIT.badRowRejectsTheBatch` | uma linha com categoria de outro tipo → 422 `transaction.category-kind-mismatch`, nada gravado |
| `ImportIT.archivedAccountIsNotFound` | conta arquivada → 404 `account.not-found` |
| `ImportIT.publishesOneAccountChanged` | 3 criadas → um `AccountChanged` e um `StatementImported(3, 0, 0)`; tudo `skipped` → nenhum `AccountChanged`, um `StatementImported(0, 0, 3)` |
| `ImportIT.logsCarryNoValues` | o log da rota (capturado) não contém o valor nem a descrição enviados |
| `ImportIT.viewCarriesTheKey` | `GET /transactions` traz `importKey` na linha importada e `null` nas outras |
| `AuditIT.importIsAudited` | uma linha `transactions.imported`, `ACCOUNT`, a conta, `{created: "3", confirmed: "0", skipped: "0"}` |
| `RoleMatrixIT` | `POST /accounts/{id}/imports`: OWNER, ADULT → 200; YOUTH na conta dele → 200; VIEWER → 403 |
| `YouthScopeIT.youthImportsOnlyOnOwnAccounts` | o Jovem na conta de um adulto → 404 `account.not-found` |
| `ApplicationModulesTest` | passa com `audit → ledger` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ImportIT' --tests '*RoleMatrixIT' --tests '*YouthScopeIT' --tests '*AuditIT' --tests '*ApplicationModulesTest'`
Expected: FAIL.

- [ ] **Step 3: Implementar.** D5 a D8. O id de cada linha passa pela mesma checagem de UUIDv7 da
  rota de lançamentos. O módulo Planejamento é lido pelo mesmo serviço que o `@RequiresModule` usa.
  Logs da rota: só a conta e as contagens.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL;
  `corepack pnpm --dir web typecheck` → sem erros.

- [ ] **Step 6: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(ledger): add the statement import route with planned matching and audit"
```

---

### Task 4: Exportação v5, a política de privacidade e os docs

**Files:**
- Create: `web/src/features/legal/texts/privacy-2026-11.4.ts`, `privacy-2026-12.4.ts`.
- Modify: `privacy/internal/ExportService.java`, `ledger/ExportedTransaction.java` e a consulta que o
  monta, `privacy/internal/LegalDocuments.java`, `web/src/features/legal/legalConfig.ts`,
  `docs/data-model.md`, `docs/architecture.md`.
- Test: o IT da exportação que já existe, o teste de `LegalDocuments` que já existe,
  `web/src/features/legal/legalConfig.test.ts` (ou o teste da política que já existe).

**Interfaces:**
- Consumes: `transactions.import_key` (Task 1).
- Produces: exportação com `formatVersion: 5`; `privacy-2026-12.4` como política vigente.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| IT da exportação `.exportsImportKey` | `formatVersion` 5; a linha importada traz `importKey`, a outra `null` |
| teste de `LegalDocuments` `.newPrivacyKeepsAcceptance` | quem aceitou a 12.3 (ou a 11.3) não precisa aceitar de novo e é avisado |
| teste do web da política | `latest.privacy === 'privacy-2026-12.4'`; o texto da 12.4 contém "Importação de extrato" e "13 meses" |

- [ ] **Step 2: Rodar e ver falhar.** Run: `./gradlew test --tests '*Export*' --tests '*LegalDocuments*'`
  (em `api/`) e `corepack pnpm --dir web test -- legal`. Expected: FAIL.

- [ ] **Step 3: Implementar.** D15 e D16 (exportação e docs). Os textos 11.4 e 12.4 são cópia exata
  da 11.3 e da 12.3 com o parágrafo de D15 depois do parágrafo da coleta do Banco Central e a data
  nova.

- [ ] **Step 4: Rodar e ver passar.** Mesmos comandos. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` e `corepack pnpm --dir web typecheck` e `lint`.

- [ ] **Step 6: Commit**

```bash
git add api web docs
git commit -m "feat(privacy): export v5 with the import key and the privacy text for statement import"
```

---

### Task 5: Os leitores e os arquivos de exemplo

**Files:**
- Create: `features/import/readers/{decode,ofx,csv}.ts`, `features/import/readers/types.ts`,
  `features/import/fixtures/{ofx-sgml-1252.ofx,ofx-xml-utf8.ofx,csv-semicolon-comma-decimal.csv,csv-debit-credit-columns-1252.csv,csv-card-positive-purchases.csv,ofx-card-negative-purchases.ofx}`.
- Modify: `.gitattributes` (`web/src/features/import/fixtures/** -text`).
- Test: `features/import/readers/{decode,ofx,csv}.test.ts`.

**Interfaces:**
- Consumes: nada.
- Produces:
  - `decode(bytes: Uint8Array, hint: 'ofx' | 'csv'): string` (D11);
  - `type StatementLine = { occurredOn: string /* aaaa-MM-dd */; cents: bigint /* com sinal */; description: string }`;
  - `readOfx(text: string): StatementLine[]` (lança `StatementReadError` quando não é OFX);
  - `parseCsv(text: string): { separator: ';' | ',' | '\t'; rows: string[][] }`;
  - `class StatementReadError extends Error` com `reason: 'unreadable' | 'empty' | 'tooLarge'`.

**Como fica:**
- Os seis arquivos são escritos à mão (D11, Global Constraints). `ofx-sgml-1252.ofx` (cabeçalho
  `CHARSET:1252`, sem fechar as tags) e `csv-semicolon-comma-decimal.csv` (duas linhas de título,
  coluna de saldo, `;`, vírgula decimal, UTF-8) trazem **os mesmos quatro lançamentos**, um deles
  com "ç" e "ã" e dois idênticos no mesmo dia. `csv-debit-credit-columns-1252.csv` usa colunas
  Débito e Crédito em windows-1252. `csv-card-positive-purchases.csv` traz compras positivas e um
  pagamento negativo; `ofx-card-negative-purchases.ofx`, compras negativas e um pagamento positivo.
  `ofx-xml-utf8.ofx` é OFX 2.x com `NAME` e `MEMO`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `decode.test.ts › ofxHeaderSays1252` | os bytes de `ofx-sgml-1252.ofx` → texto com "ç" certo |
| `decode.test.ts › csvFallsBackTo1252` | `csv-debit-credit-columns-1252.csv` → "ç" certo (o UTF-8 estrito falha e cai para 1252) |
| `decode.test.ts › stripsTheBom` | UTF-8 com BOM → o texto não começa com `﻿` |
| `ofx.test.ts › readsSgml` | quatro linhas; datas, centavos com sinal e descrições esperadas |
| `ofx.test.ts › readsXml` | `NAME` e `MEMO` diferentes → `"NAME - MEMO"`; iguais → um só |
| `ofx.test.ts › commaAmountAndEntities` | `TRNAMT` `-12,34` → `-1234n`; `&amp;` → `&` |
| `ofx.test.ts › notOfxIsUnreadable` | um CSV → `StatementReadError('unreadable')` |
| `csv.test.ts › quotedFields` | `"a;b";"c""d"` → `['a;b', 'c"d']` |
| `csv.test.ts › findsTheSeparator` | `;`, `,` e tab em três textos → o separador certo |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test -- features/import/readers`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** D11. O limite de 5 MB é conferido na tela (Task 9); o de 2.000 linhas
  aqui (`tooLarge`).

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `git ls-files --eol web/src/features/import/fixtures` mostra `-text` e
  os bytes do 1252 intactos; as duas buscas de dados pessoais saem com 1.

- [ ] **Step 6: Commit**

```bash
git add .gitattributes web/src/features/import
git commit -m "feat(web): add the ofx and csv statement readers with sample files"
```

---

### Task 6: Normalização, `import_key` e tipos

**Files:**
- Create: `features/import/{normalize,importKey,kinds}.ts`.
- Test: `features/import/{normalize,importKey,kinds}.test.ts`.

**Interfaces:**
- Consumes: `StatementLine`, `readOfx` (Task 5).
- Produces:
  - `normalizeDescription(text: string): string` (D9);
  - `type KindedLine = StatementLine & { kind: 'INCOME' | 'EXPENSE'; absCents: bigint; billPayment: boolean }`;
  - `assignKinds(lines: StatementLine[], isCard: boolean): KindedLine[]` (D12, tira as de valor zero);
  - `importKeys(accountId: string, lines: KindedLine[]): Promise<string[]>` (D10, na ordem das linhas).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `normalize.test.ts › vectors` | a tabela de D9, linha por linha |
| `importKey.test.ts › fixedVector` | conta `0192f5a0-0000-7000-8000-000000000001`, 2026-11-03, `EXPENSE` 4590, "Padaria Pão Quente" → `11761bb9…3f15` (D10) |
| `importKey.test.ts › identicalLinesGetN` | duas linhas iguais no mesmo dia → chaves diferentes; trocar a ordem do arquivo não muda o conjunto |
| `importKey.test.ts › sameKeyFromOfxAndCsv` | `ofx-sgml-1252.ofx` e as mesmas linhas do `csv-semicolon-comma-decimal.csv` (lidas com o mapeamento esperado) → as mesmas quatro chaves |
| `importKey.test.ts › cardKeysMatchAcrossConventions` | uma compra de 45,90 com sinal `+` num cartão e `−` noutro → mesma chave |
| `kinds.test.ts › bySignOutsideCards` | −10 → `EXPENSE`, +10 → `INCOME`, 0 sai |
| `kinds.test.ts › cardPositivePurchases` | `csv-card-positive-purchases.csv` → as compras `EXPENSE`, o pagamento `INCOME` com `billPayment` |
| `kinds.test.ts › cardTieMeansNegativePurchases` | duas positivas e duas negativas → as negativas são `EXPENSE` |
| `kinds.test.ts › cardOppositeSignComesUncheckedAsIncome` | `ofx-card-negative-purchases.ofx` → o pagamento positivo vira `INCOME`, `billPayment: true` |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test -- features/import`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** D9, D10 e D12. O `sameKeyFromOfxAndCsv` usa o leitor de CSV com um
  mapeamento explícito até a Task 7 existir; a Task 7 troca pelo detectado.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/import
git commit -m "feat(web): add description normalization, the import key and card kinds"
```

---

### Task 7: Detecção do CSV, a tela de colunas e o mapeamento guardado

**Files:**
- Create: `features/import/{csvDetect,csvMappingStore}.ts`, `features/import/ColumnMapping.tsx`.
- Test: `features/import/{csvDetect,csvMappingStore}.test.ts`, `ColumnMapping.test.tsx`.

**Interfaces:**
- Consumes: `parseCsv`, `StatementLine` (Task 5).
- Produces:
  - `type CsvMapping = { headerRow: number; date: number; description: number; amount: { kind: 'signed'; column: number } | { kind: 'split'; debit: number; credit: number }; decimal: ',' | '.' }`;
  - `detectCsv(parsed: { separator; rows }): { mapping: CsvMapping; certain: boolean; signature: string }`;
  - `linesFromCsv(rows: string[][], mapping: CsvMapping): StatementLine[]` (débito vira negativo);
  - `loadMapping(accountId: string, signature: string): CsvMapping | null` e
    `saveMapping(accountId: string, signature: string, mapping: CsvMapping): void` na chave
    `liberdade.import.csvMapping.v1` (`{ [accountId]: { [signature]: CsvMapping } }`), com
    `try/catch` em toda leitura e escrita;
  - `<ColumnMapping rows={string[][]} initial={CsvMapping} onConfirm={(m: CsvMapping) => void} />`.

**Como fica:**
- Assinatura do cabeçalho: as células do cabeçalho normalizadas (D9) unidas por `|`, mais `|` e o
  separador; sem cabeçalho, `#cols:N|<separador>`.
- O mapeamento guardado vence a detecção. Ele é gravado quando a pessoa confirma a prévia.
- A tela de colunas: um seletor por papel (Data, Descrição, Valor, ou Débito e Crédito), as cinco
  primeiras linhas como amostra e "Continuar" desligado até data, descrição e valor terem coluna.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `csvDetect.test.ts › semicolonCommaDecimal` | `csv-semicolon-comma-decimal.csv` → certo; pula os títulos; ignora o saldo; vírgula decimal |
| `csvDetect.test.ts › debitCreditColumns` | `csv-debit-credit-columns-1252.csv` → `split`, certo; débito vira negativo |
| `csvDetect.test.ts › isoDatesAndDot` | `aaaa-MM-dd` e ponto decimal → certo |
| `csvDetect.test.ts › dateColumnNeedsNinetyPercent` | coluna de descrição com "PIX 03/10" em parte das linhas não vira a coluna de data |
| `csvDetect.test.ts › unknownHeaderNeedsMapping` | cabeçalho sem nome de valor conhecido e duas colunas numéricas → `certain: false` |
| `csvDetect.test.ts › stripsCurrencyAndThousands` | `"R$ 1.234,56"` → `123456n` |
| `csvMappingStore.test.ts › perAccountAndSignature` | salvo para a conta A e assinatura S → lido em A+S, `null` em B+S e em A+T |
| `csvMappingStore.test.ts › survivesBrokenStorage` | `localStorage` que lança → `null` e nenhum erro |
| `ColumnMapping.test.tsx › continueNeedsAllColumns` | sem valor escolhido → "Continuar" desligado; escolhendo → `onConfirm` com o mapeamento |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test -- features/import`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** D11 (CSV). Troca o mapeamento explícito do teste da Task 6 pelo
  detectado.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/import
git commit -m "feat(web): detect csv columns and remember the mapping per account"
```

---

### Task 8: A prévia e o envio

**Files:**
- Create: `features/import/preview.ts`, `features/import/useImport.ts`.
- Modify: `shared/api/hooks.ts` (exporta `transactionsQuery`), `shared/api/endpoints.ts`
  (`importStatement`, `categoryHints`).
- Test: `features/import/preview.test.ts`, `features/import/useImport.test.tsx`.

**Interfaces:**
- Consumes: `KindedLine`, `importKeys`, `normalizeDescription` (Task 6); `TransactionView` com
  `importKey` (Task 3); as rotas das Tasks 2 e 3.
- Produces:
  - `type PreviewState = 'alreadyImported' | 'confirmPlanned' | 'possibleRepeat' | 'billPayment' | 'normal'`;
  - `type PreviewLine = KindedLine & { id: string; importKey: string; state: PreviewState; checked: boolean; categoryId: string | null; confirmsPlannedId: string | null }`;
  - `buildPreview(lines: KindedLine[], keys: string[], existing: TransactionView[], planningOn: boolean, newId: () => string): PreviewLine[]` (D13);
  - `previewRange(lines: KindedLine[]): { from: string; to: string }` (± 5 dias);
  - `useImport(ws: string, accountId: string)` com `send(lines: PreviewLine[]): Promise<ImportTotals>`,
    lotes de 500 em sequência, `ImportTotals = { created: number; confirmed: number; skipped: number; failedAfter: number | null }`,
    e que invalida as consultas de lançamentos, contas, painel e planejamento no fim.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `preview.test.ts › alreadyImportedWins` | linha cuja chave está num lançamento e que também bate com um previsto → `alreadyImported`, desmarcada |
| `preview.test.ts › confirmsTheClosestPlanned` | previsto no dia 10; linhas iguais nos dias 8 e 11 → a do dia 11 casa (`confirmsPlannedId`), a do dia 8 fica `normal` |
| `preview.test.ts › plannedOnlyWithinFiveDays` | previsto no dia 10 e linha no dia 16 → `normal` |
| `preview.test.ts › plannedNeedsSameKindAndAmount` | valor ou tipo diferente → não casa |
| `preview.test.ts › noPlannedMatchWithPlanningOff` | `planningOn: false` → nenhuma `confirmPlanned` |
| `preview.test.ts › possibleRepeatOnlyWithoutKey` | lançamento confirmado sem `importKey`, mesma data, valor, tipo e descrição normalizada → `possibleRepeat`, desmarcada; com `importKey` diferente → `normal` |
| `preview.test.ts › billPaymentUnchecked` | `billPayment: true` → estado `billPayment`, desmarcada |
| `preview.test.ts › rangeIsFiveDaysWide` | linhas de 2026-11-03 a 2026-11-28 → `{ from: '2026-10-29', to: '2026-12-03' }` |
| `useImport.test.tsx › sendsInBatchesOf500` | 1.200 linhas marcadas → três chamadas (500, 500, 200), totais somados |
| `useImport.test.tsx › stopsAtTheFailedBatch` | o segundo lote falha → `failedAfter: 500`, sem a terceira chamada |
| `useImport.test.tsx › sendsOnlyCheckedRows` | desmarcadas não vão; `description` cortada em 140; valor em módulo |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test -- features/import`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** D13. Ids com `v7()` de `uuid` (o mesmo do `TransactionForm`). O envio
  usa o `client` tipado direto, nunca a fila offline.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src
git commit -m "feat(web): build the import preview with repeat and planned matching"
```

---

### Task 9: As telas, a rota, o MSW e o e2e

**Files:**
- Create: `features/import/{ImportPage,ImportPreview,ImportResult}.tsx`,
  `features/import/messages.pt-BR.ts`, `features/import/register.ts`, `web/e2e/import.spec.ts`.
- Modify: `app/routes.tsx`, `features/transactions/TransactionsPage.tsx`,
  `features/transactions/more.messages.pt-BR.ts`, `mocks/handlers.ts`, `mocks/data.ts`,
  `web/e2e/a11y.spec.ts`.
- Test: `features/import/ImportPage.test.tsx`, `features/transactions/TransactionsPage.test.tsx`.

**Interfaces:**
- Consumes: tudo das Tasks 5 a 8; `usePlan(ws).importAllowed` (Task 2); `useOnline()`.
- Produces: a rota `/lancamentos/importar` e o botão "Importar extrato".

**Como fica:** D14. A prévia é uma lista com caixa por linha (rótulo = data, descrição e valor), o
selo do estado em texto, o seletor de categoria (o `CategoryPicker` que já existe) e o aviso de
pagamento da fatura embaixo da linha. O MSW responde as duas rotas novas (dicas: uma categoria para
"padaria pao quente"; importação: conta `created`, `confirmed` e `skipped` pelas chaves já vistas na
sessão) e devolve `importKey` nos lançamentos.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `TransactionsPage.test.tsx › importButtonForAdults` | Adulto com `importAllowed` → "Importar extrato" leva a `/lancamentos/importar` |
| `TransactionsPage.test.tsx › noImportButtonForViewerOrFreePlan` | Leitor → sem botão; `importAllowed: false` → sem botão |
| `ImportPage.test.tsx › readsAnOfxAndShowsThePreview` | escolhe a conta e `ofx-sgml-1252.ofx` → quatro linhas com data, descrição, valor e categoria sugerida |
| `ImportPage.test.tsx › csvWithoutCertaintyAsksForColumns` | CSV de cabeçalho desconhecido → tela de colunas; confirmar → prévia |
| `ImportPage.test.tsx › bulkCategory` | "Usar a mesma categoria nas 3 sem categoria" preenche as três |
| `ImportPage.test.tsx › importNeedsCategoryAndNetwork` | uma marcada sem categoria → botão desligado; sem rede → desligado e "Sem conexão. A importação precisa de internet." |
| `ImportPage.test.tsx › showsTheResult` | depois de importar → "3 entraram, 0 confirmaram um previsto, 1 já existiam" (com o plural certo) |
| `ImportPage.test.tsx › unreadableFile` | um PNG → "Não conseguimos ler este arquivo. Ele precisa ser OFX ou CSV exportado pelo banco." |
| `ImportPage.test.tsx › tooLargeFile` | arquivo de 6 MB → "Este arquivo é grande demais. Exporte um período menor." |
| `ImportPage.test.tsx › redirectsWithoutAccess` | Leitor ou `importAllowed: false` → volta para `/lancamentos` |
| `e2e import.spec.ts` | entra como Adulto, abre Importar extrato, envia `ofx-sgml-1252.ofx` com `setInputFiles`, usa a categoria em lote, importa e lê "entraram"; envia o mesmo arquivo de novo e vê "Já importado" em todas as linhas e o botão desligado |
| `e2e a11y.spec.ts` | axe sem violações na prévia e na tela de colunas |

- [ ] **Step 2: Rodar e ver falhar.** Run: `corepack pnpm --dir web test -- features/import features/transactions`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** D14, o MSW e o e2e. A rota entra em `app/routes.tsx` como
  `lazy: () => import('@/features/import/ImportPage')`. O `register.ts` é importado só pelo chunk da
  importação.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** Os gates do web:
  `corepack pnpm --dir web typecheck`, `lint`, `format:check`, `test`, `build` e `check:chunks`
  (o carregamento inicial não cresce em relação à `main`); `corepack pnpm --dir web e2e` → PASS;
  em `api/`, `./gradlew build`. As duas buscas de dados pessoais saem com 1.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the statement import page with preview, columns and result"
```
