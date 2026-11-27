# Lançamento da v0.1 (Plano 5)

Roteiro para o mantenedor. Leva alguns dias de calendário (a troca de servidores de nome e a
verificação do domínio esperam), mas só umas 4 horas de trabalho até o staging no ar. Depois vêm o
teste de restauração, a produção e as 2 semanas de uso.

## Objetivo

Pôr a v0.1 em produção do jeito certo: domínio, conta da AWS, infraestrutura, bancos, parâmetros,
login com o Google, front no Cloudflare Pages, e-mail de privacidade e o pipeline do GitHub. Cada
passo diz o que conferir antes de seguir. O staging vai primeiro; a produção só sai pelo release, com
a sua aprovação.

## Quando fazer

Depois do merge do PR 2 do Plano 5 na `main` (o código do deploy, da infra e dos runbooks precisa
estar lá). Faça os passos 1 a 12 sem pressa e nesta ordem: vários dependem de valores anotados nos
passos anteriores.

## Antes de começar

- Uma conta Google de teste (a sua e a de 1 pessoa da família) para os passos 8 e 14.
- Um cartão para a AWS e o Registro.br, e uma caixa de e-mail que você leia (os avisos da AWS e o
  `privacidade@` vão para ela).
- No computador: `git`, `gh` (autenticado), `tofu` 1.13.2, `aws` CLI v2 com o plugin Session Manager,
  `psql` 15 ou mais novo, `jq`, `openssl` e `dig`. No Windows, use o Git Bash e prefixe com
  `MSYS_NO_PATHCONV=1` os comandos do `aws ssm` cujo nome começa com `/` (senão o Git Bash troca o
  caminho). Os blocos abaixo com esses comandos já começam com `export MSYS_NO_PATHCONV=1`.
- Onde este roteiro diz `<seu e-mail>`, `<senha gerada>` ou `<id da conta>`, troque pelo seu valor.
  Nenhum desses valores entra no repositório. Segredos vão para o SSM como `SecureString` com
  `--key-id alias/liberdade`, nunca para arquivo versionado nem para o chat.
- Custos de referência (dólar de R$ 5,7324): cerca de R$ 329 por mês sob demanda, dos quais a AWS
  são US$ 56,85, e R$ 40 por ano do domínio. Veja a tabela em
  `docs/superpowers/plans/2026-11-16-liberdade-plan-5-go-live.md` (seção "Custos mensais") e a
  ADR-0014.

## Passos

### 1. Comprar o domínio

1. Em registro.br, compre `liberdadefamilia.com.br` (R$ 40 por ano).

Conferir: o domínio aparece como seu na área logada do Registro.br.

### 2. Zona no Cloudflare e servidores de nome

1. No Cloudflare, crie uma conta e adicione o site `liberdadefamilia.com.br` no plano gratuito
   (Free). Anote os dois servidores de nome que ele mostrar.
2. No Registro.br, em DNS do domínio, troque os servidores de nome pelos do Cloudflare. Se o
   domínio tiver DNSSEC ligado no Registro.br, remova o DS antes de trocar.
3. Anote o `Account ID` do Cloudflare (barra lateral da conta): ele vira `CLOUDFLARE_ACCOUNT_ID`
   no passo 12.

Conferir:

```bash
dig NS liberdadefamilia.com.br +short
```

A resposta lista os dois servidores do Cloudflare. A troca pode levar até algumas horas; o
Cloudflare mostra a zona como "Active" quando termina. Siga para o passo 3 enquanto espera.

### 3. Conta da AWS

1. Crie a conta em aws.amazon.com no plano pago (Paid), não no gratuito (Free): a conta do plano
   gratuito fecha quando os créditos acabam ou em 6 meses, e a produção cairia junto (ADR-0014).
2. Ligue o 2FA no usuário raiz. Depois crie um usuário administrador (com 2FA) para o dia a dia e
   pare de usar o raiz.
3. No quadro "Explore AWS" da página inicial do console, conclua 5 atividades de boas-vindas (por
   exemplo EC2, RDS, Lambda, Bedrock e AWS Budgets), US$ 20 de crédito cada. Apague o que elas
   criarem logo depois de cada uma, para não gastar crédito à toa. Validade dos créditos, onde
   acompanhar e o que acontece quando acabam: [stores-and-aws-credits.md](stores-and-aws-credits.md),
   parte 1.
4. Configure o `aws` CLI com o usuário administrador (`aws configure`, região `sa-east-1`). Anote
   o número da conta com:

```bash
aws sts get-caller-identity --query Account --output text
```

Conferir: em Billing > Credits aparecem os créditos (US$ 100 da criação mais os das atividades), e
`aws sts get-caller-identity` mostra o usuário administrador, não o raiz.

### 4. OpenTofu: bootstrap e infra

1. Crie o bucket do estado (uma vez, com estado local):

```bash
cd infra/bootstrap
tofu init
tofu apply -var state_bucket_name=liberdade-tfstate-<id da conta>
```

2. Em `infra/live`, crie os dois arquivos a partir dos exemplos. Os dois são ignorados pelo Git:

```bash
cd ../live
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars
```

3. Edite `backend.hcl`: `bucket = "liberdade-tfstate-<id da conta>"` (o mesmo nome do passo 1;
   `region` já está certa). Edite `terraform.tfvars`: `alert_email = "<seu e-mail>"` e, se o
   repositório não for `warthogozz-cyber/liberdade` (o padrão de `variables.tf`),
   `github_repository = "<dono>/<repositório>"`.
4. Aplique:

```bash
tofu init -backend-config=backend.hcl
tofu plan
tofu apply
```

5. Anote os outputs (`tofu output`). Cada um é usado adiante:

| Output                  | Onde entra                                                  |
| ----------------------- | ----------------------------------------------------------- |
| `instance_id`           | variável `EC2_INSTANCE_ID` (passo 12) e a sessão do passo 7 |
| `elastic_ip`            | registros A do passo 6                                      |
| `rds_endpoint`          | URL do banco (passos 7 e 8)                                 |
| `rds_master_secret_arn` | senha mestre da RDS (passo 7)                               |
| `deploy_role_arn`       | variável `AWS_DEPLOY_ROLE_ARN` (passo 12)                   |
| `alerts_topic_arn`      | conferência dos alarmes (passo 5)                           |
| `kms_key_arn`           | só referência: o alias é `alias/liberdade`                  |

Conferir:

- `tofu apply` termina sem erro (a RDS leva uns 10 minutos). O recurso `aws_instance.host` aparece
  criado.
- A instância apareceu no Systems Manager (espere uns 3 minutos depois do apply):

```bash
aws ssm describe-instance-information --region sa-east-1 \
  --query 'InstanceInformationList[].[InstanceId,PingStatus]' --output text
```

O `instance_id` do output aparece com `Online`.

- O pacote de certificados da RDS foi baixado no primeiro boot. O `user_data` baixa
  `https://truststore.pki.rds.amazonaws.com/sa-east-1/sa-east-1-bundle.pem` para
  `/opt/liberdade/certs/rds-bundle.pem` sem conferir checksum. Entre no host e anote o hash:

```bash
aws ssm start-session --target <instance_id>
ls -l /opt/liberdade/certs/rds-bundle.pem
sha256sum /opt/liberdade/certs/rds-bundle.pem
exit
```

Compare o hash com o que a AWS publica para esse pacote, se a página de certificados da RDS
trouxer um SHA-256 para ele. Se não trouxer, guarde o hash no PR 3 e trate como lacuna conhecida
(veja "Se falhar"). O arquivo precisa ter pelo menos um certificado: confira com `grep -c 'BEGIN CERTIFICATE'` (o resultado não pode ser 0).

### 5. Confirmar o e-mail de alertas

1. Abra a caixa `<seu e-mail>`. A AWS manda "AWS Notification - Subscription Confirmation" do
   tópico `liberdade-alerts`. Clique em "Confirm subscription". Sem isso, nenhum alarme chega.
2. Confira a assinatura:

```bash
aws sns list-subscriptions-by-topic --topic-arn <alerts_topic_arn> \
  --query 'Subscriptions[].[Endpoint,SubscriptionArn]' --output text
```

3. Confira os 4 alarmes:

```bash
aws cloudwatch describe-alarms --alarm-name-prefix liberdade- \
  --query 'MetricAlarms[].[AlarmName,StateValue]' --output text
```

Conferir:

- O `SubscriptionArn` é um ARN, não `PendingConfirmation`.
- Aparecem `liberdade-ec2-status`, `liberdade-ec2-cpu`, `liberdade-rds-free-storage` e
  `liberdade-rds-cpu-credits`. `INSUFFICIENT_DATA` nos primeiros minutos é normal; depois viram
  `OK`.
- Teste a entrega com o alarme de CPU (nunca com o `liberdade-ec2-status`, que dispara a
  recuperação da instância):

```bash
aws cloudwatch set-alarm-state --alarm-name liberdade-ec2-cpu \
  --state-value ALARM --state-reason "teste de entrega"
```

Chega um e-mail de ALARM; em seguida o alarme volta sozinho para `OK` e chega outro e-mail.

- O orçamento `liberdade-monthly` (US$ 60 por mês, sem contar créditos) manda e-mail direto, sem
  confirmar. O aviso `FORECASTED` (previsão de 100%) pode não disparar numa conta nova até a AWS ter
  histórico de custo. No começo, confie só no aviso `ACTUAL` (gasto real acima de 80%).

### 6. Registros A da API

No Cloudflare, em DNS da zona, crie dois registros A **só DNS** (nuvem cinza, sem proxy), com o
`elastic_ip` do passo 4:

| Tipo | Nome          | Conteúdo       | Proxy  |
| ---- | ------------- | -------------- | ------ |
| A    | `api`         | `<elastic_ip>` | só DNS |
| A    | `api-staging` | `<elastic_ip>` | só DNS |

Sem proxy, para o Caddy emitir o certificado pelo desafio HTTP-01. Se a zona tiver registro CAA, ele
precisa autorizar `letsencrypt.org`.

Conferir:

```bash
dig +short api.liberdadefamilia.com.br
dig +short api-staging.liberdadefamilia.com.br
```

Os dois devolvem o `elastic_ip`. Ainda não há HTTPS: o Caddy só sobe no primeiro deploy (passo 13).

### 7. Bancos

O `infra/sql/bootstrap.sql` cria os usuários `liberdade_production` e `liberdade_staging` e os
bancos de mesmo nome. Precisa do `psql` 15 ou mais novo (usa `\getenv`) e lê as senhas das
variáveis de ambiente `PRODUCTION_PASSWORD` e `STAGING_PASSWORD` (não de `-v`). Nunca rodou antes:
este é o primeiro uso. Use dois terminais: um só para a sessão de porta (passo 1) e um de trabalho para o resto, sem fechá-lo até o fim do passo.

1. Abra a sessão de encaminhamento de porta no primeiro terminal e deixe-a aberta (troque
   `<host da RDS>` pelo `rds_endpoint` sem o `:5432`):

```bash
aws ssm start-session --target <instance_id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<host da RDS>"],"portNumber":["5432"],"localPortNumber":["15432"]}'
```

2. No terminal de trabalho, na raiz do repositório, gere as senhas e guarde-as no SSM antes de
   usar (assim você não as perde). A senha mestre é lida do Secrets Manager; ela é gerida pela
   RDS e muda a cada rotação, então leia na hora:

```bash
export MSYS_NO_PATHCONV=1
export PRODUCTION_PASSWORD="$(openssl rand -hex 32)"
export STAGING_PASSWORD="$(openssl rand -hex 32)"
aws ssm put-parameter --name /liberdade/production/SPRING_DATASOURCE_PASSWORD \
  --type SecureString --key-id alias/liberdade --value "$PRODUCTION_PASSWORD"
aws ssm put-parameter --name /liberdade/staging/SPRING_DATASOURCE_PASSWORD \
  --type SecureString --key-id alias/liberdade --value "$STAGING_PASSWORD"
export PGPASSWORD="$(aws secretsmanager get-secret-value --secret-id <rds_master_secret_arn> \
  --query SecretString --output text | jq -r .password)"
```

3. Rode o script (o usuário mestre é `liberdade_admin`):

```bash
psql "host=127.0.0.1 port=15432 dbname=postgres user=liberdade_admin sslmode=require" \
  -f infra/sql/bootstrap.sql
```

4. Confira e limpe as variáveis:

```bash
psql "host=127.0.0.1 port=15432 dbname=postgres user=liberdade_admin sslmode=require" \
  -c '\du' -c '\l'
unset PRODUCTION_PASSWORD STAGING_PASSWORD PGPASSWORD
```

Conferir:

- O `psql` termina sem erro. Rodar de novo é seguro (não repete o que existe), mas nunca troca a
  senha de um usuário que já existe; para trocar, use `ALTER ROLE`.
- `\du` lista `liberdade_production` e `liberdade_staging`. `\l` lista os bancos
  `liberdade_production` e `liberdade_staging`, cada um com o dono de mesmo nome.
- O erro `PRODUCTION_PASSWORD and STAGING_PASSWORD must both be set` quer dizer que uma variável
  não foi exportada.
- Os dois parâmetros `SPRING_DATASOURCE_PASSWORD` existem (o passo 9 confere a lista toda).

### 8. Clientes OAuth do Google (D16)

Um cliente por ambiente, no Google Cloud Console (APIs e serviços > Credenciais > Criar
credenciais > ID do cliente OAuth, tipo "Aplicativo da Web"):

|                                    | `staging`                                                              | `production`                                                   |
| ---------------------------------- | ---------------------------------------------------------------------- | -------------------------------------------------------------- |
| URI de redirecionamento autorizado | `https://api-staging.liberdadefamilia.com.br/login/oauth2/code/google` | `https://api.liberdadefamilia.com.br/login/oauth2/code/google` |

1. Na tela de consentimento, deixe os escopos `openid`, `email` e `profile` e ponha o link da
   política: `https://app.liberdadefamilia.com.br/privacidade`.
2. Se o Google pedir verificação do app, deixe-o em modo "Teste" e cadastre as contas da família
   como usuários de teste. Isso basta para a v0.1.
3. Guarde o ID e o segredo de cada cliente nos parâmetros `GOOGLE_CLIENT_ID` e
   `GOOGLE_CLIENT_SECRET` do passo 9.

Conferir: os dois clientes existem, cada um com só o seu redirecionamento, e os 4 parâmetros do
Google estão no SSM.

### 9. Parâmetros do SSM (D7)

O `deploy.sh` lê tudo que está em `/liberdade/<ambiente>/` e grava `/run/liberdade/<ambiente>.env`
no host. Crie os parâmetros de cada ambiente (`staging` e `production`). Segredos são
`SecureString` com `--key-id alias/liberdade`; o resto é `String`. Um valor não pode ter aspas
simples nem quebra de linha (o deploy recusa).

| Parâmetro                          | Tipo         | Valor em `staging`                                                                                         | Valor em `production`                 |
| ---------------------------------- | ------------ | ---------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| `SPRING_DATASOURCE_URL`            | String       | `jdbc:postgresql://<rds_endpoint>/liberdade_staging?sslmode=verify-full&sslrootcert=/certs/rds-bundle.pem` | o mesmo, com `/liberdade_production`  |
| `SPRING_DATASOURCE_USERNAME`       | String       | `liberdade_staging`                                                                                        | `liberdade_production`                |
| `SPRING_DATASOURCE_PASSWORD`       | SecureString | já criado no passo 7                                                                                       | já criado no passo 7                  |
| `GOOGLE_CLIENT_ID`                 | String       | do passo 8                                                                                                 | do passo 8                            |
| `GOOGLE_CLIENT_SECRET`             | SecureString | do passo 8                                                                                                 | do passo 8                            |
| `WEB_ORIGIN`                       | String       | `https://staging.liberdadefamilia.com.br`                                                                  | `https://app.liberdadefamilia.com.br` |
| `WEBAUTHN_RP_ID`                   | String       | `staging.liberdadefamilia.com.br`                                                                          | `app.liberdadefamilia.com.br`         |
| `SPRING_PROFILES_ACTIVE`           | String       | `prod`                                                                                                     | `prod`                                |
| `LIBERDADE_BILLING_ENABLED`        | String       | `true` (só no [runbook do sandbox](billing-sandbox.md))                                                    | não criar até a cobrança ligar        |
| `LIBERDADE_BILLING_PRESALE_OPEN`   | String       | `true`                                                                                                     | não criar até a cobrança ligar        |
| `LIBERDADE_BILLING_ASAAS_BASE_URL` | String       | `https://api-sandbox.asaas.com`                                                                            | não criar até a cobrança ligar        |
| `LIBERDADE_BILLING_CHECKOUT_HOSTS` | String       | `sandbox.asaas.com`                                                                                        | não criar até a cobrança ligar        |
| `ASAAS_API_KEY`                    | SecureString | chave do sandbox                                                                                           | não criar até a cobrança ligar        |
| `ASAAS_WEBHOOK_TOKEN`              | SecureString | token de 32 a 255 caracteres                                                                               | não criar até a cobrança ligar        |

O `<rds_endpoint>` é o output inteiro, com a porta (`host:5432`). O caminho
`/certs/rds-bundle.pem` é onde o `compose.yaml` monta o pacote de certificados dentro do container.

Exemplos (um comando por parâmetro; repita com `--overwrite` para corrigir um valor):

```bash
export MSYS_NO_PATHCONV=1
aws ssm put-parameter --name /liberdade/staging/WEB_ORIGIN \
  --type String --value https://staging.liberdadefamilia.com.br
read -rs -p "Segredo do cliente OAuth (não aparece ao digitar): " GOOGLE_SECRET; echo
aws ssm put-parameter --name /liberdade/staging/GOOGLE_CLIENT_SECRET \
  --type SecureString --key-id alias/liberdade --value "$GOOGLE_SECRET"
unset GOOGLE_SECRET
```

Assim o segredo não fica no histórico do terminal. Faça o mesmo para o de `production`.

Conferir (só nomes, sem valores):

```bash
export MSYS_NO_PATHCONV=1
for env in staging production; do
  aws ssm get-parameters-by-path --path /liberdade/$env/ \
    --query 'Parameters[].[Name,Type]' --output text
done
```

Até a cobrança do Plano 6, cada ambiente lista os 8 primeiros parâmetros, com `SecureString` em
`SPRING_DATASOURCE_PASSWORD` e `GOOGLE_CLIENT_SECRET`. Os 6 parâmetros de cobrança (linhas
`LIBERDADE_BILLING_*` e `ASAAS_*`) são criados só no staging, pelo
[runbook do sandbox](billing-sandbox.md): o staging passa a ter 14 e a produção continua com 8
até a cobrança ligar. Os padrões do `application.properties` deixam a venda desligada, então a
produção não precisa de nenhum deles antes disso.

### 10. Projetos do Pages e redirecionamento do topo

1. No Cloudflare, em Workers e Pages, crie dois projetos Pages por envio direto (Direct Upload):
   `liberdade-staging` e `liberdade-production`. O nome vira `PAGES_PROJECT` no passo 12. Deixe
   `main` como branch de produção (o deploy usa `--branch=main`).
2. Em cada projeto, em Custom domains, adicione `staging.liberdadefamilia.com.br` (no
   `liberdade-staging`) e `app.liberdadefamilia.com.br` (no `liberdade-production`). Com a zona no
   Cloudflare, o registro DNS é criado sozinho.
3. Redirecione o domínio de topo: crie um registro A proxied (nuvem laranja) para `@` com o IP
   `192.0.2.1`, e em Rules > Redirect Rules uma regra "o host é `liberdadefamilia.com.br`" com
   redirecionamento estático 301 para `https://app.liberdadefamilia.com.br`.
4. Crie o token de API do Cloudflare de cada ambiente, só com a permissão Cloudflare Pages: Edit na
   conta (Meu perfil > Tokens de API). Anote cada token só para colar no passo 12.

Conferir:

```bash
curl -sI https://liberdadefamilia.com.br | head -n 3
```

Responde `301` com `location: https://app.liberdadefamilia.com.br/`. Os domínios do Pages mostram
"Active" (o certificado leva alguns minutos). Antes do primeiro deploy, as páginas do Pages ainda
estão vazias; é esperado.

### 11. E-mail de privacidade

1. No Cloudflare, em Email > Email Routing da zona, ative o roteamento (ele cria os registros MX e
   TXT) e confirme o endereço de destino pelo e-mail que o Cloudflare manda para `<seu e-mail>`.
2. Crie o endereço personalizado `privacidade@liberdadefamilia.com.br` com destino na sua caixa.
3. De outra conta de e-mail, mande uma mensagem de teste para `privacidade@liberdadefamilia.com.br`.

Conferir: a mensagem chega na sua caixa em poucos minutos (olhe também o spam).

### 12. GitHub: repositório, Environments, variáveis e segredos

Quem faz isto é você, na hora (configuração do repositório). Em Settings do repositório, ou pelo
`gh`:

1. **Repositório público.** O pacote do GHCR e os arquivos que o deploy baixa do
   `raw.githubusercontent.com` dependem disso. Confirme também que a `main` está protegida.
2. **Environments** `staging` e `production`:
   - `production`: Required reviewers com você; em Deployment branches and tags, "Selected branches
     and tags" com só a regra `main`. O `release-please.yml` chama o `deploy.yml` a partir de um push
     na `main` e passa o tag do release só como entrada `ref` (o que o `actions/checkout` baixa), então
     a execução fica na `main` e não é preciso liberar tags. O GitHub pede a aprovação duas vezes
     numa produção, uma no job `api` e outra no job `web`.
   - `staging`: sem revisor, mas **com** Deployment branches "Selected branches" e só a regra `main`
     (obrigatório). O papel de deploy do staging chega ao mesmo host que guarda os segredos da
     produção, então qualquer branch que pudesse disparar o staging ganharia acesso de root lá.
3. **Variáveis por Environment** (as mesmas 10 nos dois, com valores diferentes):

| Variável                      | `staging`                                                     | `production`                          |
| ----------------------------- | ------------------------------------------------------------- | ------------------------------------- |
| `AWS_DEPLOY_ROLE_ARN`         | output `deploy_role_arn`                                      | o mesmo                               |
| `EC2_INSTANCE_ID`             | output `instance_id`                                          | o mesmo                               |
| `API_URL`                     | `https://api-staging.liberdadefamilia.com.br`                 | `https://api.liberdadefamilia.com.br` |
| `WEB_URL`                     | `https://staging.liberdadefamilia.com.br`                     | `https://app.liberdadefamilia.com.br` |
| `PAGES_PROJECT`               | `liberdade-staging`                                           | `liberdade-production`                |
| `CLOUDFLARE_ACCOUNT_ID`       | `Account ID` do passo 2                                       | o mesmo                               |
| `PRIVACY_CONTROLLER`          | `<nome do controlador>`                                       | o mesmo                               |
| `PRIVACY_CONTROLLER_DOCUMENT` | `<CPF ou CNPJ, com o rótulo, ex.: "CNPJ 00.000.000/0001-00">` | o mesmo                               |
| `PRIVACY_CONTROLLER_ADDRESS`  | `<endereço físico de quem vende>`                             | o mesmo                               |
| `PRIVACY_CONTACT`             | `privacidade@liberdadefamilia.com.br`                         | o mesmo                               |

Os termos 2026-12 mostram o nome, o documento e o endereço de quem vende (Decreto 7.962/2013,
art. 2º) a partir de `PRIVACY_CONTROLLER`, `PRIVACY_CONTROLLER_DOCUMENT` e
`PRIVACY_CONTROLLER_ADDRESS` (o deploy do front as lê como `vars.*`). Confirme que
`PRIVACY_CONTROLLER` é o nome legal de quem vende. Sem os dois novos, os termos mostram "CPF ou
CNPJ a informar" e "local a informar". Preencha antes de ligar a cobrança na produção (veja
[Antes da produção](billing-sandbox.md#antes-da-produção)).

`API_URL` e `WEB_URL` vão sem barra no fim: o `smoke.sh` procura o endereço da API dentro da CSP
do front.

4. **Segredo por Environment:** `CLOUDFLARE_API_TOKEN`, o token do passo 10 de cada ambiente.
5. **Variável de repositório** `CLOUDFLARE_ACCOUNT_ID` com o mesmo `Account ID`: o workflow
   `preview.yml` lê `vars.CLOUDFLARE_ACCOUNT_ID` e, se não achar, o segredo de repositório de mesmo
   nome. Depois que um deploy de preview passar com a variável, apague o segredo antigo
   `CLOUDFLARE_ACCOUNT_ID`.
6. O `preview.yml` publica os previews dos PRs a partir do Environment `preview`, que lê
   `secrets.CLOUDFLARE_API_TOKEN` e publica no projeto Pages `liberdade`. Isso já existe e não
   precisa de mudança.
7. **Não crie ainda** `DEPLOY_ENABLED`: ela liga os deploys no passo 13.
8. **Pacote do GHCR público.** O pacote `liberdade-api` só existe depois do primeiro push na `main`
   (o workflow "Publish image" cria). Em Packages > `liberdade-api` > Package settings > Change
   visibility, escolha Public.

Por `gh`, por exemplo (repita para `production`):

```bash
gh variable set API_URL --env staging --body https://api-staging.liberdadefamilia.com.br
gh secret set CLOUDFLARE_API_TOKEN --env staging
gh variable set CLOUDFLARE_ACCOUNT_ID --body <id da conta do Cloudflare>
```

(`gh secret set` sem `--body` pede o valor no terminal, sem deixá-lo no histórico.)

Conferir:

```bash
gh variable list --env staging
gh variable list --env production
gh secret list --env staging
gh secret list --env production
```

Confira também, em Settings > Environments, que `staging` e `production` têm Deployment branches
em "Selected branches" com só a regra `main`.

Confira que o pacote é público, sem Docker e sem login: peça um token anônimo e busque o manifesto
da imagem de um commit da `main` (troque `<SHA>` pelo SHA completo do último commit da `main`):

```bash
TOKEN="$(curl -fsS 'https://ghcr.io/token?scope=repository:warthogozz-cyber/liberdade-api:pull' | jq -r .token)"
curl -fsSI -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: application/vnd.oci.image.index.v1+json' \
  https://ghcr.io/v2/warthogozz-cyber/liberdade-api/manifests/sha-<SHA> | head -n 1
unset TOKEN
```

Também vale abrir a página do pacote numa janela anônima (sem login no GitHub): ela precisa abrir.

Cada ambiente mostra as 8 variáveis e o segredo `CLOUDFLARE_API_TOKEN`; a variável `DEPLOY_ENABLED`
ainda não existe; a conferência anônima do pacote responde `HTTP/2 200`.

### 13. Ligar o deploy

1. Crie a variável de repositório `DEPLOY_ENABLED` com o valor `true`:

```bash
gh variable set DEPLOY_ENABLED --body true
```

2. Dispare o deploy do staging. O jeito normal é um push na `main`. Se a `main` já está atualizada,
   use Actions > Deploy > Run workflow, com `environment` = `staging` e `ref` = o SHA completo
   (40 caracteres) do último commit da `main`. O SHA precisa ter imagem `sha-<SHA>` no GHCR (quase
   todo commit da `main` tem). Se o job disser que a imagem não existe, rode de novo o workflow
   `main` desse commit (Actions > a execução > Re-run) ou dispare-o.

Conferir: o workflow "Deploy" fica verde, com os jobs `api` e `web` e o passo "Smoke test". Na
primeira vez, o Caddy sobe e emite os certificados de `api-staging`; pode levar um ou dois minutos
depois do deploy. O `deploy.yml` mostra a saída do host no passo "Deploy the API over SSM".

### 14. Conferir o staging

1. O `smoke.sh` verde no workflow. Para repetir à mão:

```bash
bash deploy/smoke.sh https://api-staging.liberdadefamilia.com.br https://staging.liberdadefamilia.com.br
```

2. No navegador, abra `https://staging.liberdadefamilia.com.br`, entre com o Google (conta de teste),
   crie uma passkey, saia e entre com ela, e lance um gasto.
3. Abra o console do navegador (F12): sem violação de CSP.

Conferir: login com o Google, passkey e gasto funcionam; `/privacidade` e `/termos` abrem; o
`smoke.sh` termina com `smoke: web ok`. Os logs do staging em CloudWatch (`/liberdade/staging`)
estão em JSON, sem dado pessoal. O Caddy não grava log de acesso nem de erro HTTP, então nenhum
endereço de cliente aparece em `/liberdade/caddy`.

### 15. iPhone e restauração

1. Rode o [runbook do iPhone](ios-session-cookie.md) no staging.
2. Rode o [teste de restauração](restore-test.md).

Conferir: os dois resultados anotados no PR 3 do Plano 5. Se o iPhone falhar, a família não entra até
você decidir (veja o runbook do iPhone).

### 16. Release e produção

1. O release-please mantém um PR de release aberto (v0.1.0). Revise e faça o merge dele. Isso cria o
   tag e o release e dispara, em `release-please.yml`: o job `retag` (marca a imagem com o tag do
   release) e o `deploy-production`. O merge também gera um deploy do staging (push na `main`).
2. No Actions, aprove o Environment `production` quando o GitHub pedir (duas vezes: jobs `api` e
   `web`).
3. Confira como no passo 14, com `https://api.liberdadefamilia.com.br` e
   `https://app.liberdadefamilia.com.br`, e confirme que `privacidade@` chega na sua caixa.

Conferir: `bash deploy/smoke.sh https://api.liberdadefamilia.com.br https://app.liberdadefamilia.com.br`
termina com `smoke: web ok`; a v0.1.0 foi publicada só pelo `release-please.yml`; `/privacidade` e
`/termos` abrem na produção e o aceite aparece para quem entra.

### 17. Teste S1

Com 2 pessoas da família, cronometrado (cada uma entra pelo convite, faz o aceite e lança o primeiro
gasto). Anote os tempos e o que travou no PR 3, sem dado pessoal (nada de nome nem e-mail).

### 18. Duas semanas de uso

Deixe a família usar por 2 semanas. No fim, anote no PR 3 o que travou e o custo real do Cost
Explorer (compare com R$ 329 por mês sob demanda; a decisão da reserva de 1 ano sai daqui).

## Se falhar

- **Passo 2 não propaga:** confira com `dig NS` os servidores no Registro.br; DNSSEC ligado com DS
  antigo também impede a troca.
- **`tofu apply` falha na RDS ou no orçamento:** corrija a causa e rode de novo; ele continua de onde
  parou. A RDS tem `deletion_protection` ligado, então o OpenTofu não a apaga sem você desligar.
- **Recriar a RDS depois de um `destroy`:** `final_snapshot_identifier` é fixo (`liberdade-final`).
  Um segundo destroy falha porque o snapshot já existe. Antes de destruir de novo, apague ou renomeie
  o snapshot antigo (`aws rds delete-db-snapshot --db-snapshot-identifier liberdade-final`), e
  desligue `deletion_protection` antes do destroy.
- **Pacote de certificados da RDS sem checksum (lacuna conhecida):** o `user_data` baixa o arquivo
  por HTTPS direto da AWS e não confere o SHA-256. Por isso o passo 4 manda comparar à mão. Se a AWS
  não publicar o valor, registre o hash no PR 3. Como o `user_data` está em `ignore_changes`, trocar o
  pacote depois é feito por SSM, no host.
- **Alarme não chega por e-mail:** a assinatura do SNS não foi confirmada (passo 5).
- **Aviso de previsão do orçamento não chegou:** esperado numa conta nova; vale o aviso `ACTUAL`.
- **`psql` diz `\getenv` desconhecido:** o `psql` é anterior ao 15. Instale o 15 ou mais novo.
- **`psql` recusa a conexão:** a sessão do passo 7 caiu ou a porta 15432 está em uso. Reabra a
  sessão e confira que a senha mestre veio do Secrets Manager (`rds_master_secret_arn`).
- **Deploy do staging fica vermelho:** leia a saída do host no workflow. O `deploy.sh` volta sozinho
  à versão anterior; na primeira vez, não há anterior e o ambiente fica sem API. Causas comuns:
  parâmetro faltando ou com aspas simples no valor (passo 9), imagem não pública (passo 12), senha
  do banco diferente da do `bootstrap.sql`. Corrija e dispare o deploy de novo (Actions > Deploy).
- **Deploy sem a variável `PAGES_PROJECT`, `API_URL`, `WEB_URL` ou `CLOUDFLARE_ACCOUNT_ID`:** o job
  `web` diz qual está vazia. Complete no Environment e rode de novo.
- **`smoke.sh` falha em "content-security-policy ... does not mention the API origin":** `API_URL`
  no Environment está diferente da URL real (barra no fim, ou o ambiente errado).
- **Aprovação da produção não aparece:** a execução não saiu da `main`, ou o Environment `production`
  limita outra branch.
- **Algo deu errado numa versão em produção:** use o [rollback](rollback.md).
