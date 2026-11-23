import type { LegalText } from './legalText'
import { privacy202611 } from './texts/privacy-2026-11'
import { privacy202612 } from './texts/privacy-2026-12'
import { terms202611 } from './texts/terms-2026-11'
import { terms202612 } from './texts/terms-2026-12'

const DEFAULT_CONTROLLER = 'o mantenedor do projeto Liberdade'
const DEFAULT_CONTACT = 'privacidade@liberdadefamilia.com.br'

/** An unset or blank build variable falls back to the default. */
function orDefault(value: string | undefined, fallback: string): string {
  const trimmed = value?.trim()
  return trimmed ? trimmed : fallback
}

/**
 * Who answers for the data and how to reach them, plus every version of the two documents the API
 * may put in force (`LegalDocuments.java`). Which one is in force comes from `GET /v1/legal/current`;
 * without an answer, the pages show `latest`. Only the legal routes import this file, so the texts
 * stay out of the initial load. The controller and the contact come from the build
 * (`VITE_PRIVACY_CONTROLLER`, `VITE_PRIVACY_CONTACT`), read when asked so a test can change them.
 */
export const legalConfig = {
  get controllerName(): string {
    return orDefault(import.meta.env.VITE_PRIVACY_CONTROLLER, DEFAULT_CONTROLLER)
  },
  get contactEmail(): string {
    return orDefault(import.meta.env.VITE_PRIVACY_CONTACT, DEFAULT_CONTACT)
  },
  texts: {
    'terms-2026-11': terms202611,
    'terms-2026-12': terms202612,
    'privacy-2026-11': privacy202611,
    'privacy-2026-12': privacy202612,
  } as Record<string, LegalText>,
  latest: { terms: 'terms-2026-12', privacy: 'privacy-2026-12' },
}

/** The text of `version`, or the latest one of its kind when this build does not know it. */
export function legalText(version: string, fallback: string): { version: string; text: LegalText } {
  if (Object.hasOwn(legalConfig.texts, version)) {
    return { version, text: legalConfig.texts[version] }
  }
  return { version: fallback, text: legalConfig.texts[fallback] }
}
