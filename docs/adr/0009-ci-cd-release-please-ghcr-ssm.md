# ADR-0009: CI/CD — GitHub Actions, release-please, GHCR e SSM

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

Critério S5 do PRD: CI verde em todo PR e deploy reproduzível. Uma pessoa mantém o projeto, então
o pipeline precisa fazer o trabalho chato sozinho.

## Decisão

### Em todo pull request

- **API:** Spotless (formatação), compilação, testes (JUnit, AssertJ, jqwik (ver ADR-0013), Testcontainers com
  PostgreSQL 18), verificação do Spring Modulith, testes de isolamento, cobertura com JaCoCo.
- **Front:** ESLint, Prettier, typecheck, testes (Vitest, Testing Library, fake-indexeddb),
  cobertura, build.
- **E2E:** Playwright contra a pilha local em Docker Compose: "entrar → criar família → 1º gasto em
  menos de 60 s", também em modo avião, com axe. O login usa um provedor OIDC de teste local, nunca
  o Google real.
- **Segurança:** CodeQL (Java e TypeScript), Dependabot, secret scanning com push protection.
- **Convenções:** commitlint (Conventional Commits) no título do PR.
- **Preview:** front no Cloudflare Pages com **API simulada no navegador (MSW) e dados de exemplo**.
  Um preview em `pages.dev` não consegue usar a sessão do staging, porque o cookie é do domínio
  `liberdadefamilia.com.br`.
- **Portões de cobertura:** domínio (cálculos financeiros) ≥ 90%; geral ≥ 80%.

### Na `main`

- Imagem Docker ARM64 publicada no GHCR, com a tag do commit.
- Deploy no staging pelo SSM Run Command (`docker compose pull && up`), migrações com Flyway e smoke
  test no `/actuator/health/readiness`.
- Front publicado em `staging.liberdadefamilia.com.br`.

### Releases

- **release-please** abre o PR de release com changelog e versão semver a partir dos commits.
- Ao criar a tag `vX.Y.Z`, o deploy de produção espera **aprovação manual** (GitHub Environments).
- **Rollback:** reimplantar a tag anterior; migrações sempre compatíveis com a versão anterior.

### Acesso à AWS

GitHub Actions assume um papel IAM via **OIDC**, com permissão só para o SSM Run Command na
instância e leitura do que o deploy precisa. Nenhuma chave de acesso fixa.

### Repositório

- `main` protegida por ruleset: PR obrigatório, CI verde, histórico linear (squash merge).
- CODEOWNERS, templates de issue e PR, actions fixadas por SHA, `GITHUB_TOKEN` com permissões
  mínimas.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Preview de PR apontando para o staging | O cookie de sessão não atravessa de `pages.dev` para `liberdadefamilia.com.br` |
| Deploy por SSH | Exige porta 22 aberta e chave guardada; o SSM dispensa as duas |
| Semantic-release | Publica direto da `main`; o release-please dá um PR revisável antes da versão |

## Consequências

- Custo R$ 0: Actions, CodeQL e secret scanning são grátis em repositório público.
- O E2E precisa de um provedor OIDC de teste no Docker Compose (definido no plano de
  implementação).
