# Runbook: alertas dos indicadores

Este runbook diz o que fazer quando chega o e-mail do alarme dos indicadores do Banco Central, e
como forçar uma falha no **staging** para conferir que o aviso chega. Quem executa é o mantenedor.

O desenho está no
[plano 8.1](../superpowers/plans/2026-11-24-liberdade-plan-8-1-indicators.md). O código fica em
`api/src/main/java/br/com/liberdadefamilia/indicators/` e os alarmes em
`infra/live/indicator_alerts.tf`.

## Como a coleta funciona

A API busca as cinco séries (Selic meta, CDI, IPCA do mês, IPCA em 12 meses e dólar PTAX) no Banco
Central, às 8h30 e às 19h30 (horário de Brasília). O navegador nunca chama o Banco Central. Cada
série tenta de novo até 2 vezes (espera de 2 s e de 8 s) e, ao fim de cada coleta, a API escreve
uma linha `INDICATOR_RUN ok=<n> failed=<n>` no nível INFO. A página pública que descreve o robô é
`/robo`.

## O que o e-mail significa

O alarme `liberdade-indicator-alert-<ambiente>` soma as linhas `INDICATOR_ALERT` a cada 300 s. Com
uma ou mais, vai a `ALARM` e manda e-mail ao tópico de alertas; ao voltar a zero, manda o `OK`.
**Um e-mail por janela de alarme:** um segundo alerta enquanto o alarme já está em `ALARM` não
manda e-mail novo, então leia o log de todas as séries daquela janela.

A linha tem o formato `INDICATOR_ALERT type=<TIPO> series=<SÉRIE>` (nível WARN), nunca com valor:

| Tipo           | Quer dizer                                                                                                                  | O que fazer                                                                                                                                          |
| -------------- | --------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FETCH_FAILED` | A coleta da série falhou depois das tentativas (Banco Central fora, resposta vazia ou valor fora da faixa esperada).        | Espere a próxima coleta (veja abaixo). Se a mesma série falhar por 2 dias, abra o endereço da série no navegador e compare com o formato esperado.   |
| `STALE`        | A série está sem coleta bem-sucedida há mais de 3 dias úteis. Só é conferido na coleta da manhã.                            | Procure a causa nos `FETCH_FAILED` dos dias anteriores. Na tela, o cartão já mostra o selo "desatualizado" em texto, e segue com o último valor.     |

Falhas inesperadas aparecem no log só com o nome da classe da exceção, sem mensagem.

Alarme extra, só na produção: `liberdade-indicator-run-missing`. Ele dispara quando nenhuma linha
`INDICATOR_RUN` aparece por 24 horas, isto é, quando a API ou o agendador parou. Nesse caso, veja
primeiro se o contêiner está de pé, antes de olhar o Banco Central.

## Onde ler o log

A API manda o log JSON ao grupo `/liberdade/<ambiente>` (driver `awslogs`):

```bash
export MSYS_NO_PATHCONV=1
aws logs filter-log-events --log-group-name /liberdade/production \
  --filter-pattern '"INDICATOR_ALERT"' --start-time <epoch em milissegundos de 24 h atrás>
aws logs filter-log-events --log-group-name /liberdade/production \
  --filter-pattern '"INDICATOR_RUN"' --start-time <epoch em milissegundos de 24 h atrás>
```

Troque `production` por `staging` quando for o caso.

## Quando esperar

- O Banco Central às vezes fica fora por horas. Um `FETCH_FAILED` isolado não pede ação: a coleta
  seguinte (no máximo 13 h depois) tenta tudo de novo e o `OK` chega sozinho.
- `STALE` só vem depois de 3 dias úteis sem sucesso, então um fim de semana ou um feriado não
  dispara o aviso.
- Uma série sem nenhuma coleta (ambiente recém-criado) é preenchida na partida da API, sem esperar
  o horário.

## Forçar uma falha no staging

Isto confere que o e-mail chega. **Só no staging.** Faça depois do deploy do Plano 8.1 e do
`tofu apply` de `infra/live`.

### Jeito 1: o Banco Central inalcançável

1. Aponte a API do staging para um endereço que não responde, no SSM:

   ```bash
   export MSYS_NO_PATHCONV=1
   aws ssm put-parameter --name /liberdade/staging/LIBERDADE_INDICATORS_SGS_BASE_URL \
     --type String --value http://127.0.0.1:9 --overwrite
   ```

   O valor não pode ter aspas simples nem quebra de linha, ou o `deploy.sh` recusa o deploy.

2. Faça um deploy do staging para a API ler o parâmetro.
3. Espere a próxima coleta (8h30 ou 19h30). Esperado: `INDICATOR_ALERT type=FETCH_FAILED` para
   `SELIC_TARGET`, `CDI`, `IPCA_MONTH` e `IPCA_12M` (o dólar usa outro endereço) e, em alguns
   minutos, o e-mail do alarme.
4. **Desfaça:** apague o parâmetro e faça outro deploy.

   ```bash
   aws ssm delete-parameter --name /liberdade/staging/LIBERDADE_INDICATORS_SGS_BASE_URL
   ```

   Na coleta seguinte o alarme volta a `OK` e o segundo e-mail chega.

### Jeito 2: uma linha de log enviada à mão

Mais rápido, e não mexe na API. Escreve no grupo do staging uma linha igual à que a API escreve:

```bash
export MSYS_NO_PATHCONV=1
STREAM=manual-test-$(date +%s)
aws logs create-log-stream --log-group-name /liberdade/staging --log-stream-name "$STREAM"
aws logs put-log-events --log-group-name /liberdade/staging --log-stream-name "$STREAM" \
  --log-events timestamp=$(($(date +%s) * 1000)),message='INDICATOR_ALERT type=FETCH_FAILED series=CDI'
```

Esperado: em alguns minutos o alarme `liberdade-indicator-alert-staging` vai a `ALARM` e o e-mail
chega. Passada a janela de 300 s sem novas linhas, ele volta a `OK` e manda o segundo e-mail.

O alarme `liberdade-indicator-run-missing` não existe no staging, de propósito: o staging pode
ficar parado e daria alarme falso.
