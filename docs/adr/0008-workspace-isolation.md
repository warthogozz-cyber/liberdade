# ADR-0008: Isolamento entre famílias

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

O MVP serve uma família, mas a arquitetura já nasce para várias. Vazar dado de uma família para
outra seria a pior falha possível do produto.

## Decisão

Isolamento em camadas, todas obrigatórias:

1. **Rota:** tudo que é da família fica sob `/v1/workspaces/{ws}`. Uma guarda única verifica se o
   usuário é membro ativo de `{ws}` e se o papel permite a ação; nega por padrão.
2. **Repositório:** toda consulta recebe o `workspaceId` como parâmetro obrigatório; não existe
   método "buscar por id" sem família.
3. **Banco:** `workspace_id` em toda tabela da família; FKs compostas (`workspace_id`, `id`), para
   que um lançamento nunca aponte para a conta de outra família.
4. **Testes:** suíte de isolamento cria duas famílias e confere, endpoint por endpoint, que uma não
   lê nem altera dados da outra (inclusive adivinhando ids).

Row-Level Security (RLS) do PostgreSQL fica para ser reavaliado antes de abrir para outras famílias
(fase 6), em novo ADR.

## Alternativas consideradas

| Alternativa | Por que não agora |
|---|---|
| RLS desde o início | Defesa forte, mas complica pool de conexões, migrações e testes; as camadas acima bastam para uma família |
| Um schema ou banco por família | Isolamento máximo, mas migrações e custo multiplicados |

## Consequências

- Um pouco mais de verbosidade nas consultas (sempre com `workspace_id`).
- Os testes de isolamento rodam em todo PR.
