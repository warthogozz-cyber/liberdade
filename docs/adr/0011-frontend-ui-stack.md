# ADR-0011: Bibliotecas do front

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

O PWA precisa ser acessível (WCAG 2.2 AA), funcionar offline para lançar, ter gráficos e ser
mantido por uma pessoa. Todas as bibliotecas precisam ser compatíveis com AGPL-3.0.

## Decisão

| Necessidade | Escolha | Licença |
|---|---|---|
| Componentes acessíveis | Primitivas Radix, no estilo shadcn/ui (código no repositório) | MIT |
| Estilo | Tailwind CSS com tokens do [design system](../design-system.md) | MIT |
| Dados do servidor | TanStack Query, com cache persistido para leitura offline | MIT |
| Fila offline | Dexie (IndexedDB) | Apache-2.0 |
| Service Worker | vite-plugin-pwa (Workbox) | MIT |
| Rotas | React Router | MIT |
| Formulários e validação | React Hook Form + Zod | MIT |
| Textos e formatação | react-intl (FormatJS) + `Intl` nativo | BSD-3-Clause |
| Gráficos | Apache ECharts, carregado sob demanda | Apache-2.0 |
| API simulada (testes e previews) | MSW | MIT |

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| MUI ou Chakra | Visual pronto mais difícil de deixar "calmo e confiável" sem brigar com o tema |
| Recharts ou Chart.js | Bons, mas o ECharts cobre os gráficos futuros (Construtor, investimentos) sem trocar de biblioteca |
| Redux | O estado é quase todo do servidor; TanStack Query resolve com menos código |

## Consequências

- Componentes ficam no repositório e podem ser ajustados sem esperar biblioteca.
- ECharts pesa mais; por isso carrega só nas telas com gráfico.
