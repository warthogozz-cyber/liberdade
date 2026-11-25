# Plano 9: v0.4 Trilhas de renda extra (design)

> **Status:** Em revisão · **Data:** 2026-11-24

**Base:** [PRD](../../prd.md) (US-10, US-40 a US-43, §7.7, §10, CVM), [roadmap](../../roadmap.md)
(v0.4), [modelo de dados](../../data-model.md) (§5), [wireframes](../../wireframes.md) (Novo
Projeto, Módulos), [arquitetura](../../architecture.md) (`ventures`),
[ADR-0006](../../adr/0006-money-representation.md),
[spec do Plano 8](2026-11-24-liberdade-plan-8-indicators-gamification-design.md).

## 1. Intenção e porquê

Muita família de classe média ganha um dinheiro a mais vendendo doce, revendendo produto, dando
aula ou fazendo um serviço no fim de semana. Quase sempre sem saber se aquilo dá lucro de verdade:
o custo da embalagem some no mercado do mês, o gás entra na conta de casa e o preço sai "pelo que
os outros cobram".

A v0.4 entrega o módulo "Novo Projeto" (no app, **Projetos**): um lugar para planejar uma renda
extra antes de gastar e acompanhar depois, separado das contas da casa.

- **Antes de começar:** custos, preço, margem, ponto de equilíbrio, lucro do mês, ROI e payback,
  com as fórmulas do PRD §7.7. A pessoa vê se "se paga" e em quanto tempo.
- **Para não começar do zero:** trilhas com modelos prontos (culinária, produtos digitais,
  conteúdo, revenda e serviços), com valores de exemplo e passos curtos.
- **Depois de começar:** as vendas e compras reais são lançamentos normais marcados com o projeto;
  o app compara real e planejado e mostra o payback real.
- **Investimentos:** uma trilha só educativa, que explica e simula, com o aviso da CVM. Não cria
  projeto e nunca recomenda produto.

## 2. Objetivos, não-objetivos e antiobjetivos

**Objetivos**

- US-40: criar projeto com nome, tipo, itens de investimento inicial, itens de custo fixo mensal
  e produtos (custo variável unitário, preço e venda prevista no mês).
- US-41: simulação com margem, margem %, ponto de equilíbrio (unidades e R$), lucro do mês, ROI em
  12 meses e payback, igual ao exemplo do PRD §7.7.
- US-42: lançamentos marcados com o projeto, real contra planejado por mês e payback real.
- US-43: cinco modelos em quatro trilhas de projeto, com passos, e a trilha educativa de
  investimentos com o aviso da CVM.
- A conquista "Primeiro projeto de renda extra", adiada pelo Plano 8.

**Não-objetivos**

- Recorrência marcada com projeto (a regra de recorrência não leva `venture_id`).
- Transferência marcada com projeto. Só receita e despesa levam projeto.
- Unidades vendidas de verdade. O real é em reais; a quantidade vendida não é registrada.
- Estoque, ficha técnica de receita, cálculo de imposto (MEI, Simples) e emissão de nota.
- Conta bancária própria do projeto ou separação contábil. O projeto é uma marca nos lançamentos
  da família.
- Payback descontado, VPL, TIR ou cenários de preço. Só payback simples (PRD §7.7).
- Na trilha de investimentos: imposto de renda, taxas, comparação com produto real, carteira e
  qualquer dado de mercado além do que o módulo Indicadores já mostra.
- Cartão de projeto no painel.
- Projetos em outra moeda que não a da família.

**Antiobjetivos (falharia mesmo funcionando)**

- Uma frase, um ranking ou uma ordem que soe como recomendação de investimento, ou um banco,
  corretora, fundo ou título com nome próprio na trilha de investimentos.
- Simulação mostrada como promessa ("você vai ganhar R$ 540").
- Valor de exemplo do modelo que pareça número de verdade, sem a marca "exemplo".
- Conta errada no centavo, ou ponto de equilíbrio arredondado para baixo.
- Projeto que empurra a gastar: "invista mais para lucrar mais", alerta de "falta pouco",
  conquista por faturar.
- O Jovem ver projeto, valor ou nome de projeto.
- O pacote inicial passar de 200 KB.

## 3. Restrições

- **Pacote inicial no limite de 200 KB.** Telas de projeto, trilhas, simulador e textos carregam
  sob demanda. `register.ts` do módulo fica mínimo. `check:chunks` continua no CI.
- **Cálculo exato.** ADR-0006: decimal sem arredondar no meio, arredonda só na exibição com
  `HALF_EVEN`; ponto de equilíbrio em unidades arredonda **para cima**.
- **CVM (conferido em 2026-11-24).** Seguem vigentes a Resolução CVM 19/2021 (consultoria de
  valores mobiliários) e a Resolução CVM 20/2021 (análise). A Resolução CVM 250, de 05/11/2026
  (em vigor em 01/12/2026), só muda o Anexo A da Resolução 19 (certificações para o
  credenciamento). Não há norma nova sobre conteúdo educativo, simuladores ou apps. Regra do app:
  compara, explica e simula, com aviso visível; nunca recomenda valor mobiliário de forma
  personalizada. A conferência foi feita pelas páginas de legislação da CVM, sem leitura jurídica
  do texto integral: o mantenedor confere de novo antes do go-live da v0.4.
- **Módulos.** `ventures` já existe em `ModuleKey` e no catálogo do app ("Projetos", "Renda extra e
  pequenos negócios, separados das contas da casa."). Começa desligado. O núcleo nunca depende de
  `ventures`.
- **Papéis (US-10).** Dono e Adulto criam e editam projetos. O Leitor vê. O Jovem não vê nada do
  módulo, nem a trilha de investimentos.
- **Offline.** Criar e editar projeto precisa de rede, como editar metas hoje. Marcar um lançamento
  com projeto funciona offline, pela fila de envio que já existe.
- **Base de código e ordem.** Os planos partem da `main` de hoje (Plano 8.1) e não usam código do
  8.2 nem do 8.3. Plano 9 usa as migrações **V30 a V39**. O Flyway valida a ordem (sem
  `outOfOrder`), então a V30 só pode ir para produção depois da V19 e da V20: **as duas entregas
  só são executadas com o 8.2 e o 8.3 já na `main`** (pré-requisito de execução). Isso também fixa
  a versão da exportação (seção 6.12) e a lista do `CHECK` da auditoria. A 9.2 usa ainda a tabela
  de conquistas do 8.3.
- **WCAG 2.2 AA**, PT-BR na interface, sem notificação nova.

## 4. Abordagens consideradas

### 4.1 Onde fazer as contas

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **Na API, com prévia** | Uma implementação em Java, testada com o exemplo do PRD. O formulário chama uma rota de simulação que não grava nada, com espera curta enquanto a pessoa digita | Uma só fonte de verdade para tela, exportação e real vs. planejado. Precisa de rede, como editar metas | **Escolhida pelo mantenedor** |
| No aparelho | As contas em TypeScript com `bigint`, como a calculadora de rendimento real | Funciona offline, mas o real vs. planejado e a conquista precisam das mesmas contas na API: duas implementações para manter iguais | Descartada |

### 4.2 Como ligar o real ao projeto

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **`transactions.venture_id`** | Coluna opcional com chave composta para `ventures`, que o `ledger` trata como um UUID opaco (o mesmo padrão de `recurring_rule_id`) | Segue o modelo de dados §5. O lançamento continua um lançamento normal da família, nos saldos e no painel | **Escolhida (modelo de dados)** |
| Lançamentos próprios do projeto | Tabela de vendas e custos dentro de `ventures` | Separa de verdade, mas duplica o registro e some dos saldos da família | Descartada |
| Etiqueta (`tags`) | Uma etiqueta com o nome do projeto | Sem tabela nova, mas sem integridade e fácil de errar | Descartada |

### 4.3 Onde ficam os modelos das trilhas

| Abordagem | Como funciona | Troca | Escolha |
|---|---|---|---|
| **Catálogo na API** | Os modelos são código da API; `GET` lista as trilhas e `POST` cria o projeto copiando os valores | A cópia é atômica e testável (cada modelo tem que dar lucro e se pagar). Texto PT-BR na API, como as categorias padrão | **Escolhida (decisão do design)** |
| Catálogo no app | O app monta o projeto a partir do modelo e manda tudo | Texto junto com as outras telas, mas sem teste de que o modelo fecha a conta na API | Descartada |
| Tabela no banco | Modelos editáveis sem deploy | Precisa de tela de administração que ninguém pediu | Descartada |

### 4.4 Ordem das entregas

| Abordagem | Troca | Escolha |
|---|---|---|
| **Projeto e simulação → real vs. planejado** | A 9.1 entrega valor sozinha (planejar) e não depende de nada do Plano 8. A 9.2 mexe no `ledger` e na fila offline e espera o 8.3 | **Escolhida pelo mantenedor** |
| Uma entrega só | Um PR grande, travado até o 8.3 entrar | Descartada |

## 5. Decisões de produto (do mantenedor)

1. **Um spec, duas entregas**, cada uma com seu plano e seu PR:
   - **9.1** projeto, simulação, trilhas com modelos, passos e trilha de investimentos (US-40,
     US-41, US-43);
   - **9.2** real vs. planejado, payback real e a conquista do primeiro projeto (US-42).
2. **Plano Grátis tem 1 projeto ativo.** Encerrado não conta. Família não tem limite. Com a
   cobrança desligada, não há limite (o mesmo padrão das metas).
3. **Trilha de investimentos: textos e simulador.**
   - Textos curtos sobre poupança, renda fixa, Tesouro Direto, fundos e ações: o que é, risco,
     liquidez e garantia (FGC), sem ranking.
   - Simulador de juros compostos com aporte mensal, comparando até 3 cenários com taxas que a
     pessoa digita. Com o módulo Indicadores ligado, as taxas vêm preenchidas com Selic e CDI.
   - Nunca cita banco nem produto.
4. **Revenda e serviços é uma trilha com dois modelos** (revenda e serviço), cada um com seus passos.
   São quatro trilhas de projeto mais a de investimentos.
5. **Contas na API, com prévia** (seção 4.1). Cobertura de testes do cálculo ≥ 90%.
6. **Payback real por fluxo de caixa.** Tudo o que foi marcado com o projeto conta: receitas menos
   despesas, acumulado desde o primeiro lançamento. O projeto se pagou quando o acumulado volta a
   zero; o investimento real é o ponto mais negativo (seção 6.10).

## 6. Desenho

### 6.1 Módulos e onde cada coisa mora

| Onde | O quê |
|---|---|
| API `ventures` (novo) | Projetos, itens, produtos, passos, catálogo de modelos, cálculo, rota de simulação, simulador de investimento, exportação; na 9.2, real vs. planejado |
| API `ledger` (9.2) | Coluna `venture_id`, campo nos pedidos e respostas, filtro da lista, soma mensal por projeto numa interface pública |
| API `audit` | Ouve os eventos de encerrar e excluir projeto |
| API `privacy` | Exporta projetos pela interface pública de `ventures` |
| API `gamification` (9.2) | A conquista `FIRST_VENTURE` |
| Web `features/ventures` | Lista, trilhas, formulário com prévia, tela do projeto, passos, trilha de investimentos; na 9.2, real vs. planejado |
| Web `features/transactions` (9.2) | Campo "Projeto" no formulário e filtro na lista |

`ventures` depende de `workspace`, `ledger`, `billing` e `shared`, como `planning`
**(decisão do design)**. Não depende de `indicators`: o preenchimento de Selic e CDI no simulador
vem do app, que já lê `GET /indicators` quando aquele módulo está ligado.

### 6.2 Modelo de dados

**V30 (9.1).** Segue o modelo de dados §5, com o tipo ampliado para as trilhas e uma tabela de
passos **(decisão do design)**:

```sql
CREATE TABLE ventures (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    type         text        NOT NULL CHECK (type IN ('FOOD', 'DIGITAL_PRODUCT', 'CONTENT', 'RESALE',
                                                      'SERVICE', 'OTHER')),
    template_key text        NULL,       -- o modelo de origem; nulo para projeto em branco
    status       text        NOT NULL DEFAULT 'PLANNING'
                             CHECK (status IN ('PLANNING', 'ACTIVE', 'PAUSED', 'CLOSED')),
    started_on   date        NULL,       -- o dia em que começou a vender; obrigatório fora de PLANNING
    closed_on    date        NULL,       -- o dia em que foi encerrado; só em CLOSED
    currency     char(3)     NOT NULL,   -- a moeda da família na criação
    created_by   uuid        NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,
    CHECK (status = 'PLANNING' OR started_on IS NOT NULL),
    CHECK ((status = 'CLOSED') = (closed_on IS NOT NULL)),
    CHECK (closed_on IS NULL OR closed_on >= started_on),
    FOREIGN KEY (workspace_id, created_by)
        REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by),
    UNIQUE (workspace_id, id)
);

CREATE TABLE venture_cost_items (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    venture_id   uuid          NOT NULL,
    kind         text          NOT NULL CHECK (kind IN ('INITIAL', 'FIXED_MONTHLY')),
    name         text          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    amount       numeric(19,4) NOT NULL CHECK (amount > 0),
    currency     char(3)       NOT NULL,
    is_example   boolean       NOT NULL DEFAULT false,  -- veio do modelo e ainda não foi editado
    position     smallint      NOT NULL,
    FOREIGN KEY (workspace_id, venture_id) REFERENCES ventures (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE venture_products (
    id                  uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id        uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    venture_id          uuid          NOT NULL,
    name                text          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    unit_variable_cost  numeric(19,4) NOT NULL CHECK (unit_variable_cost >= 0),
    unit_price          numeric(19,4) NOT NULL CHECK (unit_price > 0),
    planned_units_month integer       NOT NULL CHECK (planned_units_month BETWEEN 0 AND 1000000),
    currency            char(3)       NOT NULL,
    is_example          boolean       NOT NULL DEFAULT false,
    position            smallint      NOT NULL,
    FOREIGN KEY (workspace_id, venture_id) REFERENCES ventures (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE venture_steps (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    venture_id   uuid        NOT NULL,
    title        text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
    position     smallint    NOT NULL,
    done_at      timestamptz NULL,
    FOREIGN KEY (workspace_id, venture_id) REFERENCES ventures (workspace_id, id) ON DELETE CASCADE
);

CREATE INDEX ventures_by_workspace ON ventures (workspace_id, status);
CREATE INDEX venture_cost_items_by_venture ON venture_cost_items (workspace_id, venture_id);
CREATE INDEX venture_products_by_venture ON venture_products (workspace_id, venture_id);
CREATE INDEX venture_steps_by_venture ON venture_steps (workspace_id, venture_id);
```

A V30 também acrescenta `'VENTURE'` ao `CHECK` de `audit_events.target_type` (o mesmo `DROP` e
`ADD` da V17, com a lista vigente depois da V20).

O `CHECK` de `closed_on IS NULL OR closed_on >= started_on` vale para projetos encerrados, que
sempre têm `started_on` (encerrar direto de `PLANNING` grava `started_on = closed_on`).

Limites **(decisão do design)**: até 20 itens de custo por tipo, 10 produtos e 20 passos por
projeto. Valores até o `MAX` de dinheiro que o app já usa.

**V31 (9.2).**

```sql
ALTER TABLE transactions
    ADD COLUMN venture_id uuid NULL,
    ADD FOREIGN KEY (workspace_id, venture_id)
        REFERENCES ventures (workspace_id, id) ON DELETE SET NULL (venture_id),
    ADD CONSTRAINT transactions_venture_kind CHECK (venture_id IS NULL OR kind IN ('INCOME', 'EXPENSE'));
CREATE INDEX transactions_by_venture ON transactions (workspace_id, venture_id, occurred_on)
    WHERE venture_id IS NOT NULL;
```

Excluir o projeto desmarca os lançamentos, que continuam na família. O construtor confere os
nomes de coluna (`kind`, `occurred_on`) contra a V4 na hora de executar.

### 6.3 Entrega 9.1: cálculo

Uma classe de domínio pura em `ventures`, sem Spring nem banco, recebe itens e produtos e devolve
o resultado. Dinheiro em `BigDecimal` exato; arredonda só na resposta (`HALF_EVEN`, 2 casas no
dinheiro, 1 casa no percentual), exceto o ponto de equilíbrio em unidades (`CEILING`).

**Fórmulas** (PRD §7.7):

| Grandeza | Conta |
|---|---|
| Margem do produto | preço − custo variável unitário |
| Margem % do produto | margem ÷ preço |
| Investimento inicial | soma dos itens `INITIAL` |
| Custos fixos do mês | soma dos itens `FIXED_MONTHLY` |
| Margem total do mês | Σ (venda prevista × margem) |
| Faturamento previsto | Σ (venda prevista × preço) |
| Lucro do mês | margem total − custos fixos |
| ROI em 12 meses | (12 × lucro do mês − investimento) ÷ investimento |
| Payback | primeiro mês em que o lucro acumulado ≥ investimento; com lucro do mês > 0 e constante, `max(1, ⌈investimento ÷ lucro do mês⌉)` meses |

**Ponto de equilíbrio com mais de um produto (decisão do design):**

- **Um produto:** unidades = ⌈custos fixos ÷ margem⌉; em R$ = unidades × preço. Não depende da
  venda prevista.
- **Vários produtos:** usa o mix da venda prevista. Margem % ponderada = margem total ÷
  faturamento previsto. Em R$ = custos fixos ÷ margem % ponderada. Unidades de cada produto =
  ⌈venda prevista do produto × custos fixos ÷ margem total⌉.
- Sem venda prevista em nenhum produto (com mais de um), o ponto de equilíbrio fica "informe a venda
  prevista".

**Casos de borda**

| Caso | O que a tela mostra |
|---|---|
| Lucro do mês ≤ 0 | Payback: "não se paga com esses números". ROI negativo, mostrado sem alarme |
| Margem do produto ≤ 0 | "O preço não cobre o custo deste produto"; o produto não tem ponto de equilíbrio próprio |
| Margem total ≤ 0 | Ponto de equilíbrio: "não há ponto de equilíbrio com esses números" |
| Investimento = 0 | ROI: "sem investimento inicial"; payback: 1 mês se o lucro for positivo |
| Custos fixos = 0 | Ponto de equilíbrio: 0 unidades |

**Exemplo do PRD como teste** (doces gourmet): custo 1,80, preço 4,50, fixos 270,00, investimento
800,00, 300 unidades → margem **2,70** (**60,0%**), equilíbrio **100 unidades** e **R$ 450,00**,
lucro **540,00**, payback **2 meses**, ROI 12 meses **710,0%**. Mais um teste com dois produtos,
um com margem negativa e os casos de borda acima. Cobertura do cálculo ≥ 90%.

### 6.4 Entrega 9.1: rotas

Todas com `@RequiresModule(VENTURES)`, sob `/v1/workspaces/{ws}/ventures`. O Jovem recebe 403
em todas **(decisão do design)**. O Leitor só lê.

| Rota | O quê |
|---|---|
| `GET /ventures` | Projetos com status, tipo e o resumo do cálculo; `limitReached` como em metas |
| `POST /ventures` | Cria em branco ou de um modelo (`templateKey`); 403 `venture.limit-reached` no limite do Grátis |
| `GET /ventures/{id}` | Projeto completo: itens, produtos, passos e o cálculo |
| `PUT /ventures/{id}` | Troca nome, tipo, `started_on` (fora de `PLANNING`, para corrigir a data), itens e produtos de uma vez, com `version` (409 se mudou) |
| `POST /ventures/{id}/status` | Muda o status (seção 6.5) |
| `DELETE /ventures/{id}` | Exclui de vez, com confirmação na tela |
| `POST`, `PATCH`, `DELETE` em `/ventures/{id}/steps` | Acrescenta, renomeia, marca, reordena e remove passos |
| `POST /ventures/simulation` | Recebe itens e produtos, devolve o cálculo, não grava nada |
| `GET /ventures/templates` | Trilhas e modelos (seção 6.6) |
| `POST /ventures/investment-simulation` | Simulador de juros compostos (seção 6.7), não grava nada |

- Editar um item ou produto marcado `is_example` (nome ou valor diferente do que veio) desmarca
  aquele item **(decisão do design)**.
- A rota de simulação valida como `PUT`, mas aceita formulário incompleto (sem produto, sem item)
  e devolve o que der para calcular.
- Mudanças publicam `VentureChanged`; o aviso ao vivo é `{"entity":"venture"}` só para quem vê o
  módulo (não para o Jovem).

### 6.5 Entrega 9.1: status e limite do plano

| De | Para | Regra |
|---|---|---|
| `PLANNING` | `ACTIVE` | Pede `started_on` (hoje por padrão, pode ser no passado) |
| `ACTIVE` | `PAUSED` | — |
| `PAUSED` | `ACTIVE` | — |
| `ACTIVE`, `PAUSED`, `PLANNING` | `CLOSED` | Grava `closed_on` (hoje por padrão). Auditado |
| `CLOSED` | `PAUSED` | Reabrir; limpa `closed_on`; passa pelo limite do plano |

- **Limite (decisão do mantenedor):** no Grátis, com a cobrança ligada, só um projeto fora de
  `CLOSED`. Criar ou reabrir além disso responde 403 `venture.limit-reached`, com o texto no
  padrão de `goal.limit-reached` ("No plano Grátis, a família tem 1 projeto ativo. Encerre um, ou
  veja o plano Família em Mais > Plano."). Família e cobrança desligada não têm limite.
- Desligar o módulo não apaga nada. As rotas respondem 403 `module.disabled` e o item some do menu.

### 6.6 Entrega 9.1: trilhas, modelos e passos

| Trilha | Modelo (`template_key`) | Tipo |
|---|---|---|
| Culinária | `food-sweets` (doces por encomenda) | `FOOD` |
| Produtos digitais | `digital-ebook` (e-book ou curso curto) | `DIGITAL_PRODUCT` |
| Conteúdo | `content-channel` (canal de conteúdo com publis e aulas) | `CONTENT` |
| Revenda e serviços | `resale-goods` (revenda de produtos) | `RESALE` |
| Revenda e serviços | `service-hours` (serviço por atendimento) | `SERVICE` |
| Investimentos | sem modelo: só conteúdo (seção 6.7) | — |

- **O modelo `food-sweets` reproduz o exemplo do PRD §7.7** (itens que somam 800,00 de
  investimento e 270,00 de fixos, um produto de 1,80 por 4,50 com 300 unidades), como no wireframe
  dos brigadeiros. Os outros quatro têm valores plausíveis escritos pelo construtor; teste: cada um
  dá lucro positivo e payback em até 12 meses.
- Todo item e produto copiado do modelo nasce com `is_example = true`. A tela mostra o selo
  "exemplo: ajuste para a sua realidade" em cada um até ser editado, e uma faixa no topo enquanto
  houver algum.
- Família com moeda diferente de BRL cria o projeto do modelo só com nomes e passos, sem valores
  (os valores do catálogo são em reais) **(decisão do design)**.
- **Passos:** cada modelo traz de 5 a 7 passos curtos, copiados para o projeto como lista de
  tarefas. A pessoa marca, renomeia, reordena, acrescenta e remove. Projeto em branco começa com
  três passos genéricos (custos, preço, primeira venda). Revenda e serviço têm passos próprios.
- **Texto dos passos:** prático e neutro. Pode citar "confira as regras da sua cidade" ou
  "veja se o MEI faz sentido", sem afirmar regra legal, valor de imposto ou prazo.

### 6.7 Entrega 9.1: trilha de investimentos

- Tela própria dentro de Projetos, aberta pelo cartão "Investimentos (conteúdo educativo)" na
  escolha de trilha. Não cria projeto.
- O aviso da CVM fica fixo no topo da tela de textos e do simulador, com o `Disclaimer` que já
  existe: "Conteúdo educativo. Não é recomendação de investimento."
- **Textos:** poupança, renda fixa (CDB, LCI e LCA como categorias), Tesouro Direto, fundos e
  ações. Para cada um: o que é, risco, liquidez, garantia (FGC onde há, com o limite em vigor
  citado com data) e como o rendimento costuma ser cobrado de imposto, em uma linha. Ordem fixa
  pela lista acima, que vai do mais simples ao mais complexo, sem nota, estrela ou "melhor"
  **(decisão do design)**. Nenhum nome de banco, corretora, gestora, fundo ou título específico.
  Textos no app (chunk sob demanda), com data de revisão no rodapé.
- **Simulador:** valor inicial, aporte mensal e prazo (1 a 50 anos), mais até 3 cenários com nome
  e taxa ao ano que a pessoa digita.
  - Taxa mensal equivalente = (1 + taxa ao ano)^(1/12) − 1; saldo mês a mês com aporte no fim do
    mês. Resultado por cenário: saldo final, total aportado e rendimento.
  - Feito na API, em `BigDecimal` com precisão de 20 dígitos para a raiz, pelo mesmo motivo da
    seção 4.1 **(decisão do design)**. Não grava nada.
  - Com o módulo Indicadores ligado, o app preenche dois cenários com a Selic e o CDI de
    `GET /indicators`, com `SourceStamp`. A pessoa pode trocar qualquer taxa. Sem o módulo, os
    campos vêm vazios.
  - Nota fixa sob o resultado: "Simulação com taxa constante, sem imposto de renda nem taxas. Não
    é promessa de rendimento."
- Teste de texto: nenhuma palavra como "recomendamos", "melhor", "ideal para você"; nenhum nome
  próprio de instituição.

### 6.8 Entrega 9.1: telas

- Item **Projetos** no menu Mais quando o módulo está ligado e a pessoa não é Jovem.
- **Lista:** projetos fora de `CLOSED` primeiro, com status, lucro do mês previsto e payback.
  Encerrados num grupo recolhido. Botão "Começar um projeto".
- **Começar:** cinco cartões de trilha e "Em branco". Revenda e serviços abre a escolha entre os
  dois modelos. Investimentos abre a tela educativa.
- **Formulário:** nome, tipo, itens de investimento, itens de custo fixo e produtos. A prévia do
  cálculo fica ao lado (tela larga) ou fixa embaixo (celular), atualizada pela rota de simulação
  400 ms depois da última tecla. Sem rede, a prévia mostra "conecte-se para ver as contas" e salvar
  fica desabilitado, como em metas.
- **Tela do projeto:** como o wireframe (preço, custo, margem, equilíbrio, lucro, payback, ROI),
  mais os passos e o status. A conta aparece por extenso num "Como calculamos" recolhível. A
  linha "Real vs. planejado ›" só aparece na 9.2.
- Nota curta na simulação: "Com os números que você digitou. Não é promessa de resultado."
- Teclado, leitor de tela e foco gerenciado; percentuais e valores com vírgula decimal.

### 6.9 Entrega 9.2: lançamentos com projeto

- `CreateTransactionRequest` e `UpdateTransactionRequest` ganham `ventureId` opcional; a resposta
  também. Só receita e despesa; em transferência responde 422.
- **Na edição, `ventureId` nulo mantém o projeto**, como as etiquetas hoje. Para tirar o projeto,
  o pedido manda `clearVenture: true` **(decisão do design)**. Assim, edições de apps antigos, de
  quem está com o módulo desligado, do Jovem e as que já estavam na fila antes da 9.2 nunca apagam
  a marca.
- O `ledger` não conhece `ventures`: a chave composta garante que o projeto é da família. Projeto
  de outra família ou inexistente responde 422 `transaction.venture-invalid`. Projeto encerrado
  ainda aceita (um pagamento atrasado depois de encerrar é normal) **(decisão do design)**.
- **Jovem:** a resposta para ele nunca traz `ventureId`; ele não pode mandar `ventureId` nem
  `clearVenture` (422); quando ele edita um lançamento que tem projeto, o projeto continua o mesmo.
- **Formulário:** campo "Projeto (opcional)" em receita e despesa, só com o módulo ligado, a
  pessoa não sendo Jovem e havendo projeto fora de `CLOSED`. A lista de projetos fica no cache do
  app para o campo funcionar offline. O lançamento marcado offline entra na fila de envio com o
  `ventureId`. Se o projeto foi excluído nesse meio-tempo, a API responde 422
  `transaction.venture-invalid` e o item fica em "precisa de atenção", o fluxo que a fila já tem
  para 422: a pessoa abre, tira o projeto e envia de novo **(decisão do design)**. É raro
  (excluir projeto enquanto alguém lança offline) e não pede lógica nova na fila; a mensagem do
  422 diz o que fazer.
- **Lista de lançamentos:** filtro por projeto (`ventureId` na consulta) e o nome do projeto no
  detalhe.
- Módulo desligado: os lançamentos mantêm a marca, o campo some e o filtro também.

### 6.10 Entrega 9.2: real vs. planejado e payback real

- O `ledger` ganha uma leitura pública (como `LedgerFigures`): por projeto e mês, a soma de
  receitas e a soma de despesas, só de lançamentos confirmados e não excluídos.
- **Período do real:** do mês do primeiro lançamento marcado (que pode ser antes de `started_on`,
  com as compras iniciais) até o mês de `closed_on`, ou o mês atual se não encerrou.
- **Período do planejado:** do mês de `started_on` até o mesmo fim. Projeto em `PLANNING` não tem
  planejado mensal. Pausar não muda o planejado; a tabela mostra o status atual e o texto continua
  neutro **(decisão do design)**: pausas não ficam registradas mês a mês.
- **Por mês:** receita real contra faturamento previsto; despesa real contra custo previsto
  (Σ venda prevista × custo unitário + custos fixos); resultado real contra lucro previsto. Uma
  tabela dos últimos 12 meses e o total do período. Diferença em R$ e em texto neutro ("abaixo do
  previsto", "acima do previsto"), sem vermelho de alarme.
- **Payback real, por fluxo de caixa (decisão do mantenedor):**
  - resultado acumulado = Σ receitas − Σ despesas marcadas, mês a mês, desde o primeiro lançamento
    marcado. Nada conta duas vezes e não depende de `started_on`;
  - **investimento real** = o ponto mais negativo do acumulado (o máximo que saiu do bolso);
  - **se pagou** no primeiro mês em que o acumulado volta a ≥ 0 depois de ter ficado negativo. O
    número de meses conta do primeiro lançamento marcado até esse mês;
  - ainda negativo: "ainda não se pagou: faltam R$ X" (X = −acumulado), sem projetar data;
  - nunca ficou negativo: "o resultado ficou positivo desde o início", com a dica "marque também as
    compras do começo para ver o investimento real";
  - a tela mostra lado a lado o investimento e o payback planejados (seção 6.3) e os reais.
- A tela lista os lançamentos do projeto com link para Lançamentos já filtrado.
- Mesmo cálculo de domínio da 9.1, na API; a tela não faz conta.

### 6.11 Entrega 9.2: conquista

| Chave | Nome | De quem | Quando |
|---|---|---|---|
| `FIRST_VENTURE` | Primeiro projeto de renda extra | Quem criou | Primeiro projeto que passa para `ACTIVE` |

- Premia começar, não faturar **(decisão do design)**. Segue as regras do Plano 8.3: só com o
  módulo de gamificação ligado, `silent` quando encontrada fora de uma ação local, celebração só
  depois da ação. O Jovem não ganha (não vê projetos).
- `gamification` ouve um evento público de `ventures` (`VentureActivated`) e lê pela interface
  pública, sem `ventures` depender de `gamification`.

### 6.12 Privacidade, auditoria e exportação

- **Exportação:** a 9.1 acrescenta projetos (itens, produtos, passos, status, datas) e sobe a
  versão do formato para **v6** (o 8.3 deixa a v5). A 9.2 acrescenta `ventureId` nos lançamentos e
  sobe para **v7**. O construtor confere a versão vigente antes de executar. `privacy` lê por uma interface
  `VenturesExport`, como `PlanningExport`.
- **Exclusão de conta e de família:** projetos vão em cascata com a família. `created_by` vira
  nulo quando a pessoa sai, como em metas.
- **Auditoria:** `venture.closed` e `venture.deleted` (alvo `VENTURE`, sem valores). Criar,
  editar, marcar passo e simular não são auditados, como limites e aportes.
- **Logs:** nenhum valor, nome de projeto ou nome de produto.
- **Política de privacidade:** sem mudança; projetos são dados financeiros da família, já cobertos.
  O construtor confere o texto vigente.
- **Modelo de dados:** a 9.1 atualiza o §5 de `docs/data-model.md` (tipos novos, `currency`,
  `is_example`, `position`, `venture_steps`, `VENTURE` na auditoria); a 9.2 registra a V31.

## 7. Testes

- **9.1:**
  - cálculo: exemplo do PRD, dois produtos, margem negativa, todos os casos de borda da seção 6.3,
    arredondamento `HALF_EVEN` e `CEILING`; cobertura ≥ 90%;
  - cada modelo dá lucro positivo e payback ≤ 12 meses; `food-sweets` igual ao PRD;
  - limite do Grátis (criar e reabrir), cobrança desligada sem limite;
  - Jovem recebe 403 em toda rota; Leitor não escreve; módulo desligado responde `module.disabled`;
  - `is_example` some ao editar; família em outra moeda cria do modelo sem valores;
  - simulador de investimento contra valores calculados à parte (taxa 0, 1 cenário, 3 cenários,
    50 anos);
  - texto da trilha de investimentos sem palavra de recomendação nem nome próprio; aviso da CVM
    nas duas telas;
  - prévia com espera e sem rede; E2E de criar do modelo, editar e ver a simulação, com axe.
- **9.2:**
  - `venture_id` só em receita e despesa; projeto de outra família responde 422; excluir o projeto
    desmarca;
  - edição com `ventureId` nulo mantém; `clearVenture` tira;
  - Jovem não vê nem manda `ventureId` e não apaga o que existe;
  - offline: lançamento marcado entra na fila e sobe; projeto excluído no meio-tempo cai em
    "precisa de atenção";
  - real vs. planejado com meses sem lançamento, lançamento excluído, compra antes do início,
    projeto encerrado (o período para no mês de `closed_on`), `started_on` corrigido;
  - payback real: alcançado, ainda negativo, nunca negativo, e o ponto mais negativo como
    investimento real;
  - `FIRST_VENTURE` com e sem o módulo de gamificação, `silent`;
  - E2E de marcar um lançamento e ver o real.
- `check:chunks` passando em cada entrega.

## 8. Critério de saída

- **9.1:** no staging, o mantenedor cria um projeto de cada modelo, confere o exemplo do PRD na
  tela e vê o aviso da CVM nas duas telas de investimentos. O limite do Grátis é testado com a
  cobrança ligada no staging, se ela estiver disponível lá.
- **9.2:** no staging, lançamentos marcados (inclusive um feito offline) aparecem no real vs.
  planejado e o payback real bate com a conta à mão.
- **v0.4 sai quando** o exemplo do PRD §7.7 passa como teste e um projeto real da família é
  acompanhado por 1 mês (roadmap).

## 9. Custos

- Nenhum serviço pago novo e nenhuma chamada externa.
- A rota de simulação é chamada enquanto a pessoa digita, com espera de 400 ms: poucas requisições
  leves por edição, sem banco.
- Banco: poucas linhas por família.

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Conta errada no centavo ou equilíbrio para baixo | Uma implementação em Java, exemplo do PRD como teste, cobertura ≥ 90%, `CEILING` só no equilíbrio |
| Valor de exemplo tomado como verdade | Selo por item até editar e faixa no topo |
| Texto que pareça recomendação | Aviso fixo, ordem fixa sem ranking, teste de palavras, revisão de texto no PR, conferência da norma antes do go-live |
| Simulação vira promessa | Nota fixa nas duas simulações; texto neutro no real vs. planejado |
| Investimento real errado porque a pessoa não marcou as compras | Fluxo de caixa sem data de corte; dica na tela quando o acumulado nunca ficou negativo |
| Jovem vê projeto pelo lançamento | Resposta sem `ventureId` para o Jovem; teste por papel |
| Prévia lenta em rede ruim | Espera de 400 ms, último pedido vence, valor anterior fica na tela até chegar o novo |
| Pacote inicial passa de 200 KB | Tudo sob demanda; `check:chunks` no CI |
| Plano 9 começa antes do 8.2 e do 8.3 (Flyway recusa a V20 depois da V30) | Pré-requisito escrito nos dois planos e conferido antes de executar |

## 11. O que fica para o construtor

- Nomes de classes, rotas exatas além das listadas e a forma do JSON.
- Os valores e os passos dos quatro modelos além de `food-sweets`, dentro das regras da seção 6.6.
- Os textos da trilha de investimentos, dentro das regras da seção 6.7, com a data de revisão.
- O layout da lista, do formulário, da prévia e do real vs. planejado, dentro do design system e
  do wireframe.
- Como o cache do app guarda a lista de projetos para o campo offline, seguindo o que categorias e
  contas já fazem.
