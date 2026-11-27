import type { LegalText } from '../legalText'

/**
 * The privacy policy `privacy-2026-11.6`: the `privacy-2026-11.5` text, with the statement import telling that
 * the description of each line, simplified, goes to the app to suggest a category before the person confirms, and is
 * not kept. The text said only confirmed lines left the device, so a new acceptance is asked: there is no
 * `LegalDocuments.KEEPS_ACCEPTANCE` entry for it.
 */
export const privacy2026116Messages = {
  'policy.2026-11.6.intro':
    'Aqui você lê, em palavras simples, o que o Liberdade faz com os seus dados. Seguimos a Lei Geral de Proteção de Dados (LGPD), que garante o acesso fácil a estas informações (art. 9).',

  'policy.2026-11.6.who.title': 'Quem cuida dos seus dados?',
  'policy.2026-11.6.who.p1':
    'O controlador dos dados é {controller}. O controlador também atua como encarregado pelo tratamento de dados (LGPD, art. 41).',
  'policy.2026-11.6.who.p2': 'Para falar com o encarregado, escreva para {email}.',

  'policy.2026-11.6.data.title': 'Que dados guardamos e para quê?',
  'policy.2026-11.6.data.intro': 'Guardamos só o que o app precisa para funcionar:',
  'policy.2026-11.6.data.name': 'Seu nome e apelido, para a família saber quem é quem.',
  'policy.2026-11.6.data.email': 'O e-mail da sua conta Google, para você entrar.',
  'policy.2026-11.6.data.passkey':
    'A chave pública das suas passkeys. A chave privada fica no seu aparelho e nunca chega até nós.',
  'policy.2026-11.6.data.ledger':
    'Seus lançamentos, contas e categorias, além de tags, forma de pagamento, recorrências e lançamentos previstos, que são o motivo de o app existir.',
  'policy.2026-11.6.data.planning':
    'Seus orçamentos, metas e aportes, a reserva de emergência (contas marcadas e o custo mensal que você informou) e a marcação de gastos fixos, para planejar o dinheiro da família.',
  'policy.2026-11.6.data.trail':
    'A trilha de atividade: o que foi feito na conta e quando. Ela não guarda IP nem valores, só o navegador e o sistema.',
  'policy.2026-11.6.data.consents':
    'Os aceites dos termos e os consentimentos que você deu ou recebeu.',
  'policy.2026-11.6.data.public':
    'O app busca indicadores públicos no Banco Central do Brasil. Nenhum dado seu ou da sua família sai nessa busca.',
  'policy.2026-11.6.data.import':
    'Importação de extrato. Quando você importa um extrato (OFX ou CSV), o arquivo é lido no seu aparelho e não é enviado nem guardado por nós. Só os lançamentos que você confirma vão para o Liberdade, como qualquer lançamento. Antes disso, para sugerir a categoria de cada linha, o app envia a descrição da linha, simplificada, e se ela é entrada ou saída. Não vão valor nem data, e essa descrição não fica guardada: a resposta é só a categoria sugerida. Para não repetir um lançamento que você excluiu, guardamos por 13 meses um código calculado a partir da linha do extrato (um hash), sem valor, data nem descrição. Esse código é apagado junto com a conta bancária ou com a família.',
  'policy.2026-11.6.data.cookie':
    'Usamos um cookie só, o de sessão, necessário para você entrar. Não há anúncio, não vendemos dados e não usamos rastreador.',

  'policy.2026-11.6.basis.title': 'Em que a lei nos autoriza a tratar esses dados?',
  'policy.2026-11.6.basis.contract':
    'Execução do contrato: para oferecer o serviço que você pediu ao criar a conta.',
  'policy.2026-11.6.basis.consent':
    'Consentimento específico de um responsável: para o Jovem usar o app (veja a próxima seção).',
  'policy.2026-11.6.basis.legal':
    'Obrigação legal e exercício de direitos: para guardar a trilha de atividade.',

  'policy.2026-11.6.youth.title': 'E os dados de crianças e adolescentes?',
  'policy.2026-11.6.youth.p1':
    'O Jovem só entra com o consentimento de um Dono ou Adulto da família. Esse consentimento pode ser revogado a qualquer momento.',
  'policy.2026-11.6.youth.p2': 'O Jovem vê só a própria carteira, não as contas dos adultos.',
  'policy.2026-11.6.youth.p3':
    'O melhor interesse do Jovem orienta as decisões do app (LGPD, art. 14).',

  'policy.2026-11.6.partners.title': 'Com quem os dados são tratados?',
  'policy.2026-11.6.partners.intro':
    'Contamos com três operadores, que tratam os dados em nosso nome:',
  'policy.2026-11.6.partners.aws': 'AWS: servidores em São Paulo, no Brasil.',
  'policy.2026-11.6.partners.cloudflare':
    'Cloudflare: entrega do site, DNS e encaminhamento de e-mail.',
  'policy.2026-11.6.partners.google': 'Google: o login com a sua conta Google.',
  'policy.2026-11.6.partners.transfer':
    'A Cloudflare e o Google podem tratar dados fora do Brasil. Essa transferência internacional acontece por eles, como operadores (LGPD, art. 33).',
  'policy.2026-11.6.partners.feira':
    'Na Feira, o texto que você digita na busca de endereço é enviado pelo nosso servidor ao serviço Nominatim, da OpenStreetMap Foundation, no Reino Unido. Só depois que você toca para ver, o mapa vem do Google e a publicação do Instagram vem da Meta, nos Estados Unidos. Eles passam a receber o endereço de IP do seu aparelho.',

  'policy.2026-11.6.retention.title': 'Por quanto tempo guardamos?',
  'policy.2026-11.6.retention.intro':
    'Guardamos os dados enquanto a sua conta existir. Depois, estes são os prazos:',
  'policy.2026-11.6.retention.deletion':
    'Conta ou família excluída: 30 dias de carência, para você mudar de ideia. Depois disso, apagamos tudo de vez.',
  'policy.2026-11.6.retention.transaction': 'Lançamento excluído: apagado de vez após 30 dias.',
  'policy.2026-11.6.retention.invitations': 'Convites usados ou vencidos: apagados após 90 dias.',
  'policy.2026-11.6.retention.trail': 'Trilha de atividade: 12 meses.',
  'policy.2026-11.6.retention.backups':
    'Cópias de segurança do banco: somem em até 7 dias. O apagamento definitivo alcança as cópias nesse prazo.',
  'policy.2026-11.6.retention.logs':
    'Registros técnicos do servidor: até 30 dias, sem dados financeiros.',

  'policy.2026-11.6.rights.title': 'Quais são os seus direitos e como usá-los?',
  'policy.2026-11.6.rights.intro': 'Pela LGPD (art. 18), você pode:',
  'policy.2026-11.6.rights.export':
    'Baixar os seus dados e excluir a sua conta, em Mais, Privacidade.',
  'policy.2026-11.6.rights.correct': 'Corrigir os seus dados no próprio app.',
  'policy.2026-11.6.rights.revoke': 'Revogar o consentimento do Jovem, em Mais, Privacidade.',
  'policy.2026-11.6.rights.other':
    'Fazer qualquer outro pedido por e-mail, para {email}. Respondemos em até 15 dias.',

  'policy.2026-11.6.security.title': 'Como protegemos os dados?',
  'policy.2026-11.6.security.https': 'Todo o tráfego usa HTTPS.',
  'policy.2026-11.6.security.database': 'O banco de dados fica em São Paulo e é criptografado.',
  'policy.2026-11.6.security.passwordless':
    'Não existe senha no Liberdade: você entra com o Google ou com uma passkey.',
  'policy.2026-11.6.security.leastPrivilege':
    'Cada parte do sistema tem só as permissões de que precisa (menor privilégio).',

  'policy.2026-11.6.changes.title': 'E se esta política mudar?',
  'policy.2026-11.6.changes.p1': 'A versão e a data ficam no topo desta página.',
  'policy.2026-11.6.changes.p2':
    'Quando a mudança for importante, o app pede o seu aceite de novo antes de você continuar. Ajustes menores aparecem como um aviso no app.',

  'policy.2026-11.6.feira.title': 'E a Feira, os mercados perto de você?',
  'policy.2026-11.6.feira.intro':
    'A Feira só funciona na família que a ligou, e mostra mercados do OpenStreetMap e os que a própria família cadastra. Veja o que acontece com os dados dela:',
  'policy.2026-11.6.feira.location':
    'Localização: o navegador só usa a sua posição se você deixar. A posição precisa não sai do seu aparelho; o servidor recebe só a região, de cerca de 1 km.',
  'policy.2026-11.6.feira.home':
    'Endereço da família: ele vira só um ponto aproximado (cerca de 110 m) e um rótulo que você escolhe, como "Casa". Não guardamos o endereço escrito.',
  'policy.2026-11.6.feira.search':
    'Busca de endereço: o texto que você digita vai ao OpenStreetMap (Nominatim), sem nenhum dado da sua família. Os mercados vêm de um extrato público do OpenStreetMap.',
  'policy.2026-11.6.feira.embeds':
    'Mapa e Instagram: são do Google e da Meta, só carregam quando você toca e podem gravar cookies no seu navegador.',
  'policy.2026-11.6.feira.receipt':
    'Nota fiscal: ao registrar uma nota, o app lê o QR no aparelho e guarda só preços, mercado e data, nunca o CPF nem a chave da nota.',
  'policy.2026-11.6.feira.family':
    'Os mercados da família, os links que ela guardou (site, Instagram e encarte) e o ponto do endereço entram no arquivo de dados da família e são apagados junto com ela.',
} as const

export const privacy2026116: LegalText = {
  updatedAt: '27 de novembro de 2026',
  intro: 'policy.2026-11.6.intro',
  sections: [
    {
      title: 'policy.2026-11.6.who.title',
      blocks: [{ p: 'policy.2026-11.6.who.p1' }, { p: 'policy.2026-11.6.who.p2' }],
    },
    {
      title: 'policy.2026-11.6.data.title',
      blocks: [
        { p: 'policy.2026-11.6.data.intro' },
        {
          list: [
            'policy.2026-11.6.data.name',
            'policy.2026-11.6.data.email',
            'policy.2026-11.6.data.passkey',
            'policy.2026-11.6.data.ledger',
            'policy.2026-11.6.data.planning',
            'policy.2026-11.6.data.trail',
            'policy.2026-11.6.data.consents',
          ],
        },
        { p: 'policy.2026-11.6.data.cookie' },
        { p: 'policy.2026-11.6.data.public' },
        { p: 'policy.2026-11.6.data.import' },
      ],
    },
    {
      title: 'policy.2026-11.6.feira.title',
      blocks: [
        { p: 'policy.2026-11.6.feira.intro' },
        {
          list: [
            'policy.2026-11.6.feira.location',
            'policy.2026-11.6.feira.home',
            'policy.2026-11.6.feira.search',
            'policy.2026-11.6.feira.embeds',
            'policy.2026-11.6.feira.receipt',
          ],
        },
        { p: 'policy.2026-11.6.feira.family' },
      ],
    },
    {
      title: 'policy.2026-11.6.basis.title',
      blocks: [
        {
          list: [
            'policy.2026-11.6.basis.contract',
            'policy.2026-11.6.basis.consent',
            'policy.2026-11.6.basis.legal',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.6.youth.title',
      blocks: [
        { p: 'policy.2026-11.6.youth.p1' },
        { p: 'policy.2026-11.6.youth.p2' },
        { p: 'policy.2026-11.6.youth.p3' },
      ],
    },
    {
      title: 'policy.2026-11.6.partners.title',
      blocks: [
        { p: 'policy.2026-11.6.partners.intro' },
        {
          list: [
            'policy.2026-11.6.partners.aws',
            'policy.2026-11.6.partners.cloudflare',
            'policy.2026-11.6.partners.google',
          ],
        },
        { p: 'policy.2026-11.6.partners.transfer' },
        { p: 'policy.2026-11.6.partners.feira' },
      ],
    },
    {
      title: 'policy.2026-11.6.retention.title',
      blocks: [
        { p: 'policy.2026-11.6.retention.intro' },
        {
          list: [
            'policy.2026-11.6.retention.deletion',
            'policy.2026-11.6.retention.transaction',
            'policy.2026-11.6.retention.invitations',
            'policy.2026-11.6.retention.trail',
            'policy.2026-11.6.retention.backups',
            'policy.2026-11.6.retention.logs',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.6.rights.title',
      blocks: [
        { p: 'policy.2026-11.6.rights.intro' },
        {
          list: [
            'policy.2026-11.6.rights.export',
            'policy.2026-11.6.rights.correct',
            'policy.2026-11.6.rights.revoke',
            'policy.2026-11.6.rights.other',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.6.security.title',
      blocks: [
        {
          list: [
            'policy.2026-11.6.security.https',
            'policy.2026-11.6.security.database',
            'policy.2026-11.6.security.passwordless',
            'policy.2026-11.6.security.leastPrivilege',
          ],
        },
      ],
    },
    {
      title: 'policy.2026-11.6.changes.title',
      blocks: [{ p: 'policy.2026-11.6.changes.p1' }, { p: 'policy.2026-11.6.changes.p2' }],
    },
  ],
}
