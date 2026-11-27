# Lojas de apps, créditos da AWS e atualizações ao vivo

Roteiro para o mantenedor. Junta o que é preciso para três coisas: usar bem os créditos grátis da
AWS no plano pago, publicar o Liberdade na App Store e no Google Play, e atualizar a parte web do
app sem esperar a revisão das lojas.

Os fatos de preço e de regra foram conferidos em 2026-11-27 nas páginas da seção "Fontes". Preços
e regras das lojas mudam: confira de novo na hora de pagar ou enviar.

## Decisão

- **Capacitor + PWA** (decidido pelo mantenedor em 2026-11-27, como previsto na
  [ADR-0002](../adr/0002-stack-react-pwa-spring-boot.md)): o mesmo app React vira app de loja com o
  Capacitor (iPhone e Android), e o PWA continua no ar pela web, para quem não quer instalar nada.
- A publicação nas lojas é a Fase 6 do [roadmap](../roadmap.md) ("Lojas e países"). As partes 3 a
  5 deste roteiro dependem de um plano de código próprio, que ainda não existe. As partes 1 e 2
  podem ser feitas antes.

## Quando fazer

| Parte                      | Quando                                                             |
| -------------------------- | ------------------------------------------------------------------ |
| 1. AWS e créditos          | Junto com o passo 3 do [go-live](go-live.md), na criação da conta. |
| 2. Contas de desenvolvedor | Quando o plano da Fase 6 for aprovado (a Apple cobra por ano).     |
| 3. O que o código precisa  | Dentro do plano da Fase 6.                                         |
| 4. Assinatura e envio      | Depois do plano da Fase 6 entregue e conferido no staging.         |
| 5. Atualizações ao vivo    | Depois da primeira versão aprovada nas duas lojas.                 |

## Parte 1. AWS no plano pago com créditos

O [go-live](go-live.md) (passo 3) já cria a conta no plano pago (Paid). A conta do plano gratuito
(Free) é suspensa quando os créditos acabam ou em 6 meses, e a produção cairia junto. Uma conta paga
não volta a ser gratuita.

### Os créditos

| Crédito                   | Valor                             | Validade                          |
| ------------------------- | --------------------------------- | --------------------------------- |
| Cadastro                  | US$ 100                           | Veja "Credits remaining" (abaixo) |
| Atividades de boas-vindas | US$ 20 cada, até US$ 100 (5 de 6) | 12 meses desde a criação da conta |

- As 6 atividades ficam no quadro "Explore AWS" da página inicial do console: criar uma instância
  EC2, usar o playground do Bedrock, criar um orçamento no AWS Budgets, criar um app web no Lambda,
  criar um banco no RDS e criar uma tabela no DynamoDB. Qualquer 5 completam os US$ 100. Faça
  dentro dos 6 primeiros meses da conta.
- O que cada atividade gastar sai dos créditos. Apague o que ela criou logo depois.
- A página de perguntas da AWS diz 12 meses num trecho e 6 meses em outro para o crédito do
  cadastro no plano pago. A data que vale é a que o console mostra.
- Os créditos são em dólar, valem só para os serviços participantes, em todas as regiões, e não
  passam para outra conta.

### Onde acompanhar

1. **Saldo e validade:** Billing and Cost Management > Credits (contas antigas) ou
   Settings > Billing > "Credits remaining" (console novo). Anote a data de validade de cada
   crédito.
2. **Consumo do nível gratuito:** página "Free Tier" do Billing.
3. **Gasto do mês:** quadro "Cost and Usage" da página inicial do console.
4. Crie um lembrete no seu calendário para **30 dias antes** do primeiro crédito vencer.

### Orçamento e alertas

- O OpenTofu já cria o orçamento `liberdade-monthly` (`infra/live/budget.tf`): US$ 60 por mês, com
  e-mail ao passar de 80% do gasto real e ao prever passar de 100%. Ele conta o gasto **sem
  descontar os créditos** (`include_credit = false`), então o alerta mostra o custo de verdade
  mesmo enquanto os créditos pagam a conta. Os avisos vão direto para o e-mail de alertas, sem
  confirmação.
- A AWS também manda e-mails periódicos com o saldo dos créditos.
- O plano pago tem um limite de gasto opcional (a partir de US$ 20, com avisos em 50%, 75% e 90%)
  que **pausa os recursos** até o mês seguinte. Não ligue na conta de produção: pausar derruba o app
  da família. O orçamento acima avisa sem desligar nada.

### Quando os créditos acabam

- A conta passa a ser cobrada no cartão, por uso (pay-as-you-go), sem aviso de troca de plano.
- Custo de referência do go-live: cerca de US$ 56,85 por mês na AWS (ADR-0014). Os mais de 30
  serviços "sempre grátis" continuam grátis dentro dos limites mensais.
- Antes do vencimento: compare o gasto do mês (sem créditos) com o orçamento e decida se algo deve
  diminuir. Não espere a primeira fatura.

## Parte 2. Contas de desenvolvedor

Ligue a verificação em duas etapas nas duas contas. Senhas e chaves nunca vão para o repositório
nem para o chat.

| Loja        | Custo                         | O que exige                                                |
| ----------- | ----------------------------- | ---------------------------------------------------------- |
| App Store   | US$ 99 por ano, renovação     | Apple ID com 2FA; empresa precisa de CNPJ e número D-U-N-S |
| Google Play | US$ 25 uma vez, sem devolução | Conta Google, verificação de identidade e de contato       |

- **Apple:** se a anuidade vencer, o app some da loja para novos downloads e você não consegue
  enviar atualização. A isenção da taxa vale só para ONGs, instituições de ensino e governo.
- **Google, conta pessoal:** para cada app novo, um **teste fechado com pelo menos 12 testadores
  inscritos por 14 dias seguidos** antes de pedir a produção. Conta de organização não tem essa
  exigência (pede D-U-N-S). A família pode ser parte dos 12: comece o teste cedo.
- **Pessoa física ou empresa:** na conta pessoal, o seu nome aparece como vendedor na loja. Com CNPJ
  aparece o nome da empresa. Decida antes de criar: trocar depois dá trabalho nas duas lojas.

## Parte 3. O que o código precisa antes da primeira versão

Estes itens entram no plano da Fase 6. Estão aqui para o mantenedor saber o tamanho do trabalho.

1. **App empacotado, não site embrulhado.** O Capacitor leva o build da web (`web/dist`) dentro do
   app. A opção `server.url`, que só aponta para o site, a documentação do Capacitor diz que não é
   para produção, e a Apple recusa app que é só um site (regra 4.2, funcionalidade mínima).
2. **Login com Google.** O Google bloqueia o login dentro de WebView. O app abre o navegador do
   sistema (ASWebAuthenticationSession no iPhone, Custom Tabs no Android) e volta por link do app.
   A regra 4.8 da Apple pede uma opção de login com privacidade equivalente quando o app usa login
   de terceiros (como "Entrar com a Apple"). Confira a regra na hora; as passkeys podem bastar ou
   não.
3. **Passkeys.** O iPhone pede Associated Domains (`webcredentials:`) e o arquivo
   `apple-app-site-association` no domínio. O Android pede o arquivo `assetlinks.json` e WebView com
   WebAuthn ligado.
4. **Cookie de sessão.** O app de loja roda na origem `capacitor://localhost` (iPhone) ou
   `https://localhost` (Android), e a API fica em outro domínio. O cookie `__Host-sid` vira cookie
   de outro site, que o iPhone bloqueia. O plano decide como a sessão funciona no app (veja também
   [ios-session-cookie.md](ios-session-cookie.md)).
5. **Cobrança.** Na web, a cobrança é pelo Asaas, com Pix e cartão
   ([ADR-0015](../adr/0015-revenue-model-and-billing.md)). Nas lojas, compra de recurso digital
   segue as regras delas. No Brasil, a Apple abriu pagamentos alternativos e links externos em junho
   de 2026 (acordo com o CADE), com comissões próprias; no Google Play, a compra fora do app no
   Brasil está mudando. O plano decide se a primeira versão das lojas mostra a compra, aponta para a
   web ou esconde a tela, depois de ler as páginas oficiais da época.
6. **Privacidade nas lojas.** A App Store pede o rótulo de privacidade e o Google Play pede o
   formulário "Segurança dos dados". Os dois exigem excluir a conta de dentro do app (o Liberdade já
   tem, pela LGPD), e o Google pede também um link na web para isso. Preencha a partir da
   política de privacidade (tela `/privacidade`, textos em `web/src/features/legal/texts/`) e do
   [modelo de dados](../data-model.md).
7. **Licença.** O código é AGPL-3.0 ([ADR-0010](../adr/0010-license-agpl-public-repo.md)). Como
   dono do código, o mantenedor pode publicar. A Free Software Foundation entende que os termos da
   App Store conflitam com a GPL e a AGPL; se o repositório tiver contribuições de outras pessoas,
   peça o consentimento delas antes de publicar na App Store. Na dúvida, consulte um advogado.

## Parte 4. Assinatura e envio

### iPhone

1. Precisa de um Mac com Xcode, ou de um runner macOS no GitHub Actions.
2. No App Store Connect, crie o app (o identificador, por exemplo `br.com.liberdadefamilia.app`, não
   muda depois).
3. Crie uma chave da API do App Store Connect para o CI. Ela vai para os segredos do Environment
   `production` no GitHub, nunca para arquivo.
4. Envie pelo TestFlight, teste com a família, preencha o rótulo de privacidade e mande para
   revisão. A revisão costuma levar de um a poucos dias.

### Android

1. Gere a chave de envio (upload key) e ligue o Play App Signing: o Google guarda a chave que
   assina o app, e você guarda só a de envio. Guarde o arquivo da chave e a senha num cofre de
   senhas, com cópia fora do computador. Perder a chave de envio exige pedir troca ao Google.
2. O formato de envio é o AAB (Android App Bundle).
3. Siga a ordem: teste interno, teste fechado (os 12 testadores por 14 dias, se a conta for pessoal)
   e produção.

### Versões e a API

Quem instalou pela loja pode ficar semanas numa versão antiga. A API precisa continuar aceitando
as versões anteriores do app, e o app precisa avisar quando ficou velho demais para a API. O plano
da Fase 6 define quantas versões a API aceita.

## Parte 5. Atualizações ao vivo da parte web

| Onde                 | Como atualiza                                       | Tempo               |
| -------------------- | --------------------------------------------------- | ------------------- |
| PWA na web           | Cada deploy; o service worker baixa a versão nova   | Minutos             |
| App de loja, sem OTA | Nova versão enviada e revisada pela loja            | De um a poucos dias |
| App de loja, com OTA | O app baixa o pacote web novo (atualização ao vivo) | Minutos             |

### O que pode ir por atualização ao vivo

- Só código web: JavaScript, HTML, CSS, textos e imagens.
- Nada que mude o propósito do app que a loja revisou. A Apple permite baixar código interpretado
  que roda no WebKit desde que não mude o propósito do app nem contorne a segurança do sistema
  (contrato do programa, 3.3.1(B), e regra 2.5.2). O Google Play permite código que roda em WebView
  ou máquina virtual de JavaScript.
- Mudança nativa (plugin novo do Capacitor, permissão nova, ícone, versão do SDK) sempre exige
  versão nova na loja.

### Ferramentas

| Ferramenta       | Situação em 2026-11                                                  |
| ---------------- | -------------------------------------------------------------------- |
| Capgo            | Código aberto (plugin e servidor), pode rodar no próprio servidor    |
| Capawesome Cloud | Serviço pago gerenciado; servidor próprio só para os pacotes         |
| Ionic Appflow    | Sem clientes novos desde 2025-02-11; encerra em 2027-12-31: não usar |

Recomendação: começar **sem atualização ao vivo**, só com versões pelas lojas. Se a família
precisar de correções mais rápidas do que a revisão permite, o plano adiciona o Capgo no próprio
servidor, com pacotes assinados, um canal de staging e um de produção, e volta automática para o
pacote anterior se o novo falhar ao abrir.

## Custos resumidos

| Item                          | Custo                                    |
| ----------------------------- | ---------------------------------------- |
| AWS (depois dos créditos)     | ≈ US$ 56,85 por mês (go-live, ADR-0014)  |
| Apple Developer Program       | US$ 99 por ano                           |
| Google Play                   | US$ 25 uma vez                           |
| Runner macOS no CI (se usar)  | Conforme o plano do GitHub               |
| Atualização ao vivo (se usar) | Capgo no próprio servidor: só o servidor |

## Fontes

Consultadas em 2026-11-27.

- AWS, perguntas do nível gratuito: https://aws.amazon.com/free/free-tier-faqs/
- AWS, plano gratuito e pago: https://docs.aws.amazon.com/accounts/latest/reference/bcm-lite-free-tier.html
- AWS, créditos: https://docs.aws.amazon.com/accounts/latest/reference/bcm-lite-free-tier-credits.html
- Apple, programa de desenvolvedor: https://developer.apple.com/programs/whats-included/
- Apple, regras de revisão: https://developer.apple.com/app-store/review/guidelines/
- Apple, pagamentos no Brasil: https://developer.apple.com/support/payment-options-on-the-app-store-in-brazil
- Google Play, taxa de cadastro: https://support.google.com/googleplay/android-developer/answer/6112435
- Google Play, teste fechado de contas pessoais: https://support.google.com/googleplay/android-developer/answer/14151465
- Capacitor, configuração (`server.url`): https://capacitorjs.com/docs/config
- Capgo, migração do Appflow: https://capgo.app/docs/upgrade/from-appflow-to-capgo/
- Capawesome, atualizações ao vivo: https://capawesome.io/cloud/live-updates/
