# ADR-0004: Hospedagem enxuta na AWS São Paulo

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

- O mantenedor domina AWS. Os dados financeiros da família devem ficar no Brasil (LGPD) e com baixa
  latência.
- O uso é familiar e sem receita: o custo fixo precisa ser o menor possível sem abrir mão de
  backup, criptografia e um ambiente de staging.

## Decisão

- **Front:** Cloudflare Pages (plano grátis), com previews por PR.
- **API:** uma EC2 `t4g.small` (ARM) em `sa-east-1` com Docker e Caddy (TLS automático), rodando
  produção e staging.
- **Banco:** RDS PostgreSQL 18 `db.t4g.micro`, Single-AZ, criptografado com KMS, backup de 7 dias,
  bancos `liberdade_prod` e `liberdade_staging`.
- **Segredos:** SSM Parameter Store (SecureString, sem custo no nível padrão).
- **Logs:** CloudWatch Logs no nível gratuito.
- **DNS:** Route 53 para `liberdadefamilia.com.br`, registrado no Registro.br.
- **IaC:** OpenTofu em `infra/`.
- **Quando ligar:** só no fim da v0.1. Até lá, tudo roda local em Docker, e o front tem previews
  grátis.

## Custos

Preços on-demand de `sa-east-1` consultados em 2026-11-02, com dólar PTAX de venda de R$ 5,4212
(30/10/2026), arredondado para R$ 5,42.

| Item | US$/mês | R$/mês |
|---|---|---|
| EC2 `t4g.small` (730 h) | 19,56 | ≈ 106 |
| Disco EBS da EC2 + IPv4 público | — | ≈ 39 |
| RDS `db.t4g.micro` PostgreSQL (730 h) | 24,80 | ≈ 134 |
| Armazenamento do RDS | — | ≈ 25 |
| Route 53 + domínio `.com.br` (≈ R$ 40/ano) | — | ≈ 7 |
| Cloudflare Pages, SSM, CloudWatch (nível grátis) | 0 | 0 |
| **Total** | | **≈ R$ 311/mês** |
| Com EC2 e RDS reservadas por 1 ano | | **≈ R$ 220/mês** |

- **Créditos de conta nova:** até US$ 200 (≈ R$ 1.084), cerca de 3,5 meses de operação.
- **ROI e payback:** o app se paga no primeiro mês em que ajudar a família a evitar pelo menos
  R$ 311 em gastos (≈ 3% de uma renda de R$ 10 mil). ROI mensal = (economia − 311) ÷ 311; por
  exemplo, R$ 500 economizados dão ≈ 61%.
- Conferir na [calculadora da AWS](https://calculator.aws/) antes de ligar, porque preços mudam.

Fontes: [EC2 on-demand](https://aws.amazon.com/ec2/pricing/on-demand/),
[RDS for PostgreSQL](https://aws.amazon.com/rds/postgresql/pricing/),
[Nível gratuito da AWS](https://aws.amazon.com/free/),
[limites do Cloudflare Pages](https://developers.cloudflare.com/pages/platform/limits/),
[PTAX do Banco Central](https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/aplicacao#!/recursos/CotacaoDolarPeriodo),
[preços do Registro.br](https://registro.br/ajuda/precos/).

## Alternativas consideradas

| Alternativa | Custo estimado | Por que não |
|---|---|---|
| Mesma arquitetura em `us-east-1` | ≈ R$ 175/mês | Dados fora do Brasil e ≈ 120 ms a mais de latência |
| ECS Fargate + ALB (prod + staging) | ≈ R$ 750–800/mês | Auto-recuperação e deploy sem downtime, mas caro para uso familiar |
| Lambda + Aurora Serverless | ≈ R$ 30–80/mês | O banco pausa sem uso e demora a acordar; Spring Boot em Lambda tem partida lenta |

## Consequências

- Uma instância só: se cair, o app cai. Mitigação: backups com restauração pontual, IaC para
  recriar e alarme de status.
- Staging e produção dividem máquina e instância de banco; usuários de banco separados.
- Caminho de escala sem mudar código: EC2 → ECS Fargate + ALB; RDS → Multi-AZ.
