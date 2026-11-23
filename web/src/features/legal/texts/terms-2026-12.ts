import type { LegalText } from '../legalText'

/** The terms of use once billing is on (`terms-2026-12`): the 2026-11 rules plus the Founder presale. */
export const terms202612Messages = {
  'terms.2026-12.intro':
    'Estas são as regras para usar o Liberdade e para comprar o plano Família Fundadora. São curtas de propósito.',

  'terms.2026-12.what.title': 'O que é o Liberdade?',
  'terms.2026-12.what.p1':
    'O Liberdade é um app para a família organizar o dinheiro: lançar gastos e ganhos, ver o mês e conversar sobre as contas.',

  'terms.2026-12.free.title': 'O plano Grátis é grátis, e o código é aberto',
  'terms.2026-12.free.p1':
    'Tudo o que o app faz hoje é grátis e continua grátis no plano Grátis, inclusive o papel Jovem. Ninguém precisa pagar para usar o Liberdade.',
  'terms.2026-12.free.p2':
    'O código é aberto, sob a licença AGPL-3.0, e qualquer pessoa pode ler e propor melhorias.',

  'terms.2026-12.founder.title': 'O que é a Família Fundadora?',
  'terms.2026-12.founder.p1':
    'A Família Fundadora é a pré-venda do plano Família: você paga 1 ano adiantado, R$ 89,00, e apoia o projeto desde o começo.',
  'terms.2026-12.founder.p2': 'Só o Dono da família compra, e o plano vale para a família toda.',
  'terms.2026-12.founder.p3':
    'O pagamento é feito uma vez só, por Pix ou cartão à vista, na página segura do Asaas, a empresa que processa o pagamento. A nota fiscal sai pelo Asaas.',
  'terms.2026-12.founder.seller':
    'Quem vende o plano é {controller}, {document}, com endereço em {address}. Para falar sobre a compra, escreva para {email}.',
  'terms.2026-12.founder.p4':
    'O plano vale por 1 ano a partir da confirmação do pagamento. A data de fim aparece em Mais, Plano.',
  'terms.2026-12.founder.p5':
    'Se a família pagar duas vezes, o pagamento a mais é devolvido sozinho, sem você precisar pedir.',

  'terms.2026-12.includes.title': 'O que a Fundadora inclui hoje',
  'terms.2026-12.includes.p1':
    'Hoje a Fundadora inclui duas coisas: o selo "Família Fundadora" e um tema de cor, que o Dono escolhe entre quatro. O tema e o selo só aparecem na tela Plano e no Retrato do mês: o resto do app continua igual.',
  'terms.2026-12.includes.p2':
    'Os recursos pagos que planejamos, como mesada, metas ilimitadas, importar extrato e a Feira, chegam ao plano Família conforme ficarem prontos, sem data prometida. Não garantimos que algum deles fique pronto dentro do seu ano pago. Compre pelo que a Fundadora inclui hoje.',

  'terms.2026-12.refund.title': 'Desistiu? Reembolso integral em 7 dias',
  'terms.2026-12.refund.p1':
    'Nos 7 dias seguintes à confirmação do pagamento, você pode desistir e receber de volta os R$ 89,00 inteiros. É o direito de arrependimento do Código de Defesa do Consumidor (art. 49). A tela Plano mostra até quando dá para desistir.',
  'terms.2026-12.refund.p2':
    'Para desistir, o Dono toca em "Receber meu dinheiro de volta", em Mais, Plano, e confirma. Não é preciso dizer o motivo.',
  'terms.2026-12.refund.p3':
    'O plano termina na hora: o tema e o selo voltam ao padrão, e nenhum dado da família é apagado. O dinheiro volta pelo mesmo meio do pagamento. No cartão, o estorno pode levar até 10 dias úteis para aparecer na fatura.',
  'terms.2026-12.refund.p4':
    'Depois dos 7 dias, não há cancelamento, porque não existe cobrança futura para cancelar: o plano vale até o fim do ano pago. Para um caso especial, escreva para {email}.',

  'terms.2026-12.renewal.title': 'Sem renovação automática',
  'terms.2026-12.renewal.p1':
    'O plano não se renova sozinho e o Liberdade não guarda o seu cartão: não existe cobrança automática.',
  'terms.2026-12.renewal.p2':
    'Depois de 1 ano, o acesso pago termina e a família volta ao plano Grátis, sem perder nenhum dado. O tema e o selo voltam ao padrão.',
  'terms.2026-12.renewal.p3':
    'Hoje não existe renovação. O preço fica travado: se a renovação for oferecida, a família que comprou a Fundadora e não pediu reembolso renova pelos mesmos R$ 89,00 por ano.',

  'terms.2026-12.education.title': 'O conteúdo é educativo, não é recomendação de investimento',
  'terms.2026-12.education.p1':
    'O que o app mostra é educativo e informativo. O app compara, explica e simula.',
  'terms.2026-12.education.p2':
    'Ele não faz recomendação personalizada de investimento. Recomendar valores mobiliários de forma personalizada é uma atividade regulada, exercida por analistas e consultores credenciados pela CVM (Comissão de Valores Mobiliários).',
  'terms.2026-12.education.p3':
    'Decida com calma e, se precisar, procure um profissional credenciado.',

  'terms.2026-12.responsibility.title': 'O que é responsabilidade da família',
  'terms.2026-12.responsibility.p1':
    'A família é responsável pelo que registra no app e por quem convida para entrar.',
  'terms.2026-12.responsibility.p2':
    'Convide só quem você conhece e, para o Jovem, só se você for responsável por ele.',

  'terms.2026-12.asIs.title': 'O serviço é oferecido como está',
  'terms.2026-12.asIs.p1':
    'Fazemos o possível para o app funcionar bem, mas não garantimos que ele esteja sempre no ar nem livre de erros.',
  'terms.2026-12.asIs.p2':
    'Isso não tira nenhum dos seus direitos de consumidor garantidos pela lei.',

  'terms.2026-12.delete.title': 'Como excluir a conta',
  'terms.2026-12.delete.p1':
    'Você pode excluir a sua conta quando quiser, em Mais, Privacidade. Há 30 dias de carência antes do apagamento definitivo.',
  'terms.2026-12.delete.p2':
    'Dá para excluir a conta ou a família mesmo com o plano Família ativo. A exclusão não devolve o valor sozinha: nos 7 dias de arrependimento, peça o reembolso antes de excluir. Se precisar de ajuda, escreva para {email}.',
  'terms.2026-12.delete.p3':
    'Os pedidos pagos ficam guardados por 5 anos depois do pagamento, por obrigação fiscal. Se a família for excluída, eles perdem a ligação com ela, como explica a política de privacidade.',

  'terms.2026-12.law.title': 'Lei aplicável e contato',
  'terms.2026-12.law.p1': 'Valem as leis do Brasil, inclusive o Código de Defesa do Consumidor.',
  'terms.2026-12.law.p2': 'Dúvidas sobre estes termos ou sobre o plano? Escreva para {email}.',
} as const

export const terms202612: LegalText = {
  updatedAt: 'dezembro de 2026',
  intro: 'terms.2026-12.intro',
  sections: [
    { title: 'terms.2026-12.what.title', blocks: [{ p: 'terms.2026-12.what.p1' }] },
    {
      title: 'terms.2026-12.free.title',
      blocks: [{ p: 'terms.2026-12.free.p1' }, { p: 'terms.2026-12.free.p2' }],
    },
    {
      title: 'terms.2026-12.founder.title',
      blocks: [
        { p: 'terms.2026-12.founder.p1' },
        { p: 'terms.2026-12.founder.p2' },
        { p: 'terms.2026-12.founder.p3' },
        { p: 'terms.2026-12.founder.seller' },
        { p: 'terms.2026-12.founder.p4' },
        { p: 'terms.2026-12.founder.p5' },
      ],
    },
    {
      title: 'terms.2026-12.includes.title',
      blocks: [{ p: 'terms.2026-12.includes.p1' }, { p: 'terms.2026-12.includes.p2' }],
    },
    {
      title: 'terms.2026-12.refund.title',
      blocks: [
        { p: 'terms.2026-12.refund.p1' },
        { p: 'terms.2026-12.refund.p2' },
        { p: 'terms.2026-12.refund.p3' },
        { p: 'terms.2026-12.refund.p4' },
      ],
    },
    {
      title: 'terms.2026-12.renewal.title',
      blocks: [
        { p: 'terms.2026-12.renewal.p1' },
        { p: 'terms.2026-12.renewal.p2' },
        { p: 'terms.2026-12.renewal.p3' },
      ],
    },
    {
      title: 'terms.2026-12.education.title',
      blocks: [
        { p: 'terms.2026-12.education.p1' },
        { p: 'terms.2026-12.education.p2' },
        { p: 'terms.2026-12.education.p3' },
      ],
    },
    {
      title: 'terms.2026-12.responsibility.title',
      blocks: [{ p: 'terms.2026-12.responsibility.p1' }, { p: 'terms.2026-12.responsibility.p2' }],
    },
    {
      title: 'terms.2026-12.asIs.title',
      blocks: [{ p: 'terms.2026-12.asIs.p1' }, { p: 'terms.2026-12.asIs.p2' }],
    },
    {
      title: 'terms.2026-12.delete.title',
      blocks: [
        { p: 'terms.2026-12.delete.p1' },
        { p: 'terms.2026-12.delete.p2' },
        { p: 'terms.2026-12.delete.p3' },
      ],
    },
    {
      title: 'terms.2026-12.law.title',
      blocks: [{ p: 'terms.2026-12.law.p1' }, { p: 'terms.2026-12.law.p2' }],
    },
  ],
}
