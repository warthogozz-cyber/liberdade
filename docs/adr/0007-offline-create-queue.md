# ADR-0007: Fila offline só para criar lançamentos

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

O gasto acontece no mercado, na feira, no ônibus, muitas vezes sem sinal. Se o app falhar nessa
hora, o hábito de registrar morre. Por outro lado, sincronização completa nos dois sentidos exige
resolver conflitos entre membros da família.

## Decisão

- **Só criar lançamento funciona offline.** Leitura usa o último cache, com a data do dado.
  Editar e excluir exigem conexão.
- O aparelho gera o **id (UUIDv7)**, grava na fila do IndexedDB (Dexie) e mostra o selo "Aguardando
  sinal".
- O sincronizador envia em ordem, com `Idempotency-Key` igual ao id, quando a conexão volta ou o
  app ganha foco (o iOS não tem Background Sync).
- O servidor faz **insert-or-return**: `201` para novo, `200` para reenvio; nunca duplica.
- Recusa do servidor vira "Precisa de atenção" com ação de corrigir; **nada é descartado em
  silêncio**.
- A fila sobrevive a fechar o app e a reiniciar o aparelho.

Fluxo detalhado: [arquitetura, seção 7.2](../architecture.md#72-lançar-sem-internet).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Somente online | Mais simples, mas falha exatamente onde o gasto acontece |
| Offline-first completo (sync nos dois sentidos) | Resolver conflitos entre membros custaria +3 a 4 semanas no MVP |

## Consequências

- Quase sem conflitos, porque offline só se cria registro.
- Saldos mostrados offline são estimativas (cache + itens na fila), sinalizadas na tela.
- Se um dia houver edição offline, um novo ADR define a resolução de conflitos.
