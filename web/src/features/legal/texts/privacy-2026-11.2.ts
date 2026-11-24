import type { LegalText } from '../legalText'

/**
 * The privacy policy `privacy-2026-11.2`: the `privacy-2026-11.1` text, with budgets, goals and contributions, the
 * emergency reserve and the fixed-cost mark named among the data. The date is unchanged. People who accepted
 * `privacy-2026-11` or `privacy-2026-11.1` keep that acceptance and are told of the change
 * (`LegalDocuments.KEEPS_ACCEPTANCE`).
 */
export const privacy2026112Messages = {
  'policy.2026-11.2.intro':
    'Aqui você lê, em palavras simples, o que o Liberdade faz com os seus dados. Seguimos a Lei Geral de Proteção de Dados (LGPD), que garante o acesso fácil a estas informações (art. 9).',

  'policy.2026-11.2.who.title': 'Quem cuida dos seus dados?',
  'policy.2026-11.2.who.p1':
    'O controlador dos dados é {controller}. O controlador também atua como encarregado pelo tratamento de dados (LGPD, art. 41).',
  'policy.2026-11.2.who.p2': 'Para falar com o encarregado, escreva para {email}.',

  'policy.2026-11.2.data.title': 'Que dados guardamos e para quê?',
  'policy.2026-11.2.data.intro': 'Guardamos só o que o app precisa para funcionar:',
  'policy.2026-11.2.data.name': 'Seu nome e apelido, para a família saber quem é quem.',
  'policy.2026-11.2.data.email': 'O e-mail da sua conta Google, para você entrar.',
  'policy.2026-11.2.data.passkey':
    'A chave pública das suas passkeys. A chave privada fica no seu aparelho e nunca chega até nós.',
  'policy.2026-11.2.data.ledger':
    'Seus lançamentos, contas e categorias, além de tags, forma de pagamento, recorrências e lançamentos previstos, que são o motivo de o app existir.',
  'policy.2026-11.2.data.planning':
    'Seus orçamentos, metas e aportes, a reserva de emergência (contas marcadas e o custo mensal que você informou) e a marcação de gastos fixos, para planejar o dinheiro da família.',
  'policy.2026-11.2.data.trail':
    'A trilha de atividade: o que foi feito na conta e quando. Ela não guarda IP nem valores, só o navegador e o sistema.',
  'policy.2026-11.2.data.consents':
    'Os aceites dos termos e os consentimentos que você deu ou recebeu.',
  'policy.2026-11.2.data.cookie':
    'Usamos um cookie só, o de sessão, necessário para você entrar. Não há anúncio, não vendemos dados e não usamos rastreador.',

  'policy.2026-11.2.basis.title': 'Em que a lei nos autoriza a tratar esses dados?',
  'policy.2026-11.2.basis.contract':
    'Execução do contrato: para oferecer o serviço que você pediu ao criar a conta.',
  'policy.2026-11.2.basis.consent':
    'Consentimento específico de um responsável: para o Jovem usar o app (veja a próxima seção).',
  'policy.2026-11.2.basis.legal':
    'Obrigação legal e exercício de direitos: para guardar a trilha de atividade.',

  'policy.2026-11.2.youth.title': 'E os dados de crianças e adolescentes?',
  'policy.2026-11.2.youth.p1':
    'O Jovem só entra com o consentimento de um Dono ou Adulto da família. Esse consentimento pode ser revogado a qualquer momento.',
  'policy.2026-11.2.youth.p2': 'O Jovem vê só a própria carteira, não as contas dos adultos.',
  'policy.2026-11.2.youth.p3':
    'O melhor interesse do Jovem orienta as decisões do app (LGPD, art. 14).',

  'policy.2026-11.2.partners.title': 'Com quem os dados são tratados?',
  'policy.2026-11.2.partners.intro':
    'Contamos com três operadores, que tratam os dados em nosso nome:',
  'policy.2026-11.2.partners.aws': 'AWS: servidores em São Paulo, no Brasil.',
  'policy.2026-11.2.partners.cloudflare':
    'Cloudflare: entrega do site, DNS e encaminhamento de e-mail.',
  'policy.2026-11.2.partners.google': 'Google: o login com a sua conta Google.',
  'policy.2026-11.2.partners.transfer':
    'A Cloudflare e o Google podem tratar dados fora do Brasil. Essa transferência internacional acontece por eles, como operadores (LGPD, art. 33).',

  'policy.2026-11.2.retention.title': 'Por quanto tempo guardamos?',
  'policy.2026-11.2.retention.intro':
    'Guardamos os dados enquanto a sua conta existir. Depois, estes são os prazos:',
  'policy.2026-11.2.retention.deletion':
    'Conta ou família excluída: 30 dias de carência, para você mudar de ideia. Depois disso, apagamos tudo de vez.',
  'policy.2026-11.2.retention.transaction': 'Lançamento excluído: apagado de vez após 30 dias.',
  'policy.2026-11.2.retention.invitations': 'Convites usados ou vencidos: apagados após 90 dias.',
  'policy.2026-11.2.retention.trail': 'Trilha de atividade: 12 meses.',
  'policy.2026-11.2.retention.backups':
    'Cópias de segurança do banco: somem em até 7 dias. O apagamento definitivo alcança as cópias nesse prazo.',
  'policy.2026-11.2.retention.logs':
    'Registros técnicos do servidor: até 30 dias, sem dados financeiros.',

  'policy.2026-11.2.rights.title': 'Quais são os seus direitos e como usá-los?',
  'policy.2026-11.2.rights.intro': 'Pela LGPD (art. 18), você pode:',
  'policy.2026-11.2.rights.export':
    'Baixar os seus dados e excluir a sua conta, em Mais, Privacidade.',
  'policy.2026-11.2.rights.correct': 'Corrigir os seus dados no próprio app.',
  'policy.2026-11.2.rights.revoke': 'Revogar o consentimento do Jovem, em Mais, Privacidade.',
  'policy.2026-11.2.rights.other':
    'Fazer qualquer outro pedido por e-mail, para {email}. Respondemos em até 15 dias.',

  'policy.2026-11.2.security.title': 'Como protegemos os dados?',
  'policy.2026-11.2.security.https': 'Todo o tráfego usa HTTPS.',
  'policy.2026-11.2.security.database': 'O banco de dados fica em São Paulo e é criptografado.',
  'policy.2026-11.2.security.passwordless':
    'Não existe senha no Liberdade: você entra com o Google ou com uma passkey.',
  'policy.2026-11.2.security.leastPrivilege':
    'Cada parte do sistema tem só as permissões de que precisa (menor privilégio).',

  'policy.2026-11.2.changes.title': 'E se esta política mudar?',
  'policy.2026-11.2.changes.p1': 'A versão e a data ficam no topo desta página.',
  'policy.2026-11.2.changes.p2':
    'Quando a mudança for importante, o app pede o seu aceite de novo antes de você continuar. Ajustes menores aparecem como um aviso no app.',
} as const

export const privacy2026112: LegalText = {
  updatedAt: '23 de novembro de 2026',
  intro: 'policy.2026-11.2.intro',
  sections: [
    {
      title: 'policy.2026-11.2.who.title',
      blocks: [{ p: 'policy.2026-11.2.who.p1' }, { p: 'policy.2026-11.2.who.p2' }],
    },
    {
      title: 'policy.2026-11.2.data.title',
      blocks: [
        { p: 'policy.2026-11.2.data.intro' },
        {
          list: [
            'policy.2026-11.2.data.name',
            'policy.2026-11.2.data.email',
            'policy.2026-11.2.data.passkey',
            'policy.2026-11.2.data.ledger',
            'policy.2026-11.2.data.planning',
            'policy.2026-11.2.data.trail',
            'policy.2026-11.2.data.consents',
          ],
        },
        { p: 'policy.2026-11.2.data.cookie' },
      ],
    },
    {
      title: 'policy.2026-11.2.basis.title',
      blocks: [
        {
          list: [
            'policy.2026-11.2.basis.contract',
            'policy.2026-11.2.basis.consent',
            'policy.2026-11.2.basis.legal',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.2.youth.title',
      blocks: [
        { p: 'policy.2026-11.2.youth.p1' },
        { p: 'policy.2026-11.2.youth.p2' },
        { p: 'policy.2026-11.2.youth.p3' },
      ],
    },
    {
      title: 'policy.2026-11.2.partners.title',
      blocks: [
        { p: 'policy.2026-11.2.partners.intro' },
        {
          list: [
            'policy.2026-11.2.partners.aws',
            'policy.2026-11.2.partners.cloudflare',
            'policy.2026-11.2.partners.google',
          ],
        },
        { p: 'policy.2026-11.2.partners.transfer' },
      ],
    },
    {
      title: 'policy.2026-11.2.retention.title',
      blocks: [
        { p: 'policy.2026-11.2.retention.intro' },
        {
          list: [
            'policy.2026-11.2.retention.deletion',
            'policy.2026-11.2.retention.transaction',
            'policy.2026-11.2.retention.invitations',
            'policy.2026-11.2.retention.trail',
            'policy.2026-11.2.retention.backups',
            'policy.2026-11.2.retention.logs',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.2.rights.title',
      blocks: [
        { p: 'policy.2026-11.2.rights.intro' },
        {
          list: [
            'policy.2026-11.2.rights.export',
            'policy.2026-11.2.rights.correct',
            'policy.2026-11.2.rights.revoke',
            'policy.2026-11.2.rights.other',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.2.security.title',
      blocks: [
        {
          list: [
            'policy.2026-11.2.security.https',
            'policy.2026-11.2.security.database',
            'policy.2026-11.2.security.passwordless',
            'policy.2026-11.2.security.leastPrivilege',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.2.changes.title',
      blocks: [{ p: 'policy.2026-11.2.changes.p1' }, { p: 'policy.2026-11.2.changes.p2' }],
    },
  ],
}
