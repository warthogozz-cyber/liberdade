# Liberdade · Plano 4: Família e privacidade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A família convida quem quiser com um link, cada pessoa tem um papel, o Jovem entra só com
passkey e consentimento parental e vê só a própria carteira, os módulos opcionais ligam e desligam
sem quebrar o núcleo, e cada pessoa exporta os dados, vê o histórico de acessos e exclui a conta.
O critério S2 passa num E2E com todos os módulos opcionais desligados, e exportar e excluir
funcionam. Cobre as US-09, US-10, US-11 e US-12.

**Architecture:** O `workspace` ganha membros, papéis, convites, recuperação de acesso e módulos, e
expõe `Memberships` como API pública, para o `ledger` não ler mais a tabela `memberships`. O
`identity` ganha a reautenticação de 10 minutos (`@RequiresRecentSignIn`), a entrada por convite e
por recuperação (`SessionSignIn`) e a consulta de pessoas (`UserDirectory`). Dois módulos novos: o
`audit`, que grava a trilha de auditoria ouvindo os eventos dos outros módulos, e o `privacy`, que
cuida de consentimentos, exportação e exclusão de conta e de família. O `ledger` aplica o escopo do
Jovem (só as contas em que ele é titular), aceita "Quem" e o titular da conta e ganha a leitura de
um lançamento por id. Um limitador de taxa genérico vai para o `shared`. No web, as telas novas
(Família, Convite, Módulos, Privacidade) carregam sob demanda; a família escolhida fica no
aparelho, a sessão se propaga entre abas e um diálogo de reautenticação atende as ações sensíveis.

**Tech Stack:**
- API: Spring Boot 4.1.2, Spring Modulith 2.1.1, `JdbcClient`, Flyway, PostgreSQL 18, springdoc 3.2.0.
  Sem novas dependências: o zip usa `java.util.zip`, e o token usa `SecureRandom` e `MessageDigest`.
- Web: React 19, react-router 8, TanStack Query, Dexie, Radix, react-intl. Sem novas dependências.
- Ferramentas: Playwright (com o autenticador virtual do CDP para passkeys) e
  `@axe-core/playwright`, que já existem.

**Spec:**
- [PRD](../../prd.md) (US-01, que define a reautenticação; US-09 a US-12; seções 8 e 9)
- [Arquitetura](../../architecture.md) (seções 4 e 6)
- [Modelo de dados](../../data-model.md) (seções 2 e 6)
- [Design system](../../design-system.md)
- [Wireframes](../../wireframes.md)
- [Roadmap](../../roadmap.md) (v0.1 Núcleo, critério S2)
- [ADRs](../../adr/README.md), em especial 0005 (sessão), 0007 (offline), 0008 (isolamento), 0012
  (`JdbcClient`) e 0013 (testes de propriedade)

## Global Constraints

- **Idiomas:** comunicação, UI, documentação de usuário e textos de PR em português do Brasil.
  Código, commits, nomes técnicos e comentários de código em inglês.
- **Commits e títulos de PR:** Conventional Commits.
  - Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Descrições de PR terminam com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Dados pessoais:** nenhum dado pessoal real (nome, cidade, e-mail) em arquivo versionado. Testes
  usam só nomes inventados ("Ana Teste", "Bia Teste") e domínios `example.com`.
  - `PROMPT_BIG_BRAIN.md` nunca é commitado.
  - Antes de cada commit, `git grep -nIiE -f .git/info/personal-data-patterns` não retorna nada
    (código de saída 1), e o mesmo vale com `--untracked`. O arquivo de padrões só existe na
    máquina do mantenedor e nunca é impresso.
- **Segredos:** nada de segredo em arquivo. Quem digita token é o mantenedor.
- **Senhas:** o app nunca pede nem guarda senha. O Jovem entra por passkey.
- **Sessão (ADR-0005):** cookie `__Host-sid` (HttpOnly, Secure, SameSite=Lax). Sessão anônima
  expira em 30 min sem uso; sessão autenticada, em 30 dias sem uso. Todo POST, PUT, PATCH e DELETE
  exige CSRF. Toda entrada nova (Google, passkey, convite, recuperação) troca o id da sessão.
- **Reautenticação (US-01):** exportar, excluir conta ou família, mudar papel, remover membro e gerar
  link de recuperação exigem login de no máximo 10 minutos (D1).
- **Minimização (LGPD):** a sessão guarda só o id da pessoa, o instante do login e o que o fluxo de
  entrada precisa enquanto dura. A trilha de auditoria nunca guarda IP, valores, descrições ou
  nomes de conta; do aparelho, só navegador e sistema ("Chrome", "Android"). Ninguém vê o e-mail de
  outra pessoa da família.
- **Isolamento (ADR-0008):** toda rota sob `/v1/workspaces/{ws}` tem `@WorkspaceScoped`, que nega
  por padrão. Todo método de repositório recebe `workspaceId`. FKs entre tabelas da família são
  compostas (`workspace_id`, `id`). O `WorkspaceGuardCoverageIT` continua passando.
- **Papéis (US-10):** a matriz da US-10 vale como está, testada rota a rota no `RoleMatrixIT`
  (Task 6). Leitura de membros, módulos e categorias vale para todos os papéis. O Jovem vê e lança
  só nas contas em que é titular e edita só o que ele mesmo lançou. Recurso fora do alcance do
  Jovem responde 404, como se não existisse; rota que o papel não pode usar responde 403.
- **Acesso a dados (ADR-0012):** `JdbcClient`, sem JPA.
- **Dinheiro:** nunca em ponto flutuante. `BigDecimal` dentro de `Money` na API; texto no JSON;
  `bigint` em centavos no web. Arredondamento `HALF_EVEN` só na exibição (ADR-0006).
- **Erros da API:** RFC 9457, com `code` estável e `detail` em pt-BR. Nunca expõem stack trace,
  nome de classe nem mensagem de exceção. 429 leva `Retry-After`.
- **Valores fora do domínio:** mensagens de exceção e logs nunca contêm valores, saldos, nomes,
  descrições nem tokens (PRD §9).
- **Tokens de convite:** só o hash SHA-256 fica no banco. O token nunca aparece em log, em path de
  URL nem em `Referer`: viaja no fragmento do link e no corpo do POST (D9).
- **Fronteiras de módulo:** Spring Modulith `verify()` na API e ESLint no web. Dependências
  permitidas: `workspace` → `identity`, `shared`; `ledger` → `workspace`, `shared`; `audit` →
  `identity`, `workspace`, `shared`; `privacy` → `identity`, `workspace`, `ledger`, `audit`,
  `shared`. `identity` não depende de ninguém além de `shared`.
- **Contrato:** toda mudança de rota ou DTO regenera `api/openapi.json` e
  `web/src/shared/api/schema.ts` (`corepack pnpm --dir web gen:api`) no mesmo commit.
- **Cobertura:** domínio e `shared` ≥ 90% de linhas; geral ≥ 80%; no web, `src/shared/**` com o
  gate atual. O build falha abaixo disso.
- **Testes de propriedade (ADR-0013):** `@ParameterizedTest` com `@MethodSource`, semente
  `20261102L`, 1000 casos.
- **Desempenho (PRD §9):** API com p95 abaixo de 300 ms; primeira carga abaixo de 3 s em 4G. A carga
  inicial continua com o limite de 200 KB do `check:chunks` (hoje 199,5 KB): toda tela nova é
  `lazy()` (D27).
- **Acessibilidade:** WCAG 2.2 AA; alvos de toque de 44 px ou mais; cor nunca sozinha;
  `prefers-reduced-motion` respeitado.
- **Offline (ADR-0007):** só criar lançamento funciona offline. Convidar, mudar papel, módulos,
  exportar e excluir ficam desabilitados sem conexão, com explicação.
- **GitHub Actions:** actions fixadas por SHA de 40 caracteres com comentário `# vX.Y.Z`;
  `permissions: {}` no topo e só o necessário por job.
- **Dependências:** só com licença compatível com AGPL-3.0 e publicadas em 2026-10-26 ou antes.
  Exceções só com aprovação do mantenedor no chat.
- **Terceiros:** nenhuma chamada a serviço de terceiros além do login com Google.
- **Versões:** as pesquisadas em 2026-11-16. Registre no PR 2 todo desvio.
- **Ações externas:** o mantenedor deu confirmação permanente para push, abrir PR e fazer merge com
  os checks obrigatórios verdes neste repositório. Force-push, reescrita de histórico, apagar
  trabalho não mesclado, configurações do repositório e segredos continuam exigindo confirmação na
  hora. Auto-merge só se ele pedir.

### Pré-requisitos locais

Os mesmos do Plano 3: Git, `gh` autenticado, Java 21+ para o Gradle (a toolchain baixa o JDK 25),
Node 24 com corepack (`corepack pnpm …`), Docker em execução para Testcontainers e E2E, e o
Chromium do Playwright instalado.

## Entrega

- **PR 1** (docs, branch `docs/plan-4`): sem código. Leva:
  - este plano;
  - o README dos planos, com o Plano 4 "Em execução" e com link (o Plano 3 já está "Concluído");
  - no modelo de dados: sai a regra de uma família ativa por pessoa; `memberships.user_id` passa a
    aceitar nulo (`ON DELETE SET NULL`, D24); entram `memberships.recovered_at` (D12) e
    `invitations.nickname` (D8); `consents` registra só `PARENTAL` na v0.1 (D21); FKs de autoria
    viram `SET NULL` (D24); a retenção ganha a linha da família excluída;
  - na arquitetura (seção 6): `POST /v1/invitations/preview` e `POST /v1/invitations/accept` com o
    token no corpo (D9); `GET …/invitations`; `GET …/transactions/{id}`; `PUT …/accounts/{id}/owner`
    (D16); sai `GET /v1/workspaces/{ws}` (o `/v1/me` já traz a família); a gestão de passkeys
    (`GET` e `DELETE /v1/me/passkeys`) fica marcada como Plano 5.
- **PR 2** (código, branch `feat/family-and-privacy`): Tasks 1 a 16, um commit por task.
  - O merge só acontece com os checks obrigatórios verdes e o E2E verde.
  - A descrição registra os desvios, as medições do `check:chunks` e os menores deixados de
    propósito.

### Itens herdados

| Item | Onde |
|---|---|
| `GET /transactions/{id}` para editar a partir de lista desatualizada (Plano 3) | Task 5 (API) e Task 14 (web) |
| API de membership no `workspace`, em vez de o `ledger` ler a tabela (Plano 3) | Task 2 |
| Propagar a sessão entre abas (Plano 2) | Task 12 |
| Apagar `user_entities` ao excluir a conta (Plano 2) | Task 11 |
| Renomear a família (Plano 2) | Task 2 (API) e Task 13 (web) |
| `/v1/me` com uma consulta por família (N+1, Plano 3) | Task 2 |
| Titular da conta visível e editável (D6 e D24 do Plano 3) | Task 5 (API) e Task 14 (web) |
| "Quem" escolhido no lançamento (D13 do Plano 3) | Task 5 (API) e Task 14 (web) |
| "Convidar a família" nos Primeiros passos (D15 do Plano 3) | Task 2 (API) e Task 13 (web) |
| Exclusão definitiva de contas e categorias junto com a família (D25 do Plano 3) | Task 11 |
| Jovem com as permissões reais, no lugar de "negado em tudo" (D13 do Plano 2) | Task 6 |
| Sai o índice de uma família ativa por pessoa (D14 do Plano 2) | Task 2 |

## Decisões deste plano

O spec deixa estes pontos em aberto, e o plano decide:

- **D1. Reautenticação.** O instante do login fica na sessão, no atributo `liberdade.authenticatedAt`,
  gravado quando o `MinimalSecurityContextRepository` converte um login novo (Google ou passkey) e
  quando o `SessionSignIn` faz uma entrada por convite ou recuperação. A anotação pública
  `@RequiresRecentSignIn` marca as rotas sensíveis; um interceptor responde **403**
  `auth.reauthentication-required` quando o login tem mais de 10 minutos. É 403, e não 401, porque
  o web trata todo 401 como sessão perdida e apaga os dados do aparelho.
  - Rotas com a anotação: as duas exportações, `POST /v1/me/deletion`, `POST …/deletion` da família,
    `PATCH …/members/{id}` quando muda o papel, `DELETE …/members/{id}` e
    `POST …/members/{id}/recovery`.
  - O web abre o diálogo "Confirme que é você": passkey primeiro, Google como alternativa. O Google
    vai com `prompt=select_account` (o Google não aceita `prompt=login`); antes de sair, o web
    guarda o caminho de volta no `sessionStorage` (só caminhos internos da lista branca), e o
    `RootRedirect` volta para ele. Depois do sucesso, o web repete a ação uma vez.
  - Se a reautenticação entrar com outra pessoa, o web apaga os dados do aparelho e vai para
    `/inicio`.
- **D2. Limitador de taxa genérico.** `shared.RateLimiter` com janela fixa, chave em texto, limite e
  janela por instância. Ele substitui o `LoginRateLimiter`: o login usa um `RateLimiter` com os
  mesmos 20 por minuto por IP, e o `shared.RateLimitedException` substitui o do `identity`.
  Estourar dá 429 `rate.limited` com `Retry-After` em segundos. Limites novos:
  - criar convite ou link de recuperação: 20 por hora por pessoa;
  - pré-visualizar e aceitar convite: 10 por minuto por IP;
  - exportar: 10 por hora por pessoa.
  - O mapa de janelas limpa as vencidas quando passa de 10.000 chaves, para não crescer sem fim.
- **D3. CORS** passa a aceitar `PUT` (módulos e titular da conta).
- **D4. Membros.** `GET …/members` vale para todos os papéis e devolve também os removidos, com o
  status, para os lançamentos antigos mostrarem o nome. O nome exibido é o apelido; sem apelido, o
  nome da pessoa; depois que a conta da pessoa é apagada e sem apelido, o web mostra "Ex-membro".
  Nenhum e-mail sai nessa rota.
  - `PATCH …/members/{id}` (`role`, `nickname`): só o Dono. Mudar o papel pede reautenticação.
  - `DELETE …/members/{id}`: só o Dono; o vínculo vira `REMOVED` e mantém os lançamentos.
  - Nenhuma das duas pode deixar a família sem Dono ativo: 422 `member.last-owner`.
  - Mudar o papel de alguém para Jovem exige `parentalConsent: true` no corpo (422
    `member.parental-consent-required`), e quem muda passa a ser o responsável.
  - Transferir a família é promover outra pessoa a Dona e depois deixar de ser Dono: a família
    aceita mais de um Dono.
- **D5. Renomear a família:** `PATCH /v1/workspaces/{ws}` com `name` (1 a 60 caracteres, sem espaços
  nas pontas), só o Dono.
- **D6. Mais de uma família.** Sai o índice de uma família ativa por pessoa. `POST /v1/workspaces`
  continua dando 409 `workspace.already-exists` só se a pessoa já for Dona de uma família ativa ou
  com exclusão pendente; quem só é membro de outra família pode criar a sua. Sem o índice, duas
  criações simultâneas da mesma pessoa se ordenam por uma trava transacional por pessoa
  (`pg_advisory_xact_lock`), e só uma passa. O web guarda a família escolhida em
  `localStorage['liberdade.workspaceId']`, cai na primeira da lista se ela sumir, e o Mais mostra
  "Trocar de família" quando há duas ou mais.
- **D7. Convite.** Dono e Adulto criam; o Adulto não convida Dono (403 `invitation.role-forbidden`).
  Vale 7 dias, uso único, revogável. Convite de Jovem exige `parentalConsent: true` (422
  `invitation.parental-consent-required`), e quem convida vira o responsável.
  - Aceitar sendo já membro ativo dá 409 `invitation.already-member`, sem gastar o convite. Quem foi
    removido e aceita um convite novo volta no mesmo vínculo, com o papel do convite.
  - Convite usado, vencido ou revogado dá 410 `invitation.gone`, sem dizer qual dos três. Token que
    não existe dá 404 `invitation.not-found`.
- **D8. Apelido no convite.** Quem convida escreve o apelido (1 a 40 caracteres, obrigatório). Ele
  vai para a coluna nova `invitations.nickname` (só no `INVITE`; na recuperação fica nulo) e é
  copiado para o vínculo ao aceitar. É o nome que a família vê e o nome da carteira do Jovem.
- **D9. Token no fragmento.** O link é `https://<app>/convite#<token>`. O token tem 32 bytes de
  `SecureRandom` em base64url (43 caracteres); o banco guarda `sha256(token)`. O web lê o fragmento,
  guarda o token no `sessionStorage` (`liberdade.invitationToken`), apaga o fragmento com
  `history.replaceState` e só manda o token no corpo de dois POSTs anônimos:
  - `POST /v1/invitations/preview {token}` → nome da família, apelido de quem convidou, papel,
    apelido do convidado (na recuperação, o do vínculo alvo) e o status;
  - `POST /v1/invitations/accept {token, newUser?: {displayName}}`.
- **D10. Aceitar.**
  - Com sessão: entra na família com a pessoa logada.
  - Sem sessão, com Google: o web guarda o token no `sessionStorage`, entra com o Google e, na
    volta, o `RootRedirect` leva de novo a `/convite`, que aceita.
  - Sem sessão, sem Google (o caminho do Jovem): `newUser.displayName` (1 a 200 caracteres) cria
    uma pessoa sem e-mail, a sessão troca de id e já fica autenticada, e o web obriga a criar a
    passkey antes de seguir. Fechar a tela antes disso deixa a pessoa sem como voltar; a tela avisa,
    e o caminho de volta é a recuperação (D12).
  - Aceitar grava `used_at` com um `UPDATE … WHERE used_at IS NULL AND revoked_at IS NULL AND
    expires_at > now()`; duas abas aceitando juntas: só uma entra, a outra recebe 410.
- **D11. Carteira do Jovem.** Quando um Jovem entra, o `ledger` cria a conta "Carteira de
  <apelido>" (tipo dinheiro, R$ 0,00), com ele como titular. Se o nome já existir entre as contas
  ativas, acrescenta " 2", " 3", até achar um livre.
- **D12. Recuperação de acesso.** É um convite do tipo `RECOVERY` que aponta para um vínculo. A
  linha guarda `nickname` nulo e o papel do vínculo alvo; o preview mostra o apelido do vínculo.
  - Dono ou Adulto criam, com reautenticação; o Adulto não recupera um Dono (403
    `invitation.role-forbidden`). Vale 24 h, uso único.
  - Só serve para quem não tem Google (422 `member.recovery-unavailable`); quem tem Google recupera
    pelo Google.
  - Aceitar entra como a pessoa do vínculo, obriga a criar uma passkey nova e grava
    `memberships.recovered_at`.
  - O aviso ao membro: o Início mostra "Seu acesso foi recuperado em <data>. Se não foi você,
    avise o Dono da família." por 7 dias depois de `recovered_at`, até a pessoa fechar (fechado
    fica no aparelho). O evento também aparece no histórico de acessos.
- **D13. Jovem no `ledger`.** O escopo do Jovem são as contas com `owner_membership_id` igual ao
  vínculo dele, arquivadas incluídas.
  - Contas: só as do escopo. Categorias: lê todas, não cria nem edita.
  - Lançamentos: lê os que tocam uma conta do escopo (origem ou destino). A transferência da mesada
    mostra o outro lado como "Conta da família".
  - Lançar: as contas do lançamento (as duas, na transferência) precisam estar no escopo; fora
    dele, a mesma resposta de conta inexistente.
  - Editar, excluir e restaurar: só o que ele mesmo lançou (`created_by`), com as contas no escopo;
    o resto dá 404 `transaction.not-found`.
  - Painel: saldo, receitas, gastos e categorias só das contas do escopo. Transferência de uma conta
    da família para a carteira entra no saldo, não nas receitas.
- **D14. Módulos.** As chaves são `PLANNING`, `INDICATORS`, `GAMIFICATION` e `VENTURES`, todas
  desligadas por padrão (sem linha na tabela é desligado). `GET …/modules` vale para todos os
  papéis; `PUT …/modules/{key} {enabled}` para Dono e Adulto. Uma rota de módulo opcional leva
  `@RequiresModule(key)`, e o guard responde 403 `module.disabled` com o módulo desligado.
  - No web, a tela Módulos mostra o Núcleo (sempre ligado, sem chave) e os quatro opcionais com a
    descrição e a chave desabilitada com o selo "Em breve", porque nenhum tem conteúdo até a v0.2.
    O mecanismo (registro, `useModuleEnabled` e a chave que funciona) é testado com um módulo de
    teste.
  - O critério S2 é provado pelo E2E: desliga os quatro pela API e percorre o núcleo inteiro.
- **D15. "Quem".** `memberId` passa a ser opcional ao lançar (nulo é quem lança; itens antigos da
  fila não têm o campo e continuam valendo) e ao editar (nulo mantém o atual). Precisa ser um
  vínculo ativo da família (422 `transaction.member-not-found`); manter um vínculo já removido
  vale. O Jovem só usa ele mesmo (422 `transaction.member-forbidden`).
- **D16. Titular da conta.** Como o `PATCH` parcial não distingue "ausente" de "nulo", o titular
  muda por uma rota própria: `PUT …/accounts/{id}/owner {membershipId: uuid | null}`, para Dono e
  Adulto. O titular novo precisa ser um vínculo ativo (422 `account.owner-not-found`); nulo tira o
  titular.
- **D17. `GET …/transactions/{id}`:** 200 com o lançamento; 409 `transaction.deleted` se estiver
  excluído; 404 `transaction.not-found` se não existir ou estiver fora do escopo do Jovem.
- **D18. Trilha de auditoria.** Módulo `audit`, tabela `audit_events`. Ouvintes síncronos, na mesma
  transação de quem publica (`@EventListener`), para o evento e a ação valerem juntos. Ações:
  `auth.signed_in`, `member.joined`, `member.role_changed`, `member.removed`,
  `invitation.created`, `invitation.revoked`, `recovery.created`, `recovery.used`,
  `module.toggled`, `workspace.renamed`, `data.exported`, `account.deletion_requested`,
  `account.deletion_cancelled`, `workspace.deletion_requested`, `workspace.deletion_cancelled`,
  `consent.granted` e `consent.revoked`. O `metadata` leva só chaves fixas (papel, método de
  entrada, navegador, sistema, chave de módulo, escopo da exportação).
- **D19. Histórico de acessos:** `GET /v1/me/activity` devolve, dos últimos 12 meses, até 100
  eventos em que a pessoa é autora ou alvo (alvo é o usuário ou um vínculo dele), do mais novo para
  o mais antigo. O histórico da família inteira fica para depois.
- **D20. Retenção por job:** auditoria apagada depois de 12 meses; convites apagados 90 dias depois
  de usados, vencidos ou revogados; contas de usuário e famílias apagadas 30 dias depois do pedido
  (D24 e D25). Todos com `@Scheduled(cron = "0 <m> 3 * * *", zone = "America/Fortaleza")`, em
  minutos diferentes, idempotentes e sem trava.
- **D21. Consentimentos.** Módulo `privacy`, tabela `consents`. Na v0.1 só existe `PARENTAL`,
  gravado quando o Jovem entra (`document_version` = `"parental-2026-11"`). `TERMS` e `PRIVACY`
  chegam com os textos da política no Plano 5.
  - `GET /v1/me/consents` lista os consentimentos dados para mim e os que eu dei como responsável.
  - `DELETE /v1/me/consents/{id}` só o responsável que deu (403 `consent.revoke-forbidden` para os
    outros). Revogar grava `revoked_at` e tira o Jovem da família (`REMOVED`), porque sem
    consentimento não há base legal para o tratamento (LGPD art. 14).
- **D22. Exportação.** `POST /v1/me/export` (qualquer membro) e `POST /v1/workspaces/{ws}/export`
  (só o Dono), as duas com reautenticação, devolvem um `.zip` na hora, montado com
  `java.util.zip`: `dados.json` mais CSVs.
  - CSV com `;`, UTF-8 com BOM, vírgula decimal sem separador de milhar e datas `AAAA-MM-DD`, que o
    Excel e o LibreOffice em pt-BR abrem sem ajuste. Célula que começa com `=`, `+`, `-`, `@`, tab
    ou CR ganha um `'` na frente (injeção de CSV). Valor sempre positivo, com a coluna `tipo`.
  - O JSON usa o formato da API (dinheiro em texto com ponto) e leva `formatVersion: 1`.
  - Minha exportação: perfil (nome, e-mail, idioma), vínculos, consentimentos, data das passkeys,
    meu histórico de acessos e os lançamentos em que sou "Quem" ou autor.
  - Exportação da família: família, membros (apelido, papel e status, sem e-mail), contas,
    categorias, lançamentos não excluídos e módulos.
- **D23. Excluir a conta.** `POST /v1/me/deletion` com reautenticação. Se a pessoa é a única Dona
  ativa de alguma família sem exclusão pendente, 409 `account.sole-owner`. Começa a carência de 30
  dias:
  - entrar continua funcionando; `/v1/me` traz `deletionScheduledFor`, e o web mostra só a tela
    "Sua conta será excluída em <data>", com "Cancelar exclusão" e "Baixar meus dados";
  - todas as outras rotas `/v1/**` dão 403 `account.deletion-pending`, menos `GET /v1/me`,
    `DELETE /v1/me/deletion`, `POST /v1/me/export`, `GET /v1/csrf` e `POST /logout`;
  - os vínculos continuam ativos na carência; a família vê a pessoa até o fim dela.
- **D24. Apagar a conta de vez.** A V11 muda `memberships.user_id` para aceitar nulo, com
  `ON DELETE SET NULL`, e faz o mesmo com `memberships.parental_consent_by`. As FKs de autoria das
  tabelas novas (`invitations.created_by`, `invitations.parental_consent_by`,
  `workspace_modules.updated_by` e `audit_events.actor_user_id`) já nascem anuláveis e
  `ON DELETE SET NULL` nas V7, V8 e V9. O job de cada noite, por pessoa vencida:
  1. passa os vínculos dela para `REMOVED` (os lançamentos ficam com o apelido);
  2. apaga as linhas de `user_entities` com `name` igual ao id dela (as credenciais vão junto, em
     cascata);
  3. apaga as sessões dela em `spring_session` (`principal_name` igual ao id);
  4. apaga o usuário (identidades e consentimentos vão em cascata).
- **D25. Excluir a família.** `POST /v1/workspaces/{ws}/deletion` (só o Dono, com reautenticação)
  marca `deletion_requested_at`. A família some para todos na hora: o guard responde 404 para
  qualquer rota dela, menos `DELETE …/deletion` e `POST …/export`, que levam
  `includePendingDeletion = true` e continuam só para o Dono. O `/v1/me` mostra a família pendente
  só aos Donos, com `deletionScheduledFor`. Depois de 30 dias, o job apaga a família, e tudo dela
  vai em cascata (contas, categorias, lançamentos, vínculos, convites, módulos, consentimentos); a
  auditoria fica com `workspace_id` nulo até vencer os 12 meses.
- **D26. Sessão entre abas.** `BroadcastChannel('liberdade-session')` com as mensagens
  `{type: 'signed-out'}` e `{type: 'signed-in', userId}`. A aba que recebe `signed-out` apaga os
  dados e vai para `/entrar`; a que recebe `signed-in` de outra pessoa apaga os dados e recarrega
  em `/inicio`. Sem `BroadcastChannel`, nada acontece.
- **D27. Carga inicial.** O limite continua em 200 KB. Tudo que é novo carrega sob demanda:
  `/mais/familia`, `/mais/familia/convidar`, `/convite` (fora do `WorkspaceLayout`, aceita sessão
  anônima), `/mais/modulos`, `/mais/privacidade`, `/mais/privacidade/atividade`, a tela de exclusão
  pendente, o `ReauthDialog` e o aviso de recuperação. O caminho inicial só ganha o canal entre
  abas, a escolha da família e o registro de módulos. Se ainda assim estourar, o `/inicio` vira
  `lazy()` e o `/lancar` continua estático; a Task 12 registra a medição.
- **D28. Primeiros passos** ganham "Convidar a família", feito quando a família tem 2 membros ativos
  ou mais, ou um convite pendente (`MeWorkspace.familyInvited`). Só aparece para Dono e Adulto.
- **D29. Fora do escopo:** gerir passkeys (`GET` e `DELETE /v1/me/passkeys`) fica para o Plano 5,
  junto com a política de privacidade e os consentimentos `TERMS` e `PRIVACY`.

## Review Focus

1. **Convite usado duas vezes, vencido ou revogado.** Duas abas aceitando o mesmo link: uma entra,
   a outra vê "Este convite não vale mais"; o token nunca aparece em log nem no histórico do
   navegador (Tasks 3 e 13).
2. **A família sem Dono.** Rebaixar ou remover o último Dono, inclusive a si mesmo, é recusado; a
   conta do único Dono não pode ser excluída (Tasks 2, 6 e 11).
3. **O Jovem tentando ver o que não é dele por id.** Conta, lançamento e painel de outra conta
   respondem como se não existissem (404), nunca 403 nem dado parcial (Tasks 5 e 6).
4. **Lançamento antigo de quem saiu.** Quem foi removido, teve a conta apagada ou perdeu o
   consentimento continua aparecendo pelo apelido nos lançamentos e na exportação, sem quebrar a
   lista (Tasks 2, 9, 11 e 14).
5. **Exportação aberta numa planilha em pt-BR.** Valores com vírgula, acentos certos e nenhuma
   célula vira fórmula, mesmo com descrição começando por `=` ou `-` (Task 10).

---

## Mapa de arquivos

API (`api/src/main/java/br/com/liberdadefamilia/`):

| Pacote | Novo | Muda |
|---|---|---|
| `shared` | `RateLimiter`, `RateLimitedException` | — |
| `shared/internal` | — | `ProblemHandler` (`Retry-After`) |
| `identity` | `RequiresRecentSignIn`, `RecentSignInCheck`, `DeviceLabel`, `SignedIn`, `SignInMethod`, `SessionSignIn`, `UserDirectory`, `UserSummary`, `UserProfile`, `UserDeletion` | `CurrentUser` |
| `identity/internal` | `RecentSignInInterceptor`, `ReauthenticationRequiredException`, `DefaultSessionSignIn`, `JdbcUserDirectory`, `JdbcUserDeletion`, `PendingDeletionFilter`, `ReauthAuthorizationRequestResolver` | `MinimalSecurityContextRepository`, `SecurityConfig`, `LoginRateLimitFilter` (saem `LoginRateLimiter` e `RateLimitedException`), `UserAccounts`, `CurrentUserArgumentResolver` |
| `workspace` | `Memberships`, `MemberSummary`, `MembershipStatus`, `MemberJoined`, `MemberRoleChanged`, `MemberRemoved`, `WorkspaceRenamed`, `InvitationCreated`, `InvitationRevoked`, `RecoveryCreated`, `RecoveryUsed`, `ModuleKey`, `ModuleToggled`, `RequiresModule`, `Modules`, `FamilyDeletion`, `WorkspaceDeletionRequested`, `WorkspaceDeletionCancelled` | `WorkspaceScoped`, `FamilyActivity` |
| `workspace/internal` | `MemberController`, `MemberService`, `MemberRepository`, `MemberView`, `UpdateMemberRequest`, `RenameWorkspaceRequest`, `InvitationController`, `PublicInvitationController`, `InvitationService`, `InvitationRepository`, `InvitationTokens`, `InvitationView`, `InvitationPreview`, `CreateInvitationRequest`, `AcceptInvitationRequest`, `RecoveryController`, `ModuleController`, `ModuleRepository`, `ModuleView`, `JdbcMemberships`, `JdbcModules`, `JdbcFamilyDeletion`, `FamilyPurgeJob`, `InvitationPurgeJob`, `WorkspaceRejectedException`, `WorkspaceForbiddenException`, `InvitationGoneException` | `WorkspaceController`, `WorkspaceService`, `WorkspaceRepository`, `WorkspaceGuardInterceptor`, `WorkspaceWebConfig`, `MeController`, `MeWorkspace` |
| `ledger/internal` | `AccountScope`, `LedgerJdbcExport`, `UpdateAccountOwnerRequest` | `AccountController`, `AccountRepository`, `AccountService`, `TransactionController`, `TransactionService`, `TransactionRepository`, `CreateTransactionRequest`, `UpdateTransactionRequest`, `DashboardService`, `LedgerSeeder`, `LedgerFamilyActivity` |
| `ledger` | `LedgerExport`, `ExportedAccount`, `ExportedCategory`, `ExportedTransaction` | — |
| `audit` (novo) | `AuditTrail`, `AuditEntry`, `ActivityItem`, `package-info` | — |
| `audit/internal` | `AuditRepository`, `AuditListeners`, `JdbcAuditTrail`, `ActivityController`, `AuditPurgeJob` | — |
| `privacy` (novo) | `package-info` | — |
| `privacy/internal` | `ConsentRepository`, `ConsentListener`, `ConsentController`, `ConsentView`, `ExportController`, `ExportService`, `CsvWriter`, `AccountDeletionController`, `AccountDeletionService`, `FamilyDeletionController`, `AccountPurgeJob`, `PrivacyRejectedException` | — |

Migrações novas em `api/src/main/resources/db/migration/`: `V6__members.sql`,
`V7__invitations.sql`, `V8__modules.sql`, `V9__audit.sql`, `V10__consents.sql`,
`V11__deletion.sql`.

Web (`web/src/`):

| Pasta | Novo | Muda |
|---|---|---|
| `app` | `PendingDeletionPage` | `routes.tsx`, `MorePage.tsx`, `RootRedirect.tsx`, `SessionLayout.tsx`, `WorkspaceLayout.tsx`, `AppShell.tsx` |
| `shared/auth` (nova) | `passkey.ts`, `google.ts` (movido de `features/auth`), `ReauthDialog.tsx`, `useReauth.ts`, `returnPath.ts` | — |
| `shared/modules` (nova) | `registry.ts`, `useModuleEnabled.ts`, `index.ts` | — |
| `shared/offline` | `sessionChannel.ts` | `session.ts` |
| `shared/api` | — | `hooks.ts` (`useCurrentWorkspace`, `useWorkspaces`, `selectWorkspace`), `endpoints.ts`, `mutations.ts`, `queryKeys.ts`, `types.ts`, `errors.ts`, `schema.ts` |
| `features/auth` | — | `SignInPage.tsx`, `PasskeyButton.tsx`, `SignOutButton.tsx` |
| `features/members` (nova) | `FamilyPage`, `MemberRow`, `MemberSheet`, `InvitePage`, `InvitationLink`, `AcceptInvitationPage`, `RecoveryLinkSheet`, `RenameFamilySheet`, `RecoveryNotice`, `WorkspaceSwitcher`, `invitationToken.ts`, `memberName.ts`, `messages.pt-BR.ts`, `register.ts` | — |
| `features/modules` (nova) | `ModulesPage`, `moduleCatalog.ts`, `messages.pt-BR.ts`, `register.ts` | — |
| `features/privacy` (nova) | `PrivacyPage`, `ActivityPage`, `ConsentList`, `ExportButtons`, `DeleteAccountSheet`, `DeleteFamilySheet`, `download.ts`, `messages.pt-BR.ts`, `register.ts` | — |
| `features/transactions` | `MemberPicker` | `ContextSheet`, `NewTransactionPage`, `TransactionForm`, `TransactionRow`, `TransactionDetailSheet`, `EditTransaction`, `TransactionsPage` |
| `features/accounts` | `OwnerPicker` | `AccountForm`, `AccountsPage` |
| `features/dashboard` | — | `HomePage`, `GettingStartedCard`, `gettingStarted.ts`, `DashboardPage` |
| `mocks` | — | `data.ts`, `handlers.ts` |

Mais: `web/e2e/family.spec.ts`, `web/e2e/privacy.spec.ts`, `web/e2e/core-without-modules.spec.ts`,
`web/e2e/a11y.spec.ts`, `web/e2e/support.ts`, `web/e2e/passkey.spec.ts`.

**Em toda task de API:** cada rota nova sob `/v1/workspaces/{ws}` tem `@WorkspaceScoped` com os
papéis da tabela da task. Toda mudança de rota ou DTO regenera `api/openapi.json`
(`UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'`) e
`web/src/shared/api/schema.ts` (`corepack pnpm --dir web gen:api`), e atualiza os handlers e o
`mockState` do MSW com as mesmas regras e códigos, no mesmo commit. Todo evento novo de domínio é
um `record` público no pacote do módulo que publica, com só ids, papéis e chaves (nunca nomes ou
valores).

## Tasks

### Task 1: Reautenticação, limitador de taxa e evento de entrada

**Files:**
- Create em `shared/`: `RateLimiter.java`, `RateLimitedException.java`
- Create em `identity/`: `RequiresRecentSignIn.java`, `RecentSignInCheck.java`, `DeviceLabel.java`,
  `SignedIn.java`, `SignInMethod.java`, `SessionSignIn.java`
- Create em `identity/internal/`: `RecentSignInInterceptor.java`,
  `ReauthenticationRequiredException.java`, `DefaultSessionSignIn.java`,
  `ReauthAuthorizationRequestResolver.java`
- Modify: `identity/internal/MinimalSecurityContextRepository.java`, `SecurityConfig.java` (CORS
  com `PUT`, o interceptor, o resolver, o bean `loginRateLimiter`), `LoginRateLimitFilter.java`,
  `shared/internal/ProblemHandler.java`
- Delete: `identity/internal/LoginRateLimiter.java`, `identity/internal/RateLimitedException.java`
  e o teste `identity/internal/LoginRateLimiterTest.java` (os casos vão para o `RateLimiterTest`)
- Modify: `api/src/test/java/br/com/liberdadefamilia/support/TestUsers.java`
- Test (novos): `shared/RateLimiterTest.java`, `identity/DeviceLabelTest.java`,
  `identity/RecentSignInIT.java`, `identity/internal/MinimalSecurityContextRepositoryTest.java`
  (casos novos), `identity/internal/DefaultSessionSignInTest.java`
- Test: `identity/internal/LoginRateLimitIT.java`

**Interfaces:**
- Consumes: `MinimalSecurityContextRepository`, `SessionUserAuthentication`, `SessionUser`, o `Clock`
  de `ClockConfig`, `DomainException`.
- Produces:
  - `public final class RateLimiter`: construtor `(Clock clock, int limit, Duration window)`;
    `boolean tryAcquire(String key)`; `long retryAfterSeconds(String key)` (≥ 1 quando bloqueado).
  - `public final class RateLimitedException extends DomainException`: construtor
    `(long retryAfterSeconds)`; 429 `rate.limited`, `detail` "Muitas tentativas. Tente de novo em
    instantes."; `retryAfterSeconds()`. O `ProblemHandler` põe `Retry-After`.
  - `public record DeviceLabel(String browser, String os)` (em `identity`) com
    `static DeviceLabel parse(@Nullable String userAgent)`: navegador em `Chrome`, `Edge`,
    `Firefox`, `Safari`, `Samsung Internet` ou `other`; sistema em `Android`, `iOS`, `Windows`,
    `macOS`, `Linux` ou `other`.
  - `@Target(METHOD) @Retention(RUNTIME) public @interface RequiresRecentSignIn {}`.
  - `public interface RecentSignInCheck { void require(HttpServletRequest request); }`: a mesma
    checagem da anotação, para rotas em que só parte do pedido é sensível (Task 2). Quem implementa
    é o `RecentSignInInterceptor`.
  - `public enum SignInMethod { GOOGLE, PASSKEY, INVITATION, RECOVERY }`.
  - `public record SignedIn(UUID userId, SignInMethod method, DeviceLabel device)`, publicado a
    cada entrada.
  - `public interface SessionSignIn { void signIn(HttpServletRequest request, HttpServletResponse
    response, UUID userId, SignInMethod method); }`: troca o id da sessão, grava o contexto mínimo
    e `authenticatedAt` e publica `SignedIn`.
  - `public static final String AUTHENTICATED_AT = "liberdade.authenticatedAt"` e
    `public static final Duration RECENT = Duration.ofMinutes(10)` em `RecentSignInInterceptor`.
  - `ReauthenticationRequiredException`: 403 `auth.reauthentication-required`, `detail` "Confirme
    que é você para continuar."
  - `TestUsers.signedIn(...)` marca o pedido com um atraso de 0 minutos num atributo do pedido; novo
    `TestUsers.signedInMinutesAgo(UUID userId, int minutes)` marca com `minutes`, para o caso velho.
    O `TestUsers.FamilyNameHintFilter` (bean de teste) passa a receber o `Clock` da aplicação e grava
    na sessão `AUTHENTICATED_AT` = `clock.instant()` menos o atraso, porque um post-processor não
    escreve na sessão JDBC e os ITs com `Clock` fixo precisam do instante do mesmo relógio.

**Como fica:**
- `MinimalSecurityContextRepository.saveContext`: na conversão de um login novo, grava
  `AUTHENTICATED_AT` (o `Instant` do `Clock`) e publica `SignedIn` com `GOOGLE` ou `PASSKEY` e o
  `DeviceLabel` do `User-Agent`. Contexto já mínimo não mexe no instante.
- `RecentSignInInterceptor` (registrado em `/v1/**`) olha o `HandlerMethod`: sem a anotação, passa;
  com ela, lê o atributo da sessão e lança a exceção se faltar ou tiver mais de `RECENT`.
- `ReauthAuthorizationRequestResolver` embrulha o resolver padrão do OAuth2 e acrescenta
  `prompt=select_account` quando a URL de início tem `?reauth=true`.
- O `LoginRateLimiter` sai: o `SecurityConfig` declara o bean `loginRateLimiter`, um
  `RateLimiter(clock, 20, Duration.ofMinutes(1))`, que o `LoginRateLimitFilter` recebe por
  `@Qualifier`. O filtro chama `tryAcquire`; bloqueado, o redirect da navegação continua igual, e o
  XHR entrega ao `HandlerExceptionResolver`, como hoje, um
  `shared.RateLimitedException(retryAfterSeconds(ip))`, cujo `Retry-After` o `ProblemHandler` põe
  (o filtro não põe mais o cabeçalho). O `identity/internal/RateLimitedException` sai.
- O `LoginRateLimitIT.StoppedClock` passa a sobrescrever o bean `loginRateLimiter` com um
  `RateLimiter` de relógio parado.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `RateLimiterTest.allowsUpToLimit` | limite 3 em 1 min: 3 `true`, o 4º `false` |
| `RateLimiterTest.windowResets` | depois de 1 min (`Clock` mutável), volta a aceitar |
| `RateLimiterTest.keysAreIndependent` | estourar `a` não afeta `b` |
| `RateLimiterTest.retryAfterIsRemainingWindow` | bloqueado aos 20 s de uma janela de 60 s → 40 |
| `RateLimiterTest.retryAfterIsAtLeastOne` | bloqueado aos 59,5 s de uma janela de 60 s → 1 |
| `RateLimiterTest.prunesExpiredWindows` | 10.001 chaves vencidas e uma chamada nova → o mapa fica com 1 |
| `DeviceLabelTest` (parametrizado) | UA do Chrome no Android → (`Chrome`, `Android`); Safari no iPhone → (`Safari`, `iOS`); Edge no Windows → (`Edge`, `Windows`); nulo → (`other`, `other`) |
| `RecentSignInIT.freshSignInPasses` | rota de teste anotada (controller de teste no pacote de teste), login agora → 200 |
| `RecentSignInIT.staleSignInIs403` | `signedInMinutesAgo(11)` → 403 `auth.reauthentication-required` |
| `RecentSignInIT.unannotatedIgnoresAge` | `signedInMinutesAgo(60)` em `GET /v1/me` → 200 |
| `RecentSignInIT.corsAllowsPut` | preflight `OPTIONS` com `Access-Control-Request-Method: PUT` da origem do web → permitido |
| `RecentSignInIT.reauthAddsPrompt` | `GET /oauth2/authorization/google?reauth=true` → o redirect tem `prompt=select_account`; sem `reauth`, não tem |
| `LoginRateLimitIT.xhrOverLimitIs429` (ajustado) | 21ª tentativa → 429 `rate.limited` com `Retry-After` 45, agora posto pelo `ProblemHandler`, e o `detail` do `shared.RateLimitedException` |
| `MinimalSecurityContextRepositoryTest.freshSignInRecordsInstant` | conversão de OIDC → a sessão tem `liberdade.authenticatedAt` igual ao `Clock` e sai um `SignedIn(GOOGLE)` |
| `MinimalSecurityContextRepositoryTest.minimalContextKeepsInstant` | contexto já mínimo → o instante não muda e nenhum evento sai |
| `DefaultSessionSignInTest` | `signIn` troca o id da sessão, grava o contexto mínimo e o instante e publica `SignedIn` com o método dado |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*RateLimiterTest' --tests '*DeviceLabelTest' --tests '*RecentSignInIT' --tests 'br.com.liberdadefamilia.identity.internal.*'`
Expected: FAIL na compilação (`RateLimiter` não existe).

- [ ] **Step 3: Implementar**

Siga "Como fica". O `SessionSignIn` usa `request.changeSessionId()` (ou cria a sessão se não houver)
e o mesmo `SecurityContextRepository` do `SecurityConfig`.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, com cobertura do `shared` ≥ 90%.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(identity): require a recent sign-in for sensitive actions and add a shared rate limiter"
```

---

### Task 2: Membros, papéis e renomear a família

**Files:**
- Create: `api/src/main/resources/db/migration/V6__members.sql`
- Create em `workspace/`: `Memberships.java`, `MemberSummary.java`, `MembershipStatus.java`,
  `MemberJoined.java`, `MemberRoleChanged.java`, `MemberRemoved.java`, `WorkspaceRenamed.java`
- Create em `identity/`: `UserDirectory.java`, `UserSummary.java`; em `identity/internal/`:
  `JdbcUserDirectory.java`
- Create em `workspace/internal/`: `MemberController.java`, `MemberService.java`,
  `MemberRepository.java`, `MemberView.java`, `UpdateMemberRequest.java`,
  `RenameWorkspaceRequest.java`, `JdbcMemberships.java`, `WorkspaceRejectedException.java`,
  `WorkspaceForbiddenException.java`
- Modify: `workspace/FamilyActivity.java`, `workspace/internal/WorkspaceController.java`,
  `WorkspaceService.java`, `WorkspaceRepository.java`, `MeController.java`, `MeWorkspace.java`
- Modify: `ledger/internal/LedgerFamilyActivity.java`, `ledger/internal/AccountRepository.java`
  (sai `isActiveMembership`), `AccountService.java` (usa `Memberships`)
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Modify: `api/src/test/java/br/com/liberdadefamilia/support/TestWorkspaces.java`
- Test (novos): `workspace/MembersIT.java`, `workspace/MultipleFamiliesIT.java`,
  `workspace/MeQueryCountIT.java`
- Test: `workspace/WorkspaceIT.java`, `workspace/WorkspaceGuardCoverageIT.java`,
  `ledger/AccountsIT.java`, `workspace/internal/WorkspaceServiceTest.java`

**Interfaces:**
- Consumes: `RequiresRecentSignIn` e `RecentSignInCheck.require(HttpServletRequest)` (Task 1),
  `WorkspaceScoped`, `WorkspaceMember`, `Role`.
- Produces:
  - `public enum MembershipStatus { ACTIVE, REMOVED }`.
  - `public record MemberSummary(UUID workspaceId, UUID membershipId, @Nullable UUID userId, Role
    role, MembershipStatus status, @Nullable String nickname)`.
  - `public interface Memberships`:
    - `boolean isActive(UUID workspaceId, UUID membershipId)`;
    - `Optional<MemberSummary> find(UUID workspaceId, UUID membershipId)`;
    - `List<MemberSummary> all(UUID workspaceId)` (ativos e removidos);
    - `List<MemberSummary> membershipsOf(UUID userId)` (todos os vínculos da pessoa, ativos e
      removidos);
    - `Map<UUID, String> workspaceNames(Collection<UUID> workspaceIds)` (uma consulta só);
    - `boolean isSoleActiveOwnerOfLiveFamily(UUID userId)` (alguma família sem exclusão pendente em
      que a pessoa é a única Dona ativa);
    - `void remove(UUID workspaceId, UUID membershipId, @Nullable UUID actorUserId,
      MemberRemoved.Reason reason)`;
    - `void detachUser(UUID userId)` (passa todos os vínculos da pessoa para `REMOVED`, com o
      motivo `ACCOUNT_DELETED`).
  - `public record UserSummary(UUID id, String displayName, boolean hasGoogle)` e
    `public interface UserDirectory { Map<UUID, UserSummary> findAll(Collection<UUID> userIds); }`
    (uma consulta só).
  - Eventos: `MemberRoleChanged(UUID workspaceId, UUID membershipId, Role from, Role to, UUID
    actorUserId)`, `MemberRemoved(UUID workspaceId, UUID membershipId, @Nullable UUID actorUserId,
    Reason reason)` com `enum Reason { BY_OWNER, CONSENT_REVOKED, ACCOUNT_DELETED }`,
    `WorkspaceRenamed(UUID workspaceId, UUID actorUserId)`, e
    `MemberJoined(UUID workspaceId, UUID membershipId, UUID userId, Role role, String nickname,
    @Nullable UUID parentalConsentBy)` (o record nasce aqui; quem publica é a Task 3).
  - `FamilyActivity`: sai `hasTransactions(UUID)`, entra
    `Set<UUID> withTransactions(Collection<UUID> workspaceIds)`.
  - `MemberView(UUID id, @Nullable String displayName, @Nullable String nickname, Role role,
    MembershipStatus status, boolean you, boolean passkeyOnly, Instant joinedAt)`.
    `displayName` é nulo depois que a conta da pessoa é apagada; `passkeyOnly` é `!hasGoogle`.
  - `UpdateMemberRequest(@Nullable Role role, @Nullable @Size(min = 1, max = 40)
    @Pattern(WITHOUT_NUL) String nickname, @Nullable Boolean parentalConsent)`, `@PartialRequest`.
  - `RenameWorkspaceRequest(@NotBlank @Size(max = 60) @Pattern(WITHOUT_NUL) String name)`.
  - `MeWorkspace` ganha `boolean familyInvited` (2 membros ativos ou mais; o convite pendente entra
    na Task 3) e troca `hasTransactions` para vir do `withTransactions` em lote.
  - `TestWorkspaces.addMember(UUID workspaceId, UUID userId, Role role, String nickname)` →
    `UUID membershipId`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /v1/workspaces/{ws}/members` | `listMembers` | todos | 200 `MemberView[]`, ativos primeiro, depois por apelido | — |
| `PATCH /v1/workspaces/{ws}/members/{id}` | `updateMember` | OWNER | 200 `MemberView` | 404 `member.not-found`; 422 `member.last-owner`; 422 `member.parental-consent-required`; 422 `member.removed` (vínculo removido); 403 `auth.reauthentication-required` (só quando muda o papel) |
| `DELETE /v1/workspaces/{ws}/members/{id}` | `removeMember` | OWNER | 204 (repetir: 204) | 404 `member.not-found`; 422 `member.last-owner`; 403 `auth.reauthentication-required` |
| `PATCH /v1/workspaces/{ws}` | `renameWorkspace` | OWNER | 200 `MeWorkspace` | 400 `request.invalid` |

**Como fica:**
- `V6__members.sql`: `DROP INDEX memberships_one_active_family;` e um índice
  `memberships (user_id) WHERE status = 'ACTIVE'` não único, para as consultas por pessoa.
- Mudar o papel exige reautenticação, mas mudar só o apelido não. Como as duas coisas são a mesma
  rota, o `MemberController` não usa a anotação: chama `RecentSignInCheck.require(request)` (Task 1)
  quando `role` vem preenchido e é diferente do atual.
- A checagem do último Dono roda dentro da transação, com `SELECT … FOR UPDATE` nos vínculos Donos
  ativos da família, para duas remoções simultâneas não deixarem a família sem Dono.
- Mudar para Jovem com `parentalConsent: true` grava `parental_consent_at = now()` e
  `parental_consent_by` = quem muda, e publica `MemberRoleChanged`. O registro em `consents` desse
  caminho é da Task 9, que ouve `MemberRoleChanged` com `to = YOUTH`.
- `WorkspaceService.create`: o 409 passa a olhar só vínculos `OWNER` ativos em famílias ativas ou
  com exclusão pendente. Sem o índice único, a corrida de duas criações da mesma pessoa é resolvida
  por uma trava: antes da checagem de Dono, o `WorkspaceRepository` faz
  `select pg_advisory_xact_lock(hashtextextended(:userId::text, 0))`, que dura até o fim da
  transação (sem `FOR UPDATE` na tabela `users`, que é do `identity`). O `catch` de
  `DuplicateKeyException` sai.
- `MeController` monta a lista com uma consulta para os vínculos e uma chamada a
  `withTransactions` com todos os ids.
- `AccountService` troca `accounts.isActiveMembership(...)` por `memberships.isActive(...)`.

- [ ] **Step 1: Escrever os testes (falhando)**

`MembersIT`:

| Teste | Esperado |
|---|---|
| `listsMembersForEveryRole` | Dona, Adulto e Leitor (o Jovem ganha a leitura na Task 6) → cada um recebe a lista com os 3, `you` verdadeiro só no próprio, sem nenhum campo de e-mail no JSON |
| `removedMemberStaysListed` | remover o Adulto → a lista traz o Adulto com `status` `REMOVED` |
| `ownerChangesRole` | Dona muda o Adulto para Leitor → 200 com `role` `VIEWER` |
| `roleChangeNeedsRecentSignIn` | Dona com login de 11 min muda o papel → 403 `auth.reauthentication-required` |
| `nicknameChangeSkipsReauth` | Dona com login de 11 min muda só o apelido → 200 |
| `lastOwnerCannotStepDown` | única Dona se rebaixa a Adulta → 422 `member.last-owner` |
| `secondOwnerAllowsStepDown` | promover o Adulto a Dono e depois se rebaixar → 200 nas duas |
| `lastOwnerCannotBeRemoved` | Dona remove a si mesma sendo a única → 422 `member.last-owner` |
| `concurrentRemovalsKeepAnOwner` | duas Donas removem uma à outra ao mesmo tempo (duas threads) → uma recebe 204 e a outra 422 |
| `youthNeedsParentalConsent` | mudar o Adulto para Jovem sem `parentalConsent` → 422 `member.parental-consent-required`; com `true` → 200 |
| `adultCannotManageMembers` | Adulto chama `PATCH` e `DELETE` → 403 `workspace.forbidden` |
| `otherFamilyMemberIsNotFound` | id de um vínculo de outra família → 404 `member.not-found` |
| `ownerRenamesFamily` | `PATCH /v1/workspaces/{ws}` com "  Família Nova " → 200 com "Família Nova", e o `/v1/me` mostra o nome novo |
| `renameRejectsBlank` | nome "   " → 400 `request.invalid` |

`MultipleFamiliesIT`:

| Teste | Esperado |
|---|---|
| `memberOfOneCanCreateOwnFamily` | pessoa que é Adulta numa família cria a sua → 201, e o `/v1/me` lista as duas |
| `ownerCannotCreateSecond` | pessoa que já é Dona → 409 `workspace.already-exists` |

O `WorkspaceIT.concurrentCreateMakesOneFamily` continua como está e passa a ser garantido pela
trava. No `WorkspaceServiceTest`, o `membershipRefusedByTheIndexIsAlreadyExists` dá lugar a
`locksBeforeOwnerCheck`: a trava da pessoa é pedida antes da checagem de Dono (`InOrder` do
Mockito), e uma Dona já existente dá `WorkspaceAlreadyExistsException`.

`MeQueryCountIT.meUsesConstantQueries`: com 1 e com 3 famílias, o número de comandos SQL de
`GET /v1/me` é o mesmo (contado por um `DataSource` de teste que embrulha o real, ou pelo
`pg_stat_statements` se já existir no projeto; use o mais simples dos dois).

Os testes que existem de `AccountsIT` sobre titular inválido continuam passando com
`Memberships`.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*MembersIT' --tests '*MultipleFamiliesIT' --tests '*MeQueryCountIT' --tests '*AccountsIT'`
Expected: FAIL (rotas e tipos não existem).

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica". Contrato, MSW e `mockState` no mesmo commit.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web typecheck; corepack pnpm --filter web test`
Expected: tudo verde, com `ApplicationModulesTest` verde (`ledger` não lê mais `memberships`).

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(workspace): members, roles and family rename, with a membership API for the ledger"
```

---

### Task 3: Convites

**Files:**
- Create: `api/src/main/resources/db/migration/V7__invitations.sql`
- Create em `workspace/`: `InvitationCreated.java`, `InvitationRevoked.java`
- Create em `workspace/internal/`: `InvitationController.java`, `PublicInvitationController.java`,
  `InvitationService.java`, `InvitationRepository.java`, `InvitationTokens.java`,
  `InvitationView.java`, `InvitationPreview.java`, `CreateInvitationRequest.java`,
  `AcceptInvitationRequest.java`, `InvitationGoneException.java`
- Modify: `identity/UserDirectory.java` (`createPasskeyOnlyUser`), `identity/internal/JdbcUserDirectory.java`,
  `identity/internal/SecurityConfig.java` (as duas rotas públicas)
- Modify: `workspace/internal/WorkspaceRepository.java` (`familyInvited` com convite pendente)
- Modify: `ledger/internal/LedgerSeeder.java` (carteira do Jovem)
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test (novos): `workspace/InvitationsIT.java`, `workspace/internal/InvitationTokensTest.java`,
  `ledger/YouthWalletIT.java`

**Interfaces:**
- Consumes: `SessionSignIn`, `SignInMethod.INVITATION`, `RateLimiter`, `RateLimitedException` (Task 1);
  `MemberJoined`, `Memberships` (Task 2).
- Produces:
  - `UserDirectory.createPasskeyOnlyUser(String displayName)` → `UUID` (sem e-mail, sem identidade
    Google).
  - `InvitationTokens`: `static String newToken()` (32 bytes, base64url sem padding) e
    `static byte[] hash(String token)` (SHA-256 dos bytes UTF-8 do token).
  - `CreateInvitationRequest(@NotNull Role role, @NotBlank @Size(max = 40) @Pattern(WITHOUT_NUL)
    String nickname, @Nullable Boolean parentalConsent)`.
  - `InvitationView(UUID id, Role role, @Nullable String nickname, InvitationKind kind, Instant
    expiresAt, @Nullable String createdByName, @Nullable String token)`: `token` só vem na resposta
    da criação, nunca na listagem. `nickname` é nulo na recuperação (Task 4). `createdByName` é o
    apelido de quem criou nesta família, nulo depois que a conta dessa pessoa foi apagada.
    `enum InvitationKind { INVITE, RECOVERY }`.
  - `InvitationPreview(String workspaceName, @Nullable String invitedBy, Role role, @Nullable String
    nickname, InvitationKind kind)`.
  - `AcceptInvitationRequest(@NotBlank @Size(min = 43, max = 43) String token, @Nullable @Valid
    NewUser newUser)` com `record NewUser(@NotBlank @Size(max = 200) @Pattern(WITHOUT_NUL) String
    displayName)`. A resposta é 200 `AcceptedInvitation(UUID workspaceId, UUID membershipId,
    boolean mustRegisterPasskey)`.
  - `InvitationCreated(UUID workspaceId, UUID invitationId, InvitationKind kind, Role role, UUID
    actorUserId)` e `InvitationRevoked(UUID workspaceId, UUID invitationId, UUID actorUserId)`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `POST /v1/workspaces/{ws}/invitations` | `createInvitation` | OWNER, ADULT | 201 `InvitationView` com `token` | 403 `invitation.role-forbidden`; 422 `invitation.parental-consent-required`; 429 `rate.limited` |
| `GET /v1/workspaces/{ws}/invitations` | `listInvitations` | OWNER, ADULT | 200 `InvitationView[]` pendentes (não usados, não revogados, não vencidos), sem `token` | — |
| `DELETE /v1/workspaces/{ws}/invitations/{id}` | `revokeInvitation` | OWNER, ADULT | 204 (repetir: 204) | 404 `invitation.not-found` |
| `POST /v1/invitations/preview` | `previewInvitation` | anônimo ou logado | 200 `InvitationPreview` | 404 `invitation.not-found`; 410 `invitation.gone`; 429 `rate.limited` |
| `POST /v1/invitations/accept` | `acceptInvitation` | anônimo ou logado | 200 `AcceptedInvitation` | 404 `invitation.not-found`; 410 `invitation.gone`; 409 `invitation.already-member`; 422 `invitation.new-user-required` (anônimo sem `newUser`); 429 `rate.limited` |

**Como fica:**
- `V7__invitations.sql`: a tabela `invitations` como no modelo de dados, mais `nickname text NULL
  CHECK (char_length(nickname) BETWEEN 1 AND 40)`, `CHECK ((kind = 'INVITE') = (nickname IS NOT
  NULL))`, `created_by uuid NULL` → `users ON DELETE SET NULL`, `parental_consent_by uuid NULL` →
  `users ON DELETE SET NULL`, `CHECK (kind = 'RECOVERY') = (target_membership_id IS NOT NULL)`, FK
  composta `(workspace_id, target_membership_id)` → `memberships (workspace_id, id) ON DELETE
  CASCADE`,
  `token_hash bytea NOT NULL UNIQUE`, índice `(workspace_id, expires_at)`; e
  `ALTER TABLE memberships ADD COLUMN recovered_at timestamptz` (usada na Task 4).
- As rotas `/v1/invitations/**` não estão sob `/v1/workspaces/{ws}`, então não levam
  `@WorkspaceScoped`; o `SecurityConfig` as libera para anônimos, e o CSRF continua valendo (o web
  já busca o token de `/v1/csrf` antes de qualquer POST).
- Os dois POSTs públicos passam pelo `RateLimiter` de 10 por minuto por IP (o mesmo IP que o
  `LoginRateLimitFilter` usa) antes de qualquer consulta. Criar convite usa outro, de 20 por hora
  por pessoa (compartilhado com a recuperação da Task 4).
- Aceitar, numa transação só:
  1. busca o convite pelo hash com `FOR UPDATE`; não achou → 404; usado, revogado ou vencido → 410;
  2. sem sessão e sem `newUser` → 422; sem sessão e com `newUser` → `createPasskeyOnlyUser`;
  3. pessoa já ativa na família → 409, sem gravar `used_at`;
  4. insere o vínculo (ou reativa o `REMOVED` da mesma pessoa) com o papel e o apelido do convite e,
     no Jovem, `parental_consent_at` e `parental_consent_by` do convite;
  5. grava `used_at`, publica `MemberJoined`;
  6. sem sessão: `SessionSignIn.signIn(..., INVITATION)` e `mustRegisterPasskey = true`.
- `LedgerSeeder` ouve `MemberJoined` com `role = YOUTH` e cria a carteira (D11) na mesma transação.
- Logs: o controller e o serviço nunca logam o corpo; o filtro de log de requisição, se existir,
  não loga corpo de `/v1/invitations/**`.

- [ ] **Step 1: Escrever os testes (falhando)**

`InvitationTokensTest`: `newToken` tem 43 caracteres do alfabeto base64url; 1000 tokens são
distintos; `hash` tem 32 bytes e é determinístico.

`InvitationsIT`:

| Teste | Esperado |
|---|---|
| `ownerInvitesAdult` | 201 com `token` de 43 caracteres e `expiresAt` = agora + 7 dias; o banco guarda só o hash (a coluna não contém o token) |
| `adultCannotInviteOwner` | Adulto convida Dono → 403 `invitation.role-forbidden` |
| `youthInviteNeedsConsent` | Jovem sem `parentalConsent` → 422 `invitation.parental-consent-required` |
| `viewerAndYouthCannotInvite` | Leitor e Jovem → 403 `workspace.forbidden` |
| `listHidesToken` | `GET` lista o convite sem `token` e com `createdByName` = o apelido de quem convidou |
| `previewShowsFamily` | anônimo com o token → 200 com o nome da família, o apelido de quem convidou, o papel e o apelido |
| `signedInAccepts` | outra pessoa logada aceita → 200; `GET …/members` tem o apelido do convite; segundo aceite → 410 `invitation.gone` |
| `memberCannotAcceptAgain` | quem já é membro ativo aceita → 409 `invitation.already-member`, e o convite continua valendo |
| `removedMemberIsReactivated` | removido aceita convite novo → o mesmo id de vínculo volta `ACTIVE` com o papel novo |
| `anonymousNeedsNewUser` | anônimo sem `newUser` → 422 `invitation.new-user-required` |
| `anonymousCreatesPasskeyOnlyUser` | anônimo com `newUser` → 200 com `mustRegisterPasskey`; a sessão mudou de id e está autenticada; `GET /v1/me` mostra a família; o usuário tem `email` nulo |
| `expiredIsGone` | `Clock` 7 dias + 1 s depois → preview e accept dão 410 `invitation.gone` |
| `revokedIsGone` | revogar e aceitar → 410 |
| `unknownTokenIsNotFound` | token inventado → 404 `invitation.not-found` |
| `concurrentAcceptsUseOnce` | duas pessoas aceitam ao mesmo tempo (duas threads) → uma 200 e a outra 410 |
| `youthGetsConsentCopied` | Jovem aceita → o vínculo tem `parental_consent_at` e `parental_consent_by` de quem convidou |
| `previewIsRateLimited` | 11 previews do mesmo IP em 1 min → o 11º dá 429 com `Retry-After` |
| `createIsRateLimited` | 21 convites da mesma pessoa em 1 h → 429 |
| `familyInvitedFlag` | criar um convite → `GET /v1/me` com `familyInvited` verdadeiro |

`YouthWalletIT`:

| Teste | Esperado |
|---|---|
| `youthGetsWallet` | Jovem "Leo" aceita → existe a conta "Carteira de Leo", tipo `CASH`, saldo R$ 0,00, titular = o vínculo dele |
| `walletNameAvoidsClash` | já existe "Carteira de Leo" → a nova se chama "Carteira de Leo 2" |
| `adultGetsNoWallet` | Adulto aceita → nenhuma conta nova |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*InvitationsIT' --tests '*InvitationTokensTest' --tests '*YouthWalletIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web typecheck; corepack pnpm --filter web test`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(workspace): single-use invitations with parental consent and the youth wallet"
```

---

### Task 4: Recuperação de acesso

**Files:**
- Create em `workspace/`: `RecoveryCreated.java`, `RecoveryUsed.java`
- Create: `workspace/internal/RecoveryController.java`
- Modify: `workspace/internal/InvitationService.java`, `InvitationRepository.java`,
  `MeController.java`, `MeWorkspace.java` (`recoveredAt`)
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/handlers.ts`
- Test (novo): `workspace/RecoveryIT.java`

**Interfaces:**
- Consumes: as tabelas, `InvitationView` e o aceite da Task 3; `@RequiresRecentSignIn`,
  `SessionSignIn`, `SignInMethod.RECOVERY` (Task 1); `UserDirectory.findAll` (Task 2).
- Produces:
  - `RecoveryCreated(UUID workspaceId, UUID invitationId, UUID targetMembershipId, UUID
    actorUserId)` e `RecoveryUsed(UUID workspaceId, UUID targetMembershipId, UUID userId)`.
  - `MeWorkspace.recoveredAt` (`@Nullable Instant`).
  - O aceite (`POST /v1/invitations/accept`) de um convite `RECOVERY` responde
    `AcceptedInvitation` com `mustRegisterPasskey = true`; o preview responde `kind = RECOVERY`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `POST /v1/workspaces/{ws}/members/{id}/recovery` | `createRecoveryLink` | OWNER, ADULT (com reautenticação) | 201 `InvitationView` com `token`, `kind` `RECOVERY`, `expiresAt` = agora + 24 h | 404 `member.not-found`; 403 `invitation.role-forbidden` (Adulto para Dono); 422 `member.recovery-unavailable`; 422 `member.removed`; 429 `rate.limited`; 403 `auth.reauthentication-required` |

**Como fica:**
- A linha de `RECOVERY` em `invitations` guarda `nickname` nulo e `role` = o papel do vínculo alvo;
  o preview de `RECOVERY` lê o apelido do vínculo alvo.
- Criar um link novo para o mesmo vínculo revoga o anterior ainda pendente.
- No aceite de `RECOVERY`: ignora a sessão atual (se houver, ela é trocada), não aceita `newUser`,
  grava `used_at` e `memberships.recovered_at = now()`, chama
  `SessionSignIn.signIn(..., target.userId, RECOVERY)` e publica `RecoveryUsed`.
- Pessoa com Google → 422 `member.recovery-unavailable` na criação.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ownerCreatesRecoveryForPasskeyOnlyMember` | Jovem sem Google → 201, `kind` `RECOVERY`, `role` `YOUTH`, `nickname` nulo, expira em 24 h; o preview traz o apelido do vínculo |
| `recoveryNeedsRecentSignIn` | login de 11 min → 403 `auth.reauthentication-required` |
| `adultCannotRecoverOwner` | Adulto para a Dona → 403 `invitation.role-forbidden` |
| `googleMemberUsesGoogle` | membro com Google → 422 `member.recovery-unavailable` |
| `recoverySignsInAsTarget` | aceitar anônimo → 200 com `mustRegisterPasskey`; `GET /v1/me` é a pessoa do vínculo; `recoveredAt` preenchido |
| `recoveryReplacesOtherSession` | aceitar logado como outra pessoa → a sessão passa a ser a do vínculo, com outro id |
| `newLinkRevokesOld` | dois links seguidos → o primeiro dá 410 |
| `recoveryExpiresIn24h` | `Clock` 24 h + 1 s → 410 |
| `recoveryLinkUsedOnce` | segundo aceite → 410 |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*RecoveryIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica".

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
git commit -m "feat(workspace): 24-hour recovery links for passkey-only members"
```

---

### Task 5: "Quem", titular da conta e lançamento por id

**Files:**
- Modify em `ledger/internal/`: `CreateTransactionRequest.java`, `UpdateTransactionRequest.java`,
  `TransactionController.java`, `TransactionService.java`, `TransactionRepository.java`,
  `AccountController.java`, `AccountService.java`, `AccountRepository.java`
- Create: `ledger/internal/UpdateAccountOwnerRequest.java`
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test: `ledger/TransactionsIT.java` (casos novos), `ledger/AccountsIT.java` (casos novos)

**Interfaces:**
- Consumes: `Memberships.find` e `isActive` (Task 2).
- Produces:
  - `CreateTransactionRequest` ganha `@Nullable UUID memberId`; `UpdateTransactionRequest` também.
  - `UpdateAccountOwnerRequest(@Nullable UUID membershipId)`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /v1/workspaces/{ws}/transactions/{id}` | `getTransaction` | OWNER, ADULT, VIEWER (YOUTH na Task 6) | 200 `TransactionView` | 404 `transaction.not-found`; 409 `transaction.deleted` |
| `PUT /v1/workspaces/{ws}/accounts/{id}/owner` | `setAccountOwner` | OWNER, ADULT | 200 `AccountView` | 404 `account.not-found`; 422 `account.owner-not-found` |
| `POST …/transactions` e `PATCH …/transactions/{id}` | (existentes) | (os de hoje) | (os de hoje) | + 422 `transaction.member-not-found` |

**Como fica:**
- Lançar: `memberId` nulo → o vínculo de quem lança. Preenchido → precisa ser ativo.
- Editar: `memberId` nulo → mantém. Preenchido e igual ao atual → vale mesmo se o vínculo foi
  removido. Preenchido e diferente → precisa ser ativo.
- A idempotência do `POST` (reenvio com o mesmo id e o mesmo conteúdo) compara o `memberId`
  resolvido, então um item antigo da fila, sem o campo, reenviado depois continua batendo.
- `GET` por id usa a mesma projeção da lista.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `TransactionsIT.memberDefaultsToCaller` | `POST` sem `memberId` → `memberId` = o vínculo de quem lança |
| `TransactionsIT.adultRecordsForAnotherMember` | Adulto lança com o `memberId` da Dona → 201 com esse `memberId`, `createdBy` continua o Adulto |
| `TransactionsIT.inactiveMemberRejected` | `memberId` de vínculo removido ou de outra família → 422 `transaction.member-not-found` |
| `TransactionsIT.editKeepsRemovedMember` | lançamento de quem foi removido, editado só no valor (sem `memberId`) → 200, `memberId` igual |
| `TransactionsIT.retryWithoutMemberIdIsIdempotent` | o mesmo `POST` sem `memberId` duas vezes → 201 e 200, um registro |
| `TransactionsIT.getById` | 200 com os mesmos campos da lista |
| `TransactionsIT.getDeletedIs409` | excluído → 409 `transaction.deleted` |
| `TransactionsIT.getOtherFamilyIs404` | id de outra família → 404 `transaction.not-found` |
| `AccountsIT.setOwner` | `PUT …/owner` com o vínculo do Adulto → 200 com `ownerMembershipId`; com `null` → 200 sem titular |
| `AccountsIT.setOwnerRejectsInactive` | vínculo removido → 422 `account.owner-not-found` |
| `AccountsIT.viewerCannotSetOwner` | Leitor → 403 `workspace.forbidden` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*TransactionsIT' --tests '*AccountsIT'`
Expected: FAIL nos casos novos.

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web typecheck; corepack pnpm --filter web test`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(ledger): choose who spent, set the account owner and read one transaction by id"
```

---

### Task 6: O Jovem e a matriz de papéis

**Files:**
- Create: `ledger/internal/AccountScope.java`
- Modify em `ledger/internal/`: `AccountController.java`, `AccountRepository.java`,
  `CategoryController.java`, `TransactionController.java`, `TransactionService.java`,
  `TransactionRepository.java`, `DashboardController.java`, `DashboardService.java`
- Modify: `workspace/internal/WorkspaceGuardInterceptor.java` (sai o "YOUTH sempre 403"),
  `workspace/internal/MemberController.java` (YOUTH no `GET`), `workspace/Role.java` (o javadoc
  passa a dizer que o Jovem vê e lança só nas contas de que é titular, D13)
- Test (novos): `ledger/YouthScopeIT.java`, `RoleMatrixIT.java` (em
  `api/src/test/java/br/com/liberdadefamilia/`)
- Test: `workspace/WorkspaceGuardIT.java`, `ledger/domain/BalanceCalculatorTest.java` (caso novo em
  arquivo existente)

**Interfaces:**
- Consumes: `WorkspaceMember` (com `role` e `membershipId`), `BalanceCalculator`.
- Produces:
  - `record AccountScope(@Nullable Set<UUID> accountIds)`: `static AccountScope all()`;
    `boolean includes(UUID accountId)`; `boolean isRestricted()`. Construída por
    `AccountRepository.scopeFor(WorkspaceMember member)`: YOUTH → as contas com
    `owner_membership_id` = o vínculo (arquivadas incluídas); os outros papéis → `all()`.
  - Todo método de leitura do `AccountRepository`, `TransactionRepository` e do painel que lista ou
    soma ganha um parâmetro `AccountScope scope` e filtra por ele no SQL (`account_id = any(?) or
    to_account_id = any(?)` nos lançamentos).

**Matriz (US-10), rota a rota, que o `RoleMatrixIT` cobre:**

| Rota | OWNER | ADULT | YOUTH | VIEWER |
|---|---|---|---|---|
| `GET …/accounts`, `GET …/dashboard` | ✓ | ✓ | só o escopo | ✓ |
| `POST`, `PATCH`, `archive`, `unarchive`, `PUT …/owner` de contas | ✓ | ✓ | 403 | 403 |
| `GET …/categories` | ✓ | ✓ | ✓ | ✓ |
| `POST`, `PATCH`, `archive`, `unarchive` de categorias | ✓ | ✓ | 403 | 403 |
| `GET …/transactions`, `GET …/transactions/{id}` | ✓ | ✓ | só o escopo | ✓ |
| `POST …/transactions` | ✓ | ✓ | só no escopo e com ele mesmo como "Quem" | 403 |
| `PATCH`, `DELETE`, `restore` de lançamentos | ✓ | ✓ | só os próprios, no escopo | 403 |
| `GET …/members`, `GET …/modules` | ✓ | ✓ | ✓ | ✓ |
| `PATCH`, `DELETE …/members/{id}` | ✓ | 403 | 403 | 403 |
| `POST …/members/{id}/recovery`, convites (`POST`, `GET`, `DELETE`) | ✓ | ✓ | 403 | 403 |
| `PUT …/modules/{key}` | ✓ | ✓ | 403 | 403 |
| `PATCH /v1/workspaces/{ws}`, `POST …/export`, `POST` e `DELETE …/deletion` | ✓ | 403 | 403 | 403 |

As linhas de módulos, exportação e exclusão entram no `RoleMatrixIT` nas Tasks 7, 10 e 11, cada
uma acrescentando as suas.

- [ ] **Step 1: Escrever os testes (falhando)**

`YouthScopeIT` (família com Dona, Jovem "Leo" com a carteira, e a conta "Corrente" da família):

| Teste | Esperado |
|---|---|
| `youthSeesOnlyWallet` | `GET …/accounts` do Jovem → só "Carteira de Leo" |
| `youthSeesAllowanceTransfer` | Dona transfere R$ 50,00 da Corrente para a carteira → o Jovem vê a transferência; a Dona também |
| `youthDoesNotSeeFamilyExpenses` | gasto da Dona na Corrente → fora da lista do Jovem |
| `youthGetByIdOutsideScopeIs404` | `GET …/transactions/{id}` do gasto da Corrente → 404 `transaction.not-found` |
| `youthRecordsInWallet` | gasto de R$ 10,00 na carteira → 201, `memberId` = ele |
| `youthCannotRecordInFamilyAccount` | gasto na Corrente → a mesma resposta de conta inexistente que um id inventado recebe |
| `youthCannotTransferOut` | transferência da carteira para a Corrente → a mesma resposta |
| `youthCannotPickAnotherMember` | `memberId` da Dona → 422 `transaction.member-forbidden` |
| `youthEditsOnlyOwn` | editar o próprio gasto → 200; editar a transferência da mesada (criada pela Dona) → 404 `transaction.not-found` |
| `youthDashboardIsScoped` | painel do Jovem: saldo R$ 40,00 (50 − 10), receitas R$ 0,00, gastos R$ 10,00 |
| `youthCannotWriteCategories` | `POST …/categories` → 403 `workspace.forbidden` |
| `youthReadsMembers` | `GET …/members` → 200 |

`BalanceCalculatorTest.transferFromOutsideScopeCountsInBalanceOnly`: com só a carteira no mapa de
saldos iniciais, uma transferência de fora para ela soma no saldo e na linha diária, não em
receitas nem em gastos; uma transferência entre duas contas do mapa não muda o saldo total (o
comportamento atual continua).

`RoleMatrixIT`: um teste parametrizado (`@MethodSource`) com uma linha por rota e papel da tabela
acima (as linhas das Tasks 2 a 6), chamando cada rota com um corpo válido e conferindo só o status
(2xx, 403 ou 404). As rotas de escrita que não devem mudar nada para o papel negado conferem
também que o banco não mudou.

O caso que existe em `WorkspaceGuardIT` de "YOUTH sempre 403" passa a conferir que YOUTH é negado
só onde o `allow` não o inclui.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*YouthScopeIT' --tests '*RoleMatrixIT' --tests '*BalanceCalculatorTest' --tests '*WorkspaceGuardIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

O `allow` das rotas de leitura passa a incluir `YOUTH`; o escopo é aplicado no serviço e no SQL,
nunca filtrando depois em Java. Escrita fora do escopo responde como inexistente (D13).

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Expected: BUILD SUCCESSFUL, `ledger.domain` ≥ 90% e o `WorkspaceGuardCoverageIT` verde.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(ledger): youth sees and records only in the accounts they own, with a full role matrix test"
```

---

### Task 7: Módulos ligáveis

**Files:**
- Create: `api/src/main/resources/db/migration/V8__modules.sql`
- Create em `workspace/`: `ModuleKey.java`, `ModuleToggled.java`, `RequiresModule.java`, `Modules.java`
- Create em `workspace/internal/`: `ModuleController.java`, `ModuleRepository.java`,
  `ModuleView.java`, `JdbcModules.java`
- Modify: `workspace/internal/WorkspaceGuardInterceptor.java`
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test (novo): `workspace/ModulesIT.java`; `RoleMatrixIT.java` (linhas de módulos)

**Interfaces:**
- Produces:
  - `public enum ModuleKey { PLANNING, INDICATORS, GAMIFICATION, VENTURES }`; no JSON e no path,
    em minúsculas (`planning`).
  - `@Target(METHOD) @Retention(RUNTIME) public @interface RequiresModule { ModuleKey value(); }`.
  - `public interface Modules { boolean isEnabled(UUID workspaceId, ModuleKey key); }`.
  - `ModuleView(ModuleKey key, boolean enabled)`; `GET` sempre devolve as quatro, na ordem do enum.
  - `ModuleToggled(UUID workspaceId, ModuleKey key, boolean enabled, UUID actorUserId)`, publicado
    só quando o valor muda.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /v1/workspaces/{ws}/modules` | `listModules` | todos | 200 `ModuleView[]` | — |
| `PUT /v1/workspaces/{ws}/modules/{key}` | `setModule` | OWNER, ADULT | 200 `ModuleView` | 404 `module.not-found` (chave desconhecida); 400 `request.invalid` |

**Como fica:**
- `V8__modules.sql`: `workspace_modules` como no modelo, com `module_key` em
  `CHECK (module_key IN ('planning', 'indicators', 'gamification', 'ventures'))`, PK
  `(workspace_id, module_key)`, `updated_by uuid NULL` → `users ON DELETE SET NULL` e
  `workspace_id` → `workspaces ON DELETE CASCADE`.
- `PUT` faz `INSERT … ON CONFLICT (workspace_id, module_key) DO UPDATE`; repetir o mesmo valor
  responde 200 sem evento.
- O `WorkspaceGuardInterceptor`, depois de checar o papel, lê `@RequiresModule` e responde 403
  `module.disabled` ("Este módulo está desligado na sua família.") se estiver desligado.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ModulesIT.allOffByDefault` | família nova → as quatro com `enabled` falso |
| `ModulesIT.adultTurnsOn` | Adulto liga `planning` → 200; `GET` mostra ligado para todos os papéis |
| `ModulesIT.turningOffKeepsRow` | desligar → 200; a linha continua no banco com `enabled` falso |
| `ModulesIT.sameValueNoEvent` | ligar duas vezes → um `ModuleToggled` só (ouvinte de teste) |
| `ModulesIT.unknownKeyIs404` | `PUT …/modules/budgets` → 404 `module.not-found` |
| `ModulesIT.disabledModuleRouteIs403` | rota de teste com `@RequiresModule(PLANNING)` (controller no pacote de teste) → 403 `module.disabled` desligado e 200 ligado |
| `RoleMatrixIT` (linhas novas) | `GET` para os quatro papéis; `PUT` 403 para Jovem e Leitor |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ModulesIT' --tests '*RoleMatrixIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica".

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
git commit -m "feat(workspace): family-wide optional modules with a guard for module routes"
```

---

### Task 8: Trilha de auditoria e histórico de acessos

**Files:**
- Create: `api/src/main/resources/db/migration/V9__audit.sql`
- Create em `audit/`: `package-info.java` (`@ApplicationModule(allowedDependencies = {"identity",
  "workspace", "shared"})`), `AuditTrail.java`, `AuditEntry.java`, `ActivityItem.java`
- Create em `audit/internal/`: `AuditRepository.java`, `AuditListeners.java`, `JdbcAuditTrail.java`,
  `ActivityController.java`, `AuditPurgeJob.java`
- Create: `workspace/internal/InvitationPurgeJob.java`
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test (novos): `audit/AuditIT.java`, `audit/ActivityIT.java`, `audit/AuditPurgeJobIT.java`,
  `workspace/InvitationPurgeJobIT.java`

**Interfaces:**
- Consumes: os eventos das Tasks 1 a 7 (`SignedIn`, `MemberJoined`, `MemberRoleChanged`,
  `MemberRemoved`, `WorkspaceRenamed`, `InvitationCreated`, `InvitationRevoked`,
  `RecoveryCreated`, `RecoveryUsed`, `ModuleToggled`); `Memberships.membershipsOf`,
  `UserDirectory.findAll`.
- Produces:
  - `public record AuditEntry(@Nullable UUID workspaceId, @Nullable UUID actorUserId, String
    action, String targetType, @Nullable UUID targetId, Map<String, String> metadata)`.
    `targetType` em `USER`, `MEMBERSHIP`, `INVITATION`, `WORKSPACE`, `MODULE`, `CONSENT`.
  - `public interface AuditTrail { void record(AuditEntry entry); List<ActivityItem>
    activityOf(UUID userId, int limit); }` (o `privacy` grava as próprias ações por aqui e lê o
    histórico para a exportação).
  - `public record ActivityItem(UUID id, String action, Instant occurredAt, @Nullable UUID
    workspaceId, @Nullable String actorName, boolean byMe, Map<String, String> metadata)`.
    `actorName` é o apelido no vínculo da família do evento ou, sem ele, o nome da pessoa; nulo se
    a conta foi apagada.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /v1/me/activity` | `listMyActivity` | logado | 200 `ActivityItem[]` | — |

**Mapa evento → ação** (a ação e as chaves do `metadata` são fixas):

| Evento | `action` | Alvo | `metadata` |
|---|---|---|---|
| `SignedIn` | `auth.signed_in` | `USER` | `method`, `browser`, `os` |
| `MemberJoined` | `member.joined` | `MEMBERSHIP` | `role` |
| `MemberRoleChanged` | `member.role_changed` | `MEMBERSHIP` | `from`, `to` |
| `MemberRemoved` | `member.removed` | `MEMBERSHIP` | `reason` |
| `WorkspaceRenamed` | `workspace.renamed` | `WORKSPACE` | — |
| `InvitationCreated` (só `kind = INVITE`) | `invitation.created` | `INVITATION` | `role` |
| `InvitationRevoked` | `invitation.revoked` | `INVITATION` | — |
| `RecoveryCreated` | `recovery.created` | `MEMBERSHIP` (o alvo) | — |
| `RecoveryUsed` | `recovery.used` | `MEMBERSHIP` | — |
| `ModuleToggled` | `module.toggled` | `MODULE` (sem id) | `key`, `enabled` |

(`InvitationCreated` com `kind = RECOVERY` não grava nada: o `RecoveryCreated` já grava, com o
vínculo como alvo, para aparecer no histórico da pessoa.)

**Como fica:**
- `V9__audit.sql`: `audit_events` como no modelo; `workspace_id` → `workspaces ON DELETE SET NULL`;
  `actor_user_id` → `users ON DELETE SET NULL` (já nasce assim, a Task 11 não precisa mexer);
  `metadata jsonb NOT NULL DEFAULT '{}'`; índices `(actor_user_id, occurred_at DESC)`,
  `(target_type, target_id, occurred_at DESC)` e `(occurred_at)`.
- `AuditListeners`: um `@EventListener` por evento, síncrono, na transação de quem publica.
- `activityOf`: eventos dos últimos 12 meses em que `actor_user_id` = a pessoa, ou o alvo é `USER`
  com o id dela, ou `MEMBERSHIP` com um dos vínculos dela (ativos ou não); ordem decrescente;
  limite 100.
- `AuditPurgeJob` (`0 10 3 * * *`): apaga `occurred_at < now() - interval '12 months'`.
- `InvitationPurgeJob` (`0 15 3 * * *`): apaga convites com `coalesce(used_at, revoked_at,
  expires_at) < now() - interval '90 days'`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `AuditIT.signInIsAudited` | login no mock → uma linha `auth.signed_in` com `method` `GOOGLE` e `browser`/`os` do `User-Agent` do teste; nenhuma coluna ou chave com IP |
| `AuditIT.roleChangeIsAudited` | mudar papel → `member.role_changed` com `from` e `to` |
| `AuditIT.metadataNeverHasNames` | depois de convite, renomear e mudar apelido, nenhum `metadata` contém o apelido, o nome da família ou o nome da pessoa |
| `AuditIT.failedActionLeavesNoRow` | `PATCH` recusado com 422 `member.last-owner` → nenhuma linha nova |
| `ActivityIT.showsOwnAndTargeted` | a Dona muda o papel do Adulto → aparece no histórico dos dois, com `byMe` verdadeiro só no da Dona e `actorName` igual ao apelido da Dona |
| `ActivityIT.hidesOthers` | o Leitor não vê a mudança de papel do Adulto |
| `ActivityIT.recoveryShowsForTarget` | link de recuperação criado e usado → os dois eventos no histórico do Jovem |
| `ActivityIT.limitedTo100` | 120 eventos → 100, do mais novo ao mais velho |
| `AuditPurgeJobIT` | evento de 12 meses + 1 dia some; de 11 meses fica |
| `InvitationPurgeJobIT` | convite usado há 91 dias some; usado há 89 fica; pendente vencido há 91 dias some |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests 'br.com.liberdadefamilia.audit.*' --tests '*InvitationPurgeJobIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga as tabelas e "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web typecheck`
Expected: tudo verde, com o `ApplicationModulesTest` aceitando o módulo novo.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(audit): audit trail of sensitive actions and the personal activity history"
```

---

### Task 9: Consentimentos

**Files:**
- Create: `api/src/main/resources/db/migration/V10__consents.sql`
- Create em `privacy/`: `package-info.java` (`allowedDependencies = {"identity", "workspace",
  "ledger", "audit", "shared"}`)
- Create em `privacy/internal/`: `ConsentRepository.java`, `ConsentListener.java`,
  `ConsentController.java`, `ConsentView.java`, `PrivacyRejectedException.java`
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test (novo): `privacy/ConsentsIT.java`

**Interfaces:**
- Consumes: `MemberJoined`, `MemberRoleChanged`, `Memberships.find`, `remove` e `workspaceNames`
  (Task 2); `AuditTrail.record` (Task 8).
- Produces:
  - `ConsentView(UUID id, ConsentPurpose purpose, String documentVersion, UUID workspaceId, String
    workspaceName, @Nullable String subjectName, @Nullable String grantedByName, Instant grantedAt,
    @Nullable Instant revokedAt, boolean canRevoke)`; `enum ConsentPurpose { TERMS, PRIVACY,
    PARENTAL }`. `subjectName` é o apelido do Jovem; `grantedByName`, o apelido do responsável.
  - `PrivacyRejectedException(int status, String code, String detail)`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `GET /v1/me/consents` | `listMyConsents` | logado | 200 `ConsentView[]` (dados para mim e dados por mim) | — |
| `DELETE /v1/me/consents/{id}` | `revokeConsent` | logado, só quem deu | 204 (repetir: 204) | 404 `consent.not-found` (nem meu nem dado por mim); 403 `consent.revoke-forbidden` |

**Como fica:**
- `V10__consents.sql`: `consents` como no modelo; `user_id` → `users ON DELETE CASCADE`;
  `workspace_id` → `workspaces ON DELETE CASCADE`; `granted_by` → `users ON DELETE SET NULL`;
  `purpose` com `CHECK`; índices `(user_id)` e `(granted_by)`.
- `ConsentListener` grava `PARENTAL` (`document_version` = `"parental-2026-11"`, `granted_by` =
  quem convidou ou quem mudou o papel) ao ouvir `MemberJoined` com `role = YOUTH` e
  `MemberRoleChanged` com `to = YOUTH`, e grava a ação `consent.granted` no `AuditTrail`.
- Revogar: grava `revoked_at`, chama `Memberships.remove(..., CONSENT_REVOKED)` e grava
  `consent.revoked`. Revogar de novo não muda nada.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `youthJoinRecordsParentalConsent` | Jovem aceita convite da Dona → um `PARENTAL` com `user_id` = Jovem e `granted_by` = Dona |
| `roleChangeToYouthRecordsConsent` | Adulto vira Jovem com `parentalConsent` → um `PARENTAL` com `granted_by` = quem mudou |
| `bothSidesSeeConsent` | a Dona vê com `canRevoke` verdadeiro; o Jovem vê com `canRevoke` falso |
| `onlyGuardianRevokes` | o Jovem tenta revogar → 403 `consent.revoke-forbidden`; outro Adulto → 404 `consent.not-found` |
| `revokeRemovesYouth` | a Dona revoga → 204; o Jovem fica `REMOVED`, os lançamentos dele continuam na família, e `GET /v1/me` dele não lista mais a família |
| `revokeIsAudited` | a revogação gera `consent.revoked` no histórico da Dona e do Jovem |
| `revokeTwiceIsNoop` | segunda revogação → 204, `revoked_at` igual |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ConsentsIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica".

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
git commit -m "feat(privacy): record and revoke parental consent"
```

---

### Task 10: Exportação

**Files:**
- Create em `ledger/`: `LedgerExport.java`, `ExportedAccount.java`, `ExportedCategory.java`,
  `ExportedTransaction.java`; em `ledger/internal/`: `LedgerJdbcExport.java`
- Create em `privacy/internal/`: `ExportController.java`, `ExportService.java`, `CsvWriter.java`
- Create: `identity/UserProfile.java`
- Modify: `identity/UserDirectory.java` (`profileOf`), `identity/internal/JdbcUserDirectory.java`
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/handlers.ts`
- Test (novos): `privacy/ExportIT.java`, `privacy/internal/CsvWriterTest.java`; `RoleMatrixIT.java`
  (linha da exportação da família)

**Interfaces:**
- Consumes: `RequiresRecentSignIn`, `RateLimiter` (Task 1); `Memberships.all`, `membershipsOf` e
  `workspaceNames` (Task 2); `Modules.isEnabled` (Task 7); `AuditTrail.activityOf` e `record`
  (Task 8); `ConsentRepository` (Task 9).
- Produces:
  - `public interface LedgerExport { List<ExportedAccount> accounts(UUID workspaceId);
    List<ExportedCategory> categories(UUID workspaceId); List<ExportedTransaction>
    transactions(UUID workspaceId); List<ExportedTransaction> transactionsOf(UUID workspaceId,
    UUID membershipId); }` (só não excluídos; `transactionsOf` traz onde o vínculo é "Quem" ou
    autor).
  - `ExportedTransaction(UUID id, String kind, Money amount, LocalDate occurredOn, String
    accountName, @Nullable String toAccountName, @Nullable String categoryName, @Nullable String
    description, UUID memberId, Instant createdAt)`; `ExportedAccount(UUID id, String name,
    String type, Money initialBalance, Money balance, @Nullable UUID ownerMembershipId,
    @Nullable Instant archivedAt)`; `ExportedCategory(UUID id, @Nullable UUID parentId,
    String kind, String name, @Nullable Instant archivedAt)`. `kind` e `type` são o `name()` do
    enum (`EXPENSE`, `CHECKING`…), em `String` porque `AccountType` e o tipo de categoria são
    internos do `ledger`.
  - `UserDirectory.profileOf(UUID userId)` → `UserProfile(String displayName, @Nullable String
    email, String locale, Instant createdAt, @Nullable Instant passkeyCreatedAt)`.
  - `CsvWriter`: `static String cell(String value)` (aspas quando tem `;`, aspas ou quebra de
    linha; `'` na frente quando começa com `=`, `+`, `-`, `@`, `\t` ou `\r`);
    `static String amount(BigDecimal value)` (`1234,56`, sempre 2 casas); e
    `byte[] toBytes(List<String> header, List<List<String>> rows)` (BOM UTF-8, `\r\n`).

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `POST /v1/me/export` | `exportMyData` | logado (vale na carência da exclusão) | 200 `application/zip`, `Content-Disposition: attachment; filename="liberdade-meus-dados-AAAA-MM-DD.zip"` | 403 `auth.reauthentication-required`; 429 `rate.limited` |
| `POST /v1/workspaces/{ws}/export` | `exportFamilyData` | OWNER (vale na exclusão pendente) | 200 `application/zip`, `filename="liberdade-familia-AAAA-MM-DD.zip"` | 403 `auth.reauthentication-required`; 429 `rate.limited` |

**Conteúdo do zip:**
- Minha exportação: `dados.json` (`formatVersion`, `exportedAt`, `profile`, `memberships` com a
  família, papel, apelido e status, `consents`, `passkeys` só com a data de criação, `activity`,
  `transactions`), `lancamentos.csv` e `atividade.csv`.
- Exportação da família: `dados.json` (`formatVersion`, `exportedAt`, `workspace`, `members`,
  `accounts`, `categories`, `transactions`, `modules`), `lancamentos.csv`, `contas.csv`,
  `categorias.csv` e `membros.csv`.
- Colunas de `lancamentos.csv`: `data;tipo;valor;moeda;conta;conta_destino;categoria;descricao;quem;criado_em`.
  `tipo` em "Gasto", "Receita" ou "Transferência"; `quem` é o nome exibido do vínculo (D4).
- `contas.csv`: `nome;tipo;saldo_inicial;saldo;moeda;titular;arquivada_em`.
  `categorias.csv`: `nome;tipo;categoria_pai;arquivada_em`. `membros.csv`: `apelido;papel;situacao`.
  `atividade.csv`: `quando;acao;quem;navegador;sistema`.
- O `ExportService` grava `data.exported` (`scope` `ME` ou `FAMILY`) no `AuditTrail`.

- [ ] **Step 1: Escrever os testes (falhando)**

`CsvWriterTest`:

| Caso | Esperado |
|---|---|
| `amount(new BigDecimal("1234.56"))` | `1234,56` |
| `amount(new BigDecimal("5"))` | `5,00` |
| `cell("=HYPERLINK(\"x\")")` | começa com `'=` e vem entre aspas, com as aspas internas dobradas |
| `cell("-10")`, `cell("+1")`, `cell("@a")`, `cell("\tx")` | cada um ganha `'` na frente |
| `cell("Pão; leite")` | `"Pão; leite"` |
| `toBytes` | começa com `EF BB BF`, linhas terminam em `\r\n`, acentos em UTF-8 |

`ExportIT` (o PRD §7.1 lançado pelo `TestLedger`, mais um gasto com descrição `=1+1`):

| Teste | Esperado |
|---|---|
| `myExportHasMyData` | `POST /v1/me/export` → zip com `dados.json`, `lancamentos.csv` e `atividade.csv`; o JSON tem o e-mail da pessoa e nenhum outro e-mail |
| `myExportHasOnlyMyTransactions` | lançamento da Dona com "Quem" = o Adulto entra na exportação do Adulto; lançamento só da Dona não entra |
| `familyExportHasEverything` | `POST …/export` da Dona → os cinco arquivos; `lancamentos.csv` tem a linha `…;Gasto;1234,56;BRL;Corrente;;Mercado;…`; nenhum e-mail no zip |
| `csvInjectionNeutralized` | a descrição `=1+1` sai como `'=1+1` |
| `deletedTransactionsExcluded` | lançamento excluído não aparece |
| `exportNeedsRecentSignIn` | login de 11 min → 403 `auth.reauthentication-required` |
| `adultCannotExportFamily` | Adulto → 403 `workspace.forbidden` |
| `exportIsAudited` | depois da exportação, `GET /v1/me/activity` tem `data.exported` com `scope` |
| `exportIsRateLimited` | 11 exportações em 1 h → 429 |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*ExportIT' --tests '*CsvWriterTest' --tests '*RoleMatrixIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

O zip é escrito direto no `OutputStream` da resposta (`StreamingResponseBody`), sem arquivo
temporário. O JSON usa o `ObjectMapper` da aplicação, com o mesmo formato de dinheiro da API.

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
git commit -m "feat(privacy): export personal and family data as JSON and spreadsheet-safe CSV"
```

---

### Task 11: Excluir a conta e a família

**Files:**
- Create: `api/src/main/resources/db/migration/V11__deletion.sql`
- Create em `identity/`: `UserDeletion.java`; em `identity/internal/`: `JdbcUserDeletion.java`,
  `PendingDeletionFilter.java`
- Create em `workspace/`: `FamilyDeletion.java`, `WorkspaceDeletionRequested.java`,
  `WorkspaceDeletionCancelled.java`; em `workspace/internal/`: `JdbcFamilyDeletion.java`,
  `FamilyPurgeJob.java`
- Create em `privacy/internal/`: `AccountDeletionController.java`, `AccountDeletionService.java`,
  `FamilyDeletionController.java`, `AccountPurgeJob.java`
- Modify: `identity/CurrentUser.java`, `identity/internal/UserAccounts.java`,
  `CurrentUserArgumentResolver.java`, `SecurityConfig.java` (o filtro)
- Modify: `workspace/WorkspaceScoped.java` (`includePendingDeletion`),
  `workspace/internal/WorkspaceGuardInterceptor.java`, `WorkspaceRepository.java`,
  `MeController.java`, `MeWorkspace.java`
- Modify: `audit/internal/AuditListeners.java` (os dois eventos de família)
- Modify: `api/openapi.json`, `web/src/shared/api/schema.ts`, `web/src/mocks/data.ts`,
  `web/src/mocks/handlers.ts`
- Test (novos): `privacy/AccountDeletionIT.java`, `privacy/FamilyDeletionIT.java`,
  `privacy/AccountPurgeJobIT.java`, `workspace/FamilyPurgeJobIT.java`; `RoleMatrixIT.java` (linhas
  da exclusão)

**Interfaces:**
- Consumes: `RequiresRecentSignIn` (Task 1); `Memberships.isSoleActiveOwnerOfLiveFamily`,
  `detachUser` (Task 2); `AuditTrail.record` (Task 8).
- Produces:
  - `public interface UserDeletion { Instant schedule(UUID userId); void cancel(UUID userId);
    List<UUID> dueForPurge(Instant now); void purge(UUID userId); }`. `schedule` devolve a data
    final (pedido + 30 dias) e é idempotente; `purge` faz os passos 2 a 4 da D24.
  - `CurrentUser` ganha `@Nullable Instant deletionRequestedAt`.
  - `public interface FamilyDeletion { Instant schedule(UUID workspaceId, UUID actorUserId); void
    cancel(UUID workspaceId, UUID actorUserId); }`.
  - `WorkspaceScoped` ganha `boolean includePendingDeletion() default false`.
  - `WorkspaceDeletionRequested(UUID workspaceId, UUID actorUserId)` e
    `WorkspaceDeletionCancelled(UUID workspaceId, UUID actorUserId)`.
  - `MeView.user` (`MeUser`) ganha `@Nullable Instant deletionScheduledFor`; `MeWorkspace` ganha
    `@Nullable Instant deletionScheduledFor`.
  - Resposta das duas exclusões: 202 `DeletionScheduled(Instant deletionScheduledFor)`.

| Método e rota | operationId | Papéis | Sucesso | Erros |
|---|---|---|---|---|
| `POST /v1/me/deletion` | `requestAccountDeletion` | logado | 202 `DeletionScheduled` (repetir: a mesma data) | 409 `account.sole-owner`; 403 `auth.reauthentication-required` |
| `DELETE /v1/me/deletion` | `cancelAccountDeletion` | logado | 204 (repetir: 204) | — |
| `POST /v1/workspaces/{ws}/deletion` | `requestFamilyDeletion` | OWNER | 202 `DeletionScheduled` | 403 `auth.reauthentication-required` |
| `DELETE /v1/workspaces/{ws}/deletion` | `cancelFamilyDeletion` | OWNER (`includePendingDeletion`) | 204 | — |

**Como fica:**
- `V11__deletion.sql`:
  - `memberships.user_id` sem `NOT NULL`, FK refeita com `ON DELETE SET NULL`;
  - `memberships.parental_consent_by` com FK refeita `ON DELETE SET NULL` (as FKs de autoria de
    `invitations` e `workspace_modules` já nascem assim na V7 e na V8);
  - índice `users (deletion_requested_at) WHERE deletion_requested_at IS NOT NULL` e o mesmo em
    `workspaces`.
- `UserAccounts.findActive` deixa de esconder quem pediu exclusão; quem decide é o
  `PendingDeletionFilter` (depois da autenticação, em `/v1/**`): pessoa com
  `deletion_requested_at` e rota fora da lista da D23 → 403 `account.deletion-pending` ("Sua conta
  está marcada para exclusão."). O `CurrentUserArgumentResolver` não responde mais 401 nesse caso.
- O guard, para uma família com `deletion_requested_at`: 404 `workspace.not-found`, a não ser que a
  rota tenha `includePendingDeletion = true` e a pessoa seja Dona ativa.
- `/v1/me`: família pendente aparece só para os Donos, com `deletionScheduledFor`.
- `AccountDeletionService` grava `account.deletion_requested` e `account.deletion_cancelled` no
  `AuditTrail`; o `audit` grava os eventos de família como `workspace.deletion_requested` e
  `workspace.deletion_cancelled`.
- `AccountPurgeJob` (`0 45 3 * * *`): para cada id de `dueForPurge(now)`, numa transação:
  `memberships.detachUser(id)` e `userDeletion.purge(id)`.
- `FamilyPurgeJob` (`0 50 3 * * *`): `DELETE FROM workspaces WHERE deletion_requested_at < now() -
  interval '30 days'`.

- [ ] **Step 1: Escrever os testes (falhando)**

`AccountDeletionIT`:

| Teste | Esperado |
|---|---|
| `adultSchedulesDeletion` | Adulto pede → 202 com a data = agora + 30 dias; `GET /v1/me` traz `deletionScheduledFor` |
| `deletionNeedsRecentSignIn` | login de 11 min → 403 `auth.reauthentication-required` |
| `soleOwnerMustTransferFirst` | única Dona → 409 `account.sole-owner`; depois de promover outra pessoa a Dona → 202 |
| `soleOwnerOfPendingFamilyMayDelete` | única Dona de família com exclusão pendente → 202 |
| `pendingAccountIsLockedDown` | na carência, `GET …/transactions` → 403 `account.deletion-pending`; `GET /v1/me`, `POST /v1/me/export`, `GET /v1/csrf` e `POST /logout` funcionam |
| `pendingAccountCanSignIn` | na carência, entrar de novo pelo mock → sessão criada, `/v1/me` com a data |
| `cancelRestoresAccess` | `DELETE /v1/me/deletion` → 204; `GET …/transactions` volta a 200 |
| `deletionIsAudited` | pedir e cancelar → as duas ações no histórico |

`AccountPurgeJobIT` (Adulto com passkey, sessão aberta e um lançamento):

| Teste | Esperado |
|---|---|
| `purgesAfterGrace` | `Clock` 30 dias + 1 min → o usuário, as identidades, o `user_entities`, as credenciais e as sessões dele somem |
| `keepsTransactionsWithNickname` | o vínculo fica `REMOVED` com `user_id` nulo; o lançamento continua, e `GET …/members` mostra o apelido com `displayName` nulo |
| `keepsAuditWithoutActor` | os eventos dele ficam com `actor_user_id` nulo |
| `notBeforeGrace` | 29 dias → nada muda |
| `cancelledIsNotPurged` | pedido cancelado → nada muda |

`FamilyDeletionIT` e `FamilyPurgeJobIT`:

| Teste | Esperado |
|---|---|
| `ownerSchedulesFamilyDeletion` | 202; o Adulto passa a receber 404 `workspace.not-found` em qualquer rota da família e não a vê no `/v1/me` |
| `ownerSeesPendingFamily` | a Dona vê a família no `/v1/me` com `deletionScheduledFor`; `GET …/accounts` dá 404; `POST …/export` funciona |
| `ownerCancels` | `DELETE …/deletion` → 204; o Adulto volta a ver a família |
| `adultCannotDeleteFamily` | 403 `workspace.forbidden` |
| `purgeRemovesEverything` | `Clock` 30 dias + 1 min → a família, os vínculos, as contas, as categorias, os lançamentos, os convites, os módulos e os consentimentos somem; `audit_events` fica com `workspace_id` nulo |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*AccountDeletionIT' --tests '*AccountPurgeJobIT' --tests '*FamilyDeletionIT' --tests '*FamilyPurgeJobIT' --tests '*RoleMatrixIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga a tabela e "Como fica". O `FamilyPurgeJob` confia nas cascatas: as FKs de `transactions` para
`memberships` são verificadas no fim do comando, quando os lançamentos também já saíram; o
`purgeRemovesEverything` prova isso.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run (em `api/`): `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'; ./gradlew build`
Run: `corepack pnpm --dir web gen:api; corepack pnpm --filter web typecheck`
Expected: tudo verde, com o `WorkspaceGuardCoverageIT` e o `ApplicationModulesTest` verdes.

- [ ] **Step 6: Commit**

```bash
git add api web/src
git commit -m "feat(privacy): delete the account or the family after a cancellable 30-day grace"
```

---

### Task 12: Base do web: família escolhida, sessão entre abas, reautenticação e módulos

**Files:**
- Create em `web/src/shared/auth/`: `passkey.ts`, `google.ts` (movido de `features/auth`),
  `returnPath.ts`, `useReauth.ts`, `ReauthDialog.tsx`, `index.ts`, e os testes de cada um
- Create em `web/src/shared/modules/`: `registry.ts`, `useModuleEnabled.ts`, `index.ts`,
  `registry.test.ts`, `useModuleEnabled.test.tsx`
- Create: `web/src/shared/offline/sessionChannel.ts`, `sessionChannel.test.ts`
- Modify: `web/src/shared/api/hooks.ts` (`useCurrentWorkspace`, `useWorkspaces`,
  `selectWorkspace`, `useModules`), `endpoints.ts`, `queryKeys.ts`, `errors.ts`,
  `web/src/shared/offline/session.ts`
- Modify: `web/src/features/auth/SignInPage.tsx`, `PasskeyButton.tsx`, `SignOutButton.tsx`
- Modify: `web/src/app/RootRedirect.tsx`, `SessionLayout.tsx`, `providers.tsx`
- Modify: `web/src/shared/i18n/messages.pt-BR.ts` (textos do diálogo)

**Interfaces:**
- Consumes: `GET /v1/me` com várias famílias (Task 2), `GET …/modules` (Task 7), o 403
  `auth.reauthentication-required` (Task 1).
- Produces:
  - `useCurrentWorkspace(): MeWorkspace | undefined` mantém a assinatura; lê
    `localStorage['liberdade.workspaceId']` e cai em `workspaces[0]` se o id não estiver na lista.
    `useWorkspaces(): MeWorkspace[]`; `selectWorkspace(id: string): void` grava e notifica.
  - `signInWithPasskey(): Promise<void>` e `registerPasskey(label: string): Promise<void>` em
    `shared/auth/passkey.ts` (a cerimônia que hoje está em `SignInPage` e `PasskeyButton`).
  - `startGoogleSignIn(options?: { reauth?: boolean; returnTo?: string })` em `shared/auth/google.ts`.
  - `saveReturnPath(path: string): void` e `takeReturnPath(): string | undefined` em
    `returnPath.ts`: só aceita caminhos que começam com `/` e estão na lista `RETURN_PATHS`
    (`/convite`, `/mais/familia`, `/mais/privacidade`, `/mais/modulos`); qualquer outro é ignorado.
  - `useReauth(): <T>(action: () => Promise<T>) => Promise<T>`: roda a ação; se der 403
    `auth.reauthentication-required`, abre o `ReauthDialog` (carregado com `lazy`); depois do
    sucesso, roda a ação de novo, uma vez só; se a pessoa cancelar, rejeita com
    `ReauthCancelledError`.
  - `broadcastSignedOut(): void`, `broadcastSignedIn(userId: string): void` e
    `onSessionMessage(handler: (message: SessionMessage) => void): () => void` em `sessionChannel.ts`.
  - `type ModuleDefinition = { key: ModuleKey; available: boolean }`; `registerModule(definition)`,
    `listModules()` em `registry.ts`; `useModuleEnabled(key: ModuleKey): boolean` (falso enquanto
    carrega e offline sem cache).

**Como fica:**
- O `SessionLayout` assina o canal: `signed-out` → `clearSession` e `/entrar`; `signed-in` com
  outro `userId` que o do `me` em cache → `clearSession` e `location.assign('/inicio')`.
- `SignOutButton` chama `broadcastSignedOut()` depois do `POST /logout`; o `RootRedirect` chama
  `broadcastSignedIn(me.user.id)` quando o `me` chega depois de uma entrada.
- O `ReauthDialog` ("Confirme que é você") oferece "Usar passkey" e "Entrar com Google". O Google
  salva o caminho atual com `saveReturnPath` e sai com `reauth: true`; o `RootRedirect`, na volta,
  usa `takeReturnPath()` antes do destino padrão.
- Se depois da reautenticação o `me.user.id` mudou, `clearSession` e `/inicio`.
- Trocar a família não apaga o cache: as chaves já começam com `['ws', ws]`.

**Textos:**
- `reauth.title`: "Confirme que é você"
- `reauth.body`: "Por segurança, esta ação pede que você entre de novo."
- `reauth.passkey`: "Usar passkey"; `reauth.google`: "Entrar com Google"; `reauth.cancel`: "Cancelar"
- `reauth.failed`: "Não deu para confirmar. Tente de novo."

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `hooks.test.tsx` | `selectedWorkspaceWins` | duas famílias e `localStorage` com o id da segunda → `useCurrentWorkspace` é a segunda |
| `hooks.test.tsx` | `fallsBackToFirst` | id que não está na lista → a primeira |
| `returnPath.test.ts` | `acceptsListedPaths` | `/mais/privacidade` volta; `https://evil.example.com`, `//evil.example.com` e `/qualquer` não |
| `returnPath.test.ts` | `takeClears` | a segunda chamada a `takeReturnPath` é `undefined` |
| `useReauth.test.tsx` | `retriesOnceAfterReauth` | a ação falha com 403 `auth.reauthentication-required`, a passkey (simulada) dá certo → a ação roda de novo e resolve |
| `useReauth.test.tsx` | `cancelRejects` | fechar o diálogo → `ReauthCancelledError`, a ação não roda de novo |
| `useReauth.test.tsx` | `otherErrorsPassThrough` | 422 → rejeita com o erro, sem diálogo |
| `useReauth.test.tsx` | `differentUserClearsSession` | depois da passkey, o `me` volta com outro id → `clearSession` chamado e navegação para `/inicio` |
| `sessionChannel.test.ts` | `deliversMessages` | com um `BroadcastChannel` falso, `broadcastSignedOut` chega ao `onSessionMessage` de outro canal |
| `sessionChannel.test.ts` | `noopWithoutSupport` | sem `BroadcastChannel` no `globalThis`, nada lança |
| `SessionLayout.test.tsx` | `signedOutElsewhere` | mensagem `signed-out` → `clearSession` e `/entrar` |
| `registry.test.ts` | `registersFakeModule` | `registerModule({ key: 'planning', available: true })` aparece em `listModules()` |
| `useModuleEnabled.test.tsx` | `followsApi` | MSW com `planning` ligado → `true`; desligado → `false`; carregando → `false` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/shared src/app`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga "Como fica". O `ReauthDialog` usa o `Sheet`/`ConfirmDialog` que já existem e entra com
`lazy()`.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde, carga inicial ≤ 200 KB. Anote a medição para a descrição do PR 2; se
estourar, aplique a saída da D27 (`/inicio` em `lazy()`) nesta task.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): selected family per device, cross-tab session, reauthentication dialog and module registry"
```

---

### Task 13: Família no web: membros, convite, aceite e recuperação

**Files:**
- Create em `web/src/features/members/`: `FamilyPage.tsx`, `MemberRow.tsx`, `MemberSheet.tsx`,
  `InvitePage.tsx`, `InvitationLink.tsx`, `AcceptInvitationPage.tsx`, `RecoveryLinkSheet.tsx`,
  `RenameFamilySheet.tsx`, `RecoveryNotice.tsx`, `WorkspaceSwitcher.tsx`, `invitationToken.ts`,
  `memberName.ts`, `messages.pt-BR.ts`, `register.ts`, e os testes
- Modify: `web/src/app/routes.tsx` (`/mais/familia`, `/mais/familia/convidar`, `/convite`),
  `MorePage.tsx`, `RootRedirect.tsx`
- Create: `web/src/app/MorePage.test.tsx`
- Modify: `web/src/features/dashboard/HomePage.tsx`, `GettingStartedCard.tsx`, `gettingStarted.ts`
- Modify: `web/src/shared/api/endpoints.ts`, `mutations.ts`, `hooks.ts` (`useMembers`,
  `useInvitations`)
- Modify: `web/src/mocks/data.ts`, `handlers.ts`

**Interfaces:**
- Consumes: as rotas das Tasks 2, 3 e 4 (em `InvitationView`, `nickname` e `createdByName` podem
  vir nulos); `useReauth`, `registerPasskey`, `startGoogleSignIn`, `saveReturnPath` (Task 12).
- Produces:
  - `readInvitationToken(): string | undefined` em `invitationToken.ts`: lê o fragmento (43
    caracteres base64url), guarda em `sessionStorage['liberdade.invitationToken']`, apaga o
    fragmento com `history.replaceState`; sem fragmento, devolve o que estiver no
    `sessionStorage`. `clearInvitationToken()`.
  - `useMembers()`, `useInvitations()`, e as mutações `useUpdateMember`, `useRemoveMember`,
    `useCreateInvitation`, `useRevokeInvitation`, `useCreateRecoveryLink`, `useRenameWorkspace`,
    `useAcceptInvitation`, `usePreviewInvitation`.
  - `memberName(member: MemberView, intl): string` (apelido, nome ou "Ex-membro") em
    `features/members/memberName.ts` (o texto traduzido fica fora de `shared/api`), importado pela
    Task 14 de `@/features/members/memberName`.

**Telas:**
- `/mais/familia` (`FamilyPage`): nome da família com "Renomear" (só Dono); lista de membros
  ativos com papel e "Você"; convites pendentes com "Revogar" (Dono e Adulto); "Convidar alguém";
  removidos recolhidos em "Quem saiu". Tocar num membro abre o `MemberSheet`: mudar papel e
  apelido, remover (Dono), "Gerar link de recuperação" (Dono e Adulto, só para `passkeyOnly`). Mudar
  papel, remover e gerar link passam por `useReauth`.
- `/mais/familia/convidar` (`InvitePage`): apelido, papel (o Adulto não vê Dono), e, para Jovem, a
  caixa obrigatória "Declaro que sou responsável legal por esta pessoa e autorizo o uso do app por
  ela." Depois de criar, o `InvitationLink` mostra o link, "Copiar link" e "Compartilhar"
  (`navigator.share` quando existe) e avisa que o link vale 7 dias e uma vez só.
- `/convite` (`AcceptInvitationPage`, fora do `WorkspaceLayout`, aceita sessão anônima): lê o token,
  chama o preview e mostra "<apelido de quem convidou> convidou você para a <família> como <papel>".
  - Logado: "Entrar na família".
  - Anônimo: "Entrar com Google" (salva o retorno `/convite` e sai) e "Criar passkey neste
    aparelho" (pede o nome e aceita com `newUser`). Para o Jovem, só a passkey.
  - `mustRegisterPasskey`: tela "Crie sua passkey para voltar depois", com o aviso de que sem ela
    não dá para entrar de novo; só segue depois de `registerPasskey` dar certo; falhar mostra
    "Tentar de novo".
  - 410: "Este convite não vale mais. Peça um novo para quem convidou." 404: "Convite não
    encontrado." Em todos os casos, o token sai do `sessionStorage` no fim.
- Recuperação: o `RecoveryLinkSheet` mostra o link (24 h, uso único); o `/convite` com `kind =
  RECOVERY` mostra "Recuperar acesso de <apelido>" e vai direto para a passkey.
- `RecoveryNotice`: no Início, carregado com `lazy` só quando `recoveredAt` tem menos de 7 dias e o
  `localStorage['liberdade.recoveryNotice.<membershipId>']` não guarda esse mesmo `recoveredAt`.
- `WorkspaceSwitcher`: no Mais, "Trocar de família" com a lista, quando há duas ou mais.
- Primeiros passos: item "Convidar a família" (link para `/mais/familia/convidar`), feito com
  `familyInvited`, só para Dono e Adulto.

**Textos** (chaves em `members.*`; os principais):
- `members.title`: "Família"; `members.invite`: "Convidar alguém"; `members.you`: "Você"
- `members.role.OWNER`: "Dono"; `ADULT`: "Adulto"; `YOUTH`: "Jovem"; `VIEWER`: "Leitor"
- `members.formerMember`: "Ex-membro"; `members.removedSection`: "Quem saiu"
- `members.lastOwner`: "A família precisa de pelo menos um Dono."
- `invite.consent`: "Declaro que sou responsável legal por esta pessoa e autorizo o uso do app por ela."
- `invite.linkHint`: "O link vale por 7 dias e funciona uma vez só."
- `accept.gone`: "Este convite não vale mais. Peça um novo para quem convidou."
- `accept.passkeyRequired`: "Crie sua passkey para voltar depois. Sem ela, não dá para entrar de novo neste aparelho."
- `recovery.notice`: "Seu acesso foi recuperado em {date}. Se não foi você, avise o Dono da família."
- `gettingStarted.invite`: "Convidar a família"

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `invitationToken.test.ts` | `movesFragmentToSession` | `location.hash` com o token → devolve o token, o `sessionStorage` o guarda e o hash fica vazio |
| `invitationToken.test.ts` | `ignoresMalformed` | fragmento com 10 caracteres → `undefined` |
| `FamilyPage.test.tsx` | `ownerSeesManagement` | Dona → "Renomear", "Convidar alguém" e "Revogar" |
| `FamilyPage.test.tsx` | `viewerSeesListOnly` | Leitor → só a lista |
| `FamilyPage.test.tsx` | `removedCollapsed` | o removido aparece só em "Quem saiu" |
| `MemberSheet.test.tsx` | `roleChangeUsesReauth` | MSW responde 403 `auth.reauthentication-required` e depois 200 → o diálogo abre e o papel muda |
| `MemberSheet.test.tsx` | `lastOwnerMessage` | 422 `member.last-owner` → "A família precisa de pelo menos um Dono." |
| `MemberSheet.test.tsx` | `recoveryOnlyForPasskeyOnly` | membro com Google → sem "Gerar link de recuperação" |
| `InvitePage.test.tsx` | `adultCannotPickOwner` | Adulto → o papel Dono não aparece |
| `InvitePage.test.tsx` | `youthNeedsConsent` | Jovem sem a caixa → "Criar convite" desabilitado |
| `InvitePage.test.tsx` | `showsLinkWithFragment` | depois de criar → o link termina em `/convite#<token>` e "Copiar link" copia |
| `AcceptInvitationPage.test.tsx` | `signedInJoins` | logado → "Entrar na família" → `POST /accept` e navegação para `/inicio` com a família nova escolhida |
| `AcceptInvitationPage.test.tsx` | `anonymousPasskeyPath` | anônimo, nome preenchido → `POST /accept` com `newUser`, depois a tela da passkey; `registerPasskey` simulado dá certo → `/inicio` |
| `AcceptInvitationPage.test.tsx` | `youthHasNoGoogle` | preview de Jovem → sem "Entrar com Google" |
| `AcceptInvitationPage.test.tsx` | `goneMessage` | 410 → a mensagem de convite que não vale mais, e o token sai do `sessionStorage` |
| `AcceptInvitationPage.test.tsx` | `googleSavesReturn` | "Entrar com Google" → `saveReturnPath('/convite')` e o token continua no `sessionStorage` |
| `HomePage.test.tsx` | `recoveryNoticeWithin7Days` | `recoveredAt` de 2 dias → o aviso; fechar → não volta; 8 dias → sem aviso |
| `GettingStartedCard.test.tsx` | `inviteItem` | Dona → "Convidar a família"; `familyInvited` → "Feito"; Leitor → sem o item |
| `MorePage.test.tsx` | `switcherWithTwoFamilies` | duas famílias → "Trocar de família"; escolher → `selectWorkspace` com o id |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/members src/features/dashboard src/app`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga "Telas". Todas as rotas novas com `lazy()`; `features/members/register.ts` registra as
mensagens.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde, carga inicial ≤ 200 KB.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): family screen, invitation links, accepting with Google or a passkey, and recovery"
```

---

### Task 14: "Quem", titular e a visão do Jovem no web

**Files:**
- Create: `web/src/features/transactions/MemberPicker.tsx`, `MemberPicker.test.tsx`
- Create: `web/src/features/accounts/OwnerPicker.tsx`, `OwnerPicker.test.tsx`
- Create em `web/src/features/transactions/`: `TransactionRow.test.tsx`, `EditTransaction.test.tsx`
- Modify em `web/src/features/transactions/`: `ContextSheet.tsx`, `NewTransactionPage.tsx`,
  `TransactionForm.tsx`, `TransactionRow.tsx`, `TransactionDetailSheet.tsx`, `EditTransaction.tsx`,
  `TransactionsPage.tsx`, `listItems.ts`, e os testes `NewTransactionPage.test.tsx` e
  `TransactionsPage.test.tsx`
- Modify: `web/src/app/MorePage.tsx` e `MorePage.test.tsx` (criado na Task 13)
- Modify em `web/src/features/accounts/`: `AccountForm.tsx`, `AccountsPage.tsx`
- Modify: `web/src/features/dashboard/HomePage.tsx`, `DashboardPage.tsx` (rótulo do Jovem)
- Modify: `web/src/shared/offline/outbox.ts` (o campo `memberId` opcional no item)
- Modify: `web/src/shared/api/endpoints.ts`, `mutations.ts`, `hooks.ts` (`useTransaction(id)`),
  `web/src/mocks/handlers.ts`

**Interfaces:**
- Consumes: `memberId` opcional e `GET …/transactions/{id}` (Task 5); `PUT …/accounts/{id}/owner`
  (Task 5); o escopo do Jovem (Task 6); `useMembers` e `memberName` de
  `features/members/memberName.ts` (Task 13).
- Produces: `useTransaction(id: string)`; `useSetAccountOwner()`.

**Como fica:**
- "Quem": o `ContextSheet` do lançamento ganha "Quem" com os membros ativos (o padrão é "Você").
  O Jovem não vê o campo. O item da fila guarda `memberId` só quando a pessoa escolheu outro.
- Lista e detalhe mostram "Quem" pelo `memberName`; com membro removido, o nome e "(saiu)".
- Editar abre com `useTransaction(id)` (dados frescos do servidor) em vez do item da lista: se
  responder 409 `transaction.deleted`, mostra "Este lançamento foi excluído"; se responder 404,
  "Este lançamento não existe mais" e atualiza a lista.
- Contas: o formulário mostra "Titular" (opcional, "Ninguém" por padrão); mudar numa conta
  existente chama `PUT …/owner`. A lista mostra o titular embaixo do nome.
- Jovem: sem "Contas" e "Categorias" no Mais (só leitura das próprias contas no Início); o Início
  mostra "Sua carteira"; a transferência de uma conta fora do escopo mostra "Conta da família" no
  lugar do nome.

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `MemberPicker.test.tsx` | `defaultsToYou` | sem escolha → "Você" e nenhum `memberId` no item |
| `MemberPicker.test.tsx` | `listsOnlyActive` | removidos fora da lista |
| `NewTransactionPage.test.tsx` | `savesChosenMember` | escolher "Ana" → o item da fila e o `POST` levam o `memberId` dela |
| `NewTransactionPage.test.tsx` | `youthHasNoMemberPicker` | Jovem → sem o campo "Quem" |
| `TransactionRow.test.tsx` | `removedMemberName` | `memberId` de removido com apelido "Tio" → "Tio (saiu)"; sem apelido nem nome → "Ex-membro" |
| `EditTransaction.test.tsx` | `loadsFreshCopy` | a lista tem R$ 10,00 e o `GET` por id devolve R$ 12,00 → o formulário abre com R$ 12,00 |
| `EditTransaction.test.tsx` | `deletedMessage` | `GET` por id → 409 `transaction.deleted` → "Este lançamento foi excluído" |
| `OwnerPicker.test.tsx` | `setsAndClearsOwner` | escolher "Leo" → `PUT` com o id; "Ninguém" → `PUT` com `null` |
| `TransactionsPage.test.tsx` | `familyAccountLabel` | Jovem com a transferência da mesada → "Conta da família → Carteira de Leo" |
| `MorePage.test.tsx` | `youthMenu` | Jovem → sem "Contas" e "Categorias" |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/transactions src/features/accounts src/app`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde, carga inicial ≤ 200 KB (o `MemberPicker` vive no `ContextSheet`, que está no
caminho do `/lancar`; se pesar, ele entra com `lazy()` ao abrir a folha).

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): pick who spent, show and edit account owners, and the youth view"
```

---

### Task 15: Módulos, Privacidade e exclusão pendente no web

**Files:**
- Create em `web/src/features/modules/`: `ModulesPage.tsx`, `moduleCatalog.ts`,
  `messages.pt-BR.ts`, `register.ts`, `ModulesPage.test.tsx`
- Create em `web/src/features/privacy/`: `PrivacyPage.tsx`, `ActivityPage.tsx`, `ConsentList.tsx`,
  `ExportButtons.tsx`, `DeleteAccountSheet.tsx`, `DeleteFamilySheet.tsx`, `download.ts`,
  `messages.pt-BR.ts`, `register.ts`, e os testes
- Create: `web/src/app/PendingDeletionPage.tsx`, `PendingDeletionPage.test.tsx`
- Modify: `web/src/app/routes.tsx` (`/mais/modulos`, `/mais/privacidade`,
  `/mais/privacidade/atividade`), `MorePage.tsx`, `SessionLayout.tsx` (desvio para a tela de
  exclusão pendente), `WorkspaceLayout.tsx`
- Modify: `web/src/shared/api/endpoints.ts`, `mutations.ts`, `hooks.ts`, `web/src/mocks/handlers.ts`

**Interfaces:**
- Consumes: as rotas das Tasks 7 a 11; `useReauth` e `registerModule` (Task 12).
- Produces: `downloadZip(path: string, fallbackName: string): Promise<void>` em `download.ts`
  (POST com CSRF, lê o `filename` do `Content-Disposition`, cria um `Blob`, clica num `<a
  download>` temporário e revoga a URL).

**Telas:**
- `/mais/modulos`: "Núcleo" sempre ligado ("Gastos, receitas, contas e painel"), e os quatro
  opcionais com a descrição de uma linha, a chave desabilitada e o selo "Em breve" (D14). Leitor e
  Jovem veem a tela sem as chaves. O teste usa um catálogo com um módulo `available` para provar
  que a chave liga, chama o `PUT` e reflete o resultado.
  - Planejamento: "Orçamentos, metas e a reserva de emergência."
  - Indicadores: "Selic, IPCA, dólar e outros números da economia."
  - Conquistas: "Metas pequenas e marcos, sem pressão para gastar."
  - Projetos: "Renda extra e pequenos negócios, separados das contas da casa."
- `/mais/privacidade`: "Baixar meus dados"; "Baixar os dados da família" (só Dono); "Histórico de
  acessos" (link); "Consentimentos" (`ConsentList`, com "Revogar" quando `canRevoke` e um diálogo
  que avisa que o Jovem sai da família); "Excluir a família" (só Dono, `DeleteFamilySheet`);
  "Excluir minha conta" (`DeleteAccountSheet`). As ações passam por `useReauth`. Offline, tudo
  desabilitado com "Sem conexão".
  - `DeleteAccountSheet`: explica os 30 dias e o que some; com 409 `account.sole-owner`, mostra
    "Você é o único Dono de uma família. Passe a família para outra pessoa ou exclua a família
    antes." com link para `/mais/familia`.
  - `DeleteFamilySheet`: pede para digitar o nome da família para confirmar.
- `/mais/privacidade/atividade` (`ActivityPage`): lista com data, a ação em texto ("Entrou com
  passkey", "Mudou o papel de Ana para Leitor"…), o aparelho ("Chrome no Android") e quem fez.
- `PendingDeletionPage`: quando `me.user.deletionScheduledFor` existe, o `SessionLayout` mostra só
  esta tela: "Sua conta será excluída em {date}", "Cancelar exclusão", "Baixar meus dados" e
  "Sair".
- Família pendente (Dono): o Início dessa família mostra só "Esta família será excluída em {date}",
  com "Cancelar exclusão" e "Baixar os dados da família".

**Textos** (chaves em `modules.*`, `privacy.*`, `activity.*`; os principais):
- `modules.comingSoon`: "Em breve"; `modules.core`: "Núcleo"
- `privacy.exportMine`: "Baixar meus dados"; `privacy.exportFamily`: "Baixar os dados da família"
- `privacy.soleOwner`: "Você é o único Dono de uma família. Passe a família para outra pessoa ou exclua a família antes."
- `privacy.pending`: "Sua conta será excluída em {date}."; `privacy.cancelDeletion`: "Cancelar exclusão"
- `activity.auth.signed_in.PASSKEY`: "Entrou com passkey"; `activity.auth.signed_in.GOOGLE`: "Entrou com Google"
- `activity.device`: "{browser} no {os}"
- Cada `action` da D18 tem uma frase; ação desconhecida mostra "Alteração na conta".

- [ ] **Step 1: Escrever os testes (falhando)**

| Arquivo | Teste | Esperado |
|---|---|---|
| `ModulesPage.test.tsx` | `comingSoonDisabled` | os quatro com "Em breve" e chave desabilitada |
| `ModulesPage.test.tsx` | `availableModuleToggles` | catálogo de teste com um `available` → a chave liga, o `PUT` sai e o estado reflete |
| `ModulesPage.test.tsx` | `viewerHasNoSwitches` | Leitor → sem chaves |
| `ExportButtons.test.tsx` | `downloadsZip` | clicar → `POST /v1/me/export`, um `<a download>` com o `filename` do cabeçalho |
| `ExportButtons.test.tsx` | `familyOnlyForOwner` | Adulto → sem "Baixar os dados da família" |
| `ExportButtons.test.tsx` | `offlineDisabled` | offline → os dois desabilitados |
| `DeleteAccountSheet.test.tsx` | `soleOwnerMessage` | 409 `account.sole-owner` → a mensagem e o link para `/mais/familia` |
| `DeleteAccountSheet.test.tsx` | `schedulesAfterReauth` | 403 `auth.reauthentication-required` e depois 202 → a tela de exclusão pendente |
| `DeleteFamilySheet.test.tsx` | `needsTypedName` | botão desabilitado até digitar o nome exato |
| `ConsentList.test.tsx` | `revokeWarns` | "Revogar" → o diálogo avisa que o Jovem sai; confirmar → `DELETE` |
| `ActivityPage.test.tsx` | `rendersActions` | `auth.signed_in` com `PASSKEY`, `Chrome` e `Android` → "Entrou com passkey" e "Chrome no Android"; ação desconhecida → "Alteração na conta" |
| `PendingDeletionPage.test.tsx` | `cancelRestores` | "Cancelar exclusão" → `DELETE /v1/me/deletion`, e o app volta ao Início |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features/modules src/features/privacy src/app`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Siga "Telas". Todas as rotas novas e a tela de exclusão pendente com `lazy()`.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web typecheck; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde, carga inicial ≤ 200 KB.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): modules screen, privacy center with export, consents, activity and account deletion"
```

---

### Task 16: E2E e acessibilidade

**Files:**
- Create: `web/e2e/core-without-modules.spec.ts`, `web/e2e/family.spec.ts`, `web/e2e/privacy.spec.ts`
- Modify: `web/e2e/a11y.spec.ts`, `web/e2e/support.ts`, `web/e2e/passkey.spec.ts` (passa a usar o
  `addVirtualAuthenticator` do `support.ts` no lugar do código CDP que repete)

**Interfaces:**
- Consumes: tudo das Tasks 1 a 15; o ambiente E2E do Plano 3 (`compose.e2e.yaml`, perfil `e2e`,
  mock-oauth2, Playwright em pt-BR e `America/Fortaleza`).
- Produces: três specs novos, o `a11y.spec.ts` com as telas novas e, em `support.ts`:
  - `addVirtualAuthenticator(page)`: liga o autenticador virtual do CDP (`WebAuthn.enable` e
    `WebAuthn.addVirtualAuthenticator` com `protocol: 'ctap2'`, `transport: 'internal'`,
    `hasResidentKey`, `hasUserVerification` e `isUserVerified` verdadeiros);
  - `setModules(page, enabled: boolean)`: `PUT` nas quatro chaves pela API, com o CSRF da página.
  - `passkey.spec.ts` chama esse `addVirtualAuthenticator` em vez do seu próprio.

**Specs:**
- `core-without-modules.spec.ts`, `coreWorksWithEveryModuleOff` (critério S2): entrar, criar a
  família, `setModules(false)`, conferir em `/mais/modulos` que os quatro estão desligados, e
  percorrer: lançar gasto, receita e transferência; ver `/lancamentos`; editar e excluir com
  "Desfazer"; abrir `/painel`; criar uma conta e uma categoria; abrir Família, Privacidade e
  Atividade. Nenhum erro no console, nenhuma resposta 5xx e nenhuma tela de erro.
- `family.spec.ts` (dois contextos de navegador):
  - `adultJoinsWithGoogle`: a Dona convida "Bia" como Adulta; o segundo contexto abre o link,
    entra com o Google (outro usuário do mock) e cai no Início da família; a Dona vê Bia em
    Família;
  - `youthJoinsWithPasskey`: a Dona convida "Leo" como Jovem, marcando o consentimento; o segundo
    contexto, com o autenticador virtual, aceita com o nome, cria a passkey e vê só "Carteira de
    Leo"; a Dona transfere R$ 50,00 da conta dela para a carteira; o Jovem lança um gasto de
    R$ 10,00 e vê o saldo R$ 40,00; a Dona vê o gasto com "Quem" = Leo;
  - `inviteUsedTwice`: abrir o mesmo link num terceiro contexto depois do aceite → "Este convite
    não vale mais";
  - `tokenNotInHistory`: depois de abrir o link, `page.url()` não contém o token.
- `privacy.spec.ts`:
  - `exportMyData`: em Privacidade, "Baixar meus dados" → o download termina com nome
    `liberdade-meus-dados-*.zip`;
  - `deleteAndCancel`: um Adulto (convidado no início do teste) pede para excluir a conta,
    confirma com o Google, vê "Sua conta será excluída em …", cancela e volta ao Início;
  - `soleOwnerBlocked`: a única Dona tenta excluir a conta → a mensagem de único Dono;
  - `signOutPropagates`: duas abas no mesmo contexto; sair na primeira → a segunda vai para
    `/entrar`.
- `a11y.spec.ts`: acrescentar `/mais/familia` (com o `MemberSheet` aberto),
  `/mais/familia/convidar` (com o link gerado), `/convite` (anônimo, com o preview), `/mais/modulos`,
  `/mais/privacidade` (com a folha de excluir conta aberta), `/mais/privacidade/atividade`, o
  `ReauthDialog` e a tela de exclusão pendente, nos dois temas (sem violação `serious` ou
  `critical`).
- Limite de 20 entradas por minuto por IP: os specs novos reaproveitam a sessão com
  `storageState` sempre que o teste não é sobre entrar, e o `playwright.config` continua com um
  worker. Se a soma de entradas da suíte passar de 20 num minuto, o `support.ts` espera a janela
  virar antes de entrar de novo, em vez de mudar o limite.

- [ ] **Step 1: Escrever os specs**

Como em "Specs".

- [ ] **Step 2: Subir o ambiente e rodar**

Run: `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait`
Run: `corepack pnpm --filter web e2e`
Expected: na primeira rodada, falhas só onde faltar ajuste de seletor ou espera; nenhum 5xx.

- [ ] **Step 3: Ajustar até passar**

Corrija seletores e esperas nos specs. Defeito de produto vira correção na task dona do código,
num commit `fix:` separado, com o teste que o pega.

- [ ] **Step 4: Rodar tudo**

Run: `corepack pnpm --filter web e2e`
Expected: todos os specs passam, os novos e os do Plano 3.

- [ ] **Step 5: Derrubar o ambiente e conferir**

Run: `docker compose -f compose.yaml -f compose.e2e.yaml down`
Run (em `api/`): `./gradlew build`
Run: `corepack pnpm --filter web lint; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build; corepack pnpm --filter web check:chunks`
Expected: tudo verde.

- [ ] **Step 6: Commit**

```bash
git add web/e2e
git commit -m "test(e2e): core with every module off, invitations, youth by passkey, export and deletion"
```
