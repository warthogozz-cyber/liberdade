# Liberdade · Plano 2: Entrar e lançar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Uma pessoa nova entra com Google e encontra prontas a família, a conta "Carteira" e as
categorias padrão. Depois salva o primeiro gasto em menos de 60 s, inclusive sem sinal. Isso é o
critério S1 e cobre as US-01, US-02, US-03 e US-05.

**Architecture:** A API ganha três módulos Spring Modulith:
- `identity`: login com Google (OIDC), passkeys e sessão no banco.
- `workspace`: família, `/v1/me` e a guarda de acesso.
- `ledger`: contas, categorias, lançamentos e painel.

O web fala com a API por um cliente tipado gerado do OpenAPI e guarda o cache do TanStack Query no
IndexedDB. Todo gasto novo entra numa fila Dexie, que envia em ordem com `Idempotency-Key`. Um E2E
com Playwright roda contra o Compose, com um provedor OIDC de teste no lugar do Google.

**Tech Stack:**
- API: Spring Security 7 (OAuth2 Client e WebAuthn), Spring Session JDBC, `JdbcClient`, Flyway,
  PostgreSQL 18, springdoc-openapi 3.2.0.
- Web: TanStack Query com persistência, Dexie 4.4.7, openapi-fetch, openapi-typescript 7.13.0,
  MSW 3.0.3, Radix, @simplewebauthn/browser, vite-plugin-pwa, react-intl.
- Ferramentas: Playwright, @axe-core/playwright, fake-indexeddb, mock-oauth2-server 6.1.3.
- GitHub: novo job `e2e` no CI; o preview passa a rodar com MSW.

**Spec:**
- [PRD](../../prd.md) (US-01, US-02, US-03, US-05 e seção 8)
- [Arquitetura](../../architecture.md)
- [Modelo de dados](../../data-model.md)
- [Design system](../../design-system.md)
- [Wireframes](../../wireframes.md) (telas 1, 2, 3, 4 e 6)
- [Roadmap](../../roadmap.md) (seção "Fase 1 — MVP", v0.1 Núcleo)
- [ADRs 0001–0013](../../adr/README.md)

## Global Constraints

- **Idiomas:** comunicação, UI, documentação de usuário, README, CONTRIBUTING e textos de PR em
  português do Brasil. Código, commits, nomes técnicos e comentários de código em inglês.
- **Commits e títulos de PR:** Conventional Commits.
  - Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Descrições de PR terminam com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Dados pessoais:** nenhum dado pessoal real (nome, cidade, e-mail) em arquivo versionado. Testes
  usam só nomes inventados ("Ana Teste") e domínios `example.com`.
  - `PROMPT_BIG_BRAIN.md` nunca é commitado. Ele fica em `.git/info/exclude` e não entra no
    `.gitignore`.
  - Antes de cada commit, `git grep -nIiE -f .git/info/personal-data-patterns` não retorna nada.
    Esse arquivo de padrões existe só na máquina do mantenedor (dentro de `.git/`, nunca
    versionado), porque escrever os padrões no repositório já seria expor o dado.
- **Segredos:** nada de segredo em arquivo. Senhas e tokens ficam só em `.env` (ignorado) ou em
  GitHub secrets. Quem digita token é o mantenedor; o agente nunca digita credenciais. Os valores
  fixos do provedor de teste (`liberdade-e2e` / `e2e-secret`) não são segredo: só valem contra o
  mock local.
- **Senhas:** o app nunca pede nem guarda senha. Do Google pede só `openid`, `email` e `profile`.
  Guarda só `sub`, nome e e-mail. Nenhum token do Google é guardado.
- **Sessão (ADR-0005):**
  - O cookie é `__Host-sid` (HttpOnly, Secure, SameSite=Lax) e expira após 30 dias sem uso.
  - Todo POST exige CSRF.
  - O CORS só vale para a origem do web, com credenciais.
  - A reautenticação após 10 minutos (US-01) chega com a primeira ação sensível (excluir conta,
    mudar papéis, exportar dados). Nenhuma delas existe neste plano.
- **Isolamento (ADR-0008):** toda rota sob `/v1/workspaces/{ws}` passa pela guarda, que nega por
  padrão. Todo método de repositório recebe `workspaceId`. As FKs entre tabelas da família são
  compostas (`workspace_id`, `id`).
- **Acesso a dados (ADR-0012):** `JdbcClient`, sem JPA.
- **Dinheiro:** nunca em ponto flutuante.
  - Na API, `BigDecimal` dentro de `Money`.
  - No JSON, texto: `{"amount":"1234.56","currency":"BRL"}`.
  - No front, `bigint` em centavos.
- **Erros da API:** RFC 9457 (`application/problem+json`), com `code` estável e `detail` em pt-BR.
  Nunca expõem stack trace, nome de classe nem mensagem de exceção.
- **Valores fora do domínio:** mensagens de exceção e logs nunca contêm valores monetários nem
  descrições de lançamentos.
- **Fronteiras de módulo:** valem na API (Spring Modulith `verify()`) e no web (ESLint). Um módulo
  do núcleo nunca depende de um módulo opcional.
- **Cobertura:** domínio e `shared` ≥ 90% de linhas; geral ≥ 80%. O build falha abaixo disso.
- **GitHub Actions:**
  - Toda action é fixada por SHA de 40 caracteres, com comentário `# vX.Y.Z`.
  - O workflow começa com `permissions: {}`, e cada job declara só o que usa.
- **Dependências:** só com licença compatível com AGPL-3.0.
- **Acessibilidade:** WCAG 2.2 AA.
- **Terceiros:** nenhuma chamada a serviço de terceiros no app, com uma exceção: o login com Google
  (OIDC). A Inter continua auto-hospedada.
- **Versões:** são as pesquisadas em 2026-11-02. Onde o plano diz "a mais recente", use a última
  versão estável publicada em 2026-10-26 ou antes. Registre no PR toda versão escolhida e todo
  desvio.
- **Ações externas:** fazer push, abrir PR, mesclar ou mudar configurações exige confirmação do
  mantenedor na hora.

### Pré-requisitos locais

- Git, `gh` autenticado, Java 21+ para rodar o Gradle (a toolchain baixa o JDK 25), Node 24 com
  corepack.
- Docker Desktop em execução, usado pelo Testcontainers, pelo Compose e pelo E2E.
- O pnpm não está instalado globalmente: use sempre `corepack pnpm …`.
- Navegador do Playwright, uma vez, depois da Task 12:
  `corepack pnpm --filter web exec playwright install chromium`.
- Não é preciso ter conta de desenvolvedor no Google. O caminho padrão usa o provedor de teste do
  Compose. Um cliente OAuth real é opcional, e quem o cria e digita as credenciais é o mantenedor.

## Entrega

- **PR 1** (docs, branch `docs/plan-2`): sem código. Leva:
  - este plano;
  - o README dos planos, com o Plano 1 "Concluído" e o Plano 2 "Em execução" e com link;
  - as notas sobre o jqwik nos ADRs 0002, 0006, 0009 e 0013;
  - no modelo de dados, as tabelas de passkey e a chave única das categorias com `kind` (D30);
  - na arquitetura, a regra de visibilidade dos módulos como o código já faz: o pacote raiz é
    público e o subpacote `internal` é privado.
- **PR 2** (código, branch `feat/sign-in-first-expense`): Tasks 1 a 12, um commit por task.
  - O merge só acontece com os seis checks obrigatórios verdes.
  - O novo check `e2e` vira obrigatório só se o mantenedor confirmar.
  - A descrição registra as versões escolhidas e os desvios.
- O Plano 2 vira "Concluído" no README dos planos no primeiro PR do Plano 3.

### Itens herdados do Plano 1

| Item | Onde |
|---|---|
| `Money.toString` expõe o valor | Task 1 |
| `HandlerMethodValidationException` → 422 e `requireNonNullElse` | Task 1 |
| `ProblemHandler` não trata as exceções do Spring Security | Task 3 |
| `appendDigit` aceita centavos negativos | Task 2 |
| Ids de mensagem sem tipo | Task 2 |
| Gate de cobertura do web só em `src/shared/money/**` | Task 2 (passa a `src/shared/**`) |
| Redação sobre o jqwik nos ADRs 0006 e 0009 (e 0002) | PR 1 |

## Decisões deste plano

O spec deixa estes pontos em aberto, e o plano decide:

- **D1.** "Novo gasto" registra só gasto. Receita e transferência chegam no Plano 3.
  - O teclado tem a tecla "00".
  - "Quem gastou" fica fixo em "Eu" até o Plano 4.
  - Data e conta mudam numa folha aberta pela linha de contexto.
- **D2.** A tela Entrar sai sem os links "Privacidade · Termos". Eles entram com a política, no
  Plano 5.
- **D3.** O selo de item na fila diz "Aguardando sinal" (o wireframe abrevia para "Aguardando").
- **D4.** A navegação inferior tem Início, Lançamentos, (+) Novo gasto e Mais.
- **D5.** A saudação usa o primeiro nome, isto é, a primeira palavra de `displayName`.
- **D6.** O Início mostra:
  - os gastos do mês e o saldo total;
  - a dica de contas;
  - "Primeiros passos", só com o card da passkey;
  - uma linha com os itens que precisam de atenção.
- **D7.** O sobrenome do Google (`family_name`) não é gravado. Ele vem do token, fica na sessão e
  só sugere o nome da família. Sem ele, a sugestão é "Minha família".
- **D8.** `display_name` é gravado no primeiro login e não muda depois. `email` e `last_used_at`
  são atualizados a cada login.
- **D9.** A API pública do `identity` é `CurrentUser` e `Passkeys`. `/v1/me` mora no `workspace`,
  que já depende do `identity`.
- **D10.** O limite de login é de 20 tentativas por minuto por IP, em janela fixa.
  - Nos caminhos de navegação, redireciona para `/entrar?erro=limite`.
  - Nos de XHR, responde 429.
- **D11.** Há três perfis: `dev`, `test` e `e2e`. O provedor de teste usa login interativo, e cada
  teste E2E entra com um `sub` único.
- **D12.** Os formulários não usam React Hook Form nem Zod: são dois campos.
- **D13.** O papel YOUTH é negado em toda rota até o Plano 4 (consentimento parental).
- **D14.** Nesta versão, cada pessoa tem uma família só. Um segundo `POST /v1/workspaces` responde
  409.
- **D15.** A fila:
  - espera 6 s antes de enviar, para caber o "Desfazer" de 5 s;
  - é separada por `userId`;
  - usa Web Locks, para que só uma aba envie.
- **D16.** As categorias padrão ganham emoji e ordem; Mercado vem primeiro.
- **D17.** Nenhum token do Google é guardado: o repositório de clientes autorizados não faz nada.
- **D18.** No gerenciador de passkeys do aparelho, a passkey aparece com o e-mail (ou o nome). No
  banco, `user_entities.name` guarda `users.id`.
- **D19.** Sair precisa de internet. Quando há itens na fila, o app avisa que eles serão apagados
  deste aparelho.
- **D20.** Não há paginação: nesta versão, a lista do mês cabe numa resposta.
- **D21.** `/v1/me` traz também `passkeyCreatedAt`, `timezone`, `currency` e `membershipId`.
- **D22.** `POST /v1/workspaces` responde 201 sem `Location`.
- **D23.** A API aceita `EXPENSE` e `INCOME`. `TRANSFER` responde 422
  `transaction.kind-unsupported` até o Plano 3.
- **D24.** "Corrigir" um item recusado cria um id novo, porque o antigo nunca foi aceito.
- **D25.** Os totais do Início somam só os itens "Aguardando sinal". Os itens "Precisa de atenção"
  não entram.
- **D26.** O limite usa o `remoteAddr`. Os cabeçalhos de proxy (`X-Forwarded-For`) entram com a
  AWS, no Plano 5.
- **D27.** O worker do MSW e o service worker do PWA nunca são registrados juntos.
- **D28.** O fuso da família vem do navegador na criação
  (`Intl.DateTimeFormat().resolvedOptions().timeZone`). Um fuso desconhecido, ou ausente, vira
  `America/Fortaleza`, o padrão do spec. Trocar o fuso fica para a tela de ajustes da família.
- **D29.** O ledger semeia a carteira e as categorias com um `@EventListener` síncrono para
  `WorkspaceCreated`, na mesma transação. Não usa `@ApplicationModuleListener` nem o registro de
  eventos: a família nasce com a carteira e as categorias, ou não nasce.
- **D30.** O nome da categoria é único por `(workspace_id, kind, parent_id, name)` entre as não
  arquivadas, porque "Outros" existe nos dois tipos. O modelo de dados muda junto (PR 1).
- **D31.** As "6 mais usadas" contam os lançamentos dos últimos 90 dias: `occurred_on` a partir de
  hoje − 89 dias, no fuso da família. Gastos com data futura também contam. O empate segue a ordem
  padrão e depois o nome.
- **D32.** O contrato OpenAPI sai do springdoc (`org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0`,
  sem UI).
  - `/v3/api-docs` existe só nos perfis `dev` e `test`; nos outros, a rota responde 404.
  - O contrato sai em OpenAPI 3.0, com servidor fixo `/` e chaves ordenadas, para não mudar de
    máquina para máquina.
  - `api/openapi.json`, versionado, é a fonte dos tipos do web. Um teste da API falha quando o
    arquivo fica para trás, e o CI do web falha quando `schema.ts` fica para trás.
  - Nos tipos gerados, todo campo de resposta é obrigatório (`--properties-required-by-default`). Um
    campo que pode vir nulo leva `@Schema(nullable = true)` ao lado do `@Nullable` e vira `| null`
    no TypeScript.
- **D33.** Os mocks do MSW ficam em `web/src/mocks/`. Só o `main.tsx` e os arquivos de teste
  importam `@/mocks`; o ESLint barra o resto.
- **D34.** "Desfazer" no aviso "Gasto salvo" tira o gasto da fila enquanto ele não saiu. Se o envio
  já começou ou terminou, o aviso diz que não dá mais para desfazer. Isso desvia do wireframe 4,
  que também apaga o gasto já salvo: apagar lançamento chega no Plano 3, junto com a rota
  `DELETE`. O aviso fica 5 s e pausa com o mouse ou o foco em cima.

## Review Focus

1. **Toque duplo** em "Começar" ou "Salvar" gera uma família e um gasto, nunca dois (Tasks 5, 6,
   10 e 11).
2. **Outra pessoa entra no mesmo aparelho.** A fila de quem saiu não aparece nem é enviada com a
   sessão nova, e os itens esperam o dono voltar (Tasks 8 e 9).
3. **Login cancelado ou negado no Google** volta para a tela Entrar com a mensagem, nunca para uma
   página da API (Tasks 3, 10 e 12).
4. **Gasto perto da meia-noite ou na virada do mês** usa a data local da família, não a UTC
   (Tasks 6, 9 e 11).
5. **Duas abas abertas:** o envio é exclusivo e em ordem, sem duplicar (Task 9).

---

### Task 1: Correções herdadas na API

**Files:**
- Modify: `api/src/main/java/br/com/liberdadefamilia/shared/Money.java`
- Modify: `api/src/main/java/br/com/liberdadefamilia/shared/internal/ProblemHandler.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/shared/MoneyTest.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/shared/internal/ProblemHandlerTest.java`

**Interfaces:**
- Consumes: `Money` e `ProblemHandler` do Plano 1.
- Produces:
  - `Money.toString()` devolve `"Money[" + currency.getCurrencyCode() + "]"`. O valor nunca chega a
    log nem a mensagem.
  - `ProblemHandler` responde 422 `request.invalid` a `HandlerMethodValidationException`, com
    `errors: [{field, code}]`.
  - `static Map<String, String> fieldError(String field, @Nullable String code)`, package-private,
    monta cada item de `errors`. Um `code` nulo vira `"invalid"`, via
    `Objects.requireNonNullElse`. O mapeamento de erros de campo que já existe passa a usar esse
    método.

- [ ] **Step 1: Escrever os testes (falhando)**

```java
// MoneyTest
@Test
void toStringHidesAmount() {
    assertThat(Money.brl("12.34").toString()).isEqualTo("Money[BRL]").doesNotContain("12");
}
```

```java
// ProblemHandlerTest: new endpoint in ProblemProbeController. No @Validated on the class, so
// Spring MVC's built-in method validation throws HandlerMethodValidationException.
@GetMapping("/probe/param")
String param(@RequestParam @Min(1) int n) { return "ok"; }

@Test
void methodValidationIs422() {
    assertThat(mvc.get().uri("/probe/param?n=0")).hasStatus(422).bodyJson()
        .isLenientlyEqualTo("""
            {"code":"request.invalid","errors":[{"field":"n","code":"Min"}]}""");
}

@Test
void fieldErrorWithoutCodeIsInvalid() {
    assertThat(ProblemHandler.fieldError("name", null)).containsEntry("code", "invalid");
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*MoneyTest' --tests '*ProblemHandlerTest'`
Expected: FAIL na compilação (`fieldError` não existe).

- [ ] **Step 3: Implementar**

- `Money`: sobrescrever `toString()`.
- `ProblemHandler`: sobrescrever `handleHandlerMethodValidationException(…)`. Para cada
  `ParameterValidationResult`:
  - `field` é `getMethodParameter().getParameterName()`;
  - `code` é o último item de `getCodes()` de cada erro resolvível (`"Min"` em vez de
    `"Min.param.n"`).

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2.
Expected: PASS.

- [ ] **Step 5: Conferir na aplicação inteira**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api/src
git commit -m "fix(api): hide amounts in Money.toString and map method validation to 422"
```

---

### Task 2: Correções herdadas no web

**Files:**
- Modify: `web/src/shared/money/money.ts`
- Modify: `web/src/shared/money/index.ts`
- Test: `web/src/shared/money/money.test.ts`
- Create: `web/src/shared/i18n/formatjs.d.ts`
- Test: `web/src/shared/i18n/messages.test.ts`
- Modify: `web/vite.config.ts`

**Interfaces:**
- Consumes: `appendDigit`, `MAX_CENTS` e `messages` do Plano 1.
- Produces:
  - `appendDigit(cents, digit)` lança `RangeError` quando `cents < 0n`.
  - `appendDoubleZero(cents: bigint): bigint` implementa a tecla "00".
    - Devolve `cents * 100n` se couber em `MAX_CENTS`; senão, devolve `cents` sem mudança.
    - Lança `RangeError` para negativo.
    - É exportado pelo `index.ts`.
  - Ids de mensagem tipados. `formatjs.d.ts` declara
    `declare global { namespace FormatjsIntl { interface Message { ids: keyof typeof messages } } }`.
    Com isso, `defineMessage`, `<FormattedMessage>` e `intl.formatMessage` só aceitam chaves de
    `messages.pt-BR.ts`.

- [ ] **Step 1: Escrever os testes (falhando)**

`money.test.ts`:

| Chamada | Esperado |
|---|---|
| `appendDigit(-1n, 1)` | lança `RangeError` |
| `appendDoubleZero(0n)` | `0n` |
| `appendDoubleZero(12n)` | `1200n` |
| `appendDoubleZero(999_999_999n)` | `99_999_999_900n` |
| `appendDoubleZero(1_000_000_000n)` | `1_000_000_000n` (não cabe) |
| `appendDoubleZero(-1n)` | lança `RangeError` |

`messages.test.ts`:

```ts
import { defineMessage } from 'react-intl'

it('accepts only known message ids', () => {
  expect(defineMessage({ id: 'home.title' }).id).toBe('home.title')
  // @ts-expect-error unknown ids do not type-check
  defineMessage({ id: 'nope.missing' })
})
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/shared/money src/shared/i18n`
Expected: FAIL (`appendDoubleZero` não existe; `appendDigit(-1n, 1)` não lança).

Run: `corepack pnpm --filter web exec tsc -b`
Expected: FAIL com TS2578 (`Unused '@ts-expect-error' directive`).

- [ ] **Step 3: Implementar**

Fazer a guarda de negativos, `appendDoubleZero`, a exportação e `formatjs.d.ts`. No
`vite.config.ts`, trocar o limite `'src/shared/money/**'` por
`'src/shared/**': { lines: 90, functions: 90, branches: 90, statements: 90 }`. Se algum arquivo de
`src/shared` que já existe ficar abaixo de 90, os testes que faltam entram nesta task.

- [ ] **Step 4: Rodar e ver passar**

Run: os dois comandos do Step 2.
Expected: PASS, e `tsc -b` sem erros.

- [ ] **Step 5: Conferir no web inteiro**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build`
Expected: os três passam, e a cobertura está acima dos limites.

- [ ] **Step 6: Commit**

```bash
git add web/src/shared web/vite.config.ts
git commit -m "fix(web): guard money input against negatives and type message ids"
```

---

### Task 3: Identity: login com Google, sessão e segurança HTTP

**Files:**
- Modify: `api/build.gradle.kts`
- Delete: `api/src/main/resources/db/migration/.gitkeep`
- Create: `api/src/main/resources/db/migration/V1__identity_and_sessions.sql`
- Modify: `api/src/main/resources/application.properties`
- Create: `api/src/main/resources/application-dev.properties`
- Create: `api/src/main/resources/application-e2e.properties`
- Create: `api/src/test/resources/application-test.properties`
- Create: `api/src/main/java/br/com/liberdadefamilia/identity/package-info.java`
- Create: `api/src/main/java/br/com/liberdadefamilia/identity/CurrentUser.java`
- Create em `api/src/main/java/br/com/liberdadefamilia/identity/internal/`:
  - `IdentityProperties.java`
  - `SecurityConfig.java`
  - `UserAccounts.java`
  - `LiberdadeOidcUser.java`
  - `LiberdadeOidcUserService.java`
  - `NoOpAuthorizedClientRepository.java`
  - `SecurityProblemHandlers.java`
  - `CsrfController.java`
  - `CurrentUserArgumentResolver.java`
  - `IdentityWebConfig.java`
- Modify: `api/src/main/java/br/com/liberdadefamilia/shared/internal/ProblemHandler.java`
- Modify: `compose.yaml`, `.env.example` e `README.md`
- Create: `compose.e2e.yaml`
- Test: `api/src/test/java/br/com/liberdadefamilia/support/TestUsers.java`
- Test em `api/src/test/java/br/com/liberdadefamilia/identity/`:
  - `SecurityIT.java`
  - `LiberdadeOidcUserServiceIT.java`
  - `internal/CurrentUserArgumentResolverTest.java`
  - `internal/LiberdadeOidcUserTest.java`
- Modify: `HealthProbesIT.java` e `shared/internal/ProblemHandlerTest.java`

**Interfaces:**
- Consumes: `DomainException`, `ProblemHandler` e o bean `Clock` do Plano 1.
- Produces:
  - `package-info.java`: `@ApplicationModule(allowedDependencies = "shared")`.
  - `public record CurrentUser(UUID id, String displayName, @Nullable String familyNameHint)`.
    - Qualquer controller o recebe como parâmetro.
    - O resolver lê `UUID.fromString(authentication.getName())` e carrega o usuário com
      `UserAccounts.findActive`.
    - `familyNameHint` vem de `OidcUser.getFamilyName()`. No login por passkey, é `null`.
    - Sem autenticação: `AuthenticationCredentialsNotFoundException`.
    - Usuário inexistente ou com exclusão pedida: `InsufficientAuthenticationException`.
  - `UserAccounts` (bean em `internal`):
    - `UUID signInWithGoogle(String subject, String displayName, @Nullable String email)`.
      - Cada tentativa roda numa transação própria (`TransactionTemplate`).
      - Se a identidade já existe, atualiza `email` e `last_used_at`; `display_name` não muda (D8).
      - Se não existe, cria `users` e `auth_identities`.
      - Num `DuplicateKeyException` (dois primeiros logins ao mesmo tempo), tenta de novo uma vez,
        relendo a identidade.
    - `Optional<UserRecord> findActive(UUID id)` é vazio quando o usuário não existe ou pediu
      exclusão.
    - `record UserRecord(UUID id, String displayName, @Nullable String email)`.
  - `public class LiberdadeOidcUser extends DefaultOidcUser`.
    - Construtor: `(UUID userId, Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken, @Nullable OidcUserInfo userInfo)`.
    - `UUID userId()`. `getName()` devolve `userId.toString()`, que vira o `PRINCIPAL_NAME` da sessão.
    - É serializável, com `serialVersionUID`.
  - `LiberdadeOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate, UserAccounts accounts)`.
    - O bean usa `new OidcUserService()` como delegate.
    - `static String displayNameFrom(OidcUser user)` tenta, nesta ordem: `name`, `given_name`, a
      parte local do e-mail e, por fim, `"Você"`. O resultado é cortado em 200 caracteres.
  - `@ConfigurationProperties("liberdade") record IdentityProperties(Web web, WebAuthn webauthn)`,
    com `record Web(String origin)` e `record WebAuthn(String rpId, String rpName)`.
  - `GET /v1/csrf` responde `{"headerName":"X-XSRF-TOKEN","token":"…"}`.
  - `POST /logout` responde 204.
  - Test support:
    - `TestUsers(JdbcClient jdbc)`, com `UUID create(String displayName, @Nullable String email)`.
    - `static RequestPostProcessor signedIn(UUID userId, @Nullable String familyName)` usa
      `oidcLogin().oidcUser(new LiberdadeOidcUser(…))`.
    - As tasks 4 a 7 usam os dois.

**Regras de segurança** (`SecurityConfig`, um único `SecurityFilterChain`):

| Ponto | Valor |
|---|---|
| Rotas públicas | `/actuator/health`, `/actuator/health/**`, `/v1/csrf`, `/error`; o resto exige login |
| `oauth2Login` | `loginPage(origin + "/entrar")` só para desligar a página gerada; PKCE com `OAuth2AuthorizationRequestCustomizers.withPkce()`; `LiberdadeOidcUserService`; `NoOpAuthorizedClientRepository` (D17) |
| Sucesso do login | redireciona para `origin + "/"` (sempre, sem request cache: `NullRequestCache`) |
| Falha do login | redireciona para `origin + "/entrar?erro=login"` |
| Sem login / sem permissão | `SecurityProblemHandlers` implementa `AuthenticationEntryPoint` e `AccessDeniedHandler` e repassa a exceção ao `HandlerExceptionResolver`, que chega ao `ProblemHandler` |
| CSRF | `HttpSessionCsrfTokenRepository` com cabeçalho `X-XSRF-TOKEN` e `XorCsrfTokenRequestAttributeHandler` |
| CORS | origem só `liberdade.web.origin`; métodos GET, POST, OPTIONS; cabeçalhos `Content-Type`, `X-XSRF-TOKEN`, `Idempotency-Key`; credenciais; expõe `Retry-After`; `maxAge` 3600 |
| Cabeçalhos | CSP `default-src 'none'; frame-ancestors 'none'`; `Referrer-Policy: no-referrer`; `Permissions-Policy: camera=(), microphone=(), geolocation=()`; `nosniff`; HSTS |
| Logout | `POST /logout` com `HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)` |

**`ProblemHandler`**: passa a tratar as exceções do Spring Security (item herdado do Plano 1).

| Exceção | Status | `code` | `detail` |
|---|---|---|---|
| `AuthenticationException` | 401 | `auth.required` | "Entre para continuar." |
| `CsrfException` | 403 | `csrf.invalid` | "Sua sessão foi atualizada. Tente de novo." |
| `AccessDeniedException` (as demais) | 403 | `auth.forbidden` | "Você não tem permissão para isso." |

**Propriedades:**
- Base (`application.properties`), somando ao que já existe:
  - `spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}`
  - `spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}`
  - `spring.security.oauth2.client.registration.google.scope=openid,email,profile`
  - `liberdade.web.origin=${WEB_ORIGIN:http://localhost:5173}`
  - `liberdade.webauthn.rp-id=${WEBAUTHN_RP_ID:localhost}` e `liberdade.webauthn.rp-name=Liberdade`
  - `server.servlet.session.cookie.name=__Host-sid`, com `secure=true`, `http-only=true`,
    `same-site=lax` e `path=/`
  - `server.servlet.session.timeout=30d` e `spring.session.jdbc.initialize-schema=never`
- `application-test.properties` (em `src/test/resources`): `client-id=test-client` e
  `client-secret=test-secret`, sem `issuer-uri`. Assim vale o provedor comum `google` do Boot, sem
  rede.
- `application-dev.properties`: cliente `liberdade-e2e` / `e2e-secret` e
  `spring.security.oauth2.client.provider.google.issuer-uri=http://localhost:8090/google`.
- `application-e2e.properties`: o mesmo cliente, com estes endereços:
  - `issuer-uri=http://mock-oauth2:8090/google`, para a API dentro do Docker;
  - `authorization-uri=http://localhost:8090/google/authorize`, para o navegador.
- `build.gradle.kts`:
  - novas dependências: `spring-boot-starter-security-oauth2-client` e
    `spring-boot-starter-session-jdbc`;
  - em teste: `spring-boot-starter-security-test`, e também os starters `-test` dos dois novos, se
    o Boot 4.1.2 os publicar;
  - `systemProperty("spring.profiles.active", "test")` em `tasks.withType<Test>`;
  - `tasks.bootTestRun { systemProperty("spring.profiles.active", "dev") }`.

**`V1__identity_and_sessions.sql`:**
- `users`:
  - `id uuid PK DEFAULT uuidv7()`;
  - `display_name text NOT NULL`, com CHECK de 1 a 200 caracteres;
  - `email text NULL`;
  - `locale text NOT NULL DEFAULT 'pt-BR'`;
  - `deletion_requested_at timestamptz NULL`;
  - `created_at` e `updated_at timestamptz NOT NULL DEFAULT now()`;
  - `version bigint NOT NULL DEFAULT 0`.
- `auth_identities`:
  - `id uuid PK DEFAULT uuidv7()`;
  - `user_id uuid NOT NULL`, que referencia `users` com `ON DELETE CASCADE`;
  - `provider text NOT NULL CHECK (provider IN ('google'))`;
  - `subject text NOT NULL`;
  - `last_used_at timestamptz NOT NULL` e `created_at`;
  - `UNIQUE (provider, subject)`.
- `spring_session` e `spring_session_attributes` iguais ao `schema-postgresql.sql` do Spring
  Session JDBC: as colunas, os índices `IX1` a `IX3` e a FK com `ON DELETE CASCADE`.

- [ ] **Step 1: Spike do provedor de teste (sem commit)**

Crie `compose.e2e.yaml` só com o serviço `mock-oauth2`:
- imagem `ghcr.io/navikt/mock-oauth2-server:6.1.3`;
- porta `127.0.0.1:8090:8090`;
- ambiente `SERVER_PORT: "8090"` e `JSON_CONFIG: '{"interactiveLogin":true}'`.

Depois, confira:

1. `docker compose -f compose.yaml -f compose.e2e.yaml up -d mock-oauth2`.
2. `curl --resolve mock-oauth2:8090:127.0.0.1 http://mock-oauth2:8090/google/.well-known/openid-configuration`
   mostra `"issuer":"http://mock-oauth2:8090/google"`.
3. No navegador, abra
   `http://localhost:8090/google/authorize?client_id=liberdade-e2e&response_type=code&scope=openid&redirect_uri=http://localhost:9/cb&state=s&nonce=n-123`.
   Anote os nomes dos campos do formulário; o esperado é `username` e `claims`. Envie e copie o
   `code` da URL de retorno.
4. Troque o code:
   `curl --resolve mock-oauth2:8090:127.0.0.1 -u liberdade-e2e:e2e-secret -d grant_type=authorization_code -d code=<code> -d redirect_uri=http://localhost:9/cb http://mock-oauth2:8090/google/token`.
5. Decodifique o `id_token`. O esperado é:
   - `iss` = `http://mock-oauth2:8090/google`;
   - `nonce` = `n-123`;
   - `sub` = o usuário digitado.

O que fazer se algo não bater:

| Resultado | O que fazer |
|---|---|
| `iss` com `localhost` | O perfil `e2e` tira o override de `authorization-uri`. O Playwright (Task 12) abre o Chromium com `--host-resolver-rules=MAP mock-oauth2 127.0.0.1`. Registre o desvio no PR. |
| `nonce` ausente | Bloqueio: pare e reporte. Sem nonce, o login OIDC do Spring falha. |
| Campos com outros nomes | Use os nomes reais na Task 12 e registre-os no PR. |

Na Step 6, confira também se o Chrome grava o cookie `__Host-sid` em `http://localhost:8080`.
Se recusar, os perfis `dev` e `e2e` usam `LIBERDADE_SID` com `secure=false`, e o PR registra o
desvio.

- [ ] **Step 2: Escrever os testes (falhando)**

`SecurityIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, Testcontainers):

| Teste | Requisição | Esperado |
|---|---|---|
| `meRequiresSignIn` | `GET /v1/me` anônimo | 401 `application/problem+json`; `code` `auth.required`; `detail` "Entre para continuar."; sem `Location` |
| `noGeneratedLoginPage` | `GET /login` | 401 |
| `googleRedirectUsesPkceNonceAndMinimalScopes` | `GET /oauth2/authorization/google` | 302; `Location` começa com `https://accounts.google.com/o/oauth2/v2/auth` e contém `scope=openid%20email%20profile`, `code_challenge_method=S256`, `code_challenge=` e `nonce=` |
| `failedCallbackGoesBackToSignIn` | `GET /login/oauth2/code/google?error=access_denied&state=x` | 302 para `http://localhost:5173/entrar?erro=login` |
| `csrfEndpointGivesToken` | `GET /v1/csrf` | `headerName` = `X-XSRF-TOKEN`; `token` não vazio; `Set-Cookie` com `__Host-sid=`, `Path=/`, `Secure`, `HttpOnly` e `SameSite=Lax` |
| `sessionLasts30DaysIdle` | `GET /v1/csrf`, depois `select max_inactive_interval from spring_session` | `2592000` |
| `postWithoutCsrfIs403` | `POST /logout` sem token | 403 `csrf.invalid` |
| `logoutIs204` | `POST /logout` com sessão e `with(csrf())` | 204 |
| `corsAllowsOnlyWebOrigin` | `OPTIONS /v1/me` com `Origin: http://localhost:5173` e `Access-Control-Request-Method: POST` | 200; `Access-Control-Allow-Origin` = a origem; `Access-Control-Allow-Credentials: true` |
| (mesmo teste) | igual, com `Origin: https://evil.example` | 403 |
| `securityHeaders` | `GET /actuator/health` com `secure(true)` | CSP, `Referrer-Policy`, `X-Content-Type-Options: nosniff` e `Permissions-Policy` iguais à tabela acima; HSTS contém `max-age=31536000` |

`LiberdadeOidcUserServiceIT`: chama `loadUser` com um delegate falso que devolve um `OidcUser`
montado no teste.
- `firstSignInCreatesUserAndIdentity`: `sub` `g-1`, nome "Ana Teste", `ana@example.com` e
  `family_name` "Teste".
  - Cria uma linha em `users` e uma em `auth_identities`.
  - `getName()` é o id do usuário.
  - `getFamilyName()` é "Teste".
- `secondSignInReusesUser`: antes do segundo login, o teste põe `last_used_at` em
  `2000-01-01T00:00:00Z`. Depois, confere:
  - o mesmo id;
  - o e-mail novo gravado;
  - `display_name` sem mudança;
  - `last_used_at` depois de 2000-01-01.
- `displayNameFallsBack`: sem `name` nem `given_name` e com `bia@example.com`, o nome é "bia".
  Sem e-mail, é "Você".

`CurrentUserArgumentResolverTest` (Mockito, sem Spring):

| Caso | Esperado |
|---|---|
| Sem autenticação | `AuthenticationCredentialsNotFoundException` |
| Usuário inexistente | `InsufficientAuthenticationException` |
| Usuário com exclusão pedida (`findActive` vazio) | `InsufficientAuthenticationException` |
| `LiberdadeOidcUser` com `family_name` "Teste" | `CurrentUser(id, "Ana Teste", "Teste")` |
| Principal de passkey (`UsernamePasswordAuthenticationToken` com o id) | `familyNameHint` nulo |

`LiberdadeOidcUserTest.survivesJavaSerialization`: serializar e desserializar mantém `userId()` e
`getName()`.

`ProblemHandlerTest` passa a usar `@WebMvcTest(useDefaultFilters = false)`,
`@Import({ProblemHandler.class, ProblemProbeController.class})` e
`@AutoConfigureMockMvc(addFilters = false)`. Ganha três probes:
- um lança `BadCredentialsException` → 401 `auth.required`;
- um lança `MissingCsrfTokenException` → 403 `csrf.invalid`;
- um lança `AccessDeniedException` → 403 `auth.forbidden`.

Em `HealthProbesIT.envIsNotExposed`, o esperado passa a ser 401 `auth.required`.

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test`
Expected: FAIL (as classes do `identity` não existem).

- [ ] **Step 4: Implementar**

Faça as dependências, a migração, as propriedades, as classes do `identity` e o `ProblemHandler`,
seguindo as tabelas acima.

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test`
Expected: PASS, com `ApplicationModulesTest` verde.

- [ ] **Step 6: Compose, `.env.example` e README**

- `compose.e2e.yaml` ganha o serviço `api`, com:
  - `SPRING_PROFILES_ACTIVE: e2e`;
  - `depends_on: [mock-oauth2]`;
  - `restart: on-failure` (a descoberta OIDC falha se o mock ainda não subiu).
- `compose.yaml`, no serviço `api`: `GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-}` e
  `GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:-}`.
- `.env.example`: `GOOGLE_CLIENT_ID=` e `GOOGLE_CLIENT_SECRET=`, vazios.
- README, em "Como rodar":
  - Caminho recomendado: `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build` e
    `corepack pnpm --filter web dev`. O login de teste aceita qualquer nome de usuário e, se
    quiser, claims em JSON.
  - Google de verdade, opcional:
    - o mantenedor cria o cliente OAuth com redirect `http://localhost:8080/login/oauth2/code/google`;
    - põe as credenciais no `.env` e roda `docker compose up`;
    - sem elas, a API não sobe (o client id vazio falha logo no início).

Confira à mão:
1. Com o caminho recomendado no ar, abra `http://localhost:8080/oauth2/authorization/google`.
2. O formulário do mock aparece.
3. Ao enviar, o navegador volta para `http://localhost:5173/`.
4. As DevTools mostram o cookie da sessão em `localhost:8080`.

- [ ] **Step 7: Conferir na aplicação inteira**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add api compose.yaml compose.e2e.yaml .env.example README.md
git commit -m "feat(identity): sign in with Google over OIDC with JDBC sessions"
```

---

### Task 4: Passkeys e limite de tentativas de login

**Files:**
- Modify: `api/build.gradle.kts`
- Create: `api/src/main/resources/db/migration/V2__passkeys.sql`
- Create: `api/src/main/java/br/com/liberdadefamilia/identity/Passkeys.java`
- Create em `api/src/main/java/br/com/liberdadefamilia/identity/internal/`:
  - `PasskeyConfig.java`
  - `PasskeyUserDetailsService.java`
  - `FriendlyUserEntityRepository.java`
  - `JdbcPasskeys.java`
  - `LoginRateLimiter.java`
  - `LoginRateLimitFilter.java`
  - `RateLimitedException.java`
- Modify: `api/src/main/java/br/com/liberdadefamilia/identity/internal/SecurityConfig.java`
- Test em `api/src/test/java/br/com/liberdadefamilia/identity/`:
  - `PasskeyIT.java`
  - `LoginRateLimitIT.java`
  - `internal/FriendlyUserEntityRepositoryTest.java`
  - `internal/PasskeyUserDetailsServiceTest.java`
  - `internal/LoginRateLimiterTest.java`

**Interfaces:**
- Consumes: `UserAccounts.findActive`, `IdentityProperties`, `SecurityProblemHandlers`, `TestUsers` e
  `signedIn` (Task 3); o bean `Clock` e `DomainException` (Plano 1).
- Produces:
  - `public interface Passkeys { Optional<Instant> latestCreatedAt(UUID userId); }`. A
    implementação `JdbcPasskeys` devolve o maior `created` das credenciais cujo
    `user_entities.name` é `userId.toString()`, lido como `getTimestamp(…).toInstant()` (o mesmo
    que o repositório do Spring faz).
  - Endpoints do Spring Security (todos POST e com CSRF):
    - `/webauthn/register/options` (exige login) e `/webauthn/register`, com corpo
      `{"publicKey":{"credential":{…},"label":"…"}}`;
    - `/webauthn/authenticate/options` (anônimo) e `/login/webauthn`, que responde
      `{"redirectUrl":"/","authenticated":true}`.
  - Depois do login por passkey, a autenticação é uma `WebAuthnAuthentication` cujo `getName()` é
    o id do usuário. O `CurrentUserArgumentResolver` da Task 3 funciona sem mudança, com
    `familyNameHint` nulo.
  - `LoginRateLimiter(Clock clock, int limitPerMinute)`, com bean de limite 20 (D10).
    - `OptionalLong tryAcquire(String key)`: vazio quando a tentativa passa; senão, os segundos
      até a próxima janela, `60 - epochSecond % 60` (de 1 a 60).
    - Janelas fixas por minuto de época. Quando o mapa passa de 10 000 chaves, remove as de
      minutos anteriores.
    - `int size()`, package-private, só para teste.
  - `RateLimitedException` estende `DomainException`: 429, `rate.limited`, "Muitas tentativas.
    Espere um minuto e tente de novo."

**Como a passkey se liga ao usuário (D18):** o Spring procura a pessoa por
`authentication.getName()`, que é `users.id`, e no login devolve `user_entities.name` ao
`UserDetailsService`. Por isso `user_entities.name` guarda `users.id`. Já as opções enviadas ao
navegador precisam de um nome legível, que aparece no gerenciador de senhas do aparelho.
`FriendlyUserEntityRepository` decora o `JdbcPublicKeyCredentialUserEntityRepository`:

| Método | Comportamento |
|---|---|
| `findByUsername(usersId)` | Usuário ativo sem entidade: cria uma com id aleatório (`Bytes.random()`), `name` = `usersId` e `displayName` = `display_name`, e grava. Devolve uma cópia com `name` = e-mail (ou `display_name`, sem e-mail) e o mesmo id. Usuário inativo ou id malformado: repassa ao delegate, sem criar. |
| `findById(id)` | Devolve a entidade como está no banco (`name` = `users.id`). |
| `save(entity)` | Para um id que já existe, mantém o `name` gravado. |
| `delete(id)` | Repassa. |

**`PasskeyUserDetailsService`:** `loadUserByUsername(usersId)` usa `UserAccounts.findActive`.
Devolve um `UserDetails` próprio, com `getUsername()` = o id, `getPassword()` nulo (não há senha) e
a autoridade `ROLE_USER`. Usuário inexistente, inativo ou id malformado:
`UsernameNotFoundException`.

**`SecurityConfig`:**
- `.webAuthn(w -> w.rpName(rpName).rpId(rpId).allowedOrigins(webOrigin).disableDefaultRegistrationPage(true))`.
- `PasskeyConfig` cria os beans `JdbcUserCredentialRepository(JdbcOperations)` e
  `FriendlyUserEntityRepository`, que embrulha
  `JdbcPublicKeyCredentialUserEntityRepository(JdbcOperations)`.
- `LoginRateLimitFilter(LoginRateLimiter, HandlerExceptionResolver, IdentityProperties)` entra com
  `addFilterBefore(…, CsrfFilter.class)`. Fica depois do `CorsFilter`, então o 429 leva os
  cabeçalhos CORS. A chave é `request.getRemoteAddr()` (D26), e cada requisição destes caminhos
  conta uma tentativa:

| Caminho | Acima do limite |
|---|---|
| `/oauth2/authorization/**` e `/login/oauth2/code/**` (navegação) | 302 para `origin + "/entrar?erro=limite"` |
| `/webauthn/authenticate/options` e `/login/webauthn` (XHR) | Cabeçalho `Retry-After`, e a `RateLimitedException` vai ao `HandlerExceptionResolver`, que chega ao `ProblemHandler` |

**`V2__passkeys.sql`:**
- `user_entities` e `user_credentials`, iguais ao schema JDBC do WebAuthn do Spring Security
  (colunas, tipos e `NOT NULL`).
- Somando ao schema do Spring:
  - FK de `user_credentials.user_entity_user_id` para `user_entities(id)` com `ON DELETE CASCADE`;
  - índice único em `user_entities(name)`;
  - índice em `user_credentials(user_entity_user_id)`.

- [ ] **Step 1: Dependência do WebAuthn**

Adicione `implementation("com.webauthn4j:webauthn4j-core")`, que o WebAuthn do Spring Security
exige. Se o Boot 4.1.2 não gerenciar a versão, use a mais nova publicada em 2026-10-26 ou antes e
registre-a no PR.

Run: `./gradlew dependencies --configuration runtimeClasspath | grep webauthn4j`
Expected: uma linha `com.webauthn4j:webauthn4j-core` com versão resolvida.

- [ ] **Step 2: Escrever os testes (falhando)**

`PasskeyIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, Testcontainers). Os usuários vêm de
`TestUsers.create("Ana Teste", "ana@example.com")`:

| Teste | Requisição | Esperado |
|---|---|---|
| `registerOptionsUseRelyingPartyAndFriendlyName` | `POST /webauthn/register/options` com `signedIn` e `csrf()` | 200; `rp.id` `localhost`; `rp.name` `Liberdade`; `user.name` `ana@example.com`; `user.displayName` `Ana Teste`; no banco, `user_entities.name` é o id do usuário |
| `registerOptionsKeepTheSameHandle` | a mesma chamada duas vezes | o mesmo `user.id` nas duas; uma linha em `user_entities` |
| `registerOptionsRequireSignIn` | a mesma, anônima | 401 `auth.required` |
| `authenticateOptionsAreAnonymous` | `POST /webauthn/authenticate/options` anônimo com `csrf()` | 200; `rpId` `localhost`; `challenge` não vazio |
| `registrationPageIsDisabled` | `GET /webauthn/register` com `signedIn` | 404 |
| `passkeysReportsLatestCreation` | insere a entidade e duas credenciais (`created` 2026-11-01 e 2026-11-02, 10:00 UTC) e chama `Passkeys.latestCreatedAt` | `2026-11-02T10:00:00Z`; para outro usuário, vazio |

`FriendlyUserEntityRepositoryTest` (delegate em memória e `UserAccounts` com Mockito):
- `createsEntityNamedByUserId`: a primeira busca grava uma entidade com `name` = o id e devolve
  `name` = `ana@example.com` e `displayName` = `Ana Teste`.
- `fallsBackToDisplayNameWithoutEmail`: sem e-mail, `name` = `Ana Teste`.
- `reusesExistingEntity`: a segunda busca não grava de novo e devolve o mesmo id.
- `findByIdReturnsStoredEntity`: `name` = o id do usuário.
- `saveKeepsStoredName`: salvar uma cópia com `name` legível mantém o id do usuário no banco.
- `inactiveUserIsNotCreated`: com `findActive` vazio, nada é gravado.

`PasskeyUserDetailsServiceTest`:
- `loadsActiveUser`: `getUsername()` é o id, `getPassword()` é nulo e a única autoridade é
  `ROLE_USER`.
- `rejectsUnknownInactiveOrMalformed`: os três casos lançam `UsernameNotFoundException`.
- `webAuthnAuthenticationIsSerializable`: uma `WebAuthnAuthentication` com
  `ImmutablePublicKeyCredentialUserEntity` sobrevive à serialização Java, com o mesmo `getName()`.
  É o que a sessão JDBC guarda.

`LoginRateLimiterTest` (relógio fixo):

| Caso | Esperado |
|---|---|
| 20 tentativas às `2026-11-02T12:00:15Z` | todas vazias |
| a 21ª no mesmo instante | `OptionalLong.of(45)` |
| a 21ª às `12:00:59.500Z` | `OptionalLong.of(1)` |
| 20 tentativas, e a seguinte às `12:01:00Z` | vazia (janela nova) |
| chaves `a` e `b`, 20 cada | todas vazias |
| 10 001 chaves às `12:00:15Z` e uma tentativa às `12:01:00Z` | `size()` = 1 |

`LoginRateLimitIT` (`@SpringBootTest`, com um `@TestConfiguration` que troca o limitador por um
`@Primary` de relógio fixo em `2026-11-02T12:00:15Z`, para o teste não depender da virada do
minuto):
- `xhrOverLimitIs429`: 21 `POST /webauthn/authenticate/options` com `csrf()` e
  `remoteAddr` `10.0.0.21`. A 21ª responde 429, `code` `rate.limited`, o `detail` da exceção e
  `Retry-After: 45`.
- `navigationOverLimitGoesBackToSignIn`: 21 `GET /oauth2/authorization/google` com `remoteAddr`
  `10.0.0.22`. A 21ª responde 302 para `http://localhost:5173/entrar?erro=limite`.
- `otherPathsAreNotLimited`: 25 `GET /v1/csrf` com `remoteAddr` `10.0.0.23` respondem 200.

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --tests '*Passkey*' --tests '*LoginRateLimit*' --tests '*FriendlyUserEntity*'`
Expected: FAIL na compilação.

- [ ] **Step 4: Implementar**

Faça a migração, as classes e a configuração seguindo as tabelas acima.

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, com os testes da Task 3 ainda verdes.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(identity): add passkey sign-in and login rate limit"
```

---

### Task 5: Workspace: a família nasce no primeiro acesso, e as rotas da família ganham guarda

**Files:**
- Create: `api/src/main/resources/db/migration/V3__workspaces.sql`
- Create em `api/src/main/java/br/com/liberdadefamilia/workspace/`:
  - `package-info.java`
  - `Role.java`
  - `WorkspaceScoped.java`
  - `WorkspaceMember.java`
  - `WorkspaceCreated.java`
- Create em `api/src/main/java/br/com/liberdadefamilia/workspace/internal/`:
  - `WorkspaceController.java`
  - `MeController.java`
  - `WorkspaceService.java`
  - `WorkspaceRepository.java`
  - `WorkspaceGuardInterceptor.java`
  - `WorkspaceMemberArgumentResolver.java`
  - `WorkspaceWebConfig.java`
  - `WorkspaceAlreadyExistsException.java`
  - `WorkspaceNotFoundException.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/support/TestWorkspaces.java`
- Test em `api/src/test/java/br/com/liberdadefamilia/workspace/`:
  - `WorkspaceIT.java`
  - `MeIT.java`
  - `WorkspaceGuardIT.java`
  - `internal/SuggestedWorkspaceNameTest.java`

**Interfaces:**
- Consumes: `CurrentUser` e `Passkeys` (Tasks 3 e 4); `TestUsers` e `signedIn`.
- Produces:
  - `package-info.java`: `@ApplicationModule(allowedDependencies = {"identity", "shared"})`.
  - `public enum Role { OWNER, ADULT, YOUTH, VIEWER }`.
  - `@Target(METHOD) @Retention(RUNTIME) public @interface WorkspaceScoped { Role[] allow(); }`.
  - `public record WorkspaceMember(UUID workspaceId, UUID membershipId, UUID userId, Role role, ZoneId timezone, Currency baseCurrency)`.
    Qualquer método com `@WorkspaceScoped` o recebe como parâmetro.
  - `public record WorkspaceCreated(UUID workspaceId, UUID ownerMembershipId, Currency baseCurrency)`.
    É publicado dentro da transação que cria a família. Um listener síncrono (o ledger, na Task 6)
    roda na mesma transação: se ele falhar, a família não é criada.
  - `POST /v1/workspaces`:
    - Corpo `CreateWorkspaceRequest(String name, @Nullable String timezone)`. O construtor
      compacto faz `strip()` no nome; depois vêm `@NotBlank` e `@Size(max = 60)`.
    - `timezone` é o fuso do navegador (D28). Se não estiver em `ZoneId.getAvailableZoneIds()`,
      vira `America/Fortaleza`, o padrão do spec.
    - Responde 201 `{"id":"…","name":"…","role":"OWNER"}`, sem `Location` (D22).
    - Quem já tem uma família ativa recebe 409 `workspace.already-exists`, "Você já faz parte de
      uma família." (D14).
  - `GET /v1/me` (D9 e D21):

```json
{"user":{"id":"…","displayName":"Ana Teste","passkeyCreatedAt":null},
 "workspaces":[{"id":"…","name":"Família Teste","role":"OWNER","membershipId":"…",
                "timezone":"America/Fortaleza","currency":"BRL"}],
 "suggestedWorkspaceName":"Família Teste"}
```

  - `workspaces` lista só os vínculos `ACTIVE` de famílias sem exclusão pedida.
  - `static String suggestedName(@Nullable String familyNameHint)`, em `MeController`: com o
    sobrenome, `"Família " + hint.strip()`; sem ele (nulo ou em branco), `"Minha família"`. O
    resultado é cortado em 60 caracteres.
  - `WorkspaceNotFoundException`: 404, `workspace.not-found`, "Família não encontrada."
  - `TestWorkspaces(MockMvc mvc, JdbcClient jdbc)`, para as Tasks 6 e 7:
    - `UUID createFor(UUID userId)` cria a família pelo `POST /v1/workspaces` (com `signedIn` e
      `csrf()`), então o seeding da Task 6 também roda;
    - `UUID addMember(UUID workspaceId, UUID userId, Role role)` insere o vínculo por SQL e devolve
      o id;
    - `UUID membershipOf(UUID workspaceId, UUID userId)`.

**`V3__workspaces.sql`:**
- `workspaces`:
  - `id uuid PK DEFAULT uuidv7()`;
  - `name text NOT NULL`, com CHECK de 1 a 60 caracteres;
  - `base_currency char(3) NOT NULL DEFAULT 'BRL'` e `timezone text NOT NULL DEFAULT 'America/Fortaleza'`;
  - `deletion_requested_at timestamptz NULL`, `version`, `created_at` e `updated_at`.
- `memberships`:
  - `id uuid PK DEFAULT uuidv7()`;
  - `workspace_id` (FK para `workspaces`, `ON DELETE CASCADE`) e `user_id` (FK para `users`,
    `ON DELETE CASCADE`);
  - `role text NOT NULL`, com CHECK nos quatro papéis;
  - `nickname text NULL`, preenchido com o primeiro nome na criação (D5);
  - `status text NOT NULL DEFAULT 'ACTIVE'`, com CHECK em `ACTIVE` e `REMOVED`;
  - `parental_consent_at timestamptz NULL` e `parental_consent_by uuid NULL` (FK para `users`), que
    o Plano 4 usa;
  - `version`, `created_at` e `updated_at`;
  - `UNIQUE (workspace_id, user_id)` e `UNIQUE (workspace_id, id)` (alvo das FKs compostas da
    Task 6);
  - índice único parcial `memberships_one_active_family` em `(user_id) WHERE status = 'ACTIVE'`
    (D14). O Plano 4 o remove quando houver convites.

**Criar a família (`WorkspaceService`), numa transação:**
1. Se o usuário já tem um vínculo ativo, lança `WorkspaceAlreadyExistsException`.
2. Insere a família e o vínculo `OWNER`.
3. Publica `WorkspaceCreated`.
4. Se o índice único parcial recusar o vínculo (`DuplicateKeyException`, dois toques ao mesmo
   tempo), a transação volta, e o resultado também é `WorkspaceAlreadyExistsException`.

**Guarda (`WorkspaceGuardInterceptor`, em `/v1/workspaces/*/**`):** roda em ordem, e o primeiro
caso que bate decide.

| Situação | Resposta |
|---|---|
| O método não tem `@WorkspaceScoped` | 403 `auth.forbidden` (`AccessDeniedException`) |
| `{ws}` não é um UUID | 404 `workspace.not-found` |
| O usuário não tem vínculo `ACTIVE` com a família, ou a família pediu exclusão | 404 `workspace.not-found` (não revela se a família existe) |
| O papel é `YOUTH` | 403 `auth.forbidden`, mesmo que `allow` o liste (D13) |
| O papel não está em `allow` | 403 `auth.forbidden` |
| Os demais | segue; o `WorkspaceMember` vai num atributo da requisição, e o resolver o entrega ao método |

O id do usuário vem de `SecurityContextHolder` (`UUID.fromString(authentication.getName())`).
`{ws}` vem de `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`.

- [ ] **Step 1: Escrever os testes (falhando)**

`WorkspaceIT` (todas as requisições com `signedIn` e `csrf()`, salvo onde a tabela diz outra coisa):

| Teste | Requisição | Esperado |
|---|---|---|
| `createsFamily` | `{"name":"  Família Teste  ","timezone":"America/Manaus"}` | 201 com `name` "Família Teste" e `role` OWNER; sem `Location`; no banco, `timezone` `America/Manaus`, `base_currency` `BRL` e vínculo OWNER ativo com `nickname` "Ana" |
| `unknownTimezoneFallsBack` | `timezone` `Mars/Olympus` | 201; `timezone` `America/Fortaleza` |
| `missingTimezoneFallsBack` | sem `timezone` | 201; `timezone` `America/Fortaleza` |
| `blankNameIs422` | `name` `"   "` | 422 `request.invalid`; `errors` `[{"field":"name","code":"NotBlank"}]` |
| `nameUpTo60` | 61 caracteres; depois 60 (com outro usuário) | 422 com `code` `Size`; depois 201 |
| `secondFamilyIs409` | duas vezes | 409 `workspace.already-exists`, com o `detail` acima |
| `concurrentCreateMakesOneFamily` | duas threads, liberadas por um `CountDownLatch`, fazem o POST ao mesmo tempo | um 201 e um 409; uma linha em `workspaces` para o usuário |
| `publishesWorkspaceCreated` | `@RecordApplicationEvents` | um `WorkspaceCreated` com o id da família, o id do vínculo OWNER e `BRL` |
| `requiresCsrf` | sem `csrf()` | 403 `csrf.invalid` |

`MeIT`:

| Teste | Situação | Esperado |
|---|---|---|
| `meWithoutFamily` | `signedIn(id, "Teste")` | `user` com `displayName` "Ana Teste" e `passkeyCreatedAt` nulo; `workspaces` `[]`; `suggestedWorkspaceName` "Família Teste" |
| `meWithoutFamilyName` | `signedIn(id, null)` | `suggestedWorkspaceName` "Minha família" |
| `meWithFamily` | depois de `createFor` | um item com `id`, `name`, `role` OWNER, `membershipId`, `timezone` e `currency` `BRL` |
| `meHidesRemovedMembership` | vínculo passa a `REMOVED` | `workspaces` `[]` |
| `meHidesFamilyBeingDeleted` | `deletion_requested_at` preenchido | `workspaces` `[]` |
| `meShowsPasskeyDate` | uma credencial criada em `2026-11-02T10:00:00Z` | `passkeyCreatedAt` `"2026-11-02T10:00:00Z"` |

`SuggestedWorkspaceNameTest`:

| Entrada | Saída |
|---|---|
| `null` | "Minha família" |
| `"  "` | "Minha família" |
| `" Teste "` | "Família Teste" |
| 70 letras "a" | "Família " seguido de 52 letras "a" (60 no total) |

`WorkspaceGuardIT`: um controller de teste, classe estática aninhada registrada com `@Import`. O
`TestTypeExcludeFilter` do Boot deixa as classes aninhadas de teste fora da varredura dos outros
contextos. Ele tem:
- `GET /v1/workspaces/{ws}/probe/read`, com `@WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})`, que
  devolve o `role` do `WorkspaceMember`;
- `POST /v1/workspaces/{ws}/probe/write`, com `@WorkspaceScoped(allow = {OWNER, ADULT})`;
- `GET /v1/workspaces/{ws}/probe/youth`, com `@WorkspaceScoped(allow = {OWNER, YOUTH})`;
- `GET /v1/workspaces/{ws}/probe/open`, sem a anotação.

| Caso | Esperado |
|---|---|
| OWNER em `read` | 200 `OWNER` |
| VIEWER em `read` | 200 `VIEWER` |
| VIEWER em `write` | 403 `auth.forbidden` |
| ADULT em `write` | 200 |
| YOUTH em `youth` | 403 `auth.forbidden` |
| Usuário de outra família em `read` | 404 `workspace.not-found`, "Família não encontrada." |
| `{ws}` = `abc` | 404 `workspace.not-found` |
| Vínculo `REMOVED` | 404 |
| Família com exclusão pedida | 404 |
| OWNER em `open` | 403 `auth.forbidden` |
| Anônimo em `read` | 401 `auth.required` |

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*Workspace*' --tests '*MeIT'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar**

Faça a migração, os tipos públicos e as classes de `internal`, seguindo as tabelas acima. O
`WorkspaceRepository` usa `JdbcClient` (ADR-0012).

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; o `ApplicationModulesTest` aceita `workspace → identity`.

- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(workspace): create the family on first sign-in and guard family routes"
```

---

### Task 6: Ledger: carteira, categorias padrão e lançamentos idempotentes

**Files:**
- Create: `api/src/main/resources/db/migration/V4__ledger.sql`
- Create: `api/src/main/java/br/com/liberdadefamilia/ledger/package-info.java`
- Create em `api/src/main/java/br/com/liberdadefamilia/ledger/internal/`:
  - `LedgerSeeder.java`
  - `AccountController.java`, `AccountRepository.java`, `AccountType.java`
  - `CategoryController.java`, `CategoryRepository.java`
  - `TransactionController.java`, `TransactionService.java`, `TransactionRepository.java`
  - `CreateTransactionRequest.java`, `TransactionView.java`, `TransactionKind.java`
  - `TransactionAmount.java`, `TransactionAmountValidator.java`
  - `DashboardController.java`
  - `TransactionRejectedException.java`, `TransactionIdConflictException.java`
- Test em `api/src/test/java/br/com/liberdadefamilia/support/`: `TestIds.java`, `TestLedger.java`
- Test em `api/src/test/java/br/com/liberdadefamilia/ledger/`:
  - `LedgerSeedIT.java`
  - `CategoriesIT.java`
  - `TransactionsIT.java`
  - `DashboardIT.java`
  - `IsolationIT.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/workspace/WorkspaceGuardCoverageIT.java`

**Interfaces:**
- Consumes: `WorkspaceScoped`, `WorkspaceMember`, `Role` e `WorkspaceCreated` (Task 5); `Money`
  (Task 1); `TestUsers`, `signedIn` e `TestWorkspaces`.
- Produces:
  - `package-info.java`: `@ApplicationModule(allowedDependencies = {"workspace", "shared"})`.
  - As rotas abaixo, todas sob `/v1/workspaces/{ws}`. Leitura com
    `@WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})`; escrita com
    `@WorkspaceScoped(allow = {OWNER, ADULT})`.
  - `TransactionRejectedException(String code, String detail)`: 422.
  - `TransactionIdConflictException`: 409, `transaction.id-conflict`, "Este lançamento já existe com
    outros dados."
  - `TestIds.v7()`: um UUIDv7 para testes. São 48 bits de `System.currentTimeMillis()`, a versão 7,
    a variante IETF e o resto de `SecureRandom`.
  - `TestLedger(JdbcClient jdbc)`:
    - `UUID walletOf(UUID workspaceId)`;
    - `UUID categoryOf(UUID workspaceId, String kind, String name)`;
    - `void expense(UUID workspaceId, UUID membershipId, UUID categoryId, String amount, LocalDate on)`,
      que insere por SQL um gasto na carteira.

**`V4__ledger.sql`:**
- `accounts`:
  - `id uuid PK DEFAULT uuidv7()`; `workspace_id` (FK, `ON DELETE CASCADE`);
  - `name text NOT NULL`, com CHECK de 1 a 60 caracteres;
  - `type text NOT NULL`, com CHECK nos seis tipos do modelo de dados;
  - `currency char(3) NOT NULL` e `initial_balance numeric(19,4) NOT NULL DEFAULT 0`;
  - `owner_membership_id uuid NULL`, com FK composta `(workspace_id, owner_membership_id)` para
    `memberships (workspace_id, id)`;
  - `archived_at`, `version`, `created_at`, `updated_at`; `UNIQUE (workspace_id, id)`.
- `categories`:
  - `id uuid PK DEFAULT uuidv7()`; `workspace_id` (FK, `ON DELETE CASCADE`);
  - `parent_id uuid NULL`, com FK composta para `categories (workspace_id, id)`;
  - `kind text NOT NULL`, com CHECK em `INCOME` e `EXPENSE`; `name text NOT NULL` (1 a 40
    caracteres); `icon text NULL`; `color text NULL`;
  - `is_default boolean NOT NULL DEFAULT false` e `sort_order int NOT NULL DEFAULT 0`;
  - `archived_at`, `version`, `created_at`, `updated_at`; `UNIQUE (workspace_id, id)`;
  - índice único `categories_unique_name` em `(workspace_id, kind, parent_id, name)`,
    `NULLS NOT DISTINCT`, `WHERE archived_at IS NULL` (D30).
- `transactions`:
  - `id uuid PRIMARY KEY`, sem default (o id vem do aparelho); `workspace_id` (FK,
    `ON DELETE CASCADE`);
  - `kind text NOT NULL`, com CHECK nos três tipos; `amount numeric(19,4) NOT NULL CHECK (amount > 0)`;
    `currency char(3) NOT NULL`; `occurred_on date NOT NULL`;
  - FKs compostas `(workspace_id, …)`: `account_id` (NOT NULL) e `to_account_id` para `accounts`;
    `category_id` para `categories`; `member_id` e `created_by` (ambos NOT NULL) para
    `memberships`;
  - `description text NULL`, com CHECK de 1 a 140 caracteres; `deleted_at`, `version`,
    `created_at`, `updated_at`;
  - CHECKs `(kind = 'TRANSFER') = (to_account_id IS NOT NULL)`,
    `(kind = 'TRANSFER') = (category_id IS NULL)` e `to_account_id <> account_id`;
  - os três índices do modelo de dados.

**Semente (`LedgerSeeder`, D29):** um `@EventListener` para `WorkspaceCreated` insere:
- a conta "Carteira", tipo `CASH`, na moeda do evento, saldo inicial 0 e titular
  `ownerMembershipId`;
- as 19 categorias padrão, com `is_default = true` e `sort_order` na ordem da tabela (D16).

| Tipo | Categorias, em ordem |
|---|---|
| `EXPENSE` | 🛒 Mercado, 🍽️ Alimentação fora, 🚌 Transporte, 🏠 Moradia, 💊 Saúde, 🎉 Lazer, 💡 Contas e serviços, 🛍️ Compras, 📚 Educação, 🐾 Pets, 🎁 Presentes e doações, 🧾 Impostos e taxas, 📦 Outros |
| `INCOME` | 💼 Salário, ✨ Renda extra, 📈 Rendimentos, ↩️ Reembolsos, 🎁 Presentes, 📦 Outros |

O emoji vai em `icon`; o nome vai sem ele.

**Rotas:**

| Rota | Resposta |
|---|---|
| `GET /accounts` | `[{id, name, type, currency, balance}]` das contas não arquivadas, por nome. `balance` é `Money`: `initial_balance` + receitas − gastos, sem os excluídos. Na transferência, sai de `account_id` e entra em `to_account_id` |
| `GET /categories?kind=EXPENSE` (ou `INCOME`, obrigatório) | `[{id, name, kind, icon}]` das não arquivadas. Ordem: uso nos últimos 90 dias (D31, com o `Clock` e o fuso da família), depois `sort_order`, depois nome |
| `POST /transactions` | 201 `TransactionView`, sem `Location`; 200 no reenvio igual (abaixo) |
| `GET /transactions?month=2026-11` | `[TransactionView]` do mês, sem os excluídos, por `occurred_on`, `created_at` e `id`, todos decrescentes (D20) |
| `GET /dashboard?month=2026-11` | `{"month":"2026-11","expenses":Money,"balance":Money}`. `expenses` soma os gastos do mês; `balance` soma os saldos das contas não arquivadas na moeda base da família |

`month` é um `YearMonth`. Sem ele, ou fora do formato, a resposta é 400 `request.malformed`.

`TransactionView`: `{id, kind, amount, occurredOn, accountId, categoryId, description, memberId, createdAt}`.

**`POST /transactions`:**
- Cabeçalho `Idempotency-Key` (UUID) obrigatório. Sem ele, ou fora do formato, 400
  `request.malformed`.
- Corpo `CreateTransactionRequest(UUID id, TransactionKind kind, Money amount, LocalDate occurredOn, UUID accountId, UUID categoryId, @Nullable String description)`:
  - `@NotNull` em todos menos `description`. `categoryId` é obrigatório enquanto `TRANSFER` não
    existe (D23); o Plano 3 relaxa;
  - o construtor compacto faz `strip()` em `description`, e o texto em branco vira `null`; depois
    vem `@Size(max = 140)`;
  - `@TransactionAmount` em `amount` (código de erro `TransactionAmount`): maior que zero, no máximo
    999999999.99 e com escala até os dígitos da moeda.
- O serviço checa nesta ordem, e o primeiro erro decide (422 `TransactionRejectedException`):

| Checagem | `code` | `detail` |
|---|---|---|
| Cabeçalho diferente de `id`, ou `id.version() != 7` | `transaction.invalid-id` | "O identificador do lançamento é inválido." |
| `kind` é `TRANSFER` | `transaction.kind-unsupported` | "Transferências ainda não estão disponíveis." |
| Conta inexistente ou arquivada nesta família | `transaction.account-not-found` | "Conta não encontrada nesta família." |
| Categoria inexistente ou arquivada nesta família | `transaction.category-not-found` | "Categoria não encontrada nesta família." |
| `kind` da categoria diferente do lançamento | `transaction.category-kind-mismatch` | "Esta categoria não combina com o tipo do lançamento." |
| Moeda do valor diferente da conta | `transaction.currency-mismatch` | "A moeda do valor não é a mesma da conta." |

- Depois grava com `INSERT … ON CONFLICT (id) DO NOTHING`. `member_id` e `created_by` são o
  `membershipId` da guarda.
  - Inseriu: 201.
  - Não inseriu: busca `(workspaceId, id)`. Se a linha existe e tem o mesmo conteúdo (`kind`,
    `amount` por `compareTo`, `currency`, `occurredOn`, `accountId`, `categoryId`,
    `description`), responde 200 com a linha guardada. Senão (outro conteúdo, ou outra família),
    lança `TransactionIdConflictException`.

- [ ] **Step 1: Escrever os testes (falhando)**

Todas as requisições usam `signedIn` e, nos POSTs, `csrf()`. Ana é OWNER de uma família criada com
`TestWorkspaces.createFor`.

`LedgerSeedIT`:

| Teste | Esperado |
|---|---|
| `newFamilyHasWalletAndCategories` | uma conta "Carteira" `CASH` em BRL com saldo "0.00"; 13 categorias `EXPENSE` e 6 `INCOME`, todas `is_default`, com os ícones da tabela |
| `failedSeedingLeavesNoFamily` | com um `@TestConfiguration` que registra um listener que lança exceção em `WorkspaceCreated`: o POST responde 500 `internal`; nenhuma linha em `workspaces`, `memberships`, `accounts` ou `categories` para o usuário |

`CategoriesIT` (com um `@TestConfiguration` que expõe um `@Primary Clock` fixo em
`2026-11-15T15:00:00Z`; a família fica em `America/Fortaleza`):

| Teste | Situação | Esperado |
|---|---|---|
| `expenseCategoriesInDefaultOrder` | sem lançamentos | 13 itens; Mercado primeiro, Outros por último |
| `incomeCategories` | `kind=INCOME` | 6 itens; Salário primeiro |
| `mostUsedFirst` | dois gastos em Pets e um em Lazer, em 2026-11-10 | Pets, Lazer, Mercado, … |
| `usageWindowIs90Days` | um gasto em Lazer em 2026-08-18; três em Transporte em 2026-08-17 | Lazer primeiro; Transporte na posição padrão |
| `archivedHidden` | Pets com `archived_at` | 12 itens, sem Pets |
| `viewerCanRead` | um VIEWER (`addMember`) | 200 |
| `kindIsRequired` | sem `kind` | 400 `request.malformed` |

`TransactionsIT` (o `id` vem de `TestIds.v7()` e vai também no cabeçalho, salvo onde a tabela diz
outra coisa; o gasto padrão é `{"amount":"12.34","currency":"BRL"}` em Mercado, na carteira, em
2026-11-02):

| Teste | Esperado |
|---|---|
| `createsExpense` | 201 com a `TransactionView`; no banco, `member_id` e `created_by` iguais ao vínculo OWNER |
| `resendIsIdempotent` | o mesmo POST duas vezes: 201 e depois 200 com o mesmo corpo; uma linha |
| `sameIdOtherContentIs409` | mesmo `id`, `amount` "12.35": 409 `transaction.id-conflict` |
| `sameIdOtherFamilyIs409` | Bia, de outra família, reenvia o `id` de Ana com a carteira e o Mercado de Bia: 409, sem nada do lançamento de Ana no corpo |
| `missingIdempotencyKeyIs400` | sem o cabeçalho: 400 `request.malformed` |
| `keyMustMatchId` | cabeçalho com outro UUIDv7: 422 `transaction.invalid-id` |
| `idMustBeV7` | `UUID.randomUUID()` nos dois: 422 `transaction.invalid-id` |
| `amountRules` | "0.00", "-1.00", "12.345" e "1000000000.00": 422 com `errors` `[{"field":"amount","code":"TransactionAmount"}]`; "999999999.99": 201 |
| `descriptionRules` | 141 caracteres: 422 com `code` `Size`; `"   "`: 201 com `description` nulo; `" Feira "`: 201 com "Feira" |
| `missingFieldsAre422` | corpo `{}` com cabeçalho válido: 422; `errors` tem `NotNull` em `id`, `kind`, `amount`, `occurredOn`, `accountId` e `categoryId` |
| `accountFromOtherFamilyIs422` | a carteira de Bia: 422 `transaction.account-not-found` |
| `categoryFromOtherFamilyIs422` | o Mercado de Bia: 422 `transaction.category-not-found` |
| `incomeCategoryOnExpenseIs422` | Salário num `EXPENSE`: 422 `transaction.category-kind-mismatch` |
| `incomeIsAccepted` | `INCOME` em Salário: 201 |
| `transferIs422` | `TRANSFER`: 422 `transaction.kind-unsupported` |
| `currencyMismatchIs422` | `{"amount":"12.34","currency":"USD"}`: 422 `transaction.currency-mismatch` |
| `viewerCannotPost` | um VIEWER: 403 `auth.forbidden` |
| `listsMonthNewestFirst` | gastos em 2026-11-02, 2026-11-10 e 2026-12-01; `month=2026-11` devolve os de 11-10 e 11-02, nessa ordem |
| `logsNeverShowValues` | com `OutputCaptureExtension`: um 201 com "43.21" e "Feira secreta", e um 422 de moeda com os mesmos dados; a saída não contém "43.21" nem "Feira secreta" |
| `doubleTapCreatesOne` | duas threads, liberadas por um `CountDownLatch`, fazem o mesmo POST: um 201 e um 200; uma linha (Review Focus 1) |

`DashboardIT`:

| Teste | Situação | Esperado |
|---|---|---|
| `monthBoundary` | gasto de 10.00 em 2026-11-30 e de 20.00 em 2026-12-01 | `month=2026-11`: `expenses` "10.00"; `month=2026-12`: "20.00"; nos dois, `balance` "-30.00" (Review Focus 4) |
| `incomeNotInExpenses` | uma receita de 100.00 e um gasto de 30.00 no mês | `expenses` "30.00"; `balance` "70.00" |
| `emptyMonth` | sem lançamentos | `expenses` `{"amount":"0.00","currency":"BRL"}` |
| `accountBalanceComputed` | carteira com `initial_balance` 50.00 (por SQL) e um gasto de 12.34 | `GET /accounts`: `balance` "37.66" |

`IsolationIT`: Bia, OWNER de outra família, chama as cinco rotas com o `{ws}` de Ana. Todas
respondem 404 `workspace.not-found`.

`WorkspaceGuardCoverageIT`: percorre `RequestMappingHandlerMapping.getHandlerMethods()`. Todo
mapeamento cujo padrão começa com `/v1/workspaces/{ws}` tem `@WorkspaceScoped`, e há pelo menos
cinco deles.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.liberdadefamilia.ledger.*' --tests '*GuardCoverageIT'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar**

Faça a migração, a semente e as rotas, seguindo as tabelas acima. Todo método de repositório recebe
`workspaceId` (ADR-0008). Os saldos são calculados numa consulta, sem coluna de saldo.

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; o `ApplicationModulesTest` aceita `ledger → workspace`.

- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(ledger): seed the wallet and categories and record expenses idempotently"
```

---

### Task 7: Contrato OpenAPI e tipos gerados no web

**Files:**
- Modify: `api/build.gradle.kts` (dependência do springdoc)
- Modify: `api/src/main/resources/application.properties` e `application-dev.properties`
- Modify: `api/src/test/resources/application-test.properties`
- Modify: `identity/internal/SecurityConfig.java`, `identity/internal/IdentityWebConfig.java` e
  `workspace/internal/WorkspaceWebConfig.java`
- Modify, para `@Schema(nullable = true)` e os nomes da tabela abaixo: os DTOs e controllers das
  Tasks 3, 5 e 6
- Create: `api/src/main/java/br/com/liberdadefamilia/shared/internal/OpenApiConfig.java`
- Create: `api/openapi.json` (gerado pelo teste)
- Test em `api/src/test/java/br/com/liberdadefamilia/`: `OpenApiDriftIT.java`,
  `OpenApiContractIT.java`, `ApiDocsDisabledIT.java`
- Modify: `web/package.json`, `pnpm-workspace.yaml`, `pnpm-lock.yaml`
- Create: `web/src/shared/api/schema.ts` (gerado) e `web/src/shared/api/schema.test.ts`
- Modify: `web/eslint.config.js`, `web/.prettierignore`, `web/vite.config.ts`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: as rotas e os DTOs das Tasks 3, 5 e 6; `Money` (Task 1); `CurrentUser` (Task 3);
  `WorkspaceMember` (Task 5).
- Produces:
  - `api/openapi.json`, o contrato versionado.
  - `web/src/shared/api/schema.ts`, que exporta `paths` e `components`. A Task 8 importa
    `import type { paths, components } from './schema'`.
  - O script `gen:api` no `web/package.json`.
  - Para atualizar os dois arquivos depois de mudar a API:
    `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` (no PowerShell,
    `$env:UPDATE_OPENAPI='true'` antes) e depois `corepack pnpm --filter web gen:api`.

**Configuração (D32):**

| Arquivo | Propriedades |
|---|---|
| `application.properties` | `springdoc.api-docs.enabled=false`, `springdoc.api-docs.version=openapi_3_0`, `springdoc.writer-with-order-by-keys=true`, `springdoc.paths-to-match=/v1/**`, `springdoc.override-with-generic-response=false`, `springdoc.default-produces-media-type=application/json` |
| `application-dev.properties` e `application-test.properties` | `springdoc.api-docs.enabled=true` |

`SecurityConfig` libera `/v3/api-docs` e `/v3/api-docs/**`. Fora de `dev` e `test`, a rota não
existe e responde 404.

**`OpenApiConfig`:**
- Um bean `OpenAPI` com `info` (título "Liberdade API", versão "v1") e `servers` = `[{"url":"/"}]`.
- Num bloco `static`, com `SpringDocUtils.getConfig()`:
  - `replaceWithClass(Money.class, MoneySchema.class)`. `MoneySchema` é um record privado só para o
    contrato, com `@Schema(name = "Money")` e dois campos `String` obrigatórios:
    - `amount`, com `pattern` `^-?\d+(\.\d{1,4})?$` e `example` "12.34";
    - `currency`, com `pattern` `^[A-Z]{3}$` e `example` "BRL".
  - `replaceWithSchema` para os tipos que o Jackson grava como texto:

| Tipo | Schema |
|---|---|
| `YearMonth` | `string`, `pattern` `^\d{4}-(0[1-9]\|1[0-2])$`, `example` "2026-11" |
| `Currency` | `string`, `pattern` `^[A-Z]{3}$`, `example` "BRL" |
| `ZoneId` | `string`, `example` "America/Fortaleza" |

- `IdentityWebConfig` e `WorkspaceWebConfig` ganham cada um um bloco `static` com
  `SpringDocUtils.getConfig().addRequestWrapperToIgnore(…)` para `CurrentUser` e `WorkspaceMember`.
  Assim os parâmetros resolvidos pela sessão e pela guarda não aparecem no contrato, e o `shared`
  não depende de `identity` nem de `workspace`.

**Nomes no contrato.** O springdoc usa o nome simples da classe. Renomeie os records das Tasks 3, 5
e 6 que tiverem outro nome:

| Rota | Records |
|---|---|
| `GET /v1/csrf` | `CsrfTokenView` |
| `GET /v1/me` | `MeView`, com `user`: `MeUser` e `workspaces`: `MeWorkspace[]` |
| `POST /v1/workspaces` | `CreateWorkspaceRequest` → `WorkspaceView` |
| `GET /accounts` | `AccountView[]` |
| `GET /categories` | `CategoryView[]` |
| `POST /transactions`, `GET /transactions` | `CreateTransactionRequest` → `TransactionView` |
| `GET /dashboard` | `DashboardView` |

**Campos que podem vir nulos** (`@Schema(nullable = true)` ao lado do `@Nullable`):
`TransactionView.description`, `MeUser.passkeyCreatedAt`, `CreateTransactionRequest.description` e
`CreateWorkspaceRequest.timezone`. `categoryId` continua não nulo neste plano; o Plano 3 muda junto
com `TRANSFER`.

**Web:**
- `openapi-typescript` 7.13.0 como devDependency. Ele declara `typescript` 5 como peer; o
  `pnpm-workspace.yaml` aceita a 6:

```yaml
peerDependencyRules:
  allowedVersions:
    openapi-typescript>typescript: '6'
```

- Script: `"gen:api": "openapi-typescript ../api/openapi.json -o src/shared/api/schema.ts --properties-required-by-default"`.
- `schema.ts` é gerado: entra no `globalIgnores` do ESLint, no `.prettierignore` e no `exclude` da
  cobertura.

**CI (`web`):** logo depois de `pnpm install --frozen-lockfile`, o passo "Check generated API types"
roda `pnpm --filter web gen:api` e depois `git diff --exit-code -- web/src/shared/api/schema.ts`.

- [ ] **Step 1: Escrever os testes (falhando)**

`OpenApiDriftIT` (no molde do `HealthProbesIT`), teste `committedContractIsCurrent`:
- Busca `GET /v3/api-docs` e compara a árvore JSON com `Path.of("openapi.json")` (o diretório de
  trabalho do Gradle é `api/`).
- Com a variável `UPDATE_OPENAPI=true`, regrava o arquivo (indentado, com LF e uma quebra de linha
  no fim) e passa.
- Sem ela, arquivo diferente ou ausente falha com a mensagem
  `api/openapi.json is out of date. Run: UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'`.

`OpenApiContractIT` (lê `GET /v3/api-docs`):

| Teste | Esperado |
|---|---|
| `moneyIsAStringPair` | `components.schemas.Money`: `required` = `amount` e `currency`; os dois `type: string`, com os `pattern` acima |
| `moneyFieldsReferenceTheComponent` | `TransactionView.properties.amount.$ref` = `#/components/schemas/Money` |
| `monthIsYearMonthPattern` | o parâmetro `month` de `GET /v1/workspaces/{ws}/transactions` tem o `pattern` de `YearMonth` |
| `resolvedParametersAreHidden` | `GET /v1/me` não tem `parameters`; `GET /v1/workspaces/{ws}/accounts` tem só `ws`, `in: path` |
| `idempotencyKeyIsRequiredUuid` | o cabeçalho `Idempotency-Key` do POST é `required`, `type: string`, `format: uuid` |
| `serverIsRoot` | `servers` = `[{"url":"/"}]` |
| `zonesAndCurrenciesAreStrings` | `MeWorkspace.timezone` e `MeWorkspace.currency` são `type: string` |
| `nullability` | `nullable: true` em `TransactionView.description` e `MeUser.passkeyCreatedAt`; sem `nullable` em `TransactionView.categoryId`, `TransactionView.amount` e `MeUser.displayName` |
| `onlyV1Paths` | toda chave de `paths` começa com `/v1/` |

`ApiDocsDisabledIT`: `@SpringBootTest(properties = "springdoc.api-docs.enabled=false")`;
`GET /v3/api-docs` responde 404.

`web/src/shared/api/schema.test.ts` usa `expectTypeOf` do Vitest. Quem confere os tipos é o
`tsc -b`; o Vitest só roda o arquivo.

| Tipo | Esperado (`toEqualTypeOf`) |
|---|---|
| `components['schemas']['Money']['amount']` | `string` |
| `components['schemas']['TransactionView']['description']` | `string \| null` |
| `components['schemas']['TransactionView']['categoryId']` | `string` |
| `paths['/v1/me']['get']['responses'][200]['content']['application/json']['user']['passkeyCreatedAt']` | `string \| null` |

Um campo opcional por engano vira `… \| undefined` e quebra a igualdade exata.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*OpenApi*' --tests '*ApiDocsDisabledIT'`
Expected: FAIL; `/v3/api-docs` responde 401 ou 404.

- [ ] **Step 3: Implementar**

Adicione a dependência, as propriedades e o `OpenApiConfig`, e ajuste os nomes e os campos nulos.
Gere o contrato com `UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` e confira
o diff de `api/openapi.json`. No web, instale o `openapi-typescript`, rode
`corepack pnpm --filter web gen:api` e adicione o passo ao CI.

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

Run: `corepack pnpm --filter web exec tsc -b; corepack pnpm --filter web lint; corepack pnpm --filter web test:coverage`
Expected: sem erros; `schema.test.ts` passa.

- [ ] **Step 5: Commit**

```bash
git add api web pnpm-workspace.yaml pnpm-lock.yaml .github/workflows/ci.yml
git commit -m "feat(api): publish the OpenAPI contract and generate web types"
```

---

### Task 8: Cliente da API, cache persistido e mocks do MSW

**Files:**
- Modify: `web/package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml`
- Create em `web/src/shared/api/`: `config.ts`, `errors.ts`, `csrf.ts`, `client.ts`, `endpoints.ts`,
  `queryKeys.ts`, `queryClient.ts`, `hooks.ts`, `types.ts`, `index.ts`
- Create em `web/src/shared/offline/`: `db.ts`, `persister.ts`, `session.ts`, `useOnline.ts`,
  `index.ts`
- Create: `web/src/shared/testing/index.tsx`
- Create em `web/src/mocks/`: `data.ts`, `handlers.ts`, `server.ts`, `browser.ts`
- Create: `web/public/mockServiceWorker.js` (gerado), `web/src/vite-env.d.ts`, `web/.env.mocks`
- Modify: `web/src/app/providers.tsx`, `web/src/main.tsx`, `web/src/app/routes.test.tsx`,
  `web/src/test-setup.ts`, `web/vite.config.ts`, `web/eslint.config.js`, `web/.prettierignore`,
  `web/eslint/boundaries.js`
- Test: `errors.test.ts`, `csrf.test.ts`, `queryClient.test.ts`, `hooks.test.tsx` em `shared/api/`;
  `persister.test.ts`, `session.test.ts`, `useOnline.test.ts` em `shared/offline/`;
  `web/eslint/boundaries.test.js`

**Interfaces:**
- Consumes: `paths` e `components` de `@/shared/api/schema` (Task 7); as rotas e os códigos de erro
  das Tasks 3, 5 e 6.
- Produces, por `@/shared/api`:
  - `API_URL: string`.
  - `class ApiError extends Error { status: number; code: string; detail?: string }`. Falha de rede
    é `status` 0 e `code` `'network'`; resposta sem corpo de problema é `code` `'http'`.
  - `resetCsrf(): void`.
  - `postJson(path: string, body?: unknown): Promise<unknown>`, para as rotas fora de `/v1`
    (`/webauthn/…`, `/login/webauthn`, `/logout`). Devolve `undefined` no 204.
  - `getMe()`, `createWorkspace(name: string)`, `postTransaction(ws: string, payload: CreateTransactionRequest): Promise<TransactionView>`.
  - `queryKeys` e `createQueryClient(onUnauthorized: () => void): QueryClient`.
  - Hooks: `useMe()`, `useCurrentWorkspace(): MeWorkspace | undefined`, `useAccounts(ws)`,
    `useCategories(ws, kind)`, `useTransactions(ws, month)`, `useDashboard(ws, month)`,
    `useCreateWorkspace()`.
  - Tipos: `MeView`, `MeUser`, `MeWorkspace`, `AccountView`, `CategoryView`, `CategoryKind`,
    `TransactionView`, `CreateTransactionRequest`, `DashboardView` e `ApiMoney`, todos apelidos de
    `components['schemas'][…]`.
- Produces, por `@/shared/offline`:
  - `db`, com as tabelas `outbox` e `kv`, e o tipo `OutboxItem` (a Task 9 usa).
  - `persistOptions`, `clearSession(queryClient: QueryClient): Promise<void>` e
    `useOnline(): boolean`.
- Produces, por `@/shared/testing` (só para testes): `createTestQueryClient()`,
  `renderWithProviders(ui, { route?, path?, queryClient? })`, que devolve o resultado do
  `render` mais `router` e `queryClient`, e `renderHookWithProviders(hook, { queryClient? })`.
- Produces, por `@/mocks`: `server`, `worker`, `resetMockData()`, `mockState`,
  `problem(status, code, detail?)`, que monta um `HttpResponse` de problema para `server.use(…)`, e
  `seedMockFamily(): MeWorkspace`. Esta última cria "Família Teste" como o `POST /v1/workspaces`
  faria, com a semente, e devolve o vínculo. Todo teste "com família" começa por ela.
- Produces: `AppProviders({ children, queryClient })`.

**Pacotes** ("a mais recente", salvo indicação): `@tanstack/react-query`,
`@tanstack/react-query-persist-client`, `@tanstack/query-async-storage-persister`, `openapi-fetch`
e `dexie@4.4.7`; em dev, `msw@3.0.3` e `fake-indexeddb`.
- O `pnpm-workspace.yaml` ganha `msw: false` em `allowBuilds`. O worker é versionado e se regera
  com `corepack pnpm --filter web exec msw init public --save` quando o `msw` muda.
- Se o `openapi-fetch` declarar `typescript` 5 como peer, ele entra na mesma regra
  `peerDependencyRules` da Task 7.

**Cliente:**
- `API_URL` vem de `import.meta.env.VITE_API_URL`, com `http://localhost:8080` como padrão. O
  `vite-env.d.ts` tipa `VITE_API_URL` e `VITE_API_MOCKS`.
- `client.ts` cria `createClient<paths>({ baseUrl: API_URL, credentials: 'include', fetch: csrfFetch })`
  e exporta `unwrap`, que devolve `data` ou lança `ApiError`. Toda chamada passa por ele.
- `csrfFetch(request: Request): Promise<Response>`:
  - Em métodos que não são GET nem HEAD, pede `GET /v1/csrf` uma vez, guarda a promessa (duas
    chamadas ao mesmo tempo compartilham o pedido) e põe `headerName: token` na requisição.
  - Guarda um `request.clone()` antes de enviar. Com 403 `csrf.invalid`, chama `resetCsrf()` e
    reenvia uma vez só.
- `postTransaction` manda `Idempotency-Key` igual a `payload.id`.

**Cache (`createQueryClient`):**

| Opção | Valor |
|---|---|
| `queries.networkMode` | `'offlineFirst'` |
| `queries.gcTime` | 30 dias (precisa cobrir o `maxAge` da persistência) |
| `queries.staleTime` | 30 s |
| `queries.retry` | `shouldRetry`: nunca em 4xx; nos outros erros, até 2 vezes |
| `mutations.retry` | `false` |
| `QueryCache` e `MutationCache` | `onError`: um `ApiError` 401 chama `onUnauthorized` |

`useCreateWorkspace()` é uma mutação que recebe `name`. Ela manda `timezone` =
`Intl.DateTimeFormat().resolvedOptions().timeZone` (D28). O 409 `workspace.already-exists` conta
como sucesso (toque duplo, outra aba). No sucesso, espera o `invalidateQueries({ queryKey: queryKeys.me })`.

`queryKeys`: `me` = `['me']`, `workspace(ws)` = `['ws', ws]`, e abaixo dele
`accounts(ws)`, `categories(ws, kind)`, `transactions(ws, month)` e `dashboard(ws, month)`.

**Offline:**
- `db.ts`: Dexie `liberdade`, versão 1, com `outbox: 'id, [userId+createdAt], workspaceId'` e
  `kv: 'key'`.
- `OutboxItem`: `{ id, userId, workspaceId, type: 'transaction.create', payload: CreateTransactionRequest, createdAt: number, sendAfter: number, attempts: number, status: 'pending' | 'needs-attention', problem?: { code: string; detail?: string } }`.
  As datas são epoch em ms.
- `persistOptions`:
  - o `persister` é `createAsyncStoragePersister` sobre a tabela `kv`, chave
    `liberdade-query-cache`;
  - `maxAge` de 30 dias e `buster` `'1'`. Suba o `buster` quando o `schema.ts` mudar de forma
    incompatível;
  - `dehydrateOptions.shouldDehydrateMutation` devolve `false`, porque gasto offline vai pela fila.
- `clearSession(queryClient)` faz `queryClient.clear()`, `resetCsrf()` e
  `persister.removeClient()`. A fila fica, porque é separada por `userId` (Review Focus 2).
- `useOnline()` usa `useSyncExternalStore` sobre `navigator.onLine` e os eventos `online` e
  `offline`.

**App:**
- `AppProviders({ children, queryClient })` põe o `PersistQueryClientProvider` com
  `persistOptions` dentro do `IntlProvider`.
- O `main.tsx` cria o cliente com um `onUnauthorized` que primeiro faz
  `router.navigate('/entrar')` e só depois roda `clearSession`. Assim nenhuma tela busca `/v1/me` de
  novo no meio da limpeza. Um segundo 401 enquanto isso acontece não faz nada.
- Com `import.meta.env.VITE_API_MOCKS === 'true'`, o `main.tsx` faz `import('@/mocks/browser')` e
  espera o `worker.start({ onUnhandledRequest: 'bypass' })` antes do primeiro `render`. O Vite troca
  a variável por uma constante no build, e o ramo some quando ela não é `'true'`.
- `web/.env.mocks` (`VITE_API_MOCKS=true`) e o script `"dev:mocks": "vite --mode mocks"`.

**Mocks (D33):**
- `data.ts` guarda o estado em memória, tipado com `components['schemas']`.
  - O estado inicial é a sessão aberta: Ana Teste, sem família e com
    `suggestedWorkspaceName` "Família Teste".
  - `mockState.signedIn = false` faz `/v1/me` responder 401.
  - Criar a família semeia a "Carteira" e as categorias da Task 6, com os mesmos nomes, emojis e
    ordem.
  - `resetMockData()` volta ao estado inicial.
- `handlers.ts` cobre as rotas `/v1` das Tasks 3, 5 e 6, com os mesmos códigos de erro, mais
  `POST /logout` (204). As rotas de passkey entram na Task 10.
  - Todo POST sem `X-XSRF-TOKEN: mock-csrf` responde 403 `csrf.invalid`.
  - `POST /transactions` é idempotente: 201 na primeira vez, 200 com o mesmo corpo e 409
    `transaction.id-conflict` com outro.
- `server.ts` usa `setupServer` (`msw/node`), e `browser.ts` usa `setupWorker` (`msw/browser`).

**Testes:** o `test-setup.ts` passa a importar `fake-indexeddb/auto` e faz `server.listen({ onUnhandledRequest: 'error' })`.
Depois de cada teste, ele faz `server.resetHandlers()`, `resetMockData()`, `resetCsrf()`,
`localStorage.clear()` e limpa as tabelas do `db`. No fim, faz `server.close()`.
`createTestQueryClient()` é o `createQueryClient` com `retry: false`.

**Fronteiras (D33):**
- `restrict(files, patterns)` passa a devolver dois objetos:
  - os arquivos do grupo, com `ignores: ['**/*.test.{ts,tsx}']`, levam os padrões mais
    `{ group: ['@/mocks', '@/mocks/**'], message: 'Only main.tsx and tests may import the MSW mocks.' }`;
  - os testes do mesmo grupo levam só os padrões.
- `boundaryConfigs()` achata a lista. `AREAS` ganha `mocks`, e um grupo novo para
  `src/mocks/**` barra `@/features` e `@/app`.
- O `main.tsx` e o `test-setup.ts` ficam fora de toda área e podem importar `@/mocks`.
- Em `globalIgnores` e no `.prettierignore` entra `public/mockServiceWorker.js`. No `exclude` da
  cobertura entram `src/mocks/**` e `src/shared/testing/**`.

- [ ] **Step 1: Escrever os testes (falhando)**

`errors.test.ts`:

| Teste | Esperado |
|---|---|
| problema 422 `request.invalid` com `detail` | `ApiError` com `status` 422, `code` e `detail` do corpo |
| `fetch` rejeita com `TypeError` | `status` 0, `code` `'network'` |
| 502 com `text/html` | `status` 502, `code` `'http'`, `detail` `undefined` |

`csrf.test.ts` (MSW):

| Teste | Esperado |
|---|---|
| `createWorkspace` | o handler recebe `X-XSRF-TOKEN: mock-csrf` |
| primeiro POST recebe 403 `csrf.invalid`, o segundo passa | `/v1/csrf` é chamado 2 vezes; o POST, 2; a promessa resolve |
| 403 `csrf.invalid` duas vezes | rejeita com `ApiError` 403 `csrf.invalid`; o POST é chamado 2 vezes |
| `getMe()` | sem `X-XSRF-TOKEN` e sem chamada a `/v1/csrf` |
| dois POSTs ao mesmo tempo | `/v1/csrf` é chamado 1 vez |
| `postTransaction` | `Idempotency-Key` igual a `payload.id` |

`queryClient.test.ts`:

| Teste | Esperado |
|---|---|
| uma consulta recebe 401 `auth.required` | `onUnauthorized` chamado 1 vez |
| uma mutação recebe 401 | `onUnauthorized` chamado 1 vez |
| `shouldRetry` | `(0, 422)` → `false`; `(0, 503)` → `true`; `(1, rede)` → `true`; `(2, rede)` → `false` |

`hooks.test.tsx` (`renderHookWithProviders`):

| Teste | Esperado |
|---|---|
| `useMe` | `user.displayName` "Ana Teste", `workspaces` `[]`, `suggestedWorkspaceName` "Família Teste" |
| `useCreateWorkspace` com "Família Teste" | resolve; o corpo enviado traz o fuso do navegador; depois `useMe` mostra a família com `role` `OWNER` |
| `useCreateWorkspace` duas vezes seguidas | as duas resolvem; `mockState` tem uma família só |
| `useCreateWorkspace` com 422 | rejeita com `ApiError` 422 |
| `useCategories(ws, 'EXPENSE')` | 13 itens; o primeiro é "Mercado" |
| `useAccounts(ws)` | um item, "Carteira", com `balance.currency` "BRL" |

`persister.test.ts`: um cliente com `['me']` passa por `persistClient` e `restoreClient` e volta
igual. A linha `liberdade-query-cache` existe em `db.kv`.

`session.test.ts` (Review Focus 2): com `['me']` no cache, a chave em `kv` e um item na fila,
`clearSession` deixa `getQueryData(['me'])` `undefined` e `kv` vazio. A fila continua com 1 item, e
o POST seguinte busca `/v1/csrf` de novo.

`useOnline.test.ts`: começa `false` com `navigator.onLine` `false`; vira `true` no evento
`online`.

`boundaries.test.js`, no `it.each`:

| Arquivo | Importa | Mensagens |
|---|---|---|
| `src/features/transactions/a.ts` | `@/mocks/server` | 1 |
| `src/features/transactions/a.test.ts` | `@/mocks/server` | 0 |
| `src/features/transactions/a.test.ts` | `@/features/planning/b` | 1 |
| `src/shared/api/a.ts` | `@/mocks/data` | 1 |
| `src/app/a.tsx` | `@/mocks/browser` | 1 |
| `src/main.tsx` | `@/mocks/browser` | 0 |
| `src/mocks/handlers.ts` | `@/features/auth` | 1 |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/shared/api src/shared/offline eslint`
Expected: FAIL; os módulos não existem e as regras novas de fronteira não disparam.

- [ ] **Step 3: Implementar**

Instale os pacotes, rode `msw init` e escreva os módulos acima. Atualize o `routes.test.tsx` para
passar `createTestQueryClient()` ao `AppProviders`.

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --filter web lint; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build`
Expected: sem erros; `src/shared/**` com 90% ou mais; o `dist/` não contém `@/mocks` (procure
`mock-csrf` nos arquivos `.js`: nenhum resultado).

- [ ] **Step 5: Commit**

```bash
git add web pnpm-workspace.yaml pnpm-lock.yaml
git commit -m "feat(web): typed API client, persisted query cache and MSW mocks"
```

---

### Task 9: Fila offline e envio em ordem

**Files:**
- Modify: `web/package.json`, `pnpm-lock.yaml`
- Create: `web/src/shared/offline/outbox.ts`, `web/src/shared/offline/sync.ts`,
  `web/src/shared/offline/lock.ts`, `web/src/shared/offline/useOutbox.ts`,
  `web/src/shared/i18n/dates.ts`
- Modify: `web/src/shared/offline/index.ts`, `web/src/shared/i18n/index.ts`
- Test: `outbox.test.ts`, `sync.test.ts`, `useOutbox.test.tsx` em `web/src/shared/offline/`;
  `web/src/shared/i18n/dates.test.ts`

**Interfaces:**
- Consumes: `db` e `OutboxItem` (Task 8); `postTransaction`, `ApiError`, `queryKeys`,
  `TransactionView`, `DashboardView` e `CreateTransactionRequest` de `@/shared/api` (Task 8);
  `parseCents` e `centsToApi` de `@/shared/money`.
- Produces, por `@/shared/offline`:
  - `SEND_DELAY_MS = 6000` (D15).
  - `enqueueTransaction(input: { userId: string; workspaceId: string; payload: CreateTransactionRequest }, now?: number): Promise<OutboxItem>`.
  - `cancelOutboxItem(id: string): Promise<boolean>`: `true` se o item ainda estava na fila. Ele
    espera o lock `liberdade-outbox` (`navigator.locks.request(name, cb)`, sem `ifAvailable`; sem
    Web Locks, a trava em memória). Assim nunca corre contra um envio em andamento (D34).
  - `discardOutboxItem(id: string): Promise<void>`.
  - `replaceOutboxItem(oldId: string, payload: CreateTransactionRequest, now?: number): Promise<OutboxItem>` (D24).
  - `clearOutboxForUser(userId: string): Promise<void>` e
    `countOutboxForUser(userId: string): Promise<number>`, que contam os dois estados (D19).
  - `pendingTotals(items: OutboxItem[], month: string): { monthExpenses: bigint; allExpenses: bigint }`,
    em centavos, só com os itens `pending` (D25).
  - `useOutbox(userId: string | undefined, workspaceId: string | undefined): OutboxItem[] | undefined`
    e `usePendingTotals(userId, workspaceId, month)`.
  - `backoffMs(attempts: number, random?: () => number): number`.
  - `syncOutbox(deps: SyncDeps): Promise<SyncResult>`, com
    `SyncDeps = { userId: string; send: (item: OutboxItem) => Promise<TransactionView>; onSent: (item: OutboxItem, view: TransactionView) => void; now?: () => number }`
    e `SyncResult = { status: 'done' | 'waiting' | 'unauthorized' | 'busy'; nextAt?: number }`.
  - `startOutboxSync(deps: SyncDeps & { onUnauthorized: () => void }): () => void`, que devolve a
    função que para tudo.
  - `applySentTransaction(queryClient: QueryClient, workspaceId: string, view: TransactionView): void`.
- Produces, por `@/shared/i18n`: `DEFAULT_TIMEZONE = 'America/Fortaleza'`,
  `localDate(now: Date, timeZone: string): string` (`AAAA-MM-DD`),
  `currentMonth(now: Date, timeZone: string): string` (`AAAA-MM`) e
  `addDays(date: string, days: number): string`.

**Pacote:** `dexie-react-hooks` ("a mais recente"), para o `useLiveQuery`. O Dexie avisa as outras
abas quando a fila muda, então a lista se atualiza nelas também.

**Fila (`outbox.ts`):**
- `enqueueTransaction` grava `{ id: payload.id, status: 'pending', attempts: 0, createdAt: now, sendAfter: now + SEND_DELAY_MS }`
  e dispara `window.dispatchEvent(new Event('liberdade:outbox'))`. As outras funções que mudam a
  fila disparam o mesmo evento.
- `cancelOutboxItem` e `syncOutbox` usam o mesmo lock por um helper interno, `lock.ts`, para não
  haver import circular entre `outbox.ts` e `sync.ts`.
- `replaceOutboxItem` apaga o item antigo e grava o novo numa transação só do Dexie.
  - O novo usa `payload.id` (um v7 novo) e mantém o `createdAt`, para não mudar de lugar na fila.
  - Ele volta a `pending`, com `attempts` 0 e `sendAfter` = `now + SEND_DELAY_MS`.
- `useOutbox` lê pelo índice `[userId+createdAt]`, filtra pelo `workspaceId` e devolve em ordem de
  criação. Sem `userId`, devolve `[]`: a fila de outra pessoa nunca aparece (Review Focus 2).
- `pendingTotals` soma os gastos `pending`: `monthExpenses` só os de `occurredOn` no mês, e
  `allExpenses` todos. O Início soma o primeiro aos gastos do mês e tira o segundo do saldo.

**Envio (`sync.ts`):**
- `syncOutbox` pega o lock `liberdade-outbox` com
  `navigator.locks.request(name, { ifAvailable: true }, cb)`. Sem lock, responde `busy`. Sem
  `navigator.locks`, uma trava em memória faz o mesmo papel dentro da aba.
- Com `navigator.onLine` `false`, não tenta: responde `waiting`.
- Ele lê os itens `pending` do `userId`, em ordem de `createdAt`, e envia um por vez. Ele para no
  primeiro item com `sendAfter` no futuro, para não passar ninguém na frente, e devolve `waiting`
  com `nextAt` igual a esse `sendAfter`.

| Resultado do `send` | O que a fila faz |
|---|---|
| resolve (201 ou 200) | apaga o item, chama `onSent` e segue |
| `ApiError` 401 | para; responde `unauthorized`; o item fica como está |
| `ApiError` com status 0 (rede), 408, 429, 5xx ou 403 `csrf.invalid` | `attempts` + 1, `sendAfter` = agora + `backoffMs(attempts)`; para e responde `waiting` |
| outro `ApiError` 4xx | `status` `needs-attention`, `problem` = `{ code, detail }`; segue para o próximo |

- `backoffMs(n, random)` = `min(2000 × 2^(n−1), 300000) × (0,8 + 0,4 × random())`.
- `startOutboxSync` roda `syncOutbox` agora e de novo a cada:
  - evento `online`, `focus` e `liberdade:outbox`;
  - `visibilitychange` com a página visível;
  - `nextAt`, com um `setTimeout`;
  - 1 s depois de um `busy`, porque a outra aba pode ter fechado.
  - Com `unauthorized`, chama `onUnauthorized` e espera o próximo evento.
- `applySentTransaction` atualiza o cache antes de buscar de novo, para o Início não piscar:
  - no `dashboard(ws, mês do occurredOn)`, soma o valor aos gastos;
  - em todo `dashboard(ws, …)` em cache, tira o valor do saldo;
  - na `transactions(ws, mês)`, se estiver em cache, acrescenta o item;
  - depois, `invalidateQueries({ queryKey: queryKeys.workspace(ws) })`.

**Datas:** `localDate` usa `Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' })`,
que já formata como `AAAA-MM-DD`. `addDays` faz a conta sobre a data, sem fuso.

- [ ] **Step 1: Escrever os testes (falhando)**

`dates.test.ts` (Review Focus 4):

| Chamada | Esperado |
|---|---|
| `localDate(new Date('2026-11-03T02:30:00Z'), 'America/Fortaleza')` | `'2026-11-02'` |
| `localDate(new Date('2026-12-01T02:59:59Z'), 'America/Fortaleza')` | `'2026-11-30'` |
| `currentMonth(new Date('2026-12-01T02:59:59Z'), 'America/Fortaleza')` | `'2026-11'` |
| `currentMonth(new Date('2026-12-01T03:00:00Z'), 'America/Fortaleza')` | `'2026-12'` |
| `addDays('2026-03-01', -1)` | `'2026-02-28'` |
| `addDays('2026-12-31', 1)` | `'2027-01-01'` |

`outbox.test.ts`:

| Teste | Esperado |
|---|---|
| `enqueueTransaction` com `now` 1000 | item `pending`, `attempts` 0, `sendAfter` 7000; o evento `liberdade:outbox` dispara 1 vez |
| `cancelOutboxItem` duas vezes | `true`, depois `false` |
| `replaceOutboxItem` | o id antigo some; o novo tem o `createdAt` antigo, `pending` e `attempts` 0 |
| `clearOutboxForUser('a')` com itens de `a` e `b` | sobram só os de `b` |
| `countOutboxForUser` com um `pending` e um `needs-attention` | 2 |
| `pendingTotals` com 12,34 em 2026-11, 5,00 em 2026-10 e 7,00 `needs-attention` em 2026-11, mês `'2026-11'` | `monthExpenses` 1234n; `allExpenses` 1734n |

`useOutbox.test.tsx`:

| Teste | Esperado |
|---|---|
| `useOutbox('a', 'ws1')` e depois `enqueueTransaction` de `a` | a lista passa de `[]` a 1 item, sem recarregar |
| itens de `a` na fila, `useOutbox('b', 'ws1')` | `[]` (Review Focus 2) |
| `useOutbox(undefined, 'ws1')` | `[]` |

`sync.test.ts`, com `send` e `onSent` como `vi.fn` e `now` fixo:

| Teste | Esperado |
|---|---|
| dois itens vencidos, `send` resolve | `send` chamado na ordem de criação; fila vazia; `onSent` 2 vezes; `done` |
| item com `sendAfter` no futuro | `send` não é chamado; `waiting` com `nextAt` = `sendAfter` |
| primeiro item com backoff no futuro e o segundo vencido | nenhum envio; `waiting` |
| `send` rejeita com status 0 | `attempts` 1; `sendAfter` = `now` + `backoffMs(1)`; `waiting` |
| `send` rejeita com 422 `transaction.invalid-id` no primeiro | o primeiro vira `needs-attention` com o `code`; o segundo é enviado |
| `send` rejeita com 401 | `unauthorized`; o item fica igual |
| `navigator.onLine` `false` | `send` não é chamado; `waiting` |
| itens de `a`, `syncOutbox({ userId: 'b' })` | `send` não é chamado; os itens de `a` ficam (Review Focus 2) |
| duas chamadas ao mesmo tempo, com um `navigator.locks` falso que concede um lock por vez | uma responde `busy`; cada id é enviado 1 vez, em ordem (Review Focus 5) |
| a mesma corrida sem `navigator.locks` | o mesmo resultado, pela trava em memória |
| `cancelOutboxItem` chamado enquanto o `send` do mesmo item está pendente | resolve `false` só depois que o `send` resolve; o item é enviado 1 vez; `onSent` chamado (D34) |
| `send` real (`postTransaction`) contra o MSW | o `mockState` guarda o lançamento; `onSent` recebe o `TransactionView` |
| `backoffMs` | `(1, () => 0.5)` → 2000; `(2, () => 0.5)` → 4000; `(20, () => 0.5)` → 300000; `(1, () => 0)` → 1600; `(1, () => 1)` → 2400 |
| `startOutboxSync` com timers falsos | item enfileirado em t0 não sai em t0 + 5999 ms e sai em t0 + 6000 ms |
| `startOutboxSync` e o evento `online` | dispara um envio |
| `applySentTransaction` de 12,34 em 2026-11, com o dashboard de 2026-11 em gastos 10,00 e saldo 90,00 | gastos 22,34; saldo 77,66; a lista do mês ganha o item; as consultas de `['ws', ws]` ficam inválidas |
| `applySentTransaction` de um gasto de 2026-10 com só o dashboard de 2026-11 em cache | gastos de 2026-11 iguais; saldo menor |

Nos timers falsos, finja só `setTimeout`, `clearTimeout` e `Date`
(`vi.useFakeTimers({ toFake: [...] })`), para não travar o `fake-indexeddb`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/shared/offline src/shared/i18n`
Expected: FAIL; `outbox`, `sync`, `useOutbox` e `dates` não existem.

- [ ] **Step 3: Implementar**

Instale o `dexie-react-hooks` e escreva os módulos acima. Exporte tudo pelos barris
`@/shared/offline` e `@/shared/i18n`.

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --filter web exec tsc -b; corepack pnpm --filter web lint; corepack pnpm --filter web test:coverage`
Expected: sem erros; `src/shared/**` com 90% ou mais.

- [ ] **Step 5: Commit**

```bash
git add web pnpm-lock.yaml
git commit -m "feat(web): offline outbox with ordered, exclusive sync"
```

---

### Task 10: Entrar, boas-vindas, navegação, Mais e PWA

**Files:**
- Modify: `web/package.json`, `pnpm-lock.yaml`, `web/vite.config.ts`, `web/index.html`,
  `web/src/main.tsx`, `web/src/vite-env.d.ts`, `web/src/app/routes.tsx`,
  `web/src/app/routes.test.tsx`, `web/src/shared/i18n/messages.pt-BR.ts`,
  `web/src/shared/i18n/messages.test.ts`, `web/src/mocks/handlers.ts`, `web/src/mocks/data.ts`
- Modify: `web/src/features/auth/SignInPage.tsx`, `web/src/features/auth/SignInPage.test.tsx`
- Create em `web/src/features/auth/`: `google.ts`, `PasskeyButton.tsx`, `SignOutButton.tsx` e os
  testes `PasskeyButton.test.tsx`, `SignOutButton.test.tsx`
- Create: `web/src/features/onboarding/WelcomePage.tsx` e `WelcomePage.test.tsx`
- Create em `web/src/app/`: `RootRedirect.tsx`, `SessionLayout.tsx`, `WorkspaceLayout.tsx`,
  `AppShell.tsx`, `MorePage.tsx`, `SessionLayout.test.tsx`
- Create em `web/src/shared/ui/`: `ConfirmDialog.tsx`, `useDocumentTitle.ts`, `index.ts` e
  `ConfirmDialog.test.tsx`
- Create: `web/src/shared/i18n/names.ts` e `names.test.ts`; Modify: `web/src/shared/i18n/index.ts`
- Create (esqueleto que a Task 11 preenche): `web/src/features/dashboard/HomePage.tsx`,
  `web/src/features/transactions/NewExpensePage.tsx`,
  `web/src/features/transactions/TransactionsPage.tsx`
- Create: `web/public/icon.svg` e os PNGs gerados a partir dele

**Interfaces:**
- Consumes: de `@/shared/api`, `API_URL`, `postJson`, `useMe`, `useCurrentWorkspace`,
  `useCreateWorkspace`, `queryKeys`, `ApiError` e `postTransaction`; de `@/shared/offline`,
  `clearSession`, `useOnline`, `startOutboxSync`, `applySentTransaction`, `countOutboxForUser` e
  `clearOutboxForUser` (Tasks 8 e 9); as rotas de passkey da Task 4.
- Produces:
  - por `@/shared/ui`, `ConfirmDialog({ open, title, body, confirmLabel, cancelLabel, onConfirm, onCancel })`
    e `useDocumentTitle(title: string)`, que põe `"<title> · Liberdade"` no `document.title`;
  - as rotas da tabela abaixo; a Task 11 preenche `HomePage`, `NewExpensePage` e
    `TransactionsPage`, que aqui só têm o `<h1>`;
  - por `features/auth/google.ts`,
    `startGoogleSignIn(queryClient: QueryClient, assign?: (url: string) => void): Promise<void>`;
  - por `@/shared/i18n`, `firstName(displayName: string): string`, a primeira palavra sem os
    espaços das pontas (D5). As boas-vindas e a lista de lançamentos da Task 11 usam.

**Pacotes** ("a mais recente"): `@simplewebauthn/browser`, `radix-ui` e `workbox-window`; em dev,
`vite-plugin-pwa` 2.0.0 e `workbox-build`. A 2.0.0 aceita o Vite 8 e pede os dois `workbox-*` como
peers.

**Rotas:**

| Caminho | Elemento | Regra |
|---|---|---|
| `/` | `RootRedirect` | sem sessão → `/entrar`; sem família → `/boas-vindas`; com família → `/inicio` |
| `/entrar` | `SignInPage` | pública |
| (layout) | `SessionLayout` | precisa de `me`; 401 → `/entrar`; liga o envio da fila |
| `/boas-vindas` | `WelcomePage` | com família → `/inicio` |
| (layout, dentro do `SessionLayout`) | `WorkspaceLayout` | sem família → `/boas-vindas` |
| `/lancar` | `NewExpensePage` | tela cheia, sem a navegação |
| (layout, dentro do `WorkspaceLayout`) | `AppShell` | navegação inferior |
| `/inicio`, `/lancamentos`, `/mais` | `HomePage`, `TransactionsPage`, `MorePage` | |
| `*` | `NotFoundPage` | |

- Enquanto `me` carrega, inclusive durante a restauração do cache persistido, os layouts mostram
  "Carregando…" e não redirecionam.
- Erro sem dados, que não seja 401, mostra `common.error` e o botão `common.retry`. Offline e com
  cache, a tela abre com o cache.
- O `SessionLayout` roda `startOutboxSync` num `useEffect`, com:
  - `userId: me.user.id`;
  - `send: item => postTransaction(item.workspaceId, item.payload)`;
  - `onSent: (item, view) => applySentTransaction(queryClient, item.workspaceId, view)`;
  - `onUnauthorized: () => queryClient.invalidateQueries({ queryKey: queryKeys.me })`. O 401 de
    `/v1/me` aciona o `onUnauthorized` global da Task 8.
  - A limpeza do efeito para o envio. Ele religa quando o `userId` muda.
- `AppShell`: `<nav aria-label="Navegação principal">` com Início, Lançamentos, Novo gasto (+) e
  Mais (D4), em `NavLink`, com `aria-current="page"` na rota atual. O (+) leva a `/lancar`.

**Entrar (`SignInPage`):**
- `<h1>` "Liberdade", o slogan e os botões "Entrar com Google" e "Entrar com passkey". O segundo só
  aparece com `browserSupportsWebAuthn()`. Embaixo, "Sem senha. Nunca pedimos a senha do seu
  banco." Sem os links de privacidade e termos (D2).
- `startGoogleSignIn` roda `clearSession` e depois `assign(API_URL + '/oauth2/authorization/google')`.
  O padrão de `assign` é `window.location.assign`. Com isso, a sessão nova começa com o cache
  vazio (Review Focus 2).
- Passkey:
  - `postJson('/webauthn/authenticate/options')`, depois
    `startAuthentication({ optionsJSON })` e `postJson('/login/webauthn', credencial)`;
  - no sucesso, `clearSession`, e então `navigate('/')`;
  - o botão fica desabilitado do clique até a resposta (toque duplo).
  - `NotAllowedError` (a pessoa cancelou) não mostra nada. 429 mostra `signIn.error.rateLimited`. Outro
    erro mostra `signIn.error.passkey`.
- `?erro=login` e `?erro=limite` mostram a mensagem num `role="alert"`.
- Offline, os dois botões ficam desabilitados e aparece `signIn.offline`.

**Boas-vindas (`WelcomePage`):**
- `<h1>` com `welcome.greeting`, com `firstName(displayName)` (D5).
- Campo "Nome da família", preenchido com `suggestedWorkspaceName`, com `maxLength` 60 e a dica
  "Dá para mudar depois.".
- A lista "Ao começar, criamos:" mostra `welcome.account` e `welcome.categories`.
- "Começar" manda o nome sem espaços nas pontas.
  - Ele fica desabilitado com o nome vazio, offline (com `welcome.offline`) e enquanto a mutação
    roda.
  - No sucesso (201 ou 409), vai para `/lancar`. Num erro, mostra `welcome.error` num `role="alert"`.

**Mais (`MorePage`):**
- `displayName`, o nome da família, `PasskeyButton` e `SignOutButton`.
- `PasskeyButton`:
  - roda `postJson('/webauthn/register/options')`, depois `startRegistration({ optionsJSON })` e
    `postJson('/webauthn/register', { publicKey: { credential, label } })`, com o `label` vindo de
    `passkey.defaultLabel`;
  - no sucesso, mostra "Passkey criada." num `role="status"` e invalida `me`;
  - com `passkeyCreatedAt`, mostra `passkey.createdOn` com a data em `dateStyle: 'long'` no fuso da
    família;
  - offline, fica desabilitado, com `passkey.offline`.
- `SignOutButton` (D19), desabilitado offline com `signOut.offline`:
  - Sem itens na fila (`countOutboxForUser`), sai direto.
  - Com itens, abre o `ConfirmDialog` com `signOut.pendingTitle` e `signOut.pendingBody`.
  - Sair é `postJson('/logout')`, depois `clearOutboxForUser`, `navigate('/entrar')` e
    `clearSession`, nesta ordem. Uma falha mostra `signOut.failed`, e a fila fica.
- `ConfirmDialog` usa o `AlertDialog` do `radix-ui`, com o foco inicial em `cancelLabel`.

**PWA (D27):**
- `VitePWA` no `vite.config.ts`, com:
  - `registerType: 'autoUpdate'` e `injectRegister: null`;
  - `workbox.navigateFallback: '/index.html'` e `workbox.globIgnores: ['**/mockServiceWorker.js']`;
  - o manifesto: `name` e `short_name` "Liberdade", `description` com o slogan, `lang: 'pt-BR'`,
    `start_url: '/'`, `display: 'standalone'`, e `theme_color` e `background_color` com as cores
    primária e de fundo do `index.css`.
- Com `VITE_API_MOCKS` diferente de `'true'`, o `main.tsx` chama `registerSW({ immediate: true })`
  de `virtual:pwa-register`. Com mocks, só o worker do MSW é registrado. O `vite-env.d.ts` ganha
  `/// <reference types="vite-plugin-pwa/client" />`.
- Ícones:
  - `public/icon.svg`: um "L" branco sobre a cor primária;
  - os PNGs saem uma vez com
    `corepack pnpm --filter web dlx @vite-pwa/assets-generator --preset minimal-2023 public/icon.svg`
    ("a mais recente") e são versionados;
  - o `index.html` ganha `<link rel="icon" href="/icon.svg">`, o `apple-touch-icon` e o
    `theme-color`.

**Mocks:** `handlers.ts` ganha as quatro rotas de passkey da Task 4.
- `/webauthn/register/options` e `/webauthn/authenticate/options` devolvem opções fixas.
- `POST /webauthn/register` grava `passkeyCreatedAt`.
- `POST /login/webauthn` devolve `{ "redirectUrl": "/", "authenticated": true }`.
- Os testes trocam o `@simplewebauthn/browser` por `vi.mock`.

**Mensagens:** `home.title` vira `app.name`, e `home.tagline` vira `signIn.tagline`. O
`messages.test.ts` da Task 2 passa a usar `app.name`. Entram:

| id | Texto |
|---|---|
| `signIn.google` | Entrar com Google |
| `signIn.passkey` | Entrar com passkey |
| `signIn.noPassword` | Sem senha. Nunca pedimos a senha do seu banco. |
| `signIn.error.login` | Não deu para entrar. Tente de novo. |
| `signIn.error.rateLimited` | Muitas tentativas. Espere um minuto e tente de novo. |
| `signIn.error.passkey` | Não deu para entrar com a passkey. Tente de novo ou entre com Google. |
| `signIn.offline` | Sem conexão. Entrar precisa de internet. |
| `welcome.greeting` | Olá, {name}! 👋 |
| `welcome.familyName` | Nome da família |
| `welcome.changeLater` | Dá para mudar depois. |
| `welcome.weCreate` | Ao começar, criamos: |
| `welcome.account` | Conta "Carteira" |
| `welcome.categories` | Categorias do dia a dia |
| `welcome.start` | Começar |
| `welcome.error` | Não deu para criar a família. Tente de novo. |
| `welcome.offline` | Criar a família precisa de internet. |
| `nav.label` | Navegação principal |
| `nav.home`, `nav.transactions`, `nav.newExpense`, `nav.more` | Início, Lançamentos, Novo gasto, Mais |
| `passkey.create` | Criar passkey neste aparelho |
| `passkey.defaultLabel` | Este aparelho |
| `passkey.created` | Passkey criada. |
| `passkey.createdOn` | Última passkey criada em {date} |
| `passkey.failed` | A passkey não foi criada. Tente de novo. |
| `passkey.offline` | Criar passkey precisa de internet. |
| `signOut.button` | Sair |
| `signOut.offline` | Sair precisa de internet. |
| `signOut.failed` | Não deu para sair. Tente de novo. |
| `signOut.pendingTitle` | Sair deste aparelho? |
| `signOut.pendingBody` | `{count, plural, one {Há # gasto aguardando sinal. Sair apaga esse gasto deste aparelho.} other {Há # gastos aguardando sinal. Sair apaga esses gastos deste aparelho.}}` |
| `signOut.confirm`, `signOut.cancel` | Sair mesmo assim, Cancelar |
| `common.loading` | Carregando… |
| `common.error` | Não deu para carregar. Tente de novo. |
| `common.retry` | Tentar de novo |

- [ ] **Step 1: Escrever os testes (falhando)**

`routes.test.tsx` (MSW, `renderWithProviders`):

| Teste | Esperado |
|---|---|
| `/` sem família | vai para `/boas-vindas` |
| `/` com família | vai para `/inicio` |
| `/` com `mockState.signedIn = false` | vai para `/entrar` |
| `/inicio` sem família | vai para `/boas-vindas` |
| `/inicio` | `nav` "Navegação principal" com 4 links; "Início" com `aria-current="page"`; `document.title` "Início · Liberdade" |
| `/lancar` com família | `<h1>` "Novo gasto", sem a `nav` |
| caminho desconhecido | "Página não encontrada." e o link "Voltar ao início" |

Os casos "com família", aqui e nas outras telas, chamam `seedMockFamily()` antes do `render`.

`names.test.ts`: `firstName('Ana Teste')` → `'Ana'`; `firstName('  Ana  ')` → `'Ana'`;
`firstName('')` → `''`.

`SessionLayout.test.tsx`: com um item vencido de Ana Teste na fila, abrir `/inicio` envia o item
(o `mockState` passa a ter o lançamento) e a fila fica vazia.

`SignInPage.test.tsx` (`vi.mock('./google')` e `vi.mock('@simplewebauthn/browser')`):

| Teste | Esperado |
|---|---|
| `/entrar?erro=login` | `alert` "Não deu para entrar. Tente de novo." |
| `/entrar?erro=limite` | `alert` "Muitas tentativas. Espere um minuto e tente de novo." |
| clicar "Entrar com Google" | `startGoogleSignIn` chamado 1 vez |
| passkey com sucesso | `POST /login/webauthn` recebe a credencial do mock; a rota vira `/` |
| dois cliques rápidos em "Entrar com passkey" | `startAuthentication` chamado 1 vez |
| `startAuthentication` rejeita com `NotAllowedError` | nenhum `alert`; o botão volta a ficar ativo |
| `POST /login/webauthn` responde 429 | `alert` de limite |
| offline | os dois botões desabilitados; "Sem conexão. Entrar precisa de internet." |
| `browserSupportsWebAuthn` falso | sem o botão de passkey |

`google.test.ts`: com `['me']` no cache, `startGoogleSignIn(qc, assign)` deixa o cache vazio e
chama `assign` 1 vez com `http://localhost:8080/oauth2/authorization/google`.

`WelcomePage.test.tsx`:

| Teste | Esperado |
|---|---|
| abrir | `<h1>` "Olá, Ana! 👋"; campo "Nome da família" com "Família Teste"; os dois itens da lista |
| "Começar" | o POST recebe `{"name":"Família Teste",…}`; a rota vira `/lancar` |
| dois cliques rápidos em "Começar" | 1 POST (Review Focus 1) |
| nome "   " | "Começar" desabilitado |
| POST responde 409 | a rota vira `/lancar` |
| POST responde 500 | `alert` "Não deu para criar a família. Tente de novo."; o botão volta a ficar ativo |
| offline | "Começar" desabilitado; "Criar a família precisa de internet." |
| a pessoa já tem família | a rota vira `/inicio` |

`PasskeyButton.test.tsx`:

| Teste | Esperado |
|---|---|
| criar | `POST /webauthn/register` recebe `label` "Este aparelho"; `status` "Passkey criada." |
| `passkeyCreatedAt` `2026-11-02T13:00:00Z`, fuso `America/Fortaleza` | "Última passkey criada em 2 de novembro de 2026" |
| `startRegistration` rejeita | "A passkey não foi criada. Tente de novo." |
| offline | desabilitado; "Criar passkey precisa de internet." |

`SignOutButton.test.tsx`:

| Teste | Esperado |
|---|---|
| sem fila | `POST /logout`; a rota vira `/entrar`; `getQueryData(['me'])` `undefined` |
| 2 itens na fila | diálogo "Sair deste aparelho?" com "Há 2 gastos aguardando sinal. Sair apaga esses gastos deste aparelho." |
| 1 item e "Cancelar" | diálogo fecha; sem `POST /logout`; a fila continua com 1 |
| 1 item e "Sair mesmo assim" | `POST /logout`; fila vazia; a rota vira `/entrar` |
| `/logout` responde 500 | "Não deu para sair. Tente de novo."; a fila continua |
| offline | desabilitado; "Sair precisa de internet." |

`ConfirmDialog.test.tsx`: abre com o foco em "Cancelar"; `Escape` chama `onCancel`; o botão de
confirmar chama `onConfirm`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/app src/features src/shared/ui src/shared/i18n`
Expected: FAIL; as telas e as rotas novas não existem.

- [ ] **Step 3: Implementar**

Instale os pacotes, gere os ícones e escreva as telas, os layouts, as rotas, os handlers de passkey
e a configuração do PWA descritos acima.

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --filter web exec tsc -b; corepack pnpm --filter web lint; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build`
Expected: sem erros; cobertura dentro dos limites. O `web/dist/` tem `sw.js` e
`manifest.webmanifest`, com `"lang":"pt-BR"`, e o `sw.js` não cita `mockServiceWorker`.

- [ ] **Step 5: Commit**

```bash
git add web pnpm-lock.yaml
git commit -m "feat(web): sign in, welcome flow, navigation and installable PWA"
```

---

### Task 11: Novo gasto, Início e Lançamentos

**Files:**
- Modify: `web/package.json`, `pnpm-lock.yaml`
- Modify: `web/src/app/providers.tsx`, `web/src/app/WorkspaceLayout.tsx`,
  `web/src/shared/testing/index.tsx`, `web/src/shared/i18n/messages.pt-BR.ts`
- Modify: `web/src/shared/api/hooks.ts`, `web/src/shared/api/index.ts`,
  `web/src/shared/api/hooks.test.tsx`
- Create em `web/src/shared/ui/`: `Toast.tsx`, `Sheet.tsx`, `Toast.test.tsx`, `Sheet.test.tsx`;
  Modify: `index.ts`
- Modify (os esqueletos da Task 10): `web/src/features/dashboard/HomePage.tsx`,
  `web/src/features/transactions/NewExpensePage.tsx`,
  `web/src/features/transactions/TransactionsPage.tsx`
- Create em `web/src/features/transactions/`: `Keypad.tsx`, `CategoryPicker.tsx`,
  `ContextSheet.tsx`, `TransactionRow.tsx`, `listItems.ts`
- Test: `web/src/features/dashboard/HomePage.test.tsx`; em `web/src/features/transactions/`,
  `NewExpensePage.test.tsx`, `TransactionsPage.test.tsx` e `listItems.test.ts`

**Interfaces:**
- Consumes:
  - de `@/shared/money` (Task 2), `formatBRL`, `parseCents`, `centsToApi`, `appendDigit`,
    `appendDoubleZero` e `removeLastDigit`;
  - de `@/shared/api` (Task 8), `useMe`, `useCurrentWorkspace`, `useAccounts`, `useCategories`,
    `useTransactions`, `useDashboard`, `queryKeys` e os tipos;
  - de `@/shared/offline` (Tasks 8 e 9), `useOnline`, `enqueueTransaction`, `cancelOutboxItem`,
    `discardOutboxItem`, `replaceOutboxItem`, `useOutbox`, `usePendingTotals` e `OutboxItem`;
  - de `@/shared/i18n` (Tasks 9 e 10), `localDate`, `currentMonth`, `addDays` e `firstName`;
  - de `@/shared/ui` (Task 10), `ConfirmDialog` e `useDocumentTitle`; as rotas e o
    `WorkspaceLayout` da Task 10; `seedMockFamily` e `mockState` de `@/mocks`.
- Produces:
  - por `@/shared/ui`, `ToastProvider({ children })`,
    `useToast(): { show(toast: { message: string; action?: { label: string; altText: string; onClick: () => void } }): void }`
    e `Sheet({ open, onOpenChange, title, children })`;
  - por `@/shared/api`,
    `prefetchWorkspace(queryClient: QueryClient, workspaceId: string, month: string): Promise<void>`;
  - por `listItems.ts`, `mergeTransactions(server: TransactionView[] | undefined, outbox: OutboxItem[]): ListItem[]`
    e `groupByDay(items: ListItem[], today: string): DayGroup[]`, com
    `ListItem = TransactionView & { createdAtMs: number; outbox?: OutboxItem }` e
    `DayGroup = { date: string; items: ListItem[] }`;
  - os textos que o E2E da Task 12 procura: as teclas "1" a "9", "0" e "00", o rádio "🛒 Mercado",
    "Salvar", "Gasto salvo" e "Aguardando sinal".

**Pacote:** `uuid` ("a mais recente"), pelo `v7`. A API recusa id que não seja UUIDv7.

**Avisos e folhas (`@/shared/ui`):**
- `ToastProvider` usa o `Toast` do `radix-ui`, com `duration` 5000 e a região
  `toast.region`. Um aviso por vez: o novo substitui o atual.
  - A ação vira `Toast.Action` com o `altText`. Clicar nela chama `onClick` e fecha o aviso.
  - Ele entra no `AppProviders`, dentro do `IntlProvider` e acima do roteador, para o aviso
    sobreviver à troca de rota. O `renderWithProviders` ganha o mesmo provedor.
- `Sheet` é um `Dialog` do `radix-ui` preso à borda de baixo, com `radius-sheet`, o título no
  `Dialog.Title` e o botão `sheet.close`.

**Aquecimento offline:** `prefetchWorkspace` busca as contas, as categorias `EXPENSE` e `INCOME`, e o
dashboard e os lançamentos do mês. O `WorkspaceLayout` chama a função num `useEffect` assim que a
família é conhecida, com `currentMonth(new Date(), ws.timezone)`. Assim, quem lança logo depois das
boas-vindas já tem tudo em cache quando o sinal cai.

**Novo gasto (`NewExpensePage`, wireframe 3):**
- Cabeçalho: link para `/inicio` com `aria-label` `expense.back` e `<h1>` `nav.newExpense`.
  `useDocumentTitle` com o mesmo texto.
- Valor: `<output aria-label="Valor" aria-live="polite">` com `formatBRL(cents)`.
- `Keypad`: botões 1 a 9, ⌫ (`aria-label` `expense.delete`), 0 e 00. Eles usam `appendDigit`,
  `removeLastDigit` e `appendDoubleZero`.
  - As teclas físicas de dígito e o Backspace também valem, por um `keydown` no `document`. Ele
    ignora a tecla com o foco num campo, com uma folha aberta e com Ctrl, Alt ou Meta.
- `CategoryPicker`: rádios nativos num `<fieldset>` com a `<legend>` `expense.category`, desenhados
  como chips "{icon} {name}".
  - Os chips são as 6 primeiras categorias `EXPENSE`, na ordem da API, mais a escolhida quando
    ela não está entre as 6.
  - O botão `expense.moreCategories` abre a folha `expense.allCategories`, com todas as categorias
    como botões. Escolher uma fecha a folha.
  - Nenhuma vem marcada de início.
- Linha de contexto: um botão com `expense.context` e o ✎ com `aria-hidden`. Ele abre o
  `ContextSheet` (`expense.contextSheetTitle`), com:
  - os rádios de data `common.today`, `common.yesterday` e `expense.otherDate`, este com um
    `<input type="date">`;
  - os rádios de conta;
  - o texto `expense.member`, sem escolha (D1).
- O estado da data é `'today' | 'yesterday' | 'AAAA-MM-DD'`. Hoje e Ontem viram data **na hora de
  salvar**, com `localDate(new Date(), ws.timezone)` e `addDays(…, -1)` (Review Focus 4).
  - A outra data aparece no contexto com `{ day: 'numeric', month: 'short', timeZone: 'UTC' }`
    sobre `AAAA-MM-DDT00:00:00Z`.
- Conta: a última usada, lida de `localStorage` em `liberdade:last-account:<ws>`, se ela ainda
  existir; senão, a primeira. Ela é gravada depois de salvar. Todo acesso ao `localStorage` fica
  num `try/catch`.
- Descrição: campo `expense.description`, com `maxLength` 140. Vai sem os espaços das pontas, e
  vazia vira `null`.
- "Salvar" fica ativo com `cents > 0n`, categoria e conta escolhidas, e nenhum salvamento em curso.
  Um `useRef` garante um item só no toque duplo (Review Focus 1). Salvar:
  1. monta `{ id: v7(), kind: 'EXPENSE', amount: { amount: centsToApi(cents), currency: ws.currency }, occurredOn, accountId, categoryId, description }`;
  2. chama `enqueueTransaction({ userId: me.user.id, workspaceId: ws.id, payload })`;
  3. mostra o aviso `expense.saved` com a ação `expense.undo` (`altText` `expense.undoAlt`);
  4. vai para `/inicio`.
- Desfazer (D34): `cancelOutboxItem(id)`. Com `true`, mostra o aviso `expense.undone`; com
  `false`, mostra `expense.undoTooLate`.
- `?corrigir=<id>`: com o item na fila (`useOutbox`), o formulário abre preenchido com os dados
  dele, inclusive a data exata. Salvar chama `replaceOutboxItem(id, payload)` com um `v7()` novo
  (D24). Sem o item, o formulário abre vazio.
- Sem categorias (a lista vazia, ou offline sem cache), aparece `expense.noCategories` e "Salvar"
  fica desabilitado.

**Início (`HomePage`, wireframe 4, D6):**
- `<h1>` `nav.home`. O mês é `currentMonth(new Date(), ws.timezone)`, e o nome dele sai de
  `AAAA-MM-01T00:00:00Z` com `{ month: 'long', timeZone: 'UTC' }`.
- `home.expensesIn` = `parseCents(dashboard.expenses.amount)` + `monthExpenses`;
  `home.totalBalance` = `parseCents(dashboard.balance.amount)` − `allExpenses`, com
  `usePendingTotals` (D25).
- Sem os dados do dashboard: carregando mostra `common.loading`; pausado por estar offline mostra
  `home.offlineNoData`; com erro, `common.error` e `common.retry`.
- Abaixo:
  - a dica `home.addAccountsHint`;
  - o card `home.firstSteps` com `home.passkeyStep` e o link `home.passkeyStepLink` para `/mais`.
    O ✕ (`home.dismissFirstSteps`) esconde o card e grava `liberdade:first-steps-dismissed` no
    `localStorage`. Com `passkeyCreatedAt`, o card não aparece;
  - com itens `needs-attention`, um link `home.needsAttention` para `/lancamentos`.

**Lançamentos (`TransactionsPage`, wireframe 6):**
- `<h1>` `nav.transactions`. Offline, aparece `transactions.offline` num `role="status"`.
- `mergeTransactions` junta os lançamentos do mês da API com **toda** a fila do usuário nesta
  família, de qualquer mês, para nenhum item recusado ficar escondido.
  - Um id repetido fica com o da API.
  - A ordem é `occurredOn`, depois `createdAtMs`, depois `id`, tudo decrescente.
- `groupByDay` agrupa por `occurredOn`. Os títulos são `common.today`, `common.yesterday` ou a data
  com `{ day: 'numeric', month: 'long', timeZone: 'UTC' }`.
- `TransactionRow`:
  - primeira linha: ícone e nome da categoria, com `transactions.uncategorized` quando a
    categoria não está em cache; o valor é `formatBRL(-cents)` no gasto e `'+' + formatBRL(cents)`
    na receita;
  - segunda linha: o nome da conta e, quando `memberId === ws.membershipId` ou o item vem da fila,
    `firstName(me.user.displayName)`;
  - item `pending`: o selo `transactions.pending` (D3);
  - item `needs-attention`: o botão `transactions.needsAttention`. Ele abre a folha
    `transactions.attentionTitle`, com `problem.detail` (ou `transactions.attentionFallback`) e:
    - o link `transactions.fix` para `/lancar?corrigir=<id>`;
    - o botão `transactions.discard`, que abre o `ConfirmDialog` (`transactions.discardTitle`,
      `transactions.discardBody`, `transactions.discard` e `transactions.discardNo`). Confirmar
      chama `discardOutboxItem`.
- Editar e apagar um lançamento salvo ficam para o Plano 3; a tela não oferece nenhum dos dois.
- Sem nenhum item, aparece `transactions.empty`. Com erro e sem dados da API, aparecem
  `common.error` e `common.retry`, e a fila continua na lista.

**Mensagens:**

| id | Texto |
|---|---|
| `common.today`, `common.yesterday` | Hoje, Ontem |
| `toast.region` | Notificações |
| `sheet.close` | Fechar |
| `expense.back` | Voltar |
| `expense.amount` | Valor |
| `expense.delete` | Apagar |
| `expense.category` | Categoria |
| `expense.moreCategories` | Mais |
| `expense.allCategories` | Todas as categorias |
| `expense.context` | {date} · {account} · Eu |
| `expense.contextSheetTitle` | Data e conta |
| `expense.date`, `expense.account`, `expense.otherDate` | Data, Conta, Outra data |
| `expense.member` | Quem gastou: Eu |
| `expense.description` | Descrição (opcional) |
| `expense.save` | Salvar |
| `expense.saved` | Gasto salvo |
| `expense.undo` | Desfazer |
| `expense.undoAlt` | Desfazer o último gasto |
| `expense.undone` | Gasto desfeito |
| `expense.undoTooLate` | Este gasto já foi salvo e não dá mais para desfazer. |
| `expense.noCategories` | Abra o app com internet uma vez para carregar suas categorias. |
| `home.expensesIn` | Gastos em {month} |
| `home.totalBalance` | Saldo total |
| `home.addAccountsHint` | Adicione suas contas para ver o saldo real. |
| `home.offlineNoData` | Sem conexão. Os totais aparecem quando o sinal voltar. |
| `home.firstSteps` | Primeiros passos |
| `home.dismissFirstSteps` | Fechar primeiros passos |
| `home.passkeyStep` | Crie uma passkey para entrar mais rápido, sem senha. |
| `home.passkeyStepLink` | Criar passkey |
| `home.needsAttention` | `{count, plural, one {# gasto precisa de atenção} other {# gastos precisam de atenção}}` |
| `transactions.offline` | Sem conexão. Você pode lançar; os gastos saem quando o sinal voltar. |
| `transactions.pending` | Aguardando sinal |
| `transactions.needsAttention` | Precisa de atenção |
| `transactions.empty` | Nenhum lançamento neste mês. |
| `transactions.uncategorized` | Sem categoria |
| `transactions.attentionTitle` | Este gasto não foi salvo |
| `transactions.attentionFallback` | O servidor recusou este gasto. |
| `transactions.fix`, `transactions.discard`, `transactions.discardNo` | Corrigir, Descartar, Manter |
| `transactions.discardTitle` | Descartar este gasto? |
| `transactions.discardBody` | Ele não foi salvo e vai sumir deste aparelho. |

- [ ] **Step 1: Escrever os testes (falhando)**

Todos os testes de tela chamam `seedMockFamily()` antes do `render`. Onde a data importa, use
`vi.useFakeTimers({ toFake: ['Date'] })` e `vi.setSystemTime(…)`; o fuso da família é
`America/Fortaleza`.

`Toast.test.tsx`: `show` põe a mensagem na região "Notificações"; clicar na ação chama `onClick` e
fecha o aviso; um segundo `show` deixa só o aviso novo na tela.

`Sheet.test.tsx`: aberta, é um `dialog` com o nome do título; `Escape` e "Fechar" chamam
`onOpenChange(false)`.

`hooks.test.tsx` ganha: depois de `prefetchWorkspace(qc, ws, '2026-11')`, as chaves `accounts`,
`categories` (`EXPENSE` e `INCOME`), `dashboard` e `transactions` do mês têm dados.

`NewExpensePage.test.tsx` (rota `/lancar`):

| Teste | Esperado |
|---|---|
| abrir | `<h1>` "Novo gasto"; `status` "Valor" com "R$ 0,00"; 6 rádios de categoria e o botão "Mais"; o botão "Hoje · Carteira · Eu"; "Salvar" desabilitado |
| teclas 1, 2, 3, 4, depois "00", depois "Apagar" | "R$ 12,34", depois "R$ 1.234,00", depois "R$ 123,40" |
| teclado físico `1234{Backspace}` | "R$ 1,23" |
| digitar "12" na descrição | o valor continua "R$ 0,00" |
| valor sem categoria, e categoria sem valor | "Salvar" desabilitado nos dois |
| 1234, "🛒 Mercado", descrição "  pão  ", "Salvar" | 1 item na fila, com `amount` `{"amount":"12.34","currency":"BRL"}`, a categoria Mercado, a conta Carteira, `occurredOn` de hoje e `description` "pão"; a rota vira `/inicio`; o aviso "Gasto salvo" com "Desfazer" |
| dois cliques rápidos em "Salvar" | 1 item na fila (Review Focus 1) |
| hora `2026-12-01T02:30:00Z`, salvar com "Hoje" | `occurredOn` `'2026-11-30'` (Review Focus 4) |
| a mesma hora, com "Ontem" | `occurredOn` `'2026-11-29'` |
| "Outra data" com `2026-10-20` | o contexto mostra "20 de out."; `occurredOn` `'2026-10-20'` |
| "Mais", depois "🐾 Pets" | a folha fecha; 7 rádios; "🐾 Pets" marcado |
| duas contas (`server.use`), escolher a segunda e salvar; abrir `/lancar` de novo | o contexto mostra a segunda conta |
| "Desfazer" no aviso | aviso "Gasto desfeito"; a fila fica vazia |
| apagar o item do `db` (como se já tivesse saído) e clicar "Desfazer" | aviso "Este gasto já foi salvo e não dá mais para desfazer." |
| `?corrigir=<id>` de um item `needs-attention` de 12,34 em Mercado | "R$ 12,34" e "🛒 Mercado" marcado; trocar para 500 e salvar deixa 1 item, com id novo, "5.00", `pending` e o `createdAt` antigo |
| offline e sem cache de categorias | "Abra o app com internet uma vez para carregar suas categorias."; "Salvar" desabilitado |

`HomePage.test.tsx` (rota `/inicio`, hora `2026-11-15T15:00:00Z`; o dashboard vem de `server.use`
com gastos 10,00 e saldo 90,00):

| Teste | Esperado |
|---|---|
| fila com 12,34 `pending` em novembro e 7,00 `needs-attention` | "Gastos em novembro" com "R$ 22,34"; "Saldo total" com "R$ 77,66"; o link "1 gasto precisa de atenção" leva a `/lancamentos` |
| fila vazia | "R$ 10,00" e "R$ 90,00"; sem o link de atenção |
| "Primeiros passos" | o card com o link "Criar passkey" para `/mais` |
| ✕ do card, depois abrir a página de novo | o card não volta |
| `passkeyCreatedAt` preenchido | sem o card |
| offline e sem cache | "Sem conexão. Os totais aparecem quando o sinal voltar." |
| dashboard responde 500 | "Não deu para carregar. Tente de novo."; "Tentar de novo" busca de novo |

`listItems.test.ts`:

| Teste | Esperado |
|---|---|
| um item da API e um da fila com o mesmo id | 1 item, sem `outbox` |
| datas `2026-11-14` e `2026-11-15`, e duas no mesmo dia com `createdAt` diferentes | ordem: 15, depois as do dia 14 da mais nova para a mais velha |
| `server` `undefined` | só os itens da fila |
| `groupByDay` com hoje `2026-11-15` | grupos `2026-11-15`, `2026-11-14` e `2026-11-01`, nessa ordem |

`TransactionsPage.test.tsx` (rota `/lancamentos`, hora `2026-11-15T15:00:00Z`):

| Teste | Esperado |
|---|---|
| um gasto de 87,90 em Mercado hoje, na API, e um `pending` ontem, na fila | grupos "Hoje" e "Ontem"; a linha "🛒 Mercado", "−R$ 87,90" e "Carteira · Ana"; a outra linha com "Aguardando sinal" |
| a mesma id na API e na fila | 1 linha, sem selo |
| uma receita de 5.000,00 na API | "+R$ 5.000,00" |
| um `categoryId` que não está nas categorias | "Sem categoria" |
| item `pending` de outubro na fila | aparece no grupo "20 de outubro" |
| item `needs-attention` com `detail` "Conta arquivada." | o botão "Precisa de atenção" abre "Este gasto não foi salvo" com o `detail`; "Corrigir" leva a `/lancar?corrigir=<id>` |
| "Descartar" e "Descartar" no diálogo | a fila fica vazia; a linha some |
| "Descartar" e "Manter" | a fila continua com 1 |
| offline | `status` "Sem conexão. Você pode lançar; os gastos saem quando o sinal voltar." |
| sem lançamentos e fila vazia | "Nenhum lançamento neste mês." |

- [ ] **Step 2: Rodar e ver falhar**

Run: `corepack pnpm --filter web test -- src/features src/shared/ui src/shared/api`
Expected: FAIL; as telas ainda são esqueletos, e `Toast`, `Sheet` e `prefetchWorkspace` não
existem.

- [ ] **Step 3: Implementar**

Instale o `uuid` e escreva os componentes, as telas e o `prefetchWorkspace` descritos acima. Ligue o
`ToastProvider` no `AppProviders` e no `renderWithProviders`, e o `prefetchWorkspace` no
`WorkspaceLayout`.

- [ ] **Step 4: Rodar e ver passar**

Run: `corepack pnpm --filter web exec tsc -b; corepack pnpm --filter web lint; corepack pnpm --filter web test:coverage; corepack pnpm --filter web build`
Expected: sem erros; `src/shared/**` com 90% ou mais e o geral com 80% ou mais.

- [ ] **Step 5: Commit**

```bash
git add web pnpm-lock.yaml
git commit -m "feat(web): record the first expense offline-first with home and transactions"
```

---

### Task 12: E2E com Playwright e job no CI

**Files:**
- Modify: `web/package.json` e `pnpm-lock.yaml`:
  - `@playwright/test` e `@axe-core/playwright` em `devDependencies` (Apache-2.0 e MPL-2.0, as duas
    na lista do `dependency-review`), na versão mais nova dentro do corte;
  - o script `"e2e": "playwright test"`.
- Modify: `web/vite.config.ts`: `server` e `preview` com `port: 5173` e `strictPort: true`.
- Modify: `web/tsconfig.node.json`:
  - `lib` vira `["ES2023", "DOM"]`, porque os callbacks de `page.evaluate` rodam no navegador;
  - `include` ganha `playwright.config.ts` e `e2e/**/*.ts`.
- Modify: `web/eslint.config.js` (`globalIgnores`) e `web/.prettierignore`: ignoram
  `playwright-report` e `test-results`.
- Modify: `.gitignore`: `playwright-report/`, `test-results/` e `blob-report/`.
- Modify: `.github/workflows/ci.yml`: o job `e2e`.
- Modify: `.github/workflows/preview.yml`: o passo de build ganha `env: VITE_API_MOCKS: "true"`. A
  prévia do Cloudflare não tem API, então roda com os mocks da Task 8.
- Modify: `README.md`: o Status e a seção "Testes de ponta a ponta".
- Create: `web/playwright.config.ts`, `web/e2e/global-setup.ts` e `web/e2e/support.ts`.
- Test: `web/e2e/sign-in.spec.ts`, `web/e2e/first-expense.spec.ts`, `web/e2e/passkey.spec.ts` e
  `web/e2e/a11y.spec.ts`.

**Interfaces:**
- Consumes:
  - `compose.yaml` e `compose.e2e.yaml` (Task 3): o provedor de teste em
    `http://localhost:8090/google`, o cliente `liberdade-e2e` e os campos do formulário que o
    spike da Task 3 registrou (`username` e `claims`, salvo desvio no PR).
  - A API: `GET /actuator/health/readiness`, `/oauth2/authorization/google`, o retorno
    `/login/oauth2/code/google` (Task 3) e os caminhos de passkey (Task 4).
  - Os textos das Tasks 10 e 11: "Entrar com Google", "Entrar com passkey", "Olá, {name}! 👋",
    "Nome da família", "Começar", "Criar passkey neste aparelho", "Passkey criada.", "Sair",
    "Navegação principal", as teclas, os rádios de categoria, "Salvar", "Gasto salvo" e
    "Aguardando sinal".
- Produces:
  - `type E2EUser = { sub: string; claims: Record<string, string> }`;
  - `newUser(): E2EUser`;
  - `signInWithGoogle(page: Page, user: E2EUser): Promise<void>`;
  - `createFamily(page: Page): Promise<void>`;
  - o script `e2e` e o job `e2e` do CI.

Os arquivos de `e2e/` não importam nada de `src/`. Eles testam o build como o usuário vê, pelos
papéis e textos da tela. Os imports relativos levam a extensão (`./support.ts`), como pede o
`module: nodenext`.

**`playwright.config.ts`:**
- `testDir: 'e2e'`, `fullyParallel: false`, `workers: 1`, `forbidOnly: !!process.env.CI` e
  `retries: process.env.CI ? 1 : 0`.
- `reporter`: no CI, `[['github'], ['html', { open: 'never' }]]`; fora dele,
  `[['list'], ['html', { open: 'never' }]]`.
- `globalSetup: './e2e/global-setup.ts'`.
- `use: { baseURL: 'http://localhost:5173', locale: 'pt-BR', timezoneId: 'America/Fortaleza', trace: 'retain-on-failure' }`.
- Um só projeto, `chromium`, com `devices['Desktop Chrome']`. Se o spike da Task 3 caiu na linha
  "`iss` com `localhost`", o projeto ganha
  `launchOptions: { args: ['--host-resolver-rules=MAP mock-oauth2 127.0.0.1'] }`.
- `webServer: { command: 'node --run build && node --run preview', url: 'http://localhost:5173', reuseExistingServer: false, timeout: 120_000 }`:
  - O `vite preview` serve o build com o service worker do PWA, que o teste do modo avião precisa.
    O `vite dev` não registra o service worker.
  - O `node --run` roda os scripts do `package.json` sem precisar do `pnpm` no PATH; fora do CI, o
    pnpm só existe pelo corepack.
  - Com `reuseExistingServer: false` e o `strictPort`, um `vite dev` esquecido na 5173 faz a
    suíte falhar com a porta ocupada, em vez de testar sem o service worker.

**`global-setup.ts`:** consulta `http://localhost:8080/actuator/health/readiness` a cada 2 s, por
até 120 s. Sem `{"status":"UP"}`, lança
`Error('A API não respondeu em http://localhost:8080. Suba o Compose: docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait')`.

**`support.ts`:**
- `newUser()`: `sub` = `'e2e-' + crypto.randomUUID()`. As claims são `name` "Ana Teste",
  `given_name` "Ana", `family_name` "Teste" e `email` "ana@example.com". Um `sub` novo por teste dá
  uma pessoa e uma família novas, sem limpar o banco (D11).
- `signInWithGoogle(page, user)`:
  - abre `/entrar` e clica "Entrar com Google";
  - no provedor de teste, preenche `[name="username"]` com o `sub` e `[name="claims"]` com
    `JSON.stringify(user.claims)`;
  - envia o formulário e espera a URL `**/boas-vindas`.
- `createFamily(page)`: espera o `<h1>` "Olá, Ana! 👋", clica "Começar" e espera a URL `**/lancar`.
- Cada `signInWithGoogle` conta 2 tentativas no limite de 20 por minuto por IP (D10), e a suíte
  inteira faz 13. Um comentário no topo do arquivo registra essa conta, para quem somar specs.

Nos textos de valor, o espaço depois de "R$" é U+00A0, e o Playwright o normaliza. O sinal de
menos é U+2212 e não é normalizado, então os specs o escrevem como `'−R$ 5,00'`.

- [ ] **Step 1: Escrever os specs**

`sign-in.spec.ts`:

| Teste | Esperado |
|---|---|
| sem sessão, abrir `/` | a URL vira `/entrar`; os botões "Entrar com Google" e "Entrar com passkey" |
| abrir `http://localhost:8080/login/oauth2/code/google?error=access_denied&state=x` (login negado no Google) | a URL vira `http://localhost:5173/entrar?erro=login`; `alert` "Não deu para entrar. Tente de novo." (Review Focus 3) |

`first-expense.spec.ts`:

| Teste | Passos | Esperado |
|---|---|---|
| primeiro gasto online (S1) | marca `Date.now()`; `signInWithGoogle`; confere "Olá, Ana! 👋" e o campo "Nome da família" com "Família Teste"; `createFamily`; teclas 1, 2, 3 e 4; rádio "🛒 Mercado"; "Salvar" | "Gasto salvo" aparece com a URL em `/inicio`; menos de 60 000 ms entre a marca e o aviso; o card de gastos do mês mostra "R$ 12,34"; em até 20 s, `/lancamentos` mostra a linha "🛒 Mercado" com "−R$ 12,34" e sem "Aguardando sinal" |
| primeiro gasto em modo avião (S1) | marca `Date.now()`; `signInWithGoogle`; `createFamily`; espera `navigator.serviceWorker.ready` e o rádio "🛒 Mercado" (o aquecimento da Task 11 terminou); `context.setOffline(true)`; teclas 5, 0 e 0; rádio "🚌 Transporte"; "Salvar" | "Gasto salvo" em menos de 60 000 ms desde a marca; `/lancamentos`, aberto pela navegação, mostra "🚌 Transporte", "−R$ 5,00" e "Aguardando sinal" |
| (segue) `page.reload()` ainda offline | — | o app abre em `/lancamentos`, com a mesma linha e o `status` "Sem conexão. Você pode lançar; os gastos saem quando o sinal voltar." |
| (segue) `context.setOffline(false)` | — | em até 20 s, "Aguardando sinal" some; depois de `page.reload()`, há exatamente 1 linha com "−R$ 5,00" |

Se o `setOffline` não cortar as chamadas à API (o Chromium não aplica a emulação de rede ao
service worker), o spec usa `context.route('http://localhost:8080/**', (r) => r.abort())` no
lugar dele e `context.unroute` para voltar. O PR registra a troca.

`passkey.spec.ts`, num teste só:

| Passo | Esperado |
|---|---|
| `page.context().newCDPSession(page)`; `WebAuthn.enable`; `WebAuthn.addVirtualAuthenticator` com `{ protocol: 'ctap2', transport: 'internal', hasResidentKey: true, hasUserVerification: true, isUserVerified: true, automaticPresenceSimulation: true }` | — |
| `signInWithGoogle`, `createFamily`, "Mais" na "Navegação principal", "Criar passkey neste aparelho" | `status` "Passkey criada."; `WebAuthn.getCredentials` devolve 1 credencial |
| "Sair", depois "Entrar com passkey" | a URL vira `/inicio`, com a "Navegação principal" na tela |

`a11y.spec.ts`, um teste por linha da tabela:
- Cada página é analisada com
  `new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze()`.
- A análise roda nos temas claro e escuro (`page.emulateMedia({ colorScheme })`) e espera o `<h1>`
  da página antes.

| Página | Como chegar | Esperado |
|---|---|---|
| `/entrar` | sem sessão | `violations` `[]` nos dois temas |
| `/boas-vindas` | `signInWithGoogle` | `violations` `[]` nos dois temas |
| `/lancar`, `/inicio`, `/lancamentos` e `/mais` | `createFamily`, depois `page.goto` | `violations` `[]` nos dois temas |

- [ ] **Step 2: Rodar e ver falhar**

Com o Compose no ar (`docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait`):

Run: `corepack pnpm --filter web exec playwright test`
Expected: FAIL; ainda não há `playwright.config.ts`, então o Playwright não acha os specs nem o
servidor.

- [ ] **Step 3: Implementar a configuração**

Escreva `playwright.config.ts`, `global-setup.ts` e `support.ts` como descrito acima, e faça as
mudanças em `package.json`, `vite.config.ts`, `tsconfig.node.json`, ESLint, Prettier e
`.gitignore`. Rode `corepack pnpm --filter web exec playwright install chromium` uma vez.

O job `e2e` entra no `ci.yml` depois do `web`, com os mesmos pins:

```yaml
  e2e:
    runs-on: ubuntu-24.04
    timeout-minutes: 25
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          persist-credentials: false
      - uses: pnpm/action-setup@ea17c68df8912ef543352723c149a84f56e3d413 # v6.1.0
      - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
        with:
          node-version-file: .node-version
          cache: pnpm
      - run: pnpm install --frozen-lockfile
      - run: pnpm --filter web exec playwright install --with-deps chromium
      - name: Start the API with the test provider
        run: |
          cp .env.example .env
          docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait
      - run: pnpm --filter web e2e
      - name: Save Compose logs
        if: ${{ failure() }}
        run: |
          mkdir -p web/test-results
          docker compose -f compose.yaml -f compose.e2e.yaml logs --no-color > web/test-results/compose.log
      - name: Upload Playwright report
        if: ${{ failure() }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: playwright-report
          path: |
            web/playwright-report/
            web/test-results/
          retention-days: 7
      - name: Stop Compose
        if: ${{ always() }}
        run: docker compose -f compose.yaml -f compose.e2e.yaml down -v
```

No README:
- "Em construção — Fase 0." vira "Em construção — Fase 1 (v0.1 Núcleo).".
- Depois de "Testes da web", entra a seção "Testes de ponta a ponta":
  - uma frase: os testes usam o Playwright, a API no Docker e o provedor de login de teste no
    lugar do Google;
  - os comandos `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait`,
    `corepack pnpm --filter web exec playwright install chromium` (uma vez) e
    `corepack pnpm --filter web e2e`;
  - o aviso de que a suíte usa a porta 5173, então o `dev` precisa estar parado.

- [ ] **Step 4: Rodar e ver passar**

Com o Compose no ar:

Run: `corepack pnpm --filter web exec tsc -b; corepack pnpm --filter web lint; corepack pnpm --filter web format:check; corepack pnpm --filter web e2e`
Expected: sem erros de tipo, lint ou formato; o Playwright mostra 8 testes passando.

Run: `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/repo" -w /repo rhysd/actionlint:<a mesma versão do Plano 1> -color`
Expected: sem saída (os workflows estão válidos).

- [ ] **Step 5: Commit**

```bash
git add web pnpm-lock.yaml .gitignore .github README.md
git commit -m "test(e2e): cover sign-in and first expense online and offline"
```
