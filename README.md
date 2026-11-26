# Liberdade

Liberdade é um app de código aberto para cuidar das finanças da família e, quando fizer sentido,
ajudar a encontrar uma renda extra. A ideia é ser poderoso por baixo, simples na superfície: o
cálculo é rigoroso, mas o que aparece na tela é claro e sem jargão.

## Status

Em construção — Fase 1 (v0.1 Núcleo).

## Como rodar

Copie as variáveis de ambiente (o `.env` não é versionado):

```bash
cp .env.example .env
```

Suba o banco, a API e um login de teste no lugar do Google (caminho recomendado):

```bash
docker compose -f compose.yaml -f compose.e2e.yaml up -d --build
```

O login de teste aceita qualquer nome de usuário e, se você quiser, claims em JSON, como
`{"name": "Ana Teste", "email": "ana@example.com"}`.

**Ou** rode a API em desenvolvimento (sobe o PostgreSQL via Testcontainers). Os dois usam a porta
8080, então escolha um. Se a stack do compose estiver no ar, pare a API antes com
`docker compose stop api`. A API em desenvolvimento também usa o login de teste, que precisa estar
no ar. O `bootTestRun` mantém o terminal ocupado enquanto a API roda:

```bash
docker compose -f compose.yaml -f compose.e2e.yaml up -d mock-oauth2
cd api && ./gradlew bootTestRun
```

Para entrar com o Google de verdade (opcional):

1. O mantenedor cria um cliente OAuth no Google com o redirect
   `http://localhost:8080/login/oauth2/code/google`.
2. Põe as credenciais em `GOOGLE_CLIENT_ID` e `GOOGLE_CLIENT_SECRET`, no `.env`.
3. Sobe sem o login de teste:

   ```bash
   docker compose up -d --build --wait
   ```

Sem essas credenciais, a API não sobe: o client id vazio falha logo no início.

Instale as dependências da web:

```bash
corepack pnpm install
```

Rode a web em desenvolvimento:

```bash
corepack pnpm --filter web dev
```

Testes da API:

```bash
cd api && ./gradlew build
```

Testes da web:

```bash
corepack pnpm --filter web test
```

## Testes de ponta a ponta

Os testes de ponta a ponta usam o Playwright, a API no Docker e o login de teste no lugar do Google.
Suba a stack, instale o navegador do Playwright (uma vez só) e rode a suíte:

```bash
docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait
corepack pnpm --filter web exec playwright install chromium webkit
corepack pnpm --filter web e2e
```

A suíte roda em três projetos: `chromium` (desktop), `mobile-chrome` (Android, Pixel 7) e
`mobile-safari` (iPhone 15, WebKit). Para rodar só um, use `--project`, por exemplo
`corepack pnpm --filter web e2e --project=mobile-safari`. Os testes de passkey precisam do
dispositivo virtual do Chromium e ficam de fora no WebKit.

A suíte usa a porta 5173, então pare o `dev` da web antes de rodar.

A API aceita 20 tentativas de login por minuto por IP, e a suíte toda gasta 7. Rodar a suíte várias
vezes seguidas no mesmo minuto pode bater no limite: o login volta com "Muitas tentativas" (429).
Nesse caso, espere um minuto e rode de novo.

## Documentação

- [Documentação do projeto](docs/README.md)
- [Planos de implementação](docs/superpowers/plans/README.md)

## Licença

Distribuído sob a licença [AGPL-3.0](LICENSE).
