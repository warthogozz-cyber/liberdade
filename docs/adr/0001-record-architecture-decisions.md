# ADR-0001: Registrar decisões em ADRs

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

O projeto vai durar anos, terá muitas fases e decisões que dependem de preço, versão e lei, coisas
que mudam. Sem registro, o motivo de cada escolha se perde e a decisão é refeita sem necessidade.

## Decisão

Registrar toda decisão de arquitetura ou de fornecedor em `docs/adr/NNNN-titulo-em-ingles.md`, em
português, com estas seções:

- **Status:** Proposto, Aceito, Substituído por ADR-XXXX ou Descontinuado.
- **Contexto:** o problema e as restrições.
- **Decisão:** o que foi escolhido.
- **Alternativas consideradas:** com o motivo de cada recusa.
- **Consequências:** o que melhora, o que piora e o que fica para depois.
- **Custos:** em BRL por mês, com fonte e data, quando houver.

Um ADR aceito não é reescrito; mudanças viram um novo ADR que substitui o anterior.

## Consequências

- Revisões futuras (CVM, LGPD, custos) têm um ponto de partida com data e fonte.
- Pequeno custo de escrita em cada decisão relevante.
