# Liberdade · Plano 5: No ar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A v0.1 vai para a AWS em São Paulo, com staging e produção, domínio próprio, HTTPS, política
de privacidade e termos publicados e aceitos, deploy só pelo pipeline (critério S5) e a família
usando o app por 2 semanas. Antes de a família entrar, o staging confirma o cookie de sessão no
iPhone (runbook `docs/runbooks/ios-session-cookie.md`). O plano também fecha as pendências herdadas
do Plano 4 e a gestão de passkeys.

**Architecture:** A infraestrutura é código OpenTofu em `infra/`, aplicado pelo mantenedor na
máquina dele: uma EC2 t4g.small com Docker e Caddy roda a API de produção e a de staging, e uma RDS
PostgreSQL 18 privada guarda um banco por ambiente. O pipeline publica a imagem ARM64 no GHCR a
cada push na `main`, faz o deploy do staging por SSM Run Command e, quando o release-please cria uma
versão, faz o deploy da produção com aprovação manual. O front vai para o Cloudflare Pages, um
projeto por ambiente, com cabeçalhos de segurança gerados no build. O DNS passa para o Cloudflare
(ADR-0014, proposto aqui). Na API entram o perfil `prod` (proxy confiável e logs em JSON), o aceite
dos termos e da política e a gestão de passkeys. No web entram as páginas públicas `/privacidade` e
`/termos`, o aceite antes do primeiro uso e a lista de passkeys em Privacidade.

**Tech Stack:**
- API: Spring Boot 4.1.2, Spring Modulith 2.1.1, `JdbcClient`, Flyway, PostgreSQL 18, springdoc 3.2.0.
  Sem novas dependências.
- Web: React 19, react-router 8, TanStack Query, react-intl, Vite. Sem novas dependências.
- Infra: OpenTofu **1.13.2** (2026-10-21) com o provider `hashicorp/aws` **6.70.0** (2026-10-22),
  Caddy **2.11.7** (`caddy:2.11.7-alpine`, 2026-10-03), Amazon Linux 2023 arm64, Docker com o plugin
  Compose, GitHub Actions com OIDC. Versões conferidas na API do GitHub em 2026-11-16, todas
  publicadas até a data de corte.

**Spec:**
- [PRD](../../prd.md) (seções 8 e 9; critérios S1 e S5; riscos)
- [Arquitetura](../../architecture.md) (seções 6, 8, 9 e 10)
- [Modelo de dados](../../data-model.md) (`consents`, `invitations`, retenção)
- [Roadmap](../../roadmap.md) (v0.1 Núcleo, custos)
- [ADRs](../../adr/README.md), em especial 0004 (hospedagem), 0005 (sessão), 0009 (CI/CD) e 0014
  (DNS no Cloudflare, proposto neste plano)
- [Runbook do cookie no iPhone](../../runbooks/ios-session-cookie.md)

## Global Constraints

- **Idiomas:** comunicação, UI, documentação de usuário, runbooks e textos de PR em português do
  Brasil. Código, commits, nomes técnicos e comentários de código em inglês.
- **Commits e títulos de PR:** Conventional Commits.
  - Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Descrições de PR terminam com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Dados pessoais:** nenhum dado pessoal real (nome, cidade, e-mail) em arquivo versionado, nem nos
  runbooks, nem nas páginas legais. Quem é o controlador aparece só pela variável
  `VITE_PRIVACY_CONTROLLER` (D12). Testes usam nomes inventados e domínios `example.com`.
  - `PROMPT_BIG_BRAIN.md` e `.superpowers/` nunca são commitados.
  - Antes de cada commit, `git grep -nIiE -f .git/info/personal-data-patterns` não retorna nada
    (código de saída 1), e o mesmo vale com `--untracked`. O arquivo de padrões só existe na
    máquina do mantenedor e nunca é impresso.
- **Segredos:** nada de segredo em arquivo versionado. Segredos de runtime ficam no SSM Parameter
  Store como `SecureString`; os do pipeline, nos GitHub Environments. Quem cria conta, digita
  token, chave ou senha e roda `tofu apply` é o mantenedor; o agente nunca manipula credenciais da
  AWS, do Cloudflare ou do Google.
- **Senhas:** o app nunca pede nem guarda senha. As senhas dos usuários do banco são geradas e
  guardadas pelo mantenedor no SSM; a do usuário mestre da RDS fica no Secrets Manager, gerida pela
  própria RDS.
- **Sessão (ADR-0005):** cookie `__Host-sid` (HttpOnly, Secure, SameSite=Lax), só no host da API.
  Toda entrada nova troca o id da sessão, apaga os atributos da sessão anônima e troca o token CSRF
  (Task 4).
- **Infra (ASVS 5.0 L2, arquitetura §9):** EC2 sem par de chaves e sem SSH (acesso só por SSM
  Session Manager), IMDSv2 obrigatório, só as portas 80 e 443 abertas; RDS privada, com TLS
  obrigatório e criptografada com a chave KMS do projeto; menor privilégio em todo papel do IAM;
  OIDC no lugar de chave de acesso no GitHub. Os testes do OpenTofu (Tasks 11 e 12) afirmam cada uma
  dessas propriedades.
- **Deploy só pelo pipeline (S5):** a `main` continua protegida; o deploy do staging sai do push
  na `main` e o da produção sai do release, com aprovação no GitHub Environment `production`. Nada
  de `docker` à mão no host para publicar versão; o runbook de rollback usa o mesmo workflow.
- **Scripts de shell:** `#!/usr/bin/env bash`, `set -euo pipefail`, limpos no `shellcheck`. Nunca
  imprimem variáveis de ambiente nem o conteúdo dos arquivos de segredo.
- **Acesso a dados (ADR-0012):** `JdbcClient`, sem JPA.
- **Erros da API:** RFC 9457, com `code` estável e `detail` em pt-BR. Nunca expõem stack trace,
  nome de classe nem mensagem de exceção.
- **Valores fora do domínio:** mensagens de exceção e logs nunca contêm valores, saldos, nomes,
  descrições nem tokens (PRD §9). Os logs em JSON do perfil `prod` seguem a mesma regra.
- **Fronteiras de módulo:** Spring Modulith `verify()` na API e ESLint no web. Dependências
  permitidas: `workspace` → `identity`, `shared`; `ledger` → `workspace`, `shared`; `audit` →
  `identity`, `workspace`, `shared`; `privacy` → `identity`, `workspace`, `ledger`, `audit`,
  `shared`. `identity` não depende de ninguém além de `shared`.
- **Contrato:** toda mudança de rota ou DTO regenera `api/openapi.json`
  (`UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'`) e
  `web/src/shared/api/schema.ts` (`corepack pnpm --dir web gen:api`), e atualiza os handlers e o
  `mockState` do MSW com as mesmas regras e códigos, no mesmo commit.
- **Cobertura:** domínio e `shared` ≥ 90% de linhas; geral ≥ 80%; no web, `src/shared/**` com o
  gate atual. O build falha abaixo disso.
- **Conferência do web:** o passo "Conferir" de toda task de web roda, nesta ordem,
  `corepack pnpm --filter web lint`, `format:check`, `typecheck`, `test:coverage`, `build` e
  `check:chunks` (o CI já falhou uma vez só no `format:check`).
- **Desempenho (PRD §9):** API com p95 abaixo de 300 ms; primeira carga abaixo de 3 s em 4G. A carga
  inicial continua dentro do limite de 200 KB do `check:chunks`, e toda tela nova é `lazy()`.
- **Acessibilidade:** WCAG 2.2 AA; alvos de toque de 44 px ou mais; cor nunca sozinha;
  `prefers-reduced-motion` respeitado.
- **Offline (ADR-0007):** só criar lançamento funciona offline. O aceite dos termos e a remoção de
  passkey ficam desabilitados sem conexão, com explicação. Sem conexão, o app nunca bloqueia a pessoa
  por causa do aceite (D13).
- **GitHub Actions:** actions fixadas por SHA de 40 caracteres com comentário `# vX.Y.Z`;
  `permissions: {}` no topo e só o necessário por job. Imagens Docker usadas no CI ou no host vão
  fixadas por digest (`imagem:versão@sha256:…`).
- **Dependências:** só com licença compatível com AGPL-3.0 e publicadas em 2026-10-26 ou antes.
  Para o que este plano não fixa (a action de credenciais da AWS, o `actionlint`, o `shellcheck`, o
  plugin Compose), use a versão mais nova publicada até essa data, conferida na API de releases do
  GitHub na hora, e registre no PR 2. Exceções só com aprovação do mantenedor no chat.
- **Terceiros:** o app só chama o Google (login). AWS, Cloudflare e GitHub são infraestrutura e
  aparecem na política de privacidade como operadores (D12).
- **Versões:** as pesquisadas em 2026-11-16. Registre no PR 2 todo desvio.
- **Ações externas:** o mantenedor deu confirmação permanente para push, abrir PR e fazer merge com
  os checks obrigatórios verdes neste repositório. Force-push, reescrita de histórico, apagar
  trabalho não mesclado, configurações do repositório, Environments, variáveis e segredos continuam
  exigindo confirmação na hora. Auto-merge só se ele pedir.

### Pré-requisitos locais

Os mesmos do Plano 4: Git, `gh` autenticado, Java 21+ para o Gradle (a toolchain baixa o JDK 25),
Node 24 com corepack (`corepack pnpm …`), Docker em execução para Testcontainers e E2E, e o Chromium
do Playwright instalado. Para as Tasks 11 e 12, o OpenTofu 1.13.2 (binário oficial, com o SHA-256
conferido no `tofu_1.13.2_SHA256SUMS`). Nenhuma task precisa de conta da AWS: os testes do
OpenTofu usam `mock_provider`.

## Entrega

- **PR 1** (docs, branch `docs/plan-5`): sem código. Leva:
  - este plano;
  - o README dos planos, com o Plano 4 "Concluído" e o Plano 5 "Em execução" e com link;
  - a ADR-0014 (DNS no Cloudflare e custos atualizados), com status **Proposto** até o mantenedor
    aprovar, e a linha dela no README das ADRs;
  - na arquitetura: as rotas novas na seção 6 (`GET /v1/me/documents`,
    `POST /v1/me/documents/accept`, `GET /v1/me/passkeys`, `DELETE /v1/me/passkeys/{id}`), as
    páginas públicas `/privacidade` e `/termos`, e as seções 8 a 10 com o DNS no Cloudflare, os
    nomes dos ambientes, o deploy por `deploy.sh` e os cabeçalhos do front;
  - no modelo de dados: `consents.workspace_id` aceita nulo para `TERMS` e `PRIVACY` (D13) e entra
    `invitations.used_by` (D15);
  - no roadmap e no PRD: os custos recalculados (seção "Custos mensais" abaixo).
- **PR 2** (código, branch `feat/go-live`): Tasks 1 a 15, um commit por task.
  - O merge só acontece com os checks obrigatórios verdes e o E2E verde.
  - A descrição registra os desvios, as versões escolhidas na hora, as medições do `check:chunks` e
    os menores deixados de propósito.
- **Lançamento** (depois do PR 2, seção "Lançamento"): o mantenedor segue o
  `docs/runbooks/go-live.md`; o staging sobe pelo pipeline, o runbook do iPhone roda, a v0.1.0 sai
  pelo release-please e a produção sobe com aprovação.
- **PR 3** (docs, branch `docs/go-live-record`): os resultados do iPhone, do teste de restauração e
  do teste S1 com 2 pessoas, o custo real da primeira semana, a ADR-0014 "Aceito" e o Plano 5
  "Concluído".

### Itens herdados

| Item | Onde |
|---|---|
| Atributos da sessão anônima e token CSRF depois da entrada por convite ou recuperação (Plano 4) | Task 4 |
| Estratégia de cabeçalhos encaminhados no deploy (Plano 4) | Task 1 |
| Carteira criada quando alguém vira Jovem (Plano 4) | Task 4 |
| Aceite de convite idempotente (Plano 4) | Task 4 |
| Custo da consulta no `PendingDeletionFilter` (Plano 4) | D15 (fechado sem código) |
| Deadlock raro entre o pedido de exclusão e a limpeza noturna (Plano 4) | Task 5 |
| Consentimento sem revogação durante a carência de quem o deu (Plano 4) | Task 5 |
| O web recusa qualquer conta logada num convite de Jovem (Plano 4) | Task 9 |
| Teste instável `FamilyDeletionIT.ownerSeesPendingFamily` (Plano 4) | Task 5 |
| Possível instabilidade do E2E do painel sob CPU alta (Plano 4) | Task 14 |
| Só 1 KB de folga na carga inicial (Plano 4) | Task 6 |
| `format:check` no passo de conferência do web (Plano 4) | Global Constraints |
| Padrão de `liberdade.web.origin` aceita `http://a@b` (revisão do Plano 4) | Task 1 |
| Gestão de passkeys `GET` e `DELETE /v1/me/passkeys` (marcada para o Plano 5 no Plano 4) | Tasks 3 e 9 |
| `TERMS` e `PRIVACY` em `consents` (reservados no Plano 4) | Tasks 2 e 8 |
| Medição do cookie de sessão no iPhone (D17 do Plano 2) | Task 15 e Lançamento |

## Decisões deste plano

O spec deixa estes pontos em aberto, e o plano decide:

- **D1. DNS no Cloudflare (ADR-0014, proposta).** A zona `liberdadefamilia.com.br` vai para o
  plano gratuito do Cloudflare, e não para o Route 53. Motivos, conferidos em 2026-11-16:
  - o domínio de topo e o encaminhamento de e-mail (Email Routing, hoje dentro do "Email Service",
    disponível no plano gratuito) exigem a zona no Cloudflare;
  - o Pages cria sozinho o CNAME de um subdomínio quando a zona está no Cloudflare;
  - economiza US$ 0,50 por mês (R$ 2,87).
  - Os registros `api` e `api-staging` são do tipo A, **só DNS** (nuvem cinza), apontando para o IP
    elástico, para o Caddy emitir o certificado pelo desafio HTTP-01. Se houver registro CAA, ele
    precisa autorizar `letsencrypt.org` e as autoridades do Cloudflare.
  - O domínio de topo redireciona (regra de redirecionamento do Cloudflare, 301) para
    `https://app.liberdadefamilia.com.br`.
  - O registro continua no Registro.br; só os servidores de nome mudam.
  - A ADR-0004 continua aceita no resto; a ADR-0014 substitui a linha de DNS e a tabela de custos
    dela. Se o mantenedor recusar, o plano troca a zona para o Route 53 (US$ 0,50 por mês) e o
    e-mail `privacidade@` passa a ser o SES de entrada em `sa-east-1`; nada mais muda.
- **D2. Nomes.** Os ambientes se chamam `staging` e `production` em todo lugar: GitHub Environments,
  caminhos do SSM, serviços do Compose, grupos de log e bancos.

  | Ambiente | Front | API | Banco |
  |---|---|---|---|
  | Staging | `https://staging.liberdadefamilia.com.br` | `https://api-staging.liberdadefamilia.com.br` | `liberdade_staging` |
  | Produção | `https://app.liberdadefamilia.com.br` | `https://api.liberdadefamilia.com.br` | `liberdade_production` |

  - Os previews dos PRs continuam no projeto `liberdade` do Pages, com o MSW.
  - Se o domínio não estiver livre na compra, o mantenedor escolhe outro: os nomes vêm da variável
    `domain` do OpenTofu, das variáveis dos Environments e do `Caddyfile`, e o PR 3 registra a troca.
- **D3. Passkeys e cookie por ambiente.** `WEBAUTHN_RP_ID` é o host do front de cada ambiente
  (`staging.liberdadefamilia.com.br` e `app.liberdadefamilia.com.br`), para que uma passkey do
  staging nunca sirva na produção. O cookie `__Host-sid` fica só no host da API; front e API são o
  mesmo site (o `.com.br` está na Public Suffix List), então `SameSite=Lax` continua servindo.
  `WEB_ORIGIN` é a origem do front. As variáveis já existem em `application.properties`.
- **D4. Front por ambiente.**
  - Três projetos no Pages: `liberdade` (previews, já existe), `liberdade-staging` e
    `liberdade-production`, criados pelo mantenedor com o domínio próprio de cada um.
  - O build lê `VITE_API_URL`, `VITE_PRIVACY_CONTROLLER` e `VITE_PRIVACY_CONTACT` das variáveis do
    Environment.
  - Um plugin do Vite (`web/build/securityHeaders.ts`) gera `dist/_headers` com a CSP e os
    cabeçalhos abaixo, e o `vite preview` serve os mesmos cabeçalhos, para o E2E rodar sob a CSP
    real.
  - CSP: `default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' <origem da
    API>; img-src 'self' data:; font-src 'self'; worker-src 'self'; manifest-src 'self';
    frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'`. O login com o
    Google é navegação (`location.assign`), não formulário nem `fetch`, então não entra na CSP. Os
    estilos que o React aplica pelo `style` passam pelo CSSOM, que a CSP não bloqueia; o E2E falha
    se aparecer qualquer violação (Task 14).
  - Demais cabeçalhos: `Strict-Transport-Security: max-age=31536000; includeSubDomains`,
    `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy:
    camera=(), microphone=(), geolocation=(), payment=(), usb=(), publickey-credentials-get=(self),
    publickey-credentials-create=(self)`. `geolocation` volta a ser discutido quando um módulo
    pedir localização.
  - Fora do modo de mocks, o build apaga `dist/mockServiceWorker.js`.
- **D5. Imagens e gatilhos.**
  - A tag de imagem passa a ser `sha-<40 caracteres>` (`type=sha,format=long`); a `main` continua.
  - Staging: um job no `main.yml`, depois da imagem, chama o workflow reutilizável `deploy.yml`.
  - Produção: um job no `release-please.yml`, quando `release_created` é verdadeiro, espera a imagem
    `sha-<commit>`, acrescenta a tag `vX.Y.Z` com `docker buildx imagetools create` e chama o
    `deploy.yml` com o Environment `production`, que tem revisor obrigatório. Fica no mesmo
    workflow porque tag criada com o `GITHUB_TOKEN` não dispara outro workflow.
  - Os jobs de deploy só rodam com a variável **de repositório** `DEPLOY_ENABLED` igual a `true`
    (variável de Environment não vale no `if` do job). Até a AWS existir, a `main` fica verde sem
    deploy.
  - Rollback: `workflow_dispatch` do `deploy.yml` com o Environment e um ref anterior (tag ou SHA).
    Todo commit da `main` tem imagem `sha-…`, então o deploy é sempre pela tag de SHA.
- **D6. Deploy no host.**
  - O workflow assume o papel `liberdade-deploy` por OIDC e manda um `AWS-RunShellScript` para a
    instância. O comando baixa `deploy/deploy.sh`, `deploy/compose.yaml` e `deploy/Caddyfile` do
    `raw.githubusercontent.com` no SHA do deploy (conteúdo imutável) para
    `/opt/liberdade/releases/<sha>/` e roda `deploy.sh <ambiente> sha-<sha> <pasta>`.
  - O `deploy.sh` valida o Compose, copia os arquivos para `/opt/liberdade`, grava o arquivo de
    ambiente lido do SSM, troca só o serviço `api-<ambiente>` (`up -d --no-deps`), recarrega o Caddy
    (`caddy reload`, que valida antes e mantém a configuração antiga se falhar) e espera o
    healthcheck por até 180 s. Se a API não ficar saudável, volta para a tag anterior, espera de
    novo e sai com erro.
  - `compose.yaml` e `Caddyfile` são do host todo: uma mudança neles vale no próximo deploy de
    qualquer ambiente. Por isso o CI valida os dois (Task 10) e o staging, que recebe toda `main`,
    é o primeiro a aplicar.
  - A Flyway roda na subida da API; cada migração é transacional no PostgreSQL. Migrações seguem
    expandir e contrair (arquitetura §8), para a versão anterior continuar funcionando depois de um
    rollback.
- **D7. Configuração e segredos.** Os parâmetros ficam em `/liberdade/<ambiente>/<NOME>`:
  `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
  `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `WEB_ORIGIN`, `WEBAUTHN_RP_ID` e
  `SPRING_PROFILES_ACTIVE` (= `prod`). Os segredos são `SecureString` com a chave KMS do projeto. O
  `deploy.sh` grava `/run/liberdade/<ambiente>.env` (modo 0600, em tmpfs). O pacote do GHCR fica
  público (o código já é público), então o host não precisa de credencial para baixar a imagem.
- **D8. Proxy confiável.** O perfil `prod` liga `server.forward-headers-strategy=native` e confia só
  na rede do Compose (`server.tomcat.remoteip.internal-proxies` = `172.30.0.0/24`). O Caddy
  sobrescreve o `X-Forwarded-For` de quem não é proxy confiável (padrão dele). Assim o limite de
  entradas por IP (`LoginRateLimitFilter`, `PublicInvitationController`) conta o IP de quem acessa,
  e não o do Caddy, e ninguém falsifica o IP mandando o cabeçalho. Os cabeçalhos de segurança da API
  continuam com o Spring Security (o HSTS sai porque a requisição passa a ser vista como HTTPS); o
  Caddy só remove o `Server`.
- **D9. Banco.** Uma RDS com dois bancos e dois usuários donos (`liberdade_production` e
  `liberdade_staging`), criados por `infra/sql/bootstrap.sql` pelo mantenedor, numa sessão de
  encaminhamento de porta do SSM (`AWS-StartPortForwardingSessionToRemoteHost`). As senhas entram
  como variáveis do `psql`, nunca no arquivo. O `CONNECT` em cada banco é revogado de `PUBLIC`. A
  JDBC usa `sslmode=verify-full` com o pacote de certificados da RDS de `sa-east-1`, baixado pelo
  `user_data` e montado só para leitura. O pool do Hikari fica em 5 conexões por API.
- **D10. Infra como código.** OpenTofu 1.13.2, provider AWS 6.70.0, estado no S3 com
  `use_lockfile = true` (sem DynamoDB). `infra/bootstrap` cria o bucket do estado; `infra/live` cria o
  resto. O mantenedor roda `tofu apply` na máquina dele; o CI roda `fmt`, `validate` e `tofu test`
  com `mock_provider`. O `user_data` e a AMI ficam em `ignore_changes`: mudar o `user_data` não
  recria a instância (o runbook explica como aplicar à mão, por SSM).
- **D11. Observabilidade.**
  - Logs: driver `awslogs` do Docker, grupos `/liberdade/production`, `/liberdade/staging` e
    `/liberdade/caddy`, retenção de 30 dias, criados pelo OpenTofu. O perfil `prod` grava logs em
    JSON ECS (`logging.structured.format.console=ecs`). O Caddy não grava log de acesso, então
    nenhum IP vai para os logs.
  - Alarmes, com aviso por e-mail num tópico SNS: falha de status da EC2 (com recuperação
    automática da instância), CPU da EC2 acima de 80% por 15 min, espaço livre da RDS abaixo de 2 GB
    e saldo de créditos de CPU da RDS abaixo de 30.
  - Orçamento mensal de US$ 60 (≈ R$ 344) sem contar créditos, com aviso em 80% do gasto real e em
    100% do previsto.
  - O e-mail dos avisos fica em `infra/live/terraform.tfvars`, que é ignorado pelo Git.
- **D12. Política de privacidade e termos.**
  - Páginas públicas, carregadas sob demanda, em `/privacidade` e `/termos`, com o texto em pt-BR
    no próprio código (Task 8), a versão e a data no topo.
  - Versões atuais: `privacy-2026-11` e `terms-2026-11`.
  - O controlador é o mantenedor do projeto, que também faz o papel de encarregado: o plano não
    depende da dispensa de encarregado do agente de pequeno porte (Res. CD/ANPD 2/2022, art. 11),
    e o canal de contato é `privacidade@liberdadefamilia.com.br`, encaminhado pelo Cloudflare para
    a caixa que o mantenedor escolher.
  - O nome do controlador vem de `VITE_PRIVACY_CONTROLLER`, uma variável dos Environments `staging`
    e `production` que nunca é commitada; sem ela, a página mostra "o mantenedor do projeto
    Liberdade". O contato vem de `VITE_PRIVACY_CONTACT`, com o endereço acima como padrão.
  - Os links aparecem na tela de entrada, no aceite de convite, no aceite dos termos e em
    Mais → Privacidade.
- **D13. Aceite dos termos e da política.**
  - A base legal do serviço principal é a execução de contrato (LGPD art. 7º, V). O aceite registra
    que a pessoa foi informada e concordou com os termos; não é o consentimento do art. 7º, I. O
    consentimento parental do Jovem (art. 14, §1º) continua como está.
  - O registro vai para `consents` com `purpose` `TERMS` ou `PRIVACY`, `workspace_id` nulo,
    `granted_by` = a própria pessoa e `document_version` = a versão aceita. Uma versão nova
    substitui a anterior (`superseded_at`).
  - O aceite não se revoga pelo app: quem não concorda mais exclui a conta. `GET /v1/me/consents`
    continua listando só `PARENTAL`, e `DELETE /v1/me/consents/{id}` de um aceite responde 404
    `consent.not-found`.
  - O web mostra a tela "Antes de começar" quando falta aceitar a versão atual de algum documento,
    antes de qualquer tela da família. É uma regra do web, não da API: a API não recusa chamadas de
    quem ainda não aceitou. Sem conexão ou com erro na consulta, o web não bloqueia; pergunta de
    novo na próxima vez que estiver online.
  - No convite, o aceite vem junto: a página de convite mostra o aviso com os links, e o web grava o
    aceite logo depois de o convite ser aceito, sem tela extra.
- **D14. Gestão de passkeys.** `GET /v1/me/passkeys` lista as passkeys da pessoa (nome, criada em,
  último uso); `DELETE /v1/me/passkeys/{id}` exige reautenticação de 10 minutos e grava
  `passkey.removed` na trilha. Remover a última passkey de quem não tem login do Google responde
  422 `passkey.last-method`, porque a pessoa ficaria sem como entrar.
- **D15. Pendências fechadas por decisão.**
  - `PendingDeletionFilter`: fica como está. A consulta é por chave primária, numa instância só, e
    um cache deixaria uma checagem de segurança desatualizada.
  - Aceite idempotente: entra `invitations.used_by`; o mesmo convite aceito de novo pela mesma
    pessoa, já membro ativo, devolve o mesmo resultado em vez de 410.
- **D16. Google OAuth.** Um cliente OAuth por ambiente, criado pelo mantenedor, com o redirect
  `https://api[-staging].liberdadefamilia.com.br/login/oauth2/code/google`, os escopos `openid`,
  `email` e `profile` e o link da política (`https://app.liberdadefamilia.com.br/privacidade`) na
  tela de consentimento. Se o Google pedir verificação, o app fica em modo "Teste" com as contas da
  família como usuários de teste, o que basta para a v0.1.
- **D17. Ordem do lançamento.** Staging no ar → runbook do iPhone → teste de restauração no staging
  → release v0.1.0 → aprovação da produção → teste S1 com 2 pessoas → 2 semanas de uso. Se o
  iPhone falhar, a família não entra até o mantenedor decidir (o runbook já diz isso).

## Custos mensais (pesquisados em 2026-11-16)

Preços da AWS Price List API para `sa-east-1`, 730 horas por mês, dólar pela PTAX de venda de
2026-11-16 (R$ 5,7324). Domínio: Registro.br, R$ 40 por ano. Cloudflare: plano gratuito.

| Item | Uso | US$/mês | R$/mês |
|---|---|---|---|
| EC2 t4g.small, sob demanda | 730 h × US$ 0,0268 | 19,56 | 112,13 |
| EBS gp3 da EC2 | 20 GB × US$ 0,152 | 3,04 | 17,43 |
| IPv4 público (IP elástico) | 730 h × US$ 0,005 | 3,65 | 20,92 |
| RDS db.t4g.micro, Single-AZ, sob demanda | 730 h × US$ 0,034 | 24,82 | 142,28 |
| Armazenamento gp3 da RDS | 20 GB × US$ 0,219 | 4,38 | 25,11 |
| Backup da RDS (7 dias) | grátis até o tamanho do banco | 0,00 | 0,00 |
| Chave KMS do projeto | 1 chave | 1,00 | 5,73 |
| Secrets Manager (senha mestre da RDS) | 1 segredo | 0,40 | 2,29 |
| CloudWatch Logs, 4 alarmes, SNS, SSM, S3 do estado, saída de dados | dentro das faixas gratuitas | 0,00 | 0,00 |
| **AWS** | | **56,85** | **325,89** |
| Domínio (Registro.br) | R$ 40 ÷ 12 | — | 3,33 |
| DNS, Pages e e-mail (Cloudflare) | plano gratuito | — | 0,00 |
| **Total sob demanda** | | | **329,22** |
| Total com reserva de 1 ano da EC2 (US$ 0,0154/h) e da RDS (US$ 0,0213/h) | | 39,26 na AWS | **228,38** |

- **Créditos:** os US$ 200 de crédito de conta nova (R$ 1.146,48) cobrem ≈ 3,5 meses de AWS sob
  demanda.
- **Reserva:** economiza R$ 100,84 por mês. Recomendação: ficar sob demanda enquanto houver crédito
  e decidir a reserva depois das 2 semanas de uso da família, com o custo real do Cost Explorer.
- **ROI e payback:** ROI mensal = (economia mensal que o app ajuda a família a fazer − R$ 329,22) ÷
  R$ 329,22. Com R$ 500 de economia, ROI ≈ 52%. O app se paga no mês em que ajudar a economizar
  R$ 329,22 ou mais (R$ 228,38 com reserva). Por ano, sob demanda: R$ 3.950,64.
- **Riscos de custo:** crédito de CPU da RDS t4g é sempre ilimitado (paga o excedente; o alarme do
  D11 avisa antes); saída de dados acima de 100 GB por mês; o orçamento do D11 avisa em 80%.

## Review Focus

1. **Sem conexão ou com a API fora do ar no primeiro uso do dia.** A consulta de documentos falha:
   o app abre normalmente, lançar offline continua funcionando, e a tela "Antes de começar" só
   aparece quando a consulta responde que falta aceite (Tasks 2 e 8).
2. **A CSP quebrando parte do app em produção.** Gráficos do painel, passkey, login com o Google,
   o service worker do PWA e o MSW dos previews funcionam sob a CSP; qualquer violação no console
   faz o E2E falhar (Tasks 7 e 14).
3. **Deploy que não sobe.** Uma imagem que nunca fica saudável ou uma migração que falha deixa a
   versão anterior no ar, o workflow fica vermelho e o outro ambiente não é tocado (Tasks 10 e 13).
4. **Todo mundo atrás do mesmo IP.** Atrás do Caddy, 21 entradas de pessoas diferentes num minuto
   não dão 429 para todas; e quem manda `X-Forwarded-For` direto não escapa do limite (Task 1).
5. **A última forma de entrar.** O Jovem (só passkey) remove a única passkey → recusado com uma
   mensagem clara, nunca uma conta trancada; remover a passkey em uso na sessão não derruba a sessão
   atual (Tasks 3 e 9).

---

## Mapa de arquivos

API (`api/src/main/java/br/com/liberdadefamilia/`):

| Pacote | Novo | Muda |
|---|---|---|
| `identity` | `PasskeySummary`, `PasskeyRemoved` | `Passkeys` (lista e remove), `UserDeletion` (se faltar a leitura de exclusão pendente) |
| `identity/internal` | `PasskeyController`, `PasskeyView` | `IdentityProperties`, `DefaultSessionSignIn`, `JdbcPasskeys` |
| `workspace/internal` | — | `InvitationService`, `InvitationRepository`, `JdbcMemberships` |
| `ledger/internal` | — | `LedgerSeeder` |
| `audit/internal` | — | `AuditListeners` (`passkey.removed`) |
| `privacy` | — | — |
| `privacy/internal` | `LegalDocuments`, `DocumentController`, `DocumentService`, `DocumentStatus`, `AcceptDocumentsRequest` | `ConsentRepository`, `ConsentService`, `ExportService` |

Recursos da API: `api/src/main/resources/application-prod.properties` (novo);
`db/migration/V12__legal_documents.sql` e `db/migration/V13__invitation_used_by.sql` (novos).

Web (`web/`):

| Pasta | Novo | Muda |
|---|---|---|
| `build/` (nova) | `securityHeaders.ts`, `securityHeaders.test.ts` | — |
| raiz | — | `vite.config.ts`, `vitest.config.ts` (inclui `build/`), `src/vite-env.d.ts` |
| `src/app` | — | `routes.tsx`, `SessionLayout.tsx`, `MorePage.tsx` |
| `src/features/legal` (nova) | `PrivacyPolicyPage`, `TermsPage`, `LegalLayout`, `DocumentsGate`, `useDocuments.ts`, `legalConfig.ts`, `messages.pt-BR.ts` | — |
| `src/features/privacy` | `PasskeyList`, `RemovePasskeySheet` | `PrivacyPage`, `activityPhrase.ts`, `messages.pt-BR.ts` |
| `src/features/members` | — | `AcceptInvitationPage` |
| `src/features/auth` | — | `SignInPage` |
| `src/shared/api` | — | `endpoints.ts`, `mutations.ts`, `queryKeys.ts`, `types.ts`, `errors.ts`, `schema.ts`, `csrf` (o cache do token) |
| `src/mocks` | — | `data.ts`, `handlers.ts` |
| `e2e` | `legal.spec.ts` | `support.ts`, `first-expense.spec.ts`, `privacy.spec.ts`, `a11y.spec.ts`, `family.spec.ts` |

Host, infra e pipeline:

| Pasta | Novo | Muda |
|---|---|---|
| `deploy/` (nova) | `compose.yaml`, `Caddyfile`, `deploy.sh`, `smoke.sh`, `tags.env.example`, `test/compose.ci.yaml`, `test/Dockerfile.unhealthy`, `test/deploy-test.sh` | — |
| `infra/bootstrap/` (nova) | `versions.tf`, `main.tf`, `variables.tf`, `outputs.tf`, `tests/state_bucket.tftest.hcl`, `.terraform.lock.hcl` | — |
| `infra/live/` (nova) | `versions.tf`, `providers.tf`, `variables.tf`, `network.tf`, `kms.tf`, `compute.tf`, `user_data.sh.tftpl`, `database.tf`, `logs.tf`, `alarms.tf`, `budget.tf`, `deploy_role.tf`, `outputs.tf`, `backend.hcl.example`, `terraform.tfvars.example`, `tests/security.tftest.hcl`, `tests/operations.tftest.hcl`, `.terraform.lock.hcl` | — |
| `infra/sql/` (nova) | `bootstrap.sql` | — |
| `.github/workflows/` | `deploy.yml` | `main.yml`, `release-please.yml`, `ci.yml` (jobs `deploy-lint`, `deploy-test`, `infra`) |
| `docs/runbooks/` | `go-live.md`, `rollback.md`, `restore-test.md` | `ios-session-cookie.md` (onde anotar: PR 3), `README` dos runbooks se existir |
| raiz | — | `.gitignore` (`infra/**/.terraform/`, `*.tfvars`, `backend.hcl`, `*.tfstate*`) |

**Em toda task de API:** toda mudança de rota ou DTO regenera o contrato e o MSW (Global
Constraints). Todo evento novo de domínio é um `record` público no pacote do módulo que publica, só
com ids.

## Tasks

### Task 1: Perfil de produção, proxy confiável e origem estrita

**Files:**
- Create: `api/src/main/resources/application-prod.properties`
- Modify: `identity/internal/IdentityProperties.java`
- Test (novo): `identity/ForwardedHeadersUntrustedIT.java`, `identity/ForwardedHeadersTrustedIT.java`,
  `identity/internal/IdentityPropertiesTest.java`

**Interfaces:**
- Consumes: `LoginRateLimitFilter` (20 entradas por minuto por IP em `/webauthn/authenticate/options`
  e `/login/webauthn`), `liberdade.web.origin` e `liberdade.webauthn.rp-id`.
- Produces: o perfil `prod` (D8, D9, D11), que a Task 10 liga com `SPRING_PROFILES_ACTIVE=prod`.

**Como fica:**
- `application-prod.properties`:
  - `server.forward-headers-strategy=native`
  - `server.tomcat.remoteip.internal-proxies=172.30.0.0/24`
  - `logging.structured.format.console=ecs`
  - `spring.datasource.hikari.maximum-pool-size=5`
- `IdentityProperties.Web.origin`: aceita só `https://<host>[:porta]`, com host em minúsculas,
  letras, dígitos, hífen e ponto, sem usuário, caminho, query, fragmento nem barra final; ou
  `http://localhost[:porta]` e `http://127.0.0.1[:porta]`.
- Validação cruzada (`@AssertTrue` no record raiz): `webauthn.rpId` é igual ao host da origem ou um
  sufixo dele separado por ponto.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `IdentityPropertiesTest.acceptsProductionOrigin` | `https://app.liberdadefamilia.com.br` com rp-id `app.liberdadefamilia.com.br` → válido |
| `IdentityPropertiesTest.acceptsLocalhost` | `http://localhost:5173` com `localhost` → válido |
| `IdentityPropertiesTest.rejectsBadOrigins` | `http://a@b`, `https://user@app.example.com`, `http://app.example.com`, `https://app.example.com/`, `https://app.example.com/x`, `https://APP.example.com` → inválidos |
| `IdentityPropertiesTest.rejectsRpIdOutsideOrigin` | origem `https://app.example.com` com rp-id `other.example.com` → inválido; com `example.com` → válido |
| `ForwardedHeadersUntrustedIT.forwardedForFromUntrustedPeerIsIgnored` | perfil `prod`, Tomcat real (`RANDOM_PORT`), 21 POSTs em `/webauthn/authenticate/options`, cada um com um `X-Forwarded-For` diferente → o 21º responde 429 |
| `ForwardedHeadersTrustedIT.forwardedForFromTrustedProxyIsUsed` | perfil `prod` com `internal-proxies=127\.0\.0\.1` só neste teste, 21 POSTs com `X-Forwarded-For` diferentes → nenhum 429 |
| `ForwardedHeadersTrustedIT.httpsFromProxySetsHsts` | com `X-Forwarded-Proto: https` → a resposta tem `Strict-Transport-Security` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ForwardedHeaders*' --tests '*IdentityPropertiesTest'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga "Como fica". Os ITs usam o Testcontainers como os demais e sobrescrevem só o datasource.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `./gradlew build`
Expected: verde.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(api): add the prod profile with a trusted proxy and stricter origin validation"
```

---

### Task 2: Aceite dos termos e da política na API

**Files:**
- Create: `api/src/main/resources/db/migration/V12__legal_documents.sql`
- Create em `privacy/internal/`: `LegalDocuments.java`, `DocumentController.java`,
  `DocumentService.java`, `DocumentStatus.java`, `AcceptDocumentsRequest.java`
- Modify: `privacy/internal/ConsentRepository.java`, `ConsentService.java`, `ExportService.java`,
  `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test (novo): `privacy/LegalDocumentsIT.java`; modify: `privacy/ConsentsIT.java`, o IT da exportação

**Interfaces:**
- Consumes: `AuditTrail.record` (Plano 4), `ConsentPurpose { TERMS, PRIVACY, PARENTAL }`.
- Produces:
  - `LegalDocuments.CURRENT`: `Map<ConsentPurpose, String>` com `TERMS` → `"terms-2026-11"` e
    `PRIVACY` → `"privacy-2026-11"`.
  - `DocumentStatus(ConsentPurpose purpose, String currentVersion, @Nullable String acceptedVersion,
    @Nullable Instant acceptedAt)`.
  - `AcceptDocumentsRequest(@NotBlank String terms, @NotBlank String privacy)`.
  - Ação de auditoria `documents.accepted`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /v1/me/documents` | `listMyDocuments` | logado | 200 `DocumentStatus[]`, `TERMS` e depois `PRIVACY` | — |
| `POST /v1/me/documents/accept` | `acceptDocuments` | logado | 204 (repetir com as mesmas versões: 204, sem linha nova) | 409 `documents.version-outdated` (alguma versão diferente da atual); 400 `validation.failed` |

**Como fica:**
- `V12__legal_documents.sql`:
  - `consents.workspace_id` passa a aceitar nulo;
  - `CHECK ((purpose = 'PARENTAL') = (workspace_id IS NOT NULL))`;
  - `consents_one_open` é recriado como
    `UNIQUE (user_id, workspace_id, purpose) NULLS NOT DISTINCT WHERE revoked_at IS NULL AND
    superseded_at IS NULL`;
  - o comentário da tabela passa a dizer que `TERMS` e `PRIVACY` registram o aceite da pessoa, sem
    família.
- Aceitar: na mesma transação, para cada documento cuja versão aberta difere da atual, fecha a
  aberta com `superseded_at = now()` e grava uma linha nova com `granted_by` = a própria pessoa;
  grava `documents.accepted` uma vez.
- `ConsentRepository` e `ConsentService`: a lista e a revogação de `/v1/me/consents` filtram
  `purpose = 'PARENTAL'`.
- `ExportService`: os aceites entram nos consentimentos exportados, com a família vazia.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `newUserHasPendingDocuments` | pessoa nova → `TERMS` e `PRIVACY` com `currentVersion` certa e `acceptedVersion` nulo |
| `acceptRecordsBoth` | aceitar as versões atuais → 204; a lista mostra as duas aceitas com `acceptedAt` |
| `acceptTwiceIsNoop` | aceitar de novo → 204; continua uma linha aberta por documento |
| `outdatedVersionRejected` | `terms-2026-10` → 409 `documents.version-outdated`, nada gravado |
| `newVersionSupersedesOld` | linha aberta de `terms-2026-10` gravada no teste → aceitar a atual fecha a antiga com `superseded_at` |
| `acceptIsAudited` | depois do aceite, a Atividade da pessoa tem `documents.accepted` |
| `documentsNotInConsentList` | `GET /v1/me/consents` não lista os aceites; `DELETE /v1/me/consents/{id}` do aceite → 404 `consent.not-found` |
| `parentalStillNeedsWorkspace` | `INSERT` de `PARENTAL` sem família → violação do `CHECK` |
| `exportIncludesAcceptedDocuments` | a exportação traz os dois aceites com família vazia |
| `anonymousGets401` | sem sessão → 401 nas duas rotas |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*LegalDocumentsIT' --tests '*ConsentsIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica". No MSW, a pessoa simulada começa com os dois documentos aceitos, para
os previews abrirem direto; um cenário `pendingDocuments` do `mockState` liga a tela de aceite.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2, mais o IT da exportação.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web typecheck`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(privacy): record acceptance of the terms and the privacy policy"
```

---

### Task 3: Gestão de passkeys na API

**Files:**
- Create em `identity/`: `PasskeySummary.java`, `PasskeyRemoved.java`
- Create em `identity/internal/`: `PasskeyController.java`, `PasskeyView.java`
- Modify: `identity/Passkeys.java`, `identity/internal/JdbcPasskeys.java`,
  `audit/internal/AuditListeners.java`, `api/openapi.json`, `web/src/shared/api/schema.ts`,
  `web/src/mocks/data.ts`, `web/src/mocks/handlers.ts`
- Test (novo): `identity/PasskeyManagementIT.java`

**Interfaces:**
- Consumes: `@RequiresRecentSignIn` (Plano 4), as tabelas `user_entities`, `user_credentials` e
  `auth_identities`.
- Produces:
  - `Passkeys.list(UUID userId): List<PasskeySummary>`, da mais nova para a mais antiga;
    `PasskeySummary(String id, String label, Instant createdAt, @Nullable Instant lastUsedAt)`, com
    `id` = `credential_id` (base64url, como o Spring grava).
  - `PasskeyView` com os mesmos campos.
  - `PasskeyRemoved(UUID userId)`, ouvido pelo `audit`, que grava `passkey.removed`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /v1/me/passkeys` | `listMyPasskeys` | logado | 200 `PasskeyView[]` | — |
| `DELETE /v1/me/passkeys/{id}` | `removePasskey` | logado, reautenticado | 204 | 404 `passkey.not-found` (não existe ou é de outra pessoa); 422 `passkey.last-method`; 403 `auth.reauthentication-required` |

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `listsOwnPasskeysNewestFirst` | duas passkeys gravadas no teste → lista com as duas, a mais nova primeiro, com `label` e `createdAt` |
| `otherPeoplesPasskeysHidden` | a passkey de outra pessoa não aparece; `DELETE` dela → 404 `passkey.not-found` |
| `removeNeedsRecentSignIn` | login de 11 minutos atrás → 403 `auth.reauthentication-required` |
| `removeWithGoogleIdentity` | pessoa com Google e uma passkey → 204; a lista fica vazia |
| `lastPasskeyWithoutGoogleRefused` | Jovem com uma passkey e sem Google → 422 `passkey.last-method`, nada apagado |
| `secondPasskeyWithoutGoogleRemovable` | Jovem com duas → remover uma dá 204 |
| `removeIsAudited` | a Atividade mostra `passkey.removed` |
| `sessionSurvivesRemoval` | remover a passkey usada no login → a sessão atual continua (próximo `GET /v1/me` → 200) |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*PasskeyManagementIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga a tabela. A checagem da última forma de entrar e o `DELETE` correm na mesma transação, com
`FOR UPDATE` nas passkeys da pessoa, para duas remoções simultâneas não apagarem as duas.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web typecheck`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(identity): list and remove passkeys"
```

---

### Task 4: Entrada por convite, aceite idempotente e carteira do Jovem

**Files:**
- Create: `api/src/main/resources/db/migration/V13__invitation_used_by.sql`
- Modify: `identity/internal/DefaultSessionSignIn.java`, `workspace/internal/InvitationService.java`,
  `workspace/internal/InvitationRepository.java`, `ledger/internal/LedgerSeeder.java`,
  `web/src/shared/api` (o cache do token CSRF), `web/src/features/members/AcceptInvitationPage.tsx`
  (só a limpeza do token depois do aceite)
- Test: `identity/SessionSignInIT.java` (novo), `workspace/InvitationsIT.java`,
  `ledger/LedgerSeederIT.java` (ou o IT que hoje cobre a carteira do convite), o teste do cache de
  CSRF no web

**Interfaces:**
- Consumes: `SessionSignIn.signIn` (Plano 4), `MemberRoleChanged(…, Role from, Role to)`,
  `Memberships.find`.
- Produces: `invitations.used_by uuid NULL REFERENCES users ON DELETE SET NULL`;
  `InvitationRepository.markUsed(UUID id, UUID userId, Instant now)`.

**Como fica:**
- `DefaultSessionSignIn.signIn`: depois de `changeSessionId()`, remove todos os atributos da sessão
  e só então grava o contexto de segurança, `AUTHENTICATED_AT` e o tempo sem uso; apaga o token
  CSRF pelo `CsrfTokenRepository` da configuração, para o próximo pedido gerar outro. O web já busca
  um token novo depois do login por passkey; depois do aceite de convite e da recuperação, ele
  descarta o token guardado do mesmo jeito.
- `InvitationService.accept`: se o convite já foi usado, `used_by` é quem está chamando e essa
  pessoa é membro ativo da família, devolve o mesmo resultado do primeiro aceite; senão, 410
  `invitation.gone` como hoje.
- `LedgerSeeder`: ao ouvir `MemberRoleChanged` com `to = YOUTH`, cria a carteira "Carteira de
  <apelido>" com o membro como titular, se ele ainda não for titular de nenhuma conta na família.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `SessionSignInIT.inviteSignInDropsAnonymousAttributes` | sessão anônima com um atributo de teste → depois do aceite com conta nova, o atributo não existe e o id da sessão mudou |
| `SessionSignInIT.inviteSignInRotatesCsrf` | o token CSRF da sessão anônima, usado num POST depois do aceite → 403; com o token novo de `GET /v1/csrf` (a rota que o web já usa) → passa |
| `SessionSignInIT.recoverySignInRotatesCsrf` | o mesmo para a entrada por recuperação |
| `InvitationsIT.sameCallerRetryGetsSameResult` | aceitar, repetir o mesmo POST com a mesma sessão → 200 com a mesma família; um membro só |
| `InvitationsIT.otherCallerAfterUseGets410` | outra pessoa com o mesmo link depois do uso → 410 `invitation.gone` |
| `InvitationsIT.removedMemberRetryGets410` | quem aceitou e depois foi removido repete → 410 |
| `LedgerSeederIT.promotedYouthGetsWallet` | Adulto sem conta vira Jovem → existe "Carteira de <apelido>" com ele como titular |
| `LedgerSeederIT.promotedYouthWithAccountKeepsIt` | Adulto que já é titular de uma conta vira Jovem → nenhuma conta nova |
| `LedgerSeederIT.repeatedPromotionNoDuplicate` | Jovem → Adulto → Jovem → continua uma carteira |
| teste do web para o cache de CSRF | depois do aceite de convite bem-sucedido, o próximo POST busca um token novo |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*SessionSignInIT' --tests '*InvitationsIT' --tests '*LedgerSeederIT'`
Run: `corepack pnpm --filter web test -- AcceptInvitationPage csrf`
Expected: FAIL nos testes novos.

- [ ] **Step 3: Implementar**

Siga "Como fica". `V13__invitation_used_by.sql` acrescenta a coluna e o índice `(used_by)`; os
convites já usados ficam com `used_by` nulo e continuam respondendo 410.

- [ ] **Step 4: Rodar e ver passar**

Run: os mesmos comandos do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `./gradlew build`
Run: a conferência do web (Global Constraints).
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "fix: rotate the session on invitation sign-in, make accept idempotent, seed the youth wallet"
```

---

### Task 5: Exclusão sem deadlock, revogação na carência e o teste instável

**Files:**
- Modify: `workspace/internal/JdbcMemberships.java` (`detachUser` e `lockActiveOwners`),
  `privacy/internal/ConsentService.java`, `identity/UserDeletion.java` (só se faltar a leitura de
  exclusão pendente)
- Test: `workspace/MembersIT.java`, `privacy/ConsentsIT.java`, `privacy/FamilyDeletionIT.java`

**Interfaces:**
- Consumes: `UserDeletion` (Plano 4), `Memberships.detachUser(UUID userId)`.
- Produces: `UserDeletion.isPending(UUID userId): boolean`, se ainda não existir leitura equivalente.

**Como fica:**
- `detachUser`: antes de qualquer `UPDATE`, trava as linhas de `memberships` da pessoa com
  `SELECT … ORDER BY id FOR UPDATE`; `lockActiveOwners` ganha `ORDER BY id`. Todos os caminhos que
  travam memberships passam a travar na mesma ordem.
- Revogação: além das regras de hoje, um Dono ativo da família pode revogar o consentimento quando
  quem o deu tem exclusão de conta pendente; `canRevoke` reflete isso.
- Teste instável: siga o superpowers:systematic-debugging até achar a causa de
  `FamilyDeletionIT.ownerSeesPendingFamily` (o Plano 4 suspeitou do Spring Session); corrija a
  causa, não o sintoma (nada de `sleep` ou nova tentativa).

- [ ] **Step 1: Reproduzir o teste instável**

Run (em `api/`): `for i in $(seq 1 20); do ./gradlew test --rerun --tests '*FamilyDeletionIT.ownerSeesPendingFamily' -q || echo "FAIL $i"; done`
Expected: pelo menos uma falha, ou a evidência de que ela depende de outro teste (rodar com a classe
inteira e com a suíte de `privacy`). Registre a causa no relatório da task.

- [ ] **Step 2: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `MembersIT.deletionRequestAndPurgeDoNotDeadlock` | o teste de concorrência do Plano 4, repetido 50 vezes com o pedido de exclusão e a limpeza em paralelo → nenhum `DeadlockLoserDataAccessException` |
| `ConsentsIT.ownerRevokesWhenGranterPendingDeletion` | a Adulta que deu o consentimento pede exclusão; a Dona revoga → 204; o Jovem fica `REMOVED` |
| `ConsentsIT.ownerCannotRevokeOtherwise` | sem exclusão pendente da Adulta → a Dona continua vendo `canRevoke` como hoje (sem regra nova) |
| o teste que reproduz a causa do Step 1 | falha antes da correção, de forma determinística |

- [ ] **Step 3: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*MembersIT' --tests '*ConsentsIT' --tests '*FamilyDeletionIT'`
Expected: FAIL nos testes novos.

- [ ] **Step 4: Implementar**

Siga "Como fica".

- [ ] **Step 5: Rodar e ver passar**

Run: o comando do Step 3, e o laço do Step 1 de novo.
Expected: PASS, e 20 de 20 no laço.

- [ ] **Step 6: Conferir**

Run (em `api/`): `./gradlew build`
Expected: verde.

- [ ] **Step 7: Commit**

```bash
git add api
git commit -m "fix: lock memberships in id order, let an owner revoke during the granter's grace period, fix a flaky test"
```

---

### Task 6: Folga na carga inicial

**Files:**
- Modify: `web/vite.config.ts` (o grupo `vendor` do `codeSplitting`), os imports que puxam código de
  tela para a carga inicial, `web/scripts/check-chunks.mjs` (só para imprimir a composição)
- Test: `corepack pnpm --filter web check:chunks`

**Interfaces:**
- Consumes: a lista escolhida à mão do grupo `vendor` (D27 do Plano 4).
- Produces: carga inicial de **190 KB ou menos**, com o limite de 200 KB do `check:chunks` intacto.

**Como fica:**
- O `check:chunks` passa a listar, para a carga inicial, os pacotes de `node_modules` e as pastas de
  `src` que mais pesam (pelo mapa de módulos do build), para a medição sair do próprio script.
- Ordem do que conferir: (1) pacote do grupo `vendor` que só telas `lazy()` usam sai do grupo; (2)
  import estático de uma tela ou feature a partir de `routes.tsx`, `SessionLayout`, `AppShell` ou
  `register.ts` vira dinâmico; (3) mensagens do react-intl de telas sob demanda saem do pacote
  inicial.
- Se 190 KB não for alcançável sem piorar a primeira tela, a task para no menor valor alcançado e
  registra no PR 2 a tabela por pacote e o motivo.

- [ ] **Step 1: Medir**

Run: `corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: a carga inicial (hoje 199 KB) e a lista do que pesa nela.

- [ ] **Step 2: Cortar, um item por vez**

Siga a ordem de "Como fica", medindo depois de cada mudança.

- [ ] **Step 3: Conferir**

Run: a conferência do web e `corepack pnpm --filter web e2e` contra o ambiente E2E
(`docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait`).
Expected: carga inicial ≤ 190 KB e tudo verde.

- [ ] **Step 4: Commit**

```bash
git add web
git commit -m "perf(web): trim the initial load below 190 KB"
```

---

### Task 7: Build por ambiente e cabeçalhos de segurança do front

**Files:**
- Create: `web/build/securityHeaders.ts`, `web/build/securityHeaders.test.ts`
- Modify: `web/vite.config.ts`, `web/vitest.config.ts` (inclui `build/**/*.test.ts`),
  `web/src/vite-env.d.ts` (`VITE_PRIVACY_CONTROLLER`, `VITE_PRIVACY_CONTACT`), `web/tsconfig*.json`
  se o `build/` precisar entrar no `tsc -b`

**Interfaces:**
- Consumes: `VITE_API_URL` e `VITE_API_MOCKS` (já existem).
- Produces:
  - `buildSecurityHeaders(apiUrl: string): Record<string, string>`, com a CSP e os cabeçalhos do D4;
  - `toHeadersFile(headers: Record<string, string>): string`, no formato `_headers` do Pages
    (`/*` e uma linha recuada por cabeçalho);
  - `securityHeadersPlugin(): Plugin`, que emite `_headers` no build, apaga
    `mockServiceWorker.js` do `dist` quando `VITE_API_MOCKS` não é `true` e preenche
    `preview.headers` com os mesmos cabeçalhos.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `cspAllowsOnlyTheApiOrigin` | `buildSecurityHeaders('https://api.liberdadefamilia.com.br')` → `connect-src 'self' https://api.liberdadefamilia.com.br` |
| `cspUsesOriginNotPath` | `'https://api.example.com/v1/'` → `connect-src` com `https://api.example.com`, sem caminho |
| `cspHasNoUnsafeDirectives` | a CSP não contém `unsafe-inline`, `unsafe-eval` nem `*` |
| `cspLocksFramingAndBase` | contém `frame-ancestors 'none'`, `base-uri 'self'`, `object-src 'none'` |
| `hstsAndFriends` | HSTS `max-age=31536000; includeSubDomains`, `nosniff`, `no-referrer`, `Permissions-Policy` com `publickey-credentials-get=(self)` |
| `headersFileFormat` | `toHeadersFile` → primeira linha `/*`, depois `  Nome: valor` por cabeçalho |
| `rejectsInvalidApiUrl` | `''` ou `'ftp://x'` → lança erro (o build falha em vez de publicar CSP errada) |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- securityHeaders`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga as Interfaces e o D4. Sem `VITE_API_URL`, o plugin usa `http://localhost:8080`, o mesmo padrão
de `src/shared/api/config.ts`. O servidor de desenvolvimento (`vite`) fica sem a CSP.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: a conferência do web; depois `VITE_API_URL=https://api-staging.liberdadefamilia.com.br corepack pnpm --filter web build` e `cat web/dist/_headers`; e `corepack pnpm --filter web build --mode mocks` com `ls web/dist/mockServiceWorker.js`.
Expected: tudo verde; o `_headers` com a API do staging; sem `mockServiceWorker.js` no build normal
e com ele no modo de mocks.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): generate security headers per environment"
```

---

### Task 8: Política de privacidade, termos e o aceite no web

**Files:**
- Create em `web/src/features/legal/`: `PrivacyPolicyPage.tsx`, `TermsPage.tsx`, `LegalLayout.tsx`,
  `DocumentsGate.tsx`, `useDocuments.ts`, `legalConfig.ts`, `messages.pt-BR.ts` e os testes
- Modify: `web/src/app/routes.tsx`, `web/src/app/SessionLayout.tsx`, `web/src/app/MorePage.tsx`,
  `web/src/features/auth/SignInPage.tsx`, `web/src/features/members/AcceptInvitationPage.tsx`,
  `web/src/features/privacy/PrivacyPage.tsx`, `web/src/features/privacy/activityPhrase.ts`,
  `web/src/shared/api/{endpoints,mutations,queryKeys,types}.ts`

**Interfaces:**
- Consumes: `GET /v1/me/documents` e `POST /v1/me/documents/accept` (Task 2).
- Produces:
  - `useDocuments(): { pending: DocumentStatus[] | undefined; accept(): Promise<void> }`; `pending`
    fica `undefined` enquanto carrega, sem conexão ou com erro;
  - `legalConfig`: `controllerName` (de `VITE_PRIVACY_CONTROLLER`, padrão "o mantenedor do projeto
    Liberdade"), `contactEmail` (de `VITE_PRIVACY_CONTACT`, padrão
    `privacidade@liberdadefamilia.com.br`), `versions` (`terms-2026-11`, `privacy-2026-11`) e
    `updatedAt` ("novembro de 2026");
  - rotas públicas `/privacidade` e `/termos`, fora do `SessionLayout`, carregadas com `lazy()`.

**Como fica:**
- `SessionLayout`: quando `pending` tem itens, mostra o `DocumentsGate` (carregado com `lazy()`) no
  lugar das telas da família. O gate tem o título "Antes de começar", um parágrafo curto, os links
  "Termos de uso" e "Política de privacidade" (abrem em nova aba), uma caixa "Li e aceito os termos
  de uso e a política de privacidade" e o botão "Continuar", desabilitado até marcar e sem conexão.
  Se o aceite responder 409, o web recarrega os documentos e mostra o gate de novo.
- `AcceptInvitationPage`: mostra "Ao aceitar, você concorda com os Termos de uso e a Política de
  privacidade", com os links; depois de o convite ser aceito, chama `accept()` antes de navegar.
- `SignInPage`: rodapé com os dois links. `MorePage` e `PrivacyPage`: link "Política de
  privacidade" e "Termos de uso".
- `activityPhrase.ts`: `documents.accepted` → "Aceitou os termos e a política de privacidade".
- **Política de privacidade** (texto simples, frases curtas, títulos como perguntas), nesta ordem:
  1. Quem cuida dos seus dados: o controlador (`controllerName`), que também é o encarregado, e o
     contato (`contactEmail`).
  2. Que dados guardamos e para quê (LGPD art. 9º, I e II): nome e apelido, e-mail da conta Google,
     a chave pública das passkeys, lançamentos, contas e categorias, a trilha de atividade (sem IP,
     sem valores; só navegador e sistema), os aceites e consentimentos. Um cookie só, de sessão,
     necessário para entrar. Sem anúncio, sem venda de dados, sem rastreador.
  3. Base legal: execução do contrato para o serviço; consentimento específico de um responsável
     para o Jovem; obrigação legal e exercício de direitos para a trilha de atividade.
  4. Jovem (art. 14): o Jovem só entra com o consentimento de um Dono ou Adulto da família, que pode
     revogá-lo; o Jovem vê só a própria carteira; o melhor interesse dele orienta o app.
  5. Com quem os dados são tratados (art. 9º, V): AWS (servidores em São Paulo, Brasil),
     Cloudflare (entrega do site, DNS e encaminhamento de e-mail), Google (login). Transferência
     internacional pelos operadores Cloudflare e Google (art. 33).
  6. Por quanto tempo (art. 9º, II): enquanto a conta existir; depois do pedido de exclusão, 30 dias
     de carência e apagamento; cópias de segurança do banco somem em até 7 dias; logs técnicos em
     até 30 dias; os prazos da seção de retenção do modelo de dados.
  7. Seus direitos (art. 18) e como usar: baixar os dados e excluir a conta em Mais → Privacidade;
     corrigir no próprio app; revogar o consentimento do Jovem; qualquer outro pedido pelo e-mail,
     respondido em até 15 dias.
  8. Segurança: HTTPS, banco criptografado em São Paulo, sem senha, passkeys, menor privilégio.
  9. Mudanças: versão e data no topo; uma versão nova pede aceite de novo.
- **Termos de uso**, nesta ordem: o que é o Liberdade; uso gratuito e código aberto (AGPL-3.0); o
  conteúdo é educativo e informativo, sem recomendação personalizada de investimento (atividade
  regulada pela CVM); a família é responsável pelo que registra e por quem convida; o serviço é
  oferecido como está, sem garantia de disponibilidade; como excluir a conta; lei brasileira e o
  contato.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `PrivacyPolicyPage.showsControllerAndContact` | com `VITE_PRIVACY_CONTROLLER` vazio → "o mantenedor do projeto Liberdade" e o e-mail padrão; com valor → o valor |
| `PrivacyPolicyPage.coversRequiredSections` | os nove títulos aparecem, na ordem, e a versão `privacy-2026-11` |
| `TermsPage.hasCvmNotice` | o aviso de conteúdo educativo, sem recomendação personalizada |
| `routes.legalPagesArePublic` | sem sessão, `/privacidade` e `/termos` abrem sem ir para `/entrar` |
| `SessionLayout.gateWhenPending` | `pendingDocuments` → "Antes de começar" no lugar do Início |
| `SessionLayout.noGateWhenOffline` | consulta com erro de rede → o Início abre, sem gate |
| `DocumentsGate.continueNeedsCheckbox` | "Continuar" desabilitado até marcar; marcado → chama o aceite e mostra o Início |
| `DocumentsGate.reloadsOn409` | aceite com 409 → busca os documentos de novo e continua no gate |
| `DocumentsGate.disabledOffline` | sem conexão → botão desabilitado com a explicação |
| `AcceptInvitationPage.acceptsDocumentsAfterInvite` | aceite de convite bem-sucedido → `POST /v1/me/documents/accept` com as versões atuais |
| `activityPhrase.documentsAccepted` | a frase de `documents.accepted` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- legal SessionLayout AcceptInvitationPage activityPhrase routes`
Expected: FAIL nos testes novos.

- [ ] **Step 3: Implementar**

Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: a conferência do web.
Expected: tudo verde, carga inicial dentro do limite.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): publish the privacy policy and terms and ask for acceptance"
```

---

### Task 9: Passkeys em Privacidade e o convite do Jovem com conta logada

**Files:**
- Create em `web/src/features/privacy/`: `PasskeyList.tsx`, `RemovePasskeySheet.tsx` e os testes
- Modify: `web/src/features/privacy/PrivacyPage.tsx`, `activityPhrase.ts`, `messages.pt-BR.ts`,
  `web/src/features/members/AcceptInvitationPage.tsx`, `web/src/shared/api/{endpoints,mutations,queryKeys,types,errors}.ts`

**Interfaces:**
- Consumes: `GET /v1/me/passkeys` e `DELETE /v1/me/passkeys/{id}` (Task 3); `useReauth` e o
  `ReauthDialog` (Plano 4); `invitation.youth-needs-passkey` (Plano 4).
- Produces: a seção "Passkeys" em Mais → Privacidade.

**Como fica:**
- `PasskeyList`: uma linha por passkey com o nome, "Criada em <data>" e "Último uso em <data>" (ou
  "Nunca usada"); botão "Remover" de 44 px com o nome no rótulo acessível. Vazia: "Nenhuma passkey
  neste perfil". Desabilitado sem conexão.
- `RemovePasskeySheet`: confirma "Remover a passkey <nome>?"; a remoção passa pelo `useReauth`; 422
  `passkey.last-method` mostra "Esta é a única forma de entrar na sua conta. Crie outra passkey
  antes de remover esta."
- `AcceptInvitationPage`: tira a recusa local de conta logada em convite de Jovem; quem está logado
  tenta aceitar, e o 422 `invitation.youth-needs-passkey` mostra a mensagem da API com o botão
  "Sair e continuar", que sai da conta e volta ao convite.
- `activityPhrase.ts`: `passkey.removed` → "Removeu uma passkey".

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `PasskeyList.showsPasskeys` | duas passkeys do MSW → dois itens com nome e datas em pt-BR |
| `PasskeyList.empty` | lista vazia → "Nenhuma passkey neste perfil" |
| `RemovePasskeySheet.removesAfterReauth` | confirmar → reautenticação pedida pelo 403 → repete e a lista atualiza |
| `RemovePasskeySheet.lastMethodMessage` | 422 `passkey.last-method` → a mensagem acima, nada removido |
| `AcceptInvitationPage.signedInYouthInviteTriesApi` | logado com passkey, convite de Jovem → chama o aceite (antes não chamava) |
| `AcceptInvitationPage.googleAccountGetsSignOutOption` | 422 `invitation.youth-needs-passkey` → mensagem e "Sair e continuar" |
| `activityPhrase.passkeyRemoved` | a frase de `passkey.removed` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- PasskeyList RemovePasskeySheet AcceptInvitationPage activityPhrase`
Expected: FAIL nos testes novos.

- [ ] **Step 3: Implementar**

Siga "Como fica". A seção entra no `PrivacyPage`, que já é carregado sob demanda.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: a conferência do web.
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): manage passkeys and let a signed-in account try a youth invitation"
```

---

### Task 10: Host: Compose, Caddy, `deploy.sh` e a validação no CI

**Files:**
- Create em `deploy/`: `compose.yaml`, `Caddyfile`, `deploy.sh`, `smoke.sh`, `tags.env.example`,
  `test/compose.ci.yaml`, `test/Dockerfile.unhealthy`, `test/deploy-test.sh`
- Modify: `.github/workflows/ci.yml` (jobs `deploy-lint` e `deploy-test`)

**Interfaces:**
- Consumes: a imagem da API (`ghcr.io/warthogozz-cyber/liberdade-api`), o perfil `prod` (Task 1), o
  healthcheck do `compose.yaml` da raiz.
- Produces:
  - `deploy.sh <staging|production> <tag> <pasta-da-release>`: código 0 com a versão nova saudável;
    código 1 quando voltou para a anterior ou quando não havia anterior.
  - `smoke.sh <url-da-api> <url-do-front>`: código 0 quando tudo responde como esperado.
  - Variáveis só para teste: `LIBERDADE_ROOT` (padrão `/opt/liberdade`), `LIBERDADE_ENV_SOURCE`
    (`ssm`, padrão, ou `file`), `LIBERDADE_COMPOSE_EXTRA` (um arquivo Compose a mais),
    `LIBERDADE_SKIP_CADDY=1` e `HEALTH_TIMEOUT_SECONDS` (padrão 180).

**Como fica:**
- `compose.yaml`:
  - rede `liberdade` com sub-rede fixa `172.30.0.0/24`;
  - `caddy` (`caddy:2.11.7-alpine@sha256:<digest arm64 conferido na hora>`), portas 80 e 443,
    volumes `caddy_data` e `caddy_config`, `Caddyfile` só leitura, `mem_limit: 128m`,
    `restart: unless-stopped`;
  - `api-production` e `api-staging`, imagem `${API_IMAGE:-ghcr.io/warthogozz-cyber/liberdade-api}`
    com as tags `${PRODUCTION_TAG}` e `${STAGING_TAG}` de `/opt/liberdade/tags.env`, `env_file`
    `/run/liberdade/<ambiente>.env`, o pacote da RDS em `/certs/rds-bundle.pem` só leitura, o mesmo
    healthcheck do `compose.yaml` da raiz, `mem_limit: 768m`, `restart: unless-stopped`, sem porta
    publicada;
  - logging `awslogs` por serviço (`awslogs-group` do D11, `awslogs-stream` = nome do serviço,
    `mode: non-blocking`, `max-buffer-size: 4m`).
- `Caddyfile`: `api.liberdadefamilia.com.br` → `reverse_proxy api-production:8080`;
  `api-staging.liberdadefamilia.com.br` → `reverse_proxy api-staging:8080`; `header -Server` nos
  dois; sem log de acesso.
- `deploy.sh`, nesta ordem:
  1. valida os argumentos e o formato da tag (`sha-[0-9a-f]{40}`);
  2. `docker compose -f <pasta>/compose.yaml config -q` e copia `compose.yaml` e `Caddyfile` para
     `$LIBERDADE_ROOT`;
  3. grava `/run/liberdade/<ambiente>.env` (0600) com `aws ssm get-parameters-by-path --path
     /liberdade/<ambiente>/ --with-decryption --recursive`, uma linha `NOME=valor` por parâmetro,
     sem imprimir nada;
  4. guarda a tag anterior do ambiente, grava a nova em `tags.env`, `docker compose pull
     api-<ambiente>` e `up -d --no-deps api-<ambiente>`;
  5. sobe o Caddy se faltar e roda `caddy reload --config /etc/caddy/Caddyfile` dentro dele;
  6. espera o estado `healthy` do contêiner por até `HEALTH_TIMEOUT_SECONDS`;
  7. se não ficar saudável: volta a tag anterior, `up -d --no-deps` de novo, espera e sai com 1,
     imprimindo "rolled back to <tag>";
  8. no sucesso, apaga imagens sem uso com mais de 7 dias e mantém só as 5 releases mais novas em
     `$LIBERDADE_ROOT/releases`.
- `smoke.sh`: `GET <api>/actuator/health/readiness` responde 200 com `"UP"` (até 10 tentativas,
  6 s entre elas); `GET <api>/v1/me` responde 401 com `application/problem+json`; `GET <front>/`
  responde 200 com `content-security-policy` contendo a origem da API.
- CI:
  - `deploy-lint`: `actionlint` (imagem fixada por digest) em `.github/workflows`; `shellcheck` em
    `deploy/*.sh` e `deploy/test/*.sh`; `docker compose -f deploy/compose.yaml --env-file
    deploy/tags.env.example config -q`; `caddy validate --adapter caddyfile` com a mesma imagem do
    Caddy.
  - `deploy-test`: constrói a imagem da API, roda `deploy/test/deploy-test.sh`.
- `deploy-test.sh` (com `compose.ci.yaml`, que troca o `awslogs` por `json-file`, acrescenta um
  `postgres:18` e lê o ambiente de um arquivo): primeiro deploy saudável → código 0; deploy de uma
  imagem `Dockerfile.unhealthy` (a imagem boa com `ENTRYPOINT ["sleep","infinity"]`) com
  `HEALTH_TIMEOUT_SECONDS=30` → código 1, `tags.env` com a tag boa e o contêiner saudável de novo;
  primeiro deploy já ruim, sem anterior → código 1.

- [ ] **Step 1: Escrever o `deploy-test.sh` (falhando)**

Os três cenários de "Como fica", cada um com a checagem do código de saída, do `tags.env` e do
estado do contêiner.

- [ ] **Step 2: Rodar e ver falhar**

Run: `bash deploy/test/deploy-test.sh`
Expected: FAIL (o `deploy.sh` não existe).

- [ ] **Step 3: Implementar**

Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: `bash deploy/test/deploy-test.sh`
Expected: os três cenários passam.

- [ ] **Step 5: Conferir**

Run: `docker run --rm -v "$PWD:/repo" -w /repo <imagem do actionlint>`;
`docker run --rm -v "$PWD:/mnt" <imagem do shellcheck> deploy/*.sh deploy/test/*.sh`;
`docker compose -f deploy/compose.yaml --env-file deploy/tags.env.example config -q`;
`docker run --rm -v "$PWD/deploy/Caddyfile:/etc/caddy/Caddyfile:ro" <imagem do caddy> caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile`
Expected: tudo sem erro.

- [ ] **Step 6: Commit**

```bash
git add deploy .github/workflows/ci.yml
git commit -m "feat(deploy): add the host compose stack, Caddy and a deploy script with rollback"
```

---

### Task 11: Infra: estado, rede, KMS, EC2 e RDS

**Files:**
- Create: `infra/bootstrap/*` e, em `infra/live/`, `versions.tf`, `providers.tf`, `variables.tf`,
  `network.tf`, `kms.tf`, `compute.tf`, `user_data.sh.tftpl`, `database.tf`, `outputs.tf`,
  `backend.hcl.example`, `terraform.tfvars.example`, `tests/security.tftest.hcl`,
  `.terraform.lock.hcl`; `infra/sql/bootstrap.sql`
- Modify: `.gitignore`, `.github/workflows/ci.yml` (job `infra`)

**Interfaces:**
- Consumes: o D2 (nomes), o D9 (banco), o D10 (OpenTofu).
- Produces (outputs de `infra/live`): `instance_id`, `elastic_ip`, `rds_endpoint`, `kms_key_arn`,
  `rds_master_secret_arn`; variáveis `domain` (padrão `liberdadefamilia.com.br`), `alert_email`,
  `github_repository` (padrão `warthogozz-cyber/liberdade`), `monthly_budget_usd` (padrão 60).

**Como fica:**
- `bootstrap`: bucket S3 do estado com versionamento, criptografia, bloqueio de acesso público
  total, política que nega acesso sem TLS e `prevent_destroy`.
- `versions.tf`: `required_version = "~> 1.13.2"`; `hashicorp/aws` `= 6.70.0`; backend `s3` parcial
  (o bucket vem de `backend.hcl`, ignorado pelo Git) com `use_lockfile = true` e `encrypt = true`.
- Provider com `region = "sa-east-1"` e `default_tags` (`project = liberdade`).
- Rede: VPC `10.20.0.0/16`, uma sub-rede pública (EC2) e duas privadas em zonas diferentes (RDS),
  internet gateway, sem NAT.
- KMS: uma chave do projeto com rotação anual e alias `alias/liberdade`.
- EC2: t4g.small, AMI do parâmetro SSM
  `/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64`, EBS raiz gp3 de 20 GB
  criptografado com a chave, `metadata_options` com `http_tokens = "required"` e
  `http_put_response_hop_limit = 1`, sem `key_name`, `credit_specification` `standard`, IP elástico,
  `ignore_changes = [ami, user_data]`. Security group: entrada só TCP 80 e 443; saída livre.
- `user_data.sh.tftpl`: instala Docker e o plugin Compose (binário do release com SHA-256
  conferido), cria swap de 1 GB, baixa o pacote de certificados da RDS de `sa-east-1` para
  `/opt/liberdade/certs/rds-bundle.pem`, cria `/opt/liberdade` e liga o Docker.
- Papel da instância: `AmazonSSMManagedInstanceCore`, `ssm:GetParametersByPath` e
  `ssm:GetParameters` só em `parameter/liberdade/*`, `kms:Decrypt` só na chave do projeto, e
  `logs:CreateLogStream` e `logs:PutLogEvents` só nos grupos do D11.
- RDS: PostgreSQL 18, db.t4g.micro, gp3 de 20 GB, criptografada com a chave,
  `publicly_accessible = false`, `backup_retention_period = 7`, `deletion_protection = true`,
  `skip_final_snapshot = false`, `manage_master_user_password = true` com a chave do projeto,
  `auto_minor_version_upgrade = true`, grupo de parâmetros com `rds.force_ssl = 1`; security group
  com entrada só da EC2 na porta 5432.
- `bootstrap.sql`: cria os papéis `liberdade_production` e `liberdade_staging` com `LOGIN` e senha
  das variáveis `:'production_password'` e `:'staging_password'`, um banco de mesmo nome para cada
  um, com o papel como dono, e `REVOKE CONNECT ON DATABASE … FROM PUBLIC`. Pode rodar de novo sem
  erro (`\gexec` com checagem de existência).
- CI, job `infra`: baixa o OpenTofu 1.13.2 do release oficial, confere o SHA-256, e roda
  `tofu fmt -check -recursive infra`, e em `infra/bootstrap` e `infra/live`: `tofu init
  -backend=false`, `tofu validate` e `tofu test`.
- `.terraform.lock.hcl` com os hashes de `linux_amd64`, `linux_arm64`, `darwin_arm64` e
  `windows_amd64` (`tofu providers lock`).

- [ ] **Step 1: Escrever os testes (falhando)**

Em `tests/security.tftest.hcl`, com `mock_provider "aws"` e `override_data` para a AMI, as zonas e a
conta, um `run` com `command = plan` por linha:

| Teste | Esperado |
|---|---|
| `instance_requires_imdsv2` | `http_tokens == "required"` e `http_put_response_hop_limit == 1` |
| `instance_has_no_ssh_key` | `key_name` nulo |
| `only_web_ports_open` | as regras de entrada do SG da EC2 são exatamente TCP 80 e 443 |
| `root_volume_encrypted` | o volume raiz é criptografado com a chave do projeto |
| `database_is_private_and_encrypted` | `publicly_accessible == false`, `storage_encrypted == true`, `kms_key_id` da chave |
| `database_is_protected` | `deletion_protection`, `backup_retention_period == 7`, `skip_final_snapshot == false` |
| `database_forces_tls` | o grupo de parâmetros tem `rds.force_ssl = 1` |
| `database_reachable_only_from_instance` | a única entrada do SG da RDS é a porta 5432 a partir do SG da EC2 |
| `key_rotation_enabled` | `enable_key_rotation == true` |
| `instance_role_least_privilege` | a política da instância não tem `"*"` em `Action` nem em `Resource`, fora a gerenciada do SSM |
| `state_bucket_locked_down` (em `infra/bootstrap`) | versionamento ligado, bloqueio de acesso público com as quatro opções verdadeiras, política negando `aws:SecureTransport = false` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `tofu -chdir=infra/live init -backend=false && tofu -chdir=infra/live test`
Expected: FAIL (os recursos não existem).

- [ ] **Step 3: Implementar**

Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2, e o equivalente em `infra/bootstrap`.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `tofu fmt -check -recursive infra`; `tofu -chdir=infra/live validate`;
`git status --ignored infra` (nenhum `.tfvars`, `backend.hcl`, `.terraform/` nem estado versionado)
Expected: tudo sem erro.

- [ ] **Step 6: Commit**

```bash
git add infra .gitignore .github/workflows/ci.yml
git commit -m "feat(infra): add the OpenTofu stack for the network, instance and database"
```

---

### Task 12: Infra: papel do deploy, logs, alarmes e orçamento

**Files:**
- Create em `infra/live/`: `deploy_role.tf`, `logs.tf`, `alarms.tf`, `budget.tf`,
  `tests/operations.tftest.hcl`
- Modify: `infra/live/outputs.tf`, `infra/live/variables.tf`

**Interfaces:**
- Consumes: a instância, a RDS e a chave da Task 11.
- Produces: outputs `deploy_role_arn` e `alerts_topic_arn`; os grupos de log que a Task 10 usa.

**Como fica:**
- OIDC: provedor `token.actions.githubusercontent.com` com `client_id_list = ["sts.amazonaws.com"]`.
- Papel `liberdade-deploy`: confia só em `repo:<github_repository>:environment:staging` e
  `repo:<github_repository>:environment:production` (`StringEquals` em `sub` e `aud`), sessão de
  1 hora. Permite `ssm:SendCommand` só na instância e no documento `AWS-RunShellScript`, e
  `ssm:GetCommandInvocation` e `ssm:ListCommandInvocations` (que não aceitam recurso específico).
- Logs: grupos `/liberdade/production`, `/liberdade/staging` e `/liberdade/caddy`, retenção de 30
  dias.
- Alarmes do D11, com ação no tópico SNS `liberdade-alerts` (assinatura por e-mail de
  `alert_email`); o de status da EC2 também com a ação de recuperação
  `arn:aws:automate:sa-east-1:ec2:recover`.
- Orçamento mensal `monthly_budget_usd`, `cost_types` sem créditos nem reembolsos, avisos em 80% do
  real e 100% do previsto para `alert_email`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `deploy_role_trusts_only_environments` | a política de confiança tem exatamente os dois `sub` de Environment e `aud` = `sts.amazonaws.com` |
| `deploy_role_cannot_do_more` | as ações permitidas são só `ssm:SendCommand`, `ssm:GetCommandInvocation` e `ssm:ListCommandInvocations`; o `SendCommand` só na instância e no `AWS-RunShellScript` |
| `logs_kept_30_days` | os três grupos com `retention_in_days == 30` |
| `alarms_cover_the_basics` | existem os quatro alarmes, todos com a ação no tópico |
| `instance_recovers_itself` | o alarme de status tem a ação `ec2:recover` |
| `budget_ignores_credits` | `include_credit == false` e as duas notificações |

- [ ] **Step 2: Rodar e ver falhar**

Run: `tofu -chdir=infra/live test -filter=tests/operations.tftest.hcl`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: `tofu -chdir=infra/live test`
Expected: PASS nos dois arquivos de teste.

- [ ] **Step 5: Conferir**

Run: `tofu fmt -check -recursive infra; tofu -chdir=infra/live validate`
Expected: sem erro.

- [ ] **Step 6: Commit**

```bash
git add infra
git commit -m "feat(infra): add the deploy role, logs, alarms and budget"
```

---

### Task 13: Pipeline: deploy do staging e da produção

**Files:**
- Create: `.github/workflows/deploy.yml`
- Modify: `.github/workflows/main.yml`, `.github/workflows/release-please.yml`

**Interfaces:**
- Consumes: `deploy.sh` e `smoke.sh` (Task 10); `deploy_role_arn` e `instance_id` (Tasks 11 e 12),
  como variáveis dos Environments; o plugin de cabeçalhos (Task 7).
- Produces: o workflow reutilizável `deploy.yml` com as entradas `environment` (`staging` ou
  `production`) e `ref` (SHA, ou tag no `workflow_dispatch`).
- Variáveis (o mantenedor cria, Task 15): de repositório, `DEPLOY_ENABLED`; por Environment,
  `AWS_DEPLOY_ROLE_ARN`, `EC2_INSTANCE_ID`, `API_URL`, `WEB_URL`, `PAGES_PROJECT`,
  `CLOUDFLARE_ACCOUNT_ID`, `PRIVACY_CONTROLLER`, `PRIVACY_CONTACT`; segredo por Environment,
  `CLOUDFLARE_API_TOKEN`.

**Como fica:**
- `main.yml`: `type=sha,format=long` no lugar de `type=sha`; novo job `deploy-staging` (`needs:
  image`, `if: vars.DEPLOY_ENABLED == 'true'`, `uses: ./.github/workflows/deploy.yml`, `with:
  environment: staging, ref: ${{ github.sha }}`, `secrets: inherit`, `permissions: contents: read,
  id-token: write`).
- `release-please.yml`: o job atual ganha `outputs` (`release_created`, `tag_name`); job `retag`
  (`if` com `release_created` e `DEPLOY_ENABLED`, `packages: write`) espera até 30 min a imagem
  `sha-${{ github.sha }}` e cria a tag `${{ tag_name }}` com `docker buildx imagetools create`; job
  `deploy-production` (`needs: [release-please, retag]`) chama o `deploy.yml` com `production`.
- `deploy.yml` (`on: workflow_call` e `workflow_dispatch`; `concurrency: deploy-${{
  inputs.environment }}`, sem cancelar; `permissions: {}`; job com `environment: ${{
  inputs.environment }}`, `contents: read`, `id-token: write`):
  1. checkout do `ref` sem credenciais; resolve o SHA completo;
  2. espera a imagem `sha-<sha>` existir (até 30 min);
  3. assume o papel com a action oficial de credenciais da AWS (versão do Global Constraints);
  4. `aws ssm send-command` com o comando do D6 (montado com `jq`) e acompanha o
     `get-command-invocation` até o fim (até 10 min), mostrando a saída; falha se o status não for
     `Success`;
  5. build do front com `VITE_API_URL`, `VITE_PRIVACY_CONTROLLER` e `VITE_PRIVACY_CONTACT` das
     variáveis, e `wrangler pages deploy dist --project-name=${{ vars.PAGES_PROJECT }}
     --branch=main` com a mesma `wrangler-action` do `preview.yml`;
  6. `bash deploy/smoke.sh "${{ vars.API_URL }}" "${{ vars.WEB_URL }}"`.
  A API vai antes do front porque a API nova aceita o front antigo (expandir e contrair).

- [ ] **Step 1: Escrever os workflows**

Siga "Como fica".

- [ ] **Step 2: Conferir com o actionlint**

Run: `docker run --rm -v "$PWD:/repo" -w /repo <imagem do actionlint>`
Expected: sem erro.

- [ ] **Step 3: Conferir o comportamento sem AWS**

Run: depois do push do branch, `gh run list --branch feat/go-live` (só leitura, uma vez, depois que
o CI do PR terminar).
Expected: o CI verde; nenhum job de deploy roda, porque `DEPLOY_ENABLED` não existe.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows
git commit -m "ci: deploy staging on main and production on release over SSM"
```

---

### Task 14: E2E e acessibilidade

**Files:**
- Create: `web/e2e/legal.spec.ts`
- Modify: `web/e2e/support.ts`, `web/e2e/first-expense.spec.ts`, `web/e2e/privacy.spec.ts`,
  `web/e2e/family.spec.ts`, `web/e2e/a11y.spec.ts`

**Interfaces:**
- Consumes: tudo das Tasks 1 a 9; o ambiente E2E (`compose.e2e.yaml`, perfil `e2e`, mock-oauth2) e
  o `vite preview`, que agora serve a CSP (Task 7).
- Produces, em `support.ts`: um fixture que registra as mensagens do console com "Content Security
  Policy" e as respostas 5xx e faz o teste falhar se houver alguma; `acceptDocuments(page)`, que
  passa pela tela "Antes de começar".

**Specs:**
- `legal.spec.ts`:
  - `legalPagesArePublic`: sem sessão, `/privacidade` e `/termos` abrem com o título e a versão;
    da tela de entrada, os dois links chegam nelas;
  - `newPersonAcceptsBeforeUsing`: pessoa nova entra → "Antes de começar" → marca e continua → cria
    a família; entrar de novo não mostra o gate;
  - `inviteAcceptsDocuments`: o convidado aceita o convite e cai no Início sem ver o gate.
- `first-expense.spec.ts`: passa a incluir o gate, e o critério S1 (primeiro gasto em até 60 s)
  continua valendo com o aceite dentro do tempo.
- `privacy.spec.ts`, `managePasskeys`: com o autenticador virtual, cria duas passkeys, vê as duas
  em Privacidade, remove uma com reautenticação e vê `passkey.removed` na Atividade.
- `family.spec.ts`, `signedInAccountOnYouthInvite`: uma conta Google logada abre um convite de
  Jovem → vê a mensagem da API e "Sair e continuar".
- `a11y.spec.ts`: acrescentar `/privacidade`, `/termos`, o gate e a lista de passkeys com a folha de
  remover aberta, nos dois temas (sem violação `serious` ou `critical`).
- Instabilidade do painel: rodar `month-dashboard.spec.ts` com `--repeat-each=3`; se falhar, achar a
  causa (espera por resposta da rede no lugar de tempo fixo) no próprio spec.

- [ ] **Step 1: Escrever os specs**

Como em "Specs".

- [ ] **Step 2: Subir o ambiente e rodar**

Run: `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait`
Run: `corepack pnpm --filter web e2e`
Expected: na primeira rodada, falhas só onde faltar ajuste de seletor ou espera; nenhuma violação de
CSP e nenhum 5xx.

- [ ] **Step 3: Ajustar até passar**

Corrija seletores e esperas nos specs. Defeito de produto vira correção na task dona do código, num
commit `fix:` separado, com o teste que o pega.

- [ ] **Step 4: Rodar tudo**

Run: `corepack pnpm --filter web e2e; corepack pnpm --filter web exec playwright test e2e/month-dashboard.spec.ts --repeat-each=3`
Expected: todos os specs passam, e o painel passa 3 de 3.

- [ ] **Step 5: Derrubar o ambiente e conferir**

Run: `docker compose -f compose.yaml -f compose.e2e.yaml down`
Run (em `api/`): `./gradlew build`
Run: a conferência do web.
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add web/e2e
git commit -m "test(e2e): legal pages, acceptance, passkey management and a CSP guard"
```

---

### Task 15: Runbooks do lançamento

**Files:**
- Create: `docs/runbooks/go-live.md`, `docs/runbooks/rollback.md`, `docs/runbooks/restore-test.md`
- Modify: `docs/runbooks/ios-session-cookie.md` ("Onde anotar": o PR 3 do Plano 5)

**Interfaces:**
- Consumes: os outputs do OpenTofu, as variáveis da Task 13, o `bootstrap.sql`, o `smoke.sh`.
- Produces: o roteiro que o mantenedor segue na seção "Lançamento".

**Como fica:**
- Todos no formato do runbook do iPhone: Objetivo, Quando fazer, Antes de começar, Passos
  numerados com o que conferir em cada um, Se falhar. Sem dado pessoal e sem segredo: onde entra
  um valor do mantenedor, o texto diz `<seu e-mail>`, `<senha gerada>` e manda guardar no SSM.
- `go-live.md`, nesta ordem, cada passo com o comando ou a tela e a conferência:
  1. comprar o domínio no Registro.br;
  2. criar a zona no Cloudflare e trocar os servidores de nome no Registro.br; conferir com
     `dig NS liberdadefamilia.com.br`;
  3. criar a conta da AWS com 2FA no usuário raiz, um usuário administrador com 2FA para o dia a
     dia, e conferir os créditos;
  4. `tofu apply` em `infra/bootstrap`, depois `backend.hcl` e `terraform.tfvars` a partir dos
     exemplos, e `tofu init` e `tofu apply` em `infra/live`; anotar os outputs;
  5. confirmar a assinatura do e-mail de alertas do SNS;
  6. criar os registros A `api` e `api-staging` (só DNS) com o IP elástico;
  7. bancos: sessão de encaminhamento de porta do SSM para a RDS, a senha mestre lida do Secrets
     Manager, `psql -v production_password=… -v staging_password=… -f infra/sql/bootstrap.sql`;
  8. parâmetros do SSM (D7), com `--type SecureString --key-id alias/liberdade` nos segredos;
  9. clientes OAuth do Google (D16);
  10. projetos `liberdade-staging` e `liberdade-production` no Pages, com os domínios `staging.` e
      `app.`; redirecionamento do domínio de topo;
  11. Email Routing de `privacidade@` para a caixa do mantenedor; mandar um e-mail de teste;
  12. GitHub: Environments `staging` e `production` (este com revisor obrigatório e só a `main`),
      variáveis e segredos da Task 13, e o pacote `liberdade-api` do GHCR como público;
  13. `DEPLOY_ENABLED=true` (variável de repositório) e um push na `main` (ou um
      `workflow_dispatch` do `deploy.yml` para `staging`);
  14. conferir o staging: `smoke.sh` verde no workflow, login com Google, criar uma passkey,
      lançar um gasto;
  15. rodar o runbook do iPhone e o teste de restauração;
  16. aprovar o PR de release do release-please e o deploy da produção; conferir como no passo 14;
  17. teste S1 com 2 pessoas da família, cronometrado; anotar no PR 3;
  18. 2 semanas de uso; anotar no PR 3 o que travou e o custo do Cost Explorer.
- `rollback.md`: Actions → Deploy → Run workflow com o Environment e a tag ou SHA anterior;
  conferir o `smoke.sh`; se a versão nova tinha migração, conferir que ela só expandiu (D6); nunca
  `docker` à mão.
- `restore-test.md`: restaurar a RDS num instante dos últimos 7 dias para uma instância nova
  (`restore-db-instance-to-point-in-time`) com o mesmo grupo de parâmetros e SG; conectar pelo
  encaminhamento de porta; contar as linhas de `users` e `transactions` do banco de staging; apagar
  a instância restaurada sem snapshot final; anotar data, duração e resultado no PR 3. Custo: alguns
  centavos de dólar por hora de instância.

- [ ] **Step 1: Escrever os runbooks**

Siga "Como fica".

- [ ] **Step 2: Conferir**

Run: `git grep -nIiE -f .git/info/personal-data-patterns -- docs/runbooks` e a mesma busca com
`--untracked`
Expected: código de saída 1 nas duas.

Leia cada passo contra as Tasks 10 a 13: nomes de parâmetros, variáveis, Environments e caminhos
iguais aos do código.

- [ ] **Step 3: Commit**

```bash
git add docs/runbooks
git commit -m "docs(runbooks): add go-live, rollback and restore-test runbooks"
```

---

## Lançamento

Depois do merge do PR 2. Passos do mantenedor seguem o `docs/runbooks/go-live.md`; os do agente
ficam no chat.

| # | Quem | O quê | Pronto quando |
|---|---|---|---|
| 1 | Mantenedor | Passos 1 a 12 do `go-live.md` | os outputs do OpenTofu anotados e as variáveis no GitHub |
| 2 | Mantenedor | `DEPLOY_ENABLED=true` | o deploy do staging verde, com o `smoke.sh` |
| 3 | Agente | Conferir o staging pelo navegador do app (login, passkey, lançamento, cabeçalhos) e os logs no CloudWatch, só leitura | nenhuma violação de CSP, nenhum 5xx, logs em JSON sem dado pessoal |
| 4 | Mantenedor | Runbook do iPhone e teste de restauração | resultados anotados no PR 3 |
| 5 | Mantenedor | Aprovar o PR de release (v0.1.0) e o deploy da produção | `smoke.sh` verde na produção |
| 6 | Mantenedor | Teste S1 com 2 pessoas e 2 semanas de uso | anotados no PR 3 |
| 7 | Agente | PR 3: resultados, custo real, ADR-0014 "Aceito", Plano 5 "Concluído" | merge com checks verdes |

## Critérios de saída

- **S5:** a v0.1.0 está em produção, publicada só pelo `release-please.yml` com aprovação no
  Environment `production`; a `main` está protegida; o host não tem SSH; o papel do deploy só
  aceita os dois Environments.
- **iPhone:** o runbook passou nas duas esperas (10 minutos e dia seguinte), ou a issue de falha tem
  decisão do mantenedor.
- **Privacidade:** `/privacidade` e `/termos` no ar na produção, aceitos por quem entra; o e-mail
  `privacidade@` chega ao mantenedor.
- **Operação:** o teste de restauração passou no staging; os alarmes e o orçamento estão
  confirmados por e-mail.
- **S1:** o teste cronometrado com 2 pessoas da família passou, e a família usou o app por 2 semanas.
