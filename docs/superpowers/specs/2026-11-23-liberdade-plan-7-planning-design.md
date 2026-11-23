# Plano 7 — Planejamento (v0.2): design

> **Status:** aprovado pelo mantenedor em 2026-11-23 · **Data:** 2026-11-23
>
> Base: `main` em 7ee56ec, já com o Plano 6 ([PR #44](https://github.com/warthogozz-cyber/liberdade/pull/44)),
> [PRD US-20 a US-29](../../prd.md#v02--planejamento),
> [regras de cálculo 7.3 a 7.5](../../prd.md#7-regras-de-cálculo), [modelo de dados §3](../../data-model.md#3-v02--planejamento),
> [design system §5](../../design-system.md#5-movimento-e-som) e [ADR-0015](../../adr/0015-revenue-model-and-billing.md).
> As decisões de produto vieram do mantenedor, uma a uma, em 2026-11-23. O que está marcado como
> **(decisão do design)** é escolha minha, para o mantenedor conferir ao ler.

## 1. Intenção e porquê

A v0.1 responde "para onde foi o dinheiro". A v0.2 responde "para onde ele vai": quanto ainda cabe
no mês, quanto falta para a meta, quantos meses a reserva aguenta e como o saldo deve estar daqui a
três meses. É também a primeira versão com algo que o plano Família promete (metas ilimitadas), e
por isso dá sentido à pré-venda Fundadora do Plano 6.

O roadmap diz quando a v0.2 sai: **os exemplos das seções 7.3 a 7.5 do PRD passam como teste e a
família fecha um mês com orçamento.**

Junto vêm três coisas que preparam o futuro sem mudar a cara do app: o modelo pronto para outros
países (US-27), a sincronização ao vivo (US-25) e a base de movimento e som (US-28).

## 2. Objetivos, não-objetivos e antiobjetivos

**Objetivos**
- A família define um limite por categoria no mês, copia do mês anterior e acompanha o uso com
  avisos gentis a 80% e a 100% (US-20).
- A família cria metas, guarda dinheiro nelas (à mão ou por uma conta vinculada) e vê o aporte
  mensal sugerido (US-21).
- A família marca contas de reserva e vê quantos meses de gastos elas cobrem (US-22).
- Regras de recorrência geram lançamentos previstos, que alguém confirma com um toque (US-23).
- Tags livres e forma de pagamento nos lançamentos, com filtros (US-24, US-26).
- O painel mostra o saldo previsto para o fim do mês e para os 3 meses seguintes, e separa gastos
  fixos e variáveis (US-29).
- Um lançamento feito em outro aparelho aparece em poucos segundos, sem recarregar (US-25).
- País, moeda e idioma ficam no modelo, para outros países não exigirem migração de dados (US-27).
- Som opcional para conquistas e a base de movimento do design system (US-28).

**Não-objetivos (ficam para depois)**
- Mensal de R$ 14,90 e anual de R$ 119: abrem no Plano 8 ou depois, junto com a importação de
  extrato (decisão 3). A pré-venda Fundadora continua aberta.
- Notificação push, e-mail ou qualquer aviso fora do app (decisão 4).
- Interface em outro idioma ou moeda, conversão de câmbio, formas de pagamento de outros países na
  tela (Fase 6).
- Orçamento por membro, por conta ou por tag; orçamento de receitas.
- Indicadores, conquistas, mesada e missões (v0.3).

**Antiobjetivos (o Plano 7 falhou, mesmo funcionando, se...)**
- ...o app lançar sozinho um dinheiro que não saiu. Previsto só vira lançamento quando alguém
  confirma.
- ...o vermelho aparecer antes de 100%, ou um aviso de orçamento soar, vibrar ou usar tom de bronca.
- ...um gasto ou um orçamento estourado for celebrado.
- ...o Jovem receber, pela sincronização ao vivo ou por qualquer rota nova, algo de uma conta que
  ele não vê.
- ...a projeção aparecer como certeza, sem "estimativa" e sem a conta explicada.
- ...desligar o módulo Planejamento apagar dados, ou o núcleo deixar de funcionar com ele desligado
  (critério S2).
- ...o limite de metas do Grátis virar pressão: sem contagem, sem "última meta grátis", sem bloquear
  metas que já existem.

## 3. Restrições

- **Dinheiro:** `Money` e aritmética decimal exata (ADR-0006). As regras 7.3 a 7.5 do PRD valem
  como estão, com seus arredondamentos, e cada exemplo vira teste.
- **Isolamento por família** (ADR-0008): FKs compostas (`workspace_id`, `id`) em todas as tabelas
  novas; toda consulta filtra por família.
- **Visibilidade do Jovem** (US-10, D13): a regra única é `AccountScope.sqlTouches`. Toda rota
  nova e todo evento ao vivo passa por ela ou não chega ao Jovem.
- **Back:** Spring Modulith (ADR-0003), JdbcClient com SQL explícito (ADR-0012), erros em RFC 9457,
  Flyway só para frente.
- **Front:** bundle inicial ≤ 200 KB (`check:chunks`). Telas novas e o som carregam sob demanda.
  Nenhuma dependência nova sem justificar no plano.
- **Movimento e som:** design system §5 (150/200/250 ms, `ease-out`, só `transform` e `opacity`,
  `prefers-reduced-motion`, som desligado por padrão, Web Audio, até 300 ms).
- **LGPD:** os dados novos entram na exportação e saem na exclusão da família; nada novo vai para
  terceiros.
- **Infra:** uma instância por ambiente atrás do Caddy (Plano 5). Nada de serviço novo na AWS.
- **CVM:** a projeção é uma estimativa de caixa da própria família, não recomendação de
  investimento. O texto não sugere aplicar em nada.

## 4. Abordagens consideradas

**Recorrências (US-23)**

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **Gerar com antecedência** | O servidor cria lançamentos `PLANNED` até o fim do mês seguinte, na criação ou edição da regra e num job diário; confirmar troca o status | Segue o modelo de dados já escrito; previsto é um lançamento comum para a lista, os filtros e a visibilidade do Jovem. Editar uma regra precisa refazer os previstos ainda abertos | **Escolhida pelo mantenedor** |
| Previstos virtuais | Nada é gravado até confirmar; o servidor calcula as ocorrências a cada leitura | Editar regra é trivial, mas lista, filtros e visibilidade ganham dois caminhos, e o modelo muda | Descartada |

**Sincronização ao vivo (US-25)**

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **SSE com aviso e nova busca** | Depois do commit, o servidor manda só "mudou X", filtrado por quem pode ver; o app busca de novo pelas rotas atuais | Permissões continuam num lugar só. Em processo, porque há uma instância; um ajuste no Caddy | **Escolhida pelo mantenedor** |
| Consulta periódica | O app pergunta a cada ~15 s, com ETag | Sem infra nova, mas gasta bateria e requisições e atrasa o destaque | Descartada |
| LISTEN/NOTIFY do Postgres | Fan-out pelo banco | Só compensa com várias instâncias. Fica como troca futura por trás da mesma interface | Descartada agora |

## 5. Decisões de produto (do mantenedor)

1. **Um spec, três entregas em sequência**, cada uma com plano e PR próprios:
   - **7.1 Base de dados:** país, moeda e idioma (US-27), forma de pagamento (US-26), tags (US-24)
     e recorrências (US-23).
   - **7.2 Planejamento:** orçamento (US-20), metas (US-21), reserva (US-22), projeção e
     fixos/variáveis (US-29).
   - **7.3 Ao vivo, movimento e som:** sincronização (US-25), som (US-28) e a base de movimento.
2. **O Grátis tem até 3 metas ativas.** Meta concluída ou arquivada não conta, e a reserva de
   emergência não é meta. Família e Fundadora: sem limite. Com a cobrança desligada no ambiente,
   não há limite.
3. **Mensal e anual não abrem no Plano 7.** Ficam para o Plano 8 ou depois, com a importação de
   extrato. A pré-venda Fundadora continua aberta.
4. **Avisos de orçamento só dentro do app.** Sem push e sem e-mail.
5. **Módulo Planejamento ligado por padrão**, para famílias novas e atuais. Dentro dele: orçamento,
   metas, reserva, recorrências, projeção e fixos/variáveis. No núcleo, sempre ligados: tags, forma
   de pagamento, país/moeda/idioma, sincronização ao vivo, som e movimento.
6. **Recorrências geradas com antecedência** (seção 4).
7. **Sincronização por SSE com aviso e nova busca** (seção 4).
8. **A projeção usa a média de todo gasto que não veio de uma recorrência**, fixo ou variável. Assim
   um gasto fixo pago à mão, sem regra (a escola, por exemplo), não some da conta e o saldo previsto
   não sai otimista. Fixo e variável continuam separados no painel, só para leitura. O PRD (US-29)
   foi ajustado para dizer isso.

## 6. Desenho

### 6.1 Módulos e onde cada coisa mora

- **`ledger`** (núcleo) ganha: forma de pagamento, tags, status `PLANNED`/`CONFIRMED`,
  `recurring_rule_id` e `categories.is_fixed`. O status e o vínculo com a regra são colunas do
  lançamento, então moram aqui.
- **`planning`** (módulo novo, depende de `ledger` e de `workspace`; de `billing` só pela interface
  `Plans`): regras de recorrência e o job que gera previstos, orçamentos, metas e aportes, reserva e
  projeção. Toda rota dele leva `@RequiresModule(PLANNING)`.
- **`workspace`** ganha `country` e `locale` e os campos da reserva (`reserve_target_months`,
  `manual_monthly_cost`). Os campos da reserva são lidos e gravados pelas rotas do `planning`
  **(decisão do design)**: ficam em `workspaces` porque o modelo de dados já os pôs lá.
- **Ao vivo:** um componente `live` no núcleo (pacote próprio no `shared` ou módulo `live`; o
  construtor escolhe) com o registro de streams e o endpoint SSE. Os módulos publicam eventos de
  domínio; ele escuta depois do commit.

**Navegação.** A 7.2 põe o item **Planejar** na barra de baixo, entre "+ Novo gasto" e "Mais", como o
design system §4 já prevê (Início, Lançamentos, + Lançar, Planejar, Mais). Planejar abre uma tela com
Orçamento, Metas, Reserva e Recorrências. O Início ganha cards de resumo (orçamento, reserva,
projeção e fixos/variáveis), cada um levando à sua tela. Com o módulo desligado, o item e os cards
somem e a barra volta aos quatro itens de hoje. Na 7.1, enquanto Planejar não existe, Recorrências
fica em Mais e muda de lugar na 7.2 **(decisão do design)**.

**Módulo ligado por padrão.** A regra de hoje ("sem linha, desligado") continua. A migração da 7.1
insere `('planning', enabled = true)` para todas as famílias, e a criação de família passa a inserir
a mesma linha. **Desligar** esconde menus, cards e avisos do planejamento, para o job de gerar
previstos daquela família e esconde da lista os lançamentos `PLANNED` (que já não contam em saldo
nenhum). Nada é apagado; religar traz tudo de volta e o job completa o horizonte na hora
**(decisão do design)**.

### 6.2 Modelo de dados

**7.1 — `V15__country_tags_payment.sql`**
- `workspaces.country char(2) NOT NULL DEFAULT 'BR' CHECK (country IN ('BR'))` e
  `workspaces.locale text NOT NULL DEFAULT 'pt-BR' CHECK (locale IN ('pt-BR'))`. A `base_currency`
  e a `currency` de contas e lançamentos já existem. Novos países ampliam os `CHECK` por migração.
- `transactions.payment_method text NULL CHECK (payment_method IN ('PIX', 'CREDIT_CARD',
  'DEBIT_CARD', 'BOLETO', 'BANK_TRANSFER', 'CASH'))`, sempre nulo em `TRANSFER`. Os códigos são
  neutros de país; a lista por país vive no código (`PaymentMethods.forCountry("BR")`), e a tela
  mostra só a do Brasil: Pix, crédito, débito, boleto, TED e dinheiro **(decisão do design)**.
- `tags (id, workspace_id, name, created_by, created_at, archived_at)`, nome com 1 a 30 caracteres,
  único por família sem diferenciar maiúsculas entre as não arquivadas
  (`UNIQUE (workspace_id, lower(name)) WHERE archived_at IS NULL`).
- `transaction_tags (workspace_id, transaction_id, tag_id)`, PK das três colunas, FKs compostas,
  `ON DELETE CASCADE` dos dois lados. Até 5 tags por lançamento e 200 tags ativas por família
  **(decisão do design)**.

**7.1 — `V16__recurrences.sql`**
- `recurring_rules (id, workspace_id, kind, amount, currency, account_id, to_account_id,
  category_id, payment_method, description, frequency, interval, start_on, end_on, next_due_on,
  created_by, created_at, updated_at, archived_at, version)`. As mesmas regras de consistência de
  `transactions` (transferência ⇔ destino e sem categoria; moeda igual à da conta). `frequency` em
  `WEEKLY | MONTHLY | YEARLY`, `interval` de 1 a 12.
- `transactions.status text NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'PLANNED'))`,
  `transactions.recurring_rule_id uuid NULL` (FK composta, `ON DELETE SET NULL`) e
  `transactions.scheduled_on date NULL`: a data original da ocorrência, que nunca muda, mesmo que a
  data do lançamento seja ajustada ao confirmar. `CHECK ((recurring_rule_id IS NULL) = (scheduled_on
  IS NULL))` **(decisão do design)**.
- Índice único `(workspace_id, recurring_rule_id, scheduled_on) WHERE recurring_rule_id IS NOT NULL`,
  **incluindo** as linhas excluídas: pular uma ocorrência (excluir o previsto) ou confirmá-la com
  outra data nunca faz o job recriá-la.
- Insere a linha do módulo `planning` ligada para toda família existente.

**7.2 — `V17__planning.sql`**
- `budgets` como no modelo de dados (§3), só para categorias `EXPENSE`.
- `goals (id, workspace_id, name, target_amount, currency, due_on, icon, linked_account_id,
  achieved_at, archived_at, created_by, created_at, updated_at, version)`. Nome de 1 a 40
  caracteres; `icon` de uma lista fixa de chaves no código.
- `goal_contributions (id, workspace_id, goal_id, amount, occurred_on, member_id, created_at)`.
  `amount <> 0`: negativo é retirada. Só para metas sem conta vinculada.
- `accounts.is_reserve boolean NOT NULL DEFAULT false`;
  `workspaces.reserve_target_months smallint NOT NULL DEFAULT 6 CHECK (BETWEEN 1 AND 24)`;
  `workspaces.manual_monthly_cost numeric(19,4) NULL CHECK (> 0)`.
- `categories.is_fixed boolean NOT NULL DEFAULT false` (só faz sentido em `EXPENSE`; a subcategoria
  herda o valor da mãe quando a mãe é fixa).

`PLANNED` não entra em saldo, painel, orçamento, reserva nem Retrato do mês até ser confirmado.

### 6.3 7.1 — Forma de pagamento, tags e país

- **Forma de pagamento:** campo opcional no formulário de receita e gasto (some em transferência),
  com chips. Filtro na lista de lançamentos e no painel (o painel recalcula receitas, gastos e
  categorias só com aquela forma).
- **Tags:** campo com sugestões das tags da família; digitar um nome novo cria a tag. Filtro por tag
  na lista. Dono e Adulto renomeiam e arquivam tags em Mais > Categorias (uma aba "Tags")
  **(decisão do design)**.
- **Tags e o Jovem:** o Jovem põe tags nos próprios lançamentos e pode criar tags. A lista de tags
  que ele recebe traz só as que estão em lançamentos que ele vê ou que ele criou, para um nome de
  tag não vazar o que acontece em outras contas **(decisão do design)**. Se o Jovem digitar um nome
  que já existe numa tag que ele não vê, o servidor usa essa tag em silêncio, sem erro. Ele só
  descobre que um nome que ele mesmo digitou existe, o que é aceitável **(decisão do design)**.
- **País, moeda e idioma:** saem na rota da família (só leitura) e na exportação. A tela não muda.
  O front formata com o `locale` e a `base_currency` vindos da API em vez de constantes, para a
  Fase 6 não ter de caçar `'pt-BR'` no código **(decisão do design)**.

### 6.4 7.1 — Recorrências

- **Quem:** Dono e Adulto criam, editam e arquivam regras. O Jovem vê os previstos das contas em que
  é titular (pela regra única) e confirma esses. Leitor vê.
  Jovem: previsto de transferência que envolve conta de outro membro é só leitura.
- **Tela:** Recorrências (lista de regras, criar e editar), em Mais na 7.1 e em Planejar a partir da
  7.2 (seção 6.1). Na lista de lançamentos, os
  previstos aparecem numa seção "Previstos" no topo do mês, com data, valor e o botão "Confirmar".
  Previsto vencido continua lá com "venceu em dd/mm" até alguém confirmar ou pular.
- **Gerar:** ao criar ou editar a regra, e num job diário às 4h10 (`America/Sao_Paulo`), o servidor
  cria `PLANNED` de `next_due_on` até o último dia do mês seguinte ao de hoje, avança
  `next_due_on` e respeita `end_on`. Idempotente pelo índice único. Mensal no dia 31 cai no último
  dia dos meses mais curtos; anual em 29/02 cai em 28/02 nos anos comuns.
- **Regra que começa no passado:** a primeira geração parte do maior entre `start_on` e o dia 1º do
  mês atual. Uma regra criada em 23/11 com início em janeiro gera novembro e dezembro, não onze
  previstos vencidos. Editar a regra parte do primeiro dia da nova agenda a partir de hoje, para não
  criar um segundo previsto no mês **(decisão do design)**.
- **Autor do previsto:** o job grava `created_by` e `member_id` de quem criou a regra. Se essa pessoa
  saiu da família, usa o Dono. Ao confirmar, `member_id` passa a ser de quem confirmou, porque é
  essa pessoa que diz "esse dinheiro saiu"; `created_by` não muda **(decisão do design)**.
- **Confirmar:** rota própria, `POST /v1/workspaces/{ws}/transactions/{id}/confirm`, com o `version`
  e ajustes opcionais de valor, data e conta (a conta de luz mudou de valor); o ajuste vale só para
  aquele lançamento. Quem pode: Dono e Adulto em qualquer previsto que veem, e o Jovem nos previstos
  das contas em que é titular, mesmo que a regra seja de outra pessoa. Leitor não confirma. Depois de
  confirmado, valem as regras de editar de hoje **(decisão do design)**.
- **Pular:** rota própria, `POST .../{id}/skip`, com as mesmas permissões de confirmar. É uma exclusão
  lógica, com "Desfazer" como hoje.
- **Editar a regra:** apaga de vez os `PLANNED` ainda abertos daquela regra com `scheduled_on` a
  partir de hoje e gera de novo com o molde novo. Confirmados, pulados e vencidos não mudam; como o
  índice é por `scheduled_on`, uma ocorrência já confirmada ou pulada não volta. Arquivar a regra
  apaga de vez os previstos futuros abertos.
- **Sem conexão:** confirmar e pular exigem conexão, como editar e excluir hoje (PRD, US-02). Os
  botões ficam desativados offline, com a explicação de sempre. Se outra pessoa mexeu no previsto
  antes, a confirmação recebe `404` ou `409` e o app avisa "Esse previsto mudou. Confira a lista."

### 6.5 7.2 — Orçamento

- **Quem:** Dono e Adulto definem limites; Leitor vê; o Jovem não vê orçamento (US-10).
- **Tela:** Planejar > Orçamento, com seletor de mês, e um card de resumo no Início. Uma linha por
  categoria com limite:
  nome, barra, "R$ 960,00 de R$ 1.200,00" e o uso em %. Botão "Copiar do mês anterior", que copia
  só as categorias sem limite no mês de destino.
- **Conta:** regra 7.3 do PRD. O gasto da categoria soma os `EXPENSE` `CONFIRMED` do mês, de todas
  as contas, **incluindo as subcategorias** quando o limite é da categoria-mãe. Limite numa
  subcategoria conta só ela **(decisão do design)**.
- **Cores e texto:** abaixo de 80%, cor primária. De 80% a 100%, âmbar com "Chegou a 80% do limite"
  (ou "Chegou ao limite" em 100%). Acima de 100%, vermelho com "Passou R$ 60,00". Nunca vermelho
  sem texto.
- **Aviso dentro do app:** além da tela e do card no Início, salvar um gasto que faz a categoria
  cruzar 80% ou 100% mostra um toast gentil, uma vez por limiar, categoria e mês por aparelho. Sem
  som e sem vibração.
- **Fechar o mês com orçamento:** no primeiro acesso de um mês novo, o card do painel resume o mês
  anterior ("Novembro: 6 de 8 categorias dentro do limite") e oferece copiar os limites. É o
  critério de saída da v0.2. Não há celebração aqui **(decisão do design)**.

### 6.6 7.2 — Metas

- **Quem:** Dono e Adulto criam, editam, aportam e arquivam. O Jovem e o Leitor veem as metas da
  família.
- **Criar:** nome, valor-alvo, prazo opcional (mês), ícone e, opcionalmente, uma conta vinculada.
- **Guardado:** com conta vinculada, é o saldo da conta (e aportes manuais ficam desligados); sem
  ela, é a soma dos aportes, e retirar é um aporte negativo. O guardado nunca fica negativo.
- **Jovem e conta vinculada:** o Jovem vê nome, ícone, alvo, guardado, % e prazo, mas nunca o nome
  da conta vinculada. A família escolheu fazer dessa meta uma meta da família **(decisão do
  design)**.
- **Aporte sugerido:** regra 7.4 do PRD. Meses até o prazo = diferença em meses entre o mês do
  prazo e o mês atual, mínimo 1. Sem prazo, ou com a meta atingida, não há sugestão.
- **Tela:** Planejar > Metas, com a lista e o detalhe de cada meta.
- **Atingir:** a primeira vez que o guardado alcança o alvo, a API grava `achieved_at`. Retirar
  depois não desfaz `achieved_at`; a meta segue como "atingida" até ser arquivada. Quem grava
  **(decisão do design)**:
  - um aporte grava na mesma transação do banco;
  - para meta com conta vinculada, o `planning` escuta os eventos de lançamento do `ledger` depois do
    commit (`@ApplicationModuleListener`) e confere as metas ligadas às contas tocadas. O formulário
    de lançamento não sabe nada de metas;
  - a leitura das metas também confere, como rede de segurança.
- **Celebração:** o app celebra (até 1 s, uma vez por meta por aparelho, guardando os ids já
  celebrados) e toca o som se estiver ligado (7.3) quando vê uma meta com `achieved_at` ainda não
  celebrada. Isso acontece logo depois de uma ação naquele aparelho: salvar um aporte ou um
  lançamento (a resposta invalida as metas) ou abrir Planejar > Metas. Um lançamento que sai da fila
  offline depois não celebra sozinho; a celebração fica para a próxima vez que a pessoa abrir as
  metas. Nada celebra por evento ao vivo.
- **Limite do Grátis (decisão 2):** criar a 4ª meta ativa numa família Grátis, com a cobrança ligada,
  devolve `403 goal.limit-reached`. O formulário explica antes de alguém preencher: "No plano Grátis,
  a família tem até 3 metas ativas. Conclua ou arquive uma, ou veja o plano Família em Mais >
  Plano." Sem contador e sem "última meta". Se o plano Família acabar com mais de 3 ativas, todas
  continuam; só não dá para criar outra. A checagem pede `Plans.current(ws)` e a configuração da
  cobrança; o `@RequiresPlan` não serve porque o limite é por contagem.

### 6.7 7.2 — Reserva de emergência

- **Marcar:** Dono e Adulto marcam "Esta conta é reserva" na edição da conta.
- **Card:** no Início e numa tela própria (Planejar > Reserva), "A reserva cobre 4,0 meses de
  gastos", a barra até a meta e "Faltam R$ 9.000,00 para 6 meses". Texto educativo curto sobre para
  que serve uma reserva, sem citar produto financeiro.
- **Conta:** regra 7.5 do PRD. "3 últimos meses completos" são os três meses antes do atual.
  "Menos de 3 meses de histórico" quer dizer que o primeiro lançamento `CONFIRMED` da família é de
  um mês posterior ao mais antigo desses três. Aí entra o custo mensal informado; sem ele, o card
  pede o valor ("Quanto a família gasta por mês, mais ou menos?") em vez de mostrar um número.
- **Meta da reserva:** de 1 a 24 meses, padrão 6, editável por Dono e Adulto. O Jovem não vê a
  reserva.

### 6.8 7.2 — Projeção e gastos fixos e variáveis

- **Fixo ou variável:** um `EXPENSE` `CONFIRMED` é fixo se veio de uma regra
  (`recurring_rule_id` preenchido) ou se a categoria, ou a mãe dela, tem `is_fixed`. O resto é
  variável. Dono e Adulto marcam "Gasto fixo" na edição da categoria. O Início mostra os dois totais
  do mês. Essa divisão é só para leitura e não entra na projeção (decisão 8).
- **Projeção:** para o fim do mês atual e o fim de cada um dos 3 meses seguintes:
  `saldo de hoje + recorrências previstas até a data − gasto sem regra projetado até a data`, onde:
  - **saldo de hoje:** soma das contas ativas com os `CONFIRMED` até hoje, inclusive;
  - **recorrências e lançamentos já gravados (decisão do design):** dentro do horizonte que o job já
    gerou, valem as linhas gravadas: os `PLANNED` abertos (não excluídos), inclusive os vencidos, e
    os `CONFIRMED` com data futura (alguém lançou adiantado), cada um na data dele. Depois do
    horizonte, as ocorrências saem das regras ativas por cálculo, sem gravar nada, pulando todo
    `scheduled_on` que já tem linha daquela regra (qualquer status, inclusive excluída). Assim um
    previsto pulado ou com valor ajustado nunca conta duas vezes. Receita soma, gasto subtrai,
    transferência não muda o total;
  - **gasto sem regra projetado:** média dos `EXPENSE` `CONFIRMED` sem `recurring_rule_id`, fixos ou
    variáveis, dos 3 últimos meses completos. No mês atual entra proporcional aos dias que faltam
    (`média × dias depois de hoje ÷ dias do mês`, arredondado ao centavo, meia para cima); nos meses
    seguintes entra inteira.
- **Sem histórico** (mesma definição da reserva): só saldo e recorrências, com o texto "Ainda não
  há 3 meses de gastos para estimar o resto."
- **Na tela:** um card "Saldo previsto · estimativa" com os quatro valores; um toque abre a conta
  explicada com as três parcelas. Recalcula a cada lançamento (invalida a consulta).
- **Quem vê:** Dono, Adulto e Leitor. O Jovem não vê a projeção, que usa contas que ele não vê.

**Exemplo (vira teste).** Hoje é 20/11/2026, mês com 30 dias. Saldo de hoje R$ 5.000,00. Regras:
salário de R$ 6.000,00 todo dia 5 e aluguel de R$ 2.000,00 todo dia 25. Gastos sem regra de
agosto, setembro e outubro: R$ 3.000,00, R$ 3.300,00 e R$ 3.600,00 (média R$ 3.300,00).
- Fim de novembro: 5.000 − 2.000 − 3.300 × 10 ÷ 30 (= 1.100) = **R$ 1.900,00**.
- Fim de dezembro: 1.900 + 6.000 − 2.000 − 3.300 = **R$ 2.600,00**.
- Fim de janeiro: **R$ 3.300,00**. Fim de fevereiro: **R$ 4.000,00**.
- Sem histórico: **R$ 3.000,00**, **R$ 7.000,00**, **R$ 11.000,00** e **R$ 15.000,00**.

### 6.9 7.3 — Sincronização ao vivo

- **Rota:** `GET /v1/workspaces/{ws}/events` (`text/event-stream`), com a sessão por cookie e o mesmo
  guarda de família. O front abre com `new EventSource(url, { withCredentials: true })`; a CSP já
  libera a origem da API em `connect-src`.
- **Evento:** `event: changed` e `data: {"entity": "...", "id": "..."}`, com `entity` em
  `transaction`, `account`, `category`, `tag`, `rule`, `budget`, `goal`, `reserve`, `members` e
  `modules`. Sem valores, nomes nem descrições: o app busca o resto pelas rotas atuais, com as
  permissões de sempre.
- **Filtro antes de enviar:** `transaction` e `account` só vão para quem vê aquela conta (para
  transações, a regra `sqlTouches` aplicada à conta de origem e de destino, guardadas no evento).
  `budget`, `rule`, `reserve` não vão para o Jovem. Nada do planejamento vai com o módulo
  desligado.
- **Quando:** os serviços publicam eventos de domínio; o `live` escuta com
  `@TransactionalEventListener(AFTER_COMMIT)` e repassa para os streams daquela família, em
  memória (uma instância). A interface do registro permite trocar por LISTEN/NOTIFY no futuro.
- **Saúde do stream:** comentário de batimento a cada 25 s (abaixo de qualquer tempo ocioso de proxy),
  `retry: 5000`. A cada batimento o servidor confere se a sessão e o vínculo ainda valem; se não,
  fecha. Remover um membro fecha os streams dele na hora. Limites: 5 streams por pessoa e 1.000 por
  processo; acima disso, `429 rate.limited` e o app cai no modo sem tempo real (busca ao voltar para
  a aba).
- **Caddy:** segundo a documentação, o `reverse_proxy` descarrega na hora respostas
  `text/event-stream`. O plano da 7.3 confere isso no staging e põe `flush_interval -1` no caminho
  `/v1/workspaces/*/events` se precisar.
- **No app:** um stream por aba, aberto com a aba visível. Com a aba escondida por mais de 5
  minutos, fecha; ao voltar, reabre e invalida as consultas da família. Ao reconectar (ou ao voltar
  do offline), invalida tudo da família, então nada se perde mesmo sem replay de eventos.
- **Destaque:** lançamento que chega de outro aparelho entra na lista com o destaque de 200 ms, sem
  som. O app sabe o que é "de outro aparelho" porque guarda os ids que ele mesmo criou ou editou nos
  últimos minutos. Um anúncio `aria-live="polite"` diz "Lançamento novo na lista." no máximo uma vez
  a cada 10 s **(decisão do design)**.

### 6.10 7.3 — Movimento e som

- **Base de movimento:** tokens CSS (`--motion-fast: 150ms`, `--motion-base: 200ms`,
  `--motion-sheet: 250ms`, `--motion-ease: ease-out`), um hook `useReducedMotion`, a classe do
  destaque de item novo e um componente `Celebration` (até 1 s, uma vez por evento, sem bloquear a
  tela, ícone estático com movimento reduzido). As telas novas da 7.1 e da 7.2 usam o que já existe;
  a 7.3 troca durações soltas pelos tokens.
- **Som:** em Ajustes, "Sons de conquista", desligado por padrão, com o botão "Ouvir". A escolha fica
  **no aparelho** (`localStorage`), porque som é coisa do aparelho e não da família **(decisão do
  design)**. Sons sintetizados com Web Audio, até 300 ms, volume baixo, num chunk carregado só depois
  que a pessoa liga ou toca "Ouvir".
- **Quando toca:** meta atingida e reserva chegando à meta, sempre logo depois de uma ação da pessoa
  naquele aparelho. Nunca por evento ao vivo, aviso, erro ou orçamento.

### 6.11 Privacidade, auditoria e exportação

- **Exportação da família:** regras, orçamentos, metas, aportes, tags, vínculos de tag, `status`,
  `payment_method`, `is_reserve`, `is_fixed`, `country`, `locale` e os campos da reserva. A
  exportação pessoal leva os aportes feitos pela pessoa e as tags que ela criou.
- **Exclusão:** tudo novo pertence à família e sai em cascata com ela. Conta apagada de vez:
  `created_by` e `member_id` das tabelas novas viram nulos (`ON DELETE SET NULL`), como nas tabelas
  de hoje.
- **Auditoria:** entram na trilha mudar a meta da reserva e o custo mensal informado, arquivar meta e
  arquivar regra. Limites e aportes do dia a dia não **(decisão do design)**.
- **Política de privacidade:** os dados novos são do mesmo tipo dos que já existem (finanças da
  família, sem terceiros). O texto atual cobre; o plano confere se a lista de dados cita "metas,
  orçamentos e recorrências" e, se não citar, isso vira uma versão nova da política com aviso, sem
  novo aceite **(decisão do design)**.

## 7. Testes

- **Regras do PRD como teste:** 7.3 (80%, 105% e "passou R$ 60,00"), 7.4 (R$ 1.000,00 e
  R$ 1.285,72) e 7.5 (4,0 meses e R$ 9.000,00), mais o exemplo de projeção da seção 6.8. Testes de
  unidade puros, sem banco.
- **Datas das recorrências:** dia 31 em meses curtos, 29/02, semanal com `interval` 2, `end_on` no
  meio do horizonte, job rodando duas vezes (idempotência), pular e editar sem recriar.
- **Visibilidade:** para cada rota nova, um teste com o Jovem; para o SSE, um teste de integração
  com dois streams (Adulto e Jovem) em que um lançamento numa conta só do Adulto chega a um e não ao
  outro.
- **Módulo desligado:** as rotas do planejamento devolvem `403 module.disabled`; o job pula a
  família; a lista esconde os `PLANNED`; o `core-without-modules.spec.ts` (S2) continua verde.
- **Limite de metas:** Grátis com 3 ativas (criar a 4ª → `403 goal.limit-reached`), com 3 ativas e
  1 atingida (pode criar), Fundadora (sem limite), cobrança desligada (sem limite).
- **E2E:** criar regra e confirmar o previsto; orçamento cruzando 80% com o toast; meta atingida com
  a celebração; dois navegadores na mesma família com o lançamento aparecendo no outro; axe nas
  telas novas.
- **Bundle:** `check:chunks` com o inicial ≤ 200 KB e o som num chunk separado.

## 8. Critério de saída

- **7.1:** forma de pagamento, tags e recorrências no ar no staging; famílias atuais com BR, BRL e
  pt-BR; S2 verde.
- **7.2:** os exemplos 7.3 a 7.5 e o da projeção passam como teste; uma família de teste fecha um mês
  com orçamento no staging (limites definidos, gastos lançados, resumo do mês visto, limites
  copiados para o mês seguinte).
- **7.3:** um lançamento aparece em outro navegador em até 5 s no staging; o som toca só depois de
  ligado; com `prefers-reduced-motion` não há animação.
- **v0.2 sai** quando as três estão na `main` e no ar.

## 9. Custos

- **Fixo novo: R$ 0.** Nada de serviço novo na AWS nem na Cloudflare. O SSE roda no EC2 que já
  existe; cada stream aberto ocupa uma conexão e pouca memória, e o limite de 1.000 por processo
  protege a instância de 2 GB.
- **Tráfego:** o batimento de 25 s gasta poucos bytes por stream; dentro da franquia de saída que já
  temos.

## 10. Riscos

| Risco | Mitigação |
|---|---|
| A projeção parecer promessa | Rótulo "estimativa" fixo, conta explicada em um toque, nada de cor de alerta no valor |
| Previstos acumulando sem confirmação | Seção "Previstos" no topo com "venceu em dd/mm"; nada é confirmado sozinho |
| Stream preso no proxy ou cortado por ociosidade | Batimento de 25 s, conferência no staging, `flush_interval` se preciso, nova busca ao reconectar |
| Vazamento ao Jovem por evento | Filtro antes de enviar, evento sem conteúdo e teste com dois streams |
| Muitas conexões abertas | Limites por pessoa e por processo; fechar com a aba escondida |
| Limite de metas lido como pressão | Texto revisado contra o design system §8; nenhuma meta existente é bloqueada |

## 11. O que fica para o construtor

- Nomes de classes, pacotes e rotas além dos fixados aqui; a forma exata das respostas da API.
- Se o `live` é um módulo Modulith ou um pacote do `shared`.
- O desenho visual das telas novas dentro do design system.
- A lista de ícones das metas e os sons sintetizados (notas, envelope), dentro dos limites do §5.
- A divisão de cada entrega em tarefas, no plano de cada uma (7.1, 7.2 e 7.3).
