import { describe, expect, it } from 'vitest'
import legalDocumentsJava from '../../../../api/src/main/java/br/com/liberdadefamilia/privacy/internal/LegalDocuments.java?raw'
import { legalConfig } from './legalConfig'
import { legalMessages } from './messages.pt-BR'
import type { LegalText } from './legalText'

/** Every message id a text uses: the intro, the headings and each paragraph or list item. */
function idsOf(text: LegalText): string[] {
  return [
    text.intro,
    ...text.sections.flatMap((section) => [
      section.title,
      ...section.blocks.flatMap((block) => ('p' in block ? [block.p] : block.list)),
    ]),
  ]
}

describe('legal texts', () => {
  it('embedsEveryApiVersion', () => {
    expect(Object.keys(legalConfig.texts).sort()).toEqual([
      'privacy-2026-11',
      'privacy-2026-12',
      'terms-2026-11',
      'terms-2026-12',
    ])
    for (const text of Object.values(legalConfig.texts)) {
      expect(text.updatedAt.trim()).not.toBe('')
      expect(text.sections.length).toBeGreaterThan(0)
    }

    // Each version the API can ask a person to accept has its text in this build.
    const apiVersions = [...legalDocumentsJava.matchAll(/"((?:terms|privacy)-\d{4}-\d{2})"/g)].map(
      (match) => match[1],
    )
    expect(apiVersions.length).toBeGreaterThan(0)
    for (const version of apiVersions) expect(legalConfig.texts).toHaveProperty([version])
  })

  it('latestIsEmbedded', () => {
    expect(legalConfig.latest).toEqual({ terms: 'terms-2026-12', privacy: 'privacy-2026-12' })
    expect(legalConfig.texts).toHaveProperty([legalConfig.latest.terms])
    expect(legalConfig.texts).toHaveProperty([legalConfig.latest.privacy])
  })

  it('everyIdHasItsText', () => {
    for (const text of Object.values(legalConfig.texts)) {
      for (const id of idsOf(text)) expect(legalMessages).toHaveProperty([id])
    }
  })
})
