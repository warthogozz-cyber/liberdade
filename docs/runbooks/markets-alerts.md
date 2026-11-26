# Runbook: alertas da Feira (mercados)

Este runbook diz o que fazer quando chega o e-mail do alarme da importação dos mercados do
OpenStreetMap, e como forçar uma falha no **staging** para conferir que o aviso chega. Quem executa
é o mantenedor.

O código fica em `api/src/main/java/br/com/liberdadefamilia/markets/` e os alarmes em
`infra/live/markets_alerts.tf`. O arquivo é gerado toda semana pelo workflow
`.github/workflows/osm-markets.yml` e publicado como arquivos da release
`osm-markets-data` deste repositório.

## Como a importação funciona

1. O workflow semanal publica `markets-br.csv.gz` e `manifest.json` na release `osm-markets-data`.
2. Toda terça, às 4h40 (horário de Brasília), a API baixa o `manifest.json`. Se a data do
   OpenStreetMap for a mesma da última importação, só registra `MARKETS_IMPORT skipped`.
3. Se for nova, baixa o arquivo, confere o SHA-256 e só então, em **uma transação**, troca as linhas
   de `osm_markets`. Quem lê o mapa vê as linhas antigas até o commit.
4. Qualquer falha desfaz a transação, mantém as linhas antigas e escreve
   `MARKETS_ALERT type=IMPORT_FAILED reason=<MOTIVO>` (nível WARN). Sucesso escreve
   `MARKETS_IMPORT ok rows=<n>`.
5. Todo dia, às 9h10, a API confere se a última importação é mais velha que 15 dias e, se for,
   escreve `MARKETS_ALERT type=DATASET_STALE`.

Se a tabela estiver vazia quando a API sobe (ambiente novo), ela importa uma vez na partida, sem
esperar a terça. As linhas de log nunca trazem nome de mercado, coordenada ou endereço.

## O que o e-mail significa

O alarme `liberdade-markets-alert-<ambiente>` soma as linhas `MARKETS_ALERT` a cada 3600 s. Com uma
ou mais, vai a `ALARM` e manda e-mail ao tópico de alertas; ao voltar a zero, manda o `OK`. **Um
e-mail por janela de alarme:** um segundo alerta com o alarme já em `ALARM` não manda e-mail novo.

| Linha                                    | Quer dizer                                                                                  | O que fazer                                                                                                      |
| ---------------------------------------- | ------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `IMPORT_FAILED reason=HTTP`              | O GitHub não respondeu direito (erro de rede ou status diferente de 2xx).                   | Espere a próxima terça. Se persistir, confira se a release `osm-markets-data` existe e tem os dois arquivos.     |
| `IMPORT_FAILED reason=TIMEOUT`           | O download passou do tempo limite (60 s).                                                   | Igual ao HTTP. Se repetir por duas semanas, o arquivo pode estar grande demais para a rede da instância.         |
| `IMPORT_FAILED reason=TOO_LARGE`         | O arquivo passou de 50 MiB.                                                                 | Veja o tamanho do arquivo na release. Se o crescimento for real, suba `liberdade.markets.osm.max-bytes`.         |
| `IMPORT_FAILED reason=BAD_MANIFEST`      | O `manifest.json` está ilegível, ou a contagem de linhas dele não bate com o arquivo.       | Rode o workflow de novo (abaixo) e confira o `manifest.json` publicado.                                          |
| `IMPORT_FAILED reason=SHA_MISMATCH`      | O SHA-256 do arquivo baixado difere do `manifest.json`.                                     | Rode o workflow de novo. Pode ser uma publicação interrompida no meio.                                           |
| `IMPORT_FAILED reason=BAD_ROW`           | Uma linha do CSV não segue o formato (tipo, tipo de mercado, coordenada, repetição, etc.).  | O arquivo está errado: rode o workflow de novo e, se repetir, abra um chamado com o resultado do teste do script. |
| `IMPORT_FAILED reason=TOO_FEW_ROWS`      | O arquivo novo tem menos de 80% das linhas da importação anterior.                          | Provável falha na consulta ao OpenStreetMap. Rode o workflow de novo; só aceite a queda se ela for real.         |
| `DATASET_STALE`                          | A última importação tem mais de 15 dias (ou nunca houve uma e a API está no ar há mais de 1 dia). | Procure o último `IMPORT_FAILED`; se não houver, veja se o workflow semanal rodou e se o agendador da API roda.  |

Falhas inesperadas aparecem no log só com o nome da classe da exceção, sem mensagem, e podem não
gerar `MARKETS_ALERT`. Nesse caso o `DATASET_STALE` avisa depois.

## Rodar o workflow à mão

1. No GitHub, abra **Actions**, escolha o workflow `osm-markets` e use **Run workflow**.
2. Espere terminar e confira, na release `osm-markets-data`, os arquivos `markets-br.csv.gz` e
   `manifest.json`.
3. Para a API pegar o arquivo sem esperar a terça, reinicie a API (veja abaixo).

## Onde ler o log

```bash
export MSYS_NO_PATHCONV=1
aws logs filter-log-events --log-group-name /liberdade/production \
  --filter-pattern '"MARKETS_ALERT"' --start-time <epoch em milissegundos de 7 dias atrás>
aws logs filter-log-events --log-group-name /liberdade/production \
  --filter-pattern '"MARKETS_IMPORT"' --start-time <epoch em milissegundos de 7 dias atrás>
```

Troque `production` por `staging` quando for o caso.

## Forçar uma importação no staging

- **Esperar a terça**, às 4h40, ou
- **Esvaziar a tabela e reiniciar a API:** com `osm_markets` vazia, a API importa na partida. Apague
  as linhas do staging (`DELETE FROM osm_markets`) e faça um deploy ou reinicie o contêiner.
  Esperado: `MARKETS_IMPORT ok rows=<n>` no log.

## Forçar um alerta no staging

Isto confere que o e-mail chega. **Só no staging.**

1. Aponte a API do staging para um endereço que não responde, no SSM:

   ```bash
   export MSYS_NO_PATHCONV=1
   aws ssm put-parameter --name /liberdade/staging/LIBERDADE_MARKETS_OSM_DATASET_URL \
     --type String --value http://127.0.0.1:9 --overwrite
   ```

   O valor não pode ter aspas simples nem quebra de linha, ou o `deploy.sh` recusa o deploy.

2. Faça um deploy do staging e force uma importação (esvazie a tabela e reinicie, como acima).
   Esperado: `MARKETS_ALERT type=IMPORT_FAILED reason=HTTP` e, em alguns minutos, o e-mail.
3. **Desfaça:** apague o parâmetro e faça outro deploy.

   ```bash
   aws ssm delete-parameter --name /liberdade/staging/LIBERDADE_MARKETS_OSM_DATASET_URL
   ```

   Passada a janela de 3600 s sem novas linhas, o alarme volta a `OK` e o segundo e-mail chega.

Mais rápido, sem mexer na API: envie à mão uma linha igual à da API ao grupo do staging.

```bash
export MSYS_NO_PATHCONV=1
STREAM=manual-test-$(date +%s)
aws logs create-log-stream --log-group-name /liberdade/staging --log-stream-name "$STREAM"
aws logs put-log-events --log-group-name /liberdade/staging --log-stream-name "$STREAM" \
  --log-events timestamp=$(($(date +%s) * 1000)),message='MARKETS_ALERT type=DATASET_STALE'
```
