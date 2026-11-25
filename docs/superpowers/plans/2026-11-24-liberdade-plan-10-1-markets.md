# Liberdade · Plano 10.1: mercados perto de casa — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- A família liga o módulo Feira e vê os mercados perto de um ponto escolhido por ela, com distância e
  a atribuição do OpenStreetMap.
- Cada mercado tem um cartão com site, Instagram e encarte, cada um com fonte e data.
- O mapa do Google e o Instagram só carregam depois de um toque.

**Architecture:**
- Um workflow semanal do GitHub filtra o extrato do Brasil e publica `markets-br.csv.gz` num release
  fixo.
- O módulo novo `markets` da API importa esse arquivo toda semana para uma tabela global, guarda o que
  é da família à parte e responde à busca por área (POST, ponto com 2 casas).
- O aparelho mede as distâncias com a posição que nunca sai dele.
- A web ganha a feature `markets`, toda sob demanda, uma página isolada para o Instagram e a CSP com
  `frame-src`.

**Tech Stack:**
- API: Spring Boot 4.1, Spring Modulith, JdbcClient, RestClient, `@Scheduled`, Testcontainers,
  MockRestServiceServer.
- Web: React 19, TanStack Query, react-intl, MSW, Vitest, Playwright e axe.
- Workflow: `osmium-tool` pelo `apt`, Python da imagem do runner, só a biblioteca padrão.
- Infra (só escrita, aplicada pelo mantenedor): Terraform do CloudWatch.
- Sem dependências novas.

**Spec:** [`docs/superpowers/specs/2026-11-24-liberdade-plan-10-feira-design.md`](../specs/2026-11-24-liberdade-plan-10-feira-design.md),
aprovado pelo mantenedor em 2026-11-24. Este plano cobre as seções 6.1 a 6.4 e a parte da 10.1 nas
seções 6.8, 7, 8 e 12. O spec é a autoridade; onde este plano desvia, o desvio está marcado.

## Global Constraints

- **Idiomas:** código, comentários, commits e nomes em inglês; interface, docs e mensagens de erro
  para a pessoa em PT-BR.
- **Commits:** Conventional Commits em minúsculas, `feat(markets): …`, `ci(markets): …`,
  `docs(markets): …`; todo commit termina com
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `git add` só com caminhos explícitos.
- **Dados pessoais:** nenhum endereço, coordenada real de casa, nome ou banco nos testes, fixtures e
  docs. Fixtures usam pontos inventados e ruas como "Rua Exemplo". Antes de cada commit, as duas
  buscas da `personal-data-patterns` (no caminho de `git rev-parse --git-common-dir`) saem com
  código 1.
- **Logs e auditoria:** nunca levam coordenada, texto de endereço, consulta de geocodificação, URL
  do Instagram nem nome de mercado. Só tipos fixos, contagens e ids.
- **Chamadas externas:** só Geofabrik (no workflow), o release do próprio repositório, Nominatim e o
  oEmbed da Meta, todas com `User-Agent: LiberdadeBot/1.0 (+<web origin>/robo)` e tempo limite.
  **Testes nunca chamam serviço real**, exceto a conferência única do oEmbed na Task 8, Step 1, feita
  à mão pelo implementador.
- **Papéis:** Dono e Adulto mudam; Leitor e Jovem só leem (spec 6.2). O endereço da família é só do
  Dono e do Adulto.
- **Guardas:** toda rota da Feira tem `@WorkspaceScoped`, `@RequiresModule(ModuleKey.MARKETS)` e
  `@RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)`.
- **Erros:** RFC 9457 `ProblemDetail` com `code`, como no resto da API.
- **Números:** coordenadas e valores vão no JSON como texto decimal (`"-10.00012"`), nunca como
  número de ponto flutuante.
- **Fronteiras de módulo:** `markets` depende só de `workspace`, `billing` e `shared`. `audit`,
  `privacy` e `live` dependem de `markets` pelos tipos públicos da raiz do pacote, nunca pelo
  `internal`.
- **Contrato:** `api/openapi.json`, `web/src/shared/api/schema.ts` e o `RoleMatrixIT` mudam no
  mesmo commit da rota.
- **Cobertura:** JaCoCo 80% no total, 90% em `shared*` e `*.domain*`.
- **Desempenho:** pacote inicial até 200 KB (`npm run check:chunks`). Toda a feature `markets`
  carrega sob demanda.
- **Acessibilidade:** WCAG 2.2 AA; axe sem violações nas telas novas.
- **Nada de terceiros antes do toque:** nenhuma requisição a `google.com`, `instagram.com`,
  `cdninstagram.com` ou `facebook.com` sai do navegador antes de a pessoa tocar.
- **Ética:** sem "mais barato", sem contagem regressiva, sem pressa.
- **GitHub Actions:** `permissions: {}` no topo, permissão por job, actions fixadas por SHA com o
  comentário da versão, como os workflows existentes.
- **Infra:** Terraform só escrito e testado com `terraform test`; aplicar é passo do mantenedor.

### Pré-requisitos de execução

- **Plano 8.2 no `main`:** a guarda `@RequiresPlan(value = FAMILY, openWhileBillingOff = true)`.
  Sem ela, a execução para e avisa.
- **Versões:** antes da Task 9, conferir no `main` o `FORMAT_VERSION` do `ExportService` e a última
  versão de `privacy-*` em `LegalDocuments.java`. Este plano escreve `FORMAT_VERSION + 1` e a versão
  de política seguinte. A 8.2 leva a v5 e a `privacy-2026-12.4`; 8.3 e 9 podem levar outras.
- **Migrações:** a V40 é desta entrega (o Plano 10 tem V40 a V49). Se o `main` já tiver uma V40, a
  execução para e avisa.
- **Ordem:** 8.2 → 8.3 → 9 → 10.

### Pré-requisitos locais

- Docker para o PostgreSQL dos testes (`docker compose up -d db`). Nunca `docker compose down -v`.
- Python 3 no `PATH` para o teste do script do workflow (o runner Ubuntu já tem).
- `terraform` 1.x para o `terraform test` da Task 3.

## Entrega

- **PR 1 (docs):** o spec e os três planos da Feira (este PR de docs).
- **PR 2 (código):** branch `feat/plan-10-1-markets` a partir do `main`, uma task por commit (ou
  poucos commits por task), revisão final no modelo mais capaz e merge com o CI verde.
- **Depois do merge:** os passos do mantenedor (spec seção 12) e a conferência no staging (spec
  seção 8). Depois, um PR de docs marca a 10.1 como entregue no README dos planos.

## Decisões deste plano

**D1. Módulo e pacotes.**
- `markets/package-info.java`:
  `@ApplicationModule(allowedDependencies = {"workspace", "billing", "shared"})`.
- Raiz do pacote (tipos públicos): os eventos (D11) e `MarketsExport` (D12).
- `markets/domain`: regras puras, sem Spring. `markets/internal`: o resto.
- `audit`, `privacy` e `live` ganham `markets` em `allowedDependencies`. O `live` só passa a usar
  `markets` na 10.3; a dependência entra lá.
- `ModuleKey.MARKETS("markets")`, desligado por padrão. A V40 troca o `CHECK` de
  `workspace_modules`.

**D2. Propriedades** (`MarketsProperties`, `@ConfigurationProperties("liberdade.markets")`).

| Propriedade | Padrão | Uso |
|---|---|---|
| `bot-url` | `${liberdade.web.origin}/robo` | `User-Agent` |
| `osm.source` | `github` | `github` ou `fake`; `fake` é recusado no perfil `prod` |
| `osm.dataset-url` | URL de download do release `osm-markets-data` deste repositório | onde estão `manifest.json` e `markets-br.csv.gz` |
| `osm.timeout` | `PT60S` | conexão e leitura |
| `osm.max-bytes` | `52428800` (50 MB) | corta o download maior que isso |
| `osm.min-row-ratio` | `0.8` | proteção contra extrato quebrado |
| `osm.stale-after` | `P15D` | `DATASET_STALE` |
| `geocoding.enabled` | `true` | interruptor (`LIBERDADE_MARKETS_GEOCODING_ENABLED`) |
| `geocoding.source` | `nominatim` | `nominatim` ou `fake`; `fake` recusado no `prod` |
| `geocoding.base-url` | `https://nominatim.openstreetmap.org` | |
| `geocoding.min-interval` | `PT2S` | fila por processo |
| `geocoding.daily-limit` | `20` | por família, por dia de São Paulo |
| `geocoding.timeout` | `PT10S` | |
| `instagram.enabled` | `true` | interruptor do "Ver aqui" |
| `instagram.source` | `meta` | `meta` ou `fake`; `fake` recusado no `prod` |
| `instagram.base-url` | `https://graph.facebook.com/v25.0` | |
| `instagram.timeout` | `PT10S` | |

- Os perfis `dev`, `e2e` e `test` usam `fake` nas três fontes. `MarketsStartupCheck` recusa `fake`
  no perfil `prod`, como o `IndicatorStartupCheck`.

**D3. Ponto e área** (`markets/domain`).
- `GeoPoint(BigDecimal lat, BigDecimal lon)`: latitude em [−90, 90], longitude em [−180, 180].
- `GeoPoint.requireScale(int maxDecimals)`: recusa com `LocationTooPreciseException` quando
  `stripTrailingZeros().scale()` passa do máximo. A busca usa 2, o endereço da família 3 e o mercado
  da família 5.
- `SearchBox.around(GeoPoint center, int radiusKm)`: caixa de `radiusKm + 1` km. Meia-altura em
  graus = `(radiusKm + 1) / 111.32`. Meia-largura = a mesma divisão por `cos(lat)`, com o cosseno
  nunca abaixo de 0,01. `MathContext.DECIMAL64`, resultado com 5 casas.
- `radiusKm` de 1 a 10; ausente vale 3.

**D4. Referência do mercado.**
- Mercado do OSM: `osm-<type>-<id>` (`osm-n-123`, `osm-w-45`, `osm-r-6`).
- Mercado da família, e mercado do OSM que já tem linha em `markets`: o `uuid` da linha.
- Toda rota com `{ref}` aceita as duas formas. `MarketRef.parse(String)` devolve `OsmRef(type, id)`
  ou `FamilyRef(UUID)`. Outra forma dá 404 `market.not_found`.
- A busca devolve os dois: `ref` (sempre `osm-…` nos do OSM) e `familyId` (nulo sem linha). A web
  navega por `familyId ?? ref`.
- Escrever num mercado do OSM sem linha cria a linha (`origin = 'OSM'`) na mesma transação.

**D5. Busca** (`POST /v1/workspaces/{ws}/markets/search`).
- Corpo `{lat, lon, radiusKm?}`, coordenadas como texto. Mais de 2 casas → 422
  `markets.location_too_precise`. Raio fora de 1–10 → 422 `markets.radius_invalid`.
- `osm`: linhas de `osm_markets` na caixa, até 500, ordenadas por
  `(lat − c.lat)² + ((lon − c.lon) × cos(c.lat))²`. Cada item traz `ref`, `kind`, `name`, `brand`,
  `lat`, `lon`, `street`, `housenumber`, `suburb`, `city` e `familyId`.
- `family`: mercados `FAMILY` não arquivados com posição na caixa, com `ref = id`, `name`, `kind`,
  `lat`, `lon`.
- `unlocated`: mercados `FAMILY` não arquivados sem posição (`ref`, `name`, `kind`), para a seção
  "Seus mercados sem localização".
- `left`: linhas `OSM` não arquivadas cujo `(osm_type, osm_id)` não está mais em `osm_markets`, com
  `ref = id` e `cnpj`. Aparecem como "Mercado que saiu do OpenStreetMap".
- `datasetDate`: `osm_timestamp` da última importação, como data de São Paulo, ou `null`.
- Mercado arquivado não aparece em lista nenhuma.

**D6. Cartão** (`GET /v1/workspaces/{ws}/markets/{ref}`).
- `ref`, `familyId`, `origin` (`OSM` ou `FAMILY`), `status` (`ACTIVE`, `LEFT_OSM`, `ARCHIVED`),
  `kind`, `name`, `lat`, `lon`, `address` (rua, número, bairro, cidade, só do OSM), `openingHours`
  (só do OSM), `cnpj`, `datasetDate`.
- `website`, `instagram`: `{value, source: 'FAMILY' | 'OSM', date}`. O da família vence. `date` é
  `*_set_at` (família) ou o `datasetDate` (OSM). Sem nenhum dos dois: `null`.
- `flyer`: `{url, source, validUntil, setAt}` ou `null`.
- `instagram.value` é normalizado (D7). `instagramEmbeddable`: `true` quando o oEmbed pode ser
  tentado (D10).

**D7. Links da família** (`PUT /v1/workspaces/{ws}/markets/{ref}/links`).
- Corpo `{website, instagram, flyerUrl, flyerSource, flyerValidUntil}`. Campo `null` apaga o valor
  da família (volta o do OSM, se houver). `*_set_at` muda só no campo que mudou.
- `website` e `flyerUrl`: só `https://`, até 500 caracteres, sem credenciais na URL → senão 422
  `markets.link_invalid`.
- `flyerSource`: até 60 caracteres, obrigatório quando há `flyerUrl`.
- `InstagramRef.parse(String)` aceita `@perfil`, `perfil`, `https://www.instagram.com/perfil/` e
  URL de post (`/p/<code>/`, `/reel/<code>/`). Devolve `Profile(handle)` ou `Post(url)`. Perfil:
  letras, números, `.` e `_`, até 30. Outra coisa → 422 `markets.instagram_invalid`.
- Guarda a forma canônica: `https://www.instagram.com/<perfil>/` ou a URL do post sem query.
- O valor `contact:instagram` do OSM passa pelo mesmo `parse`; se falhar, o cartão não mostra.

**D8. Mercado da família.**
- `POST /v1/workspaces/{ws}/markets`: `{name (1–80), kind, lat?, lon?}`. Coordenadas até 5 casas;
  só uma das duas → 422. Cria `origin = 'FAMILY'`. 201 com o cartão.
- `PUT …/markets/{ref}/position`: `{lat, lon}` até 5 casas, só em `FAMILY` (senão 409
  `market.not_family`).
- `POST …/markets/{ref}/archive`: põe `archived_at`. Vale para `FAMILY` e para `OSM` com linha.
  Arquivar de novo não muda nada.
- `POST …/markets/{ref}/convert`: só em linha `OSM` com `status = LEFT_OSM` (senão 409
  `market.not_left`). Corpo `{name, kind, lat, lon}`. A linha vira `FAMILY`, `osm_type` e `osm_id`
  ficam nulos e nada é copiado do OSM. CNPJ, links e encarte ficam.
- O mercado da família nunca é comparado nem deduplicado com o OSM.

**D9. Endereço da família e geocodificação.**
- `GET …/markets/home` → `{lat, lon, label}` ou 204. `PUT …/markets/home` `{lat, lon, label?}`, 3
  casas no máximo (422 `markets.location_too_precise`), rótulo até 40. `DELETE` apaga a linha (204).
- `POST …/markets/geocode` `{query}`, 1 a 120 caracteres depois de `strip()`. Resposta
  `{places: [{lat, lon, label}]}`, até 5, com o ponto arredondado HALF_UP para 3 casas e `label` =
  bairro e cidade (ou cidade e estado) do `address` do Nominatim.
- Desligada: 503 `markets.geocoding_off`, e `GET …/markets/settings` devolve
  `{geocodingEnabled, instagramEnabled}` para a web esconder as opções.
- Limite: 20 por família por dia de São Paulo, contado em memória por processo. Passou → 429
  `markets.geocoding_limit`. A resposta vinda do cache não conta.
- Cache em memória por 24 h pela consulta normalizada (`TextNorm.normalize`, D13), com no máximo
  1.000 entradas (sai a mais antiga).
- Fila: `GeocodingThrottle` com um `ReentrantLock` justo; cada chamada espera até
  `última + min-interval` (`Clock` e `Sleeper` injetados). Esperar mais de 10 s → 503
  `markets.geocoding_busy`, sem chamar o Nominatim.
- Nominatim: `GET /search?q=…&format=jsonv2&addressdetails=1&limit=5&countrycodes=br` com
  `Accept-Language: pt-BR`. Erro, tempo esgotado ou JSON estranho → 502 `markets.geocoding_failed`.
- A consulta não vai para log, banco nem auditoria.

**D10. Instagram.**
- `POST …/markets/{ref}/instagram-embed` → `{html}` ou 422 `markets.instagram_unavailable`.
- Só quando `instagramEmbeddable`: a URL é de post, ou é de perfil e a Task 8, Step 1 mostrou que o
  oEmbed aceita perfil.
- Chama `GET {base}/instagram_oembed?url=<url>&maxwidth=320&omitscript=true`, sem token.
- Cache em memória por 7 dias pela URL canônica (no máximo 1.000 entradas). Recusa (4xx) também
  fica no cache por 1 dia.
- O `html` só passa quando começa com `<blockquote` e tem `class="instagram-media"`. Senão 422.
- **Portão da Task 8:** se o Step 1 mostrar que a Meta pede token, a rota não é feita,
  `instagramEmbeddable` é sempre `false`, o "Ver aqui" não aparece e a página isolada não entra.
  O relatório da Task 8 diz isso ao controlador.

**D11. Eventos e auditoria.** Registros públicos em `markets/`, publicados na transação que grava.

| Evento | Campos | Ação na auditoria | `targetType` |
|---|---|---|---|
| `MarketHomeSet` | `workspaceId`, `actorUserId` | `market_home.set` | `WORKSPACE` |
| `MarketHomeCleared` | `workspaceId`, `actorUserId` | `market_home.cleared` | `WORKSPACE` |
| `MarketAdded` | `workspaceId`, `marketId`, `actorUserId` | `market.added` | `MARKET` |
| `MarketEdited` | `workspaceId`, `marketId`, `actorUserId`, `change` (`LINKS`, `POSITION`, `ARCHIVED`, `CONVERTED`) | `market.edited` (metadata `change`) | `MARKET` |

- `MARKET` entra na lista do Javadoc de `AuditEntry`.
- Nada de coordenada, nome, URL ou rótulo na metadata.

**D12. Exportação.**
- `MarketsExport` (público, em `markets/`, implementado em `internal`):
  - `familyMarkets(workspaceId)`: as linhas de `markets`;
  - `home(workspaceId)`: o endereço da família, ou nada.
- A exportação da família ganha:
  - `feira-mercados.csv`: `id;origem;ref_osm;nome;tipo;lat;lon;cnpj;site;instagram;encarte;fonte_encarte;encarte_valido_ate;arquivado_em`.
    O `id` é o da linha, para os CSVs de preços e notas da 10.2 apontarem o mercado.
    Só o que a família tem: nas linhas `OSM`, nome e posição ficam vazios e `ref_osm` aponta o
    objeto;
  - `feira-endereco.csv`: `lat;lon;rotulo`.
- `FORMAT_VERSION` vai para a seguinte à do `main` (pré-requisito).
- A exportação pessoal não muda: a Feira é dado da família.
- Exclusão: já é em cascata pela `workspace_id`; `created_by` e `updated_by` são ids de
  membership sem FK, como no planning (conferir o padrão da V-mais-recente e seguir).

**D13. Normalização de texto** (`markets/domain/TextNorm`).
- `normalize(String)`: NFD, tira as marcas (`\p{M}`), minúsculas (`Locale.ROOT`), troca o que não é
  letra nem dígito por espaço, junta espaços e faz `strip()`.
- `"  Leite INT. 1L "` → `"leite int 1l"`. `"Pão-de-Açúcar"` → `"pao de acucar"`.
- A 10.1 usa no cache da geocodificação; a 10.2 usa nos aliases.

**D14. Importação.**
- `OsmMarketImportJob.run()`:
  1. Baixa o `manifest.json`: `{osmTimestamp, rowCount, sha256, attribution, license}`.
  2. Para se `osmTimestamp` é igual ao da última importação (log `MARKETS_IMPORT skipped`).
  3. Baixa o `.csv.gz` até `max-bytes` e confere o SHA-256 do arquivo comprimido.
  4. Recusa se `rowCount` lido for menor que `min-row-ratio` × a última importação.
  5. Lê em fluxo (`GZIPInputStream`) e, numa transação só, apaga `osm_markets`, insere em lotes de
     1.000 e grava a linha em `osm_market_imports`.
- Qualquer falha desfaz a transação e escreve `MARKETS_ALERT type=IMPORT_FAILED reason=<SHA_MISMATCH|TOO_FEW_ROWS|BAD_ROW|HTTP|TIMEOUT|TOO_LARGE|BAD_MANIFEST>`.
- Sucesso: `MARKETS_IMPORT ok rows=<n>`.
- Agenda: `@Scheduled(cron = "0 40 4 * * TUE", zone = "America/Sao_Paulo")` e, ao subir, uma vez se
  `osm_markets` estiver vazia (`ApplicationReadyEvent`, em outra thread, como o
  `IndicatorStartupRun`).
- `DATASET_STALE`: `@Scheduled(cron = "0 10 9 * * *", zone = "America/Sao_Paulo")` escreve
  `MARKETS_ALERT type=DATASET_STALE` quando a última importação tem mais de `stale-after`, ou quando
  não há importação e a API está no ar há mais de um dia.
- Linha ruim (coluna faltando, `kind` desconhecido, coordenada fora da faixa) → `BAD_ROW` e nada é
  gravado. O workflow já filtra; linha ruim aqui quer dizer arquivo errado.
- Leitores veem o conteúdo antigo até o commit (MVCC); a tabela nunca fica vazia para quem lê.

**D15. Formato publicado** (o workflow escreve, a API lê).
- `markets-br.csv.gz`: UTF-8, separador `,`, aspas RFC 4180, com cabeçalho
  `osm_type,osm_id,kind,name,brand,lat,lon,website,instagram,opening_hours,street,housenumber,suburb,city`.
- `lat` e `lon` com 5 casas.
- `kind` de `shop=supermarket|convenience|greengrocer` ou `amenity=marketplace`, em maiúsculas.
- Ponto: o nó, ou o centro da caixa das coordenadas da área. Objeto sem coordenada sai.
- `instagram` vem de `contact:instagram`; `website`, de `website` ou `contact:website`.
- `manifest.json`: `{"osmTimestamp": "<ISO-8601 do cabeçalho do pbf>", "rowCount": n, "sha256": "<do .csv.gz>", "attribution": "© OpenStreetMap contributors", "license": "ODbL-1.0"}`.
- O leitor da API (`OsmCsvReader`) e o script do workflow têm a mesma fixture de exemplo:
  `scripts/osm/testdata/sample.geojsonseq` → `scripts/osm/testdata/expected.csv`.
  `OsmCsvReaderTest` lê o `expected.csv`.

**D16. Fontes falsas** (dev, e2e e testes).
- `FakeOsmDataset`: grava 6 mercados inventados ao redor de `(-10.00000, -50.00000)`, com nomes
  "Mercado Exemplo 1"… e "Rua Exemplo". Um deles fica sem nome e outro com `contact:instagram`.
- `FakeGeocoder`: devolve um lugar fixo `(-10.000, -50.000)`, rótulo "Bairro Exemplo, Cidade
  Exemplo", para qualquer consulta, e erro para `"falha"`.
- `FakeInstagramOembed`: devolve um `blockquote` fixo, e recusa para URL com `privado`.

**D17. Web: rotas e estado.**
- `features/markets/register.ts`: mensagens e `registerModule({ key: 'markets', available: true })`.
- Rotas sob demanda dentro de `<ModuleRoute module="markets">`: `/mais/feira` (lista) e
  `/mais/feira/mercado/:ref` (cartão).
- Item "Feira" em Mais (`more.markets`) com o módulo ligado, para os quatro papéis.
- Hooks (TanStack Query):
  - `useMarketSearch(ws, area)`: chave `['ws', ws, 'markets', 'search', lat2, lon2, radius]`,
    `staleTime` de 1 h;
  - `useMarketCard(ws, ref)`;
  - `useMarketHome(ws)`;
  - `useMarketsSettings(ws)`.
- No aparelho (`localStorage`, `try/catch`, a tela funciona sem), chave `liberdade.markets.<userId>`:
  `{mode: 'precise' | 'approximate' | 'typed' | 'home', point?: {lat, lon, label?}, mapConsent?: true, instagramConsent?: true}`.
  - `point` só em `approximate` (2 casas) e `typed` (3 casas). `precise` nunca guarda ponto.
  - O sair da conta limpa as chaves `liberdade.markets.*` no mesmo lugar que limpa os outros dados
    do aparelho.

**D18. Web: localização e distância** (`features/markets/geo.ts`).
- `roundTo(value: number, decimals: 2 | 3): string`: HALF_UP para longe do zero, sempre com as casas
  (`-10.004` → `"-10.00"`).
- `distanceMeters(a, b)`: haversine com raio de 6.371.008,8 m.
- `formatDistance(m, approximate)`:
  - abaixo de 1.000 m, arredonda para 10 m ("450 m");
  - a partir de 1.000 m, 1 casa com vírgula ("1,2 km");
  - aproximada leva "cerca de " na frente.
- O centro enviado à busca é sempre `roundTo(…, 2)`. A distância usa:
  - em `precise`, a posição da memória;
  - em `approximate`, o ponto guardado;
  - em `typed` e `home`, o ponto de 3 casas.
- Mostra até 50 mercados dentro do raio, por distância.
- Aproximada: `<geolocation accuracymode="approximate">` quando `'HTMLGeolocationElement' in window`;
  senão `getCurrentPosition({ enableHighAccuracy: false, maximumAge: 600000 })`, arredonda na hora e
  guarda. O ponto guardado é reaproveitado e só muda quando a pessoa pede "Atualizar".

**D19. Web: avisos e embeds.**
- `features/markets/ThirdPartyNotice.tsx`: o diálogo do aviso, com o texto do spec e o "Não
  perguntar de novo". Um para o Google, outro para a Meta.
- `MapEmbed`: só monta o `iframe` depois do aviso aceito. Atributos:
  - `src = https://www.google.com/maps/embed/v1/place?key=<VITE_GOOGLE_MAPS_EMBED_KEY>&q=<lat>,<lon do mercado>`;
  - `referrerPolicy="strict-origin-when-cross-origin"`, `loading="lazy"`, `title="Mapa de <nome>"`,
    altura mínima de 200 px.
  - Sem chave, o botão não aparece.
- `links.ts`:
  - `mapsSearchUrl(lat, lon)` = `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lon>`;
  - `mapsDirectionsUrl(lat, lon)` = `https://www.google.com/maps/dir/?api=1&destination=<lat>%2C<lon>`;
  - `wazeUrl(lat, lon)` = `https://waze.com/ul?ll=<lat>,<lon>&navigate=yes`.
  - Todos como `<a target="_blank" rel="noopener noreferrer">`.
- `InstagramEmbed`:
  1. Depois do aviso, chama a rota de D10.
  2. Cria `<iframe sandbox="allow-scripts allow-popups" src="/embed/instagram" title="Publicação do Instagram">`.
  3. No `load`, manda `{type: 'liberdade-instagram', html}` com
     `postMessage(…, '*')`. O alvo é `'*'` porque o `iframe` sem `allow-same-origin` tem origem
     opaca.
- A página isolada (`public/embed/instagram.html` + `public/embed/instagram.js`, módulo ES):
  - aceita só mensagem de `window.parent` com `event.origin === location.origin`;
  - aceita só `type === 'liberdade-instagram'`;
  - faz o parse do `html` com `DOMParser` e aceita um único `blockquote.instagram-media` com
    `data-instgrm-permalink` começando com `https://www.instagram.com/`;
  - insere um clone limpo: só o `blockquote` com `class`, `data-instgrm-permalink`,
    `data-instgrm-version` e o texto, sem atributos `on*` nem `script`;
  - carrega `https://www.instagram.com/embed.js` uma vez.
  - Exporta `acceptEmbed(html: string): HTMLElement | null` para o teste.

**D20. CSP e cabeçalhos** (`web/config/securityHeaders.ts`).
- CSP do app: entra `frame-src 'self' https://www.google.com` (depois de `frame-ancestors`); o resto
  igual.
- `Permissions-Policy`: `geolocation=()` vira `geolocation=(self)`. `camera` fica `()` até a 10.2.
- Bloco novo no `_headers` para `/embed/instagram*`:
  - `! Content-Security-Policy`, para tirar a CSP geral;
  - a CSP do spec 6.4: `default-src 'none'; script-src 'self' https://www.instagram.com; frame-src https://www.instagram.com; img-src https://*.cdninstagram.com data:; style-src 'unsafe-inline'; frame-ancestors 'self'; base-uri 'none'`;
  - `X-Frame-Options: SAMEORIGIN` no lugar do `DENY`, se o `_headers` tiver `DENY` (conferir e
    tirar com `!` do mesmo jeito).
  - Os valores exatos se confirmam no staging (passo da seção "Depois do merge").
- `vite.config.ts`: `navigateFallbackDenylist` ganha `/^\/embed\//`.

**D21. Desvio do spec: `/robo` na 10.1.** O spec põe a explicação das coletas novas no `/robo` na
10.2. Mas o Nominatim e o oEmbed começam na 10.1, e o `User-Agent` aponta para `/robo`. Por isso o
`/robo` muda já na 10.1, com o extrato da Geofabrik (pelo GitHub Actions), o Nominatim e o oEmbed.
A 10.2 não muda o `/robo`.

**Desvio do spec: atribuição fora de "Sobre e licenças".** O spec pede a atribuição do OSM também em
"Sobre e licenças", mas o app não tem essa tela. A atribuição vai para o `/robo`, que é público e já
fala das fontes, numa seção "Dados de terceiros" com o texto da ODbL e o link para
`https://www.openstreetmap.org/copyright`. A lista e o cartão continuam mostrando a atribuição.

**D22. Política de privacidade.**
- Versão nova (a seguinte à última no `main`, pré-requisito), cópia da anterior com uma seção
  "Feira" em linguagem simples:
  - o que a localização faz e que a posição precisa não sai do aparelho;
  - que o servidor recebe só a região (cerca de 1 km);
  - que o endereço da família vira só um ponto aproximado e um rótulo;
  - que a busca de endereço vai ao OpenStreetMap sem dados da família;
  - que mapa e Instagram são do Google e da Meta, só carregam ao tocar e podem gravar cookies;
  - que, ao registrar uma nota fiscal, o app lê o QR no aparelho e guarda só preços, mercado e
    data, nunca o CPF nem a chave da nota.
- Uma versão só para o Plano 10 inteiro (spec 6.8 e 12: publicada com a 10.1). A 10.2 e a 10.3 não
  mudam a política; o primeiro plano 10.2.x muda, para dizer que o servidor consulta a SEFAZ.
- **Precisa de novo aceite?** Muda o que se coleta (o ponto da família), então segue o padrão da
  versão que trouxe a cobrança: novo aceite, sem `KEEPS_ACCEPTANCE`.
- `legalConfig.latest.privacy` aponta a nova.

**D23. Terraform** (`infra/live/markets_alerts.tf`).
- Filtro `"\"MARKETS_ALERT\""` e alarme por ambiente (staging e produção), cópia do
  `indicator_alert`, com a métrica `MarketsAlerts`, usando `local.api_log_groups`.
- `Sum >= 1` em 1 h, avisando `aws_sns_topic.alerts`.
- São 11 alarmes no total (passa da cota grátis em 1; spec seção 9 aceita).
- `infra/live/tests/operations.tftest.hcl` ganha as asserções dos dois.

## Review Focus

1. **Posição precisa saindo do aparelho:** um ponto com 3+ casas na busca, ou o ponto da precisa
   guardado no `localStorage`. Testes: `MarketSearchIT.threeDecimalsIs422`,
   `geo.test.ts › searchCenterAlwaysTwoDecimals` e
   `locationStore.test.ts › preciseNeverStoresAPoint` (Tasks 4 e 10).
2. **Terceiros antes do toque:** abrir a lista e o cartão sem tocar não pode pedir nada a Google ou
   Meta. Testes: `MarketCardPage.test.tsx › noIframeBeforeConsent` e
   `feira.spec.ts › noThirdPartyRequestBeforeTap` (Tasks 12 e 13).
3. **Extrato quebrado apagando os mercados:** arquivo com metade das linhas, SHA errado, linha ruim
   no fim. Testes: `OsmMarketImportIT.halfTheRowsIsRefused`, `...badRowAtTheEndKeepsOldRows` e
   `...readersSeeOldRowsUntilCommit` (Task 3).
4. **Nominatim acima do limite:** dois pedidos em seguida no mesmo processo, ou o 21º da família no
   dia. Testes: `GeocodingThrottleTest.secondCallWaitsTheInterval` e
   `GeocodingIT.twentyFirstIs429` (Task 6).
5. **Página isolada aceitando qualquer coisa:** mensagem de outra origem, `script` dentro do
   `blockquote`, permalink de outro domínio. Testes: `instagramEmbed.test.ts › rejectsOtherOrigin`,
   `...stripsScripts` e `...rejectsForeignPermalink` (Task 9).

## Mapa de arquivos

Caminhos da API relativos a `api/src/main/java/br/com/liberdadefamilia/` (testes em
`api/src/test/java/br/com/liberdadefamilia/`); do web, relativos a `web/src/`.

| Arquivo | Task | Papel |
|---|---|---|
| `api/src/main/resources/db/migration/V40__markets.sql` | 1 | tabelas da 10.1 e o `CHECK` dos módulos |
| `markets/package-info.java`, `workspace/ModuleKey.java`, `workspace/internal/ModuleController.java` | 1 | módulo |
| `markets/domain/{GeoPoint,SearchBox,MarketRef,InstagramRef,HttpsLink,TextNorm,MarketKind,LocationTooPreciseException}.java` | 1 | regras puras |
| `.github/workflows/osm-markets.yml`, `scripts/osm/{to_csv.py,test_to_csv.py,testdata/*}` | 2 | extrato semanal |
| `markets/internal/{MarketsProperties,MarketsStartupCheck,MarketsSourceConfig,OsmDataset,GithubOsmDataset,FakeOsmDataset,OsmCsvReader,OsmManifest,OsmMarketImporter,OsmMarketImportJob,OsmImportStartupRun,MarketsAlerts,Sleeper}.java` | 3 | importação |
| `infra/live/markets_alerts.tf`, `infra/live/tests/operations.tftest.hcl`, `docs/runbooks/markets-alerts.md` | 3 | alarme e runbook |
| `markets/internal/{MarketController,MarketSearchService,MarketRepository,OsmMarketRepository,MarketViews,MarketsOpenApiConfig}.java` | 4 | busca e cartão |
| `markets/internal/{FamilyMarketService,LinksRequest,NewMarketRequest,PositionRequest}.java`, `markets/{MarketAdded,MarketEdited}.java` | 5 | escrita dos mercados |
| `markets/internal/{MarketHomeController,MarketHomeService,Geocoder,NominatimGeocoder,FakeGeocoder,GeocodingThrottle,GeocodingLimits,GeocodingCache}.java`, `markets/{MarketHomeSet,MarketHomeCleared}.java` | 6 | endereço e geocodificação |
| `markets/internal/{InstagramOembed,MetaInstagramOembed,FakeInstagramOembed,InstagramEmbedService}.java` | 7 | oEmbed |
| `markets/MarketsExport.java`, `markets/internal/JdbcMarketsExport.java`, `privacy/internal/{ExportService,LegalDocuments}.java`, `audit/internal/AuditListeners.java`, `audit/AuditEntry.java` | 8 | exportação, auditoria e política |
| `features/legal/{BotPage.tsx,legalConfig.ts,texts/privacy-<nova>.ts}` | 8 | robô e política |
| `web/config/securityHeaders.ts`, `web/vite.config.ts`, `web/public/embed/{instagram.html,instagram.js}` | 9 | CSP e página isolada |
| `shared/modules/registry.ts`, `features/modules/{moduleCatalog.ts,messages.pt-BR.ts}`, `app/{routes.tsx,MorePage.tsx}`, `mocks/{handlers.ts,data.ts,marketsHandlers.test.ts}` | 10 | módulo na web e MSW |
| `features/markets/{register.ts,messages.pt-BR.ts,geo.ts,locationStore.ts,useMarkets.ts,LocationChooser.tsx,FeiraPage.tsx,MarketRow.tsx,OsmAttribution.tsx}` | 10 | lista |
| `features/markets/{MarketCardPage.tsx,LinksSheet.tsx,AddMarketSheet.tsx,ConvertMarketSheet.tsx,links.ts}` | 11 | cartão e escrita |
| `features/markets/{ThirdPartyNotice.tsx,MapEmbed.tsx,InstagramEmbed.tsx}`, `features/members/MarketHomeSection.tsx` | 12 | mapa, Instagram e endereço |
| `web/e2e/feira.spec.ts`, `web/e2e/a11y.spec.ts`, `web/scripts/check-chunks.mjs` | 13 | e2e e orçamento |
| `docs/architecture.md`, `docs/data-model.md`, `docs/design-system.md` | 13 | docs |

## Tasks

### Task 1: V40, o módulo e o domínio

**Files:**
- Create: `db/migration/V40__markets.sql`, `markets/package-info.java`, `markets/domain/*` (mapa).
- Modify: `workspace/ModuleKey.java`, `workspace/internal/ModuleController.java` (`allowableValues`),
  `api/openapi.json`, `web/src/shared/api/schema.ts`.
- Test: `markets/domain/{GeoPointTest,SearchBoxTest,MarketRefTest,InstagramRefTest,HttpsLinkTest,TextNormTest}.java`,
  `markets/MarketsSchemaIT.java`, `ApplicationModulesTest`.

**Interfaces:**
- Produces:
  - `record GeoPoint(BigDecimal lat, BigDecimal lon)` com `GeoPoint requireScale(int maxDecimals)`
    e `GeoPoint rounded(int decimals)` (HALF_UP);
  - `record SearchBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLon, BigDecimal maxLon)`
    com `static SearchBox around(GeoPoint center, int radiusKm)`;
  - `sealed interface MarketRef permits OsmRef, FamilyRef` com `static Optional<MarketRef> parse(String)`;
    `record OsmRef(char type, long id)` com `String asText()`; `record FamilyRef(UUID id)`;
  - `sealed interface InstagramRef permits Profile, Post` com
    `static Optional<InstagramRef> parse(String)` e `String canonicalUrl()`;
  - `HttpsLink.parse(String raw, int maxLength): Optional<URI>`;
  - `TextNorm.normalize(String): String`;
  - `enum MarketKind { SUPERMARKET, CONVENIENCE, GREENGROCER, MARKETPLACE }`;
  - `LocationTooPreciseException`;
  - `ModuleKey.MARKETS` com valor `markets`.

**Como fica:**
- A V40 é o DDL da seção 6.3 do spec (V40), sem mudança, mais a troca do `CHECK` de
  `workspace_modules`. Copiar o `CHECK` atual e acrescentar `'markets'`; o nome da constraint vem
  da migração que a criou.
- `kind` em `markets` também ganha `CHECK (kind IS NULL OR kind IN (…))`, com os quatro valores.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `GeoPointTest.rejectsMoreDecimals` | `-10.123` com `requireScale(2)` → `LocationTooPreciseException`; `-10.10` e `-10.1` passam |
| `GeoPointTest.rejectsOutOfRange` | lat 90.01 → `IllegalArgumentException` |
| `SearchBoxTest.threeKmAtTheEquator` | centro (0, 0), raio 3 → meia-altura e meia-largura `0.03593` |
| `SearchBoxTest.widerAwayFromTheEquator` | centro (-30.00, -50.00), raio 3 → meia-largura `0.04149` |
| `MarketRefTest.parsesBothForms` | `osm-w-45` → `OsmRef('w', 45)`; um uuid → `FamilyRef`; `osm-x-1`, `osm-n-`, `abc` → vazio |
| `InstagramRefTest.acceptsProfileForms` | `@mercado.exemplo`, `mercado.exemplo`, `https://www.instagram.com/mercado.exemplo/?hl=pt` → `https://www.instagram.com/mercado.exemplo/` |
| `InstagramRefTest.acceptsPostUrl` | `https://www.instagram.com/p/AbC123/?igsh=x` → `Post`, canônica sem query |
| `InstagramRefTest.rejectsOthers` | `http://instagram.com/x`, `https://evil.example/p/1/`, perfil com 31 caracteres → vazio |
| `HttpsLinkTest.onlyHttpsWithoutCredentials` | `http://…`, `https://u:p@x.example/`, `javascript:…`, 501 caracteres → vazio |
| `TextNormTest.normalizes` | os dois exemplos de D13 |
| `MarketsSchemaIT.checkRejectsMixedOrigin` | `OSM` com `name` → violação; `FAMILY` sem `name` → violação; segundo `OSM` com o mesmo objeto na família → violação |
| `MarketsSchemaIT.moduleKeyAccepted` | `INSERT` em `workspace_modules` com `markets` passa |
| `ApplicationModulesTest` | passa com `markets` |

- [ ] **Step 2: Rodar e ver falhar.** Em `api/`:
  `./gradlew test --tests '*markets*' --tests '*ApplicationModulesTest'` → FAIL.
- [ ] **Step 3: Implementar.** A migração, o `package-info.java`, o domínio, o enum e o
  `allowableValues`. Depois, `./gradlew generateOpenApi` (ou a tarefa que o projeto usa para o
  `openapi.json`) e `npm run generate:api` em `web/`.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando → PASS.
- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL; `npm run typecheck` em `web/`.
- [ ] **Step 6: Commit.**
  `git commit -m "feat(markets): add the v40 tables, the markets module and its domain rules"`.

---

### Task 2: O workflow do extrato semanal

**Files:**
- Create: `.github/workflows/osm-markets.yml`, `scripts/osm/to_csv.py`,
  `scripts/osm/test_to_csv.py`, `scripts/osm/testdata/sample.geojsonseq`,
  `scripts/osm/testdata/expected.csv`.

**Interfaces:**
- Produces: o formato da D15, que a Task 3 lê.
- `to_csv.py`:
  - argumentos: `<entrada.geojsonseq> <saida.csv.gz> <manifest.json> <osm_timestamp>`;
  - `to_row(feature: dict) -> list[str] | None`, que devolve `None` para objeto sem posição ou sem
    tipo reconhecido.

**Como fica:**
- Gatilhos: `schedule: cron '17 6 * * 1'` e `workflow_dispatch`. Mais `pull_request` com `paths`
  `scripts/osm/**` e o próprio workflow, só para o job de teste.
- `permissions: {}` no topo.
- Job `test` (`contents: read`): `python3 -m unittest discover -s scripts/osm`.
- Job `build` (`needs: test`, só fora de `pull_request`, `contents: read`):
  1. `sudo apt-get install -y osmium-tool`.
  2. `curl -fsSL -A "LiberdadeBot/1.0 (+<origem>/robo)"` do `.pbf` e do `.md5` da Geofabrik, e
     `md5sum -c`.
  3. `osmium tags-filter brazil-latest.osm.pbf nwr/shop=supermarket,convenience,greengrocer nwr/amenity=marketplace -o markets.osm.pbf`.
  4. `osmium export markets.osm.pbf -f geojsonseq --add-unique-id=type_id -o markets.geojsonseq`.
     O implementador confere em docs.osmcode.org o nome das opções na versão do `apt` e anota no
     relatório.
  5. `osmium fileinfo -g header.option.osmosis_replication_timestamp brazil-latest.osm.pbf` (ou o
     campo equivalente) para o `osm_timestamp`.
  6. `python3 scripts/osm/to_csv.py …` e `actions/upload-artifact` dos dois arquivos.
- Job `publish` (`needs: build`, `contents: write`):
  - baixa o artefato e cria o release `osm-markets-data` com
    `gh release create … --prerelease --latest=false` na primeira vez;
  - substitui os assets com `gh release upload osm-markets-data … --clobber`;
  - corpo do release: o texto da atribuição do spec 6.4.
- `concurrency: osm-markets`, `timeout-minutes: 30` no `build`.
- A fixture tem 5 objetos inventados, nenhum endereço real:
  - um nó `supermarket` completo;
  - uma via `greengrocer` com geometria de polígono;
  - uma relação `marketplace` com multipolígono;
  - um nó `convenience` sem nome e com `contact:instagram`;
  - um objeto `shop=bakery`, que sai.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste (`test_to_csv.py`) | Esperado |
|---|---|
| `test_sample_matches_expected` | rodar sobre `sample.geojsonseq` gera exatamente `expected.csv` (descomprimido) |
| `test_polygon_uses_bbox_center` | quadrado de (−10.0, −50.0) a (−10.002, −50.002) → `-10.00100,-50.00100` |
| `test_unknown_kind_is_dropped` | `shop=bakery` → `None` |
| `test_manifest_has_sha_and_count` | `sha256` do `.csv.gz` gravado, `rowCount` 4, `license` `ODbL-1.0` |
| `test_quotes_commas_in_names` | nome `Mercado Exemplo, Filial` sai entre aspas |

- [ ] **Step 2: Rodar e ver falhar.** `python3 -m unittest discover -s scripts/osm` → FAIL.
- [ ] **Step 3: Implementar** o script (só `json`, `csv`, `gzip`, `hashlib`, `sys`) e o workflow.
- [ ] **Step 4: Rodar e ver passar.** Mesmo comando → PASS. Também `actionlint`, se o projeto já
  usa; senão, conferir a sintaxe com o `act --list` ou com o próprio CI do PR.
- [ ] **Step 5: Commit.**
  `git commit -m "ci(markets): publish the weekly openstreetmap markets extract"`.

---

### Task 3: A importação semanal e o alarme

**Files:**
- Create: os arquivos da Task 3 no mapa e `api/src/test/resources/markets/` (manifestos e `.csv.gz`
  de teste, gerados a partir do `expected.csv` da Task 2).
- Modify: `application.properties`, `application-dev.properties`, `application-e2e.properties`,
  `api/src/test/resources/application-test.properties`.
- Test: `markets/OsmCsvReaderTest.java`, `markets/GithubOsmDatasetTest.java`,
  `markets/OsmMarketImportIT.java`, `markets/MarketsStartupCheckTest.java`,
  `infra/live/tests/operations.tftest.hcl`.

**Interfaces:**
- Consumes: `MarketKind`, `GeoPoint` (Task 1).
- Produces:
  - `record OsmManifest(Instant osmTimestamp, int rowCount, String sha256)`;
  - `interface OsmDataset { OsmManifest manifest() throws OsmImportException; InputStream open() throws OsmImportException; }`;
  - `OsmImportException(Reason reason)` com
    `enum Reason { SHA_MISMATCH, TOO_FEW_ROWS, BAD_ROW, HTTP, TIMEOUT, TOO_LARGE, BAD_MANIFEST }`;
  - `OsmCsvReader.read(InputStream gz, Consumer<OsmMarketRow> sink) throws OsmImportException`
    (`record OsmMarketRow(char type, long id, MarketKind kind, …)` com as colunas de `osm_markets`);
  - `OsmMarketImporter.importNow(): ImportOutcome` (`IMPORTED`, `SKIPPED_SAME_DATE`, `FAILED`);
  - `MarketsAlerts.importFailed(Reason)`, `MarketsAlerts.datasetStale()`;
  - `MarketsProperties` (D2) e `interface Sleeper` (cópia da dos indicadores, no pacote `markets`).

**Como fica:** D14, D15, D16 e D23. O SHA-256 é conferido lendo o arquivo uma vez para um arquivo
temporário (`Files.createTempFile`, apagado no `finally`) e depois lendo dele. Assim o banco nunca
recebe linha de arquivo não conferido.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `OsmCsvReaderTest.readsTheSharedSample` | o `expected.csv` da Task 2 dá 4 linhas com os campos certos |
| `OsmCsvReaderTest.badKindIsBadRow` | `kind` `BAKERY` → `BAD_ROW` |
| `GithubOsmDatasetTest.sendsTheBotUserAgent` | `MockRestServiceServer` confere `User-Agent` |
| `GithubOsmDatasetTest.cutsAtMaxBytes` | corpo maior que `max-bytes` → `TOO_LARGE` |
| `OsmMarketImportIT.firstImportFillsTheTable` | 4 linhas e uma em `osm_market_imports` |
| `OsmMarketImportIT.sameDateIsSkipped` | segundo `importNow()` com o mesmo manifesto → `SKIPPED_SAME_DATE`, nada muda |
| `OsmMarketImportIT.shaMismatchIsRefused` | SHA errado → `FAILED`, tabela igual, log `MARKETS_ALERT type=IMPORT_FAILED reason=SHA_MISMATCH` |
| `OsmMarketImportIT.halfTheRowsIsRefused` | antes 4, agora 2 → `TOO_FEW_ROWS`, tabela igual |
| `OsmMarketImportIT.badRowAtTheEndKeepsOldRows` | última linha ruim → tabela igual, sem linha nova em `osm_market_imports` |
| `OsmMarketImportIT.readersSeeOldRowsUntilCommit` | com um gancho de teste parando o importador depois do `DELETE`, outra conexão conta as linhas antigas |
| `OsmMarketImportIT.staleAfterFifteenDays` | última importação há 16 dias (`Clock` fixo) → `MARKETS_ALERT type=DATASET_STALE`; há 14 → nada |
| `OsmMarketImportIT.logsCarryNoNames` | nenhum log da importação contém "Mercado Exemplo" |
| `MarketsStartupCheckTest.fakeRefusedUnderProd` | perfil `prod` com qualquer `source=fake` → falha ao subir |
| `operations.tftest.hcl` | dois `aws_cloudwatch_metric_alarm` `markets_alert` (staging e produção) com `alarm_actions` no tópico |

- [ ] **Step 2: Rodar e ver falhar.** `./gradlew test --tests '*markets*'` e, em `infra/live`,
  `terraform test` → FAIL.
- [ ] **Step 3: Implementar.** Os arquivos, o `@EnableScheduling` já existe. O gancho de teste é um
  `ImportProgressListener` opcional (bean só no teste), chamado depois do `DELETE`.
- [ ] **Step 4: Rodar e ver passar** → PASS.
- [ ] **Step 5: Runbook.** `docs/runbooks/markets-alerts.md`: o que cada `reason` quer dizer, como
  rodar o workflow à mão, como forçar uma importação no staging (reiniciar a API com a tabela
  vazia, ou esperar a terça) e como forçar um alerta (pôr `LIBERDADE_MARKETS_OSM_DATASET_URL` num
  endereço que não responde).
- [ ] **Step 6: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 7: Commit.**
  `git commit -m "feat(markets): import the weekly markets extract and alert on failures"`.

---

### Task 4: Busca por área e cartão

**Files:**
- Create: os arquivos da Task 4 no mapa.
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/MarketSearchIT.java`, `markets/MarketCardIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `GeoPoint`, `SearchBox`, `MarketRef`, `InstagramRef` (Task 1); `osm_markets` e
  `osm_market_imports` (Task 3).
- Produces:
  - `POST /v1/workspaces/{ws}/markets/search` → `SearchResultView` (D5);
  - `GET /v1/workspaces/{ws}/markets/{ref}` → `MarketCardView` (D6);
  - `GET /v1/workspaces/{ws}/markets/settings` → `{geocodingEnabled, instagramEnabled}`;
  - `MarketRepository.findByRef(UUID ws, MarketRef ref): Optional<MarketRow>` e
    `MarketRepository.ensureOsmRow(UUID ws, OsmRef ref, UUID actor): UUID` (cria a linha `OSM` se
    falta e devolve o id), usados pelas Tasks 5 e 7 e pela 10.2.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MarketSearchIT.threeDecimalsIs422` | `lat` `"-10.001"` → 422 `markets.location_too_precise` |
| `MarketSearchIT.radiusOutOfRangeIs422` | 0 e 11 → 422 `markets.radius_invalid`; sem raio → usa 3 |
| `MarketSearchIT.returnsSeparateLists` | com 2 do OSM e 1 da família na área: `osm` tem 2, `family` tem 1, nenhum repetido |
| `MarketSearchIT.familyEditShowsOnOsmItem` | depois de `ensureOsmRow`, o item do OSM traz `familyId` |
| `MarketSearchIT.capsAtFiveHundredNearestFirst` | 600 linhas na caixa → 500, a mais perto primeiro |
| `MarketSearchIT.leftAndUnlocatedSections` | linha `OSM` sem objeto em `osm_markets` vem em `left`; `FAMILY` sem posição vem em `unlocated` |
| `MarketSearchIT.archivedNeverListed` | arquivado não está em lista nenhuma |
| `MarketSearchIT.datasetDateInSaoPaulo` | `osm_timestamp` 2026-11-18T01:00Z → `"2026-11-17"` |
| `MarketCardIT.familyLinkWinsWithItsDate` | site do OSM e da família → `source = FAMILY`, `date` = `website_set_at` |
| `MarketCardIT.osmLinkCarriesDatasetDate` | só o OSM → `source = OSM`, `date` = `datasetDate` |
| `MarketCardIT.badOsmInstagramIsHidden` | `contact:instagram` `"não sei"` → `instagram` nulo |
| `MarketCardIT.unknownRefIs404` | `osm-n-999999` sem linha e `abc` → 404 `market.not_found` |
| `MarketCardIT.otherFamilyRowIs404` | uuid de outra família → 404 |
| `MarketCardIT.moduleOffIs403` | módulo desligado → 403 `module.disabled` |
| `RoleMatrixIT` | `search`, `GET {ref}` e `settings`: os quatro papéis → 200 |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** O `@ControllerAdvice` do módulo (ou o existente, se for global)
  traduz `LocationTooPreciseException` em 422.
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar `openapi.json` e `schema.ts`.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): search markets by area and show the market card"`.

---

### Task 5: Mercados da família e links do cartão

**Files:**
- Create: os arquivos da Task 5 no mapa.
- Modify: `MarketController.java`, `api/openapi.json`, `schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/FamilyMarketIT.java`, `markets/MarketLinksIT.java`, `RoleMatrixIT`.

**Interfaces:**
- Consumes: `MarketRepository.ensureOsmRow`, `findByRef` (Task 4); `HttpsLink`, `InstagramRef`
  (Task 1).
- Produces:
  - `POST /markets`, `PUT /markets/{ref}/links`, `PUT /markets/{ref}/position`,
    `POST /markets/{ref}/archive`, `POST /markets/{ref}/convert` (D7, D8);
  - `record MarketAdded(UUID workspaceId, UUID marketId, UUID actorUserId)`;
  - `record MarketEdited(UUID workspaceId, UUID marketId, UUID actorUserId, String change)`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `FamilyMarketIT.addWithAndWithoutPosition` | com posição → aparece em `family`; sem → em `unlocated` |
| `FamilyMarketIT.sixDecimalsIs422` | `lat` com 6 casas → 422 `markets.location_too_precise` |
| `FamilyMarketIT.onlyOneCoordinateIs422` | só `lat` → 422 |
| `FamilyMarketIT.positionOnlyOnFamily` | `PUT position` num `OSM` → 409 `market.not_family` |
| `FamilyMarketIT.convertLeftOsm` | linha `OSM` sem objeto → `convert` → `origin FAMILY`, `osm_*` nulos, CNPJ e links ficam |
| `FamilyMarketIT.convertActiveOsmIs409` | objeto ainda no OSM → 409 `market.not_left` |
| `FamilyMarketIT.archiveTwiceIsNoop` | segundo `archive` → 204, `archived_at` igual |
| `MarketLinksIT.firstEditCreatesTheOsmRow` | `PUT links` em `osm-n-1` sem linha → cria linha `OSM` com o site; a busca passa a trazer `familyId` |
| `MarketLinksIT.setAtOnlyForChangedField` | trocar só o encarte → `website_set_at` igual, `flyer_set_at` novo |
| `MarketLinksIT.nullFallsBackToOsm` | `website: null` → cartão volta ao site do OSM |
| `MarketLinksIT.rejectsHttpAndBadInstagram` | `http://…` → 422 `markets.link_invalid`; `instagram` inválido → 422 `markets.instagram_invalid` |
| `MarketLinksIT.flyerNeedsSource` | `flyerUrl` sem `flyerSource` → 422 |
| `MarketLinksIT.eventsCarryNoValues` | `MarketEdited` com `change = LINKS`; a auditoria (Task 8) não tem URL |
| `RoleMatrixIT` | cada rota de escrita: Dono e Adulto → 2xx; Leitor e Jovem → 403 |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): let the family add markets and edit the card links"`.

---

### Task 6: Endereço da família e geocodificação

**Files:**
- Create: os arquivos da Task 6 no mapa.
- Modify: `api/openapi.json`, `schema.ts`, `RoleMatrixIT.java`, os `application*.properties`.
- Test: `markets/GeocodingThrottleTest.java`, `markets/GeocodingCacheTest.java`,
  `markets/NominatimGeocoderTest.java`, `markets/GeocodingIT.java`, `markets/MarketHomeIT.java`,
  `RoleMatrixIT`.

**Interfaces:**
- Consumes: `GeoPoint`, `TextNorm` (Task 1); `MarketsProperties`, `Sleeper` (Task 3).
- Produces:
  - `interface Geocoder { List<GeocodedPlace> search(String query) throws GeocodingException; }`;
    `record GeocodedPlace(GeoPoint point, String label)`, com o ponto já em 3 casas;
  - `GeocodingThrottle.acquire(): void`, que lança `GeocodingBusyException` depois de 10 s de espera;
  - `GeocodingLimits.tryConsume(UUID workspaceId): boolean`;
  - `GeocodingCache.get(String normalized)` e `put(…)`;
  - as rotas de D9;
  - `record MarketHomeSet(UUID workspaceId, UUID actorUserId)` e
    `record MarketHomeCleared(UUID workspaceId, UUID actorUserId)`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `GeocodingThrottleTest.secondCallWaitsTheInterval` | duas chamadas com `Clock` fixo → o `Sleeper` recebe `PT2S` na segunda |
| `GeocodingThrottleTest.givesUpAfterTenSeconds` | 6 chamadas à espera → a sexta lança `GeocodingBusyException` sem dormir |
| `GeocodingCacheTest.expiresAfterADay` | `Clock` + 24 h + 1 s → vazio |
| `GeocodingCacheTest.evictsOldestPastLimit` | 1.001 entradas → a primeira saiu |
| `NominatimGeocoderTest.sendsTheRightQuery` | `format=jsonv2`, `countrycodes=br`, `limit=5`, `User-Agent` do bot e `Accept-Language: pt-BR` |
| `NominatimGeocoderTest.roundsAndLabels` | `lat` `"-10.0004567"` → `"-10.000"`; rótulo "Bairro Exemplo, Cidade Exemplo" |
| `NominatimGeocoderTest.badJsonFails` | corpo `{}` → `GeocodingException` |
| `GeocodingIT.twentyFirstIs429` | 20 consultas diferentes passam; a 21ª → 429 `markets.geocoding_limit` |
| `GeocodingIT.cachedDoesNotCount` | a mesma consulta 25 vezes → todas 200 |
| `GeocodingIT.offIs503AndSettingsSaySo` | `enabled=false` → 503 `markets.geocoding_off`; `settings.geocodingEnabled` falso |
| `GeocodingIT.queryNeverLogged` | a consulta "Rua Exemplo 10" não aparece em log nenhum |
| `GeocodingIT.tooLongIs422` | 121 caracteres → 422 |
| `MarketHomeIT.fourDecimalsIs422` | `"-10.0001"` → 422 `markets.location_too_precise` |
| `MarketHomeIT.setReadClear` | `PUT` → `GET` igual → `DELETE` → `GET` 204; eventos `MarketHomeSet` e `MarketHomeCleared` |
| `RoleMatrixIT` | `GET home` e `geocode`: os quatro papéis → 200 (o Jovem e o Leitor também buscam, para a própria localização digitada); `PUT` e `DELETE home`: só Dono e Adulto |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** → PASS. Gerar o contrato.
- [ ] **Step 5: Conferir.** `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): add the family address and polite address search"`.

---

### Task 7: Instagram pelo oEmbed

**Files:**
- Create: os arquivos da Task 7 no mapa e `api/src/test/resources/markets/oembed-ok.json` (gravado
  no Step 1).
- Modify: `MarketController.java`, `api/openapi.json`, `schema.ts`, `RoleMatrixIT.java`.
- Test: `markets/MetaInstagramOembedTest.java`, `markets/InstagramEmbedIT.java`.

**Interfaces:**
- Consumes: `InstagramRef` (Task 1), `MarketRepository.findByRef` (Task 4).
- Produces:
  - `interface InstagramOembed { Optional<String> html(String canonicalUrl) throws OembedException; }`;
  - `POST /v1/workspaces/{ws}/markets/{ref}/instagram-embed` → `{html}` (D10).

- [ ] **Step 1: Conferência real, uma vez (portão).** O implementador faz à mão, fora dos testes:
  `curl -s -A "LiberdadeBot/1.0" "https://graph.facebook.com/v25.0/instagram_oembed?url=<post público de uma conta institucional>&maxwidth=320&omitscript=true"`.
  Faz o mesmo com a URL de um perfil público.
  - Responde com `html` → salva a resposta do post como `oembed-ok.json`, anota se o perfil
    funcionou e segue.
  - Pede token (erro de autenticação) → **para a task**: só o link fica. `instagramEmbeddable`
    sempre `false`, nenhuma rota é feita e a Task 9 não cria a página isolada. O relatório diz
    isso.
- [ ] **Step 2: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MetaInstagramOembedTest.noTokenInTheUrl` | a URL pedida não tem `access_token`; tem `omitscript=true` e `maxwidth=320` |
| `MetaInstagramOembedTest.refusalIsEmpty` | 400 da Meta → `Optional.empty()` |
| `MetaInstagramOembedTest.htmlMustBeABlockquote` | `html` `<script>…` → `Optional.empty()` |
| `InstagramEmbedIT.cachedForSevenDays` | duas chamadas → uma ida à fonte; `Clock` + 7 dias + 1 s → outra ida |
| `InstagramEmbedIT.unavailableIs422` | URL com `privado` (fonte falsa) → 422 `markets.instagram_unavailable` |
| `InstagramEmbedIT.profileFollowsTheGate` | perfil com `instagramEmbeddable` falso → 422 sem chamar a fonte |
| `RoleMatrixIT` | os quatro papéis → 200 (ver não muda nada) |

- [ ] **Step 3: Rodar e ver falhar** → FAIL.
- [ ] **Step 4: Implementar.** Se o Step 1 mostrou que perfil não funciona, `instagramEmbeddable` é
  `true` só para `Post`.
- [ ] **Step 5: Rodar e ver passar** → PASS. Gerar o contrato. `./gradlew build`.
- [ ] **Step 6: Commit.** `git commit -m "feat(markets): fetch the instagram embed on request"`.

---

### Task 8: Exportação, auditoria, robô e política

**Files:**
- Create: `markets/MarketsExport.java`, `markets/internal/JdbcMarketsExport.java`,
  `features/legal/texts/privacy-<nova>.ts`.
- Modify: `privacy/internal/ExportService.java`, `privacy/internal/LegalDocuments.java`,
  `privacy/package-info.java`, `audit/package-info.java`, `audit/internal/AuditListeners.java`,
  `audit/AuditEntry.java`, `features/legal/{BotPage.tsx,legalConfig.ts,messages.pt-BR.ts}`, e os
  testes de exportação existentes.
- Test: `privacy/ExportMarketsIT.java`, `audit/MarketsAuditIT.java`, `privacy/FamilyDeletionIT`
  (o existente ganha as tabelas da Feira), `BotPage.test.tsx`, `legalTexts.test.ts`.

**Interfaces:**
- Consumes: os eventos das Tasks 5 e 6.
- Produces:
  - `interface MarketsExport { List<ExportedMarket> familyMarkets(UUID ws); Optional<ExportedMarketHome> home(UUID ws); }`;
  - os registros públicos `ExportedMarket` e `ExportedMarketHome`, com os campos dos CSVs de D12;
  - a versão nova da política, em `legalConfig.latest.privacy`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ExportMarketsIT.familyExportHasFeiraFiles` | `feira-mercados.csv` com o mercado da família e a linha `OSM` (nome vazio, `ref_osm` preenchido); `feira-endereco.csv` com ponto e rótulo |
| `ExportMarketsIT.noOsmDataInExport` | o nome do mercado do OSM não aparece no zip |
| `ExportMarketsIT.formatVersionBumped` | `formatVersion` = a do `main` + 1 |
| `MarketsAuditIT.actionsRecorded` | as quatro ações de D11, com `targetType` certo e metadata só com `change` |
| `MarketsAuditIT.noCoordinatesInMetadata` | nenhuma metadata contém `-10.` |
| `FamilyDeletionIT` | apagar a família apaga `markets` e `market_home` |
| `BotPage.test.tsx › listsTheNewCollections` | menciona Geofabrik (pelo GitHub Actions), `nominatim.openstreetmap.org` e o oEmbed do Instagram |
| `BotPage.test.tsx › showsOsmAttribution` | seção "Dados de terceiros" com "© OpenStreetMap contributors", ODbL e o link do copyright |
| `legalTexts.test.ts` | a versão nova existe, tem a seção "Feira" (com a frase da nota fiscal e "CPF") e é a `latest` |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** D11, D12, D21 e D22. A versão da política e o `FORMAT_VERSION` saem
  do pré-requisito.
- [ ] **Step 4: Rodar e ver passar** → PASS. `./gradlew build` e `npm test` em `web/`.
- [ ] **Step 5: Commit.**
  `git commit -m "feat(markets): export, audit and explain the feira data in the policy and bot page"`.

---

### Task 9: CSP, permissões e a página isolada do Instagram

**Files:**
- Create: `web/public/embed/instagram.html`, `web/public/embed/instagram.js`.
- Modify: `web/config/securityHeaders.ts`, `web/vite.config.ts`.
- Test: `web/config/securityHeaders.test.ts`, `web/src/features/markets/instagramEmbed.test.ts`
  (importa `../../../public/embed/instagram.js`).

**Interfaces:**
- Produces:
  - `acceptEmbed(html: string): HTMLElement | null`, exportado por `instagram.js`;
  - a página aceita a mensagem `{type: 'liberdade-instagram', html}` (D19);
  - o `_headers` com D20.
- Se a Task 7 parou no portão, esta task faz só a CSP (`frame-src`) e o `Permissions-Policy`: sem a
  página, sem o bloco `/embed/`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `securityHeaders.test.ts › appCspAllowsOnlyGoogleFrames` | CSP do app contém `frame-src 'self' https://www.google.com` e nenhum outro host em `frame-src`; `script-src 'self'` igual |
| `securityHeaders.test.ts › geolocationSelfCameraOff` | `geolocation=(self)` e `camera=()` |
| `securityHeaders.test.ts › embedBlockReplacesCsp` | o bloco `/embed/instagram*` tem `! Content-Security-Policy` e a CSP de D20 |
| `instagramEmbed.test.ts › acceptsTheOfficialShape` | `blockquote.instagram-media` com permalink `https://www.instagram.com/p/x/` → elemento |
| `instagramEmbed.test.ts › stripsScripts` | `blockquote` com `<script>` e `onclick` dentro → elemento sem os dois |
| `instagramEmbed.test.ts › rejectsForeignPermalink` | permalink `https://evil.example/` → `null` |
| `instagramEmbed.test.ts › rejectsTwoBlockquotes` | dois `blockquote` → `null` |
| `instagramEmbed.test.ts › rejectsOtherOrigin` | `MessageEvent` com `origin` `https://evil.example` → nada inserido, `embed.js` não pedido |
| `instagramEmbed.test.ts › loadsEmbedScriptOnce` | duas mensagens válidas → um só `<script src="https://www.instagram.com/embed.js">` |

- [ ] **Step 2: Rodar e ver falhar.** Em `web/`: `npm test -- securityHeaders instagramEmbed` → FAIL.
- [ ] **Step 3: Implementar.** A página tem só `<div id="root"></div>` e
  `<script type="module" src="/embed/instagram.js"></script>`, sem script inline.
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run build` e conferir `dist/_headers` à mão.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): add the frame policy and the isolated instagram page"`.

---

### Task 10: Módulo na web, localização e lista

**Files:**
- Create: os arquivos da Task 10 no mapa (`features/markets/…`) e
  `mocks/marketsHandlers.test.ts`.
- Modify: `shared/modules/registry.ts` (se o tipo pedir), `features/modules/moduleCatalog.ts`,
  `features/modules/messages.pt-BR.ts`, `app/routes.tsx`, `app/MorePage.tsx`, `mocks/handlers.ts`,
  `mocks/data.ts`, o limpador dos dados do aparelho no sair da conta.
- Test: `features/markets/{geo.test.ts,locationStore.test.ts,LocationChooser.test.tsx,FeiraPage.test.tsx}`,
  `app/MorePage.test.tsx`, `features/modules/ModulesPage.test.tsx`, `mocks/marketsHandlers.test.ts`.

**Interfaces:**
- Consumes: o contrato das Tasks 4 a 6 (`schema.ts`).
- Produces:
  - `roundTo`, `distanceMeters`, `formatDistance` (D18);
  - `readLocation(userId): StoredLocation | null`, `writeLocation(userId, value)`,
    `clearMarketsStorage()` (D17);
  - `useMarketSearch`, `useMarketCard`, `useMarketHome`, `useMarketsSettings` (D17);
  - `<OsmAttribution date={…} />`, usado também pelo cartão.

**Como fica:**
- `LocationChooser`: o diálogo do spec 6.4.
  - As opções somem quando não se aplicam: "Digitar um endereço" com a geocodificação desligada,
    "Endereço da família" sem `market_home`.
  - "Continuar" pede a permissão só na hora.
  - Permissão negada: mensagem e volta ao diálogo.
- `FeiraPage`:
  - topo com o modo atual ("📍 Aproximada ▾"), que abre o diálogo;
  - a lista de `osm` + `family`, medida e ordenada, até 50;
  - "Mercado que saiu do OpenStreetMap" (`left`) e "Seus mercados sem localização" (`unlocated`) no
    fim;
  - "+ Adicionar mercado" para Dono e Adulto;
  - `OsmAttribution` sempre visível no rodapé, com link para
    `https://www.openstreetmap.org/copyright`;
  - mercado sem nome: "<Tipo> sem nome".
- MSW: handlers das rotas novas com os mesmos 6 mercados da `FakeOsmDataset`, e `markets` em
  `MODULE_KEYS`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `geo.test.ts › searchCenterAlwaysTwoDecimals` | `roundTo(-10.004999, 2)` → `"-10.00"`; `roundTo(-10.005, 2)` → `"-10.01"` |
| `geo.test.ts › haversineKnownDistance` | (0,0)→(0,0.01) ≈ 1.112 m (±1) |
| `geo.test.ts › formatsDistance` | 447 → "450 m"; 1.234 → "1,2 km"; aproximada → "cerca de 1,2 km" |
| `locationStore.test.ts › preciseNeverStoresAPoint` | `writeLocation` com `precise` e um ponto → o `localStorage` não tem `point` |
| `locationStore.test.ts › survivesBlockedStorage` | `localStorage` lançando → `readLocation` devolve `null`, nada quebra |
| `LocationChooser.test.tsx › asksPermissionOnlyAfterContinue` | montar não chama `getCurrentPosition`; "Continuar" chama uma vez |
| `LocationChooser.test.tsx › approximateRoundsBeforeStoring` | posição `-10.12345` → guardado `"-10.12"` |
| `LocationChooser.test.tsx › hidesTypedWhenGeocodingOff` | `geocodingEnabled` falso → sem a opção |
| `FeiraPage.test.tsx › sortsByDistanceAndCapsAtFifty` | 60 mercados → 50, o mais perto primeiro |
| `FeiraPage.test.tsx › attributionAlwaysVisible` | o link do copyright aparece sem nenhum toque |
| `FeiraPage.test.tsx › unnamedShowsKind` | `name` nulo, `SUPERMARKET` → "Supermercado sem nome" |
| `FeiraPage.test.tsx › readerHasNoAddButton` | papel Leitor → sem "Adicionar mercado" |
| `MorePage.test.tsx › showsFeiraWhenOn` | módulo ligado → item "Feira" |
| `marketsHandlers.test.ts` | busca com 3 casas → 422 no MSW, como na API |

- [ ] **Step 2: Rodar e ver falhar.** `npm test -- markets MorePage ModulesPage` → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint`, `npm run typecheck` e
  `npm run check:chunks` passando.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): add the feira module screen with nearby markets"`.

---

### Task 11: Cartão do mercado e escrita

**Files:**
- Create: os arquivos da Task 11 no mapa.
- Modify: `app/routes.tsx`, `features/markets/messages.pt-BR.ts`, `mocks/handlers.ts`.
- Test: `features/markets/{MarketCardPage.test.tsx,LinksSheet.test.tsx,AddMarketSheet.test.tsx,ConvertMarketSheet.test.tsx,links.test.ts}`.

**Interfaces:**
- Consumes: `useMarketCard` e `OsmAttribution` (Task 10); `SourceStamp` de `shared/ui`; as rotas de
  escrita (Task 5).
- Produces:
  - `mapsSearchUrl`, `mapsDirectionsUrl`, `wazeUrl` (D19);
  - os pontos de encaixe `<MapEmbed>` e `<InstagramEmbed>`, preenchidos na Task 12.

**Como fica:**
- O cartão:
  - mostra nome, tipo, endereço, horário como está no OSM e distância (se a lista passou);
  - site, Instagram e encarte com `SourceStamp`. O da família diz "adicionado pela família · dd/mm/aaaa";
    o do OSM, "OpenStreetMap · dd/mm/aaaa";
  - encarte vencido mostra "encarte vencido";
  - links "Abrir no Maps", "Como chegar" e "Abrir no Waze".
- `LinksSheet` (Dono e Adulto) valida no aparelho com as mesmas regras de D7 e mostra o erro da API
  quando houver.
- `AddMarketSheet`: nome, tipo e posição:
  - "Estou no mercado agora" chama `getCurrentPosition({ enableHighAccuracy: true })` só no toque e
    arredonda a 5 casas;
  - ou "Buscar endereço", que geocodifica e mostra a lista;
  - texto e link para cadastrar no OpenStreetMap
    (`https://www.openstreetmap.org/fixthemap`).
- `ConvertMarketSheet` aparece no cartão com `status = LEFT_OSM`, com "Arquivar" ao lado.
- `FAMILY` sem posição tem "Dar a localização" (o mesmo seletor de posição).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `links.test.ts › buildsTheThreeUrls` | `(-10.00012, -50.00034)` → as três URLs exatas de D19 |
| `MarketCardPage.test.tsx › stampsEachLink` | site da família e Instagram do OSM → dois `SourceStamp` com fontes diferentes |
| `MarketCardPage.test.tsx › expiredFlyer` | `validUntil` ontem (relógio falso) → "encarte vencido" |
| `MarketCardPage.test.tsx › externalLinksAreSafe` | todo link externo tem `target="_blank"` e `rel="noopener noreferrer"` |
| `MarketCardPage.test.tsx › leftOsmOffersConvertAndArchive` | `LEFT_OSM` → os dois botões; Jovem → nenhum |
| `LinksSheet.test.tsx › rejectsHttp` | `http://x.example` → erro no campo, sem chamada |
| `AddMarketSheet.test.tsx › positionOnlyOnTap` | montar não chama a geolocalização; o toque chama e manda 5 casas |
| `ConvertMarketSheet.test.tsx › sendsTypedNameOnly` | o corpo tem só o nome digitado, o tipo e a posição |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** → PASS. `npm run lint`, `typecheck`, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): add the market card with links, flyer and family edits"`.

---

### Task 12: Mapa e Instagram ao tocar, endereço da família

**Files:**
- Create: `features/markets/{ThirdPartyNotice.tsx,MapEmbed.tsx,InstagramEmbed.tsx}`,
  `features/members/MarketHomeSection.tsx`.
- Modify: `features/markets/MarketCardPage.tsx`, `features/members/FamilyPage.tsx`,
  `web/src/vite-env.d.ts` (`VITE_GOOGLE_MAPS_EMBED_KEY?: string`), `web/.env.example` (se existir).
- Test: `features/markets/{MapEmbed.test.tsx,InstagramEmbed.test.tsx}`,
  `features/members/MarketHomeSection.test.tsx`, `MarketCardPage.test.tsx`.

**Interfaces:**
- Consumes:
  - a rota de D10 (Task 7) e a página isolada (Task 9);
  - `useMarketHome` e as rotas de D9 (Task 6);
  - `readLocation`/`writeLocation` para os consentimentos (Task 10).
- Produces: nada que outra task use.

**Como fica:**
- D19.
- `MarketHomeSection` aparece em "Família" só com o módulo ligado e só para Dono e Adulto.
  - Busca o endereço (`geocode`) e mostra os lugares.
  - Guarda o escolhido com um rótulo opcional.
  - "Remover" com `ConfirmDialog`.
  - O texto digitado nunca vai para o `localStorage` nem para o estado global.
  - Mostra "Busca de endereço © OpenStreetMap".

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MarketCardPage.test.tsx › noIframeBeforeConsent` | sem toque, nenhum `iframe` no documento |
| `MapEmbed.test.tsx › noticeThenIframe` | toque → aviso; "Abrir mapa" → `iframe` com `src` de D19 e `referrerpolicy="strict-origin-when-cross-origin"` |
| `MapEmbed.test.tsx › notNowKeepsNothing` | "Agora não" → sem `iframe` |
| `MapEmbed.test.tsx › dontAskAgainRemembers` | com a caixa marcada, o segundo toque abre direto |
| `MapEmbed.test.tsx › noKeyNoButton` | sem `VITE_GOOGLE_MAPS_EMBED_KEY` → sem "Ver no mapa" |
| `MapEmbed.test.tsx › showsTheMarketNotThePerson` | o `q=` é a posição do mercado, nunca a da pessoa |
| `InstagramEmbed.test.tsx › sandboxWithoutSameOrigin` | `sandbox="allow-scripts allow-popups"` exato |
| `InstagramEmbed.test.tsx › postsTheHtmlOnLoad` | `load` → `postMessage({type:'liberdade-instagram', html}, '*')` |
| `InstagramEmbed.test.tsx › unavailableKeepsTheLink` | 422 → "Este perfil não pode ser mostrado aqui" e o link |
| `MarketHomeSection.test.tsx › storesOnlyPointAndLabel` | o corpo do `PUT` é `{lat, lon, label}` com 3 casas, sem o texto |
| `MarketHomeSection.test.tsx › hiddenForYouth` | Jovem e Leitor → a seção não aparece |
| `MarketHomeSection.test.tsx › geocodingOffCannotChange` | `geocodingEnabled` falso → mostra o rótulo atual e "Remover", sem campo de busca |

- [ ] **Step 2: Rodar e ver falhar** → FAIL.
- [ ] **Step 3: Implementar.** Se a Task 7 parou no portão, o `InstagramEmbed` não entra: o cartão
  mostra só o link, e os testes dele saem.
- [ ] **Step 4: Rodar e ver passar** → PASS. `lint`, `typecheck`, `check:chunks`.
- [ ] **Step 5: Commit.** `git commit -m "feat(markets): load the map and instagram only after a tap and set the family address"`.

---

### Task 13: E2E, orçamento e docs

**Files:**
- Create: `web/e2e/feira.spec.ts`.
- Modify: `web/e2e/a11y.spec.ts`, `web/scripts/check-chunks.mjs` (os arquivos da feature na lista
  `lazyOnly`), `docs/architecture.md`, `docs/data-model.md`, `docs/design-system.md`, a
  configuração do build do e2e (a chave falsa `VITE_GOOGLE_MAPS_EMBED_KEY=e2e-key`).
- Test: `web/e2e/feira.spec.ts`, `web/e2e/a11y.spec.ts`, `web/scripts/chunk-budget.test.mjs`.

**Como fica:**
- O perfil `e2e` da API usa as três fontes falsas (D16), e a importação roda na subida.
- `feira.spec.ts`:
  1. Liga o módulo e escolhe "Digitar um endereço" (o geocodificador falso devolve o ponto de
     exemplo).
  2. Vê os mercados da fonte falsa e a atribuição.
  3. Abre um cartão.
  4. Com `page.route('**/*', …)` registrando todo pedido, confere que nenhum foi a `google.com`,
     `instagram.com`, `cdninstagram.com` ou `facebook.com`.
  5. Toca em "Ver no mapa", aceita e confere um pedido a `https://www.google.com/maps/embed/v1/place`.
     O teste aborta esse pedido (`route.abort()`): nada sai de verdade.
  6. Repete com o Instagram: o `iframe` de `/embed/instagram` e um pedido a
     `https://www.instagram.com/embed.js` (abortado).
  7. Adiciona um mercado da família por endereço e o vê na lista.
- `a11y.spec.ts` passa o axe na lista, no cartão (com o aviso aberto) e no diálogo da localização.
- `core-without-modules` segue passando.
- Docs:
  - `architecture.md`: o módulo `markets`, o workflow, a importação e a página isolada;
  - `data-model.md`: a V40 e o mapa de dados, com a localização;
  - `design-system.md`: o aviso de terceiros e o padrão "carrega ao tocar".

- [ ] **Step 1: Escrever o e2e e a entrada no `lazyOnly`.**
- [ ] **Step 2: Rodar.** Em `web/`: `npm run e2e -- feira a11y core-without-modules` → PASS (o e2e
  sobe a API com o perfil `e2e`; se um contêiner antigo atrapalhar localmente, o CI confere; nunca
  recriar contêineres com `down -v`).
- [ ] **Step 3: Conferir tudo.** `./gradlew build` em `api/`; `npm run lint`, `typecheck`, `test`,
  `build` e `check:chunks` em `web/`; `terraform test` em `infra/live`.
- [ ] **Step 4: Commit.** `git commit -m "test(markets): prove nothing reaches google or meta before a tap"`.

## Depois do merge (passos do mantenedor, spec seção 12)

1. Criar a chave do Maps Embed:
   - num projeto do Google Cloud com faturamento;
   - restrita à Maps Embed API e aos domínios de staging e produção;
   - com alerta de orçamento de R$ 1.
2. Pôr `VITE_GOOGLE_MAPS_EMBED_KEY` como variável de cada Environment.
3. Aplicar `infra/live/markets_alerts.tf`.
4. Rodar o workflow `osm-markets` uma vez à mão e conferir o release `osm-markets-data` (pré-release,
   fora de "latest", com a atribuição).
5. Reiniciar a API do staging (a tabela vazia dispara a importação) e conferir `MARKETS_IMPORT ok`
   no log.
6. No staging:
   - ligar a Feira e buscar perto de um ponto de teste;
   - conferir a atribuição, o mapa abrindo com a chave restrita (o `referrerpolicy` resolve o
     `no-referrer`) e o Instagram carregando só depois do toque;
   - conferir a CSP da página isolada no console.
   - Se a CSP pedir outro host, um PR pequeno ajusta o `securityHeaders.ts`.
7. Publicar a política nova junto com a 10.1.
8. PR de docs: README dos planos, linha 10, "10.1 entregue".
