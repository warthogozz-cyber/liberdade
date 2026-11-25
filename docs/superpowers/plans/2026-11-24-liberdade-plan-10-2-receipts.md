# Liberdade · Plano 10.2: preços pela NFC-e — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Conferir de novo antes de executar.** Este plano foi escrito em 2026-11-24, antes de a 10.1
> existir no código. Antes da Task 1, o controlador confere contra o `main` os nomes que este plano
> usa da 10.1 (seção "Interfaces"), as versões da exportação e as migrações. Onde o código mudou, o
> plano é ajustado num PR de docs pequeno antes de começar.

**Goal:**
- A família registra uma nota fiscal de supermercado (NFC-e) pelo QR, por um link colado ou pela
  chave digitada, e grava os preços dos itens que escolher.
- Os preços aparecem no cartão do mercado, com data.
- O CPF da nota nunca sai do aparelho, e a chave nunca fica guardada.

**Architecture:**
- O aparelho lê o QR (`BarcodeDetector` ou `jsQR` num Web Worker), limpa o conteúdo e manda à API só
  a chave e, na v2, o `p`.
- O módulo `markets` ganha as notas: `lookup` valida a chave, acha o mercado pelo CNPJ e responde
  sempre `MANUAL` (nenhum leitor de estado nesta entrega).
- A gravação refaz a validação, guarda só o hash da chave e grava nota, produtos novos, apelidos e
  preços numa transação.
- A regra da junção automática (GTIN, depois apelido) mora aqui como função pura, para os planos
  10.2.x só a chamarem.

**Tech Stack:**
- API: Spring Boot 4.1, Spring Modulith, JdbcClient, Testcontainers, MockMvcTester.
- Web: React 19, TanStack Query, react-intl, MSW, Vitest, Playwright e axe.
- Dependência nova: `jsqr` (Apache-2.0), versão exata, só se o mantenedor aprovar (pré-requisito).

**Spec:** [`docs/superpowers/specs/2026-11-24-liberdade-plan-10-feira-design.md`](../specs/2026-11-24-liberdade-plan-10-feira-design.md),
aprovado pelo mantenedor em 2026-11-24. Este plano cobre as seções 6.5 e 6.6 e a parte da 10.2 nas
seções 6.3 (V41), 6.8, 7, 8 e 12. O spec é a autoridade; onde este plano desvia, o desvio está
marcado.

## Global Constraints

- **Idiomas:** código, comentários, commits e nomes em inglês; interface, docs e mensagens de erro
  para a pessoa em PT-BR.
- **Commits:** Conventional Commits em minúsculas, `feat(markets): …`, `test(markets): …`,
  `docs(markets): …`; todo commit termina com
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `git add` só com caminhos explícitos.
- **Dados pessoais:** nenhuma chave, CNPJ, CPF ou nota real nos testes, fixtures e docs. As chaves
  de teste saem de um gerador de teste (D3) com CNPJ inventado e dígitos calculados. Antes de cada
  commit, as duas buscas da `personal-data-patterns` (no caminho de `git rev-parse --git-common-dir`)
  saem com código 1.
- **Logs e auditoria:** nunca levam chave, `p`, CPF, CNPJ, descrição, nome de produto nem preço. Só
  tipos fixos, contagens e ids.
- **Chamadas externas:** nenhuma nesta entrega. A API não abre a página da SEFAZ; o link do QR só
  abre no navegador da pessoa.
- **Papéis (spec 6.2):** Dono e Adulto registram notas. Jovem e Leitor veem os preços e os produtos,
  mas não registram nota (403 `auth.forbidden`).
- **Guardas:** toda rota nova tem `@WorkspaceScoped`, `@RequiresModule(ModuleKey.MARKETS)` e
  `@RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)`.
- **Erros:** RFC 9457 `ProblemDetail` com `code`.
- **Números:** quantidade e preço vão no JSON como texto decimal (`"4.99"`), nunca como ponto
  flutuante. Na API, `BigDecimal`.
- **Fronteiras de módulo:** tudo novo fica em `markets`. `audit` e `privacy` usam só os tipos
  públicos da raiz do pacote.
- **Contrato:** `api/openapi.json`, `web/src/shared/api/schema.ts` e o `RoleMatrixIT` mudam no
  mesmo commit da rota.
- **Cobertura:** JaCoCo 80% no total, 90% em `shared*` e `*.domain*`.
- **Desempenho:** pacote inicial até 200 KB (`npm run check:chunks`). O leitor de QR fica num chunk
  próprio, carregado só quando a pessoa toca em "Ler o QR".
- **Acessibilidade:** WCAG 2.2 AA; axe sem violações nas telas novas. Colar o link e digitar a chave
  são as alternativas sem câmera.
- **Câmera:** só depois do toque, só a de trás quando houver, e para ao sair da tela. A imagem nunca
  sai do aparelho.
- **Ética:** a nota não vira gasto sozinha; nada de "mais barato" nem pressa.

### Pré-requisitos de execução

- **10.1 no `main`** e conferida no staging.
- **Plano 8.2 no `main`:** a guarda `@RequiresPlan(…, openWhileBillingOff = true)`.
- **Versão da exportação:** antes da Task 5, conferir o `FORMAT_VERSION` do `ExportService` no
  `main`. Este plano escreve o seguinte (a 10.1 já subiu um).
- **Migração:** a V41 é desta entrega. Se o `main` já tiver uma V41, a execução para e avisa.
- **CNPJ alfanumérico (D4):** antes da Task 1, o implementador confere a Nota Técnica em vigor sobre
  o CNPJ alfanumérico na chave da NFC-e e anota no relatório. Se ela disser outra coisa, o
  controlador ajusta D4 antes de seguir.
- **`jsqr` (spec seção 12):** antes da Task 7, o controlador pergunta ao mantenedor, pelo
  AskUserQuestion, se aprova a dependência. Se não aprovar, a Task 7 entra sem ela (D16).
- **A política de privacidade e o `/robo` não mudam.** A política publicada com a 10.1 já diz o que
  a nota guarda (10.1, D22). O `/robo` já mudou na 10.1 (10.1, D21).

### Pré-requisitos locais

- Docker para o PostgreSQL dos testes (`docker compose up -d db`). Nunca `docker compose down -v`.

## Entrega

- **PR 1 (docs):** o spec e os três planos da Feira (já feito, no PR do Plano 10).
- **PR 2 (código):** branch `feat/plan-10-2-receipts` a partir do `main`, uma task por commit (ou
  poucos commits por task), revisão final no modelo mais capaz e merge com o CI verde.
- **Depois do merge:** os passos do mantenedor (fim deste plano) e a conferência no staging (spec
  seção 8). Depois, um PR de docs marca a 10.2 como entregue no README dos planos.

## Interfaces da 10.1 que este plano usa

Conferir os nomes no `main` antes de começar (aviso do topo).

- `MarketRepository.findByRef(UUID ws, MarketRef ref): Optional<MarketRow>`.
- `MarketRepository.ensureOsmRow(UUID ws, OsmRef ref, UUID actor): UUID` (cria a linha `OSM` na
  mesma transação).
- `MarketRef.parse(String)` → `OsmRef` ou `FamilyRef`.
- `FamilyMarketService` (cria mercado `FAMILY` e publica `MarketAdded`).
- `MarketEdited(workspaceId, marketId, actorUserId, change)` com o enum de `change`.
- `TextNorm.normalize(String)`, `MarketKind`, `MarketsProperties`, `MarketsStartupCheck`.
- `MarketController`, `MarketViews` (o cartão), `MarketsExport` com `ExportedMarket`.
- Web: `useMarketSearch`, `useMarketCard`, `readLocation`, `LocationChooser`, `SourceStamp`,
  `features/markets/messages.pt-BR.ts`, as rotas em `app/routes.tsx` dentro de
  `<ModuleRoute module="markets">`, `MarketCardPage.tsx`, MSW em `mocks/handlers.ts` e `data.ts`,
  `FakeOsmDataset` (6 mercados inventados perto de `(-10.00000, -50.00000)`, sem CNPJ).

## Decisões deste plano

**D1. Pacotes.**
- `markets/domain`: `NfceKey`, `Cnpj`, `Gtin`, `KeyHash`, `ProductUnit`, `ProductMatcher`,
  `InvalidKeyException`. Sem Spring.
- `markets/internal`: o resto. Raiz do pacote: o evento `ReceiptAdded` e o que a exportação usa.
- Nenhuma dependência de módulo nova.

**D2. A chave** (`NfceKey`).
- `static NfceKey parse(String raw, YearMonth current)`: tira espaços e pontos, exige 44 posições e
  confere, nesta ordem:
  - posições 1–2 (cUF) entre os 27 códigos do IBGE;
  - 3–6 (AAMM) com mês de 01 a 12, ano de 2010 em diante e não depois de `current`;
  - 7–20 (CNPJ) válido por `Cnpj.isValid` (D4);
  - 21–22 = `65` (modelo 55 é nota de empresa, não serve);
  - 44 = dígito verificador em módulo 11 sobre as 43 primeiras.
  - Qualquer falha → `InvalidKeyException(reason)` com `reason` fixo (`LENGTH`, `UF`, `MONTH`,
    `CNPJ`, `MODEL`, `CHECK_DIGIT`), que vira 422 `receipt.key_invalid`.
- Módulo 11 da chave: pesos de 2 a 9 da direita para a esquerda, repetindo; `r = soma % 11`;
  dígito = `0` quando `r < 2`, senão `11 − r`. Cada posição vale `(caractere − 48)`, o que dá o
  dígito para `0–9` e serve para o CNPJ alfanumérico (D4).
- Saídas: `String uf()` (a sigla: `43` → `RS`, pela tabela do IBGE), `YearMonth issuedMonth()`,
  `String sellerCnpj()` (14 posições), `String digits()`.
- `toString()` não mostra a chave (devolve `NfceKey[***]`), para nunca cair num log por acaso.

**D3. Gerador de chaves de teste.**
- `markets/domain/NfceKeyFixtures` (em `src/test`) e `features/markets/testing/nfceFixtures.ts`:
  `make({uf, aamm, cnpjBase, serie, number, tpEmis})` monta a chave e calcula os dígitos do CNPJ e o
  da chave.
- `cnpjBase` é inventado (`"12345678"` + filial `"0001"`), nunca um CNPJ real.
- `withWrongCheckDigit(chave)` troca o último dígito.

**D4. Desvio do spec: CNPJ alfanumérico.**
- O spec fala em "44 dígitos". A Receita passa a emitir CNPJ alfanumérico (Instrução Normativa RFB
  2.229/2024), e a chave da NFC-e passa a aceitar letras no lugar do CNPJ.
- `Cnpj.isValid(String)`: 12 primeiras posições em `[0-9A-Z]`, 2 últimas em `[0-9]`; dígitos com os
  pesos `5,4,3,2,9,8,7,6,5,4,3,2` e `6,5,4,3,2,9,8,7,6,5,4,3,2`, valor `(caractere − 48)`, mesma
  regra do `r < 2`. Quatorze zeros (ou o mesmo caractere repetido) é inválido.
- No aparelho, a chave digitada aceita letras maiúsculas nas posições 7–18; o teclado continua
  numérico por padrão, com um botão "A chave tem letras".
- `markets.cnpj char(14)` já aceita letras.
- Pré-requisito: conferir a Nota Técnica em vigor antes da Task 1.

**D5. Desvio do spec: a chave vai no corpo da gravação.**
- O spec diz que a gravação manda "o hash". Este plano manda a chave de novo, e a API refaz a
  validação, calcula o hash e tira o CNPJ e o mês dela.
- Motivo: a API não deve confiar num hash nem num CNPJ calculado pelo aparelho. Com só o hash, uma
  pessoa poderia ligar qualquer CNPJ a qualquer mercado.
- A chave continua sem ser guardada nem registrada em log.

**D6. Hash.** `KeyHash.of(UUID workspaceId, NfceKey key)`: SHA-256 em hexadecimal minúsculo de
`"<workspaceId>|<chave>"`. O `workspaceId` no texto impede comparar hashes entre famílias.

**D7. `lookup`** (`POST /v1/workspaces/{ws}/markets/receipts/lookup`).
- Corpo `{chave, version, p?}`. `version`: `1`, `2`, `3`, ou `0` para a chave digitada ou colada sem
  QR.
- Regras do `p`:
  - `version` 1 e 0: sem `p` (senão 422 `receipt.key_invalid`);
  - `version` 2: `p` obrigatório, até 300 caracteres, começando com `<chave>|2|1|` (ambiente de
    produção). A API valida e **descarta** o `p` na 10.2; ele só será usado pelos leitores 10.2.x;
  - `version` 3: `p` igual a `<chave>|3|1`, exato. Qualquer outra forma (a offline, com o documento
    de quem comprou) → 422 `receipt.key_invalid`. O aparelho nunca deveria mandar; a API recusa se
    mandar.
  - `version` 0: a chave não diz o ambiente; vale como produção.
- Ordem: valida a chave (D2) → calcula o hash → nota já registrada: 409 `receipt.duplicate` → acha o
  mercado pelo CNPJ.
- Resposta `{mode: "MANUAL", uf, issuedMonth: "2026-11", market, items: []}`.
  - `market`: `{ref, familyId, name, kind, status}` do mercado com aquele CNPJ (arquivado também
    conta, porque o CNPJ é dele), ou `null`. `name` pode ser nulo num mercado do OSM sem nome.
- Nada é gravado.

**D8. Leitores de estado** (só a forma, sem leitor).
- `interface NfceStateReader { String uf(); }`: a 10.2 só define o tipo; o método de leitura e os
  registros do resultado entram com o primeiro 10.2.x, que conhece a página.
- Propriedades novas em `MarketsProperties`:
  - `nfce.fetch-states` (lista de siglas, vazia por padrão; `LIBERDADE_MARKETS_NFCE_FETCH_STATES`);
  - `nfce.states.<UF>.url` (mapa, sem uso na 10.2; o modelo da URL de cada estado).
- `MarketsStartupCheck` recusa subir quando um estado de `fetch-states` não tem um
  `NfceStateReader` com aquela `uf()`. Na 10.2, qualquer valor na lista impede a subida.
- O `lookup` da 10.2 nunca pergunta a leitor nenhum: o ramo `FETCHED` entra no primeiro 10.2.x.

**D9. Gravação** (`POST /v1/workspaces/{ws}/markets/receipts`).
- Corpo:
  ```
  {chave, version, p?, issuedOn: "YYYY-MM-DD",
   market: {ref} | null, newMarket: {name, kind} | null,
   items: [{productId? | newProductName?, unit, quantity?, unitPrice}]}
  ```
- Refaz D2 e D7 (chave e `p`). `mode` é sempre `MANUAL` nesta entrega; o corpo não escolhe.
- **Mercado** (exatamente um de `market` e `newMarket`, senão 422 `receipt.market_required`):
  - `market.ref` do OSM sem linha → `ensureOsmRow`; ref desconhecido → 404 `market.not_found`;
  - mercado com outro CNPJ → 409 `receipt.market_cnpj_differs`;
  - mercado sem CNPJ → recebe o CNPJ da chave e publica `MarketEdited(change = CNPJ)` (`CNPJ` entra
    no enum de `change`);
  - CNPJ já ligado a outro mercado (inclusive por corrida, pelo índice `markets_cnpj_once`) → 409
    `market.cnpj_taken`;
  - `newMarket`: nome de 1 a 80 depois de `strip()`, `kind` de `MarketKind`; cria um mercado
    `FAMILY` sem posição e com o CNPJ, pelo `FamilyMarketService` (publica `MarketAdded`).
- **Data:** `issuedOn` no mesmo ano e mês do AAMM da chave e não depois de hoje em São Paulo; senão
  422 `receipt.date_invalid`.
- **Itens:** de 1 a 200 (senão 422 `receipt.items_invalid`). Cada um:
  - exatamente um de `productId` (da família, senão 422 `receipt.product_unknown`) e
    `newProductName` (1 a 80 depois de `strip()`);
  - `unit` em `ProductUnit` (`UN`, `KG`, `G`, `L`, `ML`, `CX`, `PCT`, `DZ`);
  - `quantity` opcional, `^\d{1,15}(\.\d{1,4})?$` e maior que zero, senão 422
    `receipt.quantity_invalid`;
  - `unitPrice` com a mesma forma e maior que zero, senão 422 `receipt.price_invalid`. Cinco casas
    é erro, não arredonda.
- **Nome novo:** `ProductMatcher` (D11) com o nome normalizado como descrição. Apelido existente
  naquele mercado → usa o produto dele. Senão, cria o produto (`unit` do item) e o apelido
  (`INSERT … ON CONFLICT DO NOTHING`). Dois itens com o mesmo nome novo na mesma nota dão um produto
  só.
- Uma transação grava: mercado (se preciso), nota (`key_hash`, `issued_on`, `mode`, `item_count`,
  `created_by`), produtos, apelidos e um `price_records` por item (`observed_on = issuedOn`).
- Hash repetido (inclusive por corrida, pela `UNIQUE (workspace_id, key_hash)`) → 409
  `receipt.duplicate`, sem nada gravado.
- Resposta 201 `{receiptId, marketRef, prices}`, com `prices` = número de itens gravados.

**D10. Evento e auditoria.**

| Evento | Campos | Ação na auditoria | `targetType` |
|---|---|---|---|
| `ReceiptAdded` | `workspaceId`, `receiptId`, `actorUserId`, `mode`, `itemCount` | `receipt.added` (metadata `{mode, itemCount}`) | `RECEIPT` |

- `RECEIPT` entra na lista do Javadoc de `AuditEntry`.
- `MarketAdded` e `MarketEdited(CNPJ)` já são auditados pela 10.1.

**D11. Junção automática** (`markets/domain/ProductMatcher`, regra pura do spec 6.6).
- `static Match match(@Nullable String gtin, String descriptionNorm, ProductLookup lookup)`:
  1. `Gtin.isValid(gtin)` e `lookup.byGtin(gtin)` achou → `Existing(id)`;
  2. `lookup.byAlias(descriptionNorm)` achou → `Existing(id)`;
  3. senão → `New()`.
- `interface ProductLookup { Optional<UUID> byGtin(String gtin); Optional<UUID> byAlias(String descriptionNorm); }`.
  O serviço implementa com duas consultas na família e no mercado da nota.
- `sealed interface Match permits Existing, New`.
- `Gtin.isValid(String)`: 8, 12, 13 ou 14 dígitos, com o dígito do GS1 (pesos 3 e 1 a partir da
  direita, sem contar o último). `"SEM GTIN"` e vazio → falso.
- A 10.2 chama só o passo 2 (nome digitado, sem GTIN). Os passos 1 e 3 ficam testados para os
  10.2.x.

**D12. Preços no cartão e produtos.**
- `GET /v1/workspaces/{ws}/markets/{ref}/prices` → `{prices: [{productId, name, unit, unitPrice, observedOn}]}`:
  - o último preço de cada produto naquele mercado (`DISTINCT ON (product_id)` por
    `observed_on DESC, created_at DESC`), em ordem de nome, até 200;
  - `unitPrice` com 4 casas (`"4.9900"`);
  - mercado do OSM sem linha → lista vazia; ref desconhecido → 404 `market.not_found`.
- `GET /v1/workspaces/{ws}/markets/products?q=` → `{products: [{id, name, unit, gtin}]}`:
  - em ordem de nome, até 50;
  - `q` (até 80) filtra pelo nome normalizado contendo o `q` normalizado.
- As duas abertas aos quatro papéis.

**D13. Exportação.**
- `MarketsExport` ganha `products(ws)`, `aliases(ws)`, `prices(ws)` e `receipts(ws)`, com os
  registros públicos `ExportedProduct`, `ExportedAlias`, `ExportedPrice` e `ExportedReceipt`.
- Arquivos novos na exportação da família:
  - `feira-produtos.csv`: `id;nome;unidade;gtin;criado_em`;
  - `feira-apelidos.csv`: `mercado_id;descricao_normalizada;produto_id`;
  - `feira-precos.csv`: `produto_id;mercado_id;nota_id;data;unidade;quantidade;preco_unitario`;
  - `feira-notas.csv`: `id;mercado_id;data;modo;itens;criado_em`, sem a chave nem o hash.
- **A mais que o spec:** `feira-apelidos.csv`. Os apelidos são texto da família (nesta entrega, o
  nome digitado; nos 10.2.x, a descrição da nota), então entram.
- Os ids de mercado são os de `feira-mercados.csv` (10.1).
- `FORMAT_VERSION` vai para a seguinte à do `main` (pré-requisito).
- Exclusão: em cascata pela `workspace_id`; `created_by` segue o padrão sem FK da 10.1.

**D14. Logs.** Nada da nota em log: nem chave, nem `p`, nem CNPJ, nem nome de produto, nem preço.
O teste de log da Task 3 grava uma nota e procura cada um desses valores na saída.

**D15. Web: ler e limpar** (`features/markets/receipt/nfceQr.ts`, puro).
- `parseNfceInput(raw: string, today: Date): ParsedNfce | NfceInputError`:
  - `ParsedNfce = {chave, version: 0 | 1 | 2 | 3, p?: string, issuedOn?: string, sefazUrl?: string}`;
  - `NfceInputError = {error: 'not-nfce' | 'check-digit' | 'model' | 'homologation' | 'format'}`.
- Entradas aceitas: a URL do QR, ou a chave com espaços.
- **v1** (`chNFe=…&nVersao=100&tpAmb=…&cDest=…&dhEmi=<hex>…`):
  - `tpAmb` diferente de 1 → `homologation`;
  - devolve `version: 1` e só a chave. `cDest` (o documento de quem comprou) não sai da função;
  - `issuedOn` vem do `dhEmi`, que é a data em ISO codificada em hexadecimal.
- **v2** (`p=chave|2|tpAmb|…`):
  - online tem 5 campos e offline 8 (`chave|2|tpAmb|dia|vNF|digVal|idCSC|hash`);
  - `tpAmb` diferente de 1 → `homologation`;
  - devolve o `p` inteiro; na offline, `issuedOn` = `dia` + AAMM.
- **v3** (`p=chave|3|tpAmb` online; `chave|3|tpAmb|dia|vNF|tpIdDest|idDest|assinatura` offline):
  - `tpAmb` diferente de 1 → `homologation`;
  - devolve sempre `p = <chave>|3|1`. `tpIdDest` e `idDest` (o CPF ou CNPJ de quem comprou) não saem
    da função;
  - na offline, `issuedOn` = `dia` + AAMM.
- Chave digitada (44 posições, D4): `version: 0`, sem `p`.
- A chave passa pelas mesmas conferências de D2 (o mesmo módulo 11), para o erro aparecer antes de
  ir à API.
- `sefazUrl`: a URL original, só quando o esquema é `http` ou `https`. Fica só na memória da tela;
  nunca vai para a API nem para o `localStorage`.
- `defaultIssuedOn(parsed, today)`: o `issuedOn` do QR; senão hoje, se hoje está no AAMM; senão
  vazio, e o campo vira obrigatório.

**D16. Web: câmera** (`features/markets/receipt/scanner/`).
- `QrScanner.tsx`, num chunk próprio, montado só depois do toque em "Ler o QR":
  - `getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })`;
  - `BarcodeDetector` quando existe e `getSupportedFormats()` tem `qr_code`;
  - senão, `qrWorker.ts` (`new Worker(new URL('./qrWorker.ts', import.meta.url), { type: 'module' })`)
    com `jsQR`. O quadro vai ao worker como `ImageData`, um por vez, a cada 200 ms;
  - para todas as faixas da câmera ao achar o QR, ao desmontar e quando a aba fica oculta;
  - permissão negada ou sem câmera → mensagem e as alternativas (colar, digitar).
- **Sem aprovação do `jsqr`:** o worker não entra; sem `BarcodeDetector`, o botão "Ler o QR" não
  aparece e ficam só colar e digitar.
- `jsqr` com versão exata no `package.json` (sem `^`), como `dexie` e `echarts`. O implementador
  confere a última versão no npm e a licença Apache-2.0 e anota no relatório.
- `Permissions-Policy`: `camera=()` vira `camera=(self)`.
- `check-chunks.mjs`: os arquivos do `scanner/` entram na lista `lazyOnly`.
- CSP: `worker-src 'self'` já existe; nada muda.

**D17. Web: a tela da nota** (`/mais/feira/nota`, `ReceiptPage.tsx`).
- Entrada: "Registrar nota" na lista da Feira e no cartão, só para Dono e Adulto.
- Passo 1, "A nota": "Ler o QR", "Colar o link" e "Digitar a chave" (44 posições em grupos de 4 na
  tela). Erro de D15 aparece ali. Chama o `lookup`:
  - 409 → "Esta nota já foi registrada.";
  - 422 → "Esta chave não parece de uma nota fiscal de consumidor válida.".
- Passo 2, "O mercado":
  - com `market` → mostra o mercado ("Esta nota é deste mercado"), sem troca, porque o CNPJ já é
    dele;
  - sem → os mercados perto (`useMarketSearch`, com a localização que a pessoa já escolheu; sem
    nenhuma, o `LocationChooser`) e "Outro mercado", com nome e tipo.
- Passo 3, "Os preços":
  - o aviso "A leitura automática ainda não existe para notas de {uf}." e, com `sefazUrl`, o link
    "Abrir a nota no site da SEFAZ" (`target="_blank"`, `rel="noopener noreferrer"`);
  - a data (padrão de D15);
  - linhas de item: produto (busca em `GET /markets/products?q`, ou "Novo: {nome}"), unidade,
    quantidade opcional e preço. O preço usa `parseTypedAmount` e `centsToApi` de `shared/money`
    (2 casas na digitação; a API aceita até 4);
  - "Adicionar item", "Remover", no máximo 200.
- Fim: "Preços registrados. A nota não vira um gasto; para lançar, use Lançar." e o link para o
  cartão do mercado.
- O estado da tela fica só na memória do componente. Sair da tela descarta tudo; nada vai para o
  `localStorage`.
- Depois de gravar, invalida `['ws', ws, 'markets']` (cartão, preços, produtos e busca).

**D18. Web: preços no cartão** (`MarketPrices.tsx`).
- Lista o último preço de cada produto: nome, unidade e "R$ 4,99 · 12/11/2026". O preço de 4 casas é
  arredondado HALF_UP para centavos antes do `formatMoney` (`features/markets/price.ts`,
  `unitPriceToCents(text): bigint`).
- Vazio: "Nenhum preço registrado ainda." e, para Dono e Adulto, "Registrar nota".

**D19. Runbook** (`docs/runbooks/nfce-states.md`, spec 6.5).
- Candidatos (robots.txt de 24/11/2026): RS, MG, BA e PR. Bloqueados: SC, SP e RJ, com o motivo do
  spec.
- Vários estados usam o portal do SVRS; um leitor só pode servir a todos.
- Lista de conferência por estado: robots.txt, termos de uso, captcha, uma nota real aberta à mão
  com a data e o resultado.
- Como salvar a página: "Salvar como" no navegador, apagar a parte do consumidor (CPF, nome) num
  editor de texto, trocar por dados inventados e guardar **fora do repositório**.
- As cinco regras dos planos 10.2.x (spec 6.5, "Leitor por estado").
- Uma tabela vazia "Estado | Data | Resultado | Plano" para o mantenedor preencher.

## Review Focus

1. **CPF saindo do aparelho:** QR v3 offline ou v1 com `cDest`, colado ou lido. Testes:
   `nfceQr.test.ts › v3OfflineKeepsNoBuyerDocument`, `...v1DropsCDest` e
   `ReceiptPage.test.tsx › lookupBodyHasNoBuyerDocument` (Tasks 6 e 8); na API,
   `ReceiptLookupIT.v3OfflineFormIs422` (Task 2).
2. **Chave em log, auditoria ou exportação:** gravar uma nota e procurar a chave em toda a saída.
   Testes: `ReceiptSaveIT.logsCarryNoReceiptData`, `ReceiptsAuditIT.metadataOnlyModeAndCount` e
   `ExportReceiptsIT.noKeyOrHashInExport` (Tasks 3 e 5).
3. **A mesma nota duas vezes:** duas gravações ao mesmo tempo, ou um `lookup` depois da gravação.
   Testes: `ReceiptSaveIT.concurrentSavesOneWins` e `ReceiptLookupIT.savedKeyIs409` (Tasks 2 e 3).
4. **CNPJ ligado ao mercado errado:** escolher um mercado que já tem outro CNPJ, ou dois mercados
   disputando o mesmo CNPJ. Testes: `ReceiptSaveIT.marketWithOtherCnpjIs409` e
   `...cnpjOnAnotherMarketIs409` (Task 3).
5. **Preço e data fora da forma:** preço com 5 casas, zero, vírgula; data de outro mês ou de amanhã.
   Testes: `ReceiptSaveIT.fiveDecimalsIs422`, `...dateOutsideKeyMonthIs422` e
   `...tomorrowIs422` (Task 3).

## Mapa de arquivos

Caminhos da API relativos a `api/src/main/java/br/com/liberdadefamilia/` (testes em
`api/src/test/java/br/com/liberdadefamilia/`); do web, relativos a `web/src/`.

| Arquivo | Task | Papel |
|---|---|---|
| `api/src/main/resources/db/migration/V41__receipts.sql` | 1 | tabelas da 10.2 |
| `markets/domain/{NfceKey,Cnpj,Gtin,KeyHash,ProductUnit,ProductMatcher,ProductLookup,InvalidKeyException}.java` | 1 | regras puras |
| `markets/domain/NfceKeyFixtures.java` (teste) | 1 | gerador de chaves inventadas |
| `markets/internal/{ReceiptController,ReceiptLookupService,ReceiptRepository,LookupRequest,NfceStateReader}.java` | 2 | `lookup` |
| `markets/internal/{MarketsProperties,MarketsStartupCheck}.java` | 2 | `nfce.*` |
| `markets/internal/{ReceiptSaveService,SaveReceiptRequest,ProductRepository,PriceRepository}.java`, `markets/ReceiptAdded.java`, `markets/MarketEdited.java` | 3 | gravação |
| `markets/internal/{MarketPricesController,MarketPricesService}.java` | 4 | preços e produtos |
| `markets/MarketsExport.java`, `markets/internal/JdbcMarketsExport.java`, `privacy/internal/ExportService.java`, `audit/internal/AuditListeners.java`, `audit/AuditEntry.java` | 5 | exportação e auditoria |
| `features/markets/receipt/{nfceQr.ts,testing/nfceFixtures.ts}`, `features/markets/{useReceipts.ts,price.ts}`, `mocks/{handlers.ts,data.ts}` | 6 | leitura no aparelho e MSW |
| `features/markets/receipt/scanner/{QrScanner.tsx,qrWorker.ts,detectQr.ts}`, `web/config/securityHeaders.ts`, `web/scripts/check-chunks.mjs`, `web/package.json` | 7 | câmera |
| `features/markets/receipt/{ReceiptPage.tsx,ReceiptKeyStep.tsx,ReceiptMarketStep.tsx,ReceiptItemsStep.tsx,ProductPicker.tsx}`, `features/markets/MarketPrices.tsx`, `app/routes.tsx` | 8 | tela da nota e preços no cartão |
| `docs/runbooks/nfce-states.md`, `web/e2e/{feira-nota.spec.ts,a11y.spec.ts}`, `docs/{architecture.md,data-model.md}` | 9 | runbook, e2e e docs |

## Tasks

### Task 1: V41 e as regras da nota

**Files:**
- Create: `db/migration/V41__receipts.sql`, os arquivos de `markets/domain` da Task 1 no mapa.
- Test: `markets/domain/{NfceKeyTest,CnpjTest,GtinTest,KeyHashTest,ProductMatcherTest}.java`,
  `markets/ReceiptsSchemaIT.java`.

**Interfaces:**
- Produces:
  - `record NfceKey(String digits)` com `static NfceKey parse(String raw, YearMonth current)`,
    `String uf()`, `YearMonth issuedMonth()`, `String sellerCnpj()`;
  - `InvalidKeyException(Reason reason)`, `enum Reason { LENGTH, UF, MONTH, CNPJ, MODEL, CHECK_DIGIT }`;
  - `Cnpj.isValid(String): boolean`;
  - `Gtin.isValid(@Nullable String): boolean`;
  - `KeyHash.of(UUID workspaceId, NfceKey key): String`;
  - `enum ProductUnit { UN, KG, G, L, ML, CX, PCT, DZ }`;
  - `ProductMatcher.match(@Nullable String gtin, String descriptionNorm, ProductLookup lookup): Match`,
    `sealed interface Match permits Existing, New`, `record Existing(UUID productId)`,
    `record New()`;
  - `NfceKeyFixtures.make(...)` e `withWrongCheckDigit(String)` (teste).

**Como fica:**
- A V41 é o DDL da seção 6.3 do spec (V41), mais:
  - `CHECK (char_length(name) BETWEEN 1 AND 80)` e `CHECK (unit IN (…))` com os oito valores em
    `products`;
  - `CHECK (unit IN (…))` e `CHECK (quantity IS NULL OR quantity > 0)` em `price_records`;
  - `CHECK (item_count BETWEEN 1 AND 200)` em `receipts`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `NfceKeyTest.parsesAValidKey` | chave de `make(uf 43, aamm 2611)` → `uf()` = `RS`, `issuedMonth()` = 2026-11, `sellerCnpj()` = o CNPJ inventado |
| `NfceKeyTest.acceptsSpacesAndDots` | a mesma chave em grupos de 4 com espaços → igual |
| `NfceKeyTest.rejectsEachReason` | 43 posições → `LENGTH`; cUF `99` → `UF`; mês `13` → `MONTH`; AAMM depois de `current` → `MONTH`; CNPJ com dígito errado → `CNPJ`; modelo `55` → `MODEL`; `withWrongCheckDigit` → `CHECK_DIGIT` |
| `NfceKeyTest.toStringHidesTheKey` | `toString()` não contém nenhum trecho de 6 dígitos da chave |
| `CnpjTest.numericAndAlphanumeric` | um CNPJ numérico inventado e um alfanumérico inventado (dígitos calculados) → válidos; mesmo caractere repetido → inválido; letra numa das 2 últimas → inválido |
| `GtinTest.checkDigits` | um GTIN-8, um GTIN-13 e um GTIN-14 inventados com dígito certo → verdadeiro; dígito errado, 11 dígitos, `"SEM GTIN"`, nulo → falso |
| `KeyHashTest.dependsOnTheFamily` | a mesma chave em duas famílias → hashes diferentes; 64 caracteres hexadecimais minúsculos |
| `ProductMatcherTest.gtinFirst` | GTIN válido achado e apelido de outro produto → o do GTIN |
| `ProductMatcherTest.aliasWhenNoGtin` | GTIN nulo ou inválido, apelido achado → `Existing` do apelido |
| `ProductMatcherTest.newOtherwise` | nada achado → `New` |
| `ReceiptsSchemaIT.keyHashOncePerFamily` | duas notas com o mesmo `key_hash` na família → violação; em famílias diferentes → passa |
| `ReceiptsSchemaIT.priceMustBePositive` | `unit_price = 0` → violação |
| `ReceiptsSchemaIT.gtinOncePerFamily` | dois produtos com o mesmo GTIN na família → violação |
| `ReceiptsSchemaIT.cascadeWithMarket` | apagar o mercado apaga apelidos, notas e preços dele |

- [ ] **Step 2: Rodar e ver falhar.** Em `api/`: `./gradlew test --tests '*markets*'` → FAIL.
- [ ] **Step 3: Implementar.** D2, D4, D6 e D11, a V41 e o gerador D3.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando → PASS.
- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit.**
  `git commit -m "feat(markets): add the v41 receipt tables and the nfc-e key rules"`.

---

### Task 2: `lookup` e as propriedades da NFC-e

**Files:**
- Create: os arquivos da Task 2 no mapa.
- Modify: `markets/internal/{MarketsProperties,MarketsStartupCheck}.java`,
  `api/src/main/resources/application*.yml` (as chaves `nfce.*`), `api/openapi.json`,
  `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/ReceiptLookupIT.java`, `markets/internal/MarketsStartupCheckTest.java`,
  `RoleMatrixIT`.

**Interfaces:**
- Consumes: `NfceKey`, `KeyHash` (Task 1); `MarketRepository` e `MarketViews` (10.1).
- Produces:
  - `ReceiptRepository.existsByHash(UUID ws, String keyHash): boolean`;
  - `MarketRepository.findByCnpj(UUID ws, String cnpj): Optional<MarketRow>` (método novo no
    repositório da 10.1);
  - `ReceiptLookupService.validate(UUID ws, LookupRequest body): ValidatedKey`, com
    `record ValidatedKey(NfceKey key, String keyHash)`, que a Task 3 reusa;
  - `record LookupRequest(String chave, int version, @Nullable String p)`;
  - `interface NfceStateReader { String uf(); }`;
  - `MarketsProperties.nfce()` com `List<String> fetchStates()` e `Map<String, StateConfig> states()`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ReceiptLookupIT.manualWithoutReader` | chave válida, sem mercado com o CNPJ → 200 `{mode: MANUAL, uf: RS, issuedMonth: 2026-11, market: null, items: []}` |
| `ReceiptLookupIT.findsMarketByCnpj` | mercado da família com o CNPJ → `market.familyId` dele |
| `ReceiptLookupIT.archivedMarketStillMatches` | o mesmo mercado arquivado → ainda vem, com `status: ARCHIVED` |
| `ReceiptLookupIT.savedKeyIs409` | `receipts` já tem o hash → 409 `receipt.duplicate` |
| `ReceiptLookupIT.badKeyIs422` | `withWrongCheckDigit` → 422 `receipt.key_invalid` |
| `ReceiptLookupIT.v2NeedsProductionP` | `version 2` sem `p`, com `p` de outra chave, com `tpAmb = 2` ou com 301 caracteres → 422; `p` online certo → 200 |
| `ReceiptLookupIT.v3OfflineFormIs422` | `version 3` com `p` de 8 campos (CPF inventado `12345678909`) → 422; `<chave>\|3\|1` → 200 |
| `ReceiptLookupIT.typedKeyHasNoP` | `version 0` com `p` → 422 |
| `ReceiptLookupIT.writesNothing` | depois de 3 `lookup`, `receipts`, `markets` e `products` sem linhas novas |
| `MarketsStartupCheckTest.fetchStateWithoutReaderRefused` | `fetch-states = [RS]` sem leitor → a subida falha com mensagem que cita `RS` |
| `RoleMatrixIT` | `lookup`: Dono e Adulto 200; Jovem e Leitor 403 `auth.forbidden`; módulo desligado 403 `module.disabled` |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D7 e D8. O mesmo `validate` faz D2, D7 e o 409.
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o `openapi.json` e o `schema.ts`.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): look up an nfc-e key and its market by cnpj"`.

---

### Task 3: Gravar a nota e os preços

**Files:**
- Create: os arquivos da Task 3 no mapa.
- Modify: `markets/MarketEdited.java` (`CNPJ` no enum), `markets/internal/ReceiptController.java`,
  `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/ReceiptSaveIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `ReceiptLookupService.validate` (Task 2); `ProductMatcher`, `ProductUnit` (Task 1);
  `MarketRepository.findByRef`, `ensureOsmRow`, `FamilyMarketService` (10.1).
- Produces:
  - `record ReceiptAdded(UUID workspaceId, UUID receiptId, UUID actorUserId, String mode, int itemCount)`;
  - `MarketEdited.Change.CNPJ`;
  - `ProductRepository` com `byGtin`, `byAlias(UUID ws, UUID marketId, String norm)`,
    `create(UUID ws, String name, ProductUnit unit): UUID`,
    `addAlias(UUID ws, UUID marketId, String norm, UUID productId)`, `search(UUID ws, String q, int limit)`;
  - `PriceRepository.insert(…)` e `latestByMarket(UUID ws, UUID marketId, int limit)`, que a Task 4
    usa.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ReceiptSaveIT.savesReceiptAndPrices` | mercado da família, 2 itens (um produto existente, um nome novo) → 201 `{prices: 2}`; 1 nota com `mode = MANUAL` e `item_count = 2`, 2 preços, 1 produto novo e 1 apelido |
| `ReceiptSaveIT.osmMarketGetsARowAndTheCnpj` | `market.ref = osm-n-…` sem linha → linha `OSM` criada com o CNPJ; `MarketEdited(CNPJ)` publicado |
| `ReceiptSaveIT.newMarketIsFamilyWithoutPosition` | `newMarket {name, kind}` → mercado `FAMILY` sem posição, com o CNPJ; `MarketAdded` publicado |
| `ReceiptSaveIT.marketWithOtherCnpjIs409` | mercado com outro CNPJ → 409 `receipt.market_cnpj_differs`, nada gravado |
| `ReceiptSaveIT.cnpjOnAnotherMarketIs409` | o CNPJ da chave já está noutro mercado e a pessoa escolhe um sem CNPJ → 409 `market.cnpj_taken` |
| `ReceiptSaveIT.bothOrNeitherMarketIs422` | `market` e `newMarket` juntos, ou nenhum → 422 `receipt.market_required` |
| `ReceiptSaveIT.sameNewNameTwiceIsOneProduct` | dois itens com "Leite Exemplo 1L" e "leite exemplo 1l" → 1 produto, 2 preços |
| `ReceiptSaveIT.existingAliasReusesProduct` | apelido "leite exemplo 1l" já existe no mercado → o nome novo usa o produto dele |
| `ReceiptSaveIT.foreignProductIs422` | `productId` de outra família → 422 `receipt.product_unknown` |
| `ReceiptSaveIT.fiveDecimalsIs422` | `unitPrice "4.99001"` → 422 `receipt.price_invalid`; `"0"`, `"-1"`, `"4,99"` também |
| `ReceiptSaveIT.fourDecimalsKept` | `unitPrice "29.9990"` → gravado `29.9990` |
| `ReceiptSaveIT.dateOutsideKeyMonthIs422` | AAMM 2611 e `issuedOn 2026-10-31` → 422 `receipt.date_invalid` |
| `ReceiptSaveIT.tomorrowIs422` | relógio em 2026-11-24 (São Paulo), `issuedOn 2026-11-25` → 422 |
| `ReceiptSaveIT.itemsLimits` | 0 itens e 201 itens → 422 `receipt.items_invalid` |
| `ReceiptSaveIT.secondSaveIs409` | a mesma chave duas vezes → 409 `receipt.duplicate` |
| `ReceiptSaveIT.concurrentSavesOneWins` | duas gravações da mesma chave em duas threads → uma 201 e uma 409; 1 nota, preços de uma só |
| `ReceiptSaveIT.failedSaveLeavesNothing` | item inválido no fim da lista → nenhum mercado, produto, apelido ou preço novo |
| `ReceiptSaveIT.logsCarryNoReceiptData` | com `OutputCaptureExtension`, gravar uma nota → a saída não contém a chave, o CNPJ, "Leite Exemplo" nem `4.99` |
| `ReceiptSaveIT.receiptAddedPublished` | `ReceiptAdded` com `mode MANUAL` e `itemCount 2` (`@RecordApplicationEvents`) |
| `RoleMatrixIT` | gravar: Dono e Adulto 201; Jovem e Leitor 403 `auth.forbidden` |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D9, D10 (o evento) e D14. As violações de `UNIQUE` viram 409 pelo nome
  da constraint (`receipts_workspace_id_key_hash_key` e `markets_cnpj_once`; conferir o nome que o
  PostgreSQL deu).
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): save a receipt with its prices in one transaction"`.

---

### Task 4: Preços no cartão e produtos da família

**Files:**
- Create: os arquivos da Task 4 no mapa.
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/MarketPricesIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `PriceRepository.latestByMarket`, `ProductRepository.search` (Task 3).
- Produces: as duas rotas de D12, que as Tasks 6 e 8 usam.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MarketPricesIT.latestPerProduct` | dois preços do mesmo produto (10/11 e 20/11) → só o de 20/11 |
| `MarketPricesIT.sameDayLatestCreatedWins` | dois no mesmo dia → o gravado por último |
| `MarketPricesIT.fourDecimalsAsText` | `4.99` → `"4.9900"` |
| `MarketPricesIT.osmWithoutRowIsEmpty` | `osm-n-…` sem linha → `{prices: []}` |
| `MarketPricesIT.unknownRefIs404` | uuid que não é da família → 404 `market.not_found` |
| `MarketPricesIT.productsSortedAndFiltered` | "Arroz Exemplo", "Leite Exemplo", "Leite Integral Exemplo"; `q=leite` → os dois de leite, em ordem; `q=LÉITE` → o mesmo |
| `MarketPricesIT.noPricesFromOtherFamily` | preço de outra família no mesmo mercado do OSM não aparece |
| `RoleMatrixIT` | as duas rotas: os quatro papéis 200 |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D12.
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): show the latest prices on the market card"`.

---

### Task 5: Exportação, auditoria e exclusão

**Files:**
- Modify: `markets/MarketsExport.java`, `markets/internal/JdbcMarketsExport.java`,
  `privacy/internal/ExportService.java`, `audit/internal/AuditListeners.java`,
  `audit/AuditEntry.java`, os testes de exportação existentes.
- Test: `privacy/ExportReceiptsIT.java`, `audit/ReceiptsAuditIT.java`, `privacy/FamilyDeletionIT`.

**Interfaces:**
- Consumes: `ReceiptAdded` (Task 3).
- Produces: `MarketsExport.products/aliases/prices/receipts` e os registros públicos de D13.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ExportReceiptsIT.familyExportHasReceiptFiles` | os quatro CSVs de D13 com os cabeçalhos exatos e as linhas da nota gravada |
| `ExportReceiptsIT.noKeyOrHashInExport` | o zip não contém a chave nem o hash |
| `ExportReceiptsIT.marketIdsMatchMarketsCsv` | `mercado_id` de `feira-precos.csv` existe em `feira-mercados.csv` |
| `ExportReceiptsIT.formatVersionBumped` | `formatVersion` = a do `main` + 1 |
| `ReceiptsAuditIT.metadataOnlyModeAndCount` | `receipt.added`, `targetType RECEIPT`, metadata exatamente `{mode, itemCount}` |
| `FamilyDeletionIT` | apagar a família apaga `products`, `product_aliases`, `receipts` e `price_records` |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D10 (auditoria) e D13.
- [ ] **Step 4: Rodar e ver passar** → PASS.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): export and audit receipts and prices"`.

---

### Task 6: Leitura do QR no aparelho e o MSW

**Files:**
- Create: `features/markets/receipt/nfceQr.ts`, `features/markets/receipt/testing/nfceFixtures.ts`,
  `features/markets/useReceipts.ts`, `features/markets/price.ts`.
- Modify: `mocks/handlers.ts`, `mocks/data.ts`, `features/markets/messages.pt-BR.ts`.
- Test: `features/markets/receipt/nfceQr.test.ts`, `features/markets/price.test.ts`,
  `mocks/receiptsHandlers.test.ts`.

**Interfaces:**
- Consumes: as rotas das Tasks 2 a 4 (pelo `schema.ts`).
- Produces:
  - `parseNfceInput(raw: string, today: Date): ParsedNfce | NfceInputError` e
    `defaultIssuedOn(parsed: ParsedNfce, today: Date): string | undefined` (D15);
  - `lookupBody(parsed: ParsedNfce): { chave: string; version: 0 | 1 | 2 | 3; p?: string }`, a única
    forma de montar o corpo do `lookup` e da gravação;
  - `useReceiptLookup(ws)`, `useSaveReceipt(ws)`, `useMarketPrices(ws, ref)`,
    `useFamilyProducts(ws, q)`;
  - `unitPriceToCents(text: string): bigint` (D18);
  - `makeChave(...)` (teste), a mesma regra do D3.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `nfceQr.test.ts › v1DropsCDest` | URL v1 com `cDest=12345678909` → `{version: 1, chave}`; `JSON.stringify(lookupBody(…))` sem `12345678909` |
| `nfceQr.test.ts › v1DateFromHex` | `dhEmi` com a data codificada em hexadecimal → `issuedOn` certo |
| `nfceQr.test.ts › v2OnlineAndOffline` | as duas formas → `p` inteiro; a offline com `issuedOn` do `dia` |
| `nfceQr.test.ts › v3OfflineKeepsNoBuyerDocument` | v3 offline com `idDest=12345678909` → `p` = `<chave>\|3\|1`; nenhum campo do resultado contém `12345678909` |
| `nfceQr.test.ts › homologationRefused` | `tpAmb=2` em v1, v2 e v3 → `homologation` |
| `nfceQr.test.ts › typedKey` | 44 posições com espaços → `version: 0`, sem `p`, sem `sefazUrl` |
| `nfceQr.test.ts › badCheckDigitAndModel` | dígito trocado → `check-digit`; modelo 55 → `model`; outro QR qualquer → `not-nfce` |
| `nfceQr.test.ts › sefazUrlOnlyHttp` | QR com `javascript:` → sem `sefazUrl` |
| `nfceQr.test.ts › defaultDate` | sem data no QR e hoje no AAMM → hoje; fora do AAMM → `undefined` |
| `price.test.ts › roundsHalfUpToCents` | `"4.9950"` → `500n`; `"4.9949"` → `499n` |
| `receiptsHandlers.test.ts` | o MSW responde ao `lookup` (`MANUAL`, `market: null`), à gravação (201) e aos preços e produtos com dados inventados |

- [ ] **Step 2: Rodar e ver falhar.** Em `web/`: `npx vitest run src/features/markets src/mocks` →
  FAIL.
- [ ] **Step 3: Implementar.** D15, D18 (só `price.ts`) e os hooks.
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint` e `typecheck`.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): read and clean the nfc-e qr on the device"`.

---

### Task 7: A câmera

**Files:**
- Create: `features/markets/receipt/scanner/{QrScanner.tsx,qrWorker.ts,detectQr.ts}`.
- Modify: `web/config/securityHeaders.ts`, `web/scripts/check-chunks.mjs`, `web/package.json` e o
  lockfile (só com a aprovação do `jsqr`), `features/markets/messages.pt-BR.ts`.
- Test: `features/markets/receipt/scanner/{QrScanner.test.tsx,detectQr.test.ts}`,
  `web/config/securityHeaders.test.ts`, `web/scripts/chunk-budget.test.mjs`.

**Interfaces:**
- Consumes: nada das outras tasks.
- Produces: `<QrScanner onResult={(text: string) => void} onUnavailable={() => void} />`, montado
  pela Task 8 sob demanda (`lazy(() => import('./scanner/QrScanner'))`).
- `detectQr.ts`: `pickDetector(win): 'native' | 'worker' | 'none'`, que escolhe pela presença do
  `BarcodeDetector` com `qr_code` e pela aprovação do `jsqr` (constante de build
  `JSQR_ENABLED`, definida no próprio arquivo).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `QrScanner.test.tsx › asksForTheBackCamera` | `getUserMedia` chamado com `facingMode: { ideal: 'environment' }` e `audio: false` |
| `QrScanner.test.tsx › stopsTracksOnResult` | detector falso devolve um texto → `onResult` e `stop()` em cada faixa |
| `QrScanner.test.tsx › stopsTracksOnUnmount` | desmontar → `stop()` em cada faixa |
| `QrScanner.test.tsx › stopsWhenHidden` | `visibilitychange` com `hidden` → `stop()` |
| `QrScanner.test.tsx › deniedShowsAlternatives` | `NotAllowedError` → mensagem e `onUnavailable` |
| `detectQr.test.ts › picksNativeFirst` | `BarcodeDetector` com `qr_code` → `native`; sem ele e com `jsqr` → `worker`; sem os dois → `none` |
| `securityHeaders.test.ts` | `Permissions-Policy` com `camera=(self)` e `geolocation=(self)`; o resto igual |
| `chunk-budget.test.mjs` | os arquivos do `scanner/` estão na lista `lazyOnly` |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D16. Sem a aprovação do `jsqr`: o `qrWorker.ts` não é criado,
  `JSQR_ENABLED = false` e o teste do `worker` sai.
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint`, `typecheck`, `build` e `check:chunks`
  (o leitor num chunk próprio, fora do pacote inicial).
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): scan the receipt qr with the back camera"`.

---

### Task 8: A tela da nota e os preços no cartão

**Files:**
- Create: os arquivos da Task 8 no mapa.
- Modify: `app/routes.tsx`, `features/markets/{FeiraPage.tsx,MarketCardPage.tsx,messages.pt-BR.ts}`.
- Test: `features/markets/receipt/{ReceiptPage.test.tsx,ReceiptItemsStep.test.tsx,ProductPicker.test.tsx}`,
  `features/markets/MarketPrices.test.tsx`, `MarketCardPage.test.tsx`.

**Interfaces:**
- Consumes: `parseNfceInput`, `defaultIssuedOn`, `lookupBody`, os hooks e `unitPriceToCents`
  (Task 6); `QrScanner` (Task 7); `useMarketSearch`, `readLocation`, `LocationChooser`,
  `SourceStamp` (10.1); `parseTypedAmount`, `centsToApi` e `useFormatMoney` (`shared`).
- Produces: a rota `/mais/feira/nota` e `<MarketPrices ws ref canRegister />`. A 10.3 acrescenta os
  itens da lista ao `ProductPicker`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ReceiptPage.test.tsx › lookupBodyHasNoBuyerDocument` | colar uma URL v3 offline com CPF inventado → o corpo do `lookup` (capturado no MSW) não contém o CPF e tem `p` = `<chave>\|3\|1` |
| `ReceiptPage.test.tsx › duplicateMessage` | `lookup` 409 → "Esta nota já foi registrada." |
| `ReceiptPage.test.tsx › knownMarketCannotChange` | `lookup` com `market` → mostra o mercado, sem a lista de mercados perto |
| `ReceiptPage.test.tsx › unknownMarketOffersNearbyAndNew` | `market: null` → os mercados da busca e "Outro mercado" |
| `ReceiptPage.test.tsx › sefazLinkNeverSent` | com `sefazUrl`, o link aparece com `rel="noopener noreferrer"`; nenhum pedido ao MSW contém a URL |
| `ReceiptPage.test.tsx › typedKeyHasNoSefazLink` | chave digitada → sem o link |
| `ReceiptPage.test.tsx › nothingInLocalStorage` | depois de passar pelos 3 passos, nenhuma chave do `localStorage` contém a chave da nota |
| `ReceiptPage.test.tsx › endsWithTheCopy` | gravação 201 → "Preços registrados. A nota não vira um gasto; para lançar, use Lançar." |
| `ReceiptPage.test.tsx › hiddenForYouthAndViewer` | Jovem e Leitor → a rota mostra "Sem permissão" (o padrão do app) e os botões "Registrar nota" não aparecem |
| `ReceiptItemsStep.test.tsx › priceAsDecimalText` | digitar `4,99` → `unitPrice: "4.99"` no corpo |
| `ReceiptItemsStep.test.tsx › dateRequiredWhenUnknown` | sem data padrão → "Gravar" desligado até preencher |
| `ProductPicker.test.tsx › searchOrNew` | digitar "leite" → produtos do MSW e "Novo: leite" |
| `MarketPrices.test.tsx › showsPriceAndDate` | `"4.9900"` de 12/11/2026 → "R$ 4,99 · 12/11/2026" |
| `MarketPrices.test.tsx › emptyState` | sem preços → "Nenhum preço registrado ainda."; Dono vê "Registrar nota", Jovem não |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D17 e D18.
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint`, `typecheck`, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): register a receipt by hand and show its prices"`.

---

### Task 9: Runbook, e2e e docs

**Files:**
- Create: `docs/runbooks/nfce-states.md`, `web/e2e/feira-nota.spec.ts`.
- Modify: `web/e2e/a11y.spec.ts`, `docs/architecture.md`, `docs/data-model.md`.
- Test: `web/e2e/feira-nota.spec.ts`, `web/e2e/a11y.spec.ts`.

**Como fica:**
- Runbook: D19.
- `feira-nota.spec.ts` (perfil `e2e` da API, fontes falsas da 10.1):
  1. Dono liga a Feira, escolhe "Digitar um endereço" e vê os mercados de exemplo.
  2. "Registrar nota" → "Digitar a chave" com uma chave inventada (`makeChave` copiado para o e2e).
  3. O mercado não é achado; escolhe "Mercado Exemplo 1".
  4. Um item novo, "Leite Exemplo 1 L", `UN`, 4,99; grava e vê a frase final.
  5. Abre o cartão e vê "R$ 4,99" com a data.
  6. Registrar a mesma chave de novo → "Esta nota já foi registrada.".
  7. Com `page.route` registrando os pedidos, confere que nenhum foi a um host de fora do app.
- `a11y.spec.ts`: axe nos três passos da nota e no cartão com preços.
- Docs:
  - `architecture.md`: as notas no módulo `markets`, o `lookup` sempre manual e o leitor de QR no
    aparelho;
  - `data-model.md`: a V41 e o mapa de dados (só o hash da chave, nada do consumidor).

- [ ] **Step 1: Escrever o runbook, o e2e e o axe.**
- [ ] **Step 2: Rodar.** Em `web/`: `npm run e2e -- feira-nota a11y core-without-modules` → PASS (se
  um contêiner antigo atrapalhar localmente, o CI confere; nunca recriar contêineres com `down -v`).
- [ ] **Step 3: Conferir tudo.** `./gradlew build` em `api/`; `npm run lint`, `typecheck`, `test`,
  `build` e `check:chunks` em `web/`.
- [ ] **Step 4: Commit.** `git commit -m "test(markets): register a receipt by hand end to end and add the states runbook"`.

## Depois do merge (passos do mantenedor, spec seção 12)

1. No staging, registrar uma nota real pelo modo manual e ver os preços no cartão do mercado
   (critério de saída da 10.2). A nota real não vai para o repositório nem para o PR.
2. Conferir cada estado candidato pelo runbook `docs/runbooks/nfce-states.md` e registrar data e
   resultado lá.
3. Para cada estado que passar: salvar uma página de nota sem a parte do consumidor, fora do
   repositório, e pedir o plano 10.2.x daquele estado (ou do portal, como o SVRS). O primeiro 10.2.x
   também traz a política nova (a API passa a consultar a SEFAZ) e a linha da NFC-e no `/robo`.
4. Depois do merge de cada 10.2.x: pôr o estado em `LIBERDADE_MARKETS_NFCE_FETCH_STATES` do ambiente
   (primeiro no staging, durante a conferência).
5. PR de docs: README dos planos, linha 10, "10.2 entregue".
