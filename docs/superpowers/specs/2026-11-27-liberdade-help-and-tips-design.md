# Ajuda no app: catálogo único, "Como funciona" e cobertura total (design)

Data: 2026-11-27. Origem: auditoria de cobertura da ajuda (59 funcionalidades: 24 cobertas,
22 parciais, 13 sem ajuda), feita depois da 10.3. Decisões do mantenedor tomadas na conversa
de 2026-11-27, todas na opção recomendada.

## 1. Intenção

O mantenedor quer que qualquer pessoa da família, de qualquer idade, entenda cada tela e cada
mecanismo do app sem pedir ajuda a ninguém. A ajuda deve ser curta, simples, rápida e sem
lacunas. Hoje há dicas em 5 telas e tours em 3. Faltam uma ajuda central, a explicação dos
papéis, das telas Início e Lançar, do modo sem internet e da atualização ao vivo, e os tours da
Feira e dos Projetos. Muitos textos também têm jargão.

**Por que agora:** o app vai para produção. Quem chega depois do mantenedor (cônjuge, filhos,
parentes) não teve ninguém explicando nada.

**Sucesso:** toda tela do app tem ajuda ou está numa lista de exceções justificada. Um teste
impede que uma tela nova entre sem ajuda. A pessoa pode rever qualquer dica ou tour quando
quiser.

## 2. Metas, não-metas e antimetas

**Metas**
- Resolver as 5 lacunas principais: ajuda central com "Rever dicas", papéis, Início e Lançar,
  sem internet e ao vivo, tours de Feira e Projetos com o jargão explicado.
- Cobrir as 20 lacunas menores da auditoria (seção 6, itens 11 a 25).
- Reescrever os 36 textos longos ou com jargão (seção 4 da auditoria).
- Usar um catálogo único que alimenta dica, tour, tela "Como funciona" e o teste de cobertura.

**Não-metas**
- Vídeos, animações explicativas, chat de ajuda, busca na ajuda.
- Ajuda em outros idiomas (o app é PT-BR; o catálogo usa as chaves de mensagem de sempre).
- Sincronizar entre aparelhos quais dicas a pessoa já viu (fica no aparelho, como hoje).

**Antimetas (falha mesmo que funcione)**
- Dica que empurra a gastar, investir ou arriscar, ou que usa urgência ("não perca").
- Texto de investimento que soe como recomendação (CVM): só explica o que é cada coisa.
- Ajuda que atrapalha: mais de uma dica por tela ao mesmo tempo, tour com mais de 4 passos,
  dica que volta depois de dispensada (salvo por "Rever dicas" ou "Ver o tour").
- Dica com mais de uma ideia, mais de 2 frases curtas ou mais de 140 caracteres.
- Biblioteca nova de tour (peso no bundle, `check:chunks`, dependência a mais).

## 3. Restrições

- PT-BR, linguagem neutra em gênero ("nas próprias contas", não "nas contas dele").
- Até 140 caracteres por dica ou passo; uma ideia por dica; tours com até 4 passos (PRD US-34).
- Tudo pode ser dispensado; a chave "Dicas" em Ajustes desliga dicas e tours (US-34, já existe).
- Acessível: WCAG 2.2 AA, axe nos dois temas, foco gerenciado no tour (o `Tour` atual já faz),
  `role="note"` nas dicas, nada que dependa só de cor ou animação.
- Rápido: dica e tour continuam carregados sob demanda (`lazy`); a tela "Como funciona" é uma
  rota `lazy`. O `check:chunks` tem de passar.
- Os textos de ajuda são de interface: ficam nos arquivos `messages.pt-BR.ts`, nunca em dados
  do servidor. Nenhuma chamada de API nova.
- LGPD: a tela de Privacidade ganha uma introdução fixa em linguagem simples (PRD, política em
  linguagem simples). CVM: o glossário de investimentos só define termos, sem citar produto,
  banco ou retorno esperado.

## 4. Abordagens consideradas

| Abordagem | Como | Prós | Contras |
|---|---|---|---|
| **Catálogo único (escolhida)** | Um módulo `shared/help` descreve toda a ajuda: dicas de tela, dicas de evento, tours, seções do "Como funciona", glossário e o mapa rota → ajuda. `PageTip`, `PageTour`, a tela e o teste leem dele. | Uma fonte; o teste de cobertura sai de graça; a tela central lista tudo sem repetir texto. | Refatora `PageTip` e `tours.ts`; mais trabalho no início. |
| Acréscimos pontuais | Mais entradas em `TIP_TEXT` e `tours`, tela central escrita à mão. | Menor diff inicial. | Texto duplicado entre tela e dicas; o teste de cobertura teria de manter uma lista à parte; lacunas voltam. |
| Biblioteca de tour | Trocar `Tour` por uma biblioteca pronta. | Recursos prontos. | Dependência nova, peso no bundle, estilo e acessibilidade a revalidar. O `Tour` atual já atende. |

O mantenedor escolheu o **catálogo único**.

## 5. O que foi decidido

### 5.1 Catálogo de ajuda (`web/src/shared/help`)

Substitui o miolo de `shared/tips` (o diretório pode ser renomeado ou mantido; ver seção 6).
O catálogo é código tipado, sem texto solto: cada entrada aponta para chaves de mensagem.

Tipos de entrada:
- **Dica de tela** (`ScreenTip`): `id`, `messageId`. Aparece na primeira visita, como hoje.
- **Dica de evento** (`EventTip`): `id`, `messageId`. Aparece na primeira vez que algo acontece
  (sem internet, chegada ao vivo), não ao abrir uma tela.
- **Tour** (`TourDef`): `id`, `module?`, `steps` (1 a 4) com `anchor` (`data-tour`) e
  `messageId`. Um passo cuja âncora não está na tela é pulado, como hoje.
- **Seção do "Como funciona"** (`HelpSection`): `id`, título, lista de `messageId` e, se for de
  módulo, o `module` e o `tourId`.
- **Termo do glossário** (`GlossaryTerm`): `term` e `definition` (chaves de mensagem).
- **Mapa de rotas** (`routeHelp`): para cada caminho de rota do app, a ajuda que ele tem (dica
  de tela, tour ou ambos) ou uma exceção com o motivo.

O que já existe migra para o catálogo sem mudar o comportamento: as 5 dicas de tela e os 3
tours, com a chave de "já vi" igual (`liberdade.tip:<userId>:<id>` e `tour-<id>`), para quem já
dispensou não ver de novo. Os ids atuais (`dashboard`, `transactions`, `accounts`, `calculator`,
`import`, `tour-planning`, `tour-indicators`, `tour-achievements`) não mudam. Ids novos são
livres, em kebab-case. Dividir o passo `streak` muda o texto, não a chave do tour.

O catálogo também guarda, para cada módulo opcional, a rota da tela principal e o tour dela:

| Módulo | Tela principal | Tour |
|---|---|---|
| `planning` | `/planejar` | `planning` (já existe) |
| `indicators` | `/mais/indicadores` | `indicators` (já existe) |
| `gamification` | `/mais/conquistas` | `achievements` (já existe) |
| `markets` | `/mais/feira` | `markets` (novo, 5.7) |
| `ventures` | `/mais/projetos` | `ventures` (novo, 5.7) |

Assim todo módulo tem tour e todo "Ver o tour" tem destino. Os tours novos precisam de âncoras
`data-tour` nas telas principais de Feira e Projetos.

**Reativo:** as marcas de "já vi" e a chave de dicas viram um pequeno estado observável. Depois
de "Rever dicas" ou "Ver o tour", a dica ou o tour aparece na tela atual sem recarregar nem
trocar de tela.

### 5.2 Tela "Como funciona" (`/mais/como-funciona`)

Novo item em **Mais**, para todos os papéis, com seções curtas, nesta ordem:
1. **Começar**: Início e Lançar.
2. **Papéis**: o que cada papel faz.
3. **Sem internet**: o que acontece com o que a pessoa lança sem sinal.
4. **Ao vivo**: como as telas se atualizam quando alguém muda algo.
5. **Uma seção por módulo ligado** (Planejar, Indicadores, Conquistas, Projetos, Feira), com os
   textos do tour e um botão **"Ver o tour"**. Uma seção de módulo aparece só quando o item do
   módulo aparece no menu Mais para aquela pessoa: módulo desligado não aparece, e Projetos não
   aparece para o Jovem (a rota já é proibida a ele). O Leitor vê as seções dos módulos ligados,
   porque pode olhar tudo.
6. **Palavras**: o glossário.

"Ver o tour" leva à tela do módulo e abre o tour de novo, mesmo já visto e mesmo com a chave
de dicas desligada (foi pedido explicitamente).

### 5.3 "Rever dicas" (Ajustes)

Botão na seção Dicas de Ajustes: apaga deste aparelho todas as marcas de "já vi" da pessoa
(`liberdade.tip:<userId>:*`) e liga a chave de dicas, se estiver desligada. Mostra a confirmação
"Pronto. As dicas vão aparecer de novo." Não toca nas marcas de outra pessoa que use o mesmo
aparelho.

### 5.4 Papéis

- Texto (seção Papéis e dica da tela Família): "Dono cuida de tudo e Adulto lança e planeja.
  Jovem lança só nas próprias contas, e Leitor só olha." Fiel à matriz da US-10 e dentro do
  limite de 2 frases.
- Em **Convidar**, sob o seletor de papel, uma linha fixa que muda com o papel escolhido. Não é
  dica dispensável: é a explicação do campo. Uma frase por papel oferecido, tirada da US-10:
  - Adulto: "Lança, planeja e liga ou desliga módulos."
  - Jovem: "Vê e lança só nas próprias contas, e edita só os próprios lançamentos."
  - Leitor: "Vê tudo e não muda nada."
  - Se o seletor oferecer Dono: "Cuida de tudo, inclusive da família e dos papéis."

### 5.5 Início e Lançar

- Início (`/inicio`): "Aqui está o resumo do mês. Toque em Novo gasto para anotar o que gastou."
  (o texto final usa o rótulo real do botão da tela).
- Lançar (`/lancar`): "Digite o valor, escolha a categoria e salve. Dá para desfazer logo depois."

### 5.6 Sem internet e ao vivo (dicas de evento)

- **Sem internet:** na primeira vez que um lançamento entra na fila do aparelho (o mesmo momento
  em que aparece "Aguardando sinal"): "Sem internet, seus gastos ficam guardados neste aparelho.
  Eles sobem sozinhos quando o sinal voltar." Uma vez por pessoa e aparelho.
- **Ao vivo:** só na primeira vez que chega um lançamento salvo em outro aparelho (o evento
  `onArrival` de `shared/live`, que já separa o que o próprio aparelho salvou). Texto: "Um
  lançamento chegou de outro aparelho. A tela já se atualizou sozinha." Depois disso, só a seção
  Ao vivo do "Como funciona" explica.
- As duas respeitam a chave de dicas e "Rever dicas".
- **Onde moram:** um único anfitrião no layout do app, sempre montado, escuta os dois eventos.
- **Uma por vez:** se já há uma dica de tela ou um tour aberto, ele tem a vez. A dica de evento
  continua "não vista" e aparece na próxima vez que o evento acontecer com nada aberto. Não há
  fila.

### 5.7 Tours de Feira e Projetos, e tour ao ligar módulo

- **Feira** (tela `/mais/feira`, 3 passos): "Veja mercados perto de você e escolha onde
  procurar." / "Registre a nota da compra para guardar os preços." / "Monte a lista de compras e
  compare os mercados."
- **Projetos** (tela `/mais/projetos`, 3 passos): "Projetos mostram quanto sobra de uma renda
  extra." / "Comece por um modelo e ajuste os números." / "Marque lançamentos no projeto para ver
  o real contra o previsto."
- **Ao ligar um módulo** (tela Módulos, US-34): o aviso de módulo ligado ganha o link "Abrir
  <módulo>", que leva à tela principal dele (tabela da 5.1), onde o tour abre. O link não
  aparece se a pessoa não pode entrar na tela (Projetos para o Jovem). Se esta pessoa já tinha visto o
  tour neste aparelho (religou o módulo), ligar apaga essa marca para o tour aparecer de novo.
- **Conquistas:** o passo `streak` vira dois passos (uma ideia cada), e o tour passa a ter 3.

### 5.8 Jargão

- **Projetos:** legenda fixa sob cada número: "Margem: o que sobra de cada venda." "Equilíbrio:
  quanto vender para não ter prejuízo." "Retorno: em quantos meses o investimento volta."
- **Investimentos:** cada sigla (CDB, LCI, LCA, CDI, IPCA, Selic, FGC) explicada na primeira vez
  que aparece numa tela, em uma frase, e também no glossário. Só definição, sem recomendação.
- **Passkey:** "Passkey é entrar com a digital, o rosto ou o PIN do aparelho, sem senha." Na
  primeira menção de cada tela e no glossário.
- **TED:** o rótulo vira "Transferência (TED)".

### 5.9 As 20 lacunas menores e as 36 reescritas

Os textos propostos estão na auditoria (seção 6, itens 11 a 25, e seção 4) e entram como estão,
ajustados só para caber nas restrições da seção 3 e usar os rótulos reais das telas. A
auditoria fica fora do repositório, então a tabela abaixo é a fonte para o construtor.

**Lacunas menores (onde ficam):**

| # | Onde | Tipo | Texto |
|---|---|---|---|
| 11 | Entrar e Privacidade | inline + glossário | Passkey (5.8) |
| 12 | `/mais/privacidade` | introdução fixa | "Aqui você baixa seus dados, vê quem entrou e pode excluir a conta. Excluir leva 30 dias e dá para cancelar." |
| 13 | `/planejar/recorrencias` | dica de tela | "Previstos são contas que você espera pagar. Toque em Confirmar quando acontecerem." |
| 14 | `/mais/feira/lista` | dica de tela | "A lista é da família toda e muda ao vivo em todos os aparelhos." |
| 15 | `/mais/feira/produtos` | dica de tela | "Se dois nomes são o mesmo produto, junte para os preços ficarem em um só." |
| 16 | Troca de família | inline | "Você faz parte de mais de uma família? Troque aqui." |
| 17 | `/planejar/orcamento` | dica de tela | "Você recebe um aviso quando um gasto chega a 80% do limite. É só para ajudar." |
| 18 | `/planejar/metas` | dica de tela | "Guardar soma dinheiro na meta. Se a meta acompanha uma conta, o valor vem do saldo dela." |
| 19 | Gastos fixos x variáveis (Planejar) | legenda fixa | "Fixos se repetem todo mês, como o aluguel. Variáveis mudam, como o mercado." |
| 20 | `/mais/categorias` | dica de tela | "Use Dentro de para criar subcategorias, como Mercado dentro de Casa." |
| 21 | Filtros em Lançamentos | inline | "Use os filtros para achar um gasto por categoria, conta ou tag." |
| 22 | Tags | inline + glossário | "Tags são etiquetas livres. Use para achar gastos parecidos depois." |
| 23 | `/painel/retrato` | dica de tela | "Uma imagem do mês para compartilhar. Mostra só percentuais, nunca valores." |
| 24 | Passos de projeto | inline | "Liste o que falta fazer. Marque como Feito ao concluir." |
| 25 | `/mais/ajustes` | inline | "O app segue a opção Reduzir movimento do seu aparelho." (seção 7) |
| — | `/mais/feira/nota` | dica de tela | "Aponte a câmera para o QR da nota. Os preços ficam guardados; a nota não vira gasto." |
| — | `/mais/modulos` | dica de tela | "Ligue só o que quiser usar. Desligar esconde a parte, mas não apaga nada." |
| — | `/mais/familia` | dica de tela | Papéis (5.4) |

**Reescritas:** todas as linhas do Apêndice A (copiado da seção 4 da auditoria). O plano
transforma cada linha em arquivo, chave e texto novo. As referências `arquivo:linha` são da
auditoria e servem para achar o texto; a chave de mensagem é o que vale.

### 5.10 Teste de cobertura

Um teste de unidade lista todos os caminhos de rota do app (as rotas de `app/routes.tsx` e as
rotas internas de Feira, Projetos e Privacidade) e falha quando um caminho não está em
`routeHelp`, ou quando está mas aponta para uma dica ou tour que não existe. Outro teste confere
os textos de ajuda:
- dicas de tela, dicas de evento e passos de tour: até 140 caracteres e no máximo 2 frases;
- seções do "Como funciona", introduções fixas, legendas e glossário: até 280 caracteres, frases
  curtas;
- cada tour com 1 a 4 passos.

Exceções iniciais, com motivo: `/entrar`, `/convite`, `/privacidade` e `/termos` (públicas,
antes de entrar), `/robo` (página do robô), `/boas-vindas` (já é a introdução), `/mais` (menu),
`/mais/recorrencias` (redireciona), `*` (página não encontrada), `/mais/como-funciona` (é a
própria ajuda). Telas de formulário filhas (`:id/editar`, `comecar`) podem herdar a ajuda da tela
mãe, declarado no mapa.

### 5.11 Correções do sistema de dicas

- Remover de `TipId` os ids sem uso (`planning`, `indicators`, `achievements`): o tipo passa a
  vir do catálogo.
- Uma ajuda por tela: os tours ficam nas telas principais (a tabela da 5.1) e as subtelas têm
  dica de tela. Nenhuma tela tem dica e tour ao mesmo tempo, e o teste de cobertura confere isso.
  As dicas da 5.9 são todas de subtelas, então ficam como estão.
- O emoji "👋" da boas-vindas fica: é o único e combina com o tom amigável.

## 6. O que fica com o construtor

- Nome final do diretório (`shared/help` novo com `shared/tips` reexportando, ou renomear tudo).
- Como o teste de cobertura descobre as rotas internas dos módulos (exportar a lista de caminhos
  de cada `*Routes.tsx` é o caminho mais simples).
- O mecanismo de "Ver o tour": parâmetro na URL, estado de navegação ou apagar a marca antes de
  navegar. O comportamento exigido está em 5.2.
- Onde exatamente cada legenda fixa entra no layout, contanto que fique junto do número ou campo
  que explica.
- Divisão em PRs: até 3 (catálogo, tela e "Rever dicas"; lacunas e tours; reescritas), cada um
  verde por si.
- Testes de unidade de cada componente novo, e axe na tela "Como funciona" nos dois temas
  (e2e, no CI).

## 7. Perguntas ao mantenedor

- **Movimento reduzido (lacuna 25):** o mantenedor escolheu "segue o aparelho" (2026-11-27). Nenhuma chave nova: o app já segue `prefers-reduced-motion`, e Ajustes ganha só a linha que explica isso.

## Apêndice A. Reescritas (da seção 4 da auditoria)

Regra da auditoria: dica acima de ~2 frases curtas ou ~200 caracteres, ou passo de tour que explica mais de uma coisa. Cada texto novo ainda tem de caber nos limites da 5.10 e usar os rótulos reais das telas.

| Arquivo:linha | Problema | Sugestão PT-BR |
|---|---|---|
| `tips:23-24` (achievements.streak) | passo explica 2 coisas | Passo 1: "Sua sequência conta as semanas em que você lançou algo." Passo 2: "Uma semana de folga por mês não quebra a sequência." |
| `tips:7-8` (calculator) | jargão "rendimento", "inflação" | "Veja se o dinheiro rende mais que os preços sobem. É só uma conta, não um conselho." |
| `tips:9-10` (import) | "lido neste aparelho" obscuro | "Seu extrato fica só neste aparelho. Confira cada linha antes de salvar." |
| `tips:19` (planning.reserve) | "meses de custo" abstrato | "A reserva mostra por quantos meses a família se sustenta sem renda." |
| `tips:21` (indicators.cards) | "indicador oficial" | "Cada cartão mostra um número oficial da economia, com a fonte e a data." |
| `planning msg:88-89` (reserve.education) | ~190 car., 2 ideias | "É o dinheiro guardado para imprevistos, como uma conta inesperada ou um mês sem renda. Fica fácil de sacar." |
| `planning msg:22-23` (goals.limitReached) | ~150 car., cita plano que ainda não existe | "No plano Grátis são 3 metas ativas. Conclua ou arquive uma para criar outra." |
| `planning msg:137-138` (projection.note) | "sem recorrência" confuso | "É uma estimativa: usa a média de gastos dos últimos 3 meses." |
| `accounts msg:20-21` (hint cartão) | 2 ideias | "Os gastos deixam o saldo negativo. Para pagar a fatura, transfira de outra conta." |
| `accounts msg:37-40` (archive.body) | ~130 car. | "Some das escolhas. O histórico fica e você pode reativar." |
| `billing msg:19-20` (stalled) | ~150 car., nome do processador de pagamento | "O pagamento ainda não foi confirmado. Volte mais tarde: o plano aparece aqui quando chegar." |
| `billing msg:22-23` (waiting) | idem | "Esperando a confirmação do pagamento. Pode fechar; avisamos aqui." |
| `billing msg:31-32` (refund.body) | ~190 car. | "O plano termina agora e o tema volta ao padrão. Nenhum dado é apagado. O dinheiro volta pelo mesmo meio." |
| `billing msg:34` (refund.keep) | feminino fixo | "Continuar como Família Fundadora" |
| `import msg:4-5` | "como os centavos aparecem" | "Não deu para saber qual coluna é qual. Confira e diga como os centavos aparecem." |
| `import msg:24-25` | "OFX ou CSV" sem explicar | "Escolha a conta e o arquivo de extrato do banco (OFX ou CSV)." |
| `import msg:64-65` (result.counts) | 3 contagens em 1 frase | Uma linha por contagem: "Entraram X. Confirmaram previstos: Y. Já existiam: Z." |
| `import msg:69-72` | "O servidor recusou" | "Não foi aceito, então tentar de novo não resolve." |
| `import msg:43-44` | "Pagamento da fatura?" e "Saldo?" crípticos | "Pagamento de fatura?" / "Isto é saldo?" mais 1 linha explicando |
| `privacy msg:43-44` (deleteFamily.body) | ~200 car. | "A família e tudo nela somem em 30 dias. Até lá, só você, o Dono, pode cancelar." |
| `privacy msg:49-50` (deleteAccount.body) | ~195 car. | "Em 30 dias sua conta some. Seus lançamentos ficam na família, sem o seu nome. Até lá dá para cancelar." |
| `privacy msg:21-22` (revokeBody) | ~170 car. | "O Jovem sai da família: sem o consentimento do responsável, o app não pode guardar os dados dele." |
| `members msg:90-91` (home.help) | ~150 car. | "Serve para achar mercados perto de casa. Guardamos só um ponto aproximado." |
| `markets msg:74-75` (osmHelp) | ~150 car. | "Mercado que não está no mapa? Cadastre no OpenStreetMap e ele aparece aqui depois." |
| `markets msg:79-80` (convert.help) | ~170 car., "CNPJ, links, encarte" | "Este mercado saiu do mapa. Digite o nome e marque a posição para a família continuar usando." |
| `markets msg:150-151` (scanner.paused) | ~190 car. | "A câmera desligou. Toque em Ler o QR, ou cole o link ou digite a chave." |
| `markets msg:123-124,130-131` | "IP", "cookies" | "O Google vai saber que você abriu o mapa e pode guardar dados sobre você." (manter o aviso, em linguagem simples) |
| `markets msg:136-151,181` | "nota fiscal de consumidor", "SEFAZ", "homologação", "chave de 44 números" | "Esta nota não é de compra no mercado." / "Abrir a nota no site da Fazenda (SEFAZ)" |
| `markets msg:180` | "leitura automática ainda não existe para notas de {uf}" | "Ainda não lemos notas de {uf}. Digite os itens você mesmo." |
| `ventures msg:62-63` (exampleBanner) | ~110 car. | "Itens com o selo exemplo vêm do modelo. Ajuste para a sua realidade." |
| `ventures msg:74-75` (conflictBody) | ~120 car. | "Outra pessoa mudou este projeto. O que você digitou fica; recarregue para ver a versão salva." |
| `ventures msg:87-90,121-132` | jargão: Equilíbrio, Payback, ROI, Margem | Rótulo mais frase: "Margem: o que sobra de cada venda." "Equilíbrio: quanto vender para não ter prejuízo." "Retorno: em quantos meses o investimento volta." |
| `ventures/investments/texts.ts:22-23,40,44-45,52,57` | CDB, LCI, LCA, CDI, IPCA, Selic, FGC, custódia, alíquota; campos de 200+ car. | 1 frase por campo; explicar a sigla na 1ª vez ("FGC: fundo que protege seu dinheiro até certo limite") |
| `i18n:101-102` (undoOffline) | ~85 car., 2 ideias | "Sem internet. Para desfazer, exclua em Lançamentos quando o sinal voltar." |
| Jargão geral: "passkey" (`auth/signIn.messages.pt-BR.ts:6`, `i18n:33`, `members msg:66,72`, `privacy msg:26-40`), "TED" (`i18n:73`) | nunca explicados | Explicar 1 vez: "Passkey: entrar com a digital, o rosto ou o PIN do aparelho." "TED" vira "Transferência (TED)". |
| Tom: `i18n:11` | único emoji do app ("👋") | manter ou padronizar |
| Tom: `billing msg:30,33` | "Receber meu dinheiro de volta" (1ª pessoa) enquanto o resto fala com "você"/neutro | "Pedir o dinheiro de volta" |
