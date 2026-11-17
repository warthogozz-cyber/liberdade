/** The text of the privacy policy and of the terms of use. It loads with their routes (see `registerMessages`). */
export const legalMessages = {
  'legal.back': 'Voltar ao Liberdade',
  'legal.version': 'Versão {version} · atualizada em {date}',

  'policy.title': 'Política de privacidade',
  'policy.intro':
    'Aqui você lê, em palavras simples, o que o Liberdade faz com os seus dados. Seguimos a Lei Geral de Proteção de Dados (LGPD).',

  'policy.who.title': 'Quem cuida dos seus dados?',
  'policy.who.p1':
    'O controlador dos dados é {controller}. Essa pessoa também é a encarregada pelo tratamento dos dados.',
  'policy.who.p2': 'Para falar com ela, escreva para {email}.',

  'policy.data.title': 'Que dados guardamos e para quê?',
  'policy.data.intro': 'Guardamos só o que o app precisa para funcionar:',
  'policy.data.name': 'Seu nome e apelido, para a família saber quem é quem.',
  'policy.data.email': 'O e-mail da sua conta Google, para você entrar.',
  'policy.data.passkey':
    'A chave pública das suas passkeys. A chave privada fica no seu aparelho e nunca chega até nós.',
  'policy.data.ledger': 'Seus lançamentos, contas e categorias, que são o motivo de o app existir.',
  'policy.data.trail':
    'A trilha de atividade: o que foi feito na conta e quando. Ela não guarda IP nem valores, só o navegador e o sistema.',
  'policy.data.consents': 'Os aceites dos termos e os consentimentos que você deu ou recebeu.',
  'policy.data.cookie':
    'Usamos um cookie só, o de sessão, necessário para você entrar. Não há anúncio, não vendemos dados e não usamos rastreador.',

  'policy.basis.title': 'Em que a lei nos autoriza a tratar esses dados?',
  'policy.basis.contract':
    'Execução do contrato: para oferecer o serviço que você pediu ao criar a conta.',
  'policy.basis.consent':
    'Consentimento específico de um responsável: para o Jovem usar o app (veja a próxima seção).',
  'policy.basis.legal':
    'Obrigação legal e exercício de direitos: para guardar a trilha de atividade.',

  'policy.youth.title': 'E os dados de crianças e adolescentes?',
  'policy.youth.p1':
    'O Jovem só entra com o consentimento de um Dono ou Adulto da família. Esse consentimento pode ser revogado a qualquer momento.',
  'policy.youth.p2': 'O Jovem vê só a própria carteira, não as contas dos adultos.',
  'policy.youth.p3': 'O melhor interesse do Jovem orienta as decisões do app (LGPD, art. 14).',

  'policy.partners.title': 'Com quem os dados são tratados?',
  'policy.partners.intro': 'Contamos com três operadores, que tratam os dados em nosso nome:',
  'policy.partners.aws': 'AWS: servidores em São Paulo, no Brasil.',
  'policy.partners.cloudflare': 'Cloudflare: entrega do site, DNS e encaminhamento de e-mail.',
  'policy.partners.google': 'Google: o login com a sua conta Google.',
  'policy.partners.transfer':
    'A Cloudflare e o Google podem tratar dados fora do Brasil. Essa transferência internacional acontece por eles, como operadores (LGPD, art. 33).',

  'policy.retention.title': 'Por quanto tempo guardamos?',
  'policy.retention.intro':
    'Guardamos os dados enquanto a sua conta existir. Depois, estes são os prazos:',
  'policy.retention.deletion':
    'Conta ou família excluída: 30 dias de carência, para você mudar de ideia. Depois disso, apagamos tudo de vez.',
  'policy.retention.transaction': 'Lançamento excluído: apagado de vez após 30 dias.',
  'policy.retention.invitations': 'Convites usados ou vencidos: apagados após 90 dias.',
  'policy.retention.trail': 'Trilha de atividade: 12 meses.',
  'policy.retention.backups':
    'Cópias de segurança do banco: somem em até 7 dias. O apagamento definitivo alcança as cópias nesse prazo.',
  'policy.retention.logs': 'Registros técnicos do servidor: até 30 dias, sem dados financeiros.',

  'policy.rights.title': 'Quais são os seus direitos e como usá-los?',
  'policy.rights.intro': 'Pela LGPD (art. 18), você pode:',
  'policy.rights.export': 'Baixar os seus dados e excluir a sua conta, em Mais, Privacidade.',
  'policy.rights.correct': 'Corrigir os seus dados no próprio app.',
  'policy.rights.revoke': 'Revogar o consentimento do Jovem, em Mais, Privacidade.',
  'policy.rights.other':
    'Fazer qualquer outro pedido por e-mail, para {email}. Respondemos em até 15 dias.',

  'policy.security.title': 'Como protegemos os dados?',
  'policy.security.https': 'Todo o tráfego usa HTTPS.',
  'policy.security.database': 'O banco de dados fica em São Paulo e é criptografado.',
  'policy.security.passwordless':
    'Não existe senha no Liberdade: você entra com o Google ou com uma passkey.',
  'policy.security.leastPrivilege':
    'Cada parte do sistema tem só as permissões de que precisa (menor privilégio).',

  'policy.changes.title': 'E se esta política mudar?',
  'policy.changes.p1': 'A versão e a data ficam no topo desta página.',
  'policy.changes.p2':
    'Quando sair uma versão nova, o app pede o seu aceite de novo antes de você continuar.',

  'terms.title': 'Termos de uso',
  'terms.intro': 'Estas são as regras para usar o Liberdade. São curtas de propósito.',

  'terms.what.title': 'O que é o Liberdade?',
  'terms.what.p1':
    'O Liberdade é um app para a família organizar o dinheiro: lançar gastos e ganhos, ver o mês e conversar sobre as contas.',

  'terms.free.title': 'É grátis e de código aberto',
  'terms.free.p1':
    'O uso é gratuito. O código é aberto, sob a licença AGPL-3.0, e qualquer pessoa pode ler e propor melhorias.',

  'terms.education.title': 'O conteúdo é educativo, não é recomendação de investimento',
  'terms.education.p1':
    'O que o app mostra é educativo e informativo. O app compara, explica e simula.',
  'terms.education.p2':
    'Ele não faz recomendação personalizada de investimento. Recomendar valores mobiliários de forma personalizada é uma atividade regulada, exercida por analistas e consultores credenciados pela CVM (Comissão de Valores Mobiliários).',
  'terms.education.p3': 'Decida com calma e, se precisar, procure um profissional credenciado.',

  'terms.responsibility.title': 'O que é responsabilidade da família',
  'terms.responsibility.p1':
    'A família é responsável pelo que registra no app e por quem convida para entrar.',
  'terms.responsibility.p2':
    'Convide só quem você conhece e, para o Jovem, só se você for responsável por ele.',

  'terms.asIs.title': 'O serviço é oferecido como está',
  'terms.asIs.p1':
    'Fazemos o possível para o app funcionar bem, mas não garantimos que ele esteja sempre no ar nem livre de erros.',

  'terms.delete.title': 'Como excluir a conta',
  'terms.delete.p1':
    'Você pode excluir a sua conta quando quiser, em Mais, Privacidade. Há 30 dias de carência antes do apagamento definitivo.',

  'terms.law.title': 'Lei aplicável e contato',
  'terms.law.p1': 'Valem as leis do Brasil.',
  'terms.law.p2': 'Dúvidas sobre estes termos? Escreva para {email}.',
} as const
