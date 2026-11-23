import type { LegalText } from '../legalText'

/**
 * The privacy policy `privacy-2026-12.1`: the `privacy-2026-12` text, with tags, payment method, recurrences and planned
 * transactions named among the data, and a new date. People who accepted `privacy-2026-12` keep that acceptance and
 * are told of the change (`LegalDocuments.KEEPS_ACCEPTANCE`).
 */
export const privacy2026121Messages = {
  'policy.2026-12.1.intro':
    'Aqui você lê, em palavras simples, o que o Liberdade faz com os seus dados. Seguimos a Lei Geral de Proteção de Dados (LGPD), que garante o acesso fácil a estas informações (art. 9).',

  'policy.2026-12.1.who.title': 'Quem cuida dos seus dados?',
  'policy.2026-12.1.who.p1':
    'O controlador dos dados é {controller}. O controlador também atua como encarregado pelo tratamento de dados (LGPD, art. 41).',
  'policy.2026-12.1.who.p2': 'Para falar com o encarregado, escreva para {email}.',

  'policy.2026-12.1.data.title': 'Que dados guardamos e para quê?',
  'policy.2026-12.1.data.intro': 'Guardamos só o que o app precisa para funcionar:',
  'policy.2026-12.1.data.name': 'Seu nome e apelido, para a família saber quem é quem.',
  'policy.2026-12.1.data.email': 'O e-mail da sua conta Google, para você entrar.',
  'policy.2026-12.1.data.passkey':
    'A chave pública das suas passkeys. A chave privada fica no seu aparelho e nunca chega até nós.',
  'policy.2026-12.1.data.ledger':
    'Seus lançamentos, contas e categorias, além de tags, forma de pagamento, recorrências e lançamentos previstos, que são o motivo de o app existir.',
  'policy.2026-12.1.data.trail':
    'A trilha de atividade: o que foi feito na conta e quando. Ela não guarda IP nem valores, só o navegador e o sistema.',
  'policy.2026-12.1.data.consents':
    'Os aceites dos termos e os consentimentos que você deu ou recebeu.',
  'policy.2026-12.1.data.plan':
    'O plano da família: qual é, até quando vale e o tema de cor escolhido pela Família Fundadora.',
  'policy.2026-12.1.data.orders':
    'Os pedidos de compra do plano: o produto, o valor, as datas, o estado e os códigos do pagamento no Asaas.',
  'policy.2026-12.1.data.webhooks':
    'O registro dos avisos de pagamento que o Asaas nos manda: o tipo do aviso, os códigos, quando chegou e o resultado. O conteúdo do aviso não é guardado.',
  'policy.2026-12.1.data.payer':
    'Não guardamos CPF, cartão nem endereço de quem paga. Também não mandamos ao Asaas o nome nem o e-mail da sua conta Google.',
  'policy.2026-12.1.data.cookie':
    'Usamos um cookie só, o de sessão, necessário para você entrar. Não há anúncio, não vendemos dados e não usamos rastreador.',

  'policy.2026-12.1.basis.title': 'Em que a lei nos autoriza a tratar esses dados?',
  'policy.2026-12.1.basis.intro': 'Cada dado tem a sua base legal (LGPD, art. 7º):',
  'policy.2026-12.1.basis.contract':
    'Execução do contrato: para oferecer o serviço que você pediu ao criar a conta. Vale também para o plano da família e para os pedidos de compra.',
  'policy.2026-12.1.basis.consent':
    'Consentimento específico de um responsável: para o Jovem usar o app (veja a próxima seção).',
  'policy.2026-12.1.basis.legal':
    'Obrigação legal e exercício de direitos: para guardar a trilha de atividade. Depois que a família é excluída, os pedidos pagos ficam guardados por obrigação legal (fiscal).',
  'policy.2026-12.1.basis.legitimate':
    'Legítimo interesse: para guardar o registro dos avisos de pagamento, por segurança e para achar erros. Esse registro não guarda o conteúdo do aviso.',

  'policy.2026-12.1.youth.title': 'E os dados de crianças e adolescentes?',
  'policy.2026-12.1.youth.p1':
    'O Jovem só entra com o consentimento de um Dono ou Adulto da família. Esse consentimento pode ser revogado a qualquer momento.',
  'policy.2026-12.1.youth.p2': 'O Jovem vê só a própria carteira, não as contas dos adultos.',
  'policy.2026-12.1.youth.p3':
    'O melhor interesse do Jovem orienta as decisões do app (LGPD, art. 14).',

  'policy.2026-12.1.partners.title': 'Com quem os dados são tratados?',
  'policy.2026-12.1.partners.intro':
    'Contamos com quatro operadores, que tratam os dados em nosso nome:',
  'policy.2026-12.1.partners.aws': 'AWS: servidores em São Paulo, no Brasil.',
  'policy.2026-12.1.partners.cloudflare':
    'Cloudflare: entrega do site, DNS e encaminhamento de e-mail.',
  'policy.2026-12.1.partners.google': 'Google: o login com a sua conta Google.',
  'policy.2026-12.1.partners.asaas':
    'Asaas: o pagamento do plano Família Fundadora e a nota fiscal. Na página dele, quem paga informa CPF, nome, e-mail e, se for o caso, o cartão. Esses dados ficam no Asaas, pelas regras dele: o Liberdade não pede nem guarda esses dados.',
  'policy.2026-12.1.partners.transfer':
    'A Cloudflare e o Google podem tratar dados fora do Brasil. Essa transferência internacional acontece por eles, como operadores (LGPD, art. 33).',

  'policy.2026-12.1.retention.title': 'Por quanto tempo guardamos?',
  'policy.2026-12.1.retention.intro':
    'Guardamos os dados enquanto a sua conta existir. Depois, estes são os prazos:',
  'policy.2026-12.1.retention.deletion':
    'Conta ou família excluída: 30 dias de carência, para você mudar de ideia. Depois disso, apagamos tudo de vez, menos os pedidos de compra e o registro dos avisos de pagamento. Se a conta de quem comprou for excluída, os pedidos perdem a ligação com essa pessoa; se a família for excluída, perdem a ligação com ela. Eles ficam guardados pelos prazos dos próximos itens.',
  'policy.2026-12.1.retention.paidOrders':
    'Pedido pago: 5 anos depois do pagamento, por obrigação fiscal, mesmo que a família seja excluída antes. Na exclusão da família, o pedido perde a ligação com ela; na exclusão da conta de quem comprou, perde a ligação com essa pessoa. Nos dois casos, ficam o produto, o valor, as datas, o estado e os códigos do Asaas.',
  'policy.2026-12.1.retention.unpaidOrders': 'Pedido nunca pago (cancelado ou vencido): 180 dias.',
  'policy.2026-12.1.retention.plan': 'Plano da família: enquanto a família existir.',
  'policy.2026-12.1.retention.webhooks':
    'Registro dos avisos de pagamento, sem o conteúdo: 180 dias.',
  'policy.2026-12.1.retention.payer':
    'CPF, nome, e-mail e cartão de quem paga: ficam só no Asaas, pelos prazos das regras dele.',
  'policy.2026-12.1.retention.transaction': 'Lançamento excluído: apagado de vez após 30 dias.',
  'policy.2026-12.1.retention.invitations': 'Convites usados ou vencidos: apagados após 90 dias.',
  'policy.2026-12.1.retention.trail': 'Trilha de atividade: 12 meses.',
  'policy.2026-12.1.retention.backups':
    'Cópias de segurança do banco: somem em até 7 dias. O apagamento definitivo alcança as cópias nesse prazo.',
  'policy.2026-12.1.retention.logs':
    'Registros técnicos do servidor: até 30 dias, sem dados financeiros.',

  'policy.2026-12.1.rights.title': 'Quais são os seus direitos e como usá-los?',
  'policy.2026-12.1.rights.intro': 'Pela LGPD (art. 18), você pode:',
  'policy.2026-12.1.rights.export':
    'Baixar os seus dados e excluir a sua conta, em Mais, Privacidade. O arquivo da família traz também os pedidos de compra (produto, valor, datas e estado) e o plano atual.',
  'policy.2026-12.1.rights.correct': 'Corrigir os seus dados no próprio app.',
  'policy.2026-12.1.rights.revoke': 'Revogar o consentimento do Jovem, em Mais, Privacidade.',
  'policy.2026-12.1.rights.other':
    'Fazer qualquer outro pedido por e-mail, para {email}, inclusive sobre os dados de pagamento que ficam no Asaas. Respondemos em até 15 dias.',

  'policy.2026-12.1.security.title': 'Como protegemos os dados?',
  'policy.2026-12.1.security.https': 'Todo o tráfego usa HTTPS.',
  'policy.2026-12.1.security.database': 'O banco de dados fica em São Paulo e é criptografado.',
  'policy.2026-12.1.security.passwordless':
    'Não existe senha no Liberdade: você entra com o Google ou com uma passkey.',
  'policy.2026-12.1.security.payment':
    'O pagamento acontece na página segura do Asaas, e os dados do cartão nunca passam pelo Liberdade.',
  'policy.2026-12.1.security.leastPrivilege':
    'Cada parte do sistema tem só as permissões de que precisa (menor privilégio).',

  'policy.2026-12.1.changes.title': 'E se esta política mudar?',
  'policy.2026-12.1.changes.p1': 'A versão e a data ficam no topo desta página.',
  'policy.2026-12.1.changes.p2':
    'Quando a mudança for importante, o app pede o seu aceite de novo antes de você continuar. Ajustes menores aparecem como um aviso no app.',
} as const

export const privacy2026121: LegalText = {
  updatedAt: '23 de novembro de 2026',
  intro: 'policy.2026-12.1.intro',
  sections: [
    {
      title: 'policy.2026-12.1.who.title',
      blocks: [{ p: 'policy.2026-12.1.who.p1' }, { p: 'policy.2026-12.1.who.p2' }],
    },
    {
      title: 'policy.2026-12.1.data.title',
      blocks: [
        { p: 'policy.2026-12.1.data.intro' },
        {
          list: [
            'policy.2026-12.1.data.name',
            'policy.2026-12.1.data.email',
            'policy.2026-12.1.data.passkey',
            'policy.2026-12.1.data.ledger',
            'policy.2026-12.1.data.trail',
            'policy.2026-12.1.data.consents',
            'policy.2026-12.1.data.plan',
            'policy.2026-12.1.data.orders',
            'policy.2026-12.1.data.webhooks',
          ],
        },
        { p: 'policy.2026-12.1.data.payer' },
        { p: 'policy.2026-12.1.data.cookie' },
      ],
    },
    {
      title: 'policy.2026-12.1.basis.title',
      blocks: [
        { p: 'policy.2026-12.1.basis.intro' },
        {
          list: [
            'policy.2026-12.1.basis.contract',
            'policy.2026-12.1.basis.consent',
            'policy.2026-12.1.basis.legal',
            'policy.2026-12.1.basis.legitimate',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-12.1.youth.title',
      blocks: [
        { p: 'policy.2026-12.1.youth.p1' },
        { p: 'policy.2026-12.1.youth.p2' },
        { p: 'policy.2026-12.1.youth.p3' },
      ],
    },
    {
      title: 'policy.2026-12.1.partners.title',
      blocks: [
        { p: 'policy.2026-12.1.partners.intro' },
        {
          list: [
            'policy.2026-12.1.partners.aws',
            'policy.2026-12.1.partners.cloudflare',
            'policy.2026-12.1.partners.google',
            'policy.2026-12.1.partners.asaas',
          ],
        },
        { p: 'policy.2026-12.1.partners.transfer' },
      ],
    },
    {
      title: 'policy.2026-12.1.retention.title',
      blocks: [
        { p: 'policy.2026-12.1.retention.intro' },
        {
          list: [
            'policy.2026-12.1.retention.deletion',
            'policy.2026-12.1.retention.paidOrders',
            'policy.2026-12.1.retention.unpaidOrders',
            'policy.2026-12.1.retention.plan',
            'policy.2026-12.1.retention.webhooks',
            'policy.2026-12.1.retention.payer',
            'policy.2026-12.1.retention.transaction',
            'policy.2026-12.1.retention.invitations',
            'policy.2026-12.1.retention.trail',
            'policy.2026-12.1.retention.backups',
            'policy.2026-12.1.retention.logs',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-12.1.rights.title',
      blocks: [
        { p: 'policy.2026-12.1.rights.intro' },
        {
          list: [
            'policy.2026-12.1.rights.export',
            'policy.2026-12.1.rights.correct',
            'policy.2026-12.1.rights.revoke',
            'policy.2026-12.1.rights.other',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-12.1.security.title',
      blocks: [
        {
          list: [
            'policy.2026-12.1.security.https',
            'policy.2026-12.1.security.database',
            'policy.2026-12.1.security.passwordless',
            'policy.2026-12.1.security.payment',
            'policy.2026-12.1.security.leastPrivilege',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-12.1.changes.title',
      blocks: [{ p: 'policy.2026-12.1.changes.p1' }, { p: 'policy.2026-12.1.changes.p2' }],
    },
  ],
}
