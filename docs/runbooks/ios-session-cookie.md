# Cookie de sessão no iPhone (D17)

Roteiro para o mantenedor. Leva uns 15 minutos no primeiro dia e 2 minutos no dia seguinte.

## Objetivo

Confirmar a D17: o app instalado na Tela de Início do iPhone continua logado depois de fechado. O
cookie `__Host-sid` vale 30 dias sem uso, mas o iOS pode descartar os dados de um site instalado
antes disso. Se descartar, a pessoa teria de entrar de novo toda vez que abrisse o app.

## Antes de começar

- O preview do PR 2 precisa estar no ar, com HTTPS.
- Um iPhone com Safari. Anote o modelo do aparelho e a versão do iOS.
- Uma conta Google de teste, para entrar.

## Passos

1. Abra o ambiente de preview no Safari e entre com o Google.
2. Toque em Compartilhar e em "Adicionar à Tela de Início". Confirme o nome do app.
3. Feche o Safari e abra o app pelo ícone na Tela de Início. Confirme que ele abre logado.
4. Feche o app pelo seletor de apps (deslize o app para cima).
5. Espere 10 minutos, sem abrir o app.
6. Abra o app pelo ícone de novo.
7. Repita os passos 4 a 6 no dia seguinte, depois de uma noite sem abrir o app.

## O que olhar

- O app abre já logado, sem passar pela tela de entrada, nas duas vezes (10 minutos e dia seguinte).
- Em Ajustes > Safari > Avançado > Dados de Sites, o domínio do preview aparece com dados.

## Onde anotar

Na descrição do PR 2, em uma linha por medição, com:

- o modelo do aparelho e a versão do iOS;
- a data e a hora;
- o resultado de cada espera (10 minutos e dia seguinte): abriu logado ou pediu entrada.

Sem dado pessoal: nada de nome, e-mail, número de série ou identificador do aparelho.

## Se falhar

1. Abra uma issue com o resultado (modelo, versão do iOS, qual espera falhou e o que apareceu).
2. Não faça o merge do PR 2 até decidir o que fazer com o mantenedor.
