# Liberdade — PRD do MVP

> **Status:** rascunho para revisão · **Data:** 2026-11-02 · **Escopo:** Fase 1 (v0.1 → v0.4)
>
> Este documento é o spec do produto. Arquitetura, dados, design e decisões técnicas estão nos
> documentos listados na seção 13.

## 1. Visão

**Liberdade** é um app web (PWA), depois mobile e desktop, que é ao mesmo tempo:

1. **A central financeira da família**: renda, gastos, reserva, metas e, mais adiante,
   investimentos e patrimônio.
2. **Um copiloto de renda extra**: tirar do papel um projeto (doces gourmet, produto digital,
   serviço) com custos, preço, margem, ponto de equilíbrio, ROI e payback.

**Princípio:** poderoso por baixo, simples na superfície. O app começa com o mínimo (registrar um
gasto e ver o saldo); o resto a família liga quando quiser. Ajuda e dicas são opcionais e podem ser
desligadas.

## 2. Público do MVP

O primeiro público é a família do mantenedor (dois adultos e jovens), em uso diário real e sem
cobrança. Abrir para outras famílias só depois da v0.4, com revisão de LGPD, marca (INPI) e custos.

| Persona | Papel no app | O que precisa |
|---|---|---|
| Organizador(a) | Dono | Configura família, contas e metas; quer a visão do mês em segundos |
| Adulto casual | Adulto | Registrar o gasto rápido no mercado, às vezes sem sinal |
| Jovem | Jovem | Registrar a própria mesada e gastos, acompanhar metas da família, aprender |
| Acompanhante | Leitor | Ver sem alterar (ex.: parente que ajuda no planejamento) |

## 3. Critérios de sucesso

| # | Critério | Como medimos |
|---|---|---|
| S1 | Usuário novo registra o 1º gasto em menos de 60 s sem abrir tutorial | Teste E2E cronometrado + teste com 2 pessoas da família |
| S2 | Todo módulo além do núcleo pode ser ocultado sem quebrar nada | Teste E2E com todos os módulos opcionais desligados |
| S3 | Todo dado externo mostra fonte e data | Componente `SourceStamp` obrigatório + teste de UI |
| S4 | Cálculos financeiros têm testes e batem com exemplos manuais | Exemplos da seção 7 viram testes; cobertura do domínio ≥ 90% |
| S5 | CI verde em todo PR; deploy reproduzível | `main` protegida; deploy só pelo pipeline |
| S6 | A família registra gastos em pelo menos 3 de cada 4 semanas | Contagem interna, sem analytics de terceiros |

## 4. Escopo

### Dentro do MVP

| Versão | Entrega | RFs |
|---|---|---|
| v0.1 Núcleo | Login sem senha, família e convites, papéis, contas, categorias, receitas, gastos e transferências (inclusive offline), saldo, painel do mês, onboarding em 60 s, módulos ligáveis, exportar e excluir conta | RF-01, RF-02 (parcial), RF-04 Controle, RF-05 (parcial) |
| v0.2 Planejamento | Orçamento por categoria, metas, reserva de emergência, recorrências, tags | RF-02, RF-14 (metas em família) |
| v0.3 Indicadores e jogo | Selic, CDI, IPCA e dólar com fonte e data; calculadora educativa; sequências, conquistas e celebrações | RF-04 Tracking, RF-14 (parcial) |
| v0.4 Novo Projeto | Projeto de renda extra com custos, preço, margem, ponto de equilíbrio, ROI, payback e real vs. planejado | RF-04 Novo Projeto, RF-09 (3 modelos) |

### Fora do MVP (fases 2+, cada uma com spec próprio)

Construtor com fórmulas reativas (RF-03); multimoeda com cotação nas contas (RF-02); catálogo de
investimentos (RF-08); ranking de bancos (RF-07); biblioteca completa de renda extra (RF-09);
referências e fontes (RF-10); agente de IA (RF-11); geolocalização e preços (RF-12); redes sociais
(RF-13); modos Dicas e Notícias (RF-04); trilhas de aprendizagem (RF-06); níveis e desafios
(RF-14); contas privadas entre adultos; importação de extrato (OFX/CSV); Open Finance; apps nas
lojas.

## 5. Glossário

- **Família**: o workspace. Tudo pertence a uma família. Um usuário pode estar em mais de uma.
- **Membro**: usuário dentro de uma família, com um **papel** (Dono, Adulto, Jovem, Leitor).
- **Conta**: onde o dinheiro está (corrente, poupança, dinheiro, cartão de crédito, carteira
  digital, investimento).
- **Lançamento**: receita, gasto ou transferência entre contas.
- **Módulo**: conjunto de funções que a família liga ou desliga (Planejamento, Indicadores,
  Conquistas, Novo Projeto). O núcleo (Controle) está sempre ligado.

## 6. Requisitos por versão

Formato: história de usuário, seguida de critérios de aceite verificáveis.

### v0.1 — Núcleo

**US-01 Entrar sem senha** (RF-01)
Como membro, quero entrar com Google ou passkey, para não precisar criar senha.
- Ao tocar "Entrar com Google" e autorizar, volto logado ao app.
- O app pede só `openid`, `email` e `profile` ao Google e nunca pede nem guarda senha.
- Depois do primeiro login, o app oferece (opcional) "Criar passkey neste aparelho".
- A sessão expira após 30 dias sem uso. Ações sensíveis (excluir conta, mudar papéis, exportar
  dados) pedem reautenticação se o último login tiver mais de 10 minutos.

**US-02 Família criada sozinha** (RF-01)
- No primeiro login sem convite, o app mostra a tela de Boas-vindas com o nome "Família
  <sobrenome do Google>" já preenchido e editável (sem sobrenome, "Minha família") e o botão
  "Começar".
- Ao tocar em "Começar", o app cria a família, a conta "Carteira" (dinheiro, saldo R$ 0,00) e as
  categorias padrão (seção 8) e abre a tela de novo gasto. O nome pode ser mudado depois.
- A moeda base é BRL.

**US-03 Primeiro gasto em menos de 60 s** (RF-02, critério S1)
- Depois de criar a família, o app abre direto em "Novo gasto", com teclado numérico.
- O valor é digitado em centavos, da direita para a esquerda: 1, 2, 3, 4 viram R$ 12,34.
- A categoria é escolhida por chips (as 6 mais usadas + "Mais"). A data padrão é hoje e a conta
  padrão é a última usada.
- Ao salvar, aparece o aviso "Gasto salvo" com "Desfazer" por 5 s, e o saldo atualiza na hora.
- Do toque em "Entrar com Google" até o gasto salvo: menos de 60 s para um usuário novo.

**US-04 Receitas, gastos e transferências** (RF-02)
- Campos: tipo, valor (maior que zero), data, conta, categoria (não se aplica a transferência),
  descrição opcional e "quem" (membro; padrão: eu).
- A transferência move dinheiro entre duas contas da família e não altera o saldo total.
- Editar e excluir exigem conexão. Excluir pede confirmação e permite desfazer por 5 s.
- Se outro membro editou o lançamento antes, aparece "Este lançamento mudou" com a versão atual;
  nada é sobrescrito em silêncio.

**US-05 Lançar sem internet** (RF-02)
- Sem conexão, salvar funciona e o item aparece com o selo "Aguardando sinal".
- Ao reconectar ou reabrir o app, os itens são enviados em ordem e nenhum é duplicado, mesmo com
  reenvio.
- Se o servidor recusar um item, ele ganha o selo "Precisa de atenção" com ação para corrigir. Nunca
  é descartado em silêncio.
- Editar e excluir ficam desabilitados offline, com explicação.
- A fila sobrevive a fechar o app e a reiniciar o celular.

**US-06 Contas** (RF-02)
- Tipos: conta corrente, poupança, dinheiro, cartão de crédito, carteira digital e investimento
  (saldo informado à mão no MVP).
- Saldo inicial na criação. Conta com lançamentos é arquivada, não excluída.
- Cartão de crédito: gastos aumentam a fatura (saldo negativo); pagar a fatura é uma transferência
  da conta corrente para o cartão.
- Uma conta pode ter um **titular** (membro). O Jovem só vê contas em que é titular.

**US-07 Categorias** (RF-02)
- O app já vem com as categorias padrão (seção 8). Dá para criar, renomear, escolher ícone e cor,
  e arquivar. Subcategorias de um nível.

**US-08 Painel do mês** (RF-02, RF-04 Controle)
- Mostra saldo total, receitas, gastos e resultado do mês (receitas − gastos).
- Três gráficos: gastos por categoria (rosca), saldo acumulado dia a dia (linha) e gastos do mês
  vs. mês anterior por categoria (barras).
- Dá para trocar o mês. Tocar em uma categoria filtra a lista de lançamentos.
- Cor nunca aparece sozinha: sempre com sinal, ícone ou texto.

**US-09 Convidar membros** (RF-01)
- Dono e Adulto geram link de convite com papel definido (o Adulto não convida Dono). O link vale
  7 dias, é de uso único e pode ser revogado.
- O convidado entra com Google ou cria uma passkey direto. O Jovem não precisa de e-mail.
- Para o papel Jovem, quem convida (Dono ou Adulto) declara ser responsável legal e dá o
  consentimento parental ao gerar o link (LGPD art. 14); sem essa confirmação o link não é gerado.
- Ao entrar, o Jovem ganha a conta "Carteira de <apelido>" (dinheiro, R$ 0,00), da qual é
  titular, para registrar a mesada e os próprios gastos.
- Recuperação de acesso para quem só tem passkey: Dono ou Adulto gera um link de recuperação de uso
  único, válido por 24 h, registrado na auditoria e avisado ao membro.

**US-10 Papéis e permissões** (RF-01)

| Ação | Dono | Adulto | Jovem | Leitor |
|---|:-:|:-:|:-:|:-:|
| Ver contas, saldos e lançamentos | todas | todas | só contas em que é titular | todas |
| Lançar | ✓ | ✓ | só nas contas em que é titular | — |
| Editar e excluir lançamentos | ✓ | ✓ | só os próprios | — |
| Contas, categorias, orçamentos, metas, projetos | ✓ | ✓ | vê metas da família | vê |
| Convidar membros | ✓ | ✓ (exceto Dono) | — | — |
| Mudar papéis e remover membros | ✓ | — | — | — |
| Ligar e desligar módulos | ✓ | ✓ | — | — |
| Exportar dados da família, excluir a família | ✓ | — | — | — |

Para o Jovem, saldo total e painel consideram só as contas em que ele é titular.

**US-11 Módulos ligáveis** (RF-04, critério S2)
- Em Ajustes > Módulos, cada módulo aparece com uma linha de descrição e uma chave liga/desliga,
  valendo para a família toda.
- Desligar oculta menus, cards e avisos sem apagar dados. Religar traz tudo de volta.
- Na v0.1 só existe o núcleo, mas o mecanismo já existe e é testado.

**US-12 Meus dados** (LGPD)
- Exportar meus dados (qualquer membro) ou os da família (Dono) em JSON e CSV.
- Excluir minha conta: pede reautenticação, dá 30 dias de carência canceláveis e depois apaga de
  vez. Se eu for o único Dono, preciso transferir a família ou excluí-la antes.
- Ver e revogar consentimentos; ver o histórico de acessos e alterações sensíveis.

**US-13 Primeiros passos opcionais** (RF-05 parcial)
- Depois do primeiro gasto, uma lista "Primeiros passos" sugere: adicionar contas, convidar a
  família e criar passkey. Dá para dispensar e ela não volta.

### v0.2 — Planejamento

**US-20 Orçamento mensal por categoria**
- Limite por categoria e mês; botão "copiar do mês anterior".
- Barra de progresso com aviso gentil a 80% e a 100%. O vermelho só aparece acima de 100%, sempre
  com texto ("passou R$ 60,00").

**US-21 Metas** (RF-14 metas em família)
- Nome, valor-alvo, prazo opcional e ícone. Aportes manuais ou conta vinculada.
- Mostra progresso e aporte mensal sugerido (seção 7.4). Celebração ao atingir.

**US-22 Reserva de emergência**
- A família marca quais contas são reserva. O app mostra quantos meses de gastos ela cobre
  (seção 7.5). A meta padrão é 6 meses e pode ser editada. Vem com texto educativo curto.

**US-23 Recorrências**
- Regra semanal, mensal ou anual. Gera lançamentos **previstos**, que o membro confirma com um
  toque. O app nunca lança sozinho um dinheiro que não saiu.

**US-24 Tags**
- Tags livres nos lançamentos e filtro por tag.

### v0.3 — Indicadores e gamificação (módulos "Indicadores" e "Conquistas")

**US-30 Indicadores** (RF-04 Tracking)
- Séries: Selic meta (BCB SGS 432), CDI anualizado (SGS 4389), IPCA mensal (SGS 433), IPCA 12 meses
  (SGS 13522) e dólar PTAX de venda (API PTAX do BCB).
- Cada card mostra valor, variação, **fonte e data**. A coleta é diária. Se falhar, o card mantém o
  último valor com "atualizado em …" e ganha o selo "desatualizado" após 3 dias úteis.
- Aviso visível: "Conteúdo educativo. Não é recomendação de investimento."

**US-31 Calculadora de rendimento real** (educativa)
- O usuário informa um rendimento nominal e o app mostra o rendimento real descontando o IPCA de
  12 meses (seção 7.6). Não sugere produto nem instituição.

**US-32 Sequência semanal** (RF-14)
- Uma semana conta se houve ao menos um registro. Uma semana por mês é "congelada" automaticamente
  se faltar. O app nunca mostra "você perdeu a sequência".

**US-33 Conquistas e celebrações** (RF-14)
- Conquistas: primeiro lançamento, 4 semanas seguidas registrando, primeira meta criada, meta
  atingida, reserva de 1, 3 e 6 meses, mês dentro do orçamento, primeiro projeto de renda extra.
- Celebração visual curta, que respeita `prefers-reduced-motion`. Nenhuma conquista premia gastar,
  se endividar ou arriscar.

### v0.4 — Novo Projeto (módulo "Novo Projeto")

**US-40 Criar projeto de renda extra**
- Nome, tipo, investimento inicial (itens), custos fixos mensais (itens) e produtos com custo
  variável unitário e preço de venda.

**US-41 Simulação**
- Mostra margem de contribuição, margem %, ponto de equilíbrio (unidades e R$), lucro previsto para
  um volume informado, ROI em 12 meses e payback (seção 7.7).

**US-42 Real vs. planejado**
- Vendas e custos reais são lançamentos normais da família marcados com o projeto. O app compara
  real e planejado e mostra o payback real.

**US-43 Modelos prontos** (RF-09 parcial)
- Três modelos: doces gourmet, produto digital e serviço, com valores de exemplo marcados "exemplo:
  ajuste para a sua realidade".

## 7. Regras de cálculo

Todas valem com aritmética decimal exata (ver [ADR-0006](adr/0006-money-representation.md)). Cada
exemplo abaixo vira um teste automatizado.

### 7.1 Saldo de conta
`saldo = saldo inicial + receitas − gastos + transferências recebidas − transferências enviadas`

Exemplo: Conta corrente com saldo inicial R$ 1.000,00, receita R$ 5.000,00, gasto R$ 1.234,56 e
transferência de R$ 500,00 para a Poupança (saldo inicial R$ 0,00).
Corrente = **R$ 4.265,44**; Poupança = **R$ 500,00**; total = **R$ 4.765,44**, igual a
1.000,00 + 5.000,00 − 1.234,56. A transferência não altera o total.

### 7.2 Rateio sem perder centavo (maior resto)
Divide-se em centavos, cada parte recebe o quociente e os centavos que sobram vão, um a um, para as
partes com maior resto; em empate, pela ordem da lista.

Exemplo: R$ 100,00 em 3 partes → **R$ 33,34 + R$ 33,33 + R$ 33,33** = R$ 100,00.

### 7.3 Orçamento
`uso % = gastos da categoria no mês ÷ limite × 100`, exibido com arredondamento para baixo em
inteiro.

Exemplos com limite de Mercado R$ 1.200,00: gastos R$ 960,00 → **80%** (aviso gentil); gastos
R$ 1.260,00 → **105%**, "passou **R$ 60,00**".

### 7.4 Aporte mensal sugerido para meta
`aporte = (alvo − guardado) ÷ meses até o prazo`, arredondado **para cima** ao centavo, para que a
meta seja atingida.

Exemplos com alvo R$ 12.000,00 e R$ 3.000,00 guardados: prazo de 9 meses → **R$ 1.000,00/mês**;
prazo de 7 meses → 9.000,00 ÷ 7 = 1.285,714… → **R$ 1.285,72/mês**.

### 7.5 Reserva de emergência (meses cobertos)
`meses = saldo das contas de reserva ÷ média dos gastos dos 3 últimos meses completos`
(transferências não contam), arredondado **para baixo** com uma casa decimal, para nunca
superestimar. Com menos de 3 meses de histórico, usa o custo mensal informado pela família; se ele não
foi informado, o app pede esse valor em vez de mostrar um número.

Exemplo: gastos de R$ 4.000,00, R$ 4.500,00 e R$ 5.000,00 → média R$ 4.500,00; reserva
R$ 18.000,00 → **4,0 meses**; para a meta de 6 meses faltam **R$ 9.000,00**.

### 7.6 Rendimento real (educativo)
`real = (1 + nominal) ÷ (1 + IPCA 12 meses) − 1`

Exemplo hipotético: nominal 10% e IPCA 4% → 1,10 ÷ 1,04 − 1 = **5,77%** (exibido com 2 casas).

### 7.7 Novo Projeto
- Margem de contribuição unitária = preço − custo variável unitário.
- Margem % = margem de contribuição ÷ preço.
- Ponto de equilíbrio (unidades) = custos fixos mensais ÷ margem de contribuição, arredondado
  **para cima**.
- Lucro mensal = unidades × margem de contribuição − custos fixos mensais.
- ROI no período = (lucro acumulado no período − investimento inicial) ÷ investimento inicial.
- Payback = primeiro mês em que o lucro acumulado ≥ investimento inicial (payback simples, sem
  desconto). Se o lucro mensal for ≤ 0, o app mostra "não se paga com esses números".

Exemplo (doces gourmet): custo variável R$ 1,80, preço R$ 4,50, custos fixos R$ 270,00/mês,
investimento inicial R$ 800,00, venda prevista de 300 unidades/mês.
Margem = **R$ 2,70** (**60%**); ponto de equilíbrio = 270,00 ÷ 2,70 = **100 unidades**; lucro
mensal = 300 × 2,70 − 270,00 = **R$ 540,00**; payback = **2 meses** (540,00 no 1º mês, 1.080,00 no
2º); ROI em 12 meses = (6.480,00 − 800,00) ÷ 800,00 = **710%**.

## 8. Categorias padrão

- **Gastos:** Moradia, Mercado, Alimentação fora, Transporte, Saúde, Educação, Lazer, Contas e
  serviços, Compras, Pets, Presentes e doações, Impostos e taxas, Outros.
- **Receitas:** Salário, Renda extra, Rendimentos, Reembolsos, Presentes, Outros.

## 9. Requisitos não funcionais

| Tema | Requisito |
|---|---|
| Desempenho | Lançar um gasto responde em menos de 100 ms (otimista, local). API p95 < 300 ms nas operações comuns. Primeira carga < 3 s em 4G. |
| Disponibilidade | Meta de 99% ao mês (uma instância, uso familiar). Backup automático com restauração a qualquer ponto dos últimos 7 dias (RPO ≈ 5 min, RTO ≤ 4 h). |
| Offline | Lançar funciona sem conexão; leitura usa o último dado em cache com aviso de data. |
| Acessibilidade | WCAG 2.2 AA, tema claro e escuro, `prefers-reduced-motion`, alvos de toque ≥ 44 px. |
| Plataformas | Últimas 2 versões de Chrome, Edge, Firefox e Safari; Android 10+ e iOS 16.4+ como PWA. |
| i18n | Todos os textos externalizados; pt-BR primeiro; datas e moeda via `Intl`. |
| Privacidade | Sem anúncios e sem analytics de terceiros. Logs sem valores nem descrições de lançamentos. |
| Segurança | OWASP ASVS 5.0 nível 2 como referência (detalhes em [arquitetura](architecture.md#9-segurança)). |

## 10. Conformidade e ética

- **LGPD:** dados hospedados no Brasil; base legal de execução do serviço e consentimento para o que
  for opcional; consentimento parental para menores (art. 14); exportação e exclusão de conta;
  criptografia em trânsito e em repouso; trilha de auditoria; política de privacidade em linguagem
  simples, publicada antes da v0.1 ir para produção.
- **CVM:** indicadores e qualquer conteúdo de investimento são educativos, com aviso visível. O app
  compara, explica e simula; não recomenda valores mobiliários. A norma vigente será conferida
  antes da fase de investimentos (fase 4).
- **Gamificação ética:** sem dark patterns, sem urgência falsa, sem punição por ausência, sem
  notificações entre 21h e 8h e nenhuma recompensa ligada a gastar ou arriscar.

## 11. Rastreabilidade

| RF | Onde entra |
|---|---|
| RF-01 Estrutura e colaboração | v0.1 (família, membros, papéis); grupos e organizações na fase 2 |
| RF-02 Núcleo financeiro | v0.1 e v0.2; multimoeda, cripto e investimentos na fase 4 |
| RF-03 Construtor | Fase 2 |
| RF-04 Modos de uso | Controle (v0.1), Tracking (v0.3), Novo Projeto (v0.4); Construtor (fase 2); Dicas (fase 3); Notícias (fase 4) |
| RF-05 Guias passo a passo | Onboarding (v0.1), guia do Novo Projeto (v0.4); demais na fase 3 |
| RF-06 Trilhas | Fase 3 |
| RF-07 Ranking de bancos | Fase 4 |
| RF-08 Catálogo de investimentos | Fase 4 |
| RF-09 Biblioteca de renda extra | 3 modelos (v0.4); biblioteca na fase 2 |
| RF-10 Referências e fontes | `SourceStamp` desde a v0.3; curadoria na fase 4 |
| RF-11 Agente de IA | Fase 3 |
| RF-12 Geolocalização e preços | Fase 5 |
| RF-13 Redes sociais | Fase 5 |
| RF-14 Gamificação | Metas em família (v0.2); sequências, conquistas, celebrações (v0.3); níveis e desafios (fase 2) |

## 12. Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Escopo gigante | Nunca entregar | Versões pequenas e usáveis; cada fase com spec próprio |
| Regulação CVM | Sanção se virar recomendação | Tom educativo, aviso visível, revisão da norma antes da fase 4 |
| Dados de menores (LGPD art. 14) | Tratamento irregular | Consentimento parental, apelido em vez de nome, sem e-mail obrigatório |
| Custo da AWS | ≈ R$ 311/mês sem retorno direto | AWS só liga no fim da v0.1; reserva de 1 ano reduz para ≈ R$ 220 |
| Nome "Liberdade" é palavra comum | Marca fraca no INPI, buscas poluídas | Sem impacto no uso familiar; revisar antes de abrir ao público |
| Instância única | Queda derruba o app | Backups com restauração pontual; recriação por IaC; migração para ECS documentada |
| Dependência do Google | Sem login se o Google cair | Passkeys como segundo caminho |
| Projeto dentro do OneDrive | Sincronização de milhares de arquivos de build, travas no `.git` | Mover a pasta para fora do OneDrive antes do primeiro código |

## 13. Documentos relacionados

- [Arquitetura (C4)](architecture.md)
- [Modelo de dados](data-model.md)
- [Design system](design-system.md)
- [Wireframes do fluxo principal](wireframes.md)
- [Roadmap](roadmap.md)
- [Decisões de arquitetura (ADRs)](adr/README.md)
