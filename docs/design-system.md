# Design system

> **Status:** rascunho para revisão · **Data:** 2026-11-02 · Estilo: **calmo e confiável**

## 1. Princípios

1. **Calma antes de alarme.** Dinheiro já pesa; a interface não aumenta a ansiedade. Vermelho só
   quando algo passou do limite, e sempre com texto.
2. **Um toque para o essencial.** Lançar um gasto é a ação mais importante e fica sempre à mão.
3. **Cor nunca sozinha.** Todo significado de cor também aparece em sinal, ícone ou texto.
4. **Mostre a fonte.** Todo número externo vem com fonte e data (`SourceStamp`).
5. **Sem culpa e sem pressão.** Nada de urgência falsa, contagem regressiva ou "você perdeu".

## 2. Cores

Contraste medido pela fórmula do WCAG 2.2 (texto normal exige 4,5:1; componentes, 3:1).

### Tema claro

| Token | Valor | Uso | Contraste |
|---|---|---|---|
| `bg` | `#F4F8F8` | Fundo da página | — |
| `surface` | `#FFFFFF` | Cards, folhas, diálogos | — |
| `text` | `#0B3B45` | Texto principal | 11,4:1 no `bg` |
| `text-muted` | `#3F6B73` | Texto secundário, rótulos | 5,5:1 no `bg` |
| `primary` | `#0E5A66` | Botões, links, foco | 7,9:1 com texto branco |
| `positive` | `#1F7A4D` | Receitas, metas atingidas | 5,0:1 no `bg` · 5,3:1 no `surface` |
| `warning-text` / `warning-bg` | `#8A5A00` / `#FFF4DB` | Avisos gentis (80% do orçamento) | 5,4:1 |
| `negative` | `#B42318` | Passou do limite, erros | 6,2:1 no `bg` · 6,6:1 no `surface` |
| `track` | `#D6E4E6` | Trilho de barras de progresso | decorativo; o preenchimento e o texto carregam a informação |

### Tema escuro

| Token | Valor | Uso | Contraste |
|---|---|---|---|
| `bg` | `#0B1F24` | Fundo da página | — |
| `surface` | `#12303A` | Cards, folhas, diálogos | — |
| `text` | `#E6F0F1` | Texto principal | 14,6:1 no `bg` · 12,0:1 no `surface` |
| `text-muted` | `#A7C1C5` | Texto secundário | 9,0:1 no `bg` · 7,3:1 no `surface` |
| `primary` | `#5FB3BF` | Botões (texto `#0B1F24`), links | 7,0:1 |
| `positive` | `#4CC38A` | Receitas | 7,7:1 no `bg` · 6,3:1 no `surface` |
| `warning` | `#F2B84B` | Avisos gentis | 9,5:1 no `bg` · 7,8:1 no `surface` |
| `negative` | `#F47F72` | Passou do limite, erros | 6,6:1 no `bg` · 5,4:1 no `surface` |

O tema segue o sistema (`prefers-color-scheme`) e pode ser fixado em Ajustes.

**Gráficos:** paleta categórica derivada do `primary`, com até 6 cores e o restante agrupado em
"Outras". Sempre com rótulo direto ou legenda, e com tabela equivalente para leitores de tela.

## 3. Tipografia

- **Fonte:** Inter, auto-hospedada (licença OFL), sem chamadas a serviços de terceiros.
- **Números:** `font-variant-numeric: tabular-nums` em todo valor, para alinhar colunas.
- **Escala:** 12 · 14 · 16 (base) · 20 · 24 · 32 px. Altura de linha 1,5 no texto e 1,2 nos
  títulos.
- **Valores em destaque** (saldo do painel): 32 px, peso 600.

## 4. Espaço, forma e movimento

- **Espaçamento:** base de 4 px (4, 8, 12, 16, 24, 32, 48).
- **Raios:** 8 px (campos, chips), 12 px (cards), 16 px (folhas e diálogos).
- **Alvos de toque:** mínimo de 44 × 44 px.
- **Movimento:** 150–250 ms, `ease-out`. Com `prefers-reduced-motion`, as transições viram troca
  instantânea e as celebrações viram um ícone estático.
- **Layout:** mobile primeiro. Barra de navegação inferior até 768 px; barra lateral acima disso.
  Botão "+ Lançar" fixo no centro da barra inferior.

## 5. Componentes

| Componente | Comportamento |
|---|---|
| `Button` | Variantes primária, secundária, discreta e perigosa. Estado de carregando sem mudar de largura. |
| `MoneyInput` | Teclado numérico (`inputmode="decimal"`); dígitos entram pela direita (1, 2, 3, 4 → R$ 12,34); nunca usa ponto flutuante. |
| `AmountText` | Formata com `Intl.NumberFormat('pt-BR', {style: 'currency'})`. Receita: `+` e cor `positive`; gasto: `−` e cor do texto; nunca só cor. |
| `CategoryChip` | Ícone + nome; seleção por toque; acessível como grupo de rádio. |
| `Card` | Superfície com título opcional e ação no canto. |
| `ProgressBar` | Preenchimento + texto ("R$ 960 de R$ 1.200 · 80%"); cores mudam a 80% e acima de 100%. |
| `Toast` | Mensagem curta com ação opcional ("Desfazer"), 5 s, anunciada por `aria-live`. |
| `OfflineBadge` | Selos "Aguardando sinal" (neutro, ícone de nuvem) e "Precisa de atenção" (âmbar, ícone de alerta). |
| `EmptyState` | Ilustração simples, uma frase e uma ação. |
| `Sheet` / `Dialog` | Folha inferior no mobile e diálogo no desktop; foco preso e `Esc` fecha (Radix). |
| `Tabs`, `Nav` | Navegação principal: Início, Lançamentos, + Lançar, Planejar, Mais. |
| `SourceStamp` | "Fonte: Banco Central · atualizado em 31/10/2026"; vira "desatualizado" após 3 dias úteis. |
| `Disclaimer` | Faixa fixa: "Conteúdo educativo. Não é recomendação de investimento." |
| `ModuleToggle` | Linha com nome, descrição de uma linha e chave liga/desliga. |

Base: primitivas Radix + Tailwind, no estilo shadcn/ui (o código dos componentes fica no repositório).

## 6. Voz e tom

- Fala com **"você"**, frases curtas, verbos no presente.
- Explica o conceito antes do termo técnico: "Reserva de emergência: dinheiro guardado para
  imprevistos, como 6 meses dos seus gastos."
- **Sem culpa:** "Passou R$ 60,00 do previsto em Mercado", nunca "Você estourou o orçamento!".
- **Erros dizem o que fazer:** "Sem conexão. Seu gasto está salvo e vai ser enviado quando o sinal
  voltar."
- Celebra o hábito, não o gasto: "4 semanas registrando! Sua família está no controle."

## 7. Gamificação ética

| Pode | Não pode |
|---|---|
| Celebrar hábitos (registrar, poupar, atingir metas) | Premiar gastar, se endividar ou arriscar |
| Sequência semanal com congelamento automático | Sequência diária ou "você perdeu" |
| Conquistas privadas da família | Ranking entre membros ou com outras famílias no MVP |
| Notificação útil e rara, desligável | Notificação entre 21h e 8h, urgência falsa, contagem regressiva |
| Linguagem de apoio | Culpa, vergonha ou comparação |

## 8. Acessibilidade

- WCAG 2.2 AA: contraste, foco visível (anel `primary` de 2 px), navegação completa por teclado,
  rótulos em todos os campos e alvos de 44 px.
- Gráficos com tabela alternativa e descrição curta.
- Valores lidos corretamente por leitores de tela ("menos 1.234 reais e 56 centavos").
- Teste automático com axe nos testes E2E e revisão manual com leitor de tela a cada release minor.
