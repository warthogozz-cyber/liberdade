import { describe, expect, it } from 'vitest'
import type { DocumentStatus } from '@/shared/api'
import { versionsToAccept } from './useDocuments'

const listed = (purpose: DocumentStatus['purpose'], currentVersion: string): DocumentStatus => ({
  purpose,
  currentVersion,
  acceptedVersion: null,
  acceptedAt: null,
})

describe('versionsToAccept', () => {
  it('takesTheListedCurrentVersions', () => {
    expect(
      versionsToAccept([listed('TERMS', 'terms-2026-11'), listed('PRIVACY', 'privacy-2026-11')]),
    ).toEqual({ terms: 'terms-2026-11', privacy: 'privacy-2026-11' })
  })

  it('throwsWithoutAVersion', () => {
    expect(() => versionsToAccept([listed('TERMS', 'terms-2026-12')])).toThrow(
      'documents: no current version for PRIVACY',
    )
    expect(() => versionsToAccept([listed('TERMS', ''), listed('PRIVACY', 'p')])).toThrow(
      'documents: no current version for TERMS',
    )
  })
})
