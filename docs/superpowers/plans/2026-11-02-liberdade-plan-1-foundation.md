# Liberdade · Plano 1: Fundação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar a Fase 0 do roadmap. Isso inclui um repositório público com governança e CI/CD
completos, esqueletos da API e do PWA que sobem localmente e no CI, e o tipo `Money` testado no
back e no front. A fase termina quando um PR de exemplo passa no CI e gera preview.

**Architecture:** Um monorepo com duas pastas. `api/` é um monólito modular em Spring Boot com
Spring Modulith; nesta fase ele tem só o módulo `shared` (Money, erros RFC 9457 e Clock). `web/` é
um PWA em React e Vite, com fronteiras entre módulos garantidas por ESLint. A raiz é um workspace
pnpm (por causa do commitlint) e tem também o Docker Compose e os workflows do GitHub Actions.

**Tech Stack:**
- API: Java 25, Spring Boot 4.1.2 (JUnit 6.1.3), Spring Modulith, PostgreSQL 18, Flyway, Gradle
  Kotlin DSL com toolchain via foojay, Spotless, JaCoCo, Testcontainers.
- Web: React 19.3.1, Vite 8.3.5, TypeScript 7.0.2 (6.x se o typescript-eslint ainda não aceitar
  o 7), Tailwind v4, React Router, react-intl, Vitest e Testing Library.
- Ferramentas: Node 24, pnpm via corepack.
- GitHub: Actions, CodeQL, Dependabot, release-please e GHCR. Preview no Cloudflare Pages.

**Spec:**
- [PRD](../../prd.md)
- [Arquitetura](../../architecture.md)
- [Modelo de dados](../../data-model.md)
- [Design system](../../design-system.md)
- [Wireframes](../../wireframes.md)
- [Roadmap](../../roadmap.md) (seção "Fase 0")
- [ADRs 0001–0011](../../adr/README.md)

## Global Constraints

- **Idiomas:** comunicação, UI, documentação de usuário, README, CONTRIBUTING e textos de PR em
  português do Brasil. Código, commits, nomes técnicos e comentários de código em inglês.
- **Commits e títulos de PR:** Conventional Commits.
  - Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Descrições de PR terminam com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Dados pessoais:** nenhum dado pessoal real (nome, cidade, e-mail) em arquivo versionado.
  - `PROMPT_BIG_BRAIN.md` nunca é commitado. Ele fica em `.git/info/exclude` e não entra no
    `.gitignore`.
  - Antes de cada commit, `git grep -nIiE -f .git/info/personal-data-patterns` não retorna nada.
    Esse arquivo de padrões existe só na máquina do mantenedor (dentro de `.git/`, nunca
    versionado), porque escrever os padrões no repositório já seria expor o dado.
- **Segredos:** nada de segredo em arquivo. Senhas e tokens ficam só em `.env` (ignorado) ou em
  GitHub secrets. Quem digita token é o mantenedor; o agente nunca digita credenciais.
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
- **Terceiros:** nenhuma chamada a serviço de terceiros no app; a Inter é auto-hospedada.
- **Versões:** são as pesquisadas em 2026-11-02. Onde o plano diz "a mais recente", use a última
  versão estável publicada há pelo menos 7 dias. Registre no PR toda versão escolhida e todo
  desvio.
- **Ações externas:** criar repositório, fazer push, abrir PR ou mudar configurações exige
  confirmação do mantenedor na hora.

### Pré-requisitos locais

- Git, `gh` autenticado, Java 21+ para rodar o Gradle (a toolchain baixa o JDK 25), Node 24 com
  corepack.
- Docker Desktop em execução, usado pelo Testcontainers e pelo Compose. Ele está instalado nesta
  máquina, mas o daemon estava parado em 2026-11-02.
- O pnpm não está instalado globalmente: use sempre `corepack pnpm …`.

## Review Focus

1. **Texto de valor fora do formato:** "1.234,56", "1e3" ou um valor com mais de 4 casas é
   rejeitado, nunca arredondado em silêncio. Os testes estão nas Tasks 4 (`Money.parse`), 6 (JSON)
   e 8 (`parseCents`).
2. **`amount` como número JSON:** `{"amount": 12.5}` responde 400 `request.malformed`, nunca
   converte para decimal (Task 6).
3. **Rota inexistente ou erro 500:** a resposta é `problem+json` com `detail` em pt-BR, sem stack
   trace, nome de classe ou mensagem interna (Task 5).
4. **Valor grande ou negativo na tela:** `formatBRL` é exato acima de 2^53 e usa o sinal "−"
   (U+2212), por exemplo "−R$ 87,90". O `Intl` pt-BR coloca um espaço não separável (U+00A0)
   depois de "R$" (Task 8).
5. **Dígito além do limite no teclado:** `appendDigit` ignora o dígito que passaria de
   `MAX_CENTS` (R$ 999.999.999,99) e devolve o valor anterior (Task 8).

---

### Task 1: Arquivos base do repositório

**Files:**
- Create: `.gitattributes`, `.editorconfig`, `.gitignore`, `LICENSE`, `README.md`,
  `CONTRIBUTING.md`, `SECURITY.md`, `.env.example`, `.node-version`

**Interfaces:**
- Produces:
  - Branch `feat/foundation`, onde vão todos os commits deste plano até a Task 12.
  - `.env.example` com as chaves `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD`, que a
    Task 10 lê.

- [ ] **Step 1: Criar a branch**

Run: `git switch -c feat/foundation`
Expected: `Switched to a new branch 'feat/foundation'`

- [ ] **Step 2: Escrever os arquivos de configuração**

- `.gitattributes`:
  - `* text=auto eol=lf`
  - `*.bat text eol=crlf`
  - `*.cmd text eol=crlf`
  - `binary` para `*.jar`, `*.png`, `*.ico`, `*.woff2`
- `.editorconfig`:
  - `root = true`
  - em todos os arquivos: utf-8, lf, newline final, sem espaço no fim da linha, 2 espaços
  - `*.{java,kts}`: 4 espaços
  - `*.md`: mantém espaço no fim da linha
- `.gitignore`: `.env`, `build/`, `.gradle/`, `out/`, `node_modules/`, `dist/`, `coverage/`,
  `.idea/`, `*.iml`, `.vscode/`, `.DS_Store`.
  - **Não** listar `PROMPT_BIG_BRAIN.md`: ele já está em `.git/info/exclude`.
- `LICENSE`: texto oficial, gerado com
  `gh api licenses/agpl-3.0 --jq .body > LICENSE`.
- `.env.example`:
  - `POSTGRES_DB=liberdade`
  - `POSTGRES_USER=liberdade`
  - `POSTGRES_PASSWORD=change-me-local-only`
  - comentário em pt-BR: "copie para `.env`; nunca commite o `.env`"
- `.node-version`: `24`.

- [ ] **Step 3: Escrever os documentos em pt-BR**

- `README.md`:
  - O que é o Liberdade: as finanças da família e um copiloto de renda extra; "poderoso por
    baixo, simples na superfície".
  - Status: "Em construção — Fase 0".
  - Uma seção "Como rodar" com o texto "Em breve", preenchida na Task 10.
  - Links para `docs/README.md` e para `docs/superpowers/plans/README.md`.
  - Licença AGPL-3.0.
- `CONTRIBUTING.md`:
  - Nomes de branch `feat/…`, `fix/…`, `chore/…`, `docs/…`.
  - Conventional Commits. O título do PR vira o commit (squash) e é validado pelo commitlint.
  - Todo PR traz testes e mantém a cobertura (domínio ≥ 90%, geral ≥ 80%).
  - Regra de idiomas.
  - Nunca commitar dados pessoais nem segredos.
  - Decisões novas viram ADR (`docs/adr/`).
- `SECURITY.md`:
  - Vulnerabilidades são relatadas pelo botão "Report a vulnerability" da aba Security (relato
    privado do GitHub).
  - Sem e-mail.
  - Versão suportada: a `main`.

- [ ] **Step 4: Verificar exclusões e dados pessoais**

Run: `git check-ignore -v PROMPT_BIG_BRAIN.md && git check-ignore -v .env.example; git grep -nIiE -f .git/info/personal-data-patterns`
Expected:
- A primeira linha aponta para `.git/info/exclude:…:PROMPT_BIG_BRAIN.md`.
- `.env.example` **não** é ignorado: o segundo `check-ignore` sai com código 1 e não imprime nada.
- O `git grep` não imprime nada.

- [ ] **Step 5: Commit**

```bash
git add .gitattributes .editorconfig .gitignore LICENSE README.md CONTRIBUTING.md SECURITY.md .env.example .node-version
git commit -m "chore: add repository base files"
```

---

### Task 2: Governança e commitlint

**Files:**
- Create:
  - `.github/ISSUE_TEMPLATE/bug.yml`, `.github/ISSUE_TEMPLATE/feature.yml`,
    `.github/ISSUE_TEMPLATE/config.yml`
  - `.github/pull_request_template.md`, `.github/CODEOWNERS`
  - `package.json`, `pnpm-workspace.yaml`, `commitlint.config.mjs`, `pnpm-lock.yaml`

**Interfaces:**
- Produces:
  - Workspace pnpm na raiz com `packages: [web]`. A Task 7 cria `web/`.
  - `corepack pnpm exec commitlint` funcionando na raiz. A Task 11 usa isso no job `pr-title`.

- [ ] **Step 1: Escrever os templates em pt-BR**

- `bug.yml`: campos "O que aconteceu", "Passos para reproduzir", "O que você esperava" e
  "Aparelho e navegador", todos obrigatórios. Um aviso em markdown no topo diz: "Não cole dados
  financeiros reais nem dados pessoais."
- `feature.yml`: campos "Qual problema isso resolve", "Proposta" e "RF relacionado" (texto livre,
  por exemplo RF-02).
- `config.yml`:
  - `blank_issues_enabled: false`
  - um `contact_links` para "Relatar vulnerabilidade", com a URL absoluta
    `https://github.com/warthogozz-cyber/liberdade/security/advisories/new` (o GitHub não aceita
    URL relativa aqui)
- `pull_request_template.md`:
  - Seções "O que muda" e "Como testar".
  - Checklist: testes incluídos; cobertura mantida; sem dados pessoais; sem segredos; docs e ADR
    atualizados quando preciso; título no formato Conventional Commits.
- `CODEOWNERS`: `* @warthogozz-cyber`.

- [ ] **Step 2: Criar o workspace pnpm**

- `package.json`:
  - `"name": "liberdade"`, `"private": true`
  - `"engines": {"node": ">=24"}`
  - `devDependencies`: `@commitlint/cli` e `@commitlint/config-conventional`
- Fixe o pnpm com `corepack use pnpm@latest`. O comando grava `packageManager` com hash. A versão
  precisa ser ≥ 10.16, porque usamos `minimumReleaseAge`.
- `pnpm-workspace.yaml`:
  - `packages: [web]`
  - `minimumReleaseAge: 10080` (7 dias)
- `commitlint.config.mjs`: `export default { extends: ['@commitlint/config-conventional'] }`.
  Sem `scope-enum`.

Run: `corepack pnpm install`
Expected: cria `pnpm-lock.yaml` e termina sem erro.

- [ ] **Step 3: Verificar o commitlint**

Run: `echo "feat(web): add money input" | corepack pnpm exec commitlint; echo "exit=$?"`
Expected: `exit=0`

Run: `echo "Added stuff" | corepack pnpm exec commitlint; echo "exit=$?"`
Expected: erros `subject-empty` e `type-empty`, e `exit=1`.

- [ ] **Step 4: Commit**

```bash
git add .github package.json pnpm-workspace.yaml pnpm-lock.yaml commitlint.config.mjs
git commit -m "chore: add governance templates and commitlint"
```

---

### Task 3: Esqueleto da API

**Files:**
- Create:
  - `api/` gerado pelo Spring Initializr
  - `api/gradle/libs.versions.toml`
  - `api/src/main/resources/db/migration/.gitkeep`
  - `docs/adr/0012-data-access-jdbcclient.md`
- Modify:
  - `api/settings.gradle.kts`, `api/build.gradle.kts`
  - `api/src/main/resources/application.properties`
  - `api/src/test/java/br/com/liberdadefamilia/TestcontainersConfiguration.java`
  - `docs/adr/README.md`
- Test:
  - `api/src/test/java/br/com/liberdadefamilia/ApplicationModulesTest.java`
  - `api/src/test/java/br/com/liberdadefamilia/HealthProbesIT.java`
- Delete: `api/HELP.md`, `api/.gitignore`, `api/.gitattributes` (a raiz já cobre) e
  `LiberdadeApplicationTests.java` (o `HealthProbesIT` substitui).

**Interfaces:**
- Produces:
  - Pacote raiz `br.com.liberdadefamilia` e `LiberdadeApplication` anotada com
    `@SpringBootApplication` e `@Modulithic`.
  - `./gradlew build` com Spotless, testes, JaCoCo e verificação de cobertura.
  - `TestcontainersConfiguration` (PostgreSQL 18), que os testes de integração importam.
  - `./gradlew bootTestRun` para desenvolvimento local.

- [ ] **Step 1: Gerar o projeto**

Gere o projeto a partir da raiz do repositório. O zip vai para uma pasta temporária e é extraído
em `api/`.

```bash
TMP_DIR=$(mktemp -d)
curl -fsSL https://start.spring.io/starter.zip \
  -d type=gradle-project-kotlin -d language=java -d bootVersion=4.1.2 -d javaVersion=25 \
  -d groupId=br.com.liberdadefamilia -d artifactId=liberdade-api -d name=liberdade-api \
  -d packageName=br.com.liberdadefamilia -d applicationName=LiberdadeApplication \
  -d dependencies=web,jdbc,postgresql,flyway,actuator,validation,modulith,testcontainers \
  -d baseDir=api -o "$TMP_DIR/api.zip"
unzip -q "$TMP_DIR/api.zip" -d .
```

Confira o que foi gerado:
- `testImplementation` inclui `spring-boot-starter-webmvc-test`. No Boot 4, `@WebMvcTest` e
  `@AutoConfigureMockMvc` moraram para lá; adicione se faltar.
- `java.toolchain.languageVersion` é 25.

- [ ] **Step 2: Configurar o build**

- `settings.gradle.kts`: plugin `org.gradle.toolchains.foojay-resolver-convention`, na versão
  mais recente.
- `gradle/libs.versions.toml`: só versões que o BOM do Boot não gerencia (plugin Spotless,
  palantir-java-format, ktlint, JaCoCo). Escolha uma versão do JaCoCo cujo changelog declare
  suporte a Java 25.
- `build.gradle.kts`:
  - Spotless:
    - `java { palantirJavaFormat(<versão>); removeUnusedImports() }`
    - `kotlinGradle { ktlint(<versão>) }`
  - Plugin `jacoco`:
    - `jacocoTestReport` com XML e HTML, dependendo de `test`.
    - `jacocoTestCoverageVerification` com duas regras:
      - `BUNDLE` LINE ≥ 0.80;
      - `PACKAGE` LINE ≥ 0.90, com `includes = listOf("br.com.liberdadefamilia.shared*", "br.com.liberdadefamilia.*.domain*")`.
    - Relatório e verificação excluem `br/com/liberdadefamilia/LiberdadeApplication*`. O `main`
      nunca roda nos testes, e sem a exclusão o `BUNDLE` fica em 50% já nesta tarefa.
  - `tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }`
  - `tasks.jar { enabled = false }`: só o `bootJar` vai para `build/libs`.
- Rode `./gradlew spotlessApply` uma vez para formatar o código gerado.

- [ ] **Step 3: Configurar a aplicação**

`application.properties`:

```properties
spring.application.name=liberdade-api
management.endpoints.web.exposure.include=health
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=never
management.endpoint.health.group.readiness.include=readinessState,db
server.error.include-stacktrace=never
server.error.include-message=never
```

Mais estes ajustes:
- Em `TestcontainersConfiguration`, troque a imagem para `postgres:18`.
- Crie `db/migration/.gitkeep`. Não há tabelas neste plano.
- Anote `LiberdadeApplication` com `@Modulithic`. O `sharedModules` entra na Task 4.

- [ ] **Step 4: Escrever os testes**

`ApplicationModulesTest`:

```java
@Test
void modulesRespectBoundaries() {
    ApplicationModules.of(LiberdadeApplication.class).verify();
}
```

`HealthProbesIT` usa `@SpringBootTest`, `@AutoConfigureMockMvc`,
`@Import(TestcontainersConfiguration.class)` e `MockMvcTester`:
- `livenessIsUp`: GET `/actuator/health/liveness` → 200, `$.status` = `"UP"`.
- `readinessIsUpWithDatabase`: GET `/actuator/health/readiness` → 200, `$.status` = `"UP"`.
- `healthHidesDetails`: GET `/actuator/health` → 200 e o corpo não tem `components`.
- `envIsNotExposed`: GET `/actuator/env` → 404.

- [ ] **Step 5: Rodar o build**

Run: `cd api && ./gradlew build`
Expected: `BUILD SUCCESSFUL`, com 5 testes passando.
- Um aviso do Flyway dizendo que o PostgreSQL 18 é mais novo que a versão testada é aceitável.
- Se o Docker estiver parado, o `HealthProbesIT` falha com "Could not find a valid Docker
  environment".

- [ ] **Step 6: Escrever o ADR-0012**

`docs/adr/0012-data-access-jdbcclient.md` segue o formato do 0001: Status Aceito, Data
2026-11-02.
- **Contexto:** o ADR-0008 isola as famílias com FKs compostas (`workspace_id`, `id`). Um
  `findById(id)` de Spring Data sem `workspace_id` é o caminho mais fácil e vaza dados entre
  famílias.
- **Decisão:**
  - Acesso a dados com `JdbcClient` e SQL explícito em um repositório por módulo.
  - Todo método que lê ou grava dado de família recebe o id da família.
  - Sem JPA e sem repositórios Spring Data.
- **Alternativas:**

  | Alternativa | Por que não |
  |---|---|
  | Spring Data JPA | `findById` sem família e carregamento preguiçoso escondem consultas |
  | Spring Data JDBC | Mesmo problema do `findById` |
  | jOOQ | Geração de código e mais uma ferramenta no build; ganho pequeno para cerca de 15 tabelas |

- **Consequências:** mais SQL escrito à mão; cada repositório tem teste com Testcontainers,
  incluindo um caso de "outra família não vê".

Adicione a linha 0012 em `docs/adr/README.md`.

- [ ] **Step 7: Commit**

No Windows o Git não grava o bit de execução sozinho. Sem ele, o `./gradlew` falha com
"Permission denied" no CI e no Docker.

```bash
git add api docs/adr
git update-index --chmod=+x api/gradlew
git ls-files -s api/gradlew   # expected: starts with 100755
git commit -m "feat(api): add Spring Boot skeleton with health probes and coverage gates"
```

---

### Task 4: `Money` com TDD (e ADR-0013)

**Files:**
- Create:
  - `api/src/main/java/br/com/liberdadefamilia/shared/Money.java`
  - `api/src/main/java/br/com/liberdadefamilia/shared/DomainException.java`
  - `api/src/main/java/br/com/liberdadefamilia/shared/CurrencyMismatchException.java`
  - `docs/adr/0013-property-tests-without-jqwik.md`
- Modify: `api/src/main/java/br/com/liberdadefamilia/LiberdadeApplication.java`,
  `docs/adr/README.md`
- Test:
  - `api/src/test/java/br/com/liberdadefamilia/shared/MoneyTest.java`
  - `api/src/test/java/br/com/liberdadefamilia/shared/MoneyPropertiesTest.java`

**Interfaces:**
- Produces:

```java
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {
    public static Money of(BigDecimal amount, Currency currency);
    public static Money parse(String amount, Currency currency); // strict plain decimal
    public static Money brl(String amount);                      // parse(amount, BRL)
    public static Money zero(Currency currency);
    public Money plus(Money other);
    public Money minus(Money other);
    public Money negate();
    public boolean isPositive();
    public int compareTo(Money other);
    public String toPlainString();          // HALF_EVEN at the currency scale
    public List<Money> allocate(int parts); // largest remainder
}

public abstract class DomainException extends RuntimeException {
    protected DomainException(int status, String code, String detail); // super(code)
    public int status();
    public String code();
    public String detail();
}

public final class CurrencyMismatchException extends DomainException {}
// 422, "money.currency-mismatch", "As moedas dos valores não são iguais."
```

- [ ] **Step 1: Escrever o `MoneyTest` (falhando)**

Casos com os valores do PRD:

```java
@Test void sumsAccountBalanceFromPrd71() {
    var balance = Money.brl("1000").plus(Money.brl("5000")).minus(Money.brl("1234.56")).minus(Money.brl("500"));
    assertThat(balance).isEqualTo(Money.brl("4265.44"));
    assertThat(balance.plus(Money.brl("500"))).isEqualTo(Money.brl("4765.44"));
}
@Test void equalityIgnoresTrailingZeros() {
    assertThat(Money.brl("10")).isEqualTo(Money.brl("10.00")).hasSameHashCodeAs(Money.brl("10.00"));
}
@Test void allocatesWithLargestRemainderFromPrd72() {
    assertThat(Money.brl("100").allocate(3)).containsExactly(Money.brl("33.34"), Money.brl("33.33"), Money.brl("33.33"));
}
```

Os demais casos, cada um como teste próprio:

| Entrada | Resultado esperado |
|---|---|
| `Money.of(new BigDecimal("1.23450"), BRL)` | aceito (zeros à direita não contam como casa) |
| `brl("0.00001")` | `IllegalArgumentException` (mais de 4 casas) |
| `brl("999999999999999.9999")` | aceito |
| `brl("1000000000000000")` | IAE |
| `Money.of(new BigDecimal("1E+15"), BRL)` | IAE (limite de `NUMERIC(19,4)`) |
| `brl` de `"1e3"`, `"+5"`, `" 5"`, `"1.234,56"`, `""`, `"abc"` | IAE (`@ParameterizedTest`) |
| `brl("1").plus(Money.of(ONE, USD))` | `CurrencyMismatchException` com `code()` = `"money.currency-mismatch"` |
| `minus` e `compareTo` entre moedas diferentes | `CurrencyMismatchException` |
| `Money.of(ONE, Currency.getInstance("XXX"))` | IAE (moeda sem casas decimais definidas) |
| `toPlainString` de `"2.345"`, `"2.355"`, `"-0.005"`, `"1234.5"`, `"100"` | `"2.34"`, `"2.36"`, `"0.00"`, `"1234.50"`, `"100.00"` |
| `brl("-100").allocate(3)` | `−33.34`, `−33.33`, `−33.33` |
| `brl("0.02").allocate(3)` | `0.01`, `0.01`, `0.00` |
| `brl("0.005").allocate(2)` | IAE (fração de centavo) |
| `allocate(0)` | IAE |
| `brl("5").negate()` | `brl("-5")` |
| `isPositive` | `brl("0")` → false, `brl("0.01")` → true |

Teste `exceptionMessagesNeverContainAmounts`: as mensagens de `brl("123456.00001")` (rejeitado
pela regex) e de `Money.of(new BigDecimal("1234560000000000"), BRL)` (rejeitado pelo construtor)
não contêm `"123456"`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*MoneyTest'`
Expected: falha de compilação, porque `Money` ainda não existe.

- [ ] **Step 3: Implementar `DomainException`, `CurrencyMismatchException` e `Money`**

- **Construtor compacto** de `Money`, nesta ordem:
  1. Os campos não podem ser nulos.
  2. Moeda com `getDefaultFractionDigits() < 0` → IAE.
  3. `stripTrailingZeros().scale() > 4` → IAE.
  4. `abs() >= 10^15` → IAE.
  5. Normaliza a escala para `max(escala sem zeros, casas da moeda)`. Isso faz `equals` e
     `hashCode` do record ignorarem zeros à direita.
- **`parse`:** valida com `^-?\d{1,15}(\.\d{1,4})?$` antes de criar o `BigDecimal`, e lança IAE
  se não casar.
- **Mensagens de exceção** dizem a regra violada, nunca o valor.
- **`allocate`:**

```java
// units = amount in minor units (toBigIntegerExact; ArithmeticException -> IAE)
// negative amounts: allocate(-amount) and negate each part
// base = units / parts; remainder = units % parts
// the first `remainder` parts get base + 1, the rest get base (ties by list order)
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*MoneyTest'`
Expected: PASS.

- [ ] **Step 5: Escrever o `MoneyPropertiesTest`**

Os casos são gerados por `@MethodSource` com
`new SplittableRandom(SEED)` e `SEED = 20261102L`. Cada propriedade roda `CASES = 1000` casos,
com centavos uniformes em `[-10^11, 10^11]` e partes em `[1, 50]`.

- `allocationSumsToTotal(Money total, int parts)`: a soma das partes é igual a `total`.
- `allocationPartsDifferByAtMostOneCent(Money total, int parts)`: a maior parte menos a menor
  parte é no máximo `0.01`.
- `plusThenMinusIsIdentity(Money a, Money b)`: `a.plus(b).minus(b)` é igual a `a`.

Run: `cd api && ./gradlew test --tests '*MoneyPropertiesTest'`
Expected: PASS, com 3000 invocações.

- [ ] **Step 6: Declarar o módulo compartilhado e conferir o portão de cobertura**

- Em `LiberdadeApplication`, use `@Modulithic(sharedModules = "shared")`.
- Run: `cd api && ./gradlew build`
  Expected: `BUILD SUCCESSFUL`.
- Prova de que o portão funciona: anote `MoneyTest` e `MoneyPropertiesTest` com `@Disabled` e
  rode `./gradlew check`.
  - Expected: falha em `jacocoTestCoverageVerification`, com o pacote
    `br.com.liberdadefamilia.shared` entre as violações.
  - Depois, remova os dois `@Disabled`.

- [ ] **Step 7: Escrever o ADR-0013**

`docs/adr/0013-property-tests-without-jqwik.md`: Status Aceito, Data 2026-11-02, "Substitui em
parte: ADR-0006 (só a linha sobre jqwik)".
- **Contexto:**
  - O ADR-0006 previa testes de propriedade com jqwik.
  - As notas da versão 1.10.1 do jqwik (jqwik.net, consultadas em 2026-11-02) dizem que ela é
    provavelmente a última sobre o JUnit Platform 1.x. Versões futuras, "se um dia saírem",
    exigirão JUnit Platform 6 e Java ≥ 21.
  - O Spring Boot 4.1 já usa JUnit 6.
  - A licença do projeto passou a ter uma cláusula restringindo o uso por ferramentas de IA, o que
    conflita com o fluxo de trabalho deste repositório.
- **Decisão:** testes de propriedade com `@ParameterizedTest` e `@MethodSource` do JUnit 6.
  - A semente é fixa (`20261102L`), com 1000 casos por propriedade.
  - Quando um caso falha, o nome do teste mostra as entradas.
- **Alternativas:**

  | Alternativa | Por que não |
  |---|---|
  | jqwik 1.10.1 | Feito para JUnit Platform 1.x, com futuro incerto, e tem a cláusula de licença |
  | Outra biblioteca de property testing | Pesquisar só quando precisarmos de *shrinking*; hoje o custo não se paga |

- **Consequências:** não há *shrinking* (redução automática do caso que falha). As propriedades
  continuam documentadas como testes.

Adicione a linha 0013 em `docs/adr/README.md`. O ADR-0006 não é editado.

- [ ] **Step 8: Commit**

```bash
git add api docs/adr
git commit -m "feat(api): add Money value object with largest-remainder allocation"
```

---

### Task 5: Erros RFC 9457 e Clock

**Files:**
- Create:
  - `api/src/main/java/br/com/liberdadefamilia/shared/internal/ProblemHandler.java`
  - `api/src/main/java/br/com/liberdadefamilia/shared/internal/ClockConfig.java`
- Test:
  - `api/src/test/java/br/com/liberdadefamilia/shared/internal/ProblemHandlerTest.java`
  - `api/src/test/java/br/com/liberdadefamilia/shared/internal/ClockConfigTest.java`
- Modify: `api/src/test/java/br/com/liberdadefamilia/HealthProbesIT.java`

**Interfaces:**
- Consumes: `DomainException` (Task 4).
- Produces:
  - Qualquer exceção vira `application/problem+json` com as propriedades `code` e `detail`.
  - Erros de validação também trazem `errors: [{field, code}]`.
  - Um bean `java.time.Clock` em UTC, que os módulos dos próximos planos injetam.

- [ ] **Step 1: Escrever o teste (falhando)**

`ProblemHandlerTest` é um `@WebMvcTest` com `MockMvcTester`. Ele registra um controller de
teste, `ProblemProbeController`, como classe estática aninhada via `@Import`. Assim o
`@SpringBootTest` nunca o encontra no scan.

O controller tem estas rotas:
- GET `/test/domain-error` lança uma `DomainException` de teste com 409, `"test.conflict"` e
  `"Conflito de teste."`.
- GET `/test/boom` lança `new IllegalStateException("secret-internal-detail")`.
- POST `/test/validated` recebe `@Valid @RequestBody NameForm(@NotBlank String name)`.

Casos de teste:

| Requisição | Status | `code` | Outras checagens |
|---|---|---|---|
| GET `/test/domain-error` | 409 | `test.conflict` | `detail` = `"Conflito de teste."`; content type `application/problem+json` |
| GET `/test/boom` | 500 | `internal` | `detail` = `"Algo deu errado do nosso lado. Tente de novo em instantes."`; o corpo não contém `secret-internal-detail`, `IllegalStateException` nem `at br.com` |
| POST `/test/validated` com corpo `{` | 400 | `request.malformed` | `detail` = `"Não conseguimos ler os dados enviados."` |
| POST `/test/validated` com `{"name":""}` | 422 | `request.invalid` | `$.errors[0].field` = `name` e `$.errors[0].code` = `NotBlank` |
| POST `/test/validated` com `Content-Type: text/plain` | 415 | `request.unsupported` | — |
| GET `/nope` | 404 | `not-found` | `detail` = `"Não encontramos o que você procurou."` |
| DELETE `/test/boom` | 405 | `method.not-allowed` | — |

`ClockConfigTest`: `new ClockConfig().clock().getZone()` é `ZoneOffset.UTC`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*ProblemHandlerTest' --tests '*ClockConfigTest'`
Expected: falha de compilação ou asserts falhando (`code` ausente).

- [ ] **Step 3: Implementar**

`ProblemHandler` é um `@RestControllerAdvice` que estende `ResponseEntityExceptionHandler`.
- **`handleExceptionInternal`:** monta o `ProblemDetail` pelo status, com estes textos fixos:

  | Status | `code` | `detail` |
  |---|---|---|
  | 400 | `request.malformed` | Não conseguimos ler os dados enviados. |
  | 404 | `not-found` | Não encontramos o que você procurou. |
  | 405 | `method.not-allowed` | Esta ação não é permitida aqui. |
  | 406 e 415 | `request.unsupported` | Formato de dados não suportado. |
  | 422 | `request.invalid` | Alguns campos precisam de ajuste. |
  | 5xx | `internal` | Algo deu errado do nosso lado. Tente de novo em instantes. |
  | outros 4xx | `request.invalid` | Alguns campos precisam de ajuste. |

- **`handleMethodArgumentNotValid`:** responde 422 com `errors` (`field` e `FieldError.getCode()`).
- **`@ExceptionHandler(DomainException.class)`:** usa o `status()`, o `code()` e o `detail()` da
  própria exceção.
- **`@ExceptionHandler(Exception.class)`:** responde 500 `internal` e registra a exceção no log
  em nível `ERROR`, com stack trace. A resposta nunca leva a mensagem.

`ClockConfig` é uma `@Configuration` com `@Bean Clock clock()` que devolve
`Clock.systemUTC()`.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew test --tests '*ProblemHandlerTest' --tests '*ClockConfigTest'`
Expected: PASS.

- [ ] **Step 5: Conferir na aplicação inteira**

No `HealthProbesIT`, o caso `envIsNotExposed` passa a checar também o content type
`application/problem+json` e `$.code` = `not-found`.

Run: `cd api && ./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(api): add RFC 9457 problem handler and UTC clock"
```

---

### Task 6: `Money` no JSON

**Files:**
- Create: `api/src/main/java/br/com/liberdadefamilia/shared/internal/MoneyJacksonComponent.java`
- Test: `api/src/test/java/br/com/liberdadefamilia/shared/internal/MoneyJsonTest.java`

**Interfaces:**
- Consumes: `Money.parse` e `Money.toPlainString` (Task 4); o 400 `request.malformed` (Task 5).
- Produces: `Money` (de)serializado como `{"amount":"<texto>","currency":"<ISO 4217>"}` em toda
  a API.

- [ ] **Step 1: Escrever o teste (falhando)**

`MoneyJsonTest` é um `@WebMvcTest` com um `MoneyEchoController` aninhado, registrado via
`@Import`. A rota POST `/test/money` recebe `@RequestBody Money` e devolve o mesmo valor.

- `echoesNormalizedAmount`: `{"amount":"1234.5","currency":"BRL"}` → 200 e o corpo é exatamente
  `{"amount":"1234.50","currency":"BRL"}`.
- `rejectsNumericAmount`: `{"amount":12.5,"currency":"BRL"}` → 400 com `$.code` =
  `request.malformed`.
- `rejectsMalformedAmount` (`@ParameterizedTest`): estes valores de `amount` → 400
  `request.malformed`:
  - `"1e3"`, `"+5"`, `" 5"`
  - `"1.234,56"`, `"12.345"`
  - `""`, `"abc"`
  - `"1000000000000000"`
- `rejectsBadCurrency` (`@ParameterizedTest`): estes corpos → 400 `request.malformed`:
  - `{"amount":"1.00","currency":"XYZ"}`
  - `{"amount":"1.00"}`
  - `{"currency":"BRL"}`

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd api && ./gradlew test --tests '*MoneyJsonTest'`
Expected: FAIL. Sem o componente, o Jackson aceita `12.5` e `"1e3"` pelo construtor do record.

- [ ] **Step 3: Implementar `MoneyJacksonComponent`**

A classe é anotada com `@JacksonComponent` e tem duas classes aninhadas:
`Serializer extends ValueSerializer<Money>` e `Deserializer extends ValueDeserializer<Money>`.
- **Serializer:** escreve `amount` com `toPlainString()` e `currency` com o código ISO.
- **Deserializer:**
  - `amount` precisa ser um token de texto.
  - A parte decimal, como foi escrita, tem no máximo as casas da moeda. Por isso `"12.345"` é
    recusado em BRL.
  - Depois disso, chama `Money.parse`.
  - A moeda é lida com `Currency.getInstance`.
  - Qualquer falha vira exceção do Jackson (`ctxt.reportInputMismatch(...)`), para o Spring
    responder 400 e não 500.
  - As mensagens nunca contêm o valor recebido.

- [ ] **Step 4: Rodar e ver passar**

Run: `cd api && ./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(api): serialize Money as decimal string in JSON"
```

---

### Task 7: Esqueleto do web, ferramentas e fronteiras no ESLint

**Files:**
- Create:
  - `web/` gerado pelo create-vite com o template `react-ts`
  - `web/.prettierrc.json`, `web/.prettierignore`
  - `web/src/test-setup.ts`
  - `web/eslint/boundaries.js`
  - `web/eslint/boundaries.test.js`
- Modify:
  - `web/package.json`, `web/vite.config.ts`, `web/tsconfig.app.json`
  - `web/eslint.config.js`
  - `web/src/main.tsx`
  - `pnpm-lock.yaml`
- Delete: os arquivos de demonstração do template (`src/App.tsx`, `src/App.css`, `src/assets/`,
  `public/vite.svg`) e o `<link rel="icon">` do `index.html`. O ícone de verdade vem com o
  manifesto do PWA no Plano 2.

**Interfaces:**
- Produces:
  - Alias `@/*` → `web/src/*`.
  - Scripts em `web/package.json`: `dev`, `build` (`tsc -b && vite build`), `typecheck`
    (`tsc -b`), `lint`, `format`, `format:check`, `test` (`vitest run`) e
    `test:coverage` (`vitest run --coverage`).
  - `web/eslint/boundaries.js` exporta `CORE`, `OPTIONAL` e `boundaryConfigs()`.

- [ ] **Step 1: Gerar o projeto**

Run: `corepack pnpm create vite@latest web --template react-ts --no-interactive`
- Se essa versão do create-vite não conhecer `--no-interactive`, rode sem a flag e responda
  "No" para instalar e iniciar.

Depois:
- Ajuste `web/package.json`: `"name": "web"` e as versões de React, Vite e TypeScript da seção
  Tech Stack.
- Mantenha o TypeScript 7.0.2 só se `corepack pnpm install` não mostrar conflito de peer
  dependency com o `typescript-eslint`. Se mostrar, use o 6.x mais recente e registre no PR.
- Run: `corepack pnpm install` na raiz.
- Se o pnpm avisar sobre scripts de build ignorados, só adicione o pacote a
  `onlyBuiltDependencies` no `pnpm-workspace.yaml` se o build falhar sem ele.

- [ ] **Step 2: Configurar as ferramentas**

- **Alias `@/*`:**
  - Em `vite.config.ts`: `fileURLToPath(new URL('./src', import.meta.url))`.
  - Em `tsconfig.app.json`: `paths`, sem `baseUrl`.
- **Prettier:**
  - `.prettierrc.json` = `{"printWidth": 100, "singleQuote": true, "semi": false}`.
  - `.prettierignore` = `dist`, `coverage`.
- **Vitest** (bloco `test` em `vite.config.ts`, com `defineConfig` importado de `vitest/config`
  para o bloco ser tipado):
  - `environment: 'jsdom'`, `setupFiles: ['./src/test-setup.ts']`, sendo que o setup importa
    `@testing-library/jest-dom/vitest`.
  - `include: ['src/**/*.test.{ts,tsx}', 'eslint/**/*.test.js']`.
  - Coverage v8 com `include: ['src/**']` e
    `exclude: ['src/main.tsx', 'src/**/*.test.*', 'src/test-setup.ts', 'src/vite-env.d.ts']`.
  - Thresholds: 80 global para lines, functions, branches e statements; 90 nos quatro para
    `'src/shared/money/**'`.
- **devDependencies:** `prettier`, `vitest`, `@vitest/coverage-v8`, `jsdom`,
  `@testing-library/react`, `@testing-library/jest-dom`, `@types/node`.
- **ESLint:** em `eslint.config.js`, acrescente `coverage` ao `globalIgnores` e espalhe
  `...boundaryConfigs()` no fim do array.
- **`src/main.tsx`:** renderiza `<StrictMode><main><h1>Liberdade</h1></main></StrictMode>`. A
  Task 9 troca pelo roteador.

- [ ] **Step 3: Escrever o teste das fronteiras (falhando)**

`web/eslint/boundaries.test.js` começa com o docblock `// @vitest-environment node`.
- Ele cria `new ESLint({ cwd: <pasta web>, overrideConfigFile: true, overrideConfig: [...] })`.
- `overrideConfig` = `[{ languageOptions: { parser: tseslint.parser } }, ...boundaryConfigs()]`.
- O helper `lint(file, spec)` chama `lintText` com o código
  `import x from '<spec>'\nexport default x\n` e `filePath` = `<web>/<file>`. Ele devolve quantas
  mensagens têm `ruleId === 'no-restricted-imports'`.

| # | Arquivo | Import | Erros esperados |
|---|---|---|---|
| 1 | `src/features/planning/a.ts` | `@/features/ventures/b` | 1 |
| 2 | `src/features/transactions/a.ts` | `@/features/planning/b` | 1 |
| 3 | `src/features/planning/a.ts` | `@/shared/api/transactions` | 0 |
| 4 | `src/shared/ui/a.ts` | `@/features/transactions/b` | 1 |
| 5 | `src/app/routes.tsx` | `@/features/planning` | 0 |
| 6 | `src/features/planning/a.ts` | `../../features/ventures/b` | 1 |
| 7 | `src/features/ventures/a.ts` | `@/shared/money` | 0 |

Run: `corepack pnpm --filter web test`
Expected: FAIL, porque `boundaries.js` ainda não existe.

- [ ] **Step 4: Implementar `web/eslint/boundaries.js`**

```js
export const CORE = ['auth', 'onboarding', 'transactions', 'accounts', 'categories', 'dashboard', 'members', 'modules', 'privacy']
export const OPTIONAL = ['planning', 'indicators', 'gamification', 'ventures']
export function boundaryConfigs() // => flat config objects using `no-restricted-imports`
```

As regras, por grupo de arquivos:

| Arquivos | Pode importar | Não pode importar |
|---|---|---|
| `src/shared/**` | `@/shared/**` | `@/features/**`, `@/app/**` |
| `src/features/<core>/**` | `@/shared/**` e outros features do núcleo | `@/features/<optional>/**`, `@/app/**` |
| `src/features/<optional>/**` | `@/shared/**` (a API do núcleo é `@/shared/api`) e o próprio feature | qualquer outro `@/features/*`, `@/app/**` |
| `src/app/**` | tudo | — |

- Em todos os grupos, um padrão `regex` proíbe imports relativos que sobem para fora da pasta,
  como `^(\.\./)+(features|app|shared)/`. Isso força o uso de `@/` entre áreas.
- No flat config, as opções de uma regra **não se somam** entre objetos. Por isso cada grupo de
  arquivos precisa carregar a lista completa de padrões dele.
- A exceção "o próprio feature" usa negação: `'!@/features/planning'` e
  `'!@/features/planning/**'`.

- [ ] **Step 5: Verificar tudo**

Run: `corepack pnpm --filter web test`
Expected: PASS, com 7 testes.

Run: `corepack pnpm --filter web run format && corepack pnpm --filter web lint && corepack pnpm --filter web format:check && corepack pnpm --filter web typecheck && corepack pnpm --filter web build`
Expected: tudo sai com código 0. O `test:coverage` passa a valer a partir da Task 8, quando
`src/` tiver código.

- [ ] **Step 6: Commit**

```bash
git add web pnpm-lock.yaml pnpm-workspace.yaml
git commit -m "feat(web): add Vite React scaffold with module boundary lint rules"
```

---

### Task 8: `web/src/shared/money` com TDD

**Files:**
- Create: `web/src/shared/money/money.ts`, `web/src/shared/money/index.ts`
- Test: `web/src/shared/money/money.test.ts`

**Interfaces:**
- Produces (reexportado por `@/shared/money`):

```ts
export const MAX_CENTS: bigint // 99_999_999_999n (R$ 999.999.999,99)
export function parseCents(amount: string): bigint // API text -> cents
export function centsToApi(cents: bigint): string // cents -> API text
export function formatBRL(cents: bigint): string
export function appendDigit(cents: bigint, digit: number): bigint
export function removeLastDigit(cents: bigint): bigint
```

- [ ] **Step 1: Escrever o teste (falhando)**

```ts
const NBSP = ' '
const MINUS = '−'
it('formats PRD 7.1 total', () => expect(formatBRL(476544n)).toBe(`R$${NBSP}4.765,44`))
it('formats negative with U+2212', () => expect(formatBRL(-8790n)).toBe(`${MINUS}R$${NBSP}87,90`))
it('formats beyond 2^53 exactly', () =>
  expect(formatBRL(123456789012345678n)).toBe(`R$${NBSP}1.234.567.890.123.456,78`))
it('types 1,2,3,4 as R$ 12,34', () =>
  expect([1, 2, 3, 4].reduce((c, d) => appendDigit(c, d), 0n)).toBe(1234n))
it('ignores a digit past MAX_CENTS', () =>
  expect(appendDigit(10_000_000_000n, 0)).toBe(10_000_000_000n))
```

Os demais casos:

| Função | Entrada | Esperado |
|---|---|---|
| `formatBRL` | `0n`, `1234n` | `R$ 0,00`, `R$ 12,34` (com NBSP) |
| `parseCents` | `"1234.56"`, `"1234.5"`, `"-0.05"`, `"0"` | `123456n`, `123450n`, `-5n`, `0n` |
| `parseCents` | `"1.234,56"`, `"1e3"`, `"12.345"`, `""`, `" 5"`, `"+5"` | lança erro (`it.each`) |
| `centsToApi` | `123456n`, `-5n`, `0n` | `"1234.56"`, `"-0.05"`, `"0.00"` |
| `centsToApi` e `parseCents` | ida e volta de `-99999n`, `1n` e `MAX_CENTS` | volta ao mesmo valor |
| `appendDigit` | `(9_999_999_999n, 9)` | `MAX_CENTS` |
| `appendDigit` | `(MAX_CENTS, 9)` | `MAX_CENTS` |
| `appendDigit` | dígito `10`, `-1` ou `1.5` | lança `RangeError` |
| `removeLastDigit` | `1234n`, `0n` | `123n`, `0n` |

Run: `corepack pnpm --filter web test`
Expected: FAIL, porque o módulo não existe.

- [ ] **Step 2: Implementar `money.ts`**

- **`parseCents`:** valida com `/^-?\d+(\.\d{1,2})?$/` e monta o `bigint` com aritmética de
  texto. Nunca usa `Number`.
- **`formatBRL`:** monta o decimal exato em texto a partir do valor absoluto, por exemplo
  `"1234.56"`. Passa o texto para
  `new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'}).format(...)`; o `format`
  aceita texto decimal exato. Prefixa `'−'` quando o valor é negativo. A sobrecarga de
  `format(string)` está em `lib.es2023.intl`; se o `lib` do `tsconfig.app.json` não incluir
  `ES2023` ou mais novo, acrescente.
- **`appendDigit`:** devolve `cents * 10n + BigInt(digit)` se o resultado for ≤ `MAX_CENTS`;
  senão, devolve `cents`.

- [ ] **Step 3: Rodar e ver passar, com cobertura**

Run: `corepack pnpm --filter web test:coverage`
Expected: PASS, com `src/shared/money` acima de 90% e total acima de 80%.

- [ ] **Step 4: Commit**

```bash
git add web
git commit -m "feat(web): add bigint money helpers for BRL input and formatting"
```

---

### Task 9: Página inicial

**Files:**
- Create:
  - `web/src/app/routes.tsx`, `web/src/app/providers.tsx`, `web/src/app/NotFoundPage.tsx`
  - `web/src/shared/i18n/messages.pt-BR.ts`, `web/src/shared/i18n/index.ts`
  - `web/src/features/auth/SignInPage.tsx`
- Modify: `web/src/main.tsx`, `web/src/index.css`, `web/vite.config.ts`, `web/index.html`,
  `web/package.json`
- Test: `web/src/features/auth/SignInPage.test.tsx`, `web/src/app/routes.test.tsx`

**Interfaces:**
- Consumes: alias `@/` e setup do Vitest (Task 7).
- Produces:
  - `routes: RouteObject[]`, exportado por `src/app/routes.tsx`. Os planos seguintes acrescentam
    rotas aqui.
  - `AppProviders({children})`, que tem o `IntlProvider` pt-BR.
  - `messages` (pt-BR), com chaves `área.item`.
  - Tokens Tailwind `bg`, `surface`, `text`, `text-muted`, `primary`, `on-primary`, `positive` e
    `negative`, já com tema escuro.

- [ ] **Step 1: Escrever os testes (falhando)**

- `SignInPage.test.tsx`: renderiza `<AppProviders><SignInPage/></AppProviders>` e espera:
  - `getByRole('heading', {level: 1, name: 'Liberdade'})`;
  - `getByText('As finanças da sua família, simples e em paz.')`.
- `routes.test.tsx`: `createMemoryRouter(routes, {initialEntries: ['/nao-existe']})`, dentro de
  `AppProviders`, mostra:
  - o heading `'Página não encontrada.'`;
  - um link `'Voltar ao início'` com `href` `/`.

Run: `corepack pnpm --filter web test`
Expected: FAIL, porque os módulos não existem.

- [ ] **Step 2: Implementar**

- **Dependências:** `react-router`, `react-intl`, `@fontsource-variable/inter`, `tailwindcss` e
  `@tailwindcss/vite`, todas na versão mais recente.
- **`messages.pt-BR.ts`:**
  - `home.title`: "Liberdade"
  - `home.tagline`: "As finanças da sua família, simples e em paz."
  - `notFound.title`: "Página não encontrada."
  - `notFound.back`: "Voltar ao início"
- **`routes`:** `/` → `SignInPage`; `*` → `NotFoundPage`.
- **`main.tsx`:** importa `@fontsource-variable/inter` e `./index.css`, depois renderiza
  `RouterProvider` com `createBrowserRouter(routes)` dentro de `AppProviders`.
- **`index.css`:** `@import "tailwindcss";` e um bloco `@theme` com os tokens:

  | Token | Claro | Escuro |
  |---|---|---|
  | `--color-bg` | `#F4F8F8` | `#0B1F24` |
  | `--color-surface` | `#FFFFFF` | `#12303A` |
  | `--color-text` | `#0B3B45` | `#E6F0F1` |
  | `--color-text-muted` | `#3F6B73` | `#A7C1C5` |
  | `--color-primary` | `#0E5A66` | `#5FB3BF` |
  | `--color-on-primary` | `#FFFFFF` | `#0B1F24` |
  | `--color-positive` | `#1F7A4D` | `#4CC38A` |
  | `--color-negative` | `#B42318` | `#F47F72` |

  - A coluna "Escuro" vai em `@media (prefers-color-scheme: dark) { :root { … } }`.
  - `--font-sans: 'Inter Variable', system-ui, sans-serif`.
  - Raios de 8, 12 e 16 px.
  - O `body` usa `bg-bg text-text font-sans` e `font-variant-numeric: tabular-nums`, com altura
    de linha 1.5.
  - `warning` e `track` entram com os componentes que os usam (planos 3 e v0.2).
- **`SignInPage`:**
  - Um `<main>` centralizado com o `h1` em 32 px, peso 600, e a frase de apresentação em
    `text-muted`.
  - Os botões de login entram no Plano 2.
- **`NotFoundPage`:** `h1` e um `Link` para `/`, com foco visível de 2 px na cor `primary`.
- **`index.html`:** `<html lang="pt-BR">` e `<title>Liberdade</title>`.

- [ ] **Step 3: Rodar e ver passar**

Run: `corepack pnpm --filter web test:coverage && corepack pnpm --filter web lint && corepack pnpm --filter web typecheck && corepack pnpm --filter web build`
Expected: tudo com código 0.

- [ ] **Step 4: Conferir que não há chamadas a terceiros**

Run: `grep -rlE 'fonts\.googleapis|fonts\.gstatic' web/dist || echo none`
Expected: `none`. O `dist/assets` contém os arquivos `.woff2` da Inter.

- [ ] **Step 5: Commit**

```bash
git add web pnpm-lock.yaml
git commit -m "feat(web): add home page with pt-BR messages and design tokens"
```

---

### Task 10: Docker e Compose

**Files:**
- Create: `api/Dockerfile`, `api/.dockerignore`, `compose.yaml`
- Modify: `README.md` (seção "Como rodar")

**Interfaces:**
- Consumes: `bootJar` (Task 3) e as chaves do `.env.example` (Task 1).
- Produces:
  - Imagem da API com usuário não root (uid 10001), escutando a 8080. A Task 11 publica essa
    imagem no GHCR.
  - `docker compose up` sobe o banco e a API.

- [ ] **Step 1: Escrever o `api/Dockerfile`**

Duas etapas:
- **Builder:** `eclipse-temurin:25-jdk@sha256:<digest>`.
  - Copia o wrapper e os scripts do Gradle e roda `./gradlew --no-daemon dependencies`, como
    camada de cache.
  - Copia `src` e roda `./gradlew --no-daemon bootJar -x test`.
  - Extrai com `java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination extracted`.
- **Runtime:** `eclipse-temurin:25-jre@sha256:<digest>`.
  - Cria o usuário com `useradd --system --uid 10001 --no-create-home app`.
  - Copia as camadas `dependencies`, `spring-boot-loader`, `snapshot-dependencies` e
    `application`, nessa ordem.
  - `USER 10001` e `EXPOSE 8080`.
  - `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","org.springframework.boot.loader.launch.JarLauncher"]`.

Os digests são o **índice multiarquitetura**, obtido com
`docker buildx imagetools inspect eclipse-temurin:25-jre`. Não use o digest de uma plataforma só:
o CI gera ARM64.

`api/.dockerignore`: `build`, `.gradle`, `out`, `.idea`, `*.iml`.

- [ ] **Step 2: Escrever o `compose.yaml`**

Projeto `name: liberdade`, com dois serviços.
- **`db`:**
  - Imagem `postgres:18`, com ambiente vindo de `${POSTGRES_DB:?}`, `${POSTGRES_USER:?}` e
    `${POSTGRES_PASSWORD:?}`.
  - Volume `pgdata` montado em `/var/lib/postgresql`. Esse é o caminho da imagem 18; o `PGDATA`
    agora é `/var/lib/postgresql/18/docker`.
  - Healthcheck `pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}` a cada 5 s, com 10
    tentativas.
  - Porta `127.0.0.1:5432:5432`.
- **`api`:**
  - `build: ./api`, com `depends_on: db: condition: service_healthy`.
  - Ambiente:
    - `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${POSTGRES_DB:?}`
    - `SPRING_DATASOURCE_USERNAME=${POSTGRES_USER:?}`
    - `SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD:?}`
  - Porta `127.0.0.1:8080:8080`.

- [ ] **Step 3: Subir e verificar**

Run: `cp .env.example .env && docker compose up -d --build --wait`
Expected: os dois serviços sobem; `db` fica `healthy`.

Run: `curl --retry 30 --retry-delay 2 --retry-all-errors -fsS http://localhost:8080/actuator/health/readiness`
Expected: `{"status":"UP"}`

Run: `docker compose exec api id -u`
Expected: `10001`

Run: `docker compose down`
Expected: os containers são removidos. O volume continua.

- [ ] **Step 4: Preencher "Como rodar" no README**

Em pt-BR, com os comandos:
- Copiar `.env.example` para `.env`.
- `docker compose up -d --build --wait`.
- API em desenvolvimento: `cd api && ./gradlew bootTestRun` (sobe o PostgreSQL via
  Testcontainers).
- Web: `corepack pnpm install` e `corepack pnpm --filter web dev`.
- Testes: `./gradlew build` em `api/` e `corepack pnpm --filter web test`.

- [ ] **Step 5: Commit**

```bash
git add api/Dockerfile api/.dockerignore compose.yaml README.md
git commit -m "feat(infra): add API container image and local compose stack"
```

---

### Task 11: Workflows do GitHub Actions

**Files:**
- Create:
  - `.github/workflows/ci.yml`, `pr-title.yml`, `codeql.yml`, `dependency-submission.yml`
  - `.github/workflows/preview.yml`, `main.yml`, `release-please.yml`
  - `.github/dependabot.yml`
  - `release-please-config.json`, `.release-please-manifest.json`

**Interfaces:**
- Consumes: `./gradlew build` (Task 3), os scripts do web (Task 7), o commitlint (Task 2) e o
  Dockerfile (Task 10).
- Produces: checks com estes nomes exatos, que a Task 13 torna obrigatórios: `api`, `web`,
  `pr-title`, `dependency-review`, `analyze (java-kotlin)` e `analyze (javascript-typescript)`.

- [ ] **Step 1: Resolver os SHAs das actions**

Para cada action, pegue a última release e resolva o SHA:

```bash
gh api repos/<owner>/<repo>/commits/<tag> --jq .sha
```

Actions usadas:
- `actions/checkout`, `actions/setup-java`, `actions/setup-node`, `actions/upload-artifact`
- `gradle/actions` (`setup-gradle` e `dependency-submission`)
- `pnpm/action-setup`, `actions/dependency-review-action`, `github/codeql-action`
  (`init` e `analyze`)
- `docker/setup-buildx-action`, `docker/login-action`, `docker/metadata-action`,
  `docker/build-push-action`
- `cloudflare/wrangler-action`, `googleapis/release-please-action`

Escreva cada uma como `uses: owner/repo@<sha> # vX.Y.Z`.

- [ ] **Step 2: Escrever os workflows**

Todos começam com `permissions: {}`. O que muda entre eles:

| Arquivo | Gatilho | Jobs e pontos que importam |
|---|---|---|
| `ci.yml` | `pull_request` e `push` na `main`; `concurrency` por ref com cancelamento | **`api`**: `contents: read`; Java 25 (temurin) e `setup-gradle`; `./gradlew build` em `api/`; sobe o relatório JaCoCo como artefato. **`web`**: `contents: read`; `pnpm/action-setup` (lê o `packageManager`) e `setup-node` com `node-version-file: .node-version` e `cache: pnpm`; `pnpm install --frozen-lockfile`; depois `pnpm --filter web` com `lint`, `format:check`, `typecheck`, `test:coverage` e `build`; sobe `web/coverage`. **`dependency-review`**: só em PR; `contents: read`; `fail-on-severity: moderate`; `allow-licenses` (lista abaixo) |
| `pr-title.yml` | `pull_request` com tipos `opened`, `edited`, `synchronize` e `reopened` | **`pr-title`**: `contents: read`; instala o workspace e roda `echo "$PR_TITLE" \| pnpm exec commitlint`, com `PR_TITLE: ${{ github.event.pull_request.title }}` em `env:`. Nunca interpole o título direto no `run:` |
| `codeql.yml` | `pull_request`, `push` na `main` e semanal | **`analyze`**: matriz `language: [java-kotlin, javascript-typescript]` com `build-mode: none`; `security-events: write`, `contents: read` e `actions: read` |
| `dependency-submission.yml` | `push` na `main` | `contents: write`; `gradle/actions/dependency-submission` com `build-root-directory: api` |
| `preview.yml` | `pull_request` | `environment: preview`; roda só se `github.event.pull_request.head.repo.full_name == github.repository` e `github.actor != 'dependabot[bot]'`; `contents: read` e `pull-requests: write`; faz o build do web e roda `wrangler-action` com `apiToken` e `accountId` vindos de secrets do environment, `packageManager: pnpm`, `workingDirectory: web`, `wranglerVersion` fixo em uma release com 7 dias ou mais e `command: pages deploy dist --project-name=liberdade --branch=pr-${{ github.event.pull_request.number }}`; depois `gh pr comment <n> --edit-last --create-if-none --body "Preview: $URL"`, com `URL` vindo da saída `deployment-url` via `env:` |
| `main.yml` | `push` na `main` | `runs-on: ubuntu-24.04-arm`; `contents: read` e `packages: write`; buildx, login no GHCR com `GITHUB_TOKEN` e metadata (`type=sha` e `type=raw,value=main`); `build-push-action` com `context: api`, `platforms: linux/arm64`, imagem `ghcr.io/warthogozz-cyber/liberdade-api`, `sbom: true`, `provenance: mode=max` e cache `type=gha` |
| `release-please.yml` | `push` na `main` | `contents: write` e `pull-requests: write`; `release-please-action` com os arquivos de configuração e manifesto |

Lista de `allow-licenses`:
- MIT, MIT-0, ISC, 0BSD, Unlicense, CC0-1.0
- Apache-2.0, BSD-2-Clause, BSD-3-Clause, BlueOak-1.0.0, MPL-2.0
- GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only,
  LGPL-3.0-or-later, AGPL-3.0-only, AGPL-3.0-or-later
- CC-BY-4.0, OFL-1.1, Python-2.0
- EPL-1.0 e EPL-2.0, aceitas só para dependências duplamente licenciadas ou de teste, como o
  JUnit

Qualquer outra licença legítima entra na lista com o motivo explicado no PR.

**Limitação conhecida:** a revisão de dependências do Gradle só enxerga o que o
`dependency-submission` envia a partir da `main`.

Configuração do release-please:
- `release-please-config.json`:
  `{"packages": {".": {"release-type": "simple", "bump-minor-pre-major": true}}}`.
- `.release-please-manifest.json`: `{".": "0.0.0"}`.

- [ ] **Step 3: Escrever o `dependabot.yml`**

Ecossistemas:
- `gradle` em `/api`
- `npm` em `/`
- `github-actions` em `/`
- `docker` em `/api`
- `docker-compose` em `/`

Em todos: `schedule.interval: weekly`, `cooldown.default-days: 7`, um grupo `patterns: ["*"]` e
`commit-message: {prefix: chore, include: scope}`.

- [ ] **Step 4: Validar**

Run: `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/repo" -w /repo rhysd/actionlint:<versão mais recente> -color`
Expected: nenhuma saída e código 0.

Run: `grep -RhoE 'uses: [^ ]+' .github/workflows | grep -vE '@[0-9a-f]{40}$' || echo all-pinned`
Expected: `all-pinned`

Run: `grep -L '^permissions: {}' .github/workflows/*.yml || true`
Expected: nenhuma saída.

- [ ] **Step 5: Commit**

```bash
git add .github release-please-config.json .release-please-manifest.json
git commit -m "ci: add CI, CodeQL, preview, image publish, release-please and Dependabot"
```

---

### Task 12: Criar o repositório e abrir o PR #1 (ação externa)

Cada passo desta tarefa altera algo no GitHub. **Peça confirmação ao mantenedor antes de cada um**
(AskUserQuestion) e mostre o comando que vai rodar.

**Files:** nenhum arquivo versionado. JSONs auxiliares ficam numa pasta temporária fora do
repositório.

**Interfaces:**
- Consumes: as branches `main` (só docs) e `feat/foundation` (Tasks 1–11).
- Produces: o repositório `warthogozz-cyber/liberdade` e o PR #1 com CI verde e comentário de
  preview.

- [ ] **Step 1: Confirmar nome e visibilidade, depois ampliar os escopos do `gh`**

Confirme `warthogozz-cyber/liberdade`, público. Se o nome já existir, pergunte qual usar.

Run: `gh auth refresh -s project,workflow,read:packages`
O mantenedor conclui o fluxo no navegador.

- [ ] **Step 2: Criar o repositório e enviar a `main`**

```bash
gh repo create warthogozz-cyber/liberdade --public --source . --remote origin
git push -u origin main
```

Expected: a `main` remota tem só os commits de docs.

- [ ] **Step 3: Configurar o repositório**

- `gh repo edit --enable-rebase-merge --enable-squash-merge=false --enable-merge-commit=false`
  - Só rebase por enquanto, para o PR #1 manter os commits de cada tarefa. A Task 13 troca para
    squash.
- `gh api -X PUT repos/{owner}/{repo}/actions/permissions/workflow -f default_workflow_permissions=read -F can_approve_pull_request_reviews=true`
- `gh api -X PATCH repos/{owner}/{repo} --input security.json`
  - O JSON liga `secret_scanning` e `secret_scanning_push_protection`.
- Três chamadas `gh api -X PUT`:
  - `repos/{owner}/{repo}/vulnerability-alerts`
  - `repos/{owner}/{repo}/automated-security-fixes`
  - `repos/{owner}/{repo}/private-vulnerability-reporting`

Run: `gh api repos/{owner}/{repo} --jq '.security_and_analysis'`
Expected: secret scanning e push protection `enabled`.

- [ ] **Step 4: Criar os environments**

Com `gh api -X PUT repos/{owner}/{repo}/environments/<nome> --input <json>`:
- `preview`: sem regras.
- `staging`: `deployment_branch_policy` = `{protected_branches: true, custom_branch_policies: false}`.
- `production`:
  - Mesma política do `staging`.
  - `reviewers: [{type: "User", id: <id>}]`, com o id obtido por `gh api user --jq .id`.

- [ ] **Step 5: Preparar o Cloudflare Pages (o mantenedor faz)**

O mantenedor:
1. Cria o projeto `liberdade` como Direct Upload no Cloudflare Pages. O plano gratuito custa R$ 0.
2. Cria um API token só com a permissão "Cloudflare Pages: Edit".
3. Roda os comandos abaixo. O agente não digita esses valores.

```bash
gh secret set CLOUDFLARE_API_TOKEN --env preview
gh secret set CLOUDFLARE_ACCOUNT_ID --env preview
```

- [ ] **Step 6: Abrir o PR #1**

```bash
git push -u origin feat/foundation
gh pr create --base main --head feat/foundation --title "feat: foundation (plan 1)" --body-file <arquivo temporário>
```

O corpo em pt-BR segue o template de PR e inclui:
- o que entrou;
- como testar;
- as versões escolhidas e os desvios;
- limitações conhecidas: release PRs não disparam CI e a revisão de dependências do Gradle só
  usa os dados da `main`.

O corpo termina com a linha de atribuição.

- [ ] **Step 7: Verificar o CI e o preview**

Run: `gh pr checks 1 --watch`
Expected: `api`, `web`, `pr-title`, `dependency-review`, `analyze (java-kotlin)`,
`analyze (javascript-typescript)` e `preview` em `pass`.

Run: `curl -fsS <URL do comentário de preview> | grep -o '<title>Liberdade</title>'`
Expected: `<title>Liberdade</title>`

O preview desta fase é a página estática. O MSW entra no Plano 2.

---

### Task 13: Regras, rótulos, marcos, Project e merge (ação externa)

Mesma regra da Task 12: **confirme cada passo com o mantenedor**.

**Files:** nenhum arquivo versionado.

**Interfaces:**
- Consumes: os nomes de checks da Task 11 e o PR #1 verde da Task 12.
- Produces:
  - A `main` protegida.
  - O GitHub Project "Liberdade" com 14 issues RF.
  - A primeira imagem no GHCR.
  - O PR do release-please aberto, mas não mergeado.

- [ ] **Step 1: Criar o ruleset da `main`**

Use `gh api -X POST repos/{owner}/{repo}/rulesets --input ruleset.json`, com
`name: "main"`, `target: "branch"`, `enforcement: "active"` e
`conditions.ref_name.include: ["~DEFAULT_BRANCH"]`.

Regras:
- `deletion`
- `non_fast_forward`
- `required_linear_history`
- `pull_request`, com `required_approving_review_count: 0` e
  `required_review_thread_resolution: true`
- `required_status_checks`, com `strict_required_status_checks_policy: true` e os seis contexts
  da Task 11, cada um com `integration_id: 15368` (GitHub Actions)

Run: `gh api repos/{owner}/{repo}/rulesets --jq '.[].name'`
Expected: `main`

- [ ] **Step 2: Fazer o merge do PR #1 e trocar para squash**

```bash
gh pr merge 1 --rebase
gh repo edit --enable-squash-merge --enable-rebase-merge=false --delete-branch-on-merge
gh api -X PATCH repos/{owner}/{repo} -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=PR_BODY
```

Expected: a `main` tem os commits das Tasks 1–11 em histórico linear.

- [ ] **Step 3: Criar os rótulos e os marcos**

- **Rótulos:** `gh label create` para `area:api`, `area:web`, `area:infra`, `area:docs`,
  `type:feature`, `type:bug`, `type:chore` e `type:docs`, cada um com uma descrição curta em
  pt-BR.
- **Marcos:** `gh api repos/{owner}/{repo}/milestones -f title=… -f description=…`, com as
  descrições do roadmap:

  | Marco | Descrição |
  |---|---|
  | Fase 0 | Fundação |
  | v0.1 | Núcleo |
  | v0.2 | Planejamento |
  | v0.3 | Indicadores e gamificação |
  | v0.4 | Novo Projeto |
  | Fase 2 | Construtor |
  | Fase 3 | IA e trilhas |
  | Fase 4 | Dados externos |
  | Fase 5 | Local e social |
  | Fase 6 | Lojas e abertura |

- [ ] **Step 4: Criar o Project e as issues RF**

```bash
gh project create --owner warthogozz-cyber --title "Liberdade"
gh project link <número> --owner warthogozz-cyber --repo warthogozz-cyber/liberdade
```

Para cada linha da tabela abaixo, rode `gh issue create` com:
- título `RF-NN: <Título>`;
- rótulo `type:feature`;
- o marco da tabela;
- `--project "Liberdade"`;
- corpo em pt-BR com link para
  `https://github.com/warthogozz-cyber/liberdade/blob/main/docs/prd.md#11-rastreabilidade` e a
  coluna "Onde entra" da seção 11 do PRD.

| RF | Título | Marco |
|---|---|---|
| RF-01 | Estrutura e colaboração | v0.1 |
| RF-02 | Núcleo financeiro | v0.1 |
| RF-03 | Construtor | Fase 2 |
| RF-04 | Modos de uso | v0.1 |
| RF-05 | Guias passo a passo | v0.1 |
| RF-06 | Trilhas | Fase 3 |
| RF-07 | Ranking de bancos | Fase 4 |
| RF-08 | Catálogo de investimentos | Fase 4 |
| RF-09 | Biblioteca de renda extra | v0.4 |
| RF-10 | Referências e fontes | v0.3 |
| RF-11 | Agente de IA | Fase 3 |
| RF-12 | Geolocalização e preços | Fase 5 |
| RF-13 | Redes sociais | Fase 5 |
| RF-14 | Gamificação | v0.2 |

Run: `gh issue list --label type:feature --limit 20 --json title --jq length`
Expected: `14`

- [ ] **Step 5: Verificar a `main`**

- Run: `gh run list --branch main --workflow main.yml --limit 1 --json conclusion --jq '.[0].conclusion'`
  Expected: `success`
- Run: `gh api users/warthogozz-cyber/packages/container/liberdade-api --jq .name`
  Expected: `liberdade-api`
- Run: `gh pr list --label "autorelease: pending" --json title --jq '.[].title'`
  Expected: o título do PR do release-please. **Não faça o merge dele antes da v0.1.**
- A Fase 0 está concluída: o PR #1 passou no CI e gerou preview.
