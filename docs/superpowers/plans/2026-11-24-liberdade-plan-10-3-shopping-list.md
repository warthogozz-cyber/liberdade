# Liberdade · Plano 10.3: lista de compras e comparação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Conferir de novo antes de executar.** Este plano foi escrito em 2026-11-24, antes de a 10.1 e a
> 10.2 existirem no código. Antes da Task 1, o controlador confere contra o `main` os nomes que este
> plano usa delas (seção "Interfaces"), o `LiveRelay`, as versões da exportação e as migrações. Onde
> o código mudou, o plano é ajustado num PR de docs pequeno antes de começar.

**Goal:**
- A família mantém uma lista de compras única, que muda ao vivo em todos os aparelhos.
- Cada item mostra o último preço em cada mercado, com data.
- A família compara 2 ou 3 mercados num total justo: só os itens com preço em todos.
- Os produtos repetidos podem ser juntados, com sugestões que a família aceita ou recusa.

**Architecture:**
- O módulo `markets` ganha a lista (`shopping_list_items`), as recusas de junção
  (`product_join_dismissals`), a edição e a junção de produtos e a comparação, calculada na API com
  `BigDecimal`.
- Cada mudança na lista publica `ShoppingListChanged`. O `LiveRelay` avisa os aparelhos da família
  com `{"entity":"shopping-list"}`, só quando a Feira está ligada.
- A web ganha as telas Produtos, Lista e Comparar, todas sob demanda.

**Tech Stack:**
- API: Spring Boot 4.1, Spring Modulith, JdbcClient, Testcontainers, MockMvcTester.
- Web: React 19, TanStack Query, react-intl, MSW, Vitest, Playwright e axe.
- Sem dependência nova.

**Spec:** [`docs/superpowers/specs/2026-11-24-liberdade-plan-10-feira-design.md`](../specs/2026-11-24-liberdade-plan-10-feira-design.md),
aprovado pelo mantenedor em 2026-11-24. Este plano cobre a seção 6.7 e a parte da 10.3 nas seções
6.2, 6.3 (V42), 6.8, 7 e 8. O spec é a autoridade; onde este plano desvia, o desvio está marcado.

## Global Constraints

- **Idiomas:** código, comentários, commits e nomes em inglês; interface, docs e mensagens de erro
  para a pessoa em PT-BR.
- **Commits:** Conventional Commits em minúsculas, `feat(markets): …`, `test(markets): …`; todo
  commit termina com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `git add` só com
  caminhos explícitos.
- **Dados pessoais:** produtos e mercados de teste inventados ("Leite Exemplo 1 L", "Mercado Exemplo
  1"). Antes de cada commit, as duas buscas da `personal-data-patterns` (no caminho de
  `git rev-parse --git-common-dir`) saem com código 1.
- **Logs e auditoria:** nunca levam nome de produto, quantidade nem preço. Só ids e contagens.
- **Papéis (spec 6.2):**
  - Dono e Adulto: tudo.
  - Jovem: vê produtos, preços, lista e comparação; adiciona itens à lista (inclusive criando um
    produto só com o nome), muda a quantidade, marca e desmarca. Não renomeia, não troca a unidade,
    não junta, não recusa sugestão, não tira item da lista nem limpa os pegos (D7).
  - Leitor: vê tudo, não muda nada.
  - Recusa de papel: 403 `auth.forbidden`.
- **Guardas:** toda rota nova tem `@WorkspaceScoped`, `@RequiresModule(ModuleKey.MARKETS)` e
  `@RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)`.
- **Erros:** RFC 9457 `ProblemDetail` com `code`.
- **Números:** quantidade, preço e total como texto decimal no JSON; `BigDecimal` na API. Nunca
  ponto flutuante em conta de dinheiro, nem na web.
- **Fronteiras de módulo:** tudo novo fica em `markets`. `live`, `audit` e `privacy` usam só os
  tipos públicos da raiz do pacote.
- **Contrato:** `api/openapi.json`, `web/src/shared/api/schema.ts` e o `RoleMatrixIT` mudam no
  mesmo commit da rota.
- **Cobertura:** JaCoCo 80% no total, 90% em `shared*` e `*.domain*`.
- **Desempenho:** pacote inicial até 200 KB (`npm run check:chunks`); as telas novas são `lazy`.
- **Acessibilidade:** WCAG 2.2 AA; axe sem violações. Marcar item funciona por teclado; mudar a
  ordem tem botões "Subir" e "Descer", além de arrastar.
- **Ética (spec 6.7):** texto neutro. Nenhum "mais barato", "economize", selo, cor de vencedor nem
  sugestão de comprar mais.

### Pré-requisitos de execução

- **10.1 e 10.2 no `main`** e conferidas no staging.
- **Versão da exportação:** antes da Task 5, conferir o `FORMAT_VERSION` do `ExportService` no
  `main`. Este plano escreve o seguinte (a 10.2 já subiu um).
- **Migração:** a V42 é desta entrega. Se o `main` já tiver uma V42, a execução para e avisa.
- **`LiveRelay`:** antes da Task 3, conferir a assinatura de `LiveRelay.send` no `main`. Este plano
  parte de `send(UUID workspaceId, String entity, @Nullable UUID id, Audience audience, boolean needsPlanning)`.
  Se o Plano 9 ou outro já tiver trocado o `boolean`, a Task 3 segue a forma nova em vez de D10.
- **A política de privacidade e o `/robo` não mudam.** A lista e os produtos já estão na política
  publicada com a 10.1.

### Pré-requisitos locais

- Docker para o PostgreSQL dos testes (`docker compose up -d db`). Nunca `docker compose down -v`.

## Entrega

- **PR 1 (docs):** o spec e os três planos da Feira (já feito, no PR do Plano 10).
- **PR 2 (código):** branch `feat/plan-10-3-shopping-list` a partir do `main`, uma task por commit
  (ou poucos commits por task), revisão final no modelo mais capaz e merge com o CI verde.
- **Depois do merge:** a conferência no staging (spec seção 8) e um PR de docs marcando a 10.3 e o
  Plano 10 como entregues.

## Interfaces da 10.1 e da 10.2 que este plano usa

Conferir os nomes no `main` antes de começar (aviso do topo).

- 10.1: `MarketRepository.findByRef`, `MarketRef.parse`, `TextNorm.normalize`, `MarketViews`,
  `MarketsExport`, `MarketEdited`; na web, `useMarketSearch`, `SourceStamp`, `FeiraPage.tsx`,
  `features/markets/{messages.pt-BR.ts,register.ts}`, as rotas em `app/routes.tsx` dentro de
  `<ModuleRoute module="markets">`, MSW em `mocks/handlers.ts` e `data.ts`.
- 10.2: as tabelas `products`, `product_aliases` e `price_records` (V41); `ProductUnit`,
  `ProductRepository.create`, `search`; `PriceRepository.latestByMarket`; as rotas
  `GET /markets/products?q` e `GET /markets/{ref}/prices`; `MarketsExport.products/prices`;
  na web, `ProductPicker.tsx`, `useFamilyProducts`, `unitPriceToCents` (`features/markets/price.ts`).
- Fora da Feira: `LiveRelay` (`live`), `web/src/shared/live/invalidation.ts` (mapa `PREFIXES`),
  `shared/money` (`formatMoney`, `parseTypedAmount`) e `useFormatMoney`.

## Decisões deste plano

**D1. Pacotes.**
- `markets/domain`: `NameSimilarity`, `ComparisonCalculator` e os registros deles. Sem Spring.
- `markets/internal`: o resto. Raiz do pacote: os eventos `ShoppingListChanged` e `ProductsMerged`
  e os registros da exportação.
- `live/package-info.java`: `markets` entra em `allowedDependencies`, como a 10.1 deixou anotado.

**D2. Produtos** (`GET /v1/workspaces/{ws}/markets/products/overview`).
- Resposta `{products: [{id, name, unit, gtin, prices: [{marketRef, marketName, unitPrice, unit, observedOn}]}]}`:
  - `prices` = o último preço do produto em cada mercado (mesma regra de "último" da 10.2, D12);
  - em ordem de nome; até 500 produtos (acima disso, `truncated: true`).
- Os quatro papéis veem. A busca da 10.2 (`GET /markets/products?q`) continua para o seletor.

**D3. Editar produto** (`PATCH /v1/workspaces/{ws}/markets/products/{id}`, Dono e Adulto).
- Corpo `{name?, unit?}`: nome de 1 a 80 depois de `strip()`; `unit` de `ProductUnit`.
- Os preços antigos guardam a unidade de quando foram registrados (`price_records.unit`); trocar a
  unidade do produto não mexe neles.
- Produto de outra família → 404 `product.not_found`.
- Publica `ProductsChanged(workspaceId)`, só para o `live` (D10); sem auditoria.

**D4. Juntar** (`POST /v1/workspaces/{ws}/markets/products/{keepId}/merge`, corpo `{removeId}`,
Dono e Adulto).
- `keepId = removeId` → 422 `product.merge_same`.
- Os dois com GTIN e diferentes → 422 `product.merge_gtin_conflict` **(decisão deste plano:** GTIN
  diferente é outro produto físico; a sugestão já não junta esses pares, e a junção à mão também
  não).
- Numa transação, nesta ordem:
  1. `price_records` do segundo passam para o primeiro;
  2. `product_aliases` do segundo passam para o primeiro; onde já existe o mesmo
     `(market_id, description_norm)` no primeiro, o do segundo é apagado (vale o do primeiro);
  3. item da lista do segundo: se o primeiro já está na lista, soma a quantidade no primeiro, fica
     marcado só se os dois estavam e o do segundo é apagado; senão o item passa para o primeiro;
  4. recusas (`product_join_dismissals`) com o segundo são apagadas;
  5. se só o segundo tinha GTIN, o primeiro fica com ele (o segundo é apagado antes, por causa do
     índice `products_gtin_once`);
  6. o segundo é apagado.
- Evento `ProductsMerged(workspaceId, keptProductId, removedProductId, actorUserId)`.
- Resposta 200 com o produto que ficou (forma de D2).

**D5. Sugestões de junção** (`GET /v1/workspaces/{ws}/markets/products/suggestions`).
- `NameSimilarity.score(String a, String b): double` (`markets/domain`): Jaccard sobre os conjuntos
  de palavras de `TextNorm.normalize`, sem as palavras de uma letra. Ex.: "leite integral exemplo 1l"
  e "leite exemplo integral" → 3/4 = 0,75.
- Par sugerido quando `score ≥ 0,5` (limite do construtor, spec 13), os dois não têm GTINs
  diferentes e o par não está em `product_join_dismissals`.
- Resposta `{pairs: [{a: {id, name, unit}, b: {id, name, unit}, score}]}`, `a` o de menor id, em
  ordem de `score` decrescente, até 20.
- O cálculo é em memória sobre os produtos da família (até 500, D2); acima disso, só os 500 mais
  novos.
- Abre aos quatro papéis; a tela só mostra os botões para Dono e Adulto.

**D6. "Não são"** (`POST /v1/workspaces/{ws}/markets/products/dismissals`, corpo `{a, b}`, Dono e
Adulto).
- Grava `(menor id, maior id)`; repetido não é erro (`ON CONFLICT DO NOTHING`), responde 204.
- Produto de outra família → 404 `product.not_found`.

**D7. Lista** (rotas em `/v1/workspaces/{ws}/markets/list`).

| Rota | Quem | O que faz |
|---|---|---|
| `GET /list` | todos | `{items: [{id, productId, name, unit, quantity, checked, position, prices: [...]}]}` em ordem de `position`; `prices` como em D2 |
| `POST /list` `{productId? \| newProductName?, quantity?}` | Dono, Adulto, Jovem | adiciona no fim |
| `PATCH /list/{id}` `{quantity?, checked?}` | Dono, Adulto, Jovem | muda a quantidade ou marca |
| `PUT /list/order` `{ids: [...]}` | Dono, Adulto, Jovem | nova ordem |
| `DELETE /list/{id}` | Dono, Adulto | tira o item |
| `POST /list/clear-checked` | Dono, Adulto | tira os marcados; responde `{removed}` |

- **Desvio do spec:** o spec deixa o Jovem "adicionar e marcar"; este plano deixa também mudar a
  quantidade e a ordem, que são parte de montar a lista, e deixa tirar item e limpar os pegos só
  para Dono e Adulto. Assim o Jovem não apaga o que outra pessoa pôs.
- `POST /list`:
  - `newProductName` (1 a 80): reusa um produto da família com o mesmo nome normalizado; senão cria
    um com `unit = UN` e sem preço. O Jovem pode criar (spec 6.2).
  - produto já na lista → soma a quantidade e desmarca, em vez de duplicar (`UNIQUE (workspace_id,
    product_id)`); responde 200 com o item. Item novo → 201.
  - `quantity` padrão `1`; a mesma forma da 10.2 (`^\d{1,15}(\.\d{1,4})?$`, maior que zero), senão
    422 `list.quantity_invalid`.
  - até 300 itens na lista, senão 422 `list.full`.
- `PATCH`: `checked: true` grava `checked_at = now()`; `false` grava nulo.
- `PUT /list/order`: `ids` tem de ser exatamente os itens da lista, senão 409 `list.order_stale`
  (outro aparelho mudou; a web recarrega e tenta de novo).
- Item de outra família → 404 `list.item_not_found`.
- Toda escrita publica `ShoppingListChanged(workspaceId)`, depois do commit.
- Sem auditoria para a lista (é o dia a dia, como marcar um item); a exportação leva o estado.

**D8. Comparação** (`GET /v1/workspaces/{ws}/markets/compare?markets=<ref>,<ref>[,<ref>]`).
- 2 ou 3 refs diferentes, senão 422 `compare.markets_invalid`; ref desconhecido → 404
  `market.not_found`. Mercado do OSM sem linha conta como mercado sem preços.
- Sem `markets`: a resposta só traz `suggested` (abaixo) e `items` sem preços.
- Para cada item da lista (marcados também, porque a pessoa pode comparar depois de comprar), o
  último preço em cada mercado escolhido, com `observedOn` e `old: true` quando tem mais de 60 dias
  (hoje em São Paulo, `Clock` injetado).
- `ComparisonCalculator.compute(List<ItemPrices> items, List<UUID> markets): Comparison` (puro):
  - item **comum** = tem preço em todos os mercados escolhidos e a mesma unidade (`price_records.unit`)
    em todos;
  - total de cada mercado = soma de `unitPrice × quantity` dos itens comuns, `BigDecimal`, arredondado
    HALF_EVEN para 2 casas só no fim;
  - `missing[market]` = itens sem preço naquele mercado;
  - `unitMismatch` = itens com preço em todos, mas unidades diferentes; ficam fora do total, com
    aviso;
  - `commonCount` = número de itens comuns.
- Resposta:
  ```
  {suggested: [ref, ref],
   markets: [{ref, name, total, missingCount}],
   commonCount, unitMismatchCount,
   items: [{productId, name, quantity, prices: [{ref, unitPrice, unit, observedOn, old} | null], common, unitMismatch}]}
  ```
- `suggested`: os dois mercados com mais itens da lista com preço (empate: o com preço mais
  recente). Vazio quando há menos de dois mercados com preço.
- Os mercados vêm em `markets` na ordem pedida, nunca ordenados por total.
- Os quatro papéis veem.

**D9. Exportação.**
- `MarketsExport` ganha `shoppingList(ws)` e `joinDismissals(ws)`, com `ExportedListItem` e
  `ExportedDismissal`.
- Arquivos novos:
  - `feira-lista.csv`: `produto_id;quantidade;pego_em;posicao;criado_em`;
  - `feira-nao-juntar.csv`: `produto_a;produto_b`.
- **A mais que o spec:** `feira-nao-juntar.csv` (escolha da família, pequena; sem ela, a importação
  futura voltaria a sugerir o que a família recusou).
- `FORMAT_VERSION` vai para a seguinte à do `main` (pré-requisito).
- Exclusão: em cascata pela `workspace_id`.

**D10. Ao vivo.**
- `LiveRelay.send(…, boolean needsPlanning)` vira `send(…, @Nullable ModuleKey requires)`:
  - `null` = sempre manda; `ModuleKey.PLANNING` = o comportamento antigo de `needsPlanning = true`;
  - os chamadores de hoje trocam `false` por `null` e `true` por `ModuleKey.PLANNING`, sem mudar o
    que fazem (os testes atuais do `live` provam).
- `@ApplicationModuleListener` no `live` para `ShoppingListChanged` e `ProductsChanged`/`ProductsMerged`
  (estes avisam a lista, porque mudam os nomes e os preços):
  `send(ws, "shopping-list", null, Audience.everyone(), ModuleKey.MARKETS)`. Conferir o nome exato
  da audiência de todos no `main`.
- Web, `shared/live/invalidation.ts`:
  `'shopping-list': [['markets', 'list'], ['markets', 'compare'], ['markets', 'products']]`, em
  arrays literais (o `shared` não importa a feature).
- As chaves de consulta da Feira na web seguem essas raízes: `['ws', ws, 'markets', 'list']`,
  `['ws', ws, 'markets', 'compare', refs]`, `['ws', ws, 'markets', 'products', …]`.

**D11. Web: Produtos** (`/mais/feira/produtos`, `ProductsPage.tsx`).
- No topo, as sugestões: "São o mesmo produto?" com os dois nomes, "Juntar" e "Não são" (Dono e
  Adulto). "Juntar" abre uma confirmação: "Os preços de {b} passam para {a}, e {b} some. Não dá para
  desfazer." e um seletor de qual nome fica.
- A lista dos produtos: nome, unidade e, por mercado, "R$ 4,99 · 12/11/2026" (texto simples, não o
  `SourceStamp`: é preço de nota, não fonte de site).
- Tocar num produto (Dono e Adulto): renomear, trocar a unidade, "Juntar com outro…" (abre o
  seletor da 10.2).

**D12. Web: Lista** (`/mais/feira/lista`, `ShoppingListPage.tsx`).
- Campo "Adicionar" com o `ProductPicker` da 10.2 (busca ou "Novo: {nome}").
- Cada item: caixa de marcar, nome, quantidade (− e +, e campo), e por mercado o último preço com
  data. "Subir" e "Descer" no menu do item; arrastar também.
- Marcados vão para o fim, riscados, em "Pegos". "Limpar os pegos" (Dono e Adulto), com
  confirmação.
- Atualização otimista ao marcar e mudar a quantidade; em erro, volta e mostra o aviso. 409
  `list.order_stale` → recarrega a lista e avisa "A lista mudou em outro aparelho.".
- Vazia: "A lista está vazia. Adicione o que falta em casa."
- O `ProductPicker` da tela da nota (10.2) passa a mostrar primeiro os itens da lista (spec 6.7:
  "cada item da lista vira candidato no seletor").

**D13. Web: Comparar** (`/mais/feira/comparar`, `ComparePage.tsx`; entrada pela lista).
- Escolha de 2 ou 3 mercados entre os que têm preço, com os dois de `suggested` já marcados.
- Por item, uma linha por mercado: "R$ 4,99 (12/11)" ou "—", e "antigo" quando `old`.
- Rodapé (copiar o tom do spec 6.7):
  - "Nos {n} itens que os mercados têm:" e o total de cada mercado, na ordem escolhida;
  - "{k} itens sem preço em {mercado}" por mercado;
  - "{u} itens com unidades diferentes ficaram fora do total.";
  - "Preços de notas registradas pela família. Datas ao lado de cada preço."
- Sem cor, ícone ou ordem que aponte um "vencedor".
- Rota `/mais/feira/comparar` **a mais que o spec** (6.1 lista as rotas); a comparação é uma tela
  própria.

## Review Focus

1. **Total injusto:** um mercado sem o preço de um item, ou o item em kg num e em unidade no outro.
   Testes: `ComparisonCalculatorTest.onlyCommonItemsCount`, `...unitMismatchLeftOut` e
   `CompareIT.missingAndMismatchCounted` (Tasks 4).
2. **Junção que perde ou duplica dados:** os dois produtos na lista, os dois com apelido igual no
   mesmo mercado, só o segundo com GTIN. Testes: `ProductMergeIT.listQuantitiesSummed`,
   `...aliasConflictKeepsFirst`, `...gtinMovesToKept` e `...differentGtinsIs422` (Task 1).
3. **Jovem passando do que pode:** renomear, juntar, apagar item ou limpar os pegos. Testes:
   `RoleMatrixIT` (Tasks 1 a 3) e `ShoppingListPage.test.tsx › youthCannotRemove` (Task 7).
4. **Dois aparelhos mexendo ao mesmo tempo:** mesmo produto adicionado duas vezes, ordem mudada com
   a lista velha. Testes: `ShoppingListIT.concurrentAddsOneItem`, `...staleOrderIs409` (Task 3) e o
   e2e com dois contextos (Task 9).
5. **Texto que empurra a compra:** "mais barato", "economize", selo. Testes:
   `ComparePage.test.tsx › neutralCopy` e `messagesNeutral.test.ts` (Task 8).

## Mapa de arquivos

Caminhos da API relativos a `api/src/main/java/br/com/liberdadefamilia/` (testes em
`api/src/test/java/br/com/liberdadefamilia/`); do web, relativos a `web/src/`.

| Arquivo | Task | Papel |
|---|---|---|
| `api/src/main/resources/db/migration/V42__shopping_list.sql` | 1 | lista e recusas |
| `markets/internal/{ProductsController,ProductsService,ProductMergeService}.java`, `markets/{ProductsMerged,ProductsChanged}.java` | 1 | produtos, edição e junção |
| `markets/domain/NameSimilarity.java`, `markets/internal/ProductSuggestionsService.java` | 2 | sugestões e "Não são" |
| `markets/internal/{ShoppingListController,ShoppingListService,ShoppingListRepository}.java`, `markets/ShoppingListChanged.java`, `live/internal/{LiveRelay,…Listeners}.java`, `live/package-info.java`, `shared/live/invalidation.ts` | 3 | lista e ao vivo |
| `markets/domain/ComparisonCalculator.java`, `markets/internal/{CompareController,CompareService}.java` | 4 | comparação |
| `markets/MarketsExport.java`, `markets/internal/JdbcMarketsExport.java`, `privacy/internal/ExportService.java`, `audit/internal/AuditListeners.java`, `audit/AuditEntry.java` | 5 | exportação e auditoria |
| `features/markets/products/{ProductsPage.tsx,MergeDialog.tsx,SuggestionCard.tsx,useProducts.ts}` | 6 | tela Produtos |
| `features/markets/list/{ShoppingListPage.tsx,ListItemRow.tsx,useShoppingList.ts}`, `features/markets/receipt/ProductPicker.tsx` | 7 | tela Lista |
| `features/markets/compare/{ComparePage.tsx,useCompare.ts}` | 8 | tela Comparar |
| `web/e2e/{feira-lista.spec.ts,a11y.spec.ts}`, `docs/{architecture.md,data-model.md}` | 9 | e2e e docs |

`app/routes.tsx`, `features/markets/messages.pt-BR.ts`, `mocks/{handlers.ts,data.ts}`,
`api/openapi.json`, `web/src/shared/api/schema.ts` e `RoleMatrixIT.java` mudam nas tasks que tocam
cada rota ou tela.

## Tasks

### Task 1: V42, produtos, edição e junção

**Files:**
- Create: `db/migration/V42__shopping_list.sql`, os arquivos da Task 1 no mapa.
- Modify: `markets/internal/ProductRepository.java`, `api/openapi.json`, `schema.ts`,
  `RoleMatrixIT.java`.
- Test: `markets/ProductsIT.java`, `markets/ProductMergeIT.java`, `markets/ShoppingListSchemaIT.java`,
  `RoleMatrixIT`.

**Interfaces:**
- Consumes: as tabelas e repositórios da 10.2.
- Produces:
  - `record ProductsMerged(UUID workspaceId, UUID keptProductId, UUID removedProductId, UUID actorUserId)`;
  - `record ProductsChanged(UUID workspaceId)`;
  - `ProductsService.overview(UUID ws): ProductsOverview` e o registro `ProductPriceView(String marketRef, @Nullable String marketName, BigDecimal unitPrice, ProductUnit unit, LocalDate observedOn)`,
    que a lista (Task 3) reusa para `prices`;
  - `ProductRepository.findByNormalizedName(UUID ws, String norm): Optional<UUID>`, que a Task 3
    usa.

**Como fica:** a V42 é o DDL da seção 6.3 do spec (V42), mais
`CHECK (product_a < product_b)` em `product_join_dismissals` e as FKs `(workspace_id, product_a)` e
`(workspace_id, product_b)` para `products (workspace_id, id) ON DELETE CASCADE`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ShoppingListSchemaIT.productOncePerList` | o mesmo produto duas vezes na lista → violação |
| `ShoppingListSchemaIT.dismissalOrdered` | `product_a > product_b` → violação |
| `ProductsIT.overviewLatestPerMarket` | produto com preços em 2 mercados (dois no mesmo) → 2 itens em `prices`, os mais novos |
| `ProductsIT.renameAndUnit` | `PATCH {name: "Leite Exemplo Integral 1 L", unit: L}` → 200; preços antigos com a unidade antiga |
| `ProductsIT.blankNameIs422` | `name: "  "` ou 81 caracteres → 422 `product.name_invalid` |
| `ProductsIT.foreignProductIs404` | produto de outra família → 404 `product.not_found` |
| `ProductMergeIT.pricesAndAliasesMove` | B com 2 preços e 1 apelido → todos em A; B não existe mais |
| `ProductMergeIT.aliasConflictKeepsFirst` | A e B com o mesmo apelido no mesmo mercado → fica o de A |
| `ProductMergeIT.listQuantitiesSummed` | A (2) e B (1,5) na lista → um item de A com 3,5 |
| `ProductMergeIT.listItemMovesWhenOnlyRemovedListed` | só B na lista → o item passa para A, mesma posição |
| `ProductMergeIT.gtinMovesToKept` | só B com GTIN → A fica com ele |
| `ProductMergeIT.differentGtinsIs422` | A e B com GTINs diferentes → 422 `product.merge_gtin_conflict`, nada muda |
| `ProductMergeIT.sameIdIs422` | `keepId = removeId` → 422 `product.merge_same` |
| `ProductMergeIT.dismissalsWithRemovedGone` | recusa (B, C) → apagada |
| `ProductMergeIT.eventPublished` | `ProductsMerged` com os dois ids |
| `RoleMatrixIT` | `overview`: os quatro 200; `PATCH` e `merge`: Dono e Adulto 200, Jovem e Leitor 403 |

- [ ] **Step 2: Rodar e ver falhar.** Em `api/`: `./gradlew test --tests '*markets*'` → FAIL.
- [ ] **Step 3: Implementar.** D2, D3 e D4.
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): add the v42 list tables and edit and merge products"`.

---

### Task 2: Sugestões de junção e "Não são"

**Files:**
- Create: `markets/domain/NameSimilarity.java`, `markets/internal/ProductSuggestionsService.java`.
- Modify: `markets/internal/ProductsController.java`, `api/openapi.json`, `schema.ts`,
  `RoleMatrixIT.java`.
- Test: `markets/domain/NameSimilarityTest.java`, `markets/ProductSuggestionsIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `TextNorm.normalize` (10.1).
- Produces: `NameSimilarity.score(String a, String b): double`; as rotas de D5 e D6.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `NameSimilarityTest.jaccardOverWords` | "Leite Integral Exemplo 1L" e "leite exemplo integral" → 0,75 |
| `NameSimilarityTest.accentsAndCaseIgnored` | "Feijão Exemplo" e "FEIJAO exemplo" → 1,0 |
| `NameSimilarityTest.singleLettersIgnored` | "Arroz Exemplo T 1" e "Arroz Exemplo" → 1,0 |
| `NameSimilarityTest.disjointIsZero` | "Arroz Exemplo" e "Leite Teste" → 0 |
| `ProductSuggestionsIT.similarPairSuggested` | "Leite Exemplo 1L" e "Leite Exemplo Integral 1L" → 1 par, `a` o menor id |
| `ProductSuggestionsIT.differentGtinsNotSuggested` | o mesmo par com GTINs diferentes → nenhum |
| `ProductSuggestionsIT.oneGtinStillSuggested` | só um com GTIN → sugerido |
| `ProductSuggestionsIT.dismissedNotSuggested` | `POST dismissals {b, a}` (fora de ordem) → 204; o par some; repetir → 204 |
| `ProductSuggestionsIT.belowThresholdNotSuggested` | "Leite Exemplo" e "Leite Teste Integral Desnatado" (1/5) → nenhum |
| `RoleMatrixIT` | `suggestions`: os quatro 200; `dismissals`: Dono e Adulto 204, Jovem e Leitor 403 |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D5 e D6.
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): suggest products to merge and remember refusals"`.

---

### Task 3: A lista e o aviso ao vivo

**Files:**
- Create: os arquivos da lista na Task 3 no mapa.
- Modify: `live/internal/LiveRelay.java` e os chamadores dele, `live/package-info.java`, um listener
  novo no `live` para os eventos da Feira, `web/src/shared/live/invalidation.ts`,
  `api/openapi.json`, `schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/ShoppingListIT.java`, `live/ShoppingListLiveIT.java`, os testes atuais do `live`,
  `web/src/shared/live/invalidation.test.ts`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `ProductsService` e `ProductPriceView` (Task 1); `ProductRepository.findByNormalizedName`
  (Task 1), `create` (10.2).
- Produces:
  - `record ShoppingListChanged(UUID workspaceId)`;
  - `LiveRelay.send(UUID workspaceId, String entity, @Nullable UUID id, Audience audience, @Nullable ModuleKey requires)`;
  - as rotas de D7, que as Tasks 4 e 7 usam.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ShoppingListIT.addByProductAndByName` | `POST {productId}` → 201 no fim; `POST {newProductName: "Café Exemplo"}` → 201, produto novo `UN` sem preço |
| `ShoppingListIT.newNameReusesSameNormalized` | produto "Café Exemplo" existe; `POST {newProductName: "cafe exemplo"}` → usa ele |
| `ShoppingListIT.addingAgainSumsAndUnchecks` | item marcado com 1; `POST` do mesmo produto com 2 → 200, quantidade 3, desmarcado |
| `ShoppingListIT.concurrentAddsOneItem` | duas threads adicionam o mesmo produto novo → 1 item, quantidade somada |
| `ShoppingListIT.checkAndUncheck` | `PATCH {checked: true}` → `checked_at` gravado; `false` → nulo |
| `ShoppingListIT.quantityInvalid` | `"0"`, `"1,5"`, `"1.00001"` → 422 `list.quantity_invalid` |
| `ShoppingListIT.reorder` | `PUT order` com os ids em outra ordem → `GET` na ordem nova |
| `ShoppingListIT.staleOrderIs409` | `ids` sem um item novo → 409 `list.order_stale` |
| `ShoppingListIT.clearChecked` | 3 itens, 2 marcados → `{removed: 2}`, sobra 1 |
| `ShoppingListIT.listFull` | 300 itens → o 301º dá 422 `list.full` |
| `ShoppingListIT.pricesPerMarket` | item com preços em 2 mercados → `prices` com os 2 |
| `ShoppingListIT.eventOnEveryWrite` | cada escrita publica `ShoppingListChanged` (`@RecordApplicationEvents`) |
| `ShoppingListLiveIT.relaysToEveryoneWhenMarketsOn` | evento → `{"entity":"shopping-list"}` no canal da família, para os quatro papéis |
| `ShoppingListLiveIT.silentWhenMarketsOff` | módulo desligado → nada enviado |
| testes atuais do `live` | passam sem mudar o que esperam (só a chamada nova) |
| `invalidation.test.ts › shoppingList` | `{"entity":"shopping-list"}` invalida as chaves que começam com `['ws', ws, 'markets', 'list']`, `…'compare'` e `…'products'` |
| `RoleMatrixIT` | `GET`: os quatro 200; `POST`, `PATCH`, `PUT order`: Dono, Adulto e Jovem 2xx, Leitor 403; `DELETE` e `clear-checked`: Dono e Adulto 2xx, Jovem e Leitor 403 |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D7 e D10. O `INSERT … ON CONFLICT (workspace_id, product_id) DO UPDATE`
  soma a quantidade e desmarca, o que resolve a corrida.
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build`; em `web/`, `npx vitest run src/shared/live`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): add the family shopping list with live updates"`.

---

### Task 4: A comparação

**Files:**
- Create: `markets/domain/ComparisonCalculator.java`, `markets/internal/{CompareController,CompareService}.java`.
- Modify: `api/openapi.json`, `schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/domain/ComparisonCalculatorTest.java`, `markets/CompareIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: a lista (Task 3), `PriceRepository` (10.2), `MarketRef.parse` e `findByRef` (10.1).
- Produces:
  - `record ItemPrices(UUID productId, BigDecimal quantity, Map<UUID, PricePoint> byMarket)`,
    `record PricePoint(BigDecimal unitPrice, ProductUnit unit, LocalDate observedOn)`;
  - `ComparisonCalculator.compute(List<ItemPrices> items, List<UUID> markets): Comparison`, com
    `record Comparison(Map<UUID, BigDecimal> totals, Map<UUID, Integer> missing, int commonCount, Set<UUID> common, Set<UUID> unitMismatch)`;
  - a rota de D8, que a Task 8 usa.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ComparisonCalculatorTest.onlyCommonItemsCount` | 3 itens; o 3º sem preço em B → totais só dos 2 primeiros; `missing[B] = 1`; `commonCount = 2` |
| `ComparisonCalculatorTest.multipliesQuantity` | 2 × 4,99 e 1,5 × 10,00 em A → `24.98` |
| `ComparisonCalculatorTest.unitMismatchLeftOut` | item em `KG` em A e `UN` em B → fora do total e em `unitMismatch` |
| `ComparisonCalculatorTest.roundsOnlyAtTheEnd` | 3 × `0.3333` → `1.00` (e não 0,99 de arredondar antes) |
| `ComparisonCalculatorTest.threeMarkets` | 3 mercados → comum só com preço nos 3 |
| `CompareIT.missingAndMismatchCounted` | lista de exemplo com 2 mercados → `commonCount`, `missingCount` e `unitMismatchCount` certos |
| `CompareIT.oldAfterSixtyDays` | relógio em 2026-11-24; preço de 2026-09-24 → `old: false`; de 2026-09-23 → `old: true` |
| `CompareIT.marketsInRequestedOrder` | `markets=B,A` com B mais caro → `markets[0]` = B |
| `CompareIT.suggestsTwoWithMostPrices` | 3 mercados com 5, 3 e 1 itens com preço → `suggested` = os de 5 e 3 |
| `CompareIT.invalidMarkets` | 1 ref, 4 refs ou o mesmo ref duas vezes → 422 `compare.markets_invalid`; ref de outra família → 404 |
| `CompareIT.osmWithoutRowHasNoPrices` | um ref do OSM sem linha → todos os itens em `missing` dele, sem erro |
| `RoleMatrixIT` | `compare`: os quatro 200 |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D8.
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): compare the list across two or three markets"`.

---

### Task 5: Exportação, auditoria e exclusão

**Files:**
- Modify: `markets/MarketsExport.java`, `markets/internal/JdbcMarketsExport.java`,
  `privacy/internal/ExportService.java`, `audit/internal/AuditListeners.java`,
  `audit/AuditEntry.java`, os testes de exportação existentes.
- Test: `privacy/ExportShoppingListIT.java`, `audit/ProductsAuditIT.java`, `privacy/FamilyDeletionIT`.

**Interfaces:**
- Consumes: `ProductsMerged` (Task 1).
- Produces: `MarketsExport.shoppingList/joinDismissals`, `ExportedListItem`, `ExportedDismissal`.

| Evento | Ação na auditoria | `targetType` | Metadata |
|---|---|---|---|
| `ProductsMerged` | `products.merged` | `PRODUCT` (o que ficou) | `{removedProductId}` |

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ExportShoppingListIT.listAndDismissalsExported` | `feira-lista.csv` e `feira-nao-juntar.csv` com os cabeçalhos de D9 e as linhas certas |
| `ExportShoppingListIT.productIdsMatchProductsCsv` | todo `produto_id` da lista existe em `feira-produtos.csv` |
| `ExportShoppingListIT.formatVersionBumped` | `formatVersion` = a do `main` + 1 |
| `ProductsAuditIT.mergeAudited` | `products.merged`, `targetType PRODUCT`, metadata exatamente `{removedProductId}`, sem nomes |
| `ProductsAuditIT.listNotAudited` | adicionar, marcar e limpar → nenhuma linha nova na auditoria |
| `FamilyDeletionIT` | apagar a família apaga `shopping_list_items` e `product_join_dismissals` |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D9 e a linha da auditoria acima; `PRODUCT` entra no Javadoc de
  `AuditEntry`.
- [ ] **Step 4: Rodar e ver passar** → PASS.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): export the shopping list and audit product merges"`.

---

### Task 6: A tela Produtos

**Files:**
- Create: os arquivos da Task 6 no mapa.
- Modify: `app/routes.tsx`, `features/markets/{FeiraPage.tsx,messages.pt-BR.ts}`,
  `mocks/{handlers.ts,data.ts}`.
- Test: `features/markets/products/{ProductsPage.test.tsx,MergeDialog.test.tsx}`.

**Interfaces:**
- Consumes: as rotas das Tasks 1 e 2; `ProductPicker` e `unitPriceToCents` (10.2).
- Produces: `useProductsOverview(ws)`, `useProductSuggestions(ws)`, `useEditProduct(ws)`,
  `useMergeProducts(ws)`, `useDismissPair(ws)`, com chaves sob `['ws', ws, 'markets', 'products']`;
  a rota `/mais/feira/produtos` e o link "Produtos" na Feira.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ProductsPage.test.tsx › listsPricesPerMarket` | produto com 2 mercados → "R$ 4,99 · 12/11/2026" e o nome de cada mercado |
| `ProductsPage.test.tsx › suggestionCard` | um par do MSW → "São o mesmo produto?", "Juntar" e "Não são" |
| `ProductsPage.test.tsx › notTheSameHidesPair` | "Não são" → `POST dismissals` com `{a, b}` e o par some |
| `ProductsPage.test.tsx › youthSeesNoActions` | Jovem e Leitor → sem "Juntar", "Não são" nem editar |
| `MergeDialog.test.tsx › warnsAndChoosesName` | mostra "Não dá para desfazer."; escolher o nome de B → `merge` com `keepId` = B |
| `MergeDialog.test.tsx › gtinConflictMessage` | 422 `product.merge_gtin_conflict` → "Estes produtos têm códigos de barras diferentes e não podem ser juntados." |

- [ ] **Step 2: Rodar e ver falhar.** Em `web/`: `npx vitest run src/features/markets/products` → FAIL.
- [ ] **Step 3: Implementar.** D11.
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint`, `typecheck`, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): add the products screen with merge suggestions"`.

---

### Task 7: A tela Lista

**Files:**
- Create: os arquivos da Task 7 no mapa.
- Modify: `app/routes.tsx`, `features/markets/{FeiraPage.tsx,messages.pt-BR.ts}`,
  `features/markets/receipt/ProductPicker.tsx`, `mocks/{handlers.ts,data.ts}`.
- Test: `features/markets/list/{ShoppingListPage.test.tsx,ListItemRow.test.tsx}`,
  `features/markets/receipt/ProductPicker.test.tsx`.

**Interfaces:**
- Consumes: as rotas da Task 3; `ProductPicker` (10.2); a invalidação ao vivo (Task 3).
- Produces: `useShoppingList(ws)` (chave `['ws', ws, 'markets', 'list']`), `useAddListItem`,
  `useUpdateListItem`, `useReorderList`, `useRemoveListItem`, `useClearChecked`; a rota
  `/mais/feira/lista` e o link "Lista de compras" na Feira.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ShoppingListPage.test.tsx › addByPickerOrNewName` | escolher um produto → `POST {productId}`; "Novo: Café Exemplo" → `POST {newProductName}` |
| `ShoppingListPage.test.tsx › checkIsOptimistic` | marcar → riscado na hora e em "Pegos"; MSW 500 → volta e mostra o aviso |
| `ShoppingListPage.test.tsx › quantityAsDecimalText` | digitar `1,5` → `PATCH {quantity: "1.5"}` |
| `ShoppingListPage.test.tsx › moveUpAndDownByKeyboard` | "Subir" no 2º item → `PUT order` com a ordem nova |
| `ShoppingListPage.test.tsx › staleOrderReloads` | `PUT order` 409 → refaz o `GET` e mostra "A lista mudou em outro aparelho." |
| `ShoppingListPage.test.tsx › youthCannotRemove` | Jovem: adicionar, marcar e quantidade aparecem; "Remover" e "Limpar os pegos" não |
| `ShoppingListPage.test.tsx › viewerReadOnly` | Leitor: sem campo de adicionar nem caixas de marcar ativas |
| `ShoppingListPage.test.tsx › liveEventRefetches` | invalidar `['ws', ws, 'markets', 'list']` → refaz o `GET` |
| `ShoppingListPage.test.tsx › emptyState` | sem itens → "A lista está vazia. Adicione o que falta em casa." |
| `ListItemRow.test.tsx › pricesPerMarket` | "Mercado Exemplo 1 · R$ 4,99 · 12/11/2026" |
| `ProductPicker.test.tsx › listItemsFirst` | na tela da nota, os itens da lista aparecem antes dos outros produtos |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D12.
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint`, `typecheck`, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): add the shopping list screen"`.

---

### Task 8: A tela Comparar

**Files:**
- Create: os arquivos da Task 8 no mapa.
- Modify: `app/routes.tsx`, `features/markets/{messages.pt-BR.ts,list/ShoppingListPage.tsx}`,
  `mocks/{handlers.ts,data.ts}`.
- Test: `features/markets/compare/ComparePage.test.tsx`,
  `features/markets/messagesNeutral.test.ts`.

**Interfaces:**
- Consumes: a rota da Task 4; `unitPriceToCents` (10.2); `useFormatMoney` (`shared`).
- Produces: `useCompare(ws, refs)` (chave `['ws', ws, 'markets', 'compare', refs]`); a rota
  `/mais/feira/comparar` e o botão "Comparar mercados" na lista.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ComparePage.test.tsx › suggestedPreselected` | `suggested` do MSW → os dois mercados já marcados |
| `ComparePage.test.tsx › oneToThreeMarkets` | com 3 marcados, o 4º fica desligado; com 1, "Escolha pelo menos dois mercados." |
| `ComparePage.test.tsx › rowsWithDateAndOld` | "R$ 4,99 (12/11)", "—" e "antigo" onde `old` |
| `ComparePage.test.tsx › footerCounts` | "Nos 7 itens que os mercados têm:", "1 item sem preço em Mercado Exemplo 2" e a linha de unidades |
| `ComparePage.test.tsx › totalsInChosenOrder` | totais na ordem escolhida, sem destaque em nenhum |
| `ComparePage.test.tsx › neutralCopy` | o texto da tela não contém "barato", "economi", "melhor" nem "vencedor" (sem diferenciar maiúsculas) |
| `messagesNeutral.test.ts` | nenhuma mensagem de `features/markets/messages.pt-BR.ts` contém essas palavras |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D13. Totais em texto decimal viram centavos com a mesma regra de
  `unitPriceToCents` antes do `formatMoney`.
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint`, `typecheck`, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): add the market comparison screen"`.

---

### Task 9: E2E em dois aparelhos, acessibilidade e docs

**Files:**
- Create: `web/e2e/feira-lista.spec.ts`.
- Modify: `web/e2e/a11y.spec.ts`, `docs/architecture.md`, `docs/data-model.md`.
- Test: `web/e2e/feira-lista.spec.ts`, `web/e2e/a11y.spec.ts`.

**Como fica:**
- `feira-lista.spec.ts` (perfil `e2e` da API, fontes falsas da 10.1):
  1. Preparo pela API do e2e: duas notas inventadas (o mesmo gerador de chave da 10.2) em "Mercado
     Exemplo 1" e "Mercado Exemplo 2", com "Leite Exemplo 1 L" nos dois e "Arroz Exemplo 5 kg" só
     no 1.
  2. Dois contextos do navegador: Dono e Jovem da mesma família, os dois na Lista.
  3. O Jovem adiciona "Leite Exemplo 1 L" e "Arroz Exemplo 5 kg"; o Dono vê os dois sem recarregar.
  4. O Dono marca o leite; o Jovem vê riscado.
  5. O Dono abre "Comparar mercados": os dois mercados marcados; "Nos 1 itens…" (ajustar o plural
     da mensagem), "1 item sem preço em Mercado Exemplo 2".
  6. O Jovem não vê "Limpar os pegos".
  7. Produtos: dois produtos parecidos criados pelo preparo aparecem em "São o mesmo produto?"; o
     Dono junta e a lista mostra um só.
- `a11y.spec.ts`: axe em Produtos, Lista e Comparar.
- Docs:
  - `architecture.md`: lista, comparação e o evento ao vivo `shopping-list`;
  - `data-model.md`: a V42 e o mapa de dados.

- [ ] **Step 1: Escrever o e2e e o axe.**
- [ ] **Step 2: Rodar.** Em `web/`: `npm run e2e -- feira-lista a11y core-without-modules` → PASS
  (se um contêiner antigo atrapalhar localmente, o CI confere; nunca recriar contêineres com
  `down -v`).
- [ ] **Step 3: Conferir tudo.** `./gradlew build` em `api/`; `npm run lint`, `typecheck`, `test`,
  `build` e `check:chunks` em `web/`.
- [ ] **Step 4: Commit.** `git commit -m "test(markets): edit the shopping list on two devices end to end"`.

## Depois do merge (passos do mantenedor, spec seção 8)

1. No staging, montar uma lista real e comparar em 2 mercados com preços de notas reais (critério de
   saída da 10.3 e do Plano 10).
2. Conferir o mapa sem toque mais uma vez (o critério do Plano 10 inteiro).
3. PR de docs: README dos planos, linha 10, "entregue".
