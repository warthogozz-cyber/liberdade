# Primeira versão: ajustes, produção e teste com famílias

Roteiro para o mantenedor. Junta, na ordem certa, tudo o que falta para fechar a v0.1.0, pôr no ar
na AWS, testar com algumas famílias e transformar o retorno delas em correções e melhorias. Os
detalhes de cada passo técnico ficam nos runbooks já existentes; este guia diz quando usar cada um
e o que conferir entre eles.

Estado em 2026-11-27: Planos 1 a 11 entregues na `main`, cobrança desligada, nada no ar ainda.

## Resumo em ordem

| #   | Etapa                                 | Quem                  | Tempo de trabalho        | Onde                                                            |
| --- | ------------------------------------- | --------------------- | ------------------------ | --------------------------------------------------------------- |
| 1   | Limpeza do repositório                | Feito, mais 1 ajuste  | 5 min                    | [Parte 1](#1-limpeza-do-repositório)                            |
| 2   | Ajustes rápidos (grupo A)             | Código, em 4 PRs      | 1 a 2 dias               | [Parte 2](#2-ajustes-rápidos-antes-da-produção)                 |
| 3   | Textos legais (grupo B)               | Mantenedor decide     | 30 min de decisão        | [Parte 2](#grupo-b-textos-legais-decisão-do-mantenedor)         |
| 4   | Staging no ar                         | Mantenedor            | 4 h, mais dias de espera | [Parte 4](#4-produção-na-aws)                                   |
| 5   | Passos extras dos Planos 6 a 10       | Mantenedor            | 2 a 3 h                  | [Parte 4](#43-passos-extras-dos-planos-6-a-10)                  |
| 6   | Fechar a v0.1.0 e ir para a produção  | Mantenedor            | 1 h                      | [Parte 3](#3-fechar-a-v010) e [Parte 4](#44-release-e-produção) |
| 7   | Rodada 1: 3 a 5 famílias, 2 semanas   | Mantenedor e famílias | 2 semanas                | [Parte 5](#5-teste-com-famílias)                                |
| 8   | Correções da rodada 1 (v0.1.x)        | Código                | 1 semana                 | [Parte 6](#6-retorno-correções-e-melhorias)                     |
| 9   | Rodada 2: 10 a 15 famílias, 2 semanas | Mantenedor e famílias | 2 semanas                | [Parte 5](#55-rodada-2)                                         |
| 10  | Lojas de apps (Fase 6)                | Plano próprio         | Depois da rodada 2       | [Parte 5](#56-lojas-de-apps-depois)                             |

A regra que vale para tudo: **staging primeiro, produção só pelo release**, com a sua aprovação no
GitHub. Nada vai para a produção direto de um PR.

## 1. Limpeza do repositório

### O que já foi feito

- Branches já mescladas apagadas: 31 locais e 2 no GitHub. Não havia tag nem trabalho perdido.
- O PR #96 fechou duas pendências antigas (#38: o teste de HSTS agora confere o status 200; #40: a
  formatação do `docs/architecture.md`) e destravou o Dependabot:
  - o `commitlint` passou a aceitar o "Bump" que o Dependabot escreve no título;
  - o Dependabot não propõe mais o TypeScript 7 (quebra o `gen:api` da web) nem um `@types/node`
    de versão maior que o Node do projeto (24, em `.node-version`).
- Os PRs do Dependabot foram atualizados depois do #96 e mesclados com os obrigatórios verdes.
- O PR #16 ("chore(main): release 0.1.0") **continua aberto de propósito**: ele é o botão da
  produção (veja a [Parte 3](#3-fechar-a-v010)).

### O que fica com você

1. **Apagar a branch depois do merge, sozinho:** no GitHub, Settings > General > Pull Requests,
   marque "Automatically delete head branches". Assim nenhuma branch mesclada fica para trás.
2. **Pastas de trabalho do app do Claude:** as cópias em `.claude/worktrees/` são do app, não do
   repositório. Apague-as pelo próprio app (Settings > System > Storage) quando quiser liberar
   espaço.

### Pendências que continuam abertas, e por quê

| Issue | O que é                                                    | Quando                                                |
| ----- | ---------------------------------------------------------- | ----------------------------------------------------- |
| #39   | Criar a variável `CLOUDFLARE_ACCOUNT_ID` e tirar a reserva | Passo 12 do go-live; depois um PR pequeno             |
| #36   | Amarrar o papel de deploy da AWS ao `deploy.yml` da `main` | Depois da produção no ar (configuração sua no GitHub) |
| #75   | Teto de tamanho na resposta do Banco Central; alerta falso | Grupo C (depois do retorno)                           |
| #84   | Pontos menores da leitura de notas (10.2)                  | Grupo C, aos poucos                                   |

### Conferir

```bash
git fetch --prune
git branch -r --merged origin/main | grep -v -e 'origin/main' -e 'HEAD'   # deve sair vazio
gh pr list --repo warthogozz-cyber/liberdade                               # só o #16
```

## 2. Ajustes rápidos antes da produção

Uma auditoria da `main` (2026-11-27) procurou o que uma pessoa usando o app pode encontrar e que é
barato de corrigir. O critério: tamanho pequeno, nenhum recurso novo, e algo que a pessoa vê ou que
deixa um dado inconsistente. Quatro itens que pareciam abertos já estavam resolvidos (leitor de QR
isolado, limite de quantidade na lista, avisos de mercado arquivado e trava na junção de produtos).

### Grupo A: código, recomendado antes da produção

Cada linha vira um teste que falha antes da correção. Os itens estão juntados em 4 PRs pequenos, por
área, para revisar e voltar atrás com facilidade.

**PR A1. Lançamentos e projetos**

| Item | O que a pessoa encontra hoje                                                                                        | Correção                                                                        |
| ---- | ------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| 3    | Um Jovem que transforma em transferência um lançamento ligado a um projeto recebe um erro que não consegue resolver | A API tira o projeto do lançamento nesse caso                                   |
| 4    | Um link antigo com projeto, conta ou etiqueta que não existe mais mostra um filtro vazio "Limpar "                  | O filtro some, e o parâmetro sai do endereço, quando o item não existe          |
| 2    | Um projeto encerrado sem nunca ter começado mostra o link "Real vs. planejado" para uma página vazia                | O link só aparece se o projeto já esteve ativo                                  |
| 5    | Com o módulo Projetos desligado, a conquista "Primeiro projeto de renda extra" aparece e não pode ser ganha         | A conquista some com o módulo desligado, como já acontece com Planejamento      |
| 6    | Depois de uma simulação que falhou, os números antigos ficam na tela como se fossem atuais                          | Aviso curto de que não deu para atualizar, com os números marcados como antigos |

**PR A2. Importação e uso offline**

| Item | O que a pessoa encontra hoje                                                                        | Correção                                            |
| ---- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| 7    | Se a rede falha bem na abertura do app, a cópia offline salva no aparelho pode ser apagada          | Uma falha ao abrir a cópia nunca apaga a cópia      |
| 13   | Ao escolher de novo a coluna do valor, o separador decimal que a pessoa escolheu volta ao detectado | O app só sugere o separador até a pessoa mexer nele |

**PR A3. Feira**

| Item | O que a pessoa encontra hoje                                                                                             | Correção                                                      |
| ---- | ------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------- |
| X1   | No passo 2 da nota, a lista de mercados próximos fica vazia sem dizer se está carregando, se não achou nada ou se falhou | Mensagens de "carregando", "nenhum mercado" e "tente de novo" |
| X2   | Se tirar o endereço da família falha, o erro fica escondido atrás da janela de confirmação                               | O erro aparece dentro da janela                               |
| X3   | Ao converter um mercado arquivado, a mensagem diz que ele "ainda está no OpenStreetMap"                                  | Mensagem própria para mercado arquivado                       |
| X4   | Um mercado sem nenhum item em comum aparece com total "R$ 0,00", como se fosse o mais barato                             | "Sem itens em comum" no lugar do total                        |
| X5   | Cada vez que se liga ou desliga um mercado na comparação, a página volta a "Carregando" e pula                           | A comparação anterior fica na tela até a nova chegar          |
| X6   | Quando tirar um item ou limpar a lista falha, a mensagem diz "A lista voltou ao que era", o que confunde                 | Mensagens próprias para tirar e para limpar                   |
| 10   | A confirmação de juntar produtos usa "apelidos" e "catálogo"                                                             | Texto simples, sem jargão                                     |
| 8    | O leitor de QR pode deixar a câmera ligada se a aba abriu escondida, ou travar depois de um erro raro                    | Confere a aba ao abrir e trata os dois erros                  |

**PR A4. Dicas e exportação**

| Item | O que a pessoa encontra hoje                                                                              | Correção                                                                                                              |
| ---- | --------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 11   | Depois de sair e entrar sem recarregar, uma dica pode ficar na tela, e duas dicas podem aparecer juntas   | A dica fecha ao sair, e some quando outra janela abre por cima                                                        |
| 9    | A exportação dos dados mostra unidade e modo da nota como códigos ("UN", "KG")                            | Rótulos em português, como nos outros arquivos da exportação                                                          |
| T    | Este guia dizia que, com a cobrança desligada, as famílias de teste não abririam Importar extrato e Feira | Revisto: não precisa de código. As duas telas já ficam abertas; veja a [Parte 5.3](#53-plano-família-durante-o-teste) |

### Grupo B: textos legais, decisão do mantenedor

Os dois pedem uma nova versão do texto. Com versão nova, o app pede o aceite de novo a quem já
aceitou, e a versão sobe junto na API (`LegalDocuments`) e na web (`legalConfig`).

1. **Item 1, Política de privacidade e importação de extrato.** A política diz que só os
   lançamentos que a pessoa confirma vão para o Liberdade. Na prática, antes da confirmação, o app
   manda as descrições já normalizadas de todas as linhas do extrato para sugerir categorias. É
   uma divergência entre o texto e o app, e a LGPD pede que o texto diga a verdade.
   - **Recomendado:** nova versão da política que diga isso em linguagem simples ("para sugerir a
     categoria, o app envia a descrição de cada linha; ela não fica guardada"), depois de conferir
     no código que a API não guarda essas descrições. Sai antes da produção, porque a produção
     começa com dados reais.
   - Alternativa: mudar o app para pedir as sugestões só depois da confirmação. É mais trabalho e
     piora a importação (as categorias aparecem tarde).
2. **Item 14, Termos de uso e botão de reembolso.** Os termos mandam tocar em "Receber meu dinheiro
   de volta"; o botão do app diz "Pedir o dinheiro de volta". Só importa com a cobrança ligada.
   - **Recomendado:** corrigir junto com a próxima versão dos termos, no dia de ligar a cobrança
     (runbook [billing-sandbox](billing-sandbox.md), seção "Antes da produção"). Não pede um aceite
     novo agora, quando ninguém paga nada.

### Grupo C: depois, com o retorno das famílias

Ficam para a primeira rodada de correções, porque ninguém os vê hoje ou porque são raros:

- #75 (teto da resposta do Banco Central e alerta falso depois de um deploy);
- o teste que compara a versão legal da API com a da web (item 15);
- a ordem das conferências de uma nota repetida com link inválido (item 16, também no #84);
- a quantidade inválida na lista sem aviso (precisa ser reproduzida antes);
- o resto do #84, e o #39 e o #36 da [Parte 1](#pendências-que-continuam-abertas-e-por-quê).

### Conferir

- Os 4 PRs mesclados com os obrigatórios verdes (`api`, `web`, `pr-title`, `dependency-review`,
  `analyze (java-kotlin)`, `analyze (javascript-typescript)`), e o `e2e` verde na `main`.
- O grupo B decidido e, se for o caso, a nova política mesclada.

## 3. Fechar a v0.1.0

"Fechar a versão" quer dizer: a `main` tem tudo o que vai para as famílias e o staging foi
conferido com esse código. O release-please cuida do resto.

1. **Como funciona.** O release-please lê os títulos dos commits da `main` (padrão conventional
   commits) e mantém o PR #16 atualizado com a lista de mudanças. Fazer o merge desse PR cria o tag
   `v0.1.0`, o release no GitHub e dispara o deploy da produção (`release-please.yml`).
2. **Quando fazer o merge do #16:** só depois do passo 15 do go-live (staging conferido, iPhone e
   restauração anotados) e da [Parte 4.3](#43-passos-extras-dos-planos-6-a-10). Antes disso, deixe-o
   aberto: ele se atualiza sozinho a cada merge na `main`.
3. **Versões seguintes.** A configuração tem `bump-minor-pre-major`, então antes da 1.0:
   - commits `fix:` geram 0.1.1, 0.1.2 (correções das rodadas de teste);
   - commits `feat:` geram 0.2.0 (melhorias novas);
   - a 1.0.0 fica para quando o app sair do teste e for aberto ao público.
4. **Congelamento.** Entre o staging conferido e o merge do #16, não mescle nada novo na `main`.
   Se precisar, confira o staging de novo.

## 4. Produção na AWS

### 4.1 Antes de começar

- Leia o [go-live](go-live.md) inteiro uma vez, com a seção "Antes de começar" (ferramentas no
  computador, Git Bash com `MSYS_NO_PATHCONV=1`, contas de teste).
- Crie a conta da AWS **no plano pago** e pegue os créditos grátis: Parte 1 do
  [roteiro de lojas e créditos](stores-and-aws-credits.md#parte-1-aws-no-plano-pago-com-créditos).
  São até US$ 200 (US$ 100 no cadastro e US$ 100 em 5 atividades de boas-vindas), o que cobre cerca
  de 3 meses da infraestrutura.
- Ligue a verificação em duas etapas na AWS, no Cloudflare, no GitHub, no Google Cloud e no
  Registro.br antes de qualquer outro passo.
- Segredos só no SSM (`SecureString`, chave `alias/liberdade`) ou nos segredos dos Environments do
  GitHub. Nunca em arquivo versionado, nunca em chat, nunca em print.

### 4.2 Staging no ar (go-live, passos 1 a 15)

| Passo | O que                                        | Espera                               |
| ----- | -------------------------------------------- | ------------------------------------ |
| 1–2   | Domínio e zona no Cloudflare                 | Até 2 dias para propagar             |
| 3     | Conta da AWS (plano pago, créditos)          | Minutos                              |
| 4–5   | OpenTofu e e-mail de alertas confirmado      | 20 a 30 min (a RDS demora)           |
| 6–7   | Registros A da API e bancos                  | Minutos                              |
| 8     | Clientes OAuth do Google                     | A verificação do app pode levar dias |
| 9     | Parâmetros do SSM                            | Minutos                              |
| 10–11 | Pages e e-mail de privacidade                | Minutos                              |
| 12    | Environments, variáveis e segredos no GitHub | Minutos                              |
| 13–14 | Ligar o deploy e conferir o staging          | Minutos                              |
| 15    | iPhone e teste de restauração                | 1 h                                  |

No passo 12, crie a variável de repositório `CLOUDFLARE_ACCOUNT_ID` (isso destrava o #39). Cada
passo do go-live tem um "Conferir": não pule para o próximo sem ele.

### 4.3 Passos extras dos Planos 6 a 10

O go-live foi escrito no Plano 5. Os planos seguintes trouxeram passos próprios, todos no staging
primeiro:

| Plano | Passo                                                                                                                                                                                                                                                                                          | Runbook                                            |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| 6     | **Cobrança desligada.** Não crie `LIBERDADE_BILLING_ENABLED` nem `LIBERDADE_BILLING_PRESALE_OPEN` na produção: os dois valem `false` sem o parâmetro. O sandbox do Asaas fica para quando for vender.                                                                                          | [billing-sandbox](billing-sandbox.md)              |
| 8     | Conferir os alertas dos indicadores do Banco Central                                                                                                                                                                                                                                           | [indicator-alerts](indicator-alerts.md)            |
| 10.1  | Criar a chave do Google Maps Embed (num projeto do Google Cloud com faturamento, restrita à Maps Embed API e aos domínios de staging e produção, com alerta de orçamento de R$ 1) e definir a variável `GOOGLE_MAPS_EMBED_KEY` em cada Environment. Sem ela, o botão "Ver no mapa" não aparece | Esta tabela (o go-live ainda não lista a variável) |
| 10.1  | `tofu apply` com `infra/live/markets_alerts.tf`, rodar à mão, uma vez, o workflow `osm-markets`, reiniciar a API do staging e conferir `MARKETS_IMPORT ok` no log                                                                                                                              | [markets-alerts](markets-alerts.md)                |
| 10.1  | Conferir que a política de privacidade nova aparece e pede o aceite                                                                                                                                                                                                                            | —                                                  |
| 10.2  | Conferir os estados que permitem ler a nota, definir `LIBERDADE_MARKETS_NFCE_FETCH_STATES` e ler uma nota de verdade no staging                                                                                                                                                                | [nfce-states](nfce-states.md)                      |
| 10.3  | Uma lista de compras real e a comparação entre 2 mercados no staging                                                                                                                                                                                                                           | —                                                  |

Conferências de uso no staging, com uma conta de teste, antes do release:

- entrar com o Google e com passkey, convidar um segundo membro e lançar gastos dos dois;
- orçamento e metas (Plano 7), indicadores na tela (Plano 8), um projeto de renda extra do
  planejamento até o real (Plano 9);
- importar um extrato de teste (sem dados reais) e conferir as sugestões de categoria;
- Feira: endereço da família, lista, nota lida pelo QR e comparação;
- "Como funciona" e "Rever dicas" (Plano 11);
- a exportação dos dados e a exclusão da conta de teste no fim.

### 4.4 Release e produção

Passos 16 e 17 do go-live:

1. Merge do PR #16. Aprove o Environment `production` duas vezes no Actions (jobs `api` e `web`).
2. `bash deploy/smoke.sh https://api.liberdadefamilia.com.br https://app.liberdadefamilia.com.br`
   termina com `smoke: web ok`.
3. Teste S1 com 2 pessoas da família, cronometrado: entrar pelo convite, aceitar os termos e lançar
   o primeiro gasto. Anote os tempos sem nome nem e-mail.

### 4.5 Operação depois do lançamento

| O que                         | Quando                        | Onde                               |
| ----------------------------- | ----------------------------- | ---------------------------------- |
| Voltar para a versão anterior | Um deploy quebrou a produção  | [rollback](rollback.md)            |
| Teste de restauração do banco | Uma vez por mês               | [restore-test](restore-test.md)    |
| Alertas por e-mail            | Ler no mesmo dia              | SNS (passo 5 do go-live)           |
| Gasto do mês                  | Toda segunda-feira, 2 minutos | Billing > Cost and Usage           |
| Logs                          | Quando houver erro            | CloudWatch `/liberdade/production` |
| Créditos da AWS               | 30 dias antes de vencer       | Billing > Credits                  |

Os logs não guardam dado pessoal. Se você vir um nome, e-mail, valor ou descrição num log, isso é
um defeito: abra um issue (sem copiar o dado) e trate como prioridade.

### 4.6 Custos

| Item                   | Custo                                                                                    |
| ---------------------- | ---------------------------------------------------------------------------------------- |
| AWS sob demanda        | ≈ US$ 56,85 por mês (≈ R$ 329), pago pelos créditos no começo                            |
| Domínio                | ≈ R$ 40 por ano                                                                          |
| Cloudflare, GitHub     | R$ 0 no uso atual                                                                        |
| Google Maps Embed      | R$ 0 esperado (a Maps Embed API não cobra por uso; o alerta de R$ 1 avisa se algo mudar) |
| Apple e Google (lojas) | Só na Fase 6: US$ 99 por ano e US$ 25 uma vez                                            |

Orçamento da AWS: US$ 60 por mês, com aviso em 80% do gasto real (sem descontar os créditos). Ao
fim da rodada 2, compare o gasto real com R$ 329 e decida sobre a reserva de 1 ano (passo 18 do
go-live).

## 5. Teste com famílias

### 5.1 Por que começar pelo app instalado da web

O Liberdade já é um PWA: instala na tela inicial do celular, funciona offline e se atualiza sozinho.
Testar assim primeiro tem três vantagens:

- nenhuma revisão de loja: uma correção chega às famílias minutos depois do release;
- o mesmo código das lojas, então o retorno vale para a Fase 6;
- custo zero de conta de desenvolvedor até saber se o app serve para as famílias.

Como instalar (mande este texto às famílias):

- **iPhone:** abra `https://app.liberdadefamilia.com.br` no Safari, toque em Compartilhar e depois
  em "Adicionar à Tela de Início".
- **Android:** abra o mesmo endereço no Chrome e toque em "Instalar app" (ou no menu ⋮ >
  "Instalar app").

### 5.2 Quem convidar

- **Rodada 1:** 3 a 5 famílias que você conhece bem e que falam com franqueza. Pelo menos uma com
  iPhone, uma com Android, uma com um Jovem e uma que use a Feira.
- Cada família entra pelo convite do Dono, aceita os termos e a política no próprio app e sabe que
  é uma versão de teste.
- Diga com clareza, antes do convite:
  - os dados ficam na produção, protegidos como em qualquer uso real (criptografia, exclusão da
    conta a qualquer momento, exportação dos dados);
  - o app educa e compara; não recomenda investimento (aviso da CVM visível nas telas);
  - **nunca** mandar print ou texto com valores, nomes ou dados de conta. Para mostrar um
    problema, descrever a tela ou cobrir os valores.

### 5.3 Plano Família durante o teste

Com a cobrança desligada, Importar extrato e Feira ficam abertos para todas as famílias, sem
precisar do Plano Família. A API libera essas telas enquanto o plano não está à venda, e o app
mostra as duas normalmente. Então as famílias de teste usam tudo desde o primeiro dia, sem nenhum
passo seu.

- Só a troca de tema da Família Fundadora continua pedindo o plano pago. Ela não entra no roteiro.
- Quando você ligar a cobrança, as duas telas passam a pedir o Plano Família. Se ainda houver
  famílias de teste nessa hora, avise antes. Não insira linha de plano direto no banco pelo `psql`:
  não fica trilha de auditoria e um erro de digitação na produção é difícil de notar.

### 5.4 Roteiro das duas semanas

Mande um roteiro curto, uma parte por semana, e pergunte só no fim de cada uma.

**Semana 1, o básico**

1. Instalar o app e entrar (Google ou passkey).
2. Lançar os gastos do dia, por 7 dias.
3. Convidar mais uma pessoa da família.
4. Montar o orçamento do mês.
5. Abrir "Como funciona" quando tiver dúvida.

**Semana 2, o resto**

1. Importar um extrato do mês.
2. Fazer uma lista da Feira, ler uma nota pelo QR e comparar dois mercados.
3. Criar uma meta ou um projeto de renda extra.
4. Usar o app sem internet um dia e ver se os lançamentos sobem depois.

**Perguntas do fim de cada semana** (por e-mail ou conversa, 5 minutos):

1. O que você tentou fazer e não conseguiu?
2. O que te deixou em dúvida ou te fez parar?
3. Em que momento o app te ajudou de verdade?
4. Você continuaria usando? Por quê?
5. O que você mudaria primeiro?

### 5.5 Rodada 2

Comece quando as correções da rodada 1 estiverem na produção e nenhum problema "bloqueia" (Parte 6)
estiver aberto.

- 10 a 15 famílias, agora também de fora do seu círculo próximo.
- O mesmo roteiro e as mesmas perguntas, para comparar.
- No fim, anote: quantas famílias ainda usam na semana 2, os tempos do S1, o gasto real da AWS e a
  lista de melhorias pedidas.

### 5.6 Lojas de apps, depois

A publicação na App Store e no Google Play é a Fase 6 do [roadmap](../roadmap.md), com plano de
código próprio. O [roteiro de lojas](stores-and-aws-credits.md) já lista o trabalho (app empacotado,
login pelo navegador do sistema, passkeys, cookie de sessão, regras de cobrança das lojas, rótulos de
privacidade e licença AGPL).

Quando chegar a hora:

- **iPhone:** TestFlight, primeiro com a família (teste interno) e depois com as famílias de teste
  (teste externo, que passa por uma revisão curta da Apple).
- **Android:** com conta pessoal, o Google exige um teste fechado com pelo menos 12 testadores por
  14 dias seguidos antes da produção. As famílias da rodada 2 podem ser esses testadores: comece o
  teste fechado assim que o app de Android existir.
- Critério para começar a Fase 6: a rodada 2 terminou, as famílias continuam usando e os problemas
  "bloqueia" e "atrapalha" estão resolvidos.

## 6. Retorno, correções e melhorias

### 6.1 Canal

- **Um endereço de retorno** no domínio (por exemplo, `retorno@`), criado no Cloudflare Email Routing
  como o `privacidade@` (passo 11 do go-live), entregando na sua caixa. Assim o retorno não se mistura
  com pedidos de privacidade, que têm prazo pela LGPD.
- Uma conversa rápida no fim de cada semana com cada família da rodada 1.
- Pedidos de exclusão ou de dados vão para `privacidade@`, nunca para o canal de retorno.

### 6.2 Do retorno ao issue

Cada retorno vira um issue no GitHub, usando os modelos que já existem ("Relatar um erro" e "Sugerir uma melhoria"). O
repositório é público: o issue **nunca** leva nome, e-mail, valor, descrição de lançamento, nome de
mercado da família, print ou nome de instituição financeira. Escreva o que aconteceu com dados
inventados.

Classifique cada um em duas dimensões:

| Impacto   | Quer dizer                                                        |
| --------- | ----------------------------------------------------------------- |
| Bloqueia  | A pessoa não consegue fazer o que queria, ou um dado ficou errado |
| Atrapalha | Consegue, mas com esforço, dúvida ou um passo a mais              |
| Incomoda  | Texto, aparência, um detalhe                                      |

| Custo | Quer dizer                        |
| ----- | --------------------------------- |
| P     | Até meio dia, um PR pequeno       |
| M     | Um a três dias                    |
| G     | Mais que isso: vira plano próprio |

Ordem de trabalho:

1. **Bloqueia**, qualquer custo: corrige já e sai num release `fix:` no mesmo dia ou no seguinte.
2. **Atrapalha + P**: entra no lote da semana.
3. **Atrapalha + M** e **Incomoda + P**: lote da semana seguinte.
4. **G** e pedidos de recurso novo: vão para a lista de melhorias da [Parte 6.4](#64-melhorias-novas).

Um defeito de segurança ou de privacidade é sempre "Bloqueia" e nunca vai para issue público: use o
aviso privado de segurança do GitHub (o `config.yml` dos modelos já aponta para ele).

### 6.3 Ritmo das correções

- **Uma vez por semana**, um lote de correções: PRs pequenos, cada um com o teste que falhava,
  mesclados com os obrigatórios verdes. O release-please junta tudo no PR de release; você confere
  no staging e faz o merge (sai uma v0.1.x).
- **Correção urgente** (Bloqueia): o mesmo caminho, sem esperar o lote.
- Se um release quebrar algo, use o [rollback](rollback.md) primeiro e corrija depois.
- No fim de cada rodada, mande às famílias um resumo curto do que mudou por causa delas.

### 6.4 Melhorias novas

Recurso novo não entra como correção. Ao fim da rodada 2:

1. Junte os pedidos parecidos e conte quantas famílias pediram cada um.
2. Escolha os 2 ou 3 que mais ajudam as famílias a cuidar do dinheiro, sem pressão para gastar ou
   arriscar (a regra da gamificação ética vale também aqui).
3. Cada um passa pelo mesmo caminho dos planos anteriores: conversa de desenho, especificação,
   plano de tarefas, execução e conferência no staging. Saem como v0.2.0.
4. Os próximos passos do [roadmap](../roadmap.md) (Fase 2 em diante) entram nessa mesma fila, com a
   ordem decidida pelo retorno.

## 7. Linha do tempo sugerida

| Semana | O que                                                                    |
| ------ | ------------------------------------------------------------------------ |
| 1      | PRs A1 a A4 e decisão do grupo B; go-live passos 1 a 3 (domínio e conta) |
| 2      | Go-live passos 4 a 15 e a Parte 4.3; release v0.1.0 e teste S1           |
| 3–4    | Rodada 1                                                                 |
| 5      | Correções da rodada 1 (v0.1.x)                                           |
| 6–7    | Rodada 2                                                                 |
| 8      | Correções da rodada 2, lista de melhorias e decisão sobre a Fase 6       |

A troca de servidores de nome (passo 2) e a verificação do Google (passo 8) podem esperar dias.
Comece por elas.

## 8. Listas de conferência

### Antes do release v0.1.0

- [ ] Repositório limpo: só o PR #16 aberto, nenhuma branch mesclada sobrando.
- [ ] "Automatically delete head branches" ligado.
- [ ] PRs A1 a A4 mesclados; `e2e` verde na `main`.
- [ ] Grupo B decidido; política nova mesclada, se for o caso.
- [ ] Go-live passos 1 a 15 com todos os "Conferir".
- [ ] Parte 4.3 feita no staging.
- [ ] Cobrança desligada na produção (nenhum parâmetro de cobrança no SSM da produção).
- [ ] Verificação em duas etapas em todas as contas.
- [ ] Orçamento da AWS e lembrete dos créditos criados.

### Antes de convidar a rodada 1

- [ ] Smoke da produção verde e teste S1 anotado.
- [ ] Teste de restauração anotado.
- [ ] Endereço `retorno@` funcionando.
- [ ] Conferido numa família de teste que Importar extrato e Feira abrem com a cobrança desligada (Parte 5.3).
- [ ] Texto de convite com o aviso de versão de teste e a regra de nunca mandar dados.

### Ao fim de cada rodada

- [ ] Todo retorno virou issue, sem dado pessoal.
- [ ] Nenhum "Bloqueia" aberto.
- [ ] Gasto real da AWS anotado.
- [ ] Resumo do que mudou enviado às famílias.
