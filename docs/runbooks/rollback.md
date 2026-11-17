# Voltar para uma versão anterior

Roteiro para o mantenedor. Leva uns 10 minutos, quase todos esperando o workflow.

## Objetivo

Pôr de volta no ar uma versão anterior da API e do front de um ambiente, pelo mesmo pipeline que
publica versões novas. O deploy é sempre pelo workflow: nunca se roda `docker` à mão no host.

## Quando fazer

- A versão nova está no ar, mas com defeito que o `smoke.sh` não pegou.
- Um deploy ficou vermelho. Nesse caso o `deploy.sh` já voltou sozinho à versão anterior da API;
  este roteiro serve para conferir e, se preciso, repetir a volta.
- O deploy ficou vermelho só no passo "Smoke test": a versão nova continua no ar. Decida pelo que o
  `smoke.sh` relatou e, se for um problema de verdade, volte à mão por este roteiro.

## Antes de começar

- O tag do release (por exemplo o do release anterior) ou o SHA completo (40 caracteres) do commit
  bom. Quase todo commit da `main` tem a imagem `sha-<SHA>` no GHCR (se vários commits chegam
  rápido, o workflow `main` pode pular o do meio), então quase qualquer commit da `main` serve, desde
  que seja de depois de `deploy/` existir (o deploy baixa `deploy.sh`, `compose.yaml` e
  `Caddyfile` desse commit). Se o `docker buildx imagetools` ou o job de deploy disser que a imagem
  não existe, rode de novo o workflow `main` desse commit (Actions > a execução > Re-run) ou dispare-o.
- Permissão para aprovar o Environment `production`, se for o caso.
- O Environment certo: `staging` ou `production`. Desfazer a produção não mexe no staging, e vice-versa.

## Passos

1. Descubra se a versão ruim tinha migração de banco. Compare as pastas das duas versões:

```bash
git diff --stat <ref bom>..<ref ruim> -- api/src/main/resources/db/migration
```

Sem arquivos novos, siga para o passo 2. Com arquivos novos, leia cada um: a migração precisa
ter só expandido o esquema (colunas, tabelas ou índices novos; D6). O Flyway não desfaz
migração: a versão antiga vai rodar com o esquema novo, e isso só funciona se nada foi removido,
renomeado ou tornado obrigatório. Se a migração removeu ou renomeou algo, não faça o rollback
direto: peça uma correção para a frente.

2. No GitHub, abra Actions > Deploy > Run workflow. Em "Use workflow from", escolha `main` (o
   Environment `production` e o `staging` só aceitam a `main`: de outro branch, a regra do
   Environment recusa o disparo). Preencha:

- `environment`: `staging` ou `production`;
- `ref`: o tag ou o SHA bom.

3. Se for a produção, aprove o Environment quando o GitHub pedir (duas vezes: jobs `api` e `web`).
4. Espere o workflow terminar. O job `api` baixa os arquivos do host desse commit, troca só a API do
   ambiente escolhido e espera ela ficar saudável; o job `web` publica o front desse commit.
5. Confira o `smoke.sh`: ele roda no fim do job `web` (passo "Smoke test"). Para repetir à mão:

```bash
bash deploy/smoke.sh <API_URL do ambiente> <WEB_URL do ambiente>
```

6. Abra o app no ambiente, entre e lance um gasto de teste.

## O que olhar

- O workflow termina verde e o `smoke.sh` imprime `smoke: web ok`.
- A versão em uso é a do `ref` que você pediu (o passo "Deploy the API over SSM" mostra
  `deployed sha-<SHA> to <ambiente>`).
- Nenhum erro novo em `/liberdade/<ambiente>` no CloudWatch Logs.

## Se falhar

- **O workflow não acha a imagem:** o SHA nunca foi publicado, ou o pacote `liberdade-api` não está
  público. Confirme o SHA e a visibilidade do pacote no GHCR.
- **O `deploy.sh` volta sozinho à versão anterior ("rolled back to ..."):** a versão pedida também
  não ficou saudável. Leia a saída do host no workflow. Causa provável: a migração da versão ruim
  deixou o esquema incompatível com a versão antiga (passo 1). Escolha um commit mais novo, com a
  correção.
- **O disparo é recusado, ou a aprovação da produção não aparece:** o "Use workflow from" não foi
  `main`. Vale para `staging` e `production`.
- **Os arquivos do host mudaram:** o `compose.yaml` e o `Caddyfile` valem para os dois ambientes, e o
  rollback também volta esses dois arquivos para os do commit pedido. O Caddyfile é
  compartilhado: o rollback recarrega o Caddy na hora para os dois ambientes (todo deploy roda o
  `reload_caddy`). Se a definição do serviço `caddy` mudou, o contêiner é recriado, e os dois
  ambientes têm uma queda curta.
- **A volta automática do `deploy.sh` restaura também o arquivo de ambiente anterior.** Já o
  rollback disparado à mão lê o SSM como ele está agora: se foi uma mudança no SSM que causou o
  problema, corrija ou desfaça o SSM antes.
- **O deploy foi interrompido no meio (limite de 900 s do SSM):** o `tags.env` pode apontar a tag
  quebrada como a atual. Nesse caso, dispare à mão o deploy do último SHA bom.
- **Voltou a API para antes da migração V12:** a tela de consentimento antiga pode mostrar os aceites
  dos termos e da privacidade como cartões sem família e sem o botão "revogar". É só estético: os
  dados estão certos e as migrações só adicionam, então não precisa voltar o banco.
- **Precisa recriar a infraestrutura (OpenTofu), não só a versão:** a RDS usa o snapshot final fixo
  `liberdade-final`. Um segundo `destroy` e recriação falha porque o snapshot já existe; apague ou
  renomeie o antigo antes, e desligue `deletion_protection`.
- **Nunca use `docker` à mão no host** para publicar versão. Se o workflow não resolve, abra uma
  issue com a saída do passo "Deploy the API over SSM".
