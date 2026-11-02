# Como contribuir

Obrigado por querer ajudar. Estas são as regras do projeto.

## Branches

Use o formato `tipo/descrição-curta`, com um destes tipos:

- `feat/…` para funcionalidades novas;
- `fix/…` para correções;
- `chore/…` para manutenção e ferramentas;
- `docs/…` para documentação.

## Commits e pull requests

- Seguimos o [Conventional Commits](https://www.conventionalcommits.org/pt-br/).
- O PR é integrado com squash: o título do PR vira a mensagem do commit e é validado pelo
  commitlint. Escreva o título já no formato certo.

## Testes

Todo PR traz testes e mantém a cobertura: no mínimo 90% no domínio e 80% no geral.

## Idiomas

- Documentação de usuário e de produto em português do Brasil.
- Código, commits e nomes técnicos em inglês.

## Dados pessoais e segredos

Nunca commite dados pessoais nem segredos (senhas, chaves, tokens, arquivos `.env`). Exemplos e
testes usam nomes e valores fictícios.

## Decisões de arquitetura

Toda decisão nova vira um ADR em `docs/adr/`, com as alternativas consideradas e os custos.
