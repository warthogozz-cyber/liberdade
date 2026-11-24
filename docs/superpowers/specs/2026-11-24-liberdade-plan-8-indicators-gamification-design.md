# Plano 8: v0.3 Indicadores e gamificação (design)

> **Status:** Em revisão · **Data:** 2026-11-24

**Base:** [PRD](../../prd.md) (US-30 a US-35, §7.6, §10), [roadmap](../../roadmap.md) (v0.3),
[modelo de dados](../../data-model.md) (§4), [design system](../../design-system.md)
(`SourceStamp`, movimento e som), [spec do Plano 7](2026-11-23-liberdade-plan-7-planning-design.md),
[ADR-0016](../../adr/0016-live-sync-sse-in-process.md).

## 1. Intenção e porquê

A v0.2 deu à família um plano: orçamento, metas, reserva e projeção. A v0.3 resolve três coisas que
ainda faltam para o app virar hábito:

- **Contexto do dinheiro lá fora.** Selic, CDI, IPCA e dólar, com fonte e data, e uma calculadora
  que mostra quanto um rendimento rende de verdade depois da inflação. É conteúdo educativo: o app
  explica e compara, nunca recomenda produto (CVM).
- **Menos digitação.** Importar o extrato do banco (OFX ou CSV), revisado antes de gravar, é a
  alternativa sem custo ao Open Finance. Sem ela, quem tem muitos gastos no cartão desiste de lançar.
- **Hábito sem pressão.** Uma sequência semanal gentil e conquistas que celebram organização e
  guardar dinheiro, nunca gastar. Dicas curtas e tours ajudam quem chega a um módulo novo.

## 2. Objetivos, não-objetivos e antiobjetivos

**Objetivos**

- US-30: cinco indicadores do Banco Central coletados todo dia, com valor, variação, fonte e data,
  e o aviso "desatualizado" quando a coleta falha.
- US-31: calculadora de rendimento real com a fórmula do PRD §7.6.
- US-35: importar OFX e CSV numa conta escolhida, com revisão, categoria sugerida, aviso de
  possível repetido e sem duplicar ao importar o mesmo arquivo duas vezes.
- US-32 e US-33: sequência semanal de cada pessoa e conquistas guardadas por pessoa.
- US-34: dica curta na primeira visita a uma tela e tour de até 4 passos ao abrir um módulo.

**Não-objetivos**

- Planos mensal e anual com assinatura. Ficam para quando a cobrança ligar (decisão do mantenedor);
  a pré-venda da Família Fundadora continua como está.
- Open Finance, leitura de PDF, foto de extrato e importação de fatura de cartão como tipo próprio.
- Detectar transferências entre contas na importação.
- Indicadores além dos cinco do PRD, gráficos históricos longos e alertas de mercado.
- Conquista "Primeiro projeto de renda extra" (entra com a v0.4, Plano 9).
- Notificações push ou e-mail de sequência, conquista ou indicador.

**Antiobjetivos (falharia mesmo funcionando)**

- Qualquer frase que soe como recomendação de investimento, ou um produto sugerido pela calculadora.
- Sequência que culpa: "você perdeu a sequência", contagem regressiva, alerta de "falta pouco".
- Conquista por gastar, por pegar crédito, por arriscar ou por volume de lançamentos.
- O arquivo do extrato sair do aparelho ou ficar guardado em algum lugar.
- Indicador velho mostrado como se fosse de hoje (sem data, sem "desatualizado").
- Coleta que falha em silêncio.
- O pacote inicial passar de 200 KB.

## 3. Restrições

- **Pacote inicial em 198,5 de 200 KB.** Indicadores, calculadora, importação, conquistas, tours e
  dicas carregam sob demanda (PRD: "cobrança, som, importação e Feira carregam sob demanda").
  `check:chunks` continua no CI.
- **Fonte oficial e uso educado.** Os dados vêm das APIs públicas de dados abertos do Banco Central
  (SGS e PTAX/Olinda), não de raspagem. A API se identifica no `User-Agent`, faz uma requisição por
  série por rodada, espera entre elas e guarda tudo no banco (cache). O app nunca chama o Banco
  Central do navegador.
- **SGS limita consultas por período a 10 anos** e `ultimos/N` a 20 pontos. Algumas séries diárias
  passaram a exigir filtro de datas; a coleta sempre manda `dataInicial` e `dataFinal`.
- **CVM.** O aviso "Conteúdo educativo. Não é recomendação de investimento." aparece em toda tela de
  indicadores e na calculadora. Antes do go-live da v0.3, o mantenedor confere a norma vigente
  (hoje, Resoluções CVM 19 e 20, de 2021, sobre consultoria e análise).
- **LGPD.** O extrato é lido no aparelho e o arquivo não é enviado nem guardado. Só os lançamentos
  confirmados vão para a API. Logs nunca levam valores nem descrições.
- **Gamificação ética** (PRD §10): sem dark patterns, sem pressão para gastar ou arriscar,
  `prefers-reduced-motion` respeitado e som só se a pessoa ligou.
- **Visibilidade do Jovem:** toda rota nova passa por `AccountScope` ou não chega ao Jovem. O Jovem
  não vê orçamento, reserva nem projeção (Plano 7).
- **Módulos:** `indicators` e `gamification` já existem em `ModuleKey` e começam desligados.
- **WCAG 2.2 AA**, PT-BR na interface, sem notificação entre 21h e 8h (não há notificação nova).

## 4. Abordagens consideradas

### 4.1 Onde ler o extrato

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **No aparelho** | O PWA lê o OFX ou CSV, monta a prévia e manda só as linhas confirmadas | O arquivo nunca sai do aparelho (LGPD, minimização). Parser em TypeScript, num chunk sob demanda. Telefones antigos leem arquivos grandes mais devagar | **Escolhida pelo mantenedor** |
| Na API | O arquivo sobe, a API lê e devolve a prévia | Parser em Java, mais fácil de testar com bibliotecas. O arquivo trafega e precisa ser descartado com cuidado; fere "o arquivo não é guardado" na prática | Descartada |

### 4.2 Onde coletar os indicadores

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **Job no próprio processo** | Um `@Scheduled` no módulo `indicators` da API busca e grava | Segue o monólito modular (ADR-0003), sem infraestrutura nova. Uma falha da API para a coleta, mas o alarme avisa | **Escolhida (decisão do design)** |
| Serviço separado (Lambda + EventBridge) | Função agendada grava no banco | Isola a coleta, mas traz deploy, segredo de banco e rede novos para cinco números por dia | Descartada |
| Navegador busca direto no Banco Central | Cada aparelho chama a API pública | Sem servidor, mas sem cache, sem alarme, com CORS incerto e com o IP da pessoa indo ao terceiro | Descartada |

### 4.3 Ordem das entregas

| Abordagem | Troca | Escolha |
|---|---|---|
| **Indicadores → Importação → Jogo** | A coleta precisa de 2 semanas no ar para o critério de saída, então vai primeiro. A importação traz mais valor de uso que o jogo. O jogo usa lançamentos importados na sequência | **Escolhida pelo mantenedor** |
| Jogo primeiro | Celebra cedo, mas atrasa o relógio das 2 semanas de coleta | Descartada |

## 5. Decisões de produto (do mantenedor)

1. **Um spec, três entregas**, cada uma com seu plano e seu PR: **8.1** Indicadores e calculadora
   (US-30, US-31); **8.2** Importação de extrato (US-35); **8.3** Sequência, conquistas, dicas e
   tours (US-32, US-33, US-34).
2. **Assinatura mensal e anual fica para quando a cobrança ligar.** Sai do escopo do Plano 8.
3. **Sequência de cada pessoa**, não da família.
4. **CSV: o app detecta as colunas e lembra** o mapeamento da conta para a próxima vez.
5. **Indicadores e gamificação começam desligados**; liga quem quiser, em Módulos.
6. **Importação só no plano Família.** Com a cobrança desligada, vale para todos, como as outras
   rotas do plano.
7. **O extrato é lido no aparelho** (seção 4.1).

## 6. Desenho

### 6.1 Módulos e onde cada coisa mora

| Onde | O quê |
|---|---|
| API `indicators` (novo) | Séries, pontos, job de coleta, cliente do Banco Central, alerta, `GET` dos cartões |
| API `ledger` | Rota de importação em lote e a chave de importação nos lançamentos |
| API `gamification` (novo) | Conquistas guardadas, sequência calculada, avaliação depois do commit, job do mês no orçamento |
| Web `features/indicators` | Tela de indicadores, cartões, `SourceStamp`, calculadora |
| Web `features/import` | Leitores OFX e CSV, prévia, mapeamento, envio |
| Web `features/gamification` | Cartão da sequência, tela de conquistas, celebração |
| Web `shared/tips` | Dicas e tours (núcleo, não depende de módulo), desligáveis em Ajustes |

`indicators` não depende de nenhum outro módulo de domínio. `gamification` escuta eventos de
`ledger` e `planning` e lê por interfaces públicas, como `planning` faz hoje com `ledger`
**(decisão do design)**.

### 6.2 Modelo de dados

Migrações a partir de **V18**, uma por entrega.

**V18 (8.1)**, como no modelo de dados §4, com três colunas de saúde na série:

```sql
CREATE TABLE indicator_series (
    code            text PRIMARY KEY,            -- SELIC_TARGET, CDI, IPCA_MONTH, IPCA_12M, USD_PTAX_SELL
    source          text NOT NULL CHECK (source IN ('BCB_SGS', 'BCB_PTAX')),
    source_ref      text NOT NULL,               -- 432, 4389, 433, 13522, CotacaoDolarPeriodo
    unit            text NOT NULL CHECK (unit IN ('PERCENT_YEAR', 'PERCENT_MONTH', 'PERCENT_12M', 'BRL')),
    name            text NOT NULL,
    last_success_at timestamptz NULL,            -- a última consulta que respondeu bem, com ponto novo ou não
    last_failure_at timestamptz NULL,
    failure_streak  int NOT NULL DEFAULT 0
);
CREATE TABLE indicator_points (
    series_code text NOT NULL REFERENCES indicator_series (code),
    ref_date    date NOT NULL,
    value       numeric(19,6) NOT NULL,
    fetched_at  timestamptz NOT NULL,
    source_url  text NOT NULL,
    PRIMARY KEY (series_code, ref_date)
);
```

As cinco séries entram pela própria migração. Não há `workspace_id`: indicador é dado público.

**V19 (8.2):** `transactions.import_key text NULL` (64 hex, SHA-256) e
`UNIQUE (workspace_id, account_id, import_key) WHERE import_key IS NOT NULL`, **incluindo os
excluídos**: um lançamento importado e depois excluído não volta numa nova importação
**(decisão do design)**.

O `TransactionPurgeJob` apaga de vez os excluídos depois de 30 dias, e com eles a chave. Para a
regra acima valer depois disso, a V19 cria uma tabela só de chaves **(decisão do design)**:

```sql
CREATE TABLE import_tombstones (
    workspace_id uuid NOT NULL,
    account_id   uuid NOT NULL,
    import_key   text NOT NULL,
    purged_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, account_id, import_key),
    FOREIGN KEY (workspace_id, account_id) REFERENCES accounts (workspace_id, id) ON DELETE CASCADE
);
```

- O job de limpeza copia a `import_key` para cá na mesma transação em que apaga o lançamento.
- A importação pula a linha cuja chave está no índice dos lançamentos **ou** aqui.
- Guarda só o hash (sem valor, data ou descrição) e some depois de **13 meses**, com a conta ou com
  a família. Um extrato com mais de 13 meses pode trazer de volta o que foi excluído; a prévia mostra
  a linha como "possível repetido" só se ainda houver um lançamento parecido.

**V20 (8.3):**

```sql
CREATE TABLE achievements (
    workspace_id    uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    membership_id   uuid NOT NULL,
    achievement_key text NOT NULL,
    earned_at       timestamptz NOT NULL DEFAULT now(),
    silent          boolean NOT NULL DEFAULT false,  -- ganha na ligada do módulo: entra na lista sem celebrar
    PRIMARY KEY (membership_id, achievement_key),
    FOREIGN KEY (workspace_id, membership_id) REFERENCES memberships (workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX transactions_by_creator ON transactions (workspace_id, created_by, created_at);
```

A sequência não tem tabela: é calculada na leitura (índice acima).

### 6.3 Entrega 8.1: indicadores

**Séries e o que o cartão mostra**

| Código | Fonte | Valor | Variação |
|---|---|---|---|
| `SELIC_TARGET` | SGS 432 | % a.a. | Última mudança do Copom em p.p., com a data ("+0,50 p.p. em 05/11") |
| `CDI` | SGS 4389 | % a.a. | p.p. contra o ponto anterior |
| `IPCA_MONTH` | SGS 433 | % no mês | p.p. contra o mês anterior |
| `IPCA_12M` | SGS 13522 | % em 12 meses | p.p. contra o mês anterior |
| `USD_PTAX_SELL` | PTAX `CotacaoDolarPeriodo`, `cotacaoVenda` do boletim de fechamento | R$ | % contra o dia útil anterior |

A variação é calculada na leitura a partir dos pontos guardados **(decisão do design)**. Valores com
2 casas na tela (dólar com 4), vírgula decimal.

**Coleta**

- `IndicatorJob`: `@Scheduled(cron = "0 30 8,19 * * *", zone = "America/Sao_Paulo")`. Às 19h30 pega
  a PTAX e o CDI do dia; às 8h30 repete o que faltou **(decisão do design)**.
- Para cada série, uma requisição com janela de datas: as diárias pedem os últimos 30 dias, as
  mensais os últimos 24 meses. Na primeira rodada (série sem ponto), a janela é de 13 meses, o
  bastante para a variação. Grava com `INSERT … ON CONFLICT (series_code, ref_date) DO UPDATE`
  (o Banco Central às vezes revisa um ponto).
- Cliente HTTP com tempo limite de 10 s, até 2 novas tentativas com espera (2 s, 8 s), 1 s entre
  séries e `User-Agent: LiberdadeBot/1.0 (+<URL pública da página do bot>)`. A página do bot
  explica o que é coletado e de onde **(decisão do design)**.
- Sucesso de uma série (respondeu e o JSON fez sentido, com ponto novo ou não) grava
  `last_success_at` e zera `failure_streak`. Falha grava `last_failure_at` e soma 1.
- Resposta estranha (valor fora de faixa: taxa fora de −5% a 100%, dólar fora de R$ 1 a R$ 50) não
  é gravada e conta como falha **(decisão do design)**.

**Alerta (sem falha silenciosa)**

- `IndicatorAlerts` escreve no nível WARN `INDICATOR_ALERT type=<TIPO> series=<CODE>`, sem valores,
  com `TIPO` em `FETCH_FAILED` (a cada falha de série depois das novas tentativas) e `STALE`
  (uma vez por dia enquanto a série estiver desatualizada).
- Terraform em `infra/live/indicator_alerts.tf`, igual ao da cobrança: um filtro de métrica por
  ambiente conta `"INDICATOR_ALERT"` e o alarme `liberdade-indicator-alert-{ambiente}` avisa o
  tópico SNS de alertas. Aplicar é com o mantenedor.
- O job também escreve `INDICATOR_RUN ok=<n> failed=<n>` no nível INFO a cada rodada. Um segundo
  alarme dispara se nenhuma linha `INDICATOR_RUN` aparecer em 26 h ("dados ausentes contam como
  violação"), para o caso de o job nem rodar **(decisão do design)**.

**Desatualizado**

- Uma série está desatualizada quando `last_success_at` tem mais de **3 dias úteis** (segunda a
  sexta, no fuso de São Paulo; feriados não contam como exceção, porque a consulta de um feriado
  responde bem e atualiza `last_success_at`). Sem nenhum sucesso, também está.
- O cartão mantém o último valor, mostra "atualizado em dd/mm/aaaa" (de `last_success_at`) e ganha
  o selo "desatualizado".

**API**

- `GET /v1/workspaces/{ws}/indicators` com `@RequiresModule(INDICATORS)`: para cada série, código,
  nome, unidade, valor, data de referência, variação, fonte (nome e URL), `updatedAt` e `stale`.
  Todo papel vê, inclusive o Jovem (é dado público).
- O cálculo de "desatualizado" fica na API, para todo aparelho concordar **(decisão do design)**.

**Tela**

- Item "Indicadores" no menu Mais quando o módulo está ligado (o menu de baixo não muda).
- Um cartão por série, cada um com `SourceStamp` ("Fonte: Banco Central · atualizado em 31/10/2026",
  e "desatualizado" depois de 3 dias úteis). `SourceStamp` vira componente de `shared/ui` e é
  obrigatório em todo dado externo (S3), com teste de UI.
- O aviso da CVM fica fixo no topo da tela de indicadores e da calculadora.

### 6.4 Entrega 8.1: calculadora de rendimento real

- Rota sob o módulo de indicadores. A pessoa digita a taxa nominal ao ano; o IPCA 12 meses vem
  preenchido com o último ponto (com `SourceStamp`) e pode ser trocado.
- `real = (1 + nominal) ÷ (1 + IPCA 12 meses) − 1`, mostrado com 2 casas. Exemplo do PRD: 10% e 4%
  dão **5,77%**. A tela mostra a conta, não só o resultado.
- Cálculo só no aparelho, com aritmética decimal (sem erro de ponto flutuante na segunda casa)
  **(decisão do design)**. Teste com o exemplo do PRD e com nominal menor que a inflação (real
  negativo, mostrado como "abaixo da inflação", sem alarme).
- Nunca cita produto, banco ou "melhor opção".

### 6.5 Entrega 8.2: importação de extrato

**Fluxo**

1. Em Lançamentos, "Importar extrato" (só aparece com o plano Família, ou com a cobrança
   desligada). A pessoa escolhe a conta e o arquivo.
2. O chunk de importação lê o arquivo no aparelho. OFX 1.x (SGML) e 2.x (XML); CSV com
   detecção (abaixo). Codificação UTF-8 ou Windows-1252, pelo cabeçalho do OFX ou por tentativa no
   CSV.
3. Prévia: data, descrição, valor, tipo (entrada ou saída pelo sinal), categoria sugerida e um
   selo "possível repetido" ou "confirmar o previsto". A pessoa marca e desmarca linhas, troca
   categoria e confirma.
4. O app manda as linhas marcadas; a tela diz quantas entraram, quantas confirmaram um previsto e
   quantas já existiam.

**Conta de cartão de crédito (decisão do mantenedor)**

- A importação aceita conta de cartão. Nela, **toda compra entra como gasto**, qualquer que seja o
  sinal no arquivo (cada banco exporta a fatura com uma convenção).
- O sinal das compras é o mais comum no arquivo **(decisão do design)**. Linhas com o sinal oposto
  (pagamento da fatura ou estorno) vêm **desmarcadas**, com o aviso "Pagamento da fatura? Ele já
  aparece como saída na conta de onde saiu o dinheiro." Se a pessoa marcar uma, ela entra como
  entrada na conta do cartão (um estorno devolve saldo).
- Nas outras contas, o tipo segue o sinal do arquivo.

**CSV: detecta e lembra**

- Detecta separador (`;`, `,`, tab), linha de cabeçalho, coluna de data (`dd/MM/aaaa`,
  `aaaa-MM-dd`), de descrição e de valor (uma coluna com sinal, ou débito e crédito separados) e
  decimal com vírgula ou ponto.
- Se a detecção não tiver certeza, a pessoa escolhe as colunas numa tela curta.
- O mapeamento fica guardado **no aparelho**, por conta e pela assinatura do cabeçalho; outro
  aparelho confirma uma vez **(decisão do design)**.

**Sem duplicar**

- Cada linha leva uma `import_key` calculada no aparelho: SHA-256 de
  `account_id | data | valor | descrição normalizada | n`, em que `n` é a ordem da linha entre as
  idênticas do mesmo arquivo (duas compras iguais no mesmo dia continuam duas). A mesma regra vale
  para OFX e CSV **(decisão do design)**: o `FITID` do OFX não é estável em todo banco (alguns o
  geram de novo a cada exportação), e com uma regra só, trocar de OFX para CSV no mesmo banco também
  não duplica.
- A API grava com `ON CONFLICT DO NOTHING` no índice único da V19 e pula as chaves de
  `import_tombstones`. Importar o mesmo arquivo duas vezes, ou dois arquivos com períodos que se
  sobrepõem, não duplica nada.
- "Possível repetido" é outra coisa: lançamento confirmado já existente, sem `import_key` (feito à
  mão), com mesma conta, data, valor e descrição normalizada. O app busca os lançamentos da conta no
  período do arquivo pela rota de sempre. Linhas assim vêm desmarcadas.

**Confirmar o previsto (decisão do design)**

- Uma linha que bate com um lançamento **previsto** (de uma recorrência, Plano 7) da mesma conta,
  mesmo tipo e mesmo valor, com data a até 5 dias, aparece como "confirmar o previsto", **marcada**.
  Cada previsto casa com uma linha só, a de data mais próxima.
- A linha vai com `confirmsPlannedId`. A API confirma aquele previsto em vez de criar outro: status
  confirmado, data e valor do extrato, `member_id` de quem importou e a `import_key` da linha. Se o
  previsto já não estiver previsto (alguém confirmou ou pulou), a linha é criada como nova.
- Sem isso, a conta de luz importada e a prevista contariam duas vezes no saldo e na projeção.

**Categoria sugerida**

- `POST /v1/workspaces/{ws}/imports/category-hints` recebe até 500 descrições normalizadas e
  devolve, para cada uma, a categoria do lançamento mais recente com a mesma descrição que a pessoa
  pode ver (respeita `AccountScope`). Sem histórico, fica sem categoria **(decisão do design)**.

**Gravar**

- `POST /v1/workspaces/{ws}/accounts/{id}/imports` com até 500 linhas, cada uma com id UUIDv7 do
  aparelho, tipo (`INCOME` ou `EXPENSE`), valor, data, descrição (cortada em 140), categoria,
  `import_key` e, se for o caso, `confirmsPlannedId`. Tudo numa transação; responde
  `{created, confirmed, skipped}`.
- Guardas: `@RequiresPlan(FAMILY)`; quem pode lançar na conta (Dono e Adulto; o Jovem só nas contas
  dele; o Leitor não importa). `member_id` e `created_by` são quem importou.
- Só com rede (o lote não entra na fila offline) **(decisão do design)**.
- Cada lote publica `TransactionChanged` para o aviso ao vivo e grava uma linha de auditoria
  `IMPORT` com a conta e as contagens, sem valores.
- Lançamentos importados são lançamentos comuns: entram no saldo, no orçamento e na projeção.

**Bancos:** a família usa cinco bancos (a lista fica com o mantenedor, fora do repositório). Os
testes usam um arquivo de exemplo escrito à mão para cada um, no formato daquele banco (OFX ou CSV,
separador, colunas, sinal do cartão), nomeado pelo jeito do formato e não pelo banco, sem dado real.
O extrato real de cada um é conferido pelo mantenedor no staging (critério de saída).

### 6.6 Entrega 8.3: sequência semanal

- **De cada pessoa.** Uma semana (segunda a domingo, no fuso da família, `workspaces.timezone`)
  conta quando a pessoa registrou pelo menos um lançamento confirmado e não excluído nela, pelo
  `created_at` e `created_by` **(decisão do design)**. Importar conta como registrar; importar 3
  meses de uma vez conta uma semana só.
- **Lançamentos de recorrência não contam** (`recurring_rule_id` preenchido), nem quando a pessoa
  confirma um previsto: o job os cria em nome de quem fez a regra, e confirmar não muda
  `created_at`. Sem isso, uma regra de aluguel manteria a sequência sozinha **(decisão do design)**.
- **Semana congelada:** uma semana sem registro é congelada sozinha se nenhuma outra semana do
  mesmo mês (o mês da segunda-feira) já foi congelada. Ela só mantém a sequência viva: não soma em
  "N semanas seguidas" nem conta para `STREAK_4_WEEKS` **(decisão do design)**. A semana corrente
  nunca quebra a sequência.
- Calculada na leitura: `GET /v1/workspaces/{ws}/gamification/streak` devolve semanas seguidas,
  semanas congeladas na sequência atual e a maior sequência.
- Texto: "N semanas seguidas" e, sem sequência, "Lance algo esta semana para começar uma
  sequência". Nunca "perdeu", nunca contagem regressiva.
- Cartão pequeno no painel quando o módulo está ligado, num chunk sob demanda.

### 6.7 Entrega 8.3: conquistas

| Chave | Nome | De quem | Quando |
|---|---|---|---|
| `FIRST_TRANSACTION` | Primeiro lançamento | Quem registrou | Primeiro lançamento confirmado, fora de recorrência (a mesma regra da sequência) |
| `STREAK_4_WEEKS` | 4 semanas seguidas | A pessoa | Sequência chega a 4 |
| `FIRST_GOAL` | Primeira meta | Quem criou | Primeira meta criada |
| `GOAL_ACHIEVED` | Meta alcançada | Todos que veem metas | Primeira meta alcançada da família |
| `RESERVE_1_MONTH`, `RESERVE_3_MONTHS`, `RESERVE_6_MONTHS` | Reserva de 1, 3 e 6 meses | Dono, Adulto e Leitor | A reserva cobre 1, 3 e 6 meses |
| `MONTH_IN_BUDGET` | Mês dentro do orçamento | Dono, Adulto e Leitor | Um mês fechado com todas as categorias com limite dentro do limite |

- Conquista da família vai para cada membro ativo que vê aquilo naquele momento; o Jovem não ganha
  as de reserva e orçamento, que ele não vê **(decisão do design)**.
- Nenhuma conquista premia gastar, dívida, crédito, risco ou número de lançamentos.
- **Avaliação:** `Achievements.evaluate(workspace, membership)` roda depois do commit dos eventos de
  `ledger` e `planning` e na leitura da lista, só com o módulo ligado. Insere com
  `ON CONFLICT DO NOTHING`. `MONTH_IN_BUDGET` sai de um job no dia 1 às 4h20
  (`America/Sao_Paulo`) que olha o mês anterior das famílias com o módulo ligado e algum limite.
- **A avaliação olha o estado atual de todo mundo**, inclusive de quem chegou depois. O que ela
  encontra fora de uma ação da pessoa é gravado com `silent = true` e entra na lista sem celebrar:
  ao ligar o módulo, quando um membro novo entra, na primeira leitura da lista e quando o papel muda
  (um Jovem que vira Adulto ganha as de reserva que já valiam) **(decisão do design)**. Só o que
  nasce de um evento depois de uma ação local celebra. `MONTH_IN_BUDGET` não é retroativo.
- **Celebração** curta, só depois de uma ação local (o padrão de `GoalCelebrationWatcher`): o app
  compara a lista antes e depois e celebra o que entrou e não é `silent`. Respeita
  `prefers-reduced-motion`; som só com o som ligado.
- **Ao vivo:** um aviso `{"entity":"achievement"}` vai só para quem ganhou; o app busca a lista de
  novo, sem celebrar (não foi ação local).
- Tela "Conquistas" no menu Mais: as ganhas com data e as que faltam com o que leva a elas, em
  texto neutro.

### 6.8 Entrega 8.3: dicas e tours

- **Núcleo**, não depende de módulo, ligado por padrão. Interruptor "Mostrar dicas" em Ajustes
  desliga dicas e tours. Estado guardado no aparelho, como o som **(decisão do design)**.
- **Dica:** um balão curto (até 140 caracteres) na primeira visita da pessoa, naquele aparelho, a:
  Painel, Lançamentos, Contas, Planejar, Indicadores, Calculadora, Importar e Conquistas. Some com
  "Entendi" e não volta.
- **Tour:** até 4 passos na primeira visita à tela principal de um módulo ligado (Planejar,
  Indicadores, Conquistas). "Pular" em todos os passos. Foco gerenciado, teclado e leitor de tela.
- Código em `shared/tips`, carregado sob demanda.

### 6.9 Privacidade, auditoria e exportação

- **Exportação v5:** conquistas da pessoa (chave e data) e `import_key` nos lançamentos.
  Indicadores não entram (não são dados da família).
- **Exclusão de conta:** conquistas somem com a participação (cascata). As chaves de
  `import_tombstones` somem com a conta bancária ou com a família.
- **Retenção:** `import_tombstones` guarda só hashes, por 13 meses, para um lançamento excluído não
  voltar numa nova importação. Entra na retenção do [modelo de dados](../../data-model.md) e numa
  nova versão do texto da política (`web/src/features/legal/texts/`).
- **Auditoria:** `IMPORT` (conta, contagens). Ligar e desligar módulos já é auditado.
- **Logs:** coleta sem valores; importação sem valores nem descrições.
- **Política de privacidade:** um parágrafo sobre a importação (lida no aparelho, arquivo não
  guardado, só o hash da linha fica para evitar repetidos) e sobre a coleta do Banco Central
  (nenhum dado da família sai).

## 7. Testes

- **8.1:** cliente do Banco Central contra respostas gravadas (sucesso, vazio, erro 5xx, JSON
  estranho, valor fora de faixa); `last_success_at` e `failure_streak`; desatualizado com 3 dias
  úteis atravessando fim de semana; variação de cada série; o alerta escrito no formato do filtro;
  `SourceStamp` em todo cartão (S3); aviso da CVM nas duas telas; calculadora com 10% e 4% → 5,77%.
- **8.2:** leitores com os cinco arquivos de exemplo (OFX SGML e XML, CSV com `;` e vírgula
  decimal, débito e crédito separados, Windows-1252, cartão com compra positiva e com compra
  negativa); `import_key` igual para a mesma linha em OFX e CSV; segunda importação cria 0; arquivos
  com períodos sobrepostos; linha excluída e já apagada pelo job não volta (tombstone); "possível
  repetido"; "confirmar o previsto" (casa, não duplica, previsto já confirmado vira novo); pagamento
  da fatura desmarcado; teste do Jovem na rota de importação e nas dicas de categoria; E2E da
  importação.
- **8.3:** sequência com semana congelada (não soma em N), duas faltas no mesmo mês, virada de mês,
  fuso, lançamento de recorrência não conta; nenhuma frase de perda; conquistas de família por papel
  (Jovem sem reserva, Jovem que vira Adulto ganha em silêncio); ligar o módulo e membro novo gravam
  `silent`; celebração só após ação local; dicas desligadas em Ajustes.
- `check:chunks` passando em cada entrega.

## 8. Critério de saída

- **8.1:** os cinco cartões no staging com fonte e data; o alarme testado (uma falha forçada no
  staging chega ao e-mail); o aviso da CVM nas duas telas. O relógio das 2 semanas começa no
  deploy de produção.
- **8.2:** o mantenedor importa um extrato real de cada banco da família no staging, duas vezes,
  sem duplicar.
- **8.3:** a sequência e as conquistas aparecem para duas pessoas diferentes; dicas desligam.
- **v0.3 sai quando** a coleta roda 2 semanas sem falha silenciosa, S3 passa, o aviso da CVM aparece
  em toda tela de indicadores e um extrato real de cada banco da família entra sem duplicar.

## 9. Custos

- Nenhum serviço pago novo. Duas rodadas de cinco requisições por dia ao Banco Central.
- Dois alarmes a mais no CloudWatch (centavos por mês).
- Banco: cinco séries com poucas centenas de pontos por ano.

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Banco Central muda formato ou limite da API | Validação de faixa, alerta por falha, cliente isolado e testado com respostas gravadas |
| Job não roda e ninguém vê | Alarme por ausência de `INDICATOR_RUN` em 26 h |
| Extrato de um banco foge do padrão | Tela de mapeamento manual no CSV; arquivo de exemplo por banco; conferência real no staging |
| Duplicar ao reimportar | `import_key` com índice único, incluindo excluídos, e `import_tombstones` depois da limpeza |
| Importado e previsto contam duas vezes | "Confirmar o previsto" casa a linha com o lançamento da recorrência |
| Pagamento da fatura conta duas vezes no cartão | Linha de sinal oposto vem desmarcada, com aviso |
| Texto que pareça recomendação | Aviso fixo, revisão de texto no PR, calculadora sem produto |
| Sequência vira pressão | Semana congelada, sem frase de perda, sem notificação |
| Pacote inicial passa de 200 KB | Tudo sob demanda; `check:chunks` no CI |
| Conquista vaza ao Jovem algo da reserva | Conquista de família só para quem vê; teste por papel |

## 11. O que fica para o construtor

- Nomes de classes, rotas exatas além das listadas e a forma do JSON.
- Texto das dicas, dos tours e das conquistas, em PT-BR curto, dentro das regras da seção 2.
- A heurística de detecção do CSV e a normalização da descrição (minúsculas, sem acento, espaços
  únicos, sem números de parcela), desde que a mesma regra sirva à `import_key` e ao "possível
  repetido".
- Biblioteca de decimal no front (ou a que o projeto já usa em `Money`).
- O layout dos cartões e da prévia, dentro do design system.
