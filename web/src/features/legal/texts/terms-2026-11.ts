import type { LegalText } from '../legalText'

/** The terms of use from before billing (`terms-2026-11`). Kept word for word: people accepted them. */
export const terms202611Messages = {
  'terms.2026-11.intro': 'Estas são as regras para usar o Liberdade. São curtas de propósito.',

  'terms.2026-11.what.title': 'O que é o Liberdade?',
  'terms.2026-11.what.p1':
    'O Liberdade é um app para a família organizar o dinheiro: lançar gastos e ganhos, ver o mês e conversar sobre as contas.',

  'terms.2026-11.free.title': 'É grátis e de código aberto',
  'terms.2026-11.free.p1':
    'O uso é gratuito. O código é aberto, sob a licença AGPL-3.0, e qualquer pessoa pode ler e propor melhorias.',

  'terms.2026-11.education.title': 'O conteúdo é educativo, não é recomendação de investimento',
  'terms.2026-11.education.p1':
    'O que o app mostra é educativo e informativo. O app compara, explica e simula.',
  'terms.2026-11.education.p2':
    'Ele não faz recomendação personalizada de investimento. Recomendar valores mobiliários de forma personalizada é uma atividade regulada, exercida por analistas e consultores credenciados pela CVM (Comissão de Valores Mobiliários).',
  'terms.2026-11.education.p3':
    'Decida com calma e, se precisar, procure um profissional credenciado.',

  'terms.2026-11.responsibility.title': 'O que é responsabilidade da família',
  'terms.2026-11.responsibility.p1':
    'A família é responsável pelo que registra no app e por quem convida para entrar.',
  'terms.2026-11.responsibility.p2':
    'Convide só quem você conhece e, para o Jovem, só se você for responsável por ele.',

  'terms.2026-11.asIs.title': 'O serviço é oferecido como está',
  'terms.2026-11.asIs.p1':
    'Fazemos o possível para o app funcionar bem, mas não garantimos que ele esteja sempre no ar nem livre de erros.',

  'terms.2026-11.delete.title': 'Como excluir a conta',
  'terms.2026-11.delete.p1':
    'Você pode excluir a sua conta quando quiser, em Mais, Privacidade. Há 30 dias de carência antes do apagamento definitivo.',

  'terms.2026-11.law.title': 'Lei aplicável e contato',
  'terms.2026-11.law.p1': 'Valem as leis do Brasil.',
  'terms.2026-11.law.p2': 'Dúvidas sobre estes termos? Escreva para {email}.',
} as const

export const terms202611: LegalText = {
  updatedAt: 'novembro de 2026',
  intro: 'terms.2026-11.intro',
  sections: [
    { title: 'terms.2026-11.what.title', blocks: [{ p: 'terms.2026-11.what.p1' }] },
    { title: 'terms.2026-11.free.title', blocks: [{ p: 'terms.2026-11.free.p1' }] },
    {
      title: 'terms.2026-11.education.title',
      blocks: [
        { p: 'terms.2026-11.education.p1' },
        { p: 'terms.2026-11.education.p2' },
        { p: 'terms.2026-11.education.p3' },
      ],
    },
    {
      title: 'terms.2026-11.responsibility.title',
      blocks: [{ p: 'terms.2026-11.responsibility.p1' }, { p: 'terms.2026-11.responsibility.p2' }],
    },
    { title: 'terms.2026-11.asIs.title', blocks: [{ p: 'terms.2026-11.asIs.p1' }] },
    { title: 'terms.2026-11.delete.title', blocks: [{ p: 'terms.2026-11.delete.p1' }] },
    {
      title: 'terms.2026-11.law.title',
      blocks: [{ p: 'terms.2026-11.law.p1' }, { p: 'terms.2026-11.law.p2' }],
    },
  ],
}
