# ADR-0013: Testes de propriedade sem jqwik

- **Status:** Aceito
- **Data:** 2026-11-02
- **Substitui em parte:** [ADR-0002](0002-stack-react-pwa-spring-boot.md),
  [ADR-0006](0006-money-representation.md) e [ADR-0009](0009-ci-cd-release-please-ghcr-ssm.md)
  (só as linhas sobre jqwik)

## Contexto

- Os ADRs [0002](0002-stack-react-pwa-spring-boot.md), [0006](0006-money-representation.md) e
  [0009](0009-ci-cd-release-please-ghcr-ssm.md) previam testes de propriedade com jqwik.
- As notas da versão 1.10.1 do jqwik (jqwik.net, consultadas em 2026-11-02) dizem que ela é
  provavelmente a última sobre o JUnit Platform 1.x. Versões futuras, "se um dia saírem",
  exigirão JUnit Platform 6 e Java ≥ 21.
- O Spring Boot 4.1 já usa JUnit 6.
- A licença do projeto passou a ter uma cláusula restringindo o uso por ferramentas de IA, o que
  conflita com o fluxo de trabalho deste repositório.

## Decisão

Testes de propriedade com `@ParameterizedTest` e `@MethodSource` do JUnit 6.

- A semente é fixa (`20261102L`), com 1000 casos por propriedade.
- Quando um caso falha, o nome do teste mostra as entradas.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| jqwik 1.10.1 | Feito para JUnit Platform 1.x, com futuro incerto, e tem a cláusula de licença |
| Outra biblioteca de property testing | Pesquisar só quando precisarmos de *shrinking*; hoje o custo não se paga |

## Consequências

- Não há *shrinking* (redução automática do caso que falha).
- As propriedades continuam documentadas como testes.
