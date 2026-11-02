# ADR-0003: Monólito modular com Spring Modulith

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

- O produto tem um núcleo (controle financeiro) e muitos módulos opcionais que a família liga e
  desliga.
- Requisito S2 do PRD: ocultar qualquer módulo além do núcleo sem quebrar nada.
- Uma pessoa mantém o projeto, com orçamento de infraestrutura apertado.

## Decisão

- Uma única aplicação Spring Boot dividida em módulos por pacote: `identity`, `workspace`, `ledger`
  e `audit` (núcleo); `planning`, `indicators`, `gamification` e `ventures` (opcionais).
- **Spring Modulith** verifica em teste que nenhum módulo acessa a parte interna de outro e que não
  há ciclos.
- O núcleo nunca depende de módulo opcional. Módulos opcionais usam a API pública do núcleo ou
  ouvem seus eventos de domínio.
- Eventos em memória com o *event publication registry* do Spring Modulith, persistido no
  PostgreSQL (outbox), para reprocessar ouvintes que falharem.
- **Sem Kafka** (YAGNI): o volume de uma família não justifica um broker.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Microsserviços | Vários deploys, bancos e custos para uma pessoa e uma família; complexidade sem benefício |
| Monólito sem fronteiras | Rápido no começo, mas o critério S2 vira disciplina manual e quebra com o tempo |
| Kafka ou SQS para eventos | Infraestrutura extra (≈ dezenas a centenas de reais por mês) sem necessidade |

## Consequências

- Um deploy, um banco, transações locais simples.
- Fronteiras verificadas automaticamente; um módulo pode virar serviço separado no futuro se houver
  motivo real.
- Os módulos dividem o mesmo processo: um vazamento de memória em um afeta todos.
