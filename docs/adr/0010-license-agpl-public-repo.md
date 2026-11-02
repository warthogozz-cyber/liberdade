# ADR-0010: Repositório público com AGPL-3.0

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

- Em repositório privado no plano grátis do GitHub, proteção de branch, CodeQL e secret scanning
  exigem planos pagos.
- O mantenedor quer manter a opção de oferecer o app como serviço no futuro.

## Decisão

- Repositório **público** no GitHub, licença **AGPL-3.0**.
- Nenhum dado pessoal no repositório: arquivos de trabalho locais com informações pessoais ficam em
  `.git/info/exclude`; exemplos usam nomes e valores fictícios.
- Segredos só no SSM Parameter Store e nos secrets do GitHub; nunca em arquivos.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Privado | Recursos de segurança pagos (≈ US$ 4/mês ≈ R$ 22 no plano Team, mais add-ons) |
| MIT ou Apache-2.0 | Permitiria que alguém rodasse uma cópia fechada como serviço concorrente |

## Consequências

- CodeQL, secret scanning, rulesets e Actions grátis.
- Quem oferecer uma cópia modificada como serviço precisa publicar o código dela.
- Dependências precisam ser compatíveis com a AGPL-3.0 (MIT, Apache-2.0, BSD, GPLv3 são); o
  Dependabot e a revisão de PR checam licenças novas.
- O código fica visível para qualquer pessoa: segurança não pode depender de segredo no código.
