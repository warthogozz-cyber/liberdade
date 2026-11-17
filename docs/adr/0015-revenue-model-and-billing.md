# ADR-0015: Modelo de receita e cobrança

- **Status:** Proposto. O spec do Plano 6 confirma os preços e o meio de pagamento e muda o status
  para Aceito.
- **Data:** 2026-11-17

## Contexto

- A infra custa **≈ R$ 329/mês** sob demanda, ou ≈ R$ 228 com reserva de 1 ano
  ([ADR-0014](0014-dns-cloudflare-and-costs.md)). Nessa escala, o custo quase não muda entre 1 e
  algumas centenas de famílias.
- Os créditos de conta nova da AWS cobrem cerca de 3,5 meses. A ideia é ter receita antes de eles
  acabarem.
- O PRD previa abrir para outras famílias só depois da v0.4. O mantenedor decidiu em 2026-11-16 pôr
  a cobrança logo depois da v0.1, no Plano 6, junto com o "Retrato do mês" compartilhável.
- O diferencial que os concorrentes principais não mostram é a **família de verdade**: papéis de
  Adulto e Jovem, convites e consentimento do responsável. Cada família que entra traz de 2 a 4
  pessoas.
- O app é um PWA. Cobrando por Pix ou cartão na web, a cobrança não passa pelas lojas de apps. Não
  há texto oficial sobre PWA; é uma inferência a partir das regras das lojas para apps nativos.

## Decisão (proposta)

- **Plano Grátis, para sempre.** Uma família com lançamentos manuais, painel do mês e metas
  básicas, para o convite da família funcionar. O spec do Plano 6 define o limite exato (por
  exemplo, até 2 adultos).
- **Plano Família:** R$ 14,90/mês ou R$ 119/ano, por família inteira, não por pessoa. Inclui o que
  dá mais trabalho ou custa mais: jovens com mesada e missões, metas ilimitadas, importação de
  extrato, Retrato do mês e retrospectiva, renda extra e suporte.
- **Pré-venda Família Fundadora:** R$ 89/ano, com o preço travado enquanto a assinatura estiver
  ativa, acesso ao beta e voto no que vem a seguir.
  - Vagas limitadas só se o limite for real.
  - Nada de "vitalício": seria custo para sempre, sem receita.
- **Regras para qualquer plano:**
  - cancelamento em um clique, sem pegadinha;
  - reembolso fácil em 7 dias (direito de arrependimento do CDC, art. 49);
  - o app nunca guarda dados de cartão: quem guarda é o processador de pagamento;
  - nada de anúncios, venda de dados ou link de afiliado de investimento.
- **Meio de pagamento:** Stripe ou Asaas, escolhido no spec do Plano 6. O critério que decide é o
  **Pix Automático** (cobrança recorrente por Pix): o Stripe Brasil ainda não oferece, segundo a
  página de suporte consultada. As taxas são conferidas de novo na página oficial antes da escolha.
- **Antes da primeira cobrança:**
  - um contador confirma o CNPJ (MEI ou ME), o CNAE e o anexo do Simples Nacional;
  - os termos de uso trazem as regras de reembolso e cancelamento;
  - a política de privacidade passa a citar o processador de pagamento.

## Custos e ponto de equilíbrio

Taxas consultadas em 2026-11-16, antes de impostos. São estes os números que o spec do Plano 6
confere de novo.

| Preço | Taxa do meio de pagamento | Líquido por família/mês | Famílias para cobrir R$ 329 | Com reserva (R$ 228) |
|---|---|---|---|---|
| R$ 14,90/mês no cartão (Stripe: 3,99% + R$ 0,39, mais 0,7% do Billing) | ≈ R$ 1,09 | ≈ R$ 13,81 | **24** | **17** |
| R$ 119/ano no Pix (Stripe 1,19%, pagamento anual avulso) | ≈ R$ 1,42/ano | ≈ R$ 9,80 | **34** | **24** |
| R$ 119/ano no Pix (Asaas, R$ 2,15 por cobrança) | R$ 2,15/ano | ≈ R$ 9,74 | **34** | **24** |
| Fundadora R$ 89/ano no Pix (Stripe) | ≈ R$ 1,06/ano | ≈ R$ 7,33 | 45 | 32 |

- **Custo fixo da cobrança:** R$ 0. Nos dois provedores só há taxa por transação.
- **ROI e payback da cobrança:** o trabalho do Plano 6 se paga quando a receita líquida passa de
  R$ 329/mês: 24 famílias no mensal, ou 34 no anual. Exemplo (hipótese, não previsão): 50 fundadoras
  × R$ 89 ≈ R$ 4.450 de uma vez, cerca de 13 meses de AWS sob demanda.
- **Impostos:** no Simples Nacional, software fica no Anexo III (a partir de 6%, se a folha for pelo
  menos 28% da receita) ou no Anexo V (a partir de 15,5%). Se assinatura de software cabe no MEI
  ainda não está confirmado.
- **Referência de mercado:** Mobills Premium custa R$ 99,90/ano; Organizze vai de R$ 29,90 a
  R$ 69,90/mês no anual, sem plano grátis. R$ 14,90/mês ou R$ 119/ano pela família inteira fica
  entre os dois e sai mais barato por pessoa.

Fontes (consultadas em 2026-11-16): [Stripe Brasil, preços](https://stripe.com/br/pricing),
[Stripe e Pix](https://support.stripe.com/questions/how-to-enable-pix-as-a-payment-method-in-brazil?locale=pt-BR),
[Asaas, preços e taxas](https://www.asaas.com/precos-e-taxas),
[Pix Automático no 1º semestre de 2026](https://finsidersbrasil.com.br/giro-noticias/pix-automatico-cresce-no-1o-semestre-de-2026-diz-bc/),
[Mobills, planos](https://www.mobills.com.br/planos/),
[Organizze, planos](https://www.organizze.com.br/planos),
[Fator R no Simples Nacional 2026](https://contabilidade.com/blog/fator-r-no-simples-nacional-2026-como-calcular-exemplos-praticos-e-quando-servicos-migram-do-anexo-v-para-o-iii/),
[Apple, mudanças no iOS no Brasil (jun/2026)](https://www.apple.com/au/newsroom/2026/06/apple-announces-changes-to-ios-in-brazil/).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Anúncios dentro do app | Quebram a promessa de privacidade e pagam pouco por usuário |
| Vender ou "compartilhar" dados | Contra a LGPD e contra a confiança, que é o produto |
| Link de afiliado de corretora ou investimento | Risco CVM: remuneração indireta é indício de atividade regulada. Só com parecer de advogado |
| Plano "vitalício" | Custo para sempre, sem receita |
| Cobrar pelas lojas de apps (compra dentro do app) | A Apple no Brasil cobra de 10% a 21%, mais 5% se usar a compra dentro do app. O PWA cobra na web, sem comissão |
| Cobrar por pessoa | Pune justamente o diferencial (a família inteira usando) e trava o convite |
| Só doações (GitHub Sponsors) | Sem taxa em conta pessoal e bom sinal de projeto aberto, mas rende pouco. Fica como complemento, não como modelo |

## Consequências

- O app passa a ter um módulo de cobrança: assinatura, webhooks do provedor e o plano de cada
  família. O desenho fica no spec do Plano 6.
- Abrir para outras famílias vem antes da v0.4. A revisão de LGPD e da marca (INPI) acontece antes
  da primeira cobrança, e não mais depois da v0.4.
- O plano Grátis continua completo para o uso do dia a dia. O que é pago nunca é ameaça nem
  pressão: nada de "seus dados somem" e nada de contagem regressiva.
- Open Finance continua para depois: o agregador custa a partir de R$ 2.500/mês, cerca de 181
  famílias pagantes só para cobri-lo. Até lá, importação de extrato OFX/CSV (v0.3).
