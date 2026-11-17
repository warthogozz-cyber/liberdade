# Teste de restauração do banco

Roteiro para o mantenedor. Leva uns 45 minutos, quase todos esperando a RDS.

## Objetivo

Provar que o backup da RDS serve: restaurar o banco num instante dos últimos 7 dias para uma
instância nova, abrir o banco de staging nela, conferir que os dados estão lá e apagar a instância.
Um backup que nunca foi restaurado é só uma esperança.

## Quando fazer

Uma vez, no staging, antes da produção (passo 15 do [go-live](go-live.md)), depois de o staging ter
algum dado (o gasto do passo 14). Repita sempre que mudar a RDS (classe, versão, parâmetros).

## Antes de começar

- O staging no ar e com pelo menos um usuário e um lançamento.
- `aws` CLI com o usuário administrador, o plugin Session Manager e `psql` 15 ou mais novo.
- O `instance_id` e a `rds_master_secret_arn` (outputs do OpenTofu, anotados no passo 4 do go-live).
- Custo: a instância restaurada é uma `db.t4g.micro`, a mesma da RDS: US$ 0,034 por hora, cerca de
  R$ 0,19 por hora (dólar de R$ 5,7324), mais o armazenamento de 20 GB pelo mesmo tempo, alguns
  centavos. Apagar logo depois mantém o custo do teste em centavos de dólar. Valores da tabela de
  custos do Plano 5 (`docs/superpowers/plans/2026-11-16-liberdade-plan-5-go-live.md`).

## Passos

1. Veja até quando dá para restaurar (a retenção é de 7 dias) e leia o grupo de segurança da RDS
   original:

```bash
aws rds describe-db-instances --db-instance-identifier liberdade \
  --query 'DBInstances[0].[LatestRestorableTime,VpcSecurityGroups[0].VpcSecurityGroupId,DBParameterGroups[0].DBParameterGroupName,DBSubnetGroup.DBSubnetGroupName]' \
  --output text
```

Anote o grupo de segurança (`<id do grupo de segurança>`). O grupo de parâmetros é
`liberdade-postgres18` e o de sub-redes é `liberdade`.

2. Anote a hora de início e peça a restauração. `--use-latest-restorable-time` pega o instante mais
   recente; para um instante específico dos últimos 7 dias, troque por
   `--restore-time <data e hora em UTC, ex. 2026-11-20T12:00:00Z>`:

```bash
date -u
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier liberdade \
  --target-db-instance-identifier liberdade-restore-test \
  --use-latest-restorable-time \
  --db-instance-class db.t4g.micro \
  --db-subnet-group-name liberdade \
  --db-parameter-group-name liberdade-postgres18 \
  --vpc-security-group-ids <id do grupo de segurança> \
  --no-publicly-accessible \
  --no-multi-az
```

3. Espere a instância ficar disponível (uns 15 a 30 minutos) e anote a hora de fim:

```bash
aws rds wait db-instance-available --db-instance-identifier liberdade-restore-test
date -u
aws rds describe-db-instances --db-instance-identifier liberdade-restore-test \
  --query 'DBInstances[0].Endpoint.Address' --output text
```

4. Abra a sessão de encaminhamento de porta para a instância restaurada, numa porta diferente da do
   banco real, e deixe aberta num terminal:

```bash
aws ssm start-session --target <instance_id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<endereço da instância restaurada>"],"portNumber":["5432"],"localPortNumber":["15433"]}'
```

5. Conte as linhas do banco de staging na instância restaurada. O usuário mestre é
   `liberdade_admin`, com a senha do Secrets Manager (a restaurada herda o usuário mestre da
   original):

```bash
export PGPASSWORD="$(aws secretsmanager get-secret-value --secret-id <rds_master_secret_arn> \
  --query SecretString --output text | jq -r .password)"
psql "host=127.0.0.1 port=15433 dbname=liberdade_staging user=liberdade_admin sslmode=require" \
  -c 'SELECT count(*) AS users FROM users' \
  -c 'SELECT count(*) AS transactions FROM transactions'
unset PGPASSWORD
```

6. Compare com o banco real: abra a sessão do passo 7 do go-live (porta 15432) e rode as mesmas
   duas contagens com `port=15432`. Dá diferença pequena se alguém lançou algo depois do instante
   restaurado.
7. Apague a instância restaurada, sem snapshot final. Confira o identificador antes de rodar: é
   `liberdade-restore-test`, nunca `liberdade`:

```bash
aws rds delete-db-instance --db-instance-identifier liberdade-restore-test \
  --skip-final-snapshot --delete-automated-backups
aws rds describe-db-instances --query 'DBInstances[].DBInstanceIdentifier' --output text
```

## O que olhar

- As contagens de `users` e `transactions` na restaurada batem com as do banco real (menos o que foi
  gravado depois do instante restaurado) e não são zero.
- A lista final de instâncias mostra só `liberdade` (e `liberdade-restore-test` some em alguns
  minutos, com o status `deleting` até lá).

## Onde anotar

No PR 3 do Plano 5, num comentário com uma linha por teste: a data, a duração da restauração (hora de
fim menos hora de início, passos 2 e 3), o instante restaurado e o resultado (contagens batem ou
não). Sem dado pessoal: só números.

## Se falhar

1. Se o `psql` não autenticar: a senha mestre pode ter sido trocada pela rotação do Secrets Manager
   (leia de novo), ou a instância restaurada não herdou a senha mestre. Nesse caso, e só nessa
   instância temporária, troque a senha dela com
   `aws rds modify-db-instance --db-instance-identifier liberdade-restore-test --master-user-password <senha gerada>`.
   Nunca faça isso na `liberdade`.
2. Se o `psql` não conectar: confira que a instância restaurada usa o mesmo grupo de segurança da
   original (só ele aceita a conexão vinda do host) e que a sessão da porta 15433 está aberta.
3. Se a restauração falhar ou as contagens não baterem: abra uma issue com o erro e o instante
   pedido. A família não entra em produção até haver uma restauração que passou, ou uma decisão sua.
4. Apague a instância restaurada mesmo se o teste falhar, para não pagar por ela.
