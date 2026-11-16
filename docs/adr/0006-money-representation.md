# ADR-0006: Representação de dinheiro

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

Ponto flutuante não representa centavos com exatidão (`0.1 + 0.2 ≠ 0.3`). Em um app financeiro,
um centavo perdido destrói a confiança. O app vai ter multimoeda na fase 4.

## Decisão

| Camada | Representação |
|---|---|
| Domínio (Java) | Value object `Money(BigDecimal amount, Currency currency)`; operações só entre moedas iguais (senão, exceção) |
| Banco | `NUMERIC(19,4)` + `currency char(3)` |
| API (JSON) | Texto decimal: `{"amount": "1234.56", "currency": "BRL"}` |
| Front | `bigint` em centavos; formatação com `Intl.NumberFormat('pt-BR')` |

Regras de arredondamento:

- Cálculos internos sem arredondar; **arredonda só na exibição**, com `HALF_EVEN`.
- **Rateio** pelo método do maior resto: a soma das partes é sempre igual ao total.
- **Aporte sugerido** de meta: arredonda para cima (`CEILING`) ao centavo, para atingir a meta.
- **Meses de reserva:** arredonda para baixo, com uma casa, para nunca superestimar.
- **Ponto de equilíbrio** em unidades: arredonda para cima.
- **Uso do orçamento** em %: arredonda para baixo, em inteiro, para não mostrar 100% antes de
  atingir o limite.
- Percentuais com até 6 casas internas, exibidos com 0 a 2 casas.

Testes: casos manuais do PRD (seção 7) + testes de propriedade em JUnit, como define o
[ADR-0013](0013-property-tests-without-jqwik.md) (ex.: "a soma do rateio é igual ao total",
"transferência não muda o total da família").

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| `double`/`number` | Erro de arredondamento |
| Centavos como `long` no backend | Funciona para BRL, mas complica moedas com 0 ou 3 casas e cálculos com taxa |
| JavaMoney (JSR 354) | API extensa para a necessidade; um value object próprio pequeno é mais simples de testar |

## Consequências

- Exatidão garantida e testada; JSON legível.
- O front nunca faz conta com valores grandes em `number`; sempre `bigint`.
