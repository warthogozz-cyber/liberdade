# Plano 10: Feira (design)

> **Status:** Em revisão · **Data:** 2026-11-24

**Base:** [PRD](../../prd.md) (US-50, US-51, RF-12, §9), [roadmap](../../roadmap.md) (Plano 10),
[modelo de dados](../../data-model.md), [arquitetura](../../architecture.md) (cabeçalhos),
[spec do Plano 8](2026-11-24-liberdade-plan-8-indicators-gamification-design.md) (coleta educada,
`SourceStamp`, guarda do plano Família), [ADR-0015](../../adr/0015-revenue-model-and-billing.md).

## 1. Intenção e porquê

Mercado é o maior gasto variável da maioria das famílias. Hoje o app mostra quanto foi para
"Mercado", mas não ajuda a gastar menos lá. A Feira resolve duas coisas:

- **Onde comprar perto de casa.** Uma lista dos mercados próximos, com distância, site, Instagram e
  o encarte da semana, sem a família ter de procurar em cinco apps.
- **Quanto custa em cada um.** A família escaneia o QR da nota fiscal (NFC-e) e o app guarda os
  preços daquele mercado. Com o tempo, a lista de compras mostra quanto ela custaria em cada mercado,
  com os preços que a própria família pagou.

É um recurso do plano Família (ADR-0015). Tudo que fala com terceiros (mapa, Instagram) só carrega
depois de um toque.

## 2. Objetivos, não-objetivos e antiobjetivos

**Objetivos**

- US-50: localização do aparelho (precisa, aproximada ou digitada) ou o endereço da família; lista
  de mercados do OpenStreetMap com distância e a atribuição ODbL; mapa do Google e Instagram só
  depois de um toque; links para abrir no Maps e no Waze; a família adiciona um mercado que falta.
- Cartão do mercado com site, Instagram e encarte, cada um com fonte e data.
- US-51: escanear o QR da NFC-e registra os preços dos itens naquele mercado; a família compara uma
  lista de compras entre mercados com os preços que ela mesma registrou.

**Não-objetivos**

- Mapa de preços compartilhado e anônimo entre famílias (fase 5, RF-12).
- Lançar o gasto a partir da nota ou ligar a nota a um lançamento. A nota registra só preços
  (decisão do mantenedor); a ligação fica para depois.
- Raspar preços de sites de mercados, de apps de entrega ou de encartes.
- Guardar ou exibir o encarte dentro do app (upload, imagem, PDF). O encarte é um link.
- "Aberto agora", rotas, tempo de trajeto e mapa com todos os mercados de uma vez.
- Marcar itens da lista sem sinal (a lista abre do cache, mas marcar precisa de rede).
- Notificações de promoção ou de preço.

**Antiobjetivos (falharia mesmo funcionando)**

- Qualquer requisição ao Google ou à Meta antes de a pessoa tocar.
- A localização precisa sair do aparelho ou ficar guardada em algum lugar.
- O CPF ou o nome de quem comprou ser lido, guardado ou registrado em log.
- Consultar a SEFAZ de um estado cujo robots.txt ou termos não permitem, ou em lote, sem uma pessoa
  pedir.
- Pressão para gastar: "economize comprando mais", contagem regressiva de oferta, ranking que grita
  "o mais barato".
- Misturar os mercados da família com os do OpenStreetMap numa base só (viraria base derivada da
  ODbL; seção 6.4).
- O pacote inicial passar de 200 KB.

## 3. Restrições

- **Plano Família.** Toda rota da Feira usa `@RequiresPlan(value = FAMILY, openWhileBillingOff =
  true)`, a guarda que o Plano 8.2 cria. Com a cobrança desligada, vale para todos.
- **Módulo desligado por padrão.** A Feira é um módulo novo, `markets`, que a família liga em
  Módulos.
- **Pacote inicial em 200 KB.** A Feira, o leitor de QR e as telas do mapa carregam sob demanda
  (PRD: "cobrança, som, importação e Feira carregam sob demanda").
- **Localização (LGPD, PRD):** só com permissão, com escolha entre precisa, aproximada ou digitada.
  Localização é dado pessoal, não sensível (art. 5º I e II). Vale a necessidade (art. 6º III): o
  servidor recebe só o que precisa.
- **Coleta educada:** cache, limite de requisições e bot identificado (`LiberdadeBot/1.0`, página
  `/robo`), só onde o robots.txt e os termos permitem.
- **Servidor pequeno:** a produção roda numa t4g.small (2 GB). Processar o extrato do Brasil do
  OpenStreetMap (2 GB) não cabe ali; vai para o GitHub Actions.
- **Sem PostGIS.** O banco é PostgreSQL 18 puro; a busca por área usa latitude e longitude com
  índice comum.
- **Infraestrutura só planejada.** Chave do Google, variáveis, alarmes e Terraform são passos do
  mantenedor, nunca aplicados pelo plano.
- **WCAG 2.2 AA**, PT-BR na interface, logs sem valores, sem descrições e sem localização.

## 4. Abordagens consideradas

### 4.1 De onde vêm os mercados

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **Extrato semanal** | Um workflow semanal do GitHub baixa o extrato do Brasil da Geofabrik, filtra só mercados com `osmium` e publica um arquivo pequeno. A API importa esse arquivo por semana | Nenhum ponto da família vai a terceiros e não há cota. O dado tem até uma semana de atraso. Um workflow e um job de importação novos | **Escolhida pelo mantenedor** |
| Overpass público por região | A API consulta o Overpass por área e guarda em cache | Simples, mas a política do servidor público pede servidor próprio ou pago para uso comercial, e ele respondeu 502 "at capacity" no teste de 24/11 | Descartada |
| Overpass próprio ou pago | Instância nossa com o Brasil | Dado mais fresco, mas custa uma máquina com mais memória ou uma assinatura | Descartada |

### 4.2 Itens da nota fiscal

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **Servidor onde pode** | A API abre a página da nota na SEFAZ, uma requisição por nota escaneada, só nos estados da lista permitida. Nos outros, a pessoa digita os preços | Itens automáticos onde é permitido; respeita robots.txt e termos. Depende do HTML de cada estado | **Escolhida pelo mantenedor** |
| Só no aparelho | A pessoa abre a nota no navegador e copia ou digita | Nenhuma consulta do servidor, mas muito trabalho manual | Descartada |
| API comercial de NFC-e | Um serviço pago devolve os itens | Funciona em todo estado, mas custa por nota e entrega dados do consumidor a um terceiro | Descartada |

### 4.3 O que a nota registra

| Abordagem | Troca | Escolha |
|---|---|---|
| **Só os preços** | Sem contar duas vezes com a importação do extrato (8.2) e com o lançamento à mão | **Escolhida pelo mantenedor** |
| Preços e um gasto | Menos digitação, mas duplica o gasto de quem também importa o extrato | Descartada |

### 4.4 Como comparar

| Abordagem | Troca | Escolha |
|---|---|---|
| **Produtos da família** | Cada item vira um produto da família; junta sozinho pelo código de barras ou pela mesma descrição no mesmo mercado e sugere juntar parecidos. A lista usa produtos | **Escolhida pelo mantenedor** |
| Texto livre | Mais simples, mas "LEITE INT 1L" e "Leite integral 1 L" nunca se encontram | Descartada |
| Catálogo nacional de produtos | Compararia tudo, mas exige base externa de GTIN e manutenção | Descartada |

### 4.5 Onde calcular a distância

| Abordagem | Troca | Escolha |
|---|---|---|
| **No aparelho** | O servidor recebe só um ponto arredondado a ~1 km e devolve os mercados da área; o aparelho mede a distância com a posição precisa, que nunca sai dele | **Escolhida (decisão do design)** |
| No servidor | Mais simples, mas a posição precisa trafegaria e poderia parar em log | Descartada |

### 4.6 Instagram sem afrouxar a CSP do app

| Abordagem | Troca | Escolha |
|---|---|---|
| **Página isolada** | Um `iframe` com `sandbox` aponta para uma página estática do próprio site (`/embed/instagram`), com CSP própria que libera `www.instagram.com`. A CSP do app não muda em `script-src` | **Escolhida (decisão do design)** |
| Liberar o Instagram na CSP do app | Mais simples, mas qualquer tela poderia carregar script da Meta | Descartada |
| Só link | Mais seguro, mas não cumpre "Instagram ao tocar" do PRD | Fica como padrão até o toque |

## 5. Decisões de produto (do mantenedor)

Decididas em 16/11/2026:

1. **Localização** do aparelho, com a pessoa escolhendo precisa, aproximada ou manual, ou o endereço
   da família.
2. **Mercados do OpenStreetMap**, com cache semanal no nosso banco e atribuição ODbL. A família pode
   adicionar um mercado que falta.
3. **Lista com distâncias.** O mapa do Google (Maps Embed) só carrega ao tocar, com aviso de cookies.
   Links "Abrir no Maps" e "Abrir no Waze".
4. **Cartão do mercado** com site, Instagram e encarte, cada um com fonte e data. O oEmbed oficial
   do Instagram só ao tocar. Nada de `iframe` do site inteiro.
5. **Preços primeiro pelo QR da NFC-e.** O mapa de preços compartilhado é da fase 5.
6. **CSP:** `frame-src` libera só as origens dos embeds.

Decididas em 24/11/2026:

7. **Extrato semanal** do OpenStreetMap (seção 4.1).
8. **Servidor onde pode** para os itens da nota (seção 4.2).
9. **A nota registra só preços** (seção 4.3).
10. **Produtos da família** para comparar (seção 4.4).
11. **A 10.2 entrega o modo manual agora.** Ela traz a estrutura para os leitores por estado, mas
    nenhum leitor. Cada estado entra depois, num plano curto (10.2.x), quando o mantenedor conferir
    o estado e salvar uma página de nota sem a parte do consumidor (seção 6.5).

## 6. Desenho

### 6.1 Módulos e onde cada coisa mora

| Onde | O quê |
|---|---|
| `.github/workflows/osm-markets.yml` (novo) | Extrato semanal: baixa, filtra, publica o arquivo de mercados |
| API `markets` (novo) | Mercados do OSM (global), importação semanal, mercados da família, endereço da família, busca por área, geocodificação, oEmbed, notas, produtos, preços, lista e comparação |
| API `workspace` | `ModuleKey.MARKETS` e a migração do `CHECK` |
| API `privacy`, `audit` | Exportação, exclusão e auditoria da Feira |
| Web `features/markets` | Telas da Feira, localização, cartão, mapa e Instagram ao tocar, leitor de QR, produtos, lista e comparação |
| Web `public/embed/instagram.html` | Página isolada do Instagram, com CSP própria |
| Web `config/securityHeaders.ts` | CSP, `Permissions-Policy` e o bloco da página isolada |

`markets` depende de `workspace`, `billing` e `shared`. `privacy`, `audit` e `live` passam a
depender de `markets`, como já fazem com `planning` **(decisão do design)**.

**Módulo:** `ModuleKey.MARKETS` (valor `markets`), desligado por padrão. Toca o enum, uma migração
que troca o `CHECK` de `workspace_modules`, os `allowableValues` do `ModuleController`, o
`schema.ts` gerado, `moduleCatalog`, o `register.ts` da feature, as mensagens de Módulos, os
`MODULE_KEYS` do MSW e o mapa de módulos da exportação. Na interface, o nome é "Feira".

**Rotas na web:** `/mais/feira` (lista), `/mais/feira/mercado/:id`, `/mais/feira/nota`,
`/mais/feira/lista`, `/mais/feira/produtos`. Item "Feira" no menu Mais quando o módulo está ligado.

### 6.2 Papéis

| Papel | O que faz na Feira |
|---|---|
| Dono e Adulto | Tudo: endereço da família, mercados, cartões, notas, produtos, lista |
| Leitor | Vê tudo, não muda nada |
| Jovem | Vê mercados, preços e a lista; adiciona e marca itens da lista; não escaneia nem edita mercados e produtos |

Preço de produto não revela saldo, orçamento nem reserva, então o Jovem pode ver **(decisão do
design)**. Ao adicionar à lista, o Jovem pode criar um produto só com o nome, sem preço. Não pode
renomear, trocar a unidade, juntar nem apagar produtos **(decisão do design)**.

### 6.3 Modelo de dados

Migrações a partir de **V40** (reservadas V40–V49), uma por entrega.

**V40 (10.1)**

```sql
-- Global (sem workspace_id), como indicator_series: dado público do OpenStreetMap
CREATE TABLE osm_market_imports (
    id            bigserial PRIMARY KEY,
    osm_timestamp timestamptz NOT NULL,       -- data do dado no OpenStreetMap (manifesto)
    row_count     int NOT NULL,
    sha256        text NOT NULL,
    imported_at   timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE osm_markets (
    osm_type      char(1) NOT NULL CHECK (osm_type IN ('n', 'w', 'r')),
    osm_id        bigint NOT NULL,
    kind          text NOT NULL CHECK (kind IN ('SUPERMARKET', 'CONVENIENCE', 'GREENGROCER', 'MARKETPLACE')),
    name          text NULL,
    brand         text NULL,
    lat           numeric(8,5) NOT NULL,
    lon           numeric(8,5) NOT NULL,
    website       text NULL,
    instagram     text NULL,                  -- de contact:instagram
    opening_hours text NULL,
    street        text NULL,
    housenumber   text NULL,
    suburb        text NULL,
    city          text NULL,
    PRIMARY KEY (osm_type, osm_id)
);
CREATE INDEX osm_markets_by_point ON osm_markets (lat, lon);

-- Da família
CREATE TABLE markets (
    id                uuid PRIMARY KEY,
    workspace_id      uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    origin            text NOT NULL CHECK (origin IN ('OSM', 'FAMILY')),
    osm_type          char(1) NULL,
    osm_id            bigint NULL,            -- sem FK: o mercado pode sumir do OSM
    name              text NULL,              -- só em FAMILY
    kind              text NULL,
    lat               numeric(8,5) NULL,      -- só em FAMILY; pode faltar (criado pela nota)
    lon               numeric(8,5) NULL,
    cnpj              char(14) NULL,          -- do emitente das notas
    website           text NULL,              -- da família, por cima do OSM
    website_set_at    timestamptz NULL,
    instagram         text NULL,
    instagram_set_at  timestamptz NULL,
    flyer_url         text NULL,
    flyer_source      text NULL,              -- até 60 caracteres: "site do mercado", "WhatsApp"
    flyer_valid_until date NULL,
    flyer_set_at      timestamptz NULL,
    created_by        uuid NULL,              -- membership, SET NULL
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_by        uuid NULL,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    archived_at       timestamptz NULL,
    UNIQUE (workspace_id, id),
    CHECK ((origin = 'OSM' AND osm_type IS NOT NULL AND osm_id IS NOT NULL AND name IS NULL AND lat IS NULL)
        OR (origin = 'FAMILY' AND osm_type IS NULL AND osm_id IS NULL AND name IS NOT NULL))
);
CREATE UNIQUE INDEX markets_osm_once ON markets (workspace_id, osm_type, osm_id) WHERE origin = 'OSM';
CREATE UNIQUE INDEX markets_cnpj_once ON markets (workspace_id, cnpj) WHERE cnpj IS NOT NULL;

CREATE TABLE market_home (
    workspace_id uuid PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    lat          numeric(6,3) NOT NULL,       -- 3 casas, ~110 m
    lon          numeric(6,3) NOT NULL,
    label        text NULL,                   -- até 40 caracteres, escolhido pela pessoa ("Casa")
    updated_by   uuid NULL,
    updated_at   timestamptz NOT NULL DEFAULT now()
);
```

Mais a troca do `CHECK` de `workspace_modules` para aceitar `markets`.

- Uma linha em `markets` com `origin = 'OSM'` só existe quando a família fez algo com aquele
  mercado (editou o cartão, ligou um CNPJ, registrou uma nota). Nome, posição e endereço vêm de
  `osm_markets` na leitura; a linha da família guarda só o que é dela **(decisão do design)**.
- Se o mercado sumir do OSM, a linha da família continua. Ela aparece como "Mercado que saiu do
  OpenStreetMap", com o CNPJ se houver. A família pode arquivá-la ou transformá-la em mercado da
  família: digita o nome e a posição, e a linha passa a `origin = 'FAMILY'` com `osm_type` e
  `osm_id` nulos. Nada é copiado do OSM **(decisão do design)**.
- Um mercado `FAMILY` pode ficar sem posição, por exemplo quando foi criado na hora de registrar
  uma nota. Ele aparece numa seção "Seus mercados sem localização" no fim da lista, e a família
  pode dar a posição depois ("Estou no mercado agora" ou um endereço) **(decisão do design)**.

**V41 (10.2)**

```sql
CREATE TABLE products (
    id           uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         text NOT NULL,               -- até 80; editável
    gtin         text NULL,                   -- 8, 12, 13 ou 14 dígitos, com dígito verificador
    unit         text NOT NULL DEFAULT 'UN',  -- UN, KG, G, L, ML, CX, PCT, DZ
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id)
);
CREATE UNIQUE INDEX products_gtin_once ON products (workspace_id, gtin) WHERE gtin IS NOT NULL;

CREATE TABLE product_aliases (             -- "esta descrição, neste mercado, é este produto"
    workspace_id     uuid NOT NULL,
    market_id        uuid NOT NULL,
    description_norm text NOT NULL,
    product_id       uuid NOT NULL,
    PRIMARY KEY (workspace_id, market_id, description_norm),
    FOREIGN KEY (workspace_id, market_id) REFERENCES markets (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, product_id) REFERENCES products (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE receipts (
    id           uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    market_id    uuid NOT NULL,
    key_hash     text NOT NULL,               -- SHA-256 da chave de acesso; a chave não é guardada
    issued_on    date NOT NULL,
    mode         text NOT NULL CHECK (mode IN ('FETCHED', 'MANUAL')),
    item_count   int NOT NULL,
    created_by   uuid NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, key_hash),
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, market_id) REFERENCES markets (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE price_records (
    id           uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    market_id    uuid NOT NULL,
    product_id   uuid NOT NULL,
    receipt_id   uuid NULL,
    observed_on  date NOT NULL,
    unit         text NOT NULL,
    quantity     numeric(19,4) NULL,
    unit_price   numeric(19,4) NOT NULL CHECK (unit_price > 0),
    created_by   uuid NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, market_id) REFERENCES markets (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, product_id) REFERENCES products (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, receipt_id) REFERENCES receipts (workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX price_records_latest ON price_records (workspace_id, product_id, market_id, observed_on DESC);
```

- **A chave de acesso não é guardada, só o hash.** Com a chave, qualquer um abre a página da nota,
  e ela pode ter o CPF de quem pediu "CPF na nota" **(decisão do design)**.
- Preço unitário com 4 casas (a NFC-e traz até 10; preço por kg tem centavos fracionados). Na tela,
  2 casas.

**V42 (10.3)**

```sql
CREATE TABLE shopping_list_items (
    id           uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    product_id   uuid NOT NULL,
    quantity     numeric(19,4) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    checked_at   timestamptz NULL,
    position     int NOT NULL,
    created_by   uuid NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, product_id),
    FOREIGN KEY (workspace_id, product_id) REFERENCES products (workspace_id, id) ON DELETE CASCADE
);
CREATE TABLE product_join_dismissals (     -- "não são o mesmo": não sugerir de novo
    workspace_id uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    product_a    uuid NOT NULL,               -- o menor dos dois ids
    product_b    uuid NOT NULL,
    PRIMARY KEY (workspace_id, product_a, product_b)
);
```

Uma lista por família **(decisão do design)**. Várias listas ficam para depois, se a família pedir.

### 6.4 Entrega 10.1: mercados perto de casa

**Extrato semanal (GitHub Actions)**

- `.github/workflows/osm-markets.yml`: `schedule` toda segunda às 06:17 UTC e
  `workflow_dispatch`. Permissões `{}` no topo e `contents: write` só no job que publica; actions
  fixadas por SHA, como os outros workflows.
- Passos:
  1. Instala `osmium-tool` pelo `apt` do runner Ubuntu.
  2. Baixa `brazil-latest.osm.pbf` e o `.md5` da Geofabrik, com `User-Agent` do bot, e confere o MD5.
  3. `osmium tags-filter` com `nwr/shop=supermarket,convenience,greengrocer` e
     `nwr/amenity=marketplace` (mantém os nós e vias referenciados, para áreas e multipolígonos).
  4. `osmium export` para GeoJSON em sequência, com tipo e id do objeto (opções a conferir na
     documentação do osmium, em docs.osmcode.org, no passo do plano).
  5. Um script curto, sem dependências, reduz cada objeto a um ponto (o próprio nó ou o centro da
     área) e às colunas de `osm_markets`, descarta objetos sem posição e grava
     `markets-br.csv.gz` e `manifest.json` (data do dado, total de linhas, SHA-256, atribuição e
     licença).
  6. Publica os dois arquivos como assets de um release fixo `osm-markets-data`, marcado como
     pré-release e fora de "latest", substituindo os da semana anterior. A tag não segue o padrão
     do release-please, que não a vê.
- O release diz: "© OpenStreetMap contributors. Disponível sob a Open Database License (ODbL)
  1.0." O arquivo publicado é uma base derivada e fica pública sob a ODbL, o que cumpre o
  compartilhamento pela mesma licença **(decisão do design)**.
- Falha do workflow agendado: o GitHub manda e-mail para o mantenedor.

**Importação na API**

- `OsmMarketImportJob`: `@Scheduled` toda terça às 04:40 (`America/Sao_Paulo`) e na subida da API
  se `osm_markets` estiver vazia. Baixa o manifesto e o arquivo de
  `liberdade.markets.osm.dataset-url` (padrão: o release do repositório) com o `User-Agent` do bot.
- Confere o SHA-256 e a data do manifesto. Pula se a data for a mesma da última importação.
- **Proteção contra extrato quebrado:** recusa e alerta se o arquivo tiver menos de 80% das linhas
  da importação anterior **(decisão do design)**.
- Lê em fluxo e troca o conteúdo de `osm_markets` numa transação só, sem deixar a tabela vazia para
  quem lê no meio. Grava uma linha em `osm_market_imports`.
- Alerta: `MARKETS_ALERT type=IMPORT_FAILED` (WARN) a cada falha, e `type=DATASET_STALE` uma vez por
  dia se a última importação tiver mais de 15 dias. Terraform `infra/live/markets_alerts.tf` com
  filtro de métrica e alarme por ambiente para o tópico SNS de alertas, como o dos indicadores.
  Aplicar é com o mantenedor.

**Onde a família está**

Na primeira vez, a Feira pergunta como achar os mercados:

```
┌──────────────────────────────────┐
│ Onde procurar mercados?          │
│                                  │
│ ( ) Minha localização precisa    │
│ ( ) Minha localização aproximada │
│     (cerca de 1 km)              │
│ ( ) Digitar um endereço          │
│ ( ) Endereço da família          │
│                                  │
│ A localização fica no aparelho.  │
│ O servidor recebe só a região.   │
│            [ Continuar ]         │
└──────────────────────────────────┘
```

- **Precisa:** `getCurrentPosition`, só depois do toque. A posição fica na memória da tela, nunca é
  guardada nem enviada. O aparelho manda à API o centro arredondado a 2 casas (~1 km) e mede as
  distâncias com a posição precisa.
- **Aproximada:** onde houver o elemento `<geolocation accuracymode="approximate">` (Chrome 144+),
  usa ele; senão, `getCurrentPosition` e arredonda na hora a 2 casas. O ponto arredondado fica
  guardado no aparelho e é reaproveitado, para pedidos repetidos não revelarem a posição real. As
  distâncias aparecem como "cerca de 1,2 km".
- **Digitar um endereço:** a API geocodifica (abaixo) e devolve até 5 lugares com o ponto a 3 casas
  e um rótulo curto (bairro e cidade). O ponto escolhido fica no aparelho; o texto digitado não fica
  em lugar nenhum.
- **Endereço da família:** o ponto de `market_home`, se o Dono ou um Adulto tiver definido.
- A escolha fica no aparelho, por pessoa, e muda num toque no topo da lista.

**Endereço da família**

- Em "Família", o Dono ou um Adulto digita o endereço, escolhe um dos lugares devolvidos e dá um
  rótulo opcional ("Casa"). O app guarda **só o ponto a 3 casas e o rótulo**, nunca o texto do
  endereço **(decisão do design)**. Remover apaga a linha.
- O spec do Plano 6 promete não guardar o endereço de quem paga; isto é outra coisa (um ponto
  aproximado, escolhido pela família, para a Feira) e entra na política de privacidade.

**Geocodificação (Nominatim)**

- `POST /v1/workspaces/{ws}/markets/geocode` com `{query}` (até 120 caracteres). Só a pedido da
  pessoa, nunca enquanto digita (a política do Nominatim proíbe autocompletar).
- Fila única em cada processo da API, com no máximo 1 requisição a cada 2 s
  (`liberdade.markets.geocoding.min-interval`, padrão `PT2S`). Staging e produção dividem a mesma
  máquina e o mesmo IP, então os dois juntos ficam em 1 por segundo, o limite do Nominatim
  **(decisão do design)**. Até 20 consultas por família por dia; cache em memória por 24 h pela consulta normalizada; `User-Agent` do bot; `countrycodes=br`.
  O texto não vai para log nem para o banco.
- Desligável sem atualizar o app: `liberdade.markets.geocoding.enabled` (variável de ambiente). Desligada,
  a opção "Digitar um endereço" some e o endereço da família não pode ser trocado.
- A tela mostra "Busca de endereço © OpenStreetMap".

**Busca por área**

- `POST /v1/workspaces/{ws}/markets/search` com `{lat, lon, radiusKm}`. **POST, para a região não
  aparecer na URL nem no log de acesso** **(decisão do design)**.
- A API recusa (422) latitude ou longitude com mais de 2 casas: um aparelho com defeito não consegue
  mandar a posição precisa. `radiusKm` de 1 a 10, padrão 3.
- Devolve duas listas separadas, sem misturar: `osm` (de `osm_markets`, na caixa do raio mais 1 km
  de folga, até 500) com as informações da família por cima quando houver linha em `markets`, e
  `family` (mercados `FAMILY` com posição na mesma caixa). Também devolve a data do dado do OSM.
- O aparelho junta as duas na tela, mede, ordena por distância e mostra até 50 dentro do raio.

**Lista**

```
┌──────────────────────────────────┐
│ Feira          📍 Aproximada ▾   │
│                                  │
│ Mercado Bom Preço        450 m   │
│ Supermercado · R. das Flores     │
│                                  │
│ Hortifruti da Praça      700 m   │
│ Hortifruti · adicionado por você │
│                                  │
│ [ + Adicionar mercado ]          │
│                                  │
│ Dados: © colaboradores do        │
│ OpenStreetMap (ODbL) · 18/11     │
└──────────────────────────────────┘
```

- A atribuição fica visível sem toque, embaixo da lista e no cartão, com link para
  `https://www.openstreetmap.org/copyright`, e também em "Sobre e licenças".
- Mercado sem nome no OSM aparece como "Supermercado sem nome" (pelo tipo).

**Cartão do mercado**

- Nome, tipo, endereço (do OSM), horário como está no OSM (sem "aberto agora"), distância.
- **Site, Instagram e encarte, cada um com fonte e data:** o do OSM mostra "OpenStreetMap ·
  18/11/2026" (data do dado); o da família mostra "adicionado pela família · 20/11/2026". O da
  família vence. Usa o `SourceStamp` de `shared/ui`.
- **Encarte:** um link `https` que a família cola, com a fonte ("site do mercado") e, se quiser, a
  validade. Depois da validade, aparece "encarte vencido". O app nunca abre o encarte dentro dele.
- Links externos abrem fora do app, com `rel="noopener noreferrer"`.
- Dono e Adulto editam site, Instagram e encarte; aceitam só `https://`; o Instagram aceita o
  perfil (`@mercado` ou a URL) ou a URL de um post.

**Mapa só ao tocar**

- Botão "Ver no mapa". O toque abre o aviso, uma vez por aparelho se a pessoa marcar "Não perguntar
  de novo":
  > O mapa é do Google. Ao abrir, o Google recebe o seu IP e pode gravar cookies. [Abrir mapa]
  > [Agora não]
- Só depois cria o `iframe` de `https://www.google.com/maps/embed/v1/place?key=…&q=<lat>,<lon do
  mercado>`, com `referrerpolicy="strict-origin-when-cross-origin"`, `loading="lazy"`, título
  acessível e pelo menos 200 px. **O mapa mostra o mercado, nunca a posição da pessoa**
  **(decisão do design)**.
- A chave vem de `VITE_GOOGLE_MAPS_EMBED_KEY`, por ambiente. Sem chave, o botão não aparece.
- A página tem `Referrer-Policy: no-referrer`; o atributo `referrerpolicy` do `iframe` vale para
  aquela navegação e manda só a origem, que a restrição da chave precisa. O plano confere isso no
  staging.
- **Abrir no Maps:** `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lon>`. **Como chegar:**
  `https://www.google.com/maps/dir/?api=1&destination=<lat>%2C<lon>`. **Waze:**
  `https://waze.com/ul?ll=<lat>,<lon>&navigate=yes`. São links: nada carrega antes do toque.

**Instagram só ao tocar**

- Padrão: o link "Abrir no Instagram" (`https://www.instagram.com/<perfil>/`).
- Botão "Ver aqui". O toque mostra o aviso da Meta (como o do Google). Depois:
  1. A API chama `GET https://graph.facebook.com/v25.0/instagram_oembed?url=…&maxwidth=320&omitscript=true`,
     sem token (aberto desde 15/06/2026, pelo blog de desenvolvedores da Meta: "tokenless access
     to Meta oEmbed APIs"), e guarda a resposta por 7 dias numa tabela de cache ou em
     memória (fica para o construtor). Rota: `POST /v1/workspaces/{ws}/markets/{id}/instagram-embed`.
  2. Conta privada, desativada ou URL que o oEmbed recusa: a tela diz "Este perfil não pode ser
     mostrado aqui" e fica com o link.
  3. A web cria `<iframe sandbox="allow-scripts allow-popups" src="/embed/instagram">` e manda o
     `html` do oEmbed por `postMessage`. A página isolada confere a origem da mensagem, aceita só um
     `blockquote` com `class="instagram-media"` e `data-instgrm-permalink` em `https://www.instagram.com/`,
     insere e carrega `https://www.instagram.com/embed.js` **(decisão do design)**.
- Sem `allow-same-origin`, a página isolada não tem acesso a cookies, armazenamento nem à API do app.
- O suporte do oEmbed a perfil (não só post) não está confirmado de ponta a ponta. Se não funcionar,
  o "Ver aqui" aparece só quando a família colou a URL de um post (o encarte no Instagram, por
  exemplo).
- **Conferência antes de construir:** a primeira tarefa do Instagram no plano 10.1 faz uma chamada
  real sem token, com um post público. Se a Meta pedir token, o "Ver aqui" sai da 10.1 e fica só o
  link. Um app da Meta com segredo seria um plano à parte, com aprovação do mantenedor **(decisão do
  design)**.

**Mercado que falta**

- "Adicionar mercado": nome, tipo e posição. A posição vem de um toque em "Estou no mercado agora"
  (localização precisa, só naquele momento) ou de um endereço geocodificado. Fica em `markets` com
  `origin = 'FAMILY'`, visível só para a família.
- **Nunca é comparado, juntado nem deduplicado com o OSM** (base coletiva, não derivada, pelas
  diretrizes da ODbL) **(decisão do design)**. A tela convida, com um link, a cadastrar o mercado
  no próprio OpenStreetMap.

**CSP e permissões (web, `_headers`)**

- App: `frame-src 'self' https://www.google.com` entra na CSP. O resto não muda.
- `Permissions-Policy`: `geolocation=(self)` (10.1) e `camera=(self)` (10.2).
- Página `/embed/instagram`: o `_headers` tira a CSP geral com `! Content-Security-Policy` e põe a
  dela: `default-src 'none'; script-src 'self' https://www.instagram.com; frame-src
  https://www.instagram.com; img-src https://*.cdninstagram.com data:; style-src 'unsafe-inline';
  frame-ancestors 'self'; base-uri 'none'`. Os valores exatos se confirmam no staging.
- O service worker não intercepta `/embed/` (`navigateFallbackDenylist`).
- A CSP da API não muda.

### 6.5 Entrega 10.2: preços pela NFC-e

**Ler o QR**

- "Registrar nota" abre a câmera (`getUserMedia`, câmera de trás) num chunk sob demanda. Usa o
  `BarcodeDetector` do navegador quando existe (Chrome no Android) e, senão, a biblioteca `jsQR`
  (Apache-2.0, só JavaScript, sem WebAssembly e sem mudar `script-src`) num Web Worker
  **(decisão do design; dependência nova, aprovada no plano 10.2)**.
- Alternativas sem câmera (WCAG): colar o link do QR ou digitar os 44 dígitos da chave.
- A imagem nunca sai do aparelho; a câmera para ao sair da tela.

**Limpar no aparelho**

- O leitor aceita QR v1 (`chNFe=…`), v2 e v3 (NT 2025.001) e valida a chave: 44 dígitos, modelo
  `65`, dígito verificador em módulo 11, ambiente de produção (`tpAmb = 1`).
- **v3:** manda só `chave|3|1`, a forma online, que serve também para a nota emitida offline depois
  de transmitida. A forma offline traz o CPF ou CNPJ de quem comprou e **é descartada no aparelho**.
- **v2:** manda o `p` inteiro (online ou offline), que não tem documento de quem comprou.
- **v1:** traz o documento de quem comprou; o aparelho manda só a chave, e a nota vai para o modo
  manual.
- Da chave saem estado (cUF), mês (AAMM) e CNPJ de quem vendeu.

**Na API (10.2: só o modo manual)**

- `POST /v1/workspaces/{ws}/markets/receipts/lookup` com `{chave, version, p?}`:
  1. Valida a chave de novo e calcula o hash. Nota já registrada: 409 `receipt.duplicate`.
  2. Acha o mercado da família pelo CNPJ (`markets.cnpj`). Se não houver, a resposta pede para a
     pessoa escolher um mercado da lista perto dela (o CNPJ fica ligado a ele) ou criar um mercado
     da família, digitando o nome.
  3. Pergunta ao "leitor do estado" (uma interface, `NfceStateReader`, por UF). **Na 10.2 não
     existe nenhum leitor**, então a resposta é sempre `MANUAL` (decisão do mantenedor, item 11).
- A 10.2 não traz o cliente HTTP da SEFAZ, a lista de estados, os modelos de URL nem o jsoup. Tudo
  isso entra com o primeiro estado.

**Leitor por estado (planos 10.2.x, um por estado ou portal, depois da conferência)**

Regras que cada plano 10.2.x segue:

  1. **Busca automática** só se o estado estiver em `liberdade.markets.nfce.fetch-states` (vazia
     por padrão). A API monta a URL a partir do modelo do estado na configuração
     (`liberdade.markets.nfce.states.<UF>.url`) e do `p`. **Nunca abre uma URL vinda do aparelho**
     (sem SSRF) **(decisão do design)**.
  2. Abre a página uma vez (tempo limite de 10 s, sem nova tentativa), com `User-Agent` do bot, no
     máximo 1 requisição a cada 4 s por estado em cada processo (staging e produção dividem o IP)
     e 30 notas por família por dia. Lê o HTML e devolve: nome do emitente, data de emissão, e para
     cada item a descrição, o código, o GTIN se a página mostrar, a quantidade, a unidade e o preço
     unitário.
  3. **O leitor ignora a parte do consumidor** (CPF, nome) e nunca guarda nem registra a página em
     log. Captcha, HTML que o leitor não entende, erro ou tempo esgotado: cai no modo manual, com
     `MARKETS_ALERT type=NFCE_PARSE_FAILED uf=<UF>` (sem a chave).
  4. O teste usa uma página de exemplo feita a partir da página real que o mantenedor salvou, com a
     parte do consumidor trocada por dados inventados. A página real nunca entra no repositório.
  5. No staging, o estado fica na lista só durante a conferência.
- Resposta do `lookup`: `{mode: FETCHED | MANUAL, market, issuedOn, items[]}`, com o produto
  sugerido em cada item (seção 6.6). Nada é gravado ainda.

**Prévia e gravação**

- **Modo automático (10.2.x):** a prévia lista os itens com o produto sugerido; a pessoa desmarca
  o que não quer e troca o produto se precisar.
- **Modo manual:** a tela diz que a leitura automática ainda não existe para o estado e oferece
  "Abrir a nota no site da SEFAZ". O link é o do próprio QR, aberto no navegador da pessoa (o uso
  que o QR prevê) e nunca enviado à API. A pessoa escolhe produtos da família (e, depois da 10.3,
  itens da lista de compras) ou digita um nome novo, e digita o preço. A data vem do QR quando
  houver, senão é hoje; dá para editar.
- `POST /v1/workspaces/{ws}/markets/receipts` com o hash, o mercado, a data, o modo e os itens
  (produto existente ou novo, unidade, quantidade, preço unitário). Grava a nota e os preços numa
  transação. Responde quantos preços entraram.
- A tela termina com "Preços registrados. A nota não vira um gasto; para lançar, use Lançar."

**Estados**

- Candidatos (robots.txt de 24/11/2026): RS, MG, BA e PR. Bloqueados: SC (`Disallow: /`), SP
  (firewall de bots), RJ (bloqueio de IP e aviso de sigilo fiscal).
- **A 10.2 escreve o runbook `docs/runbooks/nfce-states.md`**: a lista de conferência por estado
  (robots.txt, termos, captcha, uma nota real) e como salvar a página sem a parte do consumidor.
  Quando um estado passa, o mantenedor registra data e resultado ali, e um plano 10.2.x traz o
  leitor. Vários estados usam o portal do SVRS, e um leitor só pode servir a todos eles **(decisão
  do design)**.
- Na 10.2, a página `/robo` passa a explicar as coletas novas: extrato da Geofabrik (pelo GitHub
  Actions), Nominatim e oEmbed do Instagram. A consulta da NFC-e entra no `/robo` com o primeiro
  estado.

### 6.6 Produtos (junção automática)

A 10.2 cria `products` e `product_aliases` e grava o alias do nome digitado no modo manual. A
junção automática abaixo vale para os itens lidos da página (10.2.x), mas a regra mora na 10.2,
testada sem rede, para os planos 10.2.x só a chamarem.

- Cada item lido aponta para um produto da família. Na ordem:
  1. **GTIN** válido igual ao de um produto: o mesmo produto.
  2. **Mesma descrição normalizada no mesmo mercado** (`product_aliases`): o mesmo produto.
  3. Senão, um produto novo com o nome da descrição (editável) e um alias para aquele mercado.
- Normalização: minúsculas, sem acento, espaços únicos, sem pontuação solta (a mesma regra em
  toda parte, no servidor).
- No modo manual, a pessoa escolhe o produto, então não há junção. Um nome novo cria o produto e um
  alias para aquele mercado.

### 6.7 Entrega 10.3: lista de compras e comparação

**Produtos**

- Tela "Produtos": nome, unidade, último preço em cada mercado com data. Dono e Adulto renomeiam,
  trocam a unidade e juntam dois produtos (os preços e aliases do segundo passam para o primeiro;
  o segundo some).
- **Sugestão de juntar:** pares de produtos com nomes parecidos (semelhança de palavras acima de um
  limite, calculada na API sobre os produtos da família) e sem GTINs diferentes aparecem como "São
  o mesmo produto?" com "Juntar" e "Não são". "Não são" grava em `product_join_dismissals`
  **(decisão do design)**.

**Lista**

- Uma lista por família, de produtos, com quantidade e marcar como pego. Adicionar busca nos
  produtos da família ou cria um pelo nome (sem preço ainda). Cada item mostra o último preço em
  cada mercado, com data.
- Mudanças avisam os outros aparelhos ao vivo (`{"entity":"shopping-list"}`, pelo módulo `live`).
- "Limpar os pegos" tira os marcados.

**Comparação**

```
┌──────────────────────────────────┐
│ Comparar      [Bom Preço] [Praça]│
│                                  │
│ Leite integral 1 L               │
│   R$ 4,99 (12/11)  R$ 5,29 (20/11)│
│ Arroz 5 kg                       │
│   R$ 27,90 (12/11)  —            │
│                                  │
│ Nos 7 itens que os dois têm:     │
│ Bom Preço R$ 84,20               │
│ Praça     R$ 79,90               │
│ 1 item sem preço em Praça        │
└──────────────────────────────────┘
```

- A pessoa escolhe 2 ou 3 mercados; o app sugere os dois com mais itens da lista com preço.
- Por item: o último preço em cada mercado, com data; preço com mais de 60 dias leva "antigo".
- **O total só soma os itens com preço em todos os mercados escolhidos**, multiplicados pela
  quantidade, e diz quantos ficaram de fora **(decisão do design)**. Item com unidade diferente
  entre mercados (kg num, unidade no outro) fica fora do total, com aviso.
- Texto neutro: nenhum selo de "mais barato", nenhuma sugestão de comprar mais.
- Cálculo na API (`GET /v1/workspaces/{ws}/markets/compare?markets=…`), com aritmética decimal.

### 6.8 Privacidade, auditoria e exportação

- **Localização:** o servidor recebe só a região a 2 casas (busca) ou o ponto a 3 casas escolhido
  para o endereço da família. A posição precisa nunca sai do aparelho. Logs, auditoria e métricas
  nunca levam coordenadas, textos de endereço, chave da nota, descrições ou preços.
- **No aparelho:** a escolha de localização, o ponto aproximado ou digitado e o "não perguntar de
  novo" dos avisos. Saem ao sair da conta.
- **Exportação:** a versão seguinte à última no `main` quando a 10.1 for executada (a 8.2 leva a
  v5; 8.3 e 9 podem levar outras). Entram: `feira-mercados.csv` (só os da família, com o que ela
  editou), `feira-endereco.csv` (ponto e rótulo), `feira-produtos.csv`, `feira-precos.csv`,
  `feira-notas.csv` (data, mercado, modo, itens; sem a chave) e `feira-lista.csv`. Dados do OSM não
  entram.
- **Exclusão:** tudo da Feira cai em cascata com a família; autoria vira nula quando a pessoa sai.
- **Auditoria:** `MARKET_HOME_SET`, `MARKET_HOME_CLEARED`, `MARKET_ADDED`, `MARKET_EDITED`,
  `RECEIPT_ADDED` (modo e número de itens), `PRODUCTS_MERGED`. Sem coordenadas nem valores.
- **Política de privacidade:** nova versão (a seguinte à última publicada; a 8.2 leva a
  `privacy-2026-12.4`), dizendo em linguagem simples: o que a localização faz e onde fica; que o
  endereço da família vira só um ponto aproximado; que mapa e Instagram são do Google e da Meta,
  só carregam ao tocar e podem gravar cookies; que a nota fiscal guarda só preços, mercado e data,
  nunca o CPF. O primeiro plano 10.2.x atualiza a política para dizer que o servidor consulta a
  SEFAZ, uma vez por nota, só nos estados permitidos. Entra no mapa de dados
  do [modelo de dados](../../data-model.md).
- **Base legal (LGPD):** execução do contrato para a Feira que a família ligou, com o consentimento
  do navegador para a localização e o toque explícito para os embeds.

## 7. Testes

- **10.1:** workflow com um extrato pequeno de exemplo (sem dado real) gera o CSV e o manifesto;
  importação (SHA errado, arquivo com 50% das linhas, mesma data, troca sem tabela vazia);
  `search` recusa 3 casas, devolve as duas listas sem misturar; geocodificação com o intervalo
  mínimo, o limite por família e o interruptor; `market_home` só com 3 casas; papéis (Leitor
  não edita, Jovem não edita mercado); E2E e unidade provando que **nenhuma requisição a
  `google.com`, `instagram.com` ou `facebook.com` sai antes do toque** (Playwright olhando a rede);
  atribuição ODbL visível sem toque; `_headers` com `frame-src`, `Permissions-Policy` e o bloco da
  página isolada; página isolada recusa mensagem de outra origem e HTML fora do formato.
- **10.2:** leitor de QR v1, v2 e v3 (online e offline), chave com dígito errado, modelo 55,
  homologação; a forma offline v3 nunca sai do aparelho (teste do corpo enviado); `lookup` sempre
  `MANUAL` sem leitor; CNPJ desconhecido pede o mercado; nota repetida 409; junção por GTIN e por
  alias (regra pura, sem rede); Jovem não registra nota; logs sem chave, CPF, descrições e preços;
  E2E do modo manual. Cada plano 10.2.x testa o seu leitor com a página de exemplo (com CPF
  inventado, que não aparece em lugar nenhum), estado fora da lista, limites e URL que nunca vem do
  aparelho.
- **10.3:** junção e sugestão (com "Não são"), lista ao vivo em dois aparelhos, comparação com o
  total só dos itens comuns, unidade diferente fora do total, preço antigo; texto sem "mais barato".
- Exportação, exclusão em cascata e `RoleMatrixIT` com as rotas novas em cada entrega.
- `check:chunks` passando em cada entrega.

## 8. Critério de saída

- **10.1:** no staging, o workflow publica o arquivo, a API importa, a lista mostra mercados perto
  de um ponto de teste com a atribuição; o mapa e o Instagram carregam só depois do toque (E2E
  olhando a rede) e o mapa abre com a chave restrita.
- **10.2:** o mantenedor registra no staging uma nota real pelo modo manual e vê os preços no
  cartão do mercado. Cada plano 10.2.x sai quando uma nota real daquele estado entra pelo modo
  automático no staging.
- **10.3:** a família monta uma lista real e compara em 2 mercados.
- **Plano 10 sai quando** a família compara uma lista real em 2 mercados e o mapa não carrega nada
  sem um toque.

## 9. Custos

- **Google Maps Embed:** sem custo, mas exige projeto no Google Cloud com faturamento ativo. Chave
  restrita ao Maps Embed API e aos domínios do app, com alerta de orçamento de R$ 1 (passo do
  mantenedor).
- **Instagram oEmbed, Nominatim, Geofabrik:** sem custo, dentro das políticas de uso.
- **GitHub Actions:** repositório público, sem custo; ~5 min por semana.
- **CloudWatch:** dois alarmes a mais (staging e produção) passam da cota grátis de 10; cerca de
  US$ 0,10 por mês por alarme acima da cota (≈ R$ 0,55).
- **Banco:** dezenas de milhares de mercados (poucas dezenas de MB) e os preços da família.

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Extrato quebrado apaga os mercados | Recusa abaixo de 80% das linhas; SHA-256; alerta |
| Workflow para e o dado envelhece | E-mail do GitHub na falha; `DATASET_STALE` depois de 15 dias; o `SourceStamp` mostra a data |
| Estado muda o HTML ou põe captcha | Leitor por estado, testado; falha cai no modo manual com alerta |
| Consulta à SEFAZ vira raspagem | Uma requisição por nota pedida por uma pessoa, lista permitida vazia por padrão, limites, bot identificado, runbook |
| CPF vaza da nota | Forma offline descartada no aparelho; leitor ignora o consumidor; chave só em hash; teste com página que tem CPF |
| Posição precisa vaza | Só 2 casas na busca, 422 com mais; POST em vez de URL; logs sem coordenadas |
| Pedidos aproximados repetidos revelam a posição | Ponto aproximado guardado e reaproveitado |
| Nominatim bloqueia o app | 1 a cada 2 s por processo (staging e produção juntos em 1/s), cache, limite por família, sem autocompletar, interruptor |
| oEmbed passa a pedir token | Conferido com chamada real na 10.1; se pedir, fica só o link |
| `no-referrer` da página quebra a chave do Google | `referrerpolicy` no `iframe`; conferido no staging |
| oEmbed não aceita perfil | Fica o link; "Ver aqui" só para URL de post |
| Página isolada vira brecha | `sandbox` sem `allow-same-origin`, conferência de origem e formato, CSP própria |
| Juntar produtos errado | Só GTIN e alias exato juntam sozinhos; o resto é sugestão; juntar é auditado |
| Comparação injusta | Total só dos itens comuns, unidade conferida, data em cada preço |
| Pacote inicial passa de 200 KB | Tudo sob demanda; leitor de QR num chunk próprio; `check:chunks` no CI |

## 11. Pré-requisitos de execução

- **Plano 8.2 no `main`:** a guarda `@RequiresPlan(value = FAMILY, openWhileBillingOff = true)`.
- **Versões da exportação e da política:** as seguintes às últimas no `main` (8.2, 8.3 e 9 podem
  avançar as duas). Cada plano confere antes de começar.
- **Ordem de execução:** 8.2 → 8.3 → 9 → 10. Os planos 10.2 e 10.3 são conferidos de novo contra o
  código antes de executar.

## 12. Passos do mantenedor (fora do código)

- **10.1:** criar a chave do Maps Embed (projeto com faturamento, restrita à API e aos domínios de
  staging e produção, alerta de orçamento) e pôr `VITE_GOOGLE_MAPS_EMBED_KEY` como variável de cada
  Environment; aplicar `infra/live/markets_alerts.tf`; rodar o workflow uma vez à mão.
- **Depois da 10.2:** conferir cada estado candidato pelo runbook e, se passar, salvar uma página
  de nota sem a parte do consumidor (fora do repositório) e pedir o plano 10.2.x daquele estado.
  Depois do merge, pôr o estado em `LIBERDADE_MARKETS_NFCE_FETCH_STATES` do ambiente.
- **10.2:** aprovar a dependência nova `jsQR` (leitor de QR).
- Publicar a nova política de privacidade junto com a 10.1.

## 13. O que fica para o construtor

- Nomes de classes, rotas exatas além das listadas e a forma do JSON.
- O formato exato do CSV publicado e o script do workflow, desde que sem dependências novas.
- Cache do oEmbed em tabela ou em memória.
- O limite de semelhança para sugerir junção e a lista de unidades aceitas.
- Os textos em PT-BR, curtos, dentro das regras da seção 2.
- O layout da lista, do cartão, da prévia e da comparação, dentro do design system.
