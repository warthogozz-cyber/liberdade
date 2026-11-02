# Planos de implementação

O spec do MVP está em [docs/](../../README.md). A entrega foi dividida em cinco planos, um por fatia
vertical. Cada plano é escrito depois que o anterior entra na `main`, para aproveitar o que se
aprendeu na execução.

| # | Plano | Escopo | Critério de saída | Status |
|---|---|---|---|---|
| 1 | [Fundação](2026-11-02-liberdade-plan-1-foundation.md) | Repositório público, CI/CD, esqueletos da API e do PWA, `Money` no back e no front, erros RFC 9457, Docker Compose, Project com RF-01 a RF-14 | Fase 0 do [roadmap](../../roadmap.md): um PR passa no CI e gera preview | Em revisão |
| 2 | Entrar e lançar | Login com Google e passkey, família criada sozinha, conta e categorias padrão, primeiro gasto com fila offline, OpenAPI, MSW e E2E com Playwright (US-01, US-02, US-03, US-05) | S1: primeiro gasto em menos de 60 s, inclusive em modo avião | A escrever |
| 3 | Ver e organizar | Receitas e transferências, contas, categorias, saldo, painel do mês, editar e excluir com "Desfazer", primeiros passos (US-04, US-06, US-07, US-08, US-13) | Exemplo da seção 7.1 do PRD correto no painel; cobertura do domínio ≥ 90% (S4) | A escrever |
| 4 | Família e privacidade | Convites, papéis, Jovem com consentimento parental, módulos ligáveis, auditoria, exportar e excluir conta (US-09 a US-12) | S2 passa com todos os módulos opcionais desligados; exportação e exclusão funcionam | A escrever |
| 5 | No ar | AWS em São Paulo (staging e produção), deploy por SSM, domínio, política de privacidade publicada, uso pela família por 2 semanas | v0.1 em produção com deploy só pelo pipeline (S5) | A escrever |
