import { privacy202611Messages } from './texts/privacy-2026-11'
import { privacy2026111Messages } from './texts/privacy-2026-11.1'
import { privacy2026112Messages } from './texts/privacy-2026-11.2'
import { privacy2026113Messages } from './texts/privacy-2026-11.3'
import { privacy2026114Messages } from './texts/privacy-2026-11.4'
import { privacy202612Messages } from './texts/privacy-2026-12'
import { privacy2026121Messages } from './texts/privacy-2026-12.1'
import { privacy2026122Messages } from './texts/privacy-2026-12.2'
import { privacy2026123Messages } from './texts/privacy-2026-12.3'
import { privacy2026124Messages } from './texts/privacy-2026-12.4'
import { terms202611Messages } from './texts/terms-2026-11'
import { terms202612Messages } from './texts/terms-2026-12'

/**
 * The privacy policy and the terms of use, every version the API may ask for. It loads with their
 * routes (see `registerMessages`). Each version's text lives in `texts/`, under its own ids.
 */
export const legalMessages = {
  'legal.back': 'Voltar ao Liberdade',
  'legal.version': 'Versão {version} · atualizada em {date}',

  'policy.title': 'Política de privacidade',
  'terms.title': 'Termos de uso',

  'bot.title': 'Robô de indicadores do Liberdade',
  'bot.intro':
    'O Liberdade tem um robô que busca indicadores públicos do Banco Central do Brasil, como juros e inflação, para mostrar na tela de indicadores. Ele só lê dados abertos. Nenhum dado seu ou da sua família sai nessa busca.',
  'bot.what.title': 'O que o robô coleta?',
  'bot.what.p1':
    'Cinco séries públicas: a meta da Selic, o CDI, o IPCA do mês, o IPCA em 12 meses e o dólar (PTAX, venda). Nada além disso.',
  'bot.where.title': 'De onde vêm os dados?',
  'bot.where.p1':
    'Das interfaces abertas do Banco Central do Brasil, em api.bcb.gov.br e olinda.bcb.gov.br. O navegador de quem usa o app nunca fala com elas: só a nossa API busca.',
  'bot.when.title': 'Quando ele passa?',
  'bot.when.p1':
    'Duas vezes por dia, às 8h30 e às 19h30 (horário de Brasília). Ele espera entre uma série e outra e se identifica como LiberdadeBot, com o endereço desta página.',
  'bot.contact.title': 'Como falar com o mantenedor?',
  'bot.contact.p1': 'Escreva para {email}. Veja também a {policy}.',
  'bot.contact.policy': 'política de privacidade',

  ...privacy202611Messages,
  ...privacy2026111Messages,
  ...privacy2026112Messages,
  ...privacy2026113Messages,
  ...privacy2026114Messages,
  ...privacy202612Messages,
  ...privacy2026121Messages,
  ...privacy2026122Messages,
  ...privacy2026123Messages,
  ...privacy2026124Messages,
  ...terms202611Messages,
  ...terms202612Messages,
} as const
