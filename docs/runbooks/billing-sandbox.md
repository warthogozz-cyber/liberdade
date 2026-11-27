# Runbook: cobrança no sandbox do Asaas

Este runbook liga a pré-venda da Família Fundadora no **staging**, com o sandbox do Asaas, e
confere a compra, o reembolso, a expiração, o alerta e o Retrato do mês. Quem executa é o
mantenedor. A produção não é tocada: ela só liga a cobrança depois da seção "Antes da produção".

O desenho está no
[spec do Plano 6](../superpowers/specs/2026-11-17-liberdade-plan-6-billing-design.md) e no
[ADR-0015](../adr/0015-revenue-model-and-billing.md). O código fica em
`api/src/main/java/br/com/liberdadefamilia/billing/`.

Antes de começar, o staging tem de estar de pé, como no [go-live](go-live.md) (passos 1 a 14), e o
`tofu apply` de `infra/live` tem de ter rodado ao menos uma vez.

Regras para todos os passos:

- Nunca cole a chave de API nem o token do webhook em chat, issue, PR ou log. Eles só ficam no SSM
  e no painel do Asaas.
- Valores com aspas simples (`'`) ou quebra de linha (CR ou LF) **quebram o `deploy.sh`**, que
  recusa o deploy. Gere e cole os valores sem esses caracteres.
- Sem a chave de API, ou com o token fora de 32 a 255 caracteres, a API sobe normalmente, mas a
  venda fica desligada (`configured` falso) e o webhook responde `401`.
- Com `LIBERDADE_BILLING_ENABLED=false` o webhook também responde `401`, mesmo com pagamentos em
  andamento. Para só parar de vender, prefira `LIBERDADE_BILLING_PRESALE_OPEN=false`. Se desligar a
  cobrança, um pagamento feito nesse meio-tempo só ativa o plano quando o Asaas reenviar o evento
  com a cobrança ligada de novo, ou quando a rotina de repetição (a cada hora) achar o pagamento,
  o que ela faz por até 48 h depois do checkout.

## Passos

### 1. Conta e chave

1. Crie a conta de **sandbox** do Asaas (não a de produção).
2. Em Integrações, gere a chave de API do sandbox.
3. Guarde a chave num gerenciador de senhas até o passo 3.

Conferir: o painel do sandbox mostra a chave criada.

### 2. Token do webhook

Gere o token num terminal local:

```bash
openssl rand -hex 32
```

Resultado esperado: 64 caracteres hexadecimais, dentro do limite de 32 a 255 que a API exige.
Guarde o token no gerenciador de senhas, junto com a chave, até o passo 4. Não cole o token em
nenhum outro lugar além do SSM (passo 3) e do painel do Asaas (passo 4).

### 3. Parâmetros no SSM

São seis parâmetros em `/liberdade/staging/`: quatro `String` e dois `SecureString`
(`--key-id alias/liberdade`).

| Parâmetro                              | Tipo         | Valor no staging                      |
| -------------------------------------- | ------------ | ------------------------------------- |
| `LIBERDADE_BILLING_ENABLED`            | String       | `true`                                |
| `LIBERDADE_BILLING_PRESALE_OPEN`       | String       | `true`                                |
| `LIBERDADE_BILLING_ASAAS_BASE_URL`     | String       | `https://api-sandbox.asaas.com`       |
| `LIBERDADE_BILLING_CHECKOUT_HOSTS`     | String       | `sandbox.asaas.com`                   |
| `ASAAS_API_KEY`                        | SecureString | a chave do passo 1                    |
| `ASAAS_WEBHOOK_TOKEN`                  | SecureString | o token do passo 2                    |

```bash
export MSYS_NO_PATHCONV=1
aws ssm put-parameter --name /liberdade/staging/LIBERDADE_BILLING_ENABLED \
  --type String --value true
aws ssm put-parameter --name /liberdade/staging/LIBERDADE_BILLING_PRESALE_OPEN \
  --type String --value true
aws ssm put-parameter --name /liberdade/staging/LIBERDADE_BILLING_ASAAS_BASE_URL \
  --type String --value https://api-sandbox.asaas.com
aws ssm put-parameter --name /liberdade/staging/LIBERDADE_BILLING_CHECKOUT_HOSTS \
  --type String --value sandbox.asaas.com
read -rs -p "Chave de API do Asaas (não aparece ao digitar): " ASAAS_KEY; echo
aws ssm put-parameter --name /liberdade/staging/ASAAS_API_KEY \
  --type SecureString --key-id alias/liberdade --value "$ASAAS_KEY"
unset ASAAS_KEY
read -rs -p "Token do webhook (não aparece ao digitar): " ASAAS_TOKEN; echo
aws ssm put-parameter --name /liberdade/staging/ASAAS_WEBHOOK_TOKEN \
  --type SecureString --key-id alias/liberdade --value "$ASAAS_TOKEN"
unset ASAAS_TOKEN
```

Para corrigir um valor, repita o comando com `--overwrite`.

Conferir (só nomes e tipos, sem valores):

```bash
export MSYS_NO_PATHCONV=1
aws ssm get-parameters-by-path --path /liberdade/staging/ \
  --query 'Parameters[].[Name,Type]' --output text
```

Resultado esperado: 14 parâmetros no staging (os 8 do go-live mais estes 6), com `SecureString`
em `ASAAS_API_KEY` e `ASAAS_WEBHOOK_TOKEN`.

### 4. Webhook no Asaas

No painel do sandbox, em Integrações, Webhooks, crie um webhook com:

- **URL:** `https://api-staging.liberdadefamilia.com.br/v1/billing/webhooks/asaas` (o mesmo
  endereço do `API_URL` do staging, mais o caminho).
- **Token de autenticação:** o token do passo 2. O Asaas o envia no cabeçalho
  `asaas-access-token`.
- **Eventos**, exatamente estes seis: `CHECKOUT_PAID`, `CHECKOUT_EXPIRED`, `CHECKOUT_CANCELED`,
  `PAYMENT_REFUNDED`, `PAYMENT_CHARGEBACK_REQUESTED` e `PAYMENT_CHARGEBACK_DISPUTE`.
- Fila de envio ativa.

Resultado esperado: o webhook aparece como ativo. Outros tipos de evento que chegarem são
ignorados pela API (resultado `IGNORED`), sem erro.

### 5. Alarme

No diretório `infra/live`, rode o `tofu apply`. Ele cria, para `staging` e `production`, um filtro
de métrica e o alarme `liberdade-billing-alert-staging` e `liberdade-billing-alert-production`
(arquivo `infra/live/billing_alerts.tf`).

Resultado esperado: o plano mostra só 2 filtros de métrica e 2 alarmes novos. Se a assinatura do
e-mail de alertas ainda não estiver confirmada (passo 5 do go-live), confirme agora pelo link
que chegou no e-mail.

Custo: com os 4 alarmes que já existiam, ficam 6 dos 10 alarmes do nível gratuito do CloudWatch.

### 6. Deploy e conferências

1. Faça o deploy do staging (workflow `deploy.yml`, como no go-live).
2. Abra `https://staging.liberdadefamilia.com.br/termos`.
3. Confira que a página mostra a versão `terms-2026-12`, com nome, documento e endereço de quem
   vende (veja "Antes da produção").
4. Entre com uma conta de teste.

Resultado esperado: o aviso "Antes de começar" aparece com o aceite novo dos termos e da
política, porque com a cobrança ligada as versões em vigor são `terms-2026-12` e
`privacy-2026-12`. Sem cobrança ligada, valem as `2026-11`.

### 7. Compra por Pix

1. Com uma família de teste, abra o plano e escolha a Família Fundadora.
2. Na página do Asaas, pague com o Pix de sandbox (no painel do sandbox, simule o pagamento).
3. Volte ao app.

Resultado esperado: a família vira Fundadora, com o pedido em `PAID`.

Conferir no banco que o pagamento foi ligado ao pedido (risco 1 do spec):

```sql
select status, asaas_payment_id is not null as tem_pagamento
from billing_orders order by created_at desc limit 1;
```

`tem_pagamento` deve ser `true`. A API pergunta ao Asaas qual cobrança nasceu do checkout depois
de confirmar o pagamento, e a rotina de repetição (a cada hora) tenta de novo. A mesma rotina
confirma, por até 48 h depois do checkout, um pagamento cujo webhook não chegou. Se continuar
nulo depois de 1 h, abra uma issue com o tipo de evento que chegou (sem ids nem valores).

### 8. Compra por cartão

Use **outra** família de teste (a primeira já tem plano). Pague com um cartão de teste da
documentação do Asaas.

Resultado esperado: o pedido vai a `PAID` e a família vira Fundadora, como no Pix.

### 9. Reembolso

Na família do passo 7 (dentro dos 7 dias), peça o reembolso.

Resultado esperado: ao pedir, o plano da família acaba na hora e ela volta ao Grátis; o app mostra
"Reembolso em processamento" e o pedido fica `REFUND_PENDING`. Quando o Asaas confirma e manda
`PAYMENT_REFUNDED` (ou a rotina horária descobre), o pedido vira `REFUNDED`. Se passar de 72 h em
`REFUND_PENDING`, a API emite o alerta `REFUND_STUCK` (veja o passo 11).

Para contraste: no perfil `e2e`, com o gateway falso, o reembolso para em `REFUND_PENDING` sem
chamar ninguém. Isso é só de teste e não acontece no sandbox.

### 10. Expiração

Use a família do passo 7, que voltou ao Grátis no passo 9.
Comece uma compra e **não pague**. Espere o checkout expirar (60 min).

Resultado esperado: o Asaas manda `CHECKOUT_EXPIRED` e o pedido vira `EXPIRED`.

### 11. Alerta

A API escreve uma linha `BILLING_ALERT type=<TIPO>` no nível WARN, sem valores nem ids. Os tipos
são `CHARGEBACK`, `REFUND_STUCK` e `WEBHOOK_ERROR`. Em produção, o log é o JSON ECS enviado pelo
driver `awslogs` ao grupo `/liberdade/<ambiente>`.

O alarme `liberdade-billing-alert-<ambiente>` soma as linhas a cada 300 s. Com uma ou mais, ele
vai a `ALARM` e manda e-mail ao tópico de alertas; quando volta a zero, manda outro e-mail com o
`OK`. Sem linhas, ele fica `OK` (dados ausentes não contam).

**Atenção:** o alarme manda **um e-mail por janela de alarme**. Um segundo alerta, de qualquer
tipo, enquanto o alarme já está em `ALARM`, não manda e-mail novo. Por isso, ao receber o e-mail,
olhe os logs de **todos** os tipos naquela janela:

```bash
export MSYS_NO_PATHCONV=1
aws logs filter-log-events --log-group-name /liberdade/staging \
  --filter-pattern '"BILLING_ALERT"' --start-time <epoch em milissegundos de 1 h atrás>
```

Para disparar um alerta de teste no staging, o jeito seguro é o `CHARGEBACK`. O `WEBHOOK_ERROR`
só sai quando uma regra falha dentro de um evento de verdade, e um tipo de evento inventado é
só ignorado, então não há como forçá-lo de fora. Faça assim, com o pedido pago do passo 8 (a
família do cartão).

Isto só vale no **sandbox**, com uma família de teste. O pedido fica em `CHARGEBACK` para sempre
e a família perde o plano (ela pode comprar de novo). **Nunca faça isso na produção**: acabaria
com o plano de uma cliente de verdade.

1. Leia o `asaas_payment_id` do pedido do passo 8 (só leitura):

   ```sql
   select asaas_payment_id from billing_orders
   where status = 'PAID' order by created_at desc limit 1;
   ```

   Se vier nulo, o evento não acharia o pedido: o webhook responderia `200` com o resultado
   `UNKNOWN_ORDER` e **nenhum alerta** sairia. Espere a rotina horária e leia de novo; se
   continuar nulo depois de 1 h, abra uma issue, como no passo 7.
2. Envie ao staging um evento de estorno à mão. O token vem do SSM para uma variável do shell,
   sem aparecer na tela nem no histórico:

```bash
export MSYS_NO_PATHCONV=1
ASAAS_TOKEN="$(aws ssm get-parameter --name /liberdade/staging/ASAAS_WEBHOOK_TOKEN   --with-decryption --query Parameter.Value --output text)"
curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  https://api-staging.liberdadefamilia.com.br/v1/billing/webhooks/asaas \
  -H 'Content-Type: application/json' \
  -H "asaas-access-token: $ASAAS_TOKEN" \
  -d '{"id":"evt_teste_alerta_1","event":"PAYMENT_CHARGEBACK_REQUESTED","payment":{"id":"<asaas_payment_id>"}}'
unset ASAAS_TOKEN
```

Resultado esperado: o `curl` imprime `200`; o pedido vira `CHARGEBACK` e a família perde o plano;
em até uns 5 a 10 minutos chega o e-mail do alarme `liberdade-billing-alert-staging` em `ALARM`,
e depois o do `OK`. Use um `id` de evento novo a cada tentativa: o mesmo `id` repetido não faz
nada. Se o token ou o cabeçalho estiver errado, a resposta é `401`. Se o `curl` der `200` mas o
pedido continuar `PAID` e não chegar e-mail, o `asaas_payment_id` estava errado (o evento foi
guardado como `UNKNOWN_ORDER`).

### 12. Retrato

No Android e no iOS (PWA instalado), abra o Retrato do mês e gere o cartão.

Resultado esperado: o cartão aparece sem erro, compartilhar abre a folha do sistema (ou baixa a
imagem, onde não há compartilhamento) e baixar grava o arquivo. A imagem é desenhada no
aparelho, sem chamada de rede. Anote os resultados do iOS, que ainda não foram medidos.

### 13. Registro

Abra o PR de docs e registre nele:

- o resultado de cada passo acima;
- as taxas que o sandbox cobrou (Pix e cartão), sem ids;
- os nomes reais dos eventos de estorno e de chargeback que o Asaas mandou, para trocar os
  nomes assumidos no código e neste runbook, se forem diferentes.

## Antes da produção

Checklist do mantenedor, da seção 9 do spec, mais as decisões do Plano 6. Só depois de todos os
itens, ligue a cobrança na produção.

- [ ] O contador confirmou CNPJ (MEI ou ME), CNAE, anexo do Simples Nacional, inscrição municipal e
      a emissão de NFS-e pelo Asaas.
- [ ] Conta PJ no Asaas aprovada; webhook de produção criado com token **próprio** (não reuse o do
      sandbox).
- [ ] Tela de consentimento do Google publicada (escopos não sensíveis), para qualquer família
      entrar.
- [ ] Revisão de LGPD feita e pesquisa de marca no INPI concluída (ADR-0015).
- [ ] Identificação de quem vende preenchida (`PRIVACY_CONTROLLER`,
      `PRIVACY_CONTROLLER_DOCUMENT`, `PRIVACY_CONTROLLER_ADDRESS`) no Environment `production`.
      Os termos 2026-12 mostram o nome, o documento e o endereço de quem vende (Decreto
      7.962/2013, art. 2º). Confirme que `PRIVACY_CONTROLLER` é o nome **legal** de quem vende.
      Veja a tabela de variáveis no [go-live](go-live.md).
- [ ] O mantenedor leu os textos 2026-12 dos termos e da política de privacidade (de preferência
      com apoio jurídico) antes de ligar a cobrança na produção.
- [ ] Nova versão dos termos 2026-12 publicada no mesmo dia: o texto atual diz "Receber meu
      dinheiro de volta", mas o botão do app diz "Pedir o dinheiro de volta"
      (`web/src/features/billing/messages.pt-BR.ts`). A nova versão usa o nome do botão. Como
      muda só o nome, ela pode entrar em `KEEPS_ACCEPTANCE`, sem pedir o aceite de novo.
- [ ] Só então, na produção: `ASAAS_API_KEY` e `ASAAS_WEBHOOK_TOKEN` de produção no SSM, mais
      `LIBERDADE_BILLING_ENABLED=true`, `LIBERDADE_BILLING_ASAAS_BASE_URL` e
      `LIBERDADE_BILLING_CHECKOUT_HOSTS` com os valores de produção do Asaas, e
      `LIBERDADE_BILLING_PRESALE_OPEN=true` quando quiser abrir as portas. Faça um deploy depois.
      Até lá, a produção continua com 8 parâmetros e a venda desligada.
- [ ] O alarme `liberdade-billing-alert-production` existe e o e-mail de alertas está confirmado.
