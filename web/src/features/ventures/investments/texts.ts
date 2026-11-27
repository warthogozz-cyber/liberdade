/**
 * The educational texts of the investments track. They are plain content, not a recommendation:
 * the order is the simplest to the most complex, and no institution or product is named. The FGC
 * facts come from its official site, checked on the review date.
 */
export const REVIEWED_ON = '2026-11-25'

export type InvestmentTopicId = 'poupanca' | 'renda-fixa' | 'tesouro-direto' | 'fundos' | 'acoes'

export interface InvestmentTopic {
  id: InvestmentTopicId
  name: string
  whatItIs: string
  risk: string
  liquidity: string
  guarantee: string
  tax: string
}

// The FGC limits in force on the review date: per person and institution (or conglomerate), and a
// global cap over 4 years.
export const FGC_LIMIT =
  'até R$ 250 mil por pessoa e por instituição ou conglomerado, com teto global de R$ 1 milhão por pessoa a cada 4 anos (limite em vigor em 25/11/2026)'

/** From the simplest to the most complex. Never reorder by return or by any score. */
export const INVESTMENT_TOPICS: readonly InvestmentTopic[] = [
  {
    id: 'poupanca',
    name: 'Poupança',
    whatItIs: 'Conta de depósito em que o dinheiro rende uma regra definida pelo governo.',
    risk: 'Baixo: o valor guardado não oscila.',
    liquidity: 'Alta: dá para sacar a qualquer momento.',
    guarantee: `Coberta pela garantia do Fundo Garantidor de Créditos (FGC) ${FGC_LIMIT}.`,
    tax: 'Para pessoa física, o rendimento é isento de imposto de renda.',
  },
  {
    id: 'renda-fixa',
    name: 'Renda fixa',
    whatItIs:
      'Empréstimo a um banco ou ao governo com rendimento combinado na hora, como CDB (Certificado de Depósito Bancário), LCI e LCA (Letras de Crédito Imobiliário e do Agronegócio), que seguem taxa fixa, o CDI (taxa que os bancos usam entre si) ou o IPCA (inflação oficial).',
    risk: 'Baixo a médio: depende de quem emite e do prazo; vender antes do vencimento pode render menos.',
    liquidity:
      'Varia: alguns títulos permitem resgate diário, outros só no vencimento, que pode levar meses ou anos.',
    guarantee: `CDB, LCI e LCA têm a garantia do FGC ${FGC_LIMIT}, e o que passa disso fica sem garantia.`,
    tax: 'O rendimento costuma ter imposto de renda retido na fonte, com alíquota (a porcentagem de imposto) que cai com o prazo, e LCI e LCA de pessoa física costumam ser isentas.',
  },
  {
    id: 'tesouro-direto',
    name: 'Tesouro Direto',
    whatItIs:
      'Empréstimo ao governo federal, comprando títulos públicos pela internet, com rendimento de taxa fixa, da Selic (a taxa básica de juros, definida pelo Banco Central) ou do IPCA.',
    risk: 'Baixo a médio: o governo federal é quem paga, mas o preço do título oscila se você vender antes do vencimento.',
    liquidity:
      'Em geral há venda em dias úteis de volta ao governo; o valor recebido depende do preço do dia.',
    guarantee:
      'Quem paga é o governo federal, e não há garantia do FGC, que não cobre títulos públicos.',
    tax: 'O rendimento costuma ter imposto de renda retido na fonte, com alíquota que cai com o prazo, e pode ter taxa de custódia (a taxa de guarda dos títulos).',
  },
  {
    id: 'fundos',
    name: 'Fundos',
    whatItIs:
      'Dinheiro de várias pessoas reunido e aplicado por uma gestora em um conjunto de ativos, e você compra cotas do fundo.',
    risk: 'Varia muito com o que o fundo compra: o valor da cota pode subir ou cair, inclusive ficar abaixo do que você aplicou.',
    liquidity: 'Varia: o resgate pode levar de um dia a vários dias, ou ter prazo de carência.',
    guarantee: 'Não tem a garantia do FGC, que não cobre cotas de fundos.',
    tax: 'O rendimento costuma ter imposto de renda retido na fonte, com regra que depende do tipo de fundo, e pode ter taxa de administração.',
  },
  {
    id: 'acoes',
    name: 'Ações',
    whatItIs:
      'Pequenas partes de uma empresa, negociadas na B3 (a bolsa de valores brasileira), em que quem compra vira sócio e participa dos resultados dela.',
    risk: 'Alto: o preço oscila todos os dias e o valor pode ficar menor do que o aplicado, em casos extremos até zero.',
    liquidity: 'Alta: dá para vender em dias de pregão, pelo preço do momento.',
    guarantee: 'Não tem a garantia do FGC, que não cobre ações.',
    tax: 'O ganho na venda pode ter imposto de renda, com regras de isenção para vendas pequenas no mês.',
  },
]
