# Planos de implementação

O spec do MVP está em [docs/](../../README.md). A entrega foi dividida em cinco planos, um por fatia
vertical. Cada plano é escrito depois que o anterior entra na `main`, para aproveitar o que se
aprendeu na execução.

| # | Plano | Escopo | Critério de saída | Status |
|---|---|---|---|---|
| 1 | [Fundação](2026-11-02-liberdade-plan-1-foundation.md) | Repositório público, CI/CD, esqueletos da API e do PWA, `Money` no back e no front, erros RFC 9457, Docker Compose, Project com RF-01 a RF-14 | Fase 0 do [roadmap](../../roadmap.md): um PR passa no CI e gera preview | Concluído |
| 2 | [Entrar e lançar](2026-11-02-liberdade-plan-2-sign-in-and-first-expense.md) | Login com Google e passkey, família criada sozinha, conta e categorias padrão, primeiro gasto com fila offline, OpenAPI, MSW e E2E com Playwright (US-01, US-02, US-03, US-05) | S1: primeiro gasto em menos de 60 s, inclusive em modo avião | Concluído |
| 3 | [Ver e organizar](2026-11-16-liberdade-plan-3-view-and-organize.md) | Receitas e transferências, contas, categorias, saldo, painel do mês, editar e excluir com "Desfazer", primeiros passos (US-04, US-06, US-07, US-08, US-13) | Exemplo da seção 7.1 do PRD correto no painel; cobertura do domínio ≥ 90% (S4) | Concluído |
| 4 | [Família e privacidade](2026-11-16-liberdade-plan-4-family-and-privacy.md) | Convites, papéis, Jovem com consentimento parental, módulos ligáveis, auditoria, exportar e excluir conta (US-09 a US-12) | S2 passa com todos os módulos opcionais desligados; exportação e exclusão funcionam | Concluído |
| 5 | [No ar](2026-11-16-liberdade-plan-5-go-live.md) | AWS em São Paulo (staging e produção), deploy por SSM, domínio, política de privacidade publicada, medição do cookie de sessão no iOS no staging ([runbook](../../runbooks/ios-session-cookie.md)), uso pela família por 2 semanas | v0.1 em produção com deploy só pelo pipeline (S5) | Em execução |

## Próximos planos

A ordem segue o [roadmap](../../roadmap.md) revisto em 2026-11-17. Cada um é escrito quando o
anterior entra na `main`.

| # | Plano | Escopo | Status |
|---|---|---|---|
| 6 | [Cobrança e Retrato do mês](2026-11-17-liberdade-plan-6-billing.md) | Pré-venda Família Fundadora no Asaas (pagamento único, reembolso em 7 dias) e Retrato do mês grátis ([spec](../specs/2026-11-17-liberdade-plan-6-billing-design.md), [ADR-0015](../../adr/0015-revenue-model-and-billing.md)) | Concluído (cobrança desligada até o go-live) |
| 7 | v0.2 Planejamento | Orçamento, metas, reserva, recorrências, projeção do saldo, gastos fixos e variáveis, sincronização ao vivo, forma de pagamento, país e moeda no modelo, movimento e som, em três entregas (7.1, 7.2 e 7.3) ([spec](../specs/2026-11-23-liberdade-plan-7-planning-design.md); [plano 7.1](2026-11-23-liberdade-plan-7-1-foundations.md); [plano 7.2](2026-11-23-liberdade-plan-7-2-planning.md)) | 7.1 e 7.2 concluídas e conferidas no staging (7.1 em 2026-11-23, PR #46; 7.2 em 2026-11-24, PR #49); 7.3 a escrever |
| 8 | v0.3 Indicadores e gamificação | Indicadores com fonte, sequência e conquistas, dicas e tours, importação OFX/CSV, planos mensal e anual | A escrever |
| 9 | v0.4 Trilhas de renda extra | Projetos de renda extra em trilhas com modelos prontos | A escrever |
| 10 | Feira | Mercados do OpenStreetMap, mapa ao tocar, cartões dos mercados, preços pela NFC-e | A escrever |
