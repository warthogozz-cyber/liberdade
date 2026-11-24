# Liberdade · Plano 8.1: Indicadores e calculadora de rendimento real — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a primeira entrega da v0.3.
- Com o módulo Indicadores ligado (ele começa desligado), a família vê em Mais cinco números da
  economia: Selic (meta), CDI, IPCA do mês, IPCA em 12 meses e dólar (PTAX venda). Cada card mostra
  o valor, a variação, a fonte e a data da atualização, e avisa quando o dado está desatualizado
  (US-30).
- A calculadora de rendimento real tira a inflação de um rendimento: 10% com IPCA de 4% dão 5,77%.
  O IPCA vem preenchido com a fonte e pode ser trocado (US-31).
- As duas telas mostram o aviso fixo "Conteúdo educativo. Não é recomendação de investimento."

**Architecture:**
- **`indicators` (módulo novo da API):** um job agendado busca as cinco séries no Banco Central duas
  vezes por dia e grava os pontos em duas tabelas sem família (V18). Uma rota de leitura monta os
  cards. O navegador nunca chama o Banco Central.
- **Saúde da coleta:** cada série guarda o último sucesso, a última falha e quantas falhas seguidas.
  Falhas e dado velho viram uma linha `INDICATOR_ALERT` no log, que o CloudWatch transforma em
  e-mail, como a cobrança já faz.
- **Front:** a feature nova `features/indicators` (sob demanda), dois componentes novos em
  `shared/ui` (`SourceStamp` e `Disclaimer`) e uma página pública `/robo` que explica o robô.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, `RestClient`, `@Scheduled`,
  PostgreSQL 18, Flyway, JdbcClient. Testes com Testcontainers, `MockRestServiceServer` e
  `MockMvcTester`.
- **Web:** React 19, react-router v8, TanStack Query, react-intl, MSW 3, Vitest e Playwright com axe.
- **Infra:** OpenTofu (`tofu test` no CI), CloudWatch.
- **Sem dependências novas.**

**Spec:**
- [Spec do Plano 8](../specs/2026-11-24-liberdade-plan-8-indicators-gamification-design.md),
  aprovado pelo mantenedor em 2026-11-24. Este plano cobre só a 8.1: as seções 6.1 a 6.4, a 6.9 no
  que toca a 8.1, e a parte 8.1 das seções 7 e 8. É a autoridade: este plano argumenta a partir dele.
- [PRD](../../prd.md) (US-30 e US-31), [Roadmap](../../roadmap.md),
  [Arquitetura](../../architecture.md), [Modelo de dados](../../data-model.md),
  [Design system](../../design-system.md).

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits (`feat(indicators): ...`, `feat(web): ...`,
  `feat(infra): ...`, `test(e2e): ...`, `docs: ...`), com o assunto em minúsculas. Cada commit
  termina com a linha `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **O navegador nunca chama o Banco Central.** Só a API busca, e só o job agendado (ou a coleta de
  partida, D4) faz chamadas para fora.
- **Nenhum teste chama o Banco Central de verdade.** O perfil de teste usa a fonte falsa; o cliente
  real é testado contra respostas gravadas em `api/src/test/resources/indicators/`.
- **Robô educado (spec 6.3):** `User-Agent: LiberdadeBot/1.0 (+<URL da página /robo>)`, timeout de
  10 s, no máximo 2 novas tentativas (espera de 2 s e de 8 s), 1 s entre uma série e outra, duas
  coletas por dia. A API do Banco Central é pública e feita para isso; não há scraping de página.
- **Sem dado de família:** as tabelas novas não têm `workspace_id`. Nada entra na exportação nem na
  exclusão de conta (spec 6.9).
- **Papéis:** os quatro papéis leem os indicadores (`allow = {OWNER, ADULT, VIEWER, YOUTH}`).
- **Módulo:** `indicators` começa desligado. Desligado, a rota responde 403 `module.disabled` e o
  item some de Mais. A coleta roda sempre, ligado ou não (D4).
- **Tempo:** o `Clock` da API em tudo que a API decide. O fuso das coletas e do "dia útil" é
  `America/Sao_Paulo`. Scheduling fica desligado nos testes; os testes chamam o job direto.
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR.
- **Logs:** nunca um valor de indicador. As linhas de alerta seguem o formato exato
  `INDICATOR_ALERT type=<FETCH_FAILED|STALE> series=<CODE>` (WARN) e `INDICATOR_RUN ok=<n> failed=<n>`
  (INFO). Falhas inesperadas logam só `failure.getClass().getName()`.
- **Fronteiras de módulo:** `indicators` declara `allowedDependencies = {"workspace", "shared"}`;
  nenhum módulo depende dele; o `ApplicationModulesTest` passa.
- **Contrato:** a rota nova entra no `api/openapi.json`, no `web/src/shared/api/schema.ts` e no
  `RoleMatrixIT`, no mesmo commit.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `shared*` e `*.domain*`.
- **Números (spec 6.2):** duas casas decimais, o dólar com quatro, vírgula como separador. Valores
  chegam do servidor como texto decimal (`"15.00"`); o web troca o ponto pela vírgula e nunca passa
  por `Number`. O sinal de menos na tela é U+2212.
- **CVM (spec 6.4):** "Conteúdo educativo. Não é recomendação de investimento." fixo no topo das duas
  telas. Nenhuma tela cita produto, banco nem "melhor opção".
- **Ética:** a variação é texto neutro (sem vermelho nem verde). Abaixo da inflação, a calculadora
  diz "abaixo da inflação", sem alarme nem ícone de perigo.
- **Desempenho:** o carregamento inicial do web continua até 200 KB (`check:chunks`, hoje
  198,5 KB). Tudo da 8.1 no web carrega sob demanda; a 8.1 não pode somar bytes ao carregamento
  inicial.
- **Acessibilidade:** axe no Playwright cobre as duas telas e `/robo`. O selo "desatualizado" é texto,
  não só cor. Os campos da calculadora têm `label`, e o erro usa `aria-describedby`.
- **Custo:** R$ 0 fixo (spec, seção 9). A API do Banco Central é gratuita. O CloudWatch fica em 9 de
  10 alarmes do nível gratuito (D7).
- **GitHub Actions:** nenhum workflow novo e nenhuma permissão nova.
- **Versões:** nenhuma biblioteca nova nem versão nova.
- **Ações externas:** nenhuma durante a execução. Gravar as respostas reais do Banco Central (Task 2)
  é um GET público, sem dado pessoal, feito uma vez pelo implementador. Aplicar o Terraform e
  conferir o staging é do mantenedor, depois do merge.

### Pré-requisitos locais

Os mesmos da 7.3: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o compose de
e2e. O `tofu` não precisa estar instalado: o CI roda `tofu fmt`, `tofu validate` e `tofu test`.

## Entrega

- **PR 1 (docs):** o spec, este plano e o README dos planos (PR #54). Entra antes do código.
- **PR 2 (código):** branch `feat/plan-8-1-indicators`, cortada da `main` depois do PR 1. Um commit
  por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o mantenedor aplica o Terraform (os alarmes novos) e o staging recebe o deploy
  pelo pipeline. O mantenedor confere no staging o critério de saída da 8.1 (seção 8 do spec):
  - os cinco cards aparecem com fonte e data, com o módulo ligado;
  - uma falha forçada no staging chega ao e-mail (D7 diz como forçar);
  - o aviso da CVM está nas duas telas;
  - as duas semanas de observação contam a partir do deploy em produção.
- **Depois disso:** o plano 8.2 (importação de extrato).

### Itens herdados

| Item | Onde |
|---|---|
| Abrir espaço no carregamento inicial antes do Plano 8 (7.3) | Task 10 confere que a 8.1 não soma nada ao inicial; abrir espaço fica fora da 8.1 |
| Reconectar com a aba visível depois de queda de mais de 60 s (7.3) | Fora da 8.1 |
| Celebração da reserva quando o alvo ou a conta mudam (7.3) | Fora da 8.1 |
| Batida do stream fora da thread do agendador (7.3) | Fora da 8.1 (o job novo não usa essa thread por muito tempo: D4) |
| `maxWait` de cerca de 1 s no debounce (7.3) | Fora da 8.1 |
| Destaque antes de o refetch terminar (7.3) | Fora da 8.1 |
| Interruptor de Ajustes com 48 px de toque e rótulo clicável (7.3) | Fora da 8.1 |
| E2E do Jovem esperando `/events`, reaproveitando `adultJoins` (7.3) | Fora da 8.1 |
| Limite de erro do `TagsTab` sob demanda (7.1) | PR próprio, com a tela de erro de rota em PT-BR |
| Issues #36, #38, #39 e #40 | Fora da 8.1 (infra, testes de proxy e prettier de docs) |

## Decisões deste plano

A seção 11 do spec deixa estes pontos ao construtor, e o plano decide:

**D1. Módulo e pacotes.** Pacote `br.com.liberdadefamilia.indicators`, com
`@ApplicationModule(allowedDependencies = {"workspace", "shared"})`.
- `indicators/domain` (puro, 90% de cobertura): `IndicatorUnit`, `Point`, `BusinessDays`,
  `Staleness`, `ValueRange`, `Variation`, `VariationUnit`, `VariationBasis` e `Variations`.
- `indicators/internal`: o resto (enum das séries, fontes, repositório, job, alertas, rota).
- `IndicatorCode` é um enum cuja ordem é a ordem dos cards: `SELIC_TARGET`, `CDI`, `IPCA_MONTH`,
  `IPCA_12M`, `USD_PTAX_SELL`. Ele guarda o comportamento (frequência, casas decimais, regra da
  variação). A fonte, a referência, a unidade e o nome ficam no seed da V18; um teste confere que os
  dois batem.

**D2. Propriedades** (`liberdade.indicators.*`, record `IndicatorProperties`):

| Propriedade | Padrão | Observação |
|---|---|---|
| `source` | `bcb` | `fake` nos perfis `dev`, `e2e` e `test`; recusado sob `prod` (`IndicatorStartupCheck`) |
| `sgs-base-url` | `https://api.bcb.gov.br` | |
| `ptax-base-url` | `https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1` | |
| `bot-url` | `${liberdade.web.origin}/robo` | vai no `User-Agent` |
| `timeout` | `10s` | conexão e leitura |
| `retry-waits` | `2s,8s` | uma espera por nova tentativa |
| `pause-between-series` | `1s` | |

**D3. Cliente do Banco Central e novas tentativas.**
- SGS: `GET {sgs}/dados/serie/bcdata.sgs.{ref}/dados?formato=json&dataInicial=dd/MM/aaaa&dataFinal=dd/MM/aaaa`,
  sempre com as duas datas (o SGS limita a janela a 10 anos). Cada registro é
  `{"data":"dd/MM/aaaa","valor":"x.xx"}`; o valor vira `BigDecimal` sem passar por `double`.
- PTAX: `GET {ptax}/odata/CotacaoDolarPeriodo(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)?@dataInicial='MM-dd-aaaa'&@dataFinalCotacao='MM-dd-aaaa'&$format=json`,
  campo `cotacaoVenda`, data de `dataHoraCotacao`. Se vier mais de uma cotação no mesmo dia, vale a
  de `dataHoraCotacao` mais tarde (o boletim de fechamento).
- Os nomes dos campos e a resposta de janela vazia são conferidos ao gravar as respostas reais
  (Task 2). Se a gravação mostrar outra coisa, o implementador segue a gravação e anota no relatório.
- Nova tentativa só em timeout, erro de IO ou 5xx: até 2, esperando 2 s e depois 8 s, por um
  `Sleeper` injetado (os testes usam um que só anota). 4xx e JSON estranho falham na hora. Um 404
  do SGS que a gravação mostrar ser "sem dados na janela" vira lista vazia.
- `source_url` de cada ponto é a URL exata da chamada (pública, sem dado pessoal).

**D4. Coleta.**
- `@Scheduled(cron = "0 30 8,19 * * *", zone = "America/Sao_Paulo")`. A coleta roda sempre, com o
  módulo ligado ou não: o dado é o mesmo para todas as famílias, e ligar o módulo mostra números na
  hora.
- Janela: série diária, os últimos 30 dias; mensal, os últimos 24 meses; série sem nenhum ponto, os
  últimos 13 meses. A janela termina hoje (São Paulo).
- Uma transação por série: grava os pontos com `INSERT ... ON CONFLICT (series_code, ref_date) DO
  UPDATE` e marca o sucesso juntos. Falha marca `last_failure_at` e soma 1 em `failure_streak`
  fora dessa transação.
- **Resposta vazia conta como falha** (`EMPTY`): as janelas sempre têm dado, e aceitar vazio como
  sucesso esconderia uma fonte quebrada atrás de um `last_success_at` em dia.
- Um ponto fora da faixa rejeita a resposta inteira (`OUT_OF_RANGE`, nada é gravado).
- O alerta `STALE` só sai na coleta da manhã (hora de São Paulo antes do meio-dia), uma vez por dia.
- `IndicatorStartupRun`: no `ApplicationReadyEvent`, só com scheduling ligado, se alguma série nunca
  teve sucesso, coleta uma vez numa thread virtual (não segura a subida). Isso enche o e2e pela fonte
  falsa e a produção no primeiro deploy. Sem checagem de `STALE` na partida.
- Duas coletas nunca correm juntas: `runOnce` usa `tryLock` e sai se já houver uma rodando.

**D5. Desatualizado.** Conta os dias de segunda a sexta em (dia do último sucesso, hoje], em São
Paulo, sem feriados. Mais de 3 → desatualizado. Nunca teve sucesso → desatualizado. Exemplo: sucesso
na sexta, ainda em dia na quarta, desatualizado na quinta.

**D6. Resposta da rota** (`GET /v1/workspaces/{ws}/indicators`):

```json
{ "indicators": [ {
  "code": "SELIC_TARGET", "name": "Selic (meta)", "unit": "PERCENT_YEAR",
  "value": "15.00", "referenceDate": "2026-11-24",
  "variation": { "value": "0.50", "unit": "PP", "basis": "LAST_CHANGE", "since": "2026-11-05" },
  "source": { "name": "Banco Central do Brasil", "url": "https://api.bcb.gov.br/..." },
  "updatedAt": "2026-11-24T11:30:02Z", "stale": false } ] }
```

- Sempre as cinco séries, na ordem do enum. Sem ponto: `value`, `referenceDate`, `variation` e
  `source.url` nulos.
- `value`: o último ponto, arredondado HALF_UP para 2 casas (o dólar para 4), como texto.
- `variation` (nula quando não há com o que comparar):
  - `SELIC_TARGET`: a última mudança nos pontos guardados, em p.p., `basis = LAST_CHANGE`, `since` =
    o primeiro dia do valor novo. Sem mudança nos pontos guardados → nula.
  - `CDI`, `IPCA_MONTH`, `IPCA_12M`: último menos o anterior, em p.p., `basis = PREVIOUS`, `since` =
    a data do ponto anterior.
  - `USD_PTAX_SELL`: `(último ÷ anterior − 1) × 100`, em %, `basis = PREVIOUS`.
  - O valor vai com 2 casas, HALF_UP, como texto com sinal só quando negativo (`"-0.25"`).
- `updatedAt`: `last_success_at` da série. `stale`: D5.
- Nomes do seed: 'Selic (meta)', 'CDI', 'IPCA do mês', 'IPCA em 12 meses', 'Dólar (PTAX venda)'.
- Sem limpeza de pontos (cerca de 1.300 linhas por ano).

**D7. Terraform** (`infra/live/indicator_alerts.tf`).
- O mapa `local.billing_alert_log_groups` vira `local.api_log_groups` (mesmas chaves, nenhuma
  mudança de estado), usado pela cobrança e pelos indicadores.
- `indicator_alert`: filtro e alarme por ambiente (staging e produção), cópia do da cobrança com
  `INDICATOR_ALERT` e a métrica `IndicatorAlerts`. O staging precisa dele para a conferência da
  seção 8.
- `indicator_run_missing`: só produção. Filtro `"\"INDICATOR_RUN\""`, métrica `IndicatorRuns`, sem
  `default_value`. Alarme `Sum < 1`, `period = 3600`, `evaluation_periods = 24`,
  `treat_missing_data = "breaching"`. **Desvio do spec:** o spec fala em 26 h, mas o CloudWatch
  limita a janela de avaliação a um dia; com coletas a no máximo 13 h uma da outra, 24 h sem
  `INDICATOR_RUN` já é falha. Só produção porque o staging pode ficar parado e daria alarme falso,
  e assim o total fica em 9 alarmes (6 hoje + 3), dentro dos 10 gratuitos.
- Os dois alarmes avisam `aws_sns_topic.alerts` (alarme e volta ao normal).
- Para forçar uma falha no staging (o mantenedor, depois do deploy): pôr a variável de ambiente
  `LIBERDADE_INDICATORS_SGS_BASE_URL` apontando para um endereço que não responde e esperar a
  próxima coleta, ou mandar uma linha `INDICATOR_ALERT type=FETCH_FAILED series=CDI` com
  `aws logs put-log-events` no grupo do staging. O runbook novo (Task 9) traz os dois jeitos.

**D8. `ModuleRoute`.** `app/PlanningModuleRoute.tsx` vira `app/ModuleRoute.tsx` com a prop
`module: ModuleKey` (mesmo comportamento: nada enquanto carrega, `Navigate to="/inicio" replace`
quando desligado). As rotas do Planejamento passam a usar `<ModuleRoute module="planning">`, e os
testes delas seguem passando.

**D9. Feature `indicators` no web.**
- `features/indicators/register.ts`: `registerMessages` e `registerModule({ key: 'indicators',
  available: true })`, importado em `app/routes.tsx` como os outros.
- Rotas sob demanda: `/mais/indicadores` e `/mais/indicadores/calculadora`, dentro de
  `<ModuleRoute module="indicators">`.
- Item "Indicadores" em Mais (`more.indicators`) quando o módulo está ligado, para os quatro papéis.
  Ele usa o hook de módulos já existente; a lista de módulos já está no carregamento inicial, então o
  item não soma bytes de dado.
- `useIndicators(workspaceId)` com a chave `['ws', ws, 'indicators']` e `staleTime` de 30 minutos.
- Card sem ponto: "Sem dados ainda".

**D10. `shared/ui`.**
- `SourceStamp({ source, url, updatedAt, stale })`: "Fonte: Banco Central do Brasil · atualizado em
  24/11/2026" (data em `America/Sao_Paulo`). Sem `updatedAt`: "Fonte: … · ainda sem atualização".
  Com `stale`, o selo de texto "desatualizado". O nome da fonte vira link (nova aba,
  `rel="noopener noreferrer"`) só quando `url` começa com `https://`.
- `Disclaimer({ children })`: faixa `role="note"`, fixa no topo da tela (`sticky top-0`).

**D11. Calculadora** (`features/indicators/realReturn.ts`, `bigint`, sem biblioteca).
- `parseRate(text: string): bigint | null`: percentual × 10⁴. Aceita vírgula ou ponto, sinal de
  menos opcional, até 4 casas. `'10'` → `100000n`, `'4,5'` → `45000n`, `'-1,25'` → `-12500n`.
  Vazio, letras, mais de 4 casas → `null`.
- Faixa aceita: de −99,99% a 1000%. Fora dela, erro no campo ("Use um número entre −99,99 e 1000").
- `realReturn(nominal: bigint, inflation: bigint): bigint`: resultado em percentual × 10², metade
  para longe do zero. `real = (1 + n) ÷ (1 + i) − 1 = (n − i) ÷ (1 + i)`. Com as entradas em
  percentual × 10⁴ (100% = `1_000_000n`), uma divisão só, arredondada uma vez:
  `roundHalfAwayFromZero((n − i) × 10_000n, 1_000_000n + i)`. Arredondar antes de subtrair daria
  empate para o lado errado nos negativos. O divisor é sempre positivo, porque a faixa barra
  inflação de −100%. (10%, 4%) → `577n`; (3%, 4%) → `-96n`; (4%, 4%) → `0n`.
- `formatRate(hundredths: bigint): string`: `577n` → `'5,77'`, `-96n` → `'−0,96'`, `0n` → `'0,00'`.
- A tela mostra a conta: "(1 + 10,00%) ÷ (1 + 4,00%) − 1 = 5,77%". Abaixo da inflação, "abaixo da
  inflação"; igual, "igual à inflação"; acima, nada a mais.
- O IPCA vem do card `IPCA_12M`. O `SourceStamp` aparece só enquanto o campo tem o valor
  preenchido; editou, o selo some e aparece "valor informado por você". Sem IPCA (sem ponto ou erro),
  o campo fica vazio e a pessoa digita.

**D12. Página do robô.** Rota pública sob demanda `/robo` (`features/legal/BotPage.tsx`): o que é
coletado (as cinco séries), de onde (`api.bcb.gov.br` e `olinda.bcb.gov.br`), quando (8h30 e 19h30,
horário de Brasília) e como falar com o mantenedor (o canal da política de privacidade).

**D13. Política de privacidade.** Versões `privacy-2026-11.3` e `privacy-2026-12.3`: cópia da 11.2 e
da 12.2 com um parágrafo novo ("O app busca indicadores públicos no Banco Central do Brasil. Nenhum
dado seu ou da sua família sai nessa busca.") e a data nova. Só aviso, sem novo aceite (o padrão
D23 da 7.2): `KEEPS_ACCEPTANCE` ganha `privacy-2026-11.3 → privacy-2026-11.2` e
`privacy-2026-12.3 → privacy-2026-12.2`, e `legalConfig.latest.privacy` passa a `privacy-2026-12.3`.

**D14. Docs.** `docs/architecture.md` (módulo e job), `docs/data-model.md` (V18),
`docs/design-system.md` (`SourceStamp` e `Disclaimer` deixam de ser só desenho),
`docs/runbooks/indicator-alerts.md` (novo: o que fazer quando o e-mail chega e como forçar uma falha
no staging) e o roadmap, se algo mudou.

**D15. E2E.** A fonte falsa (perfil `e2e`) grava pontos fixos (Task 2). O teste liga o módulo, vê os
cinco cards com fonte e data e o aviso, abre a calculadora, digita 10 e lê 5,77%, e passa o axe nas
duas telas e em `/robo`. `core-without-modules` segue passando.

**D16. Matriz de papéis.** Uma linha no `RoleMatrixIT` para o GET (quatro papéis → 200, com o módulo
ligado no preparo) e um IT para o 403 `module.disabled` com o módulo desligado.

## Review Focus

1. **Desatualizado em fim de semana e feriado:** sucesso na sexta segue em dia até quarta; um
   feriado conta como dia útil (sem calendário de feriados), então o alerta pode sair um dia antes.
   Testes: `StalenessTest.fridaySuccessFreshUntilWednesday` e `...staleOnThursday` (Task 1).
2. **Mudança de formato no Banco Central gravada como verdade:** campo renomeado, número com
   vírgula, valor absurdo (Selic 1500). Nada é gravado e a série conta falha. Testes:
   `BcbIndicatorSourceTest.renamedFieldIsBadJson`, `IndicatorJobIT.outOfRangeWritesNothing`
   (Tasks 2 e 3).
3. **Fonte falsa em produção, ou o Banco Central de verdade no CI:**
   `IndicatorStartupCheckTest.fakeRefusedUnderProd` e o perfil de teste com `source=fake`
   (Task 2).
4. **Aviso da CVM ou selo da fonte faltando:** um card sem `SourceStamp`, a calculadora sem o aviso.
   Testes: `IndicatorsPage.test.tsx › everyCardHasASourceStamp`,
   `RealReturnPage.test.tsx › showsTheDisclaimer` e o e2e (Tasks 7, 8 e 10).
5. **Arredondamento da calculadora:** resultado negativo, empate na metade, vírgula e ponto. Testes:
   `realReturn.test.ts › roundsHalfAwayFromZero` e `parsesCommaAndDot` (Task 8).

## Mapa de arquivos

Caminhos da API relativos a `api/src/main/java/br/com/liberdadefamilia/` (testes em
`api/src/test/java/br/com/liberdadefamilia/`); do web, relativos a `web/src/`.

| Arquivo | Task | Papel |
|---|---|---|
| `api/src/main/resources/db/migration/V18__indicators.sql` | 1 | as duas tabelas e o seed das cinco séries |
| `indicators/package-info.java` | 1 | módulo e dependências |
| `indicators/domain/*` | 1 | dia útil, desatualizado, faixa, variações |
| `indicators/internal/IndicatorCode.java` | 1 | as cinco séries e seu comportamento |
| `indicators/internal/IndicatorSource.java`, `FetchedPoint.java`, `IndicatorFetchException.java`, `Sleeper.java` | 2 | contrato da fonte |
| `indicators/internal/BcbIndicatorSource.java`, `FakeIndicatorSource.java` | 2 | fonte real e falsa |
| `indicators/internal/IndicatorProperties.java`, `IndicatorSourceConfig.java`, `IndicatorStartupCheck.java` | 2 | configuração |
| `api/src/test/resources/indicators/*.json` | 2 | respostas gravadas |
| `indicators/internal/IndicatorSeries.java`, `IndicatorRepository.java` | 3 | leitura e escrita |
| `indicators/internal/IndicatorJob.java`, `IndicatorAlerts.java`, `IndicatorStartupRun.java` | 3 | coleta e alertas |
| `indicators/internal/IndicatorController.java`, `IndicatorService.java`, `IndicatorViews.java`, `IndicatorsOpenApiConfig.java` | 4 | rota de leitura |
| `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java` | 4 | contrato |
| `infra/live/indicator_alerts.tf`, `infra/live/billing_alerts.tf`, `infra/live/tests/operations.tftest.hcl` | 5 | alarmes |
| `shared/ui/SourceStamp.tsx`, `shared/ui/Disclaimer.tsx`, `shared/ui/index.ts` | 6 | componentes |
| `app/ModuleRoute.tsx`, `app/PlanningModuleRoute.tsx` (sai), `app/routes.tsx`, `app/MorePage.tsx` | 7 | rota por módulo e item em Mais |
| `features/indicators/register.ts`, `messages.pt-BR.ts`, `useIndicators.ts`, `format.ts`, `IndicatorsPage.tsx`, `IndicatorCard.tsx` | 7 | tela dos indicadores |
| `mocks/handlers.ts`, `mocks/data.ts`, `mocks/indicatorHandlers.test.ts` | 7 | MSW |
| `features/indicators/realReturn.ts`, `RealReturnPage.tsx` | 8 | calculadora |
| `features/legal/BotPage.tsx`, `features/legal/texts/privacy-2026-1{1,2}.3.ts`, `legalConfig.ts`, `privacy/internal/LegalDocuments.java` | 9 | robô e política |
| `docs/architecture.md`, `docs/data-model.md`, `docs/design-system.md`, `docs/runbooks/indicator-alerts.md` | 9 | docs |
| `web/e2e/indicators.spec.ts`, `web/e2e/a11y.spec.ts` | 10 | e2e |

## Tasks

### Task 1: V18, o esqueleto do módulo e o domínio

**Files:**
- Create: `db/migration/V18__indicators.sql`, `indicators/package-info.java`,
  `indicators/domain/{IndicatorUnit,Point,BusinessDays,Staleness,ValueRange,Variation,VariationUnit,VariationBasis,Variations}.java`,
  `indicators/internal/IndicatorCode.java`.
- Test: `indicators/domain/{BusinessDaysTest,StalenessTest,ValueRangeTest,VariationsTest}.java`,
  `indicators/IndicatorSeedIT.java`, `ApplicationModulesTest`.

**Interfaces:**
- Consumes: nada novo.
- Produces:
  - `enum IndicatorUnit { PERCENT_YEAR, PERCENT_MONTH, PERCENT_12M, BRL }`;
  - `record Point(LocalDate refDate, BigDecimal value)`;
  - `BusinessDays.weekdaysAfter(LocalDate from, LocalDate to): int` — dias de segunda a sexta em
    (from, to]; 0 quando `to` não é depois de `from`;
  - `Staleness.isStale(@Nullable Instant lastSuccessAt, Instant now, ZoneId zone): boolean` (D5);
  - `ValueRange.accepts(IndicatorUnit unit, BigDecimal value): boolean` — percentuais em
    [−5, 100], `BRL` em [1, 50], limites inclusos;
  - `enum VariationUnit { PP, PERCENT }`, `enum VariationBasis { LAST_CHANGE, PREVIOUS }`;
  - `record Variation(BigDecimal value, VariationUnit unit, VariationBasis basis, LocalDate since)`
    com `value` já em 2 casas HALF_UP;
  - `Variations.lastChange(List<Point> ascending): @Nullable Variation` e
    `Variations.vsPrevious(List<Point> ascending, VariationUnit unit): @Nullable Variation` (D6);
  - `enum IndicatorCode` (D1) com `Frequency frequency()` (`DAILY`/`MONTHLY`), `int decimals()`
    (2, o dólar 4) e `VariationRule variationRule()` (`LAST_CHANGE_PP`, `PREVIOUS_PP`,
    `PREVIOUS_PERCENT`).

**Como fica:**
- A V18 é o DDL da seção 6.2 do spec, sem mudança, mais o seed:

| code | source | source_ref | unit | name |
|---|---|---|---|---|
| SELIC_TARGET | BCB_SGS | 432 | PERCENT_YEAR | Selic (meta) |
| CDI | BCB_SGS | 4389 | PERCENT_YEAR | CDI |
| IPCA_MONTH | BCB_SGS | 433 | PERCENT_MONTH | IPCA do mês |
| IPCA_12M | BCB_SGS | 13522 | PERCENT_12M | IPCA em 12 meses |
| USD_PTAX_SELL | BCB_PTAX | CotacaoDolarPeriodo | BRL | Dólar (PTAX venda) |

- `package-info.java` declara o módulo como em D1. Nada fora dele usa os tipos novos ainda.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `BusinessDaysTest.countsOnlyWeekdays` | de sexta 2026-11-20 a segunda 2026-11-23 → 1 |
| `BusinessDaysTest.sameDayIsZero` | `from == to` → 0 |
| `StalenessTest.neverSucceededIsStale` | `null` → `true` |
| `StalenessTest.fridaySuccessFreshUntilWednesday` | sucesso sex 2026-11-20 10:00 SP; agora qua 2026-11-25 20:00 SP → `false` |
| `StalenessTest.staleOnThursday` | mesmo sucesso; agora qui 2026-11-26 08:00 SP → `true` |
| `StalenessTest.usesSaoPauloDate` | sucesso 2026-11-20T02:30Z (23:30 de quinta em SP) conta como quinta |
| `ValueRangeTest.ratesBetweenMinusFiveAndHundred` | −5 e 100 → `true`; −5.01 e 100.01 → `false` |
| `ValueRangeTest.dollarBetweenOneAndFifty` | 1 e 50 → `true`; 0.99 e 50.01 → `false` |
| `VariationsTest.lastChangeFindsTheNewRate` | 14.75 (3 dias), 15.25 (2 dias a partir de 2026-11-05) → `+0.50 PP LAST_CHANGE since 2026-11-05` |
| `VariationsTest.lastChangeNullWithoutChange` | tudo 15.00 → `null` |
| `VariationsTest.vsPreviousInPoints` | 4.10 → 4.00 → `-0.10 PP PREVIOUS since` a data do 4.10 |
| `VariationsTest.vsPreviousInPercent` | 5.4321 → 5.4100 → `-0.41 PERCENT` |
| `VariationsTest.singlePointHasNoVariation` | um ponto → `null` nas duas |
| `IndicatorSeedIT.seedMatchesTheEnum` | cinco linhas, uma por `IndicatorCode`, unidades da tabela acima, saúde zerada |
| `ApplicationModulesTest` | passa com `indicators` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*indicators*' --tests '*ApplicationModulesTest'`
Expected: FAIL (tipos que não existem).

- [ ] **Step 3: Implementar.** A migração, o `package-info.java`, o domínio e o enum. O percentual
  do dólar é `(last / previous − 1) × 100` com `MathContext.DECIMAL64` antes do arredondamento.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(indicators): add the v18 tables, the module and the indicator domain"
```

---

### Task 2: As fontes (Banco Central e falsa) e a configuração

**Files:**
- Create: `indicators/internal/{IndicatorSource,FetchedPoint,IndicatorFetchException,Sleeper,BcbIndicatorSource,FakeIndicatorSource,IndicatorProperties,IndicatorSourceConfig,IndicatorStartupCheck}.java`,
  `api/src/test/resources/indicators/` (respostas gravadas).
- Modify: `application.properties`, `application-dev.properties`, `application-e2e.properties`,
  `api/src/test/resources/application-test.properties`.
- Test: `indicators/BcbIndicatorSourceTest.java`, `indicators/FakeIndicatorSourceTest.java`,
  `indicators/IndicatorStartupCheckTest.java`.

**Interfaces:**
- Consumes: `IndicatorCode`, `IndicatorUnit` (Task 1).
- Produces:
  - `record FetchedPoint(LocalDate refDate, BigDecimal value, String sourceUrl)`;
  - `interface IndicatorSource { List<FetchedPoint> fetch(IndicatorCode code, String sourceRef, LocalDate from, LocalDate to) throws IndicatorFetchException; }`
    — devolve os pontos em ordem crescente de data;
  - `IndicatorFetchException(Kind kind)` com `enum Kind { TIMEOUT, IO, HTTP_STATUS, BAD_JSON, EMPTY, OUT_OF_RANGE }`
    e `Kind kind()`; a mensagem não carrega valor nem URL;
  - `@FunctionalInterface interface Sleeper { void sleep(Duration duration) throws InterruptedException; }`;
  - `IndicatorProperties` (D2) com `boolean fake()`;
  - um bean `IndicatorSource` (real ou falso, por `source`) e um bean `Sleeper` (`Thread::sleep`)
    em `IndicatorSourceConfig`.

**Como fica:**
- `BcbIndicatorSource` usa `RestClient` com o `User-Agent` e o timeout de D2/D3 e tem um construtor
  de teste que recebe o `RestClient.Builder` (o mesmo jeito do `AsaasGateway`), para o
  `MockRestServiceServer`.
- A gravação: o implementador faz, uma vez, os GETs públicos de D3 (as cinco séries, uma janela
  curta) e salva as respostas em `api/src/test/resources/indicators/` com nomes pelo caso:
  `sgs-432-ok.json`, `sgs-433-ok.json`, `sgs-empty.json` (ou o corpo do 404, se for o caso),
  `ptax-ok.json`, `ptax-two-quotes-same-day.json` (montado a partir do real), `sgs-renamed-field.json`
  e `sgs-comma-value.json` (montados). O relatório diz o que a gravação mostrou sobre a janela vazia
  e os nomes dos campos.
- `FakeIndicatorSource` devolve pontos fixos, só em dias úteis para as diárias e no dia 1 para as
  mensais, dentro de `[from, to]`, com `sourceUrl = "fake:" + code`:
  - `SELIC_TARGET`: 14.75 até 20 dias antes de `to`, depois 15.00;
  - `CDI`: 14.90;
  - `IPCA_MONTH`: 0.40, o último mês 0.50;
  - `IPCA_12M`: 4.10, o último mês 4.00 (a calculadora do e2e dá 5,77% com 10%);
  - `USD_PTAX_SELL`: 5.4321, o último dia 5.4100.
- `source=fake` em `application-dev.properties`, `application-e2e.properties` e
  `application-test.properties`. O padrão em `application.properties` é `bcb`.
- `IndicatorStartupCheck` (`ApplicationRunner`, como o `BillingStartupCheck`) lança
  `IllegalStateException("indicators.source=fake is not allowed in prod")` sob o perfil `prod`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `BcbIndicatorSourceTest.parsesSgs` | `sgs-432-ok.json` → pontos com data e `BigDecimal` exatos, crescentes |
| `BcbIndicatorSourceTest.sendsBothDatesAndTheBotAgent` | a URL tem `formato=json`, `dataInicial` e `dataFinal` em `dd/MM/aaaa`; `User-Agent` = `LiberdadeBot/1.0 (+https://app.test/robo)` |
| `BcbIndicatorSourceTest.parsesPtaxSellingRate` | `ptax-ok.json` → `cotacaoVenda` por dia |
| `BcbIndicatorSourceTest.lastQuoteOfTheDayWins` | `ptax-two-quotes-same-day.json` → um ponto, o de `dataHoraCotacao` mais tarde |
| `BcbIndicatorSourceTest.emptyWindowIsEmptyList` | `sgs-empty.json` (ou o 404 gravado) → lista vazia |
| `BcbIndicatorSourceTest.serverErrorRetriesTwiceThenFails` | três 503 → `HTTP_STATUS`; o `Sleeper` anotou `[2s, 8s]` |
| `BcbIndicatorSourceTest.serverErrorThenSuccess` | 503 e depois 200 → pontos; o `Sleeper` anotou `[2s]` |
| `BcbIndicatorSourceTest.clientErrorFailsAtOnce` | 400 → `HTTP_STATUS`, uma chamada só, nenhuma espera |
| `BcbIndicatorSourceTest.renamedFieldIsBadJson` | `sgs-renamed-field.json` → `BAD_JSON`, sem nova tentativa |
| `BcbIndicatorSourceTest.commaValueIsBadJson` | `sgs-comma-value.json` → `BAD_JSON` |
| `BcbIndicatorSourceTest.sourceUrlIsTheRequest` | `sourceUrl` de cada ponto = a URL chamada |
| `FakeIndicatorSourceTest.selicChangesTwentyDaysBack` | os pontos de `SELIC_TARGET` mudam de 14.75 para 15.00 |
| `FakeIndicatorSourceTest.monthlyOnTheFirst` | `IPCA_12M` só no dia 1, o último 4.00 |
| `IndicatorStartupCheckTest.fakeRefusedUnderProd` | `source=fake` + perfil `prod` → `IllegalStateException` |
| `IndicatorStartupCheckTest.bcbAllowedUnderProd` | `source=bcb` + `prod` → sem erro |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*BcbIndicatorSourceTest' --tests '*FakeIndicatorSourceTest' --tests '*IndicatorStartupCheckTest'`
Expected: FAIL.

- [ ] **Step 3: Gravar as respostas reais** (GET público, uma vez) e montar as variações.

- [ ] **Step 4: Implementar.** Siga D2 e D3.

- [ ] **Step 5: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 6: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add api
git commit -m "feat(indicators): add the central bank client, the fake source and the startup check"
```

---

### Task 3: Repositório, coleta, alertas e a coleta de partida

**Files:**
- Create: `indicators/internal/{IndicatorSeries,IndicatorRepository,IndicatorJob,IndicatorAlerts,IndicatorStartupRun}.java`.
- Test: `indicators/IndicatorRepositoryIT.java`, `indicators/IndicatorJobIT.java`,
  `indicators/IndicatorAlertsTest.java`, `indicators/IndicatorStartupRunIT.java`.

**Interfaces:**
- Consumes: Task 1 (domínio, enum) e Task 2 (`IndicatorSource`, `Sleeper`, `IndicatorProperties`).
- Produces (a Task 4 usa):
  - `record IndicatorSeries(IndicatorCode code, String source, String sourceRef, IndicatorUnit unit, String name, @Nullable Instant lastSuccessAt, @Nullable Instant lastFailureAt, int failureStreak)`;
  - `IndicatorRepository`:
    - `List<IndicatorSeries> allSeries()` — na ordem do enum;
    - `Optional<LocalDate> lastRefDate(IndicatorCode code)`;
    - `void upsertPoints(IndicatorCode code, List<FetchedPoint> points, Instant fetchedAt)`;
    - `void markSuccess(IndicatorCode code, Instant at)` — `last_success_at = at`, `failure_streak = 0`;
    - `void markFailure(IndicatorCode code, Instant at)` — `last_failure_at = at`, `failure_streak + 1`;
    - `List<Point> recentPoints(IndicatorCode code, int limit)` — os `limit` mais novos, em ordem crescente;
    - `Optional<String> latestSourceUrl(IndicatorCode code)`;
  - `IndicatorJob.runOnce(boolean checkStale): RunReport` com `record RunReport(int ok, int failed)`;
  - `IndicatorAlerts.fetchFailed(IndicatorCode)`, `stale(IndicatorCode)`, `run(int ok, int failed)`.

**Como fica:**
- `IndicatorJob.run()` é o método `@Scheduled` (D4) e chama `runOnce(horaDeSaoPaulo < 12)`.
- `runOnce`, para cada série na ordem do enum:
  1. janela de D4 (sem ponto → 13 meses; diária → 30 dias; mensal → 24 meses, a partir do dia 1);
  2. `fetch`; lista vazia → falha `EMPTY`; algum ponto fora de `ValueRange` → falha `OUT_OF_RANGE`;
  3. sucesso: `upsertPoints` + `markSuccess` numa transação (`TransactionTemplate`);
  4. falha (`IndicatorFetchException` ou qualquer `RuntimeException`): `markFailure`,
     `alerts.fetchFailed(code)` e uma linha WARN `indicator fetch failed series=<CODE> kind=<KIND>`
     (ou a classe da exceção);
  5. `sleeper.sleep(pauseBetweenSeries)` antes da próxima série.
- Interrompido durante uma espera: devolve a interrupção à thread e para a coleta.
- Depois das cinco, com `checkStale`, relê as séries e chama `alerts.stale(code)` para cada uma que
  `Staleness.isStale` disser. Sempre termina com `alerts.run(ok, failed)`.
- `IndicatorStartupRun` escuta `ApplicationReadyEvent`, existe só com
  `liberdade.scheduling.enabled` ligado (`matchIfMissing = true`, como o `SchedulingConfig`) e, se
  alguma série tem `lastSuccessAt` nulo, chama `runOnce(false)` numa thread virtual. A decisão fica
  num método `collectIfNeverSucceeded()` que os testes chamam direto.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `IndicatorRepositoryIT.upsertReplacesTheSameDay` | ponto de 2026-11-24 gravado duas vezes com valores diferentes → uma linha, o segundo valor |
| `IndicatorRepositoryIT.successResetsTheStreak` | duas falhas e um sucesso → `failure_streak = 0`, `last_success_at` preenchido, `last_failure_at` mantido |
| `IndicatorRepositoryIT.failureAddsOne` | duas falhas → `failure_streak = 2` |
| `IndicatorRepositoryIT.recentPointsAscending` | três pontos, `limit = 2` → os dois mais novos, do mais velho para o mais novo |
| `IndicatorJobIT.firstRunAsksThirteenMonths` | sem pontos, `Clock` em 2026-11-24 → a fonte recebe `from = 2025-10-24` |
| `IndicatorJobIT.dailyAsksThirtyDays` | com pontos, `CDI` → `from = 2026-10-25` |
| `IndicatorJobIT.monthlyAsksTwentyFourMonths` | com pontos, `IPCA_MONTH` → `from = 2024-11-01` |
| `IndicatorJobIT.successWritesPointsAndHealth` | fonte com pontos → pontos gravados, `markSuccess`, `RunReport(5, 0)` |
| `IndicatorJobIT.outOfRangeWritesNothing` | Selic 1500 num dos pontos → nada gravado da série, `failure_streak = 1`, `INDICATOR_ALERT type=FETCH_FAILED series=SELIC_TARGET` |
| `IndicatorJobIT.emptyIsAFailure` | lista vazia → falha, alerta `FETCH_FAILED` |
| `IndicatorJobIT.oneFailureDoesNotStopTheOthers` | `CDI` lança `TIMEOUT` → as outras quatro gravadas, `RunReport(4, 1)` |
| `IndicatorJobIT.staleAlertOnlyWhenAsked` | série com sucesso há 5 dias úteis: `runOnce(true)` → `INDICATOR_ALERT type=STALE`; `runOnce(false)` → nenhum `STALE` |
| `IndicatorJobIT.pausesBetweenSeries` | o `Sleeper` anotou 4 pausas de 1 s |
| `IndicatorJobIT.secondRunWhileRunningIsSkipped` | `runOnce` chamado dentro de outro (a fonte chama de volta) → o de dentro devolve `RunReport(0, 0)` sem buscar |
| `IndicatorAlertsTest.formats` | com `OutputCaptureExtension`: `INDICATOR_ALERT type=FETCH_FAILED series=CDI` em WARN; `INDICATOR_RUN ok=4 failed=1` em INFO; nenhum número de indicador |
| `IndicatorStartupRunIT.collectsWhenASeriesNeverSucceeded` | uma série sem sucesso → coleta chamada |
| `IndicatorStartupRunIT.skipsWhenAllSucceeded` | todas com sucesso → nenhuma coleta |

Os ITs usam uma `IndicatorSource` de teste (`@TestConfiguration`, `@Primary`) que anota as janelas
pedidas e responde o que cada teste mandar, e um `Sleeper` que só anota.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*IndicatorRepositoryIT' --tests '*IndicatorJobIT' --tests '*IndicatorAlertsTest' --tests '*IndicatorStartupRunIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D4.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(indicators): collect the series twice a day with health columns and alerts"
```

---

### Task 4: A rota de leitura, o contrato e a matriz de papéis

**Files:**
- Create: `indicators/internal/{IndicatorController,IndicatorService,IndicatorViews,IndicatorsOpenApiConfig}.java`.
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `RoleMatrixIT.java`.
- Test: `indicators/IndicatorServiceTest.java`, `indicators/IndicatorsIT.java`, `RoleMatrixIT`,
  `OpenApiDriftIT`.

**Interfaces:**
- Consumes: `IndicatorRepository`, `IndicatorSeries` (Task 3), domínio (Task 1),
  `@WorkspaceScoped`, `@RequiresModule(ModuleKey.INDICATORS)`.
- Produces (o web usa pelo `schema.ts`):
  - `IndicatorsView(List<IndicatorView> indicators)`;
  - `IndicatorView(String code, String name, String unit, @Nullable String value, @Nullable LocalDate referenceDate, @Nullable VariationView variation, SourceView source, @Nullable Instant updatedAt, boolean stale)`;
  - `VariationView(String value, String unit, String basis, LocalDate since)`;
  - `SourceView(String name, @Nullable String url)`;
  - `IndicatorService.list(): IndicatorsView`.

**Como fica:**
- `GET /v1/workspaces/{ws}/indicators` com `@RequiresModule(ModuleKey.INDICATORS)` e
  `@WorkspaceScoped(allow = {OWNER, ADULT, VIEWER, YOUTH})`, como as rotas do Planejamento.
- O serviço lê `allSeries()`, pega `recentPoints(code, 400)` para `SELIC_TARGET` e
  `recentPoints(code, 2)` para as outras, e monta D6. `source.name` é sempre
  "Banco Central do Brasil".
- `IndicatorsOpenApiConfig` marca os campos anuláveis, como o `PlanningOpenApiConfig`.
- Regerar o contrato: `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` em
  `api/`, depois `corepack pnpm --dir web gen:api`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `IndicatorServiceTest.fiveCardsInEnumOrder` | sempre cinco, na ordem de D1 |
| `IndicatorServiceTest.noPointMeansNulls` | série sem ponto → `value`, `referenceDate`, `variation`, `source.url` nulos; `stale = true` |
| `IndicatorServiceTest.roundsHalfUp` | 15.005 → `"15.01"`; dólar 5.43215 → `"5.4322"` |
| `IndicatorServiceTest.selicVariationIsTheLastChange` | pontos com mudança em 2026-11-05 → `{ "0.50", PP, LAST_CHANGE, 2026-11-05 }` |
| `IndicatorServiceTest.dollarVariationInPercent` | 5.4321 → 5.4100 → `{ "-0.41", PERCENT, PREVIOUS, data do anterior }` |
| `IndicatorServiceTest.staleFollowsTheClock` | `Clock` fixo, sucesso há 4 dias úteis → `stale = true` |
| `IndicatorsIT.moduleOnReturnsCards` | módulo ligado → 200 com cinco itens |
| `IndicatorsIT.moduleOffIs403` | módulo desligado → 403, `code = module.disabled` |
| `IndicatorsIT.otherFamilyIs404` | família alheia → 404 |
| `RoleMatrixIT` | `GET W/indicators`: os quatro papéis → 200 (módulo ligado no preparo) |
| `OpenApiDriftIT` | passa com o `openapi.json` regerado |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*IndicatorServiceTest' --tests '*IndicatorsIT' --tests '*RoleMatrixIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D6 e D16.

- [ ] **Step 4: Regerar o contrato e o `schema.ts`.**

- [ ] **Step 5: Rodar e ver passar.** Mesmo comando, mais `--tests '*OpenApiDriftIT'`.
  Expected: PASS.

- [ ] **Step 6: Conferir.** `./gradlew build` e `corepack pnpm --dir web typecheck` → ambos passam.

- [ ] **Step 7: Commit**

```bash
git add api web/src/shared/api/schema.ts
git commit -m "feat(indicators): add the read route with variation, source and staleness"
```

---

### Task 5: Alarmes no CloudWatch

**Files:**
- Create: `infra/live/indicator_alerts.tf`.
- Modify: `infra/live/billing_alerts.tf` (o mapa vira `local.api_log_groups`),
  `infra/live/tests/operations.tftest.hcl`.

**Interfaces:**
- Consumes: `aws_cloudwatch_log_group.main`, `aws_sns_topic.alerts`, as linhas de log da Task 3.
- Produces: `aws_cloudwatch_log_metric_filter.indicator_alert` e
  `aws_cloudwatch_metric_alarm.indicator_alert` (staging e produção);
  `aws_cloudwatch_log_metric_filter.indicator_run` e
  `aws_cloudwatch_metric_alarm.indicator_run_missing` (só produção).

**Como fica:** D7. O nome dos alarmes segue a cobrança: `liberdade-indicator-alert-{env}` e
`liberdade-indicator-run-missing-production`. As descrições dizem o que conferir ("check the logs for
the series and the failure kind" / "no indicator run logged for 24 hours").

- [ ] **Step 1: Escrever os testes (falhando)** em `operations.tftest.hcl`, no estilo dos
  `billing_alert_filters` e `billing_alarms_notify_alerts`:

| Run | Esperado |
|---|---|
| `indicator_alert_filters` | dois filtros, nos grupos de staging e produção, com padrão `"\"INDICATOR_ALERT\""` |
| `indicator_alarms_notify_alerts` | dois alarmes; `alarm_actions` e `ok_actions` = o tópico `alerts`; a métrica do alarme = a do filtro |
| `indicator_run_missing_only_production` | um filtro e um alarme, no grupo de produção; `period = 3600`, `evaluation_periods = 24`, `treat_missing_data = "breaching"`, `comparison_operator = "LessThanThreshold"`, `threshold = 1` |
| `alarms_cover_the_basics` (o que já existe) | continua passando com os alarmes novos |

- [ ] **Step 2: Rodar e ver falhar.** Sem `tofu` local, o implementador confere a sintaxe com leitura
  cuidadosa e deixa o CI ser o juiz (`tofu fmt -check`, `tofu validate`, `tofu test` em
  `infra/live`). Com `tofu` local: `tofu init -backend=false && tofu test` em `infra/live` → FAIL.

- [ ] **Step 3: Implementar.** Renomear o mapa e criar o arquivo novo.

- [ ] **Step 4: Rodar e ver passar** (com `tofu` local, ou no CI do PR). Expected: PASS e
  `tofu fmt -check -recursive infra` sem diferença.

- [ ] **Step 5: Commit**

```bash
git add infra
git commit -m "feat(infra): alarm on indicator alerts and on a missing indicator run"
```

---

### Task 6: `SourceStamp` e `Disclaimer` no `shared/ui`

**Files:**
- Create: `shared/ui/SourceStamp.tsx`, `shared/ui/SourceStamp.test.tsx`,
  `shared/ui/Disclaimer.tsx`, `shared/ui/Disclaimer.test.tsx`.
- Modify: `shared/ui/index.ts` e as mensagens do `shared/ui` (onde o `DataAsOf` guarda as dele).

**Interfaces:**
- Consumes: nada novo.
- Produces:
  - `SourceStamp(props: { source: string; url?: string | null; updatedAt: string | null; stale: boolean })`;
  - `Disclaimer(props: { children: ReactNode })`.

**Como fica:** D10. Textos: `sourceStamp.updated` = "Fonte: {source} · atualizado em {date}",
`sourceStamp.never` = "Fonte: {source} · ainda sem atualização", `sourceStamp.stale` =
"desatualizado". O selo usa as cores neutras do design system com texto, nunca só cor.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `SourceStamp › showsSourceAndSaoPauloDate` | `updatedAt = '2026-11-25T02:30:00Z'` → "atualizado em 24/11/2026" |
| `SourceStamp › neverUpdated` | `updatedAt = null` → "ainda sem atualização" |
| `SourceStamp › staleBadgeIsText` | `stale` → o texto "desatualizado" visível |
| `SourceStamp › linksOnlyHttps` | `url = 'https://api.bcb.gov.br/x'` → link com `target="_blank"` e `rel="noopener noreferrer"`; `url = 'fake:CDI'` → sem link |
| `Disclaimer › isANote` | `role="note"` com o texto filho |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/shared/ui/SourceStamp.test.tsx src/shared/ui/Disclaimer.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implementar.**

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` → passa.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the source stamp and the disclaimer components"
```

---

### Task 7: A tela dos indicadores, o `ModuleRoute` e o item em Mais

**Files:**
- Create: `app/ModuleRoute.tsx`, `app/ModuleRoute.test.tsx`,
  `features/indicators/{register.ts,messages.pt-BR.ts,useIndicators.ts,format.ts,format.test.ts,IndicatorsPage.tsx,IndicatorsPage.test.tsx,IndicatorCard.tsx}`,
  `mocks/indicatorHandlers.test.ts`.
- Modify: `app/routes.tsx`, `app/MorePage.tsx` (e seu teste), `mocks/handlers.ts`, `mocks/data.ts`.
- Delete: `app/PlanningModuleRoute.tsx` (e seu teste vira o do `ModuleRoute`).

**Interfaces:**
- Consumes: o contrato da Task 4 (`schema.ts`), `SourceStamp` e `Disclaimer` (Task 6), o hook de
  módulos existente.
- Produces (a Task 8 usa):
  - `ModuleRoute(props: { module: ModuleKey; children: ReactNode })`;
  - `useIndicators(workspaceId: string | undefined)` → a consulta de `IndicatorsView`;
  - `formatDecimal(text: string): string` (`'15.00'` → `'15,00'`, `'-0.25'` → `'−0,25'`);
  - `formatIndicatorValue(view: IndicatorView): string | null`;
  - `formatVariation(variation: VariationView): string`.

**Como fica:**
- Valor por unidade: `PERCENT_YEAR` → "15,00% a.a."; `PERCENT_MONTH` → "0,50% no mês";
  `PERCENT_12M` → "4,00% em 12 meses"; `BRL` → "R$ 5,4100".
- Variação: `LAST_CHANGE` → "+0,50 p.p. em 05/11"; `PREVIOUS` → "−0,10 p.p. em relação a 01/10" ou
  "−0,41% em relação a 21/11". Positivo ganha "+", zero fica "0,00".
- Data de referência: séries diárias "dado de 24/11/2026"; mensais "dado de outubro de 2026".
- A tela: `Disclaimer` no topo com "Conteúdo educativo. Não é recomendação de investimento.", título
  "Indicadores", os cinco cards e o link "Calcular rendimento real" para a calculadora. Carregando,
  esqueleto; erro, a mensagem padrão de erro de carregamento com "Tentar de novo".
- MSW: `GET */v1/workspaces/:ws/indicators` responde 403 `module.disabled` quando
  `mockState.modules[ws]` não tem `indicators`, senão os cinco cards de `mockState.indicators`
  (mesmos valores da fonte falsa).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ModuleRoute › rendersWhenOn` / `redirectsWhenOff` / `rendersNothingWhilePending` | os três casos do `PlanningModuleRoute`, agora com `module` |
| os testes das rotas do Planejamento | seguem passando sem mudança de asserção |
| `format.test.ts › decimalUsesCommaAndMinusSign` | `'-0.25'` → `'−0,25'` |
| `format.test.ts › valueByUnit` | as quatro unidades acima |
| `format.test.ts › variationByBasis` | os três textos acima, com "+" no positivo |
| `IndicatorsPage › everyCardHasASourceStamp` | cinco cards, cada um com "Fonte: Banco Central do Brasil" |
| `IndicatorsPage › showsTheDisclaimer` | `role="note"` com o texto da CVM |
| `IndicatorsPage › staleCardKeepsTheValue` | card com `stale` → valor e "desatualizado" |
| `IndicatorsPage › cardWithoutPoint` | `value = null` → "Sem dados ainda" |
| `IndicatorsPage › variationIsNeutral` | o texto da variação não tem classe de cor de perigo nem de sucesso |
| `MorePage › indicatorsItemWhenOn` | módulo ligado → link "Indicadores" para `/mais/indicadores`, também para o Jovem |
| `MorePage › noIndicatorsItemWhenOff` | desligado → sem o link |
| `indicatorHandlers.test.ts › offIs403` / `onReturnsFive` | o handler responde como a API |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/app src/features/indicators src/mocks/indicatorHandlers.test.ts`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D8 e D9.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` e
  `corepack pnpm --dir web build && corepack pnpm --dir web check:chunks` → passam, e o
  carregamento inicial não cresce (198,5 KB ou menos).

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the indicators screen behind its module"
```

---

### Task 8: A calculadora de rendimento real

**Files:**
- Create: `features/indicators/realReturn.ts`, `realReturn.test.ts`, `RealReturnPage.tsx`,
  `RealReturnPage.test.tsx`.
- Modify: `app/routes.tsx` (rota `/mais/indicadores/calculadora`), `features/indicators/messages.pt-BR.ts`.

**Interfaces:**
- Consumes: `useIndicators`, `formatDecimal` (Task 7), `SourceStamp`, `Disclaimer` (Task 6).
- Produces: `parseRate(text: string): bigint | null`,
  `realReturn(nominal: bigint, inflation: bigint): bigint`, `formatRate(hundredths: bigint): string`.

**Como fica:** D11. Dois campos ("Rendimento ao ano (%)" e "Inflação em 12 meses (%)"),
`inputMode="decimal"`, o resultado num `output` com `aria-live="polite"`. Sem botão de calcular: o
resultado aparece quando os dois campos são válidos.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `realReturn.test.ts › parsesCommaAndDot` | `'10'` → `100000n`; `'4,5'` → `45000n`; `'4.5'` → `45000n`; `'-1,25'` → `-12500n` |
| `realReturn.test.ts › rejectsGarbage` | `''`, `'abc'`, `'1,23456'`, `'1,2,3'` → `null` |
| `realReturn.test.ts › specExample` | `realReturn(100000n, 40000n)` → `577n` |
| `realReturn.test.ts › belowInflationIsNegative` | `realReturn(30000n, 40000n)` → `-96n` |
| `realReturn.test.ts › equalIsZero` | `realReturn(40000n, 40000n)` → `0n` |
| `realReturn.test.ts › roundsHalfAwayFromZero` | um par que cai exatamente em ,xx5 positivo e negativo arredonda para longe do zero (o implementador escolhe o par e mostra a conta no teste) |
| `realReturn.test.ts › formats` | `577n` → `'5,77'`; `-96n` → `'−0,96'`; `0n` → `'0,00'`; `123456n` → `'1234,56'` |
| `RealReturnPage › showsTheDisclaimer` | `role="note"` com o texto da CVM |
| `RealReturnPage › prefillsIpcaWithStamp` | IPCA 12 meses `"4.00"` → campo `4,00` e "Fonte: Banco Central do Brasil" |
| `RealReturnPage › editingHidesTheStamp` | troca o IPCA → sem selo, "valor informado por você" |
| `RealReturnPage › computesTheExample` | digita `10` → "5,77%" e a conta "(1 + 10,00%) ÷ (1 + 4,00%) − 1 = 5,77%" |
| `RealReturnPage › belowInflationWithoutAlarm` | `3` → "−0,96%" e "abaixo da inflação"; sem `role="alert"` |
| `RealReturnPage › outOfRangeError` | `1001` → erro no campo, ligado por `aria-describedby`; sem resultado |
| `RealReturnPage › noIpcaLeavesFieldEmpty` | `IPCA_12M` sem ponto → campo vazio, sem selo |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/features/indicators`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga D11.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check`, `build` e `check:chunks` → passam.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the real return calculator"
```

---

### Task 9: Página do robô, política de privacidade e docs

**Files:**
- Create: `features/legal/BotPage.tsx`, `features/legal/BotPage.test.tsx`,
  `features/legal/texts/privacy-2026-11.3.ts`, `features/legal/texts/privacy-2026-12.3.ts`,
  `docs/runbooks/indicator-alerts.md`.
- Modify: `app/routes.tsx` (rota pública `/robo`), as mensagens do `legal`, `legalConfig.ts`,
  `legalTexts.test.ts`, `DocumentsGate.test.tsx`, `PolicyUpdatedNotice.test.tsx`,
  `members/AcceptInvitationPage.test.tsx`, `privacy/internal/LegalDocuments.java`,
  `privacy/DocumentsBillingIT.java`, `privacy/LegalCurrentIT.java`, `privacy/LegalDocumentsIT.java`,
  `docs/architecture.md`, `docs/data-model.md`, `docs/design-system.md`, `docs/roadmap.md` (se
  preciso).

**Interfaces:**
- Consumes: `bot-url` (D2) aponta para `/robo`.
- Produces: as versões `privacy-2026-11.3` e `privacy-2026-12.3` em vigor (sem novo aceite).

**Como fica:**
- `/robo` (D12), pública e sob demanda, com título "Robô de indicadores do Liberdade" e as quatro
  respostas de D12. Fica fora de Mais; o link é o do `User-Agent` e um "Como coletamos" no rodapé
  da tela dos indicadores.
- Política (D13): os textos novos copiam os anteriores e mudam só a lista do que o app faz com dado
  público e a data. `WITHOUT_BILLING` passa a `privacy-2026-11.3`, `WITH_BILLING` a
  `privacy-2026-12.3`, e o `KEEPS_ACCEPTANCE` ganha os dois elos. Quem aceitou a 11.2 ou a 12.2 vê o
  aviso de política atualizada e não precisa aceitar de novo.
- Docs (D14). O runbook diz o que o e-mail significa (`FETCH_FAILED` ou `STALE`, a série), onde ler o
  log, quando esperar (o Banco Central às vezes fica fora por horas; `STALE` só depois de 3 dias
  úteis) e os dois jeitos de forçar uma falha no staging (D7).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `BotPage › explainsTheRobot` | cita `api.bcb.gov.br`, `olinda.bcb.gov.br`, "8h30" e "19h30" |
| `legalTexts.test.ts` | as versões novas existem e têm o parágrafo do Banco Central |
| `DocumentsGate.test.tsx` / `PolicyUpdatedNotice.test.tsx` | quem aceitou a 12.2 vê o aviso, sem novo aceite |
| `LegalDocumentsIT` / `LegalCurrentIT` / `DocumentsBillingIT` | as versões em vigor são as `.3`; aceite da `.2` continua valendo |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --dir web test -- src/features/legal src/features/members` e (em `api/`)
`./gradlew test --tests '*Legal*' --tests '*DocumentsBillingIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar** a página, os textos, a cadeia e os docs.

- [ ] **Step 4: Rodar e ver passar.** Mesmos comandos. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` e `corepack pnpm --dir web check` → passam.

- [ ] **Step 6: Commit**

```bash
git add api web docs
git commit -m "feat: explain the indicator robot and note it in the privacy policy"
```

---

### Task 10: E2E, acessibilidade e bundle

**Files:**
- Create: `web/e2e/indicators.spec.ts`.
- Modify: `web/e2e/a11y.spec.ts`.

**Interfaces:**
- Consumes: tudo das Tasks 1 a 9; a fonte falsa enche os dados na subida do e2e (D4).

**Como fica:** D15. O teste entra como Dono, liga Indicadores em Mais › Módulos, abre Mais ›
Indicadores e espera os cinco cards (a coleta de partida pode levar alguns segundos depois da subida:
usar a espera padrão do Playwright pelo texto, não `waitForTimeout`).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `indicators.spec.ts › fiveCardsWithSource` | cinco cards; cada um com "Fonte: Banco Central do Brasil" e "atualizado em"; Selic "15,00% a.a." |
| `indicators.spec.ts › disclaimerOnBothScreens` | o texto da CVM na tela e na calculadora |
| `indicators.spec.ts › calculatorExample` | IPCA preenchido com 4,00; digita 10 → "5,77%" |
| `indicators.spec.ts › offHidesTheItem` | desliga o módulo → sem item em Mais; `/mais/indicadores` volta para `/inicio` |
| `a11y.spec.ts` | axe sem violações em `/mais/indicadores`, na calculadora e em `/robo` |
| `core-without-modules` (o que já existe) | continua passando |

- [ ] **Step 2: Rodar e ver falhar.** O e2e roda no compose de e2e, como na 7.3 (o mesmo comando do
  CI em `web/`). Expected: FAIL nos testes novos.

- [ ] **Step 3: Ajustar** o que o e2e mostrar (nomes acessíveis, esperas).

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS em todo o e2e.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web build && corepack pnpm --dir web check:chunks`
  → o carregamento inicial não passa de 198,5 KB. Anotar o número no relatório.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "test(e2e): cover the indicators, the calculator and their accessibility"
```
