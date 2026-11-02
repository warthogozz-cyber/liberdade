# ADR-0012: Acesso a dados com JdbcClient

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

O [ADR-0008](0008-workspace-isolation.md) isola as famílias com chaves estrangeiras compostas
(`workspace_id`, `id`). Um `findById(id)` do Spring Data, sem `workspace_id`, é o caminho mais
fácil e vaza dados entre famílias.

## Decisão

- Acesso a dados com `JdbcClient` e SQL explícito, em um repositório por módulo.
- Todo método que lê ou grava dado de família recebe o id da família.
- Sem JPA e sem repositórios Spring Data.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Spring Data JPA | `findById` sem família e carregamento preguiçoso escondem consultas |
| Spring Data JDBC | Mesmo problema do `findById` |
| jOOQ | Geração de código e mais uma ferramenta no build; ganho pequeno para cerca de 15 tabelas |

## Consequências

- Mais SQL escrito à mão.
- Cada repositório tem teste com Testcontainers, incluindo um caso de "outra família não vê".
