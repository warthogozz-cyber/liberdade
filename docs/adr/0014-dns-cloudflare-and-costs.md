# ADR-0014: DNS no Cloudflare e custos atualizados

- **Status:** Proposto
- **Data:** 2026-11-16

## Contexto

- A [ADR-0004](0004-hosting-aws-sa-east-1-lean.md) pôs o DNS de `liberdadefamilia.com.br` no
  Route 53 e estimou os custos com o dólar de 30/10/2026.
- O Plano 5 liga a AWS. Ao detalhar o lançamento, apareceram três fatos:
  - o contato de privacidade (`privacidade@liberdadefamilia.com.br`) precisa receber e-mail. O
    encaminhamento de e-mail do Cloudflare (Email Routing) é gratuito, mas exige a zona no
    Cloudflare;
  - o Cloudflare Pages cria sozinho o registro do subdomínio próprio quando a zona está no
    Cloudflare; com o Route 53, cada registro do front é manual;
  - o dólar subiu de R$ 5,42 para R$ 5,73, e os preços precisam de nova conferência.
- A ADR-0004 está aceita e não se edita: esta ADR substitui só a linha de DNS e a tabela de custos
  dela. O resto continua valendo.

## Decisão

- **DNS:** a zona `liberdadefamilia.com.br` vai para o plano gratuito do Cloudflare. O registro do
  domínio continua no Registro.br; só os servidores de nome mudam.
- `api` e `api-staging` são registros A **só DNS** (sem o proxy do Cloudflare), apontando para o IP
  elástico da EC2, para o Caddy emitir o certificado pelo desafio HTTP-01. Se a zona tiver registro
  CAA, ele autoriza `letsencrypt.org` e as autoridades que o Cloudflare usa no Pages.
- `app` e `staging` apontam para os projetos do Pages `liberdade-production` e `liberdade-staging`.
- O domínio de topo redireciona (301) para `https://app.liberdadefamilia.com.br`.
- `privacidade@` é encaminhado pelo Email Routing para a caixa que o mantenedor escolher.
- **Nomes dos bancos:** `liberdade_production` e `liberdade_staging`, iguais aos nomes dos ambientes
  em todo o resto (a ADR-0004 dizia `liberdade_prod`).

## Custos

Preços da AWS Price List API para `sa-east-1`, consultados em 2026-11-16, 730 horas por mês, dólar
PTAX de venda de 16/11/2026 (R$ 5,7324).

| Item | Uso | US$/mês | R$/mês |
|---|---|---|---|
| EC2 `t4g.small`, sob demanda | 730 h × US$ 0,0268 | 19,56 | 112,13 |
| EBS gp3 da EC2 | 20 GB × US$ 0,152 | 3,04 | 17,43 |
| IPv4 público (IP elástico) | 730 h × US$ 0,005 | 3,65 | 20,92 |
| RDS `db.t4g.micro`, Single-AZ, sob demanda | 730 h × US$ 0,034 | 24,82 | 142,28 |
| Armazenamento gp3 do RDS | 20 GB × US$ 0,219 | 4,38 | 25,11 |
| Backup do RDS (7 dias) | grátis até o tamanho do banco | 0,00 | 0,00 |
| Chave KMS do projeto | 1 chave | 1,00 | 5,73 |
| Secrets Manager (senha mestre do RDS) | 1 segredo | 0,40 | 2,29 |
| CloudWatch Logs, alarmes, SNS, SSM, S3 do estado, saída de dados | faixas gratuitas | 0,00 | 0,00 |
| **AWS** | | **56,85** | **325,89** |
| Domínio `.com.br` (Registro.br) | R$ 40 ÷ 12 | — | 3,33 |
| DNS, Pages e e-mail (Cloudflare) | plano gratuito | — | 0,00 |
| **Total sob demanda** | | | **≈ R$ 329/mês** |
| Com EC2 (US$ 0,0154/h) e RDS (US$ 0,0213/h) reservadas por 1 ano | | 39,26 na AWS | **≈ R$ 228/mês** |

- **Créditos de conta nova:** até US$ 200 (≈ R$ 1.146), cerca de 3,5 meses de AWS sob demanda.
- **Reserva:** economiza ≈ R$ 101 por mês. Fica sob demanda enquanto houver crédito; a reserva é
  decidida depois das 2 semanas de uso da família, com o custo real do Cost Explorer.
- **ROI e payback:** o app se paga no primeiro mês em que ajudar a família a evitar pelo menos
  R$ 329 em gastos (≈ 3% de uma renda de R$ 10 mil). ROI mensal = (economia − 329) ÷ 329; por
  exemplo, R$ 500 economizados dão ≈ 52%.
- **Alerta de orçamento:** US$ 60 por mês (≈ R$ 344), sem contar créditos.
- Por ano, sob demanda: ≈ R$ 3.951.

Fontes: [AWS Price List API](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/price-changes.html),
[EC2 on-demand](https://aws.amazon.com/ec2/pricing/on-demand/),
[RDS for PostgreSQL](https://aws.amazon.com/rds/postgresql/pricing/),
[Route 53](https://aws.amazon.com/route53/pricing/),
[planos do Cloudflare](https://www.cloudflare.com/plans/),
[Cloudflare Email Routing](https://developers.cloudflare.com/email-routing/),
[PTAX do Banco Central](https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/aplicacao#!/recursos/CotacaoDolarPeriodo),
[preços do Registro.br](https://registro.br/ajuda/precos/).

## Alternativas consideradas

| Alternativa | Custo estimado | Por que não |
|---|---|---|
| Route 53 + SES de entrada em `sa-east-1` para o `privacidade@` | + US$ 0,50/mês (≈ R$ 2,87) | Mais caro, registros do Pages manuais e uma regra de recebimento do SES para manter. Fica como plano B se esta ADR for recusada |
| Proxy do Cloudflare (nuvem laranja) também na API | 0 | O tráfego da API passaria descriptografado pelo Cloudflare e o desafio HTTP-01 do Caddy deixaria de funcionar sem ajustes; sem ganho para uso familiar |

## Consequências

- A conta do Cloudflare passa a ser crítica: ela controla DNS, front e e-mail. Exige 2FA, e só o
  mantenedor tem acesso.
- O DNS fica fora do OpenTofu: os registros são poucos, mudam raramente e o runbook de lançamento
  lista cada um. O token do Pages usado no CI não ganha permissão de DNS (menor privilégio).
- Os valores da ADR-0004 continuam como registro histórico; os vigentes são os desta ADR.
