# ADR-0002: Stack — React PWA + Spring Boot + PostgreSQL

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

- O mantenedor é desenvolvedor backend experiente em Java/Spring Boot, AWS e PostgreSQL, e também
  trabalha com React.
- O app precisa rodar no celular e no PC desde o início, funcionar sem sinal para lançar gastos e,
  mais tarde, ir para as lojas (Capacitor) e para o desktop (Tauri).
- Cálculos financeiros exigem aritmética decimal exata e muitos testes.

## Decisão

**PWA em React + API em Spring Boot + PostgreSQL**, nas versões estáveis abaixo (verificadas em
2026-11-02):

| Peça | Versão | Fonte |
|---|---|---|
| Java (LTS) | 25 | [endoflife.date/oracle-jdk](https://endoflife.date/api/oracle-jdk.json) |
| Spring Boot | 4.1.2 | [endoflife.date/spring-boot](https://endoflife.date/api/spring-boot.json) |
| PostgreSQL (RDS) | 18 (18.6 no RDS) | [Calendário do RDS for PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-release-calendar.html) |
| React | 19.3.1 | [npm: react](https://registry.npmjs.org/-/package/react/dist-tags) |
| Vite | 8.3.5 | [npm: vite](https://registry.npmjs.org/-/package/vite/dist-tags) |
| TypeScript | 7.0.2 | [npm: typescript](https://registry.npmjs.org/-/package/typescript/dist-tags) |
| Node.js (LTS) | 24 | [endoflife.date/nodejs](https://endoflife.date/api/nodejs.json) |

Escolhas de ferramenta (decididas por mim, sem impacto no produto):

- **Gradle com Kotlin DSL** na API: cache de build e configuração mais rápidos no CI.
- **pnpm** no front: instalação rápida e lockfile estrito.
- **Node 24 LTS** por enquanto: o Node 26 virou LTS em 28/10/2026, poucos dias atrás; migramos
  quando o ecossistema confirmar o suporte (revisar na v0.2).
- **TypeScript 7** (compilador nativo). Se alguma ferramenta, como o typescript-eslint, ainda não
  suportar a 7.x no início do projeto, fixamos a última 6.x até haver suporte.

## Alternativas consideradas

| Alternativa | Por que não agora |
|---|---|
| React PWA + Supabase | MVP mais rápido e barato (R$ 0 no plano Free, ≈ R$ 136/mês no Pro), mas regras de negócio em SQL e TypeScript, fora da zona de conforto do mantenedor e sem `BigDecimal` |
| Expo (React Native) + Node | Ótimo no celular, mas web e desktop ficam fracos para o Construtor e os painéis (RF-03) |
| Next.js com renderização no servidor | Não ajuda no offline e complica o PWA; o app é privado, sem necessidade de SEO |

## Consequências

- Duas linguagens (Java e TypeScript) e custo fixo maior que um backend serverless.
- Domínio financeiro com `BigDecimal`, JUnit e jqwik, onde o mantenedor é mais produtivo.
- Spring AI fica disponível para a fase 3 (agente de IA) sem trocar de stack.
- Versões são revisadas a cada release minor pelo Dependabot; este ADR registra o ponto de partida.
