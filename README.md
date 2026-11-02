# Liberdade

Liberdade é um app de código aberto para cuidar das finanças da família e, quando fizer sentido,
ajudar a encontrar uma renda extra. A ideia é ser poderoso por baixo, simples na superfície: o
cálculo é rigoroso, mas o que aparece na tela é claro e sem jargão.

## Status

Em construção — Fase 0.

## Como rodar

Copie as variáveis de ambiente (o `.env` não é versionado):

```bash
cp .env.example .env
```

Suba o banco e a API em contêineres:

```bash
docker compose up -d --build --wait
```

API em desenvolvimento (sobe o PostgreSQL via Testcontainers):

```bash
cd api && ./gradlew bootTestRun
```

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

## Documentação

- [Documentação do projeto](docs/README.md)
- [Planos de implementação](docs/superpowers/plans/README.md)

## Licença

Distribuído sob a licença [AGPL-3.0](LICENSE).
