import { privacy202611Messages } from './texts/privacy-2026-11'
import { privacy2026111Messages } from './texts/privacy-2026-11.1'
import { privacy202612Messages } from './texts/privacy-2026-12'
import { privacy2026121Messages } from './texts/privacy-2026-12.1'
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

  ...privacy202611Messages,
  ...privacy2026111Messages,
  ...privacy202612Messages,
  ...privacy2026121Messages,
  ...terms202611Messages,
  ...terms202612Messages,
} as const
